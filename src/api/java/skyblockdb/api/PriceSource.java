package skyblockdb.api;

/** Which price {@link SkyblockDbApi#price} reads (API_VERSION 5). */
public enum PriceSource {
    /** What an NPC pays, from Hypixel's item list (same as {@link SkyblockDbApi#npcSellPrice}). */
    NPC_SELL,
    /** Hypixel's bazaar {@code quick_status.sellPrice}. */
    BAZAAR_SELL,
    /** Hypixel's bazaar {@code quick_status.buyPrice}. */
    BAZAAR_BUY,
    /** The lowest Buy It Now auction price, from SkyblockAPI's auction summary. */
    LOWEST_BIN
}
