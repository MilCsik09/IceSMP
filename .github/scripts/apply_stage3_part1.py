#!/usr/bin/env python3
from __future__ import annotations
import pathlib, re
ROOT = pathlib.Path(__file__).resolve().parents[2]

def read(p): return (ROOT/p).read_text(encoding='utf-8')
def write(p,s):
    q=ROOT/p; q.parent.mkdir(parents=True,exist_ok=True); q.write_text(s,encoding='utf-8')
def once(p,old,new):
    s=read(p); c=s.count(old)
    if c!=1: raise RuntimeError(f'{p}: expected 1 occurrence, got {c}: {old[:120]!r}')
    write(p,s.replace(old,new,1))
def regex_once(p,pat,repl,flags=0):
    s=read(p); n,c=re.subn(pat,repl,s,count=1,flags=flags)
    if c!=1: raise RuntimeError(f'{p}: regex expected 1, got {c}: {pat}')
    write(p,n)

# ---------------- Config manager: immutable base/effective snapshots + optimistic batch writes ----------------
write('src/main/java/hu/taliann/icesmp/managers/ConfigManager.java', r'''package hu.taliann.icesmp.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Centralized, atomically-published configuration with conflict-safe admin writes. */
public final class ConfigManager {

    /** One immutable publication unit: packaged defaults, effective values and override provenance. */
    public record ConfigSnapshot(FileConfiguration configuration,
                                 FileConfiguration baseConfiguration,
                                 Set<String> overridePaths,
                                 long generation,
                                 String sourceFingerprint) {
        public ConfigSnapshot {
            overridePaths = overridePaths == null ? Set.of() : Set.copyOf(overridePaths);
            sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
        }

        /** Compatibility constructor retained for tests/consumers that only need the effective tree. */
        public ConfigSnapshot(final FileConfiguration configuration, final Set<String> overridePaths,
                              final long generation) {
            this(configuration, configuration, overridePaths, generation, "");
        }

        public boolean isSet(final String path) {
            return configuration != null && configuration.isSet(path);
        }

        public boolean isOverridden(final String path) {
            return overridePaths.contains(path);
        }

        public Object baseValue(final String path) {
            return baseConfiguration == null ? null : baseConfiguration.get(path);
        }
    }

    public enum BatchApplyResult { APPLIED, STALE, NO_CHANGES }

    /** Bundled per-subsystem config files under config/. */
    private static final String[] CONFIG_FILES = {
            "general", "economy", "factions", "classes", "spells", "spells-balance",
            "professions", "quests", "world", "relics", "pets", "crafting", "crates", "afk", "moderation",
            "item-rarity", "loot", "motd", "profession-materials", "profession-recipes", "sit", "tablist", "dev-items"
    };

    private final JavaPlugin plugin;
    private volatile ConfigSnapshot liveSnapshot = new ConfigSnapshot(null, null, Set.of(), 0L, "");

    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void load() {
        final YamlConfiguration base = loadBaseConfiguration();
        final YamlConfiguration effective = new YamlConfiguration();
        mergeInto(effective, base);

        plugin.reloadConfig();
        final Set<String> overridePaths = plugin.getConfig().getKeys(true).stream()
                .filter(key -> !plugin.getConfig().isConfigurationSection(key))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        mergeInto(effective, plugin.getConfig());

        final long previousGeneration = liveSnapshot.generation();
        final long nextGeneration = previousGeneration == Long.MAX_VALUE
                ? Long.MAX_VALUE : previousGeneration + 1L;
        liveSnapshot = new ConfigSnapshot(effective, base, overridePaths, nextGeneration, overrideFingerprint());
    }

    private YamlConfiguration loadBaseConfiguration() {
        final YamlConfiguration base = new YamlConfiguration();
        final File dir = new File(plugin.getDataFolder(), "config");
        dir.mkdirs();
        for (final String name : CONFIG_FILES) {
            if (!new File(dir, name + ".yml").exists()) {
                plugin.saveResource("config/" + name + ".yml", false);
            }
        }
        for (final String name : CONFIG_FILES) {
            final File file = new File(dir, name + ".yml");
            if (file.exists()) {
                mergeInto(base, YamlConfiguration.loadConfiguration(file));
            }
        }
        final File[] files = dir.listFiles((directory, fileName) -> fileName.endsWith(".yml"));
        if (files != null) {
            for (final File file : files) {
                final String baseName = file.getName().substring(0, file.getName().length() - 4);
                if (java.util.Arrays.stream(CONFIG_FILES).noneMatch(baseName::equals)) {
                    plugin.getLogger().warning("Ismeretlen config-fájl kihagyva a merge-ből: config/"
                            + file.getName() + " (csak a CONFIG_FILES lista töltődik be)");
                }
            }
        }
        return base;
    }

    private static void mergeInto(final YamlConfiguration target, final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                target.set(key, source.get(key));
            }
        }
    }

    public void reload() {
        load();
    }

    /** Serialized single-key compatibility path used by the admin command. */
    public synchronized boolean applyOverride(final String key, final Object value) {
        final ConfigSnapshot snapshot = liveSnapshot;
        return applyOverridesIfUnchanged(snapshot.generation(), snapshot.sourceFingerprint(),
                java.util.Collections.singletonMap(key, value)) == BatchApplyResult.APPLIED;
    }

    /**
     * Applies one GUI transaction with compare-and-set semantics. Both the published generation and
     * the on-disk config.yml fingerprint must still match the editor's opening snapshot, otherwise
     * a second admin or an external file edit wins and this stale transaction is rejected.
     */
    public synchronized BatchApplyResult applyOverridesIfUnchanged(final long expectedGeneration,
                                                                    final String expectedFingerprint,
                                                                    final Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            return BatchApplyResult.NO_CHANGES;
        }
        if (liveSnapshot.generation() != expectedGeneration
                || !java.util.Objects.equals(expectedFingerprint, overrideFingerprint())) {
            return BatchApplyResult.STALE;
        }
        plugin.reloadConfig();
        boolean changed = false;
        for (final Map.Entry<String, Object> entry : new LinkedHashMap<>(changes).entrySet()) {
            final String path = entry.getKey();
            final Object value = entry.getValue();
            final Object previous = plugin.getConfig().get(path);
            if (!java.util.Objects.equals(previous, value) || plugin.getConfig().isSet(path) != (value != null)) {
                plugin.getConfig().set(path, value);
                changed = true;
            }
        }
        if (!changed) {
            return BatchApplyResult.NO_CHANGES;
        }
        plugin.saveConfig();
        load();
        return BatchApplyResult.APPLIED;
    }

    private String overrideFingerprint() {
        final File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            return "MISSING";
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (final Exception failure) {
            plugin.getLogger().warning("config.yml fingerprint failed; conservative stale marker used: " + failure);
            return "ERROR:" + file.length() + ':' + file.lastModified();
        }
    }

    public ConfigSnapshot snapshot() { return liveSnapshot; }
    public FileConfiguration getConfiguration() { return liveSnapshot.configuration(); }
    public boolean contains(final String path) { return liveSnapshot.isSet(path); }
    public boolean hasOverride(final String path) { return liveSnapshot.isOverridden(path); }
    public Object getBaseValue(final String path) { return liveSnapshot.baseValue(path); }

    public String getString(final String path, final String fallback) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getString(path, fallback);
    }
    public int getInt(final String path, final int fallback) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getInt(path, fallback);
    }
    public long getLong(final String path, final long fallback) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getLong(path, fallback);
    }
    public double getDouble(final String path, final double fallback) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getDouble(path, fallback);
    }
    public boolean getBoolean(final String path, final boolean fallback) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getBoolean(path, fallback);
    }
    public List<String> getStringList(final String path) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? List.of() : configuration.getStringList(path);
    }
    public List<Double> getDoubleList(final String path) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? List.of() : configuration.getDoubleList(path);
    }
}
''')

