package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.RelicManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

public final class RelicCraftSafetyListener implements Listener {

    private final RelicManager relicManager;

    public RelicCraftSafetyListener(final RelicManager relicManager) {
        this.relicManager = relicManager;
    }

    @EventHandler
    public void onPrepareCraft(final PrepareItemCraftEvent event) {
        final ItemStack[] matrix = event.getInventory().getMatrix();
        for (final ItemStack ingredient : matrix) {
            if (relicManager.isRelicItem(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}


