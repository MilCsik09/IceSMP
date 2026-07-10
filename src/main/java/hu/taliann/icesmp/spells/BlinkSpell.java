package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Generic short-range teleport ("blink") spell: moves the caster forward along
 * the view direction, stopping before the first solid block. Uses the
 * Folia-safe teleportAsync API.
 */
public final class BlinkSpell extends BaseSpell {

    private final double distance;
    private final Particle particle;
    private final Sound sound;

    public BlinkSpell(final MessageManager messageManager, final String id, final String defaultName,
                      final int cooldown, final SpellCostType costType, final int costAmount,
                      final double distance, final Particle particle, final Sound sound) {
        super(messageManager, id, defaultName, cooldown, costType, costAmount);
        this.distance = distance;
        this.particle = particle;
        this.sound = sound;
    }

    @Override
    public void execute(final Player player) {
        final double blinkDistance = balance("distance", distance);
        final Location eye = player.getEyeLocation();
        final Vector direction = eye.getDirection();
        final RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, direction, blinkDistance, FluidCollisionMode.NEVER, true);
        final double travel = hit == null
                ? blinkDistance
                : Math.max(0.5D, eye.toVector().distance(hit.getHitPosition()) - 1.0D);

        final Location origin = player.getLocation();
        final Location destination = origin.clone().add(direction.clone().multiply(travel));
        destination.setDirection(direction);

        player.getWorld().spawnParticle(particle, origin.clone().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.5D, 0.3D, 0.04D);
        player.teleportAsync(destination).thenAccept(success -> {
            if (!success || !player.isValid()) {
                return;
            }

            player.getWorld().spawnParticle(particle, destination.clone().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.5D, 0.3D, 0.04D);
            player.playSound(destination, sound, 1.0F, 1.3F);
        });
    }
}
