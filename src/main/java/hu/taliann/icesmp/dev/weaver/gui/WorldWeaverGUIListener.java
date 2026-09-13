package hu.taliann.icesmp.dev.weaver.gui;

import hu.taliann.icesmp.dev.weaver.WorldWeaverKernel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

public final class WorldWeaverGUIListener implements Listener {
    private final WorldWeaverKernel kernel;
    public WorldWeaverGUIListener(final WorldWeaverKernel kernel) { this.kernel = java.util.Objects.requireNonNull(kernel); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void click(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WorldWeaverHolder holder)) return;
        event.setCancelled(true);
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT) return;
        kernel.click(event.getWhoClicked().getUniqueId(), holder.sessionId(), holder.viewRevision(), event.getRawSlot());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void drag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WorldWeaverHolder) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void close(final InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WorldWeaverHolder holder) {
            kernel.closeView(event.getPlayer().getUniqueId(), holder.sessionId(), holder.viewRevision());
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void held(final PlayerItemHeldEvent event) {
        kernel.clearPlayerState(event.getPlayer().getUniqueId());
    }
}
