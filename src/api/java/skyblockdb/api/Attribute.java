package skyblockdb.api;

/**
 * A Hunting attribute and the shard that carries it (API_VERSION 4). Recipe inputs and mob drops key attributes by
 * {@link #id()}.
 *
 * @param id        the attribute's ID, as used in recipe inputs ("HONEY_REFILL")
 * @param name      the attribute's display name
 * @param shardId   SkyBlock ID of its shard ("SHARD_QUEEN_BEE"); empty when not given
 * @param shardName the shard's display name ("Queen Bee Shard"); empty when not given
 * @param rarity    the shard's rarity; empty when not given
 */
public record Attribute(String id, String name, String shardId, String shardName, String rarity) {
}
