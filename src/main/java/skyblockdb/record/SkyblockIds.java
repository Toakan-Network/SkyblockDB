package skyblockdb.record;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * SkyBlock IDs in one canonical form, the repo's (NEU) spelling, so every source and the reference data agree:
 * pets "BEE;4" (type;tier index), item variants "INK_SACK-3" (Hypixel writes "INK_SACK:3").
 */
public final class SkyblockIds {
    private static final String PET_ID = "PET";
    /** Pet rarities in the order of the NEU pet ID suffix ("BEE;4" = Legendary Bee). */
    private static final List<String> PET_TIERS = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
    /**
     * ID per stack. Reading it copies the item's whole custom data, and storage holds hundreds of stacks.
     * ItemStack uses identity equality and Hypixel sends a new stack whenever an item's data changes, so a cached
     * ID never goes stale; entries vanish with the stack. Client thread only.
     */
    private static final Map<ItemStack, String> CACHE = new WeakHashMap<>();

    private SkyblockIds() {}

    /** The canonical form of an ID from any source (sacks, repo, item data); empty stays empty. */
    public static String normalize(String id) {
        if (id == null) return "";
        return id.trim().toUpperCase(Locale.ROOT).replace(':', '-');
    }

    /** The stack's canonical SkyBlock ID, or "" for non-SkyBlock items (menu buttons, glass panes). */
    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        String id = CACHE.get(stack);
        if (id == null) {
            id = read(stack);
            CACHE.put(stack, id);
        }
        return id;
    }

    private static String read(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String id = data.getStringOr("id", "");
        if (!PET_ID.equals(id)) return normalize(id);
        try {
            JsonObject info = JsonParser.parseString(data.getStringOr("petInfo", "")).getAsJsonObject();
            int tier = PET_TIERS.indexOf(info.get("tier").getAsString().toUpperCase(Locale.ROOT));
            if (tier >= 0) return normalize(info.get("type").getAsString()) + ";" + tier;
        } catch (RuntimeException e) {
            // Missing or unexpected petInfo: keep the plain "PET" ID.
        }
        return id;
    }
}
