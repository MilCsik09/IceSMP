package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Typed authority for independent infamy, wanted, exile and DARK-oath state. */
public final class PlayerProfileSinStore {

    private static final String SIN_COUNT = "sin.count";
    private static final String SIN_GENERATION = "sin.generation";
    private static final String WANTED = "sin.wanted";
    private static final String EXILED = "sin.exiled";
    private static final String DARK_PACT = "sin.dark-pact";
    private static final String RECEIPTS = "sin.operation-receipts";
    private static final int MAX_RECEIPTS = 128;

    public record SinState(int count, boolean wanted, boolean exiled, boolean darkPact,
                           long generation, Optional<FactionType> membership) {
        public SinState {
            if (count < 0 || generation < 0L) {
                throw new IllegalArgumentException("invalid sin state");
            }
            membership = membership == null ? Optional.empty() : membership;
        }

        /** Legacy compatibility projection: sinner means positive infamy only. */
        public boolean sinner() {
            return count > 0;
        }
    }

    public record AddResult(SinState state, boolean exiled,
                            Optional<FactionType> previousFaction) {
        public AddResult {
            Objects.requireNonNull(state, "state");
            previousFaction = previousFaction == null ? Optional.empty() : previousFaction;
        }
    }

    public record AddOnceResult(AddResult result, boolean applied) {
        public AddOnceResult {
            Objects.requireNonNull(result, "result");
        }
    }

