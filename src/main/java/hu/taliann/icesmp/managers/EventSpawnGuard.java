package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.gui.EventSpawnConfigMenuExtension;
import hu.taliann.icesmp.listeners.EventSpawnDebugListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Shared fail-closed placement gate for world events. Protection checks, dynamic
 * player visibility distance, view-cone rejection, finite async search, shoreline
 * clearance, footprint/biome profiles, recent-location memory and cross-event
 * reservations live here so every event consumes one precedence model.
 */
public final class EventSpawnGuard {
    public static final String EVENT_NO_BURN_KEY = "event_no_daylight_burn";
    public static final String DAYLIGHT_BURN_BASELINE_KEY = "territory_no_daylight_burn_baseline";
    public static final String EVENT_NO_ZOMBIFICATION_KEY = "event_no_zombification";
    private static final int MAX_SINGLE_REGION_PROBE_RADIUS = 7;

    private static volatile EventSpawnGuard activeGuard;

    public enum BlockReason {
        NONE,
        INVALID_WORLD,
        TERRITORY,
        CLAIM,
        REGION,
        PLAYER_DISTANCE,
        PLAYER_VIEW_CONE,
        WORLD_SPAWN,
        WORLD_BORDER,
        RESERVED,
        RECENT_LOCATION,
        UNLOADED_CHUNK,
        UNSAFE_SURFACE,
        WATER_OR_SHORE,
        BIOME_PROFILE,
        FOOTPRINT_OR_SLOPE,
        SEARCH_BUDGET,
        SEARCH_TIMEOUT
    }

    private record Reservation(EventSpawnSafetyPolicy.Point point, long expiresAtMillis) { }

    private record RecentLocation(String eventKey, EventSpawnSafetyPolicy.Point point,
                                  long expiresAtMillis) { }

    private record SearchDiagnostic(String eventKey, boolean success, int attempts,
                                    int chunks, int terrainExpansionChunks,
                                    long elapsedMillis, String detail,
                                    Map<BlockReason, Integer> reasons) { }

    private static final class SearchContext {
        private final String eventKey;
        private final long startedAtMillis;
        private final long deadlineMillis;
        private final int maxChunks;
        private final int maxTerrainExpansionChunks;
        private final boolean debugOnly;
        private final AtomicBoolean completed = new AtomicBoolean();
        private final AtomicInteger attempts = new AtomicInteger();
        private final Set<String> chunks = ConcurrentHashMap.newKeySet();
        private final Set<String> terrainExpansionChunks = ConcurrentHashMap.newKeySet();
        private final Map<BlockReason, Integer> reasons = new ConcurrentHashMap<>();
        private final List<String> debugLines = new ArrayList<>();

        private SearchContext(final String eventKey, final long timeoutMillis,
                              final int maxChunks, final int maxTerrainExpansionChunks,
                              final boolean debugOnly) {
            this.eventKey = eventKey;
            this.startedAtMillis = System.currentTimeMillis();
            this.deadlineMillis = startedAtMillis + timeoutMillis;
            this.maxChunks = maxChunks;
            this.maxTerrainExpansionChunks = maxTerrainExpansionChunks;
            this.debugOnly = debugOnly;
        }

        private boolean timedOut() {
            return System.currentTimeMillis() > deadlineMillis;
        }

