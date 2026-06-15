package hu.taliann.icesmp.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Small shared helpers for the character menu GUIs (filler panes, icon builders),
 * so the individual menu classes stay focused on layout.
 */
public final class GuiUtil {

    private GuiUtil() {
    }

    public static void fill(final Inventory inventory) {
        final ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    public static ItemStack filler() {
        final ItemStack itemStack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        final ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(Component.empty());
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static ItemStack icon(final Material material, final Component name, final List<Component> lore) {
        return icon(material, name, lore, false);
    }

    public static ItemStack icon(final Material material, final Component name, final List<Component> lore,
                                 final boolean glow) {
        final ItemStack itemStack = new ItemStack(material);
        final ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }
}
