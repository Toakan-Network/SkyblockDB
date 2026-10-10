package skyblockdb.api;

import java.time.Instant;

/**
 * One change SkyblockDB saw in the reference data: a changelog of what the item repository (and so Hypixel)
 * changed, plus our own observations being confirmed.
 *
 * @param at       when it was recorded
 * @param sbId     the item (for recipes, the output; for mobs, the mob ID)
 * @param kind     {@code item}, {@code recipe:<type>} or {@code mob}
 * @param change   {@code added}, {@code changed}, {@code retired}, {@code restored}, {@code removed} or
 *                 {@code confirmed} (an observed item that the repository now has)
 * @param field    what changed ({@code name}, {@code rarity}, {@code category}, {@code recipes}, {@code drops});
 *                 empty when the whole entry
 * @param oldValue before; empty when added
 * @param newValue after; empty when removed or retired
 * @param repoSha  the repository version (items file SHA) when known, else empty
 */
public record ReferenceChange(Instant at, String sbId, String kind, String change, String field, String oldValue,
                              String newValue, String repoSha) {
}
