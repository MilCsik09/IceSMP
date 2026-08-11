package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.OnboardingSection;
import hu.taliann.icesmp.playerprofile.domain.section.ProfessionSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Typed memory unlock and profession specialization authority. */
public final class PlayerProfileSpecializationProgressStore {
    private static final String ACTIVE_PROFESSION_SPEC = "active";

    public boolean memoryUnlocked(final UUID playerId) {
        return PlayerProfileAuthority.current().requireSection(playerId,
                ProfileSectionId.ONBOARDING, OnboardingSection.class).memorySpecUnlocked();
    }

    public CompletionStage<Boolean> grantMemoryUnlock(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.ONBOARDING, OnboardingSection.class, current -> {
                    if (current.memorySpecUnlocked()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            new OnboardingSection(current.steps(), current.claimedRewards(),
                                    current.codexEntries(), true, current.extensions()), true);
                });
    }

    public Optional<ProfessionSpecializationType> professionSpecialization(final UUID playerId) {
        final String raw = PlayerProfileAuthority.current().requireSection(playerId,
                ProfileSectionId.PROFESSIONS, ProfessionSection.class)
                .specializations().get(ACTIVE_PROFESSION_SPEC);
        if (raw == null || raw.isBlank()) return Optional.empty();
        final ProfessionSpecializationType parsed = ProfessionSpecializationType.fromId(raw);
        if (parsed == null) throw new IllegalStateException(
                "unknown PlayerProfile profession specialization: " + raw);
        return Optional.of(parsed);
    }

    public CompletionStage<Boolean> selectProfessionSpecialization(
            final UUID playerId, final ProfessionSpecializationType specialization) {
        Objects.requireNonNull(specialization, "specialization");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.PROFESSIONS, ProfessionSection.class, current -> {
                    if (current.specializations().containsKey(ACTIVE_PROFESSION_SPEC)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, String> specializations =
                            new LinkedHashMap<>(current.specializations());
                    specializations.put(ACTIVE_PROFESSION_SPEC,
                            specialization.getId().toLowerCase(Locale.ROOT));
                    final ProfessionSection next = new ProfessionSection(current.experience(),
                            current.levels(), specializations, current.recipes(),
                            current.weeklyProgress(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public CompletionStage<Boolean> resetProfessionSpecialization(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.PROFESSIONS, ProfessionSection.class, current -> {
                    if (!current.specializations().containsKey(ACTIVE_PROFESSION_SPEC)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, String> specializations =
                            new LinkedHashMap<>(current.specializations());
                    specializations.remove(ACTIVE_PROFESSION_SPEC);
                    final ProfessionSection next = new ProfessionSection(current.experience(),
                            current.levels(), specializations, current.recipes(),
                            current.weeklyProgress(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }
}
