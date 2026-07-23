package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * id stays {@code frost_fever}; display name is "Fagysugár" — a playtest eredetileg
 * "Fagycsapás"-t kért, de az ütközött a Fagylovag-spec {@code frost_strike} nevével, ezért
 * a sugár-mechanikát leíró, egyedi név került rá.
 *
 * <p>Straight 2-block-wide, ~12-block-long ray stepping 1 block at a time from the caster's
 * eye along their current look direction. Every living entity in the path is hit once
 * (damage + freeze + slowness), tracked via a per-cast dedupe set. Fully synchronous —
 * no scheduler tick, so no Folia region hop is needed (matches the other local-AoE spells
 * in this package).
 */
public final class FrostFeverSpell extends BaseSpell {

    private final JavaPlugin plugin;

    public FrostFeverSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "frost_fever", "Fagysugár", 90, SpellCostType.XP, 40);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        final double length = balance("length", 12.0D);
        final double halfWidth = balance("half-width", 1.0D); // 2 blokk széles sáv
        final double damage = balance("damage", 2.0D);
        final int freezeTicks = balanceInt("freeze-ticks", 120);
        final int slownessTicks = balanceInt("slowness-duration-ticks", 5 * 20);
        final int slownessAmplifier = balanceInt("slowness-amplifier", 2);

        final Location eye = player.getEyeLocation();
        final Vector direction = eye.getDirection().normalize();

        final Set<UUID> hit = new HashSet<>();
        for (double distance = 1.0D; distance <= length; distance += 1.0D) {
            final Location point = eye.clone().add(direction.clone().multiply(distance));
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, point, 4, halfWidth / 2.0D, 0.3D, halfWidth / 2.0D, 0.01D);
            for (final Entity nearby : player.getWorld().getNearbyEntities(point, halfWidth, 1.5D, halfWidth)) {
                if (!(nearby instanceof LivingEntity living) || living == player
                        || SpellTargetingUtil.isAlly(player, living) || !hit.add(living.getUniqueId())) {
                    continue;
                }
                hu.taliann.icesmp.utils.SpellDamageUtil.damageBySpell(player, living, damage, getId());
                living.setFreezeTicks(Math.max(living.getFreezeTicks(), freezeTicks));
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessTicks, slownessAmplifier, false, true, true));
            }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0F, 0.5F);
        return true;
    }
}
