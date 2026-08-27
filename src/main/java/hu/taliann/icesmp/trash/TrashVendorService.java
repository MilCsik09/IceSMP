package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.utils.DailyBudget;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Hidden vendor route with a durable forward-recovery journal and idempotent payout. */
public final class TrashVendorService implements Listener {

    private static final String BUDGET_ID = "buyer";
    private static final String PAYOUT_PREFIX = "trash-vendor:";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TrashCatalog catalog;
    private final TrashItemFactory itemFactory;
    private final TrashRecyclePool recyclePool;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final NamespacedKey saleMarker;

    public TrashVendorService(final JavaPlugin plugin, final ConfigManager configManager,
                              final TrashCatalog catalog, final TrashItemFactory itemFactory,
                              final TrashRecyclePool recyclePool,
                              final CurrencyManager currencyManager,
                              final FactionManager factionManager,
                              final MessageManager messageManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.recyclePool = Objects.requireNonNull(recyclePool, "recyclePool");
        this.currencyManager = Objects.requireNonNull(currencyManager, "currencyManager");
        this.factionManager = Objects.requireNonNull(factionManager, "factionManager");
        this.messageManager = Objects.requireNonNull(messageManager, "messageManager");
        this.saleMarker = new NamespacedKey(plugin, "trash_vendor_sale");
    }

    /** @return true when the held item was Trash and this service fully handled the interaction. */
    public boolean tryHandle(final Player player, final ItemStack hand) {
        final String id = itemFactory.idOf(hand).orElse(null);
        if (id == null) return false;
        if (!configManager.getBoolean("trash-runtime.enabled", true)) {
            player.sendMessage(messageManager.getMessage("buyer-not-buying",
                    "<gray>🪙 „Ilyesmire most nincs vevőm.”</gray>"));
            return true;
        }
        if (!recyclePool.openSales(player.getUniqueId()).isEmpty()) {
            recover(player, true);
            if (!recyclePool.openSales(player.getUniqueId()).isEmpty()) {
                transactionUnavailable(player);
                return true;
            }
        }

        final TrashDefinition definition = catalog.require(id);
        final int unitValue = definition.vendorValue();
        final long soldToday = DailyBudget.spentTodayOnOwnThread(player, BUDGET_ID);
        final long dailyCap = Math.max(0L,
                (long) configManager.getDouble("buyer.daily-cap", 250.0D));
        final long remaining = dailyCap <= 0L ? Long.MAX_VALUE
                : Math.max(0L, dailyCap - soldToday);
        final int sellable = (int) Math.min(hand.getAmount(), remaining / unitValue);
        if (sellable < 1) {
            player.sendMessage(messageManager.getMessage("buyer-cap-reached",
                    "<gray>🪙 „Mára kimerült a kasszám feléd — gyere vissza holnap!”</gray>"));
            return true;
        }
        final long value = Math.multiplyExact((long) sellable, unitValue);
        final CurrencyType currency = CurrencyType.fromFactionType(
                factionManager.getEconomyFaction(player.getUniqueId()));
        final int slot = player.getInventory().getHeldItemSlot();
        final TrashRecyclePool.SaleTransaction sale;
        try {
            sale = recyclePool.prepareSale(player.getUniqueId(), slot, hand.clone(), sellable,
                    currency.name(), value, DailyBudget.dayIndex(), soldToday);
            markSale(hand, sale.operationId());
            player.getInventory().setItem(slot, hand);
            if (!DailyBudget.tryConsumeDurablyOnOwnThread(
                    player, BUDGET_ID, dailyCap, value)) {
                clearSaleMarker(hand, sale.operationId());
                player.getInventory().setItem(slot, hand);
                recyclePool.cancelPrepared(sale.operationId());
                player.sendMessage(messageManager.getMessage("buyer-cap-reached",
                        "<gray>🪙 „Mára kimerült a kasszám feléd — gyere vissza holnap!”</gray>"));
                return true;
            }
            recyclePool.markBudgetReserved(sale.operationId());
            resume(player, sale.operationId());
        } catch (final RuntimeException failure) {
            plugin.getLogger().severe("Trash vendor transaction paused for "
                    + player.getUniqueId() + ": " + failure.getMessage());
            transactionUnavailable(player);
            return true;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.8F, 1.1F);
        player.sendMessage(messageManager.getMessage("buyer-trash-sold",
                "<gold>🪙 Eladva: <white>{amount}× {item}</white> — <white>{value}× veret</white> az egyenlegedre. <gray>(Mai keretedből maradt: {left})</gray></gold>",
                Map.of("amount", String.valueOf(sellable),
                        "item", definition.displayName(),
                        "value", String.valueOf(value),
                        "left", currencyManager.formatBalance(
                                dailyCap <= 0L ? 0.0D : Math.max(0L, remaining - value)))));
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        player.getScheduler().run(plugin, task -> recover(player, false), null);
    }

