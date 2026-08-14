package hu.taliann.icesmp.relics;

import hu.taliann.icesmp.relics.RelicWorldStateSnapshot.PendingRelicOperation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * A világ-szintű relic aggregátum (ownership + lost/reclaim + awakening + művelet-journal)
 * single-writer perzisztencia-határa, PUBLISH-COMMIT sorrenddel: minden mutáció a
 * kritikus szekcióban immutable candidate pillanatképet épít, azt írja durable-re, és
 * CSAK sikeres írás után publikálja (volatile csere). A runtime-ból látható committed
 * állapot így mindig részhalmaza a durable állapotnak — sikertelen írásnál a candidate
 * egyszerűen eldobódik, olvasó sosem láthatta. Az olvasások lock-mentesek és mindig
 * egyetlen teljes pillanatképet látnak (reload alatt sincs üres/félig-töltött köztes
 * állapot). A lost-mutáció owner-kötött: markLost csak a bizonyított aktuális
 * tulajdonossal fogadható el, árva lost állapot nem létezhet.
 */
public final class RelicWorldStateStore {

    /** A durable írás cserélhető, hogy a viselkedés valódi fájl nélkül is bizonyítható. */
    @FunctionalInterface
    public interface DurableWriter {
        void write(YamlConfiguration yaml) throws IOException;
    }

    public enum ArmResult {
        ARMED,
        ON_COOLDOWN,
        PERSISTENCE_FAILED
    }

    public enum MarkLostResult {
        MARKED,
        NOT_OWNER,
        PERSISTENCE_FAILED
    }

    public enum TransferResult {
        TRANSFERRED,
        NOT_OWNER,
        PERSISTENCE_FAILED
    }

    private final Object writeLock = new Object();
    private final DurableWriter writer;
    private final Logger logger;
    private volatile RelicWorldStateSnapshot current = RelicWorldStateSnapshot.EMPTY;
    private volatile Consumer<String> mutationListener;

    public RelicWorldStateStore(final DurableWriter writer, final Logger logger) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Sikeres mutáció után hívódik a relic-id-vel (pl. birtoklás-cache invalidálásához). */
    public void setMutationListener(final Consumer<String> listener) {
        this.mutationListener = listener;
    }

    // ---------- lock-mentes pillanatkép-olvasások ----------

    public RelicWorldStateSnapshot snapshot() {
        return current;
    }

    public RelicOwnership ownership(final String relicId) {
        return relicId == null || relicId.isBlank() ? null
                : current.ownerships().get(normalize(relicId));
    }

    public Map<String, RelicOwnership> ownershipsView() {
        return current.ownerships();
    }

    public boolean isLost(final String relicId) {
        return relicId != null && current.lostSince().containsKey(normalize(relicId));
    }

    public Long lostSince(final String relicId) {
        return relicId == null ? null : current.lostSince().get(normalize(relicId));
    }

    public long awakeningReadyAt(final String relicId) {
        if (relicId == null) {
            return 0L;
        }
        final Long readyAt = current.awakeningReadyAt().get(normalize(relicId));
        return readyAt == null ? 0L : readyAt;
    }

    public PendingRelicOperation pendingOperation(final String relicId) {
        return relicId == null ? null : current.operations().get(normalize(relicId));
    }

    public Map<String, PendingRelicOperation> pendingOperationsFor(final UUID owner) {
        if (owner == null) {
            return Map.of();
        }
        final LinkedHashMap<String, PendingRelicOperation> result = new LinkedHashMap<>();
        current.operations().forEach((relicId, operation) -> {
            if (owner.equals(operation.toOwner())) {
                result.put(relicId, operation);
            }
        });
        return Map.copyOf(result);
    }

    // ---------- betöltés: teljes candidate, egyetlen atomikus publish ----------

