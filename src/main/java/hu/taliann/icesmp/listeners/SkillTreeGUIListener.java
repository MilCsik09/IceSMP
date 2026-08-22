package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.JobGUI;
import hu.taliann.icesmp.gui.SkillTreeGUI;
import hu.taliann.icesmp.gui.SkillTreeHolder;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class SkillTreeGUIListener implements Listener {

    private final JobManager jobManager;
    private final CatalystItemFactory catalystItemFactory;
    private final FactionManager factionManager;
    private final MessageManager messageManager;

    public SkillTreeGUIListener(final JobManager jobManager, final CatalystItemFactory catalystItemFactory,
                                final FactionManager factionManager, final MessageManager messageManager) {
        this.jobManager = jobManager;
        this.catalystItemFactory = catalystItemFactory;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SkillTreeHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.getOwnerUuid().equals(player.getUniqueId())) {
            return;
        }

        if (event.getRawSlot() == SkillTreeGUI.getBackSlot()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
            JobGUI.openJobMenu(player, jobManager, catalystItemFactory, factionManager, messageManager);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SkillTreeHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SkillTreeHolder holder) {
            holder.setInventory(null);
        }
    }
}
