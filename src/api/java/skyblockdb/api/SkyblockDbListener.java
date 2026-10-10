package skyblockdb.api;

import java.util.Set;

/**
 * Change notifications. Called on the Minecraft client thread right after SkyblockDB has taken in a change; keep
 * handlers short. The data is already readable through {@link SkyblockDbApi} when they run. Every method has a
 * no-op default, so implement only what you need.
 */
public interface SkyblockDbListener {
    /** Items changed on a profile; {@code sbIds} are the IDs whose rows changed. */
    default void onItemsChanged(String profile, Set<String> sbIds) {}

    /** Currency values changed on a profile; {@code names} as in {@link Currency#name()}. */
    default void onCurrenciesChanged(String profile, Set<String> names) {}

    /** The current profile changed, or a profile's details (name, type, level) did. */
    default void onProfileChanged(String profile) {}
}
