package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.JobGUI;
import hu.taliann.icesmp.gui.ProfileHolder;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class ProfileGUIListener implements Listener {

    private static final int JOB_SLOT = 11;

    private final JobManager jobManager;
    private final MessageManager messageManager;

    public ProfileGUIListener(final JobManager jobManager, final MessageManager messageManager) {
        this.jobManager = jobManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ProfileHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        final ItemStack clicked = event.getCurrentItem();
        final boolean isKnowledgeBook = clicked != null && clicked.getType() == Material.KNOWLEDGE_BOOK;
        if (event.getRawSlot() == JOB_SLOT || isKnowledgeBook) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
            JobGUI.openJobMenu(player, jobManager, messageManager);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ProfileHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ProfileHolder holder) {
            holder.setInventory(null);
        }
    }
}


