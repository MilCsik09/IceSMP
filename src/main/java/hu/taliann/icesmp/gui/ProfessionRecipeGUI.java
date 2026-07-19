package hu.taliann.icesmp.gui;

import static hu.taliann.icesmp.gui.GuiUtil.accent;
import static hu.taliann.icesmp.gui.GuiUtil.grey;
import static hu.taliann.icesmp.gui.GuiUtil.icon;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The profession recipe-book GUI: a paged list of every recipe the player's active professions
 * offer, each tile showing the result, ingredients (have/need), the level requirement and whether
 * it is learned (for blueprint recipes). A left click on a craftable recipe crafts it (handled by
 * {@link hu.taliann.icesmp.listeners.ProfessionRecipeListener}). Rendering only.
 */
public final class ProfessionRecipeGUI {

    private static final int PAGE_SIZE = 45;

    private ProfessionRecipeGUI() {
    }

    /** Recipes visible to the player: every recipe of every profession they currently hold, level-sorted. */
    public static List<ProfessionRecipeCatalog.Recipe> visibleRecipes(final Player player,
                                                                      final ProfessionManager professionManager,
                                                                      final ProfessionRecipeCatalog catalog) {
        final List<ProfessionRecipeCatalog.Recipe> recipes = new ArrayList<>();
        for (final ProfessionType profession : professionManager.getActiveProfessions(player)) {
            recipes.addAll(catalog.recipesFor(profession));
        }
        recipes.sort((a, b) -> {
            final int p = a.profession().name().compareTo(b.profession().name());
            return p != 0 ? p : Integer.compare(a.level(), b.level());
        });
        return recipes;
    }

    public static void open(final Player player, final int page, final ProfessionManager professionManager,
                            final ProfessionRecipeCatalog catalog,
                            final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        final List<ProfessionRecipeCatalog.Recipe> recipes = visibleRecipes(player, professionManager, catalog);
        final int maxPage = Math.max(0, (recipes.size() - 1) / PAGE_SIZE);
        final int shownPage = Math.max(0, Math.min(page, maxPage));

        final ProfessionRecipeHolder holder = new ProfessionRecipeHolder(player.getUniqueId(), shownPage);
        final Inventory inv = Bukkit.createInventory(holder, 54,
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<dark_aqua>» Recept-könyv «</dark_aqua>"));
        holder.setInventory(inv);

        if (recipes.isEmpty()) {
            inv.setItem(22, icon(Material.BOOK, accent("Nincs recepted"),
                    List.of(grey("Válassz szakmát (/profession join), és lépj szintet,"),
                            grey("hogy receptek nyíljanak meg."))));
        }

        final int start = shownPage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < recipes.size(); i++) {
            final ProfessionRecipeCatalog.Recipe recipe = recipes.get(start + i);
            inv.setItem(i, buildTile(player, professionManager, recipe, uniqueMaterials));
            holder.map(i, recipe.id());
        }

        if (shownPage > 0) {
            inv.setItem(45, icon(Material.ARROW, Component.text("Előző oldal", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false), List.of()));
            holder.map(45, "PREV");
        }
        inv.setItem(49, icon(Material.BARRIER, Component.text("Bezárás", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false), List.of(grey("Oldal " + (shownPage + 1) + "/" + (maxPage + 1)))));
        holder.map(49, "CLOSE");
        if (shownPage < maxPage) {
            inv.setItem(53, icon(Material.ARROW, Component.text("Következő oldal", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false), List.of()));
            holder.map(53, "NEXT");
        }

        player.openInventory(inv);
    }

    private static org.bukkit.inventory.ItemStack buildTile(final Player player, final ProfessionManager professionManager,
                                                            final ProfessionRecipeCatalog.Recipe recipe,
                                                            final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        final int level = professionManager.getLevel(player, recipe.profession());
        final boolean levelOk = level >= recipe.level();
        final boolean learned = !recipe.blueprint() || professionManager.hasLearnedRecipe(player, recipe.id());
        final boolean hasMats = hasIngredients(player, recipe, uniqueMaterials);
        final boolean craftable = levelOk && learned && hasMats;

        final List<Component> lore = new ArrayList<>();
        lore.add(grey("Szakma: ").append(recipe.profession().getDisplayName()));
        lore.add(grey("Kategória: " + recipe.category()));
        lore.add(levelOk
                ? Component.text("✔ Szint " + recipe.level(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                : Component.text("✘ Szint " + recipe.level() + " kell (most " + level + ")", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        if (recipe.blueprint()) {
            lore.add(learned
                    ? Component.text("✔ Recept megtanulva", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                    : Component.text("✘ Tervrajz kell (NPC / mob-drop)", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }
        if (recipe.faction() != null) {
            lore.add(Component.text("⚑ Csak: " + recipe.faction().getDisplayName()
                            + " (" + recipe.faction().getFullName() + ")", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(grey("Hozzávalók:"));
        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            final int have = countMaterial(player, entry.getKey(), uniqueMaterials);
            final boolean enough = have >= entry.getValue();
            lore.add(Component.text("  " + (enough ? "✔ " : "✘ ") + have + "/" + entry.getValue() + " "
                    + prettyName(entry.getKey()), enough ? NamedTextColor.GRAY : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        for (final Map.Entry<String, Integer> entry : recipe.uniqueIngredients().entrySet()) {
            final int have = countUnique(player, entry.getKey(), uniqueMaterials);
            final boolean enough = have >= entry.getValue();
            lore.add(Component.text("  " + (enough ? "✔ " : "✘ ") + have + "/" + entry.getValue() + " "
                    + uniqueMaterials.displayName(entry.getKey()), enough ? NamedTextColor.AQUA : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (recipe.affixTier() != null) {
            lore.add(Component.empty());
            lore.add(Component.text("✦ Egyedi (rolled) minőség + affixek", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(craftable
                ? Component.text("» Kattints a craftoláshoz", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                : Component.text("Zárolva", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));

        final NamedTextColor nameColor = craftable ? NamedTextColor.GREEN
                : (levelOk && learned ? NamedTextColor.YELLOW : NamedTextColor.GRAY);
        final Component name = Component.text((recipe.resultAmount() > 1 ? recipe.resultAmount() + "× " : "")
                + recipe.displayName(), nameColor).decoration(TextDecoration.ITALIC, false);
        return icon(recipe.result(), name, lore, recipe.affixTier() != null);
    }

    private static boolean hasIngredients(final Player player, final ProfessionRecipeCatalog.Recipe recipe,
                                          final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            if (countMaterial(player, entry.getKey(), uniqueMaterials) < entry.getValue()) {
                return false;
            }
        }
        for (final Map.Entry<String, Integer> entry : recipe.uniqueIngredients().entrySet()) {
            if (countUnique(player, entry.getKey(), uniqueMaterials) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static int countUnique(final Player player, final String uniqueId,
                                   final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        int count = 0;
        for (final org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && uniqueId.equals(uniqueMaterials.idOf(item))) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /** Counts plain items of the given material — EXCLUDING unique materials that share the base type. */
    private static int countMaterial(final Player player, final Material material,
                                     final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        int count = 0;
        for (final org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && uniqueMaterials.idOf(item) == null) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static String prettyName(final Material material) {
        final String text = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