    /**
     * A durable állapot beolvasása. A candidate lokális változókban épül fel teljesen,
     * és egyetlen volatile cserével publikálódik — konkurens olvasó vagy a régi teljes,
     * vagy az új teljes pillanatképet látja, üres/hibrid köztest soha.
     */
    public void loadFrom(final YamlConfiguration yaml) {
        synchronized (writeLock) {
            if (yaml == null) {
                current = RelicWorldStateSnapshot.EMPTY;
                return;
            }
            final LinkedHashMap<String, RelicOwnership> ownerships = new LinkedHashMap<>();
            final LinkedHashMap<String, Long> lostSince = new LinkedHashMap<>();
            final LinkedHashMap<String, Long> awakening = new LinkedHashMap<>();
            final LinkedHashMap<String, PendingRelicOperation> operations = new LinkedHashMap<>();

            final ConfigurationSection awakeningSection = yaml.getConfigurationSection("awakening");
            if (awakeningSection != null) {
                for (final String relicId : awakeningSection.getKeys(false)) {
                    final long readyAt = awakeningSection.getLong(relicId + ".ready-at", 0L);
                    if (readyAt > 0L) {
                        awakening.put(normalize(relicId), readyAt);
                    }
                }
            }
            final ConfigurationSection ownershipSection = yaml.getConfigurationSection("ownerships");
            if (ownershipSection != null) {
                for (final String relicId : ownershipSection.getKeys(false)) {
                    final String rawOwner = ownershipSection.getString(relicId + ".owner");
                    if (rawOwner == null || rawOwner.isBlank()) {
                        continue;
                    }
                    final UUID owner;
                    try {
                        owner = UUID.fromString(rawOwner);
                    } catch (final IllegalArgumentException invalid) {
                        logger.warning("Invalid owner UUID in relics.yml for relic '" + relicId
                                + "': " + rawOwner);
                        continue;
                    }
                    final String key = normalize(relicId);
                    ownerships.put(key, new RelicOwnership(owner,
                            ownershipSection.getLong(relicId + ".last-seen", 0L)));
                    // A lost-since kizárólag ownership-rekord ALATT él — árva lost a
                    // fájlból sem jöhet létre (a snapshot-invariáns is kizárja).
                    final long lost = ownershipSection.getLong(relicId + ".lost-since", 0L);
                    if (lost > 0L) {
                        lostSince.put(key, lost);
                    }
                }
            }
            final ConfigurationSection operationSection = yaml.getConfigurationSection("operations");
            if (operationSection != null) {
                for (final String relicId : operationSection.getKeys(false)) {
                    final PendingRelicOperation operation = parseOperation(relicId,
                            operationSection.getConfigurationSection(relicId));
                    if (operation != null) {
                        operations.put(normalize(relicId), operation);
                    }
                }
            }
            current = new RelicWorldStateSnapshot(ownerships, lostSince, awakening, operations);
        }
    }

    private PendingRelicOperation parseOperation(final String relicId,
                                                 final ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        try {
            final String from = section.getString("from", "");
            return new PendingRelicOperation(
                    PendingRelicOperation.Type.valueOf(
                            section.getString("type", "").toUpperCase(Locale.ROOT)),
                    from.isBlank() ? null : UUID.fromString(from),
                    UUID.fromString(section.getString("to", "")));
        } catch (final IllegalArgumentException corrupt) {
            logger.severe("Corrupt pending relic operation dropped for '" + relicId + "': "
                    + corrupt.getMessage());
            return null;
        }
    }

    /** Az aktuális publikált állapot durable kiírása (disable-kori mentés). */
    public void persist() {
        synchronized (writeLock) {
            persistLocked(current);
        }
    }

    // ---------- szerializált logikai műveletek (candidate → durable → publish) ----------

    public void recordOwnership(final String relicId, final UUID owner, final long nowMillis) {
        if (relicId == null || relicId.isBlank() || owner == null) {
            return;
        }
        final String key = normalize(relicId);
        commit(key, base -> {
            final LinkedHashMap<String, RelicOwnership> ownerships =
                    new LinkedHashMap<>(base.ownerships());
            ownerships.put(key, new RelicOwnership(owner, nowMillis));
            return new RelicWorldStateSnapshot(ownerships, base.lostSince(),
                    base.awakeningReadyAt(), base.operations());
        });
    }

