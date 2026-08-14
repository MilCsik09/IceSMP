package hu.taliann.icesmp.core;

import hu.taliann.icesmp.listeners.ElytraRelicListener;
import hu.taliann.icesmp.listeners.FactionFoodListener;
import hu.taliann.icesmp.listeners.MetelytepoRelicListener;
import hu.taliann.icesmp.listeners.RelicCraftSafetyListener;
import hu.taliann.icesmp.listeners.RelicInactivityListener;
import hu.taliann.icesmp.listeners.RelicItemRefreshListener;
import hu.taliann.icesmp.listeners.RelicPvpTransferListener;
import hu.taliann.icesmp.listeners.RelicTriggerListener;
import hu.taliann.icesmp.managers.CityGuardManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CorruptionManager;
import hu.taliann.icesmp.managers.DarkUndeadAmbienceManager;
import hu.taliann.icesmp.managers.DevItemManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.HudManager;
import hu.taliann.icesmp.managers.InvasionManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.TablistManager;
import hu.taliann.icesmp.managers.VanishManager;
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
 * Narrow live-apply bridge for config values whose consumers intentionally cache parsed data,
 * own a fixed-period scheduler, or keep a next-run timestamp. Most gameplay systems read
 * ConfigManager at use time and never enter this class.
 */
public final class ConfigRuntimeReloadBridge {

