package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.CommunityGoalManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Routes gameplay events into the quest framework's progress tracking.
 * (Territory visits arrive through the TerritoryListener, level changes
 * through the JobManager XP-change hook, NPC talks/deliveries through the
 * FancyNpcs bridge, parkour finishes through the ParkourManager hook.)
 */
public final class QuestProgressListener implements Listener {

    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final MobScalingManager mobScalingManager;
    private final WorldBossManager worldBossManager;
    private final CommunityGoalManager communityGoalManager;

    public QuestProgressListener(final JavaPlugin plugin, final QuestManager questManager,
                                 final MobScalingManager mobScalingManager,
                                 final WorldBossManager worldBossManager,
                                 final CommunityGoalManager communityGoalManager) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.mobScalingManager = mobScalingManager;
        this.worldBossManager = worldBossManager;
        this.communityGoalManager = communityGoalManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        final Player killer = hu.taliann.icesmp.utils.MobKillUtil.eligibleTrackingKiller(event.getEntity());
        if (killer == null || event.getEntity() instanceof Player) {
            return;
        }

        // Read the event entity's data on its own region thread, then hop onto the killer's
        // scheduler — handleKill mutates the killer (quest progress, messages). Folia-safe.
        final var entityType = event.getEntityType();
        final int level = mobScalingManager.getLevel(event.getEntity());
        final boolean worldBoss = worldBossManager.isWorldBoss(event.getEntity());
        killer.getScheduler().run(plugin, task -> {
            questManager.handleKill(killer, entityType, level);
            communityGoalManager.contribute(killer, "KILL_MOBS", entityType.name(), 1);
            if (worldBoss) {
                questManager.handleBossKill(killer);
                communityGoalManager.contribute(killer, "KILL_WORLDBOSS", null, 1);
            }
        }, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        // Creative farm-guard (same as GatheringBuffListener): creative/spectator breaks
        // must not progress quests or community goals.
        final org.bukkit.GameMode mode = event.getPlayer().getGameMode();
        if (mode != org.bukkit.GameMode.SURVIVAL && mode != org.bukkit.GameMode.ADVENTURE) {
            return;
        }
        questManager.handleBlockBreak(event.getPlayer(), event.getBlock().getType());
        communityGoalManager.contribute(event.getPlayer(), "BREAK_BLOCKS", event.getBlock().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(final CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            final var result = event.getRecipe().getResult();
            questManager.handleCraft(player, result.getType(), result.getAmount());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            questManager.handleFish(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        questManager.handlePlaceBlock(event.getPlayer(), event.getBlock().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            final var type = event.getItem().getItemStack().getType();
            final int amount = event.getItem().getItemStack().getAmount();
            questManager.handleCollect(player, type, amount);
            communityGoalManager.contribute(player, "COLLECT_ITEMS", type.name(), amount);
        }
    }

    @EventHandler
    public void onPlayerKill(final PlayerDeathEvent event) {
        final Player killer = event.getEntity().getKiller();
        if (killer == null || killer.getUniqueId().equals(event.getEntity().getUniqueId())) {
            return;
        }

        // PlayerDeathEvent runs on the VICTIM's region; quest progress mutates the killer.
        killer.getScheduler().run(plugin, task -> {
            questManager.handlePlayerKill(killer);
            communityGoalManager.contribute(killer, "KILL_PLAYERS", null, 1);
        }, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(final EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player breeder)) {
            return;
        }

        // The event fires on the animal's region; the breeding player stands beside it,
        // but hop to their scheduler anyway — quest progress mutates the player's PDC.
        final var entityType = event.getEntityType();
        breeder.getScheduler().run(plugin, task -> questManager.handleBreed(breeder, entityType), null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(final EnchantItemEvent event) {
        questManager.handleEnchant(event.getEnchanter());
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        questManager.handleConsume(event.getPlayer(), event.getItem().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(final FurnaceExtractEvent event) {
        questManager.handleSmelt(event.getPlayer(), event.getItemType(), event.getItemAmount());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(final EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player tamer)) {
            return;
        }

        // The event fires on the animal's region; the tamer stands beside it, but the
        // quest progress mutates the player's PDC — hop to their scheduler.
        final var entityType = event.getEntityType();
        tamer.getScheduler().run(plugin, task -> questManager.handleTame(tamer, entityType), null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVillagerTrade(final PlayerTradeEvent event) {
        questManager.handleVillagerTrade(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        // Only re-check on block-level movement (same hot-path guard as the TerritoryListener).
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (to == null
                || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        questManager.handleBiomeVisit(event.getPlayer(),
                to.getBlock().getBiome().getKey().toString());
    }
}
