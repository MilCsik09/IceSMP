package hu.taliann.icesmp.dev.weaver.api;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Active origins survive expiry; the five-minute tail starts again when an active origin ends. */
public record PlayerQuarantine(UUID playerId, Set<UUID> activeOrigins, long quarantinedUntil) {
    public static final long MINIMUM_TAIL_MILLIS = 300_000L;
    public PlayerQuarantine {
        java.util.Objects.requireNonNull(playerId); activeOrigins = Set.copyOf(activeOrigins);
        if (activeOrigins.size() > 128 || quarantinedUntil < 0) throw new IllegalArgumentException("Invalid player quarantine bounds");
    }
    public static PlayerQuarantine clean(final UUID player) { return new PlayerQuarantine(player, Set.of(), 0); }
    public PlayerQuarantine affected(final DeveloperInfluence influence, final boolean active, final long now) {
        if (now < 0) throw new IllegalArgumentException("Invalid quarantine time");
        if (!influence.quarantinesRewards()) return this;
        final Set<UUID> origins = new HashSet<>(activeOrigins); if (active) origins.add(influence.operationId());
        return new PlayerQuarantine(playerId, origins, Math.max(quarantinedUntil, Math.addExact(now, MINIMUM_TAIL_MILLIS)));
    }
    public PlayerQuarantine ended(final UUID operation, final long now) {
        if (now < 0) throw new IllegalArgumentException("Invalid quarantine time");
        if (!activeOrigins.contains(operation)) return this;
        final Set<UUID> origins = new HashSet<>(activeOrigins); origins.remove(operation);
        return new PlayerQuarantine(playerId, origins, Math.max(quarantinedUntil, Math.addExact(now, MINIMUM_TAIL_MILLIS)));
    }
    public boolean quarantined(final long now) { return !activeOrigins.isEmpty() || now < quarantinedUntil; }
}
