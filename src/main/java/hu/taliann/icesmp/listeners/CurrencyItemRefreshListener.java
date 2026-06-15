package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.CurrencyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CurrencyItemRefreshListener implements Listener {

    private final JavaPlugin plugin;
    private final CurrencyManager currencyManager;

    public CurrencyItemRefreshListener(final JavaPlugin plugin, final CurrencyManager currencyManager) {
        this.plugin = plugin;
        this.currencyManager = currencyManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Folia: refresh on the player's own region scheduler next tick.
        player.getScheduler().run(plugin, task -> currencyManager.refreshPlayerCurrencyItems(player), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Folia: refresh on the player's own region scheduler next tick.
        player.getScheduler().run(plugin, task -> currencyManager.refreshPlayerCurrencyItems(player), null);
    }
}


