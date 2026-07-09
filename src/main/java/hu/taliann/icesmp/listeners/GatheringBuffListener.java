package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.GatheringBuffManager;
import hu.taliann.icesmp.managers.GatheringBuffManager.GatheringBuff;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies the active {@link GatheringBuffManager} window's bonus to everyday
 * gathering. All handlers run on the acting player's / block's own region thread,
 * so dropping the bonus item or adjusting the XP amount is Folia-safe with no hop.
 * Bonuses are pure item/XP faucets (ore, crops, fish, experience) — never currency.
 */
public final class GatheringBuffListener implements Listener {

    private final GatheringBuffManager gatheringBuffManager;

    public GatheringBuffListener(final GatheringBuffManager gatheringBuffManager) {
        this.gatheringBuffManager = gatheringBuffManager;
    }

    /** Mining rush → bonus ore drops; harvest hour → bonus mature-crop drops. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (!isSurvival(player) || !event.isDropItems()) {
            return;
        }

        final Block block = event.getBlock();
        final GatheringBuff active = gatheringBuffManager.getActive();
        final boolean isOre = active == GatheringBuff.MINING_RUSH && block.getType().name().endsWith("_ORE");
        final boolean isCrop = active == GatheringBuff.HARVEST_HOUR && isMatureCrop(block);
        if (!isOre && !isCrop) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() >= gatheringBuffManager.bonusDropChance(active)) {
            return;
        }

        // Re-drop the block's natural yield once more as a bonus (respects the tool used).
        final ItemStack tool = player.getInventory().getItemInMainHand();
        final Location at = block.getLocation().add(0.5D, 0.5D, 0.5D);
        for (final ItemStack drop : block.getDrops(tool)) {
            if (drop != null && !drop.getType().isAir()) {
                block.getWorld().dropItemNaturally(at, drop.clone());
            }
        }
    }

    /** Fishing frenzy → a chance to reel in a second copy of the catch. */
    @EventHandler(ignoreCancelled = true)
    public void onFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || gatheringBuffManager.getActive() != GatheringBuff.FISHING_FRENZY
                || !(event.getCaught() instanceof Item caughtItem)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= gatheringBuffManager.bonusDropChance(GatheringBuff.FISHING_FRENZY)) {
            return;
        }
        final Player player = event.getPlayer();
        final ItemStack extra = caughtItem.getItemStack().clone();
        player.getWorld().dropItemNaturally(player.getLocation(), extra);
    }

    /** XP hour → multiply every XP gain. */
    @EventHandler
    public void onExpChange(final PlayerExpChangeEvent event) {
        final double multiplier = gatheringBuffManager.xpMultiplier();
        if (multiplier <= 1.0D || event.getAmount() <= 0) {
            return;
        }
        event.setAmount((int) Math.round(event.getAmount() * multiplier));
    }

    private static boolean isMatureCrop(final Block block) {
        return block.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge();
    }

    private static boolean isSurvival(final Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }
}
