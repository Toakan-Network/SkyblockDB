package skyblockdb.record;

import kotlin.Unit;
import net.minecraft.client.Minecraft;
import skyblockdb.ApiImpl;
import skyblockdb.SkyblockApiSafe;
import skyblockdb.api.Location;
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI;
import tech.thatgravyboat.skyblockapi.api.events.location.AreaChangeEvent;
import tech.thatgravyboat.skyblockapi.api.events.location.IslandChangeEvent;
import tech.thatgravyboat.skyblockapi.api.location.LocationAPI;
import tech.thatgravyboat.skyblockapi.api.location.SkyBlockArea;
import tech.thatgravyboat.skyblockapi.api.location.SkyBlockIsland;

import java.util.Objects;
import java.util.Optional;

/**
 * Island and area, read live from SkyblockAPI's location tracking, so nothing is stored here. Listeners hear about a
 * change through {@code onLocationChanged}.
 */
public final class LocationReader {
    private LocationReader() {}

    public static void register() {
        SkyblockApiSafe.run("location", () -> {
            SkyBlockAPI.getEventBus().register(IslandChangeEvent.class, 0, false, event -> changed());
            SkyBlockAPI.getEventBus().register(AreaChangeEvent.class, 0, false, event -> changed());
        });
    }

    private static Unit changed() {
        Minecraft.getInstance().execute(() -> current().ifPresent(l -> ApiImpl.INSTANCE.fire(x -> x.onLocationChanged(l))));
        return Unit.INSTANCE;
    }

    /** Where the player is, or empty while not on SkyBlock. */
    public static Optional<Location> current() {
        return SkyblockApiSafe.get("location", LocationReader::read, Optional.empty());
    }

    private static Optional<Location> read() {
        LocationAPI api = LocationAPI.INSTANCE;
        if (!api.isOnSkyBlock()) return Optional.empty();
        SkyBlockIsland island = api.getIsland();
        SkyBlockArea area = api.getArea();
        return Optional.of(new Location(island == null ? "" : Objects.toString(island.getId(), ""),
            island == null ? "" : Objects.toString(island.getDisplayName(), ""),
            area == null ? "" : Objects.toString(area.getName(), "").trim()));
    }
}
