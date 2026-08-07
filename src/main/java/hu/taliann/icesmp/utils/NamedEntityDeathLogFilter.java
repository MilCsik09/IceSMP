package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.managers.ConfigManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/**
 * A vanilla {@code LivingEntity} minden custom-nevű mob halálakor INFO-szinten írja a
 * "Named entity ... died: ..." sort — szintezett moboknál ez elárasztja a konzolt és a
 * latest.logot, és a szint nem állítható át pluginból. Ezért a szűrő a root
 * LoggerConfig-on, még az appenderek ELŐTT dobja el a sort, így sem a terminál, sem a
 * logfájl nem kapja meg. Élő-config kapu: {@code logging.suppress-named-entity-deaths}.
 */
public final class NamedEntityDeathLogFilter extends AbstractFilter {

    private static NamedEntityDeathLogFilter installed;

    private final ConfigManager configManager;

    private NamedEntityDeathLogFilter(final ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** Idempotens: reload/újra-enable nem halmozhat több szűrőt a root LoggerConfig-ra. */
    public static synchronized void install(final ConfigManager configManager) {
        uninstall();
        final NamedEntityDeathLogFilter filter = new NamedEntityDeathLogFilter(configManager);
        filter.start();
        rootLogger().get().addFilter(filter);
        installed = filter;
    }

    public static synchronized void uninstall() {
        final NamedEntityDeathLogFilter current = installed;
        if (current != null) {
            rootLogger().get().removeFilter(current);
            current.stop();
            installed = null;
        }
    }

    private static Logger rootLogger() {
        return (Logger) LogManager.getRootLogger();
    }

    /**
     * A formázatlan ("Named entity {} died: {}") és a formázott alak egyaránt illeszkedik,
     * mert a paraméteres üzenet is a "Named entity" prefixszel és a " died" szóval jön.
     */
    public static boolean suppressible(final String message) {
        return message != null && message.startsWith("Named entity") && message.contains(" died");
    }

    private Result decide(final String message) {
        if (!suppressible(message)) {
            return Result.NEUTRAL;
        }
        return configManager.getBoolean("logging.suppress-named-entity-deaths", true)
                ? Result.DENY : Result.NEUTRAL;
    }

    @Override
    public Result filter(final LogEvent event) {
        if (event == null || event.getMessage() == null) {
            return Result.NEUTRAL;
        }
        return decide(event.getMessage().getFormattedMessage());
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker,
                         final Message message, final Throwable throwable) {
        return decide(message == null ? null : message.getFormattedMessage());
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker,
                         final Object message, final Throwable throwable) {
        return decide(message == null ? null : String.valueOf(message));
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker,
                         final String message, final Object... params) {
        return decide(message);
    }
}
