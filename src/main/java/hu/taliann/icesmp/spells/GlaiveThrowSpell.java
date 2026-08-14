package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.SpellDamageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * "Sarlóvetés" (id marad {@code throw_glaive}) — bumeráng: egy vezetett ARMOR_STAND-lövedék
 * (AngryChicken-mintás) kirepül, majd visszafordul a kaszterhez; oda- és visszaúton is 5.0
 * sebzést oszt minden útba eső élőlénynek, élőlényenként max 1x per irány. Ha a kaszter kilép,
 * világot vált vagy a lövedék lejár, a session determinisztikusan megszűnik.
 */
public final class GlaiveThrowSpell extends BaseSpell {

    private static final String PROJECTILE_TAG = "icesmp_glaive_throw";

    private enum Phase { OUTBOUND, INBOUND }

    private final JavaPlugin plugin;

    public GlaiveThrowSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "throw_glaive", "Sarlóvetés", 40, SpellCostType.XP, 30);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        final UUID shooterId = player.getUniqueId();
        final CastModifiers modifiers = SpellExecutionContext.capture();
        final Vector outboundDirection = player.getEyeLocation().getDirection().normalize();
        final ArmorStand projectile = player.getWorld().spawn(
                player.getEyeLocation().add(outboundDirection.clone().multiply(0.8D)), ArmorStand.class, as -> {
                    as.setInvisible(true);
                    as.setMarker(true);
                    as.setSmall(true);
                    as.setGravity(false);
                    as.setCollidable(false);
                    as.setInvulnerable(true);
                    as.setPersistent(false);
                    as.addScoreboardTag(PROJECTILE_TAG);
                });

        final double speed = balance("speed", 0.8D);
        final double outboundDistance = balance("distance", 8.0D);
        final double hitRadius = balance("hit-radius", 1.1D);
        final double damage = balance("damage", 5.0D);
        final double arrivalRadius = balance("arrival-radius", 1.0D);
        final int timeoutTicks = balanceInt("timeout-ticks", (int) Math.ceil(outboundDistance / speed) * 3 + 20);

        final double[] travelled = {0.0D};
        final Phase[] phase = {Phase.OUTBOUND};
        final Set<UUID> hitOutbound = new HashSet<>();
        final Set<UUID> hitInbound = new HashSet<>();
        final AtomicReference<Location> casterEye = new AtomicReference<>(player.getEyeLocation().clone());
        final AtomicBoolean casterActive = new AtomicBoolean(true);
        final AtomicBoolean flightActive = new AtomicBoolean(true);

        // The caster location is sampled only on the caster's own entity scheduler.
        // The projectile region reads an immutable clone through the atomic reference.
        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (!flightActive.get()) {
                task.cancel();
                return;
            }
            casterEye.set(player.getEyeLocation().clone());
        }, () -> casterActive.set(false), 1L, 1L);

        // Folia: drive the projectile on its own entity scheduler.
        projectile.getScheduler().runAtFixedRate(plugin, task -> {
            if (!projectile.isValid() || projectile.isDead() || !casterActive.get()) {
                flightActive.set(false);
                if (projectile.isValid()) {
                    projectile.remove();
                }
                task.cancel();
                return;
            }

            if (phase[0] == Phase.OUTBOUND) {
                final Vector step = outboundDirection.clone().multiply(speed);
                projectile.teleportAsync(projectile.getLocation().add(step));
                travelled[0] += speed;
                if (travelled[0] >= outboundDistance) {
                    phase[0] = Phase.INBOUND;
                }
            } else {
                final Location returnTarget = casterEye.get();
                if (returnTarget == null || returnTarget.getWorld() != projectile.getWorld()) {
                    flightActive.set(false);
                    projectile.remove();
                    task.cancel();
                    return;
                }
                final Vector toShooter = returnTarget.toVector().subtract(projectile.getLocation().toVector());
                final double distanceToShooter = toShooter.length();
                if (distanceToShooter <= arrivalRadius) {
                    flightActive.set(false);
                    projectile.remove();
                    task.cancel();
                    return;
                }
                final Vector step = toShooter.normalize().multiply(Math.min(speed, distanceToShooter));
                projectile.teleportAsync(projectile.getLocation().add(step));
            }

            final Player shooter = Bukkit.getPlayer(shooterId);
            final boolean shooterInCurrentRegion = shooter != null && Bukkit.isOwnedByCurrentRegion(shooter);
            final Set<UUID> hitSet = phase[0] == Phase.OUTBOUND ? hitOutbound : hitInbound;

            for (final Entity nearby : projectile.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                if (!(nearby instanceof LivingEntity living) || living instanceof ArmorStand
                        || living.getUniqueId().equals(shooterId) || SpellTargetingUtil.isAlly(shooterId, living)
                        || hitSet.contains(living.getUniqueId())) {
                    continue;
                }

                if (shooterInCurrentRegion) {
                    SpellDamageUtil.damageBySpell(shooter, living, damage, getId(), modifiers);
                } else {
                    living.damage(SpellDamageUtil.scaledDamage(damage, modifiers));
                }
                hitSet.add(living.getUniqueId());
            }
        }, () -> flightActive.set(false), 1L, 1L);

        projectile.getScheduler().runDelayed(plugin, task -> {
            flightActive.set(false);
            if (projectile.isValid()) {
                projectile.remove();
            }
        }, null, timeoutTicks);

        player.getWorld().spawnParticle(Particle.CRIT, player.getEyeLocation(), 10, 0.1D, 0.1D, 0.1D, 0.05D);
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 1.0F, 1.2F);
    }
}
