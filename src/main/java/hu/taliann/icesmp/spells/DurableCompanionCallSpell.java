package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Cast feedback only; the class runtime commits and projects the durable companion afterwards. */
public final class DurableCompanionCallSpell extends BaseSpell {

    private final Particle particle;
    private final Sound sound;
    private final float pitch;
    private final String castingMessage;

    public DurableCompanionCallSpell(final MessageManager messages,
                                     final String id,
                                     final String defaultName,
                                     final int cooldown,
                                     final SpellCostType costType,
                                     final int costAmount,
                                     final Particle particle,
                                     final Sound sound,
                                     final float pitch,
                                     final String castingMessage) {
        super(messages, id, defaultName, cooldown, costType, costAmount);
        this.particle = particle;
        this.sound = sound;
        this.pitch = pitch;
        this.castingMessage = castingMessage;
    }

    @Override
    public void execute(final Player player) {
        player.getWorld().spawnParticle(particle, player.getLocation().add(0.0D, 1.0D, 0.0D),
                24, 0.8D, 0.8D, 0.8D, 0.03D);
        player.playSound(player.getLocation(), sound, 1.0F, pitch);
        player.sendMessage(resolveMessage("spell." + getId() + ".casting", castingMessage));
    }
}
