package skyblockdb;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import skyblockdb.api.SkyblockDbApi;
import skyblockdb.record.Recorder;
import skyblockdb.reference.ReferenceData;
import skyblockdb.store.Store;

public final class SkyblockDbClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) SkyblockDb.debugLogging = true;
        SkyblockDbApi.install(ApiImpl.INSTANCE);
        SkyblockDetector.register();
        Store.get().open(store -> {
            Recorder.onStoreOpened(store);
            ReferenceData.onStoreOpened(store);
        });
        Recorder.register();
        ReferenceData.register();
        Commands.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Store.get().close());
        SkyblockDb.LOGGER.info("{} loaded", SkyblockDb.NAME);
    }
}
