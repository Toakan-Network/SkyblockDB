package skyblockdb.record;

import kotlin.Unit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import skyblockdb.ApiImpl;
import skyblockdb.SkyblockDb;
import skyblockdb.api.Currency;
import skyblockdb.api.ItemCount;
import skyblockdb.api.Profile;
import skyblockdb.reference.ReferenceData;
import skyblockdb.SkyblockDetector;
import skyblockdb.store.Store;
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI;
import tech.thatgravyboat.skyblockapi.api.events.hypixel.SacksChangeEvent;
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent;
import tech.thatgravyboat.skyblockapi.api.profile.currency.CurrencyAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.accessory.AccessoryBagAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.accessory.AccessoryBagItem;
import tech.thatgravyboat.skyblockapi.api.profile.items.equipment.EquipmentAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.equipment.EquipmentSlot;
import tech.thatgravyboat.skyblockapi.api.profile.items.sacks.SacksAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.storage.PlayerStorageInstance;
import tech.thatgravyboat.skyblockapi.api.profile.items.storage.StorageAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.vault.VaultAPI;
import tech.thatgravyboat.skyblockapi.api.profile.profile.ProfileAPI;

import java.time.Instant;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Copies SkyblockAPI's in-memory data into the store. Everything is read on the client thread (item stacks live
 * there), compared with an in-memory copy of what is stored, and only the differences go to the writer thread.
 *
 * <p>Full sync (sacks, storage, accessory bag, equipment, vault, inventory, profile) when SkyblockAPI reports a sack
 * change, a container closes or the profile changes: storage and the other menus have no change event of their
 * own, SkyblockAPI updates them while their menu is open. Currencies are compared once a second (in memory only).
 * Nothing is sent to the server.
 */
public final class Recorder {
    private static final String SOURCE = "skyblockapi";
    private static final int CURRENCY_INTERVAL_TICKS = 20;
    /** Bursts of events (several sack messages, menu hopping) end up in one sync at most this often. */
    private static final int MIN_SYNC_INTERVAL_TICKS = 10;

    /** One stored slot. {@code seenAt} is not part of equality: a re-read with the same item isn't a change. */
    record Row(String sbId, long amount, long seenAt, String source, boolean confident) {
        Object[] encode() {
            return new Object[]{sbId, amount, seenAt, source, confident};
        }

        static Row decode(Object[] v) {
            return new Row((String) v[0], (Long) v[1], (Long) v[2], (String) v[3], (Boolean) v[4]);
        }

        boolean sameAs(Row other) {
            return other != null && amount == other.amount && confident == other.confident
                && sbId.equals(other.sbId) && source.equals(other.source);
        }
    }

    // Client thread only from here on.
    private static boolean loaded = false;
    /** Stored item rows by profile|container|slot. */
    private static final Map<String, Row> rows = new HashMap<>();
    /** Stored currency values by profile|currency. */
    private static final Map<String, Currency> currencies = new HashMap<>();
    /** The profile the API calls current; null off SkyBlock. */
    private static String current;
    /** Last profile row written per profile, without its time, so unchanged profiles aren't rewritten. */
    private static final Map<String, String> profileSignatures = new HashMap<>();
    /** Display names of IDs the reference data doesn't know, gathered during one read. */
    private static final Map<String, String> seenNames = new LinkedHashMap<>();
    private static boolean syncWanted = false;
    private static int ticksSinceSync = 0;
    private static int currencyTicks = 0;
    private static boolean wasOnSkyblock = false;

    private Recorder() {}

