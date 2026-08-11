package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Kingdom-spawn placement (a "királyságok spawnpoint" feature):
 * <ul>
 *   <li><b>First join:</b> a brand-new player appears at the NEUTRAL kingdom's admin-set spawn
 *       ({@code /territory setspawn neutral}) instead of the vanilla world spawn — set via
 *       {@link AsyncPlayerSpawnLocationEvent} BEFORE the player materialises, so there is no visible
 *       teleport. New players are Menedék guests until they explicitly choose a faction, so this
 *       is their safe arrival point without granting NEUTRAL membership benefits.</li>
 *   <li><b>Respawn:</b> a player with no bed/anchor respawns at their OWN faction's kingdom spawn
 *       (falling back to the NEUTRAL spawn), steering everyone back to the capitals.</li>
 * </ul>
 * Every branch is a no-op while the relevant spawn is unset, so an unbuilt world behaves exactly
 * as before. The exact stored position (full Y + view direction) is used — no highest-block guessing.
 */
public final class FactionSpawnListener implements Listener {

    private final FactionManager factionManager;
    private final TerritoryManager territoryManager;
    private final ConfigManager configManager;

    public FactionSpawnListener(final FactionManager factionManager, final TerritoryManager territoryManager,
                                final ConfigManager configManager) {
        this.factionManager = factionManager;
        this.territoryManager = territoryManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onSpawnLocation(final AsyncPlayerSpawnLocationEvent event) {
        if (!event.isNewPlayer()
                || !configManager.getBoolean("factions.spawn.first-join-at-neutral", true)) {
            return;
        }
        final Location neutralSpawn = territoryManager.getFactionSpawn(FactionType.NEUTRAL);
        if (neutralSpawn != null) {
            event.setSpawnLocation(neutralSpawn);
        }
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        // Only plain death respawns without a bed/anchor are redirected; leaving the End,
        // bed and anchor respawns keep their vanilla targets.
        if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH
                || event.isBedSpawn() || event.isAnchorSpawn()
                || !configManager.getBoolean("factions.spawn.respawn-at-faction-spawn", true)) {
            return;
        }
        final Player player = event.getPlayer();
        // A Menedék a hozzárendelés nélküli vendég biztonságos fogadóhelye, nem tagsági fallback.
        Location spawn = territoryManager.getFactionSpawn(
                factionManager.getChosenFaction(player.getUniqueId()).orElse(FactionType.NEUTRAL));
        if (spawn == null) {
            spawn = territoryManager.getFactionSpawn(FactionType.NEUTRAL);
        }
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }
}
