package hu.taliann.icesmp.utils;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe game-mode mirror used by cross-region reward filters. */
public final class GameModeCache {

    private static final Map<UUID, GameMode> MODES = new ConcurrentHashMap<>();

    private GameModeCache() {
    }

    public static void update(final UUID playerId, final GameMode mode) {
        if (playerId != null && mode != null) {
            MODES.put(playerId, mode);
        }
    }

    public static void update(final Player player) {
        if (player != null) {
            update(player.getUniqueId(), player.getGameMode());
        }
    }

    public static void remove(final UUID playerId) {
        if (playerId != null) {
            MODES.remove(playerId);
        }
    }

    public static boolean isKnown(final UUID playerId) {
        return playerId != null && MODES.containsKey(playerId);
    }

    /** Missing cache data is fail-closed for economic/progression rewards. */
    public static boolean isSurvival(final UUID playerId) {
        return playerId != null && MODES.get(playerId) == GameMode.SURVIVAL;
    }

    /** Refreshes an unknown entry on the player's own scheduler; the current reward stays denied. */
    public static void requestRefresh(final UUID playerId) {
        if (playerId == null || MODES.containsKey(playerId)) {
            return;
        }
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        final JavaPlugin plugin = JavaPlugin.getProvidingPlugin(GameModeCache.class);
        player.getScheduler().run(plugin, task -> update(player), null);
    }
}
