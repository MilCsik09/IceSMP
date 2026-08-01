package hu.taliann.icesmp.classspec.persistence;

import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Focused, executable regressions for Profile v2 CAS durability and lifecycle barriers. */
public final class YamlClassProfileRepositoryRegressionSuite {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);

    private YamlClassProfileRepositoryRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        firstSaveAndMonotonicCasPersistExactlyOnce();
        staleAndSkippedRevisionsCannotReplaceTheAuthoritativeProfile();
        concurrentSavesAreSerializedInSubmissionOrder();
        failedWritePreservesTheDurableAndCachedProfile();
        corruptLoadIsQuarantinedWithoutReplacingTheOriginal();
        sameMillisecondQuarantinesPreserveEveryPayload();
        flushesAreQueueBarriers();
        closeDrainsAcceptedWorkAndRejectsLateWork();
        System.out.println("YamlClassProfileRepository regression tests passed.");
    }

    private static void firstSaveAndMonotonicCasPersistExactlyOnce() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-first-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
            final ClassProfile revisionZero = ClassProfile.empty(0L);

            check(await(repository.save(playerId, ClassProfileRepository.MISSING_REVISION,
                            revisionZero)).equals(revisionZero),
                    "the -1 -> 0 save must return the committed profile");
            check(repository.cached(playerId).orElseThrow().revision() == 0L,
                    "the first successful save must publish revision zero to the cache");
            check(writer.writeCount() == 1, "the first CAS must perform exactly one durable write");

            final ClassProfile revisionOne = ClassProfile.empty(1L);
            await(repository.save(playerId, 0L, revisionOne));
            check(repository.cached(playerId).orElseThrow().revision() == 1L,
                    "a normal CAS must publish n + 1");
            repository.invalidate(playerId);
            final ClassProfile reloaded = await(repository.load(playerId)).profile();
            check(reloaded.equals(revisionOne), "the n -> n + 1 profile must survive cache invalidation");
            check(writer.writeCount() == 2, "each successful CAS must write exactly once");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void staleAndSkippedRevisionsCannotReplaceTheAuthoritativeProfile()
            throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-conflict-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000102");
            final ClassProfile revisionZero = ClassProfile.empty(0L);
            await(repository.save(playerId, -1L, revisionZero));

            final ProfileRepositoryException.RevisionConflict stale = expectStageFailure(
                    ProfileRepositoryException.RevisionConflict.class,
                    repository.save(playerId, -1L, ClassProfile.empty(0L)));
            check(stale.expected() == -1L && stale.actual() == 0L,
                    "a stale expected revision must report expected and authoritative revisions");
            check(writer.writeCount() == 1, "a stale CAS must not reach durable storage");
            check(repository.cached(playerId).orElseThrow().equals(revisionZero),
                    "a stale CAS must not replace the cached authority");

            final ProfileRepositoryException.RevisionConflict skipped = expectStageFailure(
                    ProfileRepositoryException.RevisionConflict.class,
                    repository.save(playerId, 0L, ClassProfile.empty(2L)));
            check(skipped.expected() == 1L && skipped.actual() == 2L,
                    "a skipped candidate revision must report required and supplied revisions");
            check(writer.writeCount() == 1, "a skipped revision must not reach durable storage");

            repository.invalidate(playerId);
            check(await(repository.load(playerId)).profile().equals(revisionZero),
                    "conflicting writes must leave the durable profile unchanged");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void concurrentSavesAreSerializedInSubmissionOrder() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-concurrent-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000103");
            await(repository.save(playerId, -1L, ClassProfile.empty(0L)));

            writer.blockNextWrite();
            final CompletionStage<ClassProfile> first = repository.save(
                    playerId, 0L, ClassProfile.empty(1L));
            writer.awaitBlockedWrite();
            final CompletionStage<ClassProfile> second = repository.save(
                    playerId, 1L, ClassProfile.empty(2L));

            check(!second.toCompletableFuture().isDone(),
                    "a later save must remain queued behind an in-flight save");
            check(writer.writeCount() == 2,
                    "the later save must not enter the writer while the first save is blocked");
            writer.releaseBlockedWrite();
            check(await(first).revision() == 1L, "the first concurrent save must commit revision one");
            check(await(second).revision() == 2L, "the queued save must observe and commit revision two");
            check(writer.maximumConcurrentWrites() == 1,
                    "repository writes must have a single serialization point");

            repository.invalidate(playerId);
            check(await(repository.load(playerId)).profile().revision() == 2L,
                    "an older asynchronous write must never publish after a newer one");
        } finally {
            writer.releaseBlockedWrite();
            repository.close();
            deleteTree(directory);
        }
    }

    private static void failedWritePreservesTheDurableAndCachedProfile() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-failure-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000104");
            final ClassProfile authority = ClassProfile.empty(0L);
            await(repository.save(playerId, -1L, authority));
            writer.failNextWrite();

            final ProfileRepositoryException failure = expectStageFailure(
                    ProfileRepositoryException.class,
                    repository.save(playerId, 0L, ClassProfile.empty(1L)));
            check(failure.getMessage().contains("mentési hiba"),
                    "a caller must receive a persistence-specific save failure");
            check(repository.cached(playerId).orElseThrow().equals(authority),
                    "a failed candidate must not become authoritative in the cache");
            check(repository.sessionBlockReason(playerId).isPresent(),
                    "a failed durable write must block class/spec mutations for the session");

            check(await(repository.load(playerId)).profile().equals(authority),
                    "a cache hit may expose only the previously committed authority");
            check(repository.sessionBlockReason(playerId).isPresent(),
                    "a cache hit must not masquerade as a successful persistence recovery");

            repository.invalidate(playerId);
            check(await(repository.load(playerId)).profile().equals(authority),
                    "a failed replacement must leave the prior durable profile intact");
            check(repository.sessionBlockReason(playerId).isEmpty(),
                    "a successful disk reload may clear the persistence session block");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void sameMillisecondQuarantinesPreserveEveryPayload() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-quarantine-sequence-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000108");
            final byte[] firstPayload = "first-corrupt-payload".getBytes(StandardCharsets.UTF_8);
            final byte[] secondPayload = "second-corrupt-payload".getBytes(StandardCharsets.UTF_8);

            final ClassProfileRepository.QuarantineRecord first = await(repository.quarantine(
                    playerId, firstPayload, "first failure"));
            final ClassProfileRepository.QuarantineRecord second = await(repository.quarantine(
                    playerId, secondPayload, "second failure"));
            check(first.createdAtEpochMillis() == second.createdAtEpochMillis(),
                    "the fixed clock must exercise the same-millisecond collision path");
            check(!first.fileName().equals(second.fileName()),
                    "same-millisecond quarantine records must receive unique filenames");

            final String firstEvidence = Files.readString(
                    directory.resolve("quarantine").resolve(first.fileName()));
            final String secondEvidence = Files.readString(
                    directory.resolve("quarantine").resolve(second.fileName()));
            check(firstEvidence.contains(Base64.getEncoder().encodeToString(firstPayload)),
                    "the first recovery artifact must not be overwritten by a later quarantine");
            check(secondEvidence.contains(Base64.getEncoder().encodeToString(secondPayload)),
                    "the later recovery artifact must preserve its own exact payload");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void corruptLoadIsQuarantinedWithoutReplacingTheOriginal() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-quarantine-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000105");
            final byte[] original = ("format: ICS2\npayload: 'not valid base64!'\n")
                    .getBytes(StandardCharsets.UTF_8);
            final Path profileFile = directory.resolve(playerId + ".yml");
            Files.write(profileFile, original);

            final ClassProfileRepository.LoadResult result = await(repository.load(playerId));
            check(result.status() == ClassProfileRepository.Status.QUARANTINED,
                    "a corrupt envelope must return a quarantined load result");
            check(result.diagnostic().contains("Sérült Profile v2 envelope"),
                    "the quarantined load must expose an operator diagnostic");
            check(repository.cached(playerId).isEmpty(),
                    "quarantined bytes must never create a partially active cached profile");
            check(repository.sessionBlockReason(playerId).isPresent(),
                    "a quarantined profile must block its class/spec session");
            check(repository.quarantineReason(playerId).orElseThrow().contains("Sérült"),
                    "quarantine diagnostics must remain available to /spec info");
            check(java.util.Arrays.equals(Files.readAllBytes(profileFile), original),
                    "quarantine must preserve the authoritative source bytes in place");

            final List<Path> evidence;
            try (var files = Files.list(directory.resolve("quarantine"))) {
                evidence = files.filter(Files::isRegularFile).toList();
            }
            check(evidence.size() == 1, "a corrupt load must create one recovery artifact");
            final String quarantineText = Files.readString(evidence.get(0));
            check(quarantineText.contains(Base64.getEncoder().encodeToString(original)),
                    "the recovery artifact must preserve the exact original payload");

            expectStageFailure(ProfileRepositoryException.class,
                    repository.save(playerId, -1L, ClassProfile.empty(0L)));
            check(java.util.Arrays.equals(Files.readAllBytes(profileFile), original),
                    "normal CAS must not overwrite a quarantined source profile");
        } finally {
            repository.close();
            deleteTree(directory);
        }
    }

    private static void flushesAreQueueBarriers() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-flush-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        try {
            repository.load();
            final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000106");
            writer.blockNextWrite();
            final CompletionStage<ClassProfile> save = repository.save(
                    playerId, -1L, ClassProfile.empty(0L));
            writer.awaitBlockedWrite();
            final CompletionStage<Void> playerFlush = repository.flush(playerId);
            final CompletionStage<Void> globalFlush = repository.flushAll();

            check(!playerFlush.toCompletableFuture().isDone(),
                    "logout flush must wait for an already accepted player write");
            check(!globalFlush.toCompletableFuture().isDone(),
                    "flushAll must wait for all already accepted writes");
            writer.releaseBlockedWrite();
            await(save);
            await(playerFlush);
            await(globalFlush);
            check(repository.cached(playerId).orElseThrow().revision() == 0L,
                    "a completed flush barrier must observe the committed profile");
        } finally {
            writer.releaseBlockedWrite();
            repository.close();
            deleteTree(directory);
        }
    }

    private static void closeDrainsAcceptedWorkAndRejectsLateWork() throws Exception {
        final Path directory = Files.createTempDirectory("icesmp-profile-close-");
        final RecordingWriter writer = new RecordingWriter();
        final YamlClassProfileRepository repository = repository(directory, writer);
        repository.load();
        final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000107");
        writer.blockNextWrite();
        final CompletionStage<ClassProfile> accepted = repository.save(
                playerId, -1L, ClassProfile.empty(0L));
        writer.awaitBlockedWrite();

        final CountDownLatch closeReturned = new CountDownLatch(1);
        final Thread closer = new Thread(() -> {
            repository.close();
            closeReturned.countDown();
        }, "profile-repository-close-test");
        closer.start();
        check(!closeReturned.await(100L, TimeUnit.MILLISECONDS),
                "close must drain an accepted write before returning");
        writer.releaseBlockedWrite();
        check(closeReturned.await(5L, TimeUnit.SECONDS), "close must finish after the writer drains");
        closer.join(5_000L);
        check(!closer.isAlive(), "the repository close thread must terminate");
        check(await(accepted).revision() == 0L, "accepted work must complete before close returns");
        check(repository.cached(playerId).isEmpty(), "close must release the session cache");

        expectStageFailure(ProfileRepositoryException.class, repository.load(playerId));
        expectStageFailure(ProfileRepositoryException.class, repository.flushAll());
        repository.close();
        deleteTree(directory);
    }

    private static YamlClassProfileRepository repository(final Path directory,
                                                          final RecordingWriter writer) {
        final Logger logger = Logger.getLogger("profile-v2-regression-" + directory.getFileName());
        logger.setLevel(Level.OFF);
        return new YamlClassProfileRepository(directory.toFile(), logger, new ClassProfileCodec(),
                writer, FIXED_CLOCK);
    }

    private static <T> T await(final CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static <T extends Throwable> T expectStageFailure(final Class<T> type,
                                                               final CompletionStage<?> stage) {
        try {
            stage.toCompletableFuture().join();
        } catch (final CompletionException failure) {
            Throwable cause = failure.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (type.isInstance(cause)) {
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
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingWriter implements YamlClassProfileRepository.AtomicWriter {
        private final AtomicInteger writeCount = new AtomicInteger();
        private final AtomicInteger activeWrites = new AtomicInteger();
        private final AtomicInteger maximumConcurrentWrites = new AtomicInteger();
        private final AtomicBoolean failNext = new AtomicBoolean();
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
                if (entered != null && release != null && entered.getCount() != 0L) {
                    entered.countDown();
                    try {
                        if (!release.await(5L, TimeUnit.SECONDS)) {
                            throw new IOException("timed out waiting to release regression writer");
                        }
                    } catch (final InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("regression writer interrupted", interrupted);
                    } finally {
                        blockedWriteEntered = null;
                        blockedWriteRelease = null;
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

        void blockNextWrite() {
            blockedWriteEntered = new CountDownLatch(1);
            blockedWriteRelease = new CountDownLatch(1);
        }

        void awaitBlockedWrite() throws InterruptedException {
            final CountDownLatch entered = blockedWriteEntered;
            check(entered != null && entered.await(5L, TimeUnit.SECONDS),
                    "the expected blocking writer call never started");
        }

        void releaseBlockedWrite() {
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
