package skyblockdb.api;

import java.util.Map;

/**
 * How many of one Trophy Frog or Trophy Fish the current profile has caught (API_VERSION 6).
 *
 * @param id     SkyblockAPI's internal name ({@code COMMON_FROG})
 * @param name   display name without formatting codes
 * @param caught catches by tier: {@code BRONZE}, {@code SILVER}, {@code GOLD}, {@code DIAMOND}. Tiers never caught may
 *               be missing.
 */
public record TrophyProgress(String id, String name, Map<String, Integer> caught) {
    public int caught(String tier) {
        return caught.getOrDefault(tier, 0);
    }
}
