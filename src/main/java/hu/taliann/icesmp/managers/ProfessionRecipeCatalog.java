package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Config-driven WoW-style profession recipe catalog ({@code profession-recipes.yml}). Each recipe
 * belongs to a profession, requires a level, is learned either automatically at that level
 * ({@code learn: level}) or from a blueprint ({@code learn: blueprint}), consumes a list of
 * ingredients and yields a result. When the result declares an {@code affix-tier}, the crafted
 * item is rolled through {@link ItemRarityService} (so gear comes out unique).
 *
 * <p>Reloads are transactional: parsing and semantic validation build a private candidate state.
 * Readers keep the previous immutable generation until the complete candidate is published through
 * one volatile replacement. A rejected duplicate or malformed reload therefore cannot expose a
 * cleared or partially rebuilt catalog to concurrent Folia region threads.</p>
 */
public final class ProfessionRecipeCatalog {

    /**
     * One catalog recipe. {@code affixTier} is null for plain (non-gear) results. {@code uniqueResult}
     * is the produced unique-material id (null = a normal {@code result} Material). {@code ingredients}
     * are vanilla materials; {@code uniqueIngredients} are unique-material ids the recipe also needs.
     */
    public record Recipe(String id, ProfessionType profession, int level, boolean blueprint,
                         String displayName, String category, Material result, int resultAmount,
                         String affixTier, String uniqueResult, Map<Material, Integer> ingredients,
                         Map<String, Integer> uniqueIngredients, List<String> lore,
                         String signature, hu.taliann.icesmp.data.FactionType faction,
                         boolean lootOnly, String job) {
        public Recipe {
            ingredients = ingredients == null ? Map.of() : Map.copyOf(ingredients);
            uniqueIngredients = uniqueIngredients == null ? Map.of() : Map.copyOf(uniqueIngredients);
            lore = lore == null ? List.of() : List.copyOf(lore);
        }
    }

