package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.ProfessionRecipeGUI;
import hu.taliann.icesmp.gui.ProfessionRecipeHolder;
import hu.taliann.icesmp.managers.MasterworkAffixService;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Click handling and crafting for the profession recipe-book GUI. A left click on a craftable
 * recipe verifies the profession level, the learned state (for blueprint recipes) and the
 * ingredients, then consumes them and grants the result — rolled through {@link MasterworkAffixService}
 * on the recipe's {@code affix-tier} when set, so crafted gear comes out unique. All inventory
 * touches are on the clicking player's own region thread (Folia-safe).
 */
public final class ProfessionRecipeBookListener implements Listener {

    private final ProfessionManager professionManager;
    private final ProfessionRecipeCatalog catalog;
    private final MasterworkAffixService affixService;
    private final MessageManager messageManager;

    public ProfessionRecipeBookListener(final ProfessionManager professionManager, final ProfessionRecipeCatalog catalog,
                                        final MasterworkAffixService affixService, final MessageManager messageManager) {
        this.professionManager = professionManager;
        this.catalog = catalog;
        this.affixService = affixService;
        this.messageManager = messageManager;
    }

    /** Opens the recipe book for a player at the first page. */
    public void open(final Player player) {
        ProfessionRecipeGUI.open(player, 0, professionManager, catalog);
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ProfessionRecipeHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.getOwnerId())
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        final String action = holder.recipeAt(event.getSlot());
        if (action == null) {
            return;
        }
        switch (action) {
            case "CLOSE" -> player.closeInventory();
            case "PREV" -> ProfessionRecipeGUI.open(player, holder.getPage() - 1, professionManager, catalog);
            case "NEXT" -> ProfessionRecipeGUI.open(player, holder.getPage() + 1, professionManager, catalog);
            default -> craft(player, action, holder.getPage());
        }
    }

    private void craft(final Player player, final String recipeId, final int page) {
        final ProfessionRecipeCatalog.Recipe recipe = catalog.get(recipeId);
        if (recipe == null) {
            return;
        }
        if (professionManager.getLevel(player, recipe.profession()) < recipe.level()) {
            player.sendMessage(messageManager.get("profession-recipe-level", "&cEhhez a recepthez magasabb szakma-szint kell."));
            return;
        }
        if (recipe.blueprint() && !professionManager.hasLearnedRecipe(player, recipe.id())) {
            player.sendMessage(messageManager.get("profession-recipe-not-learned", "&cEhhez a recepthez előbb meg kell szerezned a tervrajzot."));
            return;
        }
        if (!hasIngredients(player, recipe)) {
            player.sendMessage(messageManager.get("profession-recipe-missing", "&cNincs meg minden hozzávaló ehhez a recepthez."));
            return;
        }

        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            player.getInventory().removeItem(new ItemStack(entry.getKey(), entry.getValue()));
        }

        ItemStack result = new ItemStack(recipe.result(), recipe.resultAmount());
        // Roll a unique quality + affixes for single-item gear results (crafted tier).
        if (recipe.affixTier() != null && recipe.resultAmount() == 1) {
            result = affixService.roll(result, recipe.affixTier());
        }
        for (final ItemStack overflow : player.getInventory().addItem(result).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.2F);
        player.sendMessage(messageManager.get("profession-recipe-crafted", "&aElkészítetted: &e%s", recipe.displayName()));
        // Refresh so the ingredient counts / craftable states update.
        ProfessionRecipeGUI.open(player, page, professionManager, catalog);
    }

    private boolean hasIngredients(final Player player, final ProfessionRecipeCatalog.Recipe recipe) {
        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            if (!player.getInventory().contains(entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ProfessionRecipeHolder) {
            event.setCancelled(true);
        }
    }
}
