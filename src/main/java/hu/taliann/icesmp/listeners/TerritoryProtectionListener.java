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
import org.bukkit.event.hanging.HangingBreakEvent;
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

    /**
     * Item frames / paintings: broken by a player → build rule; broken by an
     * explosion → explosions rule. (HangingBreakByEntityEvent shares this handler
     * list, so a single HangingBreakEvent handler covers both without a
     * double-dispatch ClassCastException.)
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingBreak(final HangingBreakEvent event) {
        final org.bukkit.Location location = event.getEntity().getLocation();
        if (event instanceof HangingBreakByEntityEvent byEntity
                && byEntity.getRemover() instanceof Player player) {
            if (protection.denyBuild(player, location)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION
                && protection.isExplosionBlockedAt(location)) {
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
        final Player attacker = resolveAttacker(event.getDamager());
        if (event.getEntity() instanceof Player victim) {
            // Safe-zone: block player-attributed AND unattributed (TNT/mob) damage.
            if ((attacker != null || isHostileSource(event.getDamager()))
                    && protection.denyCombat(victim.getLocation(), attacker, attacker != null)) {
                event.setCancelled(true);
            }
            return;
        }
        // Decoration/utility entities: a player breaking them is a build; an
        // explosion destroying them is gated by the explosions rule.
        if (event.getEntity() instanceof ArmorStand) {
            if (attacker != null) {
                if (protection.denyBuild(attacker, event.getEntity().getLocation())) {
                    event.setCancelled(true);
                }
            } else if (isHostileSource(event.getDamager())
                    && protection.isExplosionBlockedAt(event.getEntity().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * The player behind the damage: a direct attacker, a projectile's shooter, a
     * tamed pet's owner, or the player that primed a TNT. Null when no player is
     * attributable.
     */
    private static Player resolveAttacker(final org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            final ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof org.bukkit.entity.Tameable tameable && tameable.isTamed()
                && tameable.getOwner() instanceof Player owner) {
            return owner;
        }
        if (damager instanceof org.bukkit.entity.TNTPrimed tnt
                && tnt.getSource() instanceof Player source) {
            return source;
        }
        return null;
    }

    /** Whether the damage source is an explosion or a hostile creature (safe-zone shield). */
    private static boolean isHostileSource(final org.bukkit.entity.Entity damager) {
        return damager instanceof org.bukkit.entity.Explosive
                || damager instanceof org.bukkit.entity.TNTPrimed
                || damager instanceof org.bukkit.entity.Monster;
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

    // ==================== terrain (mob-grief / folyadék / dugattyú) ====================

    /** Block-eating/-moving mobs (enderman, ravager, silverfish…) leave protected zones alone. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityChangeBlock(final org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player)
                && protection.isTerrainProtectedAt(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Liquid (water/lava) may not flow INTO a protected zone from outside. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onLiquidFlow(final org.bukkit.event.block.BlockFromToEvent event) {
        if (protection.isTerrainProtectedAt(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Pistons may not push/pull blocks into or within a protected zone. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonExtend(final org.bukkit.event.block.BlockPistonExtendEvent event) {
        if (pistonAffectsProtected(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonRetract(final org.bukkit.event.block.BlockPistonRetractEvent event) {
        if (pistonAffectsProtected(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonAffectsProtected(final java.util.List<Block> blocks, final org.bukkit.block.BlockFace direction) {
        for (final Block block : blocks) {
            if (protection.isTerrainProtectedAt(block.getLocation())
                    || protection.isTerrainProtectedAt(block.getRelative(direction).getLocation())) {
                return true;
            }
        }
        return false;
    }

    // ==================== pvp (dobott/lingering bájitalok) ====================

    /**
     * Splash potions a player throws onto ANOTHER player in a safe zone are
     * neutralised (self-buffs pass). Instant-damage potions are additionally
     * handled by {@link #onEntityDamageByEntity} (a thrown potion is a projectile),
     * so this closes the debuff gap (poison/slowness/weakness…) without depending
     * on the version-specific harmful-category API.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPotionSplash(final org.bukkit.event.entity.PotionSplashEvent event) {
        final Player thrower = event.getPotion().getShooter() instanceof Player player ? player : null;
        boolean notified = false;
        for (final org.bukkit.entity.LivingEntity affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player victim) || victim.equals(thrower)) {
                continue;
            }
            if (protection.denyCombat(victim.getLocation(), thrower, !notified && thrower != null)) {
                event.setIntensity(affected, 0.0D);
                notified = true;
            }
        }
    }

    /** Lingering-potion clouds a player created stop affecting other players in a safe zone. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onAreaEffectCloud(final org.bukkit.event.entity.AreaEffectCloudApplyEvent event) {
        final Player owner = event.getEntity().getSource() instanceof Player player ? player : null;
        event.getAffectedEntities().removeIf(affected ->
                affected instanceof Player victim && !victim.equals(owner)
                        && protection.denyCombat(victim.getLocation(), owner, false));
    }
}
