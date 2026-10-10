package skyblockdb.api;

import java.util.Map;

/**
 * One way to make an item, from the item repository. Several recipes for one item come in the repository's order;
 * the first is the usual one.
 *
 * @param type        {@code crafting}, {@code forge}, {@code shop} or {@code kat}
 * @param output      canonical SkyBlock ID made
 * @param outputCount how many one run makes
 * @param inputs      ingredient keys to amounts per run. Items and pets use their SkyBlock ID; currencies are
 *                    {@code SKYBLOCK_COIN}, {@code SKYBLOCK_<CURRENCY>}; enchantments, potions and runes are
 *                    {@code ID;level}. Kat recipes include the input pet.
 * @param coins       coin cost kept outside the inputs (forge and kat), else 0
 * @param timeSeconds duration (forge and kat), else 0
 * @param npc         the selling NPC for shop recipes, else empty
 */
public record Recipe(String type, String output, int outputCount, Map<String, Integer> inputs, long coins,
                     int timeSeconds, String npc) {
}
