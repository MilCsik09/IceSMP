package hu.taliann.icesmp.runtime;

import hu.taliann.icesmp.managers.EventSpawnSafetyPolicy;

import java.util.List;
import java.util.UUID;

public final class EventSpawnSafetyRegressionSuite {
    private EventSpawnSafetyRegressionSuite() { }

    public static void main(final String[] args) {
        final UUID world = UUID.randomUUID();
        final EventSpawnSafetyPolicy.PlayerPoint player = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0), false, false, false);
        check(EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 95.999, 64, 0), List.of(player),
                96, 0, true, true, true), "inside horizontal minimum rejected");
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 96, 64, 0), List.of(player),
                96, 0, true, true, true), "exact minimum accepted");
        final EventSpawnSafetyPolicy.PlayerPoint second = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 250, 64, 0), false, false, false);
        check(EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 170, 64, 0), List.of(player, second),
                96, 0, true, true, true), "nearest of multiple players enforced");
        final EventSpawnSafetyPolicy.PlayerPoint spectator = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0), true, false, false);
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 1, 64, 0), List.of(spectator),
                96, 0, true, true, true), "spectator ignored by policy");
        final EventSpawnSafetyPolicy.PlayerPoint vanished = new EventSpawnSafetyPolicy.PlayerPoint(
                UUID.randomUUID(), new EventSpawnSafetyPolicy.Point(world, 0, 64, 0), false, true, false);
        check(!EventSpawnSafetyPolicy.tooCloseToRelevantPlayer(
                new EventSpawnSafetyPolicy.Point(world, 1, 64, 0), List.of(vanished),
                96, 0, true, true, true), "vanished player ignored by policy");
        final List<EventSpawnSafetyPolicy.Offset> candidates = EventSpawnSafetyPolicy.candidates(24, 96, 256, 42);
        check(candidates.size() == 24, "bounded attempt count");
        for (final EventSpawnSafetyPolicy.Offset offset : candidates) {
            final double distance = Math.hypot(offset.x(), offset.z());
            check(distance >= 96 - 1.0E-9 && distance <= 256 + 1.0E-9,
                    "candidate remains inside configured annulus");
        }
        check(candidates.equals(EventSpawnSafetyPolicy.candidates(24, 96, 256, 42)),
                "candidate order deterministic");
        System.out.println("Event spawn safety regression suite passed.");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
