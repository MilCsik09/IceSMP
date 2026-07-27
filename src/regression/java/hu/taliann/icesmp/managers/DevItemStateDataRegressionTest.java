package hu.taliann.icesmp.managers;

import java.util.UUID;

/** Dependency-free regression coverage for the durable DEV-item metadata invariants. */
public final class DevItemStateDataRegressionTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NEW_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INSTANCE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID GRANT = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private DevItemStateDataRegressionTest() {
    }

    public static void main(final String[] args) {
        transferPreservesSingletonProgressAndRecordedRecipient();
        pendingRecipientCanBeReassignedWithoutChangingGrant();
        durableReceiptAcknowledgesInsteadOfRedelivering();
        missingReceiptRequiresDelivery();
        unrelatedPlayerCannotConsumeTheGrant();
        markIssuedPreservesSingletonIdentity();
        malformedUuidIsRejected();
        negativeProgressAndPityAreRejected();
        partialPendingRewardIsRejected();
        unissuedStateCannotCarryEarnedProgress();
        System.out.println("DevItemStateData regression tests passed.");
    }

    private static void transferPreservesSingletonProgressAndRecordedRecipient() {
        final DevItemStateData original = pendingState();

        final DevItemStateData transferred = original.transferTo(NEW_OWNER);

        check(transferred.owner().equals(NEW_OWNER), "the configured owner must change");
        check(transferred.instanceId().equals(INSTANCE), "owner transfer must preserve the singleton token");
        check(transferred.issued(), "owner transfer must preserve issuance");
        check(transferred.progressMillis() == 42_000L, "owner transfer must preserve active time");
        check(transferred.pendingRarity().equals("epikus"), "owner transfer must preserve pending rarity");
        check(transferred.pendingEntry().equals("material:diamond"), "owner transfer must preserve pending entry");
        check(transferred.pendingItemPresent(), "owner transfer must preserve the exact pending item marker");
        check(transferred.pendingGrantId().equals(GRANT), "owner transfer must preserve the stable grant id");
        check(transferred.pendingRecipient().equals(OWNER),
                "an unresolved grant must remain bound to the previously recorded recipient");
        check(transferred.rollsSinceRare() == 7, "owner transfer must preserve rare pity");
        check(transferred.rollsSinceEpic() == 11, "owner transfer must preserve epic pity");
        check(transferred.rollsSinceLegendary() == 13, "owner transfer must preserve legendary pity");
    }

    private static void pendingRecipientCanBeReassignedWithoutChangingGrant() {
        final DevItemStateData reassigned = pendingState().reassignPendingRecipient(GRANT, NEW_OWNER);

        check(reassigned.pendingRecipient().equals(NEW_OWNER), "the verified recipient may be rebound");
        check(reassigned.pendingGrantId().equals(GRANT), "recipient rebinding must not mint another grant");
        check(reassigned.pendingEntry().equals("material:diamond"), "the exact reward identity must survive rebinding");
        expectThrows(IllegalStateException.class,
                () -> pendingState().reassignPendingRecipient(UUID.randomUUID(), NEW_OWNER));
    }

    private static void durableReceiptAcknowledgesInsteadOfRedelivering() {
        check(DevItemStateData.deliveryDecision(GRANT, OWNER, OWNER, GRANT.toString())
                        == DevItemStateData.DeliveryDecision.ACKNOWLEDGE,
                "a matching playerdata receipt must suppress replay delivery");
    }

    private static void missingReceiptRequiresDelivery() {
        check(DevItemStateData.deliveryDecision(GRANT, OWNER, OWNER, null)
                        == DevItemStateData.DeliveryDecision.DELIVER,
                "a missing receipt means the durable outbox still owes the reward");
        check(DevItemStateData.deliveryDecision(GRANT, OWNER, OWNER, UUID.randomUUID().toString())
                        == DevItemStateData.DeliveryDecision.DELIVER,
                "a receipt for another grant cannot acknowledge this reward");
    }

    private static void unrelatedPlayerCannotConsumeTheGrant() {
        check(DevItemStateData.deliveryDecision(GRANT, OWNER, NEW_OWNER, GRANT.toString())
                        == DevItemStateData.DeliveryDecision.WAIT_FOR_RECORDED_RECIPIENT,
                "even a matching string on another player must not acknowledge the recorded recipient's grant");
    }

    private static void markIssuedPreservesSingletonIdentity() {
        final DevItemStateData unissued = new DevItemStateData(
                OWNER, INSTANCE, false, 0L, "", "", false, null, null, 0, 0, 0);

        final DevItemStateData issued = unissued.markIssued();

        check(issued.issued(), "markIssued must set the durable issuance bit");
        check(issued.owner().equals(OWNER), "markIssued must preserve the owner");
        check(issued.instanceId().equals(INSTANCE), "markIssued must not mint a replacement singleton");
    }

    private static void malformedUuidIsRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> DevItemStateData.requireUuid("", "owner"));
        expectThrows(IllegalArgumentException.class,
                () -> DevItemStateData.requireUuid("not-a-uuid", "instance"));
        check(DevItemStateData.requireUuid("  " + OWNER + "  ", "owner").equals(OWNER),
                "UUID parsing may trim surrounding operator whitespace");
    }

    private static void negativeProgressAndPityAreRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, -1L,
                        "", "", false, null, null, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 0L,
                        "", "", false, null, null, -1, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 0L,
                        "", "", false, null, null, 0, -1, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 0L,
                        "", "", false, null, null, 0, 0, -1));
    }

    private static void partialPendingRewardIsRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 1L,
                        "ritka", "", false, null, null, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 1L,
                        "ritka", "material:diamond", false, GRANT, OWNER, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 1L,
                        "ritka", "material:diamond", true, null, OWNER, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 1L,
                        "ritka", "material:diamond", true, GRANT, null, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 1L,
                        "", "", true, GRANT, OWNER, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, true, 1L,
                        "", "", false, GRANT, OWNER, 0, 0, 0));
    }

    private static void unissuedStateCannotCarryEarnedProgress() {
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, false, 1L,
                        "", "", false, null, null, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, false, 0L,
                        "ritka", "material:diamond", true, GRANT, OWNER, 0, 0, 0));
        expectThrows(IllegalArgumentException.class,
                () -> new DevItemStateData(OWNER, INSTANCE, false, 0L,
                        "", "", false, null, null, 1, 0, 0));
    }

    private static DevItemStateData pendingState() {
        return new DevItemStateData(
                OWNER, INSTANCE, true, 42_000L,
                "epikus", "material:diamond", true, GRANT, OWNER,
                7, 11, 13);
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
