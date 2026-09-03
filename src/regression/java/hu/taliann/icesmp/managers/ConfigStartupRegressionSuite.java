package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.gui.BlockRegenConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigMenuHelp;
import hu.taliann.icesmp.gui.ConfigMenuRootGUI;
import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Focused startup regressions for packaged configuration and strict profession parsing. */
public final class ConfigStartupRegressionSuite {
    private static final Logger LOGGER = Logger.getLogger(ConfigStartupRegressionSuite.class.getName());
    private static final Pattern LEGACY_CHAIN = Pattern.compile("\\bCHAIN\\b");

    private ConfigStartupRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        resolvesPersistedMaterialAliases();
        rejectsPartialProfessionRecipes();
        parsesEveryBundledProfessionIngredient();
        validatesPactUniqueMaterialReference();
        rejectsNonFiniteAndWrongTypeNumericConfig();
        validatesDurationListsElementByElement();
        verifiesPackagedDefaults();
        verifiesSupportedFirstSpawnEvent();
        verifiesConfigMenuCatalog();
        verifiesBlockRegenerationConfigMenu();
        verifiesConfigMenuResetAndLiveApply();
        suppressesOnlyNamedEntityDeathLogLines();
        System.out.println("Config startup regression suite passed.");
    }

    private static void suppressesOnlyNamedEntityDeathLogLines() {
        check(hu.taliann.icesmp.utils.NamedEntityDeathLogFilter.suppressible(
                        "Named entity ZombieEntity['Zombi Lv7'/312, l='world'] died: Zombi Lv7 leesett"),
                "formatted named-entity death line must match");
        check(hu.taliann.icesmp.utils.NamedEntityDeathLogFilter.suppressible(
                        "Named entity {} died: {}"),
                "parameterized (pre-format) named-entity death line must match");
        check(!hu.taliann.icesmp.utils.NamedEntityDeathLogFilter.suppressible(
                        "Named entity ZombieEntity['Zombi Lv7'/312] was saved"),
                "non-death named-entity line must pass through");
        check(!hu.taliann.icesmp.utils.NamedEntityDeathLogFilter.suppressible(
                        "Player Steve died: leesett"),
                "player death message must pass through");
        check(!hu.taliann.icesmp.utils.NamedEntityDeathLogFilter.suppressible(null),
                "null message must pass through");
    }

    private static void resolvesPersistedMaterialAliases() {
        check(ConfigMaterialResolver.match("CHAIN") == Material.IRON_CHAIN,
                "persisted CHAIN must resolve to IRON_CHAIN");
        check(ConfigMaterialResolver.match("minecraft:chain") == Material.IRON_CHAIN,
                "namespaced persisted CHAIN must resolve to IRON_CHAIN");
        check(ConfigMaterialResolver.match("DIAMOND") == Material.DIAMOND,
                "ordinary Bukkit materials must resolve unchanged");
    }

    private static void rejectsPartialProfessionRecipes() {
        final ProfessionIngredientParser.ParsedIngredients parsed = ProfessionIngredientParser.parse(
                List.of("IRON_INGOT:2", "unique:runapor:3"));
        check(parsed.materials().get(Material.IRON_INGOT) == 2
                        && parsed.uniqueMaterials().get("runapor") == 3,
                "valid mixed profession ingredients changed");
        expectThrows(IllegalArgumentException.class, () -> ProfessionIngredientParser.parse(
                List.of("IRON_INGOT:2", "NOT_A_REAL_MATERIAL:64")));
        expectThrows(IllegalArgumentException.class, () -> ProfessionIngredientParser.parse(
                List.of("IRON_INGOT:0")));
        expectThrows(IllegalArgumentException.class, () -> ProfessionIngredientParser.parse(
                List.of("unique::2")));
    }

    private static void parsesEveryBundledProfessionIngredient() throws Exception {
        final File recipeFile = Path.of("src/main/resources/config/profession-recipes.yml").toFile();
        final File materialFile = Path.of("src/main/resources/config/profession-materials.yml").toFile();
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(recipeFile);
        final YamlConfiguration materialYaml = YamlConfiguration.loadConfiguration(materialFile);
        final ConfigurationSection root = yaml.getConfigurationSection("profession-recipes");
        final ConfigurationSection materialRoot = materialYaml.getConfigurationSection("profession-materials");
        check(root != null && !root.getKeys(false).isEmpty(), "bundled profession recipes missing");
        check(materialRoot != null && !materialRoot.getKeys(false).isEmpty(),
                "bundled profession materials missing");

        for (final String id : materialRoot.getKeys(false)) {
            final ConfigurationSection definition = materialRoot.getConfigurationSection(id);
            check(definition != null, "profession material section missing: " + id);
            check(ConfigMaterialResolver.match(definition.getString("material", "")) != null,
                    "profession material has invalid Bukkit icon: " + id);
        }

        for (final String id : root.getKeys(false)) {
            final ConfigurationSection recipe = root.getConfigurationSection(id);
            check(recipe != null, "profession recipe section missing: " + id);
            final ConfigurationSection result = recipe.getConfigurationSection("result");
            check(result != null, "profession recipe result missing: " + id);
            final String uniqueResult = result.getString("unique", null);
            if (uniqueResult == null || uniqueResult.isBlank()) {
                check(ConfigMaterialResolver.match(result.getString("material", "")) != null,
                        "profession recipe has invalid Bukkit result: " + id);
            } else {
                check(materialRoot.isConfigurationSection(uniqueResult.toLowerCase(java.util.Locale.ROOT)),
                        "profession recipe has undefined unique result: " + id + " -> " + uniqueResult);
            }

            final ProfessionIngredientParser.ParsedIngredients parsed =
                    ProfessionIngredientParser.parse(recipe.getStringList("ingredients"));
            for (final String uniqueIngredient : parsed.uniqueMaterials().keySet()) {
                check(materialRoot.isConfigurationSection(uniqueIngredient),
                        "profession recipe has undefined unique ingredient: " + id
                                + " -> " + uniqueIngredient);
            }
        }

        final String recipes = Files.readString(recipeFile.toPath());
        final String materials = Files.readString(materialFile.toPath());
        check(!LEGACY_CHAIN.matcher(recipes).find() && !LEGACY_CHAIN.matcher(materials).find(),
                "bundled profession config still contains obsolete CHAIN");
    }

    private static void validatesPactUniqueMaterialReference() {
        LOGGER.setUseParentHandlers(false);
        final YamlConfiguration valid = new YamlConfiguration();
        valid.set("profession-materials.elso_csend_szilankja.material", "ECHO_SHARD");
        valid.set("pakt.material", "elso_csend_szilankja");
        check(ConfigValidator.validateConfiguration(valid, LOGGER) == 0,
                "valid pact unique-material reference must not be reported as Bukkit Material");

        final YamlConfiguration missingReference = new YamlConfiguration();
        missingReference.set("pakt.material", "nincs_ilyen");
        check(ConfigValidator.validateConfiguration(missingReference, LOGGER) == 1,
                "missing pact unique-material reference must be reported");

        final YamlConfiguration legacyMaterial = new YamlConfiguration();
        legacyMaterial.set("example.material", "CHAIN");
        check(ConfigValidator.validateConfiguration(legacyMaterial, LOGGER) == 0,
                "persisted CHAIN alias must not be reported when runtime resolves it");

        final YamlConfiguration invalidMaterial = new YamlConfiguration();
        invalidMaterial.set("example.material", "NOT_A_REAL_MATERIAL");
        check(ConfigValidator.validateConfiguration(invalidMaterial, LOGGER) == 1,
                "ordinary invalid Bukkit Material must still be reported");
    }

    private static void rejectsNonFiniteAndWrongTypeNumericConfig() {
        final YamlConfiguration invalid = new YamlConfiguration();
        invalid.set("factions.passives.red.fire-damage-multiplier", Double.NaN);
        invalid.set("factions.passives.dark.wither.duration-multiplier", Double.POSITIVE_INFINITY);
        invalid.set("factions.passives.blue.natural-exhaustion-save-chance", Double.NEGATIVE_INFINITY);
        invalid.set("factions.passives.dark.wild-undead.target-cancel-chance", "0.5");
        invalid.set("factions.passives.dark.ambient-undead.retaliation-seconds", "60");
        invalid.set("factions.whisper.night-undead-retaliation-seconds", 2.5D);
        invalid.set("factions.tax.rate-percent", "2.0");
        invalid.set("factions.tax.minimum-amount", Double.NaN);
        invalid.set("factions.switch.cost", "500");
        check(ConfigValidator.validateConfiguration(invalid, LOGGER) == 9,
                "wrong-type, NaN and infinite numeric config must be reported independently");

        final YamlConfiguration valid = new YamlConfiguration();
        valid.set("factions.passives.red.fire-damage-multiplier", 0.25D);
        valid.set("factions.passives.blue.natural-exhaustion-save-chance", 0.25D);
        valid.set("factions.passives.dark.ambient-undead.retaliation-seconds", 60);
        valid.set("factions.tax.rate-percent", 2.0D);
        check(ConfigValidator.validateConfiguration(valid, LOGGER) == 0,
                "valid finite numeric config was rejected");
    }

    private static void validatesDurationListsElementByElement() {
        final YamlConfiguration valid = new YamlConfiguration();
        valid.set("moderation.escalation-minutes", List.of(5, 30, 180, 1440));
        check(ConfigValidator.validateConfiguration(valid, LOGGER) == 0,
                "valid duration list must not be treated as a scalar");

        final YamlConfiguration invalid = new YamlConfiguration();
        invalid.set("moderation.escalation-minutes", List.of(5, -30, "180"));
        check(ConfigValidator.validateConfiguration(invalid, LOGGER) == 2,
                "duration lists must validate every element without accepting negative or string values");
    }

    private static void verifiesPackagedDefaults() {
        final YamlConfiguration general = load("general.yml");
        final YamlConfiguration factions = load("factions.yml");
        final YamlConfiguration world = load("world.yml");
        final YamlConfiguration relics = load("relics.yml");
        final YamlConfiguration blockRegen = load("block-regen.yml");
        check(general.getConfigurationSection("sit") == null,
                "general.yml must not duplicate sit.yml ownership");
        check(factions.getBoolean("factions.kings.dethrone-on-expiry"),
                "king expiry default missing");
        check(world.getInt("mob-scaling.hard-cap-level") == 15,
                "mob scaling hard cap default missing");
        check(world.getInt("world-events.orchestration.max-active-minutes") == 60,
                "major-event active-time cap default missing");
        check(relics.isConfigurationSection("relics.definitions.sarkany_tojas"),
                "dragon egg relic definition missing");
        check(blockRegen.getDouble(
                        "territory.protection.regen.debris-horizontal-multiplier") == 1.0D,
                "debris horizontal default missing");
        check(blockRegen.getDouble(
                        "territory.protection.regen.debris-vertical-multiplier") == 1.0D,
                "debris vertical default missing");
        check(blockRegen.getDouble(
                        "territory.protection.regen.debris-horizontal-spread") == 0.0D,
                "debris spread default changed");
        check(blockRegen.getBoolean(
                        "territory.protection.regen.debris-gravity-enabled"),
                "debris gravity default missing");
    }

    private static void verifiesSupportedFirstSpawnEvent() throws Exception {
        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/FactionSpawnListener.java"));
        check(listener.contains("AsyncPlayerSpawnLocationEvent")
                        && listener.contains("event.isNewPlayer()")
                        && !listener.contains("org.spigotmc.event.player.PlayerSpawnLocationEvent")
                        && !listener.contains("hasPlayedBefore()"),
                "first join must use the supported configuration-phase spawn event");
    }

    private static void verifiesConfigMenuCatalog() {
        check(ConfigMenuRootGUI.categoryCapacity() >= ConfigMenuGUI.CATEGORIES.size() + 5,
                "config root menu capacity is smaller than the current category catalog");
        check(ConfigMenuGUI.CATEGORIES.size() >= 25,
                "expanded admin config catalog lost categories");
        final Set<String> keys = new HashSet<>();
        for (final ConfigMenuGUI.Category category : ConfigMenuGUI.CATEGORIES.values()) {
            check(category.entries().size() <= 45,
                    "config category exceeds one 54-slot page: " + category.id());
            for (final ConfigMenuGUI.Entry entry : category.entries()) {
                check(keys.add(entry.key()), "duplicate config-menu key: " + entry.key());
                final String description = ConfigMenuHelp.describe(entry.key(), entry.label());
                check(description != null && description.length() >= 35,
                        "config-menu help is missing or too vague: " + entry.key());
            }
        }
        check(ConfigMenuGUI.CATEGORIES.containsKey("party"), "party config category missing");
        check(ConfigMenuGUI.CATEGORIES.containsKey("claimek"), "claim config category missing");
        check(ConfigMenuGUI.CATEGORIES.containsKey("megjelenes"), "chat/VFX category missing");
        check(ConfigMenuGUI.CATEGORIES.containsKey("adomany"), "donation category missing");
    }

    private static void verifiesBlockRegenerationConfigMenu() throws Exception {
        check(BlockRegenConfigMenuGUI.entryCount() == 38,
                "block-regeneration menu entry count changed unexpectedly");

        final List<String> requiredKeys = List.of(
                "territory.protection.regen.enabled",
                "claims.protect-explosions",
                "territory.protection.regen.zones.capital",
                "territory.protection.regen.zones.protected-city",
                "territory.protection.regen.zones.protected-faction",
                "territory.protection.regen.zones.dungeon",
                "territory.protection.regen.zones.doom-gate",
                "territory.protection.regen.zones.faction",
                "territory.protection.regen.zones.wilderness",
                "territory.protection.rules.capital.allow-explosions",
                "territory.protection.rules.protected-city.allow-explosions",
                "territory.protection.rules.protected-faction.allow-explosions",
                "territory.protection.rules.dungeon.allow-explosions",
                "territory.protection.rules.doom-gate.allow-explosions",
                "territory.protection.rules.faction.allow-explosions",
                "territory.protection.regen.delay-seconds",
                "territory.protection.regen.restore-interval-ticks",
                "territory.protection.regen.blocks-per-pass",
                "territory.protection.regen.support-grace-seconds",
                "territory.protection.regen.max-recaptures",
                "territory.protection.regen.recapture-window-seconds",
                "territory.protection.regen.physics-shield-enabled",
                "territory.protection.regen.physics-shield-seconds",
                "territory.protection.regen.player-break.siege-enabled",
                "territory.protection.regen.player-break.siege-delay-seconds",
                "territory.protection.regen.player-break.always-enabled",
                "territory.protection.regen.player-break.always-delay-seconds",
                "territory.protection.regen.restore-effects-enabled",
                "territory.protection.regen.tile-entity-explode",
                "territory.protection.regen.debris-enabled",
                "territory.protection.regen.debris-percent",
                "territory.protection.regen.debris-lifetime-seconds",
                "territory.protection.regen.debris-launch-power",
                "territory.protection.regen.debris-horizontal-multiplier",
                "territory.protection.regen.debris-vertical-multiplier",
                "territory.protection.regen.debris-horizontal-spread",
                "territory.protection.regen.debris-extra-upward-velocity",
                "territory.protection.regen.debris-gravity-enabled"
        );
        for (final String key : requiredKeys) {
            final ConfigMenuGUI.Entry entry = BlockRegenConfigMenuGUI.findEntry(key);
            check(entry != null, "block-regeneration menu key missing: " + key);
            check(ConfigMenuHelp.describe(key, entry.label()).length() >= 50,
                    "block-regeneration help is not exact enough: " + key);
            check(!BlockRegenConfigMenuGUI.requiresRestart(key),
                    "block-regeneration menu key still requires restart: " + key);
        }

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/BlockRegenService.java"));
        check(service.contains("dynamicTickerStarted")
                        && service.contains("acquireTickWindow")
                        && service.contains("debris-horizontal-multiplier")
                        && service.contains("debris-vertical-multiplier")
                        && service.contains("debris-horizontal-spread")
                        && service.contains("debris-extra-upward-velocity")
                        && service.contains("debris-gravity-enabled"),
                "live restore timing or debris trajectory wiring is missing");
    }

    private static void verifiesConfigMenuResetAndLiveApply() throws Exception {
        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ConfigMenuGUIListener.java"));
        check(listener.contains("event.getClick() == ClickType.MIDDLE")
                        && listener.contains("session.reset(key)")
                        && listener.contains("ConfigRuntimeReloadBridge.apply")
                        && !listener.contains("set-success-restart"),
                "middle-click reset or restart-free apply is missing");

        final String renderer = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/gui/ConfigMenuEntryRenderer.java"));
        check(renderer.contains("Alapérték:")
                        && renderer.contains("Jelenleg:")
                        && renderer.contains("config.yml felülbírálás")
                        && renderer.contains("Görgőkatt"),
                "config icon lore does not expose value, default, source and reset action");

        final String configManager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/ConfigManager.java"));
        check(configManager.contains("baseConfiguration")
                        && configManager.contains("mergePackagedDefaults")
                        && configManager.contains("resetOverride")
                        && configManager.contains("\"block-regen\""),
                "base-value layering or reset support is incomplete");

        final String bridge = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/core/ConfigRuntimeReloadBridge.java"));
        check(bridge.contains("relicManager.load()")
                        && bridge.contains("mobScalingManager")
                        && !bridge.contains("scheduleTaxCollection")
                        && !bridge.contains("nextDecayAt")
                        && !bridge.contains("food-duty"),
                "cached config systems are not applied live");
    }

    private static YamlConfiguration load(final String name) {
        return YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/config", name).toFile());
    }

    private static <T extends Throwable> void expectThrows(final Class<T> type,
                                                            final Runnable action) {
        try {
            action.run();
        } catch (final Throwable thrown) {
            if (type.isInstance(thrown)) {
                return;
            }
            throw new AssertionError("Expected " + type.getName() + " but got " + thrown, thrown);
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
