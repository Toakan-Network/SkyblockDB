package skyblockdb.store;

import net.fabricmc.loader.api.FabricLoader;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import skyblockdb.SkyblockDb;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The internal MVStore file ({@code config/skyblock-db/skyblock.mv.db}). Only the single writer thread touches it:
 * the client thread hands work over with {@link #write} or {@link #query} and never waits on disk. The layout is
 * internal; other mods read through the public API.
 *
 * <p>Maps ("tables"), keys joined with {@link #SEP}:
 * <ul>
 *   <li>{@code meta}: {@code schema_version} and repo SHAs.</li>
 *   <li>{@code profiles}: profile UUID -> [player UUID, profile name, profile type, SkyBlock level, seen at].</li>
 *   <li>{@code item_counts}: profile|container|slot -> [SkyBlock ID, amount, seen at, source, confident].</li>
 *   <li>{@code currencies}: profile|currency -> [value, seen at].</li>
 *   <li>{@code history}: profile|SkyBlock ID|epoch ms (13 digits) -> total amount after the change.</li>
 * </ul>
 */
public final class Store {
    /** Bump when the layout changes, and add a step to {@link #migrate}. */
    public static final int SCHEMA_VERSION = 1;
    /** Key separator. Never part of a UUID, container name, currency name or SkyBlock ID. */
    public static final String SEP = "|";

    private static final Store INSTANCE = new Store();

    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SkyblockDB writer");
        thread.setDaemon(true);
        return thread;
    });
    /** Writer thread only. Null until opened, or when opening failed. */
    private MVStore store;
    private MVMap<String, String> meta;
    private MVMap<String, Object[]> profiles;
    private MVMap<String, Object[]> itemCounts;
    private MVMap<String, Object[]> currencies;
    private MVMap<String, Long> history;

    private Store() {}

    public static Store get() {
        return INSTANCE;
    }

    /** Opens the file on the writer thread; {@code onLoaded} then gets every stored row (on the writer thread). */
    public void open(Consumer<Store> onLoaded) {
        writer.execute(() -> {
            try {
                File dir = new File(FabricLoader.getInstance().getConfigDir().toFile(), SkyblockDb.MOD_ID);
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Cannot create " + dir);
                store = new MVStore.Builder()
                    .fileName(new File(dir, "skyblock.mv.db").getPath())
                    .compress()
                    .autoCommitDisabled()
                    .open();
                meta = store.openMap("meta", new MVMap.Builder<>());
                profiles = store.openMap("profiles", new MVMap.Builder<>());
                itemCounts = store.openMap("item_counts", new MVMap.Builder<>());
                currencies = store.openMap("currencies", new MVMap.Builder<>());
                history = store.openMap("history", new MVMap.Builder<>());
                migrate();
                store.commit();
                SkyblockDb.debug("Store opened: {} item rows, {} profiles", itemCounts.size(), profiles.size());
                onLoaded.accept(this);
            } catch (RuntimeException e) {
                SkyblockDb.LOGGER.error("Could not open the SkyblockDB store; nothing will be recorded", e);
                closeQuietly();
            }
        });
    }

    private void migrate() {
        String stored = meta.get("schema_version");
        int version = stored == null ? 0 : Integer.parseInt(stored);
        if (version > SCHEMA_VERSION) {
            throw new IllegalStateException("Store is schema " + version + ", newer than this mod (" + SCHEMA_VERSION + ")");
        }
        // Version 0 -> 1: new file, the maps above are the whole layout. Later steps go here, in order.
        meta.put("schema_version", Integer.toString(SCHEMA_VERSION));
    }

    /** Runs {@code change} on the writer thread, then commits it as one batch. Dropped if the store isn't open. */
    public void write(Consumer<Store> change) {
        writer.execute(() -> {
            if (store == null) return;
            try {
                change.accept(this);
                store.commit();
            } catch (RuntimeException e) {
                SkyblockDb.LOGGER.error("SkyblockDB write failed", e);
                store.rollback();
            }
        });
    }

    /** Reads on the writer thread; the future completes there. */
    public <T> CompletableFuture<T> query(Function<Store, T> read) {
        return CompletableFuture.supplyAsync(() -> {
            if (store == null) throw new IllegalStateException("SkyblockDB store is not open");
            return read.apply(this);
        }, writer);
    }

    /** Commits and closes the file (game shutdown). Waits briefly for queued writes. */
    public void close() {
        writer.execute(this::closeQuietly);
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) SkyblockDb.LOGGER.warn("SkyblockDB writer did not finish in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeQuietly() {
        if (store == null) return;
        try {
            store.close();
        } catch (RuntimeException e) {
            SkyblockDb.LOGGER.warn("Closing the SkyblockDB store failed", e);
        }
        store = null;
    }

    public static String key(String... parts) {
        return String.join(SEP, parts);
    }

    /** History key: the time is zero-padded so keys sort by time within an item. */
    public static String historyKey(String profile, String sbId, long epochMillis) {
        return key(profile, sbId, String.format("%013d", epochMillis));
    }

    /** Every entry whose key starts with {@code prefix} + {@link #SEP}, in key order. */
    public static <V> List<Map.Entry<String, V>> withPrefix(MVMap<String, V> map, String prefix) {
        String from = prefix + SEP;
        // '|' + 1 is '}', so every key with the prefix sorts before prefix + '}'.
        String to = prefix + (char) (SEP.charAt(0) + 1);
        List<Map.Entry<String, V>> out = new java.util.ArrayList<>();
        var cursor = map.cursor(from, to, false);
        while (cursor.hasNext()) {
            String k = cursor.next();
            if (k.equals(to)) break;
            out.add(Map.entry(k, cursor.getValue()));
        }
        return out;
    }

    public MVMap<String, String> meta() { return meta; }
    public MVMap<String, Object[]> profiles() { return profiles; }
    public MVMap<String, Object[]> itemCounts() { return itemCounts; }
    public MVMap<String, Object[]> currencies() { return currencies; }
    public MVMap<String, Long> history() { return history; }
}
