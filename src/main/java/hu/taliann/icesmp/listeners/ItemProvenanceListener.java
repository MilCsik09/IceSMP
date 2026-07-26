package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.utils.ItemProvenance;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Exact player-drop provenance without a nearby-entity heuristic. */
public final class ItemProvenanceListener implements Listener {

    public ItemProvenanceListener(final JavaPlugin plugin) {
        java.util.Objects.requireNonNull(plugin, "plugin");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDrop(final PlayerDropItemEvent event) {
        ItemProvenance.markPlayerDropped(event.getItemDrop());
    }

    /**
     * PlayerDeathEvent already exposes the exact ItemStack instances that Bukkit will spawn. Marking
     * those stacks avoids the former one-tick radius scan, which could tag another player's or a
     * natural item that merely happened to lie near the death location.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        if (event.getKeepInventory() || event.getDrops().isEmpty()) {
            return;
        }
        for (final var drop : event.getDrops()) {
            ItemProvenance.markPlayerDropped(drop);
        }
    }
}
