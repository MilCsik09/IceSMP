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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Mely Lelegzet" — 3.5s flamethrower channel. Ticks 7 times (every 10 ticks) on the
 * caster's own entity scheduler, each step re-reading the current look direction so the
 * player can sweep the cone while channeling. Two points along the current sightline
 * (a "near" and a "far" point) each gather nearby living entities in a small sphere —
 * a cheap stand-in for a widening cone.
 */
public final class DeepBreathSpell extends BaseSpell {

    private static final Map<UUID, ScheduledTask> ACTIVE_CHANNELS = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;

    public DeepBreathSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "deep_breath", "Mély Lélegzet", 90, SpellCostType.XP, 60);
        this.plugin = plugin;
    }

    @Override
    public boolean canCast(final Player player) {
        // Recast guard: a running channel must finish (or be cleared on logout) before it can restart.
        return !ACTIVE_CHANNELS.containsKey(player.getUniqueId());
    }

    @Override
    public void execute(final Player player) {
        final UUID playerId = player.getUniqueId();
        final int totalSteps = balanceInt("steps", 7);
        final int stepIntervalTicks = balanceInt("step-interval-ticks", 10);
        final double damage = balance("damage", 2.0D);
        final int burnTicks = balanceInt("burn-ticks", 20);
        final double coneNear = balance("cone-near", 2.5D);
        final double coneFar = balance("cone-far", 5.0D);
        final double coneRadius = balance("cone-radius", 1.5D);

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

            final Location eye = online.getEyeLocation();
            final Vector direction = eye.getDirection().normalize();
            final Set<UUID> hitThisStep = new HashSet<>();
            for (final double distance : new double[]{coneNear, coneFar}) {
                final Location point = eye.clone().add(direction.clone().multiply(distance));
                online.getWorld().spawnParticle(Particle.FLAME, point, 6, 0.2D, 0.2D, 0.2D, 0.01D);
                for (final Entity nearby : point.getWorld().getNearbyEntities(point, coneRadius, coneRadius, coneRadius)) {
                    if (!(nearby instanceof LivingEntity living) || living == online
                            || SpellTargetingUtil.isAlly(online, living) || !hitThisStep.add(living.getUniqueId())) {
                        continue;
                    }
                    scorch(living, online, damage, burnTicks);
                }
            }
            online.getWorld().playSound(eye, Sound.ITEM_FIRECHARGE_USE, 0.5F, 1.1F);
        }, () -> ACTIVE_CHANNELS.remove(playerId), stepIntervalTicks, stepIntervalTicks);

        if (task != null) {
            ACTIVE_CHANNELS.put(playerId, task);
        }
    }

    /**
     * Folia: a later tick of this channel can land on a different region than the cast, so
     * re-check ownership before touching a target found nearby — hop to its own scheduler if needed.
     */
    private void scorch(final LivingEntity living, final Player caster, final double damage, final int burnTicks) {
        if (Bukkit.isOwnedByCurrentRegion(living)) {
            applyScorch(living, caster, damage, burnTicks);
        } else {
            living.getScheduler().run(plugin, task -> applyScorch(living, caster, damage, burnTicks), null);
        }
    }

    private static void applyScorch(final LivingEntity living, final Player caster, final double damage, final int burnTicks) {
        if (Bukkit.isOwnedByCurrentRegion(caster)) {
            hu.taliann.icesmp.utils.SpellDamageUtil.damageBySpell(caster, living, damage, getId());
        } else {
            living.damage(damage);
        }
        living.setFireTicks(Math.max(living.getFireTicks(), burnTicks));
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final ScheduledTask task = ACTIVE_CHANNELS.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
