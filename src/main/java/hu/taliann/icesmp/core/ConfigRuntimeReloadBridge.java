package hu.taliann.icesmp.core;

import hu.taliann.icesmp.listeners.ElytraRelicListener;
import hu.taliann.icesmp.listeners.MetelytepoRelicListener;
import hu.taliann.icesmp.listeners.RelicCraftSafetyListener;
import hu.taliann.icesmp.listeners.RelicInactivityListener;
import hu.taliann.icesmp.listeners.RelicItemRefreshListener;
import hu.taliann.icesmp.listeners.RelicPvpTransferListener;
import hu.taliann.icesmp.listeners.RelicTriggerListener;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.InvasionManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Narrow live-apply bridge for config values whose consumers intentionally cache parsed data or
 * own a fixed-period scheduler. Most gameplay systems read ConfigManager at use time and never
 * enter this class. The bridge runs on the global region scheduler and fails loudly rather than
 * silently claiming that a menu change is live.
 */
public final class ConfigRuntimeReloadBridge {

    private ConfigRuntimeReloadBridge() {
    }

    public static void apply(final JavaPlugin plugin, final ConfigManager configManager,
                             final String key) {
        if (key == null || key.isBlank() || !requiresBridge(key)) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            try {
                final Object core = core(plugin);
                if (key.startsWith("relics.")) {
                    reloadRelics(plugin, core, configManager);
                }
                if (key.startsWith("mob-scaling.")) {
                    field(core, "mobScalingManager", MobScalingManager.class).load();
                }
                if (key.equals("factions.tax.enabled")
                        || key.equals("factions.tax.interval-minutes")) {
                    rescheduleTaxes(core);
                }
            } catch (final ReflectiveOperationException | RuntimeException failure) {
                plugin.getLogger().severe("A config élő alkalmazása sikertelen (" + key
                        + "): " + failure);
            }
        });
    }

    private static boolean requiresBridge(final String key) {
        return key.startsWith("relics.")
                || key.startsWith("mob-scaling.")
                || key.equals("factions.tax.enabled")
                || key.equals("factions.tax.interval-minutes");
    }

    private static void reloadRelics(final JavaPlugin plugin, final Object core,
                                     final ConfigManager configManager)
            throws ReflectiveOperationException {
        final RelicManager relicManager = field(core, "relicManager", RelicManager.class);
        relicManager.load();

        final List<Listener> registered = registeredRelicListeners(plugin);
        if (!relicManager.isEnabled()) {
            for (final Listener listener : registered) {
                HandlerList.unregisterAll(listener);
            }
            if (!registered.isEmpty()) {
                plugin.getLogger().info("Relikvia-listenerek élőben leállítva a config menüből.");
            }
            return;
        }
        if (!registered.isEmpty()) {
            return;
        }

        // If the server booted with relics.enabled=false, the original core intentionally skipped
        // these listeners. Enabling from the menu must therefore register the same set once.
        final MetelytepoManager metelytepoManager =
                field(core, "metelytepoManager", MetelytepoManager.class);
        final SinManager sinManager = field(core, "sinManager", SinManager.class);
        final WorldBossManager worldBossManager =
                field(core, "worldBossManager", WorldBossManager.class);
        final InvasionManager invasionManager =
                field(core, "invasionManager", InvasionManager.class);
        final MessageManager messageManager =
                field(core, "messageManager", MessageManager.class);
        final FactionManager factionManager =
                field(core, "factionManager", FactionManager.class);

        final var manager = plugin.getServer().getPluginManager();
        manager.registerEvents(new RelicCraftSafetyListener(relicManager), plugin);
        manager.registerEvents(new RelicInactivityListener(relicManager), plugin);
        manager.registerEvents(new RelicItemRefreshListener(relicManager), plugin);
        manager.registerEvents(new RelicTriggerListener(relicManager), plugin);
        manager.registerEvents(new MetelytepoRelicListener(plugin, metelytepoManager,
                sinManager, worldBossManager, invasionManager, messageManager), plugin);
        manager.registerEvents(new ElytraRelicListener(plugin, relicManager, factionManager,
                configManager, messageManager), plugin);
        manager.registerEvents(new RelicPvpTransferListener(plugin, relicManager,
                configManager, messageManager), plugin);
        plugin.getLogger().info("Relikvia-listenerek élőben regisztrálva a config menüből.");
    }

    private static List<Listener> registeredRelicListeners(final JavaPlugin plugin) {
        final List<Listener> listeners = new ArrayList<>();
        HandlerList.getRegisteredListeners(plugin).forEach(registered -> {
            final Listener listener = registered.getListener();
            if (isRelicListener(listener) && !listeners.contains(listener)) {
                listeners.add(listener);
            }
        });
        return listeners;
    }

    private static boolean isRelicListener(final Listener listener) {
        return listener instanceof RelicCraftSafetyListener
                || listener instanceof RelicInactivityListener
                || listener instanceof RelicItemRefreshListener
                || listener instanceof RelicTriggerListener
                || listener instanceof MetelytepoRelicListener
                || listener instanceof ElytraRelicListener
                || listener instanceof RelicPvpTransferListener;
    }

    private static void rescheduleTaxes(final Object core)
            throws ReflectiveOperationException {
        final Field taskField = core.getClass().getDeclaredField("taxTask");
        taskField.setAccessible(true);
        final Object current = taskField.get(core);
        if (current instanceof ScheduledTask scheduledTask) {
            scheduledTask.cancel();
        }
        taskField.set(core, null);

        final Method schedule = core.getClass().getDeclaredMethod("scheduleTaxCollection");
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
