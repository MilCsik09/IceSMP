package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.MarketManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Market browser: shows the newest listings (one page, capped at 45 items)
 * with price/seller lore; clicking buys from the player's bank balance.
 */
public final class MarketGUI {

    private static final int SIZE = 54;
    private static final int MAX_LISTINGS = 45;

    private MarketGUI() {
    }

    public static void open(final Player viewer, final MarketManager marketManager,
                            final CurrencyManager currencyManager, final MessageManager messageManager) {
        final Component title = messageManager.getComponent("messages.market-title", "&6» Piactér «");
        final MarketHolder holder = new MarketHolder(viewer.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        final List<MarketManager.Listing> listings = marketManager.getListingsSorted();
        for (int slot = 0; slot < Math.min(listings.size(), MAX_LISTINGS); slot++) {
            final MarketManager.Listing listing = listings.get(slot);
            inventory.setItem(slot, createDisplayItem(listing, currencyManager, messageManager));
            holder.mapSlot(slot, listing.id());
        }

        viewer.openInventory(inventory);
    }

    private static ItemStack createDisplayItem(final MarketManager.Listing listing,
                                               final CurrencyManager currencyManager,
                                               final MessageManager messageManager) {
        final ItemStack display = listing.item().clone();
        final ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        final List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(messageManager.getMessage(
                "market-lore-price",
                "&6Ár: &f{price} {currency}",
                Map.of(
                        "price", currencyManager.formatBalance(listing.price()),
                        "currency", listing.currency().getDisplayName()
                )
        ));
        lore.add(messageManager.getMessage(
                "market-lore-seller",
                "&7Eladó: &f{seller}",
                Map.of("seller", listing.sellerName())
        ));
        lore.add(messageManager.getMessage("market-lore-buy", "&eKattints a megvételhez!"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }
}
