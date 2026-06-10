package hu.taliann.icesmp.data;

import org.bukkit.Material;

public enum CurrencyType {
    RED(FactionType.RED, 1001),
    BLUE(FactionType.BLUE, 1002),
    NEUTRAL(FactionType.NEUTRAL, 1003);

    private final FactionType factionType;
    private final int customModelData;

    CurrencyType(final FactionType factionType, final int customModelData) {
        this.factionType = factionType;
        this.customModelData = customModelData;
    }

    public FactionType toFactionType() {
        return factionType;
    }

    public String getDisplayName() {
        return factionType.getDisplayName();
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public Material getMaterial() {
        return Material.PAPER;
    }

    public static CurrencyType fromFactionType(final FactionType factionType) {
        if (factionType == null) {
            return NEUTRAL;
        }

        for (final CurrencyType currencyType : values()) {
            if (currencyType.factionType == factionType) {
                return currencyType;
            }
        }

        return NEUTRAL;
    }

    public static CurrencyType fromInput(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (final CurrencyType currencyType : values()) {
            if (currencyType.name().equalsIgnoreCase(value) || currencyType.factionType.getDisplayName().equalsIgnoreCase(value)) {
                return currencyType;
            }
        }

        return null;
    }
}

