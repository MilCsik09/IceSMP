package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;
import hu.taliann.icesmp.classspec.migration.LegacyProfileMigrator;
import hu.taliann.icesmp.classspec.migration.LegacyProfileSnapshot;
import hu.taliann.icesmp.classspec.persistence.ClassProfileRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Executable join/migrate/logout/disable lifecycle regressions without Bukkit state. */
public final class ClassProfileLifecycleRegressionSuite {

    private static final UUID PLAYER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000701");

    private ClassProfileLifecycleRegressionSuite() {
    }

    public static void main(final String[] args) {
        disabledFeatureNeverReadsOrWritesProfiles();
        firstJoinMigratesOnceAndSecondJoinLoadsTheCommittedProfile();
        ambiguousMigrationPersistsEvidenceButBlocksGameplay();
        failedMigrationSaveKeepsLegacyInputAndBlocksTheSession();
        logoutFlushesBeforeInvalidationAndDisableFlushesAll();
        disableWaitsForAnAdmittedJoinBeforeFlushAll();
        System.out.println("Class profile lifecycle regression suite passed.");
    }

    private static void disabledFeatureNeverReadsOrWritesProfiles() {
        final FakeRepository repository = new FakeRepository();
        final ClassProfileLifecycleService lifecycle = service(false, repository);
        final ClassProfileLifecycleService.JoinResult result = lifecycle.join(validSnapshot())
                .toCompletableFuture().join();

        check(result.status() == ClassProfileLifecycleService.Status.LEGACY_DISABLED,
                "disabled feature did not preserve the legacy-only route");
        check(repository.loads == 0 && repository.saves == 0,
                "disabled feature touched Profile v2 persistence");
    }

    private static void firstJoinMigratesOnceAndSecondJoinLoadsTheCommittedProfile() {
        final FakeRepository repository = new FakeRepository();
        final ClassProfileLifecycleService lifecycle = service(true, repository);

        final ClassProfileLifecycleService.JoinResult first = lifecycle.join(validSnapshot())
                .toCompletableFuture().join();
        check(first.status() == ClassProfileLifecycleService.Status.READY,
                "valid legacy profile did not become ready");
        check(first.migrationAttempted(), "first missing profile did not run migration");
        check(first.profile().revision() == 0L, "first migration did not persist revision zero");
        check(repository.lastExpectedRevision == ClassProfileRepository.MISSING_REVISION,
                "first migration did not use the explicit missing revision");

        final ClassProfileLifecycleService.JoinResult second = lifecycle.join(validSnapshot())
                .toCompletableFuture().join();
        check(second.status() == ClassProfileLifecycleService.Status.READY,
                "second join failed to load the durable profile");
        check(!second.migrationAttempted(), "second join duplicated migration");
        check(repository.saves == 1, "idempotent join wrote the migration twice");
    }

    private static void ambiguousMigrationPersistsEvidenceButBlocksGameplay() {
        final FakeRepository repository = new FakeRepository();
        final ClassProfileLifecycleService lifecycle = service(true, repository);
        final LegacyProfileSnapshot ambiguous = new LegacyProfileSnapshot(
                PLAYER_ID, "wizard", 25, "unholy", "soul_bolt", java.util.Set.of("soul_bolt"),
                Optional.empty(), Map.of(), java.util.List.of(), java.util.List.of());

        final ClassProfileLifecycleService.JoinResult result = lifecycle.join(ambiguous)
                .toCompletableFuture().join();
        check(result.status() == ClassProfileLifecycleService.Status.BLOCKED,
                "ambiguous migration activated class/spec gameplay");
        check(result.profile().status() == ProfileStatus.MIGRATION_REVIEW,
                "ambiguous migration did not persist MIGRATION_REVIEW");
        check(result.profile().migrationState().preservedLegacy()
                        .containsKey("legacy.specialization"),
                "ambiguous specialization evidence was discarded");
        check(repository.saves == 1, "review evidence was not durably committed");
    }

    private static void failedMigrationSaveKeepsLegacyInputAndBlocksTheSession() {
        final FakeRepository repository = new FakeRepository();
        repository.failSave = true;
        final LegacyProfileSnapshot source = validSnapshot();
        final ClassProfileLifecycleService.JoinResult result = service(true, repository).join(source)
                .toCompletableFuture().join();

        check(result.status() == ClassProfileLifecycleService.Status.BLOCKED,
                "failed migration persistence was reported ready");
        check(repository.profiles.isEmpty(), "failed save published an in-memory authority");
        check(repository.blocks.containsKey(PLAYER_ID),
                "failed migration persistence did not block the session");
        check("necromancer".equals(source.specializationId()),
                "migration failure mutated the immutable legacy evidence");
    }

