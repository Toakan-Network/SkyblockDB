package skyblockdb.api;

/**
 * A mayor or minister perk (API_VERSION 4).
 *
 * @param id          SkyblockAPI's perk ID, e.g. {@code MOLTEN_FORGE}
 * @param name        display name ("Molten Forge")
 * @param description its text, without formatting codes
 * @param active      whether it is in effect now (a minister only has one of its mayor perks)
 */
public record Perk(String id, String name, String description, boolean active) {
}
