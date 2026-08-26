package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.integration.ProtectionBridge;
import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.Bukkit;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
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
        if (spawn == null || claimManager.getClaimAt(spawn) != null
                || territoryManager.getTerritoryAt(spawn) != null
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
        item.getPersistentDataContainer().set(ambientMarker, PersistentDataType.BYTE, (byte) 1);
        item.setUnlimitedLifetime(true);
        active.put(item.getUniqueId(), new AmbientRecord(chunk));
        final long ttlTicks = randomInclusive(catalog.lootTuning().ambient().ttlMinSeconds(),
                catalog.lootTuning().ambient().ttlMaxSeconds()) * 20L;
        item.getScheduler().runDelayed(plugin, task -> {
            untrack(item.getUniqueId());
            if (item.isValid()) item.remove();
        }, () -> untrack(item.getUniqueId()), ttlTicks);
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

    private void untrack(final UUID entityId) {
        final AmbientRecord removed = active.remove(entityId);
        if (removed != null) release(removed.chunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        clearMarker(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperPickup(final InventoryPickupItemEvent event) {
        clearMarker(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDespawn(final ItemDespawnEvent event) {
        untrack(event.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(final ItemMergeEvent event) {
        if (isAmbient(event.getEntity()) || isAmbient(event.getTarget())) event.setCancelled(true);
    }

    private void clearMarker(final Item item) {
        if (!isAmbient(item)) return;
        item.getPersistentDataContainer().remove(ambientMarker);
        item.setUnlimitedLifetime(false);
        untrack(item.getUniqueId());
    }

    private boolean isAmbient(final Item item) {
        return item != null && item.getPersistentDataContainer().has(ambientMarker, PersistentDataType.BYTE);
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
        final List<UUID> ids = new ArrayList<>(active.keySet());
        active.clear();
        synchronized (densityLock) {
            chunkCounts.clear();
        }
        for (final UUID id : ids) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Item item) {
                item.getScheduler().run(plugin, task -> {
                    if (item.isValid()) item.remove();
                }, null);
            }
        }
    }

    int activeCount() {
        return active.size();
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) { }
    private record AmbientRecord(ChunkKey chunk) { }
}
