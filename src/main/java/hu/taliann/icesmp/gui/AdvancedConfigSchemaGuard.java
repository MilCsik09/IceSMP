package hu.taliann.icesmp.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Fail-fast packaged-schema guard for the second-wave menus. */
public final class AdvancedConfigSchemaGuard {

    private static volatile boolean validated;

    private AdvancedConfigSchemaGuard() {
    }

    public static void validate() {
        if (validated) {
            return;
        }
        synchronized (AdvancedConfigSchemaGuard.class) {
            if (validated) {
                return;
            }
            final YamlConfiguration merged = new YamlConfiguration();
            mergeResource(merged, "general");
            mergeResource(merged, "world");
            mergeResource(merged, "moderation");
            mergeResource(merged, "crates");

            for (final AdvancedConfigEntry entry : ServerWorldConfigMenuGUI.entries()) {
                require(merged.isSet(entry.key()),
                        "A szerver/világ menü kulcsa hiányzik a csomagolt configból: " + entry.key());
            }
            require(merged.isSet("crates-settings.enabled"),
                    "A globális crate-beállítások hiányoznak a csomagolt sémából.");
            final ConfigurationSection crates = merged.getConfigurationSection("crates");
            require(crates != null && !crates.getKeys(false).isEmpty(),
                    "A csomagolt crates szekció üres.");
            for (final String crateId : crates.getKeys(false)) {
                for (final AdvancedConfigEntry entry : CrateConfigMenuGUI.entriesFor(crateId)) {
                    require(merged.isSet(entry.key()),
                            "A crate editor kulcsa hiányzik a csomagolt sémából: " + entry.key());
                }
                final Object rewards = merged.get("crates." + crateId + ".rewards");
                require(rewards instanceof java.util.List<?> list && !list.isEmpty(),
                        "A crate rewardlista üres vagy nem lista: " + crateId);
            }
            validated = true;
        }
    }

    private static void mergeResource(final YamlConfiguration target, final String name) {
        try (InputStream input = AdvancedConfigSchemaGuard.class.getClassLoader()
                .getResourceAsStream("config/" + name + ".yml")) {
            if (input == null) {
                throw new IllegalStateException("Hiányzó csomagolt config: " + name + ".yml");
            }
            final YamlConfiguration source = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            for (final String key : source.getKeys(true)) {
                if (!source.isConfigurationSection(key)) {
                    target.set(key, source.get(key));
                }
            }
        } catch (final java.io.IOException failure) {
            throw new IllegalStateException("A csomagolt config nem olvasható: " + name, failure);
        }
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
