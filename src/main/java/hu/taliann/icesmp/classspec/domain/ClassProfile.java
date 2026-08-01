package hu.taliann.icesmp.classspec.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable, fully validated Profile v2 aggregate. */
public final class ClassProfile {

    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_CLASS_LEVEL = 50;

    private final int schemaVersion;
    private final long revision;
    private final ProfileStatus status;
    private final String primaryClassId;
    private final int classLevel;
    private final LoadoutSlot activeSlot;
    private final boolean secondSpecUnlocked;
    private final List<ClassLoadout> loadouts;
    private final MigrationState migrationState;
    private final ProfileDiagnostics diagnostics;

    private ClassProfile(final Builder builder) {
        this.schemaVersion = builder.schemaVersion;
        this.revision = builder.revision;
        this.status = Objects.requireNonNull(builder.status, "status");
        this.primaryClassId = ClassSpecCatalog.normalize(builder.primaryClassId);
        this.classLevel = builder.classLevel;
        this.activeSlot = builder.activeSlot;
        this.secondSpecUnlocked = builder.secondSpecUnlocked;
        this.loadouts = List.copyOf(builder.loadouts);
        this.migrationState = Objects.requireNonNull(builder.migrationState, "migrationState");
        this.diagnostics = Objects.requireNonNull(builder.diagnostics, "diagnostics");
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ClassProfile empty(final long revision) {
        return builder().revision(revision).build();
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public long revision() {
        return revision;
    }

    public ProfileStatus status() {
        return status;
    }

    public String primaryClassId() {
        return primaryClassId;
    }

    public int classLevel() {
        return classLevel;
    }

    public LoadoutSlot activeSlot() {
        return activeSlot;
    }

    public boolean secondSpecUnlocked() {
        return secondSpecUnlocked;
    }

    public List<ClassLoadout> loadouts() {
        return loadouts;
    }

    public ClassLoadout loadout(final LoadoutSlot slot) {
        return loadouts.get(Objects.requireNonNull(slot, "slot").index());
    }

    public MigrationState migrationState() {
        return migrationState;
    }

    public ProfileDiagnostics diagnostics() {
        return diagnostics;
    }

    public boolean isGameplayUsable() {
        return status == ProfileStatus.READY && !diagnostics.sessionBlocked();
    }

    /** Opens the normal mutation boundary; review/quarantine/session-block state cannot use it. */
    public Builder toBuilder() {
        requireNormalMutationAllowed();
        return copyBuilder();
    }

    /** Explicit recovery boundary for admin recovery tooling only. */
    public Builder toRecoveryBuilder() {
        return copyBuilder();
    }

    /** Normal class reset. Review/quarantine evidence is protected by the mutation boundary. */
    public ClassProfile withoutClass() {
        requireNormalMutationAllowed();
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("Profile revision exhausted");
        }
        return builder().revision(revision + 1L)
                .migrationState(migrationState)
                .diagnostics(diagnostics)
                .build();
    }

    private Builder copyBuilder() {
        return new Builder()
                .schemaVersion(schemaVersion)
                .revision(revision)
                .status(status)
                .primaryClassId(primaryClassId)
                .classLevel(classLevel)
                .activeSlot(activeSlot)
                .secondSpecUnlocked(secondSpecUnlocked)
                .loadout(LoadoutSlot.FIRST, loadout(LoadoutSlot.FIRST))
                .loadout(LoadoutSlot.SECOND, loadout(LoadoutSlot.SECOND))
                .migrationState(migrationState)
                .diagnostics(diagnostics);
    }

    private void requireNormalMutationAllowed() {
        if (status != ProfileStatus.READY || diagnostics.sessionBlocked()) {
            throw new IllegalStateException("Profile requires explicit recovery: " + status);
        }
    }

    private void validate() {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Profile v2 requires schema version " + SCHEMA_VERSION);
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Profile revision must be non-negative");
        }
        if (loadouts.size() != 2 || loadouts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Profile v2 requires exactly two loadout slots");
        }
        if (primaryClassId.isEmpty()) {
            if (classLevel != 0 || activeSlot != null || secondSpecUnlocked
                    || loadouts.stream().anyMatch(loadout -> loadout.status() != LoadoutStatus.EMPTY)) {
                throw new IllegalArgumentException("A classless profile may not retain active loadouts");
            }
        } else {
            if (!ClassSpecCatalog.isKnownClass(primaryClassId)) {
                throw new IllegalArgumentException("Unknown primary class: " + primaryClassId);
            }
            if (classLevel < 1 || classLevel > MAX_CLASS_LEVEL) {
                throw new IllegalArgumentException("Class level mirror is outside the supported range");
            }
        }

        final ClassLoadout first = loadout(LoadoutSlot.FIRST);
        final ClassLoadout second = loadout(LoadoutSlot.SECOND);
        if (!secondSpecUnlocked && second.status() != LoadoutStatus.EMPTY) {
            throw new IllegalArgumentException("The second loadout is locked");
        }
        if (!first.specializationId().isEmpty()
                && first.specializationId().equals(second.specializationId())) {
            throw new IllegalArgumentException("The same specialization may not occupy both slots");
        }
        for (final ClassLoadout loadout : loadouts) {
            if (loadout.status() != LoadoutStatus.EMPTY
                    && (!ClassSpecCatalog.isKnownSpecialization(loadout.specializationId())
                    || !ClassSpecCatalog.belongsTo(loadout.specializationId(), primaryClassId))) {
                throw new IllegalArgumentException("Specialization does not belong to primary class: "
                        + loadout.specializationId());
            }
        }

        int activeCount = 0;
        for (final ClassLoadout loadout : loadouts) {
            if (loadout.status() == LoadoutStatus.ACTIVE) {
                activeCount++;
            }
        }
        if (activeSlot == null) {
            if (activeCount != 0) {
                throw new IllegalArgumentException("An ACTIVE loadout requires activeSlot");
            }
        } else if (activeCount != 1 || loadout(activeSlot).status() != LoadoutStatus.ACTIVE) {
            throw new IllegalArgumentException("activeSlot must identify the only ACTIVE loadout");
        }

        if (status != ProfileStatus.READY && (activeSlot != null || activeCount != 0)) {
            throw new IllegalArgumentException("Review/quarantine profiles cannot activate gameplay");
        }
        final boolean hasReviewLoadout = loadouts.stream()
                .anyMatch(loadout -> loadout.status() == LoadoutStatus.MIGRATION_REVIEW);
        if (hasReviewLoadout && status != ProfileStatus.MIGRATION_REVIEW) {
            throw new IllegalArgumentException("A migration-review loadout requires profile review status");
        }
        if (status == ProfileStatus.READY && migrationState.requiresReview()) {
            throw new IllegalArgumentException("A READY profile cannot retain unresolved migration reasons");
        }
        if (status == ProfileStatus.MIGRATION_REVIEW && !migrationState.requiresReview()) {
            throw new IllegalArgumentException("MIGRATION_REVIEW requires a review reason");
        }
        if (status == ProfileStatus.CORRUPT_QUARANTINE
                && diagnostics.quarantineReason().isEmpty()) {
            throw new IllegalArgumentException("CORRUPT_QUARANTINE requires a quarantine reason");
        }
        if (status != ProfileStatus.CORRUPT_QUARANTINE
                && !diagnostics.quarantineReason().isEmpty()) {
            throw new IllegalArgumentException("Only CORRUPT_QUARANTINE may retain quarantine diagnostics");
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ClassProfile profile)) {
            return false;
        }
        return schemaVersion == profile.schemaVersion && revision == profile.revision
                && classLevel == profile.classLevel && secondSpecUnlocked == profile.secondSpecUnlocked
                && status == profile.status && primaryClassId.equals(profile.primaryClassId)
                && activeSlot == profile.activeSlot && loadouts.equals(profile.loadouts)
                && migrationState.equals(profile.migrationState) && diagnostics.equals(profile.diagnostics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, revision, status, primaryClassId, classLevel, activeSlot,
                secondSpecUnlocked, loadouts, migrationState, diagnostics);
    }

    public static final class Builder {
        private int schemaVersion = SCHEMA_VERSION;
        private long revision;
        private ProfileStatus status = ProfileStatus.READY;
        private String primaryClassId = "";
        private int classLevel;
        private LoadoutSlot activeSlot;
        private boolean secondSpecUnlocked;
        private final List<ClassLoadout> loadouts = new ArrayList<>(
                List.of(ClassLoadout.empty(), ClassLoadout.empty()));
        private MigrationState migrationState = MigrationState.none();
        private ProfileDiagnostics diagnostics = ProfileDiagnostics.none();

        public Builder schemaVersion(final int value) {
            this.schemaVersion = value;
            return this;
        }

        public Builder revision(final long value) {
            this.revision = value;
            return this;
        }

        public Builder status(final ProfileStatus value) {
            this.status = value;
            return this;
        }

        public Builder primaryClassId(final String value) {
            this.primaryClassId = value;
            return this;
        }

        public Builder classLevel(final int value) {
            this.classLevel = value;
            return this;
        }

        public Builder activeSlot(final LoadoutSlot value) {
            this.activeSlot = value;
            return this;
        }

        public Builder secondSpecUnlocked(final boolean value) {
            this.secondSpecUnlocked = value;
            return this;
        }

        public Builder loadout(final LoadoutSlot slot, final ClassLoadout value) {
            loadouts.set(Objects.requireNonNull(slot, "slot").index(),
                    Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder migrationState(final MigrationState value) {
            this.migrationState = value;
            return this;
        }

        public Builder diagnostics(final ProfileDiagnostics value) {
            this.diagnostics = value;
            return this;
        }

        public ClassProfile build() {
            return new ClassProfile(this);
        }
    }
}
