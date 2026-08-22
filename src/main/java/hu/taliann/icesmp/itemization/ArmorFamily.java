package hu.taliann.icesmp.itemization;

import java.util.Locale;

/** MMORPG armor authority; deliberately independent from the Bukkit backing material. */
public enum ArmorFamily {
    CLOTH("Szövet"),
    LEATHER("Bőr"),
    MAIL("Sodrony"),
    PLATE("Lemez");

    private final String displayName;

    ArmorFamily(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ArmorFamily parse(final String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid ArmorFamily: " + raw, invalid);
        }
    }
}
