package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.ShopManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Faction shop GUI: shows each sale entry as its actual item stack with a
 * price line; clicking buys it from the player's bank (the currency burns —
 * money sink). A view over {@link ShopManager}; buying goes through it from
 * {@link hu.taliann.icesmp.listeners.ShopListener}.
 */
public final class ShopGUI {

    private ShopGUI() {
    }

    public static void open(final Player viewer, final ShopManager shopManager,
                            final CurrencyManager currencyManager, final MessageManager messageManager,
                            final String npcName) {
        final List<ConfigurationSection> items = shopManager.getItems(npcName);
        final int rows = Math.max(1, Math.min(6, (int) Math.ceil(items.size() / 9.0)));
        final int size = rows * 9;

        final Component title = messageManager.getComponent("messages.shop-title-prefix", "&2Bolt: ")
                .append(Component.text(shopManager.getTitle(npcName)));
        final ShopHolder holder = new ShopHolder(viewer.getUniqueId(), npcName);
        final Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);

        for (int i = 0; i < items.size() && i < size; i++) {
            final ConfigurationSection item = items.get(i);
            final Material material = shopManager.getMaterial(item);
            if (material == null) {
                continue;
            }
            inventory.setItem(i, createDisplayItem(viewer, shopManager, currencyManager, messageManager, item, material));
            holder.mapSlot(i, i);
        }

        viewer.openInventory(inventory);
    }

    private static ItemStack createDisplayItem(final Player viewer, final ShopManager shopManager,
                                               final CurrencyManager currencyManager, final MessageManager messageManager,
                                               final ConfigurationSection item, final Material material) {
        final int amount = shopManager.getAmount(item);
        final ItemStack display = new ItemStack(material, Math.min(64, amount));
        final ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        final double price = shopManager.getPrice(item);
        final String currencyName = shopManager.resolveCurrency(item, viewer).getDisplayName();
        final List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(messageManager.getMessage(
                "shop-lore-price",
                "&6Ár: &f{price} {currency} &7/ {amount} db",
                java.util.Map.of(
                        "price", currencyManager.formatBalance(price),
                        "currency", currencyName,
                        "amount", String.valueOf(amount)
                )
        ));
        lore.add(messageManager.getMessage("shop-lore-buy", "&eKattints a vásárláshoz!"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        // A bolt-tétel konfigurált neve nyer; enélkül a KLIENS saját nyelvű tárgyneve —
        // a nyers Material enum-név belső azonosító, annak nincs helye a magyar boltban.
        final String configuredName = item.getString("name");
        meta.displayName(configuredName != null && !configuredName.isBlank()
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(configuredName)
                        .decoration(TextDecoration.ITALIC, false)
                : Component.translatable(material).color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
        display.setItemMeta(meta);
        return display;
    }
}
