package skyblockdb.api;

import java.util.List;

/**
 * A mob from the item repository (API_VERSION 3).
 *
 * @param id         the repository's mob ID ("ZEALOT_BRUISER")
 * @param name       display name without formatting codes
 * @param island     where it lives, as the repository names it; empty when not given
 * @param type       the repository's type ("Monster", ...); empty when not given
 * @param lootTables one per variant or level, in repository order
 */
public record Mob(String id, String name, String island, String type, List<LootTable> lootTables) {
}