    /** Writer thread, right after the store opened: copy what is stored, then hand it to the client thread. */
    public static void onStoreOpened(Store store) {
        Map<String, Row> storedRows = new HashMap<>();
        store.itemCounts().forEach((k, v) -> storedRows.put(k, Row.decode(v)));
        Map<String, Currency> storedCurrencies = new HashMap<>();
        store.currencies().forEach((k, v) -> storedCurrencies.put(k, currency(k, ((Number) v[0]).doubleValue(), (Long) v[1])));
        List<Profile> storedProfiles = new ArrayList<>();
        store.profiles().forEach((k, v) -> storedProfiles.add(new Profile(k, (String) v[0], (String) v[1],
            (String) v[2], ((Long) v[3]).intValue(), Instant.ofEpochMilli((Long) v[4]))));
        Minecraft.getInstance().execute(() -> {
            rows.putAll(storedRows);
            currencies.putAll(storedCurrencies);
            Set<String> known = new HashSet<>();
            for (String key : rows.keySet()) known.add(key.substring(0, key.indexOf(Store.SEP)));
            for (String key : currencies.keySet()) known.add(key.substring(0, key.indexOf(Store.SEP)));
            for (Profile p : storedProfiles) {
                ApiImpl.INSTANCE.publishProfile(p);
                profileSignatures.put(p.profileUuid(), signature(p.playerUuid(), p.name(), p.type(), p.sbLevel()));
            }
            known.forEach(Recorder::publish);
            loaded = true;
            syncWanted = true;
        });
    }

    public static void register() {
        SkyBlockAPI.getEventBus().register(SacksChangeEvent.class, 0, false, event -> want());
        SkyBlockAPI.getEventBus().register(ContainerCloseEvent.class, 0, false, event -> want());
        SkyBlockAPI.getEventBus().register(ProfileChangeEvent.class, 0, false, event -> want());
        ClientTickEvents.END_CLIENT_TICK.register(Recorder::tick);
    }

    private static Unit want() {
        syncWanted = true;
        return Unit.INSTANCE;
    }

    private static void tick(Minecraft client) {
        boolean onSkyblock = SkyblockDetector.isOnSkyblock();
        if (onSkyblock && !wasOnSkyblock) syncWanted = true;
        wasOnSkyblock = onSkyblock;
        if (!onSkyblock && current != null) setCurrent(null);
        if (!loaded || !onSkyblock) return;
        ticksSinceSync++;
        if (syncWanted && ticksSinceSync >= MIN_SYNC_INTERVAL_TICKS) {
            syncWanted = false;
            ticksSinceSync = 0;
            currencyTicks = 0;
            sync(client, true);
        } else if (++currencyTicks >= CURRENCY_INTERVAL_TICKS) {
            currencyTicks = 0;
            sync(client, false);
        }
    }

    /** The current profile's UUID, or null while SkyblockAPI doesn't know it yet. */
    private static String profile() {
        if (!ProfileAPI.INSTANCE.isLoaded()) return null;
        UUID uuid = ProfileAPI.INSTANCE.getProfileUuid();
        return uuid == null ? null : uuid.toString();
    }

