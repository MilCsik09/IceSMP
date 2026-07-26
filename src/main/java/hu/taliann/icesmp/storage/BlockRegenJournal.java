package hu.taliann.icesmp.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Strict write-ahead journal for block regeneration.
 *
 * <p>Tile-entity snapshots are fsynced before the live container is cleared. Malformed
 * checkpoint/WAL data is authoritative-state corruption: it is quarantined and plugin enable
 * aborts instead of silently dropping the only copy of a container snapshot.
 */
public final class BlockRegenJournal {

    public enum State {
        PENDING('P'),
        APPLYING('A'),
        APPLIED('D');

        private final char marker;

        State(final char marker) {
            this.marker = marker;
        }

        static State of(final String value) {
            if (value == null || value.length() != 1) {
                return null;
            }
            for (final State state : values()) {
                if (state.marker == value.charAt(0)) {
                    return state;
                }
            }
            return null;
        }
    }

    public record Record(long id, String world, int x, int y, int z,
                         String blockData, String extra, long restoreAt) {
    }

    private static final char SEP = '\t';
    private static final String NO_EXTRA = "-";
    private static final int PENDING_FIELDS = 9;

    private final File checkpointFile;
    private final File walFile;
    private final File rotatedFile;
    private final Logger logger;
    private final Object writeLock = new Object();
    private final Object checkpointLock = new Object();
    private final AtomicLong idSequence = new AtomicLong();
    private FileChannel wal;
    private volatile boolean healthy = true;
    private boolean writeFailureLogged;

    public BlockRegenJournal(final File dataFolder, final Logger logger) {
        this.checkpointFile = new File(dataFolder, "block-regen.yml");
        this.walFile = new File(dataFolder, "block-regen.wal");
        this.rotatedFile = new File(dataFolder, "block-regen.wal.rotated");
        this.logger = logger;
    }

    public long nextId() {
        return idSequence.incrementAndGet();
    }

    public boolean isHealthy() {
        return healthy && !YamlStore.isLoadFailed(checkpointFile)
                && !YamlStore.isLoadFailed(walFile) && !YamlStore.isLoadFailed(rotatedFile);
    }

    public List<Record> loadAll() {
        synchronized (writeLock) {
            closeChannel();
            healthy = true;
            writeFailureLogged = false;
            final Map<Long, Record> live = new LinkedHashMap<>();
            final Set<Long> applied = new HashSet<>();
            readCheckpoint(live);
            replay(rotatedFile, live, applied);
            replay(walFile, live, applied);
            for (final Long id : applied) {
                live.remove(id);
            }
            long maxId = 0L;
            for (final Long id : live.keySet()) {
                maxId = Math.max(maxId, id);
            }
            for (final Long id : applied) {
                maxId = Math.max(maxId, id);
            }
            idSequence.set(maxId);
            return new ArrayList<>(live.values());
        }
    }

    public boolean appendPending(final Record record, final boolean durable) {
        if (!validRecord(record)) {
            logger.severe("block-regen napló: érvénytelen rekord, a blokk nem kerül a sorba ("
                    + (record == null ? "null" : record.world()) + ')');
            return false;
        }
        final String line = State.PENDING.marker + String.valueOf(SEP) + record.id()
                + SEP + record.world()
                + SEP + record.x()
                + SEP + record.y()
                + SEP + record.z()
                + SEP + record.restoreAt()
                + SEP + record.blockData()
                + SEP + (record.extra() == null ? NO_EXTRA : record.extra())
                + '\n';
        return append(line, durable);
    }

    /**
     * The APPLYING marker is durable for tile entities. World mutation must not start when this
     * write fails; otherwise the only snapshot and the live world could diverge without evidence.
     */
    public boolean markApplying(final Record record) {
        return record != null && append(State.APPLYING.marker + String.valueOf(SEP)
                + record.id() + '\n', record.extra() != null);
    }

    public boolean markApplied(final Record record) {
        return record != null && append(State.APPLIED.marker + String.valueOf(SEP)
                + record.id() + '\n', record.extra() != null);
    }

