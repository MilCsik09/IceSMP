package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.CommandMenuContext;
import hu.taliann.icesmp.gui.CommandMenuHolder;
import hu.taliann.icesmp.gui.CommandMenus;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Locale;

/**
 * Handles the /menu command-hub GUIs. Buttons carry an action string bound in
 * {@link CommandMenuHolder}; this listener dispatches them: open a sub-menu,
 * run a command (then refresh the menu), open a command that has its own GUI, or
 * close.
 */
public final class CommandMenuListener implements Listener {

    private final CommandMenuContext ctx;

    public CommandMenuListener(final CommandMenuContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CommandMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.getOwnerUuid().equals(player.getUniqueId())) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        final String action = holder.getActions().get(slot);
        if (action == null) {
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);

        if ("CLOSE".equals(action)) {
            player.closeInventory();
            return;
        }

        if (action.startsWith("MENU:")) {
            final CommandMenuHolder.Menu menu = parseMenu(action.substring(5));
            if (menu != null) {
                CommandMenus.open(player, menu, ctx);
            }
            return;
        }

        if (action.startsWith("LB:")) {
            final hu.taliann.icesmp.managers.StatsManager.Category category = switch (action.substring(3)) {
                case "wealth" -> hu.taliann.icesmp.managers.StatsManager.Category.WEALTH;
                case "raidkills" -> hu.taliann.icesmp.managers.StatsManager.Category.RAID_KILLS;
                default -> hu.taliann.icesmp.managers.StatsManager.Category.LEVEL;
            };
            CommandMenus.openLeaderboard(player, ctx, category);
            return;
        }

        if (action.startsWith("OPEN:")) {
            // Command opens its own GUI — close this one first, then dispatch.
            player.closeInventory();
            player.performCommand(action.substring(5));
            return;
        }

        if (action.startsWith("RUN:")) {
            player.performCommand(action.substring(4));
            // Refresh the current menu so updated state (balances, quests, ...) shows.
            CommandMenus.open(player, holder.getMenu(), ctx);
        }
    }

    private CommandMenuHolder.Menu parseMenu(final String raw) {
        try {
            return CommandMenuHolder.Menu.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CommandMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CommandMenuHolder holder) {
            holder.setInventory(null);
        }
    }
}
