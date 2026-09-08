package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable, bounded memory keyed by the opaque Phase C instance identity. */
public final class TrashAnomalyStateStore implements PersistentStore {

    private static final long MAX_COUNTER = 1_000_000_000L;
    private static final int MAX_INSTANCES = 100_000;
    private final java.util.logging.Logger logger;
    private final java.util.concurrent.locks.ReentrantLock stateLock = new java.util.concurrent.locks.ReentrantLock();
    private boolean readable;
    private final File file;
    private final AtomicWriter writer;
    private final Map<UUID, EnumMap<MemoryKey, Long>> states = new LinkedHashMap<>();

    public TrashAnomalyStateStore(final JavaPlugin plugin) {
        this(new File(plugin.getDataFolder(), "trash-anomaly-state.yml"), plugin.getLogger());
    }

    TrashAnomalyStateStore(final File file, final java.util.logging.Logger logger) {
        this(file, logger, YamlStore::saveAtomic);
    }

    TrashAnomalyStateStore(final File file, final java.util.logging.Logger logger,
                           final AtomicWriter writer) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.file = Objects.requireNonNull(file, "file");
        this.writer = Objects.requireNonNull(writer, "writer");
        YamlStore.registerCriticalWrite(file);
    }

    @Override
    public void load() {
        stateLock.lock();
        try {
            readable = false;
            states.clear();
            if (!file.exists()) { readable = true; return; }
            final YamlConfiguration yaml = YamlStore.loadTracked(file, logger);
            if (yaml.getInt("schema-version", 0) != 1) {
                corrupt("ismeretlen schema-version");
            }
            final ConfigurationSection root = yaml.getConfigurationSection("instances");
            if (root == null) { readable = true; return; }
            for (final String rawId : root.getKeys(false)) {
                final UUID instanceId;
                try {
                    instanceId = UUID.fromString(rawId);
                } catch (final IllegalArgumentException malformed) {
                    corrupt("érvénytelen instance UUID: " + rawId);
                    return;
                }
                final ConfigurationSection section = root.getConfigurationSection(rawId);
                if (section == null) {
                    corrupt("nem objektum anomaly state: " + rawId);
                    return;
                }
                final EnumMap<MemoryKey, Long> memory = new EnumMap<>(MemoryKey.class);
                for (final String rawKey : section.getKeys(false)) {
                    final MemoryKey key;
                    try {
                        key = MemoryKey.valueOf(rawKey);
                    } catch (final IllegalArgumentException unknown) {
                        corrupt("ismeretlen anomaly memory key: " + rawKey);
                        return;
                    }
                    final long value = section.getLong(rawKey, -1L);
                    if (value < 0L || value > MAX_COUNTER) {
                        corrupt("érvénytelen anomaly memory érték: " + rawId + "." + rawKey);
                        return;
                    }
                    memory.put(key, value);
                }
                if (!memory.isEmpty()) states.put(instanceId, memory);
                if (states.size() > MAX_INSTANCES) {
                    corrupt("túl sok anomaly memory instance");
                    return;
                }
            }
            readable = true;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public void save() {
        stateLock.lock();
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("schema-version", 1);
            for (final Map.Entry<UUID, EnumMap<MemoryKey, Long>> entry : states.entrySet()) {
                for (final Map.Entry<MemoryKey, Long> memory : entry.getValue().entrySet()) {
                    yaml.set("instances." + entry.getKey() + "." + memory.getKey().name(),
                            memory.getValue());
                }
            }
            // saveAtomic is the checked-I/O boundary; tracked loads fail closed without checked I/O.
            try {
                writer.write(file, yaml);
            } catch (final IOException failure) {
                throw new UncheckedIOException("Nem menthető a Trash anomaly state", failure);
            }
        } catch (final RuntimeException | Error failure) {
            readable = false;
            throw failure;
        } finally {
            stateLock.unlock();
        }
    }

    public long get(final UUID instanceId, final MemoryKey key) {
        stateLock.lock();
        try {
            final EnumMap<MemoryKey, Long> memory = states.get(instanceId);
            return memory == null ? 0L : memory.getOrDefault(key, 0L);
        } finally {
            stateLock.unlock();
        }
    }

    public long add(final UUID instanceId, final MemoryKey key, final long delta) {
        stateLock.lock();
        try {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(key, "key");
            if (delta < 0L) throw new IllegalArgumentException("a memory delta nem lehet negatív");
            if (!states.containsKey(instanceId) && states.size() >= MAX_INSTANCES) return 0L;
            final EnumMap<MemoryKey, Long> memory = states.computeIfAbsent(instanceId,
                    ignored -> new EnumMap<>(MemoryKey.class));
            final long current = memory.getOrDefault(key, 0L);
            final long next = delta >= MAX_COUNTER - current ? MAX_COUNTER : current + delta;
            memory.put(key, next);
            return next;
        } finally {
            stateLock.unlock();
        }
    }

    /** Commits rare significant counters immediately and rolls memory back on write failure. */
    public long addDurably(final UUID instanceId, final MemoryKey key,
                                        final long delta) {
        stateLock.lock();
        try {
            final EnumMap<MemoryKey, Long> before = states.containsKey(instanceId)
                    ? new EnumMap<>(states.get(instanceId)) : null;
            final long next = add(instanceId, key, delta);
            try {
                save();
                return next;
            } catch (final RuntimeException | Error failure) {
                if (before == null) states.remove(instanceId);
                else states.put(instanceId, before);
                throw failure;
            }
        } finally {
            stateLock.unlock();
        }
    }

    public int size() {
        stateLock.lock();
        try {
            return states.size();
        } finally {
            stateLock.unlock();
        }
    }

    /** Runtime memory is detached under the native lock; storage contention never blocks the caller. */
    public java.util.Optional<Map<MemoryKey, Long>> tryInspect(final UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (stateLock.isHeldByCurrentThread() || !stateLock.tryLock()) return java.util.Optional.empty();
        try {
            if (!readable) return java.util.Optional.empty();
            final Map<MemoryKey, Long> memory = states.get(instanceId);
            return java.util.Optional.of(memory == null ? Map.of() : Map.copyOf(memory));
        } finally {
            stateLock.unlock();
        }
    }

    private void corrupt(final String reason) {
        YamlStore.failCorrupt(file, logger, reason);
        throw new IllegalStateException("Sérült Trash anomaly state: " + reason);
    }

    public enum MemoryKey {
        LOCAL_PLAYER_DEATHS,
        WATCHED_TICKS
    }

    @FunctionalInterface
    interface AtomicWriter {
        void write(File file, YamlConfiguration yaml) throws IOException;
    }
}
