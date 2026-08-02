package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;

/** Pure live-membership gate for every signature-food gameplay effect. */
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
}
