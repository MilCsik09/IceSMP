package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.AfkManager;
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
 * Feeds {@link AfkManager#recordActivity(java.util.UUID)} from every input-like event — a bare
 * map-write per event, nothing else, so this listener stays cheap on the hot paths (move, chat).
 *
 * <p>{@link AsyncChatEvent} runs OFF the region threads (Paper async chat), so its handler is
 * restricted to exactly that one map-write; touching the player/world there would break Folia.
 */
public final class AfkActivityListener implements Listener {

    private final AfkManager afkManager;

    public AfkActivityListener(final AfkManager afkManager) {
        this.afkManager = afkManager;
    }

    /** Only counts as activity if the player actually moved a block or turned — not a jitter tick. */
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

    /** Async (off the region threads): record-only, no other AfkManager/player access here. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(final AsyncChatEvent event) {
        afkManager.recordActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(final PlayerCommandPreprocessEvent event) {
        afkManager.recordActivity(event.getPlayer().getUniqueId());
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
