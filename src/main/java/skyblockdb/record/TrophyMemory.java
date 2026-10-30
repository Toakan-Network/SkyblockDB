package skyblockdb.record;

import skyblockdb.ApiImpl;
import skyblockdb.api.Profile;
import skyblockdb.api.TrophyProgress;
import skyblockdb.store.Store;
import tech.thatgravyboat.skyblockapi.api.profile.profile.ProfileAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Our own copy of trophy catches. SkyblockAPI 4.2.37 saves frogs and fish to the same file, {@code trophy_fish.json}, so
 * whichever saved last replaces the other, which then reads as empty after a restart. Catches only ever go up, so what
 * is shown is the higher of SkyblockAPI's count and the one remembered here, per trophy and tier.
 */
public final class TrophyMemory {
    public static final String FROGS = "frogs";
    public static final String FISH = "fish";
    /** The order of a row in the {@code trophies} table. */
    private static final List<String> TIERS = List.of("BRONZE", "SILVER", "GOLD", "DIAMOND");
    private static final Map<String, Map<String, Integer>> REMEMBERED = new ConcurrentHashMap<>();

    private TrophyMemory() {}

    /** Writer thread. Merged rather than set, in case a read came first. */
    public static void onStoreOpened(Store store) {
        store.trophies().forEach((key, row) -> REMEMBERED.merge(key, fromRow(row), TrophyMemory::higher));
    }

    /** Client thread. {@code live} with what is remembered for the current profile added, remembering anything new. */
    static List<TrophyProgress> merged(String kind, List<TrophyProgress> live) {
        String profile = profileToUse();
        if (profile == null) return live;
        List<TrophyProgress> out = new ArrayList<>(live.size());
        for (TrophyProgress trophy : live) {
            String key = Store.key(profile, kind, trophy.id());
            Map<String, Integer> known = REMEMBERED.getOrDefault(key, Map.of());
            Map<String, Integer> best = higher(known, trophy.caught());
            if (!best.equals(known)) {
                REMEMBERED.put(key, best);
                Object[] row = toRow(best);
                Store.get().write(store -> store.trophies().put(key, row));
            }
            out.add(new TrophyProgress(trophy.id(), trophy.name(), Map.copyOf(best)));
        }
        return List.copyOf(out);
    }

    /**
     * The current profile, or null while the recorder's profile and SkyblockAPI's disagree, as they can for a moment after
     * a switch: the live counts are then another profile's and mustn't be remembered under this one.
     */
    private static String profileToUse() {
        String current = ApiImpl.INSTANCE.currentProfile().orElse(null);
        if (current == null) return null;
        String name = Objects.toString(ProfileAPI.INSTANCE.getProfileName(), "");
        for (Profile profile : ApiImpl.INSTANCE.profiles()) {
            if (profile.profileUuid().equals(current)) return profile.name().equals(name) ? current : null;
        }
        return null;
    }

    /** The higher count of each tier; tiers with no count in either are left out. */
    static Map<String, Integer> higher(Map<String, Integer> a, Map<String, Integer> b) {
        Map<String, Integer> out = new HashMap<>(a);
        b.forEach((tier, count) -> out.merge(tier, count, Math::max));
        return out;
    }

    static Object[] toRow(Map<String, Integer> caught) {
        Object[] row = new Object[TIERS.size()];
        for (int i = 0; i < row.length; i++) row[i] = caught.getOrDefault(TIERS.get(i), 0);
        return row;
    }

    static Map<String, Integer> fromRow(Object[] row) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < TIERS.size() && i < row.length; i++) out.put(TIERS.get(i), ((Number) row[i]).intValue());
        return out;
    }
}
