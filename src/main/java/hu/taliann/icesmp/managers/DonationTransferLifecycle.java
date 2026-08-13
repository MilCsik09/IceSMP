package hu.taliann.icesmp.managers;

public final class DonationTransferLifecycle {
    public enum State { DEPOSIT_PREPARED, AVAILABLE, CLAIM_PREPARED }
    public enum Recovery { ROLLBACK_DEPOSIT, COMMIT_DEPOSIT, DELIVER_CLAIM, FINALIZE_CLAIM, NONE }

    private DonationTransferLifecycle() { }

    public static Recovery recovery(final State state, final boolean transferMarkerPresent) {
        return switch (state) {
            case DEPOSIT_PREPARED -> transferMarkerPresent
                    ? Recovery.ROLLBACK_DEPOSIT : Recovery.COMMIT_DEPOSIT;
            case CLAIM_PREPARED -> transferMarkerPresent
                    ? Recovery.FINALIZE_CLAIM : Recovery.DELIVER_CLAIM;
            case AVAILABLE -> Recovery.NONE;
        };
    }
}
