package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensions;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.OnboardingSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Durable intro/onboarding authority; Bukkit gamemode remains runtime-only. */
public final class PlayerProfileIntroStore {
    private static final String INTRO_STEP = "intro";
    private static final String SEEN = "seen";
    private static final String ACTIVE_KEY = "intro.cinematic.active";
    private static final String PREVIOUS_MODE_KEY = "intro.cinematic.previous-gamemode";

    public CompletionStage<Boolean> hasSeen(final UUID playerId) {
        return PlayerProfileAuthority.current().repository().loadSnapshot(playerId)
                .thenApply(snapshot -> SEEN.equals(snapshot.onboarding().value()
                        .steps().get(INTRO_STEP)));
    }

    public CompletionStage<Boolean> markSeen(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ONBOARDING, OnboardingSection.class, current -> {
                    if (SEEN.equals(current.steps().get(INTRO_STEP))) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, String> steps = new LinkedHashMap<>(current.steps());
                    steps.put(INTRO_STEP, SEEN);
                    final OnboardingSection next = new OnboardingSection(steps,
                            current.claimedRewards(), current.codexEntries(),
                            current.memorySpecUnlocked(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public CompletionStage<CinematicState> cinematicState(final UUID playerId) {
        return PlayerProfileAuthority.current().repository().loadSnapshot(playerId)
                .thenApply(snapshot -> state(snapshot.onboarding().value()));
    }

    public CompletionStage<Boolean> beginCinematic(final UUID playerId,
                                                    final String previousGamemode) {
        if (previousGamemode == null || previousGamemode.isBlank()) {
            throw new IllegalArgumentException("previous gamemode cannot be blank");
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ONBOARDING, OnboardingSection.class, current -> {
                    if (state(current).active()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    OnboardingSection next = PlayerProfileSectionExtensions.put(
                            current, ACTIVE_KEY, true);
                    next = PlayerProfileSectionExtensions.put(next, PREVIOUS_MODE_KEY,
                            previousGamemode.trim().toUpperCase(java.util.Locale.ROOT));
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public CompletionStage<Boolean> completeCinematic(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ONBOARDING, OnboardingSection.class, current -> {
                    if (!state(current).active()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
                    extensions.remove(ACTIVE_KEY);
                    extensions.remove(PREVIOUS_MODE_KEY);
                    final OnboardingSection next = new OnboardingSection(current.steps(),
                            current.claimedRewards(), current.codexEntries(),
                            current.memorySpecUnlocked(), extensions);
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    private static CinematicState state(final OnboardingSection section) {
        final Object activeRaw = section.extensions().get(ACTIVE_KEY);
        if (activeRaw != null && !(activeRaw instanceof Boolean)) {
            throw new IllegalStateException("Invalid intro cinematic active marker");
        }
        final boolean active = Boolean.TRUE.equals(activeRaw);
        final Object modeRaw = section.extensions().get(PREVIOUS_MODE_KEY);
        if (modeRaw != null && !(modeRaw instanceof String)) {
            throw new IllegalStateException("Invalid intro cinematic gamemode marker");
        }
        final String mode = modeRaw instanceof String value ? value : "SURVIVAL";
        return new CinematicState(active, mode);
    }

    public record CinematicState(boolean active, String previousGamemode) {
        public CinematicState {
            previousGamemode = previousGamemode == null || previousGamemode.isBlank()
                    ? "SURVIVAL" : previousGamemode;
        }
    }
}
