package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DoubleJumpSpell extends BaseSpell {

    private static volatile Set<UUID> usedDoubleJump = ConcurrentHashMap.newKeySet();

    public DoubleJumpSpell(final MessageManager messageManager) {
        super(messageManager, "double_jump", "Dupla Ugras", 0, SpellCostType.HUNGER, 3);
    }

    @Override
    public boolean canCast(final Player player) {
        return !usedDoubleJump.contains(player.getUniqueId());
    }

    @Override
    public void execute(final Player player) {
        usedDoubleJump.add(player.getUniqueId());
        final double jumpVelocity = balance("jump-velocity", 0.8D);
        final Vector velocity = player.getVelocity().clone();
        velocity.setY(Math.max(jumpVelocity, velocity.getY() + jumpVelocity));
        player.setVelocity(velocity);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0.0D, 0.1D, 0.0D), 18, 0.25D, 0.1D, 0.25D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0F, 1.15F);
    }

    public static boolean hasUsedDoubleJump(final UUID playerId) {
        return playerId != null && usedDoubleJump.contains(playerId);
    }

    public static void clearDoubleJump(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        usedDoubleJump.remove(playerId);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        clearDoubleJump(playerId);
    }
}

