package hu.taliann.icesmp.factions;

/** Pure state transition for durable tax-evasion sin delivery. */
public final class FactionTaxEvasionPolicy {

    private FactionTaxEvasionPolicy() {
    }

    public record Decision(int strikesAfter, boolean reportSin) {
        public Decision {
            if (strikesAfter < 0) {
                throw new IllegalArgumentException("Tax-evasion strikes cannot be negative");
            }
        }
    }

    /**
     * A reached threshold is a durable pending outbox marker. It is never cleared by repayment,
     * logout or scheduler rejection; only the successful owner-thread delivery acknowledges it.
     */
    public static Decision afterCollection(final int strikesBefore,
                                           final double paid,
                                           final double owedAfter,
                                           final double maxArrears,
                                           final int threshold,
                                           final boolean mayReportSin) {
        final int normalizedBefore = Math.max(0, strikesBefore);
        if (threshold <= 0) {
            return new Decision(0, false);
        }
        if (normalizedBefore >= threshold) {
            return new Decision(normalizedBefore, mayReportSin);
        }
        if (!Double.isFinite(paid) || paid < 0.0D
                || !Double.isFinite(owedAfter) || owedAfter < 0.0D
                || !Double.isFinite(maxArrears) || maxArrears < 0.0D) {
            return new Decision(normalizedBefore, false);
        }
        if (maxArrears > 0.0D && paid <= 0.0D && owedAfter >= maxArrears) {
            final int next = normalizedBefore >= threshold - 1
                    ? threshold : normalizedBefore + 1;
            return new Decision(next, next >= threshold && mayReportSin);
        }
        if (owedAfter <= 0.0D || owedAfter < maxArrears) {
            return new Decision(0, false);
        }
        return new Decision(normalizedBefore, false);
    }
}
