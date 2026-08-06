package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free palette and display-consumer regressions. */
public final class FactionDisplayColorRegressionSuite {

    private FactionDisplayColorRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        canonicalPaletteIsDistinct();
        unknownMembershipStaysWhite();
        allPlayerNameConsumersUseCentralPalette();
        System.out.println("Faction display colour regression suite passed.");
    }

    private static void canonicalPaletteIsDistinct() {
        check(FactionDisplayColorPolicy.playerName(FactionType.RED)
                        == FactionDisplayColorPolicy.NameColor.RED,
                "RED player names must remain red");
        check(FactionDisplayColorPolicy.playerName(FactionType.BLUE)
                        == FactionDisplayColorPolicy.NameColor.BLUE,
                "BLUE player names must remain blue");
        check(FactionDisplayColorPolicy.playerName(FactionType.NEUTRAL)
                        == FactionDisplayColorPolicy.NameColor.GOLD,
                "NEUTRAL player names must use the warm gold identity");
        check(FactionDisplayColorPolicy.playerName(FactionType.DARK)
                        == FactionDisplayColorPolicy.NameColor.DARK_GRAY,
                "DARK player names must remain dark gray");
        check(FactionDisplayColorPolicy.playerName(FactionType.NEUTRAL)
                        != FactionDisplayColorPolicy.playerName(FactionType.DARK),
                "NEUTRAL and DARK must never share a visually adjacent gray identity");
        check("§6".equals(FactionDisplayColorPolicy.legacyPlayerName(FactionType.NEUTRAL))
                        && "§8".equals(FactionDisplayColorPolicy.legacyPlayerName(FactionType.DARK)),
                "external TAB legacy colours must match the native palette");
    }

    private static void unknownMembershipStaysWhite() {
        check(FactionDisplayColorPolicy.playerName((FactionType) null)
                        == FactionDisplayColorPolicy.NameColor.WHITE,
                "missing faction membership must stay white, not inherit NEUTRAL gold");
        check(FactionDisplayColorPolicy.playerName("unknown")
                        == FactionDisplayColorPolicy.NameColor.WHITE,
                "unknown faction id must fail safe to white");
        check("§f".equals(FactionDisplayColorPolicy.legacyPlayerName("")),
                "blank external faction id must fail safe to white");
    }

    private static void allPlayerNameConsumersUseCentralPalette() throws Exception {
        final String tablist = read("src/main/java/hu/taliann/icesmp/managers/TablistManager.java");
        final String hud = read("src/main/java/hu/taliann/icesmp/managers/HudManager.java");
        final String chat = read("src/main/java/hu/taliann/icesmp/listeners/ChatFormatListener.java");
        final String placeholders = read("src/main/java/hu/taliann/icesmp/integration/IceSMPPlaceholders.java");

        check(tablist.contains("FactionDisplayPalette.playerName(faction)")
                        && hud.contains("FactionDisplayPalette.playerName(faction)")
                        && chat.contains("FactionDisplayPalette.playerName(snapshot.factionId())")
                        && placeholders.contains("FactionDisplayPalette.legacyPlayerName(snapshot.factionId())"),
                "tab, nametag, HUD, chat and PlaceholderAPI must share one palette authority");
        check(!tablist.contains("case NEUTRAL -> NamedTextColor.GRAY")
                        && !hud.contains("case NEUTRAL -> NamedTextColor.GRAY")
                        && !chat.contains("case \"NEUTRAL\" -> NamedTextColor.GRAY")
                        && !placeholders.contains("case \"NEUTRAL\" -> \"§7\""),
                "legacy gray NEUTRAL mappings must not survive in a player-name consumer");
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
