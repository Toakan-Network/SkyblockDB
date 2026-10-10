package skyblockdb.api;

import java.time.Instant;

/**
 * What SkyblockDB knows about a SkyBlock item ID.
 *
 * @param sbId      canonical SkyBlock ID (pets {@code BEE;4})
 * @param name      display name without formatting codes ("Enchanted Coal", "Bee (Legendary)")
 * @param rarity    {@code COMMON} ... {@code DIVINE}, {@code SPECIAL}...; empty when unknown
 * @param category  item type from the lore ("ACCESSORY", "DUNGEON HELMET", "PET"); empty when unknown
 * @param origin    {@code repo} (from the item repository) or {@code observed} (seen in game, not in the repository yet)
 * @param retiredAt when the repository dropped it; null while it is current. Retired items stay so old data keeps a name.
 */
public record ReferenceItem(String sbId, String name, String rarity, String category, String origin, Instant retiredAt) {
    public boolean retired() {
        return retiredAt != null;
    }

    public boolean observed() {
        return "observed".equals(origin);
    }
}
