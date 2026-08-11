package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.SpellDamageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * "Elmerobbantás" (id marad {@code mind_blast}) — ray-trace célzás; megjelöli a célpontot, majd
 * 3mp múlva a CÉL saját schedulerén felrobban: a cél 6.0, a körülötte 3 blokkon belüli élőlények
 * (a kaszter kivétel) 5.0 splash-sebzést kapnak. Ha a cél közben meghal/eltűnik, semmi nem történik.
 * Nincs célpont esetén a költség visszajár.
 */
public final class MindBlastSpell extends BaseSpell {

    private final JavaPlugin plugin;

    public MindBlastSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "mind_blast", "Elmerobbantás", 30, SpellCostType.XP, 40);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public CastOutcome executeCast(final Player player) {
        return executeSpell(player) ? CastOutcome.SUCCESS : CastOutcome.NO_TARGET;
    }

    @Override
    public boolean executeSpell(final Player player) {
        final LivingEntity target = SpellTargetingUtil.rayTraceLivingEntity(player, balance("range", 14.0D));
        if (target == null) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a látómezőben."));
            return false;
        }

        final UUID casterId = player.getUniqueId();
        final CastModifiers modifiers = SpellExecutionContext.capture();
        final double blastDamage = balance("damage", 6.0D);
        final double splashRadius = balance("splash-radius", 3.0D);
        final double splashDamage = balance("splash-damage", 5.0D);
        final int fuseTicks = balanceInt("fuse-ticks", 3 * 20);

        target.getWorld().spawnParticle(Particle.SCULK_SOUL, target.getLocation().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.5D, 0.3D, 0.02D);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1.0F, 1.2F);

        // Folia: the delayed detonation must run on the target's own entity scheduler.
        target.getScheduler().runDelayed(plugin, task -> {
            if (!target.isValid() || target.isDead()) {
                return;
            }

            final Player caster = plugin.getServer().getPlayer(casterId);
            final boolean casterInCurrentRegion = caster != null && Bukkit.isOwnedByCurrentRegion(caster);

            if (casterInCurrentRegion) {
                SpellDamageUtil.damageBySpell(caster, target, blastDamage, getId(), modifiers);
            } else {
                target.damage(SpellDamageUtil.scaledDamage(blastDamage, modifiers));
            }

            for (final Entity nearby : target.getNearbyEntities(splashRadius, splashRadius, splashRadius)) {
                if (!(nearby instanceof LivingEntity living) || living instanceof ArmorStand
                        || living == target || living.getUniqueId().equals(casterId)
                        || SpellTargetingUtil.isAlly(casterId, living)) {
                    continue;
                }

                if (casterInCurrentRegion) {
                    SpellDamageUtil.damageBySpell(caster, living, splashDamage, getId(), modifiers);
                } else {
                    living.damage(SpellDamageUtil.scaledDamage(splashDamage, modifiers));
                }
            }

            target.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation().add(0.0D, 1.0D, 0.0D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6F, 1.6F);
        }, null, fuseTicks);

        return true;
    }
}
