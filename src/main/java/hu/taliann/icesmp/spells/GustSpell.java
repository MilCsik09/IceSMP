package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class GustSpell extends BaseSpell {

    public GustSpell(final MessageManager messageManager) {
        super(messageManager, "gust", "Lokeshullam", 60, SpellCostType.XP, 30);
    }

    @Override
    public void execute(final Player player) {
        final Location center = player.getLocation();
        for (final Entity entity : player.getWorld().getNearbyEntities(center, 5.0D, 5.0D, 5.0D)) {
            if (!(entity instanceof LivingEntity) || entity == player) {
                continue;
            }

            final Vector push = entity.getLocation().toVector().subtract(center.toVector()).normalize().multiply(2.0D).setY(0.45D);
            entity.setVelocity(push);
        }
    }
}

