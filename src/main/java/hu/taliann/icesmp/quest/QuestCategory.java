package hu.taliann.icesmp.quest;

import java.util.Locale;

/** Küldetés-kategória metadata: journal-rendezés, marker-paletta és jövőbeli routing alapja. */
public enum QuestCategory {
    MAIN,
    SIDE,
    CLASS,
    SPECIALIZATION,
    FACTION,
    RELIC,
    DAILY,
    WEEKLY,
    WORLD,
    SECRET,
    TUTORIAL;

    /**
     * MMO ajánlási sorrend NPC-interakciónál (kisebb = előrébb): a story mindig megelőzi
     * a mellék- és rotációs tartalmat, a titok a lista végére kerül.
     */
    public int offerPriority() {
        return switch (this) {
            case MAIN -> 0;
            case TUTORIAL -> 1;
            case CLASS -> 2;
            case SPECIALIZATION -> 3;
            case RELIC -> 4;
            case FACTION -> 5;
            case SIDE -> 6;
            case WORLD -> 7;
            case DAILY -> 8;
            case WEEKLY -> 9;
            case SECRET -> 10;
        };
    }

    public static QuestCategory fromConfig(final String raw, final QuestCategory fallback) {
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
