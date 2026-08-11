package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.SpellHealingUtil;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
        executeSpell(player);
    }

    @Override
    public CastOutcome executeCast(final Player player) {
        return executeSpell(player) ? CastOutcome.SUCCESS : CastOutcome.NO_TARGET;
    }

    @Override
    public boolean executeSpell(final Player player) {
        final LivingEntity target = SpellTargetingUtil.rayTraceLivingEntity(player, balance("range", RANGE));
        if (target == null) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a látómeződben."));
            return false;
        }

        final double damage = balance("damage", DRAIN_AMOUNT);
        hu.taliann.icesmp.utils.SpellDamageUtil.damageBySpell(player, target, damage, getId());

        final double heal = balance("heal", DRAIN_AMOUNT);
        SpellHealingUtil.heal(player, heal);

        player.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.3D, 0.5D, 0.3D, 0.05D);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0.0D, 2.0D, 0.0D), 3, 0.2D, 0.2D, 0.2D);
        player.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0F, 0.8F);
        return true;
    }
}
