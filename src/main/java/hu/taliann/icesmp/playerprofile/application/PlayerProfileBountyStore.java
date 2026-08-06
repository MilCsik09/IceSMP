package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Restart-safe cross-player bounty outbox owned by the criminal's faction section. */
public final class PlayerProfileBountyStore {

    private static final String PENDING = "bounty.pending";
    private static final String COMPLETED_SEQUENCE = "bounty.completed-sequence";
    private static final String NEXT_ELIGIBLE_AT = "bounty.next-eligible-at";

    public record PendingBounty(String operationId, UUID victimId, UUID hunterId,
                                long sinGeneration, long payoutSequence,
                                CurrencyType currency, long amountMilli,
                                long createdAtEpochMillis,
                                long cooldownUntilEpochMillis) {
        public PendingBounty {
            if (operationId == null || operationId.isBlank() || operationId.length() > 128) {
                throw new IllegalArgumentException("invalid bounty operation id");
            }
            Objects.requireNonNull(victimId, "victimId");
            Objects.requireNonNull(hunterId, "hunterId");
            Objects.requireNonNull(currency, "currency");
            if (sinGeneration <= 0L || payoutSequence <= 0L || amountMilli <= 0L
                    || createdAtEpochMillis <= 0L
                    || cooldownUntilEpochMillis < createdAtEpochMillis) {
                throw new IllegalArgumentException("invalid bounty outbox");
            }
        }
    }

    public record Reservation(PendingBounty pending, boolean created) {
        public Reservation { Objects.requireNonNull(pending, "pending"); }
    }

