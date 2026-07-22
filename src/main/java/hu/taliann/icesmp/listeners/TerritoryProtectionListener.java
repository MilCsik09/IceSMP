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
        if (!protection.denyBuild(event.getPlayer(), event.getBlock().getLocation())) {
            return;
        }
        // Regen-rombolás: a tiltás helyett a blokk drop/XP nélkül törhető, és a
        // beállított késleltetés után PONTOSAN visszaépül. Ostrom alatt a célzónában
        // a regisztrált harcosnak jár; zónán kívüli ("always") módban config-kapcsolós.
        final hu.taliann.icesmp.managers.BlockRegenService regen = this.blockRegenService;
        if (regen != null && regen.isEnabled()
                && !hu.taliann.icesmp.managers.BlockRegenService.isTileEntity(event.getBlock())) {
            final long delay;
            if (regen.isSiegeBreakEnabled()
                    && protection.isRaidSiegeAt(event.getPlayer(), event.getBlock().getLocation())) {
                delay = regen.siegeBreakDelayMillis();
            } else if (regen.isAlwaysBreakEnabled()) {
                delay = regen.alwaysBreakDelayMillis();
            } else {
                event.setCancelled(true);
                return;
            }
            if (regen.capture(event.getBlock(), delay)) {
                event.setDropItems(false);
                event.setExpToDrop(0);
                return;
            }
        }
        event.setCancelled(true);
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
        if (applyRegenExplosion(event.blockList(), event.getEntity().getLocation())) {
            event.setYield(0.0F);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(final BlockExplodeEvent event) {
        if (applyRegenExplosion(event.blockList(), event.getBlock().getLocation())) {
            event.setYield(0.0F);
        }
    }

    /**
     * Védett zónában a robbanás megtörténhet, de a világ visszagyógyul: a védett
     * blokkok pillanatképpel a regen-sorba kerülnek (drop nélkül — yield 0), a
     * tile-entity blokkok (láda, kemence…) érintetlenek maradnak. Kikapcsolt regen
     * mellett a korábbi teljes tiltás él.
     *
     * @return true, ha volt regen-re fogott blokk (a hívó nullázza a yield-et)
     */
    private boolean applyRegenExplosion(final java.util.List<org.bukkit.block.Block> blocks,
                                        final org.bukkit.Location center) {
        final hu.taliann.icesmp.managers.BlockRegenService regen = this.blockRegenService;
        if (regen == null || !regen.isEnabled()) {
            blocks.removeIf(block -> protection.isExplosionBlockedAt(block.getLocation()));
            return false;
        }
        boolean captured = false;
        final long delay = regen.explosionDelayMillis();
        final java.util.Iterator<org.bukkit.block.Block> it = blocks.iterator();
        while (it.hasNext()) {
            final org.bukkit.block.Block block = it.next();
            // Zóna-mátrix dönt: regen-es zónában (vadon is lehet!) gyógyuló rombolás;
            // regen nélküli védett zónában a régi teljes tiltás; máshol vanília.
            if (!regen.isZoneRegenEnabled(protection.zoneTypeKeyAt(block.getLocation()))) {
                if (protection.isExplosionBlockedAt(block.getLocation())) {
                    it.remove();
                }
                continue;
            }
            if (block.getType() == org.bukkit.Material.TNT) {
                continue; // a lánc-robbanás él, de a TNT nem épül vissza (nincs hurok)
            }
            if (!regen.capture(block, delay)) {
                it.remove();
                if (hu.taliann.icesmp.managers.BlockRegenService.isTileEntity(block)) {
                    regen.playWardEffect(block); // az óvó rúnák látványa
                }
            } else {
                regen.spawnDebris(block, center);
                captured = true;
            }
        }
        return captured;
    }

    /**
     * (1) A törmelék landolva porlad — sosem válhat valódi blokká. (2) Mob-rombolás
     * (Wither test-bontása, ravager, enderman…) védett zónában: regen-nel a blokk
     * LÁTVÁNYOSAN kitörik (törmelék repül), drop nélkül, és visszaépül — regen nélkül
     * sima tiltás. Enélkül a Wither VÉGLEG rombolta a városfalat (rés volt).
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(final org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (event.getEntity().getScoreboardTags().contains(
                hu.taliann.icesmp.managers.BlockRegenService.DEBRIS_TAG)) {
            event.setCancelled(true);
            event.getEntity().getWorld().spawnParticle(org.bukkit.Particle.BLOCK_CRUMBLE,
                    event.getEntity().getLocation(), 12, 0.2D, 0.2D, 0.2D, event.getBlockData());
            event.getEntity().remove();
            return;
        }
        // A kráterbe hulló gravitációs blokk (perem-homok) itemként esik le — nem
        // tűnhet el némán a későbbi visszaépítő felülírásban.
        if (event.getEntity() instanceof org.bukkit.entity.FallingBlock) {
            final hu.taliann.icesmp.managers.BlockRegenService fbRegen = this.blockRegenService;
            if (fbRegen != null && fbRegen.isCraterPos(event.getBlock())) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getEntity() instanceof Player || !event.getTo().isAir()) {
            return;
        }
        final hu.taliann.icesmp.managers.BlockRegenService regenSvc = this.blockRegenService;
        final boolean zoneRegen = regenSvc != null && regenSvc.isEnabled()
                && regenSvc.isZoneRegenEnabled(protection.zoneTypeKeyAt(event.getBlock().getLocation()));
        if (!zoneRegen && !protection.isExplosionBlockedAt(event.getBlock().getLocation())) {
            return;
        }
        event.setCancelled(true);
        final hu.taliann.icesmp.managers.BlockRegenService regen = this.blockRegenService;
        if (regen != null && regen.isEnabled()
                && !hu.taliann.icesmp.managers.BlockRegenService.isTileEntity(event.getBlock())
                && regen.capture(event.getBlock(), regen.explosionDelayMillis())) {
            regen.spawnDebris(event.getBlock(), event.getEntity().getLocation());
            event.getBlock().setType(org.bukkit.Material.AIR, false);
        }
    }

    /**
     * Fizika által lepattanó rátett blokk (fáklya, falitábla a kirobbant falról):
     * drop nélkül semmisül meg és a fallal együtt visszaépül — "ha csináljuk,
     * csináljuk rendesen" (tulaj-kérés).
     */
    /**
     * Fizika-pajzs: a frissen visszaépített blokkot a vanília fizika nem bánthatja —
     * se frissítés (homok-leesés, fáklya-lepattanás), se folyadék-befolyás, se
     * fizika-törés, amíg a pajzs él. A gyors-út (üres pajzs-lista) miatt a sűrű
     * physics-event terhelése pajzs nélkül gyakorlatilag nulla.
     */
    @EventHandler(ignoreCancelled = true)
    public void onShieldedPhysics(final org.bukkit.event.block.BlockPhysicsEvent event) {
        final hu.taliann.icesmp.managers.BlockRegenService regen = this.blockRegenService;
        if (regen != null && regen.isRestoredShielded(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Irány-szelektív víz-szabály: a kráterBE szabad a befolyás (látványos, természetes),
     * de kráter-pozícióBÓL tovább nem terjedhet (a fal mögötti tér nem ázik el), és a
     * frissen visszaépített blokkba (fáklya!) sem folyhat, amíg az utó-pajzs él.
     */
    @EventHandler(ignoreCancelled = true)
    public void onShieldedLiquidFlow(final org.bukkit.event.block.BlockFromToEvent event) {
        final hu.taliann.icesmp.managers.BlockRegenService regen = this.blockRegenService;
        if (regen == null) {
            return;
        }
        if (regen.isCraterPos(event.getBlock()) || regen.isRestoredShielded(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDestroy(final com.destroystokyo.paper.event.block.BlockDestroyEvent event) {
        final hu.taliann.icesmp.managers.BlockRegenService regen = this.blockRegenService;
        if (regen != null && regen.isRestoredShielded(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (regen == null || !regen.isEnabled()
                || !regen.isZoneRegenEnabled(protection.zoneTypeKeyAt(event.getBlock().getLocation()))) {
            return;
        }
        if (regen.capture(event.getBlock(), regen.explosionDelayMillis())) {
            event.setWillDrop(false);
        }
    }

    private volatile hu.taliann.icesmp.managers.BlockRegenService blockRegenService;

    public void setBlockRegenService(final hu.taliann.icesmp.managers.BlockRegenService service) {
        this.blockRegenService = service;
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
        // On retract the pulled blocks move TOWARD the piston — opposite getDirection().
        if (pistonAffectsProtected(event.getBlocks(), event.getDirection().getOppositeFace())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonAffectsProtected(final java.util.List<Block> blocks, final org.bukkit.block.BlockFace movement) {
        for (final Block block : blocks) {
            if (protection.isTerrainProtectedAt(block.getLocation())
                    || protection.isTerrainProtectedAt(block.getRelative(movement).getLocation())) {
                return true;
            }
        }
        return false;
    }

    // ==================== pvp (dobott/lingering bájitalok) ====================

    /**
     * HARMFUL splash potions a player throws onto ANOTHER player in a safe zone are
     * neutralised (beneficial team-splashes and self-buffs pass). Instant-damage
     * potions are additionally handled by {@link #onEntityDamageByEntity} (a thrown
     * potion is a projectile). Harmfulness is keyed off the effect's stable
     * namespaced key, not the version-specific category API.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPotionSplash(final org.bukkit.event.entity.PotionSplashEvent event) {
        if (!hasHarmfulEffect(event.getPotion().getEffects())) {
            return;
        }
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

    /** HARMFUL lingering-potion clouds stop affecting OTHER players in a safe zone. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onAreaEffectCloud(final org.bukkit.event.entity.AreaEffectCloudApplyEvent event) {
        final org.bukkit.entity.AreaEffectCloud cloud = event.getEntity();
        if (!cloudIsHarmful(cloud)) {
            return;
        }
        final Player owner = cloud.getSource() instanceof Player player ? player : null;
        event.getAffectedEntities().removeIf(affected ->
                affected instanceof Player victim && !victim.equals(owner)
                        && protection.denyCombat(victim.getLocation(), owner, false));
    }

    /** Harmful potion-effect keys (version-stable paths; covers old and 1.21 names). */
    private static final java.util.Set<String> HARMFUL_EFFECT_KEYS = java.util.Set.of(
            "instant_damage", "harm", "poison", "wither", "slowness", "slow", "weakness",
            "mining_fatigue", "slow_digging", "nausea", "confusion", "blindness", "hunger",
            "levitation", "unluck", "darkness", "infested", "oozing", "weaving", "wind_charged");

    private static boolean hasHarmfulEffect(final java.util.Collection<org.bukkit.potion.PotionEffect> effects) {
        for (final org.bukkit.potion.PotionEffect effect : effects) {
            if (HARMFUL_EFFECT_KEYS.contains(effect.getType().getKey().getKey())) {
                return true;
            }
        }
        return false;
    }

    private static boolean cloudIsHarmful(final org.bukkit.entity.AreaEffectCloud cloud) {
        if (hasHarmfulEffect(cloud.getCustomEffects())) {
            return true;
        }
        // Vanilla lingering potions carry no custom effects — inspect the base type
        // key (substring match tolerates strong_/long_ variants, e.g. strong_harming).
        final org.bukkit.potion.PotionType base = cloud.getBasePotionType();
        if (base == null) {
            return false;
        }
        final String key = base.getKey().getKey();
        for (final String hint : HARMFUL_BASE_HINTS) {
            if (key.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.Set<String> HARMFUL_BASE_HINTS = java.util.Set.of(
            "harm", "poison", "slow", "weak", "wither", "nausea", "blind", "hunger",
            "fatigue", "levitation", "darkness", "infested", "oozing", "weaving", "wind_charged");
}