    private void recover(final Player player, final boolean immediate) {
        clearUnknownMarkers(player);
        for (final TrashRecyclePool.SaleTransaction sale
                : recyclePool.openSales(player.getUniqueId())) {
            try {
                if (sale.stage() == TrashRecyclePool.SaleStage.PREPARED) {
                    final boolean unlimited = configManager.getDouble("buyer.daily-cap", 250.0D) <= 0.0D;
                    final long current = DailyBudget.spentTodayOnOwnThread(player, BUDGET_ID);
                    final boolean reservationVisible = sale.budgetDay() == DailyBudget.dayIndex()
                            && current >= Math.addExact(sale.budgetBefore(), sale.value());
                    if (!unlimited && !reservationVisible) {
                        clearSaleMarker(player, sale.operationId());
                        recyclePool.cancelPrepared(sale.operationId());
                        continue;
                    }
                    recyclePool.markBudgetReserved(sale.operationId());
                }
                resume(player, sale.operationId());
                if (!immediate) {
                    player.sendMessage(messageManager.getMessage("buyer-trash-recovered",
                            "<gray>🪙 A korábban félbeszakadt felvásárlás befejeződött.</gray>"));
                }
            } catch (final RuntimeException failure) {
                plugin.getLogger().severe("Trash vendor recovery paused for "
                        + player.getUniqueId() + "/" + sale.operationId() + ": "
                        + failure.getMessage());
            }
        }
    }

    private void resume(final Player player, final UUID operationId) {
        TrashRecyclePool.SaleTransaction sale = recyclePool.findSale(operationId).orElseThrow();
        if (sale.stage() == TrashRecyclePool.SaleStage.BUDGET_RESERVED) {
            removeSoldUnits(player, sale);
            sale = recyclePool.markItemRemoved(operationId);
        }
        if (sale.stage() == TrashRecyclePool.SaleStage.ITEM_REMOVED) {
            sale = recyclePool.commitRecycle(operationId);
        }
        if (sale.stage() == TrashRecyclePool.SaleStage.POOL_COMMITTED) {
            final CurrencyType currency = CurrencyType.valueOf(sale.currency());
            currencyManager.creditOnceDurably(player.getUniqueId(), currency, sale.value(),
                    PAYOUT_PREFIX + operationId);
            sale = recyclePool.markPaid(operationId);
        }
        if (sale.stage() == TrashRecyclePool.SaleStage.PAID) {
            recyclePool.completeSale(operationId);
        }
    }

    private void removeSoldUnits(final Player player,
                                 final TrashRecyclePool.SaleTransaction sale) {
        final int markedSlot = findMarkedSlot(player, sale.operationId());
        final int slot = markedSlot >= 0 ? markedSlot : sale.slot();
        final ItemStack live = player.getInventory().getItem(slot);
        if (live == null || live.getType().isAir()) return;
        final boolean marked = sale.operationId().toString().equals(markerOf(live));
        final boolean sameIdentity = sale.trashId().equals(itemFactory.idOf(live).orElse(null));
        if (!sameIdentity) {
            if (marked) throw new IllegalStateException("a vendor marker más Trash identityn maradt");
            return;
        }
        final int expectedRemainder = sale.originalAmount() - sale.soldAmount();
        if (live.getAmount() == sale.originalAmount()) {
            live.setAmount(expectedRemainder);
        } else if (!marked || live.getAmount() != expectedRemainder) {
            throw new IllegalStateException("a Trash vendor source mennyisége nem recoverálható");
        }
        if (live.getAmount() > 0) {
            clearSaleMarker(live, sale.operationId());
            player.getInventory().setItem(slot, live);
        } else {
            player.getInventory().setItem(slot, null);
        }
    }

    private int findMarkedSlot(final Player player, final UUID operationId) {
        final String expected = operationId.toString();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            if (item != null && expected.equals(markerOf(item))) return slot;
        }
        return -1;
    }

    private void clearUnknownMarkers(final Player player) {
        final List<UUID> known = recyclePool.openSales(player.getUniqueId()).stream()
                .map(TrashRecyclePool.SaleTransaction::operationId).toList();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            final String raw = markerOf(item);
            if (raw == null) continue;
            final UUID operationId;
            try {
                operationId = UUID.fromString(raw);
            } catch (final IllegalArgumentException malformed) {
                clearAnySaleMarker(item);
                player.getInventory().setItem(slot, item);
                continue;
            }
            if (!known.contains(operationId)) {
                clearAnySaleMarker(item);
                player.getInventory().setItem(slot, item);
            }
        }
    }

    private void clearSaleMarker(final Player player, final UUID operationId) {
        final int slot = findMarkedSlot(player, operationId);
        if (slot < 0) return;
        final ItemStack item = player.getInventory().getItem(slot);
        clearSaleMarker(item, operationId);
        player.getInventory().setItem(slot, item);
    }

    private void markSale(final ItemStack item, final UUID operationId) {
        final ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(saleMarker, PersistentDataType.STRING,
                operationId.toString());
        item.setItemMeta(meta);
        itemFactory.refreshPresentation(item);
    }

    private void clearSaleMarker(final ItemStack item, final UUID operationId) {
        if (item == null || !operationId.toString().equals(markerOf(item))) return;
        clearAnySaleMarker(item);
    }

    private void clearAnySaleMarker(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        final ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(saleMarker);
        item.setItemMeta(meta);
        if (itemFactory.isKnownItem(item)) itemFactory.refreshPresentation(item);
    }

    private String markerOf(final ItemStack item) {
        return item == null || !item.hasItemMeta() ? null
                : item.getItemMeta().getPersistentDataContainer().get(
                        saleMarker, PersistentDataType.STRING);
    }

    private void transactionUnavailable(final Player player) {
        player.sendMessage(messageManager.getMessage("buyer-transaction-paused",
                "<red>🪙 A felvásárlás most nem zárható le biztonságosan; a rendszer automatikusan újrapróbálja.</red>"));
    }
}
