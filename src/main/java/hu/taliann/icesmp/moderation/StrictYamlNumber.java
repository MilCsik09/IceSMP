package hu.taliann.icesmp.moderation;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Exact integral conversion for authoritative YAML fields; never truncates fractional values. */
public final class StrictYamlNumber {
    private StrictYamlNumber() {
    }

    public static long requireLong(final Object value, final String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("missing number: " + field);
        }
        try {
            if (number instanceof Byte || number instanceof Short
                    || number instanceof Integer || number instanceof Long) {
                return number.longValue();
            }
            if (number instanceof BigInteger integer) {
                return integer.longValueExact();
            }
            if (number instanceof BigDecimal decimal) {
                return decimal.longValueExact();
            }
            return new BigDecimal(number.toString()).longValueExact();
        } catch (final NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("expected exact integral long: " + field, invalid);
        }
    }

    public static int requireInt(final Object value, final String field) {
        final long parsed = requireLong(value, field);
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("integer out of range: " + field);
        }
        return (int) parsed;
    }
}
