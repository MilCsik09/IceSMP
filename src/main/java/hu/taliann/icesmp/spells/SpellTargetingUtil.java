package hu.taliann.icesmp.spells;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

public final class SpellTargetingUtil {

    private SpellTargetingUtil() {
    }

    public static LivingEntity rayTraceLivingEntity(final Player player, final double range) {
        final RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                entity -> entity instanceof LivingEntity && entity != player
        );

        if (result == null) {
            return null;
        }

        final Entity hit = result.getHitEntity();
        return hit instanceof LivingEntity living ? living : null;
    }
}

