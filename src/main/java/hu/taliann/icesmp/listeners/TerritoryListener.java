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

    /** B21 — setter-injektált: első határátlépés bestiárium-bejegyzése. */
    private volatile hu.taliann.icesmp.managers.BestiaryManager bestiaryManager;

    public void setBestiaryManager(final hu.taliann.icesmp.managers.BestiaryManager bestiaryManager) {
        this.bestiaryManager = bestiaryManager;
    }

    private final TerritoryManager territoryManager;
    private final hu.taliann.icesmp.managers.TerritoryProtectionService protectionService;
    private final ConfigManager configManager;
    private final QuestManager questManager;
    private final MessageManager messageManager;
    private final Map<UUID, String> lastTerritoryIds = new ConcurrentHashMap<>();

    public TerritoryListener(final TerritoryManager territoryManager,
                             final hu.taliann.icesmp.managers.TerritoryProtectionService protectionService,
                             final ConfigManager configManager, final QuestManager questManager,
                             final MessageManager messageManager) {
        this.territoryManager = territoryManager;
        this.protectionService = protectionService;
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
            protectionService.clearDoomGrace(player.getUniqueId());
            player.sendActionBar(messageManager.getMessage(
                    "territory-enter-wilderness",
                    "<gray>Vadon</gray>"
            ));
            return;
        }

        // VISIT_TERRITORY quest objectives complete on border crossing.
        questManager.handleTerritoryEnter(player, territory.id());
        // Az első belépés lajstrom-bejegyzés.
        final hu.taliann.icesmp.managers.BestiaryManager bestiaryRef = bestiaryManager;
        if (bestiaryRef != null) {
            bestiaryRef.record(player, hu.taliann.icesmp.managers.BestiaryManager.Category.TERRITORIES, territory.id());
        }

        // Kárhozat-zóna: belépéskor PvP-grace indul (spawn-kill védelem), kilépéskor törlődik.
        if (territory.type() == hu.taliann.icesmp.data.TerritoryType.DOOM_GATE) {
            protectionService.markDoomEntry(player.getUniqueId());
            // Belépő hangulat: baljós hang + hamu-örvény a játékos körül (a saját régió-szálán fut).
            player.playSound(player.getLocation(), org.bukkit.Sound.AMBIENT_NETHER_WASTES_MOOD, 1.2F, 0.6F);
            hu.taliann.icesmp.utils.ParticleUtil.spawn(player, org.bukkit.Particle.ASH,
                    player.getLocation().clone().add(0.0D, 1.0D, 0.0D), 40, 2.5D, 1.5D, 2.5D, 0.01D);
            hu.taliann.icesmp.utils.ParticleUtil.spawn(player, org.bukkit.Particle.SOUL,
                    player.getLocation().clone().add(0.0D, 0.5D, 0.0D), 10, 1.5D, 0.5D, 1.5D, 0.01D);
        } else {
            protectionService.clearDoomGrace(player.getUniqueId());
        }

        final String key;
        final String fallback;
        switch (territory.type()) {
            case DOOM_GATE -> {
                key = "territory-enter-doom-gate";
                fallback = "<dark_red>☠ {name} ☠</dark_red> <gray>— senkiföldje: itt nincs törvény, a harc szabad!</gray>";
            }
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

    /**
     * Enderpearl/portál/plugin-teleport is zóna-váltás — a teleport KÜLÖN handler-lista,
     * e nélkül a Kárhozat-zónába becsapódó játékos nem kapná meg a belépési PvP-grace-t
     * (és a zóna-címke sem frissülne).
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(final org.bukkit.event.player.PlayerTeleportEvent event) {
        onPlayerMove(event);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        lastTerritoryIds.remove(event.getPlayer().getUniqueId());
        protectionService.clearDoomGrace(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(final org.bukkit.event.player.PlayerKickEvent event) {
        // PlayerKickEvent does not reliably chain to PlayerQuitEvent — clear here too.
        lastTerritoryIds.remove(event.getPlayer().getUniqueId());
        protectionService.clearDoomGrace(event.getPlayer().getUniqueId());
    }
}
