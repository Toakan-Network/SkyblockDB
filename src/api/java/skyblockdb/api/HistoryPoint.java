package skyblockdb.api;

import java.time.Instant;

/**
 * An item's total on a profile (all containers) right after it changed.
 *
 * @param profile SkyBlock profile UUID
 * @param sbId    canonical SkyBlock ID
 * @param at      when the change was recorded
 * @param total   the new total
 */
public record HistoryPoint(String profile, String sbId, Instant at, long total) {
}
