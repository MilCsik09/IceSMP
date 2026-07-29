package hu.taliann.icesmp.crates;

import java.math.BigDecimal;

/** Thread-safe immutable decimal formatting for region-thread callers. */
public final class CrateFormatting {
    private CrateFormatting() {
    }

    public static String decimal(final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite crate number");
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