    public void checkpoint(final Collection<Record> liveRecords) throws IOException {
        if (!isHealthy()) {
            throw new IOException("A block-regen journal hibás állapotban van.");
        }
        synchronized (checkpointLock) {
            final Map<Long, Record> merged = new LinkedHashMap<>();
            final Set<Long> applied = new HashSet<>();
            synchronized (writeLock) {
                for (final Record record : liveRecords) {
                    if (!validRecord(record)) {
                        throw new IOException("Érvénytelen élő block-regen rekord: "
                                + (record == null ? "null" : record.id()));
                    }
                    final Record previous = merged.put(record.id(), record);
                    if (previous != null && !previous.equals(record)) {
                        throw new IOException("Ütköző block-regen rekordazonosító: " + record.id());
                    }
                }
                rotate();
                replay(rotatedFile, merged, applied);
            }
            for (final Long id : applied) {
                merged.remove(id);
            }

            final YamlConfiguration yaml = new YamlConfiguration();
            final List<Map<String, Object>> rows = new ArrayList<>();
            for (final Record record : merged.values()) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", record.id());
                row.put("world", record.world());
                row.put("x", record.x());
                row.put("y", record.y());
                row.put("z", record.z());
                row.put("data", record.blockData());
                if (record.extra() != null) {
                    row.put("extra", record.extra());
                }
                row.put("at", record.restoreAt());
                rows.add(row);
            }
            yaml.set("pending", rows);
            YamlStore.saveAtomic(checkpointFile, yaml);
            if (Files.deleteIfExists(rotatedFile.toPath())) {
                forceDirectory(rotatedFile.getParentFile());
            }
        }
    }

    private void readCheckpoint(final Map<Long, Record> out) {
        if (!checkpointFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlStore.loadTracked(checkpointFile, logger);
        long legacyId = 0L;
        int rowIndex = 0;
        for (final Map<?, ?> raw : yaml.getMapList("pending")) {
            rowIndex++;
            try {
                final Object worldRaw = raw.get("world");
                final Object xRaw = raw.get("x");
                final Object yRaw = raw.get("y");
                final Object zRaw = raw.get("z");
                final Object dataRaw = raw.get("data");
                final Object atRaw = raw.get("at");
                if (!(xRaw instanceof Number x) || !(yRaw instanceof Number y)
                        || !(zRaw instanceof Number z) || !(atRaw instanceof Number at)
                        || worldRaw == null || dataRaw == null) {
                    throw new IllegalArgumentException("hiányzó/hibás mező");
                }
                final long id = raw.get("id") instanceof Number number ? number.longValue() : --legacyId;
                final Record record = new Record(id, String.valueOf(worldRaw),
                        x.intValue(), y.intValue(), z.intValue(), String.valueOf(dataRaw),
                        raw.get("extra") == null ? null : String.valueOf(raw.get("extra")),
                        at.longValue());
                if (!validRecord(record)) {
                    throw new IllegalArgumentException("érvénytelen rekord");
                }
                final Record previous = out.put(id, record);
                if (previous != null && !previous.equals(record)) {
                    throw new IllegalArgumentException("duplikált/ütköző id");
                }
            } catch (final RuntimeException invalid) {
                YamlStore.failCorrupt(checkpointFile, logger,
                        "Érvénytelen block-regen checkpoint sor #" + rowIndex + ": "
                                + invalid.getMessage());
            }
        }
    }

    private void replay(final File file, final Map<Long, Record> live, final Set<Long> applied) {
        if (!file.exists()) {
            return;
        }
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (final IOException failure) {
            YamlStore.failCorrupt(file, logger,
                    "block-regen napló olvasási hiba: " + failure.getMessage());
            return;
        }
        if (bytes.length == 0) {
            return;
        }

        final String text;
        try {
            final CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            text = decoded.toString();
        } catch (final CharacterCodingException invalidUtf8) {
            YamlStore.failCorrupt(file, logger, "A WAL nem érvényes UTF-8.");
            return;
        }

        final boolean terminated = bytes[bytes.length - 1] == (byte) '\n';
        final String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                continue;
            }
            final boolean finalTornLine = index == lines.length - 1 && !terminated;
            try {
                replayLine(line, live, applied);
            } catch (final RuntimeException malformed) {
                if (finalTornLine) {
                    logger.warning("block-regen napló: félbeszakadt utolsó sor figyelmen kívül "
                            + "hagyva (" + file.getName() + ").");
                    continue;
                }
                YamlStore.failCorrupt(file, logger,
                        "Érvénytelen WAL sor #" + (index + 1) + ": " + malformed.getMessage());
            }
        }
    }

    private void replayLine(final String line, final Map<Long, Record> live,
                            final Set<Long> applied) {
        final String[] parts = line.split("\t", -1);
        final State state = parts.length == 0 ? null : State.of(parts[0]);
        if (state == null) {
            throw new IllegalArgumentException("ismeretlen állapotjel");
        }
        switch (state) {
            case PENDING -> {
                if (parts.length != PENDING_FIELDS) {
                    throw new IllegalArgumentException("hibás PENDING mezőszám");
                }
                final long id = Long.parseLong(parts[1]);
                final Record record = new Record(id, parts[2],
                        Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
                        Integer.parseInt(parts[5]), parts[7],
                        NO_EXTRA.equals(parts[8]) ? null : parts[8],
                        Long.parseLong(parts[6]));
                if (!validRecord(record)) {
                    throw new IllegalArgumentException("érvénytelen PENDING rekord");
                }
                final Record previous = live.put(id, record);
                if (previous != null && !previous.equals(record)) {
                    throw new IllegalArgumentException("ütköző PENDING id: " + id);
                }
            }
            case APPLYING -> {
                if (parts.length != 2) {
                    throw new IllegalArgumentException("hibás APPLYING mezőszám");
                }
                Long.parseLong(parts[1]);
            }
            case APPLIED -> {
                if (parts.length != 2) {
                    throw new IllegalArgumentException("hibás APPLIED mezőszám");
                }
                applied.add(Long.parseLong(parts[1]));
            }
        }
    }

    private boolean append(final String line, final boolean durable) {
        synchronized (writeLock) {
            if (!healthy) {
                return false;
            }
            try {
                final FileChannel channel = channel();
                final ByteBuffer buffer = ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                if (durable) {
                    channel.force(true);
                }
                writeFailureLogged = false;
                return true;
            } catch (final IOException failure) {
                healthy = false;
                if (!writeFailureLogged) {
                    writeFailureLogged = true;
                    logger.severe("block-regen napló írási hiba — a visszaépítés leáll, "
                            + "amíg a szerver kontrolláltan újra nem indul: " + failure);
                }
                closeChannel();
                return false;
            }
        }
    }

    private FileChannel channel() throws IOException {
        if (wal == null || !wal.isOpen()) {
            final File parent = walFile.getAbsoluteFile().getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            final boolean existed = walFile.exists();
            wal = FileChannel.open(walFile.toPath(), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            if (!existed) {
                forceDirectory(parent);
            }
        }
        return wal;
    }

    private void rotate() throws IOException {
        closeChannel();
        if (!walFile.exists()) {
            return;
        }
        final File parent = walFile.getAbsoluteFile().getParentFile();
        if (rotatedFile.exists()) {
            try (FileChannel out = FileChannel.open(rotatedFile.toPath(),
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                 FileChannel in = FileChannel.open(walFile.toPath(), StandardOpenOption.READ)) {
                long position = 0L;
                while (position < in.size()) {
                    position += in.transferTo(position, in.size() - position, out);
                }
                out.force(true);
            }
            Files.delete(walFile.toPath());
            forceDirectory(parent);
            return;
        }
        try {
            Files.move(walFile.toPath(), rotatedFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException unsupported) {
            Files.move(walFile.toPath(), rotatedFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        forceDirectory(parent);
    }

    private void closeChannel() {
        if (wal == null) {
            return;
        }
        try {
            wal.close();
        } catch (final IOException ignored) {
            // The next controlled restart/reload reopens it.
        }
        wal = null;
    }

    private static void forceDirectory(final File directory) throws IOException {
        if (directory == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory.toPath(), StandardOpenOption.READ)) {
            channel.force(true);
        } catch (final AccessDeniedException | UnsupportedOperationException unsupported) {
            // Windows and some providers do not expose fsync-capable directory channels.
        }
    }

    private static boolean validRecord(final Record record) {
        return record != null && record.id() != 0L && record.restoreAt() > 0L
                && !hasSeparator(record.world()) && !record.world().isBlank()
                && !hasSeparator(record.blockData()) && !record.blockData().isBlank()
                && (record.extra() == null || !hasSeparator(record.extra()));
    }

    private static boolean hasSeparator(final String value) {
        return value == null || value.indexOf(SEP) >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }
}
