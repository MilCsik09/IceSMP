package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;

import java.util.Locale;

/**
 * Dependency-free authority for player-name colours across native and external display surfaces.
 */
public final class FactionDisplayColorPolicy {

    public enum NameColor {
        RED("§c"),
        BLUE("§9"),
        GOLD("§6"),
        DARK_GRAY("§8"),
        WHITE("§f");

        private final String legacyCode;

        NameColor(final String legacyCode) {
            this.legacyCode = legacyCode;
        }

        public String legacyCode() {
            return legacyCode;
        }
    }

    private FactionDisplayColorPolicy() {
    }

    public static NameColor playerName(final FactionType faction) {
        if (faction == null) {
            return NameColor.WHITE;
        }
        return switch (faction) {
            case RED -> NameColor.RED;
            case BLUE -> NameColor.BLUE;
            case NEUTRAL -> NameColor.GOLD;
            case DARK -> NameColor.DARK_GRAY;
        };
    }

    public static NameColor playerName(final String factionId) {
        if (factionId == null || factionId.isBlank()) {
            return NameColor.WHITE;
        }
        try {
            return playerName(FactionType.valueOf(factionId.trim().toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException unknown) {
            return NameColor.WHITE;
        }
    }

    public static String legacyPlayerName(final FactionType faction) {
        return playerName(faction).legacyCode();
    }

    public static String legacyPlayerName(final String factionId) {
        return playerName(factionId).legacyCode();
    }
}
