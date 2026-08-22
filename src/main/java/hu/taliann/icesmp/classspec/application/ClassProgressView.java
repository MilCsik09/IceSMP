package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.SealReason;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Read-only Profile v2 projection shared by class progression surfaces. */
public record ClassProgressView(
        boolean gameplayUsable,
        Optional<String> primaryClassId,
        int classLevel,
        int classExperience,
        int requiredSpecializationLevel,
        int secondSlotUnlockLevel,
        Optional<LoadoutSlot> activeSlot,
        boolean secondSlotUnlocked,
        Map<LoadoutSlot, LoadoutView> loadouts,
        Optional<String> unavailableReason) {

    public ClassProgressView {
        primaryClassId = safe(primaryClassId);
        activeSlot = safe(activeSlot);
        unavailableReason = safe(unavailableReason);
        if (classLevel < 0 || classExperience < 0) {
            throw new IllegalArgumentException("Class progression cannot be negative");
        }
        if (requiredSpecializationLevel < 1 || secondSlotUnlockLevel < requiredSpecializationLevel) {
            throw new IllegalArgumentException("Invalid specialization level thresholds");
        }
        Objects.requireNonNull(loadouts, "loadouts");
        final EnumMap<LoadoutSlot, LoadoutView> copy = new EnumMap<>(LoadoutSlot.class);
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            copy.put(slot, Objects.requireNonNull(loadouts.getOrDefault(slot, LoadoutView.empty()),
                    "loadout view"));
        }
        loadouts = Collections.unmodifiableMap(copy);
    }

    public static ClassProgressView project(final ProfileDiagnostic diagnostic,
                                            final Optional<ClassSpecSection> durable,
                                            final int requiredSpecializationLevel,
                                            final int secondSlotUnlockLevel,
                                            final long masteryExperiencePerRank) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        final EnumMap<LoadoutSlot, LoadoutView> slots = new EnumMap<>(LoadoutSlot.class);
        final ClassSpecSection profile = durable == null ? null : durable.orElse(null);
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            if (profile != null) {
                slots.put(slot, LoadoutView.from(profile.loadout(slot), masteryExperiencePerRank));
                continue;
            }
            final ProfileDiagnostic.SlotDiagnostic fallback = diagnostic.slots().get(slot);
            slots.put(slot, fallback == null ? LoadoutView.empty()
                    : LoadoutView.from(fallback, masteryExperiencePerRank));
        }
        return new ClassProgressView(diagnostic.gameplayUsable(), diagnostic.primaryClassId(),
                diagnostic.classLevel(), diagnostic.classExperience(), requiredSpecializationLevel,
                secondSlotUnlockLevel, diagnostic.activeSlot(), diagnostic.secondSpecUnlocked(), slots,
                diagnostic.sessionBlockReason().or(() -> diagnostic.quarantineReason())
                        .or(() -> diagnostic.reviewReason()));
    }

    public LoadoutView loadout(final LoadoutSlot slot) {
        return loadouts.get(Objects.requireNonNull(slot, "slot"));
    }

    public record LoadoutView(
            Optional<String> specializationId,
            LoadoutStatus status,
            Optional<SealReason> sealReason,
            Map<String, String> doctrineChoices,
            int masteryRank,
            long masteryExperience,
            long masteryExperiencePerRank,
            CapstoneStatus capstoneStatus) {

        public LoadoutView {
            specializationId = safe(specializationId);
            Objects.requireNonNull(status, "status");
            sealReason = safe(sealReason);
            doctrineChoices = Map.copyOf(Objects.requireNonNull(doctrineChoices, "doctrineChoices"));
            Objects.requireNonNull(capstoneStatus, "capstoneStatus");
            if (masteryRank < 0 || masteryExperience < 0L || masteryExperiencePerRank < 1L) {
                throw new IllegalArgumentException("Invalid mastery projection");
            }
        }

        public static LoadoutView empty() {
            return new LoadoutView(Optional.empty(), LoadoutStatus.EMPTY, Optional.empty(), Map.of(),
                    0, 0L, 1L, CapstoneStatus.LOCKED);
        }

        private static LoadoutView from(final ClassLoadout loadout, final long experiencePerRank) {
            return new LoadoutView(optionalText(loadout.specializationId()), loadout.status(),
                    Optional.ofNullable(loadout.sealReason()), loadout.doctrineChoices(),
                    loadout.mastery().rank(), loadout.mastery().experience(),
                    Math.max(1L, experiencePerRank), loadout.capstoneStatus());
        }

        private static LoadoutView from(final ProfileDiagnostic.SlotDiagnostic diagnostic,
                                        final long experiencePerRank) {
            return new LoadoutView(diagnostic.specializationId(), diagnostic.status(),
                    diagnostic.sealReason(), Map.of(), diagnostic.masteryRank(), diagnostic.masteryXp(),
                    Math.max(1L, experiencePerRank), CapstoneStatus.LOCKED);
        }

        public long experienceIntoRank() {
            if (masteryRank >= 10) return masteryExperiencePerRank;
            return masteryExperience % masteryExperiencePerRank;
        }
    }

    private static <T> Optional<T> safe(final Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    private static Optional<String> optionalText(final String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
