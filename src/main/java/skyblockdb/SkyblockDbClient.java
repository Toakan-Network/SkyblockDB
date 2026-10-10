package skyblockdb;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class SkyblockDbClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) SkyblockDb.debugLogging = true;
        SkyblockDetector.register();
        SkyblockDb.LOGGER.info("{} loaded", SkyblockDb.NAME);
    }
}
