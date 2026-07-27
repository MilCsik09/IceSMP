package hu.taliann.icesmp.managers;

import java.util.UUID;

/** Dependency-free regression coverage for the practical DEV-item persistence invariants. */
public final class DevItemStateDataRegressionTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NEW_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INSTANCE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private DevItemStateDataRegressionTest() {
    }

    public static void main(final String[] args) {
        transferPreservesSingletonProgressAndPendingReward();
        markIssuedPreservesSingletonIdentity();
        malformedUuidIsRejected();
        negativeProgressAndPityAreRejected();
        partialPendingRewardIsRejected();
        unissuedStateCannotCarryEarnedProgress();
        emptyPendingStateIsValid();
        receiptFreeLegacySnapshotsRemainCompatible();
        partialLegacyReceiptMetadataIsRejected();
        receiptBackedPendingRewardRequiresManualReconciliation();
        System.out.println("DevItemStateData regression tests passed.");
    }

    private static void transferPreservesSingletonProgressAndPendingReward() {
        final DevItemStateData transferred = pendingState().transferTo(NEW_OWNER);
        check(transferred.owner().equals(NEW_OWNER), "the configured owner must change");
        check(transferred.instanceId().equals(INSTANCE), "owner transfer must preserve the singleton token");
        check(transferred.issued(), "owner transfer must preserve issuance");
        check(transferred.progressMillis() == 42_000L, "owner transfer must preserve active time");
        check(transferred.pendingRarity().equals("epikus"), "owner transfer must preserve pending rarity");
        check(transferred.pendingEntry().equals("material:diamond"), "owner transfer must preserve pending entry");
        check(transferred.pendingItemPresent(), "owner transfer must preserve the exact pending item marker");
        check(transferred.rollsSinceRare() == 7, "owner transfer must preserve rare pity");
        check(transferred.rollsSinceEpic() == 11, "owner transfer must preserve epic pity");
        check(transferred.rollsSinceLegendary() == 13, "owner transfer must preserve legendary pity");
    }

    private static void markIssuedPreservesSingletonIdentity() {
        final DevItemStateData unissued = new DevItemStateData(
                OWNER, INSTANCE, false, 0L, "", "", false, 0, 0, 0);
        final DevItemStateData issued = unissued.markIssued();
        check(issued.issued(), "markIssued must set the durable issuance bit");
        check(issued.owner().equals(OWNER), "markIssued must preserve the owner");
        check(issued.instanceId().equals(INSTANCE), "markIssued must not mint a replacement singleton");
    }

    private static void malformedUuidIsRejected() {
        expectThrows(IllegalArgumentException.class, () -> DevItemStateData.requireUuid("", "owner"));
        expectThrows(IllegalArgumentException.class, () -> DevItemStateData.requireUuid("not-a-uuid", "instance"));
        check(DevItemStateData.requireUuid("  " + OWNER + "  ", "owner").equals(OWNER),
                "UUID parsing may trim surrounding operator whitespace");
    }

    private static void negativeProgressAndPityAreRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, -1L, "", "", false, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 0L, "", "", false, -1, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 0L, "", "", false, 0, -1, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 0L, "", "", false, 0, 0, -1));
    }

    private static void partialPendingRewardIsRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 1L, "ritka", "", false, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 1L,
                        "ritka", "material:diamond", false, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 1L, "", "", true, 0, 0, 0));
    }

    private static void unissuedStateCannotCarryEarnedProgress() {
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, false, 1L, "", "", false, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, false, 0L,
                        "ritka", "material:diamond", true, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, false, 0L, "", "", false, 1, 0, 0));
    }

    private static void emptyPendingStateIsValid() {
        final DevItemStateData state = new DevItemStateData(
                OWNER, INSTANCE, true, 0L, "", "", false, 0, 0, 0);
        check(!state.hasPendingReward(), "an empty pending snapshot must not create a reward");
    }

    private static void receiptFreeLegacySnapshotsRemainCompatible() {
        DevItemStateData.validateLegacyReceiptMigration(false, "", "");
        DevItemStateData.validateLegacyReceiptMigration(true, "", "");
    }

    private static void partialLegacyReceiptMetadataIsRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> DevItemStateData.validateLegacyReceiptMigration(true, OWNER.toString(), ""));
        expectThrows(IllegalArgumentException.class,
                () -> DevItemStateData.validateLegacyReceiptMigration(true, "", OWNER.toString()));
        expectThrows(IllegalArgumentException.class,
                () -> DevItemStateData.validateLegacyReceiptMigration(false, OWNER.toString(), NEW_OWNER.toString()));
    }

    private static void receiptBackedPendingRewardRequiresManualReconciliation() {
        expectThrows(IllegalArgumentException.class,
                () -> DevItemStateData.validateLegacyReceiptMigration(
                        true, INSTANCE.toString(), OWNER.toString()));
    }

    private static DevItemStateData pendingState() {
        return new DevItemStateData(
                OWNER, INSTANCE, true, 42_000L,
                "epikus", "material:diamond", true, 7, 11, 13);
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
