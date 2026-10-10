package skyblockdb.api;

/**
 * One possible drop (API_VERSION 3).
 *
 * @param key       what drops, keyed like recipe inputs: SkyBlock ID, pets {@code BEE;4}, {@code SKYBLOCK_COIN} /
 *                  {@code SKYBLOCK_<CURRENCY>}, enchantments, potions and runes {@code ID;level}, attributes by ID
 * @param kind      {@code item}, {@code pet}, {@code currency}, {@code enchantment}, {@code potion}, {@code rune}
 *                  or {@code attribute}
 * @param chance    chance per kill as the repository gives it: 1 is always, 0.04 is 4%. A few values are above 1.
 * @param minAmount fewest dropped
 * @param maxAmount most dropped
 * @param condition when it drops, if the repository says (e.g. a required item or perk); empty otherwise
 */
public record MobDrop(String key, String kind, double chance, int minAmount, int maxAmount, String condition) {
}
