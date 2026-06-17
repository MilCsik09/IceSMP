package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.DailyQuestManager;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Feeds the daily quest objectives: hostile kills, fish catches and block breaks.
 */
public final class DailyQuestListener implements Listener {

    private final DailyQuestManager dailyQuestManager;

    public DailyQuestListener(final DailyQuestManager dailyQuestManager) {
        this.dailyQuestManager = dailyQuestManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        final Player killer = event.getEntity().getKiller();
        if (killer != null) {
            dailyQuestManager.handle(killer, "KILL_MOBS");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            dailyQuestManager.handle(event.getPlayer(), "CATCH_FISH");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        dailyQuestManager.handle(event.getPlayer(), "BREAK_BLOCKS");
    }
}
