package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.managers.KingManager;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded significant-event hooks; all player inventory work stays on the player scheduler. */
public final class TrashHistoryListener implements Listener {

    private static final long WARNING_INTERVAL_MILLIS = 60_000L;

    private final JavaPlugin plugin;
    private final TrashHistoryService history;
    private final KingManager kings;
    private final AtomicLong nextWarning = new AtomicLong();

    public TrashHistoryListener(final JavaPlugin plugin, final TrashHistoryService history,
                                final KingManager kings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.history = Objects.requireNonNull(history, "history");
        this.kings = Objects.requireNonNull(kings, "kings");
    }

    /** Never dereference the other Item entity from a player pickup callback. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) scheduleHeldScan(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldSlotChange(final PlayerItemHeldEvent event) {
        scheduleHeldScan(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(final PlayerSwapHandItemsEvent event) {
        scheduleHeldScan(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryChange(final InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) scheduleHeldScan(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(final PlayerDeathEvent event) {
        final UUID playerId = event.getEntity().getUniqueId();
        for (final ItemStack drop : event.getDrops()) {
            guarded(() -> history.recordIfTracked(drop,
                    TrashHistoryEvent.PRESENT_AT_PLAYER_DEATH, playerId, ""));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        if (event.getFrom().getEnvironment() != World.Environment.NETHER
                && player.getWorld().getEnvironment() != World.Environment.NETHER) return;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            if (item == null) continue;
            final int targetSlot = slot;
            guarded(() -> {
                if (history.recordIfTracked(item, TrashHistoryEvent.NETHER_TRANSIT,
                        player.getUniqueId(), "")) {
                    player.getInventory().setItem(targetSlot, item);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMend(final PlayerItemMendEvent event) {
        if (event.getRepairAmount() <= 0
                || !(event.getItem().getItemMeta() instanceof Damageable damageable)) return;
        final int previousDamage = damageable.getDamage();
        final Player player = event.getPlayer();
        final String token = guardedValue(() -> history.markPreparedRepair(
                event.getItem(), event.getItem(), player.getUniqueId()),
                Optional.<String>empty()).orElse(null);
        if (token == null) return;
        final EquipmentSlot slot = event.getSlot();
        player.getScheduler().run(plugin, task -> {
            final ItemStack repaired = player.getInventory().getItem(slot);
            final boolean changed = repaired != null
                    && repaired.getItemMeta() instanceof Damageable after
                    && after.getDamage() < previousDamage;
            guarded(() -> {
                if (changed) history.completePreparedRepair(repaired, token);
                else history.clearPreparedRepair(repaired, token);
                player.getInventory().setItem(slot, repaired);
            });
        }, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(final PrepareAnvilEvent event) {
        markPreparedRepair(event.getInventory(), event.getResult()).ifPresent(event::setResult);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(final PrepareGrindstoneEvent event) {
        markPreparedRepair(event.getInventory(), event.getResult()).ifPresent(event::setResult);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRepairResult(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getSlotType() != InventoryType.SlotType.RESULT) return;
        final Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.ANVIL
                && top.getType() != InventoryType.GRINDSTONE) return;
        final ItemStack output = event.getCurrentItem();
        if (!isDurabilityRepair(top.getItem(0), output)
                && !isDurabilityRepair(top.getItem(1), output)) return;
        final String token = guardedValue(() -> history.preparedRepairToken(output),
                Optional.<String>empty()).orElse(null);
        if (token != null) {
            player.getScheduler().run(plugin,
                    task -> completePreparedRepair(player, token), null);
        }
    }

    /** ItemSpawnEvent owns this Item entity's region, including direct result-slot drops. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(final ItemSpawnEvent event) {
        final Item itemEntity = event.getEntity();
        final ItemStack item = itemEntity.getItemStack();
        final String token = guardedValue(() -> history.preparedRepairToken(item),
                Optional.<String>empty()).orElse(null);
        if (token == null) return;
        guarded(() -> {
            if (!history.completePreparedRepair(item, token)) {
                history.clearPreparedRepair(item, token);
            }
            itemEntity.setItemStack(item);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        scheduleHeldScan(event.getPlayer());
    }

    private Optional<ItemStack> markPreparedRepair(final Inventory inventory,
                                                   final ItemStack result) {
        if (result == null) return Optional.empty();
        final Player actor = inventory.getViewers().stream()
                .filter(Player.class::isInstance).map(Player.class::cast).findFirst().orElse(null);
        if (actor == null) return Optional.empty();
        final ItemStack input0 = inventory.getItem(0);
        final ItemStack input1 = inventory.getItem(1);
        final ItemStack source = isDurabilityRepair(input0, result) ? input0
                : isDurabilityRepair(input1, result) ? input1 : null;
        if (source == null) return Optional.empty();
        return guardedValue(() -> history.markPreparedRepair(
                source, result, actor.getUniqueId()).map(ignored -> result), Optional.empty());
    }

    private void scheduleHeldScan(final Player player) {
        player.getScheduler().run(plugin, task -> scanPlayer(player), null);
    }

    private void scanPlayer(final Player player) {
        completePreparedRepairs(player);
        scanHeld(player, EquipmentSlot.HAND);
        scanHeld(player, EquipmentSlot.OFF_HAND);
    }

    private void scanHeld(final Player player, final EquipmentSlot slot) {
        final ItemStack item = slot == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (item == null || item.getType().isAir()) return;
        guarded(() -> {
            if (history.observeOwnerIfTracked(
                    item, player.getUniqueId(), kings.isKing(player))) {
                setHeldItem(player, slot, item);
                return;
            }
            if (!kings.isKing(player) || !history.isTrash(item)) return;
            if (item.getAmount() > 1 && player.getInventory().firstEmpty() < 0) return;
            final TrashHistoryService.SplitResult split =
                    history.splitForKing(item, player.getUniqueId());
            setHeldItem(player, slot, split.singleton());
            if (split.remainder() != null
                    && !player.getInventory().addItem(split.remainder()).isEmpty()) {
                throw new IllegalStateException("a king Trash split remainder nem fér el");
            }
        });
    }

    private static void setHeldItem(final Player player, final EquipmentSlot slot,
                                    final ItemStack item) {
        if (slot == EquipmentSlot.HAND) player.getInventory().setItemInMainHand(item);
        else player.getInventory().setItemInOffHand(item);
    }

    private void completePreparedRepair(final Player player, final String token) {
        final ItemStack cursor = player.getItemOnCursor();
        if (completePreparedRepair(cursor, token)) {
            player.setItemOnCursor(cursor);
            return;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            if (!completePreparedRepair(item, token)) continue;
            player.getInventory().setItem(slot, item);
            return;
        }
    }

    private void completePreparedRepairs(final Player player) {
        final ItemStack cursor = player.getItemOnCursor();
        completeAnyPreparedRepair(cursor);
        player.setItemOnCursor(cursor);
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            if (!completeAnyPreparedRepair(item)) continue;
            player.getInventory().setItem(slot, item);
        }
    }

    private boolean completePreparedRepair(final ItemStack item, final String token) {
        return item != null && guardedValue(
                () -> history.completePreparedRepair(item, token), false);
    }

    private boolean completeAnyPreparedRepair(final ItemStack item) {
        if (item == null) return false;
        final String token = guardedValue(() -> history.preparedRepairToken(item),
                Optional.<String>empty()).orElse(null);
        if (token == null) return false;
        if (!completePreparedRepair(item, token)) {
            guarded(() -> history.clearPreparedRepair(item, token));
        }
        return true;
    }

    private static boolean isDurabilityRepair(final ItemStack input, final ItemStack output) {
        if (input == null || output == null || input.getType().isAir() || output.getType().isAir()
                || !(input.getItemMeta() instanceof Damageable before)
                || !(output.getItemMeta() instanceof Damageable after)) return false;
        return after.getDamage() < before.getDamage();
    }

    private void guarded(final Runnable action) {
        try {
            action.run();
        } catch (final RuntimeException rejected) {
            final long now = System.currentTimeMillis();
            final long due = nextWarning.get();
            if (now >= due && nextWarning.compareAndSet(due, now + WARNING_INTERVAL_MILLIS)) {
                plugin.getLogger().warning(
                        "Trash history mutation rejected; hidden item state was left unchanged.");
            }
        }
    }

    private <T> T guardedValue(final java.util.function.Supplier<T> action, final T fallback) {
        try {
            return action.get();
        } catch (final RuntimeException rejected) {
            guarded(() -> { throw rejected; });
            return fallback;
        }
    }
}
