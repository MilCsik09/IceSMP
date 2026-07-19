package hu.taliann.icesmp.data;

/**
 * The four factions. Canon names come from docs/LORE.md: the SHORT display name is what the
 * HUD/tablist/chat shows (space-constrained surfaces), the FULL name is for /faction info and
 * menus. Parsing stays generous: enum id, short, full and the legacy pre-reskin names
 * ("Piros/Kék/Semleges/Sötét") are all accepted, so old configs/commands keep working.
 */
public enum FactionType {
    RED("Láng", "Perinfernicitas", "Piros"),
    BLUE("Fagy", "Cryghaliris", "Kék"),
    NEUTRAL("Menedék", "Ryanora & Caldestera", "Semleges"),
    DARK("Kitaszított", "A Kitaszítottak", "Sötét");

    private final String displayName;
    private final String fullName;
    private final String legacyName;

    FactionType(final String displayName, final String fullName, final String legacyName) {
        this.displayName = displayName;
        this.fullName = fullName;
        this.legacyName = legacyName;
    }

    /** Short canon name for space-constrained surfaces (HUD, tablist, chat). */
    public String getDisplayName() {
        return displayName;
    }

    /** Full canon name for info screens and menus (e.g. "Perinfernicitas"). */
    public String getFullName() {
        return fullName;
    }

    private boolean matches(final String value) {
        return name().equalsIgnoreCase(value)
                || displayName.equalsIgnoreCase(value)
                || fullName.equalsIgnoreCase(value)
                || legacyName.equalsIgnoreCase(value);
    }

    public static FactionType fromString(final String value) {
        if (value == null || value.isBlank()) {
            return NEUTRAL;
        }

        for (final FactionType factionType : values()) {
            if (factionType.matches(value)) {
                return factionType;
            }
        }

        return NEUTRAL;
    }

    public static FactionType fromInput(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (final FactionType factionType : values()) {
            if (factionType.matches(value)) {
                return factionType;
            }
        }

        return null;
    }
}
