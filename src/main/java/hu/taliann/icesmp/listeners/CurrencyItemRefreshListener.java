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

        plugin.getServer().getScheduler().runTask(plugin, () -> currencyManager.refreshPlayerCurrencyItems(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> currencyManager.refreshPlayerCurrencyItems(player));
    }
}


