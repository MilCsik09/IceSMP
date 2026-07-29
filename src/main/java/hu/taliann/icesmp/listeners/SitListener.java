package hu.taliann.icesmp.listeners;

import org.bukkit.event.entity.EntityDismountEvent;
import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.managers.SitManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/** Region-owned event adapter for the native sit-only domain. */
public final class SitListener implements Listener {
    private final SitManager sitManager;
    private final MessageManager messageManager;

    public SitListener(final SitManager sitManager, final MessageManager messageManager) {
        this.sitManager = sitManager;
        this.messageManager = messageManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (!sitManager.isClickToSitEnabled()
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Block block = event.getClickedBlock();
        if (block == null || !sitManager.isConfiguredSeatBlock(block)) {
            return;
        }
        final Player player = event.getPlayer();
        if (!player.hasPermission(Permissions.SIT) || player.isSneaking()) {
            return;
        }
        if (sitManager.isEmptyHandOnly() && event.getItem() != null && !event.getItem().getType().isAir()) {
            return;
        }
        final SitManager.SitResult result = sitManager.sit(player, block, SitManager.SitOrigin.CLICK);
        if (result == SitManager.SitResult.OK) {
            event.setCancelled(true);
            player.sendMessage(messageManager.get("sit.down", "&b[Ülés] &7Leültél."));
        } else {
            sendFailure(player, result);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(final EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && event.getDismounted() instanceof ArmorStand stand) {
            sitManager.completeDismount(player, stand);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) { sitManager.requestReset(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(final PlayerKickEvent event) { sitManager.requestReset(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final PlayerDeathEvent event) { sitManager.resetPlayer(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) { sitManager.resetPlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(final PlayerChangedWorldEvent event) { sitManager.requestReset(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (sitManager.shouldStandUpOnDamage() && event.getFinalDamage() > 0.0D
                && event.getEntity() instanceof Player player && sitManager.isSitting(player.getUniqueId())) {
            sitManager.resetPlayer(player);
            player.sendMessage(messageManager.get("sit.damage-up", "&b[Ülés] &7Sebzés miatt felálltál."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(final PlayerToggleSneakEvent event) {
        if (event.isSneaking() && sitManager.shouldStandUpOnSneak()
                && sitManager.isSitting(event.getPlayer().getUniqueId())) {
            sitManager.resetPlayer(event.getPlayer());
            event.getPlayer().sendMessage(messageManager.get("sit.sneak-up", "&b[Ülés] &7Felálltál."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        if (!sitManager.shouldStandUpOnBlockBreak() || !sitManager.hasActiveSits()) {
            return;
        }
        final UUID sitterId = sitManager.findSitterOnBlock(event.getBlock());
        if (sitterId == null) {
            return;
        }
        final Player sitter = Bukkit.getPlayer(sitterId);
        if (sitter != null && Bukkit.isOwnedByCurrentRegion(sitter)) {
            sitManager.standUp(sitter);
            sitter.sendMessage(messageManager.get("sit.block-break-up",
                    "&b[Ülés] &7Az ülőhelyed megszűnt, ezért felálltál."));
        } else {
            sitManager.requestReset(sitterId);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(final PlayerCommandPreprocessEvent event) {
        if (sitManager.isSitting(event.getPlayer().getUniqueId())
                && sitManager.isBlockedCommand(event.getMessage())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messageManager.get("sit.command-blocked",
                    "&cEzt a parancsot ülés közben nem használhatod. &7Előbb: &f/sit fel"));
        }
    }

    private void sendFailure(final Player player, final SitManager.SitResult result) {
        switch (result) {
            case DISABLED, MATERIAL_NOT_ALLOWED -> { }
            case ALREADY_SITTING -> player.sendMessage(messageManager.get("sit.already-sitting", "&cMár ülsz."));
            case IN_VEHICLE -> player.sendMessage(messageManager.get("sit.in-vehicle", "&cJárműben nem tudsz leülni."));
            case IN_LIQUID -> player.sendMessage(messageManager.get("sit.in-liquid", "&cFolyadékban nem tudsz leülni."));
            case NOT_ON_GROUND -> player.sendMessage(messageManager.get("sit.not-on-ground", "&cCsak szilárd talajon tudsz leülni."));
            case WORLD_DISABLED -> player.sendMessage(messageManager.get("sit.world-disabled", "&cEbben a világban az ülés nincs engedélyezve."));
            case TOO_FAR -> player.sendMessage(messageManager.get("sit.too-far", "&cEz az ülőhely túl messze van."));
            case UNSAFE -> player.sendMessage(messageManager.get("sit.unsafe", "&cEz a hely nem biztonságos üléshez."));
            case OCCUPIED -> player.sendMessage(messageManager.get("sit.occupied", "&cEzen a helyen már ül valaki."));
            case FOREIGN_REGION -> player.sendMessage(messageManager.get("sit.foreign-region", "&cAz ülőhely régióváltás alatt áll; próbáld újra."));
            case OBSTRUCTED -> player.sendMessage(messageManager.get("sit.obstructed", "&cItt nem tudsz leülni."));
            case OK -> { }
        }
    }
}
