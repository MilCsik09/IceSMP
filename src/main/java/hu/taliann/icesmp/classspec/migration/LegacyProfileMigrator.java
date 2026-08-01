package hu.taliann.icesmp.classspec.migration;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.classspec.domain.MigrationState;
import hu.taliann.icesmp.classspec.domain.ProfileDiagnostics;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure, deterministic and reward-free legacy-to-Profile-v2 migration. */
public final class LegacyProfileMigrator {

    public static final String MIGRATION_ID = "legacy-pdc-v1-to-profile-v2";
    private static final Set<String> COMPANION_SPECS = Set.of(
            "beast_master", "necromancer", "unholy", "demonologist");

    public Result migrate(final LegacyProfileSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        final String classId = ClassSpecCatalog.normalize(snapshot.primaryClassId());
        final String specId = ClassSpecCatalog.normalize(snapshot.specializationId());
        final List<String> reviewReasons = new ArrayList<>();
        final Map<String, String> preserved = new LinkedHashMap<>();
        preserveDiagnostics(snapshot, preserved, reviewReasons);
        preserveUnknownState(snapshot, preserved, reviewReasons);

        if (classId.isEmpty()) {
            preserveUnassigned(snapshot, preserved);
            if (snapshot.classLevel() != 0) {
                preserved.put("legacy.class_level", Integer.toString(snapshot.classLevel()));
                reviewReasons.add("Legacy class nélkül class-level/XP tükör maradt");
            }
            if (!specId.isEmpty() || hasSpecLocalData(snapshot)) {
                reviewReasons.add("Legacy class nélkül spechez kötött állapot maradt");
            }
            return finishClassless(reviewReasons, preserved);
        }
        if (!ClassSpecCatalog.isKnownClass(classId)) {
            preserved.put("legacy.primary_class", snapshot.primaryClassId());
            preserved.put("legacy.class_level", Integer.toString(snapshot.classLevel()));
            preserveUnassigned(snapshot, preserved);
            reviewReasons.add("Ismeretlen legacy primary class: " + snapshot.primaryClassId());
            return finishClassless(reviewReasons, preserved);
        }

        int classLevel = snapshot.classLevel();
        if (classLevel < 1 || classLevel > ClassProfile.MAX_CLASS_LEVEL) {
            preserved.put("legacy.class_level", Integer.toString(classLevel));
            reviewReasons.add("A legacy class level kívül esik a támogatott tartományon");
            classLevel = Math.max(1, Math.min(ClassProfile.MAX_CLASS_LEVEL, classLevel));
        }

        if (specId.isEmpty()) {
            preserveUnassigned(snapshot, preserved);
            if (hasSpecLocalData(snapshot)) {
                reviewReasons.add("Spec nélküli profilhoz spec-lokális legacy adat tartozik");
            }
            return finishWithClass(classId, classLevel, null, reviewReasons, preserved);
        }
        if (!ClassSpecCatalog.isKnownSpecialization(specId)) {
            preserved.put("legacy.specialization", snapshot.specializationId());
            preserveUnassigned(snapshot, preserved);
            reviewReasons.add("Ismeretlen legacy specialization: " + snapshot.specializationId());
            return finishWithClass(classId, classLevel, null, reviewReasons, preserved);
        }
        if (!ClassSpecCatalog.belongsTo(specId, classId)) {
            preserved.put("legacy.specialization", snapshot.specializationId());
            preserveUnassigned(snapshot, preserved);
            reviewReasons.add("A legacy specialization nem a primary classhoz tartozik");
            return finishWithClass(classId, classLevel, null, reviewReasons, preserved);
        }

        final Map<UUID, CompanionProfile> roster = migrateCompanion(
                snapshot, specId, preserved, reviewReasons);
        final Map<String, String> mechanics = migrateSoulforge(
                snapshot, specId, preserved, reviewReasons);
        final boolean review = !reviewReasons.isEmpty();
        final ClassLoadout loadout = new ClassLoadout(specId,
                review ? LoadoutStatus.MIGRATION_REVIEW : LoadoutStatus.ACTIVE,
                null, Map.of(), MasteryProgress.empty(), null,
                new LinkedHashSet<>(snapshot.favoriteSpellIds()),
                ClassSpecCatalog.normalize(snapshot.selectedSpellId()), CapstoneStatus.LOCKED,
                roster, mechanics, review ? String.join("; ", reviewReasons) : "");
        return finishWithClass(classId, classLevel, loadout, reviewReasons, preserved);
    }

