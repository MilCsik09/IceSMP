package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class EagleEyeSpell extends BaseSpell {

    private static final int DURATION_TICKS = 30 * 20;

    public EagleEyeSpell(final MessageManager messageManager) {
        super(messageManager, "eagle_eye", "Sasszem", 90, SpellCostType.HUNGER, 4);
    }

    @Override
    public void execute(final Player player) {
        final int durationTicks = balanceInt("duration-ticks", DURATION_TICKS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, durationTicks, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 0, false, false, true));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_LAUNCH, 1.0F, 1.4F);
        player.sendMessage(resolveMessage("spell.eagle_eye.activated", "<green>Sasszem aktív: élesedik a látásod és gyorsulnak a lépteid.</green>"));
    }
}
