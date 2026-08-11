package hu.taliann.icesmp.classspec.transaction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

/**
 * Small write-ahead journal for cross-store respec transactions.
 * An entry is durable before wallet debit, retains the exact wallet CAS token, and is removed
 * only after Profile v2 and the player-thread completion both finish. Files are owner-bound,
 * strict, bounded, fsync'ed and atomically replaced.
 */
public final class RespecTransactionJournal {
    private static final String FORMAT = "ICESMP-PLAYER-PROFILE-RESPEC-WAL-1";
    private static final int MAX_FILE_BYTES = 64 * 1024;
    private static final int MAX_OPERATION_ID = 192;
    private static final int MAX_DETAIL = 512;
    private final Path directory;
    private final AtomicWriter writer;

    public RespecTransactionJournal(final Path directory) {
        this(directory, RespecTransactionJournal::writeAtomic);
    }

    RespecTransactionJournal(final Path directory, final AtomicWriter writer) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.writer = Objects.requireNonNull(writer, "writer");
        try {
            Files.createDirectories(directory);
        } catch (final IOException failure) {
            throw new JournalException("Cannot create respec journal directory", failure);
        }
    }

    public synchronized void save(final Entry entry) {
        Objects.requireNonNull(entry, "entry");
        final Properties properties = new Properties();
        properties.setProperty("format", FORMAT);
        properties.setProperty("operation-id", entry.operationId());
        properties.setProperty("player-id", entry.playerId().toString());
        properties.setProperty("stage", entry.stage().name());
        properties.setProperty("currency-id", entry.currencyId());
        properties.setProperty("amount", Double.toString(entry.amount()));
        properties.setProperty("created-at", Long.toString(entry.createdAtEpochMillis()));
        properties.setProperty("profile-revision-before", Long.toString(entry.profileRevisionBefore()));
        properties.setProperty("wallet-token-present", Boolean.toString(entry.walletTokenPresent()));
        properties.setProperty("wallet-previous-present", Boolean.toString(entry.walletPreviousPresent()));
        properties.setProperty("detail", encodeText(entry.detail()));
        writeMap(properties, "wallet.previous.", entry.walletPrevious());
        writeMap(properties, "wallet.expected.", entry.walletExpected());
        try {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            properties.store(output, "IceSMP PlayerProfile respec WAL");
            final byte[] bytes = output.toByteArray();
            if (bytes.length > MAX_FILE_BYTES) {
                throw new JournalException("Respec journal entry exceeds size limit");
            }
            writer.write(file(entry.operationId()), bytes);
        } catch (final IOException failure) {
            throw new JournalException("Cannot persist respec journal entry", failure);
        }
    }

    public synchronized Entry load(final String operationId) {
        final Path file = file(operationId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            final long size = Files.size(file);
            if (size < 1 || size > MAX_FILE_BYTES) {
                throw new JournalException("Respec journal size is invalid");
            }
            final byte[] bytes = Files.readAllBytes(file);
            final Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(bytes));
            exactKeys(properties);
            if (!FORMAT.equals(required(properties, "format", 64))) {
                throw new JournalException("Unknown respec journal format");
            }
            final String storedOperationId = required(properties, "operation-id", MAX_OPERATION_ID);
            if (!storedOperationId.equals(operationId)) {
                throw new JournalException("Respec journal operation identity mismatch");
            }
            final UUID playerId = uuid(required(properties, "player-id", 64));
            final Stage stage = enumValue(Stage.class, required(properties, "stage", 64));
            final String currencyId = required(properties, "currency-id", 64);
            final double amount = finiteNonNegative(required(properties, "amount", 64), "amount");
            final long createdAt = boundedLong(required(properties, "created-at", 32), 1L, Long.MAX_VALUE,
                    "created-at");
            final long profileRevision = boundedLong(required(properties, "profile-revision-before", 32),
                    0L, Long.MAX_VALUE, "profile-revision-before");
            final boolean tokenPresent = strictBoolean(properties, "wallet-token-present");
            final boolean previousPresent = strictBoolean(properties, "wallet-previous-present");
            final String detail = decodeText(required(properties, "detail", MAX_DETAIL * 4));
            final Map<String, Double> previous = readMap(properties, "wallet.previous.");
            final Map<String, Double> expected = readMap(properties, "wallet.expected.");
            return new Entry(storedOperationId, playerId, stage, currencyId, amount, createdAt,
                    profileRevision, tokenPresent, previousPresent, previous, expected, detail);
        } catch (final IOException failure) {
            throw new JournalException("Cannot read respec journal entry", failure);
        }
    }

    public synchronized List<Entry> findByPlayer(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        final List<Entry> result = new ArrayList<>();
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .sorted()
                    .forEach(path -> {
                        final String operationId = readOperationId(path);
                        final Entry entry = load(operationId);
                        if (entry != null && playerId.equals(entry.playerId())) {
                            result.add(entry);
                        }
                    });
        } catch (final IOException failure) {
            throw new JournalException("Cannot enumerate respec journal", failure);
        }
        return List.copyOf(result);
    }

    public synchronized void delete(final String operationId) {
        try {
            Files.deleteIfExists(file(operationId));
            forceDirectory(directory);
        } catch (final IOException failure) {
            throw new JournalException("Cannot delete completed respec journal", failure);
        }
    }

    private String readOperationId(final Path path) {
        try {
            final byte[] bytes = Files.readAllBytes(path);
            final Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(bytes));
            return required(properties, "operation-id", MAX_OPERATION_ID);
        } catch (final IOException failure) {
            throw new JournalException("Cannot read respec journal identity", failure);
        }
    }

    private Path file(final String operationId) {
        final String clean = requireOperationId(operationId);
        return directory.resolve(sha256(clean) + ".properties");
    }

    private static void writeMap(final Properties properties, final String prefix,
                                 final Map<String, Double> values) {
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || !Double.isFinite(entry.getValue())
                    || entry.getValue() < 0.0D) {
                throw new JournalException("Invalid wallet snapshot value");
            }
            properties.setProperty(prefix + entry.getKey(), Double.toString(entry.getValue()));
        });
    }

    private static Map<String, Double> readMap(final Properties properties, final String prefix) {
        final Map<String, Double> result = new LinkedHashMap<>();
        properties.stringPropertyNames().stream().filter(key -> key.startsWith(prefix)).sorted()
                .forEach(key -> {
                    final String currency = key.substring(prefix.length());
                    if (currency.isBlank() || result.putIfAbsent(currency,
                            finiteNonNegative(properties.getProperty(key), key)) != null) {
                        throw new JournalException("Invalid/duplicate wallet snapshot key");
                    }
                });
        return Collections.unmodifiableMap(result);
    }

    private static void exactKeys(final Properties properties) {
        final var allowed = new java.util.HashSet<>(List.of("format", "operation-id", "player-id",
                "stage", "currency-id", "amount", "created-at", "profile-revision-before",
                "wallet-token-present", "wallet-previous-present", "detail"));
        for (final String key : properties.stringPropertyNames()) {
            if (key.startsWith("wallet.previous.") || key.startsWith("wallet.expected.")) continue;
            if (!allowed.contains(key)) throw new JournalException("Unknown respec journal key: " + key);
        }
        if (!properties.stringPropertyNames().containsAll(allowed)) {
            throw new JournalException("Missing respec journal fields");
        }
    }

    private static String required(final Properties properties, final String key, final int maximum) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new JournalException("Invalid respec journal field: " + key);
        }
        return value;
    }

    private static String requireOperationId(final String operationId) {
        if (operationId == null || operationId.isBlank() || operationId.length() > MAX_OPERATION_ID) {
            throw new IllegalArgumentException("operationId must be non-blank and bounded");
        }
        return operationId;
    }

    private static boolean strictBoolean(final Properties properties, final String key) {
        final String raw = required(properties, key, 5);
        if (!raw.equals("true") && !raw.equals("false")) {
            throw new JournalException(key + " must be a boolean");
        }
        return Boolean.parseBoolean(raw);
    }

    private static long boundedLong(final String raw, final long minimum, final long maximum,
                                    final String field) {
        try {
            final long value = Long.parseLong(raw);
            if (value < minimum || value > maximum) throw new JournalException(field + " out of bounds");
            return value;
        } catch (final NumberFormatException invalid) {
            throw new JournalException(field + " is not an integer", invalid);
        }
    }

    private static double finiteNonNegative(final String raw, final String field) {
        try {
            final double value = Double.parseDouble(raw);
            if (!Double.isFinite(value) || value < 0.0D) throw new JournalException(field + " invalid");
            return value;
        } catch (final NumberFormatException invalid) {
            throw new JournalException(field + " is not numeric", invalid);
        }
    }

    private static UUID uuid(final String raw) {
        try { return UUID.fromString(raw); }
        catch (final IllegalArgumentException invalid) { throw new JournalException("Invalid player UUID", invalid); }
    }

    private static <E extends Enum<E>> E enumValue(final Class<E> type, final String raw) {
        try { return Enum.valueOf(type, raw); }
        catch (final IllegalArgumentException invalid) { throw new JournalException("Invalid journal enum", invalid); }
    }

    private static String encodeText(final String value) {
        final String clean = value == null ? "" : value.trim();
        if (clean.length() > MAX_DETAIL) throw new JournalException("Journal detail too long");
        return Base64.getEncoder().encodeToString(clean.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(final String value) {
        try {
            final String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            if (decoded.length() > MAX_DETAIL) throw new JournalException("Journal detail too long");
            return decoded;
        } catch (final IllegalArgumentException invalid) {
            throw new JournalException("Invalid journal detail encoding", invalid);
        }
    }

    private static String sha256(final String value) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void writeAtomic(final Path target, final byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        final Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(java.nio.ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void forceDirectory(final Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (final UnsupportedOperationException | java.nio.file.AccessDeniedException ignored) {
            // Windows and some filesystems do not expose directory fsync. File content was already forced.
        }
    }

    public enum Stage { INTENT, WALLET_DEBITED, PROFILE_COMMITTED, RUNTIME_PENDING, REFUND_REQUIRED }

    public record Entry(String operationId, UUID playerId, Stage stage, String currencyId,
                        double amount, long createdAtEpochMillis, long profileRevisionBefore,
                        boolean walletTokenPresent, boolean walletPreviousPresent,
                        Map<String, Double> walletPrevious, Map<String, Double> walletExpected,
                        String detail) {
        public Entry {
            operationId = requireOperationId(operationId);
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(stage, "stage");
            currencyId = Objects.requireNonNull(currencyId, "currencyId").trim();
            if (currencyId.isBlank() || currencyId.length() > 64 || !Double.isFinite(amount)
                    || amount < 0.0D || createdAtEpochMillis <= 0L || profileRevisionBefore < 0L) {
                throw new IllegalArgumentException("Invalid respec journal entry");
            }
            walletPrevious = Map.copyOf(Objects.requireNonNull(walletPrevious, "walletPrevious"));
            walletExpected = Map.copyOf(Objects.requireNonNull(walletExpected, "walletExpected"));
            detail = detail == null ? "" : detail.trim();
            if (detail.length() > MAX_DETAIL) throw new IllegalArgumentException("detail too long");
            if (!walletTokenPresent && (!walletPrevious.isEmpty() || !walletExpected.isEmpty())) {
                throw new IllegalArgumentException("Wallet snapshots require a token");
            }
        }

        public Entry withWallet(final boolean previousPresent, final Map<String, Double> previous,
                                final Map<String, Double> expected) {
            return new Entry(operationId, playerId, Stage.WALLET_DEBITED, currencyId, amount,
                    createdAtEpochMillis, profileRevisionBefore, true, previousPresent,
                    previous, expected, "wallet debit committed");
        }

        public Entry withStage(final Stage next, final String nextDetail) {
            return new Entry(operationId, playerId, next, currencyId, amount, createdAtEpochMillis,
                    profileRevisionBefore, walletTokenPresent, walletPreviousPresent,
                    walletPrevious, walletExpected, nextDetail);
        }
    }

    @FunctionalInterface
    interface AtomicWriter { void write(Path target, byte[] bytes) throws IOException; }

    public static final class JournalException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public JournalException(final String message) { super(message); }
        public JournalException(final String message, final Throwable cause) { super(message, cause); }
    }
}
