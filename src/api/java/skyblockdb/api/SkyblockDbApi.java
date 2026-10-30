package skyblockdb.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
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
    /**
     * 1: items, currencies, profiles, history. 2: reference data (items, recipes, changes, observations). 3: mobs.
     * 4: election, attributes, recipe requirements, NPC sell prices, all recipes. 5: location, bazaar and lowest BIN
     * prices. 6: trophy catches, Accessory Bag with recombobulated flags. 7: Forge contents.
     */
    int API_VERSION = 7;

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

    /**
     * How an item's total changed on a profile since {@code since}, oldest first. History is off by default, for
     * debugging only: it records while the player has {@code /skyblockdb history on} or {@code /skyblockdb debug}
     * on. Otherwise this is empty.
     */
    CompletableFuture<List<HistoryPoint>> history(String profile, String sbId, Instant since);

    // Reference data (API_VERSION 2). Empty until the item repository has loaded once (then kept between sessions).

    /** What is known about an ID, current, retired or observed. */
    Optional<ReferenceItem> item(String sbId);

    /** Every known item by SkyBlock ID. */
    Map<String, ReferenceItem> referenceItems();

    /** The recipes that make {@code sbId}, every type. */
    List<Recipe> recipes(String sbId);

    /** The recipes that use {@code sbId} as an input. */
    List<Recipe> usages(String sbId);

    /** A mob by the repository's ID (API_VERSION 3). */
    Optional<Mob> mob(String id);

    /** Every mob by ID (API_VERSION 3). */
    Map<String, Mob> mobs();

    /** Mobs that can drop {@code key} (a SkyBlock ID, or a drop key as in {@link MobDrop#key()}) (API_VERSION 3). */
    List<Mob> droppedBy(String key);

    /** Every recipe, every type (API_VERSION 4). */
    List<Recipe> allRecipes();

    /**
     * What unlocks the recipe of {@code key} (a SkyBlock ID, pet {@code BEE;4} or attribute ID), one line each, e.g.
     * {@code Coal VII} or {@code Heart of the Mountain 2}; empty when nothing is needed or known (API_VERSION 4).
     */
    List<String> requirements(String key);

    /** A Hunting attribute by ID (API_VERSION 4). */
    Optional<Attribute> attribute(String id);

    /** Every Hunting attribute by ID (API_VERSION 4). */
    Map<String, Attribute> attributes();

    /** What an NPC pays for one {@code sbId}, from Hypixel's item list; empty when unknown (API_VERSION 4). */
    OptionalDouble npcSellPrice(String sbId);

    /** Every NPC sell price by SkyBlock ID, including items the repository doesn't list (API_VERSION 4). */
    Map<String, Double> npcSellPrices();

    /** Mayor and minister, live; empty until SkyblockAPI knows them (API_VERSION 4). */
    Optional<Election> election();

    /** A mayor or minister perk that is in effect now, e.g. {@code MOLTEN_FORGE} (API_VERSION 4). */
    Optional<Perk> activePerk(String id);

    /** Where the player is, live; empty while not on SkyBlock or not known yet (API_VERSION 5). */
    Optional<Location> location();

    /**
     * One {@code sbId}'s price from {@code source}; empty when unknown (API_VERSION 5). Bazaar and lowest BIN come from
     * SkyblockAPI's own downloads (refreshed every two hours), so they are empty for a moment after the game starts.
     * Pets have no lowest BIN.
     */
    OptionalDouble price(String sbId, PriceSource source);

    /**
     * Trophy Frog catches of the current profile, one entry per frog, live from SkyblockAPI; empty if it can't be read
     * (API_VERSION 6). Client thread only.
     */
    List<TrophyProgress> trophyFrogs();

    /** Trophy Fish catches of the current profile, as {@link #trophyFrogs} (API_VERSION 6). Client thread only. */
    List<TrophyProgress> trophyFish();

    /**
     * The current profile's Accessory Bag, live from SkyblockAPI, as of the last time its menu was open; empty until
     * then (API_VERSION 6). Client thread only.
     */
    List<AccessoryItem> accessories();

    /**
     * What is in the current profile's Forge, live from SkyblockAPI, as of the last time its menu was open; empty until
     * then or if it can't be read (API_VERSION 7). Client thread only.
     */
    List<ForgeItem> forging();

    /** Reference changes recorded since {@code since} (null: all), oldest first. */
    CompletableFuture<List<ReferenceChange>> referenceChanges(Instant since);

    /** Our own observations; {@code openOnly} leaves out the ones a repository update has confirmed. */
    CompletableFuture<List<Observation>> observations(boolean openOnly);

    void addListener(SkyblockDbListener listener);

    void removeListener(SkyblockDbListener listener);

    /** Holds the installed API; not part of the API. */
    final class Holder {
        private static volatile SkyblockDbApi instance;

        private Holder() {}
    }
}
