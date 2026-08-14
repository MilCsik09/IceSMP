package hu.taliann.icesmp.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source and pure-policy regressions for the Folia scoreboard fallback boundary. */
public final class PlatformCapabilitiesRegressionSuite {

    private PlatformCapabilitiesRegressionSuite() {
    }

    public static void main(final String[] args) throws IOException {
        check(PlatformCapabilities.supportsBukkitScoreboards(false),
                "Paper must retain the existing Bukkit scoreboard renderer");
        check(!PlatformCapabilities.supportsBukkitScoreboards(true),
                "Folia must never enter the unsupported Bukkit scoreboard API");

        final String hud = read("src/main/java/hu/taliann/icesmp/managers/HudManager.java");
        final String tab = read("src/main/java/hu/taliann/icesmp/managers/TablistManager.java");
        check(hud.contains("foliaCompactFallbackEnabled(final Player player)")
                        && hud.contains("applyFoliaCompactHud(player, snapshot)"),
                "Folia lost the native compact HUD fallback");
        check(hud.contains("!iceSmpHudActive(player)"),
                "first-party HUD readiness no longer suppresses the native Folia class HUD");
        check(hud.contains("isSectionHidden(player, SECTION_ALL) || !snapshot.hasClass()")
                        && !hud.contains("!sidebarVisibleFor(player) || !snapshot.hasClass()"),
                "compact Folia HUD visibility is incorrectly coupled to the disabled scoreboard renderer");
        check(tab.contains("if (!PlatformCapabilities.supportsBukkitScoreboards()) {\n            return;\n        }"),
                "Folia tablist path can reach unsupported scoreboard operations");
        System.out.println("Platform capability regression suite passed.");
    }

    private static String read(final String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
