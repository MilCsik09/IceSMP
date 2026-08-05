package hu.taliann.icesmp.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Shared fail-closed placement gate for world events. Protection checks, online-player
 * distance, world spawn/border safety, finite search, shoreline clearance and cross-event
 * reservations live here so every event consumes one precedence model instead of
 * reimplementing it.
 */
public final class EventSpawnGuard {
    public static final String EVENT_NO_BURN_KEY = "event_no_daylight_burn";
    public static final String EVENT_NO_ZOMBIFICATION_KEY = "event_no_zombification";

    private static volatile EventSpawnGuard activeGuard;

    public enum BlockReason {
        NONE,
        INVALID_WORLD,
        TERRITORY,
        CLAIM,
        REGION,
        PLAYER_DISTANCE,
        WORLD_SPAWN,
        WORLD_BORDER,
        RESERVED,
        UNLOADED_CHUNK,
        UNSAFE_SURFACE,
        WATER_OR_SHORE
    }

    private record Reservation(EventSpawnSafetyPolicy.Point point, long expiresAtMillis) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final ClaimManager claimManager;
    private final Map<UUID, EventSpawnSafetyPolicy.PlayerPoint> players = new ConcurrentHashMap<>();
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final Map<String, Long> diagnosticLogAt = new ConcurrentHashMap<>();
    private volatile Predicate<UUID> vanishedPredicate = ignored -> false;

    public EventSpawnGuard(final JavaPlugin plugin, final ConfigManager configManager,
                           final TerritoryManager territoryManager, final ClaimManager claimManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.claimManager = claimManager;
        activeGuard = this;
    }

    /** Runtime singleton bridge for managers built before the guard in the manual DI order. */
    public static EventSpawnGuard current() {
        return activeGuard;
    }

    public void setVanishedPredicate(final Predicate<UUID> vanishedPredicate) {
        this.vanishedPredicate = vanishedPredicate == null ? ignored -> false : vanishedPredicate;
    }

    /** Capture only entity-owned values; later guard reads are immutable and region-safe. */
    public void trackPlayer(final Player player) {
        trackPlayer(player, player.getLocation(), player.getGameMode());
    }

