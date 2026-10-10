package skyblockdb.api;

/**
 * Where the player is on SkyBlock (API_VERSION 5), read live from SkyblockAPI.
 *
 * @param island     SkyblockAPI's island ID, e.g. {@code mining_3} (Dwarven Mines) or {@code mining_1}; empty when not known
 * @param islandName the island's display name, e.g. {@code Gold Mine}; empty when not known
 * @param area       the scoreboard area, e.g. {@code Royal Mines}; empty when not known
 */
public record Location(String island, String islandName, String area) {
}
