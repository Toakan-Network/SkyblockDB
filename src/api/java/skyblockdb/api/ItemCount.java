package skyblockdb.api;

import java.time.Instant;

/**
 * One stored stack or sack entry.
 *
 * @param profile   SkyBlock profile UUID
 * @param container where it is: {@code sacks}, {@code inventory}, {@code enderchest:N}, {@code backpack:N},
 *                  {@code rift_storage:N}, {@code accessory_bag}, {@code equipment}, {@code rift_equipment},
 *                  {@code vault}. More may be added later.
 * @param slot      slot within the container (sacks: the SkyBlock ID; accessory bag: {@code page.slot};
 *                  equipment: the slot name)
 * @param sbId      canonical SkyBlock ID (repo spelling: pets {@code BEE;4}, variants {@code INK_SACK-3})
 * @param amount    stack size or sack count
 * @param seenAt    when the source last saw it
 * @param source    where the value came from ({@code skyblockapi})
 * @param confident false when the value is a guess the source isn't sure of
 */
public record ItemCount(String profile, String container, String slot, String sbId, long amount,
                        Instant seenAt, String source, boolean confident) {
}
