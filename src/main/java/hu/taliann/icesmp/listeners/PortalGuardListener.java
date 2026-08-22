package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.prologue.PrologueContentPolicy;
import hu.taliann.icesmp.prologue.PrologueRuntime;
import hu.taliann.icesmp.prologue.PrologueWorldAccess;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * Single-Nether-gate + End owner policy. FIRE creation remains blocked independently from
 * traversal; once armed, Prologue additionally fail-closes Overworld -> Nether travel until
 * Olethropyla is durably unlocked, and afterwards only the configured central gate is legitimate.
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
        if (event.getReason() != PortalCreateEvent.CreateReason.FIRE) return;
        if (configManager.getBoolean("nether-portal.allow-creation", false)) return;
        if (event.getEntity() instanceof Player player) {
            if (player.hasPermission(Permissions.TERRITORY_BYPASS)) return;
            player.sendActionBar(messageManager.getMessage(
                    "portal-creation-blocked",
                    "<dark_purple>⛩ E világon egyetlen kapu nyílik a mélységre — a Kárhozat Kapuja. Keresd fel, ha mered.</dark_purple>"));
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onNetherPortalUse(final PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) return;
        final Player player = event.getPlayer();
        // Nether -> Overworld return remains legal. The restriction controls entry from the living world.
        if (event.getFrom().getWorld() != null
                && event.getFrom().getWorld().getEnvironment() == World.Environment.NETHER) return;
        if (!PrologueContentPolicy.netherGateAuthorityActive(configManager)) return;
        if (player.isOp()) return;
        if (!PrologueContentPolicy.netherTraversalAvailable(configManager)) {
            event.setCancelled(true);
            player.sendActionBar(messageManager.getMessage(
                    "doom-gate-sealed",
                    "<dark_purple>Olethropyla zúg, de nem enged át. A Kárhozat Kapuja még instabil.</dark_purple>"));
            return;
        }
        final PrologueRuntime runtime = PrologueRuntime.current();
        final double radius = Math.max(4.0D, configManager.getDouble(
                "world-events.prologue.gate.travel-radius", 24.0D));
        if (runtime == null || !PrologueWorldAccess.within(
                event.getFrom(), runtime.worldAccess().gateAnchor(), radius)) {
            event.setCancelled(true);
            player.sendActionBar(messageManager.getMessage(
                    "nether-gate-wrong-portal",
                    "<dark_purple>A Nether csak Olethropylán, a Kárhozat Kapuján át érhető el.</dark_purple>"));
        }
    }

    /**
     * Closes command/plugin/custom-portal routes into the Nether as well. Only Bukkit's explicit
     * OP status bypasses the story gate; ordinary admin/territory permissions remain subject to it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDirectNetherEntry(final PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent || event.getTo() == null
                || event.getTo().getWorld() == null || event.getFrom().getWorld() == null) return;
        if (event.getFrom().getWorld().getEnvironment() == World.Environment.NETHER
                || event.getTo().getWorld().getEnvironment() != World.Environment.NETHER) return;
        if (!PrologueContentPolicy.netherGateAuthorityActive(configManager)) return;
        final Player player = event.getPlayer();
        if (player.isOp()) return;
        if (!PrologueContentPolicy.netherTraversalAvailable(configManager)) {
            event.setCancelled(true);
            player.sendActionBar(messageManager.getMessage(
                    "doom-gate-sealed",
                    "<dark_purple>Olethropyla zúg, de nem enged át. A Kárhozat Kapuja még instabil.</dark_purple>"));
            return;
        }
        final PrologueRuntime runtime = PrologueRuntime.current();
        final double radius = Math.max(4.0D, configManager.getDouble(
                "world-events.prologue.gate.travel-radius", 24.0D));
        if (runtime == null || !PrologueWorldAccess.within(
                event.getFrom(), runtime.worldAccess().gateAnchor(), radius)) {
            event.setCancelled(true);
            player.sendActionBar(messageManager.getMessage(
                    "nether-gate-wrong-portal",
                    "<dark_purple>A Nether csak Olethropylán, a Kárhozat Kapuján át érhető el.</dark_purple>"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEndFrameActivate(final org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != org.bukkit.Material.END_PORTAL_FRAME
                || event.getItem() == null
                || event.getItem().getType() != org.bukkit.Material.ENDER_EYE) return;
        if (configManager.getBoolean("end-portal.allow", false)
                || event.getPlayer().hasPermission(Permissions.TERRITORY_BYPASS)) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(messageManager.getMessage(
                "end-portal-blocked",
                "<dark_purple>⛩ A Vég kapuja némán mered rád — a pecsétjét még nem törte fel senki. (A Vég egy későbbi fejezetben nyílik meg.)</dark_purple>"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEndPortalUse(final PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;
        if (configManager.getBoolean("end-portal.allow", false)
                || event.getPlayer().hasPermission(Permissions.TERRITORY_BYPASS)) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(messageManager.getMessage(
                "end-portal-blocked",
                "<dark_purple>⛩ A Vég kapuja némán mered rád — a pecsétjét még nem törte fel senki. (A Vég egy későbbi fejezetben nyílik meg.)</dark_purple>"));
    }
}
