package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;
import hu.taliann.icesmp.classspec.domain.SealReason;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable projection used by {@code /spec info} and mutation responses. */
public record ProfileDiagnostic(
        boolean authorityAvailable,
        boolean loaded,
        int schemaVersion,
        long revision,
        ProfileStatus profileStatus,
        Optional<String> primaryClassId,
        int classLevel,
        int classExperience,
        Optional<LoadoutSlot> activeSlot,
        boolean secondSpecUnlocked,
        Map<LoadoutSlot, SlotDiagnostic> slots,
        Optional<String> reviewReason,
        Optional<String> quarantineReason,
        Optional<String> sessionBlockReason) {

    public ProfileDiagnostic {
        primaryClassId = safeOptional(primaryClassId);
        activeSlot = safeOptional(activeSlot);
        reviewReason = safeOptional(reviewReason);
        quarantineReason = safeOptional(quarantineReason);
        sessionBlockReason = safeOptional(sessionBlockReason);
        Objects.requireNonNull(slots, "slots");
        final EnumMap<LoadoutSlot, SlotDiagnostic> copy = new EnumMap<>(LoadoutSlot.class);
        slots.forEach((slot, diagnostic) -> copy.put(
                Objects.requireNonNull(slot, "slot"),
                Objects.requireNonNull(diagnostic, "slot diagnostic")));
        slots = Collections.unmodifiableMap(copy);
        if (loaded && profileStatus == null) {
            throw new IllegalArgumentException("A loaded diagnostic requires profile status");
        }
        if (classLevel < 0 || classExperience < 0) {
            throw new IllegalArgumentException("Class level/experience diagnostics cannot be negative");
        }
    }

    public static ProfileDiagnostic loading() {
        return unavailable("profile loading");
    }

    public static ProfileDiagnostic quarantined(final String reason) {
        return new ProfileDiagnostic(true, true, 2, -1L, ProfileStatus.QUARANTINED,
                Optional.empty(), 0, 0, Optional.empty(), false, Map.of(), Optional.empty(),
                optionalText(reason), optionalText(reason));
    }

    public static ProfileDiagnostic unavailable(final String reason) {
        return new ProfileDiagnostic(true, false, 0, -1L, null,
                Optional.empty(), 0, 0, Optional.empty(), false, Map.of(), Optional.empty(),
                Optional.empty(), optionalText(reason));
    }

    public boolean gameplayUsable() {
        return authorityAvailable && loaded && profileStatus == ProfileStatus.READY
                && sessionBlockReason.isEmpty();
    }

    public record SlotDiagnostic(
            Optional<String> specializationId,
            LoadoutStatus status,
            Optional<SealReason> sealReason,
            int masteryRank,
            long masteryXp) {

        public SlotDiagnostic {
            specializationId = safeOptional(specializationId);
            Objects.requireNonNull(status, "status");
            sealReason = safeOptional(sealReason);
            if (masteryRank < 0 || masteryRank > 10) {
                throw new IllegalArgumentException("masteryRank must be between 0 and 10");
            }
            if (masteryXp < 0L) {
                throw new IllegalArgumentException("masteryXp cannot be negative");
            }
        }
    }

    private static <T> Optional<T> safeOptional(final Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    private static Optional<String> optionalText(final String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
