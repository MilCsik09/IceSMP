package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.integration.ProtectionBridge;
import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Player-driven, loaded-chunk-only ambient litter with a coordinate-only density index. */
public final class TrashAmbientManager implements Listener, PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TrashCatalog catalog;
    private final TrashLootService loot;
    private final TrashContextResolver contexts;
    private final ClaimManager claimManager;
    private final TerritoryManager territoryManager;
    private final AfkManager afkManager;
    private final NamespacedKey ambientMarker;
    private final NamespacedKey ambientExpiresAt;
    private final Map<UUID, Long> nextAttemptAt = new ConcurrentHashMap<>();
    private final Map<UUID, AmbientRecord> active = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Integer> chunkCounts = new ConcurrentHashMap<>();
    private final Object densityLock = new Object();

    public TrashAmbientManager(final JavaPlugin plugin, final ConfigManager configManager,
                               final TrashCatalog catalog, final TrashLootService loot,
                               final TrashContextResolver contexts, final ClaimManager claimManager,
                               final TerritoryManager territoryManager, final AfkManager afkManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.loot = Objects.requireNonNull(loot, "loot");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.claimManager = Objects.requireNonNull(claimManager, "claimManager");
        this.territoryManager = Objects.requireNonNull(territoryManager, "territoryManager");
        this.afkManager = Objects.requireNonNull(afkManager, "afkManager");
        this.ambientMarker = new NamespacedKey(plugin, "trash_ambient");
        this.ambientExpiresAt = new NamespacedKey(plugin, "trash_ambient_expires_at");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !enabled()) return;
        final Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL || afkManager.isAfk(player.getUniqueId())) return;
        final long now = System.currentTimeMillis();
        final Long due = nextAttemptAt.putIfAbsent(player.getUniqueId(), now + nextDelayMillis());
        if (due == null || now < due) return;
        if (!nextAttemptAt.replace(player.getUniqueId(), due, now + nextDelayMillis())) return;

        final Location origin = event.getTo().clone();
        if (origin.getWorld() == null) return;
        final Location candidate = candidate(origin);
        plugin.getServer().getRegionScheduler().run(plugin, candidate,
                task -> attemptSpawn(candidate));
    }

    private void attemptSpawn(final Location column) {
        if (!enabled() || column.getWorld() == null) return;
        final World world = column.getWorld();
        final int chunkX = column.getBlockX() >> 4;
        final int chunkZ = column.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) return;
        final Location spawn = resolveSurface(world, column.getBlockX(), column.getBlockZ());
        final hu.taliann.icesmp.data.Territory territory = spawn == null ? null
                : territoryManager.getTerritoryAt(spawn);
        if (spawn == null || claimManager.getClaimAt(spawn) != null
                || territory != null && territory.type().isProtectedZone()
                || !Boolean.FALSE.equals(ProtectionBridge.queryProtected(spawn))) return;

        final ChunkKey chunk = new ChunkKey(world.getUID(), chunkX, chunkZ);
        if (!reserve(chunk)) return;
        final org.bukkit.inventory.ItemStack stack = loot.roll(TrashLootSource.AMBIENT,
                contexts.resolve(TrashLootSource.AMBIENT, spawn, null)).orElse(null);
        if (stack == null) {
            release(chunk);
            return;
        }
        final Item item;
        try {
            item = world.dropItem(spawn, stack);
        } catch (final RuntimeException failure) {
            release(chunk);
            return;
        }
        final long ttlSeconds = randomInclusive(catalog.lootTuning().ambient().ttlMinSeconds(),
                catalog.lootTuning().ambient().ttlMaxSeconds());
        final long expiresAt = Math.addExact(System.currentTimeMillis(),
                Math.multiplyExact(ttlSeconds, 1_000L));
        item.getPersistentDataContainer().set(ambientMarker, PersistentDataType.BYTE, (byte) 1);
        item.getPersistentDataContainer().set(ambientExpiresAt, PersistentDataType.LONG, expiresAt);
        item.setUnlimitedLifetime(true);
        active.put(item.getUniqueId(), new AmbientRecord(chunk));
        scheduleExpiry(item, expiresAt);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(final EntitiesLoadEvent event) {
        for (final Entity entity : event.getEntities()) {
            if (entity instanceof Item item) recoverLoaded(item);
        }
    }

    /** Recovers ambient entities from chunks that were already loaded before listener registration. */
    public void start() {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            for (final World world : Bukkit.getWorlds()) {
                for (final Chunk chunk : world.getLoadedChunks()) {
                    final int chunkX = chunk.getX();
                    final int chunkZ = chunk.getZ();
                    final Location anchor = new Location(world, (chunkX << 4) + 8.0D,
                            world.getMinHeight(), (chunkZ << 4) + 8.0D);
                    Bukkit.getRegionScheduler().run(plugin, anchor, region -> {
                        if (!world.isChunkLoaded(chunkX, chunkZ)) return;
                        for (final Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
                            if (entity instanceof Item item) recoverLoaded(item);
                        }
                    });
                }
            }
        });
    }

    private void recoverLoaded(final Item item) {
        if (!isAmbient(item)) return;
        final Long expiresAt = item.getPersistentDataContainer().get(
                ambientExpiresAt, PersistentDataType.LONG);
        if (expiresAt == null || expiresAt <= System.currentTimeMillis()) {
            item.remove();
            return;
        }
        final ChunkKey chunk = chunkOf(item.getLocation());
        if (active.putIfAbsent(item.getUniqueId(), new AmbientRecord(chunk)) == null) {
            registerLoaded(chunk);
            item.setUnlimitedLifetime(true);
            scheduleExpiry(item, expiresAt);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(final EntitiesUnloadEvent event) {
        for (final Entity entity : event.getEntities()) {
            if (entity instanceof Item item && active.containsKey(item.getUniqueId())) {
                untrack(item.getUniqueId());
            }
        }
    }

    private void scheduleExpiry(final Item item, final long expiresAt) {
        final long remainingMillis = Math.max(1L, expiresAt - System.currentTimeMillis());
        final long delayTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
        item.getScheduler().runDelayed(plugin, task -> {
            if (!item.isValid()) {
                untrack(item.getUniqueId());
                return;
            }
            final Long currentExpiry = item.getPersistentDataContainer().get(
                    ambientExpiresAt, PersistentDataType.LONG);
            if (currentExpiry == null || currentExpiry <= System.currentTimeMillis()) {
                untrack(item.getUniqueId());
                item.remove();
                return;
            }
            scheduleExpiry(item, currentExpiry);
        }, () -> untrack(item.getUniqueId()), delayTicks);
    }

    private boolean reserve(final ChunkKey center) {
        synchronized (densityLock) {
            final int perChunk = Math.max(1, Math.min(16,
                    configManager.getInt("trash-runtime.ambient.max-per-chunk", 1)));
            final int neighborhoodCap = Math.max(1, Math.min(32,
                    configManager.getInt("trash-runtime.ambient.max-per-neighborhood", 4)));
            if (chunkCounts.getOrDefault(center, 0) >= perChunk) return false;
            int neighborhood = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    neighborhood += chunkCounts.getOrDefault(
                            new ChunkKey(center.worldId(), center.chunkX() + dx, center.chunkZ() + dz), 0);
                }
            }
            if (neighborhood >= neighborhoodCap) return false;
            chunkCounts.merge(center, 1, Integer::sum);
            return true;
        }
    }

    private void release(final ChunkKey chunk) {
        synchronized (densityLock) {
            chunkCounts.computeIfPresent(chunk, (ignored, count) -> count <= 1 ? null : count - 1);
        }
    }

    private void registerLoaded(final ChunkKey chunk) {
        synchronized (densityLock) {
            chunkCounts.merge(chunk, 1, Integer::sum);
        }
    }

    private void untrack(final UUID entityId) {
        final AmbientRecord removed = active.remove(entityId);
        if (removed != null) release(removed.chunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        untrack(event.getItem().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperPickup(final InventoryPickupItemEvent event) {
        untrack(event.getItem().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDespawn(final ItemDespawnEvent event) {
        untrack(event.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(final ItemMergeEvent event) {
        if (active.containsKey(event.getEntity().getUniqueId())
                || active.containsKey(event.getTarget().getUniqueId())) event.setCancelled(true);
    }

    private boolean isAmbient(final Item item) {
        return item != null && item.getPersistentDataContainer().has(ambientMarker, PersistentDataType.BYTE);
    }

    private static ChunkKey chunkOf(final Location location) {
        return new ChunkKey(location.getWorld().getUID(),
                location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private Location candidate(final Location origin) {
        final TrashLootTuning.Ambient tuning = catalog.lootTuning().ambient();
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final double distance = random.nextDouble(tuning.distanceMinBlocks(),
                Math.nextUp((double) tuning.distanceMaxBlocks()));
        double angle = random.nextDouble(Math.PI * 2.0D);
        final double dx = Math.cos(angle) * distance;
        final double dz = Math.sin(angle) * distance;
        final org.bukkit.util.Vector look = origin.getDirection().setY(0.0D);
        if (look.lengthSquared() > 0.0001D) {
            look.normalize();
            final double dot = look.getX() * dx / distance + look.getZ() * dz / distance;
            if (dot > 0.5D) angle += Math.PI;
        }
        return new Location(origin.getWorld(), origin.getX() + Math.cos(angle) * distance,
                0.0D, origin.getZ() + Math.sin(angle) * distance);
    }

    private static Location resolveSurface(final World world, final int x, final int z) {
        final int waterY = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        final Block surface = world.getBlockAt(x, waterY, z);
        if (surface.getType() == Material.WATER) {
            return new Location(world, x + 0.5D, waterY + 0.55D, z + 0.5D);
        }
        final int floorY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (floorY <= world.getMinHeight() || floorY + 2 >= world.getMaxHeight()) return null;
        final Block floor = world.getBlockAt(x, floorY, z);
        final Block feet = world.getBlockAt(x, floorY + 1, z);
        if (!floor.getType().isSolid() || floor.getType().hasGravity()
                || Tag.LEAVES.isTagged(floor.getType()) || !feet.isPassable() || feet.isLiquid()
                || floor.getType() == Material.MAGMA_BLOCK || floor.getType() == Material.CACTUS) return null;
        return new Location(world, x + 0.5D, floorY + 1.15D, z + 0.5D);
    }

    private long nextDelayMillis() {
        final TrashLootTuning.Ambient tuning = catalog.lootTuning().ambient();
        return randomInclusive(tuning.attemptMinSeconds(), tuning.attemptMaxSeconds()) * 1000L;
    }

    private static long randomInclusive(final int minimum, final int maximum) {
        return minimum == maximum ? minimum
                : ThreadLocalRandom.current().nextLong(minimum, (long) maximum + 1L);
    }

    private boolean enabled() {
        return configManager.getBoolean("trash-runtime.enabled", true)
                && configManager.getBoolean("trash-runtime.ambient.enabled", true);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        nextAttemptAt.remove(playerId);
    }

    public void shutdown() {
        nextAttemptAt.clear();
        active.clear();
        synchronized (densityLock) {
            chunkCounts.clear();
        }
    }

    int activeCount() {
        return active.size();
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) { }
    private record AmbientRecord(ChunkKey chunk) { }
}
