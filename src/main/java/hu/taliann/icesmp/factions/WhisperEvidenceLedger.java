package hu.taliann.icesmp.factions;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Runtime-only, witness-to-suspect evidence. Evidence is exact, expiring and single-use. */
public final class WhisperEvidenceLedger {

    private final Map<UUID, Map<UUID, Long>> runtimeEvidence = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public WhisperEvidenceLedger() {
        this(System::currentTimeMillis);
    }

    public WhisperEvidenceLedger(final LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void grant(final UUID witnessId, final UUID suspectId, final long ttlMillis) {
        Objects.requireNonNull(witnessId, "witnessId");
        Objects.requireNonNull(suspectId, "suspectId");
        if (witnessId.equals(suspectId)) throw new IllegalArgumentException("self evidence");
        if (ttlMillis <= 0L) throw new IllegalArgumentException("non-positive evidence ttl");
        final long now = clock.getAsLong();
        final long expiresAt = ttlMillis > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + ttlMillis;
        runtimeEvidence.computeIfAbsent(witnessId, ignored -> new ConcurrentHashMap<>())
                .put(suspectId, expiresAt);
    }

    public boolean has(final UUID witnessId, final UUID suspectId) {
        Objects.requireNonNull(witnessId, "witnessId");
        Objects.requireNonNull(suspectId, "suspectId");
        final Map<UUID, Long> bySuspect = runtimeEvidence.get(witnessId);
        if (bySuspect == null) return false;
        final Long expiresAt = bySuspect.get(suspectId);
        if (expiresAt == null) return false;
        if (expiresAt > clock.getAsLong()) return true;
        bySuspect.remove(suspectId, expiresAt);
        if (bySuspect.isEmpty()) runtimeEvidence.remove(witnessId, bySuspect);
        return false;
    }

    public boolean consume(final UUID witnessId, final UUID suspectId) {
        Objects.requireNonNull(witnessId, "witnessId");
        Objects.requireNonNull(suspectId, "suspectId");
        final Map<UUID, Long> bySuspect = runtimeEvidence.get(witnessId);
        if (bySuspect == null) return false;
        final long now = clock.getAsLong();
        final AtomicBoolean consumed = new AtomicBoolean(false);
        bySuspect.computeIfPresent(suspectId, (ignored, expiresAt) -> {
            if (expiresAt > now) consumed.set(true);
            return null;
        });
        if (bySuspect.isEmpty()) runtimeEvidence.remove(witnessId, bySuspect);
        return consumed.get();
    }

    public void clearPlayer(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        runtimeEvidence.remove(playerId);
        for (final Map.Entry<UUID, Map<UUID, Long>> entry : runtimeEvidence.entrySet()) {
            entry.getValue().remove(playerId);
            if (entry.getValue().isEmpty()) runtimeEvidence.remove(entry.getKey(), entry.getValue());
        }
    }
}
