package hu.taliann.icesmp.quest;

import java.util.Locale;

/**
 * Láthatósági policy: a játékos nem feltétlenül ismer minden létező questet.
 * HIDDEN quest player-útvonalon (lista, journal, tab-complete) SOHA nem szivároghat.
 */
public enum QuestVisibility {
    ALWAYS,
    PREREQUISITES_MET,
    DISCOVERED,
    HIDDEN;

    public static QuestVisibility fromConfig(final String raw, final QuestVisibility fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException unknown) {
            return null;
        }
    }
}