    private static void sync(Minecraft client, boolean items) {
        String profile = profile();
        if (profile == null) return;
        if (!profile.equals(current)) setCurrent(profile);
        long now = System.currentTimeMillis();
        Map<String, Currency> currencyChanges = currencyChanges(profile, now);
        if (!items) {
            if (!currencyChanges.isEmpty()) {
                submit(profile, now, null, List.of(), List.of(), Map.of(), currencyChanges);
                publishAndFire(profile, Set.of(), currencyChanges.keySet(), false);
            }
            return;
        }
        Map<String, Row> read = new LinkedHashMap<>();
        Set<String> containers = new HashSet<>();
        seenNames.clear();
        try {
            readItems(client, profile, now, read, containers);
        } catch (ConcurrentModificationException e) {
            syncWanted = true; // SkyblockAPI changed a list while it was copied; try again shortly
            return;
        }
        // Sack IDs carry no name; the ID stands in until the repository has one.
        for (Row row : read.values()) {
            if (ReferenceData.item(row.sbId()) == null) seenNames.putIfAbsent(row.sbId(), row.sbId());
        }
        ReferenceData.observe(seenNames);

        // Diff, only inside containers this read covered: a container SkyblockAPI knows nothing about yet keeps
        // its stored rows.
        Map<String, Row> puts = new LinkedHashMap<>();
        List<String> removes = new ArrayList<>();
        Set<String> changedIds = new HashSet<>();
        Map<String, Long> before = new HashMap<>();
        Map<String, Long> after = new HashMap<>();
        String profilePrefix = profile + Store.SEP;
        for (Map.Entry<String, Row> e : rows.entrySet()) {
            if (!e.getKey().startsWith(profilePrefix)) continue;
            before.merge(e.getValue().sbId(), e.getValue().amount(), Long::sum);
            if (containers.contains(containerOf(e.getKey())) && !read.containsKey(e.getKey())) removes.add(e.getKey());
        }
        read.forEach((key, row) -> {
            Row old = rows.get(key);
            if (!row.sameAs(old)) {
                puts.put(key, row);
                changedIds.add(row.sbId());
                if (old != null) changedIds.add(old.sbId());
            }
        });
        for (String key : removes) changedIds.add(rows.remove(key).sbId());
        rows.putAll(puts);
        for (Map.Entry<String, Row> e : rows.entrySet()) {
            if (e.getKey().startsWith(profilePrefix)) after.merge(e.getValue().sbId(), e.getValue().amount(), Long::sum);
        }
        Map<String, Long> totals = new HashMap<>();
        Set<String> ids = new HashSet<>(before.keySet());
        ids.addAll(after.keySet());
        for (String id : ids) {
            long total = after.getOrDefault(id, 0L);
            if (total != before.getOrDefault(id, 0L)) totals.put(id, total);
        }

        String player = playerUuid(client);
        String name = Objects.toString(ProfileAPI.INSTANCE.getProfileName(), "");
        // The enum name (IRONMAN), as the API documents; its toString is "Ironman".
        String type = ProfileAPI.INSTANCE.getProfileType() == null ? "" : ProfileAPI.INSTANCE.getProfileType().name();
        int level = ProfileAPI.INSTANCE.getSbLevel();
        Object[] profileRow = new Object[]{player, name, type, (long) level, now};
        // Written only when something in it (other than the time) changed.
        String signature = signature(player, name, type, level);
        boolean profileChanged = !signature.equals(profileSignatures.put(profile, signature));
        if (profileChanged) {
            ApiImpl.INSTANCE.publishProfile(new Profile(profile, player, name, type, level, Instant.ofEpochMilli(now)));
        } else {
            profileRow = null;
        }
        if (puts.isEmpty() && removes.isEmpty() && currencyChanges.isEmpty()) {
            if (profileRow != null) {
                submit(profile, now, profileRow, List.of(), List.of(), Map.of(), Map.of());
                ApiImpl.INSTANCE.fire(l -> l.onProfileChanged(profile));
            }
            return;
        }
        SkyblockDb.debug("Sync: {} changed, {} removed, {} totals, {} currencies",
            puts.size(), removes.size(), totals.size(), currencyChanges.size());
        submit(profile, now, profileRow, new ArrayList<>(puts.entrySet()), removes, totals, currencyChanges);
        publishAndFire(profile, changedIds, currencyChanges.keySet(), profileChanged);
    }

    private static String signature(String player, String name, String type, int level) {
        return player + Store.SEP + name + Store.SEP + type + Store.SEP + level;
    }

    private static Currency currency(String key, double value, long seenAt) {
        int sep = key.indexOf(Store.SEP);
        return new Currency(key.substring(0, sep), key.substring(sep + 1), value, Instant.ofEpochMilli(seenAt));
    }

    private static void setCurrent(String profile) {
        current = profile;
        ApiImpl.INSTANCE.setCurrent(profile);
        if (profile != null) ApiImpl.INSTANCE.fire(l -> l.onProfileChanged(profile));
    }

