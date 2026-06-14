package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.MinionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.UUID;

/**
 * Keeps summoned minions loyal: a minion never targets its own summoner and
 * never turns on another minion of the same owner.
 */
public final class MinionProtectionListener implements Listener {

    private final MinionManager minionManager;

    public MinionProtectionListener(final MinionManager minionManager) {
        this.minionManager = minionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(final EntityTargetLivingEntityEvent event) {
        final UUID minionOwner = minionManager.getOwner(event.getEntity());
        if (minionOwner == null || event.getTarget() == null) {
            return;
        }

        // PASSIVE/STAY stance minions never pick a target.
        if (minionManager.getStance(event.getEntity()) != MinionManager.Stance.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        if (event.getTarget() instanceof Player player && player.getUniqueId().equals(minionOwner)) {
            event.setCancelled(true);
            return;
        }

        if (minionManager.isOwnedBy(event.getTarget(), minionOwner)) {
            event.setCancelled(true);
        }
    }
}
