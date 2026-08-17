package hu.taliann.icesmp.hud;

import java.util.Set;
import java.util.UUID;

/** Dependency-free behavioral regression for target selection, metadata and stale-callback rules. */
public final class TargetFrameRegressionSuite {
    private static int assertions;
    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000102");

    private TargetFrameRegressionSuite() { }

    public static void main(final String[] args) {
        canonicalRanksAndMetadataRoundTrip();
        targetSwitchRejectsStaleCallbacks();
        lifecycleClearsStaleTargets();
        metadataFailsClosed();
        rangeBoundaryIsBounded();
        System.out.println("Target Frame behavioral regression suite passed. assertions=" + assertions);
    }

    private static void canonicalRanksAndMetadataRoundTrip() {
        final TargetFrameTracker tracker = new TargetFrameTracker();
        final TargetHudState.Rank[] ranks = {
                TargetHudState.Rank.NORMAL, TargetHudState.Rank.VETERAN,
                TargetHudState.Rank.ELITE, TargetHudState.Rank.CHAMPION,
                TargetHudState.Rank.WORLD_BOSS
        };
        for (int index = 0; index < ranks.length; index++) {
            final UUID target = new UUID(0L, 200L + index);
            final long generation = tracker.begin(VIEWER, target);
            final TargetFrameTracker.Snapshot snapshot = snapshot(target,
                    "mob_" + index, ranks[index], 10 + index, 80.0D, 100.0D, 1_000L + index);
            check(tracker.publish(VIEWER, generation, snapshot), "canonical snapshot publishes");
            final TargetFrameTracker.Snapshot current = tracker.current(
                    VIEWER, WORLD, 1_100L + index, 5_000L);
            check(current != null && current.templateId().equals("mob_" + index)
                            && current.rank() == ranks[index] && current.level() == 10 + index,
                    "canonical template/rank/level survive projection");
        }

        final UUID target = new UUID(0L, 300L);
        final long generation = tracker.begin(VIEWER, target);
        tracker.publish(VIEWER, generation, snapshot(target, "mob_hp",
                TargetHudState.Rank.ELITE, 25, 70.0D, 100.0D, 2_000L));
        tracker.publish(VIEWER, generation, snapshot(target, "mob_hp",
                TargetHudState.Rank.VETERAN, 26, 40.0D, 120.0D, 2_100L));
        final TargetFrameTracker.Snapshot updated = tracker.current(VIEWER, WORLD, 2_200L, 5_000L);
        check(updated != null && updated.health() == 40.0D && updated.maximumHealth() == 120.0D
                        && updated.level() == 26 && updated.rank() == TargetHudState.Rank.VETERAN,
                "HP, level and rank updates replace the previous immutable snapshot");
    }

    private static void targetSwitchRejectsStaleCallbacks() {
        final TargetFrameTracker tracker = new TargetFrameTracker();
        final UUID first = new UUID(0L, 401L);
        final UUID second = new UUID(0L, 402L);
        final long oldGeneration = tracker.beginSample(VIEWER);
        final long currentGeneration = tracker.beginSample(VIEWER);
        check(!tracker.publish(VIEWER, oldGeneration, snapshot(first, "first",
                        TargetHudState.Rank.NORMAL, 1, 20.0D, 20.0D, 10L)),
                "late callback from the previous target sample is rejected");
        check(tracker.publish(VIEWER, currentGeneration, snapshot(second, "second",
                        TargetHudState.Rank.ELITE, 30, 90.0D, 100.0D, 11L)),
                "the newest sampled target publishes");
        check(tracker.current(VIEWER, WORLD, 12L, 100L).targetId().equals(second),
                "target switch cannot be overwritten by stale work");
    }