    /** Rebuilds a profile's API view from the in-memory rows and currencies. */
    private static void publish(String profile) {
        String prefix = profile + Store.SEP;
        List<ItemCount> items = new ArrayList<>();
        rows.forEach((key, row) -> {
            if (key.startsWith(prefix)) {
                items.add(ApiImpl.itemCount(key, row.sbId(), row.amount(), row.seenAt(), row.source(), row.confident()));
            }
        });
        ApiImpl.INSTANCE.publishItems(profile, items);
        Map<String, Currency> values = new LinkedHashMap<>();
        currencies.forEach((key, c) -> {
            if (key.startsWith(prefix)) values.put(c.name(), c);
        });
        ApiImpl.INSTANCE.publishCurrencies(profile, values);
    }

    private static void publishAndFire(String profile, Set<String> changedIds, Set<String> currencyKeys, boolean profileChanged) {
        publish(profile);
        if (!changedIds.isEmpty()) {
            Set<String> ids = Set.copyOf(changedIds);
            ApiImpl.INSTANCE.fire(l -> l.onItemsChanged(profile, ids));
        }
        if (!currencyKeys.isEmpty()) {
            Set<String> names = Set.copyOf(ApiImpl.currencyNames(currencyKeys));
            ApiImpl.INSTANCE.fire(l -> l.onCurrenciesChanged(profile, names));
        }
        if (profileChanged) ApiImpl.INSTANCE.fire(l -> l.onProfileChanged(profile));
    }

    /** Container of a profile|container|slot key. */
    private static String containerOf(String key) {
        int first = key.indexOf(Store.SEP);
        int second = key.indexOf(Store.SEP, first + 1);
        return key.substring(first + 1, second);
    }

