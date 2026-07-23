package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * "Átok Nyúl" (id: shadowburn) — a kaszter egy átkozott (killer bunny kinézetű)
 * nyulat idéz meg a célkereszt irányába. A nyúl 3 másodpercig figyeli a
 * közelségét: ha egy ellenfél (nem a kaszter) hozzáér, felrobban és sebzést oszt
 * szét a közelben; ha lejár az ideje, hatástalanul eltűnik.
 */
public final class ShadowburnSpell extends BaseSpell {

    private final JavaPlugin plugin;

    public ShadowburnSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "shadowburn", "Átok Nyúl", 90, SpellCostType.XP, 35);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        final UUID casterId = player.getUniqueId();
        final Vector direction = player.getEyeLocation().getDirection().setY(0.0D).normalize();
        final double spawnDistance = balance("spawn-distance", 2.0D);
        final Location spawnLocation = player.getLocation().add(direction.multiply(spawnDistance));

        final Rabbit rabbit = player.getWorld().spawn(spawnLocation, Rabbit.class);
        rabbit.setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
        rabbit.setAI(false);
        rabbit.setCollidable(false);
        rabbit.addScoreboardTag("icesmp_shadowburn_rabbit");
        rabbit.addScoreboardTag("icesmp_owner_" + casterId);

        final double triggerRadius = balance("trigger-radius", 1.2D);
        final double explodeRadius = balance("explode-radius", 2.5D);
        final double damage = balance("damage", 6.0D);
        final int lifetimeTicks = balanceInt("lifetime-ticks", 3 * 20);

        // Folia: the rabbit's own entity scheduler drives the proximity watch, matching
        // the AngryChickenSpell pattern.
        rabbit.getScheduler().runAtFixedRate(plugin, task -> {
            if (!rabbit.isValid() || rabbit.isDead()) {
                task.cancel();
                return;
            }

            for (final Entity nearby : rabbit.getNearbyEntities(triggerRadius, triggerRadius, triggerRadius)) {
                if (!(nearby instanceof LivingEntity living) || living.getUniqueId().equals(casterId)) {
                    continue;
                }
                // Baráti tűz: párttag/frakciótárs ne élesítse az aknát.
                final Player owner = Bukkit.getPlayer(casterId);
                if (owner != null && Bukkit.isOwnedByCurrentRegion(owner)
                        && SpellTargetingUtil.isAlly(owner, living)) {
                    continue;
                }

                detonate(rabbit, casterId, explodeRadius, damage);
                task.cancel();
                return;
            }
        }, null, 1L, 1L);

        rabbit.getScheduler().runDelayed(plugin, task -> {
            if (rabbit.isValid() && !rabbit.isDead()) {
                rabbit.getWorld().spawnParticle(Particle.POOF, rabbit.getLocation(), 14, 0.3D, 0.3D, 0.3D, 0.02D);
                rabbit.remove();
            }
        }, null, lifetimeTicks);

        player.sendMessage(resolveMessage("spell.shadowburn.cast", "<dark_red>Egy átkozott nyulat idézel meg...</dark_red>"));
        player.playSound(player.getLocation(), Sound.ENTITY_RABBIT_AMBIENT, 0.8F, 0.6F);
    }

    private void detonate(final Rabbit rabbit, final UUID casterId, final double explodeRadius, final double damage) {
        final Location center = rabbit.getLocation();
        final World world = center.getWorld();
        rabbit.remove();

        world.spawnParticle(Particle.EXPLOSION, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.2F);

        final Player caster = Bukkit.getPlayer(casterId);
        final boolean casterInRegion = caster != null && Bukkit.isOwnedByCurrentRegion(caster);

        for (final Entity nearby : world.getNearbyEntities(center, explodeRadius, explodeRadius, explodeRadius)) {
            if (!(nearby instanceof LivingEntity living) || living.getUniqueId().equals(casterId)) {
                continue;
            }
            // Baráti tűz: a robbanás ne sebezze a párttagot/frakciótársat.
            if (casterInRegion && SpellTargetingUtil.isAlly(caster, living)) {
                continue;
            }

            if (casterInRegion) {
                hu.taliann.icesmp.utils.SpellDamageUtil.damageBySpell(caster, living, damage, getId());
            } else {
                living.damage(damage);
            }
        }
    }
}
