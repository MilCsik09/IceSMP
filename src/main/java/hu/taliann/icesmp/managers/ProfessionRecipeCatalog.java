package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.itemization.ArmorFamily;
import hu.taliann.icesmp.professions.ProfessionMaterialRegistry;
import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Canonical immutable Profession 2.0 recipe registry. Config reload builds a private generation,
 * validates identity/dependencies, then publishes all indexes at once.
 */
public final class ProfessionRecipeCatalog {

    public record Recipe(String id, ProfessionType profession, int level, boolean blueprint,
                         String displayName, String category, Material result, int resultAmount,
                         String affixTier, String uniqueResult, Map<Material, Integer> ingredients,
                         Map<String, Integer> uniqueIngredients, List<String> lore,
                         String signature, hu.taliann.icesmp.data.FactionType faction,
                         boolean lootOnly, String job, String kind,
                         String templateId, boolean masterwork) {
        public Recipe {
            ingredients = ingredients == null ? Map.of() : Map.copyOf(ingredients);
            uniqueIngredients = uniqueIngredients == null ? Map.of() : Map.copyOf(uniqueIngredients);
            lore = lore == null ? List.of() : List.copyOf(lore);
        }
        public Recipe(final String id, final ProfessionType profession, final int level,
                      final boolean blueprint, final String displayName, final String category,
                      final Material result, final int resultAmount, final String affixTier,
                      final String uniqueResult, final Map<Material, Integer> ingredients,
                      final Map<String, Integer> uniqueIngredients, final List<String> lore,
                      final String signature, final hu.taliann.icesmp.data.FactionType faction,
                      final boolean lootOnly, final String job, final String kind) {
            this(id, profession, level, blueprint, displayName, category, result, resultAmount,
                    affixTier, uniqueResult, ingredients, uniqueIngredients, lore, signature,
                    faction, lootOnly, job, kind, null, false);
        }
    }

    public record EconomyMetadata(String category, String tier, List<String> dependencies,
                                  boolean batchable, int batchLimit, boolean economyManaged,
                                  boolean alternateRecipe) {
        public EconomyMetadata {
            category = normalizeText(category, "UTILITY");
            tier = normalizeText(tier, "COMMON");
            dependencies = dependencies == null ? List.of() : dependencies.stream()
                    .filter(java.util.Objects::nonNull).map(ProfessionRecipeCatalog::normalizeId)
                    .distinct().sorted().toList();
            if (batchLimit < 1 || batchLimit > 64) throw new IllegalArgumentException("invalid batch limit");
        }
        static EconomyMetadata defaults(final String kind) {
            return new EconomyMetadata(categoryFromKind(kind), "COMMON", List.of(), false, 1, false, false);
        }
    }

