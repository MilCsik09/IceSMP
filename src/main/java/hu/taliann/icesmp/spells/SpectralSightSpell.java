package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Szellemlatas" — 8s, 4-phase invisibility toggle (invisible / visible / invisible / visible,
 * 2s each) driven on the caster's own entity scheduler. Only ever touches the caster itself
 * (its own scheduler only ticks while the caster is present), so no Folia region hop is needed.
 */
public final class SpectralSightSpell extends BaseSpell {

    private static final Map<UUID, ScheduledTask> ACTIVE_CHANNELS = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;

    public SpectralSightSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "spectral_sight", "Szellemlátás", 75, SpellCostType.HUNGER, 3);
        this.plugin = plugin;
    }

    @Override
    public boolean canCast(final Player player) {
        return !ACTIVE_CHANNELS.containsKey(player.getUniqueId());
    }

    @Override
    public void execute(final Player player) {
        final UUID playerId = player.getUniqueId();
        final int phaseTicks = balanceInt("phase-ticks", 40); // 2 mp fázisonként
        final int phases = balanceInt("phases", 4);

        final AtomicInteger phaseCounter = new AtomicInteger(-1);
        final ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduled -> {
            final Player online = Bukkit.getPlayer(playerId);
            final int phase = phaseCounter.incrementAndGet();
            if (online == null || !online.isValid() || online.isDead() || phase >= phases) {
                if (online != null) {
                    online.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
                scheduled.cancel();
                ACTIVE_CHANNELS.remove(playerId);
                return;
            }

            if (phase % 2 == 0) {
                online.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, phaseTicks + 5, 0, false, false, true));
                online.getWorld().spawnParticle(Particle.SCULK_SOUL, online.getLocation().add(0.0D, 1.0D, 0.0D), 15, 0.3D, 0.5D, 0.3D, 0.02D);
                online.playSound(online.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 0.6F, 1.0F);
            } else {
                online.removePotionEffect(PotionEffectType.INVISIBILITY);
                online.getWorld().spawnParticle(Particle.SCULK_SOUL, online.getLocation().add(0.0D, 1.0D, 0.0D), 8, 0.3D, 0.5D, 0.3D, 0.01D);
            }
        }, () -> ACTIVE_CHANNELS.remove(playerId), 1L, phaseTicks);

        if (task != null) {
            ACTIVE_CHANNELS.put(playerId, task);
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final ScheduledTask task = ACTIVE_CHANNELS.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        final Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }
}
