package hu.taliann.icesmp.crates;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free validation rules shared by the crate config loader and regressions. */
public final class CrateRules {

    public static final int MAX_REWARDS = 128;
    public static final int MAX_REWARD_ITEM_AMOUNT = 64;
    public static final int MAX_KEY_AMOUNT = 2304;
    public static final int MAX_RECIPE_REWARD_AMOUNT = 64;
    public static final int MAX_REQUIRED_KEYS = 2304;
    public static final int MAX_MASS_OPEN = 64;
    public static final double MAX_WEIGHT = 1_000_000_000.0D;
    public static final double MAX_CURRENCY_REWARD = 1_000_000_000.0D;
    public static final long MAX_COOLDOWN_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    public static final int MAX_COMMAND_LENGTH = 256;

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private static final Set<String> COMMAND_PLACEHOLDERS = Set.of("player", "uuid", "crate", "amount");

    private CrateRules() {
    }

    public static String normalizeId(final String raw) {
        if (raw == null) {
            return null;
        }
        final String normalized = raw.strip().toLowerCase(Locale.ROOT);
        return ID.matcher(normalized).matches() ? normalized : null;
    }

    public static boolean strictBoolean(final Object raw, final boolean fallback, final String field) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        throw new IllegalArgumentException("a " + field + " csak valódi boolean lehet");
    }

    public static long exactLong(final Object raw, final long fallback, final long minimum,
                                 final long maximum, final String field) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("Invalid crate integer range");
        }
        if (raw == null) {
            return requireRange(fallback, minimum, maximum, field);
        }
        final BigInteger integer;
        try {
            if (raw instanceof BigInteger value) {
                integer = value;
            } else if (raw instanceof BigDecimal value) {
                integer = value.toBigIntegerExact();
            } else if (raw instanceof Byte || raw instanceof Short
                    || raw instanceof Integer || raw instanceof Long) {
                integer = BigInteger.valueOf(((Number) raw).longValue());
            } else if (raw instanceof CharSequence text) {
                final String normalized = text.toString().strip();
                if (normalized.isEmpty()) {
                    throw invalidInteger(field);
                }
                integer = new BigInteger(normalized);
            } else {
                // Float/Double are deliberately rejected even when mathematically integral: YAML
                // integer state must never cross a binary floating-point representation.
                throw invalidInteger(field);
            }
            final BigInteger min = BigInteger.valueOf(minimum);
            final BigInteger max = BigInteger.valueOf(maximum);
            if (integer.compareTo(min) < 0 || integer.compareTo(max) > 0) {
                throw new IllegalArgumentException("a " + field + " tartománya: " + minimum + ".." + maximum);
            }
            return integer.longValueExact();
        } catch (final NumberFormatException | ArithmeticException failure) {
            throw invalidInteger(field);
        }
    }

    public static int exactInt(final Object raw, final int fallback, final int minimum,
                               final int maximum, final String field) {
        return Math.toIntExact(exactLong(raw, fallback, minimum, maximum, field));
    }

    public static List<String> strictStringList(final Object raw, final String field) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException("a " + field + " csak string lista lehet");
        }
        final List<String> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            final Object value = values.get(index);
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("a " + field + "[" + index + "] nem üres string kell legyen");
            }
            result.add(text.strip());
        }
        return List.copyOf(result);
    }

    public static double finiteDecimal(final Object raw, final double fallback, final double minimum,
                                       final double maximum, final String field) {
        if (raw == null) {
            return requireDecimalRange(fallback, minimum, maximum, field);
        }
        final double value;
        try {
            if (raw instanceof BigDecimal decimal) {
                value = decimal.doubleValue();
            } else if (raw instanceof BigInteger integer) {
                value = new BigDecimal(integer).doubleValue();
            } else if (raw instanceof Number number) {
                value = number.doubleValue();
            } else if (raw instanceof CharSequence text) {
                value = new BigDecimal(text.toString().strip()).doubleValue();
            } else {
                throw invalidDecimal(field);
            }
        } catch (final NumberFormatException failure) {
            throw invalidDecimal(field);
        }
        return requireDecimalRange(value, minimum, maximum, field);
    }

    public static double positiveWeight(final Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("a weight kötelező szám");
        }
        return finiteDecimal(raw, 0.0D, Math.nextUp(0.0D), MAX_WEIGHT, "weight");
    }

    public static int itemAmount(final Object raw, final int fallback) {
        return exactInt(raw, fallback, 1, MAX_REWARD_ITEM_AMOUNT, "amount");
    }

    public static int boundedPositiveInt(final Object raw, final int fallback, final int maximum,
                                         final String field) {
        return exactInt(raw, fallback, 1, maximum, field);
    }

    public static long cooldownMillis(final Object rawSeconds) {
        final long seconds = exactLong(rawSeconds, 0L, 0L, MAX_COOLDOWN_MILLIS / 1000L,
                "cooldown-seconds");
        return Math.multiplyExact(seconds, 1000L);
    }

    public static double currencyAmount(final Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("a currency amount kötelező szám");
        }
        return finiteDecimal(raw, 0.0D, Math.nextUp(0.0D), MAX_CURRENCY_REWARD,
                "currency amount");
    }

    public static String validateCommand(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("a command kötelező");
        }
        final String command = raw.strip();
        if (command.isEmpty() || command.length() > MAX_COMMAND_LENGTH
                || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0
                || command.startsWith("/")) {
            throw new IllegalArgumentException("a command 1.." + MAX_COMMAND_LENGTH
                    + " karakteres, sortörés és kezdő / nélküli parancs lehet");
        }
        final Matcher matcher = PLACEHOLDER.matcher(command);
        while (matcher.find()) {
            if (!COMMAND_PLACEHOLDERS.contains(matcher.group(1))) {
                throw new IllegalArgumentException("nem engedélyezett command placeholder: {" + matcher.group(1) + "}");
            }
        }
        final String withoutKnown = PLACEHOLDER.matcher(command).replaceAll("");
        if (withoutKnown.indexOf('{') >= 0 || withoutKnown.indexOf('}') >= 0) {
            throw new IllegalArgumentException("hibás command placeholder-szintaxis");
        }
        return command;
    }

    public static String renderCommand(final String template, final String player, final String uuid,
                                       final String crateId, final int amount) {
        return template
                .replace("{player}", player)
                .replace("{uuid}", uuid)
                .replace("{crate}", crateId)
                .replace("{amount}", Integer.toString(amount));
    }

    public static String formatPercent(final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("nem véges százalék");
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    public static int maxOpenable(final int availableKeys, final int keysPerOpen, final int requested,
                                  final boolean massEnabled, final int massMaximum) {
        if (availableKeys < 0 || keysPerOpen <= 0 || requested <= 0 || massMaximum <= 0) {
            return 0;
        }
        final int requestedBound = massEnabled ? Math.min(requested, massMaximum) : 1;
        return Math.min(requestedBound, availableKeys / keysPerOpen);
    }

    private static long requireRange(final long value, final long minimum, final long maximum,
                                     final String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("a " + field + " tartománya: " + minimum + ".." + maximum);
        }
        return value;
    }

    private static double requireDecimalRange(final double value, final double minimum,
                                              final double maximum, final String field) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("a " + field + " véges és " + minimum + ".." + maximum + " közötti lehet");
        }
        return value;
    }

    private static IllegalArgumentException invalidInteger(final String field) {
        return new IllegalArgumentException("a " + field + " csak pontos, 64 bites egész szám lehet");
    }

    private static IllegalArgumentException invalidDecimal(final String field) {
        return new IllegalArgumentException("a " + field + " csak véges szám lehet");
    }
}
