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

    public ItemStack createHeartItem(final int amount) {
        return create(amount, "heart", Material.ECHO_SHARD, "<dark_green>Nyughatatlan Szív</dark_green>",
                "<gray>Egy el nem porladt szív, amely még emlékszik a parancsszóra —</gray>",
                "<gray>éjjel, szabad ég alatt idézd meg vele a ghúlodat. (Szentségtelen)</gray>");
    }

    public ItemStack createSealItem(final int amount) {
        return create(amount, "seal", Material.AMETHYST_SHARD, "<dark_purple>Démon-pecsét</dark_purple>",
                "<gray>A fátylon túlról zárt alku pecsétje — éjjel törd fel,</gray>",
                "<gray>és a démon a szolgálatodba áll. (Boszorkánymester)</gray>");
    }

    public ItemStack createPetArmorItem(final int amount) {
        return create(amount, "pet_armor", Material.LEATHER_HORSE_ARMOR, "<gold>Társvért</gold>",
                "<gray>Mesterien szabott vért, amely bármely társ testére igazodik —</gray>",
                "<gray>jobb katt a saját társadon: páncélt és életerőt kap. (minden társ-tartó)</gray>");
    }

    public boolean isPetArmorItem(final ItemStack item) {
        return "pet_armor".equals(tagOf(item));
    }

    public boolean isHeartItem(final ItemStack item) {
        return "heart".equals(tagOf(item));
    }

    public boolean isSealItem(final ItemStack item) {
        return "seal".equals(tagOf(item));
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
        item.setItemMeta(meta);
        hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(item, "icesmp:capture_" + tag);
        return item;
    }
}