    public SinState read(final UUID playerId) {
        return decode(PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.FACTION, FactionSection.class));
    }

    public CompletionStage<AddResult> add(final UUID playerId, final int amount,
                                          final int exileThreshold) {
        return add(playerId, amount, Math.max(1, exileThreshold - 1), exileThreshold);
    }

    public CompletionStage<AddResult> add(final UUID playerId, final int amount,
                                          final int wantedThreshold,
                                          final int exileThreshold) {
        validateAdd(amount, wantedThreshold, exileThreshold);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final AddResult result = addToSection(
                            current, amount, wantedThreshold, exileThreshold);
                    return PlayerProfileService.ConditionalMutation.changed(
                            sectionFor(current, result), result);
                });
    }

    /** Operation-ID protected infamy mutation for restart-safe outbox replay. */
    public CompletionStage<AddOnceResult> addOnce(final UUID playerId, final int amount,
                                                  final int exileThreshold,
                                                  final String operationId) {
        return addOnce(playerId, amount, Math.max(1, exileThreshold - 1),
                exileThreshold, operationId);
    }

    public CompletionStage<AddOnceResult> addOnce(final UUID playerId, final int amount,
                                                  final int wantedThreshold,
                                                  final int exileThreshold,
                                                  final String operationId) {
        validateAdd(amount, wantedThreshold, exileThreshold);
        final String receipt = receipt(operationId);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final LinkedHashSet<String> receipts = receipts(current);
                    if (receipts.contains(receipt)) {
                        final SinState state = decode(current);
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new AddOnceResult(new AddResult(state, false,
                                        state.membership()), false));
                    }
                    final AddResult result = addToSection(
                            current, amount, wantedThreshold, exileThreshold);
                    final FactionSection mutated = sectionFor(current, result);
                    while (receipts.size() >= MAX_RECEIPTS) receipts.remove(receipts.iterator().next());
                    receipts.add(receipt);
                    final FactionSection next = withReceipts(mutated, receipts);
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new AddOnceResult(result, true));
                });
    }

    private static void validateAdd(final int amount, final int wantedThreshold,
                                    final int exileThreshold) {
        if (amount <= 0) throw new IllegalArgumentException("sin amount must be positive");
        if (wantedThreshold < 0 || exileThreshold < 0) {
            throw new IllegalArgumentException("negative crime threshold");
        }
    }

    private static AddResult addToSection(final FactionSection current,
                                          final int amount,
                                          final int wantedThreshold,
                                          final int exileThreshold) {
        final SinState before = decode(current);
        final int count = Math.addExact(before.count(), amount);
        final long generation = before.count() == 0
                ? Math.addExact(before.generation(), 1L) : before.generation();
        final boolean wanted = before.wanted()
                || wantedThreshold > 0 && count >= wantedThreshold;
        final boolean newlyExiled = exileThreshold > 0 && count >= exileThreshold
                && !before.exiled();
        final SinState after = new SinState(count, wanted,
                before.exiled() || newlyExiled, before.darkPact(), generation,
                before.membership());
        return new AddResult(after, newlyExiled, before.membership());
    }

    private static FactionSection sectionFor(final FactionSection current,
                                             final AddResult result) {
        final SinState state = result.state();
        return withSin(current, state.count(), state.wanted(), state.exiled(),
                state.darkPact(), state.generation());
    }

    public CompletionStage<SinState> markSinner(final UUID playerId) {
        return mutate(playerId, state -> new Values(Math.max(1, state.count()),
                state.wanted(), state.exiled(), state.darkPact(), state.generation()));
    }

    public CompletionStage<SinState> reduce(final UUID playerId, final int amount) {
        if (amount < 0) throw new IllegalArgumentException("negative sin reduction");
        return mutate(playerId, state -> {
            final int count = Math.max(0, state.count() - amount);
            return new Values(count, count > 0 && state.wanted(), state.exiled(),
                    state.darkPact(), state.generation());
        });
    }

    public CompletionStage<SinState> resetCount(final UUID playerId) {
        return mutate(playerId, state -> new Values(0, false, state.exiled(),
                state.darkPact(), state.generation()));
    }

    public CompletionStage<SinState> sealDarkPact(final UUID playerId) {
        return mutate(playerId, state -> new Values(state.count(), state.wanted(),
                state.exiled(), true, state.generation()));
    }

    /** Administrative faction override: guarantees the DARK prerequisites. */
    public CompletionStage<SinState> sealDarkForFactionOverride(final UUID playerId) {
        return mutate(playerId, state -> new Values(state.count(), state.wanted(),
                true, true, state.generation()));
    }

    public CompletionStage<SinState> exile(final UUID playerId) {
        return mutate(playerId, state -> new Values(state.count(), state.wanted(),
                true, state.darkPact(), state.generation()));
    }

    public CompletionStage<SinState> clearDarkPactForFactionOverride(final UUID playerId) {
        return mutate(playerId, state -> new Values(state.count(), state.wanted(),
                state.exiled(), false, state.generation()));
    }

    public CompletionStage<SinState> breakDarkPact(final UUID playerId) {
        return mutate(playerId, state -> new Values(0, false, false,
                false, state.generation()));
    }

    public CompletionStage<Boolean> clearSinner(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final SinState before = decode(current);
                    if (before.count() == 0 && !before.wanted()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(true);
                    }
                    final FactionSection next = withSin(current, 0, false,
                            before.exiled(), before.darkPact(), before.generation());
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    private CompletionStage<SinState> mutate(final UUID playerId,
                                             final java.util.function.Function<SinState, Values> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final SinState before = decode(current);
                    final Values values = Objects.requireNonNull(mutation.apply(before), "sin mutation");
                    final FactionSection next = withSin(current, values.count(), values.wanted(),
                            values.exiled(), values.darkPact(), values.generation());
                    final SinState after = decode(next);
                    if (after.equals(before)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(before);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(next, after);
                });
    }

    private record Values(int count, boolean wanted, boolean exiled,
                          boolean darkPact, long generation) {
        private Values {
            if (count < 0 || generation < 0L) {
                throw new IllegalArgumentException("invalid sin values");
            }
        }
    }

    static SinState decode(final FactionSection section) {
        final long rawCount = section.reputation().getOrDefault(SIN_COUNT, 0L);
        final int count = Math.toIntExact(rawCount);
        final long generation = section.reputation().getOrDefault(SIN_GENERATION, 0L);
        final boolean wanted = booleanExtension(section, WANTED);
        final boolean exiled = booleanExtension(section, EXILED);
        final boolean darkPact = booleanExtension(section, DARK_PACT);
        final Optional<FactionType> membership;
        if (section.membershipId().isBlank()) membership = Optional.empty();
        else {
            final FactionType parsed = FactionType.fromInput(section.membershipId());
            if (parsed == null) throw new IllegalStateException(
                    "unknown faction in sinner profile: " + section.membershipId());
            membership = Optional.of(parsed);
        }
        return new SinState(count, wanted, exiled, darkPact, generation, membership);
    }

    private static boolean booleanExtension(final FactionSection section, final String key) {
        final Object raw = section.extensions().get(key);
        if (raw == null) return false;
        if (raw instanceof Boolean value) return value;
        throw new IllegalStateException("invalid boolean faction extension: " + key);
    }

    static FactionSection withSin(final FactionSection current, final int count,
                                  final boolean wanted, final boolean exiled,
                                  final boolean darkPact, final long generation) {
        final LinkedHashMap<String, Long> reputation = new LinkedHashMap<>(current.reputation());
        if (count == 0) reputation.remove(SIN_COUNT);
        else reputation.put(SIN_COUNT, (long) count);
        if (generation == 0L) reputation.remove(SIN_GENERATION);
        else reputation.put(SIN_GENERATION, generation);
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        putBoolean(extensions, WANTED, wanted);
        putBoolean(extensions, EXILED, exiled);
        putBoolean(extensions, DARK_PACT, darkPact);
        return new FactionSection(current.membershipId(), current.lastChosenFaction(),
                current.everChosen(), current.joinedAt(), current.leftAt(), current.history(),
                reputation, current.cooldowns(), extensions);
    }

    private static void putBoolean(final Map<String, Object> target,
                                   final String key, final boolean value) {
        if (value) target.put(key, true); else target.remove(key);
    }

    private static LinkedHashSet<String> receipts(final FactionSection section) {
        final Object raw = section.extensions().get(RECEIPTS);
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        if (raw == null) return result;
        if (!(raw instanceof Iterable<?> iterable)) {
            throw new IllegalStateException("invalid sin receipt list");
        }
        for (final Object value : iterable) {
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalStateException("invalid sin receipt value");
            }
            result.add(text);
        }
        return result;
    }

    private static FactionSection withReceipts(final FactionSection current,
                                               final Set<String> receipts) {
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        if (receipts.isEmpty()) extensions.remove(RECEIPTS);
        else extensions.put(RECEIPTS, List.copyOf(receipts));
        return new FactionSection(current.membershipId(), current.lastChosenFaction(),
                current.everChosen(), current.joinedAt(), current.leftAt(), current.history(),
                current.reputation(), current.cooldowns(), extensions);
    }

    private static String receipt(final String operationId) {
        final String value = Objects.requireNonNull(operationId, "operationId").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("blank sin operation id");
        if (value.length() > 160) throw new IllegalArgumentException("sin operation id too long");
        return value;
    }
}
