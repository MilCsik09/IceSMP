package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * "Megzavarás" — a leírt hatás ("rosszul lőnek és mennek az ellenfelek") célpont-típusonként
 * MÁS eszközzel valósul meg, mert a látás-zavaró effektek (vakság/sötétség) csak a játékos
 * képernyőjére hatnak, a mob-AI-t nem érdeklik (playtest-visszajelzés):
 * <ul>
 *   <li><b>Játékos ellenfél:</b> vakság + sötétség — ténylegesen nem lát célozni;</li>
 *   <li><b>Mob:</b> lassúság + gyengeség, ÉS a teljes időtartam alatt másodpercenként elveszti
 *       a célpontját (aggro-törlés a mob saját régió-schedulerén) — tehát "elfelejti", kit
 *       támadott, és ha újra rád támad, gyengén üt.</li>
 * </ul>
 * A korábbi méreg-komponens kikerült: a spell identitása tiszta hatástalanítás (CC), nem sebzés.
 */
public final class ConfusionSpell extends BaseSpell {

    private final JavaPlugin plugin;

    public ConfusionSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "confusion", "Megzavaras", 20 * 60, SpellCostType.XP, 160);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        final double radius = balance("radius", 15.0D);
        final int durationTicks = balanceInt("duration-ticks", 20 * 10);
        boolean hit = false;
        for (final LivingEntity target : player.getLocation().getNearbyLivingEntities(radius, radius, radius)) {
            if (target == player || SpellTargetingUtil.isAlly(player, target)
                    || target.getLocation().distanceSquared(player.getLocation()) > (radius * radius)) {
                continue;
            }

            // Folia: 15 blokkos sugárnál a talált entitás más régióhoz tartozhat.
            if (Bukkit.isOwnedByCurrentRegion(target)) {
                confuse(target, durationTicks);
            } else {
                target.getScheduler().run(plugin, task -> confuse(target, durationTicks), null);
            }
            hit = true;
        }
        // No target: fail the cast so the (expensive, long-cooldown) cast is refunded.
        if (!hit) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a közeledben."));
        }
        return hit;
    }

    /** Runs on the target's own region thread. */
    private void confuse(final LivingEntity target, final int durationTicks) {
        if (target instanceof Player) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, durationTicks, 0, false, true, true));
            return;
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, 0, false, true, true));
        if (target instanceof Mob mob) {
            final long confusedUntil = System.currentTimeMillis() + (durationTicks * 50L);
            mob.setTarget(null);
            // A mob saját schedulerén tickel, így a task az entitással együtt hal meg (retired).
            mob.getScheduler().runAtFixedRate(plugin, task -> {
                if (!mob.isValid() || System.currentTimeMillis() > confusedUntil) {
                    task.cancel();
                    return;
                }
                mob.setTarget(null);
            }, null, 20L, 20L);
        }
    }
}
