package hu.taliann.icesmp.crates;

/** Failure policy after reward-side effects have started. */
public final class CrateRewardProgress {

    public enum Recovery {
        REFUND_KEY,
        ROLLBACK_CURRENCY_THEN_REFUND_KEY,
        MANUAL_REVIEW
    }

    private CrateRewardProgress() {
    }

    public static Recovery recoveryFor(final int successfulCommands, final int successfulItems,
                                       final boolean currencyApplied) {
        if (successfulCommands < 0 || successfulItems < 0) {
            throw new IllegalArgumentException("Invalid crate reward progress");
        }
        if (successfulCommands > 0 || successfulItems > 0) {
            return Recovery.MANUAL_REVIEW;
        }
        return currencyApplied ? Recovery.ROLLBACK_CURRENCY_THEN_REFUND_KEY : Recovery.REFUND_KEY;
    }
}
