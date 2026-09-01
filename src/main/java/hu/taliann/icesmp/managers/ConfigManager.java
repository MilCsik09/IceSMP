package hu.taliann.icesmp.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized, atomically-published configuration with packaged-default fallback and
 * optimistic-concurrency admin writes.
 */
public final class ConfigManager {

    /** One immutable publication unit: packaged/deployed defaults, effective values and provenance. */
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

    public enum BatchApplyResult { APPLIED, STALE, NO_CHANGES, LOCKED, REJECTED }

    public enum Authority {
        RUNTIME_OVERRIDE,
        OPERATOR_TUNABLE,
        LOCKED_CANONICAL_CONTENT,
        UNKNOWN
    }

    public enum ReloadPolicy {
        LIVE_RELOADABLE,
        SAFE_RELOAD_WITH_RECONCILIATION,
        RESTART_REQUIRED,
        UNKNOWN
    }

    /** Deployed, schema-bounded server-owner settings. */
    private static final String[] OPERATOR_CONFIG_FILES = {
            "general", "economy", "factions", "block-regen", "class-gameplay", "spells-balance",
            "professions", "world", "event-spawn-safety", "pets", "crafting", "crates", "afk",
            "moderation", "motd", "professions-2", "sit", "tablist", "dev-items", "client"
    };

    /** Packaged, Git-authored gameplay authorities. They are never copied into the server config folder. */
    private static final String[] CONTENT_FILES = {
            "content/progression/classes.yml",
            "content/progression/spells.yml",
            "content/progression/quests.yml",
            "content/equipment/rarities.yml",
            "content/equipment/equipment.yml",
            "content/equipment/relics.yml",
            "content/professions/materials.yml",
            "content/professions/recipes.yml",
            "content/pve/enemies.yml",
            "content/pve/loot.yml",
            "content/events/prologue.yml"
    };

    /** Old deployed files are preserved before locked packaged content takes ownership. */
    private static final Set<String> LEGACY_CONTENT_FILES = Set.of(
            "classes", "spells", "quests", "item-rarity", "item-templates", "relics",
            "profession-materials", "profession-recipes", "mob-templates", "loot",
            "material-economy-expansion", "equipment-catalog-expansion", "reward-discoverability-closure");

    /** Explicit live tuning seams whose packaged defaults intentionally live beside authored definitions. */
    private static final List<String> EXPLICIT_OPERATOR_PREFIXES = List.of(
            "health.",
            "itemization.stats.",
            "itemization.loot.",
            "relics.enabled",
            "relics.inactivity.",
            "relics.passive-death.",
            "relics.wings.faction-locked-pickup",
            "relics.pvp-transfer.enabled");
    private static final Set<String> RESTART_REQUIRED_OPERATOR_PATHS = Set.of(
            "factions.tax.enabled",
            "factions.tax.interval-minutes",
            "hud.icesmp-hud.survival.refresh-ticks");
    private static final List<String> RECONCILIATION_PREFIXES = List.of(
            "motd.", "sit.", "crates-settings.", "crates.", "resource-pack.",
            "factions.passives.", "factions.whisper.", "professions.recipes.",
            "moderation.", "hud.", "tablist.", "mob-scaling.");
    private static final Set<String> RECONCILIATION_PATHS = Set.of(
            "world-events.check-interval-seconds",
            "settings.disable-locator-bar",
            "pets.companion.tick-ticks",
            "currency.economy-event.check-interval-minutes");
    private static final List<String> MANAGED_FAMILY_SALVAGE_OUTPUTS = List.of(
            "szovet_foszlany", "bor_hulladek", "lanc_toredek", "femhulladek");

    private static volatile ConfigManager active;

