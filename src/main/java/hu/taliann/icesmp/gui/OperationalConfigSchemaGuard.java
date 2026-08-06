package hu.taliann.icesmp.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Fail-fast guard: every operational menu entry must exist in the packaged config schema. */
public final class OperationalConfigSchemaGuard {

    private static volatile boolean validated;

    private OperationalConfigSchemaGuard() {
    }

    public static void validate() {
        if (validated) {
            return;
        }
        synchronized (OperationalConfigSchemaGuard.class) {
            if (validated) {
                return;
            }
            try {
                final YamlConfiguration packaged = new YamlConfiguration();
                for (final String file : new String[]{
                        "general", "tablist", "afk", "pets", "economy", "moderation"}) {
                    try (InputStream input = OperationalConfigSchemaGuard.class.getClassLoader()
                            .getResourceAsStream("config/" + file + ".yml")) {
                        if (input == null) {
                            throw new IllegalStateException(
                                    "Hiányzó csomagolt config az üzemeltetési menühöz: " + file);
                        }
                        mergeInto(packaged, YamlConfiguration.loadConfiguration(
                                new InputStreamReader(input, StandardCharsets.UTF_8)));
                    }
                }

                final Set<String> keys = new HashSet<>();
                for (final OperationalConfigMenuGUI.Category category
                        : OperationalConfigMenuGUI.categories().values()) {
                    for (final ConfigMenuGUI.Entry entry : category.entries()) {
                        if (!keys.add(entry.key())) {
                            throw new IllegalStateException(
                                    "Duplikált üzemeltetési config-kulcs: " + entry.key());
                        }
                        if (!packaged.isSet(entry.key())) {
                            throw new IllegalStateException(
                                    "Az üzemeltetési menü kulcsa hiányzik a csomagolt configból: "
                                            + entry.key());
                        }
                    }
                }
                if (keys.size() != OperationalConfigMenuGUI.entryCount()) {
                    throw new IllegalStateException(
                            "Az üzemeltetési menü bejegyzésszáma eltér a katalógustól.");
                }
                validated = true;
            } catch (final java.io.IOException failure) {
                throw new IllegalStateException(
                        "Az üzemeltetési config-erőforrás nem olvasható.", failure);
            }
        }
    }

    private static void mergeInto(final YamlConfiguration target,
                                  final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                target.set(key, source.get(key));
            }
        }
    }
}
