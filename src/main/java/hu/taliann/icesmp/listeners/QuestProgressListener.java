package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.integrity.*;
import org.bukkit.Bukkit;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.function.BiConsumer;
import hu.taliann.icesmp.managers.CommunityGoalManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.managers.QuestPhysicalRewardDeliveryService;
import hu.taliann.icesmp.managers.WorldBossManager;
import hu.taliann.icesmp.progression.BlockRewardOriginTracker;
import hu.taliann.icesmp.progression.ItemAcquisitionPolicy;
import hu.taliann.icesmp.utils.ItemProvenance;
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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
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
    private final ItemAcquisitionPolicy.ReceiptWindow acquisitionReceipts =
            new ItemAcquisitionPolicy.ReceiptWindow(4_096);

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
        scheduleRewardRecovery(event.getPlayer().getUniqueId(), 0);
    }

    private void scheduleRewardRecovery(final UUID playerId, final int attempt) {
        final Player handle = Bukkit.getPlayer(playerId);
        if (handle == null) return;
        handle.getScheduler().runDelayed(plugin, task -> {
            final Player owned = Bukkit.getPlayer(playerId);
            if (owned == null || !Bukkit.isOwnedByCurrentRegion(owned) || !owned.isOnline()) return;
            final boolean ready = hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority
                    .installed().flatMap(authority -> authority.repository().cached(playerId)).isPresent();
            if (ready) {
                questManager.recoverPendingRewards(owned);
            } else if (attempt + 1 < PROFILE_READY_RETRIES) {
                scheduleRewardRecovery(playerId, attempt + 1);
            } else plugin.getLogger().severe("PlayerProfile quest reward recovery timed out for " + playerId);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingRewardClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (pendingReward(event.getCurrentItem()) || pendingReward(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        final int hotbar = event.getHotbarButton();
        if (hotbar >= 0 && hotbar < 9 && pendingReward(player.getInventory().getItem(hotbar))) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND
                && pendingReward(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingRewardDrag(final InventoryDragEvent event) {
        if (pendingReward(event.getOldCursor())
                || event.getNewItems().values().stream().anyMatch(this::pendingReward)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingRewardDrop(final PlayerDropItemEvent event) {
        if (pendingReward(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingRewardHandSwap(final PlayerSwapHandItemsEvent event) {
        if (pendingReward(event.getMainHandItem()) || pendingReward(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingRewardInteract(final PlayerInteractEvent event) {
        if (pendingReward(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingRewardConsume(final PlayerItemConsumeEvent event) {
        if (pendingReward(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingRewardPlace(final BlockPlaceEvent event) {
        if (pendingReward(event.getItemInHand())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPendingRewardDeath(final PlayerDeathEvent event) {
        if (event.getKeepInventory()) return;
        for (final var iterator = event.getDrops().iterator(); iterator.hasNext();) {
            final ItemStack drop = iterator.next();
            if (!pendingReward(drop)) continue;
            iterator.remove();
            event.getItemsToKeep().add(drop);
        }
    }

    private static RewardContext communityContext(final RewardContext reward) {
        return new RewardContext(RewardChannel.COMMUNITY_GOAL, reward.recipient(), reward.sources());
    }

    private boolean pendingReward(final ItemStack item) {
        return QuestPhysicalRewardDeliveryService.isPendingRewardItem(plugin, item);
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
        kill.runOnKiller(plugin, hu.taliann.icesmp.integrity.RewardChannel.QUEST_PROGRESS, killer -> {
            questManager.handleKill(killer, entityType, level, kill.rewardContext(RewardChannel.QUEST_PROGRESS));
            if (kill.eligibleFor(hu.taliann.icesmp.integrity.RewardChannel.COMMUNITY_GOAL)) communityGoalManager.contribute(killer, "KILL_MOBS", entityType.name(), 1, kill.rewardContext(RewardChannel.COMMUNITY_GOAL));
            if (worldBoss) {
                questManager.handleBossKill(killer, kill.rewardContext(RewardChannel.QUEST_PROGRESS));
                if (kill.eligibleFor(hu.taliann.icesmp.integrity.RewardChannel.COMMUNITY_GOAL)) communityGoalManager.contribute(killer, "KILL_WORLDBOSS", null, 1, kill.rewardContext(RewardChannel.COMMUNITY_GOAL));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final org.bukkit.GameMode mode = event.getPlayer().getGameMode();
        if (mode != org.bukkit.GameMode.SURVIVAL && mode != org.bukkit.GameMode.ADVENTURE) return;
        if (!BlockRewardOriginTracker.isRewardEligible(event.getBlock())) return;
        final RewardContext reward = capture(event.getPlayer(), () -> BukkitRewardSources.block(event.getBlock()));
        if (reward == null) return;
        questManager.handleBlockBreak(event.getPlayer(), event.getBlock().getType(), reward);
        communityGoalManager.contribute(event.getPlayer(), "BREAK_BLOCKS",
                event.getBlock().getType().name(), 1, communityContext(reward));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(final CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            final var result = event.getRecipe().getResult();
            final RewardContext reward = capture(player, () -> List.of());
            if (reward == null) return;
            questManager.handleCraft(player, result.getType(), result.getAmount(), reward);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        final RewardContext reward = capture(event.getPlayer(), () -> event.getCaught() == null ? List.of() : BukkitRewardSources.causal(event.getCaught()));
        if (reward == null) return;
        questManager.handleFish(event.getPlayer(), reward);
        // A kifogott tárgy csak a későbbi, sikeres EntityPickupItemEventben acquisition.
        // Így ugyanaz a logical item nem számít a horogra kerüléskor és a felvételkor is.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(final EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        final Item item = event.getItem();
        final ItemStack stack = item.getItemStack();
        final int acquired = ItemAcquisitionPolicy.acceptedPickupAmount(player.getGameMode(),
                event.isCancelled(), ItemProvenance.isPlayerDropped(item), stack.getAmount(),
                event.getRemaining());
        if (acquired <= 0 || stack.getType().isAir()) return;
        final String logicalEvent = player.getUniqueId() + "|pickup|" + item.getUniqueId()
                + '|' + stack.getAmount() + '|' + event.getRemaining();
        final UUID receipt = UUID.nameUUIDFromBytes(logicalEvent.getBytes(StandardCharsets.UTF_8));
        if (!acquisitionReceipts.claim(receipt)) return;
        final RewardContext reward = capture(player, () -> BukkitRewardSources.causal(item));
        if (reward == null) return;
        questManager.handleCollect(player, stack.getType(), acquired, reward);
        communityGoalManager.contribute(player, "COLLECT_ITEMS", stack.getType().name(), acquired, communityContext(reward));
    }

    /** Buckets enter the player's inventory directly and therefore have no pickup event. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(final PlayerBucketFillEvent event) {
        final Player player = event.getPlayer();
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL
                && player.getGameMode() != org.bukkit.GameMode.ADVENTURE) return;
        final ItemStack result = event.getItemStack();
        if (result == null || result.getType().isAir() || result.getAmount() <= 0) return;
        final String logicalEvent = player.getUniqueId() + "|bucket|"
                + event.getBlock().getWorld().getUID() + '|' + event.getBlock().getX() + '|'
                + event.getBlock().getY() + '|' + event.getBlock().getZ() + '|'
                + result.getType().name() + '|' + System.identityHashCode(event);
        final UUID receipt = UUID.nameUUIDFromBytes(logicalEvent.getBytes(StandardCharsets.UTF_8));
        if (!acquisitionReceipts.claim(receipt)) return;
        final RewardContext reward = capture(player, () -> BukkitRewardSources.block(event.getBlock()));
        if (reward == null) return;
        questManager.handleCollect(player, result.getType(), result.getAmount(), reward);
        communityGoalManager.contribute(player, "COLLECT_ITEMS",
                result.getType().name(), result.getAmount(), communityContext(reward));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        final RewardContext reward = capture(event.getPlayer(), () -> BukkitRewardSources.block(event.getBlock()));
        if (reward == null) return;
        questManager.handlePlaceBlock(event.getPlayer(), event.getBlock().getType(), reward);
    }

    @EventHandler
    public void onPlayerKill(final PlayerDeathEvent event) {
        final Player killer = event.getEntity().getKiller();
        if (killer == null || killer.getUniqueId().equals(event.getEntity().getUniqueId())) return;
        final UUID playerId = killer.getUniqueId();
        final List<RewardSource> sources;
        try { sources = BukkitRewardSources.death(RewardChannel.QUEST_PROGRESS, event.getEntity()).sources(); }
        catch (final RuntimeException | LinkageError unavailable) { return; }
        onPlayerOwner(playerId, sources, (owned, reward) -> {
            questManager.handlePlayerKill(owned, reward);
            if (GameplayRewardGate.evaluate(new RewardContext(RewardChannel.COMMUNITY_GOAL, playerId, reward.sources())).allowed())
                communityGoalManager.contribute(owned, "KILL_PLAYERS", null, 1, communityContext(reward));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(final EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player breeder)) return;
        final UUID playerId = breeder.getUniqueId();
        final var entityType = event.getEntityType();
        final List<RewardSource> sources;
        try {
            final var lineage = new LinkedHashSet<>(BukkitRewardSources.causal(event.getEntity()));
            lineage.addAll(BukkitRewardSources.causal(event.getMother()));
            lineage.addAll(BukkitRewardSources.causal(event.getFather()));
            sources = List.copyOf(lineage);
        } catch (final RuntimeException | LinkageError unavailable) { return; }
        onPlayerOwner(playerId, sources, (owned, reward) -> questManager.handleBreed(owned, entityType, reward));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(final EnchantItemEvent event) {
        final RewardContext reward = capture(event.getEnchanter(), () -> BukkitRewardSources.block(event.getEnchantBlock()));
        if (reward == null) return;
        questManager.handleEnchant(event.getEnchanter(), reward);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        final RewardContext reward = capture(event.getPlayer(), () -> List.of());
        if (reward == null) return;
        questManager.handleConsume(event.getPlayer(), event.getItem().getType(), reward);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(final FurnaceExtractEvent event) {
        final RewardContext reward = capture(event.getPlayer(), () -> BukkitRewardSources.block(event.getBlock()));
        if (reward == null) return;
        questManager.handleSmelt(event.getPlayer(), event.getItemType(), event.getItemAmount(), reward);
        if (event.getItemAmount() <= 0) return;
        final String identity = event.getPlayer().getUniqueId() + "|smelt|"
                + event.getBlock().getWorld().getUID() + '|'
                + event.getBlock().getX() + '|' + event.getBlock().getY() + '|'
                + event.getBlock().getZ() + '|' + event.getItemType().name() + '|'
                + event.getItemAmount() + '|' + System.identityHashCode(event);
        final UUID contributionId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        if (!acquisitionReceipts.claim(contributionId)) return;
        questManager.handleCollect(event.getPlayer(), event.getItemType(), event.getItemAmount(), reward);
        communityGoalManager.contribute(event.getPlayer(), "COLLECT_ITEMS",
                event.getItemType().name(), event.getItemAmount(), communityContext(reward));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(final EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player tamer)) return;
        final UUID playerId = tamer.getUniqueId();
        final var entityType = event.getEntityType();
        final List<RewardSource> sources;
        try { sources = BukkitRewardSources.causal(event.getEntity()); }
        catch (final RuntimeException | LinkageError unavailable) { return; }
        onPlayerOwner(playerId, sources, (owned, reward) -> questManager.handleTame(owned, entityType, reward));
    }

    @EventHandler(ignoreCancelled = true)
    public void onVillagerTrade(final PlayerTradeEvent event) {
        final RewardContext reward = capture(event.getPlayer(), () -> BukkitRewardSources.causal(event.getVillager()));
        if (reward == null) return;
        questManager.handleVillagerTrade(event.getPlayer(), reward);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ()
                && from.getBlockY() == to.getBlockY())) return;
        if (!Bukkit.isOwnedByCurrentRegion(to)) return;
        final RewardContext reward = capture(event.getPlayer(), () -> BukkitRewardSources.block(to.getBlock()));
        if (reward == null) return;
        questManager.handleBiomeVisit(event.getPlayer(),
                to.getBlock().getBiome().getKey().toString(), reward);
    }
    private static RewardContext capture(final Player player, final java.util.function.Supplier<List<RewardSource>> original) {
        try {
            if (!Bukkit.isOwnedByCurrentRegion(player) || !player.isOnline()) return null;
            final var sources = new LinkedHashSet<>(original.get());
            sources.addAll(BukkitRewardSources.causal(player));
            final var reward = new RewardContext(RewardChannel.QUEST_PROGRESS, player.getUniqueId(), List.copyOf(sources));
            return GameplayRewardGate.evaluate(reward).allowed() ? reward : null;
        } catch (final RuntimeException | LinkageError unavailable) { return null; }
    }

    private void onPlayerOwner(final UUID playerId, final List<RewardSource> sources,
                               final BiConsumer<Player, RewardContext> action) {
        final Player handle = Bukkit.getPlayer(playerId);
        if (handle == null) return;
        handle.getScheduler().run(plugin, task -> {
            final Player owned = Bukkit.getPlayer(playerId);
            if (owned == null || !Bukkit.isOwnedByCurrentRegion(owned) || !owned.isOnline()) return;
            final RewardContext reward = capture(owned, () -> sources);
            if (reward != null) action.accept(owned, reward);
        }, null);
    }

}
