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
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, EnumMap<MemoryKey, Long>> states = new LinkedHashMap<>();

    public TrashAnomalyStateStore(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = new File(plugin.getDataFolder(), "trash-anomaly-state.yml");
        YamlStore.registerCriticalWrite(file);
    }

    @Override
    public synchronized void load() {
        states.clear();
        if (!file.exists()) return;
        final YamlConfiguration yaml = YamlStore.loadTracked(file, plugin.getLogger());
        if (yaml.getInt("schema-version", 0) != 1) {
            corrupt("ismeretlen schema-version");
        }
        final ConfigurationSection root = yaml.getConfigurationSection("instances");
        if (root == null) return;
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
    }

    @Override
    public synchronized void save() {
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
            YamlStore.saveAtomic(file, yaml);
        } catch (final IOException failure) {
            throw new UncheckedIOException("Nem menthető a Trash anomaly state", failure);
        }
    }

    public synchronized long get(final UUID instanceId, final MemoryKey key) {
        final EnumMap<MemoryKey, Long> memory = states.get(instanceId);
        return memory == null ? 0L : memory.getOrDefault(key, 0L);
    }

    public synchronized long add(final UUID instanceId, final MemoryKey key, final long delta) {
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
    }

    /** Commits rare significant counters immediately and rolls memory back on write failure. */
    public synchronized long addDurably(final UUID instanceId, final MemoryKey key,
                                        final long delta) {
        final EnumMap<MemoryKey, Long> before = states.containsKey(instanceId)
                ? new EnumMap<>(states.get(instanceId)) : null;
        final long next = add(instanceId, key, delta);
        try {
            save();
            return next;
        } catch (final RuntimeException failure) {
            if (before == null) states.remove(instanceId);
            else states.put(instanceId, before);
            throw failure;
        }
    }

    public synchronized int size() {
        return states.size();
    }

    private void corrupt(final String reason) {
        YamlStore.failCorrupt(file, plugin.getLogger(), reason);
        throw new IllegalStateException("Sérült Trash anomaly state: " + reason);
    }

    public enum MemoryKey {
        LOCAL_PLAYER_DEATHS,
        WATCHED_TICKS
    }
}
