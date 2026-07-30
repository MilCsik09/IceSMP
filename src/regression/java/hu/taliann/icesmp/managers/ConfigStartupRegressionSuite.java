package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        verifiesPackagedDefaults();
        verifiesSupportedFirstSpawnEvent();
        System.out.println("Config startup regression suite passed.");
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

    private static void verifiesPackagedDefaults() {
        final YamlConfiguration general = load("general.yml");
        final YamlConfiguration factions = load("factions.yml");
        final YamlConfiguration world = load("world.yml");
        final YamlConfiguration relics = load("relics.yml");
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

    private static YamlConfiguration load(final String name) {
        return YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/config", name).toFile());
    }

    private static <T extends Throwable> void expectThrows(final Class<T> type, final Runnable action) {
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
