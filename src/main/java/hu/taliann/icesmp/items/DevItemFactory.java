package hu.taliann.icesmp.items;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Creates and identifies owner-bound developer items. DEV items are deliberately separate from
 * relics: they never participate in relic ownership, inactivity expiry or PvP transfer.
 */
public final class DevItemFactory {

    public static final String BINGULUS_ID = "csodalatos_bingulus";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ConfigManager configManager;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey instanceKey;

    public DevItemFactory(final JavaPlugin plugin, final ConfigManager configManager) {
        this.configManager = configManager;
        this.itemIdKey = new NamespacedKey(plugin, "dev_item_id");
        this.ownerKey = new NamespacedKey(plugin, "dev_item_owner");
        this.instanceKey = new NamespacedKey(plugin, "dev_item_instance");
    }

    public ItemStack createBingulus(final UUID owner, final UUID instanceId) {
        final String base = "dev-items." + BINGULUS_ID + ".item.";
        final Material material = Material.matchMaterial(
                configManager.getString(base + "material", "HEART_OF_THE_SEA").toUpperCase(Locale.ROOT));
        final ItemStack item = new ItemStack(material == null || material.isAir() ? Material.HEART_OF_THE_SEA : material);
        final ItemMeta meta = item.getItemMeta();

        meta.displayName(LEGACY.deserialize(configManager.getString(
                        base + "display-name", "&d&lCsodálatos Bingulus"))
                .decoration(TextDecoration.ITALIC, false));

        final List<Component> lore = new ArrayList<>();
        for (final String line : configManager.getStringList(base + "lore")) {
            lore.add(LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore.isEmpty() ? null : lore);

        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemIdKey, PersistentDataType.STRING, BINGULUS_ID);
        pdc.set(ownerKey, PersistentDataType.STRING, owner.toString());
        pdc.set(instanceKey, PersistentDataType.STRING, instanceId.toString());
        item.setItemMeta(meta);

        final String model = configManager.getString(base + "item-model", "icesmp:csodalatos_bingulus");
        if (!model.isBlank()) {
            ItemDataFactory.applyItemModel(item, model);
        }
        return item;
    }

    public boolean isDevItem(final ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemIdKey, PersistentDataType.STRING);
    }

    public boolean isBingulus(final ItemStack item) {
        return BINGULUS_ID.equals(itemIdOf(item));
    }

    public String itemIdOf(final ItemStack item) {
        if (!isDevItem(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    public UUID ownerOf(final ItemStack item) {
        return uuidValue(item, ownerKey);
    }

    public UUID instanceOf(final ItemStack item) {
        return uuidValue(item, instanceKey);
    }

    private UUID uuidValue(final ItemStack item, final NamespacedKey key) {
        if (!isDevItem(item)) {
            return null;
        }
        final String raw = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }
}
