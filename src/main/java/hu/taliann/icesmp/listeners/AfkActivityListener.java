package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.GlobalAfkTracker;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Feeds the global AFK state from input-like events. Every handler is a map-only state update;
 * async chat therefore never touches player or world state off its owning thread.
 */
public final class AfkActivityListener implements Listener {

    private final AfkManager afkManager;

    public AfkActivityListener(final AfkManager afkManager) {
        this.afkManager = afkManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && Float.compare(from.getYaw(), to.getYaw()) == 0
                && Float.compare(from.getPitch(), to.getPitch()) == 0) {
            return;
        }
        afkManager.recordActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(final AsyncChatEvent event) {
        afkManager.recordActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(final PlayerCommandPreprocessEvent event) {
        if (!GlobalAfkTracker.isAfkToggleCommand(event.getMessage())) {
            afkManager.recordActivity(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        afkManager.recordActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        afkManager.recordActivity(event.getPlayer().getUniqueId());
    }
}
