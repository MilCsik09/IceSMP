package hu.taliann.icesmp.quests;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Regression coverage for quest-NPC provenance, exact-name validation and manual provisioning. */
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
        deploymentSnapshotKeepsMissingPlacementExplicit();
        validationReportsExactConfigProvenanceAndCaseMismatch();
        adminCommandExposesTheValidationWithoutInventingCoordinates();
        System.out.println("Quest NPC validation regression suite passed.");
    }

    private static void packagedQuestsReferenceTheExpectedNpcContract() {
        final YamlConfiguration quests = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config/quests.yml"));
        final ConfigurationSection questRoot = quests.getConfigurationSection("quests");
        check(questRoot != null, "packaged quests root is missing");

        final Set<String> referenced = new LinkedHashSet<>();
        for (final Object value : questRoot.getValues(true).entrySet().stream()
                .filter(entry -> entry.getKey().equals("giver-npc")
                        || entry.getKey().endsWith(".giver-npc")
                        || entry.getKey().equals("npc")
                        || entry.getKey().endsWith(".npc"))
                .map(java.util.Map.Entry::getValue)
                .toList()) {
            if (value instanceof String name && !name.isBlank()) {
                referenced.add(name);
            }
        }
        check(referenced.equals(EXPECTED_REQUIRED_NPCS),
                "packaged quest NPC contract drifted: " + referenced);
    }

    private static void deploymentSnapshotKeepsMissingPlacementExplicit() {
        final YamlConfiguration snapshot = YamlConfiguration.loadConfiguration(
                new File("Other/plugins/FancyNpcs/npcs.yml"));
        final ConfigurationSection npcs = snapshot.getConfigurationSection("npcs");
        check(npcs != null, "packaged FancyNpcs deployment snapshot is missing");

        final Set<String> actualNames = new LinkedHashSet<>();
        for (final String key : npcs.getKeys(false)) {
            final String name = npcs.getString(key + ".name");
            if (name != null) {
                actualNames.add(name.toLowerCase(Locale.ROOT));
            }
        }
        final Set<String> placedRequired = new LinkedHashSet<>(EXPECTED_REQUIRED_NPCS);
        placedRequired.retainAll(actualNames);
        check(placedRequired.isEmpty(),
                "deployment snapshot changed; update the manual provisioning evidence: " + placedRequired);
    }

    private static void validationReportsExactConfigProvenanceAndCaseMismatch() throws Exception {
        final String manager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/QuestManager.java"));
        final String bridge = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/integration/FancyNpcsQuestBridge.java"));
        check(manager.contains("record QuestNpcReference")
                        && manager.contains("getQuestNpcReferences()")
                        && manager.contains("getCurrentPath()")
                        && manager.contains(".giver-npc")
                        && manager.contains(".npc"),
                "quest NPC references no longer include exact quest/config provenance");
        check(bridge.contains("getAllNpcs")
                        && bridge.contains("caseInsensitiveMatch")
                        && bridge.contains("expectedName")
                        && bridge.contains("formatReferences"),
                "FancyNpcs validation lost exact-name or case-mismatch diagnostics");
    }

    private static void adminCommandExposesTheValidationWithoutInventingCoordinates() throws Exception {
        final String command = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/QuestCommand.java"));
        final String bridge = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/integration/FancyNpcsQuestBridge.java"));
        check(command.contains("case \"validatenpcs\"")
                        && command.contains("/quest admin validatenpcs")
                        && command.contains("getQuestNpcReferences()"),
                "admin NPC validation command is no longer wired");
        check(bridge.contains("A koordináta és világ nem következtethető biztonságosan")
                        && !bridge.contains("new Location("),
                "validation must not invent NPC world coordinates");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
