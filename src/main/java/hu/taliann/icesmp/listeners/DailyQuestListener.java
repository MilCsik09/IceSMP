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
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Feeds the daily quest objectives: hostile kills, animal kills, fish catches,
 * block breaks, ore mining and crop harvesting.
 */
public final class DailyQuestListener implements Listener {

    private final JavaPlugin plugin;
    private final DailyQuestManager dailyQuestManager;

    public DailyQuestListener(final JavaPlugin plugin, final DailyQuestManager dailyQuestManager) {
        this.plugin = plugin;
        this.dailyQuestManager = dailyQuestManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        final Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        // Folia: the death event runs on the mob's region thread; handle mutates the killer
        // (daily-quest progress, messages). Hop onto the killer's own scheduler first.
        final String objective;
        if (event.getEntity() instanceof Monster) {
            objective = "KILL_MOBS";
        } else if (event.getEntity() instanceof Animals) {
            objective = "KILL_ANIMALS";
        } else {
            return;
        }
        killer.getScheduler().run(plugin, task -> dailyQuestManager.handle(killer, objective), null);
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
