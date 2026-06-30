package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Bee;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Beast Master offensive summon: releases an angry bee swarm on the
 * ray-traced target. Bees are spawned and aimed instantly (no tasks).
 */
public final class BeeSwarmSpell extends BaseSpell {

    private static final double RANGE = 12.0D;
    private static final int BEE_COUNT = 3;
    private static final int ANGER_TICKS = 20 * 20;

    public BeeSwarmSpell(final MessageManager messageManager) {
        super(messageManager, "bee_swarm", "Méhraj", 180, SpellCostType.HUNGER, 6);
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        final LivingEntity target = SpellTargetingUtil.rayTraceLivingEntity(player, RANGE);
        if (target == null) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a látómeződben."));
            return false;
        }

        for (int index = 0; index < BEE_COUNT; index++) {
            player.getWorld().spawn(player.getLocation().add(0.0D, 1.0D, 0.0D), Bee.class, bee -> {
                bee.setAnger(ANGER_TICKS);
                bee.setTarget(target);
            });
        }

        player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0D, 1.0D, 0.0D), 15, 0.4D, 0.5D, 0.4D, 0.05D);
        player.playSound(player.getLocation(), Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 1.0F, 1.0F);
        return true;
    }
}
