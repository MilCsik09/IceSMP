package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.ProfessionRecipeGUI;
import hu.taliann.icesmp.gui.ProfessionRecipeHolder;
import hu.taliann.icesmp.managers.ItemRarityService;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Click handling and crafting for the profession recipe-book GUI. A left click on a craftable
 * recipe verifies the profession level, the learned state (for blueprint recipes) and the
 * ingredients, then consumes them and grants the result — rolled through {@link ItemRarityService}
 * on the recipe's {@code affix-tier} when set, so crafted gear comes out unique. All inventory
 * touches are on the clicking player's own region thread (Folia-safe).
 */
public final class ProfessionRecipeBookListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ProfessionManager professionManager;
    private final ProfessionRecipeCatalog catalog;
    private final ItemRarityService affixService;
    private final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials;
    private final MessageManager messageManager;

    public ProfessionRecipeBookListener(final ProfessionManager professionManager, final ProfessionRecipeCatalog catalog,
                                        final ItemRarityService affixService,
                                        final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials,
                                        final MessageManager messageManager) {
        this.professionManager = professionManager;
        this.catalog = catalog;
        this.affixService = affixService;
        this.uniqueMaterials = uniqueMaterials;
        this.messageManager = messageManager;
    }

    /** Opens the recipe book for a player at the first page. */
    public void open(final Player player) {
        ProfessionRecipeGUI.open(player, 0, professionManager, catalog, uniqueMaterials);
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
            case "PREV" -> ProfessionRecipeGUI.open(player, holder.getPage() - 1, professionManager, catalog, uniqueMaterials);
            case "NEXT" -> ProfessionRecipeGUI.open(player, holder.getPage() + 1, professionManager, catalog, uniqueMaterials);
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
        consumeUnique(player, recipe);

        ItemStack result = recipe.uniqueResult() != null
                ? uniqueMaterials.create(recipe.uniqueResult(), recipe.resultAmount())
                : new ItemStack(recipe.result(), recipe.resultAmount());
        if (result == null) {
            return;
        }
        // Named prestige items (gear / tome / special consumable): stamp the designed name + lore so the
        // crafted item matches the recipe book and the mob-loot naming model. Bulk results carry no lore
        // and stay vanilla + stackable. Unique materials already carry their own name/lore from the factory.
        if (recipe.uniqueResult() == null && recipe.lore() != null && !recipe.lore().isEmpty()) {
            final ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                meta.displayName(LEGACY.deserialize(recipe.displayName())
                        .colorIfAbsent(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                final List<Component> loreLines = new ArrayList<>();
                for (final String line : recipe.lore()) {
                    loreLines.add(LEGACY.deserialize(line)
                            .colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(loreLines);
                result.setItemMeta(meta);
            }
        }
        // Roll a unique quality + affixes for single-item gear results (crafted tier). The roll keeps the
        // stamped display name (prefixing the rarity) and appends the affix lines below the lore.
        if (recipe.affixTier() != null && recipe.resultAmount() == 1) {
            result = affixService.roll(result, recipe.affixTier());
        }
        for (final ItemStack overflow : player.getInventory().addItem(result).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.2F);
        player.sendMessage(messageManager.get("profession-recipe-crafted", "&aElkészítetted: &e%s", recipe.displayName()));
        // Refresh so the ingredient counts / craftable states update.
        ProfessionRecipeGUI.open(player, page, professionManager, catalog, uniqueMaterials);
    }

    private boolean hasIngredients(final Player player, final ProfessionRecipeCatalog.Recipe recipe) {
        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            if (countPlain(player, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        for (final Map.Entry<String, Integer> entry : recipe.uniqueIngredients().entrySet()) {
            if (countUnique(player, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Counts plain items of a material, EXCLUDING unique materials that share the base type. */
    private int countPlain(final Player player, final Material material) {
        int count = 0;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && uniqueMaterials.idOf(item) == null) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private int countUnique(final Player player, final String uniqueId) {
        int count = 0;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && uniqueId.equals(uniqueMaterials.idOf(item))) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void consumeUnique(final Player player, final ProfessionRecipeCatalog.Recipe recipe) {
        for (final Map.Entry<String, Integer> entry : recipe.uniqueIngredients().entrySet()) {
            int remaining = entry.getValue();
            final ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                final ItemStack item = contents[slot];
                if (item == null || !entry.getKey().equals(uniqueMaterials.idOf(item))) {
                    continue;
                }
                final int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
            }
        }
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ProfessionRecipeHolder) {
            event.setCancelled(true);
        }
    }
}
