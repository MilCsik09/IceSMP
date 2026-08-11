package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.EventSpawnGuard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/** Lightweight extension for /events debug without duplicating the main EventsCommand. */
public final class EventSpawnDebugListener implements Listener {
    private static final String PERMISSION = "icesmp.admin.events";
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final EventSpawnGuard guard;

    public EventSpawnDebugListener(final JavaPlugin plugin, final EventSpawnGuard guard) {
        this.plugin = plugin;
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(final PlayerCommandPreprocessEvent event) {
        final String raw = event.getMessage().trim();
        final String[] parts = raw.split("\\s+");
        if (parts.length < 2
                || !("/events".equalsIgnoreCase(parts[0])
                || "/event".equalsIgnoreCase(parts[0]))
                || !"debug".equalsIgnoreCase(parts[1])) {
            return;
        }
        event.setCancelled(true);
        final Player player = event.getPlayer();
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Component.text("Nincs jogosultságod ehhez a parancshoz."));
            return;
        }
        final String eventKey;
        if (parts.length >= 4 && "spawn".equalsIgnoreCase(parts[2])) {
            eventKey = parts[3];
        } else if (parts.length >= 3) {
            eventKey = parts[2];
        } else {
            player.sendMessage(LEGACY.deserialize(
                    "§cHasználat: /events debug spawn <event-kulcs>"));
            return;
        }
        final String normalized = eventKey.toLowerCase(Locale.ROOT).replace('_', '-');
        final Location origin = player.getLocation().clone();
        player.sendMessage(LEGACY.deserialize(
                "§7Event-helykeresés diagnosztika indul: §f" + normalized));
        guard.debugSearch(normalized, origin, lines -> send(player, lines));
    }

    private void send(final Player player, final List<String> lines) {
        if (!player.isOnline()) {
            return;
        }
        player.getScheduler().run(plugin, task -> {
            for (final String line : lines) {
                player.sendMessage(LEGACY.deserialize(line));
            }
        }, null);
    }
}
