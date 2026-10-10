package skyblockdb.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Read access to what SkyblockDB has recorded. Get it with {@link #get()}.
 *
 * <p>Methods without a {@code profile} argument use the current profile and return empty results while it is not
 * known (not on SkyBlock yet). Data from other profiles and past sessions is available too. Current-state reads
 * come from memory and are safe from any thread; {@link #history} reads the store and completes off-thread.
 *
 * <p>Compatibility: {@link #API_VERSION} goes up when something is added. Nothing is removed or changed without
 * a major version of SkyblockDB.
 */
public interface SkyblockDbApi {
    int API_VERSION = 1;

    /** The API, once SkyblockDB has started (from client initialisation on). Empty if it isn't installed. */
    static Optional<SkyblockDbApi> get() {
        return Optional.ofNullable(Holder.instance);
    }

    /** Called once by SkyblockDB itself. */
    static void install(SkyblockDbApi api) {
        if (Holder.instance != null) throw new IllegalStateException("SkyblockDB API already installed");
        Holder.instance = api;
    }

    /** The current profile's UUID, while on SkyBlock and known. */
    Optional<String> currentProfile();

    /** Every profile recorded so far. */
    List<Profile> profiles();

    /** Total of an item across every container of the current profile (0 if none or unknown). */
    long count(String sbId);

    long count(String profile, String sbId);

    /** Where an item is on the current profile, one entry per slot. */
    List<ItemCount> counts(String sbId);

    List<ItemCount> counts(String profile, String sbId);

    /** Everything in one container of the current profile ({@code "sacks"}, {@code "backpack:3"} ...). */
    List<ItemCount> container(String container);

    List<ItemCount> container(String profile, String container);

    /** Every item row of the current profile. */
    List<ItemCount> items();

    List<ItemCount> items(String profile);

    /** Totals per SkyBlock ID across all containers of the current profile. */
    Map<String, Long> totals();

    Map<String, Long> totals(String profile);

    /** Currency values of the current profile, by name. */
    Map<String, Currency> currencies();

    Map<String, Currency> currencies(String profile);

    /** How an item's total changed on a profile since {@code since}, oldest first. */
    CompletableFuture<List<HistoryPoint>> history(String profile, String sbId, Instant since);

    void addListener(SkyblockDbListener listener);

    void removeListener(SkyblockDbListener listener);

    /** Holds the installed API; not part of the API. */
    final class Holder {
        private static volatile SkyblockDbApi instance;

        private Holder() {}
    }
}
