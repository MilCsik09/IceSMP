package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.SpellDamageUtil;
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
 * configured kind in a horizontal spread. Arrows are flagged non-pickup. Every
 * projectile carries the immutable cast damage multiplier to its later hit
 * event, so mastery/gear/combo scaling survives the scheduler boundary.
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
    private final double baseDamage;
    private final int pierceLevel;
    private final Sound sound;
    private final float soundVolume;
    private final float soundPitch;

    public ProjectileBurstSpell(final MessageManager messageManager, final String id, final String defaultName,
                                final int cooldown, final SpellCostType costType, final int costAmount,
                                final ProjectileKind kind, final int count, final double spreadDegrees,
                                final double speed, final double baseDamage,
                                final Sound sound, final float soundVolume, final float soundPitch) {
        this(messageManager, id, defaultName, cooldown, costType, costAmount, kind, count, spreadDegrees, speed,
                baseDamage, 0, sound, soundVolume, soundPitch);
    }

    public ProjectileBurstSpell(final MessageManager messageManager, final String id, final String defaultName,
                                final int cooldown, final SpellCostType costType, final int costAmount,
                                final ProjectileKind kind, final int count, final double spreadDegrees,
                                final double speed, final double baseDamage, final int pierceLevel,
                                final Sound sound, final float soundVolume, final float soundPitch) {
        super(messageManager, id, defaultName, cooldown, costType, costAmount);
        this.kind = kind;
        this.count = Math.max(1, count);
        this.spreadDegrees = spreadDegrees;
        this.speed = speed;
        if (!Double.isFinite(baseDamage) || baseDamage <= 0.0D) {
            throw new IllegalArgumentException("projectile spell baseDamage must be finite and positive");
        }
        this.baseDamage = baseDamage;
        this.pierceLevel = Math.max(0, pierceLevel);
        this.sound = sound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
    }

    @Override
    public void execute(final Player player) {
        final Vector direction = player.getEyeLocation().getDirection();
        final int projectileCount = Math.max(1, balanceInt("count", count));
        final double spread = balance("spread", spreadDegrees);
        final double projectileSpeed = balance("speed", speed);
        final CastModifiers modifiers = SpellExecutionContext.capture();

        for (int index = 0; index < projectileCount; index++) {
            final double angle = spread * (index - ((projectileCount - 1) / 2.0D));
            final Vector velocity = direction.clone()
                    .rotateAroundY(Math.toRadians(angle))
                    .multiply(projectileSpeed);
            final Projectile projectile = player.launchProjectile(kind.projectileClass, velocity);
            SpellDamageUtil.markProjectile(projectile, getId(), balance("damage", baseDamage), modifiers);
            if (projectile instanceof AbstractArrow arrow) {
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.setCritical(true);
                final int pierce = Math.max(0, balanceInt("pierce-level", pierceLevel));
                if (pierce > 0) {
                    arrow.setPierceLevel(Math.min(127, pierce));
                }
            }
        }

        if (sound != null) {
            player.playSound(player.getLocation(), sound, soundVolume, soundPitch);
        }
    }
}
