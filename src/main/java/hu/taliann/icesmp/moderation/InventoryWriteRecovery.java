package hu.taliann.icesmp.moderation;

/**
 * Classifies the observable slot state after a target inventory setter reported failure and its
 * rollback also failed. The manager can then preserve one-owner semantics instead of blindly
 * returning an inserted stack that may already be committed in the target slot.
 */
public final class InventoryWriteRecovery {
    public enum Outcome { ROLLED_BACK, COMMITTED, UNKNOWN }

    private InventoryWriteRecovery() {
    }

    public static Outcome classify(final boolean matchesInserted, final boolean matchesDisplaced) {
        // Equal inserted/displaced stacks make both predicates true. Treating that as rollback is
        // count-safe: the target kept an equivalent stack and the viewer gets exactly one back.
        if (matchesDisplaced) {
            return Outcome.ROLLED_BACK;
        }
        if (matchesInserted) {
            return Outcome.COMMITTED;
        }
        return Outcome.UNKNOWN;
    }
}
