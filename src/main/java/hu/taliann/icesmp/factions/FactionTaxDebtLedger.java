package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileTaxStore;

/**
 * Temporary source-compatibility alias for pure tax policy helpers.
 *
 * <p>This type owns no state, persistence, migration data or player UUID map. Canonical tax debt,
 * strikes and outboxes live exclusively in {@link PlayerProfileTaxStore}. New code must call the
 * PlayerProfile store directly; this alias can be removed after the stacked PRs are restacked.</p>
 */
@Deprecated(forRemoval = true)
public final class FactionTaxDebtLedger {

    private FactionTaxDebtLedger() { }

    public record EvasionDecision(int strikesAfter, boolean reportSin) {
        public EvasionDecision {
            if (strikesAfter < 0) {
                throw new IllegalArgumentException("Tax-evasion strikes cannot be negative");
            }
        }
    }

    public static EvasionDecision afterCollection(final int strikesBefore,
                                                  final double paid,
                                                  final double owedAfter,
                                                  final double maxArrears,
                                                  final int threshold,
                                                  final boolean mayReportSin) {
        final PlayerProfileTaxStore.EvasionDecision decision =
                PlayerProfileTaxStore.afterCollection(strikesBefore, paid, owedAfter,
                        maxArrears, threshold, mayReportSin);
        return new EvasionDecision(decision.strikesAfter(), decision.reportSin());
    }

    public static double checkedAmountAdd(final double current, final double addition) {
        return PlayerProfileTaxStore.checkedAmountAdd(current, addition);
    }
}
