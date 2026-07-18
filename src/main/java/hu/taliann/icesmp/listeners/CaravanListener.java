package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.ShopGUI;
import hu.taliann.icesmp.managers.CaravanManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.ShopManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Opens the caravan shop when a player right-clicks the merchant caravan entity.
 * Cancelling the interact suppresses the wandering trader's native trade GUI, so
 * only our currency-sink shop opens. The event fires on the interacting player's
 * own region thread, so opening the inventory is Folia-safe without a hop.
 */
public final class CaravanListener implements Listener {

    private final CaravanManager caravanManager;
    private final ShopManager shopManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public CaravanListener(final CaravanManager caravanManager, final ShopManager shopManager,
                           final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.caravanManager = caravanManager;
        this.shopManager = shopManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // Fire once (main hand only).
        }
        if (!caravanManager.isCaravanEntity(event.getRightClicked().getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        final Player player = event.getPlayer();
        if (!shopManager.hasShop(CaravanManager.SHOP_NAME)) {
            // Caravan just left, or no stock configured.
            player.sendMessage(messageManager.get("caravan-closed", "&7A karaván már bezárta a standját."));
            return;
        }
        ShopGUI.open(player, shopManager, currencyManager, messageManager, CaravanManager.SHOP_NAME);
    }
}
