package skyblockdb.api;

import java.time.Instant;

/**
 * One currency value of a profile.
 *
 * @param profile SkyBlock profile UUID
 * @param name    {@code purse}, {@code bank}, {@code coop_bank}, {@code motes}, {@code bits}, {@code copper},
 *                {@code sowdust}, {@code kernels}, {@code north_stars}, {@code gems}, {@code soulflow}
 * @param value   the amount (purse can have decimals)
 * @param seenAt  when it last changed
 */
public record Currency(String profile, String name, double value, Instant seenAt) {
}
