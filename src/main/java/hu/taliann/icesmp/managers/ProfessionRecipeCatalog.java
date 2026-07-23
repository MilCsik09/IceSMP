package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.ProfessionType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Config-driven WoW-style profession recipe catalog ({@code profession-recipes.yml}). Each recipe
 * belongs to a profession, requires a level, is learned either automatically at that level
 * ({@code learn: level}) or from a blueprint ({@code learn: blueprint}), consumes a list of
 * ingredients and yields a result. When the result declares an {@code affix-tier}, the crafted
 * item is rolled through {@link ItemRarityService} (so gear comes out unique). Loaded once on
 * enable; the actual crafting/learning lives in the profession-recipe GUI and its listener.
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
                         boolean lootOnly, int customModelData, String job) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, Recipe> byId = new LinkedHashMap<>();
    private final Map<ProfessionType, List<Recipe>> byProfession = new EnumMap<>(ProfessionType.class);

    public ProfessionRecipeCatalog(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void load() {
        byId.clear();
        byProfession.clear();
        if (configManager.getConfiguration() == null) {
            return;
        }
        final ConfigurationSection root = configManager.getConfiguration().getConfigurationSection("profession-recipes");
        if (root == null) {
            return;
        }
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            final Recipe recipe = parse(id.toLowerCase(Locale.ROOT), section);
            if (recipe == null) {
                continue;
            }
            byId.put(recipe.id(), recipe);
            byProfession.computeIfAbsent(recipe.profession(), key -> new ArrayList<>()).add(recipe);
        }
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
        // A unique-material eredmény ikonját a profession-materials config adja; a sima eredmény a material.
        final String uniqueResult = resultSection.getString("unique", null);
        final Material result = uniqueResult != null
                ? Material.matchMaterial(uniqueIconMaterial(uniqueResult))
                : Material.matchMaterial(resultSection.getString("material", "").toUpperCase(Locale.ROOT));
        if (result == null) {
            plugin.getLogger().warning("profession-recipes." + id + ": érvénytelen result — kihagyva.");
            return null;
        }
        final Map<Material, Integer> ingredients = new LinkedHashMap<>();
        final Map<String, Integer> uniqueIngredients = new LinkedHashMap<>();
        for (final String token : section.getStringList("ingredients")) {
            final String[] parts = token.split(":");
            if (parts.length >= 3 && "unique".equalsIgnoreCase(parts[0].trim())) {
                uniqueIngredients.merge(parts[1].trim().toLowerCase(Locale.ROOT), parseCount(parts[2]), Integer::sum);
                continue;
            }
            final Material material = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                continue;
            }
            ingredients.merge(material, parts.length > 1 ? parseCount(parts[1]) : 1, Integer::sum);
        }
        if (ingredients.isEmpty() && uniqueIngredients.isEmpty()) {
            plugin.getLogger().warning("profession-recipes." + id + ": nincs érvényes ingredient — kihagyva.");
            return null;
        }
        final int level = Math.max(1, section.getInt("level", 1));
        final boolean blueprint = "blueprint".equalsIgnoreCase(section.getString("learn", "level"));
        final String displayName = section.getString("display-name", prettyName(result));
        final String category = section.getString("category", "Egyéb");
        final int amount = Math.max(1, resultSection.getInt("amount", 1));
        final String affixTier = resultSection.getString("affix-tier", null);
        // Optional lore lines: when present, the crafted item is stamped with the designed name + lore
        // (a "named" prestige item — gear/tome/special consumable); bulk results have no lore and stay vanilla.
        final List<String> lore = section.getStringList("lore");
        // Signature items: a PDC id the perk listener recognises; optional faction gate.
        final String signature = resultSection.getString("signature", null);
        final hu.taliann.icesmp.data.FactionType faction =
                hu.taliann.icesmp.data.FactionType.fromInput(section.getString("faction", null));
        // Loot-only: a tervrajz KIZÁRÓLAG világboss/nehéz esemény lootból eshet
        // (NPC-bolt/sima mob sosem adja) — csak blueprint-tanulású receptnél értelmes.
        final boolean lootOnly = blueprint && section.getBoolean("loot-only", false);
        // Resource-pack horog: a nevesített/lore-os eredmény CustomModelData-t kaphat
        // (result.custom-model-data) — a kiosztott értékek a docs/RESOURCE_PACK_CMD.md listán.
        final int customModelData = Math.max(0, resultSection.getInt("custom-model-data", 0));
        // Kaszt-zárt recept: csak a megadott kaszt készítheti (pl. Varázsló-rúnák).
        final String job = section.getString("job", null);
        return new Recipe(id, profession, level, blueprint, displayName, category, result, amount,
                affixTier == null || affixTier.isBlank() ? null : affixTier.toLowerCase(Locale.ROOT),
                uniqueResult == null || uniqueResult.isBlank() ? null : uniqueResult.toLowerCase(Locale.ROOT),
                ingredients, uniqueIngredients, lore,
                signature == null || signature.isBlank() ? null : signature.toLowerCase(Locale.ROOT), faction,
                lootOnly, customModelData,
                job == null || job.isBlank() ? null : job.toLowerCase(Locale.ROOT));
    }

    /** Minden recept-id betöltési sorrendben (admin item-adó parancs tab-complete-je). */
    public List<String> allIds() {
        return List.copyOf(byId.keySet());
    }

    public Recipe get(final String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Recipe> recipesFor(final ProfessionType profession) {
        return byProfession.getOrDefault(profession, List.of());
    }

    /** Ids of the blueprint-learned recipes (for the admin blueprint-give tab-complete). */
    public List<String> blueprintRecipeIds() {
        final List<String> ids = new ArrayList<>();
        for (final Recipe recipe : byId.values()) {
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
        for (final Recipe recipe : byId.values()) {
            if (recipe.blueprint() && (bossSource || !recipe.lootOnly())) {
                ids.add(recipe.id());
            }
        }
        return ids;
    }

    public boolean isEmpty() {
        return byId.isEmpty();
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

    private static int parseCount(final String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (final NumberFormatException ignored) {
            return 1;
        }
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
