package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.storage.CriticalPersistenceWriteError;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.zip.CRC32;

/** Bounded fsync journal so a history mutation never rewrites the complete authority file. */
final class TrashHistoryJournal {

    private static final int MAGIC = 0x5452484A;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTES = Integer.BYTES * 3 + Long.BYTES;
    private static final int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;

    private final Logger logger;
    private final File file;

    TrashHistoryJournal(final JavaPlugin plugin, final File file) {
        this(Objects.requireNonNull(plugin, "plugin").getLogger(), file);
    }

    TrashHistoryJournal(final Logger logger, final File file) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.file = Objects.requireNonNull(file, "file");
        YamlStore.registerCriticalWrite(file);
    }

    LoadResult loadAfter(final long snapshotSequence) {
        if (!file.exists()) return new LoadResult(snapshotSequence, 0, List.of());
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {
            final long size = channel.size();
            if (size > MAX_FILE_BYTES) corrupt("a Trash history journal túl nagy");
            final List<Record> records = new ArrayList<>();
            long offset = 0L;
            long previousSequence = -1L;
            long recoveredSequence = snapshotSequence;
            int completeRecords = 0;
            while (offset < size) {
                if (size - offset < HEADER_BYTES) {
                    truncateTail(channel, offset);
                    break;
                }
                final ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
                readFully(channel, header, offset);
                header.flip();
                final int magic = header.getInt();
                final int version = header.getInt();
                final long sequence = header.getLong();
                final int length = header.getInt();
                if (magic != MAGIC || version != FORMAT_VERSION || sequence < 1L
                        || length < 1 || length > MAX_PAYLOAD_BYTES
                        || previousSequence >= sequence) {
                    corrupt("érvénytelen Trash history journal frame");
                }
                final long frameBytes = HEADER_BYTES + (long) length + Integer.BYTES;
                if (size - offset < frameBytes) {
                    truncateTail(channel, offset);
                    break;
                }
                final ByteBuffer payload = ByteBuffer.allocate(length);
                readFully(channel, payload, offset + HEADER_BYTES);
                payload.flip();
                final byte[] bytes = new byte[length];
                payload.get(bytes);
                final ByteBuffer checksum = ByteBuffer.allocate(Integer.BYTES);
                readFully(channel, checksum, offset + HEADER_BYTES + length);
                checksum.flip();
                if (checksum.getInt() != checksum(sequence, bytes)) {
                    if (offset + frameBytes == size) {
                        truncateTail(channel, offset);
                        break;
                    }
                    corrupt("sérült Trash history journal checksum");
                }
                previousSequence = sequence;
                completeRecords++;
                if (sequence > snapshotSequence) {
                    if (sequence != recoveredSequence + 1L) {
                        corrupt("hiányzó Trash history journal sequence");
                    }
                    records.add(new Record(sequence, new String(bytes, StandardCharsets.UTF_8)));
                    recoveredSequence = sequence;
                }
                offset += frameBytes;
            }
            return new LoadResult(recoveredSequence, completeRecords, records);
        } catch (final IOException failure) {
            throw new CriticalPersistenceWriteError(file, failure);
        }
    }

    void append(final long sequence, final String payload) {
        if (YamlStore.isLoadFailed(file)) {
            throw new CriticalPersistenceWriteError(file,
                    new IOException("a sérült Trash history journal írása letiltott"));
        }
        final byte[] bytes = Objects.requireNonNull(payload, "payload")
                .getBytes(StandardCharsets.UTF_8);
        if (sequence < 1L || bytes.length < 1 || bytes.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("érvénytelen Trash history journal payload");
        }
        final File parent = file.getAbsoluteFile().getParentFile();
        try {
            if (parent != null) Files.createDirectories(parent.toPath());
            try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                final ByteBuffer frame = ByteBuffer.allocate(HEADER_BYTES + bytes.length
                        + Integer.BYTES);
                frame.putInt(MAGIC);
                frame.putInt(FORMAT_VERSION);
                frame.putLong(sequence);
                frame.putInt(bytes.length);
                frame.put(bytes);
                frame.putInt(checksum(sequence, bytes));
                frame.flip();
                while (frame.hasRemaining()) channel.write(frame);
                channel.force(true);
            }
        } catch (final IOException failure) {
            throw new CriticalPersistenceWriteError(file, failure);
        }
    }

    void reset() {
        if (!file.exists()) return;
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE)) {
            channel.truncate(0L);
            channel.force(true);
        } catch (final IOException failure) {
            throw new CriticalPersistenceWriteError(file, failure);
        }
    }

    private void truncateTail(final FileChannel channel, final long offset) throws IOException {
        channel.truncate(offset);
        channel.force(true);
    }

    void corrupt(final String reason) {
        YamlStore.failCorrupt(file, logger, reason);
    }

    private static int checksum(final long sequence, final byte[] payload) {
        final CRC32 crc = new CRC32();
        final ByteBuffer sequenceBytes = ByteBuffer.allocate(Long.BYTES).putLong(sequence);
        crc.update(sequenceBytes.array());
        crc.update(payload);
        return (int) crc.getValue();
    }

    private static void readFully(final FileChannel channel, final ByteBuffer target,
                                  final long offset) throws IOException {
        long position = offset;
        while (target.hasRemaining()) {
            final int read = channel.read(target, position);
            if (read < 0) throw new IOException("váratlan Trash history journal EOF");
            if (read == 0) continue;
            position += read;
        }
    }

    record Record(long sequence, String payload) { }

    record LoadResult(long sequence, int completeRecords, List<Record> records) {
        LoadResult {
            records = List.copyOf(records);
        }
    }
}
