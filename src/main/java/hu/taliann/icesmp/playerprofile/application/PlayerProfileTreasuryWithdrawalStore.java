package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensions;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed per-player treasury withdrawal allowance; the treasury itself remains global. */
public final class PlayerProfileTreasuryWithdrawalStore {
    private static final String DAY_KEY = "treasury.withdraw.day";
    private static final String AMOUNT_KEY = "treasury.withdraw.milli";
    private static final long SCALE = 1_000L;

    public CompletionStage<Reservation> reserve(final UUID playerId, final long day,
                                                final double amount, final double cap) {
        final long requested = toMilli(amount, "amount");
        final long maximum = cap <= 0.0D ? Long.MAX_VALUE : toMilli(cap, "cap");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final long storedDay = number(current.extensions().get(DAY_KEY), -1L);
                    final long previous = storedDay == day
                            ? number(current.extensions().get(AMOUNT_KEY), 0L) : 0L;
                    if (requested > maximum || previous > maximum - requested) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Reservation(false, day, previous, requested));
                    }
                    FactionSection next = PlayerProfileSectionExtensions.put(current, DAY_KEY, day);
                    next = PlayerProfileSectionExtensions.put(next, AMOUNT_KEY,
                            Math.addExact(previous, requested));
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new Reservation(true, day, previous, requested));
                });
    }

    /** Compensates only the exact reservation produced by this call; concurrent use fails closed. */
    public CompletionStage<Boolean> rollback(final UUID playerId, final Reservation reservation) {
        if (reservation == null || !reservation.allowed()) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final long storedDay = number(current.extensions().get(DAY_KEY), -1L);
                    final long stored = number(current.extensions().get(AMOUNT_KEY), 0L);
                    final long expected = Math.addExact(reservation.previousMilli(),
                            reservation.reservedMilli());
                    if (storedDay != reservation.day() || stored != expected) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final FactionSection next = PlayerProfileSectionExtensions.put(
                            current, AMOUNT_KEY, reservation.previousMilli());
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public double usedToday(final UUID playerId, final long day) {
        final FactionSection current = PlayerProfileAuthority.current().requireSection(
                playerId, ProfileSectionId.FACTION, FactionSection.class);
        if (number(current.extensions().get(DAY_KEY), -1L) != day) return 0.0D;
        return number(current.extensions().get(AMOUNT_KEY), 0L) / (double) SCALE;
    }

    private static long toMilli(final double amount, final String label) {
        if (!Double.isFinite(amount) || amount <= 0.0D) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
        final double scaled = amount * SCALE;
        if (!Double.isFinite(scaled) || scaled > Long.MAX_VALUE) {
            throw new IllegalArgumentException(label + " exceeds storage range");
        }
        return Math.round(scaled);
    }

    private static long number(final Object raw, final long fallback) {
        if (raw == null) return fallback;
        if (!(raw instanceof Number number)) {
            throw new IllegalStateException("Invalid treasury allowance extension type");
        }
        final long value = number.longValue();
        if (value < 0L) throw new IllegalStateException("Negative treasury allowance extension");
        return value;
    }

    public record Reservation(boolean allowed, long day, long previousMilli,
                              long reservedMilli) {
        public Reservation {
            if (day < 0L || previousMilli < 0L || reservedMilli <= 0L) {
                throw new IllegalArgumentException("Invalid treasury allowance reservation");
            }
        }
    }
}
