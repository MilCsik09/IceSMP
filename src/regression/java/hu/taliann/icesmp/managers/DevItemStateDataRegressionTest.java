package hu.taliann.icesmp.managers;

import java.util.UUID;

/** Dependency-free regressions for strict DEV-item snapshot metadata validation. */
public final class DevItemStateDataRegressionTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INSTANCE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private DevItemStateDataRegressionTest() {
    }

    public static void main(final String[] args) {
        runAll();
        System.out.println("DevItem state metadata regression tests passed.");
    }

    static void runAll() {
        malformedUuidIsRejected();
        negativeProgressAndPityAreRejected();
        partialPendingRewardIsRejected();
        unissuedStateCannotCarryProgress();
        unissuedStateCannotCarryPendingReward();
        unissuedStateCannotCarryPity();
        emptyPendingStateIsValid();
        completePendingStateIsValid();
    }

    private static void malformedUuidIsRejected() {
        expectThrows(IllegalArgumentException.class, () -> DevItemStateData.requireUuid("", "owner"));
        expectThrows(IllegalArgumentException.class,
                () -> DevItemStateData.requireUuid("not-a-uuid", "instance"));
        check(DevItemStateData.requireUuid("  " + OWNER + "  ", "owner").equals(OWNER),
                "UUID parsing may trim surrounding operator whitespace");
    }

    private static void negativeProgressAndPityAreRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> state(true, -1L, "", "", false, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> state(true, 0L, "", "", false, -1, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> state(true, 0L, "", "", false, 0, -1, 0));
        expectThrows(IllegalArgumentException.class,
                () -> state(true, 0L, "", "", false, 0, 0, -1));
    }

    private static void partialPendingRewardIsRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> state(true, 1L, "ritka", "", false, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> state(true, 1L, "ritka", "material:diamond", false, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> state(true, 1L, "", "", true, 0, 0, 0));
    }

    private static void unissuedStateCannotCarryProgress() {
        expectThrows(IllegalArgumentException.class,
                () -> state(false, 1L, "", "", false, 0, 0, 0));
    }

    private static void unissuedStateCannotCarryPendingReward() {
        expectThrows(IllegalArgumentException.class,
                () -> state(false, 0L, "ritka", "material:diamond", true, 0, 0, 0));
    }

    private static void unissuedStateCannotCarryPity() {
        expectThrows(IllegalArgumentException.class,
                () -> state(false, 0L, "", "", false, 1, 0, 0));
    }

    private static void emptyPendingStateIsValid() {
        final DevItemStateData state = state(true, 0L, "", "", false, 0, 0, 0);
        check(!state.hasPendingReward(), "empty pending fields must not create a reward");
    }

    private static void completePendingStateIsValid() {
        final DevItemStateData state = state(true, 42_000L,
                "epikus", "unique:jegsziv:2", true, 7, 11, 13);
        check(state.hasPendingReward(), "complete pending metadata must remain valid");
    }

    private static DevItemStateData state(final boolean issued, final long progress,
                                          final String rarity, final String entry,
                                          final boolean pendingItem,
                                          final int rare, final int epic, final int legendary) {
        return new DevItemStateData(OWNER, INSTANCE, issued, progress, rarity, entry,
                pendingItem, rare, epic, legendary);
    }

    private static <T extends Throwable> T expectThrows(final Class<T> type, final ThrowingRunnable action) {
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
