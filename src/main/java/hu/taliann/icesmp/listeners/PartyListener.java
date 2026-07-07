package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.PartyManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;

/**
 * Party combat rules (WoW-style):
 * <ul>
 *   <li><b>Shared XP, split per head:</b> when a party member kills a mob, the
 *       mob's XP is divided evenly among the nearby members (the more of them,
 *       the smaller each share) and granted directly — no orb race.</li>
 *   <li><b>No friendly fire:</b> party members cannot damage each other, melee
 *       or projectile.</li>
 * </ul>
 * The death event runs on the mob's region thread; granting XP hops to each
 * member's own scheduler (Folia-safe). Damage events run on the victim's region
 * thread and only read the tiny synchronized party map.
 */
public final class PartyListener implements Listener {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final PartyManager partyManager;

    public PartyListener(final org.bukkit.plugin.java.JavaPlugin plugin, final PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
    }

    /** Mob XP → split evenly among the killer's nearby party members. */
    @EventHandler(ignoreCancelled = true)
    public void onDeath(final EntityDeathEvent event) {
        if (!partyManager.isEnabled() || !partyManager.isXpShareEnabled()
                || event.getEntity() instanceof Player) {
            return;
        }
        final Player killer = event.getEntity().getKiller();
        final int xp = event.getDroppedExp();
        if (killer == null || xp <= 0) {
            return;
        }

        final List<Player> nearby = partyManager.getNearbyMembers(killer, true);
        if (nearby.size() < 2) {
            return; // Solo (or party-less) kill: vanilla orbs as usual.
        }

        // WoW-style split: everyone nearby gets an equal share instead of the orbs.
        final int share = Math.max(1, xp / nearby.size());
        event.setDroppedExp(0);
        for (final Player member : nearby) {
            member.getScheduler().run(plugin, task -> member.giveExp(share), null);
        }
    }

    /** No friendly fire inside a party — melee and projectiles alike. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        if (!partyManager.isEnabled() || !partyManager.isFriendlyFireBlocked()
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        final Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null && partyManager.isSameParty(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** The attacking player behind a damage source: direct hit or a shot projectile. */
    private Player resolveAttacker(final Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            final ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