    private final JavaPlugin plugin;
    private final Map<String, Object> runtimeOverrides = new ConcurrentHashMap<>();
    private volatile ConfigSnapshot liveSnapshot = new ConfigSnapshot(null, null, Set.of(), 0L, "");
    private volatile Set<String> operatorEditablePaths = Set.of();

    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        synchronized (ConfigManager.class) {
            active = this;
        }
    }

    /** Runtime singleton bridge for late-bound systems installed after the manual core DI graph. */
    public static ConfigManager current() {
        return active;
    }

    /** Identity-safe lifecycle teardown: a stale core may never clear a newer installation. */
    public static void clearIfCurrent(final ConfigManager candidate) {
        synchronized (ConfigManager.class) {
            if (active == candidate) active = null;
        }
    }

    /**
     * Applies a non-persistent runtime gate. This layer survives config reloads but never writes
     * config.yml or subsystem files; lifecycle owners must clear it when the temporary gate ends.
     */
    public void setRuntimeOverride(final String path, final Object value) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("runtime override path is required");
        }
        if (value == null) runtimeOverrides.remove(path); else runtimeOverrides.put(path, value);
    }

    public void clearRuntimeOverride(final String path) {
        if (path != null) runtimeOverrides.remove(path);
    }

    public boolean hasRuntimeOverride(final String path) {
        return path != null && runtimeOverrides.containsKey(path);
    }

    public Map<String, Object> runtimeOverrides() {
        return Map.copyOf(runtimeOverrides);
    }

    /** Loads one validated candidate and publishes it only after every authority check succeeds. */
    public synchronized void load() {
        final BaseLoad baseLoad = loadBaseConfiguration();
        final YamlConfiguration rootOverrides = loadRootOverrides(baseLoad.operatorPaths());
        final YamlConfiguration effective = mergedConfiguration(baseLoad.configuration(), rootOverrides);
        validateManagedSalvageMappings(effective);

        final long previousGeneration = liveSnapshot.generation();
        final long nextGeneration = previousGeneration == Long.MAX_VALUE
                ? Long.MAX_VALUE : previousGeneration + 1L;
        operatorEditablePaths = Set.copyOf(baseLoad.operatorPaths());
        liveSnapshot = new ConfigSnapshot(effective, baseLoad.configuration(), leafPaths(rootOverrides),
                nextGeneration, overrideFingerprint());
    }

    private record BaseLoad(YamlConfiguration configuration, Set<String> operatorPaths) { }

    private YamlConfiguration loadRootOverrides(final Set<String> allowedPaths) {
        final File root = new File(plugin.getDataFolder(), "config.yml");
        if (!root.exists()) {
            plugin.saveDefaultConfig();
        }
        final YamlConfiguration deployed = loadStrict(root, "config.yml");
        warnRejectedOverrides("config.yml", deployed, allowedPaths);
        final YamlConfiguration filtered = new YamlConfiguration();
        mergeSelected(filtered, deployed, allowedPaths);
        plugin.reloadConfig();
        return filtered;
    }

    /** Shared production merge seam; package-private so the regression gate exercises this logic. */
    static YamlConfiguration mergedConfiguration(final ConfigurationSection base,
                                                  final ConfigurationSection overrides) {
        final YamlConfiguration effective = new YamlConfiguration();
        if (base != null) mergeInto(effective, base);
        if (overrides != null) mergeInto(effective, overrides);
        return effective;
    }

    /**
     * Managed family salvage may be operator-overridden, but it may never silently disappear or
     * resolve to a non-authored economy material. Missing defaults therefore abort config publish
     * before mutation code can fall back to an unrelated valuable currency.
     */
    static void validateManagedSalvageMappings(final ConfigurationSection effective) {
        if (effective == null) {
            throw new IllegalStateException("effective configuration is unavailable");
        }
        for (final String output : MANAGED_FAMILY_SALVAGE_OUTPUTS) {
            final String path = "itemization.salvage.output-map." + output;
            final String mapped = effective.getString(path, "").trim().toLowerCase(java.util.Locale.ROOT);
            if (mapped.isBlank()) {
                throw new IllegalStateException(path + ": missing managed salvage output mapping");
            }
            final ConfigurationSection definition = effective.getConfigurationSection(
                    "profession-materials." + mapped);
            if (definition == null || !definition.getBoolean("economy-managed", false)) {
                throw new IllegalStateException(path + ": mapped output is not an economy-managed profession material: "
                        + mapped);
            }
        }
    }

    private BaseLoad loadBaseConfiguration() {
        final YamlConfiguration base = new YamlConfiguration();
        final File dir = new File(plugin.getDataFolder(), "config");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create operator config directory: " + dir);
        }
        migrateLegacyContentFiles(dir);

        for (final String resource : CONTENT_FILES) {
            mergeInto(base, loadResourceStrict(resource));
        }

        final LinkedHashSet<String> operatorPaths = new LinkedHashSet<>();
        for (final String name : OPERATOR_CONFIG_FILES) {
            final String resource = "config/" + name + ".yml";
            final YamlConfiguration packaged = loadResourceStrict(resource);
            operatorPaths.addAll(leafPaths(packaged));
            mergeInto(base, packaged);
            if (!new File(dir, name + ".yml").exists()) {
                plugin.saveResource(resource, false);
            }
        }
        final YamlConfiguration packagedRoot = loadResourceStrict("config.yml");
        operatorPaths.addAll(leafPaths(packagedRoot));
        mergeInto(base, packagedRoot);
        for (final String prefix : EXPLICIT_OPERATOR_PREFIXES) {
            for (final String path : leafPaths(base)) {
                if (path.equals(prefix) || path.startsWith(prefix)) {
                    operatorPaths.add(path);
                }
            }
        }

        for (final String name : OPERATOR_CONFIG_FILES) {
            final File file = new File(dir, name + ".yml");
            if (file.exists()) {
                final YamlConfiguration deployed = loadStrict(file, "config/" + file.getName());
                final Set<String> ownedPaths = leafPaths(loadResourceStrict("config/" + name + ".yml"));
                warnRejectedOverrides("config/" + file.getName(), deployed, ownedPaths);
                mergeSelected(base, deployed, ownedPaths);
            }
        }
        final File[] files = dir.listFiles((directory, fileName) -> fileName.endsWith(".yml"));
        if (files != null) {
            for (final File file : files) {
                final String baseName = file.getName().substring(0, file.getName().length() - 4);
                if (Arrays.stream(OPERATOR_CONFIG_FILES).noneMatch(baseName::equals)) {
                    plugin.getLogger().warning("Ismeretlen config-fájl kihagyva a merge-ből: config/"
                            + file.getName() + " (csak a dokumentált operator configok töltődnek be)");
                }
            }
        }
        return new BaseLoad(base, Set.copyOf(operatorPaths));
    }

    private YamlConfiguration loadResourceStrict(final String resource) {
        try (InputStream input = plugin.getResource(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing packaged authority: " + resource);
            }
            final YamlConfiguration loaded = new YamlConfiguration();
            loaded.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return loaded;
        } catch (final IOException | InvalidConfigurationException failure) {
            throw new IllegalStateException("Invalid packaged authority " + resource, failure);
        }
    }

    private static YamlConfiguration loadStrict(final File file, final String label) {
        final YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
            return loaded;
        } catch (final IOException | InvalidConfigurationException failure) {
            throw new IllegalStateException("Invalid deployed operator configuration " + label, failure);
        }
    }

    private void warnRejectedOverrides(final String source, final ConfigurationSection deployed,
                                       final Set<String> allowedPaths) {
        final List<String> rejected = leafPaths(deployed).stream()
                .filter(path -> !allowedPaths.contains(path))
                .sorted()
                .toList();
        if (!rejected.isEmpty()) {
            plugin.getLogger().warning(source + ": " + rejected.size()
                    + " locked/unknown key ignored; canonical gameplay is packaged under content/. First keys: "
                    + String.join(", ", rejected.stream().limit(8).toList()));
        }
    }

    private void migrateLegacyContentFiles(final File configDir) {
        final Path backupDir = plugin.getDataFolder().toPath()
                .resolve("migration-backups/config-content-command-surface-2/config");
        for (final String name : new TreeSet<>(LEGACY_CONTENT_FILES)) {
            final Path source = configDir.toPath().resolve(name + ".yml");
            if (!Files.exists(source)) {
                continue;
            }
            try {
                Files.createDirectories(backupDir);
                final String digest = java.util.HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))).substring(0, 12);
                final Path target = backupDir.resolve(name + "-" + digest + ".yml");
                if (Files.exists(target) && Files.mismatch(source, target) == -1L) {
                    Files.delete(source);
                } else {
                    try {
                        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                    } catch (final java.nio.file.AtomicMoveNotSupportedException unsupported) {
                        Files.move(source, target);
                    }
                }
                plugin.getLogger().warning("Legacy gameplay config archived and locked canonical authority activated: config/"
                        + name + ".yml -> " + plugin.getDataFolder().toPath().relativize(target));
            } catch (final Exception failure) {
                throw new IllegalStateException("Cannot preserve legacy content config before migration: " + source,
                        failure);
            }
        }
    }

    private static void mergeInto(final YamlConfiguration target, final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                target.set(key, source.get(key));
            }
        }
    }

    private static void mergeSelected(final YamlConfiguration target, final ConfigurationSection source,
                                      final Set<String> allowedPaths) {
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key) && allowedPaths.contains(key)) {
                target.set(key, source.get(key));
            }
        }
    }

    private static Set<String> leafPaths(final ConfigurationSection source) {
        if (source == null) {
            return Set.of();
        }
        final LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                paths.add(key);
            }
        }
        return Set.copyOf(paths);
    }

    public void reload() {
        load();
    }

    /** Serialized single-key compatibility path used by admin commands and focused adapters. */
    public synchronized boolean applyOverride(final String key, final Object value) {
        if (!isOperatorEditable(key)) {
            return false;
        }
        final ConfigSnapshot snapshot = liveSnapshot;
        return applyOverridesIfUnchanged(snapshot.generation(), snapshot.sourceFingerprint(),
                java.util.Collections.singletonMap(key, value)) == BatchApplyResult.APPLIED;
    }

    /** Removes only the config.yml override; subsystem/package defaults become authoritative. */
    public boolean resetOverride(final String key) {
        return applyOverride(key, null);
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
        if (changes.keySet().stream().anyMatch(key -> !isOperatorEditable(key))) {
            return BatchApplyResult.LOCKED;
        }
        if (liveSnapshot.generation() != expectedGeneration
                || !java.util.Objects.equals(expectedFingerprint, overrideFingerprint())) {
            return BatchApplyResult.STALE;
        }
        final File overrideFile = new File(plugin.getDataFolder(), "config.yml");
        final boolean overrideFileExisted = overrideFile.exists();
        final byte[] previousOverrideBytes;
        try {
            previousOverrideBytes = overrideFileExisted ? Files.readAllBytes(overrideFile.toPath()) : new byte[0];
        } catch (final IOException failure) {
            throw new IllegalStateException("Cannot snapshot config.yml before operator update", failure);
        }
        plugin.reloadConfig();
        boolean changed = false;
        for (final Map.Entry<String, Object> entry : new LinkedHashMap<>(changes).entrySet()) {
            final String path = entry.getKey();
            final Object value = entry.getValue();
            final Object previous = plugin.getConfig().get(path);
            if (!java.util.Objects.equals(previous, value)
                    || plugin.getConfig().isSet(path) != (value != null)) {
                plugin.getConfig().set(path, value);
                changed = true;
            }
        }
        if (!changed) {
            return BatchApplyResult.NO_CHANGES;
        }
        try {
            plugin.saveConfig();
            load();
            return BatchApplyResult.APPLIED;
        } catch (final RuntimeException failure) {
            try {
                restoreOverrideFile(overrideFile.toPath(), overrideFileExisted, previousOverrideBytes);
                plugin.reloadConfig();
            } catch (final Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private static void restoreOverrideFile(final Path target, final boolean existed,
                                            final byte[] previousBytes) throws IOException {
        if (!existed) {
            Files.deleteIfExists(target);
            return;
        }
        final Path temporary = target.resolveSibling(target.getFileName() + ".rollback.tmp");
        Files.write(temporary, previousBytes);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (final java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String overrideFingerprint() {
        final File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            return "MISSING";
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file.toPath()));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (final Exception failure) {
            plugin.getLogger().warning("config.yml fingerprint failed; conservative stale marker used: " + failure);
            return "ERROR:" + file.length() + ':' + file.lastModified();
        }
    }

    private Object runtimeValue(final String path) {
        return runtimeOverrides.get(path);
    }

    public ConfigSnapshot snapshot() { return liveSnapshot; }
    public boolean isOperatorEditable(final String path) {
        return path != null && operatorEditablePaths.contains(path);
    }
    public Set<String> operatorEditablePaths() { return operatorEditablePaths; }
    public Authority authorityOf(final String path) {
        if (path == null || path.isBlank()) return Authority.UNKNOWN;
        if (runtimeOverrides.containsKey(path)) return Authority.RUNTIME_OVERRIDE;
        if (operatorEditablePaths.contains(path)) return Authority.OPERATOR_TUNABLE;
        if (liveSnapshot.isSet(path)) return Authority.LOCKED_CANONICAL_CONTENT;
        return Authority.UNKNOWN;
    }
    public ReloadPolicy reloadPolicyOf(final String path) {
        return switch (authorityOf(path)) {
            case RUNTIME_OVERRIDE -> ReloadPolicy.LIVE_RELOADABLE;
            case OPERATOR_TUNABLE -> operatorReloadPolicy(path);
            case LOCKED_CANONICAL_CONTENT -> ReloadPolicy.RESTART_REQUIRED;
            case UNKNOWN -> ReloadPolicy.UNKNOWN;
        };
    }
    private static ReloadPolicy operatorReloadPolicy(final String path) {
        if (RESTART_REQUIRED_OPERATOR_PATHS.contains(path)) {
            return ReloadPolicy.RESTART_REQUIRED;
        }
        if (RECONCILIATION_PATHS.contains(path)
                || RECONCILIATION_PREFIXES.stream().anyMatch(path::startsWith)) {
            return ReloadPolicy.SAFE_RELOAD_WITH_RECONCILIATION;
        }
        return ReloadPolicy.LIVE_RELOADABLE;
    }
    public synchronized void restoreSnapshot(final ConfigSnapshot snapshot) {
        if (snapshot == null || snapshot.configuration() == null) {
            throw new IllegalArgumentException("valid snapshot required");
        }
        liveSnapshot = snapshot;
    }
    public FileConfiguration getConfiguration() { return liveSnapshot.configuration(); }
    public boolean contains(final String path) {
        return runtimeOverrides.containsKey(path) || liveSnapshot.isSet(path);
    }
    public boolean hasOverride(final String path) { return liveSnapshot.isOverridden(path); }
    public Object getBaseValue(final String path) { return liveSnapshot.baseValue(path); }

    public String getBaseString(final String path, final String fallback) {
        final FileConfiguration base = liveSnapshot.baseConfiguration();
        return base == null ? fallback : base.getString(path, fallback);
    }

    public double getBaseDouble(final String path, final double fallback) {
        final FileConfiguration base = liveSnapshot.baseConfiguration();
        return base == null ? fallback : base.getDouble(path, fallback);
    }

    public boolean getBaseBoolean(final String path, final boolean fallback) {
        final FileConfiguration base = liveSnapshot.baseConfiguration();
        return base == null ? fallback : base.getBoolean(path, fallback);
    }

    public String getString(final String path, final String fallback) {
        final Object runtime = runtimeValue(path);
        if (runtime != null) return String.valueOf(runtime);
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getString(path, fallback);
    }
    public int getInt(final String path, final int fallback) {
        final Object runtime = runtimeValue(path);
        if (runtime instanceof Number number) return number.intValue();
        if (runtime instanceof String value) {
            try { return Integer.parseInt(value); } catch (final NumberFormatException ignored) { }
        }
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getInt(path, fallback);
    }
    public long getLong(final String path, final long fallback) {
        final Object runtime = runtimeValue(path);
        if (runtime instanceof Number number) return number.longValue();
        if (runtime instanceof String value) {
            try { return Long.parseLong(value); } catch (final NumberFormatException ignored) { }
        }
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getLong(path, fallback);
    }
    public double getDouble(final String path, final double fallback) {
        final Object runtime = runtimeValue(path);
        if (runtime instanceof Number number) return number.doubleValue();
        if (runtime instanceof String value) {
            try { return Double.parseDouble(value); } catch (final NumberFormatException ignored) { }
        }
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getDouble(path, fallback);
    }
    public boolean getBoolean(final String path, final boolean fallback) {
        final Object runtime = runtimeValue(path);
        if (runtime instanceof Boolean value) return value;
        if (runtime instanceof String value) return Boolean.parseBoolean(value);
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getBoolean(path, fallback);
    }
    public List<String> getStringList(final String path) {
        final Object runtime = runtimeValue(path);
        if (runtime instanceof List<?> values) return values.stream().map(String::valueOf).toList();
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? List.of() : configuration.getStringList(path);
    }
    public List<Double> getDoubleList(final String path) {
        final Object runtime = runtimeValue(path);
        if (runtime instanceof List<?> values) {
            final java.util.ArrayList<Double> parsed = new java.util.ArrayList<>();
            for (final Object value : values) {
                if (value instanceof Number number) parsed.add(number.doubleValue());
            }
            return List.copyOf(parsed);
        }
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? List.of() : configuration.getDoubleList(path);
    }
}
