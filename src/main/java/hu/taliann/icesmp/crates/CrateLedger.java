package hu.taliann.icesmp.crates;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Thread-confined-by-caller opening statistics and cooldown domain with exact rollback tokens. */
public final class CrateLedger {

    private final Map<UUID, PlayerState> players = new HashMap<>();

    public Mutation prepare(final UUID playerId, final String name, final String crateId, final int opens,
                            final long now, final long cooldownMillis) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(crateId, "crateId");
        if (opens <= 0 || now < 0L || cooldownMillis < 0L) {
            throw new IllegalArgumentException("Invalid crate ledger mutation");
        }
        final PlayerState existing = players.get(playerId);
        final long previousCount = existing == null ? 0L : existing.counts.getOrDefault(crateId, 0L);
        final long previousCooldown = existing == null ? 0L : existing.cooldowns.getOrDefault(crateId, 0L);
        final String previousName = existing == null ? null : existing.lastKnownName;

        // Compute every failure-prone value before any side effect. The token is applied only after
        // the reward saga has reached its settlement stage, so scheduler rejection cannot create a
        // phantom persisted cooldown or statistic.
        final long newCount = Math.addExact(previousCount, opens);
        final long newCooldown = cooldownMillis > 0L
                ? Math.addExact(now, cooldownMillis) : previousCooldown;
        final long previousTotal = existing == null ? 0L : exactTotal(existing.counts);
        Math.addExact(previousTotal, opens);
        return new Mutation(playerId, crateId, previousCount, previousCooldown, previousName,
                name == null ? previousName : name, newCount, newCooldown);
    }

    public boolean canApply(final Mutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        final PlayerState existing = players.get(mutation.playerId());
        final long currentCount = existing == null ? 0L
                : existing.counts.getOrDefault(mutation.crateId(), 0L);
        final long currentCooldown = existing == null ? 0L
                : existing.cooldowns.getOrDefault(mutation.crateId(), 0L);
        final String currentName = existing == null ? null : existing.lastKnownName;
        return currentCount == mutation.previousCount()
                && currentCooldown == mutation.previousCooldown()
                && Objects.equals(currentName, mutation.previousName());
    }

    public void apply(final Mutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (!canApply(mutation)) {
            throw new IllegalStateException("Crate ledger mutation token is stale");
        }
        final PlayerState existing = players.get(mutation.playerId());
        final PlayerState state = existing == null ? new PlayerState() : existing;
        if (existing == null) {
            players.put(mutation.playerId(), state);
        }
        state.lastKnownName = mutation.newName();
        state.counts.put(mutation.crateId(), mutation.newCount());
        if (mutation.newCooldown() == 0L) {
            state.cooldowns.remove(mutation.crateId());
        } else {
            state.cooldowns.put(mutation.crateId(), mutation.newCooldown());
        }
    }

    public Mutation record(final UUID playerId, final String name, final String crateId, final int opens,
                           final long now, final long cooldownMillis) {
        final Mutation mutation = prepare(playerId, name, crateId, opens, now, cooldownMillis);
        apply(mutation);
        return mutation;
    }

    public boolean canRollback(final Mutation mutation) {
        final PlayerState state = players.get(mutation.playerId());
        return state != null
                && state.counts.getOrDefault(mutation.crateId(), 0L) == mutation.newCount()
                && state.cooldowns.getOrDefault(mutation.crateId(), 0L) == mutation.newCooldown();
    }

    public void rollback(final Mutation mutation) {
        final PlayerState state = players.get(mutation.playerId());
        if (!canRollback(mutation)) {
            throw new IllegalStateException("Crate ledger rollback token is stale");
        }
        if (mutation.previousCount() == 0L) {
            state.counts.remove(mutation.crateId());
        } else {
            state.counts.put(mutation.crateId(), mutation.previousCount());
        }
        if (mutation.previousCooldown() == 0L) {
            state.cooldowns.remove(mutation.crateId());
        } else {
            state.cooldowns.put(mutation.crateId(), mutation.previousCooldown());
        }
        state.lastKnownName = mutation.previousName();
        removeIfEmpty(mutation.playerId(), state);
    }

    public long remainingCooldown(final UUID playerId, final String crateId, final long now) {
        final PlayerState state = players.get(playerId);
        if (state == null) {
            return 0L;
        }
        final long until = state.cooldowns.getOrDefault(crateId, 0L);
        if (until <= now) {
            return 0L;
        }
        try {
            return Math.subtractExact(until, now);
        } catch (final ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public long count(final UUID playerId, final String crateId) {
        final PlayerState state = players.get(playerId);
        return state == null ? 0L : state.counts.getOrDefault(crateId, 0L);
    }

    public long total(final UUID playerId) {
        final PlayerState state = players.get(playerId);
        return state == null ? 0L : exactTotal(state.counts);
    }

    private static long exactTotal(final Map<String, Long> counts) {
        long total = 0L;
        for (final long count : counts.values()) {
            total = Math.addExact(total, count);
        }
        return total;
    }

    public ResetToken reset(final UUID playerId, final String crateId) {
        final PlayerState previous = players.get(playerId);
        final PlayerSnapshot before = previous == null ? null : previous.toSnapshot();
        if (previous == null) {
            return new ResetToken(playerId, before);
        }
        if (crateId == null) {
            players.remove(playerId);
        } else {
            previous.counts.remove(crateId);
            previous.cooldowns.remove(crateId);
            removeIfEmpty(playerId, previous);
        }
        return new ResetToken(playerId, before);
    }

    public void rollbackReset(final ResetToken token) {
        if (token.previous() == null) {
            players.remove(token.playerId());
        } else {
            players.put(token.playerId(), PlayerState.from(token.previous()));
        }
    }

    public Map<UUID, PlayerSnapshot> snapshot() {
        final Map<UUID, PlayerSnapshot> snapshot = new LinkedHashMap<>();
        players.forEach((uuid, state) -> snapshot.put(uuid, state.toSnapshot()));
        return Map.copyOf(snapshot);
    }

    public void replace(final Map<UUID, PlayerSnapshot> snapshot) {
        players.clear();
        snapshot.forEach((uuid, state) -> players.put(uuid, PlayerState.from(state)));
    }

    public UUID findByName(final String name) {
        if (name == null) {
            return null;
        }
        for (final Map.Entry<UUID, PlayerState> entry : players.entrySet()) {
            if (entry.getValue().lastKnownName != null && entry.getValue().lastKnownName.equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String lastKnownName(final UUID playerId) {
        final PlayerState state = players.get(playerId);
        return state == null ? null : state.lastKnownName;
    }

    private void removeIfEmpty(final UUID playerId, final PlayerState state) {
        if (state.counts.isEmpty() && state.cooldowns.isEmpty()) {
            players.remove(playerId);
        }
    }

    public record Mutation(UUID playerId, String crateId, long previousCount, long previousCooldown,
                           String previousName, String newName, long newCount, long newCooldown) {
    }

    public record ResetToken(UUID playerId, PlayerSnapshot previous) {
    }

    public record PlayerSnapshot(String lastKnownName, Map<String, Long> counts, Map<String, Long> cooldowns) {
        public PlayerSnapshot {
            counts = Map.copyOf(counts);
            cooldowns = Map.copyOf(cooldowns);
        }
    }

    private static final class PlayerState {
        private String lastKnownName;
        private final Map<String, Long> counts = new HashMap<>();
        private final Map<String, Long> cooldowns = new HashMap<>();

        private PlayerSnapshot toSnapshot() {
            return new PlayerSnapshot(lastKnownName, counts, cooldowns);
        }

        private static PlayerState from(final PlayerSnapshot snapshot) {
            final PlayerState state = new PlayerState();
            state.lastKnownName = snapshot.lastKnownName();
            state.counts.putAll(snapshot.counts());
            state.cooldowns.putAll(snapshot.cooldowns());
            return state;
        }
    }
}
