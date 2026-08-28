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
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.GenericGameEvent;
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

/** Bounded, typed and protection-aware runtime for the 23 Phase E consuming identities. */
public final class TrashRelicRuntime implements Listener, PlayerStateCleanup {

    private static final int MAX_FIELDS_PER_WORLD = 32;
    private static final int MAX_FIELDS_GLOBAL = 128;
    private static final int MAX_NEARBY_ENTITIES = 24;
    private static final int MAX_ANCHORED_DROPS = 64;
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
    private final Set<UUID> effectVetoArmed = ConcurrentHashMap.newKeySet();
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
    }

    public void start() {
        fractures.recover();
    }

    public void shutdown() {
        fields.clear();
        effectVetoArmed.clear();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        effectVetoArmed.remove(playerId);
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
            case TEGLA -> createField(player, FieldKind.PROJECTILE_WALL, 2.5D, 400L, false,
                    2.0D);
            case FEL_PAR_PAPUCS -> returnHome(player, behavior);
            case KOEK -> openFracture(player, event.getClickedBlock(), behavior);
            case FEKETE_VIASZDUGO ->
                    createField(player, FieldKind.ACOUSTIC_NULL, 6.0D, 200L, true, 0.0D);
            case SZAKADT_FEHER_ZASZLO ->
                    createField(player, FieldKind.CEASEFIRE, 7.0D, 240L, true, 0.0D);
            case KORMOS_SATORSZOG -> lightningTarget(player, event.getClickedBlock(), behavior);
            case MELYNEPI_SELEJTEK ->
                    createField(player, FieldKind.SPATIAL_ANCHOR, 6.0D, 160L, true, 0.0D);
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
        final int helmetSlot = findSlot(player,
                TrashRelicBehavior.A_LEGBIZTONSAGOSABB_SISAK);
        if (helmetSlot >= 0) dropTransformed(player, helmetSlot, null);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        final ItemStack consumed = event.getItem();
        if (!eligibleConsumable(consumed)) return;
        final int slot = findSlot(player, TrashRelicBehavior.REPEDT_BOGRE);
        if (slot < 0 || !history.transformInventorySlotOnSuccess(player, slot)) return;
        final ItemStack preserved = consumed.clone();
        preserved.setAmount(1);
        player.getScheduler().run(plugin, ignored -> player.getInventory().addItem(preserved)
                .values().forEach(overflow -> player.getWorld().dropItemNaturally(
                        player.getLocation(), overflow)), null);
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
        final int[] age = {0};
        projectile.getScheduler().runAtFixedRate(plugin, task -> {
            if (!projectile.isValid() || ++age[0] > 100) {
                task.cancel();
                return;
            }
            final RuleField hit = claimField(projectile.getLocation(), FieldKind.PROJECTILE_WALL);
            if (hit == null) return;
            projectile.remove();
            task.cancel();
            final Player owner = Bukkit.getPlayer(hit.owner());
            if (owner != null) owner.getScheduler().run(plugin,
                    ignored -> transform(owner, TrashRelicBehavior.TEGLA), null);
        }, () -> { }, 1L, 1L);
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

    private void createField(final Player player, final FieldKind kind, final double radius,
                             final long durationTicks, final boolean consume,
                             final double forwardOffset) {
        cleanupFields();
        final Location playerLocation = player.getLocation();
        final long worldFields = fields.stream().filter(field ->
                field.center().getWorld().equals(playerLocation.getWorld())).count();
        if (fields.size() >= MAX_FIELDS_GLOBAL || worldFields >= MAX_FIELDS_PER_WORLD) return;
        final Location center = playerLocation.clone();
        if (forwardOffset > 0.0D) {
            center.add(playerLocation.getDirection().normalize().multiply(forwardOffset));
        }
        final RuleField field = new RuleField(UUID.randomUUID(), kind, center, radius,
                System.currentTimeMillis() + durationTicks * 50L, player.getUniqueId());
        fields.add(field);
        if (consume && !transform(player, behaviorFor(kind))) {
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

    private RuleField claimField(final Location location, final FieldKind kind) {
        cleanupFields();
        for (final RuleField field : fields) {
            if (field.kind() == kind && contains(field, location) && fields.remove(field)) {
                return field;
            }
        }
        return null;
    }

    private boolean inField(final Location location, final FieldKind kind) {
        cleanupFields();
        return fields.stream().anyMatch(field -> field.kind() == kind && contains(field, location));
    }

    private void cleanupFields() {
        final long now = System.currentTimeMillis();
        fields.removeIf(field -> field.expiresAt() <= now);
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
                             long expiresAt, UUID owner) {
        private RuleField {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            center = Objects.requireNonNull(center, "center").clone();
            Objects.requireNonNull(owner, "owner");
        }
    }
}
