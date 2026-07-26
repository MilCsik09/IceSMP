package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.PartyManager;
import hu.taliann.icesmp.utils.MobKillUtil;
import hu.taliann.icesmp.utils.PartyRewardResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/** Folia-safe party combat rules. */
public final class PartyListener implements Listener {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final PartyManager partyManager;

    public PartyListener(final org.bukkit.plugin.java.JavaPlugin plugin,
                         final PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(final EntityDeathEvent event) {
        if (!partyManager.isEnabled() || !partyManager.isXpShareEnabled()
                || event.getEntity() instanceof Player) {
            return;
        }
        final MobKillUtil.KillContext kill =
                MobKillUtil.eligibleTrackingKill(event.getEntity());
        final int xp = event.getDroppedExp();
        if (kill == null || xp <= 0 || kill.victimWorldId() == null
                || !PartyRewardResolver.hasPartyPair(
                partyManager, kill.killerId())) {
            return;
        }

        event.setDroppedExp(0);
        PartyRewardResolver.resolveNearby(plugin, partyManager, kill.killerId(),
                kill.victimWorldId(), kill.victimX(), kill.victimY(), kill.victimZ(),
                partyManager.getShareRadius(), nearby -> {
                    if (nearby.size() < 2) {
                        kill.runOnKiller(plugin, killer -> killer.giveExp(xp));
                        return;
                    }
                    final int share = xp / nearby.size();
                    final int remainder = xp % nearby.size();
                    for (final UUID memberId : nearby) {
                        final Player member = Bukkit.getPlayer(memberId);
                        if (member == null) {
                            continue;
                        }
                        final int amount = share
                                + (memberId.equals(kill.killerId()) ? remainder : 0);
                        if (amount > 0) {
                            member.getScheduler().run(plugin,
                                    task -> member.giveExp(amount), null);
                        }
                    }
                });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        if (!partyManager.isEnabled() || !partyManager.isFriendlyFireBlocked()
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        final Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null && partyManager.isSameParty(
                attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreAttack(
            final io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        if (!partyManager.isEnabled()
                || !(event.getAttacked() instanceof Player victim)) {
            return;
        }
        final Player attacker = event.getPlayer();
        if (!partyManager.isSameParty(
                attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        attacker.sendActionBar(net.kyori.adventure.text.Component.text(
                "🛡 Csapattársat nem üthetsz.",
                net.kyori.adventure.text.format.NamedTextColor.GRAY));
    }

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
