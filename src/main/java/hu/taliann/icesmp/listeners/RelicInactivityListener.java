package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.RelicManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class RelicInactivityListener implements Listener {

    private final RelicManager relicManager;

    public RelicInactivityListener(final RelicManager relicManager) {
        this.relicManager = relicManager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        relicManager.handlePlayerJoin(event.getPlayer());
    }
}
