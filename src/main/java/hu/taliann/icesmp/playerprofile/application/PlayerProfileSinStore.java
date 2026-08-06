package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Typed sinner, sin-count and DARK-pact authority inside the faction section. */
public final class PlayerProfileSinStore {

    private static final String SIN_COUNT = "sin.count";
    private static final String SINNER = "sin.sinner";
    private static final String DARK_PACT = "sin.dark-pact";

    public record SinState(int count, boolean sinner, boolean darkPact,
                           Optional<FactionType> membership) {
        public SinState {
            if (count < 0) throw new IllegalArgumentException("negative sin count");
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
                    final SinState before = decode(current);
                    final int count = Math.addExact(before.count(), amount);
                    final Optional<FactionType> previous = before.membership();
                    final boolean exiled = exileThreshold > 0 && count >= exileThreshold
                            && before.membership().orElse(null) != FactionType.DARK;
                    FactionSection next = withSin(current, count, true,
                            before.darkPact() || exiled);
                    if (exiled) next = withMembership(next, FactionType.DARK,
                            System.currentTimeMillis());
                    final SinState after = decode(next);
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new AddResult(after, exiled, previous));
                });
    }

    public CompletionStage<SinState> markSinner(final UUID playerId) {
        return mutate(playerId, state -> new Values(state.count(), true, state.darkPact()));
    }

    public CompletionStage<SinState> reduce(final UUID playerId, final int amount) {
        if (amount < 0) throw new IllegalArgumentException("negative sin reduction");
        return mutate(playerId, state -> new Values(
                Math.max(0, state.count() - amount), state.sinner(), state.darkPact()));
    }

    public CompletionStage<SinState> resetCount(final UUID playerId) {
        return mutate(playerId, state -> new Values(0, state.sinner(), state.darkPact()));
    }

    public CompletionStage<SinState> sealDarkPact(final UUID playerId) {
        return mutate(playerId, state -> new Values(state.count(), true, true));
    }

    /** Membership override removes only the pact; the sinner mark and count remain auditable. */
    public CompletionStage<SinState> clearDarkPactForFactionOverride(final UUID playerId) {
        return mutate(playerId, state -> new Values(state.count(), state.sinner(), false));
    }

    /** Penance is the only operation that clears pact, sinner mark and count together. */
    public CompletionStage<SinState> breakDarkPact(final UUID playerId) {
        return mutate(playerId, state -> new Values(0, false, false));
    }

    /** Returns false without mutation while a DARK pact is active. */
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
                    final FactionSection next = withSin(current, 0, false, false);
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
                            values.sinner(), values.darkPact());
                    final SinState after = decode(next);
                    if (after.equals(before)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(before);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(next, after);
                });
    }

    private record Values(int count, boolean sinner, boolean darkPact) {
        private Values {
            if (count < 0) throw new IllegalArgumentException("negative sin count");
        }
    }

    private static SinState decode(final FactionSection section) {
        final long rawCount = section.reputation().getOrDefault(SIN_COUNT, 0L);
        final int count = Math.toIntExact(rawCount);
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
        return new SinState(count, sinner, darkPact, membership);
    }

    private static boolean booleanExtension(final FactionSection section, final String key) {
        final Object raw = section.extensions().get(key);
        if (raw == null) return false;
        if (raw instanceof Boolean value) return value;
        throw new IllegalStateException("invalid boolean faction extension: " + key);
    }

    private static FactionSection withSin(final FactionSection current, final int count,
                                          final boolean sinner, final boolean darkPact) {
        final LinkedHashMap<String, Long> reputation = new LinkedHashMap<>(current.reputation());
        if (count == 0) reputation.remove(SIN_COUNT);
        else reputation.put(SIN_COUNT, (long) count);
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        putBoolean(extensions, SINNER, sinner);
        putBoolean(extensions, DARK_PACT, darkPact);
        return new FactionSection(current.membershipId(), current.lastChosenFaction(),
                current.everChosen(), current.joinedAt(), current.leftAt(), current.history(),
                reputation, current.cooldowns(), extensions);
    }

    private static void putBoolean(final Map<String, Object> target,
                                   final String key, final boolean value) {
        if (value) target.put(key, true);
        else target.remove(key);
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
}
