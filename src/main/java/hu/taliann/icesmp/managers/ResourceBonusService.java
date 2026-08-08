package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classrelic.ClassRelicActivation;
import hu.taliann.icesmp.classrelic.RelicModifier;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileClassMechanicStore;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class-resource bonuses with PlayerProfile-backed warlock pact state and the concrete resource-side
 * integration of Class Relic Class Power / Resonance / Awakening. It deliberately does not create a
 * second relic mechanic model: activation, ownership and durable awakening cooldown stay in
 * {@link hu.taliann.icesmp.classrelic.ClassRelicService}.
 */
public final class ResourceBonusService implements Listener {

    private static final String PACT_KEY = "warlock.pact.resource-multiplier";
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final hu.taliann.icesmp.classrelic.ClassRelicService classRelicService;
    private final PlayerProfileClassMechanicStore mechanics =
            new PlayerProfileClassMechanicStore();
    private final Map<UUID, Double> pactCache = new ConcurrentHashMap<>();

    public ResourceBonusService(final JavaPlugin plugin, final ConfigManager configManager,
                                final hu.taliann.icesmp.classrelic.ClassRelicService classRelicService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.classRelicService = classRelicService;
    }

    /**
     * Base Class Power arrives through the framework's typed modifier channel. An active
     * specialization Resonance then extends the same resource loop by a small, explicit amount;
     * no relic id/class ownership checks are duplicated here.
     */
    public double maxMultiplier(final UUID playerId) {
        double multiplier = pactCache.getOrDefault(playerId, 1.0D)
                * classRelicService.modifier(playerId, RelicModifier.CLASS_RESOURCE_MAX);
        final ClassRelicActivation activation = classRelicService.resolve(playerId);
        if (activation.resonanceActive() && activation.resolvedResonanceId().isPresent()) {
            final String resonance = activation.resolvedResonanceId().orElseThrow();
            final double resonancePercent = Math.max(0.0D, configManager.getDouble(
                    "class-gameplay.relics.resonance-resource-percent." + resonance, 0.0D));
            multiplier *= 1.0D + resonancePercent / 100.0D;
        }
        return multiplier;
    }

    public boolean hasPakt(final Player player) {
        if (player == null) return false;
        if (pactCache.containsKey(player.getUniqueId())) return true;
        try {
            final boolean present = mechanics.read(player.getUniqueId(), PACT_KEY).isPresent();
            if (present) loadPact(player.getUniqueId());
            return present;
        } catch (final RuntimeException notReady) {
            return false;
        }
    }

    /** Optimistically updates the runtime mirror; the PlayerProfile CAS proves durability. */
    public void sealPakt(final Player player) {
        if (player == null || hasPakt(player)) return;
        final double multiplier = 1.0D + Math.max(0.0D,
                configManager.getDouble("pakt.bonus-percent", 20.0D)) / 100.0D;
        pactCache.put(player.getUniqueId(), multiplier);
        mechanics.putIfAbsent(player.getUniqueId(), PACT_KEY, Double.toString(multiplier))
                .whenComplete((created, failure) -> {
                    if (failure != null) {
                        pactCache.remove(player.getUniqueId());
                        plugin.getLogger().severe("PlayerProfile warlock pact commit failed for "
                                + player.getUniqueId() + ": " + rootMessage(failure));
                    } else if (!Boolean.TRUE.equals(created)) {
                        loadPact(player.getUniqueId());
                    }
                });
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        schedulePactLoad(event.getPlayer(), 0);
    }

    private void schedulePactLoad(final Player player, final int attempt) {
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            if (loadPact(player.getUniqueId()) || attempt >= 39) return;
            schedulePactLoad(player, attempt + 1);
        }, null, 5L);
    }

    private boolean loadPact(final UUID playerId) {
        try {
            final var stored = mechanics.read(playerId, PACT_KEY);
            if (stored.isEmpty()) {
                pactCache.remove(playerId);
                return true;
            }
            final double multiplier = Double.parseDouble(stored.orElseThrow());
            if (!Double.isFinite(multiplier) || multiplier < 1.0D || multiplier > 10.0D) {
                throw new IllegalStateException("invalid warlock pact multiplier");
            }
            pactCache.put(playerId, multiplier);
            return true;
        } catch (final hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.ProfileNotReadyException notReady) {
            return false;
        } catch (final RuntimeException corrupt) {
            plugin.getLogger().severe("PlayerProfile warlock pact read failed for "
                    + playerId + ": " + corrupt.getMessage());
            pactCache.remove(playerId);
            return true;
        }
    }

    /**
     * Awakening is a low-health safety valve, not a second class kit. The durable cooldown is armed
     * first; potion effects are published only after ARMED, so persistence failure never grants the
     * gameplay effect. The current hit is not rewritten and lethal hits do not retroactively revive.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLowHealthDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0.0D) return;
        final var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute == null) return;
        final double remaining = player.getHealth() - event.getFinalDamage();
        if (remaining <= 0.0D) return;
        final double threshold = Math.max(1.0D, Math.min(99.0D, configManager.getDouble(
                "class-gameplay.relics.awakening.low-health-threshold-percent", 30.0D)));
        if (remaining / maxHealthAttribute.getValue() * 100.0D > threshold) return;

        final ClassRelicActivation activation = classRelicService.resolve(player.getUniqueId());
        if (!activation.basePowerActive()
                || !("warrior".equals(activation.classId()) || "evoker".equals(activation.classId()))) {
            return;
        }
        if (classRelicService.tryArmAwakening(player)
                != hu.taliann.icesmp.classrelic.ClassRelicService.AwakeningResult.ARMED) {
            return;
        }

        final String path = "class-gameplay.relics.awakening." + activation.classId();
        final int duration = Math.max(20,
                configManager.getInt(path + ".duration-ticks", 100));
        if ("warrior".equals(activation.classId())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration,
                    Math.max(0, configManager.getInt(path + ".absorption-amplifier", 1)),
                    true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration,
                    Math.max(0, configManager.getInt(path + ".speed-amplifier", 0)),
                    true, true, true));
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration,
                    Math.max(0, configManager.getInt(path + ".absorption-amplifier", 0)),
                    true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration,
                    Math.max(0, configManager.getInt(path + ".regeneration-amplifier", 1)),
                    true, true, true));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        pactCache.remove(event.getPlayer().getUniqueId());
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
