package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.InvseeManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Rendering-only part of the existing /invsee GUI; entity access stays in InvseeManager. */
public final class InvseeGUI {

    public static final int MAIN_ENDER_BUTTON = 45;
    public static final int MAIN_MODE_SLOT = 49;
    public static final int MAIN_CLOSE_SLOT = 53;
    public static final int ENDER_BACK_BUTTON = 36;
    public static final int ENDER_MODE_SLOT = 40;
    public static final int ENDER_CLOSE_SLOT = 44;

    private InvseeGUI() {
    }

    public static Inventory create(final InvseeHolder holder, final InvseeManager.Snapshot snapshot,
                                   final MessageManager messages) {
        final boolean edit = holder.mode() == InvseeHolder.Mode.EDIT;
        final String mode = edit ? "SZERKESZTÉS" : "CSAK OLVASÁS";
        final Component title = messages.getComponent("moderation.invsee.title",
                "&6» %s — %s (%s) «", holder.targetName(),
                holder.view() == InvseeHolder.View.MAIN ? "inventory" : "ender-láda", mode);
        final int size = holder.view() == InvseeHolder.View.MAIN ? 54 : 45;
        final Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        update(inventory, holder, snapshot, messages);
        return inventory;
    }

    public static void update(final Inventory inventory, final InvseeHolder holder,
                              final InvseeManager.Snapshot snapshot, final MessageManager messages) {
        if (holder.view() == InvseeHolder.View.MAIN) {
            final ItemStack[] storage = snapshot.storage();
            for (int slot = 0; slot < 36; slot++) {
                inventory.setItem(slot, cloneAt(storage, slot));
            }
            final ItemStack[] armor = snapshot.armor();
            for (int index = 0; index < 4; index++) {
                inventory.setItem(36 + index, cloneAt(armor, index));
            }
            inventory.setItem(40, cloneItem(snapshot.offHand()));
            for (int slot = 41; slot <= 44; slot++) {
                inventory.setItem(slot, GuiUtil.filler());
            }
            for (int slot = 45; slot < 54; slot++) {
                inventory.setItem(slot, GuiUtil.filler());
            }
            inventory.setItem(MAIN_ENDER_BUTTON, GuiUtil.icon(Material.ENDER_CHEST,
                    messages.getComponent("moderation.invsee.ender", "&dEnder-láda"),
                    List.of(messages.getComponent("moderation.invsee.live-hint", "&7Kattints az élő nézethez"))));
            inventory.setItem(MAIN_MODE_SLOT, modeIcon(holder.mode(), messages));
            inventory.setItem(MAIN_CLOSE_SLOT, GuiUtil.icon(Material.BARRIER,
                    messages.getComponent("moderation.invsee.close", "&cBezárás"), List.of()));
        } else {
            final ItemStack[] ender = snapshot.ender();
            for (int slot = 0; slot < 27; slot++) {
                inventory.setItem(slot, cloneAt(ender, slot));
            }
            for (int slot = 27; slot < 45; slot++) {
                inventory.setItem(slot, GuiUtil.filler());
            }
            inventory.setItem(ENDER_BACK_BUTTON, GuiUtil.icon(Material.ARROW,
                    messages.getComponent("moderation.invsee.back", "&eVissza az inventoryhoz"), List.of()));
            inventory.setItem(ENDER_MODE_SLOT, modeIcon(holder.mode(), messages));
            inventory.setItem(ENDER_CLOSE_SLOT, GuiUtil.icon(Material.BARRIER,
                    messages.getComponent("moderation.invsee.close", "&cBezárás"), List.of()));
        }
    }

    public static boolean isTargetSlot(final InvseeHolder.View view, final int rawSlot) {
        return view == InvseeHolder.View.MAIN ? rawSlot >= 0 && rawSlot <= 40
                : rawSlot >= 0 && rawSlot < 27;
    }

    private static ItemStack modeIcon(final InvseeHolder.Mode mode, final MessageManager messages) {
        if (mode == InvseeHolder.Mode.EDIT) {
            return GuiUtil.icon(Material.LIME_DYE,
                    messages.getComponent("moderation.invsee.mode-edit", "&aSZERKESZTÉS"),
                    List.of(messages.getComponent("moderation.invsee.edit-swap-hint",
                                    "&7A felső slot a kurzorral cserélhető."),
                            messages.getComponent("moderation.invsee.edit-audit-hint",
                                    "&7Minden módosítás auditálva van.")));
        }
        return GuiUtil.icon(Material.GRAY_DYE,
                messages.getComponent("moderation.invsee.mode-read", "&7CSAK OLVASÁS"),
                List.of(messages.getComponent("moderation.invsee.read-hint",
                        "&7A cél inventoryja nem módosítható.")));
    }

    private static ItemStack cloneAt(final ItemStack[] source, final int index) {
        return source == null || index < 0 || index >= source.length ? null : cloneItem(source[index]);
    }

    private static ItemStack cloneItem(final ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }
}
