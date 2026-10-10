package skyblockdb;

import skyblockdb.api.Currency;
import skyblockdb.api.HistoryPoint;
import skyblockdb.api.ItemCount;
import skyblockdb.api.Mob;
import skyblockdb.api.Observation;
import skyblockdb.api.Profile;
import skyblockdb.api.Recipe;
import skyblockdb.api.ReferenceChange;
import skyblockdb.api.ReferenceItem;
import skyblockdb.api.SkyblockDbApi;
import skyblockdb.api.SkyblockDbListener;
import skyblockdb.reference.ReferenceData;
import skyblockdb.store.Store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The public API. Reads come from immutable per-profile views that the recorder replaces after every change, so any
 * thread can read without locks; history goes to the store's writer thread.
 */
public final class ApiImpl implements SkyblockDbApi {
    public static final ApiImpl INSTANCE = new ApiImpl();

    /** Everything readable for one profile, built once per change. */
    private record View(List<ItemCount> items, Map<String, List<ItemCount>> byId,
                        Map<String, List<ItemCount>> byContainer, Map<String, Long> totals) {
        static final View EMPTY = new View(List.of(), Map.of(), Map.of(), Map.of());
    }

    private final Map<String, View> views = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Currency>> currencies = new ConcurrentHashMap<>();
    private final Map<String, Profile> profiles = new ConcurrentHashMap<>();
    private volatile String current;
    private final List<SkyblockDbListener> listeners = new CopyOnWriteArrayList<>();

    private ApiImpl() {}

    // Publishing (recorder, client thread).

    /** Replaces a profile's item view with {@code rows}. */
    public void publishItems(String profile, Collection<ItemCount> rows) {
        List<ItemCount> items = List.copyOf(rows);
        Map<String, List<ItemCount>> byId = new HashMap<>();
        Map<String, List<ItemCount>> byContainer = new HashMap<>();
        Map<String, Long> totals = new LinkedHashMap<>();
        for (ItemCount row : items) {
            byId.computeIfAbsent(row.sbId(), k -> new ArrayList<>()).add(row);
            byContainer.computeIfAbsent(row.container(), k -> new ArrayList<>()).add(row);
            totals.merge(row.sbId(), row.amount(), Long::sum);
        }
        byId.replaceAll((k, v) -> List.copyOf(v));
        byContainer.replaceAll((k, v) -> List.copyOf(v));
        views.put(profile, new View(items, Map.copyOf(byId), Map.copyOf(byContainer), Map.copyOf(totals)));
    }

    public void publishCurrencies(String profile, Map<String, Currency> values) {
        currencies.put(profile, Map.copyOf(values));
    }

    public void publishProfile(Profile profile) {
        profiles.put(profile.profileUuid(), profile);
    }

    public void setCurrent(String profile) {
        current = profile;
    }

    public void fire(Consumer<SkyblockDbListener> call) {
        for (SkyblockDbListener listener : listeners) {
            try {
                call.accept(listener);
            } catch (RuntimeException e) {
                SkyblockDb.LOGGER.warn("A SkyblockDB listener failed: {}", listener.getClass().getName(), e);
            }
        }
    }

    // SkyblockDbApi.

    private View view(String profile) {
        return profile == null ? View.EMPTY : views.getOrDefault(profile, View.EMPTY);
    }

    @Override
    public Optional<String> currentProfile() {
        return Optional.ofNullable(current);
    }

    @Override
    public List<Profile> profiles() {
        return List.copyOf(profiles.values());
    }

    @Override
    public long count(String sbId) {
        return count(current, sbId);
    }

    @Override
    public long count(String profile, String sbId) {
        return view(profile).totals().getOrDefault(sbId, 0L);
    }

    @Override
    public List<ItemCount> counts(String sbId) {
        return counts(current, sbId);
    }

    @Override
    public List<ItemCount> counts(String profile, String sbId) {
        return view(profile).byId().getOrDefault(sbId, List.of());
    }

    @Override
    public List<ItemCount> container(String container) {
        return container(current, container);
    }

    @Override
    public List<ItemCount> container(String profile, String container) {
        return view(profile).byContainer().getOrDefault(container, List.of());
    }

    @Override
    public List<ItemCount> items() {
        return items(current);
    }

    @Override
    public List<ItemCount> items(String profile) {
        return view(profile).items();
    }

