package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.itemization.ItemMutationCoordinator;
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
 * id: {@code runa_*}) rákattintja a táskájában lévő fegyverre/páncélra. Canonical
 * Itemization 2.0 tárgynál a kurzorstack előbb tartósan visszakerül a player
 * inventoryba, majd a rúna-fogyasztás + target mutation egyetlen mutation-WAL
 * before/after snapshotban történik. A legacy compatibility itemek továbbra is
 * egyetlen {@code rune_effect} PDC-t visznek.
 */
public final class RuneApplyListener implements Listener {

    /** Legacy/projekciós PDC-kulcs; új tárgyon az ItemInstance rune-state az authority. */
    public static final NamespacedKey RUNE_PDC_KEY = NamespacedKey.fromString("icesmp:rune_effect");

    private final UniqueMaterialFactory uniqueMaterials;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService;

    public RuneApplyListener(final UniqueMaterialFactory uniqueMaterials,
                             final ConfigManager configManager, final MessageManager messageManager,
                             final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService) {
        this.uniqueMaterials = uniqueMaterials;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.itemIdentityService = itemIdentityService;
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

        final hu.taliann.icesmp.itemization.ItemIdentityService.Inspection inspection =
                itemIdentityService.inspect(target);
        if (inspection.status()
                != hu.taliann.icesmp.itemization.ItemIdentityService.Status.NOT_MANAGED) {
            event.setCancelled(true);
            if (inspection.status()
                    != hu.taliann.icesmp.itemization.ItemIdentityService.Status.VALID) {
                managedFailure(player, "rune-managed-invalid");
                return;
            }
            // The WAL snapshots PlayerInventory only. Never mutate a canonical item in an external
            // chest/container slot, otherwise the target could escape the transaction boundary.
            if (event.getClickedInventory() != player.getInventory()) {
                player.sendMessage(messageManager.getMessage("rune-managed-own-inventory",
                        "<red>◆ Canonical tárgyba rúnát csak a saját inventorydban helyezhetsz.</red>"));
                return;
            }
            final ItemMutationCoordinator coordinator = ItemMutationCoordinator.current();
            if (coordinator == null) {
                managedFailure(player, "rune-managed-invalid");
                return;
            }
            // Re-home the complete carried stack before the durable mutation. After this save the
            // payment itself is ordinary playerdata, so a crash cannot lose/duplicate a cursor rune.
            if (!rehomeCursor(player, event, cursor, runeId)) {
                player.sendMessage(messageManager.getMessage("rune-managed-rehome-failed",
                        "<red>◆ A rúna biztonságos eltárolása meghiúsult; semmi nem változott.</red>"));
                return;
            }
            coordinator.executeRune(player, event.getSlot(), runeId, outcome -> {
                if (!outcome.success()) {
                    managedFailure(player, outcome.messageKey());
                    player.sendMessage(messageManager.getMessage("rune-managed-payment-safe",
                            "<gray>A rúna nem veszett el; a sikertelen művelet után az inventorydban maradt.</gray>"));
                    return;
                }
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9F, 1.2F);
                player.sendMessage(messageManager.getMessage("rune-applied",
                        "<aqua>◆ A rúna a tárgyba égett: <white>{rune}</white> — a Mélység Népe bólint.</aqua>",
                        Map.of("rune", uniqueMaterials.displayName(runeId))));
            });
            return;
        }

        // Legacy compatibility path: one non-canonical rune marker, no ItemInstance authority.
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
        consumeCursor(event, cursor);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9F, 1.2F);
        player.sendMessage(messageManager.getMessage("rune-applied",
                "<aqua>◆ A rúna a tárgyba égett: <white>{rune}</white> — a Mélység Népe bólint.</aqua>",
                Map.of("rune", uniqueMaterials.displayName(runeId))));
    }

    private boolean rehomeCursor(final Player player, final InventoryClickEvent event,
                                 final ItemStack cursor, final String runeId) {
        final ItemStack carried = cursor.clone();
        if (!player.getInventory().addItem(carried).isEmpty()) return false;
        event.getView().setCursor(null);
        try {
            player.saveData();
            return true;
        } catch (final RuntimeException saveFailure) {
            removeUnique(player, runeId, carried.getAmount());
            event.getView().setCursor(cursor.clone());
            try { player.saveData(); } catch (final RuntimeException rollbackFailure) {
                saveFailure.addSuppressed(rollbackFailure);
            }
            return false;
        }
    }

    private void removeUnique(final Player player, final String runeId, final int amount) {
        int remaining = amount;
        final ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack item = contents[slot];
            if (item == null || !runeId.equals(uniqueMaterials.idOf(item))) continue;
            final int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            if (take == item.getAmount()) player.getInventory().setItem(slot, null);
            else item.setAmount(item.getAmount() - take);
        }
    }

    private void managedFailure(final Player player, final String key) {
        final String message = switch (key == null ? "" : key) {
            case "rune-managed-no-socket" -> "<red>◆ Ezen a tárgyon nincs rúnahely.</red>";
            case "rune-managed-full" -> "<red>◆ Ezen a tárgyon nincs szabad, használható rúnahely.</red>";
            case "itemization-operation-pending" ->
                    "<red>◆ Egy korábbi tárgyművelet recoveryre vár; előbb azt kell lezárni.</red>";
            case "itemization-duplicate" ->
                    "<red>◆ Duplikált canonical item-identitás miatt a rúna-művelet blokkolva.</red>";
            default -> "<red>◆ A tárgy identitása vagy a mutation állapot hibás; a rúna nem veszett el.</red>";
        };
        player.sendMessage(messageManager.getMessage(key == null || key.isBlank()
                ? "rune-managed-invalid" : key, message));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5F, 0.6F);
    }

    private static void consumeCursor(final InventoryClickEvent event, final ItemStack cursor) {
        cursor.setAmount(cursor.getAmount() - 1);
        event.getView().setCursor(cursor.getAmount() <= 0 ? null : cursor);
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
