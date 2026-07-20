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
 * admin-adás), és jobb-kattra váltható be a benne lévő ÖSSZEGRE és VALUTÁRA, amelyeket
 * PDC hordoz. A valuta sorsoláskor dől el (bármelyik frakció-veret lehet — a Kapu
 * mindenhonnan sodor pénzt), így a beváltás árfolyam-játékot is kínál.
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
    public ItemStack createRandom(final double value) {
        final CurrencyType[] all = CurrencyType.values();
        return create(all[ThreadLocalRandom.current().nextInt(all.length)], value);
    }

    public ItemStack create(final CurrencyType currency, final double value) {
        final double rounded = Math.max(0.01D, Math.floor(value * 100.0D) / 100.0D);
        final ItemStack stack = new ItemStack(Material.LEATHER, 1);
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text("💰 Kopott erszény", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(formatValue(rounded) + " " + currency.getDisplayName(), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Jobb-katt: a tartalma a számládra kerül.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("„Valaki elvesztette. Most a tiéd.”", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true)));
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(valueKey, PersistentDataType.DOUBLE, rounded);
        pdc.set(currencyKey, PersistentDataType.STRING, currency.name());
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isPouch(final ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(valueKey, PersistentDataType.DOUBLE);
    }

    public double getValue(final ItemStack stack) {
        if (!isPouch(stack)) {
            return 0.0D;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .getOrDefault(valueKey, PersistentDataType.DOUBLE, 0.0D);
    }

    public CurrencyType getCurrency(final ItemStack stack) {
        if (!isPouch(stack)) {
            return null;
        }
        return CurrencyType.fromInput(stack.getItemMeta().getPersistentDataContainer()
                .get(currencyKey, PersistentDataType.STRING));
    }

    private static String formatValue(final double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
