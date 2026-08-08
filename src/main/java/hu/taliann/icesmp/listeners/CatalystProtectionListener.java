package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * Physical Lélekkapocs protection.
 *
 * <p>The ItemStack is a rebuildable owner-bound mirror, never class/spec authority. A personal
 * artifact cannot be dropped, transferred into external inventories, or picked up by another
 * player. On death it is removed from drops and retained through {@link PlayerDeathEvent#getItemsToKeep()},
 * so there is no claim/materialize/redeposit crash window for this rebuildable item.</p>
 */
public final class CatalystProtectionListener implements Listener {

    private final CatalystItemFactory catalystItemFactory;
    private final MessageManager messageManager;

    public CatalystProtectionListener(final JavaPlugin plugin,
                                      final CatalystItemFactory catalystItemFactory,
                                      final MessageManager messageManager) {
        this.catalystItemFactory = catalystItemFactory;
        this.messageManager = messageManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        if (!catalystItemFactory.isCatalyst(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(messageManager.get("catalyst-no-drop",
                "&8A Lélekkapocs nem hagyja el a gazdáját — a Fa ajándéka veled marad."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        final ItemStack item = event.getItem().getItemStack();
        if (!catalystItemFactory.isCatalyst(item)) return;
        final UUID ownerId = catalystItemFactory.ownerOf(item).orElse(null);
        if (ownerId == null || ownerId.equals(player.getUniqueId())) return;
        event.setCancelled(true);
        player.sendActionBar(messageManager.getMessage("soulbond.foreign",
                "<red>Ez a Lélekkapocs nem hozzád tartozik.</red>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        final ItemStack current = event.getCurrentItem();
        final ItemStack cursor = event.getCursor();
        ItemStack hotbar = null;
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            hotbar = player.getInventory().getItem(event.getHotbarButton());
        }
        if (!isCatalyst(current) && !isCatalyst(cursor) && !isCatalyst(hotbar)) return;

        final boolean externalView = event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && event.getView().getTopInventory().getType() != InventoryType.CREATIVE;
        final boolean clickedPlayerInventory = event.getClickedInventory() == player.getInventory();
        final boolean shiftTransfer = event.isShiftClick();
        if (externalView || !clickedPlayerInventory || shiftTransfer) {
            event.setCancelled(true);
            player.sendActionBar(messageManager.getMessage("soulbond.no-transfer",
                    "<red>A személyes Lélekkapocs nem helyezhető át másik tárolóba.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        final int topSize = event.getView().getTopInventory().getSize();
        final boolean catalystTouchesTop = event.getNewItems().entrySet().stream()
                .anyMatch(entry -> entry.getKey() < topSize && isCatalyst(entry.getValue()));
        if (!catalystTouchesTop) return;
        event.setCancelled(true);
        player.sendActionBar(messageManager.getMessage("soulbond.no-transfer",
                "<red>A személyes Lélekkapocs nem helyezhető át másik tárolóba.</red>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(final PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final UUID playerId = player.getUniqueId();
        for (final var iterator = event.getDrops().iterator(); iterator.hasNext(); ) {
            final ItemStack drop = iterator.next();
            if (!catalystItemFactory.isCatalyst(drop)) continue;
            final UUID ownerId = catalystItemFactory.ownerOf(drop).orElse(null);
            if (ownerId == null || !ownerId.equals(playerId)) continue;
            iterator.remove();
            event.getItemsToKeep().add(drop);
        }
    }

    private boolean isCatalyst(final ItemStack item) {
        return catalystItemFactory.isCatalyst(item);
    }
}
