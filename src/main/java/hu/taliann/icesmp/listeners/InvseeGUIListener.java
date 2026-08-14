package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.gui.InvseeGUI;
import hu.taliann.icesmp.gui.InvseeHolder;
import hu.taliann.icesmp.managers.InvseeManager;
import hu.taliann.icesmp.moderation.InvseeWriteCoordinator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** GUI-side routing for invsee; cross-entity work stays in InvseeManager. */
public final class InvseeGUIListener implements Listener {
    private final InvseeManager manager;

    public InvseeGUIListener(final InvseeManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvseeHolder holder)
                || !(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        final int topSize = event.getView().getTopInventory().getSize();
        final int rawSlot = event.getRawSlot();
        if (manager.hasPending(viewer, holder)) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot >= topSize) {
            if (holder.mode() == InvseeHolder.Mode.READ_ONLY) {
                event.setCancelled(true);
                return;
            }
            final InventoryAction action = event.getAction();
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.HOTBAR_SWAP
                    || action == InventoryAction.COLLECT_TO_CURSOR
                    || action == InventoryAction.UNKNOWN) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (rawSlot < 0) {
            return;
        }
        if ((holder.view() == InvseeHolder.View.MAIN
                && rawSlot == InvseeGUI.MAIN_ENDER_BUTTON)
                || (holder.view() == InvseeHolder.View.ENDER
                && rawSlot == InvseeGUI.ENDER_BACK_BUTTON)) {
            manager.switchView(viewer, holder);
            return;
        }
        if ((holder.view() == InvseeHolder.View.MAIN
                && rawSlot == InvseeGUI.MAIN_CLOSE_SLOT)
                || (holder.view() == InvseeHolder.View.ENDER
                && rawSlot == InvseeGUI.ENDER_CLOSE_SLOT)) {
            manager.closeFromGui(viewer, holder);
            return;
        }
        if (holder.mode() == InvseeHolder.Mode.EDIT
                && viewer.hasPermission(Permissions.MODERATION_INVENTORY_EDIT)
                && InvseeWriteCoordinator.canWrite(viewer, holder.targetId())
                && InvseeGUI.isTargetSlot(holder.view(), rawSlot)) {
            manager.beginSwap(viewer, holder, rawSlot);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(final InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvseeHolder)) {
            return;
        }
        final int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof InvseeHolder holder
                && event.getPlayer() instanceof Player viewer) {
            manager.handleClose(viewer, holder);
            InvseeWriteCoordinator.releaseAfterClose(viewer, holder);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        InvseeWriteCoordinator.releasePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDisable(final PluginDisableEvent event) {
        if (event.getPlugin() == JavaPlugin.getProvidingPlugin(InvseeGUIListener.class)) {
            InvseeWriteCoordinator.reset();
        }
    }
}
