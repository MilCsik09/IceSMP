package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.ProfessionType;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Profession recipes: themed, PDC-tagged "masterwork" items
 * that only a player with the right profession at a high enough level can craft.
 * The recipe is always discoverable, but {@code ProfessionRecipeListener} clears
 * the result unless the crafter meets the requirement — so the professions
 * produce tangible, prestige gear.
 */
@SuppressWarnings("deprecation")
public final class ProfessionRecipeManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** A craftable masterwork: its result item plus the profession/level needed. */
    public record Recipe(String id, ProfessionType profession, int requiredLevel, ItemStack result,
                         String[] shape, java.util.Map<Character, Material> ingredients) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey recipeKey;
    private final List<Recipe> recipes = new ArrayList<>();
    private final Set<NamespacedKey> registeredRecipeKeys = new LinkedHashSet<>();

    public ProfessionRecipeManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.recipeKey = new NamespacedKey(plugin, "profession_recipe");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("professions.recipes.enabled", true);
    }

    /** Idempotently rebuilds the Bukkit registry; disabled/reload states remove stale keys first. */
    public synchronized void registerRecipes() {
        clearRegisteredRecipes();
        recipes.clear();
        if (!isEnabled()) {
            return;
        }
        // A kódba égetett régi mestermű-készlet alapból KI: numerikusan mindig
        // felülmúlta a katalógus-párját (két párhuzamos rendszer) — a recept-katalógus
        // az egyetlen craft-forrás. A már legyártott példányok érvényben maradnak.
        if (!configManager.getBoolean("professions.recipes.legacy-masterworks", false)) {
            return;
        }

        define("tarnasz_csakany", ProfessionType.MINER, 15,
                tool(Material.DIAMOND_PICKAXE, "<aqua>Tárnász Csákány</aqua>", "<gray>A Vasművek Akadémiáján edzett szerszám.</gray>",
                        new Enchantment[]{Enchantment.EFFICIENCY, Enchantment.UNBREAKING}, new int[]{4, 3}),
                new String[]{"GDG", "DSD", " S "}, java.util.Map.of('G', Material.EMERALD, 'D', Material.DIAMOND, 'S', Material.STICK));

        define("favago_fejsze", ProfessionType.LUMBERJACK, 15,
                tool(Material.DIAMOND_AXE, "<dark_green>Favágó Fejsze</dark_green>", "<gray>A Bokic menti favágócéhek remeke.</gray>",
                        new Enchantment[]{Enchantment.EFFICIENCY, Enchantment.UNBREAKING}, new int[]{4, 2}),
                new String[]{"GD ", "DS ", " S "}, java.util.Map.of('G', Material.EMERALD, 'D', Material.DIAMOND, 'S', Material.STICK));

        define("bastya_pajzs", ProfessionType.ARMORER, 15,
                tool(Material.SHIELD, "<gold>Bástya Pajzs</gold>", "<gray>Caldestera kovácsmestereinek remeke.</gray>",
                        new Enchantment[]{Enchantment.UNBREAKING}, new int[]{5}),
                new String[]{"GIG", "III", " I "}, java.util.Map.of('G', Material.EMERALD, 'I', Material.IRON_INGOT));

        define("bolcs_konyve", ProfessionType.ENCHANTER, 15,
                storedBook("<dark_aqua>Bölcs Könyve</dark_aqua>", "<gray>Caldestera Akadémiájának örök bölcsessége.</gray>", Enchantment.MENDING),
                new String[]{"GLG", "LBL", "GLG"}, java.util.Map.of('G', Material.EMERALD, 'L', Material.LAPIS_LAZULI, 'B', Material.BOOK));

        // 2. szintű mestermunkák (30. szint) — netherit-alapú, erősebb változatok.
        define("osveny_csakany", ProfessionType.MINER, 30,
                tool(Material.NETHERITE_PICKAXE, "<aqua>Ősvény Csákány</aqua>", "<gray>A Kárhozat Kapuja netheritjéből kovácsolva.</gray>",
                        new Enchantment[]{Enchantment.EFFICIENCY, Enchantment.UNBREAKING}, new int[]{5, 5}),
                new String[]{"NEN", "NSN", " S "}, java.util.Map.of('N', Material.NETHERITE_INGOT, 'E', Material.EMERALD_BLOCK, 'S', Material.STICK));

        define("rengeteg_fejsze", ProfessionType.LUMBERJACK, 30,
                tool(Material.NETHERITE_AXE, "<dark_green>Rengeteg Fejsze</dark_green>", "<gray>A Kárhozat Kapuja netheritje — ligeteket dönt.</gray>",
                        new Enchantment[]{Enchantment.EFFICIENCY, Enchantment.UNBREAKING}, new int[]{5, 4}),
                new String[]{"NE ", "NS ", " S "}, java.util.Map.of('N', Material.NETHERITE_INGOT, 'E', Material.EMERALD_BLOCK, 'S', Material.STICK));

        define("sarkany_vert", ProfessionType.ARMORER, 30,
                tool(Material.NETHERITE_CHESTPLATE, "<gold>Sárkányvért</gold>", "<gray>Kallan sárkányainak pikkelye ihlette.</gray>",
                        new Enchantment[]{Enchantment.UNBREAKING}, new int[]{8}),
                new String[]{"N N", "NEN", "NNN"}, java.util.Map.of('N', Material.NETHERITE_INGOT, 'E', Material.EMERALD_BLOCK));

        define("mester_tomusz", ProfessionType.ENCHANTER, 30,
                storedBook("<dark_aqua>Mester Tomusz</dark_aqua>", "<gray>Caldestera Akadémiájának legmélyebb tudása.</gray>", Enchantment.EFFICIENCY, 5),
                new String[]{"NLN", "LBL", "NLN"}, java.util.Map.of('N', Material.NETHERITE_INGOT, 'L', Material.LAPIS_LAZULI, 'B', Material.BOOK));

        for (final Recipe recipe : recipes) {
            try {
                final NamespacedKey key = new NamespacedKey(plugin, "prof_" + recipe.id());
                final ShapedRecipe shaped = new ShapedRecipe(key, recipe.result());
                shaped.shape(recipe.shape());
                recipe.ingredients().forEach(shaped::setIngredient);
                if (plugin.getServer().addRecipe(shaped)) {
                    registeredRecipeKeys.add(key);
                } else {
                    plugin.getLogger().warning("Server rejected duplicate profession recipe key: " + key);
                }
            } catch (final IllegalStateException | IllegalArgumentException exception) {
                plugin.getLogger().warning("Could not register profession recipe '" + recipe.id() + "': " + exception.getMessage());
            }
        }
    }

    /** Removes only keys owned by this manager; safe on reload and plugin disable. */
    public synchronized void clearRegisteredRecipes() {
        for (final NamespacedKey key : registeredRecipeKeys) {
            plugin.getServer().removeRecipe(key);
        }
        registeredRecipeKeys.clear();
    }

    public void shutdown() {
        clearRegisteredRecipes();
        recipes.clear();
    }

    /**
     * The requirement for a crafted result, if it is a profession masterwork.
     *
     * @param result the recipe result item
     * @return the matching recipe (with profession + level), or null
     */
    public Recipe getRequirement(final ItemStack result) {
        if (result == null || result.getType().isAir() || !result.hasItemMeta()) {
            return null;
        }
        final String id = result.getItemMeta().getPersistentDataContainer().get(recipeKey, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        for (final Recipe recipe : recipes) {
            if (recipe.id().equalsIgnoreCase(id)) {
                return recipe;
            }
        }
        return null;
    }

    private void define(final String id, final ProfessionType profession, final int level, final ItemStack result,
                        final String[] shape, final java.util.Map<Character, Material> ingredients) {
        final int requiredLevel = Math.max(1, configManager.getInt("professions.recipes." + id + ".required-level", level));
        final ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(recipeKey, PersistentDataType.STRING, id.toLowerCase(Locale.ROOT));
        result.setItemMeta(meta);
        recipes.add(new Recipe(id.toLowerCase(Locale.ROOT), profession, requiredLevel, result, shape, ingredients));
    }

    private ItemStack tool(final Material material, final String name, final String lore,
                           final Enchantment[] enchants, final int[] levels) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MINI.deserialize(lore).decoration(TextDecoration.ITALIC, false)));
        for (int i = 0; i < enchants.length; i++) {
            meta.addEnchant(enchants[i], levels[i], true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack storedBook(final String name, final String lore, final Enchantment enchant) {
        return storedBook(name, lore, enchant, 1);
    }

    private ItemStack storedBook(final String name, final String lore, final Enchantment enchant, final int level) {
        final ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MINI.deserialize(lore).decoration(TextDecoration.ITALIC, false)));
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.addStoredEnchant(enchant, Math.max(1, level), true);
        }
        item.setItemMeta(meta);
        return item;
    }
}
