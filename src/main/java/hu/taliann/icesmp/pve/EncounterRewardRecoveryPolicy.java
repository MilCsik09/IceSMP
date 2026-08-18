package hu.taliann.icesmp.pve;

/** Exact-evidence restart policy for a personal encounter reward receipt. */
public final class EncounterRewardRecoveryPolicy {
    public enum ReceiptState { PREPARED, COMMITTED }
    public enum Decision { DELIVER, COMMIT_WITNESS, CLEANUP_ONLY, MANUAL_REVIEW }

    private EncounterRewardRecoveryPolicy() { }

    public static Decision decide(final ReceiptState receiptState, final int witnessCount) {
        if (receiptState == null || witnessCount < 0) {
            throw new IllegalArgumentException("invalid encounter reward evidence");
        }
        if (witnessCount > 1) return Decision.MANUAL_REVIEW;
        if (receiptState == ReceiptState.COMMITTED) return Decision.CLEANUP_ONLY;
        return witnessCount == 1 ? Decision.COMMIT_WITNESS : Decision.DELIVER;
    }
}
