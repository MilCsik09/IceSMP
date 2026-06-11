package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.TalentManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Awards profession XP for gathering and crafting activities:
 * MINER for ore mining, FARMER for harvesting ripe crops,
 * ARMORER for crafting/upgrading armor and tools, FISHERMAN for catching fish.
 */
public final class ProfessionXpListener implements Listener {

    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART
    );

    private final ProfessionManager professionManager;
    private final ConfigManager configManager;
    private final TalentManager talentManager;

    public ProfessionXpListener(final ProfessionManager professionManager, final ConfigManager configManager,
                                final TalentManager talentManager) {
        this.professionManager = professionManager;
        this.configManager = configManager;
        this.talentManager = talentManager;
    }

    private void awardXp(final Player player, final ProfessionType profession, final String configPath, final int fallback) {
        final int baseXp = Math.max(0, configManager.getInt(configPath, fallback));
        final double bonusPercent = Math.max(0.0D, talentManager.getEffectTotal(player, "profession-xp-bonus"));
        final int totalXp = (int) Math.round(baseXp * (1.0D + (bonusPercent / 100.0D)));
        professionManager.addXpFor(player, profession, totalXp);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final Material material = block.getType();

        if (isOre(material)) {
            awardXp(player, ProfessionType.MINER, "professions.xp.mining-ore", 5);
            return;
        }

        if (CROPS.contains(material) && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge()) {
            awardXp(player, ProfessionType.FARMER, "professions.xp.farming-harvest", 3);
        }
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
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        awardXp(event.getPlayer(), ProfessionType.FISHERMAN, "professions.xp.fishing", 4);
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
