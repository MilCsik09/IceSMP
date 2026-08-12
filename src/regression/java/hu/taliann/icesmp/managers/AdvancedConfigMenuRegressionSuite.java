package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.crates.CrateRules;
import hu.taliann.icesmp.gui.AdvancedConfigEntry;
import hu.taliann.icesmp.gui.CrateConfigMenuGUI;
import hu.taliann.icesmp.gui.ServerWorldConfigMenuGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Second-wave menu schema, input-session, crate and live-apply regressions. */
public final class AdvancedConfigMenuRegressionSuite {

    private AdvancedConfigMenuRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        verifiesServerWorldCatalog();
        verifiesCrateCatalogAndRewards();
        verifiesSafeInputAndLiveApplyWiring();
        verifiesCrateRuleBoundaries();
        System.out.println("Advanced config menu regression suite passed.");
    }

    private static void verifiesServerWorldCatalog() {
        check(ServerWorldConfigMenuGUI.entryCount() == 17,
                "server/world advanced entry count changed unexpectedly");
        final YamlConfiguration merged = merged("general.yml", "world.yml", "moderation.yml");
        final Set<String> keys = new HashSet<>();
        int textEntries = 0;
        int listEntries = 0;
        for (final AdvancedConfigEntry entry : ServerWorldConfigMenuGUI.entries()) {
            check(keys.add(entry.key()), "duplicate server/world advanced key: " + entry.key());
            check(merged.isSet(entry.key()), "missing packaged advanced key: " + entry.key());
            check(entry.description().length() >= 50,
                    "advanced entry description is too vague: " + entry.key());
            if (entry.type() == AdvancedConfigEntry.Type.TEXT) {
                textEntries++;
            } else if (entry.type() == AdvancedConfigEntry.Type.STRING_LIST) {
                listEntries++;
            }
        }
        check(textEntries >= 4, "safe text editor lost required text fields");
        check(listEntries >= 5, "safe list editor lost required list fields");
        check(ServerWorldConfigMenuGUI.findEntry("world-events.intro.lines") != null,
                "intro line list editor missing");
        check(ServerWorldConfigMenuGUI.findEntry("moderation.chat-filter.words") != null,
                "moderation word-list editor missing");
    }

    private static void verifiesCrateCatalogAndRewards() {
        final YamlConfiguration crates = merged("crates.yml");
        check(crates.getBoolean("crates-settings.enabled"),
                "packaged native crate master toggle missing");
        check(!crates.isSet("crates-settings.spin-animation"),
                "retired inventory spin toggle must not return to the packaged crate schema");
        final ConfigurationSection root = crates.getConfigurationSection("crates");
        check(root != null && !root.getKeys(false).isEmpty(), "packaged crates missing");

        for (final String crateId : root.getKeys(false)) {
            final List<AdvancedConfigEntry> entries = CrateConfigMenuGUI.entriesFor(crateId);
            check(entries.size() == 18, "crate base editor entry count changed: " + crateId);
            for (final AdvancedConfigEntry entry : entries) {
                check(crates.isSet(entry.key()),
                        "crate base editor points to missing packaged key: " + entry.key());
            }
            final Object rewards = crates.get("crates." + crateId + ".rewards");
            check(rewards instanceof List<?> list && !list.isEmpty(),
                    "packaged crate reward list missing: " + crateId);
        }
    }

    private static void verifiesSafeInputAndLiveApplyWiring() throws Exception {
        final String root = read("src/main/java/hu/taliann/icesmp/gui/ConfigMenuRootGUI.java");
        check(root.contains("ConfigMenuGUI.CATEGORIES.size() + 5")
                        && root.contains("ServerWorldConfigMenuGUI.ROOT_ACTION")
                        && root.contains("CrateConfigMenuGUI.ROOT_ACTION")
                        && root.contains("AdvancedConfigSchemaGuard.validate"),
                "advanced menus or schema guard are missing from config root");

        final String listener = read(
                "src/main/java/hu/taliann/icesmp/listeners/ConfigMenuGUIListener.java");
        check(listener.contains("AsyncChatEvent")
                        && listener.contains("INPUT_TIMEOUT_MILLIS = 120_000L")
                        && listener.contains("raw.split(\"\\\\s*;;\\\\s*\")")
                        && listener.contains("!cancel")
                        && listener.contains("!default")
                        && listener.contains("!empty")
                        && listener.contains("EventPriority.LOWEST")
                        && listener.contains("ConfigChatInputGate.open")
                        && listener.contains("ConfigChatInputGate.close")
                        && listener.contains("player.getScheduler().run")
                        && listener.contains("CrateRewardEditor.path")
                        && listener.contains("AdvancedConfigRuntimeBridge.apply"),
                "safe private input, Folia write hop or full reward override is missing");

        final String moderation = read(
                "src/main/java/hu/taliann/icesmp/listeners/ChatModerationListener.java");
        check(moderation.contains("ConfigChatInputGate.isOpen")
                        && moderation.contains("privát admin-input")
                        && moderation.contains("return;"),
                "moderation may still censor or log private config input");

        final String renderer = read(
                "src/main/java/hu/taliann/icesmp/gui/AdvancedConfigEntryRenderer.java");
        check(renderer.contains("chatben &f;;")
                        && renderer.contains("Alapérték:")
                        && renderer.contains("Görgőkatt/Q"),
                "advanced editor lore does not expose delimiter, default and reset");

        final String editor = read(
                "src/main/java/hu/taliann/icesmp/gui/CrateRewardEditor.java");
        check(editor.contains("copy-on-write")
                        && editor.contains("CrateRules.validateCommand")
                        && editor.contains("legalább egy reward")
                        && editor.contains("crates.\" + crateId + \".rewards"),
                "crate reward editor lost full-list or validation invariants");

        final String bridge = read(
                "src/main/java/hu/taliann/icesmp/core/AdvancedConfigRuntimeBridge.java");
        check(bridge.contains("reloadConfig")
                        && bridge.contains("scheduleWorldEvents")
                        && bridge.contains("worldEventsTask")
                        && bridge.contains("applyLocatorBar")
                        && bridge.contains("setGameRule"),
                "crate reload, world-event reschedule or locator-bar live apply is missing");

        final String build = read("build.gradle.kts");
        check(build.contains("advancedConfigMenuRegressionTest")
                        && build.contains("AdvancedConfigMenuRegressionSuite")
                        && build.contains("operationalConfigMenuRegressionTest, advancedConfigMenuRegressionTest"),
                "advanced config regression is not part of Gradle check");
    }

    private static void verifiesCrateRuleBoundaries() {
        check(CrateRules.normalizeId(" Ritka_Lada ").equals("ritka_lada"),
                "crate id normalization changed");
        check(CrateRules.validateCommand("xp add {player} 10 levels")
                        .equals("xp add {player} 10 levels"),
                "valid crate command was rejected");
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.validateCommand("/op {player}"));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.validateCommand("give {unknown} diamond"));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.itemAmount(0, 1));
    }

    private static YamlConfiguration merged(final String... files) {
        final YamlConfiguration target = new YamlConfiguration();
        for (final String file : files) {
            final YamlConfiguration source = YamlConfiguration.loadConfiguration(
                    Path.of("src/main/resources/config", file).toFile());
            for (final String key : source.getKeys(true)) {
                if (!source.isConfigurationSection(key)) {
                    target.set(key, source.get(key));
                }
            }
        }
        return target;
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void expectThrows(final Class<? extends Throwable> type,
                                     final Runnable action) {
        try {
            action.run();
        } catch (final Throwable failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError("Unexpected exception type: " + failure, failure);
        }
        throw new AssertionError("Expected exception: " + type.getName());
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
