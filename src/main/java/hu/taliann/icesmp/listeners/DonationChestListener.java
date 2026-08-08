package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.DonationChestGUI;
import hu.taliann.icesmp.gui.DonationChestHolder;
import hu.taliann.icesmp.managers.DonationChestManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One-way donation input plus shared read/take browsing.
 *
 * <p>Every top-inventory mutation is cancelled. Inventory ownership changes are
 * deferred to the player's next entity tick, after vanilla has finished the cancelled
 * click/drag transaction, and committed only if the source still equals the captured
 * stack. This prevents event post-processing from restoring a donated item.</p>
 */
public final class DonationChestListener implements Listener {

    private final DonationChestManager manager;
    private final MessageManager messages;
    private final JavaPlugin plugin =
            JavaPlugin.getProvidingPlugin(DonationChestListener.class);

    public DonationChestListener(final DonationChestManager manager,
                                 final MessageManager messages) {
        this.manager = manager;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder()
                instanceof DonationChestHolder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!holder.getOwnerUuid().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        final int topSize = event.getView().getTopInventory().getSize();
        final int rawSlot = event.getRawSlot();

        // The lower inventory stays usable, except operations that could move a
        // rendered top-inventory copy or intentionally donate via shift-click.
        if (rawSlot >= topSize) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                final ItemStack expected = cloneItem(event.getCurrentItem());
                submitInventoryDeposit(player, holder, event.getSlot(),
                        expected, amount(expected));
            } else if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                    || event.getClick() == ClickType.DOUBLE_CLICK
                    || event.getAction() == InventoryAction.UNKNOWN) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        if (rawSlot < 0) {
            return;
        }
        if (DonationChestGUI.isDepositSlot(rawSlot)) {
            handleDepositClick(player, holder, event);
            return;
        }

        switch (rawSlot) {
            case DonationChestGUI.PREV_SLOT ->
                    reopen(player, holder.getPage() - 1);
            case DonationChestGUI.NEXT_SLOT ->
                    reopen(player, holder.getPage() + 1);
            case DonationChestGUI.MENU_SLOT -> runPlayerTask(player,
                    () -> player.performCommand("menu"));
            case DonationChestGUI.HELP_SLOT,
                 DonationChestGUI.PAGE_INFO_SLOT -> {
                // Informational controls.
            }
            default -> {
                if (DonationChestGUI.isContentSlot(rawSlot)) {
                    submitTake(player, holder, holder.getEntryAt(rawSlot));
                }
            }
        }
    }

    private void handleDepositClick(final Player player,
                                    final DonationChestHolder holder,
                                    final InventoryClickEvent event) {
        final ClickType click = event.getClick();
        if (click == ClickType.NUMBER_KEY) {
            final int hotbar = event.getHotbarButton();
            final ItemStack expected = hotbar < 0 ? null
                    : cloneItem(player.getInventory().getItem(hotbar));
            submitInventoryDeposit(player, holder, hotbar,
                    expected, amount(expected));
            return;
        }
        if (click == ClickType.SWAP_OFFHAND) {
            final ItemStack expected = cloneItem(
                    player.getInventory().getItemInOffHand());
            submitDonation(player, holder, () -> manager.donateOffHand(
                    player, expected, amount(expected)));
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.RIGHT) {
            return;
        }

        final ItemStack expected = cloneItem(event.getCursor());
        if (isEmpty(expected)) {
            return;
        }
        final int moved = click == ClickType.RIGHT ? 1 : expected.getAmount();
        submitDonation(player, holder,
                () -> manager.donateCursor(player, expected, moved));
    }

    private void submitInventoryDeposit(final Player player,
                                        final DonationChestHolder holder,
                                        final int slot,
                                        final ItemStack expected,
                                        final int amount) {
        submitDonation(player, holder, () -> manager.donateInventorySlot(
                player, slot, expected, amount));
    }

    /** Runs after the cancelled inventory event, on the player's owning entity thread. */
    private void submitDonation(final Player player,
                                final DonationChestHolder holder,
                                final Supplier<String> transaction) {
        runPlayerTask(player, () -> {
            final String errorKey;
            try {
                errorKey = transaction.get();
            } catch (final RuntimeException failure) {
                plugin.getLogger().warning("Donation deposit transaction failed for "
                        + player.getUniqueId() + ": " + failure);
                handleResult(player, holder, "donation-transaction-failed");
                return;
            }
            handleResult(player, holder, errorKey);
        });
    }

    private void handleResult(final Player player,
                              final DonationChestHolder holder,
                              final String errorKey) {
        if (errorKey != null) {
            player.playSound(player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messages.get(errorKey,
                    DonationChestManager.defaultErrorFor(errorKey),
                    Map.of("limit",
                            String.valueOf(manager.getMaxPerPlayer()))));
            refreshIfViewing(player, holder);
            return;
        }
        player.playSound(player.getLocation(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
        player.sendMessage(messages.getMessage(
                "donation-add-success",
                "&aA tárgy az adomány-ládába került."));
        refreshIfViewing(player, holder);
    }

    private void submitTake(final Player player,
                            final DonationChestHolder holder,
                            final UUID entryId) {
        if (entryId == null) {
            return;
        }
        runPlayerTask(player, () -> handleTake(player, holder, entryId));
    }

    private void handleTake(final Player player,
                            final DonationChestHolder holder,
                            final UUID entryId) {
        if (!isEmpty(player.getItemOnCursor())) {
            player.sendMessage(messages.get(
                    "donation-take-cursor",
                    "&eElőbb tedd le a kurzoron lévő tárgyat."));
            return;
        }

        final ItemStack item;
        try {
            item = manager.takeEntry(entryId);
        } catch (final RuntimeException failure) {
            player.playSound(player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messages.get(
                    "donation-take-failed",
                    "&cAz adomány most nem vehető ki; próbáld újra."));
            return;
        }
        if (item == null) {
            player.playSound(player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messages.get(
                    "donation-take-gone",
                    "&cEzt a tárgyat már elvitte valaki más."));
            refreshIfViewing(player, holder);
            return;
        }

        final Map<Integer, ItemStack> leftovers =
                player.getInventory().addItem(item);
        leftovers.values().forEach(left ->
                player.getWorld().dropItemNaturally(
                        player.getLocation(), left));

        player.playSound(player.getLocation(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                1.0F, 1.2F);
        player.sendMessage(messages.get(
                "donation-take-success",
                "&aElvettél egy adományt az adomány-ládából."));
        refreshIfViewing(player, holder);
    }

    private void refreshIfViewing(final Player player,
                                  final DonationChestHolder holder) {
        if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
            DonationChestGUI.open(player, manager, messages,
                    Math.max(0, holder.getPage()));
        }
    }

    private void reopen(final Player player, final int page) {
        runPlayerTask(player, () -> DonationChestGUI.open(
                player, manager, messages, Math.max(0, page)));
    }

    private void runPlayerTask(final Player player, final Runnable action) {
        try {
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline()) {
                    action.run();
                }
            }, null);
        } catch (final RuntimeException unavailable) {
            // The player/plugin retired before the post-event operation.
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder()
                instanceof DonationChestHolder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int topSize = event.getView().getTopInventory().getSize();
        final boolean touchesTop = event.getRawSlots().stream()
                .anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }

        event.setCancelled(true);
        if (!holder.getOwnerUuid().equals(player.getUniqueId())
                || !event.getRawSlots().stream()
                .allMatch(DonationChestGUI::isDepositSlot)) {
            return;
        }

        final ItemStack expected = cloneItem(event.getOldCursor());
        if (isEmpty(expected)) {
            return;
        }
        int moved = 0;
        for (final Map.Entry<Integer, ItemStack> entry
                : event.getNewItems().entrySet()) {
            if (!DonationChestGUI.isDepositSlot(entry.getKey())
                    || isEmpty(entry.getValue())
                    || !entry.getValue().isSimilar(expected)) {
                return;
            }
            moved += entry.getValue().getAmount();
        }
        if (moved <= 0 || moved > expected.getAmount()) {
            return;
        }
        final int committedAmount = moved;
        submitDonation(player, holder, () -> manager.donateCursor(
                player, expected, committedAmount));
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder()
                instanceof DonationChestHolder holder) {
            holder.setInventory(null);
        }
    }

    private static int amount(final ItemStack item) {
        return isEmpty(item) ? 0 : item.getAmount();
    }

    private static ItemStack cloneItem(final ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static boolean isEmpty(final ItemStack item) {
        return item == null || item.getType().isAir()
                || item.getAmount() <= 0;
    }
}
