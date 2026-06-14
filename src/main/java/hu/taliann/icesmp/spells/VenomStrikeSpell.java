package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Poisoner specialization signature spell: poisons and weakens the targeted enemy.
 */
public final class VenomStrikeSpell extends BaseSpell {

    private static final double RANGE = 10.0D;
    private static final int POISON_TICKS = 8 * 20;
    private static final int WEAKNESS_TICKS = 6 * 20;

    public VenomStrikeSpell(final MessageManager messageManager) {
        super(messageManager, "venom_strike", "Méregcsapás", 60, SpellCostType.HUNGER, 6);
    }

    @Override
    public void execute(final Player player) {
        final LivingEntity target = SpellTargetingUtil.rayTraceLivingEntity(player, RANGE);
        if (target == null) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a látómeződben."));
            return;
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, POISON_TICKS, 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, WEAKNESS_TICKS, 0, false, true, true));
        player.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.5D, 0.3D, 0.05D);
        player.playSound(target.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.8F, 1.4F);
    }
}
