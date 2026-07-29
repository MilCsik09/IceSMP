package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.managers.VanishManager;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle and gameplay gates for the persisted vanish state. */
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
        if (moderationManager.isVanished(event.getPlayer().getUniqueId())) {
            event.joinMessage(null);
        }
        PaperEntityTaskSubmission.runDelayed(plugin, event.getPlayer().getScheduler(), () -> {
            vanishManager.refreshViewer(event.getPlayer());
            vanishManager.refreshSubject(event.getPlayer().getUniqueId());
        }, () -> { }, 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(final PlayerQuitEvent event) {
        vanishManager.markOffline(event.getPlayer().getUniqueId());
        if (moderationManager.isVanished(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
        moderationManager.recordLastLocationAsync(event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(final PlayerKickEvent event) {
        // PlayerQuitEvent follows a successful kick and owns the durable location snapshot. Keeping
        // kick cleanup transient avoids two racing persistence writes for the same disconnect.
        vanishManager.markOffline(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        vanishManager.refreshViewer(event.getPlayer());
        vanishManager.refreshSubject(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobTarget(final EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && moderationManager.isVanished(player.getUniqueId())) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && moderationManager.isVanished(player.getUniqueId())
                && !configManager.getBoolean("moderation.vanish.allow-item-pickup", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && moderationManager.isVanished(player.getUniqueId())
                && !configManager.getBoolean("moderation.vanish.allow-damage", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageBy(final EntityDamageByEntityEvent event) {
        final Player player = attackingPlayer(event.getDamager());
        if (player != null && moderationManager.isVanished(player.getUniqueId())
                && !configManager.getBoolean("moderation.vanish.allow-damage", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (moderationManager.isVanished(event.getPlayer().getUniqueId())
                && !configManager.getBoolean("moderation.vanish.allow-interaction", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (moderationManager.isVanished(event.getPlayer().getUniqueId())
                && !configManager.getBoolean("moderation.vanish.allow-interaction", false)) {
            event.setCancelled(true);
        }
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