    private static void readItems(Minecraft client, String profile, long now, Map<String, Row> out, Set<String> containers) {
        Map<String, Integer> sacks = new HashMap<>(SacksAPI.INSTANCE.getSackItems());
        if (!sacks.isEmpty()) {
            containers.add("sacks");
            // SkyblockAPI can hold one item under two spellings: a stale "RED_ROSE-5" next to the current
            // "RED_ROSE:5" (Hypixel's own form). Both normalise to one ID; the ':' entry wins, whatever its count.
            Map<String, Integer> counts = new HashMap<>();
            Set<String> fromHypixelSpelling = new HashSet<>();
            sacks.forEach((rawId, count) -> {
                String id = SkyblockIds.normalize(rawId);
                if (id.isEmpty() || count == null) return;
                boolean hypixel = rawId.indexOf(':') >= 0;
                if (!counts.containsKey(id) || (hypixel && !fromHypixelSpelling.contains(id))) {
                    counts.put(id, count);
                    if (hypixel) fromHypixelSpelling.add(id);
                }
            });
            counts.forEach((id, count) -> {
                if (count > 0) out.put(Store.key(profile, "sacks", id), new Row(id, count, now, SOURCE, true));
            });
        }
        storage(profile, "enderchest", StorageAPI.INSTANCE.getEnderchests(), out, containers);
        storage(profile, "backpack", StorageAPI.INSTANCE.getBackpacks(), out, containers);
        storage(profile, "rift_storage", StorageAPI.INSTANCE.getRiftStorage(), out, containers);

        List<AccessoryBagItem> accessories = new ArrayList<>(AccessoryBagAPI.INSTANCE.getItems());
        if (!accessories.isEmpty()) {
            containers.add("accessory_bag");
            for (AccessoryBagItem item : accessories) {
                stack(profile, "accessory_bag", item.getPage() + "." + item.getSlot(), item.getItem(), now, out);
            }
        }
        equipment(profile, "equipment", EquipmentAPI.INSTANCE.getNormalEquipment(), now, out, containers);
        equipment(profile, "rift_equipment", EquipmentAPI.INSTANCE.getRiftEquipment(), now, out, containers);

        List<ItemStack> vault = new ArrayList<>(VaultAPI.INSTANCE.getItems());
        if (!vault.isEmpty()) {
            containers.add("vault");
            for (int i = 0; i < vault.size(); i++) stack(profile, "vault", Integer.toString(i), vault.get(i), now, out);
        }

        if (client.player != null) {
            containers.add("inventory");
            Inventory inventory = client.player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                stack(profile, "inventory", Integer.toString(i), inventory.getItem(i), now, out);
            }
        }
    }

    private static void storage(String profile, String kind, List<PlayerStorageInstance> instances,
                                Map<String, Row> out, Set<String> containers) {
        for (PlayerStorageInstance instance : new ArrayList<>(instances)) {
            String container = kind + ":" + instance.getIndex();
            containers.add(container);
            long seen = instance.getLastUpdated() != null ? instance.getLastUpdated().toEpochMilliseconds() : System.currentTimeMillis();
            List<ItemStack> items = new ArrayList<>(instance.getItems());
            for (int i = 0; i < items.size(); i++) stack(profile, container, Integer.toString(i), items.get(i), seen, out);
        }
    }

    private static void equipment(String profile, String container, Map<EquipmentSlot, ItemStack> slots, long now,
                                  Map<String, Row> out, Set<String> containers) {
        Map<EquipmentSlot, ItemStack> copy = new HashMap<>(slots);
        if (copy.isEmpty()) return;
        containers.add(container);
        copy.forEach((slot, stack) -> stack(profile, container, slot.name(), stack, now, out));
    }

    private static void stack(String profile, String container, String slot, ItemStack stack, long seen, Map<String, Row> out) {
        String id = SkyblockIds.of(stack);
        if (id.isEmpty()) return;
        out.put(Store.key(profile, container, slot), new Row(id, stack.getCount(), seen, SOURCE, true));
        if (ReferenceData.item(id) == null) seenNames.putIfAbsent(id, stack.getHoverName().getString());
    }

    private static Map<String, Currency> currencyChanges(String profile, long now) {
        CurrencyAPI c = CurrencyAPI.INSTANCE;
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("purse", c.getPurse());
        values.put("bank", (double) c.getPersonalBank());
        values.put("coop_bank", (double) c.getCoopBank());
        values.put("motes", (double) c.getMotes());
        values.put("bits", (double) c.getBits());
        values.put("copper", (double) c.getCopper());
        values.put("sowdust", (double) c.getSowdust());
        values.put("kernels", (double) c.getKernels());
        values.put("north_stars", (double) c.getNorthStars());
        values.put("gems", (double) c.getGems());
        values.put("soulflow", (double) c.getSoulflow());
        Map<String, Currency> changes = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            String key = Store.key(profile, name);
            Currency old = currencies.get(key);
            if (old == null || old.value() != value) changes.put(key, new Currency(profile, name, value, Instant.ofEpochMilli(now)));
        });
        currencies.putAll(changes);
        return changes;
    }

    private static String playerUuid(Minecraft client) {
        UUID account = client.getUser().getProfileId();
        return account == null ? "" : account.toString();
    }

    private static void submit(String profile, long now, Object[] profileRow, List<Map.Entry<String, Row>> puts,
                               List<String> removes, Map<String, Long> totals, Map<String, Currency> currencyChanges) {
        Store.get().write(store -> {
            if (profileRow != null) store.profiles().put(profile, profileRow);
            for (Map.Entry<String, Row> e : puts) store.itemCounts().put(e.getKey(), e.getValue().encode());
            for (String key : removes) store.itemCounts().remove(key);
            if (store.recordHistory()) {
                totals.forEach((id, total) -> store.history().put(Store.historyKey(profile, id, now), total));
            }
            currencyChanges.forEach((key, c) -> store.currencies().put(key, new Object[]{c.value(), now}));
        });
    }
}
