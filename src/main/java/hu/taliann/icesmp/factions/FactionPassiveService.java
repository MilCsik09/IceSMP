package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Thread-safe, transient per-player/per-mob retaliation state. */
public final class FactionPassiveService implements PlayerStateCleanup {

    private record PlayerMob(UUID playerId, UUID mobId) {
    }

    private final LongSupplier clock;
    private final Map<PlayerMob, Long> neutralRetaliationUntil = new ConcurrentHashMap<>();
    private final Map<PlayerMob, Long> darkRetaliationUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> entityFireUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> scriptedCombatFireUntil = new ConcurrentHashMap<>();
    private final Set<UUID> adjustingWitherEffect = ConcurrentHashMap.newKeySet();

    public FactionPassiveService() {
        this(System::currentTimeMillis);
    }

    public FactionPassiveService(final LongSupplier clock) {
        this.clock = clock;
    }

    public void provokeNeutral(final UUID playerId, final UUID mobId, final long retaliationMillis) {
        if (playerId == null || mobId == null || retaliationMillis <= 0L) {
            return;
        }
        pruneExpired();
        neutralRetaliationUntil.put(new PlayerMob(playerId, mobId), expiresAt(retaliationMillis));
    }

    public boolean isNeutralRetaliating(final UUID playerId, final UUID mobId) {
        return playerId != null && mobId != null
                && isLive(neutralRetaliationUntil, new PlayerMob(playerId, mobId));
    }

    public long neutralRetaliationRemainingMillis(final UUID playerId, final UUID mobId) {
        return playerId == null || mobId == null ? 0L
                : remainingMillis(neutralRetaliationUntil, new PlayerMob(playerId, mobId));
    }

    public void provokeDark(final UUID playerId, final UUID mobId, final long retaliationMillis) {
        if (playerId == null || mobId == null || retaliationMillis <= 0L) {
            return;
        }
        pruneExpired();
        darkRetaliationUntil.put(new PlayerMob(playerId, mobId), expiresAt(retaliationMillis));
    }

    public boolean isDarkRetaliating(final UUID playerId, final UUID mobId) {
        return playerId != null && mobId != null
                && isLive(darkRetaliationUntil, new PlayerMob(playerId, mobId));
    }

    public long darkRetaliationRemainingMillis(final UUID playerId, final UUID mobId) {
        return playerId == null || mobId == null ? 0L
                : remainingMillis(darkRetaliationUntil, new PlayerMob(playerId, mobId));
    }

    /** Clears exactly one mob lease after entity retirement or rejected scheduling. */
    public void clearNeutralRetaliation(final UUID playerId, final UUID mobId) {
        if (playerId != null && mobId != null) {
            neutralRetaliationUntil.remove(new PlayerMob(playerId, mobId));
        }
    }

    /** Clears exactly one mob lease after entity retirement or rejected scheduling. */
    public void clearDarkRetaliation(final UUID playerId, final UUID mobId) {
        if (playerId != null && mobId != null) {
            darkRetaliationUntil.remove(new PlayerMob(playerId, mobId));
        }
    }

    /**
     * Converts Paper's floating-point combust duration (seconds) without truncating sub-second
     * provenance. Invalid or unrepresentable durations fail closed instead of being clamped.
     */
    public static long combustDurationMillis(final float durationSeconds) {
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0.0F) {
            return 0L;
        }
        final double millis = Math.ceil(durationSeconds * 1_000.0D);
        if (!Double.isFinite(millis) || millis > Long.MAX_VALUE) {
            return 0L;
        }
        return (long) millis;
    }

    /**
     * Paper combustion cannot shorten an already longer fire duration. Provenance therefore lives
     * for the effective post-event fire window, not merely for the newly requested duration.
     */
    public static long effectiveCombustDurationMillis(final float durationSeconds,
                                                       final int existingFireTicks) {
        final long requested = combustDurationMillis(durationSeconds);
        final long existing = Math.max(0L, (long) existingFireTicks) * 50L;
        return Math.max(requested, existing);
    }

    public void markEntityFire(final UUID playerId, final long durationMillis) {
        markEntityFire(playerId, durationMillis, false);
    }

    public void markEntityFire(final UUID playerId, final long durationMillis,
                               final boolean scriptedCombat) {
        if (playerId == null || durationMillis <= 0L) {
            return;
        }
        pruneExpired();
        final long expiry = expiresAt(durationMillis);
        entityFireUntil.merge(playerId, expiry, Math::max);
        if (scriptedCombat) {
            scriptedCombatFireUntil.merge(playerId, expiry, Math::max);
        }
    }

    public boolean isEntityFire(final UUID playerId) {
        return isLive(entityFireUntil, playerId);
    }

    public boolean isScriptedCombatFire(final UUID playerId) {
        return isLive(scriptedCombatFireUntil, playerId);
    }

    public boolean beginWitherAdjustment(final UUID playerId) {
        return playerId != null && adjustingWitherEffect.add(playerId);
    }

    public void endWitherAdjustment(final UUID playerId) {
        adjustingWitherEffect.remove(playerId);
    }

    public boolean isAdjustingWitherEffect(final UUID playerId) {
        return adjustingWitherEffect.contains(playerId);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        darkRetaliationUntil.keySet().removeIf(key -> key.playerId().equals(playerId));
        entityFireUntil.remove(playerId);
        scriptedCombatFireUntil.remove(playerId);
        adjustingWitherEffect.remove(playerId);
        neutralRetaliationUntil.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void clearAll() {
        neutralRetaliationUntil.clear();
        darkRetaliationUntil.clear();
        entityFireUntil.clear();
        scriptedCombatFireUntil.clear();
        adjustingWitherEffect.clear();
    }

    int transientEntryCount() {
        pruneExpired();
        return neutralRetaliationUntil.size() + darkRetaliationUntil.size() + entityFireUntil.size()
                + scriptedCombatFireUntil.size()
                + adjustingWitherEffect.size();
    }

    private long expiresAt(final long durationMillis) {
        try {
            return Math.addExact(clock.getAsLong(), durationMillis);
        } catch (final ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private <K> boolean isLive(final Map<K, Long> state, final K key) {
        if (key == null) {
            return false;
        }
        final Long until = state.get(key);
        if (until == null) {
            return false;
        }
        if (until <= clock.getAsLong()) {
            state.remove(key, until);
            return false;
        }
        return true;
    }

    private <K> long remainingMillis(final Map<K, Long> state, final K key) {
        if (key == null) {
            return 0L;
        }
        final Long until = state.get(key);
        if (until == null) {
            return 0L;
        }
        final long remaining = until - clock.getAsLong();
        if (remaining <= 0L) {
            state.remove(key, until);
            return 0L;
        }
        return remaining;
    }

    private void pruneExpired() {
        final long now = clock.getAsLong();
        neutralRetaliationUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        darkRetaliationUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        entityFireUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        scriptedCombatFireUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
