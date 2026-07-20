package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.StrangerNpcManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * D19 — az Idegen érinthetetlen: a jobb-katt (a WANDERING_TRADER kereskedő-UI-ja!)
 * elnyelve — az Idegen nem árul semmit, nem ad semmit. A sebzést az invulnerable
 * flag fogja; a rejtély-jelleg így marad tiszta. Folia: az interakció a játékos
 * régió-szálán fut, az entitás régió-lokális (kattintás-távolság).
 */
public final class StrangerListener implements Listener {

    private final StrangerNpcManager strangerNpcManager;

    public StrangerListener(final StrangerNpcManager strangerNpcManager) {
        this.strangerNpcManager = strangerNpcManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEntityEvent event) {
        if (strangerNpcManager.isStranger(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
