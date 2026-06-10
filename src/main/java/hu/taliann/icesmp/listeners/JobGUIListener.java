package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.gui.JobGUI;
import hu.taliann.icesmp.gui.JobGUIHolder;
import hu.taliann.icesmp.gui.ProfileGUI;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class JobGUIListener implements Listener {

    private final JobManager jobManager;
    private final MetelytepoManager metelytepoManager;
    private final MessageManager messageManager;

    public JobGUIListener(final JobManager jobManager, final MetelytepoManager metelytepoManager,
                          final MessageManager messageManager) {
        this.jobManager = jobManager;
        this.metelytepoManager = metelytepoManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof JobGUIHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!holder.getOwnerUuid().equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (event.getRawSlot() == JobGUI.getBackSlot()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
            ProfileGUI.openProfile(player, player, metelytepoManager, messageManager);
            return;
        }

        final JobType selectedJob = JobGUI.resolveJobType(event.getRawSlot());
        if (selectedJob == null) {
            return;
        }

        if (jobManager.setPrimaryJob(player, selectedJob)) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            player.sendMessage(messageManager.getComponent("messages.job-select-primary-success", "&aElsodleges kaszt kivalasztva:").append(Component.space()).append(selectedJob.getDisplayName()));
            JobGUI.openJobMenu(player, jobManager, messageManager);
            return;
        }

        if (jobManager.setSecondaryJob(player, selectedJob)) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            player.sendMessage(messageManager.getComponent("messages.job-select-secondary-success", "&aMasodlagos kaszt kivalasztva:").append(Component.space()).append(selectedJob.getDisplayName()));
            JobGUI.openJobMenu(player, jobManager, messageManager);
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
        player.sendMessage(messageManager.getComponent("messages.job-select-failed", "&cJelenleg nem valaszthatsz uj kasztot!"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof JobGUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof JobGUIHolder holder) {
            holder.setInventory(null);
        }
    }
}

