package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.managers.MajorEventGate;
import hu.taliann.icesmp.managers.TerritoryProtectionService;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;

/** Bounded, typed and protection-aware runtime for the 23 Phase E consuming identities. */
public final class TrashRelicRuntime implements Listener, PlayerStateCleanup {

    private static final int MAX_FIELDS_PER_WORLD = 32;
    private static final int MAX_FIELDS_GLOBAL = 128;
    private static final int MAX_NEARBY_ENTITIES = 24;
    private static final int MAX_ANCHORED_DROPS = 64;
    private static final int MAX_TRACKED_PROJECTILES = 256;
    private static final long DEATH_ANCHOR_MILLIS = 20L * 60L * 1_000L;
    private static final Set<String> HOSTILE_EFFECTS = Set.of(
            "BLINDNESS", "DARKNESS", "HUNGER", "LEVITATION", "MINING_FATIGUE",
            "NAUSEA", "POISON", "SLOWNESS", "UNLUCK", "WEAKNESS", "WITHER");

    private final JavaPlugin plugin;
    private final TrashCatalog catalog;
    private final TrashItemFactory items;
    private final TrashHistoryService history;
    private final TrashSpatialFractureStore fractures;
    private final BloodMoonManager bloodMoon;
    private final ClaimManager claims;
    private final TerritoryProtectionService territoryProtection;
    private final TrashRuntimeTelemetry telemetry;
    private final NamespacedKey deathAnchorKey;
    private final NamespacedKey brickReservationKey;
    private final Set<UUID> effectVetoArmed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingConsumes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> claimedFields = ConcurrentHashMap.newKeySet();
    private final Set<UUID> trackedProjectiles = ConcurrentHashMap.newKeySet();
    private final Semaphore projectileTrackerPermits = new Semaphore(MAX_TRACKED_PROJECTILES);
    private final List<RuleField> fields = new CopyOnWriteArrayList<>();

    public TrashRelicRuntime(final JavaPlugin plugin, final TrashCatalog catalog,
                             final TrashItemFactory items, final TrashHistoryService history,
                             final TrashSpatialFractureStore fractures,
                             final BloodMoonManager bloodMoon, final ClaimManager claims,
                             final TerritoryProtectionService territoryProtection,
                             final TrashRuntimeTelemetry telemetry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.items = Objects.requireNonNull(items, "items");
        this.history = Objects.requireNonNull(history, "history");
        this.fractures = Objects.requireNonNull(fractures, "fractures");
        this.bloodMoon = Objects.requireNonNull(bloodMoon, "bloodMoon");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.territoryProtection = Objects.requireNonNull(territoryProtection,
                "territoryProtection");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.deathAnchorKey = new NamespacedKey(plugin, "trash_death_anchor_until");
        this.brickReservationKey = new NamespacedKey(plugin, "trash_brick_reservation");
    }

