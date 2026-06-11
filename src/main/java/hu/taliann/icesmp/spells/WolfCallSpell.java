package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;

/**
 * Beast Master signature summon: calls a loyal, already-tamed wolf companion
 * to the caster's side. The wolf is spawned and tamed instantly on the
 * caster's region thread (no scheduled tasks).
 */
public final class WolfCallSpell extends BaseSpell {

    public WolfCallSpell(final MessageManager messageManager) {
        super(messageManager, "wolf_call", "Farkashívás", 600, SpellCostType.XP, 100);
    }

    @Override
    public void execute(final Player player) {
        player.getWorld().spawn(player.getLocation(), Wolf.class, wolf -> {
            wolf.setTamed(true);
            wolf.setOwner(player);
        });

        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0.0D, 0.5D, 0.0D), 20, 0.6D, 0.3D, 0.6D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.0F, 0.6F);
        player.sendMessage(resolveMessage("spell.wolf_call.activated", "<dark_green>Hű farkas társ érkezik a hívásodra.</dark_green>"));
    }
}
