package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.CrateSpinHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Drives the crate-spin reveal GUI ({@link hu.taliann.icesmp.gui.CrateSpinGUI}).
 * The GUI is purely cosmetic (the reward was already rolled and credited before it ever
 * opens), so there is nothing to click — every click/drag on it is just cancelled. The one
 * real job here is stopping the animation's {@code runDelayed} chain when the player closes
 * the inventory early or logs out, via the holder's volatile {@code cancelled} flag (the
 * next chained step checks it before touching the inventory again).
 *
 * <p>Folia: inventory events run on the viewing player's own region thread, matching the
 * animation chain in {@link hu.taliann.icesmp.gui.CrateSpinGUI} — no scheduler hop needed.
 */
public final class CrateSpinGUIListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CrateSpinHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CrateSpinHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CrateSpinHolder holder) {
            hu.taliann.icesmp.gui.CrateSpinGUI.cancel(holder.getOwnerUuid());
        }
    }
}
