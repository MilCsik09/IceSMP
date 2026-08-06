package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.BestiaryHolder;
import hu.taliann.icesmp.managers.CommunityGoalManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Routes final gameplay events into quest and community-goal progress. */
public final class QuestProgressListener implements Listener {

    private static final int PROFILE_READY_RETRIES = 40;
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
    public void onJoin(final PlayerJoinEvent event) {
        scheduleRewardRecovery(event.getPlayer(), 0);
    }

    private void scheduleRewardRecovery(final Player player, final int attempt) {
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            final boolean ready = hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority
                    .installed().flatMap(authority -> authority.repository()
                            .cached(player.getUniqueId())).isPresent();
            if (ready) {
                questManager.recoverPendingRewards(player);
                return;
            }
            if (attempt + 1 < PROFILE_READY_RETRIES) {
                scheduleRewardRecovery(player, attempt + 1);
            } else {
                plugin.getLogger().severe("PlayerProfile quest reward recovery timed out for "
                        + player.getUniqueId());
            }
        }, null, 5L);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        questManager.clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(final PlayerKickEvent event) {
        questManager.clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        final var kill = hu.taliann.icesmp.utils.MobKillUtil
                .eligibleTrackingKill(event.getEntity());
        if (kill == null) return;
        final var entityType = event.getEntityType();
        final int level = mobScalingManager.getLevel(event.getEntity());
        final boolean worldBoss = worldBossManager.isWorldBoss(event.getEntity());
        kill.runOnKiller(plugin, killer -> {
            questManager.handleKill(killer, entityType, level);
            communityGoalManager.contribute(killer, "KILL_MOBS", entityType.name(), 1);
            if (worldBoss) {
                questManager.handleBossKill(killer);
                communityGoalManager.contribute(killer, "KILL_WORLDBOSS", null, 1);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final org.bukkit.GameMode mode = event.getPlayer().getGameMode();
        if (mode != org.bukkit.GameMode.SURVIVAL && mode != org.bukkit.GameMode.ADVENTURE) return;
        questManager.handleBlockBreak(event.getPlayer(), event.getBlock().getType());
        communityGoalManager.contribute(event.getPlayer(), "BREAK_BLOCKS",
                event.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(final CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            final var result = event.getRecipe().getResult();
            questManager.handleCraft(player, result.getType(), result.getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        questManager.handleFish(event.getPlayer());
        if (!(event.getCaught() instanceof Item caught)) return;
        final var stack = caught.getItemStack();
        if (stack.getType().isAir() || stack.getAmount() <= 0) return;
        if (communityGoalManager.contributeOnce(event.getPlayer(), "COLLECT_ITEMS",
                stack.getType().name(), stack.getAmount(), caught.getUniqueId())) {
            questManager.handleCollect(event.getPlayer(), stack.getType(), stack.getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        questManager.handlePlaceBlock(event.getPlayer(), event.getBlock().getType());
    }

    @EventHandler
    public void onPlayerKill(final PlayerDeathEvent event) {
        final Player killer = event.getEntity().getKiller();
        if (killer == null || killer.getUniqueId().equals(event.getEntity().getUniqueId())) return;
        final Player online = org.bukkit.Bukkit.getPlayer(killer.getUniqueId());
        if (online == null) return;
        online.getScheduler().run(plugin, task -> {
            questManager.handlePlayerKill(online);
            communityGoalManager.contribute(online, "KILL_PLAYERS", null, 1);
        }, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(final EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player breeder)) return;
        final var entityType = event.getEntityType();
        breeder.getScheduler().run(plugin,
                task -> questManager.handleBreed(breeder, entityType), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(final EnchantItemEvent event) {
        questManager.handleEnchant(event.getEnchanter());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        questManager.handleConsume(event.getPlayer(), event.getItem().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(final FurnaceExtractEvent event) {
        questManager.handleSmelt(event.getPlayer(), event.getItemType(), event.getItemAmount());
        if (event.getItemAmount() <= 0) return;
        final String identity = event.getPlayer().getUniqueId() + "|smelt|"
                + event.getBlock().getWorld().getUID() + '|'
                + event.getBlock().getX() + '|' + event.getBlock().getY() + '|'
                + event.getBlock().getZ() + '|' + event.getItemType().name() + '|'
                + event.getItemAmount() + '|' + System.identityHashCode(event);
        final UUID contributionId = UUID.nameUUIDFromBytes(
                identity.getBytes(StandardCharsets.UTF_8));
        if (communityGoalManager.contributeOnce(event.getPlayer(), "COLLECT_ITEMS",
                event.getItemType().name(), event.getItemAmount(), contributionId)) {
            questManager.handleCollect(event.getPlayer(), event.getItemType(), event.getItemAmount());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(final EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player tamer)) return;
        final var entityType = event.getEntityType();
        tamer.getScheduler().run(plugin,
                task -> questManager.handleTame(tamer, entityType), null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVillagerTrade(final PlayerTradeEvent event) {
        questManager.handleVillagerTrade(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ()
                && from.getBlockY() == to.getBlockY())) return;
        questManager.handleBiomeVisit(event.getPlayer(),
                to.getBlock().getBiome().getKey().toString());
    }
}
