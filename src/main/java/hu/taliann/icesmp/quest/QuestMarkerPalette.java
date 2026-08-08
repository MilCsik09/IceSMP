package hu.taliann.icesmp.quest;

import org.bukkit.Color;

/**
 * Központi NPC-marker szemantika és színpaletta — a per-player megjelenítés (particle)
 * a FancyNpcs-hídban marad, de a jelentés→szín döntés EGY helyen él, nem szétszórva.
 *
 * Vizuális kánon: ! sárga = új quest; ? arany = leadható; ? szürke = aktív, még nincs
 * kész; ! kék = napi/heti; ! lila = class/spec/relic; ! türkiz = titok.
 */
public final class QuestMarkerPalette {

    public enum MarkerState {
        AVAILABLE,
        READY_TO_TURN_IN,
        IN_PROGRESS_INTERACTION,
        DAILY,
        CLASS,
        SPECIALIZATION,
        RELIC,
        SECRET
    }

    private QuestMarkerPalette() {
    }

    /** A kategória-alapú "elérhető" marker-állapot (AVAILABLE finomítása). */
    public static MarkerState availableStateFor(final QuestCategory category) {
        return switch (category == null ? QuestCategory.SIDE : category) {
            case DAILY, WEEKLY -> MarkerState.DAILY;
            case CLASS -> MarkerState.CLASS;
            case SPECIALIZATION -> MarkerState.SPECIALIZATION;
            case RELIC -> MarkerState.RELIC;
            case SECRET -> MarkerState.SECRET;
            default -> MarkerState.AVAILABLE;
        };
    }

    public static Color color(final MarkerState state) {
        return switch (state) {
            case AVAILABLE -> Color.YELLOW;
            case READY_TO_TURN_IN -> Color.fromRGB(0xFF, 0xAA, 0x00);
            case IN_PROGRESS_INTERACTION -> Color.GRAY;
            case DAILY -> Color.fromRGB(0x33, 0x66, 0xFF);
            case CLASS, SPECIALIZATION, RELIC -> Color.fromRGB(0xAA, 0x00, 0xAA);
            case SECRET -> Color.fromRGB(0x00, 0xCC, 0xCC);
        };
    }

    public static String symbol(final MarkerState state) {
        return switch (state) {
            case READY_TO_TURN_IN -> "?";
            case IN_PROGRESS_INTERACTION -> "?";
            default -> "!";
        };
    }
}
