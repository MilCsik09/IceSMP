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
 * Read/write donation chest.
 *
 * <p>The empty top row is a one-way deposit zone. Shared donation entries are
 * rendered below it and remain claimable by clicking.</p>
 */
public final class DonationChestGUI {

    private static final int SIZE = 54;
    public static final int DEPOSIT_START = 0;
    public static final int DEPOSIT_END = 8;
    public static final int CONTENT_START = 9;
    public static final int CONTENT_END = 44;
    private static final int PER_PAGE =
            CONTENT_END - CONTENT_START + 1;

    public static final int HELP_SLOT = 45;
    public static final int PREV_SLOT = 48;
    public static final int PAGE_INFO_SLOT = 49;
    public static final int NEXT_SLOT = 50;
    public static final int MENU_SLOT = 53;

    private DonationChestGUI() {
    }

    public static void open(final Player viewer,
                            final DonationChestManager manager,
                            final MessageManager messages) {
        open(viewer, manager, messages, 0);
    }

    public static void open(final Player viewer,
                            final DonationChestManager manager,
                            final MessageManager messages,
                            final int page) {
        final List<DonationChestManager.DonationEntry> donations =
                manager.getEntriesSorted();
        final int totalPages = Math.max(1,
                (int) Math.ceil(donations.size() / (double) PER_PAGE));
        final int safePage =
                Math.max(0, Math.min(page, totalPages - 1));

        final Component title = messages.getComponent(
                "messages.donation-chest-title",
                "&6» Adomány-láda — húzd be felül «");
        final DonationChestHolder holder =
                new DonationChestHolder(viewer.getUniqueId());
        holder.setPage(safePage);
        final Inventory inventory =
                Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        // 0..8 intentionally stay empty: this is the visible input row.
        for (int slot = CONTENT_START; slot <= CONTENT_END; slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }

        final int start = safePage * PER_PAGE;
        for (int index = 0;
             index < PER_PAGE && start + index < donations.size();
             index++) {
            final DonationChestManager.DonationEntry entry =
                    donations.get(start + index);
            final int slot = CONTENT_START + index;
            inventory.setItem(slot, createDisplayItem(entry, messages));
            holder.mapSlot(slot, entry.id());
        }

        for (int slot = 45; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }
        inventory.setItem(HELP_SLOT, depositHelp(messages));
        if (safePage > 0) {
            inventory.setItem(PREV_SLOT,
                    nav(Material.ARROW, "« Előző oldal"));
        }
        if (safePage < totalPages - 1) {
            inventory.setItem(NEXT_SLOT,
                    nav(Material.ARROW, "Következő oldal »"));
        }
        inventory.setItem(PAGE_INFO_SLOT, nav(Material.PAPER,
                "Oldal " + (safePage + 1) + "/" + totalPages
                        + " — " + donations.size() + " adomány"));
        inventory.setItem(MENU_SLOT,
                nav(Material.ARROW, "Főmenü (/menu)"));

        viewer.openInventory(inventory);
    }

    public static boolean isDepositSlot(final int rawSlot) {
        return rawSlot >= DEPOSIT_START && rawSlot <= DEPOSIT_END;
    }

    public static boolean isContentSlot(final int rawSlot) {
        return rawSlot >= CONTENT_START && rawSlot <= CONTENT_END;
    }

    public static void removeClaimedEntry(final DonationChestHolder holder,
                                          final java.util.UUID entryId) {
        final Integer slot = holder.removeEntry(entryId);
        if (slot != null && holder.getInventory().getSize() == SIZE) {
            holder.getInventory().setItem(slot, GuiUtil.filler());
        }
    }

    private static ItemStack depositHelp(
            final MessageManager messages) {
        final ItemStack item = new ItemStack(Material.HOPPER);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Adományozás",
                        NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                messages.getMessage("donation-deposit-drag",
                        "&eHúzd a tárgyat a felső üres sorba."),
                messages.getMessage("donation-deposit-shift",
                        "&eVagy SHIFT+kattints a saját inventorydban."),
                messages.getMessage("donation-deposit-partial",
                        "&7Jobb katt: 1 db; bal katt: teljes stack.")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createDisplayItem(
            final DonationChestManager.DonationEntry entry,
            final MessageManager messages) {
        final ItemStack display = entry.item();
        final ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }
        final List<Component> lore = meta.lore() == null
                ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(messages.getMessage("donation-lore-donor",
                "&7Adományozó: &f{donor}",
                Map.of("donor", entry.donorName())));
        lore.add(messages.getMessage("donation-lore-take",
                "&eKattints az elvételhez!"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private static ItemStack nav(final Material material,
                                 final String label) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
