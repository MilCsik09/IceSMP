package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class LifeDrainSpell extends BaseSpell {

    private static final double RANGE = 10.0D;
    private static final double DRAIN_AMOUNT = 4.0D;

    public LifeDrainSpell(final MessageManager messageManager) {
        super(messageManager, "life_drain", "Életszívás", 60, SpellCostType.XP, 20);
    }

    @Override
    public void execute(final Player player) {
        final LivingEntity target = SpellTargetingUtil.rayTraceLivingEntity(player, RANGE);
        if (target == null) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a látómeződben."));
            return;
        }

        target.damage(DRAIN_AMOUNT, player);

        final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(Math.min(maxHealth.getValue(), player.getHealth() + DRAIN_AMOUNT));
        }

        player.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.3D, 0.5D, 0.3D, 0.05D);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0.0D, 2.0D, 0.0D), 3, 0.2D, 0.2D, 0.2D);
        player.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0F, 0.8F);
    }
}
