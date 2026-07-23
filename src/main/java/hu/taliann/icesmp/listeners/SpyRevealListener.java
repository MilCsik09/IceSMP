package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.SpyManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * G14 — lebuktatás: bármilyen PvP-interakció (adott VAGY kapott találat) leveszi
 * az álcát. Folia: a sértett a saját szálán bukik le azonnal; a támadó a SAJÁT
 * régió-szálára hopposolva (másik entitás érintése szabály).
 */
public final class SpyRevealListener implements Listener {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final SpyManager spyManager;

    public SpyRevealListener(final org.bukkit.plugin.java.JavaPlugin plugin, final SpyManager spyManager) {
        this.plugin = plugin;
        this.spyManager = spyManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player damager) {
            attacker = damager;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        final boolean pvp = attacker != null && event.getEntity() instanceof Player;
        if (!pvp) {
            return;
        }
        final Player victim = (Player) event.getEntity();
        if (spyManager.isSpying(victim.getUniqueId())) {
            spyManager.reveal(victim, "spy-revealed-hit",
                    "<red>🕵 Az ütés leverte az álcád — lelepleződtél!</red>");
        }
        final Player attackerRef = attacker;
        if (spyManager.isSpying(attackerRef.getUniqueId())) {
            attackerRef.getScheduler().run(plugin, task -> spyManager.reveal(attackerRef,
                    "spy-revealed-attack",
                    "<red>🕵 A kezed elárult — az álca lehullt rólad!</red>"), null);
        }
    }
}
