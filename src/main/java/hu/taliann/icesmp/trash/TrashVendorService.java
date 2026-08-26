package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.utils.DailyBudget;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;

/** Existing Felvásárló integration: apparent value only, with no special-item warning or reveal. */
public final class TrashVendorService {

    private final ConfigManager configManager;
    private final TrashCatalog catalog;
    private final TrashItemFactory itemFactory;
    private final TrashRecyclePool recyclePool;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;

    public TrashVendorService(final ConfigManager configManager, final TrashCatalog catalog,
                              final TrashItemFactory itemFactory, final TrashRecyclePool recyclePool,
                              final CurrencyManager currencyManager, final FactionManager factionManager,
                              final MessageManager messageManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.recyclePool = Objects.requireNonNull(recyclePool, "recyclePool");
        this.currencyManager = Objects.requireNonNull(currencyManager, "currencyManager");
        this.factionManager = Objects.requireNonNull(factionManager, "factionManager");
        this.messageManager = Objects.requireNonNull(messageManager, "messageManager");
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
        final TrashDefinition definition = catalog.require(id);
        final int unitValue = definition.vendorValue();
        final long soldToday = DailyBudget.spentTodayOnOwnThread(player, "buyer");
        final long dailyCap = Math.max(0L, (long) configManager.getDouble("buyer.daily-cap", 250.0D));
        final long remaining = dailyCap <= 0L ? Long.MAX_VALUE : Math.max(0L, dailyCap - soldToday);
        final int sellable = (int) Math.min(hand.getAmount(), remaining / unitValue);
        if (sellable < 1) {
            player.sendMessage(messageManager.getMessage("buyer-cap-reached",
                    "<gray>🪙 „Mára kimerült a kasszám feléd — gyere vissza holnap!”</gray>"));
            return true;
        }
        final long value = Math.multiplyExact((long) sellable, unitValue);
        if (!DailyBudget.tryConsumeOnOwnThread(player, "buyer", dailyCap, value)) {
            player.sendMessage(messageManager.getMessage("buyer-cap-reached",
                    "<gray>🪙 „Mára kimerült a kasszám feléd — gyere vissza holnap!”</gray>"));
            return true;
        }

        final ItemStack soldSnapshot = hand.clone();
        soldSnapshot.setAmount(1);
        hand.setAmount(hand.getAmount() - sellable);
        recyclePool.offer(soldSnapshot, sellable);
        final CurrencyType currency = CurrencyType.fromFactionType(
                factionManager.getEconomyFaction(player.getUniqueId()));
        currencyManager.payOutTokens(player, currency, value);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.8F, 1.1F);
        player.sendMessage(messageManager.getMessage("buyer-trash-sold",
                "<gold>🪙 Eladva: <white>{amount}× {item}</white> — <white>{value}× veret</white> a kezedbe. <gray>(Mai keretedből maradt: {left})</gray></gold>",
                Map.of("amount", String.valueOf(sellable),
                        "item", definition.displayName(),
                        "value", String.valueOf(value),
                        "left", currencyManager.formatBalance(
                                dailyCap <= 0L ? 0.0D : Math.max(0L, remaining - value)))));
        return true;
    }
}
