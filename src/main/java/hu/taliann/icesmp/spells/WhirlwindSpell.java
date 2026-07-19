package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Forgoszel" — 5s spin channel. Every 10 ticks (10 steps total) on the caster's own entity
 * scheduler, everything living within 4 blocks of the caster's CURRENT location takes a hit
 * and is knocked outward. Recasting while the channel runs is blocked via {@link #canCast}.
 */
public final class WhirlwindSpell extends BaseSpell {

    private static final Map<UUID, ScheduledTask> ACTIVE_CHANNELS = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;

    public WhirlwindSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "whirlwind", "Forgószél", 150, SpellCostType.HUNGER, 6);
        this.plugin = plugin;
    }

    @Override
    public boolean canCast(final Player player) {
        return !ACTIVE_CHANNELS.containsKey(player.getUniqueId());
    }

    @Override
    public void execute(final Player player) {
        final UUID playerId = player.getUniqueId();
        final int totalSteps = balanceInt("steps", 10);
        final int stepIntervalTicks = balanceInt("step-interval-ticks", 10);
        final double radius = balance("radius", 4.0D);
        final double damage = balance("damage", 2.0D);
        final double knockback = balance("knockback", 0.5D);

        final AtomicInteger stepCounter = new AtomicInteger(0);
        final ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduled -> {
            final Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isValid() || online.isDead()) {
                scheduled.cancel();
                ACTIVE_CHANNELS.remove(playerId);
                return;
            }
            if (stepCounter.incrementAndGet() > totalSteps) {
                scheduled.cancel();
                ACTIVE_CHANNELS.remove(playerId);
                return;
            }

            final Location center = online.getLocation();
            for (final Entity nearby : online.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (!(nearby instanceof LivingEntity living) || living == online || SpellTargetingUtil.isAlly(online, living)) {
                    continue;
                }
                sweep(living, online, center, damage, knockback);
            }
            online.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0.0D, 1.0D, 0.0D),
                    10, radius / 2.0D, 0.3D, radius / 2.0D, 0.0D);
            online.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
        }, () -> ACTIVE_CHANNELS.remove(playerId), stepIntervalTicks, stepIntervalTicks);

        if (task != null) {
            ACTIVE_CHANNELS.put(playerId, task);
        }
    }

    /** Folia: a later channel step can land on a different region than the cast — hop if needed. */
    private void sweep(final LivingEntity living, final Player caster, final Location center,
                        final double damage, final double knockback) {
        if (Bukkit.isOwnedByCurrentRegion(living)) {
            applySweep(living, caster, center, damage, knockback);
        } else {
            living.getScheduler().run(plugin, task -> applySweep(living, caster, center, damage, knockback), null);
        }
    }

    private static void applySweep(final LivingEntity living, final Player caster, final Location center,
                                    final double damage, final double knockback) {
        if (Bukkit.isOwnedByCurrentRegion(caster)) {
            living.damage(damage, caster);
        } else {
            living.damage(damage);
        }
        final Vector away = living.getLocation().toVector().subtract(center.toVector()).setY(0.0D);
        if (away.lengthSquared() > 1.0E-4D) {
            living.setVelocity(away.normalize().multiply(knockback).setY(0.3D));
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final ScheduledTask task = ACTIVE_CHANNELS.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
