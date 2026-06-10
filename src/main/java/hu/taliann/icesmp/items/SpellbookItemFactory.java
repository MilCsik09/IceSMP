package hu.taliann.icesmp.items;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class SpellbookItemFactory {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final int SPELLBOOK_CUSTOM_MODEL_DATA = 5201;

    private final NamespacedKey isSpellbookKey;
    private final NamespacedKey uniqueIdKey;

    public SpellbookItemFactory(final JavaPlugin plugin) {
        this.isSpellbookKey = new NamespacedKey(plugin, "is_spellbook");
        this.uniqueIdKey = new NamespacedKey(plugin, "unique_id");
    }

    public ItemStack createSpellbook() {
        final ItemStack itemStack = new ItemStack(Material.BOOK);
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        meta.displayName(MINI_MESSAGE.deserialize("<light_purple><bold>M\u00e1gikus Var\u00e1zsk\u00f6nyv</bold></light_purple>"));
        meta.lore(List.of());

        final CustomModelDataComponent customModelDataComponent = meta.getCustomModelDataComponent();
        customModelDataComponent.setFloats(List.of((float) SPELLBOOK_CUSTOM_MODEL_DATA));
        meta.setCustomModelDataComponent(customModelDataComponent);

        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(isSpellbookKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(uniqueIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public boolean isSpellbook(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }

        return Boolean.TRUE.equals(itemStack.getItemMeta().getPersistentDataContainer().get(isSpellbookKey, PersistentDataType.BOOLEAN));
    }
}


