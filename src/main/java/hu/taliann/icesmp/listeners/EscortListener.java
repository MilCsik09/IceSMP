package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.EscortManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Escort event hooks: settles the escort when the convoy dies, prunes fallen
 * wave mobs, and — as the second line of the "events never grief terrain" rule —
 * strips block damage from any explosion an escort wave mob might cause (the
 * wave pool has no explosive mobs, this is belt-and-braces). Joining players are
 * shown the live escort boss bar. All handlers run on the entity's/player's own
 * region thread (Folia-safe).
 */
public final class EscortListener implements Listener {

    private final EscortManager escortManager;

    public EscortListener(final EscortManager escortManager) {
        this.escortManager = escortManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(final EntityDeathEvent event) {
        if (escortManager.isConvoy(event.getEntity().getUniqueId())) {
            escortManager.onConvoyDied();
            return;
        }
        escortManager.onWaveMobDied(event.getEntity().getUniqueId());
    }

    /**
     * Teszter-visszajelzés: „nem egyértelmű, mitől hal meg a láma" — a konvojt CSAK
     * a szörnyek sebezhetik (támadás/lövedék/robbanás); a környezeti halál-okok
     * (esés, fulladás, kaktusz, tűz, szikla) némán morzsolták — tiltva.
     */
    @EventHandler(ignoreCancelled = true)
    public void onConvoyDamage(final org.bukkit.event.entity.EntityDamageEvent event) {
        if (!escortManager.isConvoy(event.getEntity().getUniqueId())) {
            return;
        }
        switch (event.getCause()) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE, ENTITY_EXPLOSION -> { }
            default -> event.setCancelled(true);
        }
    }

    /** Terrain guard: an escort mob's explosion may hurt entities, never blocks. */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(final EntityExplodeEvent event) {
        if (escortManager.isWaveMob(event.getEntity().getUniqueId())) {
            event.blockList().clear();
        }
    }

    /** Show the live escort boss bar to a joining player. */
    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        escortManager.showTo(event.getPlayer());
    }
}
