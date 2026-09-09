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
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import hu.taliann.icesmp.trash.TrashRuleFieldService.FieldKind;
import hu.taliann.icesmp.trash.TrashRuleFieldService.FieldClaim;
import hu.taliann.icesmp.trash.TrashRuleFieldService.RuleField;
import hu.taliann.icesmp.trash.TrashRuleFieldService.Point;
import hu.taliann.icesmp.trash.TrashRelicPolicy.ProjectileTracking;

/** Bounded, typed and protection-aware runtime for the 23 Phase E consuming identities. */
public final class TrashRelicRuntime implements Listener, PlayerStateCleanup {

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
    private final ProjectileTracking projectileTracking = new ProjectileTracking(MAX_TRACKED_PROJECTILES);
    private final TrashRuleFieldService ruleFields = new TrashRuleFieldService();
    private final TrashRelicActivationService activation;

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
        this.activation = new TrashRelicActivationService(ruleFields, projectileTracking, plugin::isEnabled,
                receipt -> confirmObservedWallRemoval(receipt, 20), telemetry::recordBehaviorRuntimeError);
    }

    public void start() {
        fractures.recover();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            clearReservationsOnOwner(player.getUniqueId());
        }
    }

    public void shutdown() {
        projectileTracking.close();
        for (final RuleField field : ruleFields.close()) releaseReservation(field);
        effectVetoArmed.clear();
        pendingConsumes.clear();
        fractures.shutdown();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        effectVetoArmed.remove(playerId);
        pendingConsumes.remove(playerId);
        for (final RuleField field : ruleFields.snapshot().fields()) {
            if (field.kind() == FieldKind.PROJECTILE_WALL
                    && field.owner().equals(playerId) && ruleFields.remove(field)) {
                releaseReservation(field);
            }
        }
        clearReservationsOnOwner(playerId);
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
        final EquipmentSlot consumedHand = event.getHand();
        final int consumedSlot = consumedHand == EquipmentSlot.OFF_HAND
                ? -1 : player.getInventory().getHeldItemSlot();
        final int equivalentBefore = countSimilar(player, consumed);
        player.getScheduler().run(plugin, ignored -> {
            pendingConsumes.remove(player.getUniqueId());
            if (!TrashRelicPolicy.consumptionCommitted(
                    sameItemInConsumedSlot(player, consumedHand, consumedSlot, consumed),
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(final WorldUnloadEvent event) {
        final UUID worldId = event.getWorld().getUID();
        for (final RuleField field : ruleFields.snapshot().fields()) {
            if (field.center().world().equals(worldId) && ruleFields.remove(field)) releaseReservation(field);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameEvent(final GenericGameEvent event) {
        final Entity source = event.getEntity();
        if (source != null && suppressesAcousticsAt(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(final ProjectileLaunchEvent event) {
        final Projectile projectile = event.getEntity();
        cleanupFields();
        final boolean wallActive = hasFieldKind(FieldKind.PROJECTILE_WALL);
        if (!wallActive) return;
        final ProjectileTracking.Ticket ticket = projectileTracking.admit(projectile.getUniqueId());
        if (ticket == null) return;
        final int[] age = {0};
        try {
            final var scheduled = projectile.getScheduler().runAtFixedRate(plugin, task -> {
                try {
                    if (!projectileTracking.active(ticket) || !projectile.isValid() || ++age[0] > 100) {
                        task.cancel();
                        projectileTracking.release(ticket);
                        return;
                    }
                    final FieldClaim hit = claimField(projectile.getLocation(), FieldKind.PROJECTILE_WALL);
                    if (hit == null) return;
                    boolean transferred = false;
                    try {
                        transferred = dispatchForeignProjectileWall(hit, projectile, ticket);
                        if (transferred) {
                            task.cancel();
                            return;
                        }
                        if (resolveProjectileWall(hit, projectile, ticket)) {
                            task.cancel();
                            projectileTracking.release(ticket);
                        }
                    } finally {
                        if (!transferred) releaseFieldClaim(hit);
                    }
                } catch (final RuntimeException | Error failure) {
                    task.cancel();
                    projectileTracking.release(ticket);
                    throw failure;
                }
            }, () -> projectileTracking.release(ticket), 1L, 1L);
            // A retired scheduler returns null without invoking either callback.
            if (scheduled == null) projectileTracking.release(ticket);
        } catch (final RuntimeException | Error failure) {
            projectileTracking.release(ticket);
            throw failure;
        }
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
        clearStaleBrickReservations(player);
        final ItemStack held = itemInHand(player, hand);
        if (behaviorOf(held).orElse(null) != TrashRelicBehavior.TEGLA) return;
        final String existingToken = reservationTokenOf(held);
        if (existingToken != null) {
            try {
                if (history.tryInspect(held).isEmpty()) return;
            } catch (final RuntimeException rejected) {
                telemetry.recordBehaviorRuntimeError();
                return;
            }
            if (ruleFields.snapshot().fields().stream().anyMatch(field -> existingToken.equals(
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
                point(center), 2.5D, System.currentTimeMillis() + 400L * 50L,
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
        final RuleField field = new RuleField(UUID.randomUUID(), kind, point(center), radius,
                System.currentTimeMillis() + durationTicks * 50L, player.getUniqueId(), null);
        if (!addFieldIfCapacity(field)) return;
        if (!transform(player, behaviorFor(kind))) {
            ruleFields.remove(field);
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

    public TrashRuleFieldService ruleFields() { return ruleFields; }

    private FieldClaim claimField(final Location location, final FieldKind kind) {
        cleanupFields();
        return ruleFields.claim(point(location), kind).orElse(null);
    }

    private boolean resolveProjectileWall(final FieldClaim claim, final Projectile projectile,
                                          final ProjectileTracking.Ticket ticket) {
        final RuleField field = claim.field();
        final Player owner = Bukkit.getPlayer(field.owner());
        final java.util.function.BooleanSupplier admitted = () -> projectileTracking.active(ticket)
                && owner != null && Bukkit.isOwnedByCurrentRegion(owner)
                && Bukkit.isOwnedByCurrentRegion(projectile) && owner.isOnline() && projectile.isValid()
                && owner.getWorld().getUID().equals(field.center().world())
                && ruleFields.contains(field) && ruleFields.isClaimed(claim);
        final var consumed = new java.util.concurrent.atomic.AtomicReference<TrashHistoryStore.WallReceipt>();
        return TrashRelicPolicy.completeProjectileWall(owner != null
                        && Bukkit.isOwnedByCurrentRegion(owner)
                        && Bukkit.isOwnedByCurrentRegion(projectile),
                admitted,
                () -> {
                    consumed.set(consumeBrickReservation(owner, field, projectile.getUniqueId(), admitted));
                    return consumed.get() != null;
                },
                () -> {
                    try {
                        // The consume acknowledgement may arrive after field expiry or shutdown.
                        // Preserve the receipt rather than projecting an effect outside its admission.
                        final var observation = ruleFields.tryObserveClaimedEffect(claim, admitted, () -> {
                            projectile.remove();
                            return !projectile.isValid();
                        });
                        if (observation.isEmpty()) {
                            telemetry.recordBehaviorRuntimeError();
                            return;
                        }
                        final boolean observedRemoved = observation.orElseThrow();
                        if (observedRemoved) confirmObservedWallRemoval(consumed.get(), 20);
                        else telemetry.recordBehaviorRuntimeError();
                    } finally {
                        ruleFields.removeClaimed(claim);
                    }
                });
    }

    private boolean dispatchForeignProjectileWall(final FieldClaim claim, final Projectile projectile,
                                                   final ProjectileTracking.Ticket ticket) {
        if (!Bukkit.isOwnedByCurrentRegion(projectile)) throw new IllegalStateException("Foreign projectile dispatch");
        final RuleField field = claim.field();
        final Player owner = Bukkit.getPlayer(field.owner());
        if (owner == null || Bukkit.isOwnedByCurrentRegion(owner)) return false;
        final UUID projectileId = projectile.getUniqueId();
        final var projectileScheduler = projectile.getScheduler();
        return activation.dispatchWall(claim, ticket, new TrashRelicActivationService.InventoryOwner() {
            @Override public boolean schedule(Runnable action, Runnable retired) {
                return owner.getScheduler().run(plugin, ignored -> action.run(), retired) != null;
            }
            @Override public boolean admitted() {
                return Bukkit.isOwnedByCurrentRegion(owner) && owner.isOnline()
                        && owner.getWorld().getUID().equals(field.center().world());
            }
            @Override public TrashHistoryStore.WallReceipt consume(java.util.function.BooleanSupplier admission) {
                return consumeBrickReservation(owner, field, projectileId, admission);
            }
        }, new TrashRelicActivationService.ProjectileOwner() {
            @Override public boolean schedule(Runnable action, Runnable retired) {
                return projectileScheduler.run(plugin, ignored -> action.run(), retired) != null;
            }
            @Override public boolean admitted() {
                return Bukkit.isOwnedByCurrentRegion(projectile) && projectile.isValid()
                        && field.contains(point(projectile.getLocation()), System.currentTimeMillis());
            }
            @Override public boolean removeObserved() {
                projectile.remove();
                return !projectile.isValid();
            }
        });
    }

    private void confirmObservedWallRemoval(final TrashHistoryStore.WallReceipt receipt, final int retries) {
        if (!plugin.isEnabled() || !projectileTracking.snapshot().open()) return;
        try {
            // Only the preceding owner-local positive observation enters this path. A retry carries
            // immutable native evidence, never an entity handle or an inference from later UUID absence.
            if (history.tryConfirmProjectileWallRemoval(receipt, () -> true)) return;
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
            return;
        }
        if (retries == 0) {
            telemetry.recordBehaviorRuntimeError();
            return;
        }
        try {
            Bukkit.getAsyncScheduler().runDelayed(plugin,
                    ignored -> confirmObservedWallRemoval(receipt, retries - 1), 250,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
        }
    }

    private TrashHistoryStore.WallReceipt consumeBrickReservation(
            final Player player, final RuleField field, final UUID projectileId,
            final java.util.function.BooleanSupplier admitted) {
        final String token = field.reservationToken();
        final int slot = findBrickReservation(player, token);
        if (slot < 0) return null;
        final TrashHistoryStore.WallReceipt receipt;
        try {
            receipt = history.tryConsumeProjectileWall(player, slot, field, projectileId, admitted).orElse(null);
            if (receipt == null) return null;
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
            return null;
        }
        // Marker cleanup must not turn an acknowledged consuming transition into a retry.
        try {
            clearBrickReservation(player, token);
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
        }
        return receipt;
    }

    TrashRelicPolicy.TrackingSnapshot projectileTrackingState() { return projectileTracking.snapshot(); }

    private void releaseFieldClaim(final FieldClaim claim) {
        ruleFields.releaseClaim(claim);
    }

    private boolean inField(final Location location, final FieldKind kind) {
        cleanupFields();
        return location != null && location.getWorld() != null && ruleFields.activeAt(point(location), kind);
    }

    private boolean hasFieldKind(final FieldKind kind) { return ruleFields.hasKind(kind); }

    private boolean hasFieldCapacity(final Location location) {
        cleanupFields();
        return ruleFields.hasCapacity(point(location).world());
    }

    private boolean addFieldIfCapacity(final RuleField field) {
        cleanupFields();
        return ruleFields.add(field);
    }

    private void cleanupFields() {
        for (final RuleField field : ruleFields.expire()) releaseReservation(field);
    }

    private void releaseReservation(final RuleField field) {
        if (field.reservationToken() == null) return;
        clearReservationsOnOwner(field.owner());
    }

    private void clearReservationsOnOwner(final UUID ownerId) {
        final Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;
        final Runnable cleanup = () -> clearStaleBrickReservations(owner);
        if (Bukkit.isOwnedByCurrentRegion(owner)) {
            cleanup.run();
            return;
        }
        if (!plugin.isEnabled()) return;
        try {
            owner.getScheduler().run(plugin, ignored -> cleanup.run(), null);
        } catch (final IllegalPluginAccessException disabled) {
            // The marker grants no authority; join/start/use rechecks the native field registry.
        }
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
        if (!Bukkit.isOwnedByCurrentRegion(player)) throw new IllegalStateException("Foreign wall recovery inventory");
        final var pending = history.tryInspectPendingProjectileWalls();
        if (pending.isEmpty()) return;
        final java.util.Map<UUID, Integer> slots = new java.util.HashMap<>();
        final Set<UUID> duplicates = new java.util.HashSet<>();
        try {
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                final ItemStack item = player.getInventory().getItem(slot);
                if (item == null) continue;
                final UUID instance = history.instanceIdOf(item).orElse(null);
                if (instance != null && slots.putIfAbsent(instance, slot) != null) duplicates.add(instance);
            }
        } catch (final RuntimeException rejected) {
            telemetry.recordBehaviorRuntimeError();
            return;
        }
        final var recovery = history.tryInspectWallRecoveryReceipts(slots.keySet());
        if (recovery.isEmpty()) return;
        for (final var receipt : recovery.orElseThrow().values()) {
            if (!receipt.actor().equals(player.getUniqueId()) || duplicates.contains(receipt.instanceId())) continue;
            final Integer slot = slots.get(receipt.instanceId());
            if (slot == null) continue;
            try {
                restoreAcknowledgedBrickSlot(player, slot, receipt);
            } catch (final RuntimeException rejected) {
                telemetry.recordBehaviorRuntimeError();
            }
        }
        final Set<String> active = ruleFields.snapshot().fields().stream()
                .map(RuleField::reservationToken).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        pending.orElseThrow().forEach(receipt -> active.add(receipt.field().reservationToken()));
        recovery.orElseThrow().values().forEach(receipt -> active.add(receipt.field().reservationToken()));
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            final String token = reservationTokenOf(item);
            if (token != null && !active.contains(token)) clearBrickReservation(player, token);
        }
    }

    private void restoreAcknowledgedBrickSlot(final Player player, final int slot,
                                               final TrashHistoryStore.WallReceipt receipt) {
        if (!Bukkit.isOwnedByCurrentRegion(player) || !player.isOnline()) return;
        final ItemStack before = player.getInventory().getItem(slot).clone();
        if (!receipt.field().reservationToken().equals(reservationTokenOf(before))) return;
        if (history.tryRestoreAcknowledgedWallProjection(before, player.getUniqueId(), receipt,
                () -> Bukkit.isOwnedByCurrentRegion(player) && player.isOnline()
                        && before.equals(player.getInventory().getItem(slot)),
                restored -> player.getInventory().setItem(slot, restored))) {
            clearBrickReservation(player, receipt.field().reservationToken());
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

    private static boolean sameItemInConsumedSlot(final Player player,
                                                  final EquipmentSlot hand,
                                                  final int mainHandSlot,
                                                  final ItemStack consumed) {
        final ItemStack current = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItem(mainHandSlot);
        return current != null && current.isSimilar(consumed);
    }

    private static void setItemInHand(final Player player, final EquipmentSlot hand,
                                      final ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(item);
        else player.getInventory().setItemInMainHand(item);
    }

    private static Point point(final Location location) {
        return new Point(Objects.requireNonNull(location.getWorld()).getUID(), location.getX(), location.getY(), location.getZ());
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

}
