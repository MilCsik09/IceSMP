package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.items.UniqueMaterialFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Anyag-alapú hozzávalók EGYETLEN egyeztető szerződése: a készlet-számolás és a fogyasztás
 * ugyanezt a predikátumot használja.
 *
 * <p><b>Miért közös:</b> ahol a két fázis külön definíció szerint döntött (a számolás típus
 * szerint, a fogyasztás {@code Inventory#removeItem}-mel, azaz {@code isSimilar}-ral), ott
 * check–consume rés keletkezett: a követelményt fedezte olyan tárgy, amit a levonás nem talált
 * meg, és a hozzávaló INGYEN maradt. Ez a szakma-craftot és a rituálé-áldozatot egyaránt érintette.
 *
 * <p><b>Mi számít hozzávalónak:</b> a kopás/sérülés NEM zavar (a használt szerszám is jó
 * hozzávaló), de bármilyen IDENTITÁS-jel kizárja: unique-material, PDC-bélyeg (signature,
 * „Készítette”), saját név vagy lore. Az ilyen tárgyak nem tűnhetnek el hozzávalóként — és nem is
 * fedezhetik a követelményt.
 *
 * <p><b>Folia:</b> minden metódus a JÁTÉKOS inventoryját olvassa/írja, tehát a játékos saját
 * régió-szálán hívandó.
 */
public final class PlainIngredients {

    private PlainIngredients() {
    }

    /**
     * Elfogadható-e a tárgy a megadott anyag hozzávalójaként.
     *
     * @param unique a unique-material felismerő; {@code null} esetén a unique-kizárás kimarad
     */
    public static boolean matches(final ItemStack item, final Material material,
                                  final UniqueMaterialFactory unique) {
        if (item == null || item.getType() != material) {
            return false;
        }
        if (unique != null && unique.idOf(item) != null) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return true;
        }
        final ItemMeta meta = item.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasLore() && meta.getPersistentDataContainer().isEmpty();
    }

    /** Hány elfogadható darab van a játékosnál. */
    public static int count(final Player player, final Material material,
                            final UniqueMaterialFactory unique) {
        int count = 0;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (matches(item, material, unique)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Levonás pontosan azokból a tárgyakból, amiket a {@link #count} beszámított. A művelet
     * ATOMIKUS: ha nincs elég, semmit nem vesz el.
     *
     * @return true, ha a teljes mennyiség elfogyott
     */
    public static boolean consume(final Player player, final Material material, final int amount,
                                  final UniqueMaterialFactory unique) {
        if (amount <= 0) {
            return true;
        }
        if (count(player, material, unique) < amount) {
            return false;
        }
        int remaining = amount;
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack item = contents[slot];
            if (!matches(item, material, unique)) {
                continue;
            }
            final int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
            remaining -= take;
        }
        return remaining == 0;
    }
}
