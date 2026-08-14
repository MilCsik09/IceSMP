package hu.taliann.icesmp.quests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Regression coverage for exact quest-NPC validation and manual provisioning. */
public final class QuestNpcValidationRegressionSuite {

    private static final Set<String> EXPECTED_REQUIRED_NPCS = Set.of(
            "hirnok", "vandor_kereskedo", "erdei_venek", "harcos_mester",
            "ijasz_mester", "varazslo_mester", "orgyilkos_mester", "druida_mester",
            "paplovag_mester", "halallovag_mester", "saman_mester", "szerzetes_mester",
            "pap_mester", "pakt_mester", "demonvadasz_mester", "sarkany_mester",
            "kovacs_mester", "revesz"
    );

    private QuestNpcValidationRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        packagedQuestsReferenceTheExpectedNpcContract();
        bridgeReportsExactNamesCaseMismatchAndConfigProvenance();
        startupValidationDoesNotInventCoordinatesOrCommands();
        System.out.println("Quest NPC validation regression suite passed.");
    }

    private static void packagedQuestsReferenceTheExpectedNpcContract() throws Exception {
        final Set<String> referenced = collectYamlScalars(
                Path.of("src/main/resources/config/quests.yml"),
                Set.of("giver-npc", "npc"),
                -1
        );
        check(referenced.equals(EXPECTED_REQUIRED_NPCS),
                "packaged quest NPC contract drifted: " + referenced);
    }

    private static Set<String> collectYamlScalars(final Path path, final Set<String> keys,
                                                   final int requiredIndent) throws Exception {
        final List<String> lines = Files.readAllLines(path);
        final Set<String> values = new LinkedHashSet<>();
        for (final String line : lines) {
            final int indent = leadingSpaces(line);
            if (requiredIndent >= 0 && indent != requiredIndent) {
                continue;
            }
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            for (final String key : keys) {
                final String prefix = key + ":";
                if (!trimmed.startsWith(prefix)) {
                    continue;
                }
                final String value = unquote(trimmed.substring(prefix.length()).trim());
                if (!value.isBlank()) {
                    values.add(value);
                }
                break;
            }
        }
        return values;
    }

    private static int leadingSpaces(final String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private static String unquote(final String value) {
        if (value.length() >= 2) {
            final char first = value.charAt(0);
            final char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static void bridgeReportsExactNamesCaseMismatchAndConfigProvenance() throws Exception {
        final String bridge = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/integration/FancyNpcsQuestBridge.java"));
        check(bridge.contains("getAllNpcs")
                        && bridge.contains("caseInsensitiveMatch")
                        && bridge.contains("expectedName")
                        && bridge.contains("referencesFor")
                        && bridge.contains("getValues(true)")
                        && bridge.contains("giver-npc")
                        && bridge.contains("path.endsWith(\".npc\")"),
                "FancyNpcs validation lost exact-name, case-mismatch or config provenance diagnostics");
        check(bridge.contains("validateNpcs(final Set<String> questNpcNames)"),
                "startup validation is no longer compatible with the current QuestManager API");
    }

    private static void startupValidationDoesNotInventCoordinatesOrCommands() throws Exception {
        final String bridge = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/integration/FancyNpcsQuestBridge.java"));
        check(bridge.contains("A koordináta és világ nem következtethető biztonságosan")
                        && !bridge.contains("new Location("),
                "validation must not invent NPC world coordinates");
        check(!bridge.contains("registerCommand(")
                        && !bridge.contains("BasicCommand")
                        && !bridge.contains("questnpcs"),
                "quest diagnostics must not add an undocumented root command");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
