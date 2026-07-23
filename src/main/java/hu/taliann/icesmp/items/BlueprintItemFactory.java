package hu.taliann.icesmp.items;

import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Builds and identifies blueprint items — the "recipe scroll" that teaches a blueprint-gated
 * profession recipe when right-clicked. The taught recipe id is stored in the item's PDC, so a
 * blueprint can be handed out by an NPC shop, dropped by a mob, or given by an admin.
 */
public final class BlueprintItemFactory {

    private final NamespacedKey recipeKey;
    private final ProfessionRecipeCatalog catalog;

    public BlueprintItemFactory(final JavaPlugin plugin, final ProfessionRecipeCatalog catalog) {
        this.recipeKey = new NamespacedKey(plugin, "blueprint_recipe");
        this.catalog = catalog;
    }

    /** Creates a blueprint item for the given recipe, or null if the recipe is unknown. */
    public ItemStack create(final String recipeId) {
        final ProfessionRecipeCatalog.Recipe recipe = catalog.get(recipeId);
        if (recipe == null) {
            return null;
        }
        final ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Tervrajz: " + recipe.displayName(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Szakma: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(recipe.profession().getDisplayName()),
                Component.text("Jobb katt: megtanulod a receptet.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("(Craftoláshoz " + recipe.level() + ". szakma-szint is kell.)", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(recipeKey, PersistentDataType.STRING, recipe.id());
        item.setItemMeta(meta);
        hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(item, "icesmp:blueprint");
        return item;
    }

    /** The recipe id a blueprint item teaches, or null if the item is not a blueprint. */
    public String recipeIdOf(final ItemStack item) {
        if (item == null || item.getType() != Material.KNOWLEDGE_BOOK || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(recipeKey, PersistentDataType.STRING);
    }
}
