package hu.taliann.icesmp.crates;

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

    public static double positiveWeight(final Object raw) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("a weight kötelező szám");
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value <= 0.0D || value > MAX_WEIGHT) {
            throw new IllegalArgumentException("a weight véges, pozitív és legfeljebb " + MAX_WEIGHT + " lehet");
        }
        return value;
    }

    public static int itemAmount(final Object raw, final int fallback) {
        final int value = raw == null ? fallback : exactInt(raw, "amount");
        if (value <= 0 || value > MAX_REWARD_ITEM_AMOUNT) {
            throw new IllegalArgumentException("az amount 1.." + MAX_REWARD_ITEM_AMOUNT + " lehet");
        }
        return value;
    }

    public static int boundedPositiveInt(final Object raw, final int fallback, final int maximum,
                                         final String field) {
        final int value = raw == null ? fallback : exactInt(raw, field);
        if (value <= 0 || value > maximum) {
            throw new IllegalArgumentException("a " + field + " 1.." + maximum + " lehet");
        }
        return value;
    }

    public static long cooldownMillis(final Object rawSeconds) {
        if (rawSeconds == null) {
            return 0L;
        }
        if (!(rawSeconds instanceof Number number)) {
            throw new IllegalArgumentException("a cooldown-seconds kötelező szám");
        }
        final double seconds = number.doubleValue();
        if (!Double.isFinite(seconds) || seconds < 0.0D
                || seconds > MAX_COOLDOWN_MILLIS / 1000.0D) {
            throw new IllegalArgumentException("a cooldown-seconds 0..604800 közötti véges szám lehet");
        }
        return Math.round(seconds * 1000.0D);
    }

    public static double currencyAmount(final Object raw) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("a currency amount kötelező szám");
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value <= 0.0D || value > MAX_CURRENCY_REWARD) {
            throw new IllegalArgumentException("a currency amount véges, pozitív és legfeljebb "
                    + MAX_CURRENCY_REWARD + " lehet");
        }
        return value;
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
        final String withoutKnown = matcherResetReplace(command);
        if (withoutKnown.indexOf('{') >= 0 || withoutKnown.indexOf('}') >= 0) {
            throw new IllegalArgumentException("hibás command placeholder-szintaxis");
        }
        return command;
    }

    private static String matcherResetReplace(final String command) {
        final Matcher matcher = PLACEHOLDER.matcher(command);
        return matcher.replaceAll("");
    }

    public static String renderCommand(final String template, final String player, final String uuid,
                                       final String crateId, final int amount) {
        return template
                .replace("{player}", player)
                .replace("{uuid}", uuid)
                .replace("{crate}", crateId)
                .replace("{amount}", Integer.toString(amount));
    }

    public static int maxOpenable(final int availableKeys, final int keysPerOpen, final int requested,
                                  final boolean massEnabled, final int massMaximum) {
        if (availableKeys < 0 || keysPerOpen <= 0 || requested <= 0 || massMaximum <= 0) {
            return 0;
        }
        final int requestedBound = massEnabled ? Math.min(requested, massMaximum) : 1;
        return Math.min(requestedBound, availableKeys / keysPerOpen);
    }

    private static int exactInt(final Object raw, final String field) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("a " + field + " kötelező egész szám");
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("a " + field + " kötelező egész szám");
        }
        return (int) value;
    }
}
