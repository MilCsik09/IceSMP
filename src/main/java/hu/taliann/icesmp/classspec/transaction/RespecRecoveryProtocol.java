package hu.taliann.icesmp.classspec.transaction;

/**
 * Pure crash-recovery decision table for the cross-store respec protocol.
 * The Profile v2 durable operation receipt is the semantic commit witness; the wallet operation
 * independently proves whether the debit is missing, reversible, or already terminal.
 */
public final class RespecRecoveryProtocol {
    private RespecRecoveryProtocol() {
    }

    public enum WalletWitness {
        NOT_REQUIRED,
        MISSING,
        DEBITED,
        COMMITTED,
        ROLLED_BACK
    }

    public enum Action {
        COMPLETE,
        COMMIT_WALLET_AND_COMPLETE,
        ROLLBACK_WALLET_AND_ABORT,
        DELETE_INTENT_AND_ABORT,
        BLOCK_INCONSISTENT
    }

    public record Decision(Action action, String reason) {
        public Decision {
            if (action == null || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Recovery decision must be complete");
            }
        }
    }

    public static Decision decide(final boolean profileCommitted, final double amount,
                                  final WalletWitness walletWitness) {
        if (!Double.isFinite(amount) || amount < 0.0D) {
            throw new IllegalArgumentException("Respec amount must be finite and non-negative");
        }
        if (walletWitness == null) {
            throw new IllegalArgumentException("Wallet witness is required");
        }
        if (amount == 0.0D) {
            if (walletWitness != WalletWitness.NOT_REQUIRED && walletWitness != WalletWitness.MISSING) {
                return new Decision(Action.BLOCK_INCONSISTENT,
                        "Zero-cost respec unexpectedly has a durable wallet mutation");
            }
            return profileCommitted
                    ? new Decision(Action.COMPLETE, "Zero-cost Profile v2 respec is committed")
                    : new Decision(Action.DELETE_INTENT_AND_ABORT,
                            "Zero-cost respec did not reach the Profile v2 commit");
        }
        if (walletWitness == WalletWitness.NOT_REQUIRED) {
            return new Decision(Action.BLOCK_INCONSISTENT,
                    "Paid respec has no wallet witness classification");
        }
        if (profileCommitted) {
            return switch (walletWitness) {
                case DEBITED -> new Decision(Action.COMMIT_WALLET_AND_COMPLETE,
                        "Profile v2 commit won; finalize the exact durable wallet debit");
                case COMMITTED -> new Decision(Action.COMPLETE,
                        "Profile v2 and wallet receipts are both committed");
                case MISSING -> new Decision(Action.BLOCK_INCONSISTENT,
                        "Profile v2 respec committed without a durable wallet witness");
                case ROLLED_BACK -> new Decision(Action.BLOCK_INCONSISTENT,
                        "Profile v2 respec committed after the wallet debit was rolled back");
                case NOT_REQUIRED -> throw new IllegalStateException("handled above");
            };
        }
        return switch (walletWitness) {
            case MISSING, ROLLED_BACK -> new Decision(Action.DELETE_INTENT_AND_ABORT,
                    "Profile v2 commit is absent and no debit remains to recover");
            case DEBITED -> new Decision(Action.ROLLBACK_WALLET_AND_ABORT,
                    "Profile v2 commit is absent; roll back the exact durable debit");
            case COMMITTED -> new Decision(Action.BLOCK_INCONSISTENT,
                    "Wallet operation committed without a Profile v2 respec receipt");
            case NOT_REQUIRED -> throw new IllegalStateException("handled above");
        };
    }
}
