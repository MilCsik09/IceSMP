package hu.taliann.icesmp.motd;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

/** Dependency-free deterministic selector and strict scalar rules used by the server-list presentation. */
public final class MotdSelector {

    private static final Set<String> SUPPORTED_PLACEHOLDERS = Set.of("online", "max");

    public enum Mode {
        TIME,
        RANDOM;

        public static Mode parse(final String value) {
            if (value == null) {
                throw new IllegalArgumentException("A MOTD választási mód hiányzik.");
            }
            try {
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Ismeretlen MOTD választási mód: " + value);
            }
        }
    }

    public enum ActiveEvent {
        BLOOD_MOON,
        WORLD_BOSS,
        SEASON_END,
        NONE
    }

    private MotdSelector() {
    }

    public static int selectIndex(final Mode mode, final int size, final long nowMillis,
                                  final long intervalMillis, final long seed) {
        if (mode == null) {
            throw new IllegalArgumentException("A választási mód nem lehet null.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("A MOTD variánslista nem lehet üres.");
        }
        if (intervalMillis <= 0L) {
            throw new IllegalArgumentException("A MOTD intervallum csak pozitív lehet.");
        }
        final long bucket = Math.floorDiv(nowMillis, intervalMillis);
        if (mode == Mode.TIME) {
            return Math.floorMod(bucket, size);
        }
        return Math.floorMod(mix64(seed ^ (bucket * 0x9E3779B97F4A7C15L)), size);
    }

    public static ActiveEvent selectEvent(final boolean bloodMoonActive, final boolean worldBossActive,
                                          final long seasonEndMillis, final long nowMillis,
                                          final long seasonThresholdMillis) {
        if (bloodMoonActive) {
            return ActiveEvent.BLOOD_MOON;
        }
        if (worldBossActive) {
            return ActiveEvent.WORLD_BOSS;
        }
        if (seasonThresholdMillis < 0L || seasonEndMillis < nowMillis) {
            return ActiveEvent.NONE;
        }
        final long latestIncluded = nowMillis > Long.MAX_VALUE - seasonThresholdMillis
                ? Long.MAX_VALUE : nowMillis + seasonThresholdMillis;
        return seasonEndMillis <= latestIncluded ? ActiveEvent.SEASON_END : ActiveEvent.NONE;
    }

    /**
     * Parses a finite integral scalar without routing exact long values through {@code double}.
     * This keeps configured random seeds deterministic across the entire signed 64-bit range.
     */
    public static long parseWholeNumber(final Object raw, final long fallback, final long minimum,
                                        final long maximum, final String path) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("Érvénytelen egészszám-tartomány: " + minimum + ".." + maximum);
        }
        if (raw == null) {
            return requireRange(fallback, minimum, maximum, path);
        }

        final BigDecimal decimal;
        try {
            if (raw instanceof BigDecimal value) {
                decimal = value;
            } else if (raw instanceof BigInteger value) {
                decimal = new BigDecimal(value);
            } else if (raw instanceof Byte || raw instanceof Short
                    || raw instanceof Integer || raw instanceof Long) {
                decimal = BigDecimal.valueOf(((Number) raw).longValue());
            } else if (raw instanceof Float || raw instanceof Double) {
                final double value = ((Number) raw).doubleValue();
                if (!Double.isFinite(value)) {
                    throw invalidWholeNumber(path);
                }
                decimal = BigDecimal.valueOf(value);
            } else if (raw instanceof Number value) {
                decimal = new BigDecimal(value.toString());
            } else {
                final String text = raw.toString().trim();
                if (text.isEmpty()) {
                    throw invalidWholeNumber(path);
                }
                decimal = new BigDecimal(text);
            }
            return requireRange(decimal.longValueExact(), minimum, maximum, path);
        } catch (final NumberFormatException | ArithmeticException exception) {
            throw invalidWholeNumber(path);
        }
    }

    /** Rejects every brace token except the two documented dynamic MOTD placeholders. */
    public static void validatePlaceholders(final String value, final String path) {
        if (value == null) {
            throw new IllegalArgumentException(path + ": a szöveg nem lehet null");
        }
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current == '}') {
                throw new IllegalArgumentException(path + ": pár nélküli '}' karakter");
            }
            if (current != '{') {
                continue;
            }
            final int close = value.indexOf('}', index + 1);
            if (close < 0) {
                throw new IllegalArgumentException(path + ": lezáratlan placeholder");
            }
            final String token = value.substring(index + 1, close);
            if (token.indexOf('{') >= 0 || !SUPPORTED_PLACEHOLDERS.contains(token)) {
                throw new IllegalArgumentException(path + ": ismeretlen placeholder: {" + token + "}");
            }
            index = close;
        }
    }

    private static long requireRange(final long value, final long minimum, final long maximum,
                                     final String path) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + ": tartományon kívüli érték ("
                    + minimum + ".." + maximum + ")");
        }
        return value;
    }

    private static IllegalArgumentException invalidWholeNumber(final String path) {
        return new IllegalArgumentException(path + ": csak véges, 64 bites egész szám lehet");
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
