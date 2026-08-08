package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;
import net.kyori.adventure.text.format.NamedTextColor;

/** Adventure adapter for the dependency-free faction display-colour policy. */
public final class FactionDisplayPalette {

    private FactionDisplayPalette() {
    }

    public static NamedTextColor playerName(final FactionType faction) {
        return toAdventure(FactionDisplayColorPolicy.playerName(faction));
    }

    public static NamedTextColor playerName(final String factionId) {
        return toAdventure(FactionDisplayColorPolicy.playerName(factionId));
    }

    public static String legacyPlayerName(final FactionType faction) {
        return FactionDisplayColorPolicy.legacyPlayerName(faction);
    }

    public static String legacyPlayerName(final String factionId) {
        return FactionDisplayColorPolicy.legacyPlayerName(factionId);
    }

    /** A konfigurált Adventure-szín §-kódja külső TAB/scoreboard fogyasztóknak. */
    public static String legacyCode(final NamedTextColor color) {
        return org.bukkit.ChatColor.valueOf(
                color.toString().toUpperCase(java.util.Locale.ROOT)).toString();
    }

    private static NamedTextColor toAdventure(final FactionDisplayColorPolicy.NameColor color) {
        return switch (color) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case WHITE -> NamedTextColor.WHITE;
        };
    }
}
