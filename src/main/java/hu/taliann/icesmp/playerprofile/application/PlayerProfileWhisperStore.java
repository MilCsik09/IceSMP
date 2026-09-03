package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Durable whisperer flag and public exposure stage inside the faction section. */
public final class PlayerProfileWhisperStore {

    private static final String WHISPERER = "whisper.whisperer";
    private static final String EXPOSURE_STAGE = "whisper.exposure-stage";

    public enum Stage {
        CLEAN("tiszta"),
        OBSERVED("megfigyelt"),
        SUSPECTED("gyanúsított"),
        EXPOSED("leleplezett");

        private final String displayName;

        Stage(final String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public Stage advance() {
            return values()[Math.min(values().length - 1, ordinal() + 1)];
        }

        public Stage cover() {
            return values()[Math.max(0, ordinal() - 1)];
        }
    }

    public record State(boolean whisperer, Stage stage) {
        public State {
            Objects.requireNonNull(stage, "stage");
        }
    }

    public record Adjustment(State state, boolean exposed) {
        public Adjustment {
            Objects.requireNonNull(state, "state");
        }
    }

    public record CoverResult(State state, boolean applied) {
        public CoverResult {
            Objects.requireNonNull(state, "state");
        }
    }

    public State read(final UUID playerId) {
        return decode(PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.FACTION, FactionSection.class));
    }

    public CompletionStage<State> makeWhisperer(final UUID playerId) {
        return mutate(playerId, current -> new State(true, Stage.CLEAN));
    }

    public CompletionStage<State> clear(final UUID playerId) {
        return mutate(playerId, current -> new State(false, Stage.CLEAN));
    }

    public CompletionStage<State> forceExpose(final UUID playerId) {
        return mutate(playerId, current -> new State(false, Stage.EXPOSED));
    }

    /** Advances exactly one fixed stage and atomically clears the role at EXPOSED. */
    public CompletionStage<Adjustment> advance(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final State before = decode(current);
                    if (!before.whisperer()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Adjustment(before, false));
                    }
                    final Stage nextStage = before.stage().advance();
                    final boolean exposed = nextStage == Stage.EXPOSED;
                    final State after = new State(!exposed, nextStage);
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, after), new Adjustment(after, exposed));
                });
    }

    /** Removes one stage of pressure. Exposure is final and never restores the role. */
    public CompletionStage<CoverResult> applyCover(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final State before = decode(current);
                    if (!before.whisperer() || before.stage() == Stage.CLEAN) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new CoverResult(before, false));
                    }
                    final State after = new State(true, before.stage().cover());
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, after), new CoverResult(after, true));
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
        final long rawStage = section.reputation().getOrDefault(EXPOSURE_STAGE, 0L);
        if (rawStage < 0L || rawStage >= Stage.values().length) {
            throw new IllegalStateException("invalid whisper exposure stage");
        }
        final Stage stage = Stage.values()[(int) rawStage];
        if (!whisperer && stage != Stage.CLEAN && stage != Stage.EXPOSED) {
            throw new IllegalStateException("inactive whisperer retains intermediate exposure stage");
        }
        return new State(whisperer, stage);
    }

    private static FactionSection withState(final FactionSection current, final State state) {
        final LinkedHashMap<String, Long> reputation = new LinkedHashMap<>(current.reputation());
        if (state.stage() == Stage.CLEAN) reputation.remove(EXPOSURE_STAGE);
        else reputation.put(EXPOSURE_STAGE, (long) state.stage().ordinal());
        final LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(current.extensions());
        if (state.whisperer()) extensions.put(WHISPERER, true);
        else extensions.remove(WHISPERER);
        return new FactionSection(current.membershipId(), current.lastChosenFaction(),
                current.everChosen(), current.joinedAt(), current.leftAt(), current.history(),
                reputation, current.cooldowns(), extensions);
    }
}
