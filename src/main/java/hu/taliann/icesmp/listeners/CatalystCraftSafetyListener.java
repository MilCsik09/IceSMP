package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.CatalystItemFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents Ability Catalyst items from being consumed as crafting ingredients
 * or furnace fuel. Important for the FLINT and RABBIT_HIDE based catalysts,
 * which are valid vanilla recipe ingredients.
 */
public final class CatalystCraftSafetyListener implements Listener {

    private final CatalystItemFactory catalystItemFactory;

    public CatalystCraftSafetyListener(final CatalystItemFactory catalystItemFactory) {
        this.catalystItemFactory = catalystItemFactory;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        for (final ItemStack itemStack : event.getInventory().getMatrix()) {
            if (catalystItemFactory.isCatalyst(itemStack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(final FurnaceBurnEvent event) {
        if (catalystItemFactory.isCatalyst(event.getFuel())) {
            event.setCancelled(true);
        }
    }
}
