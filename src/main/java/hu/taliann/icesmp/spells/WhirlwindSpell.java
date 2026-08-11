package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.SpellDamageUtil;
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
 * "Forgoszel" — 5s spin channel. Every 10 ticks on the caster's own entity scheduler,
 * nearby enemies take a hit and are knocked outward. The immutable cast modifier snapshot
 * is propagated to all later channel and target scheduler hops.
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
        final CastModifiers modifiers = SpellExecutionContext.capture();
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
                if (!(nearby instanceof LivingEntity living) || living == online
                        || SpellTargetingUtil.isAlly(online, living)) {
                    continue;
                }
                sweep(living, online, center, damage, knockback, modifiers);
            }
            online.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0.0D, 1.0D, 0.0D),
                    10, radius / 2.0D, 0.3D, radius / 2.0D, 0.0D);
            online.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
        }, () -> ACTIVE_CHANNELS.remove(playerId), stepIntervalTicks, stepIntervalTicks);

        if (task != null) ACTIVE_CHANNELS.put(playerId, task);
    }

    private void sweep(final LivingEntity living, final Player caster, final Location center,
                       final double damage, final double knockback, final CastModifiers modifiers) {
        if (Bukkit.isOwnedByCurrentRegion(living)) {
            applySweep(living, caster, center, damage, knockback, modifiers);
        } else {
            living.getScheduler().run(plugin,
                    task -> applySweep(living, caster, center, damage, knockback, modifiers), null);
        }
    }

    private void applySweep(final LivingEntity living, final Player caster, final Location center,
                            final double damage, final double knockback, final CastModifiers modifiers) {
        if (Bukkit.isOwnedByCurrentRegion(caster)) {
            SpellDamageUtil.damageBySpell(caster, living, damage, getId(), modifiers);
        } else {
            final double scaled = SpellDamageUtil.scaledDamage(damage, modifiers);
            if (scaled > 0.0D) living.damage(scaled);
        }
        final Vector away = living.getLocation().toVector().subtract(center.toVector()).setY(0.0D);
        if (away.lengthSquared() > 1.0E-4D) {
            living.setVelocity(away.normalize().multiply(knockback).setY(0.3D));
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final ScheduledTask task = ACTIVE_CHANNELS.remove(playerId);
        if (task != null) task.cancel();
    }
}
