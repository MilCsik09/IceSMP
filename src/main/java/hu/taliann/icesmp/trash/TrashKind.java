package hu.taliann.icesmp.trash;

import java.util.Locale;

/** Server-only taxonomy; player-facing presentation always remains {@code Ócska}. */
public enum TrashKind {
    MUNDANE,
    STORY,
    ANOMALY,
    TRASH_RELIC;

    static TrashKind parse(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("hiányzó internal.kind");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("ismeretlen internal.kind: " + value, invalid);
        }
    }

    boolean isInert() {
        return this == MUNDANE || this == STORY;
    }
}