    private static Result finishClassless(final List<String> reviewReasons,
                                          final Map<String, String> preserved) {
        final boolean review = !reviewReasons.isEmpty();
        final ClassProfile profile = ClassProfile.builder().revision(0L)
                .status(review ? ProfileStatus.MIGRATION_REVIEW : ProfileStatus.READY)
                .migrationState(new MigrationState(MIGRATION_ID,
                        distinct(reviewReasons), Map.copyOf(preserved)))
                .diagnostics(ProfileDiagnostics.none()).build();
        return new Result(profile, review ? Outcome.REVIEW_REQUIRED : Outcome.MIGRATED,
                List.copyOf(reviewReasons));
    }

    private static Result finishWithClass(final String classId, final int classLevel,
                                          final ClassLoadout first,
                                          final List<String> reviewReasons,
                                          final Map<String, String> preserved) {
        final boolean review = !reviewReasons.isEmpty();
        final ClassProfile.Builder builder = ClassProfile.builder().revision(0L)
                .status(review ? ProfileStatus.MIGRATION_REVIEW : ProfileStatus.READY)
                .primaryClassId(classId).classLevel(classLevel)
                .migrationState(new MigrationState(MIGRATION_ID,
                        distinct(reviewReasons), Map.copyOf(preserved)))
                .diagnostics(ProfileDiagnostics.none());
        if (first != null) {
            builder.loadout(LoadoutSlot.FIRST, first);
            if (!review) {
                builder.activeSlot(LoadoutSlot.FIRST);
            }
        }
        final ClassProfile profile = builder.build();
        return new Result(profile, review ? Outcome.REVIEW_REQUIRED : Outcome.MIGRATED,
                List.copyOf(reviewReasons));
    }

    private static Map<UUID, CompanionProfile> migrateCompanion(
            final LegacyProfileSnapshot snapshot, final String specId,
            final Map<String, String> preserved, final List<String> reviewReasons) {
        if (snapshot.companion().isEmpty()) {
            return Map.of();
        }
        final LegacyProfileSnapshot.LegacyCompanion legacy = snapshot.companion().orElseThrow();
        if (!COMPANION_SPECS.contains(specId)) {
            preserveCompanion(legacy, preserved);
            reviewReasons.add("A legacy companion nem companion-specializációhoz tartozik");
            return Map.of();
        }
        if (legacy.typeId().isBlank()) {
            preserveCompanion(legacy, preserved);
            reviewReasons.add("A legacy companion típusa hiányzik");
            return Map.of();
        }
        int level = legacy.level();
        if (level < 1) {
            preserved.put("legacy.companion.level", Integer.toString(level));
            reviewReasons.add("A legacy companion szintje érvénytelen");
            level = 1;
        }
        final String namespace = ClassSpecCatalog.companionNamespace(specId);
        final UUID logicalId = UUID.nameUUIDFromBytes((MIGRATION_ID + ":"
                + snapshot.playerId() + ":" + namespace).getBytes(StandardCharsets.UTF_8));
        final List<String> equipment = new ArrayList<>(legacy.equipmentOrModuleIds());
        final CompanionProfile companion = new CompanionProfile(logicalId, namespace,
                ClassSpecCatalog.normalize(legacy.typeId()), legacy.name(), level,
                legacy.experience(), ClassSpecCatalog.normalize(legacy.traitOrMutationId()),
                legacy.stance(), equipment, legacy.resummonAtEpochMillis(),
                Map.of("legacy.ritual_summoned", Boolean.toString(legacy.ritualSummoned())));
        return Map.of(logicalId, companion);
    }

    private static Map<String, String> migrateSoulforge(
            final LegacyProfileSnapshot snapshot, final String specId,
            final Map<String, String> preserved, final List<String> reviewReasons) {
        if (snapshot.soulforgeRanks().isEmpty()) {
            return Map.of();
        }
        if (!"necromancer".equals(specId)) {
            snapshot.soulforgeRanks().forEach((branch, rank) ->
                    preserved.put("orphaned.soulforge." + branch, Integer.toString(rank)));
            reviewReasons.add("Soulforge-rang maradt Nekromanta loadout nélkül");
            return Map.of();
        }
        final Map<String, String> mechanics = new LinkedHashMap<>();
        snapshot.soulforgeRanks().forEach((branch, rank) -> {
            mechanics.put("necromancer.soulforge." + ClassSpecCatalog.normalize(branch),
                    Integer.toString(rank));
            if (rank == null || rank < 0 || rank > 5) {
                preserved.put("legacy.soulforge." + branch, String.valueOf(rank));
                reviewReasons.add("Soulforge-rang kívül esik a támogatott tartományon: " + branch);
            }
        });
        return Map.copyOf(mechanics);
    }

