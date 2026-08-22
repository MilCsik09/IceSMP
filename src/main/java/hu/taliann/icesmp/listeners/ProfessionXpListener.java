package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.TalentManager;
import hu.taliann.icesmp.progression.BlockRewardOriginTracker;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Awards XP for the WoW-style profession roster with durable anti-farm block provenance. */
public final class ProfessionXpListener implements Listener {
    private volatile hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyGoal;
    private volatile hu.taliann.icesmp.managers.AbundanceManager abundanceManager;

    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.SWEET_BERRY_BUSH, Material.COCOA);
    private static final Set<Material> POTIONS = EnumSet.of(
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION);

    private final ProfessionManager professionManager;
    private final ConfigManager configManager;
    private final TalentManager talentManager;
    private final hu.taliann.icesmp.managers.AfkManager afkManager;

    public ProfessionXpListener(final ProfessionManager professionManager, final ConfigManager configManager,
                                final TalentManager talentManager,
                                final hu.taliann.icesmp.managers.AfkManager afkManager) {
        this.professionManager = professionManager;
        this.configManager = configManager;
        this.talentManager = talentManager;
        this.afkManager = afkManager;
    }

    public void setWeeklyGoal(final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyGoal) {
        this.weeklyGoal = weeklyGoal;
    }

    public void setAbundanceManager(final hu.taliann.icesmp.managers.AbundanceManager abundanceManager) {
        this.abundanceManager = abundanceManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        BlockRewardOriginTracker.markPlayerPlaced(event.getBlockPlaced());
    }

    private void awardHarvestXp(final Player player, final String configPath, final int fallback) {
        final hu.taliann.icesmp.managers.AbundanceManager abundanceRef = abundanceManager;
        final double mult = abundanceRef != null && abundanceRef.isActive()
                ? Math.max(1.0D, configManager.getDouble("professions.seasonal.abundance-multiplier", 1.5D))
                : 1.0D;
        final int base = Math.max(0, configManager.getInt(configPath, fallback));
        awardXpAmount(player, ProfessionType.HERBALIST, (int) Math.round(base * mult));
    }

    private void awardXp(final Player player, final ProfessionType profession,
                         final String configPath, final int fallback) {
        if (afkManager != null && configManager.getBoolean("afk.block-rewards", true)
                && afkManager.isAfk(player.getUniqueId())) return;
        awardXpAmount(player, profession, Math.max(0, configManager.getInt(configPath, fallback)));
    }

    private void awardXpAmount(final Player player, final ProfessionType profession, final int baseXp) {
        if (afkManager != null && configManager.getBoolean("afk.block-rewards", true)
                && afkManager.isAfk(player.getUniqueId())) return;
        final double bonusPercent = Math.max(0.0D,
                talentManager.getEffectTotal(player, "profession-xp-bonus"));
        final int totalXp = (int) Math.round(baseXp * (1.0D + (bonusPercent / 100.0D)));
        professionManager.addXpFor(player, profession, totalXp).whenComplete((change, failure) -> {
            if (failure != null || change == null || !change.changed()) return;
            professionManager.runOnOwnerThread(player, () -> {
                final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyRef = weeklyGoal;
                if (weeklyRef != null && player.isOnline()) weeklyRef.add(player, profession, totalXp);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (!isSurvival(player) || !event.isDropItems()) return;
        final Block block = event.getBlock();
        final boolean rewardEligible = BlockRewardOriginTracker.isRewardEligible(block);
        BlockRewardOriginTracker.clearPlayerPlacedAfterBreak(block);
        if (!rewardEligible) return;
        final Material material = block.getType();
        if (isOre(material)) {
            awardXp(player, ProfessionType.MINER, "professions.xp.mining-ore", 5);
            return;
        }
        if (Tag.LOGS.isTagged(material)) {
            awardXp(player, ProfessionType.LUMBERJACK, "professions.xp.logging", 2);
            return;
        }
        if (Tag.FLOWERS.isTagged(material)) {
            awardHarvestXp(player, "professions.xp.herbalism-harvest", 3);
            return;
        }
        if (CROPS.contains(material) && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge()) {
            awardHarvestXp(player, "professions.xp.herbalism-harvest", 3);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHarvestBlock(final PlayerHarvestBlockEvent event) {
        if (!isSurvival(event.getPlayer())) return;
        awardHarvestXp(event.getPlayer(), "professions.xp.herbalism-harvest", 3);
    }

    private static boolean isSurvival(final Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(final CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isSurvival(player)) return;
        final ItemStack result = event.getRecipe().getResult();
        if (isArmorerCraft(result.getType())) {
            awardXpAmount(player, ProfessionType.ARMORER,
                    Math.max(0, configManager.getInt("professions.xp.crafting-gear", 8))
                            * craftedBatches(event));
        }
    }

    private int craftedBatches(final CraftItemEvent event) {
        if (!event.isShiftClick()) return 1;
        int batches = Integer.MAX_VALUE;
        for (final ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient != null && ingredient.getType() != Material.AIR) {
                batches = Math.min(batches, ingredient.getAmount());
            }
        }
        if (batches == Integer.MAX_VALUE || batches < 1) return 1;
        return Math.min(Math.max(1, configManager.getInt("professions.xp.bulk-event-cap", 16)), batches);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmithItem(final SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isSurvival(player)) return;
        awardXp(player, ProfessionType.ARMORER, "professions.xp.smithing", 15);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(final EnchantItemEvent event) {
        if (!isSurvival(event.getEnchanter())) return;
        awardXp(event.getEnchanter(), ProfessionType.ENCHANTER, "professions.xp.enchanting", 10);
    }

    private static final java.util.Set<org.bukkit.event.inventory.InventoryAction> TAKE_ACTIONS =
            java.util.Set.of(
                    org.bukkit.event.inventory.InventoryAction.PICKUP_ALL,
                    org.bukkit.event.inventory.InventoryAction.PICKUP_HALF,
                    org.bukkit.event.inventory.InventoryAction.PICKUP_SOME,
                    org.bukkit.event.inventory.InventoryAction.PICKUP_ONE,
                    org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY,
                    org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP,
                    org.bukkit.event.inventory.InventoryAction.SWAP_WITH_CURSOR,
                    org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR,
                    org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT,
                    org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewedPotionPickup(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isSurvival(player)) return;
        if (!(event.getView().getTopInventory() instanceof BrewerInventory)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() > 2) return;
        final ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !POTIONS.contains(clicked.getType())) return;
        if (!TAKE_ACTIONS.contains(event.getAction())) return;
        awardXp(player, ProfessionType.ALCHEMIST, "professions.xp.brewing", 12);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!isSurvival(event.getPlayer())) return;
        awardXp(event.getPlayer(), ProfessionType.FISHERMAN, "professions.xp.fishing", 4);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceExtract(final FurnaceExtractEvent event) {
        if (!event.getItemType().isEdible()) return;
        if (!isSurvival(event.getPlayer())) return;
        final int cap = Math.max(1, configManager.getInt("professions.xp.bulk-event-cap", 16));
        awardXpAmount(event.getPlayer(), ProfessionType.COOK,
                Math.max(0, configManager.getInt("professions.xp.cooking", 3))
                        * Math.min(cap, Math.max(1, event.getItemAmount())));
    }

    private boolean isOre(final Material material) {
        return material == Material.ANCIENT_DEBRIS
                || material.name().toLowerCase(Locale.ROOT).endsWith("_ore");
    }

    private boolean isArmorerCraft(final Material material) {
        final String name = material.name().toLowerCase(Locale.ROOT);
        return name.endsWith("_helmet") || name.endsWith("_chestplate")
                || name.endsWith("_leggings") || name.endsWith("_boots")
                || material == Material.SHIELD;
    }
}
