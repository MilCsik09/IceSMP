package hu.taliann.icesmp.items;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Factory for crate-key items (native crate system, IDEAS B8 — replaces
 * CrazyCrates). A key is a config-themed item (material/name/custom-model-data
 * come from {@code config/crates.yml}) tagged with the {@code crate_key} PDC
 * key so {@link hu.taliann.icesmp.listeners.CrateListener} and
 * {@link hu.taliann.icesmp.managers.CrateManager} can identify which crate it
 * opens without parsing the display name.
 */
@SuppressWarnings("deprecation")
public final class CrateKeyFactory {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private final ConfigManager configManager;
    private final NamespacedKey crateKeyIdKey;

    public CrateKeyFactory(final JavaPlugin plugin, final ConfigManager configManager) {
        this.configManager = configManager;
        this.crateKeyIdKey = new NamespacedKey(plugin, "crate_key");
    }

    /**
     * Creates a stack of keys for the given crate, themed from {@code config/crates.yml}.
     *
     * @param crateId the crate id (config/crates.yml crates.&lt;id&gt;)
     * @param amount the stack size (clamped to 1..64)
     * @return the key item, or an AIR stack if the crate id is unknown
     */
    public ItemStack createKey(final String crateId, final int amount) {
        if (crateId == null || crateId.isBlank()) {
            return new ItemStack(Material.AIR);
        }
        final String basePath = "crates." + crateId;
        final Material material = Material.matchMaterial(configManager.getString(basePath + ".key-material", "TRIPWIRE_HOOK"));
        if (material == null || material.isAir()) {
            return new ItemStack(Material.AIR);
        }

        final ItemStack itemStack = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }

        final String keyName = configManager.getString(basePath + ".key-name", "&fLáda Kulcs");
        meta.displayName(SERIALIZER.deserialize(TextUtil.color(keyName)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                SERIALIZER.deserialize(TextUtil.color("&8Láda-kulcs")).decoration(TextDecoration.ITALIC, false),
                SERIALIZER.deserialize(TextUtil.color("&7Jobb katt a ládán: &fkinyitás")).decoration(TextDecoration.ITALIC, false)
        ));

        final int customModelData = configManager.getInt(basePath + ".key-custom-model-data", 0);
        if (customModelData > 0) {
            final CustomModelDataComponent customModelDataComponent = meta.getCustomModelDataComponent();
            customModelDataComponent.setFloats(List.of((float) customModelData));
            meta.setCustomModelDataComponent(customModelDataComponent);
        }

        meta.getPersistentDataContainer().set(crateKeyIdKey, PersistentDataType.STRING, crateId);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    /** Whether the item is any crate key. */
    public boolean isKey(final ItemStack itemStack) {
        return keyCrateId(itemStack) != null;
    }

    /** The crate id this key opens, or null if the item is not a crate key. */
    public String keyCrateId(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(crateKeyIdKey, PersistentDataType.STRING);
    }
}
