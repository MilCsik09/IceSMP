package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.ClaimTrustGUI;
import hu.taliann.icesmp.gui.ClaimTrustHolder;
import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

/**
 * Drives the claim-trust GUI: clicking a trusted head revokes trust, clicking a
 * nearby head grants it. Both delegate to /claim (un)trust — ClaimManager stays
 * the single owner of the actual trust-state change — and then rebuild the GUI.
 * The clicking player is on their own region thread (inventory events run on the
 * event-entity's region thread); the nearby-player names read here come from an
 * already-resolved {@code getNearbyPlayers} snapshot taken when the GUI opened,
 * so no extra Folia scheduler hop is needed for the click itself.
 */
public final class ClaimTrustGUIListener implements Listener {

    private final ClaimManager claimManager;
    private final MessageManager messageManager;

    public ClaimTrustGUIListener(final ClaimManager claimManager, final MessageManager messageManager) {
        this.claimManager = claimManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClaimTrustHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.getOwnerUuid().equals(player.getUniqueId())) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot == ClaimTrustGUI.BACK_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
            player.closeInventory();
            return;
        }

        final UUID trustedId = holder.getTrustedAt(slot);
        if (trustedId != null) {
            runAndReopen(player, "claim untrust ", trustedId);
            return;
        }

        final UUID nearbyId = holder.getNearbyAt(slot);
        if (nearbyId != null) {
            runAndReopen(player, "claim trust ", nearbyId);
        }
    }

    private void runAndReopen(final Player player, final String commandPrefix, final UUID targetId) {
        final String name = Bukkit.getOfflinePlayer(targetId).getName();
        if (name != null) {
            player.performCommand(commandPrefix + name);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
        ClaimTrustGUI.open(player, claimManager, messageManager);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClaimTrustHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ClaimTrustHolder holder) {
            holder.setInventory(null);
        }
    }
}
