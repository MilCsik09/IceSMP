package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.IntroManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Triggers the first-join intro sequence.
 */
public final class IntroListener implements Listener {

    private final IntroManager introManager;

    public IntroListener(final IntroManager introManager) {
        this.introManager = introManager;
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        introManager.playOnFirstJoin(event.getPlayer());
    }
}
