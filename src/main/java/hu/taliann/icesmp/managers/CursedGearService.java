package hu.taliann.icesmp.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * B54 — Elátkozott felszerelés (lore: az Ócska-átok és az Első Csend-érintette tárgyak —
 * kódex I. + item-rarity). BOSS-forrású gear-loot ritkán "Átkozott": erősebb (a viselője
 * bónusz-sebzést kap), de FELVÉVE NEM ERESZT — a páncél nem vehető le szabadon, csak a
 * rituálé-oltár Átok-törése ({@code uncurse} rituálé-típus) oldja. A felvétel tudatos
 * döntés: az első felhelyezési kísérlet figyelmeztet, csak a gyors második erősít meg.
 *
 * <p>Állapot: PDC-flag az itemen ({@code cursed}) + figyelmeztető lore-sorok. A viselkedést
 * a {@code CursedGearListener} adja; a curse-stamp a MobLootListener boss-ágából jön.
 * Minden kulcs élőben olvasódik (item-rarity.cursed.*).
 */
public final class CursedGearService {

    /** A lore-sor kezdete, amiről az átok-sor felismerhető (törléskor is ez a marker). */
    private static final String CURSE_MARK = "☠ Átkozott";

    private final ConfigManager configManager;
    private final NamespacedKey cursedKey;

    public CursedGearService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.configManager = configManager;
        this.cursedKey = new NamespacedKey(plugin, "cursed");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("item-rarity.cursed.enabled", true);
    }

    public boolean isCursed(final ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(cursedKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /**
     * Boss-forrású gear-drop átok-sorsolása (a MobLootListener hívja a roll után):
     * chance eséllyel a tárgy Átkozottá válik — PDC-flag + figyelmeztető lore.
     * Csak nem-stackelhető (gear) tárgyra fut.
     */
    public ItemStack maybeCurse(final ItemStack item) {
        if (!isEnabled() || item == null || item.getMaxStackSize() != 1 || isCursed(item)) {
            return item;
        }
        final double chance = Math.max(0.0D, Math.min(1.0D,
                configManager.getDouble("item-rarity.cursed.chance", 0.08D)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return item;
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(cursedKey, PersistentDataType.BYTE, (byte) 1);
        final List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.text(CURSE_MARK + " — az Első Csend érintése", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Erőt ad (+" + (int) Math.round(damageBonusPerPiece() * 100.0D)
                        + "% sebzés), de felvéve nem ereszt —", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.text("csak az oltár Átok-törése oldja le.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Viselt/forgatott Átkozott darabok száma (páncél + főkéz) — a sebzés-bónusz alapja. */
    public int cursedPieceCount(final Player player) {
        int count = 0;
        for (final ItemStack armor : player.getInventory().getArmorContents()) {
            if (isCursed(armor)) {
                count++;
            }
        }
        if (isCursed(player.getInventory().getItemInMainHand())) {
            count++;
        }
        return count;
    }

    /** Az átok ereje: darabonkénti kimenő sebzés-bónusz (konfigból, élőben). */
    public double damageBonusPerPiece() {
        return Math.max(0.0D, configManager.getDouble("item-rarity.cursed.damage-bonus-per-piece", 0.1D));
    }

    /** A darabonkénti bónuszok összegének plafonja. */
    public double damageBonusCap() {
        return Math.max(0.0D, configManager.getDouble("item-rarity.cursed.damage-bonus-cap", 0.4D));
    }

    /**
     * Átok-törés (az oltár {@code uncurse} rituáléja): a flag törlődik, az átok-sorok
     * helyére a megtört átok emléke kerül — a tárgy megtartja az erejét jelző nevét,
     * de a bónusz és a levételi zár megszűnik.
     *
     * @return true, ha a tárgy átkozott volt és megtört
     */
    public boolean breakCurse(final ItemStack item) {
        if (!isCursed(item)) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(cursedKey);
        final List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        // Az átok-sorok (marker + a két magyarázó sor) eltávolítása a plain-szöveg alapján.
        lore.removeIf(line -> {
            final String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(line);
            return plain.startsWith(CURSE_MARK) || plain.startsWith("Erőt ad (+")
                    || plain.startsWith("csak az oltár Átok-törése");
        });
        lore.add(Component.text("☠ Megtört átok — az Első Csend elengedte.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        meta.lore(lore);
        item.setItemMeta(meta);
        return true;
    }
}
