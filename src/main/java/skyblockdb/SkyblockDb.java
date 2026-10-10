package skyblockdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkyblockDb {
    public static final String MOD_ID = "skyblock-db";
    public static final String NAME = "SkyblockDB";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** Diagnostic logging. Off: only warnings and errors reach latest.log. */
    public static volatile boolean debugLogging = false;

    private SkyblockDb() {}

    /** Logs a diagnostic line only while debug logging is on. */
    public static void debug(String message, Object... args) {
        if (debugLogging) LOGGER.info(message, args);
    }
}
