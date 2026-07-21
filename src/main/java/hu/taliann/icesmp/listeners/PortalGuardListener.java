package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * Nether-portál világszabály (tulaj-döntés): új nether-portál GYÚJTÁSA alapból
 * MINDENHOL tilos — a világ egyetlen élő kapuja a Kárhozat Kapuja, amelyet az
 * admin-csapat gyújt meg (a zóna-bypass joggal a tiltás átléphető). A NETHER_PAIR
 * ok (a túloldali pár automatikus képzése átkeléskor) szabad marad, különben a
 * Kapu se működne. Élő kulcs: nether-portal.allow-creation (true = vanília).
 */
public final class PortalGuardListener implements Listener {

    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public PortalGuardListener(final ConfigManager configManager, final MessageManager messageManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(final PortalCreateEvent event) {
        if (event.getReason() != PortalCreateEvent.CreateReason.FIRE) {
            return;
        }
        if (configManager.getBoolean("nether-portal.allow-creation", false)) {
            return;
        }
        // Admin-gyújtás (a Kárhozat Kapuja felélesztése): a zóna-bypass jog átengedi.
        if (event.getEntity() instanceof Player player) {
            if (player.hasPermission(Permissions.TERRITORY_BYPASS)) {
                return;
            }
            player.sendActionBar(messageManager.getMessage(
                    "portal-creation-blocked",
                    "<dark_purple>⛩ E világon egyetlen kapu nyílik a mélységre — a Kárhozat Kapuja. Keresd fel, ha mered.</dark_purple>"));
        }
        event.setCancelled(true);
    }
}
