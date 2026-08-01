package hu.taliann.icesmp.classspec.migration;

import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Dependency-free executable regressions for legacy snapshot migration. */
public final class LegacyProfileMigrationRegressionSuite {

    private static final UUID PLAYER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final LegacyProfileMigrator MIGRATOR = new LegacyProfileMigrator();

    private LegacyProfileMigrationRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        validSpecializationMigratesToFirstActiveSlot();
        classWithoutSpecializationStaysUsableAndEmpty();
        classlessXpEvidenceRequiresReview();
        migrationIsDeterministicAndIdempotent();
        mismatchedAndUnknownSpecializationsRequireReview();
        selectedAndFavoriteSpellsRemainSpecLocal();
        companionMapsToTheOwningSpecWithoutEntityIdentity();
        necromancerSoulforgeStaysLocalAndOrphansArePreserved();
        normalizedMechanicalCollisionPreservesBothValues();
        uncertainDiagnosticsFailClosedIntoReview();
        companionSnapshotCannotCarryLiveRuntimeIdentity();
        System.out.println("Legacy Profile v2 migration regression tests passed.");
    }

    private static void validSpecializationMigratesToFirstActiveSlot() {
        final LegacyProfileMigrator.Result result = MIGRATOR.migrate(snapshot(
                "wizard", 27, "elementalist", "arcane_burst", Set.of("blink", "arcane_burst"),
                Optional.empty(), Map.of(), List.of(), List.of()));

        check(result.outcome() == LegacyProfileMigrator.Outcome.MIGRATED,
                "valid legacy specialization unexpectedly required review");
        final ClassProfile profile = result.profile();
        check(profile.schemaVersion() == 2 && profile.revision() == 0L,
                "migration must create schema 2/revision 0");
        check(profile.status() == ProfileStatus.READY && profile.isGameplayUsable(),
                "valid migration did not create a usable profile");
        check("wizard".equals(profile.primaryClassId()) && profile.classLevel() == 27,
                "class identity or level mirror changed during migration");
        check(profile.activeSlot() == LoadoutSlot.FIRST && !profile.secondSpecUnlocked(),
                "legacy specialization was not assigned to the only active slot");
        check(profile.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.ACTIVE
                        && "elementalist".equals(profile.loadout(LoadoutSlot.FIRST).specializationId()),
                "slot one did not contain the migrated specialization");
        check(profile.loadout(LoadoutSlot.SECOND).status() == LoadoutStatus.EMPTY,
                "migration populated the locked second slot");
        check(LegacyProfileMigrator.MIGRATION_ID.equals(
                        profile.migrationState().lastSuccessfulMigration()),
                "migration idempotency marker was not persisted");
    }

    private static void classWithoutSpecializationStaysUsableAndEmpty() {
        final LegacyProfileMigrator.Result result = MIGRATOR.migrate(snapshot(
                "warrior", 18, "", "", Set.of(), Optional.empty(), Map.of(), List.of(), List.of()));

        check(result.outcome() == LegacyProfileMigrator.Outcome.MIGRATED,
                "clean spec-less player unexpectedly required review");
        check(result.profile().status() == ProfileStatus.READY && result.profile().activeSlot() == null,
                "spec-less player activated a loadout");
        check(result.profile().loadouts().stream()
                        .allMatch(loadout -> loadout.status() == LoadoutStatus.EMPTY),
                "spec-less migration hid state in an empty slot");
    }

    private static void classlessXpEvidenceRequiresReview() {
        final LegacyProfileMigrator.Result result = MIGRATOR.migrate(snapshot(
                "", 12, "", "", Set.of(), Optional.empty(), Map.of(), List.of(), List.of()));
        assertReview(result, "classless legacy XP was silently discarded");
        check("12".equals(result.profile().migrationState().preservedLegacy()
                        .get("legacy.class_level")),
                "classless legacy XP/level mirror was not preserved for recovery");
    }

    private static void migrationIsDeterministicAndIdempotent() {
        final LegacyProfileSnapshot snapshot = snapshot(
                "archer", 31, "beast_master", "barbed_shot", Set.of("barbed_shot"),
                Optional.of(companion("WOLF", "Morzsi")), Map.of(), List.of(), List.of());

        final LegacyProfileMigrator.Result first = MIGRATOR.migrate(snapshot);
        final LegacyProfileMigrator.Result retry = MIGRATOR.migrate(snapshot);
        check(first.profile().equals(retry.profile()) && first.outcome() == retry.outcome(),
                "retry produced a different Profile v2 aggregate");
        final UUID firstCompanion = first.profile().loadout(LoadoutSlot.FIRST)
                .companionRoster().keySet().iterator().next();
        final UUID retryCompanion = retry.profile().loadout(LoadoutSlot.FIRST)
                .companionRoster().keySet().iterator().next();
        check(firstCompanion.equals(retryCompanion),
                "retry generated a duplicate logical companion identity");
    }

    private static void mismatchedAndUnknownSpecializationsRequireReview() {
        final LegacyProfileMigrator.Result mismatch = MIGRATOR.migrate(snapshot(
                "warrior", 25, "elementalist", "", Set.of(), Optional.empty(),
                Map.of(), List.of(), List.of()));
        assertReview(mismatch, "parent-class mismatch activated gameplay");
        check("elementalist".equals(mismatch.profile().migrationState().preservedLegacy()
                        .get("legacy.specialization")),
                "mismatched specialization was not preserved");

        final LegacyProfileMigrator.Result unknown = MIGRATOR.migrate(snapshot(
                "wizard", 25, "future_void_mage", "", Set.of(), Optional.empty(),
                Map.of(), List.of(), List.of()));
        assertReview(unknown, "unknown specialization activated gameplay");
        check("future_void_mage".equals(unknown.profile().migrationState().preservedLegacy()
                        .get("legacy.specialization")),
                "unknown specialization was not preserved for recovery");
    }

    private static void selectedAndFavoriteSpellsRemainSpecLocal() {
        final LegacyProfileMigrator.Result result = MIGRATOR.migrate(snapshot(
                "wizard", 30, "elementalist", " ARCANE_BURST ",
                Set.of("BLINK", "frost_nova"), Optional.empty(), Map.of(), List.of(), List.of()));
        final ClassLoadout loadout = result.profile().loadout(LoadoutSlot.FIRST);

        check("arcane_burst".equals(loadout.selectedSpell()),
                "selected spell was not normalized and retained");
        check(loadout.favoriteSpells().equals(Set.of("blink", "frost_nova")),
                "favorite spells were not retained on the migrated loadout");
        check(result.profile().loadout(LoadoutSlot.SECOND).favoriteSpells().isEmpty(),
                "favorites leaked into the second loadout");
    }

    private static void companionMapsToTheOwningSpecWithoutEntityIdentity() {
        final UUID discardedEntityId =
                UUID.fromString("00000000-0000-0000-0000-000000000499");
        final LegacyProfileSnapshot.MechanicalStateAccumulator uncertain =
                new LegacyProfileSnapshot.MechanicalStateAccumulator();
        uncertain.dropLiveEntityReference("pet_entity");
        final LegacyProfileSnapshot snapshot = snapshot(
                "archer", 32, "beast_master", "", Set.of(),
                Optional.of(companion("WOLF", "Morzsi")), Map.of(), uncertain.entries(),
                uncertain.diagnostics());

        final LegacyProfileMigrator.Result result = MIGRATOR.migrate(snapshot);
        check(result.outcome() == LegacyProfileMigrator.Outcome.MIGRATED,
                "dropping a transient entity identity made an otherwise valid profile ambiguous");
        final Map<UUID, CompanionProfile> roster = result.profile().loadout(LoadoutSlot.FIRST)
                .companionRoster();
        check(roster.size() == 1, "legacy companion was not migrated exactly once");
        final CompanionProfile migrated = roster.values().iterator().next();
        check("beast_master.stable".equals(migrated.namespace()),
                "companion was assigned to the wrong spec namespace");
        check(!migrated.companionId().equals(discardedEntityId),
                "live entity identity became the durable companion identity");
        check(result.profile().migrationState().preservedLegacy().values().stream()
                        .noneMatch(discardedEntityId.toString()::equals),
                "discarded live entity UUID leaked into preserved legacy values");
        check(result.profile().migrationState().preservedLegacy().values().contains(
                        LegacyProfileSnapshot.DiagnosticCode.LIVE_ENTITY_REFERENCE_DROPPED.name()),
                "entity-drop diagnostic was not retained");
    }

    private static void necromancerSoulforgeStaysLocalAndOrphansArePreserved() {
        final LegacyProfileMigrator.Result necromancer = MIGRATOR.migrate(snapshot(
                "wizard", 40, "necromancer", "", Set.of(), Optional.empty(),
                Map.of("elet", 3, "sebzes", 2), List.of(), List.of()));
        final Map<String, String> mechanicState = necromancer.profile()
                .loadout(LoadoutSlot.FIRST).mechanicState();
        check("3".equals(mechanicState.get("necromancer.soulforge.elet"))
                        && "2".equals(mechanicState.get("necromancer.soulforge.sebzes")),
                "Soulforge ranks did not migrate into the necromancer namespace");

        final LegacyProfileMigrator.Result orphan = MIGRATOR.migrate(snapshot(
                "wizard", 40, "elementalist", "", Set.of(), Optional.empty(),
                Map.of("elet", 3), List.of(), List.of()));
        assertReview(orphan, "orphan Soulforge state activated a non-necromancer profile");
        check(orphan.profile().loadout(LoadoutSlot.FIRST).mechanicState().isEmpty(),
                "Soulforge mechanics leaked into a non-necromancer loadout");
        check("3".equals(orphan.profile().migrationState().preservedLegacy()
                        .get("orphaned.soulforge.elet")),
                "orphan Soulforge rank was not preserved");
    }

    private static void normalizedMechanicalCollisionPreservesBothValues() {
        final LegacyProfileSnapshot.MechanicalStateAccumulator uncertain =
                new LegacyProfileSnapshot.MechanicalStateAccumulator();
        check(uncertain.add("spec.future-key", LegacyProfileSnapshot.ValueKind.STRING, "left"),
                "first unknown mechanical value was rejected");
        check(uncertain.add("spec.future_key", LegacyProfileSnapshot.ValueKind.STRING, "right"),
                "colliding unknown mechanical value was overwritten or rejected");
        final LegacyProfileMigrator.Result result = MIGRATOR.migrate(snapshot(
                "wizard", 25, "elementalist", "", Set.of(), Optional.empty(), Map.of(),
                uncertain.entries(), uncertain.diagnostics()));

        assertReview(result, "unknown mechanical collision did not fail closed");
        check(result.profile().migrationState().preservedLegacy().values().containsAll(
                        Set.of("left", "right")),
                "normalization collision overwrote one legacy value");
        check(result.profile().migrationState().preservedLegacy().values().contains(
                        LegacyProfileSnapshot.DiagnosticCode.NORMALIZED_KEY_COLLISION.name()),
                "normalization collision was not diagnosed");
    }

    private static void uncertainDiagnosticsFailClosedIntoReview() {
        final LegacyProfileSnapshot.Diagnostic diagnostic = new LegacyProfileSnapshot.Diagnostic(
                LegacyProfileSnapshot.DiagnosticCode.INVALID_LEGACY_VALUE,
                "class_spec", "legacy field used an unexpected primitive type");
        final LegacyProfileMigrator.Result result = MIGRATOR.migrate(snapshot(
                "wizard", 25, "elementalist", "", Set.of(), Optional.empty(),
                Map.of(), List.of(), List.of(diagnostic)));

        assertReview(result, "uncertain diagnostic did not block class/spec runtime");
        check(result.profile().loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.MIGRATION_REVIEW,
                "uncertain specialization was not marked MIGRATION_REVIEW");
        check(!result.profile().migrationState().preservedLegacy().isEmpty(),
                "uncertain diagnostic was discarded");
    }

    private static void companionSnapshotCannotCarryLiveRuntimeIdentity() {
        final var components = LegacyProfileSnapshot.LegacyCompanion.class.getRecordComponents();
        for (final var component : components) {
            check(!UUID.class.equals(component.getType()),
                    "legacy companion snapshot can carry a live entity UUID: " + component.getName());
            check(!component.getType().getName().startsWith("org.bukkit."),
                    "legacy companion snapshot imports a live Bukkit runtime type: " + component.getName());
        }
        check(java.util.Arrays.stream(components).noneMatch(component ->
                        component.getName().toLowerCase(java.util.Locale.ROOT).contains("entity")),
                "entity runtime identity leaked past the migration snapshot boundary");
    }

    private static LegacyProfileSnapshot snapshot(
            final String classId, final int classLevel, final String specId,
            final String selectedSpell, final Set<String> favorites,
            final Optional<LegacyProfileSnapshot.LegacyCompanion> companion,
            final Map<String, Integer> soulforge,
            final List<LegacyProfileSnapshot.MechanicalEntry> unknown,
            final List<LegacyProfileSnapshot.Diagnostic> diagnostics) {
        return new LegacyProfileSnapshot(PLAYER_ID, classId, classLevel, specId, selectedSpell,
                favorites, companion, soulforge, unknown, diagnostics);
    }

    private static LegacyProfileSnapshot.LegacyCompanion companion(final String typeId, final String name) {
        return new LegacyProfileSnapshot.LegacyCompanion(typeId, name, 7, 1_234L,
                "loyal", "ACTIVE", Set.of("legacy_pet_armor"), 42_000L, false);
    }

    private static void assertReview(final LegacyProfileMigrator.Result result, final String message) {
        check(result.outcome() == LegacyProfileMigrator.Outcome.REVIEW_REQUIRED, message);
        check(result.profile().status() == ProfileStatus.MIGRATION_REVIEW, message);
        check(result.profile().activeSlot() == null && !result.profile().isGameplayUsable(), message);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
