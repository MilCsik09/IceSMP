package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.integration.LayPoseBridge;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.SitManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Native GSit-style click-to-sit: right-clicking an empty-handed stair/slab with an empty hand
 * seats the player on top of it. All the actual seat bookkeeping lives in {@link SitManager}; this
 * listener only decides WHEN to sit/stand and always does so on the affected player's own event
 * thread, so every call into the manager here is already region-local (see {@link SitManager}
 * class javadoc for the Folia reasoning).
 *
 * <p>IDEAS A49: also owns the "lay pose" auto-stand-up-on-move rule ({@link #onMove}) — the seat
 * system and the lay pose are two independent per-player states in {@link SitManager}, but both are
 * cleared here since {@code PlayerMoveEvent} always fires on the moving player's own region thread.
 */
public final class SitListener implements Listener {

    private final SitManager sitManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public SitListener(final SitManager sitManager, final ConfigManager configManager,
                        final MessageManager messageManager) {
        this.sitManager = sitManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (!configManager.getBoolean("sit.enabled", true) || !configManager.getBoolean("sit.click-to-sit", true)) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Block block = event.getClickedBlock();
        if (block == null || !(Tag.STAIRS.isTagged(block.getType()) || Tag.SLABS.isTagged(block.getType()))) {
            return;
        }

        final Player player = event.getPlayer();
        if (player.isSneaking() || sitManager.isSitting(player.getUniqueId())) {
            return;
        }
        // Empty hand only — a held item means the click is meant to place/use it, not sit.
        if (event.getItem() != null && !event.getItem().getType().isAir()) {
            return;
        }
        if (!sitManager.hasClearanceAbove(block)) {
            return;
        }

        sitManager.sit(player, sitManager.computeSeatLocation(block));
    }

    /** Covers both explicit stand-up (command) and any external dismount (e.g. knockback off the seat). */
    @EventHandler(ignoreCancelled = true)
    public void onDismount(final EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getDismounted() instanceof ArmorStand) || !sitManager.isSitting(player.getUniqueId())) {
            return;
        }
        // Same region as the just-vacated seat (vehicle/passenger pairs never cross regions in
        // Folia), so removing the stand here is safe without a scheduler hop.
        sitManager.standUp(player);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Runs on the player's own region thread, so the seat ArmorStand (co-located by the Folia
        // passenger/vehicle invariant) can be removed directly here.
        sitManager.standUp(event.getPlayer());
        clearLay(event.getPlayer(), false);
    }

    @EventHandler
    public void onPlayerKick(final PlayerKickEvent event) {
        sitManager.standUp(event.getPlayer());
        clearLay(event.getPlayer(), false);
    }

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        sitManager.standUp(event.getEntity());
        clearLay(event.getEntity(), false);
    }

    @EventHandler
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        // Stand up before the teleport resolves so the seat never ends up in a different region
        // (or world) than the player.
        sitManager.standUp(event.getPlayer());
        clearLay(event.getPlayer(), false);
    }

    /**
     * IDEAS A49: mozgásra felkel a fekvő pózból. {@code PlayerMoveEvent} fej-forgatásra (csak
     * yaw/pitch változás) is tüzel — csak akkor állunk fel, ha a tényleges koordináta is változott,
     * különben minden körülnézés lefektetne/felállítana.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        if (!sitManager.isLaying(player.getUniqueId())) {
            return;
        }
        final org.bukkit.Location from = event.getFrom();
        final org.bukkit.Location to = event.getTo();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }
        clearLay(player, true);
    }

    /** Drops the lay disguise + {@link SitManager} bookkeeping; optionally notifies the player. */
    private void clearLay(final Player player, final boolean notify) {
        if (!sitManager.isLaying(player.getUniqueId())) {
            return;
        }
        LayPoseBridge.clear(player);
        sitManager.stopLaying(player.getUniqueId());
        if (notify) {
            player.sendMessage(messageManager.get("sit-lay-up", "&b[Fekvés] &7Felálltál."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        // Cheap guard: skip the scan entirely unless something is actually seated anywhere.
        if (!sitManager.hasActiveSits()) {
            return;
        }
        final Player sitter = sitManager.findSitterOnBlock(event.getBlock());
        if (sitter != null) {
            sitManager.standUp(sitter);
        }
    }
}
