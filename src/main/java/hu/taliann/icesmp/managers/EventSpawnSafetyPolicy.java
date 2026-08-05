package hu.taliann.icesmp.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Pure, deterministic distance/search policy shared by all event spawn gates. */
public final class EventSpawnSafetyPolicy {
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));

    private EventSpawnSafetyPolicy() { }

    public record Point(UUID worldId, double x, double y, double z) { }

    public record PlayerPoint(UUID playerId, Point point, boolean spectator, boolean vanished, boolean admin) { }

    public record Offset(double x, double z) { }

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
            if (!candidate.worldId().equals(player.point().worldId())) {
                continue;
            }
            if ((ignoreSpectators && player.spectator())
                    || (ignoreVanished && player.vanished())
                    || (ignoreAdmins && player.admin())) {
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
