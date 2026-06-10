package hu.taliann.icesmp.data;

public enum FactionType {
    RED("Piros"),
    BLUE("Kék"),
    NEUTRAL("Semleges");

    private final String displayName;

    FactionType(final String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static FactionType fromString(final String value) {
        if (value == null || value.isBlank()) {
            return NEUTRAL;
        }

        for (final FactionType factionType : values()) {
            if (factionType.name().equalsIgnoreCase(value) || factionType.displayName.equalsIgnoreCase(value)) {
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
            if (factionType.name().equalsIgnoreCase(value) || factionType.displayName.equalsIgnoreCase(value)) {
                return factionType;
            }
        }

        return null;
    }
}

