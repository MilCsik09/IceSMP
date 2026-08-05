package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.managers.VanishManager;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle, leak-prevention and explicit capability gates for persisted vanish state. */
public final class VanishListener implements Listener {
    private final JavaPlugin plugin;
    private final ModerationManager moderationManager;
    private final VanishManager vanishManager;
    private final ConfigManager configManager;

    public VanishListener(final JavaPlugin plugin, final ModerationManager moderationManager,
                          final VanishManager vanishManager, final ConfigManager configManager) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
        this.vanishManager = vanishManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinMessage(final PlayerJoinEvent event) {
        vanishManager.markOnline(event.getPlayer().getUniqueId());
        moderationManager.rememberOnlinePlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        if (isVanished(event.getPlayer())) {
            event.joinMessage(null);
        }
        refreshAfterTrackingChange(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(final PlayerQuitEvent event) {
        vanishManager.markOffline(event.getPlayer().getUniqueId());
        if (isVanished(event.getPlayer())) {
            event.quitMessage(null);
        }
        moderationManager.recordLastLocationAsync(event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(final PlayerKickEvent event) {
        vanishManager.markOffline(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        refreshAfterTrackingChange(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        refreshAfterTrackingChange(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final PlayerRespawnEvent event) {
        refreshAfterTrackingChange(event.getPlayer(), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobTarget(final EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isVanished(player)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isVanished(player)
                && !configManager.getBoolean("moderation.vanish.allow-item-pickup", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isVanished(player)
                && !configManager.getBoolean("moderation.vanish.allow-damage", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageBy(final EntityDamageByEntityEvent event) {
        final Player player = attackingPlayer(event.getDamager());
        if (player != null && isVanished(player)
                && !configManager.getBoolean("moderation.vanish.allow-damage", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (interactionBlocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (interactionBlocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(final InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && interactionBlocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && interactionBlocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && interactionBlocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(final AsyncChatEvent event) {
        if (isVanished(event.getPlayer())
                && !configManager.getBoolean("moderation.vanish.allow-chat", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(final PlayerAdvancementDoneEvent event) {
        if (isVanished(event.getPlayer())) {
            event.message(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(final PlayerDeathEvent event) {
        if (isVanished(event.getPlayer())) {
            event.deathMessage(null);
        }
    }

    private boolean interactionBlocked(final Player player) {
        return isVanished(player)
                && !configManager.getBoolean("moderation.vanish.allow-interaction", false);
    }

    private boolean isVanished(final Player player) {
        return moderationManager.isVanished(player.getUniqueId());
    }

    private void refreshAfterTrackingChange(final Player player, final long delayTicks) {
        PaperEntityTaskSubmission.runDelayed(plugin, player.getScheduler(), () -> {
            vanishManager.refreshViewer(player);
            vanishManager.refreshSubject(player.getUniqueId());
        }, () -> { }, delayTicks);
    }

    private static Player attackingPlayer(final Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
