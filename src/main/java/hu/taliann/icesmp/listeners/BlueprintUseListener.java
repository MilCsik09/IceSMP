package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.BlueprintItemFactory;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Learns a blueprint recipe when the player right-clicks a blueprint item: the recipe id is read
 * from the item's PDC, recorded on the player ({@link ProfessionManager#learnRecipe}), and one
 * blueprint is consumed. Already-known blueprints are rejected without consuming the item.
 */
public final class BlueprintUseListener implements Listener {

    private final BlueprintItemFactory blueprintFactory;
    private final ProfessionRecipeCatalog catalog;
    private final ProfessionManager professionManager;
    private final MessageManager messageManager;

    public BlueprintUseListener(final BlueprintItemFactory blueprintFactory, final ProfessionRecipeCatalog catalog,
                                final ProfessionManager professionManager, final MessageManager messageManager) {
        this.blueprintFactory = blueprintFactory;
        this.catalog = catalog;
        this.professionManager = professionManager;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        final Player player = event.getPlayer();
        final ItemStack item = player.getInventory().getItemInMainHand();
        final String recipeId = blueprintFactory.recipeIdOf(item);
        if (recipeId == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        final ProfessionRecipeCatalog.Recipe recipe = catalog.get(recipeId);
        if (recipe == null) {
            player.sendMessage(messageManager.get("blueprint-unknown", "&cEz a tervrajz nem tartozik ismert recepthez."));
            return;
        }
        if (!professionManager.learnRecipe(player, recipeId)) {
            player.sendMessage(messageManager.get("blueprint-already-known", "&7Ezt a receptet már ismered."));
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
        player.sendMessage(messageManager.get("blueprint-learned", "&aÚj receptet tanultál: &e%s&a! (Recept-könyv: /profession recipes)", recipe.displayName()));
    }
}
