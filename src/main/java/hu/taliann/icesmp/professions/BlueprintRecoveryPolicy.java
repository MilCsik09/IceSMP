package hu.taliann.icesmp.professions;

/**
 * Deterministic restart decision for a PREPARED blueprint-learning receipt.
 * The temporary reservation marker is the inventory-side witness; the learned flag is the
 * canonical PlayerProfile profession-side witness.
 */
public final class BlueprintRecoveryPolicy {

    public enum Decision {
        /** Profile did not change and no durable reservation exists. */
        ROLLBACK_UNTOUCHED,
        /** Profile did not change but the reserved blueprint is durable: release it. */
        RELEASE_AND_ROLLBACK,
        /** Profile unlock is durable and the reserved blueprint still exists: consume exactly one. */
        CONSUME_AND_COMMIT,
        /** Profile unlock is durable and reservation is gone: consumption already completed. */
        COMMIT_CONSUMED
    }

    private BlueprintRecoveryPolicy() { }

    public static Decision decide(final boolean learned, final boolean reservationPresent) {
        if (learned) {
            return reservationPresent ? Decision.CONSUME_AND_COMMIT : Decision.COMMIT_CONSUMED;
        }
        return reservationPresent ? Decision.RELEASE_AND_ROLLBACK : Decision.ROLLBACK_UNTOUCHED;
    }
}