    public void start() {
        fractures.recover();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin,
                    ignored -> clearStaleBrickReservations(player), null);
        }
    }

    public void shutdown() {
        for (final RuleField field : List.copyOf(fields)) releaseReservation(field);
        fields.clear();
        effectVetoArmed.clear();
        pendingConsumes.clear();
        claimedFields.clear();
        trackedProjectiles.clear();
        fractures.shutdown();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        effectVetoArmed.remove(playerId);
        pendingConsumes.remove(playerId);
        final Player player = Bukkit.getPlayer(playerId);
        if (player != null) clearStaleBrickReservations(player);
        for (final RuleField field : List.copyOf(fields)) {
            if (field.kind() == FieldKind.PROJECTILE_WALL
                    && field.owner().equals(playerId) && fields.remove(field)) {
                claimedFields.remove(field.id());
                if (player != null) clearBrickReservation(player, field.reservationToken());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(final PlayerInteractEvent event) {
        if (event.getHand() == null || event.getItem() == null || !rightClick(event.getAction())) {
            return;
        }
        final TrashRelicBehavior behavior = behaviorOf(event.getItem()).orElse(null);
        if (behavior == null) return;
        event.setCancelled(true);
        final Player player = event.getPlayer();
        switch (behavior) {
            case LYUKAS_VODOR -> {
                for (final PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
                    player.removePotionEffect(effect.getType());
                }
                player.setFireTicks(0);
                transform(player, behavior);
            }
            case BOT -> {
                if (player.getEyeLocation().getDirection().getY() < 0.75D) return;
                player.getWorld().strikeLightningEffect(player.getLocation());
                player.damage(8.0D);
                transform(player, behavior);
            }
            case A_VILAG_LEGELESEBB_KESE -> splitOffhand(player, behavior);
            case PALACKOZOTT_NEM -> effectVetoArmed.add(player.getUniqueId());
            case TEGLA -> createProjectileWall(player, event.getHand());
            case FEL_PAR_PAPUCS -> returnHome(player, behavior);
            case KOEK -> openFracture(player, event.getClickedBlock(), behavior);
            case FEKETE_VIASZDUGO ->
                    createField(player, FieldKind.ACOUSTIC_NULL, 6.0D, 200L, 0.0D);
            case SZAKADT_FEHER_ZASZLO ->
                    createField(player, FieldKind.CEASEFIRE, 7.0D, 240L, 0.0D);
            case KORMOS_SATORSZOG -> lightningTarget(player, event.getClickedBlock(), behavior);
            case MELYNEPI_SELEJTEK ->
                    createField(player, FieldKind.SPATIAL_ANCHOR, 6.0D, 160L, 0.0D);
            case A_NAGYON_ROSSZ_OTLET -> {
                final Location center = player.getLocation().add(
                        player.getLocation().getDirection().multiply(1.5D));
                player.getWorld().createExplosion(center, 4.5F, false, false, player);
                transform(player, behavior);
            }
            case ELSZAKADT_VIRRASZTOKANOC -> requestBloodMoon(player, behavior, false);
            case REPEDT_VIRRASZTOUVEG -> requestBloodMoon(player, behavior, true);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        cleanupFields();
        if (event instanceof EntityDamageByEntityEvent byEntity
                && ceasefireEligible(byEntity)
                && blocksCombatAt(event.getEntity().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player player)) return;
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Projectile projectile) {
            final int reflectSlot = findSlot(player, TrashRelicBehavior.VISSZA_A_FELADONAK);
            if (reflectSlot >= 0 && history.transformInventorySlotOnSuccess(player, reflectSlot)) {
                event.setCancelled(true);
                projectile.getScheduler().run(plugin, ignored -> {
                    if (!projectile.isValid()) return;
                    projectile.setVelocity(projectile.getVelocity().multiply(-1.0D));
                    projectile.setShooter(player);
                }, () -> { });
                return;
            }
        }
        final int shieldSlot = findSlot(player, TrashRelicBehavior.TOROTT_PAJZSDESZKA);
        if (shieldSlot >= 0 && history.transformInventorySlotOnSuccess(player, shieldSlot)) {
            event.setCancelled(true);
            return;
        }
        final boolean lethal = event.getFinalDamage() >= player.getHealth();
        if (!lethal) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            final int ropeSlot = findSlot(player, TrashRelicBehavior.SZAKADT_KOTEL);
            if (ropeSlot >= 0 && history.transformInventorySlotOnSuccess(player, ropeSlot)) {
                player.setFallDistance(0.0F);
                event.setCancelled(true);
                return;
            }
        }
        final int bandageSlot = findSlot(player, TrashRelicBehavior.REGI_KOTES);
        if (bandageSlot >= 0 && history.transformInventorySlotOnSuccess(player, bandageSlot)) {
            event.setDamage(Math.max(0.0D, player.getHealth() - 1.0D));
            return;
        }
        if (behaviorOf(player.getInventory().getHelmet()).orElse(null)
                == TrashRelicBehavior.A_LEGBIZTONSAGOSABB_SISAK) {
            dropTransformedHelmet(player);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            abandonLosingSword(player, byEntity.getDamager());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotion(final EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null
                || !HOSTILE_EFFECTS.contains(event.getNewEffect().getType().getKey()
                .getKey().toUpperCase(java.util.Locale.ROOT))
                || !effectVetoArmed.remove(player.getUniqueId())) return;
        final int slot = findSlot(player, TrashRelicBehavior.PALACKOZOTT_NEM);
        if (slot >= 0 && history.transformInventorySlotOnSuccess(player, slot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        final ItemStack consumed = event.getItem();
        if (!eligibleConsumable(consumed)
                || findSlot(player, TrashRelicBehavior.REPEDT_BOGRE) < 0
                || !pendingConsumes.add(player.getUniqueId())) return;
        final ItemStack preserved = consumed.clone();
        preserved.setAmount(1);
        final int equivalentBefore = countSimilar(player, consumed);
        player.getScheduler().run(plugin, ignored -> {
            pendingConsumes.remove(player.getUniqueId());
            if (!TrashRelicPolicy.consumptionCommitted(
                    equivalentBefore, countSimilar(player, consumed))) return;
            final int slot = findSlot(player, TrashRelicBehavior.REPEDT_BOGRE);
            if (slot < 0) return;
            try {
                history.transformInventorySlotAndAddOnSuccess(player, slot, preserved);
            } catch (final RuntimeException rejected) {
                // The already-committed vanilla consumption remains authoritative; no dupe/drop.
                telemetry.recordBehaviorRuntimeError();
            }
        }, () -> pendingConsumes.remove(player.getUniqueId()));
    }

    /** Explicit opt-in for a bounded RNG consumer; no global random hook exists. */
    public boolean claimBestBucket(final Player player) {
        final int slot = findSlot(player, TrashRelicBehavior.SZERENCSES_GARAS);
        return slot >= 0 && history.transformInventorySlotOnSuccess(player, slot);
    }

    /** Explicit opt-in seam for authored sound/perception consumers. */
    public boolean suppressesAcousticsAt(final Location location) {
        return inField(location, FieldKind.ACOUSTIC_NULL);
    }

    /** Explicit opt-in seam for typed displacement consumers; the input vector is never mutated. */
    public Vector constrainDisplacement(final Location location, final Vector proposed) {
        Objects.requireNonNull(proposed, "proposed");
        return inField(location, FieldKind.SPATIAL_ANCHOR)
                ? proposed.clone().multiply(0.2D) : proposed.clone();
    }

    /** Explicit opt-in seam for authored combat initiators outside Bukkit damage events. */
    public boolean blocksCombatAt(final Location location) {
        return inField(location, FieldKind.CEASEFIRE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(final PlayerDeathEvent event) {
        final List<ItemStack> drops = event.getDrops();
        int nail = -1;
        for (int index = 0; index < drops.size(); index++) {
            if (behaviorOf(drops.get(index)).orElse(null) == TrashRelicBehavior.KOPORSOSZOG) {
                nail = index;
                break;
            }
        }
        if (nail < 0) return;
        try {
            final TrashHistoryService.SplitResult result = history.transformOnSuccess(
                    drops.get(nail), event.getEntity().getUniqueId());
            if (result.remainder() == null) drops.set(nail, result.singleton());
            else {
                drops.set(nail, result.remainder());
                drops.add(result.singleton());
            }
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
            return;
        }
        final long until = System.currentTimeMillis() + DEATH_ANCHOR_MILLIS;
        int anchored = 0;
        for (final ItemStack drop : drops) {
            if (anchored >= MAX_ANCHORED_DROPS) break;
            if (!eligibleDeathDrop(drop)) continue;
            final var meta = drop.getItemMeta();
            meta.getPersistentDataContainer().set(deathAnchorKey,
                    PersistentDataType.LONG, until);
            drop.setItemMeta(meta);
            if (items.isKnownItem(drop)) items.refreshPresentation(drop);
            anchored++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(final ItemSpawnEvent event) {
        recoverDeathAnchor(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(final EntitiesLoadEvent event) {
        int visited = 0;
        for (final Entity entity : event.getEntities()) {
            if (++visited > 256 || !(entity instanceof Item item)) continue;
            item.getScheduler().run(plugin, ignored -> recoverDeathAnchor(item), null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        clearStaleBrickReservations(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(final WorldLoadEvent event) {
        fractures.recoverWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameEvent(final GenericGameEvent event) {
        final Entity source = event.getEntity();
        if (source != null && suppressesAcousticsAt(source.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(final ProjectileLaunchEvent event) {
        final Projectile projectile = event.getEntity();
        cleanupFields();
        final boolean wallActive = hasFieldKind(FieldKind.PROJECTILE_WALL);
        if (!TrashRelicPolicy.mayTrackProjectile(wallActive, trackedProjectiles.size(),
                MAX_TRACKED_PROJECTILES) || !projectileTrackerPermits.tryAcquire()) return;
        if (!trackedProjectiles.add(projectile.getUniqueId())) {
            projectileTrackerPermits.release();
            return;
        }
        final int[] age = {0};
        projectile.getScheduler().runAtFixedRate(plugin, task -> {
            if (!projectile.isValid() || ++age[0] > 100) {
                releaseProjectileTracker(projectile.getUniqueId());
                task.cancel();
                return;
            }
            final RuleField hit = claimField(projectile.getLocation(), FieldKind.PROJECTILE_WALL);
            if (hit == null) return;
            final Vector priorVelocity = projectile.getVelocity().clone();
            projectile.setVelocity(new Vector());
            releaseProjectileTracker(projectile.getUniqueId());
            task.cancel();
            final Player owner = Bukkit.getPlayer(hit.owner());
            if (owner == null) {
                releaseFieldClaim(hit);
                restoreProjectile(projectile, priorVelocity);
                return;
            }
            owner.getScheduler().run(plugin,
                    ignored -> resolveProjectileWall(owner, hit, projectile, priorVelocity),
                    () -> {
                        releaseFieldClaim(hit);
                        restoreProjectile(projectile, priorVelocity);
                    });
        }, () -> releaseProjectileTracker(projectile.getUniqueId()), 1L, 1L);
    }

    private void splitOffhand(final Player player, final TrashRelicBehavior knife) {
        final ItemStack target = player.getInventory().getItemInOffHand();
        if (target.getType().isAir() || target.getAmount() < 2
                || items.isKnownItem(target) || target.hasItemMeta()
                && !target.getItemMeta().getPersistentDataContainer().isEmpty()
                || player.getInventory().firstEmpty() < 0) return;
        final int first = target.getAmount() / 2;
        final ItemStack secondHalf = target.clone();
        secondHalf.setAmount(target.getAmount() - first);
        target.setAmount(first);
        player.getInventory().setItemInOffHand(target);
        player.getInventory().addItem(secondHalf);
        transform(player, knife);
    }

    private void returnHome(final Player player, final TrashRelicBehavior behavior) {
        Location target = player.getRespawnLocation();
        if (target == null) target = player.getWorld().getSpawnLocation();
        final Location destination = target.clone();
        Bukkit.getRegionScheduler().run(plugin, destination, ignored -> {
            if (!safeStand(destination)) return;
            player.teleportAsync(destination).thenAccept(success -> {
                if (!success) return;
                player.getScheduler().run(plugin,
                        second -> transform(player, behavior), null);
            });
        });
    }

    private void openFracture(final Player player, final Block block,
                              final TrashRelicBehavior behavior) {
        if (block == null || !claims.canUse(player.getUniqueId(), block.getLocation())
                || territoryProtection.isTerrainProtectedAt(block.getLocation())) return;
        if (fractures.open(player.getUniqueId(), block, 200L)) transform(player, behavior);
    }

    private void lightningTarget(final Player player, final Block block,
                                 final TrashRelicBehavior behavior) {
        if (block == null || !claims.canUse(player.getUniqueId(), block.getLocation())
                || territoryProtection.isTerrainProtectedAt(block.getLocation())) return;
        final Location target = block.getLocation().add(0.5D, 1.0D, 0.5D);
        target.getWorld().strikeLightningEffect(target);
        int visited = 0;
        for (final Entity nearby : target.getWorld().getNearbyEntities(target, 3.0D, 3.0D, 3.0D)) {
            if (++visited > MAX_NEARBY_ENTITIES || !(nearby instanceof LivingEntity living)) continue;
            living.getScheduler().run(plugin, ignored -> {
                if (living.isValid()) living.damage(8.0D, player);
            }, null);
        }
        transform(player, behavior);
    }

    private void requestBloodMoon(final Player player, final TrashRelicBehavior behavior,
                                  final boolean start) {
        Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> {
            final boolean success;
            if (start) {
                final MajorEventGate gate = MajorEventGate.current();
                success = !bloodMoon.isActive()
                        && (gate == null || gate.mayStartNaturally("blood-moon"))
                        && bloodMoon.forceStart();
            } else {
                success = bloodMoon.isActive() && bloodMoon.forceEnd();
            }
            if (success) player.getScheduler().run(plugin,
                    second -> transform(player, behavior), null);
        });
    }

    private void createProjectileWall(final Player player, final EquipmentSlot hand) {
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return;
        cleanupFields();
        final ItemStack held = itemInHand(player, hand);
        if (behaviorOf(held).orElse(null) != TrashRelicBehavior.TEGLA) return;
        final String existingToken = reservationTokenOf(held);
        if (existingToken != null) {
            if (fields.stream().anyMatch(field -> existingToken.equals(
                    field.reservationToken()))) return;
            clearBrickReservation(player, existingToken);
        }
        final Location playerLocation = player.getLocation();
        if (!hasFieldCapacity(playerLocation)) return;
        try {
            if (!history.individualizeHandOnSuccess(player, hand,
                    TrashHistoryEvent.ACTIVATED)) return;
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
            return;
        }
        final ItemStack reserved = itemInHand(player, hand);
        if (behaviorOf(reserved).orElse(null) != TrashRelicBehavior.TEGLA) return;
        final String token = UUID.randomUUID().toString();
        final var meta = reserved.getItemMeta();
        meta.getPersistentDataContainer().set(brickReservationKey,
                PersistentDataType.STRING, token);
        reserved.setItemMeta(meta);
        items.refreshPresentation(reserved);
        setItemInHand(player, hand, reserved);
        final Location center = playerLocation.clone().add(
                playerLocation.getDirection().normalize().multiply(2.0D));
        final RuleField field = new RuleField(UUID.randomUUID(), FieldKind.PROJECTILE_WALL,
                center, 2.5D, System.currentTimeMillis() + 400L * 50L,
                player.getUniqueId(), token);
        if (!addFieldIfCapacity(field)) clearBrickReservation(player, token);
    }

    private void createField(final Player player, final FieldKind kind, final double radius,
                             final long durationTicks, final double forwardOffset) {
        final Location playerLocation = player.getLocation();
        if (!hasFieldCapacity(playerLocation)) return;
        final Location center = playerLocation.clone();
        if (forwardOffset > 0.0D) {
            center.add(playerLocation.getDirection().normalize().multiply(forwardOffset));
        }
        final RuleField field = new RuleField(UUID.randomUUID(), kind, center, radius,
                System.currentTimeMillis() + durationTicks * 50L, player.getUniqueId(), null);
        if (!addFieldIfCapacity(field)) return;
        if (!transform(player, behaviorFor(kind))) {
            fields.remove(field);
        }
    }

    private void abandonLosingSword(final Player player, final Entity attacker) {
        final int slot = player.getInventory().getHeldItemSlot();
        if (behaviorOf(player.getInventory().getItem(slot)).orElse(null)
                != TrashRelicBehavior.A_KARD_AMELY_MINDEN_CSATAT_MEGNYER) return;
        dropTransformed(player, slot, attacker.getUniqueId());
    }

    private boolean dropTransformed(final Player player, final int slot, final UUID owner) {
        try {
            if (!history.transformInventorySlotOnSuccess(player, slot)) return false;
            final ItemStack remnant = player.getInventory().getItem(slot);
            if (remnant == null || remnant.getType().isAir()) return false;
            player.getInventory().setItem(slot, null);
            final Item dropped = player.getWorld().dropItem(player.getLocation(), remnant);
            if (owner != null) dropped.setOwner(owner);
            return true;
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
            return false;
        }
    }

    private boolean dropTransformedHelmet(final Player player) {
        try {
            if (!history.transformHelmetOnSuccess(player)) return false;
            final ItemStack remnant = player.getInventory().getHelmet();
            if (remnant == null || remnant.getType().isAir()) return false;
            player.getInventory().setHelmet(null);
            player.getWorld().dropItem(player.getLocation(), remnant);
            return true;
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
            return false;
        }
    }

    private void recoverDeathAnchor(final Item item) {
        if (!item.getItemStack().hasItemMeta()) return;
        final Long until = item.getItemStack().getItemMeta().getPersistentDataContainer().get(
                deathAnchorKey, PersistentDataType.LONG);
        if (until == null) return;
        if (until <= System.currentTimeMillis()) {
            clearDeathAnchor(item);
            return;
        }
        item.setVelocity(new Vector());
        item.setInvulnerable(true);
        item.setUnlimitedLifetime(true);
        final long ticks = Math.max(1L, (until - System.currentTimeMillis() + 49L) / 50L);
        item.getScheduler().runDelayed(plugin, ignored -> clearDeathAnchor(item),
                () -> { }, ticks);
    }

    private void clearDeathAnchor(final Item item) {
        if (!item.isValid()) return;
        final ItemStack stack = item.getItemStack();
        if (stack.hasItemMeta()) {
            final var meta = stack.getItemMeta();
            meta.getPersistentDataContainer().remove(deathAnchorKey);
            stack.setItemMeta(meta);
            if (items.isKnownItem(stack)) items.refreshPresentation(stack);
            item.setItemStack(stack);
        }
        item.setInvulnerable(false);
        item.setUnlimitedLifetime(false);
    }

    private boolean transform(final Player player, final TrashRelicBehavior behavior) {
        final int slot = findSlot(player, behavior);
        try {
            return slot >= 0 && history.transformInventorySlotOnSuccess(player, slot);
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
            return false;
        }
    }

    private int findSlot(final Player player, final TrashRelicBehavior behavior) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (behaviorOf(player.getInventory().getItem(slot)).orElse(null) == behavior) return slot;
        }
        return -1;
    }

    private Optional<TrashRelicBehavior> behaviorOf(final ItemStack stack) {
        if (!items.isBaseIdentity(stack)) return Optional.empty();
        final String id = items.idOf(stack).orElse(null);
        if (id == null) return Optional.empty();
        final TrashDefinition definition = catalog.require(id);
        if (definition.internalKind() != TrashKind.TRASH_RELIC) return Optional.empty();
        return Optional.of(TrashRelicBehavior.parse(definition.behavior()));
    }

    private boolean eligibleConsumable(final ItemStack item) {
        // Conservative vanilla-only allowlist: custom/economy/quest consumables all carry meta.
        return item != null && !item.getType().isAir() && !items.isKnownItem(item)
                && !item.hasItemMeta();
    }

    private static int countSimilar(final Player player, final ItemStack sample) {
        int amount = 0;
        for (final ItemStack candidate : player.getInventory().getContents()) {
            if (candidate != null && candidate.isSimilar(sample)) amount += candidate.getAmount();
        }
        return amount;
    }

    private boolean eligibleDeathDrop(final ItemStack item) {
        return item != null && !item.getType().isAir() && (items.isKnownItem(item)
                || !item.hasItemMeta()
                || item.getItemMeta().getPersistentDataContainer().isEmpty());
    }

    private static boolean ceasefireEligible(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return false;
        if (event.getDamager() instanceof LivingEntity) return true;
        return event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity;
    }

    private synchronized RuleField claimField(final Location location, final FieldKind kind) {
        cleanupFields();
        for (final RuleField field : fields) {
            if (field.kind() == kind && contains(field, location)
                    && claimedFields.add(field.id())) {
                if (fields.contains(field)) return field;
                claimedFields.remove(field.id());
            }
        }
        return null;
    }

    private void resolveProjectileWall(final Player owner, final RuleField field,
                                       final Projectile projectile, final Vector priorVelocity) {
        if (!fields.contains(field) || !claimedFields.contains(field.id())
                || !consumeBrickReservation(owner, field.reservationToken())) {
            releaseFieldClaim(field);
            restoreProjectile(projectile, priorVelocity);
            return;
        }
        fields.remove(field);
        claimedFields.remove(field.id());
        projectile.getScheduler().run(plugin, ignored -> {
            if (projectile.isValid()) projectile.remove();
        }, () -> { });
    }

    private boolean consumeBrickReservation(final Player player, final String token) {
        final int slot = findBrickReservation(player, token);
        if (slot < 0) return false;
        try {
            if (!history.transformInventorySlotOnSuccess(player, slot)) return false;
            clearBrickReservation(player, token);
            return true;
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
            return false;
        }
    }

    private void restoreProjectile(final Projectile projectile, final Vector velocity) {
        projectile.getScheduler().run(plugin, ignored -> {
            if (projectile.isValid()) projectile.setVelocity(velocity.clone());
        }, () -> { });
    }

    private void releaseProjectileTracker(final UUID projectileId) {
        if (trackedProjectiles.remove(projectileId)) projectileTrackerPermits.release();
    }

    private void releaseFieldClaim(final RuleField field) {
        claimedFields.remove(field.id());
    }

    private boolean inField(final Location location, final FieldKind kind) {
        cleanupFields();
        return fields.stream().anyMatch(field -> field.kind() == kind && contains(field, location));
    }

    private boolean hasFieldKind(final FieldKind kind) {
        return fields.stream().anyMatch(field -> field.kind() == kind
                && field.expiresAt() > System.currentTimeMillis());
    }

    private synchronized boolean hasFieldCapacity(final Location location) {
        cleanupFields();
        final long worldFields = fields.stream().filter(field ->
                field.center().getWorld().equals(location.getWorld())).count();
        return fields.size() < MAX_FIELDS_GLOBAL && worldFields < MAX_FIELDS_PER_WORLD;
    }

    private synchronized boolean addFieldIfCapacity(final RuleField field) {
        if (!hasFieldCapacity(field.center())) return false;
        fields.add(field);
        return true;
    }

    private synchronized void cleanupFields() {
        final long now = System.currentTimeMillis();
        for (final RuleField field : List.copyOf(fields)) {
            if (field.expiresAt() <= now && !claimedFields.contains(field.id())
                    && fields.remove(field)) releaseReservation(field);
        }
    }

    private void releaseReservation(final RuleField field) {
        if (field.reservationToken() == null) return;
        final Player owner = Bukkit.getPlayer(field.owner());
        if (owner != null) owner.getScheduler().run(plugin,
                ignored -> clearBrickReservation(owner, field.reservationToken()), null);
    }

    private int findBrickReservation(final Player player, final String token) {
        if (token == null) return -1;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            if (token.equals(reservationTokenOf(item))
                    && behaviorOf(item).orElse(null) == TrashRelicBehavior.TEGLA) return slot;
        }
        return -1;
    }

    private void clearBrickReservation(final Player player, final String token) {
        if (token == null) return;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            if (item == null || !token.equals(reservationTokenOf(item))) continue;
            final var meta = item.getItemMeta();
            meta.getPersistentDataContainer().remove(brickReservationKey);
            item.setItemMeta(meta);
            if (items.isKnownItem(item)) items.refreshPresentation(item);
            player.getInventory().setItem(slot, item);
        }
    }

    private void clearStaleBrickReservations(final Player player) {
        final Set<String> active = fields.stream()
                .map(RuleField::reservationToken).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            final String token = reservationTokenOf(item);
            if (token != null && !active.contains(token)) clearBrickReservation(player, token);
        }
    }

    private String reservationTokenOf(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                brickReservationKey, PersistentDataType.STRING);
    }

    private static ItemStack itemInHand(final Player player, final EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }

    private static void setItemInHand(final Player player, final EquipmentSlot hand,
                                      final ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(item);
        else player.getInventory().setItemInMainHand(item);
    }

    private static boolean contains(final RuleField field, final Location location) {
        return location != null && field.center().getWorld().equals(location.getWorld())
                && field.center().distanceSquared(location) <= field.radius() * field.radius();
    }

    private static TrashRelicBehavior behaviorFor(final FieldKind kind) {
        return switch (kind) {
            case ACOUSTIC_NULL -> TrashRelicBehavior.FEKETE_VIASZDUGO;
            case CEASEFIRE -> TrashRelicBehavior.SZAKADT_FEHER_ZASZLO;
            case SPATIAL_ANCHOR -> TrashRelicBehavior.MELYNEPI_SELEJTEK;
            case PROJECTILE_WALL -> TrashRelicBehavior.TEGLA;
        };
    }

    private static boolean safeStand(final Location target) {
        if (target == null || target.getWorld() == null) return false;
        final Block feet = target.getBlock();
        return feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()
                && feet.getRelative(0, -1, 0).getType().isSolid();
    }

    private static boolean rightClick(final Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private enum FieldKind { PROJECTILE_WALL, ACOUSTIC_NULL, CEASEFIRE, SPATIAL_ANCHOR }

    private record RuleField(UUID id, FieldKind kind, Location center, double radius,
                             long expiresAt, UUID owner, String reservationToken) {
        private RuleField {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            center = Objects.requireNonNull(center, "center").clone();
            Objects.requireNonNull(owner, "owner");
        }
    }
}
