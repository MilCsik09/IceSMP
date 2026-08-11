package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.EventSpawnGuard;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Maintains immutable player position, look-direction and send-distance snapshots. */
public final class EventSpawnGuardListener implements Listener {
    private final EventSpawnGuard guard;

    public EventSpawnGuardListener(final EventSpawnGuard guard) {
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        guard.trackPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        guard.forgetPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        final Location to = event.getTo();
        if (to != null && changedPositionOrDirection(event.getFrom(), to)) {
            guard.trackPlayer(event.getPlayer(), to, event.getPlayer().getGameMode());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            guard.trackPlayer(event.getPlayer(), event.getTo(), event.getPlayer().getGameMode());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        guard.trackPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final PlayerRespawnEvent event) {
        guard.trackPlayer(event.getPlayer(), event.getRespawnLocation(), event.getPlayer().getGameMode());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(final PlayerGameModeChangeEvent event) {
        guard.trackPlayer(event.getPlayer(), event.getPlayer().getLocation(), event.getNewGameMode());
    }

    private static boolean changedPositionOrDirection(final Location from, final Location to) {
        return from.getWorld() != to.getWorld()
                || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()
                || Math.abs(from.getYaw() - to.getYaw()) >= 2.0F
                || Math.abs(from.getPitch() - to.getPitch()) >= 2.0F;
    }
}
