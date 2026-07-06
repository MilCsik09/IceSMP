package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.WildHuntManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Awards the Wild Hunt loot to the player who slays the elite beast. The death
 * event fires on the beast's own region thread, so {@link WildHuntManager#onSlain}
 * drops the loot at the death location without a hop.
 */
public final class WildHuntListener implements Listener {

    private final WildHuntManager wildHuntManager;

    public WildHuntListener(final WildHuntManager wildHuntManager) {
        this.wildHuntManager = wildHuntManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(final EntityDeathEvent event) {
        final LivingEntity dead = event.getEntity();
        if (!wildHuntManager.isWildHunt(dead.getUniqueId())) {
            return;
        }
        wildHuntManager.onSlain(dead.getKiller(), dead.getLocation());
    }
}