    private static final String HUD_OBJECTIVE = "icesmp_hud";

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
                    reschedule(core, "scheduleTaxCollection", "taxTask");
                }
                if (key.startsWith("dev-items.csodalatos_bingulus.")) {
                    field(core, "devItemManager", DevItemManager.class).refreshOnlineOwner();
                }
                if (key.equals("corruption.enabled")
                        || key.equals("corruption.interval-minutes")) {
                    final CorruptionManager corruption =
                            field(core, "corruptionManager", CorruptionManager.class);
                    resetLongField(corruption, "nextAttemptAt");
                    corruption.tick();
                }
                if (key.equals("dark-undead.enabled")
                        || key.equals("dark-undead.spawn-interval-seconds")) {
                    final DarkUndeadAmbienceManager darkUndead = field(core,
                            "darkUndeadAmbienceManager", DarkUndeadAmbienceManager.class);
                    resetLongField(darkUndead, "nextSpawnAt");
                    darkUndead.tick();
                }
                if (key.equals("city-guards.enabled")
                        || key.equals("city-guards.step-seconds")) {
                    final CityGuardManager guards =
                            field(core, "cityGuardManager", CityGuardManager.class);
                    if (!configManager.getBoolean("city-guards.enabled", true)) {
                        guards.shutdown();
                    } else {
                        resetLongField(guards, "nextTickAt");
                        guards.tick();
                    }
                }
                if (key.equals("factions.food-duty.enabled")
                        || key.equals("factions.food-duty.check-minutes")) {
                    final FactionFoodListener food =
                            field(core, "factionFoodListener", FactionFoodListener.class);
                    resetLongField(food, "nextCheckAt");
                    food.tick();
                }
                if (key.equals("factions.whisper.decay-minutes")) {
                    resetLongField(field(core, "whisperManager", Object.class), "nextDecayAt");
                }

                // Operational menu: fixed-period drivers are cancelled and recreated from the
                // freshly published config generation. All other values are read by their consumer
                // on the next already-scheduled tick/event.
                if (key.equals("hud.refresh-ticks")
                        || key.equals("tablist.refresh-ticks")) {
                    reschedule(core, "scheduleHud", "hudTask", "tablistTask");
                }
                if (key.startsWith("hud.") && !key.equals("hud.refresh-ticks")) {
                    refreshHudOutput(plugin, core, configManager);
                }
                if (key.startsWith("tablist.") && !key.equals("tablist.refresh-ticks")) {
                    refreshTablistOutput(plugin, core);
                }
                if (key.equals("pets.companion.tick-ticks")) {
                    reschedule(core, "schedulePetCombat", "petTask");
                }
                if (key.equals("currency.economy-event.check-interval-minutes")) {
                    reschedule(core, "scheduleEconomyEvents", "economyEventTask");
                }
                if (key.startsWith("moderation.")) {
                    field(core, "moderationManager", ModerationManager.class)
                            .reloadConfiguration();
                    if (key.startsWith("moderation.vanish.")) {
                        field(core, "vanishManager", VanishManager.class).refreshAll();
                    }
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
                || key.startsWith("dev-items.csodalatos_bingulus.")
                || key.equals("factions.tax.enabled")
                || key.equals("factions.tax.interval-minutes")
                || key.equals("corruption.enabled")
                || key.equals("corruption.interval-minutes")
                || key.equals("dark-undead.enabled")
                || key.equals("dark-undead.spawn-interval-seconds")
                || key.equals("city-guards.enabled")
                || key.equals("city-guards.step-seconds")
                || key.equals("factions.food-duty.enabled")
                || key.equals("factions.food-duty.check-minutes")
                || key.equals("factions.whisper.decay-minutes")
                || key.startsWith("hud.")
                || key.startsWith("tablist.")
                || key.equals("pets.companion.tick-ticks")
                || key.equals("currency.economy-event.check-interval-minutes")
                || key.startsWith("moderation.");
    }

    private static void refreshHudOutput(final JavaPlugin plugin, final Object core,
                                         final ConfigManager configManager)
            throws ReflectiveOperationException {
        final HudManager hud = field(core, "hudManager", HudManager.class);
        final boolean enabled = configManager.getBoolean("hud.enabled", true);
        final boolean nativeTablist = configManager.getBoolean("tablist.enabled", true);
        final boolean fallbackTabName = configManager.getBoolean("hud.tablist-enabled", true);

        for (final org.bukkit.entity.Player player
                : List.copyOf(org.bukkit.Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> {
                if (!enabled) {
                    hud.cleanup(player);
                    removeHudSidebar(player);
                    if (!nativeTablist) {
                        player.playerListName(null);
                    }
                    return;
                }
                if (!fallbackTabName && !nativeTablist) {
                    player.playerListName(null);
                }
                hud.init(player);
                hud.update(player);
            }, null);
        }
        if (enabled) {
            hud.tick();
        }
    }

    private static void removeHudSidebar(final org.bukkit.entity.Player player) {
        final org.bukkit.scoreboard.Scoreboard board = player.getScoreboard();
        final org.bukkit.scoreboard.Objective objective = board.getObjective(HUD_OBJECTIVE);
        if (objective != null) {
            objective.unregister();
        }
        for (int index = 0; index < 15; index++) {
            final org.bukkit.scoreboard.Team team = board.getTeam("hud_" + index);
            if (team != null) {
                team.unregister();
            }
        }
    }

    private static void refreshTablistOutput(final JavaPlugin plugin, final Object core)
            throws ReflectiveOperationException {
        final TablistManager tablist = field(core, "tablistManager", TablistManager.class);
        for (final org.bukkit.entity.Player player
                : List.copyOf(org.bukkit.Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> tablist.cleanup(player), null);
        }
        // Cleanup and rebuild must not race on the same entity scheduler. One global tick leaves
        // enough time for each player's queued cleanup to retire before the normal publish phase.
        plugin.getServer().getGlobalRegionScheduler().runDelayed(
                plugin, task -> tablist.tick(), 1L);
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

    /** Cancels the old task fields first, then invokes the private core scheduler exactly once. */
    private static void reschedule(final Object core, final String scheduleMethod,
                                   final String... taskFields)
            throws ReflectiveOperationException {
        for (final String taskFieldName : taskFields) {
            final Field taskField = core.getClass().getDeclaredField(taskFieldName);
            taskField.setAccessible(true);
            final Object current = taskField.get(core);
            if (current instanceof ScheduledTask scheduledTask) {
                scheduledTask.cancel();
            }
            taskField.set(core, null);
        }
        final Method schedule = core.getClass().getDeclaredMethod(scheduleMethod);
        schedule.setAccessible(true);
        schedule.invoke(core);
    }

    private static void resetLongField(final Object owner, final String name)
            throws ReflectiveOperationException {
        final Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(owner, 0L);
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
