package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.DailyQuestManager;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Feeds the daily quest objectives: hostile kills, animal kills, fish catches,
 * block breaks, ore mining and crop harvesting.
 */
public final class DailyQuestListener implements Listener {

    private final DailyQuestManager dailyQuestManager;

    public DailyQuestListener(final DailyQuestManager dailyQuestManager) {
        this.dailyQuestManager = dailyQuestManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        final Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (event.getEntity() instanceof Monster) {
            dailyQuestManager.handle(killer, "KILL_MOBS");
        } else if (event.getEntity() instanceof Animals) {
            dailyQuestManager.handle(killer, "KILL_ANIMALS");
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
        final Player player = event.getPlayer();
        dailyQuestManager.handle(player, "BREAK_BLOCKS");

        // Ore mining: any block whose material name ends with "_ORE" (covers every ore tier),
        // plus ancient debris.
        final org.bukkit.Material type = event.getBlock().getType();
        if (type.name().endsWith("_ORE") || type == org.bukkit.Material.ANCIENT_DEBRIS) {
            dailyQuestManager.handle(player, "MINE_ORE");
        }

        // Crop harvest: a fully-grown ageable crop (wheat, carrots, potatoes, beetroot, nether wart…).
        if (event.getBlock().getBlockData() instanceof Ageable ageable && ageable.getAge() == ageable.getMaximumAge()) {
            dailyQuestManager.handle(player, "HARVEST_CROPS");
        }
    }
}