write('src/main/java/hu/taliann/icesmp/gui/ConfigEditSession.java', r'''package hu.taliann.icesmp.gui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pure staged config edit transaction; no click mutates disk until SAVE. */
public final class ConfigEditSession {
    private final long expectedGeneration;
    private final String expectedFingerprint;
    private final Map<String, Object> openingValues;
    private final Map<String, Object> defaults;
    private final Map<String, Object> pending = new LinkedHashMap<>();

    public ConfigEditSession(final long expectedGeneration, final String expectedFingerprint,
                             final Map<String, Object> openingValues, final Map<String, Object> defaults) {
        this.expectedGeneration = expectedGeneration;
        this.expectedFingerprint = expectedFingerprint == null ? "" : expectedFingerprint;
        this.openingValues = Collections.unmodifiableMap(new LinkedHashMap<>(openingValues));
        this.defaults = Collections.unmodifiableMap(new LinkedHashMap<>(defaults));
    }

    public long expectedGeneration() { return expectedGeneration; }
    public String expectedFingerprint() { return expectedFingerprint; }
    public boolean dirty() { return !pending.isEmpty(); }
    public boolean hasPending(final String key) { return pending.containsKey(key); }
    public Set<String> changedKeys() { return Set.copyOf(pending.keySet()); }

    public Object value(final String key) {
        if (pending.containsKey(key)) {
            final Object staged = pending.get(key);
            return staged == null ? defaults.get(key) : staged;
        }
        return openingValues.get(key);
    }

    public Object defaultValue(final String key) { return defaults.get(key); }
    public void stage(final String key, final Object value) { pending.put(key, value); }
    /** Null means remove the override so the packaged/subsystem default becomes authoritative. */
    public void reset(final String key) { pending.put(key, null); }

    public Map<String, Object> pendingChanges() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(pending));
    }
}
''')

print('stage3 part 1 applied')