    public Optional<PendingBounty> pending(final UUID victimId) {
        final FactionSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(victimId, "victimId"),
                ProfileSectionId.FACTION, FactionSection.class);
        return decodePending(victimId, section.extensions().get(PENDING));
    }

    /**
     * Atomically reserves one payout and optionally clears the sin count. An existing pending
     * payout is returned unchanged so a later kill cannot steal a recovery claim.
     */
    public CompletionStage<Optional<Reservation>> reserve(
            final UUID victimId,
            final UUID hunterId,
            final CurrencyType currency,
            final long amountMilli,
            final int minimumSins,
            final boolean clearSins,
            final long cooldownMillis) {
        Objects.requireNonNull(victimId, "victimId");
        Objects.requireNonNull(hunterId, "hunterId");
        Objects.requireNonNull(currency, "currency");
        if (amountMilli <= 0L || minimumSins < 1 || cooldownMillis < 0L) {
            throw new IllegalArgumentException("invalid bounty reservation");
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                victimId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final Optional<PendingBounty> existing = decodePending(
                            victimId, current.extensions().get(PENDING));
                    if (existing.isPresent()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                Optional.of(new Reservation(existing.orElseThrow(), false)));
                    }
                    final PlayerProfileSinStore.SinState sin =
                            PlayerProfileSinStore.decode(current);
                    final long now = System.currentTimeMillis();
                    if (sin.count() < minimumSins || now < nextEligibleAt(current)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(Optional.empty());
                    }
                    final long sequence = Math.addExact(completedSequence(current), 1L);
                    final long cooldownUntil = saturatingAdd(now, cooldownMillis);
                    final String operationId = "bounty-" + victimId + '-' + sequence;
                    final PendingBounty pending = new PendingBounty(operationId,
                            victimId, hunterId, sin.generation(), sequence, currency,
                            amountMilli, now, cooldownUntil);
                    FactionSection next = current;
                    if (clearSins) {
                        next = PlayerProfileSinStore.withSin(current, 0,
                                sin.sinner(), sin.darkPact(), sin.generation());
                    }
                    next = withPending(next, pending);
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            Optional.of(new Reservation(pending, true)));
                });
    }

    /** Removes the outbox only after the hunter wallet receipt committed. */
    public CompletionStage<Boolean> complete(final UUID victimId,
                                             final PendingBounty expected) {
        Objects.requireNonNull(expected, "expected");
        if (!expected.victimId().equals(victimId)) {
            throw new IllegalArgumentException("bounty victim mismatch");
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                victimId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final Optional<PendingBounty> live = decodePending(
                            victimId, current.extensions().get(PENDING));
                    if (live.isEmpty()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                completedSequence(current) >= expected.payoutSequence());
                    }
                    if (!live.orElseThrow().equals(expected)) {
                        throw new IllegalStateException("bounty outbox identity changed");
                    }
                    final LinkedHashMap<String, Object> extensions =
                            new LinkedHashMap<>(current.extensions());
                    extensions.remove(PENDING);
                    extensions.put(COMPLETED_SEQUENCE, Math.max(
                            completedSequence(current), expected.payoutSequence()));
                    extensions.put(NEXT_ELIGIBLE_AT, Math.max(
                            nextEligibleAt(current), expected.cooldownUntilEpochMillis()));
                    final FactionSection next = new FactionSection(
                            current.membershipId(), current.lastChosenFaction(),
                            current.everChosen(), current.joinedAt(), current.leftAt(),
                            current.history(), current.reputation(), current.cooldowns(),
                            extensions);
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    private static FactionSection withPending(final FactionSection current,
                                              final PendingBounty pending) {
        final LinkedHashMap<String, Object> extensions =
                new LinkedHashMap<>(current.extensions());
        extensions.put(PENDING, Map.of(
                "operation-id", pending.operationId(),
                "hunter", pending.hunterId().toString(),
                "generation", pending.sinGeneration(),
                "sequence", pending.payoutSequence(),
                "currency", pending.currency().name(),
                "amount-milli", pending.amountMilli(),
                "created-at", pending.createdAtEpochMillis(),
                "cooldown-until", pending.cooldownUntilEpochMillis()));
        return new FactionSection(current.membershipId(), current.lastChosenFaction(),
                current.everChosen(), current.joinedAt(), current.leftAt(),
                current.history(), current.reputation(), current.cooldowns(), extensions);
    }

    private static Optional<PendingBounty> decodePending(final UUID victimId,
                                                         final Object raw) {
        if (raw == null) return Optional.empty();
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalStateException("invalid bounty pending type");
        }
        try {
            final String operationId = requiredString(map, "operation-id");
            final UUID hunter = UUID.fromString(requiredString(map, "hunter"));
            final long generation = requiredLong(map, "generation");
            final long sequence = requiredLong(map, "sequence");
            final CurrencyType currency = CurrencyType.valueOf(
                    requiredString(map, "currency"));
            final long amount = requiredLong(map, "amount-milli");
            final long created = requiredLong(map, "created-at");
            final long cooldownUntil = requiredLong(map, "cooldown-until");
            return Optional.of(new PendingBounty(operationId, victimId, hunter,
                    generation, sequence, currency, amount, created, cooldownUntil));
        } catch (final RuntimeException invalid) {
            throw new IllegalStateException("invalid bounty pending payload", invalid);
        }
    }

    private static long completedSequence(final FactionSection section) {
        return nonNegativeLong(section.extensions().get(COMPLETED_SEQUENCE),
                "bounty completed sequence");
    }

    private static long nextEligibleAt(final FactionSection section) {
        return nonNegativeLong(section.extensions().get(NEXT_ELIGIBLE_AT),
                "bounty next eligible timestamp");
    }

    private static long nonNegativeLong(final Object raw, final String field) {
        if (raw == null) return 0L;
        if (raw instanceof Number number && number.longValue() >= 0L) {
            return number.longValue();
        }
        throw new IllegalStateException("invalid " + field);
    }

    private static String requiredString(final Map<?, ?> map, final String key) {
        final Object raw = map.get(key);
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("missing bounty field: " + key);
        }
        return value;
    }

    private static long requiredLong(final Map<?, ?> map, final String key) {
        final Object raw = map.get(key);
        if (!(raw instanceof Number value) || value.longValue() <= 0L) {
            throw new IllegalArgumentException("invalid bounty field: " + key);
        }
        return value.longValue();
    }

    private static long saturatingAdd(final long first, final long second) {
        if (second <= 0L) return first;
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
}
