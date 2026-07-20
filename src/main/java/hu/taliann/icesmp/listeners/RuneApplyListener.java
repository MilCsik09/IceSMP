package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * B26 — rúna-felhelyezés: a játékos a KURZORÁN tartott rúnát (unique material,
 * id: {@code runa_*}) rákattintja a táskájában lévő fegyverre/páncélra — a cél
 * PDC-jébe kerül a {@code rune_effect} (a hatás-listener ezt olvassa), a lore
 * egy rúna-sort kap, a rúna elfogy. Tárgyanként EGY rúna (nem cserélhető —
 * a döntés súlya a lore-hoz illik). A cél-típusokat a crafting.yml
 * {@code runes.<id>.applies} listája szabja meg (weapon/bow/chest).
 * Folia: az InventoryClickEvent a játékos saját régió-szálán fut.
 */
public final class RuneApplyListener implements Listener {

    /** A felrúnázott tárgy PDC-kulcsa (értéke a rúna-id) — a hatás-listener is ezt olvassa. */
    public static final NamespacedKey RUNE_PDC_KEY = NamespacedKey.fromString("icesmp:rune_effect");

    private final UniqueMaterialFactory uniqueMaterials;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public RuneApplyListener(final UniqueMaterialFactory uniqueMaterials,
                             final ConfigManager configManager, final MessageManager messageManager) {
        this.uniqueMaterials = uniqueMaterials;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final ItemStack cursor = event.getCursor();
        final String runeId = uniqueMaterials.idOf(cursor);
        if (runeId == null || !runeId.startsWith("runa_")) {
            return;
        }
        final ItemStack target = event.getCurrentItem();
        if (target == null || target.getType().isAir() || uniqueMaterials.idOf(target) != null) {
            return;
        }
        if (!appliesTo(runeId, target.getType())) {
            return; // nem cél-tárgy: hagyjuk a normál inventory-műveletet futni
        }
        event.setCancelled(true);
        final ItemMeta meta = target.getItemMeta();
        if (meta == null) {
            return;
        }
        if (meta.getPersistentDataContainer().has(RUNE_PDC_KEY, PersistentDataType.STRING)) {
            player.sendMessage(messageManager.getMessage("rune-already",
                    "<red>◆ Ezen a tárgyon már él egy rúna — a vésetek nem tűrik egymást.</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5F, 0.6F);
            return;
        }
        meta.getPersistentDataContainer().set(RUNE_PDC_KEY, PersistentDataType.STRING, runeId);
        final List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.text("◆ " + configManager.getString(
                        "runes." + runeId + ".display", uniqueMaterials.displayName(runeId)),
                        NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        target.setItemMeta(meta);
        // A kurzor-fogyasztást explicit setCursor-ral rögzítjük: a cancel-elt event után a
        // kliens a szerver-oldali kurzort kapja vissza — az in-place mutáció önmagában nem
        // minden Paper-verzión propagál (végtelen rúna-dupe lenne).
        cursor.setAmount(cursor.getAmount() - 1);
        event.getView().setCursor(cursor.getAmount() <= 0 ? null : cursor);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9F, 1.2F);
        player.sendMessage(messageManager.getMessage("rune-applied",
                "<aqua>◆ A rúna a tárgyba égett: <white>{rune}</white> — a Mélység Népe bólint.</aqua>",
                Map.of("rune", uniqueMaterials.displayName(runeId))));
    }

    /** A rúna cél-típus szabálya (crafting.yml runes.<id>.applies: weapon|bow|chest). */
    private boolean appliesTo(final String runeId, final Material material) {
        final String name = material.name();
        for (final String scope : configManager.getStringList("runes." + runeId + ".applies")) {
            switch (scope.toLowerCase(Locale.ROOT)) {
                case "weapon" -> {
                    if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("TRIDENT")) {
                        return true;
                    }
                }
                case "bow" -> {
                    if (name.equals("BOW") || name.equals("CROSSBOW")) {
                        return true;
                    }
                }
                case "chest" -> {
                    if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
                        return true;
                    }
                }
                default -> { }
            }
        }
        return false;
    }
}
