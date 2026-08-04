package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.relics.RelicTrigger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class RelicCooldownService {

    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public boolean isOnCooldown(final UUID playerId, final String relicId, final RelicTrigger trigger) {
        return getRemainingMillis(playerId, relicId, trigger) > 0L;
    }

    public long getRemainingMillis(final UUID playerId, final String relicId, final RelicTrigger trigger) {
        final Long expiresAt = cooldowns.get(key(playerId, relicId, trigger));
        if (expiresAt == null) {
            return 0L;
        }

        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    public void startCooldown(final UUID playerId, final String relicId, final RelicTrigger trigger,
                              final long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return;
        }

        cooldowns.put(key(playerId, relicId, trigger), System.currentTimeMillis() + cooldownMillis);
    }

    public void clearPlayer(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        final String prefix = playerId + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String key(final UUID playerId, final String relicId, final RelicTrigger trigger) {
        return playerId + ":" + relicId + ":" + trigger.name();
    }
}

/** Per-entry isolation for inventory refreshes; programming failures remain visible to the caller. */
final class RelicRefreshPipeline {

    private RelicRefreshPipeline() {
    }

    static <T, D> int refresh(final T[] entries,
                              final Function<T, D> identifier,
                              final BiConsumer<T, D> refresher,
                              final FailureHandler<T, D> failureHandler) {
        if (entries == null) {
            return 0;
        }

        int changed = 0;
        for (int index = 0; index < entries.length; index++) {
            final T entry = entries[index];
            D definition = null;
            try {
                definition = identifier.apply(entry);
                if (definition == null) {
                    continue;
                }
                refresher.accept(entry, definition);
                changed++;
            } catch (final RuntimeException exception) {
                failureHandler.accept(index, entry, definition, exception);
            }
        }
        return changed;
    }

    @FunctionalInterface
    interface FailureHandler<T, D> {
        void accept(int index, T entry, D definition, RuntimeException exception);
    }
}
