package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.CrateBrowserGUI;
import hu.taliann.icesmp.gui.CrateBrowserHolder;
import hu.taliann.icesmp.gui.GuiUtil;
import hu.taliann.icesmp.managers.CrateManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Owner-checking, read-only listener for the native crate list and preview GUI. */
public final class CrateBrowserGUIListener implements Listener {

    private final CrateManager crateManager;
    private final CurrencyManager currencyManager;

    public CrateBrowserGUIListener(final CrateManager crateManager, final CurrencyManager currencyManager) {
        this.crateManager = crateManager;
        this.currencyManager = currencyManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CrateBrowserHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.ownerId().equals(player.getUniqueId())
                || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        final String action = holder.actionAt(event.getRawSlot());
        if (action == null) {
            return;
        }
        if ("CLOSE".equals(action)) {
            GuiUtil.sound(player, GuiUtil.GuiSound.CLICK);
            player.closeInventory();
        } else if ("BACK".equals(action)) {
            CrateBrowserGUI.openList(player, crateManager, currencyManager);
        } else if (action.startsWith("PREVIEW:")) {
            CrateBrowserGUI.openPreview(player, crateManager, action.substring("PREVIEW:".length()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CrateBrowserHolder) {
            event.setCancelled(true);
        }
    }
}
