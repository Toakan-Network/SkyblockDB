package skyblockdb;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ConcurrentModificationException;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Every call into SkyblockAPI goes through here, grouped by feature ("sacks", "storage", "election", ...).
 *
 * <p>Fabric loads the newest SkyblockAPI that any installed mod bundles. If that one no longer has something this
 * build of SkyblockDB calls (a {@link LinkageError}, e.g. {@link NoSuchMethodError}), the feature is switched off for
 * the session with one warning, and everything else keeps working. Any other error from SkyblockAPI is logged once
 * per feature and that call is skipped. Nothing reaches the game loop.
 */
public final class SkyblockApiSafe {
    private static final Set<String> disabled = ConcurrentHashMap.newKeySet();
    private static final Set<String> warned = ConcurrentHashMap.newKeySet();

    private SkyblockApiSafe() {}

    /** {@code call}'s result, or {@code fallback} when the feature is off or the call failed. */
    public static <T> T get(String feature, Supplier<T> call, T fallback) {
        if (disabled.contains(feature)) return fallback;
        try {
            return call.get();
        } catch (ConcurrentModificationException e) {
            throw e; // SkyblockAPI changed a list while it was copied; the caller retries shortly
        } catch (LinkageError e) {
            if (disabled.add(feature)) {
                SkyblockDb.LOGGER.warn("SkyblockDB: {} switched off: the loaded SkyblockAPI {} doesn't match this SkyblockDB "
                    + "build ({}). Update SkyblockDB.", feature, skyblockApiVersion(), e.toString());
            }
            return fallback;
        } catch (RuntimeException e) {
            if (warned.add(feature)) SkyblockDb.LOGGER.warn("SkyblockDB: reading {} from SkyblockAPI failed; skipped", feature, e);
            return fallback;
        }
    }

    public static void run(String feature, Runnable call) {
        get(feature, () -> {
            call.run();
            return null;
        }, null);
    }

    public static boolean isDisabled(String feature) {
        return disabled.contains(feature);
    }

    /** Features switched off this session, sorted. */
    public static Set<String> disabled() {
        return new TreeSet<>(disabled);
    }

    private static String skyblockApiVersion() {
        return FabricLoader.getInstance().getModContainer("skyblock-api")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("(unknown)");
    }
}
