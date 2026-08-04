package hu.taliann.icesmp.factions;

/**
 * Small, dependency-free write-ahead transaction protocol shared by faction membership and tax
 * mutations. A failure before the durable domain commit compensates the wallet. A journal-cleanup
 * failure after the domain commit never compensates an already committed transaction; the WAL is
 * deliberately left for idempotent startup recovery.
 */
public final class DurableTransactionProtocol {

    /** Result of a durable commit. A pending WAL cleanup is not a failed domain transaction. */
    public record ExecutionResult(boolean recoveryPending, Throwable cleanupFailure) {
        public static ExecutionResult complete() {
            return new ExecutionResult(false, null);
        }

        public static ExecutionResult recoveryPending(final Throwable failure) {
            return new ExecutionResult(true, failure);
        }
    }

    public interface Steps {
        void prepare();

        boolean hasWalletMutation();

        void applyWallet();

        void commitDomain();

        void rollbackWallet();

        void completeJournal();
    }

    private DurableTransactionProtocol() {
    }

    public static ExecutionResult execute(final Steps steps) {
        if (steps == null) {
            throw new IllegalArgumentException("Transaction steps are required");
        }
        steps.prepare();
        boolean walletApplied = false;
        try {
            if (steps.hasWalletMutation()) {
                steps.applyWallet();
                walletApplied = true;
            }
            steps.commitDomain();
        } catch (final RuntimeException | Error failure) {
            if (walletApplied) {
                try {
                    steps.rollbackWallet();
                } catch (final RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                    throw failure;
                }
            }
            try {
                steps.completeJournal();
            } catch (final RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }

        // This is intentionally outside the compensation block. At this point both durable stores
        // have committed. If WAL cleanup fails, startup recovery observes the all-after state and
        // completes it; rolling the wallet back here would create a split-brain commit.
        try {
            steps.completeJournal();
            return ExecutionResult.complete();
        } catch (final RuntimeException | Error cleanupFailure) {
            return ExecutionResult.recoveryPending(cleanupFailure);
        }
    }
}
