package hu.taliann.icesmp.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Pure, deterministic geometry used by the Folia-aware event spawn guard. */
public final class EventSpawnSafetyPolicy {
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));
    private static final ConcurrentMap<Integer, List<GridOffset>> WATER_PROBE_CACHE =
            new ConcurrentHashMap<>();

    private EventSpawnSafetyPolicy() { }

    public record Point(UUID worldId, double x, double y, double z) { }

    /**
     * Immutable player snapshot. lookX/lookZ are the horizontal look vector captured on
     * the player's entity thread; guard evaluation never touches a foreign entity.
     */
    public record PlayerPoint(UUID playerId, Point point, boolean spectator, boolean vanished,
                              boolean admin, double lookX, double lookZ,
                              int sendViewDistanceChunks) {
        public PlayerPoint(final UUID playerId, final Point point, final boolean spectator,
                           final boolean vanished, final boolean admin) {
            this(playerId, point, spectator, vanished, admin, 0.0D, 0.0D, 0);
        }

        public PlayerPoint(final UUID playerId, final Point point, final boolean spectator,
                           final boolean vanished, final boolean admin,
                           final double lookX, final double lookZ) {
            this(playerId, point, spectator, vanished, admin, lookX, lookZ, 0);
        }
    }

    public record Offset(double x, double z) { }

    /** Integer column offset used by the shoreline/water and footprint scans. */
    public record GridOffset(int x, int z) { }

    public static double effectiveHorizontalMinimum(final double configuredMinimum,
                                                    final int viewDistanceChunks,
                                                    final double viewMarginBlocks,
                                                    final boolean dynamicViewDistance) {
        final double configured = Math.max(0.0D, configuredMinimum);
        if (!dynamicViewDistance) {
            return configured;
        }
        final double rendered = Math.max(0, viewDistanceChunks) * 16.0D
                + Math.max(0.0D, viewMarginBlocks);
        return Math.max(configured, rendered);
    }

    /**
     * Exact coordinate in the middle of the containing chunk. An offset in [-8,8)
     * from this coordinate stays inside the same chunk, which is useful for legacy
     * region tasks that randomize a probe after choosing their scheduler anchor.
     */
    public static double chunkCenterCoordinate(final int blockCoordinate) {
        return (blockCoordinate & ~15) + 8.0D;
    }

    public static boolean tooCloseToRelevantPlayer(final Point candidate,
                                                    final Collection<PlayerPoint> players,
                                                    final double minHorizontal,
                                                    final double minThreeDimensional,
                                                    final boolean ignoreSpectators,
                                                    final boolean ignoreVanished,
                                                    final boolean ignoreAdmins) {
        final double horizontalSquared = square(Math.max(0.0D, minHorizontal));
        final double threeDimensionalSquared = square(Math.max(0.0D, minThreeDimensional));
        for (final PlayerPoint player : players) {
            if (!candidate.worldId().equals(player.point().worldId())
                    || ignored(player, ignoreSpectators, ignoreVanished, ignoreAdmins)) {
                continue;
            }
            final double dx = candidate.x() - player.point().x();
            final double dz = candidate.z() - player.point().z();
            if (horizontalSquared > 0.0D && dx * dx + dz * dz < horizontalSquared) {
                return true;
            }
            if (threeDimensionalSquared > 0.0D) {
                final double dy = candidate.y() - player.point().y();
                if (dx * dx + dy * dy + dz * dz < threeDimensionalSquared) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Conservative Folia-safe visibility test. It intentionally uses a captured view cone
     * instead of reading blocks across foreign regions. Candidates inside a relevant
     * player's horizontal viewport are rejected even when the distance minimum is met.
     */
    public static boolean visibleInsidePlayerCone(final Point candidate,
                                                  final Collection<PlayerPoint> players,
                                                  final double maxDistance,
                                                  final double coneAngleDegrees,
                                                  final boolean ignoreSpectators,
                                                  final boolean ignoreVanished,
                                                  final boolean ignoreAdmins) {
        final double boundedDistance = Math.max(0.0D, maxDistance);
        if (boundedDistance <= 0.0D) {
            return false;
        }
        final double maxSquared = square(boundedDistance);
        final double halfRadians = Math.toRadians(Math.max(1.0D,
                Math.min(179.0D, coneAngleDegrees))) / 2.0D;
        final double minimumDot = Math.cos(halfRadians);
        for (final PlayerPoint player : players) {
            if (!candidate.worldId().equals(player.point().worldId())
                    || ignored(player, ignoreSpectators, ignoreVanished, ignoreAdmins)) {
                continue;
            }
            final double dx = candidate.x() - player.point().x();
            final double dz = candidate.z() - player.point().z();
            final double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared <= 1.0E-9D || distanceSquared > maxSquared) {
                continue;
            }
            final double lookLength = Math.hypot(player.lookX(), player.lookZ());
            if (lookLength <= 1.0E-9D) {
                continue;
            }
            final double targetLength = Math.sqrt(distanceSquared);
            final double dot = (dx / targetLength) * (player.lookX() / lookLength)
                    + (dz / targetLength) * (player.lookZ() / lookLength);
            if (dot >= minimumDot) {
                return true;
            }
        }
        return false;
    }

    private static boolean ignored(final PlayerPoint player,
                                   final boolean ignoreSpectators,
                                   final boolean ignoreVanished,
                                   final boolean ignoreAdmins) {
        return (ignoreSpectators && player.spectator())
                || (ignoreVanished && player.vanished())
                || (ignoreAdmins && player.admin());
    }

    /**
     * Bounded, deterministic golden-angle ring. Every result is in [minRadius,maxRadius],
     * so callers cannot loop forever or silently fall back inside the configured minimum.
     */
    public static List<Offset> candidates(final int attempts, final double minRadius,
                                          final double maxRadius, final long seed) {
        final int count = Math.max(1, attempts);
        final double min = Math.max(0.0D, minRadius);
        final double max = Math.max(min, maxRadius);
        final double phase = Math.floorMod(seed, 1_000_003L) / 1_000_003.0D * Math.PI * 2.0D;
        final List<Offset> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            final double fraction = count == 1 ? 0.5D : index / (double) (count - 1);
            final double radius = min + (max - min) * fraction;
            final double angle = phase + index * GOLDEN_ANGLE;
            result.add(new Offset(Math.cos(angle) * radius, Math.sin(angle) * radius));
        }
        return List.copyOf(result);
    }

    /**
     * Returns every integer column inside a circular buffer, nearest columns first.
     * Only 33 bounded masks can exist, so repeated runtime checks reuse immutable lists.
     */
    public static List<GridOffset> waterProbeOffsets(final int radius) {
        final int bounded = Math.max(0, Math.min(32, radius));
        return WATER_PROBE_CACHE.computeIfAbsent(bounded,
                EventSpawnSafetyPolicy::buildWaterProbeOffsets);
    }

    private static List<GridOffset> buildWaterProbeOffsets(final int radius) {
        final int squared = radius * radius;
        final List<GridOffset> result = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= squared) {
                    result.add(new GridOffset(x, z));
                }
            }
        }
        result.sort(Comparator
                .comparingInt((GridOffset offset) -> offset.x() * offset.x() + offset.z() * offset.z())
                .thenComparingInt(GridOffset::x)
                .thenComparingInt(GridOffset::z));
        return List.copyOf(result);
    }

    public static boolean withinHorizontal(final Point first, final Point second, final double distance) {
        if (!first.worldId().equals(second.worldId())) {
            return false;
        }
        final double dx = first.x() - second.x();
        final double dz = first.z() - second.z();
        return dx * dx + dz * dz < square(Math.max(0.0D, distance));
    }

    private static double square(final double value) {
        return value * value;
    }
}
