package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Typed sinner, sin-count, bounty generation and DARK-pact authority. */
public final class PlayerProfileSinStore {

    private static final String SIN_COUNT = "sin.count";
    private static final String SIN_GENERATION = "sin.generation";
    private static final String SINNER = "sin.sinner";
    private static final String DARK_PACT = "sin.dark-pact";
    private static final String RECEIPTS = "sin.operation-receipts";
    private static final int MAX_RECEIPTS = 128;

    public record SinState(int count, boolean sinner, boolean darkPact,
                           long generation, Optional<FactionType> membership) {
        public SinState {
            if (count < 0 || generation < 0L) {
                throw new IllegalArgumentException("invalid sin state");
            }
            membership = membership == null ? Optional.empty() : membership;
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
        public AddOnceResult { Objects.requireNonNull(result, "result"); }
    }

    public SinState read(final UUID playerId) {
        return decode(PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.FACTION, FactionSection.class));
    }

    public CompletionStage<AddResult> add(final UUID playerId, final int amount,
                                          final int exileThreshold) {
        if (amount <= 0) throw new IllegalArgumentException("sin amount must be positive");
        if (exileThreshold < 0) throw new IllegalArgumentException("negative exile threshold");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final AddResult result = addToSection(current, amount, exileThreshold);
                    return PlayerProfileService.ConditionalMutation.changed(
                            sectionFor(current, result), result);
                });
    }

    /** Operation-ID protected sin mutation for restart-safe outbox replay. */
    public CompletionStage<AddOnceResult> addOnce(final UUID playerId, final int amount,
                                                  final int exileThreshold,
                                                  final String operationId) {
        if (amount <= 0) throw new IllegalArgumentException("sin amount must be positive");
        if (exileThreshold < 0) throw new IllegalArgumentException("negative exile threshold");
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
                    final AddResult result = addToSection(current, amount, exileThreshold);
                    final FactionSection mutated = sectionFor(current, result);
                    while (receipts.size() >= MAX_RECEIPTS) receipts.remove(receipts.iterator().next());
                    receipts.add(receipt);
                    final FactionSection next = withReceipts(mutated, receipts);
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new AddOnceResult(result, true));
                });
    }

    private static AddResult addToSection(final FactionSection current,
                                          final int amount,
                                          final int exileThreshold) {
        final SinState before = decode(current);
        final int count = Math.addExact(before.count(), amount);
        final long generation = before.count() == 0
                ? Math.addExact(before.generation(), 1L) : before.generation();
        final Optional<FactionType> previous = before.membership();
        final boolean exiled = exileThreshold > 0 && count >= exileThreshold
                && before.membership().orElse(null) != FactionType.DARK;
        FactionSection next = withSin(current, count, true,
                before.darkPact() || exiled, generation);
        if (exiled) next = withMembership(next, FactionType.DARK,
                System.currentTimeMillis());
        return new AddResult(decode(next), exiled, previous);
    }

    private static FactionSection sectionFor(final FactionSection current,
                                             final AddResult result) {
        FactionSection next = withSin(current, result.state().count(),
                result.state().sinner(), result.state().darkPact(),
                result.state().generation());
        if (result.exiled()) next = withMembership(next, FactionType.DARK,
                System.currentTimeMillis());
        return next;
    }

    public CompletionStage<SinState> markSinner(final UUID playerId) {
        return mutate(playerId, state -> new Values(state.count(), true,
                state.darkPact(), state.generation()));
    }

    public CompletionStage<SinState> reduce(final UUID playerId, final int amount) {
        if (amount < 0) throw new IllegalArgumentException("negative sin reduction");
        return mutate(playerId, state -> new Values(
                Math.max(0, state.count() - amount), state.sinner(),
                state.darkPact(), state.generation()));
    }

    public CompletionStage<SinState> resetCount(final UUID playerId) {
        return mutate(playerId, state -> new Values(0, state.sinner(),
                state.darkPact(), state.generation()));
    }

    public CompletionStage<SinState> sealDarkPact(final UUID playerId) {
        return mutate(playerId, state -> new Values(state.count(), true,
                true, state.generation()));
    }

    public CompletionStage<SinState> clearDarkPactForFactionOverride(final UUID playerId) {
        return mutate(playerId, state -> new Values(state.count(), state.sinner(),
                false, state.generation()));
    }

    public CompletionStage<SinState> breakDarkPact(final UUID playerId) {
        return mutate(playerId, state -> new Values(0, false, false, state.generation()));
    }

    public CompletionStage<Boolean> clearSinner(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final SinState before = decode(current);
                    if (before.darkPact()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    if (!before.sinner() && before.count() == 0) {
                        return PlayerProfileService.ConditionalMutation.unchanged(true);
                    }
                    final FactionSection next = withSin(current, 0, false,
                            false, before.generation());
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
                    final FactionSection next = withSin(current, values.count(),
                            values.sinner(), values.darkPact(), values.generation());
                    final SinState after = decode(next);
                    if (after.equals(before)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(before);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(next, after);
                });
    }

    private record Values(int count, boolean sinner, boolean darkPact, long generation) {
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
        final boolean sinner = booleanExtension(section, SINNER);
        final boolean darkPact = booleanExtension(section, DARK_PACT);
        final Optional<FactionType> membership;
        if (section.membershipId().isBlank()) membership = Optional.empty();
        else {
            final FactionType parsed = FactionType.fromInput(section.membershipId());
            if (parsed == null) throw new IllegalStateException(
                    "unknown faction in sinner profile: " + section.membershipId());
            membership = Optional.of(parsed);
        }
        return new SinState(count, sinner, darkPact, generation, membership);
    }

    private static boolean booleanExtension(final FactionSection section, final String key) {
        final Object raw = section.extensions().get(key);
        if (raw == null) return false;
        if (raw instanceof Boolean value) return value;
        throw new IllegalStateException("invalid boolean faction extension: " + key);
    }

    static FactionSection withSin(final FactionSection current, final int count,
                                  final boolean sinner, final boolean darkPact,
                                  final long generation) {
        final LinkedHashMap<String, Long> reputation = new LinkedHashMap<>(current.reputation());
        if (count == 0) reputation.remove(SIN_COUNT);
        else reputation.put(SIN_COUNT, (long) count);
        if (generation == 0L) reputation.remove(SIN_GENERATION);
        else reputation.put(SIN_GENERATION, generation);
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        putBoolean(extensions, SINNER, sinner);
        putBoolean(extensions, DARK_PACT, darkPact);
        return new FactionSection(current.membershipId(), current.lastChosenFaction(),
                current.everChosen(), current.joinedAt(), current.leftAt(), current.history(),
                reputation, current.cooldowns(), extensions);
    }

    private static void putBoolean(final Map<String, Object> target,
                                   final String key, final boolean value) {
        if (value) target.put(key, true); else target.remove(key);
    }

    private static FactionSection withMembership(final FactionSection current,
                                                 final FactionType target,
                                                 final long now) {
        final List<String> history = new ArrayList<>(current.history());
        if (history.isEmpty() || !history.get(history.size() - 1).equalsIgnoreCase(target.name())) {
            history.add(target.name());
            if (history.size() > 128) history.remove(0);
        }
        return new FactionSection(target.name(), target.name(), true, now,
                current.membershipId().isBlank() ? current.leftAt() : now,
                history, current.reputation(), current.cooldowns(), current.extensions());
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
        if (operationId == null || operationId.isBlank() || operationId.trim().length() > 128) {
            throw new IllegalArgumentException("invalid sin operation id");
        }
        return operationId.trim();
    }
}
