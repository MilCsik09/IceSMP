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
    private static final Set<String> KINDS =
            Set.of("gyakorlo", "hozam", "egyedi", "lanc", "ritkasag");
    private static final List<String> FUNCTIONAL_KEYS =
            List.of("template", "affix-tier", "enchant", "attributes", "consumable", "signature", "potion-effects");
    private static final int GYAKORLO_MAX_LEVEL = 15;
    /** A vanília MAGA is ugyanígy duplikálja ezt a tárgyat — a katalógusból ez nem látszik. */
    private static final Set<String> VANILLA_DUPLICATION = Set.of("kovacsmesteri_sablon");

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
            final String template = result.getString("template", null);
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
                    section.getBoolean("loot-only", false), section.getString("job", null),
                    section.getString("kind", "hozam"), template, result.getBoolean("masterwork", false));
            final String fingerprint = ProfessionRecipeCatalog.semanticFingerprint(recipe);
            check(fingerprints.add(fingerprint), "semantic duplicate: " + id + " -> " + fingerprint);

            // Recept-fajta szerződés: minden recept KIMONDJA, mire való, és a fajta megszabja,
            // milyen kart húzhat meg. Enélkül a katalógus visszacsúszik oda, hogy a vanilla
            // duplikátumnak és az üres ígéret-itemnek nincs mihez képest nemet mondani.
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
            // Üres ígéret-item: a főzet, ami nem hat, és az enchantkönyv, ami üllőn nem ad
            // semmit. Mindkettő a nevével ígér, és a felület nem árulja el, hogy nem teljesít.
            if (material != null && material.name().endsWith("POTION")) {
                check(!result.getStringList("potion-effects").isEmpty(),
                        "potion result carries real effects: " + id);
            }
            if (material == Material.ENCHANTED_BOOK) {
                final String enchant = result.getString("enchant", "");
                check(enchant != null && !enchant.isBlank(),
                        "enchanted book result carries a real enchantment: " + id);
            }
            // Önmagát sokszorozó recept: a kimenet ugyanaz az anyag, mint a bemenet, többször.
            // Ez korlátlanul ismételhető nyersanyag-forrás, a gazdaság csendes szivárgása.
            if (unique == null && material != null) {
                final Integer sameMaterialInput = parsed.materials().get(material);
                check(sameMaterialInput == null
                                || Math.max(1, result.getInt("amount", 1)) <= sameMaterialInput
                                || VANILLA_DUPLICATION.contains(id),
                        "recipe does not multiply its own input material: " + id);
            }
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
