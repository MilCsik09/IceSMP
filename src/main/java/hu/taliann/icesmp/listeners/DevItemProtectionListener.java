package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.DevItemFactory;
import hu.taliann.icesmp.managers.DevItemManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps owner-bound DEV items inside the configured owner's personal inventory.
 */
public final class DevItemProtectionListener implements Listener {

    private final JavaPlugin plugin;
    private final DevItemManager manager;
    private final DevItemFactory factory;

    public DevItemProtectionListener(final JavaPlugin plugin, final DevItemManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.factory = manager.itemFactory();
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        event.getPlayer().getScheduler().runDelayed(plugin,
                task -> manager.handleRespawn(event.getPlayer()), null, 1L);
    }

    @EventHandler
    public void onDrop(final PlayerDropItemEvent event) {
        if (factory.isDevItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(final PlayerDeathEvent event) {
        event.getDrops().removeIf(factory::isDevItem);
    }

    @EventHandler
    public void onPickup(final EntityPickupItemEvent event) {
        if (!factory.isDevItem(event.getItem().getItemStack())) {
            return;
        }
        final java.util.UUID itemOwner = factory.ownerOf(event.getItem().getItemStack());
        if (!(event.getEntity() instanceof Player player)
                || itemOwner == null || !itemOwner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    /**
     * Denies the held item's own right-click behaviour while still allowing harmless block use
     * (for example opening a chest). A decorated pot is special: its block interaction itself can
     * swallow an arbitrary held item without opening an inventory, therefore it is cancelled fully.
     */
    @EventHandler
    public void onUse(final PlayerInteractEvent event) {
        if (!factory.isDevItem(event.getItem())) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.DECORATED_POT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onConsume(final PlayerItemConsumeEvent event) {
        if (factory.isDevItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(final BlockPlaceEvent event) {
        if (factory.isDevItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /**
     * Cancel every entity interaction while the DEV item is held. This covers item frames and armour
     * stands, but also item-consuming interactions such as handing the stack to an allay.
     */
    @EventHandler
    public void onEntityInteract(final PlayerInteractEntityEvent event) {
        if (factory.isDevItem(itemInHand(event.getPlayer(), event.getHand()))) {
            event.setCancelled(true);
        }
    }

    /** Some entities fire the more specific at-entity event with its own handler list. */
    @EventHandler
    public void onEntityInteractAt(final PlayerInteractAtEntityEvent event) {
        if (factory.isDevItem(itemInHand(event.getPlayer(), event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmorStand(final PlayerArmorStandManipulateEvent event) {
        if (factory.isDevItem(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(final InventoryMoveItemEvent event) {
        if (factory.isDevItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHopperPickup(final InventoryPickupItemEvent event) {
        if (factory.isDevItem(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (!factory.isDevItem(event.getOldCursor())) {
            return;
        }
        final int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final ItemStack current = event.getCurrentItem();
        final ItemStack cursor = event.getCursor();
        ItemStack hotbar = null;
        if (event.getHotbarButton() >= 0) {
            hotbar = player.getInventory().getItem(event.getHotbarButton());
        }

        final boolean offhandSwap = "SWAP_OFFHAND".equals(event.getClick().name())
                && factory.isDevItem(player.getInventory().getItemInOffHand());
        if (!factory.isDevItem(current) && !factory.isDevItem(cursor)
                && !factory.isDevItem(hotbar) && !offhandSwap) {
            return;
        }

        if (event.getAction().name().startsWith("DROP")
                || (factory.isDevItem(current) && cursor != null && cursor.getType() == Material.BUNDLE)
                || (factory.isDevItem(cursor) && current != null && current.getType() == Material.BUNDLE)) {
            event.setCancelled(true);
            return;
        }

        // Any open external inventory would let shift-click/number-key/drag escape the personal
        // inventory. While such a view is open, DEV item interactions are simply frozen.
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
            return;
        }

        // In the normal player-inventory screen a shift-click from PlayerInventory targets the 2x2
        // crafting matrix. That destination is not represented by getClickedInventory(), therefore it
        // needs an explicit action guard rather than relying only on the clicked inventory type.
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && factory.isDevItem(current)) {
            event.setCancelled(true);
            return;
        }

        // Movement is allowed only between actual PlayerInventory slots (hotbar, storage,
        // armour/offhand). Crafting/result/outside slots remain forbidden.
        if (!(event.getClickedInventory() instanceof PlayerInventory)) {
            event.setCancelled(true);
        }
    }

    private static ItemStack itemInHand(final Player player, final org.bukkit.inventory.EquipmentSlot hand) {
        return hand == org.bukkit.inventory.EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
    }
}
