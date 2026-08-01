package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;
import hu.taliann.icesmp.classspec.migration.LegacyProfileMigrator;
import hu.taliann.icesmp.classspec.migration.LegacyProfileSnapshot;
import hu.taliann.icesmp.classspec.persistence.ClassProfileRepository;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Feature-flagged join/migrate/flush lifecycle without Bukkit or live entity references. */
public final class ClassProfileLifecycleService {

    private final BooleanSupplier enabled;
    private final ClassProfileRepository repository;
    private final LegacyProfileMigrator migrator;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Object admissionLock = new Object();
    private final Set<CompletableFuture<JoinResult>> inFlightJoins = ConcurrentHashMap.newKeySet();

    public ClassProfileLifecycleService(final BooleanSupplier enabled,
                                        final ClassProfileRepository repository,
                                        final LegacyProfileMigrator migrator) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.migrator = Objects.requireNonNull(migrator, "migrator");
    }

    public CompletionStage<JoinResult> join(final LegacyProfileSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!enabled.getAsBoolean()) {
            return CompletableFuture.completedFuture(JoinResult.legacyDisabled());
        }
        final CompletableFuture<JoinResult> admitted = new CompletableFuture<>();
        synchronized (admissionLock) {
            if (!accepting.get()) {
                return CompletableFuture.completedFuture(JoinResult.blocked(
                        "Profile v2 lifecycle leállt", null, false));
            }
            inFlightJoins.add(admitted);
        }
        final UUID playerId = snapshot.playerId();
        final CompletionStage<ClassProfileRepository.LoadResult> load;
        try {
            load = Objects.requireNonNull(repository.load(playerId), "repository load stage");
        } catch (final RuntimeException failure) {
            final String detail = failureMessage(failure);
            repository.blockSession(playerId, detail);
            admitted.complete(JoinResult.blocked(detail, repository.cached(playerId).orElse(null), false));
            inFlightJoins.remove(admitted);
            return admitted;
        }
        load.thenCompose(result -> switch (result.status()) {
            case FOUND -> validateMirror(snapshot, result.profile());
            case MISSING -> migrateAndCommit(snapshot);
            case QUARANTINED -> CompletableFuture.completedFuture(
                    JoinResult.quarantined(result.diagnostic()));
        }).exceptionally(failure -> {
            final String detail = failureMessage(failure);
            repository.blockSession(playerId, detail);
            return JoinResult.blocked(detail, repository.cached(playerId).orElse(null), false);
        }).whenComplete((result, failure) -> {
            if (failure == null) {
                admitted.complete(result);
            } else {
                admitted.completeExceptionally(failure);
            }
            inFlightJoins.remove(admitted);
        });
        return admitted;
    }

    private CompletionStage<JoinResult> validateMirror(final LegacyProfileSnapshot snapshot,
                                                       final ClassProfile profile) {
        if (profile.status() != ProfileStatus.READY || !profile.isGameplayUsable()) {
            return CompletableFuture.completedFuture(JoinResult.blocked(
                    profile.status() == ProfileStatus.MIGRATION_REVIEW
                            ? String.join("; ", profile.migrationState().reviewReasons())
                            : profile.diagnostics().quarantineReason(), profile, false));
        }
        final String legacyClass = ClassSpecCatalog.normalize(snapshot.primaryClassId());
        if (!profile.primaryClassId().equals(legacyClass)) {
            final String reason = "Profile/JobManager class mirror eltérés: profile="
                    + profile.primaryClassId() + ", legacy=" + legacyClass;
            repository.blockSession(snapshot.playerId(), reason);
            return CompletableFuture.completedFuture(JoinResult.blocked(reason, profile, false));
        }
        if (profile.primaryClassId().isEmpty() || profile.classLevel() == snapshot.classLevel()) {
            return CompletableFuture.completedFuture(JoinResult.ready(profile, false));
        }
        if (snapshot.classLevel() < 1 || snapshot.classLevel() > ClassProfile.MAX_CLASS_LEVEL) {
            final String reason = "JobManager class-level mirror tartományhibás: " + snapshot.classLevel();
            repository.blockSession(snapshot.playerId(), reason);
            return CompletableFuture.completedFuture(JoinResult.blocked(reason, profile, false));
        }
        final ClassProfile candidate = profile.toBuilder()
                .revision(profile.revision() + 1L)
                .classLevel(snapshot.classLevel()).build();
        return repository.save(snapshot.playerId(), profile.revision(), candidate)
                .thenApply(saved -> JoinResult.ready(saved, false));
    }

    private CompletionStage<JoinResult> migrateAndCommit(final LegacyProfileSnapshot snapshot) {
        final LegacyProfileMigrator.Result migrated;
        try {
            migrated = migrator.migrate(snapshot);
        } catch (final RuntimeException failure) {
            final String reason = "Legacy migrációs hiba: " + failureMessage(failure);
            final byte[] evidence = snapshot.toString().getBytes(StandardCharsets.UTF_8);
            return repository.quarantine(snapshot.playerId(), evidence, reason)
                    .handle((record, quarantineFailure) -> JoinResult.blocked(
                            quarantineFailure == null ? reason
                                    : reason + "; quarantine: " + failureMessage(quarantineFailure),
                            null, true));
        }
        return repository.save(snapshot.playerId(), ClassProfileRepository.MISSING_REVISION,
                        migrated.profile())
                .thenApply(saved -> migrated.outcome() == LegacyProfileMigrator.Outcome.MIGRATED
                        ? JoinResult.ready(saved, true)
                        : JoinResult.blocked(String.join("; ", migrated.diagnostics()), saved, true));
    }

    /** Flushes before invalidating the session cache. */
    public CompletionStage<Void> logout(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!enabled.getAsBoolean()) {
            return CompletableFuture.completedFuture(null);
        }
        return repository.flush(playerId).whenComplete((ignored, failure) ->
                repository.invalidate(playerId));
    }

    /** Closes the admission gate before draining all queued writes. */
    public CompletionStage<Void> prepareDisable() {
        if (!enabled.getAsBoolean()) {
            accepting.set(false);
            return CompletableFuture.completedFuture(null);
        }
        final CompletableFuture<?>[] joins;
        synchronized (admissionLock) {
            accepting.set(false);
            joins = inFlightJoins.toArray(CompletableFuture[]::new);
        }
        return CompletableFuture.allOf(joins).thenCompose(ignored -> repository.flushAll());
    }

    public boolean accepting() {
        return accepting.get();
    }

    public record JoinResult(Status status, ClassProfile profile, String diagnostic,
                             boolean migrationAttempted) {
        public JoinResult {
            Objects.requireNonNull(status, "status");
            diagnostic = diagnostic == null ? "" : diagnostic;
            if (status == Status.READY && profile == null) {
                throw new IllegalArgumentException("READY join requires a profile");
            }
        }

        public static JoinResult legacyDisabled() {
            return new JoinResult(Status.LEGACY_DISABLED, null, "", false);
        }

        public static JoinResult ready(final ClassProfile profile, final boolean migrated) {
            return new JoinResult(Status.READY, Objects.requireNonNull(profile, "profile"), "", migrated);
        }

        public static JoinResult blocked(final String diagnostic, final ClassProfile profile,
                                         final boolean migrated) {
            return new JoinResult(Status.BLOCKED, profile, diagnostic, migrated);
        }

        public static JoinResult quarantined(final String diagnostic) {
            return new JoinResult(Status.QUARANTINED, null, diagnostic, false);
        }

        public Optional<ClassProfile> profileOptional() {
            return Optional.ofNullable(profile);
        }
    }

    public enum Status {
        LEGACY_DISABLED,
        READY,
        BLOCKED,
        QUARANTINED
    }

    private static String failureMessage(final Throwable failure) {
        Throwable current = failure;
        while (current instanceof java.util.concurrent.CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
