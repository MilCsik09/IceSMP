package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.TalentManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
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

/**
 * Awards profession XP for the WoW-style profession roster:
 * MINER (ores), HERBALIST (ripe crops, flowers, berries), LUMBERJACK (logs),
 * ARMORER (gear crafting + smithing), ENCHANTER (enchanting table),
 * ALCHEMIST (collecting brewed potions), FISHERMAN (catches), COOK (furnace food).
 */
public final class ProfessionXpListener implements Listener {

    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.SWEET_BERRY_BUSH, Material.COCOA
    );

    private static final Set<Material> POTIONS = EnumSet.of(
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION
    );

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

    private void awardXp(final Player player, final ProfessionType profession, final String configPath, final int fallback) {
        // IDEAS A46: AFK-jelölt játékos nem termel szakma-XP-t (auto-farm exploit-fék).
        if (afkManager != null && configManager.getBoolean("afk.block-rewards", true)
                && afkManager.isAfk(player.getUniqueId())) {
            return;
        }
        final int baseXp = Math.max(0, configManager.getInt(configPath, fallback));
        final double bonusPercent = Math.max(0.0D, talentManager.getEffectTotal(player, "profession-xp-bonus"));
        final int totalXp = (int) Math.round(baseXp * (1.0D + (bonusPercent / 100.0D)));
        professionManager.addXpFor(player, profession, totalXp);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        // Creative farm-guard (same as GatheringBuffListener): no profession XP for
        // creative/spectator breaks or silk-touch-less no-drop breaks.
        if (!isSurvival(player) || !event.isDropItems()) {
            return;
        }
        final Block block = event.getBlock();
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
            awardXp(player, ProfessionType.HERBALIST, "professions.xp.herbalism-harvest", 3);
            return;
        }

        if (CROPS.contains(material) && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge()) {
            awardXp(player, ProfessionType.HERBALIST, "professions.xp.herbalism-harvest", 3);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerHarvestBlock(final PlayerHarvestBlockEvent event) {
        if (!isSurvival(event.getPlayer())) {
            return;
        }
        awardXp(event.getPlayer(), ProfessionType.HERBALIST, "professions.xp.herbalism-harvest", 3);
    }

    private static boolean isSurvival(final Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(final CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final ItemStack result = event.getRecipe().getResult();
        if (isArmorerCraft(result.getType())) {
            awardXp(player, ProfessionType.ARMORER, "professions.xp.crafting-gear", 8);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmithItem(final SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        awardXp(player, ProfessionType.ARMORER, "professions.xp.smithing", 15);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(final EnchantItemEvent event) {
        awardXp(event.getEnchanter(), ProfessionType.ENCHANTER, "professions.xp.enchanting", 10);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrewedPotionPickup(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory() instanceof BrewerInventory)) {
            return;
        }

        // The three result slots of the brewing stand are raw slots 0-2.
        if (event.getRawSlot() < 0 || event.getRawSlot() > 2) {
            return;
        }

        final ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !POTIONS.contains(clicked.getType())) {
            return;
        }

        awardXp(player, ProfessionType.ALCHEMIST, "professions.xp.brewing", 12);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        awardXp(event.getPlayer(), ProfessionType.FISHERMAN, "professions.xp.fishing", 4);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceExtract(final FurnaceExtractEvent event) {
        if (!event.getItemType().isEdible()) {
            return;
        }

        awardXp(event.getPlayer(), ProfessionType.COOK, "professions.xp.cooking", 3);
    }

    private boolean isOre(final Material material) {
        return material == Material.ANCIENT_DEBRIS
                || material.name().toLowerCase(Locale.ROOT).endsWith("_ore");
    }

    private boolean isArmorerCraft(final Material material) {
        final String name = material.name().toLowerCase(Locale.ROOT);
        return name.endsWith("_helmet")
                || name.endsWith("_chestplate")
                || name.endsWith("_leggings")
                || name.endsWith("_boots")
                || material == Material.SHIELD;
    }
}
