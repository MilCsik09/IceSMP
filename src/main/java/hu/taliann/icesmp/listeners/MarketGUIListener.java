package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.MarketGUI;
import hu.taliann.icesmp.gui.MarketHolder;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.MarketManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;
import java.util.UUID;

public final class MarketGUIListener implements Listener {

    private final MarketManager marketManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public MarketGUIListener(final MarketManager marketManager, final CurrencyManager currencyManager,
                             final MessageManager messageManager) {
        this.marketManager = marketManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MarketHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.getOwnerUuid().equals(player.getUniqueId())) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot == MarketGUI.PREV_SLOT && holder.getPage() > 0) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
            MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage() - 1);
            return;
        }
        if (slot == MarketGUI.NEXT_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
            MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage() + 1);
            return;
        }

        final UUID listingId = holder.getListingAt(slot);
        if (listingId == null) {
            return;
        }

        final MarketManager.Listing listing = marketManager.getListing(listingId);
        final double paid = listing == null ? 0.0D : marketManager.getEffectivePrice(player, listing);
        final String errorKey = marketManager.buy(player, listingId);
        if (errorKey != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage());
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
        player.sendMessage(messageManager.getMessage(
                "market-buy-success",
                "&aMegvetted: &f{price} {currency} &7(a bankodból).",
                Map.of(
                        "price", currencyManager.formatBalance(paid),
                        "currency", listing.currency().getDisplayName()
                )
        ));

        final Player seller = Bukkit.getPlayer(listing.seller());
        if (seller != null) {
            seller.sendMessage(messageManager.getMessage(
                    "market-sold-notice",
                    "&aEladtad egy tárgyadat a piacon &f{buyer}&a részére — a bevétel a bankodba került.",
                    Map.of("buyer", player.getName())
            ));
        }

        MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage());
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "market-listing-gone" -> "&cEz a tétel már elkelt.";
            case "market-own-listing" -> "&cA saját tételedet nem veheted meg (visszavonás: /market cancel).";
            case "market-insufficient-balance" -> "&cNincs elég fedezet a bankodban ehhez a vásárláshoz.";
            default -> "&cA vásárlás nem sikerült.";
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MarketHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MarketHolder holder) {
            holder.setInventory(null);
        }
    }
}
