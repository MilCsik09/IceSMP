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
 * a PDC-ben hordozott összeg a PDC-ben hordozott valutában a számlára kerül. Folia: az
 * interact a játékos saját régió-szálán fut, a bank-írás szál-biztos (CurrencyManager).
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
        final double value = pouchFactory.getValue(hand);
        final CurrencyType currency = pouchFactory.getCurrency(hand);
        if (value <= 0.0D || currency == null) {
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        currencyManager.addToBalance(player.getUniqueId(), currency, value);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 0.7F);
        player.sendMessage(messageManager.getMessage("money-pouch-redeem",
                "<gold>💰 Kioldottad az erszény zsinórját: <white>+{amount} {currency}</white> a számládon.</gold>",
                Map.of("amount", currencyManager.formatBalance(value),
                        "currency", currency.getDisplayName())));
    }
}
