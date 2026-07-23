package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ArcheologyManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;

/**
 * A régészeti lelőhely kiásásának észlelése: a gyanús homok/kavics ecsetelésének
 * végén a blokk BlockDropItemEvent-tel adja ki a leletet — innen indul a
 * megosztott jutalom (runner-up) kör.
 */
public final class ArcheologyShareListener implements Listener {

    private final ArcheologyManager archeologyManager;

    public ArcheologyShareListener(final ArcheologyManager archeologyManager) {
        this.archeologyManager = archeologyManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDropItem(final BlockDropItemEvent event) {
        final Material type = event.getBlockState().getType();
        if (type != Material.SUSPICIOUS_SAND && type != Material.SUSPICIOUS_GRAVEL) {
            return;
        }
        if (!(event.getPlayer() instanceof Player digger)) {
            return;
        }
        archeologyManager.handleExcavated(digger, event.getBlock().getLocation());
    }
}
