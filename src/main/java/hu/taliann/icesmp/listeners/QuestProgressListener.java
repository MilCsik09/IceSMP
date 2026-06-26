package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.QuestManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Routes gameplay events into the quest framework's progress tracking.
 * (Territory visits arrive through the TerritoryListener, level changes
 * through the JobManager XP-change hook.)
 */
public final class QuestProgressListener implements Listener {

    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final MobScalingManager mobScalingManager;

    public QuestProgressListener(final JavaPlugin plugin, final QuestManager questManager,
                                 final MobScalingManager mobScalingManager) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.mobScalingManager = mobScalingManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        final Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player) {
            return;
        }

        // Read the event entity's data on its own region thread, then hop onto the killer's
        // scheduler — handleKill mutates the killer (quest progress, messages). Folia-safe.
        final var entityType = event.getEntityType();
        final int level = mobScalingManager.getLevel(event.getEntity());
        killer.getScheduler().run(plugin, task -> questManager.handleKill(killer, entityType, level), null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        questManager.handleBlockBreak(event.getPlayer(), event.getBlock().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(final CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            questManager.handleCraft(player, event.getRecipe().getResult().getType());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            questManager.handleFish(event.getPlayer());
        }
    }
}
