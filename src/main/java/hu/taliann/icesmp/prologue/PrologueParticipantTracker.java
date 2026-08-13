package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Finale presence/damage evidence and current in-arena participant snapshot. */
public final class PrologueParticipantTracker {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PrologueManager state;
    private final PrologueWorldAccess worldAccess;
    private final ConcurrentHashMap<UUID, Integer> presenceSeconds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> damage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> bossDamage = new ConcurrentHashMap<>();
    private volatile Set<UUID> currentNear = Set.of();
    private volatile Set<UUID> lastPersisted = Set.of();
    private volatile boolean tracking;
    private volatile boolean durable;

    public PrologueParticipantTracker(final JavaPlugin plugin, final ConfigManager config,
                                      final PrologueManager state, final PrologueWorldAccess worldAccess) {
        this.plugin = plugin;
        this.config = config;
        this.state = state;
        this.worldAccess = worldAccess;
    }

    public void begin(final boolean durable) {
        presenceSeconds.clear();
        damage.clear();
        bossDamage.clear();
        currentNear = Set.of();
        lastPersisted = durable ? state.finaleParticipants() : Set.of();
        tracking = true;
        this.durable = durable;
    }

    public void resumeDurable() {
        presenceSeconds.clear();
        damage.clear();
        bossDamage.clear();
        currentNear = Set.of();
        lastPersisted = state.finaleParticipants();
        tracking = true;
        durable = true;
    }

    public void stop() {
        tracking = false;
        currentNear = Set.of();
    }

    public void tickPresence() {
        if (!tracking) return;
        final Location anchor = worldAccess.gatheringAnchor();
        if (anchor == null) {
            currentNear = Set.of();
            return;
        }
        final double radius = Math.max(8.0D, config.getDouble(
                "world-events.prologue.finale.participant-radius", 72.0D));
        final Set<UUID> snapshot = ConcurrentHashMap.newKeySet();
        currentNear = snapshot;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                if (!tracking || !player.isOnline()) return;
                if (PrologueWorldAccess.within(player.getLocation(), anchor, radius)) {
                    snapshot.add(player.getUniqueId());
                    presenceSeconds.merge(player.getUniqueId(), 1, Math::addExact);
                    updateDurableEligibility();
                }
            }, null);
        }
    }

    public void recordDamage(final UUID playerId, final double amount, final boolean boss) {
        if (!tracking || playerId == null || !Double.isFinite(amount) || amount <= 0.0D) return;
        damage.merge(playerId, amount, Double::sum);
        if (boss) bossDamage.merge(playerId, amount, Double::sum);
        updateDurableEligibility();
    }

    public int currentParticipantCount() {
        return currentNear.size();
    }

    public Set<UUID> currentParticipants() {
        return Set.copyOf(currentNear);
    }

    public Set<UUID> eligibleParticipants() {
        final int minPresence = Math.max(1, config.getInt(
                "world-events.prologue.finale.rewards.minimum-presence-seconds", 45));
        final double minDamage = Math.max(0.1D, config.getDouble(
                "world-events.prologue.finale.rewards.minimum-damage", 1.0D));
        final double minBossDamage = Math.max(0.1D, config.getDouble(
                "world-events.prologue.finale.rewards.minimum-boss-damage", 1.0D));
        final Set<UUID> candidates = new HashSet<>();
        candidates.addAll(presenceSeconds.keySet());
        candidates.addAll(damage.keySet());
        candidates.addAll(bossDamage.keySet());
        candidates.addAll(durable ? state.finaleParticipants() : Set.of());
        candidates.removeIf(playerId -> {
            if (durable && state.finaleParticipants().contains(playerId)) return false;
            final int seconds = presenceSeconds.getOrDefault(playerId, 0);
            final double dealt = damage.getOrDefault(playerId, 0.0D);
            final double bossDealt = bossDamage.getOrDefault(playerId, 0.0D);
            return bossDealt < minBossDamage && (seconds < minPresence || dealt < minDamage);
        });
        return Set.copyOf(candidates);
    }

    private void updateDurableEligibility() {
        if (!tracking || !durable) return;
        final Set<UUID> eligible = eligibleParticipants();
        if (eligible.equals(lastPersisted)) return;
        synchronized (this) {
            if (eligible.equals(lastPersisted)) return;
            state.recordParticipants(eligible, "participant-tracker");
            lastPersisted = eligible;
        }
    }
}
