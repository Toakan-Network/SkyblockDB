package skyblockdb.record;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import skyblockdb.SkyblockApiSafe;
import skyblockdb.api.AccessoryItem;
import skyblockdb.api.ForgeItem;
import skyblockdb.api.TrophyProgress;
import tech.thatgravyboat.skyblockapi.api.area.atoll.trophyfrog.TrophyFrogAPI;
import tech.thatgravyboat.skyblockapi.api.area.atoll.trophyfrog.TrophyFrogType;
import tech.thatgravyboat.skyblockapi.api.area.isle.trophyfish.TrophyFishType;
import tech.thatgravyboat.skyblockapi.api.area.isle.trophyfish.TrophyFishingAPI;
import tech.thatgravyboat.skyblockapi.api.datatype.defaults.trophy.TrophyTier;
import tech.thatgravyboat.skyblockapi.api.profile.items.accessory.AccessoryBagAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.accessory.AccessoryBagItem;
import tech.thatgravyboat.skyblockapi.api.profile.items.forge.ForgeAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.forge.ForgeSlot;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Trophy catches, the Forge and the Accessory Bag with its recombobulated flags, read live from what SkyblockAPI keeps per
 * profile, so nothing is stored here. Client thread only: item stacks live there.
 */
public final class CollectionReader {
    private static final String ITEM_PREFIX = "item:";

    private CollectionReader() {}

    public static List<TrophyProgress> trophyFrogs() {
        return SkyblockApiSafe.get("trophy_frogs", () -> {
            List<TrophyProgress> out = new ArrayList<>();
            for (TrophyFrogType type : TrophyFrogType.values()) {
                out.add(new TrophyProgress(type.getInternalName(), type.getStrippedName(),
                    tiers(TrophyFrogAPI.INSTANCE.getCaught(type))));
            }
            return TrophyMemory.merged(TrophyMemory.FROGS, out);
        }, List.of());
    }

    public static List<TrophyProgress> trophyFish() {
        return SkyblockApiSafe.get("trophy_fish", () -> {
            List<TrophyProgress> out = new ArrayList<>();
            for (TrophyFishType type : TrophyFishType.values()) {
                out.add(new TrophyProgress(type.getInternalName(), type.getStrippedName(),
                    tiers(TrophyFishingAPI.INSTANCE.getCaughtTiers(type))));
            }
            return TrophyMemory.merged(TrophyMemory.FISH, out);
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

    public static List<ForgeItem> forging() {
        return SkyblockApiSafe.get("forge", () -> {
            List<ForgeItem> out = new ArrayList<>();
            for (Map.Entry<Integer, ForgeSlot> e : new TreeMap<>(ForgeAPI.INSTANCE.getForgeSlots()).entrySet()) {
                String id = forgeId(e.getValue());
                if (!id.isEmpty()) {
                    out.add(new ForgeItem(id, e.getKey(), Instant.ofEpochMilli(e.getValue().getExpiryTime().toEpochMilliseconds())));
                }
            }
            return List.copyOf(out);
        }, List.of());
    }

    /**
     * Kotlin gives the getter a name Java can't write ({@code getSkyBlockId-EnzYQXU}), whose suffix can change, so it is
     * found by its start. It returns the ID as a string such as {@code item:titanium_ring}.
     */
    private static String forgeId(ForgeSlot slot) {
        for (Method method : ForgeSlot.class.getMethods()) {
            if (method.getName().startsWith("getSkyBlockId") && method.getParameterCount() == 0) {
                try {
                    return forgeId(String.valueOf(method.invoke(slot)));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException("ForgeSlot has no getSkyBlockId");
    }

    /** {@code item:titanium_ring} as {@code TITANIUM_RING}; other kinds, such as pets, aren't plain item IDs and are left out. */
    static String forgeId(String raw) {
        return raw.startsWith(ITEM_PREFIX) ? SkyblockIds.normalize(raw.substring(ITEM_PREFIX.length())) : "";
    }

    /** Hypixel marks it in the item's custom data as {@code rarity_upgrades: 1}. */
    private static boolean recombobulated(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("rarity_upgrades", 0) > 0;
    }
}