    private static void lifecycleClearsStaleTargets() {
        final TargetFrameTracker tracker = new TargetFrameTracker();
        final UUID target = new UUID(0L, 501L);
        long generation = tracker.begin(VIEWER, target);
        tracker.publish(VIEWER, generation, snapshot(target, "",
                TargetHudState.Rank.NORMAL, 0, 20.0D, 20.0D, 100L));
        check(tracker.current(VIEWER, UUID.randomUUID(), 101L, 100L) == null,
                "viewer or target world change clears the frame");

        generation = tracker.begin(VIEWER, target);
        tracker.publish(VIEWER, generation, snapshot(target, "",
                TargetHudState.Rank.NORMAL, 0, 20.0D, 20.0D, 200L));
        tracker.invalidateTarget(target);
        check(tracker.current(VIEWER, WORLD, 201L, 100L) == null,
                "target death or despawn clears every viewer");

        generation = tracker.begin(VIEWER, target);
        tracker.publish(VIEWER, generation, snapshot(target, "",
                TargetHudState.Rank.NORMAL, 0, 20.0D, 20.0D, 300L));
        check(tracker.current(VIEWER, WORLD, 500L, 100L) == null,
                "expired target snapshots clear instead of remaining stale");

        generation = tracker.begin(VIEWER, target);
        tracker.publish(VIEWER, generation, snapshot(target, "",
                TargetHudState.Rank.NORMAL, 0, 20.0D, 20.0D, 600L));
        tracker.clear(VIEWER);
        check(tracker.current(VIEWER, WORLD, 601L, 100L) == null && tracker.size() == 0,
                "no-target, disconnect and player death share the same clear contract");
    }

    private static void metadataFailsClosed() {
        final Set<String> known = Set.of("known_mob");
        check(TargetFrameMetadataPolicy.templateId("KNOWN-MOB", known::contains)
                        .equals("known_mob"),
                "known canonical template ids normalize");
        check(TargetFrameMetadataPolicy.templateId("stale_template", known::contains).isBlank(),
                "stale template ids become vanilla fallback metadata");
        check(TargetFrameMetadataPolicy.level(-1) == 0
                        && TargetFrameMetadataPolicy.level(201) == 0
                        && TargetFrameMetadataPolicy.level(70) == 70,
                "malformed levels fail closed while valid authored levels survive");
        check(TargetFrameMetadataPolicy.rank("ELITE", false) == TargetHudState.Rank.ELITE
                        && TargetFrameMetadataPolicy.rank("nonsense", false)
                        == TargetHudState.Rank.NORMAL
                        && TargetFrameMetadataPolicy.rank("nonsense", true)
                        == TargetHudState.Rank.WORLD_BOSS,
                "malformed rank never invents an elite but preserves a canonical boss marker");
        check(TargetFrameMetadataPolicy.affixStatus(
                        "VOLATILE,INVALID,VAMPIRIC,SHIELDED").equals("volatile • vampiric")
                        && TargetFrameMetadataPolicy.archetypeStatus("not_real").isBlank(),
                "affix/archetype projection ignores malformed and unbounded metadata");
    }

    private static void rangeBoundaryIsBounded() {
        check(TargetFrameTracker.boundedRange(-1.0D) == TargetFrameTracker.MIN_RANGE
                        && TargetFrameTracker.boundedRange(500.0D) == TargetFrameTracker.MAX_RANGE,
                "target range config is bounded");
        check(TargetFrameTracker.withinRange(24.0D * 24.0D, 24.0D)
                        && !TargetFrameTracker.withinRange(24.0D * 24.0D + 0.01D, 24.0D),
                "range boundary is inclusive and rejects the first out-of-range point");
    }

    private static TargetFrameTracker.Snapshot snapshot(
            final UUID target, final String template, final TargetHudState.Rank rank,
            final int level, final double health, final double maximumHealth, final long at) {
        return new TargetFrameTracker.Snapshot(target, WORLD, template, "Célpont",
                TargetHudState.Kind.HOSTILE, rank, level, "", health, maximumHealth,
                "", "", 0, 0, at);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
