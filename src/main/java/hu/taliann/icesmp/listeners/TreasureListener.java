package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.TreasureEventManager;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Grants the treasure to the first player who opens the marked chest. The
 * interact fires on the player's own region thread — the same region as the
 * block — so {@link TreasureEventManager#claim} mutates the block and inventory
 * without a hop. Cancelling the event suppresses the vanilla chest GUI so the
 * loot is handed out exactly once.
 */
public final class TreasureListener implements Listener {

    private final TreasureEventManager treasureEventManager;

    public TreasureListener(final TreasureEventManager treasureEventManager) {
        this.treasureEventManager = treasureEventManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Block block = event.getClickedBlock();
        if (block == null || !treasureEventManager.isTreasureBlock(block)) {
            return;
        }
        event.setCancelled(true);
        treasureEventManager.claim(event.getPlayer());
    }

    /** Breaking the marked chest awards it to the breaker rather than losing the loot. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        if (!treasureEventManager.isTreasureBlock(event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        treasureEventManager.claim(event.getPlayer());
    }
}
