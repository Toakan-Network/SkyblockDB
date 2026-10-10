package skyblockdb;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Tells whether the player is on a Hypixel SkyBlock server. Recording is off everywhere else, so lobbies and
 * other servers never change the stored data.
 *
 * <p>The official Hypixel Mod API is a required dependency: its location event decides. If it is somehow
 * missing at runtime, nothing counts as SkyBlock and recording stays off.
 */
public final class SkyblockDetector {
    private static final String MOD_API_ID = "hypixel-mod-api";

    private static volatile boolean onSkyblock = false;

    private SkyblockDetector() {}

    public static void register() {
        if (FabricLoader.getInstance().isModLoaded(MOD_API_ID)) {
            HypixelLocationListener.register();
        } else {
            SkyblockDb.LOGGER.warn("Hypixel Mod API not found; SkyBlock detection unavailable, so recording stays off");
        }
        ClientTickEvents.START_CLIENT_TICK.register(SkyblockDetector::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> set(false, null));
    }

    public static boolean isOnSkyblock() {
        return onSkyblock;
    }

    /** Called by {@link HypixelLocationListener} on every server change. */
    static void onModApiLocation(boolean skyblock, String mode) {
        set(skyblock, skyblock ? mode : null);
    }

    private static void tick(Minecraft client) {
        // The dev client runs in singleplayer, where the Mod API sends nothing.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            onSkyblock = client.level != null;
        }
    }

    private static void set(boolean skyblock, String mode) {
        if (skyblock != onSkyblock) {
            onSkyblock = skyblock;
            SkyblockDb.debug(skyblock ? "Entered SkyBlock{}" : "Left SkyBlock{}", mode != null ? " (" + mode + ")" : "");
        }
    }
}
