package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.MobScalingManager;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Scaling plus reversible territory-mob lifecycle reconciliation. */
public final class MobScalingListener implements Listener {
    private final MobScalingManager mobScalingManager;

    public MobScalingListener(final MobScalingManager mobScalingManager) {
        this.mobScalingManager = mobScalingManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        mobScalingManager.applyScaling(event.getEntity(), event.getSpawnReason());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(final EntitiesLoadEvent event) {
        for (final org.bukkit.entity.Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) {
                mobScalingManager.reconcileTerritoryProtection(living);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(final EntityMoveEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || sameColumn(event.getFrom(), event.getTo())) {
            return;
        }
        mobScalingManager.reconcileTerritoryProtection(living, event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final EntityTeleportEvent event) {
        if (event.getEntity() instanceof LivingEntity living && event.getTo() != null) {
            mobScalingManager.reconcileTerritoryProtection(living, event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(final EntityCombustEvent event) {
        // Bukkit's base event is the daylight ignition path; block/entity combustion
        // subclasses must remain vanilla so this never becomes global fire immunity.
        if (event.getClass() == EntityCombustEvent.class
                && event.getEntity() instanceof LivingEntity living
                && mobScalingManager.hasDaylightProtection(living)) {
            event.setCancelled(true);
        }
    }

    private static boolean sameColumn(final Location from, final Location to) {
        return from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ();
    }
}
