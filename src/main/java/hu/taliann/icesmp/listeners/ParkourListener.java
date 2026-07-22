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

    /**
     * Mobilitás-fék: a cél-ellenőrzés csak távolságot néz, ezért gyöngy/refős
     * teleporttal, elytrával vagy riptide-dal a pálya bejárás nélkül teljesíthető
     * lenne — az ilyen mozgás megszakítja a futamot.
     */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(final org.bukkit.event.player.PlayerTeleportEvent event) {
        final org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            parkourManager.cancelForMobility(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(final org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof org.bukkit.entity.Player player) {
            parkourManager.cancelForMobility(player);
        }
    }

    @EventHandler
    public void onRiptide(final org.bukkit.event.player.PlayerRiptideEvent event) {
        parkourManager.cancelForMobility(event.getPlayer());
    }
}
