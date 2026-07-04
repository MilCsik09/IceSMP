package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Theft detection (ROADMAP "Bűn-rendszer bővítés"): taking items out of a
 * storage container that stands in ANOTHER faction's territory counts as a
 * sin. Wartime looting between the two warring factions is sanctioned (no
 * sin), mirroring the raid-kill rule, and admins with the territory bypass
 * permission are exempt. A per-territory cooldown keeps one looting spree
 * from stacking a sin on every clicked item.
 *
 * <p>Folia: InventoryClickEvent fires on the clicking player's region thread
 * — the open container is in the same region — so the sin PDC write needs no
 * scheduler hop.</p>
 */
public final class TheftListener implements Listener {

    private static final String BYPASS_PERMISSION = "icesmp.admin.territory.bypass";

    /** Block-backed storage the theft rule watches (virtual GUIs have no location and never match). */
    private static final Set<InventoryType> STORAGE_TYPES = EnumSet.of(
            InventoryType.CHEST,
            InventoryType.BARREL,
            InventoryType.SHULKER_BOX,
            InventoryType.FURNACE,
            InventoryType.BLAST_FURNACE,
            InventoryType.SMOKER,
            InventoryType.HOPPER,
            InventoryType.DISPENSER,
            InventoryType.DROPPER,
            InventoryType.BREWING
    );

    /** Click actions that move an item OUT of the clicked container slot. */
    private static final Set<InventoryAction> TAKE_ACTIONS = EnumSet.of(
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_SOME,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.COLLECT_TO_CURSOR,
            InventoryAction.HOTBAR_SWAP
    );

    private final MetelytepoManager metelytepoManager;
    private final TerritoryManager territoryManager;
    private final FactionManager factionManager;
    private final RaidManager raidManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    // Per-player, per-territory timestamp of the last recorded theft sin.
    private final Map<UUID, Map<String, Long>> lastTheftAt = new ConcurrentHashMap<>();

    public TheftListener(final MetelytepoManager metelytepoManager, final TerritoryManager territoryManager,
                         final FactionManager factionManager, final RaidManager raidManager,
                         final ConfigManager configManager, final MessageManager messageManager) {
        this.metelytepoManager = metelytepoManager;
        this.territoryManager = territoryManager;
        this.factionManager = factionManager;
        this.raidManager = raidManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    // MONITOR: only a click every other plugin let through counts as an actual theft.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!configManager.getBoolean("factions.sins.theft.enabled", true)) {
            return;
        }

        final Inventory top = event.getView().getTopInventory();
        if (!STORAGE_TYPES.contains(top.getType()) || !top.equals(event.getClickedInventory())) {
            return;
        }

        if (!TAKE_ACTIONS.contains(event.getAction())
                || event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
            return;
        }

        // Virtual GUIs (market, menus) report no block location — only real placed
        // containers can be stolen from.
        final Location location = top.getLocation();
        if (location == null) {
            return;
        }

        final Territory territory = territoryManager.getTerritoryAt(location);
        if (territory == null) {
            return;
        }

        final FactionType playerFaction = factionManager.getFaction(player.getUniqueId());
        if (playerFaction == territory.faction() || player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }

        // Wartime looting between the warring factions is sanctioned, like raid kills.
        if (raidManager.isAtWar(playerFaction, territory.faction())) {
            return;
        }

        final long cooldownMillis = Math.max(0L,
                configManager.getLong("factions.sins.theft.cooldown-seconds", 60L)) * 1000L;
        final long now = System.currentTimeMillis();
        final Map<String, Long> playerThefts =
                lastTheftAt.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>());
        final boolean[] newSin = {false};
        playerThefts.compute(territory.id(), (key, last) -> {
            if (last != null && now - last < cooldownMillis) {
                return last; // Still in the same looting spree — no extra sin.
            }
            newSin[0] = true;
            return now;
        });
        if (!newSin[0]) {
            return;
        }

        final int weight = Math.max(1, configManager.getInt("factions.sins.theft.weight", 1));
        final int threshold = Math.max(0, configManager.getInt("factions.sins.exile-threshold", 4));
        final int sinCount = metelytepoManager.addSin(player, weight);
        player.sendMessage(messageManager.getMessage(
                "sinner.theft-recorded",
                "<dark_purple>Bűnt követtél el: lopás a(z) {faction} frakció területén ({territory}). "
                        + "<gray>Bűneid: <white>{count}</white>/<white>{threshold}</white></gray></dark_purple>",
                Map.of(
                        "faction", territory.faction().getDisplayName(),
                        "territory", territory.name(),
                        "count", String.valueOf(sinCount),
                        "threshold", String.valueOf(threshold)
                )
        ));
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        lastTheftAt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(final PlayerKickEvent event) {
        // PlayerKickEvent does not reliably chain to PlayerQuitEvent — clear here too.
        lastTheftAt.remove(event.getPlayer().getUniqueId());
    }
}
