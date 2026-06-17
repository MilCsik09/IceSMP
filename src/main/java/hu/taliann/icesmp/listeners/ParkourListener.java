package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ParkourManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Detects when a player on an active parkour run reaches the finish, and clears
 * their run on quit. Only reacts to actual block-position changes.
 */
public final class ParkourListener implements Listener {

    private final ParkourManager parkourManager;

    public ParkourListener(final ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        parkourManager.checkFinish(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        parkourManager.cancel(event.getPlayer().getUniqueId());
    }
}