    @Override
    public Map<String, Long> totals() {
        return totals(current);
    }

    @Override
    public Map<String, Long> totals(String profile) {
        return view(profile).totals();
    }

    @Override
    public Map<String, Currency> currencies() {
        return currencies(current);
    }

    @Override
    public Map<String, Currency> currencies(String profile) {
        return profile == null ? Map.of() : currencies.getOrDefault(profile, Map.of());
    }

    @Override
    public CompletableFuture<List<HistoryPoint>> history(String profile, String sbId, Instant since) {
        long from = since == null ? 0 : since.toEpochMilli();
        return Store.get().query(store -> {
            List<HistoryPoint> points = new ArrayList<>();
            for (Map.Entry<String, Long> e : Store.withPrefix(store.history(), Store.key(profile, sbId))) {
                String key = e.getKey();
                long at = Long.parseLong(key.substring(key.lastIndexOf(Store.SEP) + 1));
                if (at >= from) points.add(new HistoryPoint(profile, sbId, Instant.ofEpochMilli(at), e.getValue()));
            }
            return List.copyOf(points);
        });
    }

    @Override
    public Optional<ReferenceItem> item(String sbId) {
        return Optional.ofNullable(ReferenceData.item(sbId));
    }

    @Override
    public Map<String, ReferenceItem> referenceItems() {
        return ReferenceData.items();
    }

    @Override
    public List<Recipe> recipes(String sbId) {
        return ReferenceData.recipes(sbId);
    }

    @Override
    public List<Recipe> usages(String sbId) {
        return ReferenceData.usages(sbId);
    }

    @Override
    public Optional<Mob> mob(String id) {
        return Optional.ofNullable(ReferenceData.mob(id));
    }

    @Override
    public Map<String, Mob> mobs() {
        return ReferenceData.mobs();
    }

    @Override
    public List<Mob> droppedBy(String key) {
        return ReferenceData.droppedBy(key);
    }

    @Override
    public CompletableFuture<List<ReferenceChange>> referenceChanges(Instant since) {
        String from = since == null ? "" : String.format("%013d", since.toEpochMilli());
        return Store.get().query(store -> {
            List<ReferenceChange> out = new ArrayList<>();
            for (Map.Entry<String, Object[]> e : store.referenceChanges().tailMap(from, true).entrySet()) {
                String key = e.getKey();
                Object[] v = e.getValue();
                long at = Long.parseLong(key.substring(0, key.indexOf(Store.SEP)));
                out.add(new ReferenceChange(Instant.ofEpochMilli(at), (String) v[0], (String) v[1], (String) v[2],
                    (String) v[3], (String) v[4], (String) v[5], (String) v[6]));
            }
            return List.copyOf(out);
        });
    }

    @Override
    public CompletableFuture<List<Observation>> observations(boolean openOnly) {
        return Store.get().query(store -> {
            List<Observation> out = new ArrayList<>();
            store.observations().forEach((key, v) -> {
                int sep = key.lastIndexOf(Store.SEP);
                long resolved = (Long) v[4];
                if (openOnly && resolved != 0L) return;
                out.add(new Observation(key.substring(0, sep), key.substring(sep + 1), (String) v[0],
                    Instant.ofEpochMilli((Long) v[1]), Instant.ofEpochMilli((Long) v[2]), ((Long) v[3]).intValue(),
                    resolved == 0L ? null : Instant.ofEpochMilli(resolved)));
            });
            return List.copyOf(out);
        });
    }

    @Override
    public void addListener(SkyblockDbListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(SkyblockDbListener listener) {
        listeners.remove(listener);
    }

    /** Converts a stored row (key profile|container|slot). */
    public static ItemCount itemCount(String key, String sbId, long amount, long seenAt, String source, boolean confident) {
        int first = key.indexOf(Store.SEP);
        int second = key.indexOf(Store.SEP, first + 1);
        return new ItemCount(key.substring(0, first), key.substring(first + 1, second), key.substring(second + 1),
            sbId, amount, Instant.ofEpochMilli(seenAt), source, confident);
    }

    /** Names from a set of profile|name currency keys. */
    public static Set<String> currencyNames(Collection<String> keys) {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (String key : keys) names.add(key.substring(key.indexOf(Store.SEP) + 1));
        return names;
    }
}
