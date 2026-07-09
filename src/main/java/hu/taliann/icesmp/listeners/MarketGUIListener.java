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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public final class MarketGUIListener implements Listener {

    private final JavaPlugin plugin;
    private final MarketManager marketManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public MarketGUIListener(final JavaPlugin plugin, final MarketManager marketManager,
                             final CurrencyManager currencyManager,
                             final MessageManager messageManager) {
        this.plugin = plugin;
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
            MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage() - 1, holder.getFilter());
            return;
        }
        if (slot == MarketGUI.NEXT_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
            MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage() + 1, holder.getFilter());
            return;
        }

        final UUID listingId = holder.getListingAt(slot);
        if (listingId == null) {
            return;
        }

        final MarketManager.Listing listing = marketManager.getListing(listingId);
        if (listing != null && listing.auction()) {
            // Left-click = minimum next bid; right-click = a bigger quick raise;
            // shift-click = buy the auction out at its buy-out price (if it has one).
            final double amount;
            if (event.isShiftClick() && listing.hasBuyOut()) {
                amount = listing.buyOut();
            } else if (event.isRightClick()) {
                amount = marketManager.getBigBid(listing);
            } else {
                amount = marketManager.getMinimumBid(listing);
            }
            handleBid(player, holder, listingId, listing, amount);
            return;
        }

        final double paid = listing == null ? 0.0D : marketManager.getEffectivePrice(player, listing);
        final String errorKey = marketManager.buy(player, listingId);
        if (errorKey != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage(), holder.getFilter());
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
            // Folia: the seller is a different player (possibly in another region) than the buyer
            // whose click thread we are on — deliver the notice on the seller's own scheduler.
            final String buyerName = player.getName();
            seller.getScheduler().run(plugin, task -> seller.sendMessage(messageManager.getMessage(
                    "market-sold-notice",
                    "&aEladtad egy tárgyadat a piacon &f{buyer}&a részére — a bevétel a bankodba került.",
                    Map.of("buyer", buyerName)
            )), null);
        }

        MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage(), holder.getFilter());
    }

    /** Places {@code amount} on the clicked auction and refreshes the GUI. */
    private void handleBid(final Player player, final MarketHolder holder,
                           final UUID listingId, final MarketManager.Listing listing,
                           final double amount) {
        final MarketManager.BidOutcome outcome = marketManager.bid(player, listingId, amount);
        if (outcome.errorKey() != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messageManager.get(outcome.errorKey(), defaultErrorFor(outcome.errorKey())));
            MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage(), holder.getFilter());
            return;
        }

        if (outcome.boughtOut()) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
            player.sendMessage(messageManager.getMessage(
                    "market-buyout-success",
                    "&aMegvetted azonnal a buy-out áron: &f{price} {currency}&a — a tárgy a /market claim paranccsal átvehető.",
                    Map.of(
                            "price", currencyManager.formatBalance(outcome.amount()),
                            "currency", listing.currency().getDisplayName()
                    )
            ));
            notifyOutbid(outcome, listing);
            notifySellerSold(listing, player.getName());
            MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage(), holder.getFilter());
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.4F);
        player.sendMessage(messageManager.getMessage(
                "market-bid-success",
                "&aLicitáltál: &f{bid} {currency} &7(a bankodból zárolva; túllicitálásnál visszajár).",
                Map.of(
                        "bid", currencyManager.formatBalance(outcome.amount()),
                        "currency", listing.currency().getDisplayName()
                )
        ));

        notifyOutbid(outcome, listing);

        MarketGUI.open(player, marketManager, currencyManager, messageManager, holder.getPage(), holder.getFilter());
    }

    /** Notifies the previously-escrowed bidder (if any) that their bid was refunded. */
    private void notifyOutbid(final MarketManager.BidOutcome outcome, final MarketManager.Listing listing) {
        if (outcome.previousBidder() == null) {
            return;
        }
        // Folia: the outbid player may be in another region — hop to their scheduler.
        final Player outbid = Bukkit.getPlayer(outcome.previousBidder());
        if (outbid != null) {
            final String itemName = listing.item().getType().name();
            outbid.getScheduler().run(plugin, task -> outbid.sendMessage(messageManager.getMessage(
                    "market-outbid-notice",
                    "&cTúllicitáltak (&f{item}&c) — a zárolt licited visszakerült a bankodba.",
                    Map.of("item", itemName)
            )), null);
        }
    }

    /** Notifies the seller that a buy-out settled their auction immediately. */
    private void notifySellerSold(final MarketManager.Listing listing, final String buyerName) {
        final Player seller = Bukkit.getPlayer(listing.seller());
        if (seller != null) {
            seller.getScheduler().run(plugin, task -> seller.sendMessage(messageManager.getMessage(
                    "market-sold-notice",
                    "&aEladtad egy tárgyadat a piacon &f{buyer}&a részére — a bevétel a bankodba került.",
                    Map.of("buyer", buyerName)
            )), null);
        }
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "market-listing-gone" -> "&cEz a tétel már elkelt.";
            case "market-own-listing" -> "&cA saját tételedet nem veheted meg (visszavonás: /market cancel).";
            case "market-insufficient-balance" -> "&cNincs elég fedezet a bankodban ehhez a vásárláshoz.";
            case "market-auction-ended" -> "&cEz az aukció már lezárult.";
            case "market-already-highest" -> "&cMár te vagy a legmagasabb licitáló.";
            case "market-bid-too-low" -> "&cA licit nem éri el a minimum következő licitet.";
            case "market-auction-use-bid" -> "&cEz aukciós tétel — licitálni lehet rá.";
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
