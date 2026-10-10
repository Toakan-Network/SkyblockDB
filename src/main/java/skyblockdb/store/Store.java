package skyblockdb.store;

import net.fabricmc.loader.api.FabricLoader;
import skyblockdb.SkyblockDb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The internal store: JSON files in {@code config/skyblock-db/}, one per table, held in memory. Only the single
 * writer thread touches it: the client thread hands work over with {@link #write} or {@link #query} and never waits
 * on disk. Changed tables are saved at most every {@link #SAVE_DELAY_SECONDS} seconds, and on close. The layout is
 * internal; other mods read through the public API.
 *
 * <p>Tables, keys joined with {@link #SEP} (see {@link Table} for the file formats):
 * <ul>
 *   <li>{@code meta}: {@code schema_version}, the reference hash and repo SHA, {@code history_enabled}.</li>
 *   <li>{@code profiles}: profile UUID -> [player UUID, profile name, profile type, SkyBlock level, seen at].</li>
 *   <li>{@code item_counts}: profile|container|slot -> [SkyBlock ID, amount, seen at, source, confident].</li>
 *   <li>{@code currencies}: profile|currency -> [value, seen at].</li>
 *   <li>{@code history} (log, only while switched on): profile|SkyBlock ID|epoch ms (13 digits) -> total amount
 *       after the change.</li>
 *   <li>{@code items}: SkyBlock ID -> [name, rarity, category, origin (repo/observed), retired at (0: current)].</li>
 *   <li>{@code recipes}: type|output|n (3 digits, repository order) -> [output count, inputs "ID*n,ID*n" (sorted),
 *       coins, seconds, npc].</li>
 *   <li>{@code reference_changes} (log): epoch ms (13 digits)|sequence (6) -> [SkyBlock ID, kind, change, field,
 *       old, new, repo SHA].</li>
 *   <li>{@code observations}: SkyBlock ID|kind -> [seen value, first seen, last seen, sessions, resolved at (0: open)].</li>
 *   <li>{@code mobs}: mob ID -> [name, island, type, loot tables as JSON].</li>
 *   <li>{@code requirements}: SkyBlock ID, pet or attribute ID -> [unlock line, ...].</li>
 *   <li>{@code attributes}: attribute ID -> [name, shard ID, shard name, rarity].</li>
 *   <li>{@code npc_prices}: SkyBlock ID -> [NPC sell price].</li>
 * </ul>
 */
public final class Store {
    /** Bump when the layout changes, and add a step to {@link #migrate}. */
    public static final int SCHEMA_VERSION = 6;
    /** Key separator. Never part of a UUID, container name, currency name or SkyBlock ID. */
    public static final String SEP = "|";
    /** Changes reach disk at most this long after they were made (a crash loses at most this much). */
    private static final int SAVE_DELAY_SECONDS = 5;
    /** Meta key of the history switch ("true" when on). */
    private static final String HISTORY_SETTING = "history_enabled";

    private static final Store INSTANCE = new Store();

    private final ScheduledThreadPoolExecutor writer = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "SkyblockDB writer");
        thread.setDaemon(true);
        return thread;
    });

    {
        // close() saves at once; a save still waiting for its delay is not needed then.
        writer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    // Writer thread only.
    /** Null until opened, or when opening failed. */
    private Path dir;
    private boolean saveScheduled = false;
    /** Whether item history is recorded. Off by default; read from any thread. */
    private volatile boolean recordHistory = false;
    private final Table<String> meta = new Table<>("meta", false);
    private final Table<Object[]> profiles = new Table<>("profiles", false);
    private final Table<Object[]> itemCounts = new Table<>("item_counts", false);
    private final Table<Object[]> currencies = new Table<>("currencies", false);
    private final Table<Long> history = new Table<>("history", true);
    private final Table<Object[]> items = new Table<>("items", false);
    private final Table<Object[]> recipes = new Table<>("recipes", false);
    private final Table<Object[]> referenceChanges = new Table<>("reference_changes", true);
    private final Table<Object[]> observations = new Table<>("observations", false);
    private final Table<Object[]> mobs = new Table<>("mobs", false);
    private final Table<Object[]> requirements = new Table<>("requirements", false);
    private final Table<Object[]> attributes = new Table<>("attributes", false);
    private final Table<Object[]> npcPrices = new Table<>("npc_prices", false);
    private final List<Table<?>> tables = List.of(meta, profiles, itemCounts, currencies, history, items, recipes,
        referenceChanges, observations, mobs, requirements, attributes, npcPrices);

    private Store() {}

    public static Store get() {
        return INSTANCE;
    }

    /** Loads the files on the writer thread; {@code onLoaded} then gets every stored row (on the writer thread). */
    public void open(Consumer<Store> onLoaded) {
        writer.execute(() -> {
            try {
                Path folder = FabricLoader.getInstance().getConfigDir().resolve(SkyblockDb.MOD_ID);
                Files.createDirectories(folder);
                // Versions before 0.4.5 used an H2 MVStore file, which could lose a session's writes after a crash.
                if (Files.deleteIfExists(folder.resolve("skyblock.mv.db"))) {
                    SkyblockDb.LOGGER.info("SkyblockDB: replaced the old store file with JSON files; recording starts afresh");
                }
                meta.load(folder);
                recordHistory = "true".equals(meta.get(HISTORY_SETTING));
                for (Table<?> table : tables) {
                    if (table == meta) continue;
                    // History is off by default (only useful for debugging); a log left from when it was on goes.
                    if (table == history && !recordHistory()) {
                        boolean had = Files.exists(folder.resolve("history.jsonl"));
                        history.discard(folder);
                        if (had) SkyblockDb.LOGGER.info("SkyblockDB: history is off; removed the old history log");
                        continue;
                    }
                    table.load(folder);
                }
                dir = folder;
                migrate();
                saveSoon();
                SkyblockDb.debug("Store opened: {} item rows, {} profiles", itemCounts.size(), profiles.size());
                onLoaded.accept(this);
            } catch (IOException | RuntimeException e) {
                SkyblockDb.LOGGER.error("Could not open the SkyblockDB store; nothing will be recorded", e);
                dir = null;
            }
        });
    }

    private void migrate() {
        String stored = meta.get("schema_version");
        int version = stored == null ? 0 : Integer.parseInt(stored);
        if (version > SCHEMA_VERSION) {
            throw new IllegalStateException("Store is schema " + version + ", newer than this mod (" + SCHEMA_VERSION + ")");
        }
        // 4: JSON files (schemas 1-3 were the MVStore file, which is not carried over). 5: requirements, attributes
        // and npc_prices, which fill on the next repository load (the reference hash covers them, so it differs).
        // 6: recipe inputs joined with ',' (';' broke pet and enchantment keys) and numbered in repository order;
        // recipes are emptied, so the next load stores them again as a baseline.
        // Later steps go here, in order.
        if (version >= 4 && version < 6) {
            recipes.clear();
            meta.remove("reference_hash");
        }
        if (version != SCHEMA_VERSION) meta.put("schema_version", Integer.toString(SCHEMA_VERSION));
    }

    /**
     * Runs {@code change} on the writer thread; the tables it changed are saved within a few seconds. Dropped if the
     * store isn't open. A change that throws is logged; what it changed before throwing stays.
     */
    public void write(Consumer<Store> change) {
        writer.execute(() -> {
            if (dir == null) return;
            try {
                change.accept(this);
            } catch (RuntimeException e) {
                SkyblockDb.LOGGER.error("SkyblockDB write failed", e);
            }
            saveSoon();
        });
    }

    /** Reads on the writer thread; the future completes there. */
    public <T> CompletableFuture<T> query(Function<Store, T> read) {
        return CompletableFuture.supplyAsync(() -> {
            if (dir == null) throw new IllegalStateException("SkyblockDB store is not open");
            return read.apply(this);
        }, writer);
    }

    /** Saves everything and stops the writer (game shutdown). Waits briefly for queued writes. */
    public void close() {
        writer.execute(this::save);
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) SkyblockDb.LOGGER.warn("SkyblockDB writer did not finish in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void saveSoon() {
        if (saveScheduled || tables.stream().noneMatch(Table::dirty)) return;
        saveScheduled = true;
        writer.schedule(this::save, SAVE_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void save() {
        saveScheduled = false;
        if (dir == null) return;
        for (Table<?> table : tables) {
            try {
                table.save(dir);
            } catch (IOException | RuntimeException e) {
                SkyblockDb.LOGGER.error("SkyblockDB: could not save {}", table, e);
            }
        }
    }

    /** True while item history is recorded: switched on ({@code /skyblockdb history on}), or debug logging is on. */
    public boolean recordHistory() {
        return recordHistory || SkyblockDb.debugLogging;
    }

    /** True when history is switched on itself, not just by debug logging. */
    public boolean historySwitchedOn() {
        return recordHistory;
    }

    /** Switches history recording and remembers the choice. Turning it off keeps the log until the next start. */
    public void setRecordHistory(boolean on) {
        recordHistory = on;
        write(store -> {
            if (on) meta.put(HISTORY_SETTING, "true");
            else meta.remove(HISTORY_SETTING);
        });
    }

    public static String key(String... parts) {
        return String.join(SEP, parts);
    }

    /** History key: the time is zero-padded so keys sort by time within an item. */
    public static String historyKey(String profile, String sbId, long epochMillis) {
        return key(profile, sbId, String.format("%013d", epochMillis));
    }

    /** Every entry whose key starts with {@code prefix} + {@link #SEP}, in key order. */
    public static <V> List<Map.Entry<String, V>> withPrefix(NavigableMap<String, V> map, String prefix) {
        // '|' + 1 is '}', so every key with the prefix sorts before prefix + '}'.
        return new ArrayList<>(map.subMap(prefix + SEP, true, prefix + (char) (SEP.charAt(0) + 1), false).entrySet());
    }

    public NavigableMap<String, String> meta() { return meta; }
    public NavigableMap<String, Object[]> profiles() { return profiles; }
    public NavigableMap<String, Object[]> itemCounts() { return itemCounts; }
    public NavigableMap<String, Object[]> currencies() { return currencies; }
    public NavigableMap<String, Long> history() { return history; }
    public NavigableMap<String, Object[]> items() { return items; }
    public NavigableMap<String, Object[]> recipes() { return recipes; }
    public NavigableMap<String, Object[]> referenceChanges() { return referenceChanges; }
    public NavigableMap<String, Object[]> observations() { return observations; }
    public NavigableMap<String, Object[]> mobs() { return mobs; }
    public NavigableMap<String, Object[]> requirements() { return requirements; }
    public NavigableMap<String, Object[]> attributes() { return attributes; }
    public NavigableMap<String, Object[]> npcPrices() { return npcPrices; }
}
