package skyblockdb.api;

import java.util.List;

/**
 * One loot table of a {@link Mob} (API_VERSION 3).
 *
 * @param name     the variant's name ("Zealot Bruiser")
 * @param mobLevel its level
 * @param xp       experience dropped
 * @param combatXp Combat XP given
 * @param drops    what it can drop
 */
public record LootTable(String name, int mobLevel, int xp, int combatXp, List<MobDrop> drops) {
}
