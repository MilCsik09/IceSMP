package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.TalentManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class TalentAttributeListener implements Listener {

    private final JavaPlugin plugin;
    private final TalentManager talentManager;

    public TalentAttributeListener(final JavaPlugin plugin, final TalentManager talentManager) {
        this.plugin = plugin;
        this.talentManager = talentManager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        talentManager.applyAttributeTalents(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        // Re-apply talent attribute modifiers after respawn (idempotent), in case anything
        // cleared them on death. Scheduled one tick later on the player's region so the
        // respawned entity's attributes are fully initialised.
        final Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> talentManager.applyAttributeTalents(player), null, 1L);
    }
}
