package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.itemization.EquipmentProficiencyService;
import hu.taliann.icesmp.itemization.ItemTemplate;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * B54 — Elátkozott felszerelés. A curse gameplay-hozzájárulás ugyanazt az active-equipment
 * authorityt használja, mint a canonical stat/set/rune/signature/CombatPower útvonalak.
 */
public final class CursedGearService {

    /** A lore-sor kezdete, amiről az átok-sor felismerhető (törléskor is ez a marker). */
    private static final String CURSE_MARK = "☠ Átkozott";

    private record RuntimeSuppression(UUID playerId, ItemTemplate.Slot slot) { }

    private final ConfigManager configManager;
    private final NamespacedKey cursedKey;
    /**
     * Csak a confirmation-visszavétel overflow-eseteire: ha az itemet nincs hová rehome-olni,
     * a fizikai slotban maradhat, de a curse teljesen inert és levehető marad. Az ItemStack snapshot
     * biztosítja, hogy egy későbbi másik tárgy ne örökölje a transient tiltást.
     */
    private final ConcurrentHashMap<RuntimeSuppression, ItemStack> runtimeSuppressed =
            new ConcurrentHashMap<>();

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

    /** BASIC keeps its existing curse policy; managed canonical curse contributes only while ACTIVE. */
    public boolean isActiveCurse(final Player player, final ItemStack item,
                                 final ItemTemplate.Slot slot) {
        return isCursed(item)
                && !isRuntimeSuppressed(player, item, slot)
                && EquipmentProficiencyService.allowsGameplayContribution(player, item, slot);
    }

    public void suppressRuntimeCurse(final Player player, final ItemStack item,
                                     final ItemTemplate.Slot slot) {
        if (player == null || item == null || slot == null) return;
        runtimeSuppressed.put(new RuntimeSuppression(player.getUniqueId(), slot), item.clone());
    }

    public void clearRuntimeSuppression(final Player player, final ItemTemplate.Slot slot) {
        if (player != null && slot != null) {
            runtimeSuppressed.remove(new RuntimeSuppression(player.getUniqueId(), slot));
        }
    }

    /** Clears a stale transient block only when the physical slot no longer contains that item. */
    public void reconcileRuntimeSuppression(final Player player, final ItemStack current,
                                            final ItemTemplate.Slot slot) {
        if (player == null || slot == null) return;
        final RuntimeSuppression key = new RuntimeSuppression(player.getUniqueId(), slot);
        final ItemStack blocked = runtimeSuppressed.get(key);
        if (blocked != null && (current == null || current.getType().isAir() || !blocked.isSimilar(current))) {
            runtimeSuppressed.remove(key, blocked);
        }
    }

    public void clearRuntimeState(final UUID playerId) {
        if (playerId != null) runtimeSuppressed.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    private boolean isRuntimeSuppressed(final Player player, final ItemStack item,
                                        final ItemTemplate.Slot slot) {
        if (player == null || item == null || slot == null) return false;
        final ItemStack blocked = runtimeSuppressed.get(new RuntimeSuppression(player.getUniqueId(), slot));
        return blocked != null && blocked.isSimilar(item);
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

    /** Viselt/forgatott ACTIVE átkozott darabok száma (páncél + főkéz). */
    public int cursedPieceCount(final Player player) {
        if (player == null) return 0;
        int count = 0;
        if (isActiveCurse(player, player.getInventory().getHelmet(), ItemTemplate.Slot.HEAD)) count++;
        if (isActiveCurse(player, player.getInventory().getChestplate(), ItemTemplate.Slot.CHEST)) count++;
        if (isActiveCurse(player, player.getInventory().getLeggings(), ItemTemplate.Slot.LEGS)) count++;
        if (isActiveCurse(player, player.getInventory().getBoots(), ItemTemplate.Slot.FEET)) count++;
        if (isActiveCurse(player, player.getInventory().getItemInMainHand(), ItemTemplate.Slot.MAIN_HAND)) count++;
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

    /** A tudatos-felvétel megerősítés-ablaka millisecundumban (konfigból, élőben). */
    public long confirmMillis() {
        return Math.max(1L, configManager.getLong("item-rarity.cursed.confirm-seconds", 5L)) * 1000L;
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
