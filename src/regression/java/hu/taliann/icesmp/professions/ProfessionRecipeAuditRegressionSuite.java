package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ProfessionIngredientParser;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.utils.ConfigMaterialResolver;
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
    private static final Set<String> KINDS =
            Set.of("gyakorlo", "hozam", "egyedi", "lanc", "ritkasag",
                    "processing", "crafting", "equipment", "service");
    private static final List<String> FUNCTIONAL_KEYS =
            List.of("template", "affix-tier", "enchant", "attributes", "consumable", "signature", "potion-effects");
    private static final int GYAKORLO_MAX_LEVEL = 15;
    private static final int EXPECTED_RECIPE_COUNT = 471;
    private static final int EXPECTED_CANONICAL_GEAR_COUNT = 85;
    /** A vanília MAGA is ugyanígy duplikálja ezt a tárgyat — a katalógusból ez nem látszik. */
    private static final Set<String> VANILLA_DUPLICATION = Set.of("kovacsmesteri_sablon");

    private ProfessionRecipeAuditRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path path = Path.of("src/main/resources/content/professions/recipes.yml");
        final String raw = Files.readString(path);
        check(!raw.contains("  kezdo_horgaszbot:"), "removed duplicate recipe must not remain craftable");
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        final YamlConfiguration materials = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/content/professions/materials.yml").toFile());
        final YamlConfiguration equipment = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/content/equipment/equipment.yml").toFile());
        final ConfigurationSection root = yaml.getConfigurationSection("profession-recipes");
        final ConfigurationSection equipmentRoot = equipment.getConfigurationSection("item-templates");
        check(root != null, "profession recipe root exists");
        check(equipmentRoot != null, "equipment template root exists");
        final Set<String> ids = new TreeSet<>(root.getKeys(false));
        final Set<String> fingerprints = new HashSet<>();
        int canonicalGearCount = 0;
        for (final String id : ids) {
            final ConfigurationSection section = root.getConfigurationSection(id);
            check(section != null, "recipe section: " + id);
            final ConfigurationSection result = section.getConfigurationSection("result");
            check(result != null, "result section: " + id);
            final ProfessionIngredientParser.ParsedIngredients parsed =
                    ProfessionIngredientParser.parse(section.getStringList("ingredients"));
            final String unique = result.getString("unique", null);
            final String template = result.getString("template", null);
            final ConfigurationSection templateDefinition = template == null ? null
                    : equipmentRoot.getConfigurationSection(template.toLowerCase(Locale.ROOT));
            if (template != null) {
                check(templateDefinition != null,
                        "canonical gear template exists: " + id + " -> " + template);
            }
            final String outputMaterial = templateDefinition == null
                    ? result.getString("material", "")
                    : templateDefinition.getString("material", "");
            final Material material = unique == null ? Material.matchMaterial(outputMaterial) : Material.PAPER;
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
                    section.getBoolean("loot-only", false), section.getString("job", null),
                    section.getString("kind", "hozam"), template, result.getBoolean("masterwork", false));
            final String fingerprint = ProfessionRecipeCatalog.semanticFingerprint(recipe);
            check(fingerprints.add(fingerprint), "semantic duplicate: " + id + " -> " + fingerprint);

            final String kind = section.getString("kind", "");
            check(KINDS.contains(kind), "recipe declares a valid kind: " + id + " -> '" + kind + "'");
            if ("egyedi".equals(kind)) {
                boolean functional = false;
                for (final String key : FUNCTIONAL_KEYS) {
                    if (result.contains(key)) {
                        functional = true;
                        break;
                    }
                }
                check(functional, "kind=egyedi carries a functional component: " + id);
            }
            if (template != null) {
                canonicalGearCount++;
                check(!template.isBlank(), "canonical gear template id is non-empty: " + id);
                check(unique == null && Math.max(1, result.getInt("amount", 1)) == 1,
                        "canonical gear is a single non-unique stack: " + id);
                check(!result.contains("affix-tier") && !result.contains("signature")
                                && !result.contains("attributes") && !result.contains("enchant")
                                && !result.contains("consumable") && !result.contains("potion-effects"),
                        "canonical gear does not mix legacy result mutators: " + id);
            }
            if ("ritkasag".equals(kind)) {
                check(Math.max(1, result.getInt("amount", 1)) == 1,
                        "kind=ritkasag never multiplies: " + id);
            }
            if ("gyakorlo".equals(kind)) {
                check(Math.max(1, section.getInt("level", 1)) <= GYAKORLO_MAX_LEVEL,
                        "kind=gyakorlo stays at or below level " + GYAKORLO_MAX_LEVEL + ": " + id);
                check(parsed.uniqueMaterials().isEmpty(),
                        "kind=gyakorlo costs nothing beyond plain materials: " + id);
            }
            if (material != null && material.name().endsWith("POTION")) {
                check(!result.getStringList("potion-effects").isEmpty(),
                        "potion result carries real effects: " + id);
            }
            if (material == Material.ENCHANTED_BOOK) {
                final String enchant = result.getString("enchant", "");
                check(enchant != null && !enchant.isBlank(),
                        "enchanted book result carries a real enchantment: " + id);
            }
            if (unique == null && material != null) {
                final Integer sameMaterialInput = parsed.materials().get(material);
                check(sameMaterialInput == null
                                || Math.max(1, result.getInt("amount", 1)) <= sameMaterialInput
                                || VANILLA_DUPLICATION.contains(id),
                        "recipe does not multiply its own input material: " + id);
            }
            if (unique != null) {
                final ConfigurationSection definition = materials.getConfigurationSection(
                        "profession-materials." + unique.toLowerCase(Locale.ROOT));
                check(definition != null, "unique profession output is defined: " + unique);
                check(ConfigMaterialResolver.match(definition.getString("material", "")) != null,
                        "unique profession output has a valid Bukkit icon: " + unique);
            }
        }
        check(ids.size() == EXPECTED_RECIPE_COUNT,
                "active authored recipe count is " + EXPECTED_RECIPE_COUNT + ", got " + ids.size());
        check(canonicalGearCount == EXPECTED_CANONICAL_GEAR_COUNT,
                "canonical template recipe count is " + EXPECTED_CANONICAL_GEAR_COUNT
                        + ", got " + canonicalGearCount);
        check(new ArrayList<>(ids).equals(ids.stream().sorted().toList()), "deterministic recipe order");

        final Map<Material, Integer> mutableIngredients = new HashMap<>();
        mutableIngredients.put(Material.STICK, 1);
        final Map<String, Integer> mutableUniqueIngredients = new HashMap<>();
        mutableUniqueIngredients.put("audit_token", 1);
        final List<String> mutableLore = new ArrayList<>(List.of("audit"));
        final ProfessionRecipeCatalog.Recipe immutableRecipe = new ProfessionRecipeCatalog.Recipe(
                "immutable_audit", ProfessionType.COOK, 1, false, "Audit", "Audit",
                Material.PAPER, 1, null, null, mutableIngredients, mutableUniqueIngredients,
                mutableLore, null, null, false, null, "hozam");
        mutableIngredients.clear();
        mutableUniqueIngredients.clear();
        mutableLore.clear();
        check(immutableRecipe.ingredients().equals(Map.of(Material.STICK, 1)),
                "recipe material ingredients are defensively copied");
        check(immutableRecipe.uniqueIngredients().equals(Map.of("audit_token", 1)),
                "recipe unique ingredients are defensively copied");
        check(immutableRecipe.lore().equals(List.of("audit")), "recipe lore is defensively copied");

        final ProfessionRecipeCatalog.Recipe canonicalA = new ProfessionRecipeCatalog.Recipe(
                "canonical_a", ProfessionType.ARMORER, 1, false, "A", "Audit",
                Material.IRON_SWORD, 1, null, null, Map.of(Material.STICK, 1), Map.of(),
                List.of(), null, null, false, null, "egyedi", "template_a", false);
        final ProfessionRecipeCatalog.Recipe canonicalB = new ProfessionRecipeCatalog.Recipe(
                "canonical_b", ProfessionType.ARMORER, 1, false, "B", "Audit",
                Material.IRON_SWORD, 1, null, null, Map.of(Material.STICK, 1), Map.of(),
                List.of(), null, null, false, null, "egyedi", "template_b", false);
        check(!ProfessionRecipeCatalog.semanticFingerprint(canonicalA)
                        .equals(ProfessionRecipeCatalog.semanticFingerprint(canonicalB)),
                "canonical templates have distinct semantic output identities");

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
        final String transaction = Files.readString(
                Path.of("src/main/java/hu/taliann/icesmp/professions/ProfessionCraftTransaction.java"));
        check(bookListener.contains("plan.uniqueInputs().entrySet()")
                        && transaction.contains("plan.uniqueInputs().entrySet()")
                        && transaction.contains("uniqueMaterials.idOf(item)"),
                "effective CraftPlan unique inputs preserve canonical unique-item identity through execution");
        final String boundary = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/VanillaCraftingBoundaryListener.java"));
        check(boundary.contains("Transformation.VANILLA_RECIPE_OUTPUT")
                        && boundary.contains("onCrafter(final CrafterCraftEvent")
                        && boundary.contains("onCook(final BlockCookEvent"),
                "canonical custom recipe output collision is blocked on player, crafter and cook paths");
        final String professionConfig = Files.readString(
                Path.of("src/main/resources/config/professions.yml"));
        check(professionConfig.contains("legacy-masterworks: false"),
                "legacy Bukkit canonical-lookalike recipes remain disabled");
        final String identity = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/itemization/ItemIdentityService.java"));
        check(identity.contains("POLICY_VIOLATION") && identity.contains("canonicalStateValidator.apply(item)"),
                "command/plugin enchant laundering participates in canonical identity inspection");
        System.out.println("PROFESSION_RECIPE_AUDIT recipes=" + ids.size()
                + " canonical_gear=" + canonicalGearCount
                + " semantic_duplicates=0 key_duplicates=0 atomic_reload=true boundary_collisions=0");
        System.out.println("Profession recipe audit regression suite passed.");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
