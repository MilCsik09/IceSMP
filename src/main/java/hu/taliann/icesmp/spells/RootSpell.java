package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class RootSpell extends BaseSpell {

    public RootSpell(final MessageManager messageManager) {
        super(messageManager, "root", "Gyokerezes", 300, SpellCostType.HUNGER, 8);
    }

    @Override
    public void execute(final Player player) {
        final Location center = player.getLocation();
        for (final Entity entity : player.getWorld().getNearbyEntities(center, 5.0D, 5.0D, 5.0D)) {
            if (!(entity instanceof LivingEntity living) || entity == player) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 10, false, true, true));
            // JUMP_BOOST 128 is the vanilla "no jump" level — it pins the target in place.
            // (A high positive amplifier such as 250 would instead launch it skyward to its death.)
            living.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 128, false, true, true));
        }
    }
}

