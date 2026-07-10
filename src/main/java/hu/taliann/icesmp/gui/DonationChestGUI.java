package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.DonationChestManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * Community donation chest browser (paginated, like {@link MarketGUI}): every donated item
 * fills the top rows, the bottom row holds an "Adományozás" tile that donates the viewer's
 * held item, page navigation and a Főmenü back button. Pure gifting — clicking an item tile
 * takes it, no price involved.
 */
public final class DonationChestGUI {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    public static final int ADD_SLOT = 45;
    public static final int PREV_SLOT = 48;
    public static final int PAGE_INFO_SLOT = 49;
    public static final int NEXT_SLOT = 50;
    public static final int MENU_SLOT = 53;

    private DonationChestGUI() {
    }

    public static void open(final Player viewer, final DonationChestManager donationChestManager,
                            final MessageManager messageManager) {
        open(viewer, donationChestManager, messageManager, 0);
    }

    public static void open(final Player viewer, final DonationChestManager donationChestManager,
                            final MessageManager messageManager, final int page) {
        final List<DonationChestManager.DonationEntry> donations = donationChestManager.getEntriesSorted();
        final int totalPages = Math.max(1, (int) Math.ceil(donations.size() / (double) PER_PAGE));
        final int safePage = Math.max(0, Math.min(page, totalPages - 1));

        final Component title = messageManager.getComponent("messages.donation-chest-title", "&6» Adomány-láda «");
        final DonationChestHolder holder = new DonationChestHolder(viewer.getUniqueId());
        holder.setPage(safePage);
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        final int start = safePage * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < donations.size(); i++) {
            final DonationChestManager.DonationEntry entry = donations.get(start + i);
            inventory.setItem(i, createDisplayItem(entry, messageManager));
            holder.mapSlot(i, entry.id());
        }

        for (int slot = PER_PAGE; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }

        inventory.setItem(ADD_SLOT, addTile(messageManager));
        if (safePage > 0) {
            inventory.setItem(PREV_SLOT, nav(Material.ARROW, "« Előző oldal"));
        }
        if (safePage < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, nav(Material.ARROW, "Következő oldal »"));
        }
        inventory.setItem(PAGE_INFO_SLOT, nav(Material.PAPER,
                "Oldal " + (safePage + 1) + "/" + totalPages + " — " + donations.size() + " adomány"));
        inventory.setItem(MENU_SLOT, nav(Material.ARROW, "Főmenü (/menu)"));

        viewer.openInventory(inventory);
    }

    private static ItemStack addTile(final MessageManager messageManager) {
        final ItemStack item = new ItemStack(Material.HOPPER);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Adományozás", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(messageManager.getMessage("donation-tile-info",
                "&eAdományozás: &ffogd kézbe a tárgyat és kattints ide!")));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createDisplayItem(final DonationChestManager.DonationEntry entry,
                                               final MessageManager messageManager) {
        final ItemStack display = entry.item().clone();
        final ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        final List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(messageManager.getMessage("donation-lore-donor", "&7Adományozó: &f{donor}",
                Map.of("donor", entry.donorName() == null ? "?" : entry.donorName())));
        lore.add(messageManager.getMessage("donation-lore-take", "&eKattints az elvételhez!"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private static ItemStack nav(final Material material, final String label) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
