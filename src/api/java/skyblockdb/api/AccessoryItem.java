package skyblockdb.api;

/**
 * One accessory in the current profile's Accessory Bag (API_VERSION 6).
 *
 * @param sbId           canonical SkyBlock ID
 * @param slot           {@code page.slot}, as in {@link ItemCount#slot()}
 * @param recombobulated whether a Recombobulator 3000 raised its rarity by one
 */
public record AccessoryItem(String sbId, String slot, boolean recombobulated) {
}