    private static void preserveDiagnostics(final LegacyProfileSnapshot snapshot,
                                            final Map<String, String> preserved,
                                            final List<String> reviewReasons) {
        int index = 0;
        for (final LegacyProfileSnapshot.Diagnostic diagnostic : snapshot.diagnostics()) {
            final String prefix = "legacy.diagnostic." + String.format("%03d", index++);
            preserved.put(prefix + ".code", diagnostic.code().name());
            preserved.put(prefix + ".field", diagnostic.field());
            preserved.put(prefix + ".detail", diagnostic.detail());
            if (diagnostic.code() != LegacyProfileSnapshot.DiagnosticCode.LIVE_ENTITY_REFERENCE_DROPPED) {
                reviewReasons.add("Legacy diagnosztika: " + diagnostic.code());
            }
        }
    }

    private static void preserveUnknownState(final LegacyProfileSnapshot snapshot,
                                             final Map<String, String> preserved,
                                             final List<String> reviewReasons) {
        int index = 0;
        for (final LegacyProfileSnapshot.MechanicalEntry entry : snapshot.unknownMechanicalState()) {
            final String prefix = "legacy.mechanic." + String.format("%03d", index++);
            preserved.put(prefix + ".key", entry.rawKey());
            preserved.put(prefix + ".kind", entry.valueKind().name());
            preserved.put(prefix + ".value", entry.value());
        }
        if (!snapshot.unknownMechanicalState().isEmpty()) {
            reviewReasons.add("Ismeretlen legacy mechanikai állapot megőrizve");
        }
    }

    private static void preserveUnassigned(final LegacyProfileSnapshot snapshot,
                                           final Map<String, String> preserved) {
        if (!snapshot.selectedSpellId().isBlank()) {
            preserved.put("legacy.selected_spell", snapshot.selectedSpellId());
        }
        int index = 0;
        for (final String favorite : snapshot.favoriteSpellIds()) {
            preserved.put("legacy.favorite_spell." + String.format("%03d", index++), favorite);
        }
        snapshot.companion().ifPresent(companion -> preserveCompanion(companion, preserved));
        snapshot.soulforgeRanks().forEach((branch, rank) ->
                preserved.put("orphaned.soulforge." + branch, Integer.toString(rank)));
    }

    private static void preserveCompanion(final LegacyProfileSnapshot.LegacyCompanion companion,
                                          final Map<String, String> preserved) {
        preserved.put("orphaned.companion.type", companion.typeId());
        preserved.put("orphaned.companion.name", companion.name());
        preserved.put("orphaned.companion.level", Integer.toString(companion.level()));
        preserved.put("orphaned.companion.xp", Long.toString(companion.experience()));
        preserved.put("orphaned.companion.trait", companion.traitOrMutationId());
        preserved.put("orphaned.companion.stance", companion.stance());
        preserved.put("orphaned.companion.resummon_at",
                Long.toString(companion.resummonAtEpochMillis()));
        preserved.put("orphaned.companion.ritual",
                Boolean.toString(companion.ritualSummoned()));
        int index = 0;
        for (final String equipment : companion.equipmentOrModuleIds()) {
            preserved.put("orphaned.companion.equipment."
                    + String.format("%02d", index++), equipment);
        }
    }

    private static boolean hasSpecLocalData(final LegacyProfileSnapshot snapshot) {
        return !snapshot.selectedSpellId().isBlank() || !snapshot.favoriteSpellIds().isEmpty()
                || snapshot.companion().isPresent() || !snapshot.soulforgeRanks().isEmpty()
                || !snapshot.unknownMechanicalState().isEmpty();
    }

    private static List<String> distinct(final List<String> reasons) {
        return List.copyOf(new LinkedHashSet<>(reasons));
    }

    public record Result(ClassProfile profile, Outcome outcome, List<String> diagnostics) {
        public Result {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(outcome, "outcome");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public enum Outcome {
        MIGRATED,
        REVIEW_REQUIRED
    }
}
