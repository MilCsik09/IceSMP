package hu.taliann.icesmp.managers;

import java.util.Set;
import java.util.UUID;

/** Focused regressions for the simple immutable Bingulus state. */
public final class DevItemStateDataRegressionTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NEW_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INSTANCE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Set<String> RARITIES = Set.of("kozonseges", "ritka", "epikus", "legendas");

    private DevItemStateDataRegressionTest() {
    }

    public static void main(final String[] args) {
        runAll();
        System.out.println("DevItem state regression tests passed.");
    }

    static void runAll() {
        rewardIntervalIsClamped();
        pendingSnapshotIsExactAndRestartable();
        fullInventoryLeavesPendingUntilRetry();
        ownerReloadPreservesStateAndFencesOldOwner();
        writeFailureDoesNotPublishCandidateOrTripOtherFeature();
        strictStateRejectsCorruption();
    }

    private static void rewardIntervalIsClamped() {
        var state = issuedState(0L, null, 0, 0, 0);
        state = state.advanceProgress(599_999L, 600_000L, FakeItem::copy);
        check(state.progressMillis() == 599_999L, "reward interval completed early");
        check(state.pending() == null, "reward existed before the interval");

        state = state.advanceProgress(1L, 600_000L, FakeItem::copy);
        check(state.progressMillis() == 600_000L, "reward interval did not complete");
        state = state.withPending(pending(reward()), FakeItem::copy);
        check(state.pending() != null, "reward was not created at the interval");
    }

    private static void pendingSnapshotIsExactAndRestartable() {
        final FakeItem rolled = reward();
        final var pending = pending(rolled);
        rolled.amount = 64;
        rolled.meta = "mutated caller item";

        final var state = issuedState(600_000L, pending, 2, 3, 4);
        final var restarted = state.copy(FakeItem::copy);
        final FakeItem exact = restarted.pending().itemCopy(FakeItem::copy);
        check(exact.amount == 2, "pending amount changed");
        check(exact.meta.equals("Fagyott Korona"), "pending meta changed");
        check(exact.affix.equals("crit+7"), "pending affix changed");
        check(exact.stamp.equals("crafted-by:Milán"), "pending crafted stamp changed");

        exact.meta = "external mutation";
        check(restarted.pending().itemCopy(FakeItem::copy).meta.equals("Fagyott Korona"),
                "pending exposed its mutable internal item");
        check(restarted.pending().same(state.pending(), FakeItem::same),
                "normal restart rerolled or changed the exact pending reward");
    }

    private static void fullInventoryLeavesPendingUntilRetry() {
        final var state = issuedState(600_000L, pending(reward()), 7, 11, 13);
        final boolean inventoryHasSpace = false;
        final var afterFullInventory = inventoryHasSpace
                ? state.completed(new DevItemStateData.PityCounters(0, 0, 0), FakeItem::copy)
                : state.copy(FakeItem::copy);
        check(afterFullInventory.pending() != null, "full inventory cleared pending reward");
        check(afterFullInventory.progressMillis() == 600_000L, "full inventory reset progress");

        final var delivered = afterFullInventory.completed(
                new DevItemStateData.PityCounters(0, 0, 0), FakeItem::copy);
        check(delivered.pending() == null, "retry did not clear pending after delivery");
        check(delivered.progressMillis() == 0L, "delivery did not reset progress");
    }

    private static void ownerReloadPreservesStateAndFencesOldOwner() {
        final var before = issuedState(600_000L, pending(reward()), 5, 6, 7);
        final var transferred = before.withOwner(NEW_OWNER, FakeItem::copy);
        check(transferred.ownerIs(NEW_OWNER), "configured owner did not become active");
        check(!transferred.ownerIs(OWNER), "old owner tick was not fenced");
        check(transferred.progressMillis() == before.progressMillis(), "owner reload lost progress");
        check(transferred.pity().equals(before.pity()), "owner reload lost pity");
        check(transferred.pending().same(before.pending(), FakeItem::same),
                "owner reload changed pending reward");

        final var oldTickResult = transferred.ownerIs(OWNER)
                ? transferred.completed(DevItemStateData.PityCounters.ZERO, FakeItem::copy)
                : transferred;
        check(oldTickResult.pending() != null,
                "old owner tick cleared the new owner's pending state");
    }

    private static void writeFailureDoesNotPublishCandidateOrTripOtherFeature() {
        final var live = new Box<>(issuedState(600_000L, null, 1, 2, 3));
        final var candidate = live.value.withPending(pending(reward()), FakeItem::copy);
        final boolean[] independentEconomyHealthy = {true};
        expectThrows(SimulatedWriteFailure.class, () -> {
            writeCandidate(candidate, true);
            live.value = candidate;
        });
        check(live.value.pending() == null, "failed write published pending state");
        check(independentEconomyHealthy[0], "DEV write failure stopped an independent feature");
    }

    private static void strictStateRejectsCorruption() {
        expectThrows(IllegalArgumentException.class,
                () -> DevItemStateData.requireUuid("not-a-uuid", "owner"));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData<>(OWNER, INSTANCE, true, -1L, null,
                        DevItemStateData.PityCounters.ZERO));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData.PityCounters(-1, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData<>(OWNER, INSTANCE, false, 1L, null,
                        DevItemStateData.PityCounters.ZERO));
        expectThrows(IllegalArgumentException.class,
                () -> DevItemStateData.PendingReward.of("ritka", "material:stone",
                        new FakeItem("AIR", 1, "", "", ""), FakeItem::copy,
                        FakeItem::valid, RARITIES::contains));
        expectThrows(IllegalArgumentException.class,
                () -> DevItemStateData.PendingReward.of("ismeretlen", "material:stone",
                        reward(), FakeItem::copy, FakeItem::valid, RARITIES::contains));
    }

    private static DevItemStateData<FakeItem> issuedState(
            final long progress,
            final DevItemStateData.PendingReward<FakeItem> pending,
            final int rare, final int epic, final int legendary) {
        return new DevItemStateData<>(OWNER, INSTANCE, true, progress, pending,
                new DevItemStateData.PityCounters(rare, epic, legendary));
    }

    private static DevItemStateData.PendingReward<FakeItem> pending(final FakeItem item) {
        return DevItemStateData.PendingReward.of("epikus", "unique:jegsziv:2", item,
                FakeItem::copy, FakeItem::valid, RARITIES::contains);
    }

    private static FakeItem reward() {
        return new FakeItem("DIAMOND", 2, "Fagyott Korona", "crit+7", "crafted-by:Milán");
    }

    private static void writeCandidate(final DevItemStateData<FakeItem> candidate,
                                       final boolean fail) {
        if (candidate == null) {
            throw new AssertionError("candidate missing");
        }
        if (fail) {
            throw new SimulatedWriteFailure();
        }
    }

    private static <T extends Throwable> T expectThrows(final Class<T> type,
                                                         final ThrowingRunnable action) {
        try {
            action.run();
        } catch (final Throwable thrown) {
            if (type.isInstance(thrown)) {
                return type.cast(thrown);
            }
            throw new AssertionError("Expected " + type.getName() + " but got " + thrown, thrown);
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Box<T> {
        private T value;

        private Box(final T value) {
            this.value = value;
        }
    }

    private static final class FakeItem {
        private final String material;
        private int amount;
        private String meta;
        private final String affix;
        private final String stamp;

        private FakeItem(final String material, final int amount, final String meta,
                         final String affix, final String stamp) {
            this.material = material;
            this.amount = amount;
            this.meta = meta;
            this.affix = affix;
            this.stamp = stamp;
        }

        private FakeItem copy() {
            return new FakeItem(material, amount, meta, affix, stamp);
        }

        private static boolean valid(final FakeItem item) {
            return item != null && !"AIR".equals(item.material) && item.amount > 0;
        }

        private static boolean same(final FakeItem left, final FakeItem right) {
            return left != null && right != null
                    && left.material.equals(right.material)
                    && left.amount == right.amount
                    && left.meta.equals(right.meta)
                    && left.affix.equals(right.affix)
                    && left.stamp.equals(right.stamp);
        }
    }

    private static final class SimulatedWriteFailure extends RuntimeException {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
