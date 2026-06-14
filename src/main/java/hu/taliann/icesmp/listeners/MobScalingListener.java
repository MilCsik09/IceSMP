package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.MobScalingManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class MobScalingListener implements Listener {

    private final MobScalingManager mobScalingManager;

    public MobScalingListener(final MobScalingManager mobScalingManager) {
        this.mobScalingManager = mobScalingManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        mobScalingManager.applyScaling(event.getEntity(), event.getSpawnReason());
    }
}
