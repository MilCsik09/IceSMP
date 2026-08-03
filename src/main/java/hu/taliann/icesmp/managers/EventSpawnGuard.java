package hu.taliann.icesmp.managers;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Shared fail-closed placement gate for world events. Protection checks, online-player
 * distance, world spawn/border safety, finite search and cross-event reservations live
 * here so every event consumes one precedence model instead of reimplementing it.
 */
public final class EventSpawnGuard {
    public static final String EVENT_NO_BURN_KEY = "event_no_daylight_burn";
    public static final String EVENT_NO_ZOMBIFICATION_KEY = "event_no_zombification";

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
        UNSAFE_SURFACE
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
        final List<EventSpawnSafetyPolicy.PlayerPoint> snapshot = players.values().stream()
                .map(player -> new EventSpawnSafetyPolicy.PlayerPoint(player.playerId(), player.point(),
                        player.spectator(), vanishedPredicate.test(player.playerId()), player.admin()))
                .toList();
        if (EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(candidate, snapshot,
                configManager.getDouble("world-events.safety.min-horizontal-distance-blocks", 96.0D),
                configManager.getDouble("world-events.safety.min-3d-distance-blocks", 0.0D),
                configManager.getBoolean("world-events.safety.ignore-spectators", true),
                configManager.getBoolean("world-events.safety.ignore-vanished", true),
                configManager.getBoolean("world-events.safety.ignore-admins", true))) {
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

    /** Must run on the region thread that owns x/z. */
    public boolean isUnsafeSurface(final String eventKey, final World world, final int x, final int z) {
        if (world == null) {
            return true;
        }
        if (configManager.getBoolean("world-events.safety.require-loaded-chunk", true)
                && !world.isChunkLoaded(x >> 4, z >> 4)) {
            return true;
        }
        final int floorY = world.getHighestBlockYAt(x, z);
        if (floorY <= world.getMinHeight() + 1 || floorY + 2 >= world.getMaxHeight()) {
            return true;
        }
        final Block floor = world.getBlockAt(x, floorY, z);
        final Block feet = world.getBlockAt(x, floorY + 1, z);
        final Block head = world.getBlockAt(x, floorY + 2, z);
        if (!floor.getType().isSolid() || !feet.isPassable() || !head.isPassable()) {
            return true;
        }
        return rule(eventKey, "water") && (floor.isLiquid() || feet.isLiquid());
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
                configManager.getInt("world-events.safety.search-attempts", 24),
                configManager.getDouble("world-events.safety.search-min-radius-blocks", 96.0D),
                configManager.getDouble("world-events.safety.search-max-radius-blocks", 256.0D),
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
        final World world = column.getWorld();
        if (world == null) {
            tryCandidate(eventKey, origin, candidates, index + 1, onFound, onFailure);
            return;
        }
        if (configManager.getBoolean("world-events.safety.require-loaded-chunk", true)
                && !world.isChunkLoaded(column.getBlockX() >> 4, column.getBlockZ() >> 4)) {
            tryCandidate(eventKey, origin, candidates, index + 1, onFound, onFailure);
            return;
        }
        plugin.getServer().getRegionScheduler().run(plugin, column, task -> {
            final int x = column.getBlockX();
            final int z = column.getBlockZ();
            final int y = world.getHighestBlockYAt(x, z) + 1;
            final Location candidate = new Location(world, x + 0.5D, y, z + 0.5D);
            if (blockReason(eventKey, candidate) != BlockReason.NONE
                    || isUnsafeSurface(eventKey, world, x, z)
                    || !reserve(eventKey, candidate)) {
                tryCandidate(eventKey, origin, candidates, index + 1, onFound, onFailure);
                return;
            }
            onFound.accept(candidate);
        });
    }

    private synchronized boolean reserve(final String eventKey, final Location location) {
        if (blockReason(eventKey, location) != BlockReason.NONE) {
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
                + ". No close or forbidden fallback was used.");
    }

    private boolean rule(final String eventKey, final String protection) {
        return configManager.getBoolean("world-events.spawn-rules." + eventKey + "." + protection, true);
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
