package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

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
            living.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 250, false, true, true));
        }
    }

    public static void clearPlayerState(final UUID playerId) {
        // Root no longer stores persistent per-player movement state.
    }
}

