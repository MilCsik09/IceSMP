package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.relics.RelicTrigger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class RelicTriggerListener implements Listener {

    private final RelicManager relicManager;

    public RelicTriggerListener(final RelicManager relicManager) {
        this.relicManager = relicManager;
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        final RelicTrigger trigger = resolveTrigger(event.getAction());
        if (trigger == null) {
            return;
        }

        final ItemStack itemStack = event.getItem();
        if (!relicManager.isRelicItem(itemStack)) {
            return;
        }

        relicManager.handleTrigger(event.getPlayer(), itemStack, trigger);
    }

    private RelicTrigger resolveTrigger(final Action action) {
        if (action == Action.RIGHT_CLICK_AIR) {
            return RelicTrigger.RIGHT_CLICK_AIR;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            return RelicTrigger.RIGHT_CLICK_BLOCK;
        }

        return null;
    }
}


