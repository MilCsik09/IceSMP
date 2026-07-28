package hu.taliann.icesmp.moderation;

import java.time.Duration;
import java.util.Locale;

/** Dependency-free parser shared by moderation commands and regression tests. */
public final class ModerationDuration {
    private static final Duration MAXIMUM = Duration.ofDays(365);

    private ModerationDuration() {
    }

    public static Long parseMillis(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("0") || normalized.equals("permanent") || normalized.equals("vegleges")
                || normalized.equals("végleges")) {
            return 0L;
        }
        final char suffix = normalized.charAt(normalized.length() - 1);
        final String numberPart = Character.isLetter(suffix)
                ? normalized.substring(0, normalized.length() - 1) : normalized;
        final long amount;
        try {
            amount = Long.parseLong(numberPart);
        } catch (final NumberFormatException invalid) {
            return null;
        }
        if (amount <= 0L) {
            return null;
        }
        final Duration duration;
        try {
            duration = switch (suffix) {
                case 's' -> Duration.ofSeconds(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd', 'n' -> Duration.ofDays(amount);
                case 'w' -> Duration.ofDays(Math.multiplyExact(amount, 7L));
                case 'm', 'p' -> Duration.ofMinutes(amount);
                default -> Character.isLetter(suffix) ? null : Duration.ofMinutes(amount);
            };
        } catch (final ArithmeticException overflow) {
            return null;
        }
        if (duration == null || duration.isZero() || duration.isNegative() || duration.compareTo(MAXIMUM) > 0) {
            return null;
        }
        try {
            return duration.toMillis();
        } catch (final ArithmeticException overflow) {
            return null;
        }
    }

    public static boolean looksLikeToken(final String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        final String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("permanent") || normalized.equals("vegleges")
                || normalized.equals("végleges")) {
            return true;
        }
        return normalized.matches("[+-]?\\d+[shdwnmp]?");
    }
}
