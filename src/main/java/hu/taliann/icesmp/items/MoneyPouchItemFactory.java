package hu.taliann.icesmp.items;

import hu.taliann.icesmp.data.CurrencyType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kopott erszény — fizikai "talált pénz" tárgy (WoW-stílusú mob-drop / horgász-lelet).
 * A pénz NEM közvetlenül íródik jóvá: az erszény tárgyként esik (mob-loot, horgászat,
 * admin-adás), és jobb-kattra váltható be a benne lévő darabszámú FIZIKAI veretre (token-item) — a SZÁMLÁRA pénz
 * KIZÁRÓLAG a banki befizetésen át kerülhet! A valuta és az összeg az erszény létrehozásakor
 * eldől és PDC-ben stabilan megmarad, de bontásig szándékosan rejtve marad a játékos előtt.
 */
public final class MoneyPouchItemFactory {

    private static final int CUSTOM_MODEL_DATA = 1010;

    private final NamespacedKey valueKey;
    private final NamespacedKey currencyKey;

    public MoneyPouchItemFactory(final JavaPlugin plugin) {
        this.valueKey = new NamespacedKey(plugin, "pouch_value");
        this.currencyKey = new NamespacedKey(plugin, "pouch_currency");
    }

    /** Erszény véletlen valutával (mob-drop / horgász-lelet útja). */
    public ItemStack createRandom(final long value) {
        final CurrencyType[] all = CurrencyType.values();
        return create(all[ThreadLocalRandom.current().nextInt(all.length)], value);
    }

    public ItemStack create(final CurrencyType currency, final long value) {
        final long rounded = Math.max(1L, value);
        final ItemStack stack = new ItemStack(Material.LEATHER, 1);
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text("💰 Kopott erszény", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Valami csörög odabent...", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Jobb-katt: bontsd ki az erszényt.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("„Valaki elvesztette. Most a tiéd.”", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true)));
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(valueKey, PersistentDataType.LONG, rounded);
        pdc.set(currencyKey, PersistentDataType.STRING, currency.name());
        stack.setItemMeta(meta);
        hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(stack, "icesmp:money_pouch");
        return stack;
    }

    public boolean isPouch(final ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(valueKey, PersistentDataType.LONG);
    }

    public long getValue(final ItemStack stack) {
        if (!isPouch(stack)) {
            return 0L;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .getOrDefault(valueKey, PersistentDataType.LONG, 0L);
    }

    public CurrencyType getCurrency(final ItemStack stack) {
        if (!isPouch(stack)) {
            return null;
        }
        return CurrencyType.fromInput(stack.getItemMeta().getPersistentDataContainer()
                .get(currencyKey, PersistentDataType.STRING));
    }
}
