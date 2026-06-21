package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Territory awareness: shows an action bar when a player crosses a territory
 * border, and (optionally, 'territory.protection.enabled') prevents players
 * from building in another faction's land.
 */
public final class TerritoryListener implements Listener {

    private static final String BYPASS_PERMISSION = "icesmp.admin.territory.bypass";

    private final TerritoryManager territoryManager;
    private final FactionManager factionManager;
    private final ConfigManager configManager;
    private final QuestManager questManager;
    private final MessageManager messageManager;
    private final Map<UUID, String> lastTerritoryIds = new ConcurrentHashMap<>();

    public TerritoryListener(final TerritoryManager territoryManager, final FactionManager factionManager,
                             final ConfigManager configManager, final QuestManager questManager,
                             final MessageManager messageManager) {
        this.territoryManager = territoryManager;
        this.factionManager = factionManager;
        this.configManager = configManager;
        this.questManager = questManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(final PlayerMoveEvent event) {
        if (!configManager.getBoolean("territory.notify.enabled", true)) {
            return;
        }

        final Location from = event.getFrom();
        final Location to = event.getTo();
        // Only re-evaluate on block-level movement to keep the hot path cheap.
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        final Player player = event.getPlayer();
        final Territory territory = territoryManager.getTerritoryAt(to);
        final String currentId = territory == null ? "" : territory.id();
        final String previousId = lastTerritoryIds.put(player.getUniqueId(), currentId);
        if (currentId.equals(previousId)) {
            return;
        }

        if (territory == null) {
            player.sendActionBar(messageManager.getMessage(
                    "territory-enter-wilderness",
                    "<gray>Vadon</gray>"
            ));
            return;
        }

        // VISIT_TERRITORY quest objectives complete on border crossing.
        questManager.handleTerritoryEnter(player, territory.id());

        player.sendActionBar(messageManager.getMessage(
                territory.capital() ? "territory-enter-capital" : "territory-enter",
                territory.capital()
                        ? "<gold>✦ {name} ✦</gold> <gray>({faction} főváros)</gray>"
                        : "<yellow>{name}</yellow> <gray>({faction} terület)</gray>",
                Map.of("name", territory.name(), "faction", territory.faction().getDisplayName())
        ));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        if (isBuildBlocked(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (isBuildBlocked(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        lastTerritoryIds.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(final org.bukkit.event.player.PlayerKickEvent event) {
        // PlayerKickEvent does not reliably chain to PlayerQuitEvent — clear here too.
        lastTerritoryIds.remove(event.getPlayer().getUniqueId());
    }

    private boolean isBuildBlocked(final Player player, final Location location) {
        if (!configManager.getBoolean("territory.protection.enabled", false)
                || player.hasPermission(BYPASS_PERMISSION)) {
            return false;
        }

        final Territory territory = territoryManager.getTerritoryAt(location);
        if (territory == null) {
            return false;
        }

        final FactionType playerFaction = factionManager.getFaction(player.getUniqueId());
        if (playerFaction == territory.faction()) {
            return false;
        }

        player.sendActionBar(messageManager.getMessage(
                "territory-build-denied",
                "<red>Ez a(z) {faction} frakció területe — itt nem építhetsz.</red>",
                Map.of("faction", territory.faction().getDisplayName())
        ));
        return true;
    }
}
