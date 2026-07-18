package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.InvseeGUI;
import hu.taliann.icesmp.gui.InvseeHolder;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Drives the read-only {@code /invsee} GUI: every click is cancelled — this is a
 * pillanatkép-nézet, nem élő inventory —, the only interactive slots are the ender-chest button
 * (main view) and the back button (ender view), both of which just re-render the ALREADY-CAPTURED
 * snapshot stored on the holder. No further entity access happens here, so no Folia scheduler hop
 * is needed anywhere in this listener — the clicking viewer is on their own region thread and the
 * snapshot arrays are plain data.
 */
public final class InvseeGUIListener implements Listener {

    private final MessageManager messageManager;

    public InvseeGUIListener(final MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvseeHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }

        final int slot = event.getRawSlot();
        if (holder.getView() == InvseeHolder.View.MAIN && slot == InvseeGUI.ENDER_BUTTON_SLOT) {
            hu.taliann.icesmp.gui.GuiUtil.sound(viewer, hu.taliann.icesmp.gui.GuiUtil.GuiSound.PAGE);
            InvseeGUI.openEnder(viewer, holder, messageManager);
        } else if (holder.getView() == InvseeHolder.View.MAIN && slot == InvseeGUI.REFRESH_SLOT) {
            // Új pillanatkép — a parancs-útvonal fut újra (jogosultság-ellenőrzéssel
            // és a célpont-száli snapshottal együtt); nézőnként 2 mp-es fék a hop-spam ellen.
            final long now = System.currentTimeMillis();
            final Long last = refreshDebounce.get(viewer.getUniqueId());
            if (last == null || now - last >= 2000L) {
                refreshDebounce.put(viewer.getUniqueId(), now);
                hu.taliann.icesmp.gui.GuiUtil.sound(viewer, hu.taliann.icesmp.gui.GuiUtil.GuiSound.CLICK);
                viewer.performCommand("invsee " + holder.getTargetName());
            }
        } else if (holder.getView() == InvseeHolder.View.ENDER && slot == InvseeGUI.BACK_SLOT) {
            hu.taliann.icesmp.gui.GuiUtil.sound(viewer, hu.taliann.icesmp.gui.GuiUtil.GuiSound.PAGE);
            InvseeGUI.openMain(viewer, holder, messageManager);
        }
    }

    /** Frissítés-fék nézőnként (a performCommand célpont-száli hopot indít). */
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> refreshDebounce =
            new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InvseeHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof InvseeHolder holder) {
            holder.setInventory(null);
        }
    }
}
