package hu.taliann.icesmp.integrity;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;

/** World reward output boundary; player-owned inventory is not a newly minted mob drop. */
public final class VanillaRewardIntegrityListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void beforeRewards(final EntityDeathEvent event) { enforce(event); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void afterRewards(final EntityDeathEvent event) { enforce(event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void beforePlayerRewards(final org.bukkit.event.entity.PlayerDeathEvent event) { enforce(event); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void afterPlayerRewards(final org.bukkit.event.entity.PlayerDeathEvent event) { enforce(event); }
    private void enforce(final EntityDeathEvent event) {
        final var entity = event.getEntity();
        if (!(entity instanceof Player) && !BukkitRewardSources.deathAllowed(RewardChannel.VANILLA_DROPS, entity)) event.getDrops().clear();
        if (!BukkitRewardSources.deathAllowed(RewardChannel.VANILLA_XP, entity)) event.setDroppedExp(0);
    }
}
