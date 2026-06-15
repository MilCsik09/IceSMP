package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.HudManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Sets up the live HUD when a player joins and tears it down when they leave.
 * Both events run on the joining/leaving player's region thread (Folia-safe).
 */
public final class HudListener implements Listener {

    private final HudManager hudManager;

    public HudListener(final HudManager hudManager) {
        this.hudManager = hudManager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        hudManager.init(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        hudManager.cleanup(event.getPlayer());
    }
}