    public void trackPlayer(final Player player, final Location location, final GameMode gameMode) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        players.put(player.getUniqueId(), new EventSpawnSafetyPolicy.PlayerPoint(
                player.getUniqueId(), point(location), gameMode == GameMode.SPECTATOR,
                false, player.hasPermission("icesmp.admin.all")));
    }

    public void forgetPlayer(final UUID playerId) {
        if (playerId != null) {
            players.remove(playerId);
        }
    }

    public boolean isBlocked(final String eventKey, final Location location) {
        return blockReason(eventKey, location) != BlockReason.NONE;
    }

    public BlockReason blockReason(final String eventKey, final Location location) {
        return blockReason(eventKey, location, true);
    }

    /**
     * The safe-search path already performed the expensive shoreline scan while resolving
     * footing. It calls this overload with includeWater=false so protections, players and
     * reservations are rechecked without reading the same columns two more times.
     */
    private BlockReason blockReason(final String eventKey, final Location location,
                                    final boolean includeWater) {
        if (location == null || location.getWorld() == null) {
            return BlockReason.INVALID_WORLD;
        }
        if (masterSwitch()) {
            if (rule(eventKey, "territory") && territoryManager.getTerritoryAt(location) != null) {
                return BlockReason.TERRITORY;
            }
            if (rule(eventKey, "claim") && claimManager.getClaimAt(location) != null) {
                return BlockReason.CLAIM;
            }
            if (rule(eventKey, "region")
                    && hu.taliann.icesmp.integration.ProtectionBridge.isProtected(location)) {
                return BlockReason.REGION;
            }
        }
        if (!safetyEnabled()) {
            return BlockReason.NONE;
        }
        final World world = location.getWorld();
        final EventSpawnSafetyPolicy.Point candidate = point(location);
        final double spawnDistance = Math.max(0.0D, configManager.getDouble(
                "world-events.safety.min-world-spawn-distance-blocks", 128.0D));
        if (spawnDistance > 0.0D && EventSpawnSafetyPolicy.withinHorizontal(
                candidate, point(world.getSpawnLocation()), spawnDistance)) {
            return BlockReason.WORLD_SPAWN;
        }
        if (!insideBorder(world, location)) {
            return BlockReason.WORLD_BORDER;
        }
        final int chunkX = location.getBlockX() >> 4;
        final int chunkZ = location.getBlockZ() >> 4;
        if (includeWater && waterSafetyRequired(eventKey)
                && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)
                && waterOrShoreUnsafe(world, location.getBlockX(), location.getBlockZ())) {
            return BlockReason.WATER_OR_SHORE;
        }
        final List<EventSpawnSafetyPolicy.PlayerPoint> snapshot = players.values().stream()
                .map(player -> new EventSpawnSafetyPolicy.PlayerPoint(player.playerId(), player.point(),
                        player.spectator(), vanishedPredicate.test(player.playerId()), player.admin()))
                .toList();
        if (EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(candidate, snapshot,
                configManager.getDouble("world-events.safety.min-horizontal-distance-blocks", 192.0D),
                configManager.getDouble("world-events.safety.min-3d-distance-blocks", 0.0D),
                configManager.getBoolean("world-events.safety.ignore-spectators", true),
                configManager.getBoolean("world-events.safety.ignore-vanished", true),
                configManager.getBoolean("world-events.safety.ignore-admins", false))) {
            return BlockReason.PLAYER_DISTANCE;
        }
        final long now = System.currentTimeMillis();
        final double reservationDistance = Math.max(0.0D, configManager.getDouble(
                "world-events.safety.reservation-distance-blocks", 64.0D));
        reservations.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
        for (final Map.Entry<String, Reservation> entry : reservations.entrySet()) {
            if (!entry.getKey().equals(eventKey)
                    && EventSpawnSafetyPolicy.withinHorizontal(candidate, entry.getValue().point(),
                    reservationDistance)) {
                return BlockReason.RESERVED;
            }
        }
        return BlockReason.NONE;
    }

    /**
     * Resolves stable footing on the region thread owning x/z. Leaves, gravity
     * blocks, liquids, damaging floors and columns inside the configured shoreline
     * buffer are rejected; tall mobs get three passable body blocks.
     */
    public Location resolveSafeStandingLocation(final String eventKey, final World world,
                                                final int x, final int z) {
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)
                || !Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            return null;
        }
        final int floorY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (floorY <= world.getMinHeight() || floorY + 3 >= world.getMaxHeight()) {
            return null;
        }
        final Block floor = world.getBlockAt(x, floorY, z);
        final Block feet = world.getBlockAt(x, floorY + 1, z);
        final Block head = world.getBlockAt(x, floorY + 2, z);
        final Block upperHead = world.getBlockAt(x, floorY + 3, z);
        if (!stableFloor(floor)
                || !clearBody(feet) || !clearBody(head) || !clearBody(upperHead)) {
            return null;
        }
        if (waterSafetyRequired(eventKey) && waterOrShoreUnsafe(world, x, z)) {
            return null;
        }
        return new Location(world, x + 0.5D, floorY + 1.0D, z + 0.5D);
    }

    public boolean isUnsafeSurface(final String eventKey, final World world, final int x, final int z) {
        return resolveSafeStandingLocation(eventKey, world, x, z) == null;
    }

    private boolean waterOrShoreUnsafe(final World world, final int centerX, final int centerZ) {
        for (final EventSpawnSafetyPolicy.GridOffset offset
                : EventSpawnSafetyPolicy.waterProbeOffsets(shorelineRadius())) {
            final int x = centerX + offset.x();
            final int z = centerZ + offset.z();
            final int chunkX = x >> 4;
            final int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)
                    || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
                return true;
            }
            if (waterAtSurface(world, x, z)) {
                return true;
            }
        }
        return false;
    }

    private int shorelineRadius() {
        return Math.max(0, Math.min(32, configManager.getInt(
                "world-events.water-safety.buffer-blocks", 8)));
    }

    private static boolean waterAtSurface(final World world, final int x, final int z) {
        final int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        final int from = Math.min(world.getMaxHeight() - 1, surfaceY + 1);
        final int to = Math.max(world.getMinHeight(), surfaceY - 3);
        for (int y = from; y >= to; y--) {
            if (isWater(world.getBlockAt(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWater(final Block block) {
        if (block.getType() == Material.WATER) {
            return true;
        }
        return block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private static boolean stableFloor(final Block floor) {
        final Material material = floor.getType();
        return material.isSolid()
                && material.isOccluding()
                && !material.hasGravity()
                && material != Material.POWDER_SNOW
                && material != Material.MAGMA_BLOCK
                && material != Material.CAMPFIRE
                && material != Material.SOUL_CAMPFIRE
                && material != Material.CACTUS
                && material != Material.SWEET_BERRY_BUSH
                && material != Material.WITHER_ROSE;
    }

    private static boolean clearBody(final Block block) {
        return block.isPassable() && !block.isLiquid()
                && block.getType() != Material.FIRE
                && block.getType() != Material.SOUL_FIRE;
    }

    /** Tries the supplied column first, then falls back to the bounded annulus search. */
    public void findSafeAtOrNear(final String eventKey, final Location origin, final long seed,
                                 final Consumer<Location> onFound, final Runnable onFailure) {
        if (origin == null || origin.getWorld() == null) {
            onFailure.run();
            return;
        }
        prepareCandidateChunks(eventKey, origin.clone(),
                () -> validatePreferred(eventKey, origin.clone(), seed, onFound, onFailure),
                () -> findSafeNear(eventKey, origin, seed, onFound, onFailure));
    }

    private void validatePreferred(final String eventKey, final Location column, final long seed,
                                   final Consumer<Location> onFound, final Runnable onFailure) {
        final World world = column.getWorld();
        if (world == null) {
            findSafeNear(eventKey, column, seed, onFound, onFailure);
            return;
        }
        final Location candidate = resolveSafeStandingLocation(
                eventKey, world, column.getBlockX(), column.getBlockZ());
        if (candidate != null && reserveAfterSurfaceValidation(eventKey, candidate)) {
            onFound.accept(candidate);
            return;
        }
        findSafeNear(eventKey, column, seed, onFound, onFailure);
    }

    /**
     * Finite Folia-safe search around an entity-owned origin. Candidate columns are visited
     * at most search-attempts times; no valid location means a controlled abort, never a
     * close/forbidden fallback.
     */
    public void findSafeNear(final String eventKey, final Location origin, final long seed,
                             final Consumer<Location> onFound, final Runnable onFailure) {
        if (origin == null || origin.getWorld() == null) {
            onFailure.run();
            return;
        }
        final List<EventSpawnSafetyPolicy.Offset> candidates = EventSpawnSafetyPolicy.candidates(
                configManager.getInt("world-events.safety.search-attempts", 32),
                configManager.getDouble("world-events.safety.search-min-radius-blocks", 256.0D),
                configManager.getDouble("world-events.safety.search-max-radius-blocks", 512.0D),
                seed);
        tryCandidate(eventKey, origin.clone(), candidates, 0, onFound, onFailure);
    }

    private void tryCandidate(final String eventKey, final Location origin,
                              final List<EventSpawnSafetyPolicy.Offset> candidates, final int index,
                              final Consumer<Location> onFound, final Runnable onFailure) {
        if (index >= candidates.size()) {
            logSearchFailure(eventKey, origin, candidates.size());
            onFailure.run();
            return;
        }
        final EventSpawnSafetyPolicy.Offset offset = candidates.get(index);
        final Location column = origin.clone().add(offset.x(), 0.0D, offset.z());
        if (column.getWorld() == null) {
            tryCandidate(eventKey, origin, candidates, index + 1, onFound, onFailure);
            return;
        }
        prepareCandidateChunks(eventKey, column,
                () -> validateCandidate(eventKey, origin, candidates, index,
                        column, onFound, onFailure),
                () -> tryCandidate(eventKey, origin, candidates, index + 1,
                        onFound, onFailure));
    }

    private void validateCandidate(final String eventKey, final Location origin,
                                   final List<EventSpawnSafetyPolicy.Offset> candidates,
                                   final int index, final Location column,
                                   final Consumer<Location> onFound, final Runnable onFailure) {
        final World world = column.getWorld();
        if (world == null) {
            tryCandidate(eventKey, origin, candidates, index + 1, onFound, onFailure);
            return;
        }
        final int x = column.getBlockX();
        final int z = column.getBlockZ();
        final Location candidate = resolveSafeStandingLocation(eventKey, world, x, z);
        if (candidate == null || !reserveAfterSurfaceValidation(eventKey, candidate)) {
            tryCandidate(eventKey, origin, candidates, index + 1, onFound, onFailure);
            return;
        }
        onFound.accept(candidate);
    }

    /**
     * Prepares every chunk touched by the exact spawn column and shoreline buffer without
     * synchronously loading terrain on a Folia region thread. Only already-generated chunks
     * may be loaded; this search can never grow the world.
     */
    private void prepareCandidateChunks(final String eventKey, final Location column,
                                        final Runnable onReady, final Runnable onUnavailable) {
        final World world = column.getWorld();
        if (world == null) {
            runContinuation(onUnavailable);
            return;
        }
        final int radius = waterSafetyRequired(eventKey) ? shorelineRadius() : 0;
        final int minChunkX = (column.getBlockX() - radius) >> 4;
        final int maxChunkX = (column.getBlockX() + radius) >> 4;
        final int minChunkZ = (column.getBlockZ() - radius) >> 4;
        final int maxChunkZ = (column.getBlockZ() + radius) >> 4;
        final boolean requireLoaded = configManager.getBoolean(
                "world-events.safety.require-loaded-chunk", false);

        if (requireLoaded) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        runContinuation(onUnavailable);
                        return;
                    }
                }
            }
            scheduleCandidateRegion(column, onReady, onUnavailable);
            return;
        }

        final List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                loads.add(world.getChunkAtAsync(chunkX, chunkZ, false));
            }
        }
        if (loads.isEmpty()) {
            scheduleCandidateRegion(column, onReady, onUnavailable);
            return;
        }

        CompletableFuture.allOf(loads.toArray(CompletableFuture<?>[]::new))
                .whenComplete((ignored, failure) -> {
                    if (failure != null || !plugin.isEnabled()) {
                        runContinuation(onUnavailable);
                        return;
                    }
                    for (final CompletableFuture<Chunk> load : loads) {
                        final Chunk chunk;
                        try {
                            chunk = load.join();
                        } catch (final RuntimeException unavailable) {
                            runContinuation(onUnavailable);
                            return;
                        }
                        if (chunk == null || !world.isChunkLoaded(chunk)) {
                            runContinuation(onUnavailable);
                            return;
                        }
                    }
                    scheduleCandidateRegion(column, onReady, onUnavailable);
                });
    }

    private void scheduleCandidateRegion(final Location column, final Runnable onReady,
                                         final Runnable onUnavailable) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            plugin.getServer().getRegionScheduler().run(plugin, column, task -> onReady.run());
        } catch (final RuntimeException unavailable) {
            runContinuation(onUnavailable);
        }
    }

    private void runContinuation(final Runnable continuation) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            plugin.getServer().getGlobalRegionScheduler().run(
                    plugin, task -> continuation.run());
        } catch (final RuntimeException ignored) {
            // Disable won the race; no callback may safely continue after plugin shutdown.
        }
    }

    /**
     * Serializes reservation conflict checks. All non-surface rules are re-evaluated at
     * publication time, but water is not rescanned because resolveSafeStandingLocation
     * has already validated the exact column and shoreline buffer on this region thread.
     */
    private synchronized boolean reserveAfterSurfaceValidation(final String eventKey,
                                                               final Location location) {
        if (blockReason(eventKey, location, false) != BlockReason.NONE) {
            return false;
        }
        final long ttlMillis = Math.max(1L, configManager.getLong(
                "world-events.safety.reservation-seconds", 120L)) * 1_000L;
        reservations.put(eventKey, new Reservation(point(location), System.currentTimeMillis() + ttlMillis));
        return true;
    }

    public void clearReservations() {
        reservations.clear();
        players.clear();
    }

    private boolean insideBorder(final World world, final Location location) {
        final WorldBorder border = world.getWorldBorder();
        final Location center = border.getCenter();
        final double margin = Math.max(0.0D, configManager.getDouble(
                "world-events.safety.world-border-margin-blocks", 16.0D));
        final double half = Math.max(0.0D, border.getSize() / 2.0D - margin);
        return Math.abs(location.getX() - center.getX()) <= half
                && Math.abs(location.getZ() - center.getZ()) <= half;
    }

    private void logSearchFailure(final String eventKey, final Location origin, final int attempts) {
        final long now = System.currentTimeMillis();
        final long previous = diagnosticLogAt.getOrDefault(eventKey, 0L);
        if (now - previous < 60_000L || !diagnosticLogAt.replace(eventKey, previous, now)
                && diagnosticLogAt.putIfAbsent(eventKey, now) != null) {
            return;
        }
        plugin.getLogger().warning("Event spawn search aborted: event=" + eventKey
                + ", attempts=" + attempts + ", world=" + origin.getWorld().getName()
                + ", origin=" + origin.getBlockX() + "," + origin.getBlockZ()
                + ". No close, wet or forbidden fallback was used.");
    }

    private boolean rule(final String eventKey, final String protection) {
        return configManager.getBoolean("world-events.spawn-rules." + eventKey + "." + protection, true);
    }

    private boolean waterSafetyRequired(final String eventKey) {
        if (!configManager.getBoolean("world-events.water-safety.enabled", true)) {
            return false;
        }
        return configManager.getBoolean("world-events.water-safety.enforce-all-events", true)
                || rule(eventKey, "water");
    }

    private boolean masterSwitch() {
        if (configManager.getConfiguration() != null
                && configManager.getConfiguration().isSet("world-events.spawn-rules-enabled")) {
            return configManager.getBoolean("world-events.spawn-rules-enabled", true);
        }
        return configManager.getBoolean("world-events.avoid-territory", true);
    }

    private boolean safetyEnabled() {
        return configManager.getBoolean("world-events.safety.enabled", true);
    }

    private static EventSpawnSafetyPolicy.Point point(final Location location) {
        return new EventSpawnSafetyPolicy.Point(location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ());
    }

    public static void prepare(final Mob mob) {
        mob.getPersistentDataContainer().set(new NamespacedKey("icesmp", EVENT_NO_BURN_KEY),
                PersistentDataType.BYTE, (byte) 1);
        mob.getPersistentDataContainer().set(new NamespacedKey("icesmp", EVENT_NO_ZOMBIFICATION_KEY),
                PersistentDataType.BYTE, (byte) 1);
        if (mob instanceof org.bukkit.entity.PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        } else if (mob instanceof org.bukkit.entity.Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        } else if (mob instanceof org.bukkit.entity.AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        } else if (mob instanceof org.bukkit.entity.Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        } else if (mob instanceof org.bukkit.entity.Phantom phantom) {
            phantom.setShouldBurnInDay(false);
        }
    }
}
