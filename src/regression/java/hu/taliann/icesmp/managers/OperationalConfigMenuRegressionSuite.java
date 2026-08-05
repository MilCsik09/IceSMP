package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.OperationalConfigHelp;
import hu.taliann.icesmp.gui.OperationalConfigMenuGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Focused source/config contract for the operational admin menu. */
public final class OperationalConfigMenuRegressionSuite {

    private OperationalConfigMenuRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        verifiesCatalogAndPackagedKeys();
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
        check(categories.keySet().equals(Set.of("afk", "hud", "pets", "economy", "moderation")),
                "operational categories changed or disappeared");

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

    private static void verifiesMenuWiringAndLiveApply() throws Exception {
        final String root = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/gui/ConfigMenuRootGUI.java"));
        check(root.contains("OperationalConfigMenuGUI.ROOT_ACTION")
                        && root.contains("Üzemeltetés és finomhangolás")
                        && root.contains("ConfigMenuGUI.CATEGORIES.size() + 2"),
                "operational submenu is not linked from the config root");

        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ConfigMenuGUIListener.java"));
        check(listener.contains("OperationalConfigMenuGUI.CATEGORY_ACTION_PREFIX")
                        && listener.contains("OperationalConfigMenuGUI.findEntry")
                        && listener.contains("OperationalConfigMenuGUI.isOperationalCategory")
                        && listener.contains("resetOverride"),
                "operational click, reopen or default-reset wiring is missing");

        final String bridge = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/core/ConfigRuntimeReloadBridge.java"));
        check(bridge.contains("scheduleHud")
                        && bridge.contains("schedulePetCombat")
                        && bridge.contains("scheduleEconomyEvents")
                        && bridge.contains("moderationManager")
                        && bridge.contains("vanishManager"),
                "fixed schedulers or moderation cache are not applied live");
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
