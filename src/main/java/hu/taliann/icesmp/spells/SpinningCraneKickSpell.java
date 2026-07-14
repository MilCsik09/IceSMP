package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * "Porgo Darurugas" — two damage waves around the caster: one immediate, one 10 ticks
 * (0.5s) later via the caster's own entity scheduler.
 */
public final class SpinningCraneKickSpell extends BaseSpell {

    private final JavaPlugin plugin;

    public SpinningCraneKickSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "spinning_crane_kick", "Pörgő Darurúgás", 30, SpellCostType.HUNGER, 4);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        final double radius = balance("radius", 4.0D);
        final double damage = balance("damage", 4.0D);

        strike(player, radius, damage);

        final UUID playerId = player.getUniqueId();
        player.getScheduler().runDelayed(plugin, task -> {
            final Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isValid() || online.isDead()) {
                return;
            }
            strike(online, radius, damage);
        }, null, balanceInt("second-wave-delay-ticks", 10));
    }

    private void strike(final Player caster, final double radius, final double damage) {
        final Location center = caster.getLocation();
        for (final Entity nearby : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living == caster) {
                continue;
            }
            kick(living, caster, damage);
        }
        caster.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0.0D, 1.0D, 0.0D),
                12, radius / 2.0D, 0.3D, radius / 2.0D, 0.0D);
        caster.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.1F);
    }

    /**
     * The immediate wave runs on the caster's own event thread (safe, matches the other local-AoE
     * spells in this package); the delayed wave may land on a different region, so both funnel
     * through the same ownership check for consistency and safety.
     */
    private void kick(final LivingEntity living, final Player caster, final double damage) {
        if (Bukkit.isOwnedByCurrentRegion(living)) {
            applyKick(living, caster, damage);
        } else {
            living.getScheduler().run(plugin, task -> applyKick(living, caster, damage), null);
        }
    }

    private static void applyKick(final LivingEntity living, final Player caster, final double damage) {
        if (Bukkit.isOwnedByCurrentRegion(caster)) {
            living.damage(damage, caster);
        } else {
            living.damage(damage);
        }
    }
}
