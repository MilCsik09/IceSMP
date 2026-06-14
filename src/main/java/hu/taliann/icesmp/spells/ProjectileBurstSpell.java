package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Generic projectile volley spell: launches one or more projectiles of the
 * configured kind in a horizontal spread. Arrows are flagged non-pickup.
 */
public final class ProjectileBurstSpell extends BaseSpell {

    public enum ProjectileKind {
        ARROW(Arrow.class),
        SPECTRAL_ARROW(SpectralArrow.class),
        SMALL_FIREBALL(SmallFireball.class),
        WIND_CHARGE(WindCharge.class),
        SNOWBALL(Snowball.class);

        private final Class<? extends Projectile> projectileClass;

        ProjectileKind(final Class<? extends Projectile> projectileClass) {
            this.projectileClass = projectileClass;
        }
    }

    private final ProjectileKind kind;
    private final int count;
    private final double spreadDegrees;
    private final double speed;
    private final Sound sound;
    private final float soundVolume;
    private final float soundPitch;

    public ProjectileBurstSpell(final MessageManager messageManager, final String id, final String defaultName,
                                final int cooldown, final SpellCostType costType, final int costAmount,
                                final ProjectileKind kind, final int count, final double spreadDegrees,
                                final double speed, final Sound sound, final float soundVolume, final float soundPitch) {
        super(messageManager, id, defaultName, cooldown, costType, costAmount);
        this.kind = kind;
        this.count = Math.max(1, count);
        this.spreadDegrees = spreadDegrees;
        this.speed = speed;
        this.sound = sound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
    }

    @Override
    public void execute(final Player player) {
        final Vector direction = player.getEyeLocation().getDirection();

        for (int index = 0; index < count; index++) {
            final double angle = spreadDegrees * (index - ((count - 1) / 2.0D));
            final Vector velocity = direction.clone()
                    .rotateAroundY(Math.toRadians(angle))
                    .multiply(speed);
            final Projectile projectile = player.launchProjectile(kind.projectileClass, velocity);
            if (projectile instanceof AbstractArrow arrow) {
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.setCritical(true);
            }
        }

        if (sound != null) {
            player.playSound(player.getLocation(), sound, soundVolume, soundPitch);
        }
    }
}
