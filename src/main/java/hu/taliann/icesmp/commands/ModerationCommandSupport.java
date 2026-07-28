package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.moderation.ModerationDuration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Small command-side adapter around the native moderation service; it owns no state. */
final class ModerationCommandSupport {

    record Target(UUID id, String name, Player online) {
    }

    private ModerationCommandSupport() {
    }

    static Optional<Target> resolveTarget(final ModerationManager manager, final String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return Optional.empty();
        }
        final Player online = Bukkit.getPlayerExact(rawName);
        if (online != null) {
            manager.rememberOnlinePlayer(online.getUniqueId(), online.getName());
            return Optional.of(new Target(online.getUniqueId(), online.getName(), online));
        }
        final OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(rawName);
        if (cached != null && cached.getName() != null) {
            return Optional.of(new Target(cached.getUniqueId(), cached.getName(), null));
        }
        return manager.findKnownPlayer(rawName)
                .map(known -> new Target(known.id(), known.name(), Bukkit.getPlayer(known.id())));
    }

    static UUID administratorId(final CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    static void send(final JavaPlugin plugin, final CommandSender sender, final String message) {
        if (sender instanceof Player player) {
            player.getScheduler().run(plugin, task -> player.sendMessage(message), null);
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> sender.sendMessage(message));
        }
    }

    /** Schedules a message by UUID without carrying a foreign Player object across entity regions. */
    static void send(final JavaPlugin plugin, final UUID playerId, final String message) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline()) {
                    player.sendMessage(message);
                }
            }, null);
        }
    }

    static Long parseDurationMillis(final String raw) {
        return ModerationDuration.parseMillis(raw);
    }

    static boolean looksLikeDurationToken(final String raw) {
        return ModerationDuration.looksLikeToken(raw);
    }

    static String durationText(final Long millis) {
        if (millis == null || millis == 0L) {
            return "végleges";
        }
        long seconds = Math.max(1L, millis / 1000L);
        if (seconds % 86_400L == 0L) {
            return (seconds / 86_400L) + " nap";
        }
        if (seconds % 3_600L == 0L) {
            return (seconds / 3_600L) + " óra";
        }
        if (seconds % 60L == 0L) {
            return (seconds / 60L) + " perc";
        }
        return seconds + " másodperc";
    }
}
