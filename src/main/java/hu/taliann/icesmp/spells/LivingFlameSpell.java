package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * "Lánggolyó" (id marad {@code living_flame}) — ray-trace célzás; a célpont 5.0 sebzést és 3mp
 * égést kap, a célpont körüli 2.5 blokkos körben minden MÁS élőlény (a kaszter kivétel) 2.5
 * splash-sebzést és 1.5mp égést kap. Nincs célpont esetén a költség visszajár.
 */
public final class LivingFlameSpell extends BaseSpell {

    public LivingFlameSpell(final MessageManager messageManager) {
        super(messageManager, "living_flame", "Lánggolyó", 20, SpellCostType.XP, 30);
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        final LivingEntity target = SpellTargetingUtil.rayTraceLivingEntity(player, balance("range", 15.0D));
        if (target == null) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a látómezőben."));
            return false;
        }

        final double directDamage = balance("damage", 5.0D);
        final int directIgniteTicks = balanceInt("ignite-ticks", 3 * 20);
        hu.taliann.icesmp.utils.SpellDamageUtil.damageBySpell(player, target, directDamage, getId());
        target.setFireTicks(Math.max(target.getFireTicks(), directIgniteTicks));

        final double splashRadius = balance("splash-radius", 2.5D);
        final double splashDamage = balance("splash-damage", 2.5D);
        final int splashIgniteTicks = balanceInt("splash-ignite-ticks", (int) (1.5D * 20));

        for (final Entity nearby : target.getNearbyEntities(splashRadius, splashRadius, splashRadius)) {
            if (!(nearby instanceof LivingEntity living) || living instanceof ArmorStand
                    || living == target || living.getUniqueId().equals(player.getUniqueId())
                    || SpellTargetingUtil.isAlly(player, living)) {
                continue;
            }

            hu.taliann.icesmp.utils.SpellDamageUtil.damageBySpell(player, living, splashDamage, getId());
            living.setFireTicks(Math.max(living.getFireTicks(), splashIgniteTicks));
        }

        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.4D, 0.4D, 0.4D, 0.03D);
        target.getWorld().spawnParticle(Particle.LAVA, target.getLocation(), 6, splashRadius / 2.0D, 0.2D, splashRadius / 2.0D, 0.0D);
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0F, 1.1F);
        return true;
    }
}
