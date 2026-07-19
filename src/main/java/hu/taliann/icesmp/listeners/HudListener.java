package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.HudManager;
import hu.taliann.icesmp.managers.TablistManager;
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
    private final TablistManager tablistManager;

    public HudListener(final HudManager hudManager, final TablistManager tablistManager) {
        this.hudManager = hudManager;
        this.tablistManager = tablistManager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        hudManager.init(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        hudManager.cleanup(event.getPlayer());
        tablistManager.cleanup(event.getPlayer().getUniqueId());
    }
}
