package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Beast Master support spell: empowers every nearby tamed companion owned by
 * the caster with strength, regeneration and bonus absorption hearts.
 */
public final class PrimalBondSpell extends BaseSpell {

    private static final double RADIUS = 12.0D;
    private static final int EFFECT_TICKS = 20 * 20;

    public PrimalBondSpell(final MessageManager messageManager) {
        super(messageManager, "primal_bond", "Ősi Kötelék", 120, SpellCostType.HUNGER, 5);
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        int empowered = 0;

        for (final Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), RADIUS, RADIUS, RADIUS)) {
            if (!(entity instanceof Tameable tameable) || !tameable.isTamed()
                    || !(entity instanceof LivingEntity companion)
                    || tameable.getOwner() == null
                    || !player.getUniqueId().equals(tameable.getOwner().getUniqueId())) {
                continue;
            }

            companion.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, EFFECT_TICKS, 0, false, true, true));
            companion.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, EFFECT_TICKS, 0, false, true, true));
            companion.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, EFFECT_TICKS, 1, false, true, true));
            player.getWorld().spawnParticle(Particle.HEART, companion.getLocation().add(0.0D, 1.0D, 0.0D), 5, 0.3D, 0.3D, 0.3D);
            empowered++;
        }

        if (empowered == 0) {
            player.sendMessage(resolveMessage("spell.primal_bond.no-companions", "<gray>Nincs a közeledben megszelídített társad.</gray>"));
            return false;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 0.8F, 1.2F);
        player.sendMessage(resolveMessage("spell.primal_bond.activated", "<dark_green>Az ősi kötelék megerősíti a társaidat.</dark_green>"));
        return true;
    }
}
