package hu.taliann.icesmp.config;

import hu.taliann.icesmp.gui.ConfigMenuGUI;
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

    private ConfigGuiCoverageRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final YamlConfiguration merged = new YamlConfiguration();
        try (var stream = Files.list(Path.of("src/main/resources/config"))) {
            stream.filter(path -> path.toString().endsWith(".yml")).sorted().forEach(path ->
                    merge(merged, YamlConfiguration.loadConfiguration(path.toFile())));
        }
        final Map<String, Object> scalar = new HashMap<>();
        for (final String key : merged.getKeys(true)) {
            if (!merged.isConfigurationSection(key) && isScalar(merged.get(key))) scalar.put(key, merged.get(key));
        }
        final Map<String, ConfigMenuGUI.Entry> entries = new HashMap<>();
        final Set<String> duplicates = new HashSet<>();
        for (final ConfigMenuGUI.Entry entry : ConfigMenuGUI.allEntries()) {
            if (entries.put(entry.key(), entry) != null) duplicates.add(entry.key());
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
        final List<String> missingRequired = scalar.keySet().stream()
                .filter(key -> MUST_EXPOSE_PREFIXES.stream().anyMatch(key::startsWith))
                .filter(key -> !entries.containsKey(key)).sorted().toList();
        check(missingRequired.isEmpty(), "required schema entries missing from GUI: " + missingRequired);
        final int displayed = entries.size();
        final int excluded = scalar.size() - displayed;
        System.out.println("CONFIG_GUI_COVERAGE total=" + scalar.size() + " displayed=" + displayed
                + " intentionally_excluded=" + excluded + " missing=0 stale=0 duplicate=0");
        System.out.println("Config GUI coverage regression suite passed.");
    }

    private static boolean isScalar(final Object value) {
        return value instanceof Boolean || value instanceof Number || value instanceof String;
    }
    private static void merge(final YamlConfiguration target, final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) if (!source.isConfigurationSection(key)) target.set(key, source.get(key));
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
