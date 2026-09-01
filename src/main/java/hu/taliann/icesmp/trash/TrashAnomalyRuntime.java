package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.TerritoryProtectionService;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import io.papermc.paper.entity.LookAnchor;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded Folia-safe Phase D runtime for every authored Anomaly behavior. */
public final class TrashAnomalyRuntime implements Listener, PlayerStateCleanup {

    private static final int MAX_ACTIVE_PHYSICS_PER_WORLD = 256;
    private static final int MAX_PENDING_ECHOES = 256;
    private static final int MAX_RECENT_SOUNDS = 256;
    private static final long SOUND_CAPTURE_MAX_AGE_MILLIS = 5_000L;
    private static final double SOUND_CAPTURE_RADIUS_SQUARED = 144.0D;
    private static final double MAX_SEEK_RADIUS = 12.0D;
    private static final int MAX_NEARBY_ENTITIES = 24;
    private static final Set<TrashAnomalyBehavior> PHYSICS = EnumSet.of(
            TrashAnomalyBehavior.MELYNEPI_CSAPAGYGOLYO,
            TrashAnomalyBehavior.TUL_NEHEZ_ALATET,
            TrashAnomalyBehavior.FELFELE_HULLO_HOPEHELY,
            TrashAnomalyBehavior.BOKICNAK_ELLENTMONDO_LEVEL,
            TrashAnomalyBehavior.URES_ERSZENY,
            TrashAnomalyBehavior.VERFA_SZALKAJA,
            TrashAnomalyBehavior.RADICORAI_ARNYEKSZALKA,
            TrashAnomalyBehavior.UDVARI_GOMB,
            TrashAnomalyBehavior.SZAKADT_HADIJEL,
            TrashAnomalyBehavior.SARKANYISTALLO_CSATJA,
            TrashAnomalyBehavior.EZUSTOZOTT_KANAL,
            TrashAnomalyBehavior.BAL_ZOKNI,
            TrashAnomalyBehavior.JOBB_ZOKNI,
            TrashAnomalyBehavior.BAL_LANCSZEM,
            TrashAnomalyBehavior.JOBB_LANCSZEM,
            TrashAnomalyBehavior.PORTALKOROM);
    private static final Set<TrashAnomalyBehavior> TOSS = EnumSet.of(
            TrashAnomalyBehavior.FELREVERT_GARAS,
            TrashAnomalyBehavior.HETPARTOS_KAVICS,
            TrashAnomalyBehavior.HAZUDNI_NEM_TUDO_DOBOKOCKA);
    private static final Set<Material> HEAT_SOURCES = EnumSet.of(
            Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.LAVA, Material.MAGMA_BLOCK);
    private static final Set<Material> MECHANISMS = EnumSet.of(
            Material.OBSERVER, Material.PISTON, Material.STICKY_PISTON, Material.DISPENSER,
            Material.DROPPER, Material.HOPPER, Material.NOTE_BLOCK, Material.REDSTONE_LAMP,
            Material.COPPER_BULB, Material.IRON_DOOR, Material.IRON_TRAPDOOR);
    private static final List<String> WHISPERS = List.of(
            "Nem innen fúj a szél.", "Valaki mögötted lapozott.",
            "A tinta még emlékszik.", "Ez a sor tegnap nem volt itt.");

    private final JavaPlugin plugin;
    private final TrashCatalog catalog;
    private final TrashItemFactory items;
    private final TrashHistoryService history;
    private final TrashAnomalyStateStore memory;
    private final TossableObjectRuntime tosses;
    private final CurrencyManager currency;
    private final FactionManager factions;
    private final KingManager kings;
    private final ClaimManager claims;
    private final TerritoryProtectionService territoryProtection;
    private final NamespacedKey runtimeStateKey;
    private final Set<UUID> activePhysics = ConcurrentHashMap.newKeySet();
    private final Set<UUID> runtimeStateEntities = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AtomicInteger> activeByWorld = new ConcurrentHashMap<>();
    private final Set<UUID> pendingEchoes = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedDeque<CapturedSound> recentSounds =
            new ConcurrentLinkedDeque<>();
    private final Set<UUID> pairReservations = ConcurrentHashMap.newKeySet();
    private final Set<CompassProjection> compassProjections = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> whisperCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> presentationCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> silentEventUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Double> physicsOriginY = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> physicsWorldByItem = new ConcurrentHashMap<>();
    private ScheduledTask heldTick;

