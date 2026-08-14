package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Durable whisperer flag and suspicion score inside the faction section. */
public final class PlayerProfileWhisperStore {

    private static final String WHISPERER = "whisper.whisperer";
    private static final String SUSPICION = "whisper.suspicion-milli";
    private static final long SCALE = 1_000L;

    public record State(boolean whisperer, long suspicionMilli) {
        public State {
            if (suspicionMilli < 0L) throw new IllegalArgumentException("negative suspicion");
        }
        public double suspicion() { return suspicionMilli / (double) SCALE; }
    }

    public record Adjustment(State state, boolean exposed) {
        public Adjustment { Objects.requireNonNull(state, "state"); }
    }

    public State read(final UUID playerId) {
        return decode(PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.FACTION, FactionSection.class));
    }

    public CompletionStage<State> makeWhisperer(final UUID playerId) {
        return mutate(playerId, current -> new State(true, 0L));
    }

    public CompletionStage<State> clear(final UUID playerId) {
        return mutate(playerId, current -> new State(false, 0L));
    }

    /** Adds or removes suspicion and atomically clears the hidden status at the threshold. */
    public CompletionStage<Adjustment> adjust(final UUID playerId,
                                              final double delta,
                                              final double threshold) {
        if (!Double.isFinite(delta) || !Double.isFinite(threshold) || threshold <= 0.0D) {
            throw new IllegalArgumentException("invalid whisper suspicion adjustment");
        }
        final long deltaMilli = BigDecimal.valueOf(delta).movePointRight(3)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        final long thresholdMilli = BigDecimal.valueOf(threshold).movePointRight(3)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final State before = decode(current);
                    if (!before.whisperer()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Adjustment(before, false));
                    }
                    final long nextScore = Math.max(0L,
                            Math.addExact(before.suspicionMilli(), deltaMilli));
                    final boolean exposed = nextScore >= thresholdMilli;
                    final State after = exposed ? new State(false, 0L)
                            : new State(true, nextScore);
                    if (after.equals(before)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Adjustment(before, false));
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, after), new Adjustment(after, exposed));
                });
    }

    private CompletionStage<State> mutate(
            final UUID playerId,
            final java.util.function.Function<State, State> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final State before = decode(current);
                    final State after = Objects.requireNonNull(mutation.apply(before), "whisper state");
                    if (after.equals(before)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(before);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, after), after);
                });
    }

    private static State decode(final FactionSection section) {
        final Object rawFlag = section.extensions().get(WHISPERER);
        final boolean whisperer;
        if (rawFlag == null) whisperer = false;
        else if (rawFlag instanceof Boolean value) whisperer = value;
        else throw new IllegalStateException("invalid whisperer flag type");
        final long suspicion = section.reputation().getOrDefault(SUSPICION, 0L);
        if (suspicion < 0L) throw new IllegalStateException("negative whisper suspicion");
        if (!whisperer && suspicion != 0L) {
            throw new IllegalStateException("non-whisperer profile retains suspicion");
        }
        return new State(whisperer, suspicion);
    }

    private static FactionSection withState(final FactionSection current, final State state) {
        final LinkedHashMap<String, Long> reputation = new LinkedHashMap<>(current.reputation());
        if (state.suspicionMilli() == 0L) reputation.remove(SUSPICION);
        else reputation.put(SUSPICION, state.suspicionMilli());
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        if (state.whisperer()) extensions.put(WHISPERER, true);
        else extensions.remove(WHISPERER);
        return new FactionSection(current.membershipId(), current.lastChosenFaction(),
                current.everChosen(), current.joinedAt(), current.leftAt(), current.history(),
                reputation, current.cooldowns(), extensions);
    }
}
