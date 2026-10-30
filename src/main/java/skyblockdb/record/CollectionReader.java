package skyblockdb.record;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import skyblockdb.SkyblockApiSafe;
import skyblockdb.api.AccessoryItem;
import skyblockdb.api.TrophyProgress;
import tech.thatgravyboat.skyblockapi.api.area.atoll.trophyfrog.TrophyFrogAPI;
import tech.thatgravyboat.skyblockapi.api.area.atoll.trophyfrog.TrophyFrogType;
import tech.thatgravyboat.skyblockapi.api.area.isle.trophyfish.TrophyFishType;
import tech.thatgravyboat.skyblockapi.api.area.isle.trophyfish.TrophyFishingAPI;
import tech.thatgravyboat.skyblockapi.api.datatype.defaults.trophy.TrophyTier;
import tech.thatgravyboat.skyblockapi.api.profile.items.accessory.AccessoryBagAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.accessory.AccessoryBagItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trophy catches and the Accessory Bag with its recombobulated flags, read live from what SkyblockAPI keeps per
 * profile, so nothing is stored here. Client thread only: item stacks live there.
 */
public final class CollectionReader {
    private CollectionReader() {}

    public static List<TrophyProgress> trophyFrogs() {
        return SkyblockApiSafe.get("trophy_frogs", () -> {
            List<TrophyProgress> out = new ArrayList<>();
            for (TrophyFrogType type : TrophyFrogType.values()) {
                out.add(new TrophyProgress(type.getInternalName(), type.getStrippedName(),
                    tiers(TrophyFrogAPI.INSTANCE.getCaught(type))));
            }
            return List.copyOf(out);
        }, List.of());
    }

    public static List<TrophyProgress> trophyFish() {
        return SkyblockApiSafe.get("trophy_fish", () -> {
            List<TrophyProgress> out = new ArrayList<>();
            for (TrophyFishType type : TrophyFishType.values()) {
                out.add(new TrophyProgress(type.getInternalName(), type.getStrippedName(),
                    tiers(TrophyFishingAPI.INSTANCE.getCaughtTiers(type))));
            }
            return List.copyOf(out);
        }, List.of());
    }

    private static Map<String, Integer> tiers(Map<TrophyTier, Integer> caught) {
        Map<String, Integer> out = new LinkedHashMap<>();
        caught.forEach((tier, count) -> {
            if (tier != TrophyTier.NONE) out.put(tier.name(), count);
        });
        return Map.copyOf(out);
    }

    public static List<AccessoryItem> accessories() {
        return SkyblockApiSafe.get("accessory_bag", () -> {
            List<AccessoryItem> out = new ArrayList<>();
            for (AccessoryBagItem item : new ArrayList<>(AccessoryBagAPI.INSTANCE.getItems())) {
                String id = SkyblockIds.of(item.getItem());
                if (!id.isEmpty()) out.add(new AccessoryItem(id, item.getPage() + "." + item.getSlot(), recombobulated(item.getItem())));
            }
            return List.copyOf(out);
        }, List.of());
    }

    /** Hypixel marks it in the item's custom data as {@code rarity_upgrades: 1}. */
    private static boolean recombobulated(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("rarity_upgrades", 0) > 0;
    }
}
