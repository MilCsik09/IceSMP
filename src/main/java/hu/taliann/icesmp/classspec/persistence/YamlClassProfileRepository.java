package hu.taliann.icesmp.classspec.persistence;

import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Per-player YAML envelope around the deterministic ICS2 payload. A single I/O executor is the
 * serialization point for all critical mutations, so an older asynchronous write cannot publish
 * after a newer one. Files are atomically replaced through {@link YamlStore}.
 */
public final class YamlClassProfileRepository implements ClassProfileRepository, PersistentStore, AutoCloseable {

    private static final String ENVELOPE_FORMAT = "ICS2";

    private final File profileDirectory;
    private final File quarantineDirectory;
    private final Logger logger;
    private final ClassProfileCodec codec;
    private final AtomicWriter writer;
    private final Clock clock;
    private final ExecutorService ioExecutor;
    private final Object lifecycleLock = new Object();
    private final Map<UUID, ClassProfile> cache = new ConcurrentHashMap<>();
    private final Map<UUID, String> sessionBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, String> quarantineReasons = new ConcurrentHashMap<>();
    private final AtomicLong quarantineSequence = new AtomicLong();
    private volatile boolean loaded;
    private volatile boolean accepting = true;

    public YamlClassProfileRepository(final File dataFolder, final Logger logger) {
        this(new File(Objects.requireNonNull(dataFolder, "dataFolder"), "class-profiles-v2"),
                Objects.requireNonNull(logger, "logger"), new ClassProfileCodec(),
                YamlStore::saveAtomic, Clock.systemUTC());
    }

    YamlClassProfileRepository(final File profileDirectory, final Logger logger,
                               final ClassProfileCodec codec, final AtomicWriter writer,
                               final Clock clock) {
        this.profileDirectory = Objects.requireNonNull(profileDirectory, "profileDirectory");
        this.quarantineDirectory = new File(profileDirectory, "quarantine");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.clock = Objects.requireNonNull(clock, "clock");
        final AtomicInteger sequence = new AtomicInteger();
        this.ioExecutor = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task,
                    "IceSMP-profile-v2-io-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void load() {
        try {
            Files.createDirectories(profileDirectory.toPath());
            Files.createDirectories(quarantineDirectory.toPath());
            loaded = true;
        } catch (final IOException failure) {
            throw new ProfileRepositoryException("Profile v2 könyvtár nem hozható létre", failure);
        }
    }

    @Override
    public void save() {
        await(flushAll());
    }

    @Override
    public CompletionStage<LoadResult> load(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return submit(() -> loadAuthoritative(playerId, true));
    }

