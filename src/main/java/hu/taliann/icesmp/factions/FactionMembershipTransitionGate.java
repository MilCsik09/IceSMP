package hu.taliann.icesmp.factions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded in-flight admission leases; durable unfinished effects remain in PlayerProfile. */
public final class FactionMembershipTransitionGate {
    private static final int LIMIT = 128;
    private record Lease(UUID operationId, boolean running) { }
    private final Map<UUID, Lease> active = new HashMap<>();

    public synchronized boolean claim(final UUID playerId, final UUID operationId) {
        Objects.requireNonNull(playerId); Objects.requireNonNull(operationId);
        if (active.containsKey(playerId) || active.size() >= LIMIT) return false;
        active.put(playerId, new Lease(operationId, true));
        return true;
    }

    public synchronized boolean resume(final UUID playerId, final UUID operationId) {
        Objects.requireNonNull(playerId); Objects.requireNonNull(operationId);
        final Lease lease = active.get(playerId);
        if (lease == null) return claim(playerId, operationId);
        if (!lease.operationId().equals(operationId) || lease.running()) return false;
        active.put(playerId, new Lease(operationId, true));
        return true;
    }

    public synchronized void pause(final UUID playerId, final UUID operationId) {
        final Lease lease = active.get(Objects.requireNonNull(playerId));
        if (lease != null && lease.operationId().equals(Objects.requireNonNull(operationId))) {
            active.put(playerId, new Lease(operationId, false));
        }
    }

    public synchronized boolean pending(final UUID playerId) {
        return active.containsKey(Objects.requireNonNull(playerId));
    }

    public synchronized boolean paused(final UUID playerId, final UUID operationId) {
        final Lease lease = active.get(Objects.requireNonNull(playerId));
        return lease != null && lease.operationId().equals(operationId) && !lease.running();
    }

    public synchronized boolean release(final UUID playerId, final UUID operationId) {
        final Lease lease = active.get(Objects.requireNonNull(playerId));
        return lease != null && lease.operationId().equals(Objects.requireNonNull(operationId)) && active.remove(playerId, lease);
    }
}
