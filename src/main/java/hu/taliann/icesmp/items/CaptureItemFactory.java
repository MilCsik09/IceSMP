package hu.taliann.icesmp.items;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * The capture items used to tame a companion: the Beast Master's taming leash (any non-hostile animal) and the Necromancer's
 * soul binder (any hostile mob / undead). Both are PDC-tagged and consumed on a
 * successful capture.
 */
public final class CaptureItemFactory {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final NamespacedKey captureKey;

    public CaptureItemFactory(final JavaPlugin plugin) {
        this.captureKey = new NamespacedKey(plugin, "capture_item");
    }

    public ItemStack createBeastItem(final int amount) {
        return create(amount, "beast", Material.LEAD, "<dark_green>Ősi Kötés Póráza</dark_green>",
                "<gray>Aetrinita és Kallan természet-kötése — jobb katt egy állaton: a társaddá fogadod.</gray>", "<gray>(Vadmester)</gray>");
    }

    public ItemStack createNecroItem(final int amount) {
        return create(amount, "necro", Material.GHAST_TEAR, "<dark_purple>Sötét Paktum-tekercs</dark_purple>",
                "<gray>Eleftheria mérgének paktuma — jobb katt egy szörnyön: szolgáddá köti.</gray>", "<gray>(Nekromanta)</gray>");
    }

    public boolean isBeastCapture(final ItemStack item) {
        return "beast".equals(tagOf(item));
    }

    public boolean isNecroCapture(final ItemStack item) {
        return "necro".equals(tagOf(item));
    }

    public boolean isCaptureItem(final ItemStack item) {
        return tagOf(item) != null;
    }

    private String tagOf(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(captureKey, PersistentDataType.STRING);
    }

    private ItemStack create(final int amount, final String tag, final Material material,
                             final String name, final String lore, final String spec) {
        final ItemStack item = new ItemStack(material, Math.max(1, amount));
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MINI.deserialize(lore).decoration(TextDecoration.ITALIC, false),
                MINI.deserialize(spec).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(captureKey, PersistentDataType.STRING, tag);
        meta.setCustomModelData("beast".equals(tag) ? 5301 : 5302); // docs/RESOURCE_PACK_CMD.md
        item.setItemMeta(meta);
        return item;
    }
}
