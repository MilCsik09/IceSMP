package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.OperationalConfigHelp;
import hu.taliann.icesmp.gui.OperationalConfigMenuGUI;
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

    private OperationalConfigMenuRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        verifiesCatalogAndPackagedKeys();
        verifiesRuntimeAlignedRanges();
        verifiesMenuWiringAndLiveApply();
        System.out.println("Operational config menu regression suite passed.");
    }

    @SuppressWarnings("unchecked")
    private static void verifiesCatalogAndPackagedKeys() throws Exception {
        check(OperationalConfigMenuGUI.categoryCount() == 5,
                "operational menu category count changed unexpectedly");
        check(OperationalConfigMenuGUI.entryCount() == 98,
                "operational menu entry count changed unexpectedly");

        final YamlConfiguration merged = new YamlConfiguration();
        for (final String file : new String[]{
                "general.yml", "tablist.yml", "afk.yml", "pets.yml",
                "economy.yml", "moderation.yml"}) {
            mergeInto(merged, YamlConfiguration.loadConfiguration(
                    Path.of("src/main/resources/config", file).toFile()));
        }

        final Field catalogField = OperationalConfigMenuGUI.class
                .getDeclaredField("CATEGORIES");
        catalogField.setAccessible(true);
        final Map<String, OperationalConfigMenuGUI.Category> categories =
                (Map<String, OperationalConfigMenuGUI.Category>) catalogField.get(null);
        check(new ArrayList<>(categories.keySet()).equals(
                        List.of("afk", "hud", "pets", "economy", "moderation")),
                "operational category order changed or categories disappeared");

        final Set<String> keys = new HashSet<>();
        for (final OperationalConfigMenuGUI.Category category : categories.values()) {
            check(category.entries().size() <= 45,
                    "operational category exceeds one page: " + category.id());
            for (final ConfigMenuGUI.Entry entry : category.entries()) {
                check(keys.add(entry.key()), "duplicate operational config key: " + entry.key());
                check(merged.isSet(entry.key()),
                        "operational menu points to a missing packaged key: " + entry.key());
                check(OperationalConfigHelp.describe(entry.key(), entry.label()).length() >= 40,
                        "operational help is missing or vague: " + entry.key());
            }
        }
        check(keys.size() == OperationalConfigMenuGUI.entryCount(),
                "operational key count differs from catalog count");
    }

    private static void verifiesRuntimeAlignedRanges() {
        check(minimum("currency.exchange-rate") == 0.01D,
                "fixed exchange-rate menu must reject the runtime-invalid zero value");
        check(minimum("currency.dynamic-exchange.min-multiplier") == 0.01D,
                "dynamic exchange floor does not match ExchangeRateService");
        check(minimum("currency.economy-event.min-multiplier") == 1.0D,
                "positive demand-shock floor does not match EconomyEventManager");
        check(minimum("currency.economy-event.panic-min-multiplier") == 0.1D,
                "panic multiplier floor does not match EconomyEventManager");
        check(minimum("currency.market-boom.duration-minutes") == 5.0D,
                "market-boom duration floor does not match EconomyEventManager");
    }

    private static double minimum(final String key) {
        final ConfigMenuGUI.Entry entry = OperationalConfigMenuGUI.findEntry(key);
        check(entry != null, "missing operational entry: " + key);
        return entry.min();
    }

    private static void verifiesMenuWiringAndLiveApply() throws Exception {
        final String root = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/gui/ConfigMenuRootGUI.java"));
        check(root.contains("OperationalConfigMenuGUI.ROOT_ACTION")
                        && root.contains("OperationalConfigSchemaGuard.validate")
                        && root.contains("AdvancedConfigSchemaGuard.validate")
                        && root.contains("ServerWorldConfigMenuGUI.ROOT_ACTION")
                        && root.contains("CrateConfigMenuGUI.ROOT_ACTION")
                        && root.contains("Üzemeltetés és finomhangolás")
                        && root.contains("ConfigMenuGUI.CATEGORIES.size() + 4"),
                "operational submenu or expanded config root wiring is missing");

        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ConfigMenuGUIListener.java"));
        check(listener.contains("OperationalConfigMenuGUI.CATEGORY_ACTION_PREFIX")
                        && listener.contains("OperationalConfigMenuGUI.findEntry")
                        && listener.contains("OperationalConfigMenuGUI.isOperationalCategory")
                        && listener.contains("OperationalConfigPolicy.validate")
                        && listener.contains("ConfigMenuEntryRenderer.defaultValue")
                        && listener.contains("resetOverride"),
                "operational click, constraint or default-reset wiring is missing");

        final String bridge = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/core/ConfigRuntimeReloadBridge.java"));
        check(bridge.contains("scheduleHud")
                        && bridge.contains("schedulePetCombat")
                        && bridge.contains("scheduleEconomyEvents")
                        && bridge.contains("refreshHudOutput")
                        && bridge.contains("removeHudSidebar")
                        && bridge.contains("refreshTablistOutput")
                        && bridge.contains("moderationManager")
                        && bridge.contains("vanishManager"),
                "fixed schedulers, visual cleanup or moderation cache are not applied live");

        final String build = Files.readString(Path.of("build.gradle.kts"));
        check(build.contains("operationalConfigMenuRegressionTest")
                        && build.contains("OperationalConfigMenuRegressionSuite"),
                "operational regression suite is not part of the Gradle verification graph");
    }

    private static void mergeInto(final YamlConfiguration target,
                                  final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                target.set(key, source.get(key));
            }
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
