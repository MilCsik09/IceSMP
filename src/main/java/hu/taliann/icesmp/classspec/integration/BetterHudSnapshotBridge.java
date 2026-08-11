package hu.taliann.icesmp.classspec.integration;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional BetterHud 1.14.1 display-cache adapter.
 *
 * <p>Only immutable IceSMP snapshot strings enter BetterHud's per-player variable map. BetterHud
 * never becomes gameplay authority, and absence/API drift is a logged no-op rather than fatal.</p>
 */
public final class BetterHudSnapshotBridge {

    public static final String HUD_ID = "icesmp_class_hud";

    private final JavaPlugin plugin;
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private volatile Access access;

    public BetterHudSnapshotBridge(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Returns true only when the configured HUD exists and received the immutable projection. */
    public boolean publish(final UUID playerId, final Map<String, String> snapshot) {
        try {
            final Access current = access == null ? (access = resolve()) : access;
            if (current == null) {
                return false;
            }
            final Object hudManager = current.hudManager().invoke(current.betterHud());
            if (current.hud().invoke(hudManager, HUD_ID) == null) return false;
            final Object manager = current.playerManager().invoke(current.betterHud());
            final Object hudPlayer = current.hudPlayer().invoke(manager, playerId);
            if (hudPlayer == null) return false;
            @SuppressWarnings("unchecked")
            final Map<String, String> variables = (Map<String, String>) current.variableMap().invoke(hudPlayer);
            variables.putAll(Map.copyOf(snapshot));
            failureLogged.set(false);
            return true;
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (failureLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning("BetterHud snapshot projection unavailable; native fallback remains safe: "
                        + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            access = null;
            return false;
        }
    }

    /**
     * Emits BetterHud's documented custom popup event after a new proc was captured on the
     * player's Folia region thread. The event is display-only: it carries no gameplay mutation
     * callback and BetterHud remains entirely optional.
     */
    public void showProcPopup(final Player player, final String proc) {
        if (player == null || proc == null || proc.isBlank()) return;
        try {
            final Access current = access == null ? (access = resolve()) : access;
            if (current == null) return;
            final Object customEvent = current.customPopupEvent().newInstance(player, "icesmp_class_proc");
            plugin.getServer().getPluginManager().callEvent((Event) customEvent);
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (failureLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning("BetterHud proc popup unavailable; persistent HUD remains active: "
                        + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            access = null;
        }
    }

    private Access resolve() throws ReflectiveOperationException {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("BetterHud")) {
            return null;
        }
        final Class<?> api = Class.forName("kr.toxicity.hud.api.BetterHud", false,
                plugin.getServer().getPluginManager().getPlugin("BetterHud").getClass().getClassLoader());
        final Object betterHud = api.getMethod("getInstance").invoke(null);
        final Method playerManager = api.getMethod("getPlayerManager");
        final Method hudManager = api.getMethod("getHudManager");
        final Class<?> hudManagerApi = Class.forName("kr.toxicity.hud.api.manager.HudManager", false,
                api.getClassLoader());
        final Method hud = hudManagerApi.getMethod("getHud", String.class);
        final Class<?> playerManagerApi = Class.forName("kr.toxicity.hud.api.manager.PlayerManager", false,
                api.getClassLoader());
        final Method hudPlayer = playerManagerApi.getMethod("getHudPlayer", UUID.class);
        final Method variableMap = Class.forName("kr.toxicity.hud.api.player.HudPlayer", false,
                api.getClassLoader()).getMethod("getVariableMap");
        final Class<?> customPopupEvent = Class.forName(
                "kr.toxicity.hud.api.bukkit.event.CustomPopupEvent", false, api.getClassLoader());
        final Constructor<?> customPopupConstructor = customPopupEvent.getConstructor(Player.class, String.class);
        return new Access(betterHud, playerManager, hudManager, hud, hudPlayer, variableMap,
                customPopupConstructor);
    }

    private record Access(Object betterHud, Method playerManager, Method hudManager, Method hud,
                          Method hudPlayer, Method variableMap, Constructor<?> customPopupEvent) {
    }
}
