package hu.taliann.icesmp.dialog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free contract checks for the JAR datapack website dialog. */
public final class PauseMenuDialogRegressionSuite {

    private static final Path DIALOG = Path.of(
            "src/main/resources/datapack/data/icesmp/dialog/website.json");
    private static final Path PAUSE_TAG = Path.of(
            "src/main/resources/datapack/data/minecraft/tags/dialog/pause_screen_additions.json");

    private PauseMenuDialogRegressionSuite() {
    }

    public static void main(final String[] args) throws IOException {
        final String dialog = compact(Files.readString(DIALOG));
        final String pauseTag = compact(Files.readString(PAUSE_TAG));
        final String bootstrap = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/IceSMPBootstrap.java"));

        check(dialog.contains("\"type\":\"minecraft:notice\""),
                "a weboldal-dialog nem minecraft:notice típusú");
        check(dialog.contains("\"external_title\":{\"text\":\"Weboldal\""),
                "az ESC-menü közvetlen Weboldal felirata hiányzik");
        check(dialog.contains("\"type\":\"open_url\",\"url\":\"https://icesmp.taliann.dev\""),
                "a weboldalgomb nem a kanonikus HTTPS címre mutat");
        check(dialog.contains("\"can_close_with_escape\":true")
                        && dialog.contains("\"pause\":false")
                        && dialog.contains("\"after_action\":\"close\""),
                "a dialog bezárási/pause szerződése hibás");

        check(pauseTag.contains("\"replace\":false"),
                "a pause-screen tag nem interoperábilis additív módban működik");
        check(pauseTag.contains("\"values\":[\"icesmp:website\"]"),
                "az IceSMP weboldal-dialog nincs egyedüli közvetlen ESC-célként regisztrálva");
        check(bootstrap.contains("!/datapack") && bootstrap.contains("discoverPack(packUri, \"icesmp\""),
                "a JAR datapack felderítési útja hiányzik");

        System.out.println("Pause-menu dialog regression suite passed.");
    }

    private static String compact(final String json) {
        return json.replaceAll("\\s+", "");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
