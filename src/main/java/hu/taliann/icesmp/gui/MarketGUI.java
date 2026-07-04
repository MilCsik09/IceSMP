package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.MarketManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Market browser with pagination (ROADMAP phase 12): listings fill the top rows,
 * the bottom row holds page navigation. Clicking a listing buys it from the
 * player's bank balance.
 */
public final class MarketGUI {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    public static final int PREV_SLOT = 45;
    public static final int PAGE_INFO_SLOT = 49;
    public static final int NEXT_SLOT = 53;

    private MarketGUI() {
    }

    public static void open(final Player viewer, final MarketManager marketManager,
                            final CurrencyManager currencyManager, final MessageManager messageManager) {
        open(viewer, marketManager, currencyManager, messageManager, 0, null);
    }

    public static void open(final Player viewer, final MarketManager marketManager,
                            final CurrencyManager currencyManager, final MessageManager messageManager, final int page) {
        open(viewer, marketManager, currencyManager, messageManager, page, null);
    }

    public static void open(final Player viewer, final MarketManager marketManager,
                            final CurrencyManager currencyManager, final MessageManager messageManager,
                            final int page, final String filter) {
        final List<MarketManager.Listing> listings = new ArrayList<>();
        for (final MarketManager.Listing listing : marketManager.getListingsSorted()) {
            if (matchesFilter(listing, filter)) {
                listings.add(listing);
            }
        }
        final int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) PER_PAGE));
        final int safePage = Math.max(0, Math.min(page, totalPages - 1));

        final Component title = messageManager.getComponent("messages.market-title", "&6» Piactér «");
        final MarketHolder holder = new MarketHolder(viewer.getUniqueId());
        holder.setPage(safePage);
        holder.setFilter(filter);
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        final int start = safePage * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < listings.size(); i++) {
            final MarketManager.Listing listing = listings.get(start + i);
            inventory.setItem(i, createDisplayItem(viewer, marketManager, listing, currencyManager, messageManager));
            holder.mapSlot(i, listing.id());
        }

        // Bottom navigation row.
        for (int slot = PER_PAGE; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }
        if (safePage > 0) {
            inventory.setItem(PREV_SLOT, nav(Material.ARROW, "« Előző oldal"));
        }
        if (safePage < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, nav(Material.ARROW, "Következő oldal »"));
        }
        inventory.setItem(PAGE_INFO_SLOT, nav(Material.PAPER,
                "Oldal " + (safePage + 1) + "/" + totalPages + " — " + listings.size() + " tétel"));

        viewer.openInventory(inventory);
    }

    private static boolean matchesFilter(final MarketManager.Listing listing, final String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        final String needle = filter.toLowerCase(java.util.Locale.ROOT);
        final ItemStack item = listing.item();
        if (item.getType().name().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            final String name = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName())
                    .toLowerCase(java.util.Locale.ROOT);
            return name.contains(needle);
        }
        return false;
    }

    private static ItemStack nav(final Material material, final String label) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createDisplayItem(final Player viewer, final MarketManager marketManager,
                                               final MarketManager.Listing listing,
                                               final CurrencyManager currencyManager,
                                               final MessageManager messageManager) {
        final ItemStack display = listing.item().clone();
        final ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        final List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        if (listing.auction()) {
            // Auctions are settled by absolute bids, so no reputation multiplier applies.
            if (listing.hasBid()) {
                lore.add(messageManager.getMessage(
                        "market-lore-current-bid",
                        "&6Aktuális licit: &f{bid} {currency} &7({bidder})",
                        Map.of(
                                "bid", currencyManager.formatBalance(listing.highestBid()),
                                "currency", listing.currency().getDisplayName(),
                                "bidder", listing.highestBidderName() == null ? "?" : listing.highestBidderName()
                        )
                ));
            } else {
                lore.add(messageManager.getMessage(
                        "market-lore-start-price",
                        "&6Kikiáltási ár: &f{price} {currency}",
                        Map.of(
                                "price", currencyManager.formatBalance(listing.price()),
                                "currency", listing.currency().getDisplayName()
                        )
                ));
            }
            lore.add(messageManager.getMessage(
                    "market-lore-time-left",
                    "&7Hátralévő idő: &f{time}",
                    Map.of("time", formatRemaining(listing.endsAt() - System.currentTimeMillis()))
            ));
            lore.add(messageManager.getMessage(
                    "market-lore-seller",
                    "&7Eladó: &f{seller}",
                    Map.of("seller", listing.sellerName())
            ));
            lore.add(messageManager.getMessage(
                    "market-lore-bid",
                    "&eKattints a licitáláshoz! &7(min. {next} {currency})",
                    Map.of(
                            "next", currencyManager.formatBalance(marketManager.getMinimumBid(listing)),
                            "currency", listing.currency().getDisplayName()
                    )
            ));
        } else {
            // Show the price the VIEWER will actually pay (faction reputation adjusts it), not the
            // base list price — otherwise the lore and the charged amount disagree.
            final double effectivePrice = marketManager.getEffectivePrice(viewer, listing);
            lore.add(messageManager.getMessage(
                    "market-lore-price",
                    "&6Ár: &f{price} {currency}",
                    Map.of(
                            "price", currencyManager.formatBalance(effectivePrice),
                            "currency", listing.currency().getDisplayName()
                    )
            ));
            lore.add(messageManager.getMessage(
                    "market-lore-seller",
                    "&7Eladó: &f{seller}",
                    Map.of("seller", listing.sellerName())
            ));
            lore.add(messageManager.getMessage("market-lore-buy", "&eKattints a megvételhez!"));
        }
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    /** Formats a remaining-time millis span as a compact Hungarian "1n 2ó 3p" string. */
    public static String formatRemaining(final long millis) {
        final long totalMinutes = Math.max(0L, millis / 60_000L);
        final long days = totalMinutes / (24L * 60L);
        final long hours = (totalMinutes / 60L) % 24L;
        final long minutes = totalMinutes % 60L;
        final StringBuilder text = new StringBuilder();
        if (days > 0) {
            text.append(days).append("n ");
        }
        if (hours > 0) {
            text.append(hours).append("ó ");
        }
        text.append(minutes).append("p");
        return text.toString();
    }
}
