package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Poisoner utility spell: the master of toxins can purge their own ailments
 * and gain a short burst of regeneration.
 */
public final class AntidoteSpell extends BaseSpell {

    private static final List<PotionEffectType> CURED_EFFECTS = List.of(
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS,
            PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS,
            PotionEffectType.HUNGER
    );

    public AntidoteSpell(final MessageManager messageManager) {
        super(messageManager, "antidote", "Ellenméreg", 90, SpellCostType.HUNGER, 4);
    }

    @Override
    public void execute(final Player player) {
        for (final PotionEffectType effectType : CURED_EFFECTS) {
            player.removePotionEffect(effectType);
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0, false, false, true));
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.4D, 0.6D, 0.4D);
        player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0F, 1.3F);
        player.sendMessage(resolveMessage("spell.antidote.activated", "<green>A mérgek elhagyják a testedet.</green>"));
    }
}
