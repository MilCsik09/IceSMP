package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.ShopGUI;
import hu.taliann.icesmp.gui.ShopHolder;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.ShopManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Drives the faction shop GUI: a click on a sale entry buys it through the
 * ShopManager (money sink), then refreshes so the player can keep shopping.
 * Inventory clicks run on the clicking player's own region thread, so the
 * currency deduction and item grant are Folia-safe without a hop.
 */
public final class ShopListener implements Listener {

    private final ShopManager shopManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public ShopListener(final ShopManager shopManager, final CurrencyManager currencyManager,
                        final MessageManager messageManager) {
        this.shopManager = shopManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.getOwnerUuid().equals(player.getUniqueId())) {
            return;
        }

        final int itemIndex = holder.getItemIndexAt(event.getRawSlot());
        if (itemIndex < 0) {
            return;
        }

        final String errorKey = shopManager.buy(player, holder.getNpcName(), itemIndex);
        if (errorKey != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0F, 1.1F);
        }

        // Re-open to reflect the updated balance / keep shopping (shop may have closed).
        if (shopManager.hasShop(holder.getNpcName())) {
            ShopGUI.open(player, shopManager, currencyManager, messageManager, holder.getNpcName());
        } else {
            player.closeInventory();
        }
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "shop-closed" -> "&cEz a bolt zárva van.";
            case "shop-wrong-faction" -> "&cEbben a boltban csak a saját frakciója tagjai vásárolhatnak.";
            case "shop-item-gone" -> "&cEz a tétel már nem elérhető.";
            case "shop-insufficient" -> "&cNincs elég fedezet a bankodban ehhez a vásárláshoz.";
            default -> "&cA vásárlás nem sikerült.";
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ShopHolder holder) {
            holder.setInventory(null);
        }
    }
}