    @Override
    public CompletionStage<ClassProfile> save(final UUID playerId, final long expectedRevision,
                                               final ClassProfile nextProfile) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(nextProfile, "nextProfile");
        return submit(() -> {
            final LoadResult currentResult = loadAuthoritative(playerId, false);
            if (currentResult.status() == Status.QUARANTINED) {
                throw block(playerId, "A karanténban levő profil nem írható felül", null);
            }
            final long actualRevision = currentResult.status() == Status.MISSING
                    ? MISSING_REVISION : currentResult.profile().revision();
            if (actualRevision != expectedRevision) {
                final String detail = "Profile revision conflict: expected=" + expectedRevision
                        + ", actual=" + actualRevision;
                sessionBlocks.put(playerId, detail);
                throw new ProfileRepositoryException.RevisionConflict(expectedRevision, actualRevision, detail);
            }
            final long requiredNext = actualRevision + 1L;
            if (nextProfile.revision() != requiredNext) {
                final String detail = "Profile revision must advance exactly once: expected next="
                        + requiredNext + ", candidate=" + nextProfile.revision();
                sessionBlocks.put(playerId, detail);
                throw new ProfileRepositoryException.RevisionConflict(requiredNext,
                        nextProfile.revision(), detail);
            }

            final byte[] encoded = codec.encode(nextProfile);
            final YamlConfiguration envelope = new YamlConfiguration();
            envelope.set("format", ENVELOPE_FORMAT);
            envelope.set("payload", Base64.getEncoder().encodeToString(encoded));
            try {
                writer.write(profileFile(playerId), envelope);
            } catch (final IOException | RuntimeException failure) {
                throw block(playerId, "Profile v2 mentési hiba: " + safeMessage(failure), failure);
            }

            cache.put(playerId, nextProfile);
            sessionBlocks.remove(playerId);
            return nextProfile;
        });
    }

    @Override
    public CompletionStage<QuarantineRecord> quarantine(final UUID playerId,
                                                         final byte[] originalPayload,
                                                         final String reason) {
        Objects.requireNonNull(playerId, "playerId");
        final byte[] immutablePayload = originalPayload == null ? new byte[0] : originalPayload.clone();
        final String diagnostic = reason == null || reason.isBlank() ? "ismeretlen decode-hiba" : reason.trim();
        return submit(() -> quarantineNow(playerId, immutablePayload, diagnostic));
    }

    @Override
    public CompletionStage<Void> flush(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return submit(() -> null);
    }

    @Override
    public CompletionStage<Void> flushAll() {
        return submit(() -> null);
    }

    @Override
    public void invalidate(final UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    @Override
    public Optional<ClassProfile> cached(final UUID playerId) {
        return Optional.ofNullable(playerId == null ? null : cache.get(playerId));
    }

    @Override
    public Optional<String> sessionBlockReason(final UUID playerId) {
        return Optional.ofNullable(playerId == null ? null : sessionBlocks.get(playerId));
    }

    @Override
    public Optional<String> quarantineReason(final UUID playerId) {
        return Optional.ofNullable(playerId == null ? null : quarantineReasons.get(playerId));
    }

    @Override
    public void blockSession(final UUID playerId, final String reason) {
        if (playerId == null) {
            return;
        }
        final String detail = reason == null || reason.isBlank()
                ? "Profile v2 session blocked" : reason.trim();
        sessionBlocks.put(playerId, detail);
    }

    private LoadResult loadAuthoritative(final UUID playerId, final boolean explicitReload) {
        ensureLoaded();
        final ClassProfile cachedProfile = cache.get(playerId);
        if (cachedProfile != null) {
            return LoadResult.found(cachedProfile);
        }

        final File file = profileFile(playerId);
        if (!file.exists()) {
            if (explicitReload) {
                sessionBlocks.remove(playerId);
                quarantineReasons.remove(playerId);
            }
            return LoadResult.missing();
        }

        final byte[] envelopeBytes;
        try {
            envelopeBytes = Files.readAllBytes(file.toPath());
        } catch (final IOException failure) {
            throw block(playerId, "Profile v2 olvasási hiba: " + safeMessage(failure), failure);
        }

        final byte[] payload;
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(decodeUtf8(envelopeBytes));
            if (!ENVELOPE_FORMAT.equals(yaml.getString("format"))) {
                throw new IllegalArgumentException("Ismeretlen profil-envelope formátum");
            }
            final String base64 = yaml.getString("payload");
            if (base64 == null || base64.isBlank()) {
                throw new IllegalArgumentException("Hiányzó profil payload");
            }
            payload = Base64.getDecoder().decode(base64);
        } catch (final InvalidConfigurationException | IllegalArgumentException
                       | CharacterCodingException failure) {
            final String reason = "Sérült Profile v2 envelope: " + safeMessage(failure);
            quarantineNow(playerId, envelopeBytes, reason);
            return LoadResult.quarantined(reason);
        }

        final ClassProfile decoded;
        try {
            decoded = codec.decode(payload);
        } catch (final ClassProfileCodec.DecodeException | RuntimeException failure) {
            final String reason = "Sérült ICS2 payload: " + safeMessage(failure);
            quarantineNow(playerId, payload, reason);
            return LoadResult.quarantined(reason);
        }
        cache.put(playerId, decoded);
        quarantineReasons.remove(playerId);
        if (explicitReload) {
            sessionBlocks.remove(playerId);
        }
        return LoadResult.found(decoded);
    }

    private QuarantineRecord quarantineNow(final UUID playerId, final byte[] originalPayload,
                                            final String reason) {
        final long timestamp = clock.millis();
        final File target = new File(quarantineDirectory,
                playerId + "-" + timestamp + "-" + quarantineSequence.incrementAndGet() + ".yml");
        final YamlConfiguration quarantine = new YamlConfiguration();
        quarantine.set("player", playerId.toString());
        quarantine.set("created-at", timestamp);
        quarantine.set("reason", reason);
        quarantine.set("original-base64", Base64.getEncoder().encodeToString(originalPayload));
        try {
            Files.createDirectories(quarantineDirectory.toPath());
            writer.write(target, quarantine);
        } catch (final IOException | RuntimeException failure) {
            final String detail = reason + "; karanténmentés is hibázott: " + safeMessage(failure);
            sessionBlocks.put(playerId, detail);
            logger.severe("Profile v2 quarantine write failed for " + playerId + ": " + detail);
            throw new ProfileRepositoryException(detail, failure);
        }
        cache.remove(playerId);
        quarantineReasons.put(playerId, reason);
        sessionBlocks.put(playerId, reason);
        logger.severe("Profile v2 quarantine: " + playerId + " -> " + target.getName()
                + " (" + reason + ")");
        return new QuarantineRecord(playerId, timestamp, reason, target.getName());
    }

    private File profileFile(final UUID playerId) {
        return new File(profileDirectory, playerId + ".yml");
    }

    private void ensureLoaded() {
        if (!loaded) {
            throw new ProfileRepositoryException("Profile v2 repository nincs betöltve");
        }
    }

    private ProfileRepositoryException block(final UUID playerId, final String detail,
                                             final Throwable cause) {
        sessionBlocks.put(playerId, detail);
        return cause == null ? new ProfileRepositoryException(detail)
                : new ProfileRepositoryException(detail, cause);
    }

    private <T> CompletableFuture<T> submit(final CheckedSupplier<T> work) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        synchronized (lifecycleLock) {
            if (!accepting) {
                future.completeExceptionally(new ProfileRepositoryException(
                        "Profile v2 lifecycle már leállt"));
                return future;
            }
            try {
                ioExecutor.execute(() -> {
                    try {
                        future.complete(work.get());
                    } catch (final Throwable failure) {
                        future.completeExceptionally(failure);
                    }
                });
            } catch (final RejectedExecutionException failure) {
                future.completeExceptionally(new ProfileRepositoryException(
                        "Profile v2 I/O executor nem fogad munkát", failure));
            }
        }
        return future;
    }

    @Override
    public void close() {
        final CompletableFuture<Void> barrier = new CompletableFuture<>();
        synchronized (lifecycleLock) {
            if (!accepting) {
                return;
            }
            accepting = false;
            ioExecutor.execute(() -> barrier.complete(null));
        }
        await(barrier);
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                throw new ProfileRepositoryException("Profile v2 I/O drain timeout");
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ProfileRepositoryException("Profile v2 I/O drain megszakadt", interrupted);
        }
        cache.clear();
        quarantineReasons.clear();
    }

    private static String decodeUtf8(final byte[] bytes) throws CharacterCodingException {
        final CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
        return decoded.toString();
    }

    private static String safeMessage(final Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static void await(final CompletionStage<Void> stage) {
        try {
            stage.toCompletableFuture().join();
        } catch (final RuntimeException failure) {
            throw failure;
        }
    }

    @FunctionalInterface
    interface AtomicWriter {
        void write(File file, YamlConfiguration yaml) throws IOException;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
