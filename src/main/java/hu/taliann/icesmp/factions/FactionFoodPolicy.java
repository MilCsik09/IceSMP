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
