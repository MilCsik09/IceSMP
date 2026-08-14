package hu.taliann.icesmp.core;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CrateManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Live hooks owned by the second-wave server/world and crate config editors. */
public final class AdvancedConfigRuntimeBridge {

    private AdvancedConfigRuntimeBridge() {
    }

    public static void apply(final JavaPlugin plugin, final ConfigManager configManager,
                             final String key) {
        if (key == null || key.isBlank() || !requiresBridge(key)) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            try {
                final Object core = core(plugin);
                if (key.startsWith("crates-settings.") || key.startsWith("crates.")) {
                    field(core, "crateManager", CrateManager.class).reloadConfig();
                }
                if (key.equals("world-events.check-interval-seconds")) {
                    reschedule(core, "scheduleWorldEvents", "worldEventsTask");
                }
                if (key.equals("settings.disable-locator-bar")) {
                    applyLocatorBar(configManager);
                }
            } catch (final ReflectiveOperationException | RuntimeException failure) {
                plugin.getLogger().severe("A második hullámos config élő alkalmazása sikertelen ("
                        + key + "): " + failure);
            }
        });
    }

    private static boolean requiresBridge(final String key) {
        return key.startsWith("crates-settings.")
                || key.startsWith("crates.")
                || key.equals("world-events.check-interval-seconds")
                || key.equals("settings.disable-locator-bar");
    }

    @SuppressWarnings("unchecked")
    private static void applyLocatorBar(final ConfigManager configManager) {
        final boolean disable = configManager.getBoolean("settings.disable-locator-bar", true);
        try {
            final Method getByName = GameRule.class.getMethod("getByName", String.class);
            final GameRule<?> rawRule = (GameRule<?>) getByName.invoke(null, "locatorBar");
            if (rawRule == null || rawRule.getType() != Boolean.class) {
                return;
            }
            final GameRule<Boolean> rule = (GameRule<Boolean>) rawRule;
            for (final World world : Bukkit.getWorlds()) {
                world.setGameRule(rule, !disable);
            }
        } catch (final ReflectiveOperationException ignored) {
            // A szerververzión nem létező gamerule esetén a módosítás szándékosan no-op.
        }
    }

    private static void reschedule(final Object core, final String method,
                                   final String taskFieldName)
            throws ReflectiveOperationException {
        final Field taskField = core.getClass().getDeclaredField(taskFieldName);
        taskField.setAccessible(true);
        final Object current = taskField.get(core);
        if (current instanceof ScheduledTask task) {
            task.cancel();
        }
        taskField.set(core, null);
        final Method schedule = core.getClass().getDeclaredMethod(method);
        schedule.setAccessible(true);
        schedule.invoke(core);
    }

    private static Object core(final JavaPlugin plugin) throws ReflectiveOperationException {
        final Field coreField = plugin.getClass().getDeclaredField("core");
        coreField.setAccessible(true);
        final Object core = coreField.get(plugin);
        if (core == null) {
            throw new IllegalStateException("IceSMPCore még nincs inicializálva.");
        }
        return core;
    }

    private static <T> T field(final Object owner, final String name, final Class<T> type)
            throws ReflectiveOperationException {
        final Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }
}
