package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.TerritoryProtectionService;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Enforces the configurable per-zone-type territory protection (build, interact,
 * pvp, explosions, fire) via {@link TerritoryProtectionService}. Mirrors the
 * ClaimProtectionListener event set; every handler runs on the acting
 * block/entity's region thread and the zone lookup is a lock-free read, so no
 * scheduler hop is needed except the PvP notice (handled inside the service).
 */
public final class TerritoryProtectionListener implements Listener {

    private final TerritoryProtectionService protection;

    public TerritoryProtectionListener(final TerritoryProtectionService protection) {
        this.protection = protection;
    }

    // ==================== build (törés / rakás / vödör / függő tárgyak) ====================

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(final BlockBreakEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(final BlockPlaceEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(final PlayerBucketEmptyEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketFill(final PlayerBucketFillEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingPlace(final HangingPlaceEvent event) {
        final Player player = event.getPlayer();
        if (player != null && protection.denyBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingBreak(final HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player
                && protection.denyBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ==================== interact (konténer / ajtó / gomb / kar…) ====================

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        // Only gate meaningful right-clicks: storage containers and other
        // interactable blocks (doors, buttons, levers, gates, workstations…).
        if (!(block.getState() instanceof Container) && !block.getType().isInteractable()) {
            return;
        }
        if (protection.denyInteract(event.getPlayer(), block.getLocation())) {
            event.setCancelled(true);
        }
    }

    // ==================== pvp (+ dekoráció rongálása) ====================

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        final Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        if (event.getEntity() instanceof Player victim) {
            if (protection.denyPvp(victim, attacker)) {
                event.setCancelled(true);
            }
            return;
        }
        // Armor stands count as builds (they hold gear/decoration).
        if (event.getEntity() instanceof ArmorStand
                && protection.denyBuild(attacker, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** The player behind the damage: a direct attacker or the shooter of a projectile. */
    private static Player resolveAttacker(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            final ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    // ==================== explosions ====================

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(final EntityExplodeEvent event) {
        event.blockList().removeIf(block -> protection.isExplosionBlockedAt(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(final BlockExplodeEvent event) {
        event.blockList().removeIf(block -> protection.isExplosionBlockedAt(block.getLocation()));
    }

    // ==================== fire (gyújtás / terjedés / égés) ====================

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onIgnite(final BlockIgniteEvent event) {
        if (protection.isFireBlockedAt(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBurn(final BlockBurnEvent event) {
        if (protection.isFireBlockedAt(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpread(final BlockSpreadEvent event) {
        if (event.getSource().getType() == org.bukkit.Material.FIRE
                && protection.isFireBlockedAt(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