    private record CatalogState(Map<String, Recipe> byId,
                                Map<ProfessionType, List<Recipe>> byProfession) {
        private static CatalogState empty() {
            return new CatalogState(Map.of(), Map.of());
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private volatile CatalogState state = CatalogState.empty();

    public ProfessionRecipeCatalog(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /** Builds and validates a private candidate, then publishes the complete immutable generation. */
    public synchronized void load() {
        if (configManager.getConfiguration() == null) {
            state = CatalogState.empty();
            return;
        }
        final ConfigurationSection root = configManager.getConfiguration()
                .getConfigurationSection("profession-recipes");
        if (root == null) {
            state = CatalogState.empty();
            return;
        }

        final Map<String, Recipe> nextById = new LinkedHashMap<>();
        final Map<ProfessionType, List<Recipe>> nextByProfession = new EnumMap<>(ProfessionType.class);
        final Set<String> semanticFingerprints = new HashSet<>();
        for (final String id : new TreeSet<>(root.getKeys(false))) {
            final ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            final Recipe recipe = parse(id.toLowerCase(Locale.ROOT), section);
            if (recipe == null) {
                continue;
            }
            if (nextById.putIfAbsent(recipe.id(), recipe) != null) {
                throw new IllegalStateException("Duplicate profession recipe id: " + recipe.id());
            }
            final String fingerprint = semanticFingerprint(recipe);
            if (!semanticFingerprints.add(fingerprint)) {
                throw new IllegalStateException("Semantic duplicate profession recipe: " + recipe.id()
                        + " (" + fingerprint + ")");
            }
            nextByProfession.computeIfAbsent(recipe.profession(), key -> new ArrayList<>()).add(recipe);
        }

        final Map<String, Recipe> frozenById = Collections.unmodifiableMap(new LinkedHashMap<>(nextById));
        final Map<ProfessionType, List<Recipe>> frozenByProfession = new EnumMap<>(ProfessionType.class);
        nextByProfession.forEach((profession, recipes) ->
                frozenByProfession.put(profession, List.copyOf(recipes)));
        state = new CatalogState(frozenById, Collections.unmodifiableMap(frozenByProfession));
    }

    private Recipe parse(final String id, final ConfigurationSection section) {
        final ProfessionType profession = ProfessionType.fromId(section.getString("profession", ""));
        if (profession == null) {
            plugin.getLogger().warning("profession-recipes." + id + ": ismeretlen szakma — kihagyva.");
            return null;
        }
        final ConfigurationSection resultSection = section.getConfigurationSection("result");
        if (resultSection == null) {
            plugin.getLogger().warning("profession-recipes." + id + ": hiányzó result — kihagyva.");
            return null;
        }
        final String uniqueResult = resultSection.getString("unique", null);
        final Material result = uniqueResult != null
                ? ConfigMaterialResolver.match(uniqueIconMaterial(uniqueResult))
                : ConfigMaterialResolver.match(resultSection.getString("material", ""));
        if (result == null) {
            plugin.getLogger().warning("profession-recipes." + id + ": érvénytelen result — kihagyva.");
            return null;
        }
        final ProfessionIngredientParser.ParsedIngredients parsedIngredients;
        try {
            parsedIngredients = ProfessionIngredientParser.parse(section.getStringList("ingredients"));
        } catch (final IllegalArgumentException invalidIngredient) {
            plugin.getLogger().warning("profession-recipes." + id + ": hibás ingredient ("
                    + invalidIngredient.getMessage() + ") — a teljes recept kihagyva.");
            return null;
        }
        final int level = Math.max(1, section.getInt("level", 1));
        final boolean blueprint = "blueprint".equalsIgnoreCase(section.getString("learn", "level"));
        final String displayName = section.getString("display-name", prettyName(result));
        final String category = section.getString("category", "Egyéb");
        final int amount = Math.max(1, resultSection.getInt("amount", 1));
        final String affixTier = resultSection.getString("affix-tier", null);
        final List<String> lore = section.getStringList("lore");
        final String signature = resultSection.getString("signature", null);
        final hu.taliann.icesmp.data.FactionType faction =
                hu.taliann.icesmp.data.FactionType.fromInput(section.getString("faction", null));
        final boolean lootOnly = blueprint && section.getBoolean("loot-only", false);
        final String job = section.getString("job", null);
        return new Recipe(id, profession, level, blueprint, displayName, category, result, amount,
                affixTier == null || affixTier.isBlank() ? null : affixTier.toLowerCase(Locale.ROOT),
                uniqueResult == null || uniqueResult.isBlank() ? null : uniqueResult.toLowerCase(Locale.ROOT),
                parsedIngredients.materials(), parsedIngredients.uniqueMaterials(), lore,
                signature == null || signature.isBlank() ? null : signature.toLowerCase(Locale.ROOT), faction,
                lootOnly, job == null || job.isBlank() ? null : job.toLowerCase(Locale.ROOT));
    }

    /** Canonical input/output signature independent of YAML order, profession and progression metadata. */
    public static String semanticFingerprint(final Recipe recipe) {
        final List<String> inputs = new ArrayList<>();
        recipe.ingredients().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> inputs.add("material:" + entry.getKey().name() + ':' + entry.getValue()));
        recipe.uniqueIngredients().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> inputs.add("unique:" + entry.getKey() + ':' + entry.getValue()));
        final String output = recipe.uniqueResult() == null
                ? "material:" + recipe.result().name()
                : "unique:" + recipe.uniqueResult();
        return String.join("+", inputs) + "->" + output + ':' + recipe.resultAmount();
    }

    /** Minden recept-id betöltési sorrendben (admin item-adó parancs tab-complete-je). */
    public List<String> allIds() {
        return List.copyOf(state.byId().keySet());
    }

    public Recipe get(final String id) {
        return id == null ? null : state.byId().get(id.toLowerCase(Locale.ROOT));
    }

    public List<Recipe> recipesFor(final ProfessionType profession) {
        return state.byProfession().getOrDefault(profession, List.of());
    }

    /** Ids of the blueprint-learned recipes (for the admin blueprint-give tab-complete). */
    public List<String> blueprintRecipeIds() {
        final List<String> ids = new ArrayList<>();
        for (final Recipe recipe : state.byId().values()) {
            if (recipe.blueprint()) {
                ids.add(recipe.id());
            }
        }
        return ids;
    }

    /**
     * I22 — a tervrajz-drop sorsolási poolja: a loot-only receptek tervrajza CSAK
     * boss-forrásból eshet, a többi blueprint-recept mindkét ágból.
     */
    public List<String> blueprintDropPool(final boolean bossSource) {
        final List<String> ids = new ArrayList<>();
        for (final Recipe recipe : state.byId().values()) {
            if (recipe.blueprint() && (bossSource || !recipe.lootOnly())) {
                ids.add(recipe.id());
            }
        }
        return ids;
    }

    public boolean isEmpty() {
        return state.byId().isEmpty();
    }

    /** The icon material configured for a unique material (fallback PAPER), upper-cased for matching. */
    private String uniqueIconMaterial(final String uniqueId) {
        if (configManager.getConfiguration() == null) {
            return "PAPER";
        }
        return configManager.getConfiguration()
                .getString("profession-materials." + uniqueId.toLowerCase(Locale.ROOT) + ".material", "PAPER")
                .toUpperCase(Locale.ROOT);
    }

    private static String prettyName(final Material material) {
        final String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder sb = new StringBuilder();
        for (final String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
