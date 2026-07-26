package hu.taliann.icesmp.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Idempotens write-ahead napló YAML felett.
 *
 * <p>Egy tranzakció (piaci vásárlás, licit, listázás, tárgy-átadás) több FÜGGETLEN
 * tároló-fájlt és a játékos inventoryját is érinti, amikre nincs közös atomi commit:
 * a {@code YamlStore.saveAtomic} EGY fájlt ír atomikusan, de a fájlok EGYMÁSHOZ képest
 * nem konzisztensek, ha közben elhal a folyamat. A napló ezt zárja le: minden hatás
 * ELŐTT lemezre kerül a tranzakció leírása (egyedi ID + payload), és a hatások
 * alkalmazása után a bejegyzés tartósan eltűnik. Indulás után a megmaradt bejegyzések
 * = félbehagyott tranzakciók, amiket a hívó determinisztikusan befejez vagy visszaforgat.
 *
 * <p>A napló-írás SZINKRON (a hívó szálán) — ez szándékos: egy debounce-olt írás
 * pontosan azt a rést hagyná nyitva, amit a napló bezár. A fájl normál üzemben üres
 * vagy egy-két bejegyzést tartalmaz, a tranzakciók pedig ember-ütemű kattintások.
 *
 * <p>A {@link #isHealthy()} false, ha a napló-fájl nem olvasható/írható. A hívónak
 * ilyenkor MEG KELL TAGADNIA a tranzakciót: naplózás nélkül nincs recovery, tehát a
 * tranzakció pont attól nem védett, ami ellen készült.
 */
public final class TransactionJournal {

    /**
     * Egy folyamatban lévő tranzakció naplóbejegyzése: egyedi azonosító + szabad
     * payload. A payloadot a hívó tölti (érintett egyenlegek a művelet ELŐTTI
     * absztolút értékkel, tárgy-pillanatképek, tulajdonos-UUID-k), és a recovery
     * kizárólag ebből dolgozik — a bejegyzés önmagában elég a döntéshez.
     */
    public static final class Entry {

        private final UUID id;
        private final String type;
        private final long createdAt;
        private final YamlConfiguration data;

        private Entry(final UUID id, final String type, final long createdAt, final YamlConfiguration data) {
            this.id = id;
            this.type = type;
            this.createdAt = createdAt;
            this.data = data;
        }

        public UUID id() {
            return id;
        }

        public String type() {
            return type;
        }

        public long createdAt() {
            return createdAt;
        }

        public ConfigurationSection data() {
            return data;
        }
    }

    private final File file;
    private final Logger logger;
    /** Beszúrási sorrendben tartott nyitott bejegyzések; a monitor védi. */
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private volatile boolean healthy = true;

    public TransactionJournal(final File file, final Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /**
     * Betölti a nyitott bejegyzéseket. Sérült napló-fájlnál a store karanténba teszi a
     * fájlt és letiltja az írását — ilyenkor a napló nem egészséges.
     */
    public synchronized void load() {
        entries.clear();
        final YamlConfiguration yaml = YamlStore.loadTracked(file, logger);
        healthy = !YamlStore.isLoadFailed(file);

        final ConfigurationSection section = yaml.getConfigurationSection("entries");
        if (section == null) {
            return;
        }

        for (final String idKey : section.getKeys(false)) {
            try {
                final UUID id = UUID.fromString(idKey);
                final YamlConfiguration data = new YamlConfiguration();
                final ConfigurationSection stored = section.getConfigurationSection(idKey + ".data");
                if (stored != null) {
                    copyValues(stored, data, "");
                }
                entries.put(id, new Entry(id, section.getString(idKey + ".type", ""),
                        section.getLong(idKey + ".created-at", 0L), data));
            } catch (final IllegalArgumentException ignored) {
                // Hibás azonosító: a bejegyzés nem értelmezhető, kihagyjuk.
            }
        }
    }

    /** Új, még NEM tartós bejegyzés. A payload feltöltése után {@link #prepare(Entry)} teszi lemezre. */
    public Entry create(final String type) {
        return new Entry(UUID.randomUUID(), type, System.currentTimeMillis(), new YamlConfiguration());
    }

    /**
     * Write-ahead korlát: a bejegyzés lemezre kerül, MIELŐTT a tranzakció bármit
     * módosítana. False esetén a hívó nem kezdheti el a tranzakciót.
     */
    public synchronized boolean prepare(final Entry entry) {
        entries.put(entry.id(), entry);
        if (flush()) {
            return true;
        }
        entries.remove(entry.id());
        return false;
    }

    /**
     * A tranzakció lezárása: a bejegyzés tartósan eltűnik a naplóból.
     *
     * <p>A visszatérési érték KÖTELEZŐEN vizsgálandó. Ha a lemezre-írás bukik, a bejegyzés
     * visszakerül a memóriába — enélkül a napló-bejegyzés a LEMEZEN maradt, a hívó viszont
     * (a commit-tanút eldobva) befejezettnek hitte a tranzakciót, és a következő mentés
     * tanú nélkül írt: az újraindulás „nem commitolt"-nak minősítette a már kifizetett
     * vásárlást és visszaforgatta a pénzt, miközben a tárgy a vevőnél volt.
     *
     * @return true, ha a bejegyzés bizonyítottan eltűnt a lemezről is
     */
    public synchronized boolean complete(final Entry entry) {
        final Entry removed = entries.remove(entry.id());
        if (removed == null) {
            return true; // már nincs nyitva
        }
        if (flush()) {
            return true;
        }
        entries.put(removed.id(), removed);
        return false;
    }

    /** A még nyitott (indulás után: félbehagyott) bejegyzések pillanatképe. */
    public synchronized List<Entry> pending() {
        return List.copyOf(entries.values());
    }

    public boolean isHealthy() {
        return healthy;
    }

    private boolean flush() {
        if (YamlStore.isLoadFailed(file)) {
            return false;
        }

        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Entry entry : entries.values()) {
            final String base = "entries." + entry.id();
            yaml.set(base + ".type", entry.type());
            yaml.set(base + ".created-at", entry.createdAt());
            copyValues(entry.data, yaml, base + ".data.");
        }

        try {
            YamlStore.saveAtomic(file, yaml);
            return true;
        } catch (final IOException exception) {
            healthy = false;
            logger.severe("A tranzakciós napló (" + file.getName() + ") nem írható: " + exception.getMessage());
            return false;
        }
    }

    /**
     * Levél-értékek átmásolása szakaszok között. A köztes szakaszokat kihagyjuk: a
     * {@code getKeys(true)} a leveleket teljes útvonallal is felsorolja, a szakasz-objektum
     * beállítása pedig felülírná a már bemásolt gyerekeket.
     */
    private static void copyValues(final ConfigurationSection source, final YamlConfiguration target,
                                   final String prefix) {
        for (final String key : source.getKeys(true)) {
            final Object value = source.get(key);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            target.set(prefix + key, value);
        }
    }
}
