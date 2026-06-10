package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.CurrencyManager;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

public final class CurrencyCraftListener implements Listener {

    private final CurrencyManager currencyManager;

    public CurrencyCraftListener(final CurrencyManager currencyManager) {
        this.currencyManager = currencyManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        for (final ItemStack itemStack : event.getInventory().getMatrix()) {
            if (currencyManager.isCurrencyItem(itemStack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}



