package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Guardian specialization signature spell: a short defensive stance with
 * damage resistance and bonus absorption hearts.
 */
public final class BulwarkSpell extends BaseSpell {

    private static final int DURATION_TICKS = 10 * 20;

    public BulwarkSpell(final MessageManager messageManager) {
        super(messageManager, "bulwark", "Bástya", 120, SpellCostType.HUNGER, 8);
    }

    @Override
    public void execute(final Player player) {
        final int durationTicks = balanceInt("duration-ticks", DURATION_TICKS);
        final int amplifier = balanceInt("amplifier", 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, amplifier, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, durationTicks, amplifier, false, true, true));
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.5D, 0.8D, 0.5D, 0.02D);
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.7F);
        player.sendMessage(resolveMessage("spell.bulwark.activated", "<gold>Bástyává szilárdulsz: a csapások lepattannak rólad.</gold>"));
    }
}
