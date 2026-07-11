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
 * item is rolled through {@link MasterworkAffixService} (so gear comes out unique). Loaded once on
 * enable; the actual crafting/learning lives in the profession-recipe GUI and its listener.
 */
public final class ProfessionRecipeCatalog {

    /** One catalog recipe. {@code affixTier} is null for plain (non-gear) results. */
    public record Recipe(String id, ProfessionType profession, int level, boolean blueprint,
                         String displayName, String category, Material result, int resultAmount,
                         String affixTier, Map<Material, Integer> ingredients) {
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
        final Material result = resultSection == null ? null
                : Material.matchMaterial(resultSection.getString("material", "").toUpperCase(Locale.ROOT));
        if (result == null) {
            plugin.getLogger().warning("profession-recipes." + id + ": érvénytelen result.material — kihagyva.");
            return null;
        }
        final Map<Material, Integer> ingredients = new LinkedHashMap<>();
        for (final String token : section.getStringList("ingredients")) {
            final String[] parts = token.split(":", 2);
            final Material material = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                continue;
            }
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (final NumberFormatException ignored) {
                    amount = 1;
                }
            }
            ingredients.merge(material, amount, Integer::sum);
        }
        if (ingredients.isEmpty()) {
            plugin.getLogger().warning("profession-recipes." + id + ": nincs érvényes ingredient — kihagyva.");
            return null;
        }
        final int level = Math.max(1, section.getInt("level", 1));
        final boolean blueprint = "blueprint".equalsIgnoreCase(section.getString("learn", "level"));
        final String displayName = section.getString("display-name", prettyName(result));
        final String category = section.getString("category", "Egyéb");
        final int amount = Math.max(1, resultSection.getInt("amount", 1));
        final String affixTier = resultSection.getString("affix-tier", null);
        return new Recipe(id, profession, level, blueprint, displayName, category, result, amount,
                affixTier == null || affixTier.isBlank() ? null : affixTier.toLowerCase(Locale.ROOT), ingredients);
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

    public boolean isEmpty() {
        return byId.isEmpty();
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