    private record CatalogState(Map<String, Recipe> byId,
                                Map<ProfessionType, List<Recipe>> byProfession,
                                Map<String, List<Recipe>> byOutput,
                                Map<ArmorFamily, List<Recipe>> byFamily,
                                Map<String, EconomyMetadata> economy,
                                Map<String, String> aliases) {
        private static CatalogState empty() {
            return new CatalogState(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ProfessionMaterialRegistry materialRegistry;
    private volatile CatalogState state = CatalogState.empty();
    private volatile hu.taliann.icesmp.itemization.ItemTemplateRegistry itemTemplates;

    public ProfessionRecipeCatalog(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.materialRegistry = new ProfessionMaterialRegistry(configManager);
    }

    public void setItemTemplates(final hu.taliann.icesmp.itemization.ItemTemplateRegistry itemTemplates) {
        this.itemTemplates = java.util.Objects.requireNonNull(itemTemplates, "itemTemplates");
    }

    public synchronized void load() {
        if (configManager.getConfiguration() == null) {
            state = CatalogState.empty();
            return;
        }
        materialRegistry.load();
        final ConfigurationSection root = configManager.getConfiguration().getConfigurationSection("profession-recipes");
        if (root == null) {
            state = CatalogState.empty();
            return;
        }
        final Map<String, Recipe> nextById = new LinkedHashMap<>();
        final Map<ProfessionType, List<Recipe>> nextByProfession = new EnumMap<>(ProfessionType.class);
        final Map<String, List<Recipe>> nextByOutput = new LinkedHashMap<>();
        final Map<ArmorFamily, List<Recipe>> nextByFamily = new EnumMap<>(ArmorFamily.class);
        final Map<String, EconomyMetadata> nextEconomy = new LinkedHashMap<>();
        final Map<String, String> semanticOwners = new HashMap<>();
        for (final String id : new TreeSet<>(root.getKeys(false))) {
            final ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            final String normalizedId = normalizeId(id);
            final Recipe recipe = parse(normalizedId, section);
            final EconomyMetadata economy = parseEconomy(section, recipe);
            if (nextById.putIfAbsent(recipe.id(), recipe) != null) {
                throw new IllegalStateException("Duplicate profession recipe id: " + recipe.id());
            }
            final String fingerprint = semanticFingerprint(recipe);
            final String previous = semanticOwners.putIfAbsent(fingerprint, recipe.id());
            if (previous != null && !economy.alternateRecipe()) {
                throw new IllegalStateException("Semantic duplicate profession recipe: " + recipe.id()
                        + " duplicates " + previous + " (mark alternate-recipe only when intentional)");
            }
            nextEconomy.put(recipe.id(), economy);
            nextByProfession.computeIfAbsent(recipe.profession(), ignored -> new ArrayList<>()).add(recipe);
            nextByOutput.computeIfAbsent(outputKey(recipe), ignored -> new ArrayList<>()).add(recipe);
            if (recipe.templateId() != null && itemTemplates != null) {
                final var template = itemTemplates.require(recipe.templateId());
                if (template.armorFamily() != null) {
                    nextByFamily.computeIfAbsent(template.armorFamily(), ignored -> new ArrayList<>()).add(recipe);
                }
            }
        }
        final Map<String, String> aliases = parseAliases(nextById);
        validateManagedDependencyCycles(nextById, nextEconomy);
        state = new CatalogState(
                Collections.unmodifiableMap(new LinkedHashMap<>(nextById)),
                freezeProfessionLists(nextByProfession), freezeLists(nextByOutput), freezeFamilyLists(nextByFamily),
                Collections.unmodifiableMap(new LinkedHashMap<>(nextEconomy)), aliases);
    }

    private Recipe parse(final String id, final ConfigurationSection section) {
        final ProfessionType profession = ProfessionType.fromId(section.getString("profession", ""));
        if (profession == null) throw new IllegalStateException("profession-recipes." + id + ": unknown profession");
        final int level = section.getInt("level", 1);
        if (level < 1 || level > ProfessionManager.MAX_PROFESSION_LEVEL) {
            throw new IllegalStateException("profession-recipes." + id + ": level must be 1.."
                    + ProfessionManager.MAX_PROFESSION_LEVEL);
        }
        final ConfigurationSection resultSection = section.getConfigurationSection("result");
        if (resultSection == null) throw new IllegalStateException("profession-recipes." + id + ": missing result");
        final String uniqueResultRaw = resultSection.getString("unique", null);
        final String uniqueResult = uniqueResultRaw == null || uniqueResultRaw.isBlank()
                ? null : normalizeId(uniqueResultRaw);
        if (uniqueResult != null && !materialRegistry.isDefined(uniqueResult)) {
            throw new IllegalStateException("profession-recipes." + id + ": unknown unique result: " + uniqueResult);
        }
        final Material result = uniqueResult != null
                ? materialRegistry.require(uniqueResult).icon()
                : ConfigMaterialResolver.match(resultSection.getString("material", ""));
        if (result == null || result.isAir()) throw new IllegalStateException("profession-recipes." + id + ": invalid result");
        final int amount = resultSection.getInt("amount", 1);
        if (amount < 1 || amount > 4096) {
            throw new IllegalStateException("profession-recipes." + id + ": invalid result amount " + amount);
        }
        final ProfessionIngredientParser.ParsedIngredients parsed = ProfessionIngredientParser.parse(section.getStringList("ingredients"));
        parsed.uniqueMaterials().keySet().forEach(material -> {
            if (!materialRegistry.isDefined(material)) {
                throw new IllegalStateException("profession-recipes." + id + ": unknown unique ingredient: " + material);
            }
        });
        final boolean blueprint = "blueprint".equalsIgnoreCase(section.getString("learn", "level"));
        final String displayName = section.getString("display-name", prettyName(result));
        final String category = section.getString("category", "Egyéb");
        final String affixTierRaw = resultSection.getString("affix-tier", null);
        final String affixTier = affixTierRaw == null || affixTierRaw.isBlank() ? null : normalizeId(affixTierRaw);
        final String signatureRaw = resultSection.getString("signature", null);
        final String signature = signatureRaw == null || signatureRaw.isBlank() ? null : normalizeId(signatureRaw);
        final String templateRaw = resultSection.getString("template", null);
        final String templateId = templateRaw == null || templateRaw.isBlank() ? null : normalizeId(templateRaw);
        if (templateId != null) validateCanonicalTemplate(id, resultSection, result, amount, uniqueResult, templateId, affixTier, signature);
        final hu.taliann.icesmp.data.FactionType faction =
                hu.taliann.icesmp.data.FactionType.fromInput(section.getString("faction", null));
        final boolean lootOnly = blueprint && section.getBoolean("loot-only", false);
        final String jobRaw = section.getString("job", null);
        return new Recipe(id, profession, level, blueprint, displayName, category, result, amount,
                affixTier, uniqueResult, parsed.materials(), parsed.uniqueMaterials(), section.getStringList("lore"),
                signature, faction, lootOnly,
                jobRaw == null || jobRaw.isBlank() ? null : normalizeId(jobRaw),
                section.getString("kind", "hozam").toLowerCase(Locale.ROOT),
                templateId, resultSection.getBoolean("masterwork", false));
    }

    private void validateCanonicalTemplate(final String id, final ConfigurationSection resultSection,
                                           final Material result, final int amount, final String uniqueResult,
                                           final String templateId, final String affixTier, final String signature) {
        if (uniqueResult != null || amount != 1) {
            throw new IllegalStateException("profession-recipes." + id + ": canonical result must be one non-stackable item");
        }
        if (affixTier != null || signature != null || resultSection.contains("attributes")
                || resultSection.contains("enchant") || resultSection.contains("consumable")
                || resultSection.contains("potion-effects")) {
            throw new IllegalStateException("profession-recipes." + id + ": canonical template cannot mix legacy mutators");
        }
        final var registry = itemTemplates;
        if (registry == null) return;
        final var template = registry.find(templateId).orElseThrow(() ->
                new IllegalStateException("profession-recipes." + id + ": unknown authored template: " + templateId));
        if (!template.material().equals(result.name())) {
            throw new IllegalStateException("profession-recipes." + id + ": template material mismatch");
        }
        if (hu.taliann.icesmp.itemization.ItemTemplate.isArmorSlot(template.slot())
                && !template.isArmorFamilyEquipment()) {
            throw new IllegalStateException("profession-recipes." + id + ": Equipment 2.0 ArmorFamily missing");
        }
        if (template.armorFamily() != null
                && !hu.taliann.icesmp.itemization.ItemTemplate.isArmorSlot(template.slot())) {
            throw new IllegalStateException("profession-recipes." + id + ": ArmorFamily on non-armor output");
        }
    }

    private EconomyMetadata parseEconomy(final ConfigurationSection section, final Recipe recipe) {
        return new EconomyMetadata(section.getString("economy-category", categoryFromKind(recipe.kind())),
                section.getString("material-tier", "COMMON"), section.getStringList("processing-dependencies"),
                section.getBoolean("batchable", false), Math.max(1, section.getInt("batch-limit", 1)),
                section.getBoolean("economy-managed", false), section.getBoolean("alternate-recipe", false));
    }

    private Map<String, String> parseAliases(final Map<String, Recipe> recipes) {
        final ConfigurationSection root = configManager.getConfiguration()
                .getConfigurationSection("professions.economy.recipe-aliases");
        if (root == null) return Map.of();
        final LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        for (final String raw : new TreeSet<>(root.getKeys(false))) {
            final String alias = normalizeId(raw);
            final String target = normalizeId(root.getString(raw, ""));
            if (alias.equals(target) || recipes.containsKey(alias) || !recipes.containsKey(target)) {
                throw new IllegalStateException("invalid profession recipe alias: " + alias + " -> " + target);
            }
            aliases.put(alias, target);
        }
        return Collections.unmodifiableMap(aliases);
    }

    private static void validateManagedDependencyCycles(final Map<String, Recipe> recipes,
                                                        final Map<String, EconomyMetadata> economy) {
        final Map<String, Set<String>> graph = new HashMap<>();
        for (final Recipe recipe : recipes.values()) {
            if (recipe.uniqueResult() == null || !economy.get(recipe.id()).economyManaged()) continue;
            final Set<String> deps = new HashSet<>(recipe.uniqueIngredients().keySet());
            deps.addAll(economy.get(recipe.id()).dependencies());
            graph.put(recipe.uniqueResult(), deps);
        }
        final Set<String> visiting = new HashSet<>();
        final Set<String> visited = new HashSet<>();
        for (final String node : graph.keySet()) visit(node, graph, visiting, visited);
    }

    private static void visit(final String node, final Map<String, Set<String>> graph,
                              final Set<String> visiting, final Set<String> visited) {
        if (visited.contains(node) || !graph.containsKey(node)) return;
        if (!visiting.add(node)) throw new IllegalStateException("profession processing dependency cycle at " + node);
        for (final String dependency : graph.getOrDefault(node, Set.of())) visit(dependency, graph, visiting, visited);
        visiting.remove(node);
        visited.add(node);
    }

    public static String semanticFingerprint(final Recipe recipe) {
        final List<String> inputs = new ArrayList<>();
        recipe.ingredients().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> inputs.add("material:" + entry.getKey().name() + ':' + entry.getValue()));
        recipe.uniqueIngredients().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> inputs.add("unique:" + entry.getKey() + ':' + entry.getValue()));
        return String.join("+", inputs) + "->" + outputKey(recipe) + ':' + recipe.resultAmount();
    }

    public List<String> allIds() { return List.copyOf(state.byId().keySet()); }
    public Recipe get(final String id) {
        if (id == null) return null;
        final String normalized = normalizeId(id);
        final String target = state.aliases().getOrDefault(normalized, normalized);
        return state.byId().get(target);
    }
    public List<Recipe> recipesFor(final ProfessionType profession) {
        return state.byProfession().getOrDefault(profession, List.of());
    }
    public List<Recipe> recipesForOutput(final String outputKey) {
        return state.byOutput().getOrDefault(normalizeOutputLookup(outputKey), List.of());
    }
    public List<Recipe> recipesForFamily(final ArmorFamily family) {
        return family == null ? List.of() : state.byFamily().getOrDefault(family, List.of());
    }
    public EconomyMetadata economy(final String recipeId) {
        final Recipe recipe = get(recipeId);
        return recipe == null ? EconomyMetadata.defaults("utility")
                : state.economy().getOrDefault(recipe.id(), EconomyMetadata.defaults(recipe.kind()));
    }
    public ProfessionMaterialRegistry materialRegistry() { return materialRegistry; }
    public List<String> blueprintRecipeIds() {
        return state.byId().values().stream().filter(Recipe::blueprint).map(Recipe::id).toList();
    }
    public List<String> blueprintDropPool(final boolean bossSource) {
        return state.byId().values().stream().filter(Recipe::blueprint)
                .filter(recipe -> bossSource || !recipe.lootOnly()).map(Recipe::id).toList();
    }
    public boolean isEmpty() { return state.byId().isEmpty(); }

    private static String outputKey(final Recipe recipe) {
        return recipe.templateId() != null ? "template:" + recipe.templateId()
                : recipe.uniqueResult() != null ? "unique:" + recipe.uniqueResult()
                : "material:" + recipe.result().name().toLowerCase(Locale.ROOT);
    }
    private static String normalizeOutputLookup(final String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
    private static String categoryFromKind(final String kind) {
        if (kind == null) return "UTILITY";
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "processing", "hozam" -> "PROCESSING";
            case "egyedi" -> "UTILITY";
            case "consumable" -> "CONSUMABLE";
            case "upgrade_service" -> "UPGRADE_SERVICE";
            default -> "UTILITY";
        };
    }
    private static String normalizeText(final String raw, final String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
    private static String normalizeId(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("blank id");
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
    private static String prettyName(final Material material) {
        final String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder sb = new StringBuilder();
        for (final String part : parts) if (!part.isEmpty())
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        return sb.toString().trim();
    }
    private static Map<ProfessionType, List<Recipe>> freezeProfessionLists(
            final Map<ProfessionType, List<Recipe>> source) {
        final Map<ProfessionType, List<Recipe>> result = new EnumMap<>(ProfessionType.class);
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }
    private static Map<ArmorFamily, List<Recipe>> freezeFamilyLists(
            final Map<ArmorFamily, List<Recipe>> source) {
        final Map<ArmorFamily, List<Recipe>> result = new EnumMap<>(ArmorFamily.class);
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }
    private static <V> Map<String, List<V>> freezeLists(final Map<String, List<V>> source) {
        final LinkedHashMap<String, List<V>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }
}
