package hu.taliann.icesmp.data;

import org.bukkit.Material;

/**
 * The four faction currencies. Canon names come from docs/LORE.md (Parázsló Parals /
 * Hópihér-veret / Creutzér / Csontveret). Parsing stays generous: enum id, the currency's
 * canon name and any accepted faction name (short/full/legacy) all resolve — old saved
 * data and commands keep working.
 */
public enum CurrencyType {
    RED(FactionType.RED, "Parázsló Parals", 1001),
    BLUE(FactionType.BLUE, "Hópihér-veret", 1002),
    NEUTRAL(FactionType.NEUTRAL, "Creutzér", 1003),
    DARK(FactionType.DARK, "Csontveret", 1004);

    private final FactionType factionType;
    private final String currencyName;
    private final int customModelData;

    CurrencyType(final FactionType factionType, final String currencyName, final int customModelData) {
        this.factionType = factionType;
        this.currencyName = currencyName;
        this.customModelData = customModelData;
    }

    public FactionType toFactionType() {
        return factionType;
    }

    public String getDisplayName() {
        return currencyName;
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
            if (currencyType.name().equalsIgnoreCase(value)
                    || currencyType.currencyName.equalsIgnoreCase(value)) {
                return currencyType;
            }
        }

        // Faction-name based lookup (short/full/legacy — e.g. "Piros" still resolves to RED).
        final FactionType faction = FactionType.fromInput(value);
        return faction == null ? null : fromFactionType(faction);
    }
}
