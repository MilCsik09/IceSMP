package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSection;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensions;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongUnaryOperator;

/**
 * Typed helper for small durable gameplay values stored in section extension maps.
 * All writes use conditional section CAS; no manager-owned map or player PDC is involved.
 */
public final class PlayerProfileGameplayState {

    private final JavaPlugin plugin;

    public PlayerProfileGameplayState(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public long getLong(final UUID playerId,
                        final ProfileSectionId sectionId,
                        final String key,
                        final long fallback) {
        return PlayerProfileAuthority.current().longExtension(playerId, sectionId, key)
                .orElse(fallback);
    }

    public boolean getBoolean(final UUID playerId,
                              final ProfileSectionId sectionId,
                              final String key,
                              final boolean fallback) {
        return PlayerProfileAuthority.current().booleanExtension(playerId, sectionId, key)
                .orElse(fallback);
    }

    public String getString(final UUID playerId,
                            final ProfileSectionId sectionId,
                            final String key,
                            final String fallback) {
        return PlayerProfileAuthority.current().stringExtension(playerId, sectionId, key)
                .orElse(fallback);
    }

    public <T extends PlayerProfileSection> CompletionStage<Void> put(
            final UUID playerId,
            final ProfileSectionId sectionId,
            final Class<T> type,
            final String key,
            final Object value) {
        return PlayerProfileAuthority.current().putExtension(playerId, sectionId, type, key, value)
                .thenApply(ignored -> null);
    }

    /** Atomically reserves a cooldown timestamp; false means the cooldown is still active. */
    public <T extends PlayerProfileSection> CompletionStage<Boolean> reserveCooldown(
            final UUID playerId,
            final ProfileSectionId sectionId,
            final Class<T> type,
            final String key,
            final long nowEpochMillis,
            final long cooldownMillis) {
        if (nowEpochMillis < 0L || cooldownMillis < 0L) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("cooldown timestamps must be non-negative"));
        }
        final String normalized = normalizeKey(key);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, sectionId, type, current -> {
                    final Object raw = current.extensions().get(normalized);
                    final long previous = raw instanceof Number number ? number.longValue() : Long.MIN_VALUE;
                    if (previous != Long.MIN_VALUE && nowEpochMillis - previous < cooldownMillis) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            PlayerProfileSectionExtensions.put(current, normalized, nowEpochMillis), true);
                });
    }

    /** Atomically updates one non-negative long extension and returns the committed value. */
    public <T extends PlayerProfileSection> CompletionStage<Long> updateLong(
            final UUID playerId,
            final ProfileSectionId sectionId,
            final Class<T> type,
            final String key,
            final long fallback,
            final LongUnaryOperator update) {
        Objects.requireNonNull(update, "update");
        final String normalized = normalizeKey(key);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, sectionId, type, current -> {
                    final Object raw = current.extensions().get(normalized);
                    final long previous = raw instanceof Number number ? number.longValue() : fallback;
                    final long nextValue = update.applyAsLong(previous);
                    if (nextValue < 0L) {
                        throw new IllegalArgumentException("extension counter cannot be negative");
                    }
                    if (nextValue == previous && raw instanceof Number) {
                        return PlayerProfileService.ConditionalMutation.unchanged(nextValue);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            PlayerProfileSectionExtensions.put(current, normalized, nextValue), nextValue);
                });
    }

    /** Stores a canonical immutable string set as a sorted extension list. */
    public <T extends PlayerProfileSection> CompletionStage<Set<String>> updateStringSet(
            final UUID playerId,
            final ProfileSectionId sectionId,
            final Class<T> type,
            final String key,
            final java.util.function.UnaryOperator<Set<String>> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        final String normalized = normalizeKey(key);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, sectionId, type, current -> {
                    final TreeSet<String> existing = new TreeSet<>();
                    final Object raw = current.extensions().get(normalized);
                    if (raw instanceof Iterable<?> iterable) {
                        for (final Object value : iterable) {
                            if (value instanceof String text && !text.isBlank()) {
                                existing.add(text.trim().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                    final Set<String> requested = Objects.requireNonNull(
                            mutation.apply(Set.copyOf(existing)), "set mutation result");
                    final TreeSet<String> next = new TreeSet<>();
                    for (final String value : requested) {
                        if (value == null || value.isBlank()) {
                            throw new IllegalArgumentException("extension set contains a blank value");
                        }
                        next.add(value.trim().toLowerCase(Locale.ROOT));
                    }
                    if (next.equals(existing)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(Set.copyOf(next));
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            PlayerProfileSectionExtensions.put(current, normalized, java.util.List.copyOf(next)),
                            Set.copyOf(next));
                });
    }

    public void runOnOwnerThread(final Player player, final Runnable action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        player.getScheduler().run(plugin, ignored -> action.run(), null);
    }

    private static String normalizeKey(final String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("extension key cannot be blank");
        }
        final String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 128 || !normalized.matches("[a-z0-9][a-z0-9._:-]*")) {
            throw new IllegalArgumentException("invalid extension key: " + key);
        }
        return normalized;
    }
}
