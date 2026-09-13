package hu.taliann.icesmp.config;

import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.ClassGameplayConfigMenuGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigGuiCoverageRegressionSuite {
    private static final List<String> MUST_EXPOSE_PREFIXES = List.of(
            "world-events.safety.", "moderation.vanish.", "territory.mob-rules.doom-gate.");
    private static final List<String> CONTENT_TUNING_PREFIXES = List.of(
            "health.", "itemization.stats.", "itemization.loot.", "relics.inactivity.",
            "relics.passive-death.");
    private static final Set<String> CONTENT_TUNING_KEYS = Set.of(
            "relics.enabled", "relics.wings.faction-locked-pickup", "relics.pvp-transfer.enabled");

    private ConfigGuiCoverageRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final YamlConfiguration merged = new YamlConfiguration();
        final Set<String> operatorPaths = new HashSet<>();
        try (var stream = Files.list(Path.of("src/main/resources/config"))) {
            stream.filter(path -> path.toString().endsWith(".yml")).sorted().forEach(path -> {
                final YamlConfiguration loaded = YamlConfiguration.loadConfiguration(path.toFile());
                merge(merged, loaded);
                operatorPaths.addAll(leafPaths(loaded));
            });
        }
        try (var stream = Files.walk(Path.of("src/main/resources/content"))) {
            stream.filter(path -> path.toString().endsWith(".yml")).sorted().forEach(path ->
                    merge(merged, YamlConfiguration.loadConfiguration(path.toFile())));
        }
        final YamlConfiguration root = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/config.yml").toFile());
        merge(merged, root);
        operatorPaths.addAll(leafPaths(root));
        leafPaths(merged).stream()
                .filter(key -> CONTENT_TUNING_KEYS.contains(key)
                        || CONTENT_TUNING_PREFIXES.stream().anyMatch(key::startsWith))
                .forEach(operatorPaths::add);
        final Map<String, Object> scalar = new HashMap<>();
        for (final String key : merged.getKeys(true)) {
            if (!merged.isConfigurationSection(key) && isScalar(merged.get(key))) scalar.put(key, merged.get(key));
        }
        final Map<String, ConfigMenuGUI.Entry> entries = new HashMap<>();
        final Set<String> duplicates = new HashSet<>();
        final List<ConfigMenuGUI.Entry> menuEntries = new java.util.ArrayList<>(ConfigMenuGUI.allEntries());
        menuEntries.addAll(ClassGameplayConfigMenuGUI.entries());
        for (final ConfigMenuGUI.Entry entry : menuEntries) {
            if (entries.put(entry.key(), entry) != null) duplicates.add(entry.key());
            check(operatorPaths.contains(entry.key()), "GUI exposes locked canonical content: " + entry.key());
            final Object value = scalar.get(entry.key());
            check(value != null, "unknown GUI path: " + entry.key());
            switch (entry.type()) {
                case TOGGLE -> check(value instanceof Boolean, "boolean type mismatch: " + entry.key());
                case INTEGER, NUMBER -> {
                    check(value instanceof Number, "numeric type mismatch: " + entry.key());
                    final double number = ((Number) value).doubleValue();
                    check(number >= entry.min() && number <= entry.max(), "default outside range: " + entry.key());
                }
                case CYCLE -> check(entry.options().stream().map(v -> v.toLowerCase(Locale.ROOT))
                        .anyMatch(v -> v.equals(String.valueOf(value).toLowerCase(Locale.ROOT))),
                        "cycle default missing: " + entry.key());
            }
        }
        check(duplicates.isEmpty(), "duplicate GUI entries: " + duplicates);
        final YamlConfiguration classGameplay = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/config/class-gameplay.yml").toFile());
        final Set<String> classGameplayKeys = classGameplay.getKeys(true).stream()
                .filter(key -> !classGameplay.isConfigurationSection(key))
                .filter(key -> classGameplay.get(key) instanceof Boolean
                        || classGameplay.get(key) instanceof Number)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        final List<String> missingRequired = scalar.keySet().stream()
                .filter(key -> MUST_EXPOSE_PREFIXES.stream().anyMatch(key::startsWith)
                        || classGameplayKeys.contains(key))
                .filter(key -> !entries.containsKey(key)).sorted().toList();
        check(missingRequired.isEmpty(), "required schema entries missing from GUI: " + missingRequired);
        final int displayed = entries.size();
        final int excluded = operatorPaths.size() - displayed;
        System.out.println("CONFIG_GUI_COVERAGE total=" + operatorPaths.size() + " displayed=" + displayed
                + " intentionally_excluded=" + excluded + " missing=0 stale=0 duplicate=0");
        System.out.println("Config GUI coverage regression suite passed.");
    }

    private static boolean isScalar(final Object value) {
        return value instanceof Boolean || value instanceof Number || value instanceof String;
    }
    private static Set<String> leafPaths(final ConfigurationSection source) {
        return source.getKeys(true).stream()
                .filter(key -> !source.isConfigurationSection(key))
                .collect(java.util.stream.Collectors.toSet());
    }
    private static void merge(final YamlConfiguration target, final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) if (!source.isConfigurationSection(key)) target.set(key, source.get(key));
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
