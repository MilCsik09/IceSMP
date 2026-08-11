package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.managers.ConfigManager;

import java.util.logging.Logger;

/**
 * Boot-kori leltár-sorok ("Loaded N ...", érvényes-táblák, config-echo) csatornája.
 * Alapból FINE szintre kerülnek, hogy a konzol a tényleges eseményeké maradjon;
 * a {@code logging.verbose-startup} élő-config kulccsal INFO-ra emelhetők.
 */
public final class StartupLog {

    private StartupLog() {
    }

    public static void info(final Logger logger, final ConfigManager configManager,
                            final String message) {
        if (configManager != null && configManager.getBoolean("logging.verbose-startup", false)) {
            logger.info(message);
        } else {
            logger.fine(message);
        }
    }
}
