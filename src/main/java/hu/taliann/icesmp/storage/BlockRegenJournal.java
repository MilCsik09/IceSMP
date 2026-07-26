package hu.taliann.icesmp.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
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
 * A blokk-visszaépítés írás-előre naplója (write-ahead log + checkpoint).
 *
 * <p>A visszaépítendő blokk pillanatképe egy rombolás ELŐTT nem létező adat: a konténer
 * tartalma a kiürítés után CSAK a rekordban él. Ezért a rekord előbb tartósan lemezre kerül,
 * és csak utána szabad a világban bármit elpusztítani. A napló két fájlból áll:
 * <ul>
 *   <li>{@code block-regen.yml} — checkpoint: az élő rekordok teljes halmaza, a
 *       {@link YamlStore#saveAtomic} atomi írásával (operátor számára olvasható);</li>
 *   <li>{@code block-regen.wal} — hozzáfűzéses napló: a checkpoint óta történt változások
 *       ({@code P} = új rekord, {@code A} = visszaépítés indul, {@code D} = véglegesítve).</li>
 * </ul>
 *
 * <p>Állapotgép: PENDING (P) → APPLYING (A) → APPLIED (D). A replay CSAK a D-vel véglegesített
 * rekordot dobja el; a PENDING és az APPLYING egyaránt újra sorba kerül, mert a visszaépítés
 * idempotens (ugyanaz a blokk-adat és ugyanaz az NBT-pillanatkép kerül vissza), így a
 * félbehagyott restore újraindítás után biztonságosan újrafut.
 */
public final class BlockRegenJournal {

    /** A rekord életciklusa; véglegesnek KIZÁRÓLAG az APPLIED számít. */
    public enum State {

        PENDING('P'),
        APPLYING('A'),
        APPLIED('D');

        private final char marker;

        State(final char marker) {
            this.marker = marker;
        }

        static State of(final String value) {
            if (value.length() != 1) {
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

    /** Egy visszaépítendő blokk: pozíció + blokk-adat + opcionális NBT-pillanatkép. */
    public record Record(long id, String world, int x, int y, int z,
                         String blockData, String extra, long restoreAt) {
    }

    /** Mezőválasztó: sem a világnév, sem a blokk-adat, sem a Base64-NBT nem tartalmazhat tabot. */
    private static final char SEP = '\t';
    private static final String NO_EXTRA = "-";
    private static final int PENDING_FIELDS = 9;

    private final File checkpointFile;
    private final File walFile;
    private final File rotatedFile;
    private final Logger logger;
    /** A napló-írások és a checkpoint-rotáció sorosítása (régió-szálakról is hívjuk). */
    private final Object writeLock = new Object();
    /**
     * A checkpointok sorosítása. Külön zár, hogy a (lassú) YAML-írás alatt a régió-szálak
     * hozzáfűzése ne álljon meg; a zár-sorrend mindig checkpointLock → writeLock.
     */
    private final Object checkpointLock = new Object();
    private final AtomicLong idSequence = new AtomicLong();
    private FileChannel wal;
    private boolean writeFailureLogged;

    public BlockRegenJournal(final File dataFolder, final Logger logger) {
        this.checkpointFile = new File(dataFolder, "block-regen.yml");
        this.walFile = new File(dataFolder, "block-regen.wal");
        this.rotatedFile = new File(dataFolder, "block-regen.wal.rotated");
        this.logger = logger;
    }

    /** Egyedi rekord-azonosító; a betöltés a lemezen látott legnagyobb id-re állítja a számlálót. */
    public long nextId() {
        return idSequence.incrementAndGet();
    }

    /**
     * A teljes élő rekordhalmaz: checkpoint, majd a rotált és az aktív napló replay-e.
     * A sorrend kötött — a friss napló felülírja a checkpointot, a D-jelölések pedig
     * mindkét naplóból érvényesek a checkpointban szereplő rekordokra is.
     */
    public List<Record> loadAll() {
        synchronized (writeLock) {
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

    /**
     * Új rekord tartós rögzítése. A {@code durable} rekordnál (NBT-pillanatkép) a hívás
     * csak fsync UTÁN tér vissza: a hívó ezután pusztíthatja a blokkot. False esetén a
     * hívó NEM üríthet és NEM rombolhat — a napló nélkül a tartalom nyomtalanul elveszne.
     */
    public boolean appendPending(final Record record, final boolean durable) {
        if (hasSeparator(record.world()) || hasSeparator(record.blockData())
                || (record.extra() != null && hasSeparator(record.extra()))) {
            logger.severe("block-regen napló: a rekord mezője tabot/újsort tartalmaz, "
                    + "a blokk nem kerül a visszaépítő sorba (" + record.world() + ')');
            return false;
        }
        final StringBuilder line = new StringBuilder(96);
        line.append(State.PENDING.marker).append(SEP).append(record.id())
                .append(SEP).append(record.world())
                .append(SEP).append(record.x())
                .append(SEP).append(record.y())
                .append(SEP).append(record.z())
                .append(SEP).append(record.restoreAt())
                .append(SEP).append(record.blockData())
                .append(SEP).append(record.extra() == null ? NO_EXTRA : record.extra())
                .append('\n');
        return append(line.toString(), durable);
    }

    /** A visszaépítés megkezdésének jelölése; elvesztése csak idempotens újrafutást okoz. */
    public void markApplying(final long id) {
        append(State.APPLYING.marker + String.valueOf(SEP) + id + '\n', false);
    }

    /**
     * Véglegesítés: a rekordot a hívó CSAK akkor törölheti a sorból, ha ez true-t adott.
     * Az fsync-et a tartalom-hordozó (NBT-s) rekordhoz kötjük — sima blokknál egy elveszett
     * jelölés legfeljebb ugyanannak a blokk-adatnak az ismételt visszaírását okozza.
     */
    public boolean markApplied(final Record record) {
        return append(State.APPLIED.marker + String.valueOf(SEP) + record.id() + '\n',
                record.extra() != null);
    }

    /**
     * Checkpoint: az élő rekordok teljes halmaza egy atomi YAML-írásban, utána a napló
     * rotációja. A rotált naplót CSAK a sikeres checkpoint-írás után dobjuk el, különben
     * a benne lévő rekordok egyetlen példánya tűnne el.
     *
     * @param live az ÉLŐ (nem lemásolt) rekordgyűjtemény — a pillanatkép a zárolás alatt készül
     */
    public void checkpoint(final Collection<Record> live) throws IOException {
        if (YamlStore.isLoadFailed(checkpointFile)) {
            // A YamlStore megtagadja a sérült checkpoint felülírását. Ilyenkor a naplót sem
            // rotáljuk: a rekordok egyetlen tartós példánya a WAL-ban van, az nem dobható el.
            return;
        }
        synchronized (checkpointLock) {
            final Map<Long, Record> merged = new LinkedHashMap<>();
            final Set<Long> applied = new HashSet<>();
            synchronized (writeLock) {
                // A pillanatkép a napló-zárolás ALATT készül, és a rotált naplót is
                // beolvasztja: így sem a párhuzamosan hozzáfűzött, sem a sorba még be nem
                // tett rekord nem eshet ki a checkpointból.
                for (final Record record : live) {
                    merged.put(record.id(), record);
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
            Files.deleteIfExists(rotatedFile.toPath());
        }
    }

    private void readCheckpoint(final Map<Long, Record> out) {
        if (!checkpointFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlStore.loadTracked(checkpointFile, logger);
        long legacyId = 0L;
        for (final Map<?, ?> raw : yaml.getMapList("pending")) {
            try {
                // A napló előtti fájlokban nincs id: negatív azonosítót adunk, így sosem
                // ütközik a friss (pozitív) sorszámokkal.
                final long id = raw.get("id") instanceof Number number ? number.longValue() : --legacyId;
                out.put(id, new Record(id, String.valueOf(raw.get("world")),
                        ((Number) raw.get("x")).intValue(),
                        ((Number) raw.get("y")).intValue(),
                        ((Number) raw.get("z")).intValue(),
                        String.valueOf(raw.get("data")),
                        raw.get("extra") == null ? null : String.valueOf(raw.get("extra")),
                        ((Number) raw.get("at")).longValue()));
            } catch (final RuntimeException ignored) {
                // Sérült sor — a többi rekord attól még betölt.
            }
        }
    }

    private void replay(final File file, final Map<Long, Record> live, final Set<Long> applied) {
        if (!file.exists()) {
            return;
        }
        int broken = 0;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                final String[] parts = line.split("\t", -1);
                final State state = State.of(parts[0]);
                if (state == null) {
                    broken++;
                    continue;
                }
                try {
                    switch (state) {
                        case PENDING -> {
                            if (parts.length < PENDING_FIELDS) {
                                broken++;
                            } else {
                                final long id = Long.parseLong(parts[1]);
                                live.put(id, new Record(id, parts[2],
                                        Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
                                        Integer.parseInt(parts[5]), parts[7],
                                        NO_EXTRA.equals(parts[8]) ? null : parts[8],
                                        Long.parseLong(parts[6])));
                            }
                        }
                        case APPLYING -> {
                            // Félbehagyott visszaépítés: a rekord marad, a replay újrafuttatja.
                        }
                        case APPLIED -> applied.add(Long.parseLong(parts[1]));
                    }
                } catch (final RuntimeException malformed) {
                    broken++;
                }
            }
        } catch (final IOException failure) {
            logger.severe("block-regen napló olvasási hiba (" + file.getName() + "): " + failure);
        }
        if (broken > 0) {
            // Összeomlás közben félbe maradt utolsó sor a normális eset; többnél már gyanús.
            logger.warning("block-regen napló: " + broken + " értelmezhetetlen sor ("
                    + file.getName() + ") — kihagyva.");
        }
    }

    private boolean append(final String line, final boolean durable) {
        synchronized (writeLock) {
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
                if (!writeFailureLogged) {
                    writeFailureLogged = true;
                    logger.severe("block-regen napló írási hiba — a blokk-visszaépítés "
                            + "pillanatképei nem rögzíthetők: " + failure);
                }
                closeChannel();
                return false;
            }
        }
    }

    private FileChannel channel() throws IOException {
        if (wal == null || !wal.isOpen()) {
            final File parent = walFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            wal = FileChannel.open(walFile.toPath(), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }
        return wal;
    }

    private void rotate() throws IOException {
        closeChannel();
        if (!walFile.exists()) {
            return;
        }
        if (rotatedFile.exists()) {
            // Egy korábbi checkpoint megszakadt, a rotált napló még él: NEM írjuk felül,
            // hanem a végére fűzünk, különben a benne lévő rekordok elvesznének.
            try (OutputStream out = Files.newOutputStream(rotatedFile.toPath(),
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                Files.copy(walFile.toPath(), out);
            }
            Files.delete(walFile.toPath());
            return;
        }
        Files.move(walFile.toPath(), rotatedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void closeChannel() {
        if (wal == null) {
            return;
        }
        try {
            wal.close();
        } catch (final IOException ignored) {
            // A csatorna a következő íráskor újranyílik.
        }
        wal = null;
    }

    private static boolean hasSeparator(final String value) {
        return value == null || value.indexOf(SEP) >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }
}
