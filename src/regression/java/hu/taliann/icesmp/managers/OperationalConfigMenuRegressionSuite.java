package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.OperationalConfigHelp;
import hu.taliann.icesmp.gui.OperationalConfigMenuGUI;
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
        verifiesMenuWiringAndLiveApply();
        System.out.println("Operational config menu regression suite passed.");
    }

    @SuppressWarnings("unchecked")
    private static void verifiesCatalogAndPackagedKeys() throws Exception {
        check(OperationalConfigMenuGUI.categoryCount() == 5, "source catalog category count changed");
        check(OperationalConfigMenuGUI.entryCount() == 104, "source catalog entry count changed");
        check(TransactionalOperationalConfigMenuGUI.categoryCount() == 4,
                "canonical operational view must hide duplicate moderation category");
        check(TransactionalOperationalConfigMenuGUI.entryCount() == 93,
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

    private static void verifiesMenuWiringAndLiveApply() throws Exception {
        final String root = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/gui/ConfigMenuRootGUI.java"));
        check(root.contains("TransactionalOperationalConfigMenuGUI.categoryCount")
                        && root.contains("AdvancedConfigSchemaGuard.validate")
                        && root.contains("ServerWorldConfigMenuGUI.ROOT_ACTION")
                        && root.contains("CrateConfigMenuGUI.ROOT_ACTION")
                        && root.contains("ConfigMenuGUI.CATEGORIES.size() + 4"),
                "expanded staged config root wiring is missing");

        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ConfigMenuGUIListener.java"));
        check(listener.contains("ConfigEditSession")
                        && listener.contains("TransactionalOperationalConfigMenuGUI.openCategory")
                        && listener.contains("OperationalConfigPolicy.validate")
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
