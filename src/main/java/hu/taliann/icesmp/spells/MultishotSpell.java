package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class MultishotSpell extends BaseSpell {

    private static final double[] SPREAD_DEGREES = {-10.0D, 0.0D, 10.0D};
    private static final double ARROW_SPEED = 2.2D;

    public MultishotSpell(final MessageManager messageManager) {
        super(messageManager, "multishot", "Sortűz", 45, SpellCostType.HUNGER, 5);
    }

    @Override
    public void execute(final Player player) {
        final Vector direction = player.getEyeLocation().getDirection();
        final double arrowSpeed = balance("speed", ARROW_SPEED);

        for (final double spreadDegrees : SPREAD_DEGREES) {
            final Vector velocity = direction.clone()
                    .rotateAroundY(Math.toRadians(spreadDegrees))
                    .multiply(arrowSpeed);
            final Arrow arrow = player.launchProjectile(Arrow.class, velocity);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setCritical(true);
        }

        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1.0F, 0.9F);
    }
}