        private void reject(final BlockReason reason) {
            reasons.merge(reason, 1, Integer::sum);
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final ClaimManager claimManager;
    private final Map<UUID, EventSpawnSafetyPolicy.PlayerPoint> players = new ConcurrentHashMap<>();
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<RecentLocation> recentLocations = new ConcurrentLinkedDeque<>();
    private final Map<String, Long> diagnosticLogAt = new ConcurrentHashMap<>();
    private final Map<String, Long> searchBackoffUntil = new ConcurrentHashMap<>();
    private final Set<String> pendingArrivals = ConcurrentHashMap.newKeySet();
    private final Map<String, SearchDiagnostic> diagnostics = new ConcurrentHashMap<>();
    private final AtomicInteger activeSearches = new AtomicInteger();
    private volatile Predicate<UUID> vanishedPredicate = ignored -> false;

    public EventSpawnGuard(final JavaPlugin plugin, final ConfigManager configManager,
                           final TerritoryManager territoryManager, final ClaimManager claimManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.claimManager = claimManager;
        activeGuard = this;
        EventSpawnConfigMenuExtension.install();
        Bukkit.getPluginManager().registerEvents(new EventSpawnDebugListener(plugin, this), plugin);
    }

    /** Runtime singleton bridge for managers built before the guard in the manual DI order. */
    public static EventSpawnGuard current() {
        return activeGuard;
    }

    public void setVanishedPredicate(final Predicate<UUID> vanishedPredicate) {
        this.vanishedPredicate = vanishedPredicate == null ? ignored -> false : vanishedPredicate;
    }

    /** Capture entity-owned values; later guard reads are immutable and region-safe. */
    public void trackPlayer(final Player player) {
        trackPlayer(player, player.getLocation(), player.getGameMode());
    }

    public void trackPlayer(final Player player, final Location location, final GameMode gameMode) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        final Vector direction = location.getDirection();
        players.put(player.getUniqueId(), new EventSpawnSafetyPolicy.PlayerPoint(
                player.getUniqueId(), point(location), gameMode == GameMode.SPECTATOR,
                false, player.hasPermission("icesmp.admin.all"),
                direction.getX(), direction.getZ(), player.getSendViewDistance()));
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
     * The safe-search path already performed the expensive shoreline/footprint scan.
     * It calls this overload with includeSurface=false so non-surface rules are rechecked
     * without reading the same columns again.
     */
    private BlockReason blockReason(final String eventKey, final Location location,
                                    final boolean includeSurface) {
        if (location == null || location.getWorld() == null) {
            return BlockReason.INVALID_WORLD;
        }
        final String key = normalizeEventKey(eventKey);
        if (masterSwitch()) {
            if (rule(key, "territory") && territoryManager.getTerritoryAt(location) != null) {
                return BlockReason.TERRITORY;
            }
            if (rule(key, "claim") && claimManager.getClaimAt(location) != null) {
                return BlockReason.CLAIM;
            }
            if (rule(key, "region")
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
        if (includeSurface) {
            if (!world.isChunkLoaded(chunkX, chunkZ)
                    || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
                return BlockReason.UNLOADED_CHUNK;
            }
            final BlockReason surface = surfaceReason(key, world,
                    location.getBlockX(), location.getBlockZ());
            if (surface != BlockReason.NONE) {
                return surface;
            }
        }

        final List<EventSpawnSafetyPolicy.PlayerPoint> snapshot = playerSnapshot();
        final boolean ignoreSpectators = configManager.getBoolean(
                "world-events.safety.ignore-spectators", true);
        final boolean ignoreVanished = configManager.getBoolean(
                "world-events.safety.ignore-vanished", true);
        final boolean ignoreAdmins = configManager.getBoolean(
                "world-events.safety.ignore-admins", false);
        if (EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(candidate, snapshot,
                effectiveHorizontalMinimum(key),
                configManager.getDouble("world-events.safety.min-3d-distance-blocks", 0.0D),
                ignoreSpectators, ignoreVanished, ignoreAdmins)) {
            return BlockReason.PLAYER_DISTANCE;
        }
        if (visibilityConeEnabled(key) && EventSpawnSafetyPolicy.visibleInsidePlayerCone(
                candidate, snapshot, visibilityConeDistance(key), visibilityConeAngle(key),
                ignoreSpectators, ignoreVanished, ignoreAdmins)) {
            return BlockReason.PLAYER_VIEW_CONE;
        }
        if (!configManager.getBoolean(profilePath(key, "ignore-recent-locations"), false)
                && recentLocationBlocked(key, candidate)) {
            return BlockReason.RECENT_LOCATION;
        }
        if (!biomeAllowed(key, location)) {
            return BlockReason.BIOME_PROFILE;
        }

        final long now = System.currentTimeMillis();
        final double reservationDistance = Math.max(0.0D, configManager.getDouble(
                "world-events.safety.reservation-distance-blocks", 64.0D));
        reservations.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
        for (final Map.Entry<String, Reservation> entry : reservations.entrySet()) {
            if (!eventFamily(entry.getKey()).equals(eventFamily(key))
                    && EventSpawnSafetyPolicy.withinHorizontal(candidate,
                    entry.getValue().point(), reservationDistance)) {
                return BlockReason.RESERVED;
            }
        }
        return BlockReason.NONE;
    }

    /**
     * Resolves stable footing on the region thread owning x/z. Leaves, gravity
     * blocks, liquids, damaging floors, steep/blocked footprints and shoreline
     * columns are rejected; tall mobs get three passable body blocks.
     */
    public Location resolveSafeStandingLocation(final String eventKey, final World world,
                                                final int x, final int z) {
        final String key = normalizeEventKey(eventKey);
        final BlockReason surface = surfaceReason(key, world, x, z);
        if (surface != BlockReason.NONE) {
            return null;
        }
        final int floorY = standingFloorY(world, x, z);
        return new Location(world, x + 0.5D, floorY + 1.0D, z + 0.5D);
    }

    public boolean isUnsafeSurface(final String eventKey, final World world, final int x, final int z) {
        return surfaceReason(normalizeEventKey(eventKey), world, x, z) != BlockReason.NONE;
    }

    private BlockReason surfaceReason(final String eventKey, final World world,
                                      final int x, final int z) {
        if (world == null) {
            return BlockReason.INVALID_WORLD;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)
                || !Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            return BlockReason.UNLOADED_CHUNK;
        }
        final int floorY = standingFloorY(world, x, z);
        if (floorY == Integer.MIN_VALUE) {
            return BlockReason.UNSAFE_SURFACE;
        }
        final Location center = new Location(world, x + 0.5D, floorY + 1.0D, z + 0.5D);
        if (!biomeAllowed(eventKey, center)) {
            return BlockReason.BIOME_PROFILE;
        }
        if (!footprintAllowed(eventKey, world, x, z, floorY)) {
            return BlockReason.FOOTPRINT_OR_SLOPE;
        }
        if (waterSafetyRequired(eventKey) && waterOrShoreUnsafe(eventKey, world, x, z)) {
            return BlockReason.WATER_OR_SHORE;
        }
        return BlockReason.NONE;
    }

    private int standingFloorY(final World world, final int x, final int z) {
        final int floorY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (floorY <= world.getMinHeight() || floorY + 3 >= world.getMaxHeight()) {
            return Integer.MIN_VALUE;
        }
        final Block floor = world.getBlockAt(x, floorY, z);
        final Block feet = world.getBlockAt(x, floorY + 1, z);
        final Block head = world.getBlockAt(x, floorY + 2, z);
        final Block upperHead = world.getBlockAt(x, floorY + 3, z);
        if (!stableFloor(floor)
                || !clearBody(feet) || !clearBody(head) || !clearBody(upperHead)) {
            return Integer.MIN_VALUE;
        }
        return floorY;
    }

    private boolean footprintAllowed(final String eventKey, final World world,
                                     final int centerX, final int centerZ, final int centerFloorY) {
        final int radius = footprintRadius(eventKey);
        if (radius <= 0) {
            return true;
        }
        final int maxDelta = Math.max(0, configManager.getInt(
                profilePath(eventKey, "max-height-delta-blocks"), defaultMaxHeightDelta(eventKey)));
        final int step = Math.max(1, Math.min(radius, configManager.getInt(
                profilePath(eventKey, "footprint-sample-step-blocks"), 2)));
        final int squared = radius * radius;
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx * dx + dz * dz > squared) {
                    continue;
                }
                final int x = centerX + dx;
                final int z = centerZ + dz;
                final int chunkX = x >> 4;
                final int chunkZ = z >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)
                        || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
                    return false;
                }
                final int floorY = standingFloorY(world, x, z);
                if (floorY == Integer.MIN_VALUE || Math.abs(floorY - centerFloorY) > maxDelta) {
                    return false;
                }
            }
        }
        if (configManager.getBoolean(profilePath(eventKey, "require-sky"), false)) {
            return world.getHighestBlockYAt(centerX, centerZ, HeightMap.WORLD_SURFACE)
                    <= centerFloorY + 1;
        }
        return true;
    }

    private boolean waterOrShoreUnsafe(final String eventKey, final World world,
                                       final int centerX, final int centerZ) {
        for (final EventSpawnSafetyPolicy.GridOffset offset
                : EventSpawnSafetyPolicy.waterProbeOffsets(shorelineRadius(eventKey))) {
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

    private int shorelineRadius(final String eventKey) {
        final String key = normalizeEventKey(eventKey);
        if (key.endsWith("-route") || key.endsWith("-wave")) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_SINGLE_REGION_PROBE_RADIUS, configManager.getInt(
                "world-events.water-safety.buffer-blocks", MAX_SINGLE_REGION_PROBE_RADIUS)));
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
        final SearchContext context = beginSearch(eventKey, false, onFailure);
        if (context == null || origin == null || origin.getWorld() == null) {
            if (context != null) {
                failSearch(context, BlockReason.INVALID_WORLD, origin, onFailure);
            }
            return;
        }
        final Location preferred = origin.clone();
        prepareCandidateChunks(context, preferred,
                () -> validatePreferred(context, preferred, seed, onFound, onFailure),
                () -> searchNear(context, preferred, seed, onFound, onFailure));
    }

    private void validatePreferred(final SearchContext context, final Location column,
                                   final long seed, final Consumer<Location> onFound,
                                   final Runnable onFailure) {
        if (!contextAlive(context, column, onFailure)) {
            return;
        }
        context.attempts.incrementAndGet();
        final World world = column.getWorld();
        final int x = column.getBlockX();
        final int z = column.getBlockZ();
        final BlockReason surface = surfaceReason(context.eventKey, world, x, z);
        if (surface == BlockReason.NONE) {
            final Location candidate = new Location(world, x + 0.5D,
                    standingFloorY(world, x, z) + 1.0D, z + 0.5D);
            final BlockReason reason = blockReason(context.eventKey, candidate, false);
            if (reason == BlockReason.NONE && reserve(context, candidate)) {
                publishFound(context, candidate, onFound, onFailure);
                return;
            }
            context.reject(reason == BlockReason.NONE ? BlockReason.RESERVED : reason);
        } else {
            context.reject(surface);
        }
        searchNear(context, column, seed, onFound, onFailure);
    }

    /** Finite Folia-safe search; no valid location means a controlled abort. */
    public void findSafeNear(final String eventKey, final Location origin, final long seed,
                             final Consumer<Location> onFound, final Runnable onFailure) {
        final SearchContext context = beginSearch(eventKey, false, onFailure);
        if (context == null || origin == null || origin.getWorld() == null) {
            if (context != null) {
                failSearch(context, BlockReason.INVALID_WORLD, origin, onFailure);
            }
            return;
        }
        searchNear(context, origin.clone(), seed, onFound, onFailure);
    }

    private void searchNear(final SearchContext context, final Location origin, final long seed,
                            final Consumer<Location> onFound, final Runnable onFailure) {
        final List<EventSpawnSafetyPolicy.Offset> candidates = EventSpawnSafetyPolicy.candidates(
                configManager.getInt("world-events.safety.search-attempts", 32),
                searchMinRadius(context.eventKey), searchMaxRadius(context.eventKey), seed);
        tryCandidate(context, origin, candidates, 0, seed, false, onFound, onFailure,
                () -> searchTerrainExpansion(context, origin, seed, onFound, onFailure));
    }

    private void searchTerrainExpansion(final SearchContext context, final Location origin,
                                        final long seed, final Consumer<Location> onFound,
                                        final Runnable onFailure) {
        if (!terrainExpansionEnabled(context.eventKey)
                || context.maxTerrainExpansionChunks <= 0
                || configManager.getBoolean(
                        "world-events.safety.require-loaded-chunk", false)) {
            failSearch(context, BlockReason.SEARCH_BUDGET, origin, onFailure);
            return;
        }
        final int attempts = Math.max(1, configManager.getInt(
                profilePath(context.eventKey, "terrain-expansion-attempts"),
                configManager.getInt(
                        "world-events.placement.terrain-expansion.attempts", 24)));
        final long rescueSeed = seed ^ 0x6A09E667F3BCC909L;
        final double rescueMaxRadius = Math.max(searchMaxRadius(context.eventKey),
                configManager.getDouble(
                        profilePath(context.eventKey, "terrain-expansion-max-radius-blocks"),
                        configManager.getDouble(
                                "world-events.placement.terrain-expansion.max-radius-blocks",
                                768.0D)));
        final List<EventSpawnSafetyPolicy.Offset> candidates = EventSpawnSafetyPolicy.candidates(
                attempts, searchMinRadius(context.eventKey), rescueMaxRadius,
                rescueSeed);
        tryCandidate(context, origin, candidates, 0, rescueSeed, true, onFound, onFailure,
                () -> failSearch(context, BlockReason.SEARCH_BUDGET, origin, onFailure));
    }

    private void tryCandidate(final SearchContext context, final Location origin,
                              final List<EventSpawnSafetyPolicy.Offset> candidates, final int index,
                              final long seed, final boolean allowTerrainExpansion,
                              final Consumer<Location> onFound, final Runnable onFailure,
                              final Runnable onExhausted) {
        if (!contextAlive(context, origin, onFailure)) {
            return;
        }
        if (index >= candidates.size()) {
            onExhausted.run();
            return;
        }
        final EventSpawnSafetyPolicy.Offset offset = candidates.get(index);
        final Location column = centeredCandidate(origin, offset);
        if (column.getWorld() == null) {
            context.reject(BlockReason.INVALID_WORLD);
            tryCandidate(context, origin, candidates, index + 1, seed, allowTerrainExpansion,
                    onFound, onFailure, onExhausted);
            return;
        }
        prepareCandidateChunks(context, column, allowTerrainExpansion,
                () -> validateCandidate(context, origin, candidates, index,
                        column, seed, allowTerrainExpansion, onFound, onFailure, onExhausted),
                () -> {
                    context.reject(BlockReason.UNLOADED_CHUNK);
                    tryCandidate(context, origin, candidates, index + 1, seed,
                            allowTerrainExpansion, onFound, onFailure, onExhausted);
                });
    }

    private void validateCandidate(final SearchContext context, final Location origin,
                                   final List<EventSpawnSafetyPolicy.Offset> candidates,
                                   final int index, final Location column,
                                   final long seed, final boolean allowTerrainExpansion,
                                   final Consumer<Location> onFound, final Runnable onFailure,
                                   final Runnable onExhausted) {
        if (!contextAlive(context, origin, onFailure)) {
            return;
        }
        final List<EventSpawnSafetyPolicy.GridOffset> probes =
                EventSpawnSafetyPolicy.candidateProbeOffsets(
                        candidateProbeRadius(context.eventKey), maxColumnProbes(context.eventKey),
                        seed ^ (index * 0x9E3779B97F4A7C15L));
        validateCandidateProbe(context, origin, candidates, index, column, probes, 0,
                seed, allowTerrainExpansion, onFound, onFailure, onExhausted);
    }

    private void validateCandidateProbe(final SearchContext context, final Location origin,
                                        final List<EventSpawnSafetyPolicy.Offset> candidates,
                                        final int index, final Location column,
                                        final List<EventSpawnSafetyPolicy.GridOffset> probes,
                                        final int probeIndex, final long seed,
                                        final boolean allowTerrainExpansion,
                                        final Consumer<Location> onFound,
                                        final Runnable onFailure, final Runnable onExhausted) {
        if (!contextAlive(context, origin, onFailure)) {
            return;
        }
        if (probeIndex >= probes.size()) {
            tryCandidate(context, origin, candidates, index + 1, seed, allowTerrainExpansion,
                    onFound, onFailure, onExhausted);
            return;
        }
        context.attempts.incrementAndGet();
        final EventSpawnSafetyPolicy.GridOffset probe = probes.get(probeIndex);
        final Location probedColumn = column.clone().add(probe.x(), 0.0D, probe.z());
        final World world = probedColumn.getWorld();
        final int x = probedColumn.getBlockX();
        final int z = probedColumn.getBlockZ();
        final BlockReason surface = surfaceReason(context.eventKey, world, x, z);
        if (surface != BlockReason.NONE) {
            context.reject(surface);
            validateCandidateProbe(context, origin, candidates, index, column, probes,
                    probeIndex + 1, seed, allowTerrainExpansion, onFound, onFailure, onExhausted);
            return;
        }
        final Location candidate = new Location(world, x + 0.5D,
                standingFloorY(world, x, z) + 1.0D, z + 0.5D);
        final BlockReason reason = blockReason(context.eventKey, candidate, false);
        if (reason != BlockReason.NONE || !reserve(context, candidate)) {
            context.reject(reason == BlockReason.NONE ? BlockReason.RESERVED : reason);
            validateCandidateProbe(context, origin, candidates, index, column, probes,
                    probeIndex + 1, seed, allowTerrainExpansion, onFound, onFailure, onExhausted);
            return;
        }
        publishFound(context, candidate, onFound, onFailure);
    }

    private boolean reserve(final SearchContext context, final Location location) {
        if (context.debugOnly) {
            return true;
        }
        return reserveAfterSurfaceValidation(context.eventKey, location);
    }

    /**
     * Prepares every chunk touched by the spawn footprint and shoreline buffer without
     * synchronously loading terrain on a Folia region thread. The normal phase only reloads
     * generated terrain; the bounded rescue phase may asynchronously expand a few chunks.
     */
    private void prepareCandidateChunks(final SearchContext context, final Location column,
                                        final Runnable onReady, final Runnable onUnavailable) {
        prepareCandidateChunks(context, column, false, onReady, onUnavailable);
    }

    private void prepareCandidateChunks(final SearchContext context, final Location column,
                                        final boolean allowTerrainExpansion,
                                        final Runnable onReady, final Runnable onUnavailable) {
        if (context.completed.get() || !plugin.isEnabled()) {
            return;
        }
        if (context.timedOut()) {
            runContinuation(onUnavailable);
            return;
        }
        final World world = column.getWorld();
        if (world == null) {
            runContinuation(onUnavailable);
            return;
        }
        final int radius = candidateProbeRadius(context.eventKey);
        final int minChunkX = (column.getBlockX() - radius) >> 4;
        final int maxChunkX = (column.getBlockX() + radius) >> 4;
        final int minChunkZ = (column.getBlockZ() - radius) >> 4;
        final int maxChunkZ = (column.getBlockZ() + radius) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                context.chunks.add(world.getUID() + ":" + chunkX + ":" + chunkZ);
                if (context.chunks.size() > context.maxChunks) {
                    context.reject(BlockReason.SEARCH_BUDGET);
                    runContinuation(onUnavailable);
                    return;
                }
            }
        }

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
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    if (allowTerrainExpansion) {
                        final String chunkKey = world.getUID() + ":" + chunkX + ":" + chunkZ;
                        if (context.terrainExpansionChunks.add(chunkKey)
                                && context.terrainExpansionChunks.size()
                                > context.maxTerrainExpansionChunks) {
                            context.terrainExpansionChunks.remove(chunkKey);
                            context.reject(BlockReason.SEARCH_BUDGET);
                            runContinuation(onUnavailable);
                            return;
                        }
                    }
                    loads.add(world.getChunkAtAsync(chunkX, chunkZ, allowTerrainExpansion));
                }
            }
        }
        if (loads.isEmpty()) {
            scheduleCandidateRegion(column, onReady, onUnavailable);
            return;
        }

        CompletableFuture.allOf(loads.toArray(CompletableFuture<?>[]::new))
                .whenComplete((ignored, failure) -> {
                    if (failure != null || !plugin.isEnabled() || context.timedOut()) {
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

    private void publishFound(final SearchContext context, final Location location,
                              final Consumer<Location> onFound, final Runnable onFailure) {
        if (context.debugOnly) {
            context.debugLines.add("§aKiválasztott hely: §f" + location.getWorld().getName()
                    + " " + location.getBlockX() + ", " + location.getBlockY()
                    + ", " + location.getBlockZ());
            final SearchDiagnostic diagnostic = new SearchDiagnostic(context.eventKey, true,
                    context.attempts.get(), context.chunks.size(),
                    context.terrainExpansionChunks.size(),
                    System.currentTimeMillis() - context.startedAtMillis,
                    "candidate-selected", Map.copyOf(context.reasons));
            diagnostics.put(context.eventKey, diagnostic);
            if (context.completed.compareAndSet(false, true)) {
                activeSearches.decrementAndGet();
            }
            context.debugLines.add(formatDiagnostic(diagnostic));
            onFound.accept(location);
            return;
        }
        final boolean arrival = configManager.getBoolean(
                profilePath(context.eventKey, "arrival.enabled"),
                configManager.getBoolean("world-events.placement.arrival.enabled", true));
        final long delayTicks = Math.max(0L, configManager.getLong(
                profilePath(context.eventKey, "arrival.delay-seconds"),
                configManager.getLong("world-events.placement.arrival.delay-seconds", 3L))) * 20L;
        if (!arrival || delayTicks <= 0L) {
            markRecent(context.eventKey, location);
            completeSuccess(context, location, onFound);
            return;
        }

        final World world = location.getWorld();
        if (world == null) {
            failSearch(context, BlockReason.INVALID_WORLD, location, onFailure);
            return;
        }
        if (!completeSearchPhase(context, location, "arrival-pending")) {
            return;
        }
        pendingArrivals.add(context.eventKey);
        try {
            showArrivalSigns(context.eventKey, location);
        } catch (final RuntimeException visualFailure) {
            plugin.getLogger().fine("Event arrival cue skipped for " + context.eventKey
                    + ": " + visualFailure.getMessage());
        }
        try {
            plugin.getServer().getRegionScheduler().runDelayed(plugin, location, task -> {
                if (!plugin.isEnabled()) {
                    pendingArrivals.remove(context.eventKey);
                    return;
                }
                final BlockReason surface = surfaceReason(context.eventKey, world,
                        location.getBlockX(), location.getBlockZ());
                final BlockReason reason = surface == BlockReason.NONE
                        ? blockReason(context.eventKey, location, false) : surface;
                if (reason != BlockReason.NONE) {
                    releaseFamilyReservation(context.eventKey);
                    pendingArrivals.remove(context.eventKey);
                    recordArrivalFailure(context, reason, location);
                    onFailure.run();
                    return;
                }
                pendingArrivals.remove(context.eventKey);
                markRecent(context.eventKey, location);
                diagnostics.put(context.eventKey, new SearchDiagnostic(context.eventKey, true,
                        context.attempts.get(), context.chunks.size(),
                        context.terrainExpansionChunks.size(),
                        System.currentTimeMillis() - context.startedAtMillis,
                        "arrival-complete@" + world.getName() + ":"
                                + location.getBlockX() + "," + location.getBlockZ(),
                        Map.copyOf(context.reasons)));
                onFound.accept(location);
            }, delayTicks);
        } catch (final RuntimeException unavailable) {
            releaseFamilyReservation(context.eventKey);
            pendingArrivals.remove(context.eventKey);
            recordArrivalFailure(context, BlockReason.UNLOADED_CHUNK, location);
            onFailure.run();
        }
    }

    private boolean completeSearchPhase(final SearchContext context, final Location location,
                                        final String detail) {
        if (!context.completed.compareAndSet(false, true)) {
            return false;
        }
        activeSearches.decrementAndGet();
        diagnostics.put(context.eventKey, new SearchDiagnostic(context.eventKey, true,
                context.attempts.get(), context.chunks.size(),
                context.terrainExpansionChunks.size(),
                System.currentTimeMillis() - context.startedAtMillis,
                detail + "@" + location.getWorld().getName() + ":"
                        + location.getBlockX() + "," + location.getBlockZ(),
                Map.copyOf(context.reasons)));
        return true;
    }

    private void recordArrivalFailure(final SearchContext context, final BlockReason reason,
                                      final Location location) {
        context.reject(reason);
        final long backoffMillis = Math.max(0L, configManager.getLong(
                "world-events.placement.search-backoff-seconds", 30L)) * 1_000L;
        searchBackoffUntil.put(context.eventKey, System.currentTimeMillis() + backoffMillis);
        diagnostics.put(context.eventKey, new SearchDiagnostic(context.eventKey, false,
                context.attempts.get(), context.chunks.size(),
                context.terrainExpansionChunks.size(),
                System.currentTimeMillis() - context.startedAtMillis,
                "arrival-revalidation-" + reason + "@" + location.getWorld().getName()
                        + ":" + location.getBlockX() + "," + location.getBlockZ(),
                Map.copyOf(context.reasons)));
    }

    private void showArrivalSigns(final String eventKey, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        if (configManager.getBoolean("world-events.placement.arrival.particles", true)) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    location.clone().add(0.0D, 1.0D, 0.0D),
                    18, 1.5D, 0.8D, 1.5D, 0.01D);
            world.spawnParticle(Particle.SOUL,
                    location.clone().add(0.0D, 1.0D, 0.0D),
                    8, 1.0D, 0.5D, 1.0D, 0.01D);
        }
        if (configManager.getBoolean("world-events.placement.arrival.sound", true)) {
            world.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 0.65F);
        }
        if (!configManager.getBoolean("world-events.placement.arrival.player-hint", true)) {
            return;
        }
        final UUID worldId = world.getUID();
        final Location target = location.clone();
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> {
                if (!player.isOnline() || !player.getWorld().getUID().equals(worldId)) {
                    return;
                }
                final Location from = player.getLocation();
                final String direction = cardinalDirection(
                        target.getX() - from.getX(), target.getZ() - from.getZ());
                player.sendActionBar(Component.text(
                        arrivalHint(eventKey) + " " + direction + " felől…",
                        NamedTextColor.DARK_AQUA));
                if (configManager.getBoolean("world-events.placement.arrival.sound", true)) {
                    player.playSound(from, arrivalSound(eventKey), 0.35F, 0.65F);
                }
            }, null);
        }
    }

    private static String arrivalHint(final String eventKey) {
        return switch (eventKey) {
            case "meteor" -> "Az égbolt mélyén valami felizzik";
            case "caravan", "player-caravan", "escort" -> "Távoli kürtszó visszhangzik";
            case "cultists", "corruption" -> "Fojtott kántálás szivárog a szélben";
            case "wild-hunt" -> "Vad üvöltés hasítja ketté a csendet";
            case "world-boss", "invasion" -> "A föld baljósan megremeg";
            default -> "Valami megmozdult a vadonban";
        };
    }

    private static Sound arrivalSound(final String eventKey) {
        return switch (eventKey) {
            case "meteor" -> Sound.ENTITY_GENERIC_EXPLODE;
            case "caravan", "player-caravan", "escort" -> Sound.EVENT_RAID_HORN;
            case "cultists", "corruption" -> Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD;
            default -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
        };
    }

    private static String cardinalDirection(final double dx, final double dz) {
        if (Math.abs(dx) < 1.0D && Math.abs(dz) < 1.0D) {
            return "közvetlenül itt";
        }
        final double angle = Math.toDegrees(Math.atan2(-dx, dz));
        final int sector = Math.floorMod((int) Math.round(angle / 45.0D), 8);
        return switch (sector) {
            case 0 -> "dél";
            case 1 -> "délnyugat";
            case 2 -> "nyugat";
            case 3 -> "északnyugat";
            case 4 -> "észak";
            case 5 -> "északkelet";
            case 6 -> "kelet";
            default -> "délkelet";
        };
    }

    private SearchContext beginSearch(final String eventKey, final boolean debugOnly,
                                      final Runnable onFailure) {
        final String key = normalizeEventKey(eventKey);
        final long now = System.currentTimeMillis();
        if (!debugOnly && (searchBackoffUntil.getOrDefault(key, 0L) > now
                || pendingArrivals.contains(key))) {
            onFailure.run();
            return null;
        }
        final int maxConcurrent = Math.max(1, configManager.getInt(
                "world-events.placement.max-concurrent-searches", 2));
        while (true) {
            final int current = activeSearches.get();
            if (current >= maxConcurrent) {
                onFailure.run();
                return null;
            }
            if (activeSearches.compareAndSet(current, current + 1)) {
                break;
            }
        }
        final long configuredTimeout = Math.max(250L, configManager.getLong(
                "world-events.placement.search-timeout-millis", 5000L));
        final long timeout = terrainExpansionEnabled(key)
                ? Math.max(configuredTimeout, configManager.getLong(
                        "world-events.placement.terrain-expansion.minimum-timeout-millis", 15000L))
                : configuredTimeout;
        final int maxChunks = Math.max(1, configManager.getInt(
                "world-events.placement.max-chunks-per-search", 96));
        final int maxTerrainExpansionChunks = terrainExpansionEnabled(key)
                ? Math.max(0, configManager.getInt(
                        "world-events.placement.terrain-expansion.max-new-chunks-per-search", 24))
                : 0;
        final SearchContext context = new SearchContext(key, timeout, maxChunks,
                maxTerrainExpansionChunks, debugOnly);
        final long timeoutTicks = Math.max(1L, (timeout + 49L) / 50L);
        try {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (!context.completed.get()) {
                    failSearch(context, BlockReason.SEARCH_TIMEOUT, null, onFailure);
                }
            }, timeoutTicks);
        } catch (final RuntimeException unavailable) {
            failSearch(context, BlockReason.SEARCH_TIMEOUT, null, onFailure);
            return null;
        }
        return context;
    }

    private boolean contextAlive(final SearchContext context, final Location origin,
                                 final Runnable onFailure) {
        if (context.completed.get() || !plugin.isEnabled()) {
            return false;
        }
        if (context.timedOut()) {
            failSearch(context, BlockReason.SEARCH_TIMEOUT, origin, onFailure);
            return false;
        }
        return true;
    }

    private void completeSuccess(final SearchContext context, final Location location,
                                 final Consumer<Location> onFound) {
        if (completeSearchPhase(context, location, "candidate-selected")) {
            onFound.accept(location);
        }
    }

    private void failSearch(final SearchContext context, final BlockReason terminalReason,
                            final Location origin, final Runnable onFailure) {
        context.reject(terminalReason);
        if (!context.completed.compareAndSet(false, true)) {
            return;
        }
        activeSearches.decrementAndGet();
        releaseFamilyReservation(context.eventKey);
        if (!context.debugOnly) {
            final long backoffMillis = Math.max(0L, configManager.getLong(
                    "world-events.placement.search-backoff-seconds", 30L)) * 1_000L;
            searchBackoffUntil.put(context.eventKey, System.currentTimeMillis() + backoffMillis);
        }
        final String detail = origin == null || origin.getWorld() == null ? "unknown"
                : origin.getWorld().getName() + ":" + origin.getBlockX() + "," + origin.getBlockZ();
        diagnostics.put(context.eventKey, new SearchDiagnostic(context.eventKey, false,
                context.attempts.get(), context.chunks.size(),
                context.terrainExpansionChunks.size(),
                System.currentTimeMillis() - context.startedAtMillis,
                terminalReason + "@" + detail, Map.copyOf(context.reasons)));
        if (context.debugOnly) {
            final SearchDiagnostic diagnostic = diagnostics.get(context.eventKey);
            if (diagnostic != null) {
                context.debugLines.add(formatDiagnostic(diagnostic));
            }
            onFailure.run();
            return;
        }
        logSearchFailure(context, origin);
        onFailure.run();
    }

    /**
     * Validates an escort-style route on the owning region of every sampled point.
     * The endpoint is returned only when all quarter-route samples satisfy the full
     * surface, footprint, water, protection and player-visibility policy.
     */
    public void findSafeRoute(final String eventKey, final Location start,
                              final double distance, final long seed,
                              final Consumer<Location> onFound, final Runnable onFailure) {
        final SearchContext context = beginSearch(eventKey, false, onFailure);
        if (context == null || start == null || start.getWorld() == null) {
            if (context != null) {
                failSearch(context, BlockReason.INVALID_WORLD, start, onFailure);
            } else {
                releaseFamilyReservation(eventKey);
            }
            return;
        }
        final int headings = Math.max(1, configManager.getInt(
                "world-events.placement.route-attempts", 8));
        final List<EventSpawnSafetyPolicy.Offset> offsets = EventSpawnSafetyPolicy.candidates(
                headings, Math.max(1.0D, distance), Math.max(1.0D, distance), seed);
        tryRouteHeading(context, start.clone(), offsets, 0, onFound, onFailure);
    }

    private void tryRouteHeading(final SearchContext context, final Location start,
                                 final List<EventSpawnSafetyPolicy.Offset> offsets,
                                 final int headingIndex, final Consumer<Location> onFound,
                                 final Runnable onFailure) {
        if (!contextAlive(context, start, onFailure)) {
            return;
        }
        if (headingIndex >= offsets.size()) {
            failSearch(context, BlockReason.SEARCH_BUDGET, start, onFailure);
            return;
        }
        validateRouteSample(context, start, offsets, headingIndex, 1, null,
                onFound, onFailure);
    }

    private void validateRouteSample(final SearchContext context, final Location start,
                                     final List<EventSpawnSafetyPolicy.Offset> offsets,
                                     final int headingIndex, final int quarter,
                                     final Location acceptedEndpoint,
                                     final Consumer<Location> onFound, final Runnable onFailure) {
        if (!contextAlive(context, start, onFailure)) {
            return;
        }
        if (quarter > 4) {
            if (acceptedEndpoint == null) {
                context.reject(BlockReason.UNSAFE_SURFACE);
                tryRouteHeading(context, start, offsets, headingIndex + 1, onFound, onFailure);
                return;
            }
            markRecent(context.eventKey, acceptedEndpoint);
            completeSuccess(context, acceptedEndpoint, onFound);
            return;
        }
        final EventSpawnSafetyPolicy.Offset offset = offsets.get(headingIndex);
        final double fraction = quarter / 4.0D;
        final Location column = start.clone().add(offset.x() * fraction, 0.0D,
                offset.z() * fraction);
        prepareCandidateChunks(context, column, () -> {
            context.attempts.incrementAndGet();
            final World world = column.getWorld();
            final int x = column.getBlockX();
            final int z = column.getBlockZ();
            final BlockReason surface = surfaceReason(context.eventKey, world, x, z);
            if (surface != BlockReason.NONE) {
                context.reject(surface);
                tryRouteHeading(context, start, offsets, headingIndex + 1, onFound, onFailure);
                return;
            }
            final Location resolved = new Location(world, x + 0.5D,
                    standingFloorY(world, x, z) + 1.0D, z + 0.5D);
            final BlockReason reason = blockReason(context.eventKey, resolved, false);
            if (reason != BlockReason.NONE) {
                context.reject(reason);
                tryRouteHeading(context, start, offsets, headingIndex + 1, onFound, onFailure);
                return;
            }
            validateRouteSample(context, start, offsets, headingIndex, quarter + 1,
                    quarter == 4 ? resolved : acceptedEndpoint, onFound, onFailure);
        }, () -> {
            context.reject(BlockReason.UNLOADED_CHUNK);
            tryRouteHeading(context, start, offsets, headingIndex + 1, onFound, onFailure);
        });
    }

    /** Runs the real candidate pipeline without reserving or spawning anything. */
    public void debugSearch(final String eventKey, final Location origin,
                            final Consumer<List<String>> onComplete) {
        final SearchContext context = beginSearch(eventKey, true,
                () -> onComplete.accept(List.of("§cA debug keresés nem indítható: keresési limit.")));
        if (context == null) {
            return;
        }
        context.debugLines.addAll(policySummary(context.eventKey));
        final Consumer<Location> found = ignored -> onComplete.accept(List.copyOf(context.debugLines));
        final Runnable failed = () -> onComplete.accept(List.copyOf(context.debugLines));
        if (origin == null || origin.getWorld() == null) {
            failSearch(context, BlockReason.INVALID_WORLD, origin, failed);
            return;
        }
        final long seed = origin.getBlockX() * 31L + origin.getBlockZ();
        searchNear(context, origin.clone(), seed, found, failed);
    }

    public List<String> policySummary(final String eventKey) {
        final String key = normalizeEventKey(eventKey);
        final List<String> lines = new ArrayList<>();
        lines.add("§6Event spawn debug: §f" + key);
        lines.add("§7Minimum játékostáv: §f" + Math.round(effectiveHorizontalMinimum(key))
                + " §7| keresés: §f" + Math.round(searchMinRadius(key)) + "–"
                + Math.round(searchMaxRadius(key)));
        final int trackedSendDistance = players.values().stream()
                .mapToInt(EventSpawnSafetyPolicy.PlayerPoint::sendViewDistanceChunks)
                .max().orElse(0);
        lines.add("§7View/send distance: §f" + Bukkit.getServer().getViewDistance()
                + "/" + trackedSendDistance + " chunk §7| nézési kúp: §f"
                + (visibilityConeEnabled(key) ? "igen" : "nem"));
        lines.add("§7Footprint: §f" + footprintRadius(key)
                + " blokk §7| vízpuffer: §f" + shorelineRadius(key));
        lines.add("§7Oszloppróba: §f" + maxColumnProbes(key)
                + " §7| limitált terepbővítés: §f"
                + (terrainExpansionEnabled(key) ? "igen/" + configManager.getInt(
                        "world-events.placement.terrain-expansion.max-new-chunks-per-search", 24)
                : "nem"));
        lines.add("§7Aktív keresések: §f" + activeSearches.get()
                + " §7| friss helyek: §f" + recentLocations.size());
        final SearchDiagnostic previous = diagnostics.get(key);
        if (previous != null) {
            lines.add("§7Utolsó eredmény: §f" + formatDiagnostic(previous));
        }
        return List.copyOf(lines);
    }

    private static String formatDiagnostic(final SearchDiagnostic diagnostic) {
        return (diagnostic.success() ? "§aSIKER" : "§cSIKERTELEN")
                + " §7próbák=§f" + diagnostic.attempts()
                + " §7chunkok=§f" + diagnostic.chunks()
                + " §7új=§f" + diagnostic.terrainExpansionChunks()
                + " §7idő=§f" + diagnostic.elapsedMillis() + "ms"
                + " §7okok=§f" + diagnostic.reasons();
    }

    private synchronized boolean reserveAfterSurfaceValidation(final String eventKey,
                                                               final Location location) {
        if (blockReason(eventKey, location, false) != BlockReason.NONE) {
            return false;
        }
        final long ttlMillis = Math.max(1L, configManager.getLong(
                "world-events.safety.reservation-seconds", 120L)) * 1_000L;
        reservations.put(eventKey, new Reservation(point(location),
                System.currentTimeMillis() + ttlMillis));
        return true;
    }

    private void releaseFamilyReservation(final String eventKey) {
        final String family = eventFamily(eventKey);
        reservations.keySet().removeIf(key -> eventFamily(key).equals(family));
    }

    private void markRecent(final String eventKey, final Location location) {
        final long ttlMillis = Math.max(0L, configManager.getLong(
                "world-events.placement.recent-location-cooldown-minutes", 45L)) * 60_000L;
        if (ttlMillis <= 0L) {
            return;
        }
        recentLocations.addFirst(new RecentLocation(eventKey, point(location),
                System.currentTimeMillis() + ttlMillis));
        while (recentLocations.size() > 256) {
            recentLocations.pollLast();
        }
    }

    private boolean recentLocationBlocked(final String eventKey,
                                          final EventSpawnSafetyPolicy.Point candidate) {
        final long now = System.currentTimeMillis();
        recentLocations.removeIf(location -> location.expiresAtMillis() <= now);
        final double distance = Math.max(0.0D, configManager.getDouble(
                "world-events.placement.recent-location-distance-blocks", 256.0D));
        if (distance <= 0.0D) {
            return false;
        }
        final boolean shared = configManager.getBoolean(
                "world-events.placement.recent-location-share-across-events", true);
        final boolean continuingSameEvent = reservations.containsKey(eventKey);
        for (final RecentLocation recent : recentLocations) {
            if (continuingSameEvent && recent.eventKey().equals(eventKey)) {
                continue;
            }
            if ((shared || recent.eventKey().equals(eventKey))
                    && EventSpawnSafetyPolicy.withinHorizontal(candidate, recent.point(), distance)) {
                return true;
            }
        }
        return false;
    }

    public void clearReservations() {
        reservations.clear();
        recentLocations.clear();
        searchBackoffUntil.clear();
        pendingArrivals.clear();
        diagnostics.clear();
        players.clear();
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

    private boolean insideBorder(final World world, final Location location) {
        final WorldBorder border = world.getWorldBorder();
        final Location center = border.getCenter();
        final double margin = Math.max(0.0D, configManager.getDouble(
                "world-events.safety.world-border-margin-blocks", 16.0D));
        final double half = Math.max(0.0D, border.getSize() / 2.0D - margin);
        return Math.abs(location.getX() - center.getX()) <= half
                && Math.abs(location.getZ() - center.getZ()) <= half;
    }

    private void logSearchFailure(final SearchContext context, final Location origin) {
        final String eventKey = context.eventKey;
        final long now = System.currentTimeMillis();
        final long previous = diagnosticLogAt.getOrDefault(eventKey, 0L);
        if (now - previous < 60_000L || !diagnosticLogAt.replace(eventKey, previous, now)
                && diagnosticLogAt.putIfAbsent(eventKey, now) != null) {
            return;
        }
        final String where = origin == null || origin.getWorld() == null ? "unknown"
                : origin.getWorld().getName() + ":" + origin.getBlockX() + "," + origin.getBlockZ();
        plugin.getLogger().warning("Event spawn search aborted: event=" + eventKey
                + ", attempts=" + context.attempts.get()
                + ", chunks=" + context.chunks.size()
                + ", terrain-expansion=" + context.terrainExpansionChunks.size()
                + ", origin=" + where + ", reasons=" + context.reasons
                + ". No close, visible, wet or forbidden fallback was used.");
    }

    private boolean terrainExpansionEnabled(final String eventKey) {
        return configManager.getBoolean(profilePath(eventKey, "terrain-expansion-enabled"),
                configManager.getBoolean(
                        "world-events.placement.terrain-expansion.enabled", true));
    }

    private int maxColumnProbes(final String eventKey) {
        return Math.max(1, configManager.getInt(
                profilePath(eventKey, "max-column-probes-per-candidate"),
                configManager.getInt(
                        "world-events.placement.max-column-probes-per-candidate", 8)));
    }

    private int candidateProbeRadius(final String eventKey) {
        return Math.max(waterSafetyRequired(eventKey) ? shorelineRadius(eventKey) : 0,
                footprintRadius(eventKey));
    }

    private List<EventSpawnSafetyPolicy.PlayerPoint> playerSnapshot() {
        return players.values().stream()
                .map(player -> new EventSpawnSafetyPolicy.PlayerPoint(player.playerId(), player.point(),
                        player.spectator(), vanishedPredicate.test(player.playerId()), player.admin(),
                        player.lookX(), player.lookZ(), player.sendViewDistanceChunks()))
                .toList();
    }

    private double effectiveHorizontalMinimum(final String eventKey) {
        final double global = configManager.getDouble(
                "world-events.safety.min-horizontal-distance-blocks", 192.0D);
        final double configured = configManager.getDouble(
                profilePath(eventKey, "min-horizontal-distance-blocks"), global);
        final boolean dynamic = configManager.getBoolean(
                profilePath(eventKey, "use-dynamic-view-distance"),
                configManager.getBoolean("world-events.placement.dynamic-view-distance-enabled", true));
        final double margin = configManager.getDouble(
                "world-events.placement.view-distance-margin-blocks", 32.0D);
        final int trackedSendDistance = players.values().stream()
                .mapToInt(EventSpawnSafetyPolicy.PlayerPoint::sendViewDistanceChunks)
                .max().orElse(0);
        final int effectiveViewDistance = Math.max(
                Bukkit.getServer().getViewDistance(), trackedSendDistance);
        return EventSpawnSafetyPolicy.effectiveHorizontalMinimum(configured,
                effectiveViewDistance, margin, dynamic);
    }

    private boolean visibilityConeEnabled(final String eventKey) {
        return configManager.getBoolean(profilePath(eventKey, "visibility-cone.enabled"),
                configManager.getBoolean("world-events.placement.visibility-cone.enabled", true));
    }

    private double visibilityConeDistance(final String eventKey) {
        return Math.max(0.0D, configManager.getDouble(
                profilePath(eventKey, "visibility-cone.max-distance-blocks"),
                configManager.getDouble(
                        "world-events.placement.visibility-cone.max-distance-blocks", 384.0D)));
    }

    private double visibilityConeAngle(final String eventKey) {
        return Math.max(1.0D, Math.min(179.0D, configManager.getDouble(
                profilePath(eventKey, "visibility-cone.angle-degrees"),
                configManager.getDouble(
                        "world-events.placement.visibility-cone.angle-degrees", 110.0D))));
    }

    private double searchMinRadius(final String eventKey) {
        final double configured = Math.max(0.0D, configManager.getDouble(
                profilePath(eventKey, "search-min-radius-blocks"),
                configManager.getDouble("world-events.safety.search-min-radius-blocks", 256.0D)));
        final double clearance = Math.max(0.0D, configManager.getDouble(
                "world-events.placement.search-clearance-margin-blocks", 32.0D));
        return Math.max(configured, effectiveHorizontalMinimum(eventKey) + clearance);
    }

    private double searchMaxRadius(final String eventKey) {
        final double min = searchMinRadius(eventKey);
        return Math.max(min, configManager.getDouble(
                profilePath(eventKey, "search-max-radius-blocks"),
                configManager.getDouble("world-events.safety.search-max-radius-blocks", 512.0D)));
    }

    private int footprintRadius(final String eventKey) {
        return Math.max(0, Math.min(MAX_SINGLE_REGION_PROBE_RADIUS, configManager.getInt(
                profilePath(eventKey, "footprint-radius-blocks"),
                defaultFootprintRadius(eventKey))));
    }

    private static int defaultFootprintRadius(final String eventKey) {
        return switch (eventKey) {
            case "meteor", "world-boss", "invasion" -> MAX_SINGLE_REGION_PROBE_RADIUS;
            case "cultists" -> 6;
            case "caravan", "player-caravan", "escort" -> 5;
            case "wild-hunt" -> 4;
            default -> 2;
        };
    }

    private static int defaultMaxHeightDelta(final String eventKey) {
        return switch (eventKey) {
            case "caravan", "player-caravan", "escort" -> 2;
            case "meteor", "world-boss", "invasion" -> 4;
            default -> 3;
        };
    }

    private boolean biomeAllowed(final String eventKey, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final String biome = world.getBiome(location.getBlockX(), location.getBlockY(),
                location.getBlockZ()).getKey().getKey().toLowerCase(Locale.ROOT);
        final List<String> allowed = normalizedList(configManager.getStringList(
                profilePath(eventKey, "allowed-biomes")));
        if (!allowed.isEmpty() && !allowed.contains(biome) && !allowed.contains("minecraft:" + biome)) {
            return false;
        }
        final List<String> denied = normalizedList(configManager.getStringList(
                profilePath(eventKey, "denied-biomes")));
        return !denied.contains(biome) && !denied.contains("minecraft:" + biome);
    }

    private static List<String> normalizedList(final List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT).trim()).toList();
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

    private static String profilePath(final String eventKey, final String suffix) {
        return "world-events.profiles." + normalizeEventKey(eventKey) + "." + suffix;
    }

    private static String normalizeEventKey(final String eventKey) {
        return eventKey == null || eventKey.isBlank()
                ? "unknown" : eventKey.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String eventFamily(final String eventKey) {
        final String normalized = normalizeEventKey(eventKey);
        if (normalized.endsWith("-route")) {
            return normalized.substring(0, normalized.length() - "-route".length());
        }
        if (normalized.endsWith("-wave")) {
            return normalized.substring(0, normalized.length() - "-wave".length());
        }
        return normalized;
    }

    /**
     * Automatic candidates use the center column of their chunk. Together with the
     * seven-block probe cap this keeps every Folia surface read on the scheduled region.
     */
    private static Location centeredCandidate(final Location origin,
                                              final EventSpawnSafetyPolicy.Offset offset) {
        final int rawX = (int) Math.floor(origin.getX() + offset.x());
        final int rawZ = (int) Math.floor(origin.getZ() + offset.z());
        final Location centered = origin.clone();
        centered.setX(EventSpawnSafetyPolicy.chunkCenterCoordinate(rawX));
        centered.setZ(EventSpawnSafetyPolicy.chunkCenterCoordinate(rawZ));
        return centered;
    }

    private static EventSpawnSafetyPolicy.Point point(final Location location) {
        return new EventSpawnSafetyPolicy.Point(location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ());
    }

    public static void prepare(final Mob mob) {
        final Boolean burnBaseline = mob instanceof org.bukkit.entity.AbstractSkeleton skeleton
                ? skeleton.shouldBurnInDay() : mob instanceof org.bukkit.entity.Zombie zombie
                ? zombie.shouldBurnInDay() : mob instanceof org.bukkit.entity.Phantom phantom
                ? phantom.shouldBurnInDay() : null;
        if (burnBaseline != null && !mob.getPersistentDataContainer().has(
                new NamespacedKey("icesmp", DAYLIGHT_BURN_BASELINE_KEY), PersistentDataType.BYTE)) {
            mob.getPersistentDataContainer().set(
                    new NamespacedKey("icesmp", DAYLIGHT_BURN_BASELINE_KEY),
                    PersistentDataType.BYTE, (byte) (burnBaseline ? 1 : 0));
        }
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
