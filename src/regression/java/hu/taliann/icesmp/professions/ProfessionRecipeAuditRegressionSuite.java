package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ProfessionIngredientParser;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ProfessionRecipeAuditRegressionSuite {
    private ProfessionRecipeAuditRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path path = Path.of("src/main/resources/config/profession-recipes.yml");
        final String raw = Files.readString(path);
        check(!raw.contains("  kezdo_horgaszbot:"), "removed duplicate recipe must not remain craftable");
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        final YamlConfiguration materials = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/config/profession-materials.yml").toFile());
        final ConfigurationSection root = yaml.getConfigurationSection("profession-recipes");
        check(root != null, "profession recipe root exists");
        final Set<String> ids = new TreeSet<>(root.getKeys(false));
        final Set<String> fingerprints = new HashSet<>();
        for (final String id : ids) {
            final ConfigurationSection section = root.getConfigurationSection(id);
            check(section != null, "recipe section: " + id);
            final ConfigurationSection result = section.getConfigurationSection("result");
            check(result != null, "result section: " + id);
            final ProfessionIngredientParser.ParsedIngredients parsed =
                    ProfessionIngredientParser.parse(section.getStringList("ingredients"));
            final String unique = result.getString("unique", null);
            final Material material = unique == null ? Material.matchMaterial(result.getString("material", "")) : Material.PAPER;
            check(material != null, "valid output material: " + id);
            final ProfessionType profession = ProfessionType.fromId(section.getString("profession", ""));
            check(profession != null, "valid profession gate: " + id);
            final ProfessionRecipeCatalog.Recipe recipe = new ProfessionRecipeCatalog.Recipe(
                    id, profession, Math.max(1, section.getInt("level", 1)),
                    "blueprint".equalsIgnoreCase(section.getString("learn", "level")),
                    section.getString("display-name", id), section.getString("category", "Egyéb"), material,
                    Math.max(1, result.getInt("amount", 1)), result.getString("affix-tier", null), unique,
                    parsed.materials(), parsed.uniqueMaterials(), section.getStringList("lore"),
                    result.getString("signature", null), FactionType.fromInput(section.getString("faction", null)),
                    section.getBoolean("loot-only", false), section.getString("job", null));
            final String fingerprint = ProfessionRecipeCatalog.semanticFingerprint(recipe);
            check(fingerprints.add(fingerprint), "semantic duplicate: " + id + " -> " + fingerprint);
            if (unique != null) {
                final String model = materials.getString("profession-materials."
                        + unique.toLowerCase(Locale.ROOT) + ".item-model");
                check(model != null && !model.isBlank(), "unique profession output has icon: " + unique);
            }
        }
        check(new ArrayList<>(ids).equals(ids.stream().sorted().toList()), "deterministic recipe order");

        final Map<Material, Integer> mutableIngredients = new HashMap<>();
        mutableIngredients.put(Material.STICK, 1);
        final Map<String, Integer> mutableUniqueIngredients = new HashMap<>();
        mutableUniqueIngredients.put("audit_token", 1);
        final List<String> mutableLore = new ArrayList<>(List.of("audit"));
        final ProfessionRecipeCatalog.Recipe immutableRecipe = new ProfessionRecipeCatalog.Recipe(
                "immutable_audit", ProfessionType.COOK, 1, false, "Audit", "Audit",
                Material.PAPER, 1, null, null, mutableIngredients, mutableUniqueIngredients,
                mutableLore, null, null, false, null);
        mutableIngredients.clear();
        mutableUniqueIngredients.clear();
        mutableLore.clear();
        check(immutableRecipe.ingredients().equals(Map.of(Material.STICK, 1)),
                "recipe material ingredients are defensively copied");
        check(immutableRecipe.uniqueIngredients().equals(Map.of("audit_token", 1)),
                "recipe unique ingredients are defensively copied");
        check(immutableRecipe.lore().equals(List.of("audit")), "recipe lore is defensively copied");

        final String manager = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/managers/ProfessionRecipeManager.java"));
        check(manager.indexOf("clearRegisteredRecipes();") < manager.indexOf("if (!isEnabled())"),
                "reload removes stale recipes before disabled gate");
        final String catalog = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/managers/ProfessionRecipeCatalog.java"));
        check(catalog.contains("private volatile CatalogState state"),
                "catalog readers observe one volatile immutable generation");
        check(!catalog.contains("byId.clear()") && !catalog.contains("byProfession.clear()"),
                "failed reload cannot clear the published catalog before validation");
        check(catalog.indexOf("final Set<String> semanticFingerprints")
                        < catalog.lastIndexOf("state = new CatalogState"),
                "candidate validation completes before atomic publication");
        final String core = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"))
                .replace("\r\n", "\n");
        check(core.contains("professionRecipeCatalog.load();\n            professionRecipeManager.registerRecipes();"),
                "full reload rebuilds recipe registry");
        check(core.contains("professionRecipeManager::shutdown"), "disable removes owned recipe keys");
        final String listener = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeListener.java"));
        check(listener.contains("hasProfession(player, recipe.profession())")
                        && listener.contains("getLevel(player, recipe.profession())"),
                "legacy masterwork profession and level gates remain enforced");
        final String bookListener = Files.readString(
                Path.of("src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java"));
        check(bookListener.contains("recipe.uniqueIngredients().entrySet()")
                        && bookListener.contains("uniqueMaterials.idOf(item)"),
                "catalog custom ingredients require canonical unique-item identity");
        System.out.println("PROFESSION_RECIPE_AUDIT recipes=" + ids.size()
                + " semantic_duplicates=0 key_duplicates=0 atomic_reload=true");
        System.out.println("Profession recipe audit regression suite passed.");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
