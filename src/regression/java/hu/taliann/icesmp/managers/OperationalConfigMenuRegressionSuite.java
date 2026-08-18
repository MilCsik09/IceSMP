package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.gui.ConfigEditSession;
import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigStagedBatchValidator;
import hu.taliann.icesmp.gui.OperationalConfigHelp;
import hu.taliann.icesmp.gui.OperationalConfigMenuGUI;
import hu.taliann.icesmp.gui.OperationalConfigPolicy;
import hu.taliann.icesmp.gui.TransactionalOperationalConfigMenuGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Focused source/config contract for the operational admin menu. */
public final class OperationalConfigMenuRegressionSuite {

    private OperationalConfigMenuRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        verifiesCatalogAndPackagedKeys();
        verifiesRuntimeAlignedRanges();
        verifiesStagedBatchValidation();
        verifiesMenuWiringAndLiveApply();
        System.out.println("Operational config menu regression suite passed.");
    }

    @SuppressWarnings("unchecked")
    private static void verifiesCatalogAndPackagedKeys() throws Exception {
        check(OperationalConfigMenuGUI.categoryCount() == 5, "source catalog category count changed");
        check(OperationalConfigMenuGUI.entryCount() == 107, "source catalog entry count changed");
        check(TransactionalOperationalConfigMenuGUI.categoryCount() == 4,
                "canonical operational view must hide duplicate moderation category");
        check(TransactionalOperationalConfigMenuGUI.entryCount() == 96,
                "canonical operational entry count changed unexpectedly");

        final YamlConfiguration merged = new YamlConfiguration();
        for (final String file : new String[]{"general.yml", "tablist.yml", "afk.yml", "pets.yml",
                "economy.yml", "moderation.yml"}) {
            mergeInto(merged, YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config", file).toFile()));
        }
        final Field catalogField = OperationalConfigMenuGUI.class.getDeclaredField("CATEGORIES");
        catalogField.setAccessible(true);
        final Map<String, OperationalConfigMenuGUI.Category> categories =
                (Map<String, OperationalConfigMenuGUI.Category>) catalogField.get(null);
        check(new ArrayList<>(categories.keySet()).equals(List.of("afk", "hud", "pets", "economy", "moderation")),
                "source operational category order changed");
        final Set<String> keys = new HashSet<>();
        for (final OperationalConfigMenuGUI.Category category : categories.values()) {
            check(category.entries().size() <= 45, "operational category exceeds one page: " + category.id());
            for (final ConfigMenuGUI.Entry entry : category.entries()) {
                check(keys.add(entry.key()), "duplicate operational config key: " + entry.key());
                check(merged.isSet(entry.key()), "missing packaged operational key: " + entry.key());
                check(OperationalConfigHelp.describe(entry.key(), entry.label()).length() >= 40,
                        "operational help is missing or vague: " + entry.key());
            }
        }
    }

    private static void verifiesRuntimeAlignedRanges() {
        check(minimum("hud.icesmp-hud.target-frame.range") == 3.0D
                        && maximum("hud.icesmp-hud.target-frame.range") == 64.0D,
                "Target Frame raytrace range must match the runtime clamp");
        check(minimum("currency.exchange-rate") == 0.01D, "fixed exchange-rate minimum mismatch");
        check(minimum("currency.dynamic-exchange.min-multiplier") == 0.01D, "dynamic exchange floor mismatch");
        check(minimum("currency.economy-event.min-multiplier") == 1.0D, "positive shock floor mismatch");
        check(minimum("currency.economy-event.panic-min-multiplier") == 0.1D, "panic floor mismatch");
        check(minimum("currency.market-boom.duration-minutes") == 5.0D, "market boom duration floor mismatch");
    }

    private static double minimum(final String key) {
        final ConfigMenuGUI.Entry entry = OperationalConfigMenuGUI.findEntry(key);
        check(entry != null, "missing operational entry: " + key);
        return entry.min();
    }

    private static double maximum(final String key) {
        final ConfigMenuGUI.Entry entry = OperationalConfigMenuGUI.findEntry(key);
        check(entry != null, "missing operational entry: " + key);
        return entry.max();
    }

    private static void verifiesStagedBatchValidation() {
        final String minimumKey = "currency.dynamic-exchange.min-multiplier";
        final String maximumKey = "currency.dynamic-exchange.max-multiplier";
        final Map<String, Object> opening = Map.of(minimumKey, 0.25D, maximumKey, 4.0D);
        final ConfigEditSession session = new ConfigEditSession(1L, "fingerprint", opening, opening);
        final ConfigManager configManager = new ConfigManager(null);

        final Object stagedMinimum = 0.8D;
        check(OperationalConfigPolicy.validate(minimumKey, stagedMinimum, configManager,
                        session.candidate(minimumKey, stagedMinimum)) == null,
                "individually valid staged minimum was rejected");
        session.stage(minimumKey, stagedMinimum);

        final Object invalidMaximum = 0.5D;
        final String immediateProblem = OperationalConfigPolicy.validate(maximumKey, invalidMaximum,
                configManager, session.candidate(maximumKey, invalidMaximum));
        check(immediateProblem != null && immediateProblem.contains("alsó korlátnál"),
                "second field was not validated against the staged minimum");

        session.stage(maximumKey, invalidMaximum);
        final String batchProblem = ConfigStagedBatchValidator.validate(session.snapshot(), configManager);
        check(batchProblem != null && batchProblem.contains(maximumKey),
                "invalid staged min/max batch could reach publication");
        check(session.pendingChanges().size() == 2,
                "failed batch validation discarded the staged transaction");

        final String rhythmMinimum = "classes.shaman.maelstrom.rhythm-min-millis";
        final String rhythmMaximum = "classes.shaman.maelstrom.rhythm-max-millis";
        final Map<String, Object> classOpening = Map.of(rhythmMinimum, 600, rhythmMaximum, 1600);
        final ConfigEditSession classSession = new ConfigEditSession(
                2L, "class-fingerprint", classOpening, classOpening);
        classSession.stage(rhythmMinimum, 1800);
        final String classImmediateProblem = OperationalConfigPolicy.validate(
                rhythmMaximum, 1700, configManager, classSession.candidate(rhythmMaximum, 1700));
        check(classImmediateProblem != null && classImmediateProblem.contains("alsó korlátnál"),
                "class-gameplay field validation ignored the staged paired bound");
        classSession.stage(rhythmMaximum, 1700);
        final String classBatchProblem = ConfigStagedBatchValidator.validate(
                classSession.snapshot(), configManager);
        check(classBatchProblem != null && classBatchProblem.contains(rhythmMinimum),
                "invalid class-gameplay min/max batch could reach publication");
    }

    private static void verifiesMenuWiringAndLiveApply() throws Exception {
        final String root = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/gui/ConfigMenuRootGUI.java"));
        check(root.contains("TransactionalOperationalConfigMenuGUI.categoryCount")
                        && root.contains("AdvancedConfigSchemaGuard.validate")
                        && root.contains("ServerWorldConfigMenuGUI.ROOT_ACTION")
                        && root.contains("CrateConfigMenuGUI.ROOT_ACTION")
                        && root.contains("ConfigMenuGUI.CATEGORIES.size() + 5"),
                "expanded staged config root wiring is missing");

        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ConfigMenuGUIListener.java"));
        check(listener.contains("ConfigEditSession")
                        && listener.contains("TransactionalOperationalConfigMenuGUI.openCategory")
                        && listener.contains("OperationalConfigPolicy.validate")
                        && listener.contains("ConfigStagedBatchValidator.validate")
                        && listener.contains("session.candidate")
                        && listener.contains("session.stage")
                        && listener.contains("session.reset")
                        && listener.contains("applyOverridesIfUnchanged"),
                "operational staged click/reset/save wiring is missing");

        final String bridge = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/core/ConfigRuntimeReloadBridge.java"));
        check(bridge.contains("scheduleHud") && bridge.contains("schedulePetCombat")
                        && bridge.contains("scheduleEconomyEvents") && bridge.contains("refreshHudOutput")
                        && bridge.contains("refreshTablistOutput") && bridge.contains("moderationManager"),
                "operational live hooks are incomplete");
        final String build = Files.readString(Path.of("build.gradle.kts"));
        check(build.contains("operationalConfigMenuRegressionTest")
                        && build.contains("OperationalConfigMenuRegressionSuite"),
                "operational regression suite is not in Gradle check");
    }

    private static void mergeInto(final YamlConfiguration target, final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) if (!source.isConfigurationSection(key)) target.set(key, source.get(key));
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
