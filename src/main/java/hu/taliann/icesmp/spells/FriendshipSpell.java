package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.util.Vector;

public final class FriendshipSpell extends BaseSpell {

    public FriendshipSpell(final MessageManager messageManager) {
        super(messageManager, "friendship", "Baratsag", 45, SpellCostType.HUNGER, 4);
    }

    @Override
    public boolean canCast(final Player player) {
        return resolveTarget(player) != null;
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        final Tameable tameable = resolveTarget(player);
        if (tameable == null) {
            return false;
        }

        tameable.setOwner(player);
        tameable.setTamed(true);
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.0F, 1.1F);
        return true;
    }

    private Tameable resolveTarget(final Player player) {
        final LivingEntity directTarget = SpellTargetingUtil.rayTraceLivingEntity(player, 16.0D);
        if (directTarget instanceof Tameable tameable && !tameable.isTamed()) {
            return tameable;
        }

        final Vector direction = player.getEyeLocation().getDirection().normalize();
        Tameable bestTarget = null;
        double bestDistanceSquared = Double.MAX_VALUE;

        for (final var entity : player.getNearbyEntities(16.0D, 16.0D, 16.0D)) {
            if (!(entity instanceof Tameable tameable) || tameable.isTamed()) {
                continue;
            }

            final Vector toTarget = tameable.getLocation().toVector().subtract(player.getEyeLocation().toVector());
            final double distanceSquared = toTarget.lengthSquared();
            if (distanceSquared <= 0.01D) {
                continue;
            }

            final double alignment = direction.dot(toTarget.normalize());
            if (alignment < 0.6D || distanceSquared >= bestDistanceSquared) {
                continue;
            }

            bestDistanceSquared = distanceSquared;
            bestTarget = tameable;
        }

        return bestTarget;
    }
}

