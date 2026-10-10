package skyblockdb.api;

import java.util.List;

/**
 * The elected mayor or the minister (API_VERSION 4).
 *
 * @param id    SkyblockAPI's candidate ID, e.g. {@code COLE}
 * @param name  display name
 * @param perks their perks, each marked active or not
 */
public record Candidate(String id, String name, List<Perk> perks) {
}
