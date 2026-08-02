package hu.taliann.icesmp.classspec.persistence;

import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Executable Profile v2 owner-binding, strict-YAML, CAS, recovery and shutdown regressions. */
public final class YamlClassProfileRepositoryRegressionSuite {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);
    private static int assertions;

    private YamlClassProfileRepositoryRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        firstSaveAndMonotonicCasPersistExactlyOnce();
        staleAndSkippedRevisionsCannotReplaceTheAuthoritativeProfile();
        sameInstanceConcurrentSavesAreSerialized();
        twoRepositoryInstancesPerformOnePlayerCasAtomically();
        differentPlayersAreNotGloballySerialized();
        failedWritePreservesTheDurableAndCachedProfile();
        ownerBindingRejectsRenamedAndSubstitutedProfiles();
        strictYamlRejectsTypeConfusionAndShapeChanges();
        corruptLoadIsQuarantinedWithoutReplacingTheOriginal();
        explicitRecoveryPreservesEvidenceAndIsIdempotent();
        sameMillisecondQuarantinesPreserveEveryPayload();
        flushesAreAcceptedWorkBarriers();
        closeDrainsAcceptedWorkAndRejectsLateWork();
        boundedShutdownInterruptsStuckIoAndRejectsNewWork();
        System.out.println("YamlClassProfileRepository regression tests passed. assertions=" + assertions);
    }

    private static void firstSaveAndMonotonicCasPersistExactlyOnce() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-first-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = id(101);
            final ClassProfile revisionZero = ClassProfile.empty(playerId, 0L);
            check(await(repository.save(playerId, ClassProfileRepository.MISSING_REVISION,
                    revisionZero)).equals(revisionZero), "-1 -> 0 must commit the first profile");
            check(repository.cached(playerId).orElseThrow().revision() == 0L,
                    "first save must publish revision zero only after durable write");
            check(writer.writeCount() == 1, "first CAS must write once");

            final ClassProfile revisionOne = ClassProfile.empty(playerId, 1L);
            await(repository.save(playerId, 0L, revisionOne));
            repository.invalidate(playerId);
            check(await(repository.load(playerId)).profile().equals(revisionOne),
                    "n -> n+1 must survive a disk reload");
            check(writer.writeCount() == 2, "each committed CAS must write once");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void staleAndSkippedRevisionsCannotReplaceTheAuthoritativeProfile() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-conflict-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = id(102);
            final ClassProfile authority = ClassProfile.empty(playerId, 0L);
            await(repository.save(playerId, -1L, authority));

            final ProfileRepositoryException.RevisionConflict stale = expectStageFailure(
                    ProfileRepositoryException.RevisionConflict.class,
                    repository.save(playerId, -1L, ClassProfile.empty(playerId, 0L)));
            check(stale.expected() == -1L && stale.actual() == 0L,
                    "stale expected revision must expose expected and authority revisions");
            final ProfileRepositoryException.RevisionConflict skipped = expectStageFailure(
                    ProfileRepositoryException.RevisionConflict.class,
                    repository.save(playerId, 0L, ClassProfile.empty(playerId, 2L)));
            check(skipped.expected() == 1L && skipped.actual() == 2L,
                    "skipped candidate revision must be rejected");
            check(writer.writeCount() == 1, "conflicts must not reach durable storage");
            repository.invalidate(playerId);
            check(await(repository.load(playerId)).profile().equals(authority),
                    "conflicts must leave the durable authority unchanged");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void sameInstanceConcurrentSavesAreSerialized() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-concurrent-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = id(103);
            await(repository.save(playerId, -1L, ClassProfile.empty(playerId, 0L)));
            writer.blockNextWrites(1);
            final CompletionStage<ClassProfile> first = repository.save(
                    playerId, 0L, ClassProfile.empty(playerId, 1L));
            writer.awaitBlockedWrites();
            final CompletionStage<ClassProfile> second = repository.save(
                    playerId, 1L, ClassProfile.empty(playerId, 2L));
            check(!second.toCompletableFuture().isDone(),
                    "later same-player save must remain behind the in-flight save");
            writer.releaseBlockedWrites();
            check(await(first).revision() == 1L && await(second).revision() == 2L,
                    "same-instance queue must commit in submission order");
            check(writer.maximumConcurrentWrites() == 1,
                    "same player must have one write critical section");
        } finally {
            writer.releaseBlockedWrites();
            repository.close();
            deleteTree(directory);
        }
    }

    private static void twoRepositoryInstancesPerformOnePlayerCasAtomically() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-cross-instance-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository firstRepository = repository(directory, writer);
        final YamlClassProfileRepository secondRepository = repository(directory, writer);
        final ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            firstRepository.load();
            secondRepository.load();
            final UUID playerId = id(104);
            await(firstRepository.save(playerId, -1L, ClassProfile.empty(playerId, 0L)));
            firstRepository.invalidate(playerId);
            secondRepository.invalidate(playerId);
            final CountDownLatch start = new CountDownLatch(1);
            final CompletableFuture<Object> a = CompletableFuture.supplyAsync(
                    () -> raceSave(firstRepository, playerId, "a", start), callers);
            final CompletableFuture<Object> b = CompletableFuture.supplyAsync(
                    () -> raceSave(secondRepository, playerId, "b", start), callers);
            start.countDown();
            final Object resultA = a.join();
            final Object resultB = b.join();
            check((resultA instanceof ClassProfile) != (resultB instanceof ClassProfile),
                    "exactly one cross-instance CAS candidate must commit");
            check((resultA instanceof ProfileRepositoryException.RevisionConflict)
                            != (resultB instanceof ProfileRepositoryException.RevisionConflict),
                    "the losing cross-instance CAS must receive a revision conflict");
            firstRepository.invalidate(playerId);
            secondRepository.invalidate(playerId);
            check(await(firstRepository.load(playerId)).profile().revision() == 1L,
                    "cross-instance race must leave exactly revision one durable");
            check(await(secondRepository.load(playerId)).profile().revision() == 1L,
                    "both instances must reload the same winner");
            check(writer.writeCount() == 2,
                    "one initial and one winning cross-instance write are permitted");
        } finally {
            callers.shutdownNow();
            firstRepository.close();
            secondRepository.close();
            deleteTree(directory);
        }
    }

    private static Object raceSave(final YamlClassProfileRepository repository, final UUID playerId,
                                   final String marker, final CountDownLatch start) {
        try {
            awaitLatch(start, "cross-instance start barrier " + marker);
            return await(repository.save(playerId, 0L, ClassProfile.empty(playerId, 1L)));
        } catch (final CompletionException failure) {
            return unwrap(failure);
        }
    }

    private static void differentPlayersAreNotGloballySerialized() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-independent-players-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository firstRepository = repository(directory, writer);
        final YamlClassProfileRepository secondRepository = repository(directory, writer);
        try {
            firstRepository.load();
            secondRepository.load();
            writer.blockNextWrites(2);
            final CompletionStage<ClassProfile> first = firstRepository.save(id(105), -1L,
                    ClassProfile.empty(id(105), 0L));
            final CompletionStage<ClassProfile> second = secondRepository.save(id(106), -1L,
                    ClassProfile.empty(id(106), 0L));
            writer.awaitBlockedWrites();
            check(writer.maximumConcurrentWrites() >= 2,
                    "different players must not be serialized by one global JVM lock");
            writer.releaseBlockedWrites();
            check(await(first).ownerId().equals(id(105)) && await(second).ownerId().equals(id(106)),
                    "independent player writes must both commit");
        } finally {
            writer.releaseBlockedWrites();
            firstRepository.close();
            secondRepository.close();
            deleteTree(directory);
        }
    }

    private static void failedWritePreservesTheDurableAndCachedProfile() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-failure-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = id(107);
            final ClassProfile authority = ClassProfile.empty(playerId, 0L);
            await(repository.save(playerId, -1L, authority));
            writer.failNextWrite();
            expectStageFailure(ProfileRepositoryException.class,
                    repository.save(playerId, 0L, ClassProfile.empty(playerId, 1L)));
            check(repository.cached(playerId).orElseThrow().equals(authority),
                    "failed candidate must not replace cache authority");
            check(repository.sessionBlockReason(playerId).isPresent(),
                    "failed durable write must fail-close the session");
            repository.invalidate(playerId);
            check(await(repository.load(playerId)).profile().equals(authority),
                    "failed replacement must leave prior file intact");
            check(repository.sessionBlockReason(playerId).isEmpty(),
                    "successful authoritative reload may clear the persistence block");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void ownerBindingRejectsRenamedAndSubstitutedProfiles() throws Exception {
        final UUID expected = id(108);
        final UUID foreign = id(109);
        ownerCase("renamed-file", expected, foreign, foreign);
        ownerCase("wrong-envelope-owner", expected, foreign, expected);
        ownerCase("foreign-payload", expected, expected, foreign);

        final Path directory = Files.createTempDirectory("icesmp-profile-owner-happy-");
        final YamlClassProfileRepository repository = repository(directory, new RecordingWriter());
        try {
            repository.load();
            writeEnvelope(directory, expected, expected, ClassProfile.empty(expected, 0L), null);
            final ClassProfileRepository.LoadResult result = await(repository.load(expected));
            check(result.status() == ClassProfileRepository.Status.FOUND
                            && result.profile().ownerId().equals(expected),
                    "matching file, envelope and payload owner must load");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void ownerCase(final String label, final UUID fileOwner,
                                  final UUID envelopeOwner, final UUID payloadOwner) throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-owner-" + label + "-");
        final YamlClassProfileRepository repository = repository(directory, new RecordingWriter());
        try {
            repository.load();
            final ClassProfile payload = ClassProfile.empty(payloadOwner, 0L);
            writeEnvelope(directory, fileOwner, envelopeOwner, payload, null);
            final ClassProfileRepository.LoadResult result = await(repository.load(fileOwner));
            check(result.status() == ClassProfileRepository.Status.QUARANTINED,
                    label + " must be quarantined");
            check(repository.cached(fileOwner).isEmpty(), label + " must not publish cache state");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void strictYamlRejectsTypeConfusionAndShapeChanges() throws Exception {
        final UUID playerId = id(110);
        final ClassProfileCodec codec = new ClassProfileCodec();
        final byte[] payload = codec.encode(ClassProfile.empty(playerId, 0L));
        final String digest = ClassProfileCodec.digestHex(payload);
        final String encoded = Base64.getEncoder().encodeToString(payload);
        final List<String> malformed = List.of(
                "format: ICS2-YAML-2\nowner: " + playerId + "\nschema: 2\nrevision: 0.0\ndigest: " + digest + "\npayload: " + encoded + "\n",
                "format: ICS2-YAML-2\nowner: true\nschema: 2\nrevision: 0\ndigest: " + digest + "\npayload: " + encoded + "\n",
                "format: ICS2-YAML-2\nowner: " + playerId + "\nschema: '2'\nrevision: 0\ndigest: " + digest + "\npayload: " + encoded + "\n",
                "format: ICS2-YAML-2\nowner: " + playerId + "\nschema: 2\nrevision: -1\ndigest: " + digest + "\npayload: " + encoded + "\n",
                "format: ICS2-YAML-2\nowner: " + playerId + "\nschema: 2\nrevision: 0\ndigest: " + digest + "\npayload: true\n",
                "format: ICS2-YAML-2\nowner: " + playerId + "\nschema: 2\nrevision: 0\ndigest: " + digest + "\npayload: " + encoded + "\nextra: forbidden\n",
                "format: ICS2-YAML-2\nowner: " + playerId + "\nschema: 2\nrevision: 0\npayload: " + encoded + "\n");
        int index = 0;
        for (final String yaml : malformed) {
            final Path directory = Files.createTempDirectory("icesmp-profile-yaml-type-" + index++ + "-");
            final YamlClassProfileRepository repository = repository(directory, new RecordingWriter());
            try {
                repository.load();
                Files.writeString(directory.resolve(playerId + ".yml"), yaml, StandardCharsets.UTF_8);
                final ClassProfileRepository.LoadResult result = await(repository.load(playerId));
                check(result.status() == ClassProfileRepository.Status.QUARANTINED,
                        "malformed/type-confused envelope must quarantine");
                check(repository.cached(playerId).isEmpty(),
                        "malformed envelope must not create partial profile state");
            } finally {
                repository.close();
                deleteTree(directory);
            }
        }
    }

    private static void corruptLoadIsQuarantinedWithoutReplacingTheOriginal() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-quarantine-");
        final YamlClassProfileRepository repository = repository(directory, new RecordingWriter());
        try {
            repository.load();
            final UUID playerId = id(111);
            final byte[] original = "format: ICS2-YAML-2\npayload: 'invalid'\n"
                    .getBytes(StandardCharsets.UTF_8);
            final Path profileFile = directory.resolve(playerId + ".yml");
            Files.write(profileFile, original);
            final ClassProfileRepository.LoadResult result = await(repository.load(playerId));
            check(result.status() == ClassProfileRepository.Status.QUARANTINED,
                    "corrupt envelope must return quarantine status");
            check(repository.sessionBlockReason(playerId).isPresent(),
                    "quarantine must fail-close the session");
            check(java.util.Arrays.equals(Files.readAllBytes(profileFile), original),
                    "quarantine must preserve original source bytes");
            final Path evidence = findEvidence(directory, result.evidenceId());
            check(Files.readString(evidence).contains(Base64.getEncoder().encodeToString(original)),
                    "evidence must retain exact original bytes");
            expectStageFailure(ProfileRepositoryException.class,
                    repository.save(playerId, -1L, ClassProfile.empty(playerId, 0L)));
            check(java.util.Arrays.equals(Files.readAllBytes(profileFile), original),
                    "normal CAS must not overwrite quarantined data");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void explicitRecoveryPreservesEvidenceAndIsIdempotent() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-recovery-");
        final YamlClassProfileRepository repository = repository(directory, new RecordingWriter());
        try {
            repository.load();
            final UUID playerId = id(112);
            Files.writeString(directory.resolve(playerId + ".yml"), "not: a-profile\n");
            final ClassProfileRepository.LoadResult quarantine = await(repository.load(playerId));
            final Path evidence = findEvidence(directory, quarantine.evidenceId());
            final byte[] evidenceBefore = Files.readAllBytes(evidence);
            final ClassProfile recovered = await(repository.recover(
                    playerId, quarantine.evidenceId(), "admin:test-recovery"));
            check(recovered.ownerId().equals(playerId) && recovered.revision() == 0L,
                    "recovery must create owner-bound revision zero");
            check(recovered.status() == ProfileStatus.READY && recovered.activeSlot() == null,
                    "recovery must create a clean inactive profile");
            check(recovered.diagnostics().recoveryAuditId().equals("admin:test-recovery"),
                    "recovery audit id must be durable");
            check(java.util.Arrays.equals(evidenceBefore, Files.readAllBytes(evidence)),
                    "recovery must preserve the original evidence artifact");
            final ClassProfile retry = await(repository.recover(
                    playerId, quarantine.evidenceId(), "admin:test-recovery"));
            check(retry.equals(recovered), "same recovery audit id must be idempotent");
            expectStageFailure(ProfileRepositoryException.class,
                    repository.recover(playerId, quarantine.evidenceId(), "admin:other"));
            repository.invalidate(playerId);
            check(await(repository.load(playerId)).profile().equals(recovered),
                    "recovered profile must survive restart-like reload");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void sameMillisecondQuarantinesPreserveEveryPayload() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-quarantine-sequence-");
        final YamlClassProfileRepository repository = repository(directory, new RecordingWriter());
        try {
            repository.load();
            final UUID playerId = id(113);
            final byte[] firstPayload = "first-corrupt-payload".getBytes(StandardCharsets.UTF_8);
            final byte[] secondPayload = "second-corrupt-payload".getBytes(StandardCharsets.UTF_8);
            final ClassProfileRepository.QuarantineRecord first = await(repository.quarantine(
                    playerId, firstPayload, "first failure"));
            final ClassProfileRepository.QuarantineRecord second = await(repository.quarantine(
                    playerId, secondPayload, "second failure"));
            check(first.createdAtEpochMillis() == second.createdAtEpochMillis(),
                    "fixed clock must exercise same-millisecond evidence generation");
            check(!first.fileName().equals(second.fileName()),
                    "same-millisecond evidence filenames must be unique");
            check(Files.readString(directory.resolve("quarantine").resolve(first.fileName()))
                            .contains(Base64.getEncoder().encodeToString(firstPayload)),
                    "first evidence must remain intact");
            check(Files.readString(directory.resolve("quarantine").resolve(second.fileName()))
                            .contains(Base64.getEncoder().encodeToString(secondPayload)),
                    "second evidence must retain its own bytes");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void flushesAreAcceptedWorkBarriers() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-flush-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = id(114);
            writer.blockNextWrites(1);
            final CompletionStage<ClassProfile> save = repository.save(
                    playerId, -1L, ClassProfile.empty(playerId, 0L));
            writer.awaitBlockedWrites();
            final CompletionStage<Void> playerFlush = repository.flush(playerId);
            final CompletionStage<Void> globalFlush = repository.flushAll();
            check(!playerFlush.toCompletableFuture().isDone()
                            && !globalFlush.toCompletableFuture().isDone(),
                    "flush barriers must wait for accepted writes");
            writer.releaseBlockedWrites();
            await(save);
            await(playerFlush);
            await(globalFlush);
            check(repository.cached(playerId).orElseThrow().revision() == 0L,
                    "completed barriers must observe durable authority");
        } finally {
            writer.releaseBlockedWrites();
            repository.close();
            deleteTree(directory);
        }
    }

    private static void closeDrainsAcceptedWorkAndRejectsLateWork() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-close-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        repository.load();
        final UUID playerId = id(115);
        writer.blockNextWrites(1);
        final CompletionStage<ClassProfile> accepted = repository.save(
                playerId, -1L, ClassProfile.empty(playerId, 0L));
        writer.awaitBlockedWrites();
        final CountDownLatch closeReturned = new CountDownLatch(1);
        final Thread closer = new Thread(() -> {
            repository.close();
            closeReturned.countDown();
        }, "profile-repository-close-test");
        closer.start();
        check(!closeReturned.await(100L, TimeUnit.MILLISECONDS),
                "close must drain accepted work before returning");
        writer.releaseBlockedWrites();
        check(closeReturned.await(5L, TimeUnit.SECONDS), "close must finish after writer drains");
        closer.join(5_000L);
        check(!closer.isAlive() && await(accepted).revision() == 0L,
                "accepted work must complete before close returns");
        expectStageFailure(ProfileRepositoryException.class, repository.load(playerId));
        expectStageFailure(ProfileRepositoryException.class, repository.flushAll());
        repository.close();
        deleteTree(directory);
    }

    private static void boundedShutdownInterruptsStuckIoAndRejectsNewWork() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-timeout-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        repository.load();
        final UUID playerId = id(116);
        writer.blockNextWrites(1);
        final CompletionStage<ClassProfile> accepted = repository.save(
                playerId, -1L, ClassProfile.empty(playerId, 0L));
        writer.awaitBlockedWrites();
        final long started = System.nanoTime();
        final ClassProfileRepository.ShutdownResult result = await(
                repository.shutdown(Duration.ofMillis(75L)));
        final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        check(!result.drained() && result.pendingOperations() >= 1,
                "stuck I/O must produce an observable non-drained shutdown result");
        check(elapsedMillis < 2_000L, "shutdown timeout must be bounded");
        expectStageFailure(ProfileRepositoryException.class, repository.load(id(117)));
        expectStageFailure(Throwable.class, accepted);
        writer.releaseBlockedWrites();
        deleteTree(directory);
    }

    private static void writeEnvelope(final Path directory, final UUID fileOwner,
                                      final UUID envelopeOwner, final ClassProfile profile,
                                      final Object revisionOverride) throws IOException {
        final ClassProfileCodec codec = new ClassProfileCodec();
        final byte[] payload = codec.encode(profile);
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format", "ICS2-YAML-2");
        yaml.set("owner", envelopeOwner.toString());
        yaml.set("schema", profile.schemaVersion());
        yaml.set("revision", revisionOverride == null ? profile.revision() : revisionOverride);
        yaml.set("digest", ClassProfileCodec.digestHex(payload));
        yaml.set("payload", Base64.getEncoder().encodeToString(payload));
        Files.writeString(directory.resolve(fileOwner + ".yml"), yaml.saveToString(), StandardCharsets.UTF_8);
    }

    private static Path findEvidence(final Path directory, final String evidenceId) throws IOException {
        try (var files = Files.list(directory.resolve("quarantine"))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .filter(path -> !path.getFileName().toString().endsWith(".marker.yml"))
                    .filter(path -> {
                        try {
                            final YamlConfiguration yaml = new YamlConfiguration();
                            yaml.load(path.toFile());
                            return evidenceId.equals(yaml.getString("evidence-id"));
                        } catch (final Exception failure) {
                            throw new CompletionException(failure);
                        }
                    }).findFirst().orElseThrow();
        }
    }

    private static YamlClassProfileRepository repository(final Path directory,
                                                          final RecordingWriter writer) {
        final Logger logger = Logger.getLogger("profile-v2-regression-" + directory.getFileName());
        logger.setLevel(Level.OFF);
        return new YamlClassProfileRepository(directory.toFile(), logger, new ClassProfileCodec(),
                writer, FIXED_CLOCK);
    }

    private static UUID id(final int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }

    private static <T> T await(final CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static void awaitLatch(final CountDownLatch latch, final String label) {
        try {
            check(latch.await(5L, TimeUnit.SECONDS), label + " timed out");
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(label + " interrupted", interrupted);
        }
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T extends Throwable> T expectStageFailure(final Class<T> type,
                                                               final CompletionStage<?> stage) {
        try {
            stage.toCompletableFuture().join();
        } catch (final Throwable failure) {
            final Throwable cause = unwrap(failure);
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
            if (type == Throwable.class) {
                return type.cast(cause);
            }
            throw new AssertionError("Expected " + type.getName() + " but got " + cause, cause);
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown");
    }

    private static void deleteTree(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingWriter implements YamlClassProfileRepository.AtomicWriter {
        private final AtomicInteger writeCount = new AtomicInteger();
        private final AtomicInteger activeWrites = new AtomicInteger();
        private final AtomicInteger maximumConcurrentWrites = new AtomicInteger();
        private final AtomicBoolean failNext = new AtomicBoolean();
        private volatile AtomicInteger blockingWrites = new AtomicInteger();
        private volatile CountDownLatch blockedWriteEntered;
        private volatile CountDownLatch blockedWriteRelease;

        @Override
        public void write(final File file, final YamlConfiguration yaml) throws IOException {
            writeCount.incrementAndGet();
            final int active = activeWrites.incrementAndGet();
            maximumConcurrentWrites.accumulateAndGet(active, Math::max);
            try {
                final CountDownLatch entered = blockedWriteEntered;
                final CountDownLatch release = blockedWriteRelease;
                if (entered != null && release != null && blockingWrites.getAndDecrement() > 0) {
                    entered.countDown();
                    try {
                        if (!release.await(5L, TimeUnit.SECONDS)) {
                            throw new IOException("timed out waiting to release regression writer");
                        }
                    } catch (final InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("regression writer interrupted", interrupted);
                    }
                }
                if (failNext.compareAndSet(true, false)) {
                    throw new IOException("injected durable write failure");
                }
                YamlStore.saveAtomic(file, yaml);
            } finally {
                activeWrites.decrementAndGet();
            }
        }

        void blockNextWrites(final int count) {
            check(count > 0, "blocking writer count must be positive");
            blockingWrites = new AtomicInteger(count);
            blockedWriteEntered = new CountDownLatch(count);
            blockedWriteRelease = new CountDownLatch(1);
        }

        void awaitBlockedWrites() {
            final CountDownLatch entered = blockedWriteEntered;
            check(entered != null, "blocking writer was not armed");
            awaitLatch(entered, "expected blocked writer calls");
        }

        void releaseBlockedWrites() {
            final CountDownLatch release = blockedWriteRelease;
            if (release != null) {
                release.countDown();
            }
        }

        void failNextWrite() {
            failNext.set(true);
        }

        int writeCount() {
            return writeCount.get();
        }

        int maximumConcurrentWrites() {
            return maximumConcurrentWrites.get();
        }
    }
}
