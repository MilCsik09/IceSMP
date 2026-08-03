package hu.taliann.icesmp.factions;

/** Pure finite-arithmetic guard for persisted faction treasury balances. */
public final class FactionTreasuryAmountPolicy {

    private FactionTreasuryAmountPolicy() {
    }

    /** Returns NaN when either operand or the resulting balance is invalid/non-finite. */
    public static double checkedAdd(final double current, final double addition) {
        if (!Double.isFinite(current) || current < 0.0D
                || !Double.isFinite(addition) || addition <= 0.0D) {
            return Double.NaN;
        }
        final double next = current + addition;
        return Double.isFinite(next) && next >= 0.0D ? next : Double.NaN;
    }
}
