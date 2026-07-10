package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.DonationChestGUI;
import hu.taliann.icesmp.gui.DonationChestHolder;
import hu.taliann.icesmp.managers.DonationChestManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Drives the donation chest GUI: donating the held item, taking a donated item, and page
 * navigation. The clicking player is on their own region thread (inventory events always run
 * on the event-entity's region thread), so no Folia scheduler hop is needed anywhere here —
 * both the donor and the taker act on their own inventory only.
 */
public final class DonationChestListener implements Listener {

    private final DonationChestManager donationChestManager;
    private final MessageManager messageManager;

    public DonationChestListener(final DonationChestManager donationChestManager,
                                 final MessageManager messageManager) {
        this.donationChestManager = donationChestManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DonationChestHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.getOwnerUuid().equals(player.getUniqueId())) {
            return;
        }

        final int slot = event.getRawSlot();
        switch (slot) {
            case DonationChestGUI.ADD_SLOT -> handleAdd(player, holder);
            case DonationChestGUI.PREV_SLOT -> reopen(player, holder.getPage() - 1);
            case DonationChestGUI.NEXT_SLOT -> reopen(player, holder.getPage() + 1);
            case DonationChestGUI.MENU_SLOT -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                player.performCommand("menu");
            }
            default -> handleTake(player, holder, slot);
        }
    }

    private void handleAdd(final Player player, final DonationChestHolder holder) {
        final String errorKey = donationChestManager.donateHeldItem(player);
        if (errorKey != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey),
                    Map.of("limit", String.valueOf(donationChestManager.getMaxPerPlayer()))));
            reopen(player, holder.getPage());
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
        player.sendMessage(messageManager.getMessage("donation-add-success",
                "&aA kezedben tartott tárgyat az adomány-ládába tetted — bárki elveheti."));
        reopen(player, holder.getPage());
    }

    private void handleTake(final Player player, final DonationChestHolder holder, final int slot) {
        final UUID entryId = holder.getEntryAt(slot);
        if (entryId == null) {
            return;
        }

        // Atomically claimed FIRST — if two players click the same tile at once, only one of
        // these calls gets a non-null item back, so the other simply sees "already taken".
        final ItemStack item = donationChestManager.takeEntry(entryId);
        if (item == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messageManager.get("donation-take-gone", "&cEzt a tárgyat már elvitte valaki más."));
            reopen(player, holder.getPage());
            return;
        }

        final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
        player.sendMessage(messageManager.get("donation-take-success", "&aElvettél egy adományt az adomány-ládából."));
        reopen(player, holder.getPage());
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "donation-chest-disabled" -> "&cAz adomány-láda jelenleg ki van kapcsolva.";
            case "donation-no-item" -> "&cNincs tárgy a kezedben — ezt adományoznád?";
            case "donation-chest-full" -> "&cAz adomány-láda megtelt — próbáld később, ha valaki elvitt valamit.";
            case "donation-per-player-limit" -> "&cElérted a saját adomány-limitedet ebben a ládában (&f{limit} tétel&c).";
            default -> "&cAz adományozás nem sikerült.";
        };
    }

    private void reopen(final Player player, final int page) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
        DonationChestGUI.open(player, donationChestManager, messageManager, Math.max(0, page));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DonationChestHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof DonationChestHolder holder) {
            holder.setInventory(null);
        }
    }
}
