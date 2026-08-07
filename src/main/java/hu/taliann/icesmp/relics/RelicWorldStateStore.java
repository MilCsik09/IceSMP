package hu.taliann.icesmp.relics;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * A világ-szintű relic aggregátum (ownership + lost/reclaim + awakening ready-at) egyetlen
 * single-writer perzisztencia-határa. Minden logikai mutáció (állapot-ellenőrzés → módosítás
 * → durable write) EGY szerializált kritikus szekció: párhuzamos régió-szálak nem
 * veszíthetik el egymás commitolt állapotát, és két konkurens Awakening-aktiválásból
 * pontosan egy lehet ARMED. Sikertelen durable write esetén a memória-állapot visszaáll a
 * commit előtti értékre (fail-closed: a runtime sosem jelenthet olyan sikert, ami a
 * lemezen nincs meg). Az olvasások lock-mentesek (concurrent map).
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

    private final Object writeLock = new Object();
    private final Map<String, RelicOwnership> ownerships = new ConcurrentHashMap<>();
    private final Map<String, Long> lostSince = new ConcurrentHashMap<>();
    private final Map<String, Long> awakeningReadyAt = new ConcurrentHashMap<>();
    private final DurableWriter writer;
    private final Logger logger;
    private volatile Consumer<String> mutationListener;

    public RelicWorldStateStore(final DurableWriter writer, final Logger logger) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Sikeres mutáció után hívódik a relic-id-vel (pl. birtoklás-cache invalidálásához). */
    public void setMutationListener(final Consumer<String> listener) {
        this.mutationListener = listener;
    }

    // ---------- lock-mentes olvasások ----------

    public RelicOwnership ownership(final String relicId) {
        return relicId == null || relicId.isBlank() ? null : ownerships.get(normalize(relicId));
    }

    public Map<String, RelicOwnership> ownershipsView() {
        return Map.copyOf(ownerships);
    }

    public boolean isLost(final String relicId) {
        return relicId != null && lostSince.containsKey(normalize(relicId));
    }

    public Long lostSince(final String relicId) {
        return relicId == null ? null : lostSince.get(normalize(relicId));
    }

    public long awakeningReadyAt(final String relicId) {
        if (relicId == null) {
            return 0L;
        }
        final Long readyAt = awakeningReadyAt.get(normalize(relicId));
        return readyAt == null ? 0L : readyAt;
    }

    // ---------- szerializált logikai műveletek ----------

    public void loadFrom(final YamlConfiguration yaml) {
        synchronized (writeLock) {
            ownerships.clear();
            lostSince.clear();
            awakeningReadyAt.clear();
            if (yaml == null) {
                return;
            }
            final ConfigurationSection awakeningSection = yaml.getConfigurationSection("awakening");
            if (awakeningSection != null) {
                for (final String relicId : awakeningSection.getKeys(false)) {
                    final long readyAt = awakeningSection.getLong(relicId + ".ready-at", 0L);
                    if (readyAt > 0L) {
                        awakeningReadyAt.put(normalize(relicId), readyAt);
                    }
                }
            }
            final ConfigurationSection ownershipSection = yaml.getConfigurationSection("ownerships");
            if (ownershipSection == null) {
                return;
            }
            for (final String relicId : ownershipSection.getKeys(false)) {
                final String rawOwner = ownershipSection.getString(relicId + ".owner");
                if (rawOwner == null || rawOwner.isBlank()) {
                    continue;
                }
                try {
                    ownerships.put(normalize(relicId), new RelicOwnership(UUID.fromString(rawOwner),
                            ownershipSection.getLong(relicId + ".last-seen", 0L)));
                } catch (final IllegalArgumentException invalid) {
                    logger.warning("Invalid owner UUID in relics.yml for relic '" + relicId
                            + "': " + rawOwner);
                    continue;
                }
                final long lost = ownershipSection.getLong(relicId + ".lost-since", 0L);
                if (lost > 0L) {
                    lostSince.put(normalize(relicId), lost);
                }
            }
        }
    }

    /** Az aktuális állapot durable kiírása (disable-kori mentés is ezt használja). */
    public void persist() {
        synchronized (writeLock) {
            persistLocked();
        }
    }

    public void recordOwnership(final String relicId, final UUID owner, final long nowMillis) {
        if (relicId == null || relicId.isBlank() || owner == null) {
            return;
        }
        final String key = normalize(relicId);
        synchronized (writeLock) {
            final RelicOwnership previous = ownerships.put(key,
                    new RelicOwnership(owner, nowMillis));
            try {
                persistLocked();
            } catch (final RuntimeException failure) {
                restoreOwnership(key, previous);
                throw failure;
            }
        }
        notifyMutation(key);
    }

    /** @return true, ha ténylegesen volt törölhető ownership vagy lost-jelölés */
    public boolean releaseOwnership(final String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return false;
        }
        final String key = normalize(relicId);
        synchronized (writeLock) {
            final RelicOwnership previousOwnership = ownerships.remove(key);
            final Long previousLost = lostSince.remove(key);
            if (previousOwnership == null && previousLost == null) {
                return false;
            }
            try {
                persistLocked();
            } catch (final RuntimeException failure) {
                restoreOwnership(key, previousOwnership);
                restoreLost(key, previousLost);
                throw failure;
            }
        }
        notifyMutation(key);
        return true;
    }

    public void markLost(final String relicId, final long nowMillis) {
        if (relicId == null || relicId.isBlank()) {
            return;
        }
        final String key = normalize(relicId);
        synchronized (writeLock) {
            final Long previous = lostSince.put(key, nowMillis);
            try {
                persistLocked();
            } catch (final RuntimeException failure) {
                restoreLost(key, previous);
                throw failure;
            }
        }
        notifyMutation(key);
    }

    /** @return true, ha volt törölhető lost-jelölés */
    public boolean clearLost(final String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return false;
        }
        final String key = normalize(relicId);
        synchronized (writeLock) {
            final Long previous = lostSince.remove(key);
            if (previous == null) {
                return false;
            }
            try {
                persistLocked();
            } catch (final RuntimeException failure) {
                restoreLost(key, previous);
                throw failure;
            }
        }
        notifyMutation(key);
        return true;
    }

    /** A játékos MINDEN relikviáján frissíti a last-seen bélyeget. @return true, ha változott. */
    public boolean markOwnerSeen(final UUID playerId, final long nowMillis) {
        if (playerId == null) {
            return false;
        }
        synchronized (writeLock) {
            final Map<String, RelicOwnership> previous = new LinkedHashMap<>();
            for (final Map.Entry<String, RelicOwnership> entry : ownerships.entrySet()) {
                if (playerId.equals(entry.getValue().owner())) {
                    previous.put(entry.getKey(), entry.getValue());
                    entry.setValue(new RelicOwnership(playerId, nowMillis));
                }
            }
            if (previous.isEmpty()) {
                return false;
            }
            try {
                persistLocked();
            } catch (final RuntimeException failure) {
                ownerships.putAll(previous);
                throw failure;
            }
        }
        return true;
    }

    /**
     * Atomikus Awakening-aktiválás: a ready-at ellenőrzés, az új érték kiszámítása és a
     * durable commit EGY kritikus szekció — két konkurens hívásból pontosan egy ARMED.
     * Sikertelen durable írásnál a memória-állapot visszaáll, az eredmény
     * PERSISTENCE_FAILED: ARMED siker csak megtörtént lemez-commit után jelenthető.
     */
    public ArmResult tryArmAwakening(final String relicId, final long nowMillis,
                                     final long cooldownSeconds) {
        if (relicId == null || relicId.isBlank() || cooldownSeconds < 0L) {
            return ArmResult.PERSISTENCE_FAILED;
        }
        final String key = normalize(relicId);
        synchronized (writeLock) {
            final Long previous = awakeningReadyAt.get(key);
            final long readyAt = previous == null ? 0L : previous;
            if (nowMillis < readyAt) {
                return ArmResult.ON_COOLDOWN;
            }
            awakeningReadyAt.put(key,
                    Math.addExact(nowMillis, Math.multiplyExact(cooldownSeconds, 1000L)));
            try {
                persistLocked();
            } catch (final RuntimeException failure) {
                if (previous == null) {
                    awakeningReadyAt.remove(key);
                } else {
                    awakeningReadyAt.put(key, previous);
                }
                logger.severe("Awakening arm rolled back (durable write failed) for '" + key
                        + "': " + failure.getMessage());
                return ArmResult.PERSISTENCE_FAILED;
            }
            return ArmResult.ARMED;
        }
    }

    // ---------- belsők ----------

    private void persistLocked() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<String, RelicOwnership> entry : ownerships.entrySet()) {
            final String basePath = "ownerships." + entry.getKey();
            yaml.set(basePath + ".owner", entry.getValue().owner().toString());
            yaml.set(basePath + ".last-seen", entry.getValue().lastSeenMillis());
            final Long lost = lostSince.get(entry.getKey());
            if (lost != null) {
                yaml.set(basePath + ".lost-since", lost);
            }
        }
        for (final Map.Entry<String, Long> entry : awakeningReadyAt.entrySet()) {
            yaml.set("awakening." + entry.getKey() + ".ready-at", entry.getValue());
        }
        try {
            writer.write(yaml);
        } catch (final IOException failure) {
            throw new java.io.UncheckedIOException("Failed to save relic world state", failure);
        }
    }

    private void restoreOwnership(final String key, final RelicOwnership previous) {
        if (previous == null) {
            ownerships.remove(key);
        } else {
            ownerships.put(key, previous);
        }
    }

    private void restoreLost(final String key, final Long previous) {
        if (previous == null) {
            lostSince.remove(key);
        } else {
            lostSince.put(key, previous);
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
