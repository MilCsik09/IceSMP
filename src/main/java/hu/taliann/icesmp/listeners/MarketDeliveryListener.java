package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.MarketManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;

/**
 * Hands over queued market deliveries (auction wins, unsold auction returns)
 * when the player joins. The join event runs on the player's own region
 * thread, so the inventory mutation is Folia-safe; /market claim covers the
 * case of a full inventory cleared later.
 */
public final class MarketDeliveryListener implements Listener {

    private final MarketManager marketManager;
    private final MessageManager messageManager;

    public MarketDeliveryListener(final MarketManager marketManager, final MessageManager messageManager) {
        this.marketManager = marketManager;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (!marketManager.hasPendingDelivery(player.getUniqueId())) {
            return;
        }

        final int delivered = marketManager.deliverPending(player);
        if (delivered > 0) {
            player.sendMessage(messageManager.getMessage(
                    "market-delivery-on-join",
                    "&aA piactérről &f{count}&a tárgy várt rád — megkaptad (aukció-nyeremény vagy visszajáró tétel).",
                    Map.of("count", String.valueOf(delivered))
            ));
        }
    }
}
