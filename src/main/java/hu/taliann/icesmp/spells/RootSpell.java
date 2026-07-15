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
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        final Location center = player.getLocation();
        final double radius = balance("radius", 5.0D);
        final int durationTicks = balanceInt("duration-ticks", 80);
        boolean hit = false;
        for (final Entity entity : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity == player || SpellTargetingUtil.isAlly(player, living)) {
                continue;
            }
            hit = true;
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, balanceInt("slowness-amplifier", 10), false, true, true));
            // JUMP_BOOST 128 is the vanilla "no jump" level — it pins the target in place.
            // (A high positive amplifier such as 250 would instead launch it skyward to its death.)
            // Intentionally NOT balance-overridable: it's a mechanism sentinel, not a strength dial.
            living.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, durationTicks, 128, false, true, true));
        }
        // No target: fail the cast so the (steep, 8-hunger) cost is refunded and no cooldown starts.
        if (!hit) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a közeledben."));
        }
        return hit;
    }
}