    /**
     * Claim/reclaim világ-oldali commitja EGY durable írásban: ownership az új
     * tulajdonosra, lost-jelölés törölve, és a fizikai kézbesítés függő receiptje
     * rögzítve — a kézbesítés előtti crash után a recovery a receiptből
     * determinisztikusan tudja, hogy kézbesíteni kell.
     */
    public void beginClaim(final String relicId, final UUID owner, final long nowMillis,
                           final PendingRelicOperation.Type type) {
        Objects.requireNonNull(owner, "owner");
        if (relicId == null || relicId.isBlank()
                || type == PendingRelicOperation.Type.TRANSFER) {
            throw new IllegalArgumentException("invalid claim arguments: " + relicId + "/" + type);
        }
        final String key = normalize(relicId);
        commit(key, base -> {
            final LinkedHashMap<String, RelicOwnership> ownerships =
                    new LinkedHashMap<>(base.ownerships());
            final RelicOwnership previous = ownerships.put(key, new RelicOwnership(owner, nowMillis));
            final LinkedHashMap<String, Long> lostSince = new LinkedHashMap<>(base.lostSince());
            lostSince.remove(key);
            final LinkedHashMap<String, PendingRelicOperation> operations =
                    new LinkedHashMap<>(base.operations());
            operations.put(key, new PendingRelicOperation(type,
                    previous == null ? null : previous.owner(), owner));
            return new RelicWorldStateSnapshot(ownerships, lostSince,
                    base.awakeningReadyAt(), operations);
        });
    }

    /**
     * PvP-transfer világ-oldali commitja. A death-event scoped expected-owner bizonyítéka
     * (ha jelen van) felülírja a legacy manager köztes ownership-rereadjét, és az ellenőrzés
     * ugyanazon writeLock kritikus szekcióban történik, mint a durable commit. Mismatch vagy
     * persistence-hiba kivétellel állítja meg a legacy void callert, így a fizikai PDC sem
     * írható át sikertelen világ-commit után.
     */
    public TransferResult beginTransfer(final String relicId, final UUID expectedOwner,
                                        final UUID toOwner, final long nowMillis) {
        final UUID scopedExpectedOwner = RelicTransferExpectation.currentExpectedOwner();
        final UUID effectiveExpectedOwner = scopedExpectedOwner == null
                ? expectedOwner : scopedExpectedOwner;
        if (relicId == null || relicId.isBlank() || effectiveExpectedOwner == null || toOwner == null) {
            throw new IllegalArgumentException("relic transfer requires relicId, expectedOwner and toOwner");
        }
        final String key = normalize(relicId);
        synchronized (writeLock) {
            final RelicOwnership ownership = current.ownerships().get(key);
            if (ownership == null || !ownership.owner().equals(effectiveExpectedOwner)) {
                throw new IllegalStateException("relic transfer owner mismatch for '" + key + "'");
            }
            final LinkedHashMap<String, RelicOwnership> ownerships =
                    new LinkedHashMap<>(current.ownerships());
            ownerships.put(key, new RelicOwnership(toOwner, nowMillis));
            final LinkedHashMap<String, Long> lostSince = new LinkedHashMap<>(current.lostSince());
            lostSince.remove(key);
            final LinkedHashMap<String, PendingRelicOperation> operations =
                    new LinkedHashMap<>(current.operations());
            operations.put(key, new PendingRelicOperation(
                    PendingRelicOperation.Type.TRANSFER, effectiveExpectedOwner, toOwner));
            final RelicWorldStateSnapshot candidate = new RelicWorldStateSnapshot(
                    ownerships, lostSince, current.awakeningReadyAt(), operations);
            // Deliberately propagate persistence failure: RelicManager's legacy void API catches
            // RuntimeException and returns before mutating the physical item PDC.
            persistLocked(candidate);
            current = candidate;
        }
        notifyMutation(key);
        return TransferResult.TRANSFERRED;
    }

