package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileDailyBudgetStore;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Common daily anti-farm budget implementation. */
public final class DailyBudget {

    private static final PlayerProfileDailyBudgetStore DURABLE =
            new PlayerProfileDailyBudgetStore();
    private static final Map<Key, PlayerProfileDailyBudgetStore.BudgetState> MIRRORS =
            new ConcurrentHashMap<>();
    private static final Map<Key, CompletableFuture<Void>> TAILS =
            new ConcurrentHashMap<>();
    private static final Map<Key, Object> LOCKS = new ConcurrentHashMap<>();

    private DailyBudget() { }

    /** UTC day bucket retained for backward gameplay semantics. */
    public static long dayIndex() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    /**
     * Synchronous compatibility facade over an optimistic projection. The accepted reservation is
     * serialized into an EconomySection CAS. A failed commit invalidates the projection, therefore
     * a later call reloads the durable value instead of continuing from an unproven amount.
     */
    public static boolean tryConsumeOnOwnThread(final Player player, final String budgetId,
                                                final double cap, final long amount) {
        if (player == null || amount < 0L) return false;
        if (cap <= 0.0D) return true;
        final long limit = (long) cap;
        final String id = normalize(budgetId);
        if (id == null) return true;
        final Key key = new Key(player.getUniqueId(), id);
        synchronized (LOCKS.computeIfAbsent(key, ignored -> new Object())) {
            final long today = dayIndex();
            final PlayerProfileDailyBudgetStore.BudgetState before = state(key);
            final long spent = before.day() == today ? before.spent() : 0L;
            if (amount > limit || spent > limit - amount) return false;
            final var after = new PlayerProfileDailyBudgetStore.BudgetState(
                    today, Math.addExact(spent, amount));
            MIRRORS.put(key, after);
            final CompletableFuture<Void> previous = TAILS.getOrDefault(key,
                    CompletableFuture.completedFuture(null));
            final CompletableFuture<Void> next = previous.thenCompose(ignored ->
                    DURABLE.reserve(key.playerId(), key.budgetId(), today, amount, limit)
                            .thenCompose(result -> result.allowed()
                                    ? CompletableFuture.completedFuture(null)
                                    : CompletableFuture.failedFuture(new IllegalStateException(
                                            "durable daily budget rejected optimistic reservation"))))
                    .toCompletableFuture();
            TAILS.put(key, next);
            next.whenComplete((ignored, failure) -> {
                TAILS.remove(key, next);
                if (failure != null) MIRRORS.remove(key);
            });
            return true;
        }
    }

    public static long spentTodayOnOwnThread(final Player player, final String budgetId) {
        final String id = normalize(budgetId);
        if (player == null || id == null) return 0L;
        final PlayerProfileDailyBudgetStore.BudgetState state =
                state(new Key(player.getUniqueId(), id));
        return state.day() == dayIndex() ? state.spent() : 0L;
    }

    private static PlayerProfileDailyBudgetStore.BudgetState state(final Key key) {
        final var mirror = MIRRORS.get(key);
        if (mirror != null) return mirror;
        try {
            final var durable = DURABLE.read(key.playerId(), key.budgetId());
            MIRRORS.put(key, durable);
            return durable;
        } catch (final RuntimeException notReady) {
            return new PlayerProfileDailyBudgetStore.BudgetState(-1L, 0L);
        }
    }

    private static String normalize(final String budgetId) {
        if (budgetId == null || budgetId.isBlank()) return null;
        final String id = budgetId.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        return id.isBlank() || id.length() > 96 ? null : id;
    }

    private record Key(UUID playerId, String budgetId) { }

    /**
     * Runtime-only budget for non-player or cross-entity keys. It is intentionally categorized as
     * ephemeral anti-spam state and never represents durable progression or currency ownership.
     */
    public static final class InMemory<K> {
        private final Map<K, long[]> entries = new ConcurrentHashMap<>();
        private final int sweepAbove;

        public InMemory(final int sweepAbove) {
            this.sweepAbove = Math.max(16, sweepAbove);
        }

        public boolean tryConsume(final K budgetKey, final long amount, final double cap) {
            if (budgetKey == null || amount < 0L) return false;
            if (cap <= 0.0D) return true;
            final long today = dayIndex();
            if (entries.size() > sweepAbove)
                entries.values().removeIf(entry -> entry[0] != today);
            final long limit = (long) cap;
            final boolean[] allowed = {false};
            entries.compute(budgetKey, (key, old) -> {
                final long spent = old != null && old[0] == today ? old[1] : 0L;
                if (amount > limit || spent > limit - amount)
                    return new long[]{today, spent};
                allowed[0] = true;
                return new long[]{today, Math.addExact(spent, amount)};
            });
            return allowed[0];
        }

        public long spentToday(final K budgetKey) {
            final long[] entry = budgetKey == null ? null : entries.get(budgetKey);
            return entry != null && entry[0] == dayIndex() ? entry[1] : 0L;
        }
    }
}
