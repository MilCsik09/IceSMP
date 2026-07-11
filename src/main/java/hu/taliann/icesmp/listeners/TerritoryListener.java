package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Territory awareness: shows a type-specific action bar when a player crosses a
 * zone border. The actual build/interact/pvp/explosion/fire protection lives in
 * {@link TerritoryProtectionListener} (via {@code TerritoryProtectionService}).
 */
public final class TerritoryListener implements Listener {

    private final TerritoryManager territoryManager;
    private final ConfigManager configManager;
    private final QuestManager questManager;
    private final MessageManager messageManager;
    private final Map<UUID, String> lastTerritoryIds = new ConcurrentHashMap<>();

    public TerritoryListener(final TerritoryManager territoryManager,
                             final ConfigManager configManager, final QuestManager questManager,
                             final MessageManager messageManager) {
        this.territoryManager = territoryManager;
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

        final String key;
        final String fallback;
        switch (territory.type()) {
            case CAPITAL -> {
                key = "territory-enter-capital";
                fallback = "<gold>✦ {name} ✦</gold> <gray>({faction} főváros)</gray>";
            }
            case PROTECTED_CITY -> {
                key = "territory-enter-protected-city";
                fallback = "<aqua>⛨ {name} ⛨</aqua> <gray>(védett város)</gray>";
            }
            case PROTECTED_FACTION -> {
                key = "territory-enter-protected-faction";
                fallback = "<gold>⛨ {name} ⛨</gold> <gray>({faction} védett terület)</gray>";
            }
            default -> {
                key = "territory-enter";
                fallback = "<yellow>{name}</yellow> <gray>({faction} terület)</gray>";
            }
        }
        player.sendActionBar(messageManager.getMessage(key, fallback,
                Map.of("name", territory.name(), "faction", territory.faction().getDisplayName())));
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
}
