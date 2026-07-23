package hu.taliann.icesmp.items;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

@SuppressWarnings({"unused", "UnstableApiUsage"})
public final class CurrencyItemFactory {

    private final ConfigManager configManager;
    private final NamespacedKey currencyTypeKey;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();

    public CurrencyItemFactory(final JavaPlugin plugin, final ConfigManager configManager) {
        this.configManager = configManager;
        this.currencyTypeKey = new NamespacedKey(plugin, "currency_type");
    }

    public ItemStack create(final CurrencyType currencyType, final long amount) {
        final int stackAmount = (int) Math.max(1L, Math.min(64L, amount));
        final ItemStack itemStack = new ItemStack(currencyType.getMaterial(), stackAmount);
        applyMeta(itemStack, currencyType);
        return itemStack;
    }

    public ItemStack refresh(final ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        final CurrencyType currencyType = getCurrencyType(itemStack);
        if (currencyType == null) {
            return itemStack;
        }

        applyMeta(itemStack, currencyType);
        return itemStack;
    }

    public boolean isCurrencyItem(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }

        final CurrencyType currencyType = getCurrencyType(itemStack);
        return currencyType != null && itemStack.getType() == currencyType.getMaterial();
    }

    public CurrencyType getCurrencyType(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }

        final String rawType = itemStack.getItemMeta().getPersistentDataContainer().get(currencyTypeKey, PersistentDataType.STRING);
        return CurrencyType.fromInput(rawType);
    }

    private void applyMeta(final ItemStack itemStack, final CurrencyType currencyType) {
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.displayName(serializer.deserialize(resolveDisplayName(currencyType)));
        meta.lore(List.of());

        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(currencyTypeKey, PersistentDataType.STRING, currencyType.name());

        itemStack.setItemMeta(meta);
        hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(itemStack,
                "icesmp:currency_" + currencyType.name().toLowerCase(java.util.Locale.ROOT));
    }

    private String resolveDisplayName(final CurrencyType currencyType) {
        final String basePath = "currency.item";
        final String configuredName = configManager.getString(basePath + ".types." + currencyType.name() + ".display-name", null);
        if (configuredName != null && !configuredName.isBlank()) {
            return TextUtil.color(configuredName.replace("%currency%", currencyType.getDisplayName()));
        }

        return switch (currencyType) {
            case RED -> TextUtil.color("&cParázsló Parals");
            case BLUE -> TextUtil.color("&9Hópihér-veret");
            case NEUTRAL -> TextUtil.color("&5Creutzér");
            case DARK -> TextUtil.color("&8Csontveret");
        };
    }
}


