package skyblockdb.api;

import java.time.Instant;

/**
 * A SkyBlock profile the player has played on with SkyblockDB installed.
 *
 * @param profileUuid SkyBlock profile UUID, the key every other record uses
 * @param playerUuid  Minecraft account UUID
 * @param name        profile name ("Apple")
 * @param type        profile type ({@code NORMAL}, {@code IRONMAN}, {@code BINGO}, ...)
 * @param sbLevel     SkyBlock level when last seen
 * @param seenAt      when it was last updated
 */
public record Profile(String profileUuid, String playerUuid, String name, String type, int sbLevel, Instant seenAt) {
}
