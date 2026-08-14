package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensions;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.EconomySection;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed per-player period anti-farm/reward budgets. */
public final class PlayerProfileDailyBudgetStore {

    public record BudgetState(long day, long spent) {
        public BudgetState {
            if (day < -1L || spent < 0L) throw new IllegalArgumentException("invalid budget state");
        }
    }

    /** The serial identifies the exact reservation commit, so compensation cannot ABA-match. */
    public record Reservation(boolean allowed, BudgetState state, long serial) {
        public Reservation { Objects.requireNonNull(state, "state"); }
    }

    public BudgetState read(final UUID playerId, final String budgetId) {
        final EconomySection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.ECONOMY, EconomySection.class);
        return read(section, normalize(budgetId));
    }

    public CompletionStage<Reservation> reserve(final UUID playerId,
                                                final String budgetId,
                                                final long day,
                                                final long amount,
                                                final long cap) {
        if (day < 0L || amount < 0L || cap < 0L) {
            throw new IllegalArgumentException("negative daily budget value");
        }
        final String id = normalize(budgetId);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class, current -> {
                    final BudgetState before = read(current, id);
                    final long serial = number(current.extensions().get(serialKey(id)), 0L,
                            "budget serial");
                    final long spent = before.day() == day ? before.spent() : 0L;
                    if (amount > cap || spent > cap - amount) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Reservation(false, new BudgetState(day, spent), serial));
                    }
                    final BudgetState after = new BudgetState(day, Math.addExact(spent, amount));
                    final long nextSerial = Math.addExact(serial, 1L);
                    EconomySection next = PlayerProfileSectionExtensions.put(current,
                            dayKey(id), day);
                    next = PlayerProfileSectionExtensions.put(next, sumKey(id), after.spent());
                    next = PlayerProfileSectionExtensions.put(next, serialKey(id), nextSerial);
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new Reservation(true, after, nextSerial));
                });
    }

    /**
     * Compensates a successful reservation only while no later mutation changed the budget.
     * This prevents a delayed runtime failure from rolling back another accepted operation.
     */
    public CompletionStage<Boolean> rollback(final UUID playerId,
                                             final String budgetId,
                                             final Reservation reservation,
                                             final long amount) {
        Objects.requireNonNull(reservation, "reservation");
        if (!reservation.allowed() || amount < 0L || reservation.state().spent() < amount) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        final String id = normalize(budgetId);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ECONOMY, EconomySection.class, current -> {
                    final BudgetState durable = read(current, id);
                    final long serial = number(current.extensions().get(serialKey(id)), 0L,
                            "budget serial");
                    if (serial != reservation.serial() || !durable.equals(reservation.state())) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final BudgetState previous = new BudgetState(
                            durable.day(), durable.spent() - amount);
                    EconomySection next = PlayerProfileSectionExtensions.put(current,
                            dayKey(id), previous.day());
                    next = PlayerProfileSectionExtensions.put(next, sumKey(id), previous.spent());
                    next = PlayerProfileSectionExtensions.put(next, serialKey(id),
                            Math.addExact(serial, 1L));
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    private static BudgetState read(final EconomySection section, final String id) {
        final long day = number(section.extensions().get(dayKey(id)), -1L, "budget day");
        final long spent = number(section.extensions().get(sumKey(id)), 0L, "budget sum");
        return new BudgetState(day, spent);
    }

    private static long number(final Object raw, final long fallback, final String field) {
        if (raw == null) return fallback;
        if (raw instanceof Number value) return value.longValue();
        throw new IllegalStateException("invalid " + field + " type");
    }

    private static String dayKey(final String id) { return "budget." + id + ".day"; }
    private static String sumKey(final String id) { return "budget." + id + ".sum"; }
    private static String serialKey(final String id) { return "budget." + id + ".serial"; }

    private static String normalize(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("budget id required");
        final String id = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        if (id.isBlank() || id.length() > 96) throw new IllegalArgumentException("invalid budget id");
        return id;
    }
}
