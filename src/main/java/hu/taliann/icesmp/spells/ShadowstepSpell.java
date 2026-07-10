package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class ShadowstepSpell extends BaseSpell {

    private static final double RANGE = 15.0D;
    private static final double BEHIND_DISTANCE = 1.5D;
    private static final int INVISIBILITY_TICKS = 3 * 20;

    public ShadowstepSpell(final MessageManager messageManager) {
        super(messageManager, "shadowstep", "Árnyéklépés", 60, SpellCostType.HUNGER, 6);
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        final LivingEntity target = SpellTargetingUtil.rayTraceLivingEntity(player, balance("range", RANGE));
        if (target == null) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a látómeződben."));
            return false;
        }

        final double behindDistance = balance("distance", BEHIND_DISTANCE);
        final Location origin = player.getLocation();
        final Location targetLocation = target.getLocation();

        Vector facing = targetLocation.getDirection().setY(0.0D);
        if (facing.lengthSquared() < 1.0E-4D) {
            facing = targetLocation.toVector().subtract(origin.toVector()).setY(0.0D);
        }
        if (facing.lengthSquared() < 1.0E-4D) {
            facing = new Vector(1.0D, 0.0D, 0.0D);
        }
        facing.normalize();

        final Location destination = targetLocation.clone().subtract(facing.clone().multiply(behindDistance));
        destination.setDirection(targetLocation.toVector().subtract(destination.toVector()));

        player.getWorld().spawnParticle(Particle.PORTAL, origin.clone().add(0.0D, 1.0D, 0.0D), 30, 0.3D, 0.5D, 0.3D, 0.05D);
        player.teleportAsync(destination).thenAccept(success -> {
            if (!success || !player.isValid()) {
                return;
            }

            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, balanceInt("duration-ticks", INVISIBILITY_TICKS), 0, false, false, true));
            player.getWorld().spawnParticle(Particle.PORTAL, destination.clone().add(0.0D, 1.0D, 0.0D), 30, 0.3D, 0.5D, 0.3D, 0.05D);
            player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.2F);
        });
        return true;
    }
}