    /** A fizikai mellékhatás lezárult: a függő receipt törlése. Idempotens. */
    public boolean completeOperation(final String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return false;
        }
        final String key = normalize(relicId);
        final boolean[] changed = {false};
        commit(key, base -> {
            if (!base.operations().containsKey(key)) {
                return null;
            }
            changed[0] = true;
            final LinkedHashMap<String, PendingRelicOperation> operations =
                    new LinkedHashMap<>(base.operations());
            operations.remove(key);
            return new RelicWorldStateSnapshot(base.ownerships(), base.lostSince(),
                    base.awakeningReadyAt(), operations);
        });
        return changed[0];
    }

    /** @return true, ha ténylegesen volt törölhető ownership/lost/receipt */
    public boolean releaseOwnership(final String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return false;
        }
        final String key = normalize(relicId);
        final boolean[] changed = {false};
        commit(key, base -> {
            if (!base.ownerships().containsKey(key) && !base.operations().containsKey(key)) {
                return null;
            }
            changed[0] = true;
            final LinkedHashMap<String, RelicOwnership> ownerships =
                    new LinkedHashMap<>(base.ownerships());
            ownerships.remove(key);
            // Az elveszett-jelölés és a függő receipt a tulajdonnal együtt jár —
            // felszabadításkor mindkettő törlődik (az új tulajdonos tiszta lappal indul).
            final LinkedHashMap<String, Long> lostSince = new LinkedHashMap<>(base.lostSince());
            lostSince.remove(key);
            final LinkedHashMap<String, PendingRelicOperation> operations =
                    new LinkedHashMap<>(base.operations());
            operations.remove(key);
            return new RelicWorldStateSnapshot(ownerships, lostSince,
                    base.awakeningReadyAt(), operations);
        });
        return changed[0];
    }

    /**
     * Owner-kötött lost-mutáció: kizárólag a bizonyított AKTUÁLIS tulajdonossal
     * fogadható el — egy stale fizikai példány korábbi gazdájának halála nem teheti
     * LOST-ra másvalaki élő relicét, és tulajdonos nélkül lost állapot sem jöhet létre.
     */
    public MarkLostResult markLost(final String relicId, final UUID expectedOwner,
                                   final long nowMillis) {
        if (relicId == null || relicId.isBlank() || expectedOwner == null) {
            return MarkLostResult.NOT_OWNER;
        }
        final String key = normalize(relicId);
        synchronized (writeLock) {
            final RelicOwnership ownership = current.ownerships().get(key);
            if (ownership == null || !ownership.owner().equals(expectedOwner)) {
                return MarkLostResult.NOT_OWNER;
            }
            final LinkedHashMap<String, Long> lostSince = new LinkedHashMap<>(current.lostSince());
            lostSince.put(key, nowMillis);
            final RelicWorldStateSnapshot candidate = new RelicWorldStateSnapshot(
                    current.ownerships(), lostSince, current.awakeningReadyAt(),
                    current.operations());
            try {
                persistLocked(candidate);
            } catch (final RuntimeException failure) {
                logger.severe("markLost rolled back (durable write failed) for '" + key + "': "
                        + failure.getMessage());
                return MarkLostResult.PERSISTENCE_FAILED;
            }
            current = candidate;
        }
        notifyMutation(key);
        return MarkLostResult.MARKED;
    }

    /**
     * Az elveszett-jelölés törlése (visszakapott tárgy) — owner-kötött, a markLost
     * párjaként: stale példány gazdája nem törölheti a valódi tulajdonos lost-jelölését
     * (az a tulaj oltár-újraidézését blokkolná el).
     */
    public boolean clearLost(final String relicId, final UUID expectedOwner) {
        if (relicId == null || relicId.isBlank() || expectedOwner == null) {
            return false;
        }
        final String key = normalize(relicId);
        final boolean[] changed = {false};
        commit(key, base -> {
            final RelicOwnership ownership = base.ownerships().get(key);
            if (ownership == null || !ownership.owner().equals(expectedOwner)
                    || !base.lostSince().containsKey(key)) {
                return null;
            }
            changed[0] = true;
            final LinkedHashMap<String, Long> lostSince = new LinkedHashMap<>(base.lostSince());
            lostSince.remove(key);
            return new RelicWorldStateSnapshot(base.ownerships(), lostSince,
                    base.awakeningReadyAt(), base.operations());
        });
        return changed[0];
    }

    /** A játékos MINDEN relikviáján frissíti a last-seen bélyeget. @return true, ha változott. */
    public boolean markOwnerSeen(final UUID playerId, final long nowMillis) {
        if (playerId == null) {
            return false;
        }
        final boolean[] changed = {false};
        commit(null, base -> {
            final LinkedHashMap<String, RelicOwnership> ownerships =
                    new LinkedHashMap<>(base.ownerships());
            boolean any = false;
            for (final Map.Entry<String, RelicOwnership> entry : ownerships.entrySet()) {
                if (playerId.equals(entry.getValue().owner())) {
                    entry.setValue(new RelicOwnership(playerId, nowMillis));
                    any = true;
                }
            }
            if (!any) {
                return null;
            }
            changed[0] = true;
            return new RelicWorldStateSnapshot(ownerships, base.lostSince(),
                    base.awakeningReadyAt(), base.operations());
        });
        return changed[0];
    }

    /**
     * Atomikus Awakening-aktiválás: ready-at ellenőrzés, candidate, durable commit és
     * publish EGY kritikus szekcióban — két konkurens hívásból pontosan egy ARMED, és a
     * candidate érték sikertelen írásnál sosem válik láthatóvá.
     */
    public ArmResult tryArmAwakening(final String relicId, final long nowMillis,
                                     final long cooldownSeconds) {
        if (relicId == null || relicId.isBlank() || cooldownSeconds < 0L) {
            return ArmResult.PERSISTENCE_FAILED;
        }
        final String key = normalize(relicId);
        synchronized (writeLock) {
            final Long previous = current.awakeningReadyAt().get(key);
            final long readyAt = previous == null ? 0L : previous;
            if (nowMillis < readyAt) {
                return ArmResult.ON_COOLDOWN;
            }
            final LinkedHashMap<String, Long> awakening =
                    new LinkedHashMap<>(current.awakeningReadyAt());
            awakening.put(key, Math.addExact(nowMillis, Math.multiplyExact(cooldownSeconds, 1000L)));
            final RelicWorldStateSnapshot candidate = new RelicWorldStateSnapshot(
                    current.ownerships(), current.lostSince(), awakening, current.operations());
            try {
                persistLocked(candidate);
            } catch (final RuntimeException failure) {
                logger.severe("Awakening arm discarded (durable write failed) for '" + key
                        + "': " + failure.getMessage());
                return ArmResult.PERSISTENCE_FAILED;
            }
            current = candidate;
            return ArmResult.ARMED;
        }
    }

    // ---------- belsők ----------

    /**
     * Közös commit-minta: a kritikus szekcióban a candidate a PUBLIKÁLT pillanatképből
     * épül, durable-re íródik, és csak sikeres írás után cserélődik be — hibánál a
     * candidate eldobódik és a hiba a hívóhoz jut (a publikált állapot érintetlen).
     * A mutator {@code null} visszatérése = nincs változás (se írás, se publish).
     */
    private void commit(final String notifyRelicId,
                        final java.util.function.UnaryOperator<RelicWorldStateSnapshot> mutator) {
        synchronized (writeLock) {
            final RelicWorldStateSnapshot candidate = mutator.apply(current);
            if (candidate == null) {
                return;
            }
            persistLocked(candidate);
            current = candidate;
        }
        if (notifyRelicId != null) {
            notifyMutation(notifyRelicId);
        }
    }

    private void persistLocked(final RelicWorldStateSnapshot snapshot) {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<String, RelicOwnership> entry : snapshot.ownerships().entrySet()) {
            final String basePath = "ownerships." + entry.getKey();
            yaml.set(basePath + ".owner", entry.getValue().owner().toString());
            yaml.set(basePath + ".last-seen", entry.getValue().lastSeenMillis());
            final Long lost = snapshot.lostSince().get(entry.getKey());
            if (lost != null) {
                yaml.set(basePath + ".lost-since", lost);
            }
        }
        for (final Map.Entry<String, Long> entry : snapshot.awakeningReadyAt().entrySet()) {
            yaml.set("awakening." + entry.getKey() + ".ready-at", entry.getValue());
        }
        for (final Map.Entry<String, PendingRelicOperation> entry
                : snapshot.operations().entrySet()) {
            final String basePath = "operations." + entry.getKey();
            yaml.set(basePath + ".type", entry.getValue().type().name());
            if (entry.getValue().fromOwner() != null) {
                yaml.set(basePath + ".from", entry.getValue().fromOwner().toString());
            }
            yaml.set(basePath + ".to", entry.getValue().toOwner().toString());
        }
        try {
            writer.write(yaml);
        } catch (final IOException failure) {
            throw new java.io.UncheckedIOException("Failed to save relic world state", failure);
        }
    }

    private void notifyMutation(final String relicId) {
        final Consumer<String> listener = mutationListener;
        if (listener != null) {
            try {
                listener.accept(relicId);
            } catch (final RuntimeException failure) {
                logger.warning("Relic world-state mutation listener failed for '" + relicId
                        + "': " + failure.getMessage());
            }
        }
    }

    private static String normalize(final String relicId) {
        return relicId.toLowerCase(Locale.ROOT);
    }
}