    private static void logoutFlushesBeforeInvalidationAndDisableFlushesAll() {
        final FakeRepository repository = new FakeRepository();
        final ClassProfileLifecycleService lifecycle = service(true, repository);
        lifecycle.join(validSnapshot()).toCompletableFuture().join();

        lifecycle.logout(PLAYER_ID).toCompletableFuture().join();
        check(repository.playerFlushes == 1, "logout did not flush the player profile");
        check(repository.invalidations == 1 && repository.cached(PLAYER_ID).isEmpty(),
                "logout did not invalidate only after its flush barrier");

        lifecycle.prepareDisable().toCompletableFuture().join();
        check(repository.globalFlushes == 1, "plugin disable did not call flushAll");
        final ClassProfileLifecycleService.JoinResult late = lifecycle.join(validSnapshot())
                .toCompletableFuture().join();
        check(late.status() == ClassProfileLifecycleService.Status.BLOCKED,
                "lifecycle accepted a join after disable admission closed");
    }

    private static void disableWaitsForAnAdmittedJoinBeforeFlushAll() {
        final FakeRepository repository = new FakeRepository();
        repository.deferNextLoad = true;
        final ClassProfileLifecycleService lifecycle = service(true, repository);
        final CompletionStage<ClassProfileLifecycleService.JoinResult> join =
                lifecycle.join(validSnapshot());
        final CompletionStage<Void> disable = lifecycle.prepareDisable();

        check(!disable.toCompletableFuture().isDone(),
                "disable flush overtook an admitted join load/migration");
        check(repository.globalFlushes == 0,
                "flushAll ran before the admitted join produced its durable result");
        repository.completeDeferredLoad(ClassProfileRepository.LoadResult.missing());
        check(join.toCompletableFuture().join().status() == ClassProfileLifecycleService.Status.READY,
                "admitted join failed while disable drained it");
        disable.toCompletableFuture().join();
        check(repository.saves == 1 && repository.globalFlushes == 1,
                "disable did not flush after the admitted migration commit");
    }

    private static ClassProfileLifecycleService service(final boolean enabled,
                                                        final FakeRepository repository) {
        return new ClassProfileLifecycleService(() -> enabled, repository,
                new LegacyProfileMigrator());
    }

    private static LegacyProfileSnapshot validSnapshot() {
        return new LegacyProfileSnapshot(PLAYER_ID, "wizard", 25, "necromancer",
                "soul_bolt", java.util.Set.of("soul_bolt"), Optional.empty(),
                Map.of(), java.util.List.of(), java.util.List.of());
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeRepository implements ClassProfileRepository {
        private final Map<UUID, ClassProfile> profiles = new LinkedHashMap<>();
        private final Map<UUID, String> blocks = new LinkedHashMap<>();
        private int loads;
        private int saves;
        private int playerFlushes;
        private int globalFlushes;
        private int invalidations;
        private long lastExpectedRevision = Long.MIN_VALUE;
        private boolean failSave;
        private boolean deferNextLoad;
        private CompletableFuture<LoadResult> deferredLoad;

        @Override
        public CompletionStage<LoadResult> load(final UUID playerId) {
            loads++;
            if (deferNextLoad) {
                deferNextLoad = false;
                deferredLoad = new CompletableFuture<>();
                return deferredLoad;
            }
            final ClassProfile profile = profiles.get(playerId);
            return CompletableFuture.completedFuture(profile == null
                    ? LoadResult.missing() : LoadResult.found(profile));
        }

        private void completeDeferredLoad(final LoadResult result) {
            check(deferredLoad != null, "no deferred load to complete");
            deferredLoad.complete(result);
            deferredLoad = null;
        }

        @Override
        public CompletionStage<ClassProfile> save(final UUID playerId, final long expectedRevision,
                                                   final ClassProfile nextProfile) {
            saves++;
            lastExpectedRevision = expectedRevision;
            if (failSave) {
                return CompletableFuture.failedFuture(new IllegalStateException("injected save failure"));
            }
            final ClassProfile current = profiles.get(playerId);
            final long actual = current == null ? MISSING_REVISION : current.revision();
            if (actual != expectedRevision || nextProfile.revision() != actual + 1L) {
                return CompletableFuture.failedFuture(new IllegalStateException("invalid CAS"));
            }
            profiles.put(playerId, nextProfile);
            return CompletableFuture.completedFuture(nextProfile);
        }

        @Override
        public CompletionStage<QuarantineRecord> quarantine(final UUID playerId,
                                                             final byte[] originalPayload,
                                                             final String reason) {
            blocks.put(playerId, reason);
            return CompletableFuture.completedFuture(
                    new QuarantineRecord(playerId, 1L, reason, "quarantine.yml"));
        }

        @Override
        public CompletionStage<Void> flush(final UUID playerId) {
            playerFlushes++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> flushAll() {
            globalFlushes++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void invalidate(final UUID playerId) {
            invalidations++;
            profiles.remove(playerId);
        }

        @Override
        public Optional<ClassProfile> cached(final UUID playerId) {
            return Optional.ofNullable(profiles.get(playerId));
        }

        @Override
        public Optional<String> sessionBlockReason(final UUID playerId) {
            return Optional.ofNullable(blocks.get(playerId));
        }

        @Override
        public Optional<String> quarantineReason(final UUID playerId) {
            return Optional.empty();
        }

        @Override
        public void blockSession(final UUID playerId, final String reason) {
            blocks.put(playerId, reason);
        }
    }
}
