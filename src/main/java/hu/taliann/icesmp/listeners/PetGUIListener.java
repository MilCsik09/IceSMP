package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.PetGUI;
import hu.taliann.icesmp.gui.PetGUIHolder;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Drives the companion GUI: every click delegates to an existing /pet subcommand
 * (PetManager stays the single owner of pet state) and then rebuilds the GUI so
 * the stance/status tiles always show live state.
 */
public final class PetGUIListener implements Listener {

    private final PetManager petManager;
    private final MessageManager messageManager;

    public PetGUIListener(final PetManager petManager, final MessageManager messageManager) {
        this.petManager = petManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PetGUIHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.getOwnerUuid().equals(player.getUniqueId())) {
            return;
        }

        final String action = holder.getActionAt(event.getRawSlot());
        if (action == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);

        if ("CLOSE".equals(action)) {
            player.closeInventory();
            return;
        }
        if (action.startsWith("HINT:")) {
            player.closeInventory();
            player.sendMessage(messageManager.get("pet-name-usage", "&cHasználat: /pet name <név>"));
            return;
        }
        if (action.startsWith("RUN:")) {
            player.performCommand(action.substring("RUN:".length()));
            PetGUI.open(player, petManager, messageManager);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PetGUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PetGUIHolder holder) {
            holder.setInventory(null);
        }
    }
}
