package skyblockdb.api;

import java.time.Instant;

/**
 * Something seen in game that the item repository doesn't have (yet). Kept apart from repository data.
 *
 * @param sbId       the SkyBlock ID seen
 * @param kind       {@code unknown_id} (the ID is not in the repository)
 * @param seenValue  what the game showed (the item's display name)
 * @param firstSeen  first time seen
 * @param lastSeen   last time seen (updated at most once per game session)
 * @param sessions   how many game sessions it was seen in
 * @param resolvedAt when a repository update confirmed it; null while open
 */
public record Observation(String sbId, String kind, String seenValue, Instant firstSeen, Instant lastSeen,
                          int sessions, Instant resolvedAt) {
    public boolean open() {
        return resolvedAt == null;
    }
}