    public TrashAnomalyRuntime(final JavaPlugin plugin, final TrashCatalog catalog,
                               final TrashItemFactory items, final TrashHistoryService history,
                               final TrashAnomalyStateStore memory,
                               final TossableObjectRuntime tosses,
                               final CurrencyManager currency, final FactionManager factions,
                               final KingManager kings, final ClaimManager claims,
                               final TerritoryProtectionService territoryProtection) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.items = Objects.requireNonNull(items, "items");
        this.history = Objects.requireNonNull(history, "history");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.tosses = Objects.requireNonNull(tosses, "tosses");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.factions = Objects.requireNonNull(factions, "factions");
        this.kings = Objects.requireNonNull(kings, "kings");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.territoryProtection = Objects.requireNonNull(territoryProtection,
                "territoryProtection");
        this.runtimeStateKey = new NamespacedKey(plugin, "trash_runtime_state");
    }

    public void start() {
        if (heldTick != null) return;
        heldTick = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            for (final Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(plugin, ignored -> tickHeldItems(player), null);
            }
        }, 20L, 20L);
    }

    public void shutdown() {
        if (heldTick != null) {
            heldTick.cancel();
            heldTick = null;
        }
        tosses.shutdown();
        for (final UUID entityId : Set.copyOf(activePhysics)) {
            final Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof Item item) {
                item.getScheduler().run(plugin, ignored -> {
                    item.setGravity(true);
                    releasePhysics(entityId);
                }, () -> releasePhysics(entityId));
            } else {
                releasePhysics(entityId);
            }
        }
        activePhysics.clear();
        runtimeStateEntities.clear();
        activeByWorld.clear();
        pendingEchoes.clear();
        recentSounds.clear();
        pairReservations.clear();
        for (final CompassProjection projection : Set.copyOf(compassProjections)) {
            final Player player = Bukkit.getPlayer(projection.playerId());
            if (player != null) player.getScheduler().run(plugin,
                    ignored -> resyncCompassProjection(player, projection.hand()), null);
        }
        compassProjections.clear();
        whisperCooldown.clear();
        presentationCooldown.clear();
        silentEventUntil.clear();
        physicsOriginY.clear();
        physicsWorldByItem.clear();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        whisperCooldown.remove(playerId);
        presentationCooldown.remove(playerId);
        silentEventUntil.remove(playerId);
        compassProjections.removeIf(projection -> projection.playerId().equals(playerId));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(final PlayerInteractEvent event) {
        if (event.getHand() == null || event.getItem() == null) return;
        final TrashAnomalyBehavior behavior = behaviorOf(event.getItem()).orElse(null);
        if (behavior == null) return;
        final Player player = event.getPlayer();
        if (TOSS.contains(behavior) && isRightClick(event.getAction())) {
            if (behavior == TrashAnomalyBehavior.HETPARTOS_KAVICS
                    && !hasWaterSkipPath(player)) return;
            event.setUseItemInHand(Event.Result.DENY);
            tosses.toss(player, event.getItem(), behavior);
            return;
        }
        switch (behavior) {
            case TOROTT_IRANYTU -> projectOppositeDirection(
                    player, event.getHand(), event.getItem());
            case FAGYOTT_TINTAS_CETLI -> {
                if (isCold(player.getLocation())) quietActionBar(player, "A tinta lassan előkúszik a papíron.");
            }
            case SZARAZ_GYUFA -> {
                if (player.isInWaterOrRain()) quietActionBar(player, "Száraz.");
            }
            case TOROTT_HADISIP -> lateWhistle(player);
            case URES_CSONTZACSKO -> {
                if (isRightClick(event.getAction())) {
                    event.setUseItemInHand(Event.Result.DENY);
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BONE_BLOCK_HIT,
                            0.7F, 0.75F);
                }
            }
            case JEGMEZOI_CSENGONYELV -> {
                if (isRightClick(event.getAction()) && !isCold(player.getLocation())) {
                    event.setUseItemInHand(Event.Result.DENY);
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BELL_USE,
                            0.75F, 1.15F);
                }
            }
            case CSENDVERTE_CSENGONYELV -> {
                if (isRightClick(event.getAction())) {
                    silentEventUntil.put(player.getUniqueId(), System.currentTimeMillis() + 300L);
                    event.setUseItemInHand(Event.Result.DENY);
                }
            }
            case ELKESO_VISSZHANGDARAB -> {
                if (isRightClick(event.getAction())) scheduleEcho(player, event.getHand());
            }
            case MELYNEPI_KIEGETT_BIZTOSITEK, MELYNEPI_VAKLENCSE -> {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    attachMechanism(player, event.getHand(), event.getClickedBlock(), behavior);
                    event.setUseItemInHand(Event.Result.DENY);
                }
            }
            case SUTTOGO_CETLI -> whisperInDarkness(player);
            case A_KRUMPLI_AMI_NEM_AKAR_ELULTETODNI -> {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                        && event.getClickedBlock().getType() == Material.FARMLAND) {
                    event.setCancelled(true);
                }
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFrameInteract(final PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;
        final Player player = event.getPlayer();
        final TrashAnomalyBehavior held = behaviorOf(itemInHand(event.getPlayer(), event.getHand()))
                .orElse(null);
        if (held == TrashAnomalyBehavior.BALMENETES_CSAVAR) {
            event.setCancelled(true);
            frame.getScheduler().run(plugin, ignored ->
                    frame.setRotation(frame.getRotation().rotateCounterClockwise()), null);
            return;
        }
        frame.getScheduler().run(plugin, ignored -> {
            final ItemStack framed = frame.getItem();
            if (behaviorOf(framed).orElse(null)
                    != TrashAnomalyBehavior.CSONTSZAMVEVO_CERUZACSONKJA) return;
            final UUID instanceId = history.instanceIdOf(framed).orElse(null);
            if (instanceId == null) return;
            final long deaths = memory.get(instanceId,
                    TrashAnomalyStateStore.MemoryKey.LOCAL_PLAYER_DEATHS);
            player.getScheduler().run(plugin,
                    second -> quietActionBar(player, Long.toString(deaths)), null);
        }, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        final Item dropped = event.getItemDrop();
        final UUID playerId = event.getPlayer().getUniqueId();
        final FactionType faction = factions.getChosenFaction(playerId).orElse(null);
        dropped.getScheduler().run(plugin, ignored -> {
            final TrashAnomalyBehavior behavior = behaviorOf(dropped.getItemStack()).orElse(null);
            if (behavior == null) return;
            if (behavior == TrashAnomalyBehavior.CSENDVERTE_CSENGONYELV) dropped.setSilent(true);
            if (behavior == TrashAnomalyBehavior.NEVTELEN_DOGCEDULA) {
            if (faction == FactionType.RED) dropped.setRotation(90.0F, 0.0F);
            if (faction == FactionType.BLUE) dropped.setRotation(-90.0F, 0.0F);
            }
            if (behavior == TrashAnomalyBehavior.GORBE_SATORSZOG) {
                prepareReturningDrop(playerId, dropped);
            }
        }, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(final ItemSpawnEvent event) {
        final Item item = event.getEntity();
        if (hasRuntimeState(item)) runtimeStateEntities.add(item.getUniqueId());
        final TrashAnomalyBehavior behavior = behaviorOf(item.getItemStack()).orElse(null);
        if (behavior == null) return;
        if (behavior == TrashAnomalyBehavior.CSENDVERTE_CSENGONYELV) item.setSilent(true);
        if (PHYSICS.contains(behavior)) startPhysics(item, behavior);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(final EntitiesLoadEvent event) {
        int visited = 0;
        for (final Entity entity : event.getEntities()) {
            if (++visited > MAX_ACTIVE_PHYSICS_PER_WORLD || !(entity instanceof Item item)) continue;
            item.getScheduler().run(plugin, ignored -> {
                if (hasRuntimeState(item)) runtimeStateEntities.add(item.getUniqueId());
                final TrashAnomalyBehavior behavior = behaviorOf(item.getItemStack()).orElse(null);
                if (behavior == null) return;
                if (PHYSICS.contains(behavior)) startPhysics(item, behavior);
                if (behavior == TrashAnomalyBehavior.GORBE_SATORSZOG
                        && item.getOwner() != null) {
                    item.getScheduler().runDelayed(plugin,
                            task -> returnToOwner(item), null, 20L);
                }
            }, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(final EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Item item)
                || behaviorOf(item.getItemStack()).orElse(null)
                != TrashAnomalyBehavior.KIKOPOTT_OBSZIDIANSZILANK) return;
        event.setCancelled(true);
        item.setVelocity(item.getVelocity().multiply(-0.55D).setY(0.18D));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)
                || behaviorOf(item.getItemStack()).orElse(null)
                != TrashAnomalyBehavior.ELSZENESEDETT_VERFAAG) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
            item.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAutomationMove(final InventoryMoveItemEvent event) {
        final TrashAnomalyBehavior behavior = behaviorOf(event.getItem()).orElse(null);
        if (behavior == TrashAnomalyBehavior.BOTERAI_NE_VIDD_CIMKE
                || behavior == TrashAnomalyBehavior.A_PENZTAR_UTOLSO_GARASA
                && lastEligibleStack(event.getSource(), event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAutomationPickup(final InventoryPickupItemEvent event) {
        if (behaviorOf(event.getItem().getItemStack()).orElse(null)
                == TrashAnomalyBehavior.BOTERAI_NE_VIDD_CIMKE) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        final ItemStack current = event.getCurrentItem();
        if (event.getRawSlot() >= 0
                && event.getRawSlot() < event.getView().getTopInventory().getSize()
                && behaviorOf(current).orElse(null)
                == TrashAnomalyBehavior.A_PENZTAR_UTOLSO_GARASA
                && lastEligibleStack(event.getView().getTopInventory(), current)) {
            event.setCancelled(true);
        }
        player.getScheduler().runDelayed(plugin, ignored -> reconcileSerial(player), null, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) reconcileSerial(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        reconcileSerial(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstone(final BlockRedstoneEvent event) {
        invertComparator(event);
        if (event.getNewCurrent() <= event.getOldCurrent()) return;
        consumeAttachedMechanism(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final UUID deadPlayerId = player.getUniqueId();
        int visited = 0;
        for (final Entity nearby : player.getNearbyEntities(12.0D, 12.0D, 12.0D)) {
            if (++visited > MAX_NEARBY_ENTITIES || !(nearby instanceof ItemFrame frame)) continue;
            frame.getScheduler().run(plugin, ignored -> {
                if (!frame.isValid() || frame.getItem().getAmount() != 1
                        || behaviorOf(frame.getItem()).orElse(null)
                        != TrashAnomalyBehavior.CSONTSZAMVEVO_CERUZACSONKJA) return;
                ItemStack framed = frame.getItem().clone();
                history.individualizeUnit(framed, TrashHistoryEvent.PRESENT_AT_PLAYER_DEATH,
                        deadPlayerId, "");
                frame.setItem(framed, false);
                history.instanceIdOf(framed).ifPresent(instance -> memory.addDurably(instance,
                        TrashAnomalyStateStore.MemoryKey.LOCAL_PLAYER_DEATHS, 1L));
            }, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameEvent(final GenericGameEvent event) {
        final Entity source = event.getEntity();
        if (!(source instanceof Player player)) return;
        if (silentEventUntil.getOrDefault(player.getUniqueId(), 0L)
                >= System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEligibleBlockBreakSound(final BlockBreakEvent event) {
        captureSound(event.getBlock().getLocation(),
                event.getBlock().getBlockData().getSoundGroup().getBreakSound(), 1.0F, 0.8F);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEligibleBlockPlaceSound(final BlockPlaceEvent event) {
        captureSound(event.getBlockPlaced().getLocation(),
                event.getBlockPlaced().getBlockData().getSoundGroup().getPlaceSound(), 1.0F, 0.8F);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEligibleConsumeSound(final PlayerItemConsumeEvent event) {
        captureSound(event.getPlayer().getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEligibleProjectileSound(final ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.AbstractArrow)) return;
        captureSound(event.getEntity().getLocation(), Sound.ENTITY_ARROW_HIT, 1.0F, 1.0F);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerge(final ItemMergeEvent event) {
        if (TrashAnomalyPolicy.blocksGroundMerge(hasRuntimeState(event.getEntity()),
                runtimeStateEntities.contains(event.getTarget().getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerged(final ItemMergeEvent event) {
        releasePhysics(event.getEntity().getUniqueId());
        runtimeStateEntities.remove(event.getEntity().getUniqueId());
    }

    private boolean hasRuntimeState(final Item item) {
        return item.getPersistentDataContainer().has(runtimeStateKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDespawn(final ItemDespawnEvent event) {
        releasePhysics(event.getEntity().getUniqueId());
        runtimeStateEntities.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        final Item item = event.getItem();
        final UUID itemId = item.getUniqueId();
        runtimeStateEntities.remove(itemId);
        item.getScheduler().run(plugin, ignored -> releasePhysicsAndRestore(item),
                () -> releasePhysics(itemId));
    }

    private void tickHeldItems(final Player player) {
        for (final EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            final ItemStack stack = itemInHand(player, hand);
            final TrashAnomalyBehavior behavior = behaviorOf(stack).orElse(null);
            if (behavior == TrashAnomalyBehavior.TOROTT_IRANYTU) {
                projectOppositeDirection(player, hand, stack);
                continue;
            }
            clearCompassProjection(player, hand);
            if (behavior == TrashAnomalyBehavior.TOROTT_ZSEBORA) {
                final ItemStack singleton = ensureSingletonInHand(player, hand,
                        TrashHistoryEvent.ACTIVATED);
                final UUID instance = singleton == null ? null : history.instanceIdOf(singleton).orElse(null);
                if (instance != null) {
                    final long ticks = memory.add(instance,
                            TrashAnomalyStateStore.MemoryKey.WATCHED_TICKS, 20L);
                    quietActionBar(player, formatWatch(ticks));
                }
            } else if (behavior == TrashAnomalyBehavior.FAGYOTT_TINTAS_CETLI
                    && isCold(player.getLocation())) {
                quietActionBar(player, "A tinta lassan előkúszik a papíron.");
            } else if (behavior == TrashAnomalyBehavior.SUTTOGO_CETLI) {
                whisperInDarkness(player);
            }
        }
    }

    private void startPhysics(final Item item, final TrashAnomalyBehavior behavior) {
        final UUID itemId = item.getUniqueId();
        final UUID worldId = item.getWorld().getUID();
        final AtomicInteger count = activeByWorld.computeIfAbsent(worldId, ignored -> new AtomicInteger());
        if (count.incrementAndGet() > MAX_ACTIVE_PHYSICS_PER_WORLD) {
            count.decrementAndGet();
            return;
        }
        if (!activePhysics.add(itemId)) {
            count.decrementAndGet();
            return;
        }
        physicsWorldByItem.put(itemId, worldId);
        if (behavior == TrashAnomalyBehavior.FELFELE_HULLO_HOPEHELY) item.setGravity(false);
        physicsOriginY.put(itemId, item.getLocation().getY());
        final int[] age = {0};
        try {
            item.getScheduler().runAtFixedRate(plugin, task -> {
                if (!item.isValid() || ++age[0] > 600
                        || behaviorOf(item.getItemStack()).orElse(null) != behavior) {
                    task.cancel();
                    releasePhysicsAndRestore(item);
                    return;
                }
                tickPhysics(item, behavior, age[0]);
            }, () -> releasePhysics(itemId), 1L, 2L);
        } catch (final RuntimeException rejected) {
            releasePhysicsAndRestore(item);
        }
    }

    private void tickPhysics(final Item item, final TrashAnomalyBehavior behavior, final int age) {
        switch (behavior) {
            case MELYNEPI_CSAPAGYGOLYO -> lowDrag(item);
            case TUL_NEHEZ_ALATET -> heavy(item);
            case FELFELE_HULLO_HOPEHELY -> rise(item);
            case BOKICNAK_ELLENTMONDO_LEVEL -> opposeWater(item);
            case URES_ERSZENY -> {
                if (age % 4 == 0) attractCurrency(item);
            }
            case VERFA_SZALKAJA -> {
                if (age % 10 == 0) seekBlock(item, HEAT_SOURCES, 4, 3);
            }
            case RADICORAI_ARNYEKSZALKA -> {
                if (age % 10 == 0) seekShadow(item);
            }
            case UDVARI_GOMB, SZAKADT_HADIJEL, SARKANYISTALLO_CSATJA -> {
                if (age % 20 == 0) historicalReaction(item, behavior);
            }
            case EZUSTOZOTT_KANAL -> {
                if (age % 10 == 0) seekKing(item);
            }
            case BAL_ZOKNI, JOBB_ZOKNI, BAL_LANCSZEM, JOBB_LANCSZEM -> {
                if (age % 2 == 0) pair(item, behavior);
            }
            case PORTALKOROM -> {
                if (age % 10 == 0) seekBlock(item, Set.of(Material.NETHER_PORTAL), 5, 3);
            }
            default -> { }
        }
    }

    private void lowDrag(final Item item) {
        if (!item.isOnGround()) return;
        final Vector velocity = item.getVelocity();
        final Vector horizontal = velocity.clone().setY(0.0D);
        if (horizontal.lengthSquared() < 0.0025D) return;
        final Location ahead = item.getLocation().add(horizontal.clone().normalize().multiply(0.6D));
        if (!ahead.getWorld().isChunkLoaded(ahead.getBlockX() >> 4, ahead.getBlockZ() >> 4)) {
            item.setVelocity(new Vector());
            return;
        }
        if (!ahead.getBlock().isPassable()
                || !ahead.clone().subtract(0.0D, 0.55D, 0.0D).getBlock().getType().isSolid()) {
            item.setVelocity(new Vector());
            return;
        }
        item.setVelocity(horizontal.normalize().multiply(Math.min(0.42D,
                Math.max(0.12D, horizontal.length() * 0.995D))).setY(0.01D));
    }

    private void heavy(final Item item) {
        final Vector velocity = item.getVelocity();
        final double pull = item.isInWater() ? 0.24D : 0.13D;
        item.setVelocity(velocity.setY(Math.max(-1.8D, velocity.getY() - pull)));
    }

    private void rise(final Item item) {
        final Location location = item.getLocation();
        final Location above = location.clone().add(0.0D, 1.1D, 0.0D);
        final boolean ceiling = above.getWorld().isChunkLoaded(above.getBlockX() >> 4,
                above.getBlockZ() >> 4) && !above.getBlock().isPassable();
        final double originY = physicsOriginY.getOrDefault(item.getUniqueId(), location.getY());
        if (ceiling || location.getY() >= originY + 8.0D
                || location.getY() > item.getWorld().getMaxHeight() - 2.0D) {
            item.setVelocity(new Vector());
        } else {
            item.setVelocity(item.getVelocity().multiply(0.72D).setY(0.16D));
        }
    }

    private void opposeWater(final Item item) {
        if (!item.isInWater()) return;
        final Block center = item.getLocation().getBlock();
        final Vector flow = new Vector();
        for (final org.bukkit.block.BlockFace face : List.of(org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.WEST)) {
            final Block neighbor = center.getRelative(face);
            if (!sameChunk(center, neighbor) || neighbor.getType() != Material.WATER) continue;
            final int level = neighbor.getBlockData() instanceof Levelled water ? water.getLevel() : 0;
            flow.add(face.getDirection().multiply(8 - Math.min(8, level)));
        }
        if (flow.lengthSquared() > 0.001D) {
            item.setVelocity(flow.normalize().multiply(-0.11D).setY(item.getVelocity().getY()));
        }
    }

    private void attractCurrency(final Item source) {
        int visited = 0;
        final Location target = source.getLocation();
        for (final Entity nearby : source.getNearbyEntities(MAX_SEEK_RADIUS, 5.0D, MAX_SEEK_RADIUS)) {
            if (++visited > MAX_NEARBY_ENTITIES) break;
            if (!(nearby instanceof Item coin)) continue;
            coin.getScheduler().run(plugin, ignored -> {
                if (!coin.isValid() || !currency.isCurrencyItem(coin.getItemStack())) return;
                final Vector direction = target.toVector().subtract(coin.getLocation().toVector());
                if (direction.lengthSquared() > 0.09D) {
                    coin.setVelocity(coin.getVelocity().multiply(0.65D)
                            .add(direction.normalize().multiply(0.055D)));
                }
            }, null);
        }
    }

    private void seekShadow(final Item item) {
        final Block origin = item.getLocation().getBlock();
        Block best = null;
        int bestLight = origin.getLightFromSky();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                final Block candidate = origin.getRelative(dx, 0, dz);
                if (!sameChunk(origin, candidate) || !candidate.isPassable()) continue;
                if (candidate.getLightFromSky() < bestLight) {
                    bestLight = candidate.getLightFromSky();
                    best = candidate;
                }
            }
        }
        if (best != null) steer(item, best.getLocation().add(0.5D, 0.2D, 0.5D), 0.055D);
    }

    private void seekBlock(final Item item, final Set<Material> targets,
                           final int horizontalRadius, final int verticalRadius) {
        final Block origin = item.getLocation().getBlock();
        Block nearest = null;
        double distance = Double.MAX_VALUE;
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    final Block candidate = origin.getRelative(dx, dy, dz);
                    if (!sameChunk(origin, candidate) || !targets.contains(candidate.getType())) continue;
                    final double current = candidate.getLocation().distanceSquared(item.getLocation());
                    if (current < distance) {
                        nearest = candidate;
                        distance = current;
                    }
                }
            }
        }
        if (nearest != null) steer(item, nearest.getLocation().add(0.5D, 0.4D, 0.5D), 0.065D);
    }

    private void seekKing(final Item item) {
        int visited = 0;
        final AtomicBoolean claimed = new AtomicBoolean();
        for (final Entity nearby : item.getNearbyEntities(MAX_SEEK_RADIUS, 6.0D, MAX_SEEK_RADIUS)) {
            if (++visited > MAX_NEARBY_ENTITIES) break;
            if (!(nearby instanceof Player player)) continue;
            player.getScheduler().run(plugin, ignored -> {
                if (!player.isOnline() || !kings.isKing(player) || !claimed.compareAndSet(false, true)) return;
                final Location target = player.getLocation().clone();
                item.getScheduler().run(plugin, second -> {
                    if (item.isValid()) steer(item, target, 0.05D);
                }, null);
            }, null);
        }
    }

    private void historicalReaction(final Item item, final TrashAnomalyBehavior behavior) {
        int visited = 0;
        final Location itemLocation = item.getLocation().clone();
        for (final Entity nearby : item.getNearbyEntities(6.0D, 4.0D, 6.0D)) {
            if (++visited > MAX_NEARBY_ENTITIES) break;
            if (!(nearby instanceof LivingEntity living)) continue;
            living.getScheduler().run(plugin, ignored -> {
                if (!living.isValid()) return;
                final boolean eligible = switch (behavior) {
                    case UDVARI_GOMB -> living instanceof Monster
                            && MobScalingManager.templateIdOf(living) != null;
                    case SZAKADT_HADIJEL -> living instanceof Monster
                            && authoredWarUndead(MobScalingManager.templateIdOf(living));
                    case SARKANYISTALLO_CSATJA -> living instanceof Animals
                            || living instanceof AbstractHorse;
                    default -> false;
                };
                if (!eligible) return;
                if (behavior == TrashAnomalyBehavior.SARKANYISTALLO_CSATJA) {
                    final Vector away = living.getLocation().toVector().subtract(itemLocation.toVector());
                    if (away.lengthSquared() > 0.01D) {
                        living.setVelocity(living.getVelocity().add(away.normalize().multiply(0.08D)));
                    }
                } else {
                    living.lookAt(itemLocation, LookAnchor.EYES);
                }
            }, null);
        }
    }

    private void pair(final Item source, final TrashAnomalyBehavior behavior) {
        if (!pairReservations.add(source.getUniqueId())) return;
        final TrashAnomalyBehavior opposite = opposite(behavior);
        final List<Item> candidates = new java.util.ArrayList<>();
        int visited = 0;
        for (final Entity nearby : source.getNearbyEntities(3.0D, 2.0D, 3.0D)) {
            if (++visited > MAX_NEARBY_ENTITIES) break;
            if (nearby instanceof Item item
                    && pairReservations.add(item.getUniqueId())) candidates.add(item);
        }
        if (candidates.isEmpty()) {
            pairReservations.remove(source.getUniqueId());
            return;
        }
        final AtomicBoolean claimed = new AtomicBoolean();
        final AtomicInteger pending = new AtomicInteger(candidates.size());
        for (final Item candidate : candidates) {
            try {
                candidate.getScheduler().run(plugin, ignored -> {
                    if (!candidate.isValid()
                            || behaviorOf(candidate.getItemStack()).orElse(null) != opposite
                            || !claimed.compareAndSet(false, true)) {
                        releasePairProbe(source, candidate, claimed, pending);
                        return;
                    }
                    pending.decrementAndGet();
                    beginPair(source, candidate, behavior, opposite);
                }, () -> releasePairProbe(source, candidate, claimed, pending));
            } catch (final RuntimeException rejected) {
                releasePairProbe(source, candidate, claimed, pending);
            }
        }
    }

    private void beginPair(final Item source, final Item counterpart,
                           final TrashAnomalyBehavior behavior,
                           final TrashAnomalyBehavior opposite) {
        if (!counterpart.isValid()
                || behaviorOf(counterpart.getItemStack()).orElse(null) != opposite) {
            releasePair(source, counterpart);
            return;
        }
        final Location target = counterpart.getLocation().clone();
        try {
            source.getScheduler().run(plugin, second -> {
                if (!source.isValid() || behaviorOf(source.getItemStack()).orElse(null) != behavior) {
                    releasePair(source, counterpart);
                    return;
                }
                steer(source, target, 0.075D);
                if (source.getLocation().distanceSquared(target) > 0.55D) {
                    releasePair(source, counterpart);
                    return;
                }
                consumePairCandidate(source, counterpart, behavior, opposite);
            }, () -> releasePair(source, counterpart));
        } catch (final RuntimeException rejected) {
            releasePair(source, counterpart);
        }
    }

    private void releasePairProbe(final Item source, final Item candidate,
                                  final AtomicBoolean claimed, final AtomicInteger pending) {
        pairReservations.remove(candidate.getUniqueId());
        if (pending.decrementAndGet() == 0 && !claimed.get()) {
            pairReservations.remove(source.getUniqueId());
        }
    }

    private void consumePairCandidate(final Item source, final Item counterpart,
                                      final TrashAnomalyBehavior behavior,
                                      final TrashAnomalyBehavior opposite) {
        counterpart.getScheduler().run(plugin, ignored -> {
            if (!counterpart.isValid()
                    || behaviorOf(counterpart.getItemStack()).orElse(null) != opposite) {
                releasePair(source, counterpart);
                return;
            }
            final ItemStack consumed = counterpart.getItemStack().clone();
            consumed.setAmount(1);
            final ItemStack candidateRemainder = counterpart.getItemStack().clone();
            candidateRemainder.setAmount(counterpart.getItemStack().getAmount() - 1);
            final Location rollbackLocation = counterpart.getLocation().clone();
            if (candidateRemainder.getAmount() < 1) counterpart.remove();
            else counterpart.setItemStack(candidateRemainder);
            source.getScheduler().run(plugin,
                    second -> completePair(source, counterpart, behavior, consumed, rollbackLocation),
                    () -> rollbackPair(source, counterpart, consumed, rollbackLocation));
        }, () -> releasePair(source, counterpart));
    }

    private void completePair(final Item source, final Item counterpart,
                              final TrashAnomalyBehavior behavior, final ItemStack consumed,
                              final Location rollbackLocation) {
        try {
            if (!source.isValid() || behaviorOf(source.getItemStack()).orElse(null) != behavior) {
                rollback(rollbackLocation, consumed);
                return;
            }
            final TrashHistoryService.SplitResult result = history.transformOnSuccess(
                    source.getItemStack(), null);
            if (result.remainder() == null) source.setItemStack(result.singleton());
            else {
                source.setItemStack(result.remainder());
                source.getWorld().dropItem(source.getLocation(), result.singleton());
            }
        } catch (final RuntimeException rejected) {
            rollback(rollbackLocation, consumed);
        } finally {
            releasePair(source, counterpart);
        }
    }

    private void rollbackPair(final Item source, final Item counterpart, final ItemStack consumed,
                              final Location rollbackLocation) {
        rollback(rollbackLocation, consumed);
        releasePair(source, counterpart);
    }

    private void rollback(final Location location, final ItemStack stack) {
        Bukkit.getRegionScheduler().run(plugin, location, ignored -> {
            if (location.getWorld() != null) location.getWorld().dropItemNaturally(location, stack);
        });
    }

    private void attachMechanism(final Player player, final EquipmentSlot hand, final Block block,
                                 final TrashAnomalyBehavior behavior) {
        if (!MECHANISMS.contains(block.getType())) return;
        if (behavior == TrashAnomalyBehavior.MELYNEPI_VAKLENCSE
                && block.getType() != Material.OBSERVER) return;
        if (!claims.canUse(player.getUniqueId(), block.getLocation())
                || territoryProtection.denyInteract(player, block.getLocation())) return;
        final ItemStack held = itemInHand(player, hand);
        final TrashHistoryService.SplitResult split;
        try {
            split = history.splitAndRecord(held, TrashHistoryEvent.WORLD_EVENT_PRESENT,
                    player.getUniqueId(), "");
        } catch (final RuntimeException rejected) {
            return;
        }
        final Item entity;
        try {
            entity = block.getWorld().dropItem(block.getLocation().add(0.5D, 0.75D, 0.5D),
                    split.singleton());
        } catch (final RuntimeException rejected) {
            return;
        }
        setItemInHand(player, hand, split.remainder());
        entity.setGravity(false);
        entity.setVelocity(new Vector());
        entity.setPickupDelay(Integer.MAX_VALUE);
        entity.setUnlimitedLifetime(true);
        entity.getPersistentDataContainer().set(runtimeStateKey, PersistentDataType.STRING,
                UUID.randomUUID().toString());
        runtimeStateEntities.add(entity.getUniqueId());
    }

    private void consumeAttachedMechanism(final BlockRedstoneEvent event) {
        final Block block = event.getBlock();
        int visited = 0;
        for (final Entity nearby : block.getWorld().getNearbyEntities(
                block.getLocation().add(0.5D, 0.5D, 0.5D), 1.15D, 1.15D, 1.15D)) {
            if (++visited > MAX_NEARBY_ENTITIES || !(nearby instanceof Item item)
                    || !item.getPersistentDataContainer().has(runtimeStateKey,
                    PersistentDataType.STRING)) continue;
            final TrashAnomalyBehavior behavior = behaviorOf(item.getItemStack()).orElse(null);
            if (behavior != TrashAnomalyBehavior.MELYNEPI_KIEGETT_BIZTOSITEK
                    && !(behavior == TrashAnomalyBehavior.MELYNEPI_VAKLENCSE
                    && block.getType() == Material.OBSERVER)) continue;
            try {
                final TrashHistoryService.SplitResult transformed = history.transformOnSuccess(
                        item.getItemStack(), null);
                if (transformed.remainder() != null) continue;
                event.setNewCurrent(event.getOldCurrent());
                item.setItemStack(transformed.singleton());
                item.getPersistentDataContainer().remove(runtimeStateKey);
                runtimeStateEntities.remove(item.getUniqueId());
                item.setGravity(true);
                item.setPickupDelay(0);
                item.setUnlimitedLifetime(false);
                item.setVelocity(new Vector(0.0D, 0.18D, 0.0D));
                return;
            } catch (final RuntimeException rejected) {
                return;
            }
        }
    }

    private void invertComparator(final BlockRedstoneEvent event) {
        final Block comparator = event.getBlock();
        if (comparator.getType() != Material.COMPARATOR
                || !(comparator.getBlockData() instanceof Directional directional)) return;
        final Block source = comparator.getRelative(directional.getFacing().getOppositeFace());
        if (!(source.getState() instanceof InventoryHolder holder)) return;
        boolean contains = false;
        double fullness = 0.0D;
        int eligibleSlots = 0;
        for (final ItemStack stack : holder.getInventory().getStorageContents()) {
            if (behaviorOf(stack).orElse(null) == TrashAnomalyBehavior.HAZUG_MERLEGNYELV) {
                contains = true;
                continue;
            }
            eligibleSlots++;
            if (stack != null && !stack.getType().isAir()) {
                fullness += stack.getAmount() / (double) Math.min(stack.getMaxStackSize(),
                        holder.getInventory().getMaxStackSize());
            }
        }
        if (!contains) return;
        final int normal = fullness <= 0.0D || eligibleSlots == 0 ? 0
                : Math.min(15, 1 + (int) Math.floor(14.0D * fullness / eligibleSlots));
        event.setNewCurrent(15 - normal);
    }

    private void prepareReturningDrop(final UUID playerId, final Item dropped) {
        final ItemStack source = dropped.getItemStack();
        final TrashHistoryService.SplitResult split;
        try {
            split = history.splitAndRecord(source, TrashHistoryEvent.ACTIVATED,
                    playerId, "");
        } catch (final RuntimeException rejected) {
            return;
        }
        dropped.setItemStack(split.singleton());
        dropped.setOwner(playerId);
        if (split.remainder() != null) {
            dropped.getWorld().dropItemNaturally(dropped.getLocation(), split.remainder());
        }
        final long delay = ThreadLocalRandom.current().nextLong(40L, 101L);
        dropped.getScheduler().runDelayed(plugin, task -> returnToOwner(dropped), null, delay);
    }

    private void returnToOwner(final Item dropped) {
        if (!dropped.isValid()) return;
        final UUID owner = dropped.getOwner();
        final Player player = owner == null ? null : Bukkit.getPlayer(owner);
        if (player == null || !player.isOnline()) return;
        final ItemStack returned = dropped.getItemStack().clone();
        final Location rollbackLocation = dropped.getLocation().clone();
        dropped.remove();
        player.getScheduler().run(plugin, ignored -> player.getInventory().addItem(returned)
                .values().forEach(overflow -> player.getWorld().dropItemNaturally(
                        player.getLocation(), overflow)),
                () -> rollback(rollbackLocation, returned));
    }

    private void captureSound(final Location location, final Sound sound,
                              final float volume, final float pitch) {
        if (location.getWorld() == null) return;
        final long now = System.currentTimeMillis();
        recentSounds.addLast(new CapturedSound(location.getWorld().getUID(), location.getX(),
                location.getY(), location.getZ(), sound, volume, pitch, now));
        while (recentSounds.size() > MAX_RECENT_SOUNDS) recentSounds.pollFirst();
        while (true) {
            final CapturedSound oldest = recentSounds.peekFirst();
            if (oldest == null || now - oldest.capturedAt() <= SOUND_CAPTURE_MAX_AGE_MILLIS) break;
            recentSounds.pollFirst();
        }
    }

    private CapturedSound recentSoundNear(final Location location) {
        if (location.getWorld() == null) return null;
        final long oldestAllowed = System.currentTimeMillis() - SOUND_CAPTURE_MAX_AGE_MILLIS;
        int visited = 0;
        for (final java.util.Iterator<CapturedSound> iterator = recentSounds.descendingIterator();
             iterator.hasNext() && visited++ < MAX_RECENT_SOUNDS;) {
            final CapturedSound sound = iterator.next();
            if (sound.capturedAt() < oldestAllowed) break;
            if (!sound.worldId().equals(location.getWorld().getUID())) continue;
            final double dx = sound.x() - location.getX();
            final double dy = sound.y() - location.getY();
            final double dz = sound.z() - location.getZ();
            if (dx * dx + dy * dy + dz * dz <= SOUND_CAPTURE_RADIUS_SQUARED) return sound;
        }
        return null;
    }

    private void scheduleEcho(final Player player, final EquipmentSlot hand) {
        final CapturedSound captured = recentSoundNear(player.getLocation());
        if (captured == null) return;
        final ItemStack singleton = ensureSingletonInHand(player, hand, TrashHistoryEvent.ACTIVATED);
        final UUID instance = singleton == null ? null : history.instanceIdOf(singleton).orElse(null);
        if (instance == null) return;
        synchronized (pendingEchoes) {
            if (pendingEchoes.size() >= MAX_PENDING_ECHOES || !pendingEchoes.add(instance)) return;
        }
        final long delay = ThreadLocalRandom.current().nextLong(200L, 601L);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            pendingEchoes.remove(instance);
            final org.bukkit.World world = Bukkit.getWorld(captured.worldId());
            if (world == null) return;
            final Location origin = new Location(world, captured.x(), captured.y(), captured.z());
            if (!world.isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) return;
            Bukkit.getRegionScheduler().run(plugin, origin, region -> {
                world.playSound(origin, captured.sound(), captured.volume(), captured.pitch());
            });
        }, delay);
    }

    private ItemStack ensureSingletonInHand(final Player player, final EquipmentSlot hand,
                                            final TrashHistoryEvent event) {
        final ItemStack held = itemInHand(player, hand);
        if (held == null || held.getType().isAir()) return null;
        if (history.isValidTracked(held)) return held;
        if (held.getAmount() > 1 && player.getInventory().firstEmpty() < 0) return null;
        try {
            final TrashHistoryService.SplitResult split = history.splitAndRecord(held, event,
                    player.getUniqueId(), "");
            setItemInHand(player, hand, split.singleton());
            if (split.remainder() != null) player.getInventory().addItem(split.remainder())
                    .values().forEach(overflow -> player.getWorld().dropItemNaturally(
                            player.getLocation(), overflow));
            return split.singleton();
        } catch (final RuntimeException rejected) {
            return null;
        }
    }

    private void projectOppositeDirection(final Player player, final EquipmentSlot hand,
                                          final ItemStack compass) {
        Location target = null;
        if (compass != null && compass.getItemMeta() instanceof CompassMeta meta
                && meta.hasLodestone()) target = meta.getLodestone();
        if (target == null) target = player.getRespawnLocation();
        if (target == null) target = player.getCompassTarget();
        final Location playerLocation = player.getLocation();
        if (target == null || target.getWorld() == null || playerLocation.getWorld() == null
                || !target.getWorld().equals(playerLocation.getWorld())) {
            clearCompassProjection(player, hand);
            return;
        }
        final TrashAnomalyPolicy.OppositePoint opposite = TrashAnomalyPolicy.oppositePoint(
                playerLocation.getX(), playerLocation.getZ(), target.getX(), target.getZ(), 1024.0D);
        if (opposite == null || !(compass.getItemMeta() instanceof CompassMeta)) {
            clearCompassProjection(player, hand);
            return;
        }
        final ItemStack projected = compass.clone();
        final CompassMeta projectedMeta = (CompassMeta) projected.getItemMeta();
        projectedMeta.setLodestone(new Location(playerLocation.getWorld(), opposite.x(),
                playerLocation.getY(), opposite.z()));
        projectedMeta.setLodestoneTracked(false);
        projected.setItemMeta(projectedMeta);
        player.sendEquipmentChange(player, hand, projected);
        compassProjections.add(new CompassProjection(player.getUniqueId(), hand));
    }

    private void clearCompassProjection(final Player player, final EquipmentSlot hand) {
        if (!compassProjections.remove(new CompassProjection(player.getUniqueId(), hand))) return;
        resyncCompassProjection(player, hand);
    }

    private void resyncCompassProjection(final Player player, final EquipmentSlot hand) {
        player.sendEquipmentChange(player, hand, itemInHand(player, hand).clone());
    }

    private void lateWhistle(final Player player) {
        if (!isRightThreatNearby(player)) return;
        player.getScheduler().runDelayed(plugin, ignored -> {
            if (player.isOnline()) player.playSound(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_3,
                    0.22F, 1.65F);
        }, null, 14L);
    }

    private boolean isRightThreatNearby(final Player player) {
        int visited = 0;
        for (final Entity nearby : player.getNearbyEntities(4.5D, 3.0D, 4.5D)) {
            if (++visited > MAX_NEARBY_ENTITIES) break;
            if (nearby instanceof Monster monster && !monster.isDead()) return true;
        }
        return false;
    }

    private void whisperInDarkness(final Player player) {
        if (player.getLocation().getBlock().getLightLevel() > 4) return;
        final long now = System.currentTimeMillis();
        if (whisperCooldown.getOrDefault(player.getUniqueId(), 0L) > now
                || ThreadLocalRandom.current().nextInt(5) != 0) return;
        whisperCooldown.put(player.getUniqueId(), now + 30_000L);
        quietActionBar(player, WHISPERS.get(ThreadLocalRandom.current().nextInt(WHISPERS.size())));
    }

    private void reconcileSerial(final Player player) {
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] storage = inventory.getStorageContents();
        int target = -1;
        for (int slot = storage.length - 1; slot >= 0; slot--) {
            if (storage[slot] == null || storage[slot].getType().isAir()) {
                target = slot;
                break;
            }
        }
        if (target < 0) return;
        for (int source = 0; source < target; source++) {
            if (behaviorOf(storage[source]).orElse(null)
                    == TrashAnomalyBehavior.CALDESTERAI_SORSZAM_999) {
                inventory.setItem(target, storage[source]);
                inventory.setItem(source, null);
                return;
            }
        }
    }

    private Optional<TrashAnomalyBehavior> behaviorOf(final ItemStack stack) {
        if (!items.isBaseIdentity(stack)) return Optional.empty();
        final String id = items.idOf(stack).orElse(null);
        if (id == null) return Optional.empty();
        final TrashDefinition definition = catalog.require(id);
        if (definition.internalKind() != TrashKind.ANOMALY) return Optional.empty();
        return Optional.of(TrashAnomalyBehavior.parse(definition.behavior()));
    }

    private static boolean lastEligibleStack(final Inventory inventory, final ItemStack selected) {
        if (inventory == null || selected == null) return false;
        int nonEmpty = 0;
        for (final ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && !stack.getType().isAir() && ++nonEmpty > 1) return false;
        }
        return nonEmpty == 1;
    }

    private static boolean authoredWarUndead(final String templateId) {
        if (templateId == null || templateId.isBlank()) return false;
        final String normalized = templateId.toLowerCase(Locale.ROOT);
        return normalized.contains("seventh") || normalized.contains("hetedik")
                || normalized.contains("war") || normalized.contains("habor");
    }

    private static TrashAnomalyBehavior opposite(final TrashAnomalyBehavior behavior) {
        return switch (behavior) {
            case BAL_ZOKNI -> TrashAnomalyBehavior.JOBB_ZOKNI;
            case JOBB_ZOKNI -> TrashAnomalyBehavior.BAL_ZOKNI;
            case BAL_LANCSZEM -> TrashAnomalyBehavior.JOBB_LANCSZEM;
            case JOBB_LANCSZEM -> TrashAnomalyBehavior.BAL_LANCSZEM;
            default -> throw new IllegalArgumentException("nem páros behavior");
        };
    }

    private void releasePair(final Item first, final Item second) {
        pairReservations.remove(first.getUniqueId());
        pairReservations.remove(second.getUniqueId());
    }

    private void releasePhysicsAndRestore(final Item item) {
        if (item.isValid() && behaviorOf(item.getItemStack()).orElse(null)
                == TrashAnomalyBehavior.FELFELE_HULLO_HOPEHELY) item.setGravity(true);
        releasePhysics(item.getUniqueId());
    }

    private void releasePhysics(final UUID itemId) {
        if (!activePhysics.remove(itemId)) return;
        physicsOriginY.remove(itemId);
        final UUID worldId = physicsWorldByItem.remove(itemId);
        final AtomicInteger counter = worldId == null ? null : activeByWorld.get(worldId);
        if (counter != null && counter.decrementAndGet() <= 0) {
            activeByWorld.remove(worldId, counter);
        }
    }

    private static void steer(final Item item, final Location target, final double strength) {
        final Vector direction = target.toVector().subtract(item.getLocation().toVector());
        if (direction.lengthSquared() < 0.04D) return;
        item.setVelocity(item.getVelocity().multiply(0.72D)
                .add(direction.normalize().multiply(strength)));
    }

    private static boolean sameChunk(final Block first, final Block second) {
        return first.getWorld().equals(second.getWorld())
                && first.getX() >> 4 == second.getX() >> 4
                && first.getZ() >> 4 == second.getZ() >> 4;
    }

    private static boolean isCold(final Location location) {
        final String biome = location.getBlock().getBiome().name();
        return biome.contains("FROZEN") || biome.contains("SNOW") || biome.contains("ICE")
                || biome.contains("COLD") || location.getBlock().getType().name().contains("ICE");
    }

    private static boolean hasWaterSkipPath(final Player player) {
        if (player.isInWater()) return true;
        final Location origin = player.getEyeLocation();
        final Vector direction = origin.getDirection().normalize();
        for (double distance = 0.5D; distance <= 7.0D; distance += 0.5D) {
            final Location sample = origin.clone().add(direction.clone().multiply(distance));
            if (!sample.getWorld().isChunkLoaded(sample.getBlockX() >> 4,
                    sample.getBlockZ() >> 4)) return false;
            if (sample.getBlock().getType() == Material.WATER) return true;
            if (!sample.getBlock().isPassable()) return false;
        }
        return false;
    }

    private void quietActionBar(final Player player, final String text) {
        final long now = System.currentTimeMillis();
        if (presentationCooldown.getOrDefault(player.getUniqueId(), 0L) > now) return;
        presentationCooldown.put(player.getUniqueId(), now + 900L);
        player.sendActionBar(Component.text(text, NamedTextColor.DARK_GRAY));
    }

    private static String formatWatch(final long ticks) {
        final long seconds = ticks / 20L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600L,
                seconds / 60L % 60L, seconds % 60L);
    }

    private static boolean isRightClick(final Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private static ItemStack itemInHand(final Player player, final EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }

    private static void setItemInHand(final Player player, final EquipmentSlot hand,
                                      final ItemStack stack) {
        final ItemStack safe = stack == null ? new ItemStack(Material.AIR) : stack;
        if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(safe);
        else player.getInventory().setItemInMainHand(safe);
    }

    private record CompassProjection(UUID playerId, EquipmentSlot hand) { }

    private record CapturedSound(UUID worldId, double x, double y, double z, Sound sound,
                                 float volume, float pitch, long capturedAt) { }
}
