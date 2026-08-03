package hu.taliann.icesmp.factions;

/**
 * Pure classification for write-ahead journal recovery. The two durable domains may each be at
 * their exact before or after snapshot. Only the four unambiguous combinations are actionable;
 * every partial/unknown state fails closed for manual recovery.
 */
public final class DurableRecoveryPolicy {

    public enum Decision {
        COMPLETE_COMMITTED,
        DISCARD_UNAPPLIED,
        ROLLBACK_WALLET,
        ROLLBACK_DOMAIN,
        AMBIGUOUS
    }

    private DurableRecoveryPolicy() {
    }

    public static Decision decide(final boolean domainBefore,
                                  final boolean domainAfter,
                                  final boolean walletBefore,
                                  final boolean walletAfter,
                                  final boolean hasWalletMutation) {
        if (domainAfter && walletAfter) {
            return Decision.COMPLETE_COMMITTED;
        }
        if (domainBefore && walletBefore) {
            return Decision.DISCARD_UNAPPLIED;
        }
        if (hasWalletMutation && domainBefore && walletAfter) {
            return Decision.ROLLBACK_WALLET;
        }
        if (domainAfter && walletBefore) {
            return Decision.ROLLBACK_DOMAIN;
        }
        return Decision.AMBIGUOUS;
    }
}
