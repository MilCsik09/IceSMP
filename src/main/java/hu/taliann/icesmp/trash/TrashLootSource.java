package hu.taliann.icesmp.trash;

import java.util.Locale;

/** Canonical acquisition channel; luck, rank and profession modifiers never enter this enum. */
public enum TrashLootSource {
    FISHING("FISH"),
    MOB("MOB"),
    AMBIENT("AMBIENT");

    private final String affinityToken;

    TrashLootSource(final String affinityToken) {
        this.affinityToken = affinityToken;
    }

    String affinityToken() {
        return affinityToken;
    }

    static TrashLootSource parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("hiányzó Trash loot source");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("ismeretlen Trash loot source: " + raw, invalid);
        }
    }
}
