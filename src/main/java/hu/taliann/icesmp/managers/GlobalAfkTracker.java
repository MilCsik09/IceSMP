package hu.taliann.icesmp.managers;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe state machine for the retained global AFK behaviour.
 *
 * <p>The state is immutable and replaced atomically, so async chat activity and region-thread
 * commands cannot leave the manual flag and the inactivity baseline out of sync.
 */
public final class GlobalAfkTracker {

    private static final long MAX_TIMEOUT_SECONDS = 31_536_000L;

    private record State(long lastActivityMillis, boolean manualAfk) {
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    public void recordActivity(final UUID playerId) {
        recordActivity(playerId, System.currentTimeMillis());
    }

    public void recordActivity(final UUID playerId, final long nowMillis) {
        if (playerId != null) {
            states.put(playerId, new State(nowMillis, false));
        }
    }

    /**
     * Toggles the player's overall AFK state and starts a fresh inactivity window.
     *
     * @return the new AFK state; {@code false} also covers a null player id
     */
    public boolean toggleAfk(final UUID playerId, final long nowMillis, final long timeoutSeconds) {
        if (playerId == null) {
            return false;
        }
        final AtomicBoolean nowAfk = new AtomicBoolean();
        states.compute(playerId, (ignored, previous) -> {
            final State current = previous == null ? new State(nowMillis, false) : previous;
            final boolean nextManual = !isAfk(current, nowMillis, timeoutSeconds);
            nowAfk.set(nextManual);
            return new State(nowMillis, nextManual);
        });
        return nowAfk.get();
    }

    public boolean isAfk(final UUID playerId, final long nowMillis, final long timeoutSeconds) {
        if (playerId == null) {
            return false;
        }
        final State state = states.get(playerId);
        return state != null && isAfk(state, nowMillis, timeoutSeconds);
    }

    public boolean isManuallyAfk(final UUID playerId) {
        final State state = playerId == null ? null : states.get(playerId);
        return state != null && state.manualAfk();
    }

    public void clear(final UUID playerId) {
        if (playerId != null) {
            states.remove(playerId);
        }
    }

    /**
     * The preprocess listener must not treat the AFK toggle itself as activity. Only IceSMP's
     * literal and namespaced roots are exempt; similar or foreign namespaced commands stay activity.
     */
    public static boolean isAfkToggleCommand(final String rawCommand) {
        if (rawCommand == null) {
            return false;
        }
        final String trimmed = rawCommand.stripLeading();
        if (!trimmed.startsWith("/")) {
            return false;
        }
        int end = 1;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        final String root = trimmed.substring(1, end);
        return root.equalsIgnoreCase("afk") || root.equalsIgnoreCase("icesmp:afk");
    }

    private static boolean isAfk(final State state, final long nowMillis, final long timeoutSeconds) {
        return state.manualAfk()
                || elapsedAtLeast(state.lastActivityMillis(), nowMillis, timeoutMillis(timeoutSeconds));
    }

    private static long timeoutMillis(final long timeoutSeconds) {
        final long clamped = Math.max(1L, Math.min(MAX_TIMEOUT_SECONDS, timeoutSeconds));
        return clamped * 1_000L;
    }

    private static boolean elapsedAtLeast(final long startedAt, final long nowMillis, final long threshold) {
        if (nowMillis < startedAt) {
            return false;
        }
        try {
            return Math.subtractExact(nowMillis, startedAt) >= threshold;
        } catch (final ArithmeticException overflow) {
            return true;
        }
    }
}
