package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.items.MoneyPouchItemFactory;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Kopott erszény beváltása: jobb-katt a kézben tartott erszénnyel → egy darab elfogy,
 * és a PDC-ben hordozott darabszámú FIZIKAI veret (token-item) kerül a játékoshoz.
 * A SZÁMLÁRA pénz kizárólag banki befizetéssel kerülhet — az erszény ezért itemet ad,
 * nem egyenleget! Folia: az interact a játékos saját régió-szálán fut.
 */
public final class MoneyPouchListener implements Listener {

    private final MoneyPouchItemFactory pouchFactory;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public MoneyPouchListener(final MoneyPouchItemFactory pouchFactory,
                              final CurrencyManager currencyManager,
                              final MessageManager messageManager) {
        this.pouchFactory = pouchFactory;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    // Szándékosan NINCS ignoreCancelled: a RIGHT_CLICK_AIR eseményt a Bukkit
    // "cancelled" (useInteractedBlock=DENY) állapottal löki — az annotáció a
    // levegőbe-kattintós erszény-nyitást némítaná el.
    @EventHandler
    public void onUse(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        final Player player = event.getPlayer();
        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (!pouchFactory.isPouch(hand)) {
            return;
        }
        event.setCancelled(true);
        final long value = pouchFactory.getValue(hand);
        final CurrencyType currency = pouchFactory.getCurrency(hand);
        if (value <= 0L || currency == null) {
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        // Fizikai veretek a kézbe (64-es kötegekben); ami nem fér, a földre esik.
        long left = value;
        while (left > 0L) {
            final long batch = Math.min(64L, left);
            left -= batch;
            final ItemStack tokens = currencyManager.createCurrencyItem(currency, batch);
            for (final ItemStack overflow : player.getInventory().addItem(tokens).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 0.7F);
        player.sendMessage(messageManager.getMessage("money-pouch-redeem",
                "<gold>💰 Kioldottad az erszény zsinórját: <white>{amount}× {currency}</white> hullott a kezedbe. <gray>(A bankban tudod befizetni.)</gray></gold>",
                Map.of("amount", String.valueOf(value),
                        "currency", currency.getDisplayName())));
    }
}
