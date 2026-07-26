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

    /**
     * End-zár (tulaj-döntés): az End a szezon 2 admin-eseményéig zárva — a stronghold
     * kerete nem aktiválható, és a már égő portálon sem lehet átlépni. A zóna-bypass
     * jog (admin) átenged. Élő kulcs: end-portal.allow (true = vanília End).
     */
    @EventHandler(ignoreCancelled = true)
    public void onEndFrameActivate(final org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != org.bukkit.Material.END_PORTAL_FRAME
                || event.getItem() == null
                || event.getItem().getType() != org.bukkit.Material.ENDER_EYE) {
            return;
        }
        if (configManager.getBoolean("end-portal.allow", false)
                || event.getPlayer().hasPermission(Permissions.TERRITORY_BYPASS)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendActionBar(messageManager.getMessage(
                "end-portal-blocked",
                "<dark_purple>⛩ A Vég kapuja némán mered rád — a pecsétjét még nem törte fel senki. (A Vég egy későbbi fejezetben nyílik meg.)</dark_purple>"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEndPortalUse(final org.bukkit.event.player.PlayerPortalEvent event) {
        if (event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return;
        }
        if (configManager.getBoolean("end-portal.allow", false)
                || event.getPlayer().hasPermission(Permissions.TERRITORY_BYPASS)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendActionBar(messageManager.getMessage(
                "end-portal-blocked",
                "<dark_purple>⛩ A Vég kapuja némán mered rád — a pecsétjét még nem törte fel senki. (A Vég egy későbbi fejezetben nyílik meg.)</dark_purple>"));
    }
}
