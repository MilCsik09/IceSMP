package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;

/** Pure live-membership and duration gates for faction-food gameplay effects. */
public final class FactionFoodPolicy {

    private FactionFoodPolicy() {
    }

    public static FactionType requiredFaction(final String signature) {
        if (signature == null) {
            return null;
        }
        return switch (signature) {
            case "fagyasztott_pisztrang", "sarkany_porkolt" -> FactionType.BLUE;
            case "fonixtojas_rantotta", "vadlakoma" -> FactionType.RED;
            case "kakaobabos_sutemeny", "vandorunnep_lepenye" -> FactionType.NEUTRAL;
            case "mortengradi_hamukenyer", "hamvak_lakomaja" -> FactionType.DARK;
            default -> null;
        };
    }

    public static FactionType requiredFaction(final String signature,
                                              final boolean trustedFoodMarker) {
        return trustedFoodMarker ? requiredFaction(signature) : null;
    }

    public static boolean mayApplyBuff(final FactionType currentFaction,
                                       final String signature,
                                       final boolean trustedFoodMarker) {
        final FactionType required = requiredFaction(signature, trustedFoodMarker);
        return required != null && currentFaction == required;
    }

    /** Revalidates a queued food-duty callback against the live config and membership. */
    public static boolean mayRunDutyCallback(final boolean enabled,
                                             final FactionType currentFaction) {
        return enabled && (currentFaction == FactionType.BLUE
                || currentFaction == FactionType.RED);
    }

    /** Converts a positive config duration without overflow; zero means fail-closed/disabled. */
    public static long durationMillis(final long value, final long unitMillis) {
        if (value <= 0L || unitMillis <= 0L) {
            return 0L;
        }
        try {
            return Math.multiplyExact(value, unitMillis);
        } catch (final ArithmeticException overflow) {
            return 0L;
        }
    }

    /** Builds a future deadline without wrapping into the past. */
    public static long deadline(final long now, final long delayMillis) {
        if (now < 0L || delayMillis <= 0L) {
            return 0L;
        }
        try {
            return Math.addExact(now, delayMillis);
        } catch (final ArithmeticException overflow) {
            return 0L;
        }
    }

    /** Corrupt/future timestamps and invalid durations never trigger a duty debuff. */
    public static boolean hasGraceElapsed(final long now, final long last,
                                          final long graceMillis) {
        return now >= 0L && last >= 0L && graceMillis > 0L && now >= last
                && now - last >= graceMillis;
    }

    /** Converts seconds to Paper ticks without int overflow; zero means skip the effect. */
    public static int durationTicks(final int seconds) {
        if (seconds <= 0) {
            return 0;
        }
        try {
            return Math.multiplyExact(seconds, 20);
        } catch (final ArithmeticException overflow) {
            return 0;
        }
    }
}
