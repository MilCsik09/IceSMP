package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Lightweight config sanity check run once after {@link ConfigManager#load()}.
 * It walks the merged keyspace and logs a clear warning for the admin typos that
 * would otherwise fail silently (a mistyped item falls back to nothing, a bad
 * percent skews balance) — it never throws or blocks startup, it only reports.
 *
 * <p>Validation is convention-driven off the leaf key name, so it covers every
 * subsystem uniformly without a hand-maintained schema:
 * <ul>
 *   <li>{@code material} / {@code materials} → must resolve to a real {@link Material};</li>
 *   <li>{@code loot} / keys ending in {@code -loot} (string lists) → each entry must be a valid
 *       {@code MATERIAL[:COUNT|:MIN:MAX]} loot-table row (see {@link LootTable#describeProblem});</li>
 *   <li>{@code currency} → must be {@code OWN}/{@code FACTION}/{@code SAJAT} or a valid
 *       {@link CurrencyType};</li>
 *   <li>keys ending in {@code percent} → must be within 0–100;</li>
 *   <li>keys ending in {@code -minutes}/{@code -hours}/{@code -seconds}/{@code -ticks}/{@code -millis}
 *       → must not be negative.</li>
 * </ul>
 */
public final class ConfigValidator {

    private ConfigValidator() {
    }

    /** Validates the loaded configuration and logs a warning per problem found, followed by a one-line summary. */
    public static void validate(final ConfigManager configManager, final Logger logger) {
        final FileConfiguration config = configManager.getConfiguration();
        if (config == null) {
            return;
        }

        final int problems = validateConfiguration(config, logger);
        if (problems == 0) {
            logger.info("Konfiguráció ellenőrizve: nem található elgépelés.");
        } else {
            logger.warning("Konfiguráció-ellenőrzés: " + problems
                    + " lehetséges hiba (a plugin nem módosítja automatikusan a hibás értékeket — "
                    + "javítsd a fenti sorokat).");
        }
    }

    static int validateConfiguration(final FileConfiguration config, final Logger logger) {
        int problems = 0;
        for (final String key : config.getKeys(true)) {
            if (config.isConfigurationSection(key)) {
                continue;
            }
            final String leaf = leafOf(key);

            if ("pakt.material".equals(key)) {
                problems += checkUniqueMaterialReference(config, logger, key, config.getString(key));
            } else if (leaf.equals("material")) {
                problems += checkMaterial(logger, key, config.getString(key));
            } else if (leaf.equals("materials") && config.isList(key)) {
                for (final String value : config.getStringList(key)) {
                    problems += checkMaterial(logger, key, value);
                }
            } else if ((leaf.equals("loot") || leaf.endsWith("-loot")) && config.isList(key)) {
                for (final String value : config.getStringList(key)) {
                    problems += checkLootEntry(logger, key, value);
                }
            } else if (leaf.equals("currency")) {
                problems += checkCurrency(logger, key, config.getString(key));
            } else if (leaf.endsWith("percent")) {
                problems += checkPercent(logger, key, config.get(key));
            } else if (leaf.endsWith("-multiplier")) {
                problems += checkFiniteNonNegative(logger, key, config.get(key), "szorzó");
            } else if ((key.startsWith("factions.passives.") || key.startsWith("factions.whisper."))
                    && leaf.endsWith("-chance")) {
                problems += checkUnitInterval(logger, key, config.get(key));
            } else if (key.startsWith("factions.passives.") && leaf.endsWith("-radius")) {
                problems += checkFiniteNonNegative(logger, key, config.get(key), "sugár");
            } else if (isDurationKey(leaf)) {
                problems += checkNonNegative(logger, key, config.get(key));
            }
        }
        return problems;
    }

    private static int checkUniqueMaterialReference(final FileConfiguration config, final Logger logger,
                                                    final String key, final String value) {
        final String reference = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (reference.isEmpty()
                || !config.isConfigurationSection("profession-materials." + reference)) {
            logger.warning("Config: ismeretlen profession-material hivatkozás a(z) '" + key
                    + "' kulcsnál: '" + value + "'.");
            return 1;
        }
        return 0;
    }

    private static int checkMaterial(final Logger logger, final String key, final String value) {
        if (value == null || value.isBlank() || ConfigMaterialResolver.match(value) == null) {
            logger.warning("Config: ismeretlen Material a(z) '" + key + "' kulcsnál: '" + value
                    + "' — ellenőrizd a nevet (pl. DIAMOND, ENCHANTED_GOLDEN_APPLE).");
            return 1;
        }
        return 0;
    }

    private static int checkLootEntry(final Logger logger, final String key, final String value) {
        final String problem = value == null ? "üres bejegyzés" : LootTable.describeProblem(value);
        if (problem != null) {
            logger.warning("Config: hibás loot-bejegyzés a(z) '" + key + "' listában ('" + value + "'): "
                    + problem + " — formátum: MATERIAL, MATERIAL:DB vagy MATERIAL:MIN:MAX.");
            return 1;
        }
        return 0;
    }

    private static int checkCurrency(final Logger logger, final String key, final String value) {
        if (value == null) {
            return 0;
        }
        if ("OWN".equalsIgnoreCase(value) || "FACTION".equalsIgnoreCase(value) || "SAJAT".equalsIgnoreCase(value)) {
            return 0;
        }
        if (CurrencyType.fromInput(value) == null) {
            logger.warning("Config: ismeretlen valuta a(z) '" + key + "' kulcsnál: '" + value
                    + "' — használható: OWN, RED, BLUE, NEUTRAL, DARK.");
            return 1;
        }
        return 0;
    }

    private static int checkPercent(final Logger logger, final String key, final Object value) {
        if (!(value instanceof Number number)) {
            return 0;
        }
        final double percent = number.doubleValue();
        if (percent < 0.0D || percent > 100.0D) {
            logger.warning("Config: a(z) '" + key + "' százalék-érték a 0–100 tartományon kívül esik: "
                    + percent + ".");
            return 1;
        }
        return 0;
    }

    private static int checkNonNegative(final Logger logger, final String key, final Object value) {
        if (!(value instanceof Number number)) {
            return 0;
        }
        if (number.doubleValue() < 0.0D) {
            logger.warning("Config: a(z) '" + key + "' időtartam nem lehet negatív: " + number + ".");
            return 1;
        }
        return 0;
    }

    private static int checkFiniteNonNegative(final Logger logger, final String key,
                                              final Object value, final String kind) {
        if (!(value instanceof Number number)) {
            logger.warning("Config: a(z) '" + key + "' " + kind + " csak szám lehet: " + value + ".");
            return 1;
        }
        final double parsed = number.doubleValue();
        if (!Double.isFinite(parsed) || parsed < 0.0D) {
            logger.warning("Config: a(z) '" + key + "' " + kind
                    + " csak véges, nem negatív szám lehet: " + value + ".");
            return 1;
        }
        return 0;
    }

    private static int checkUnitInterval(final Logger logger, final String key, final Object value) {
        if (!(value instanceof Number number)) {
            logger.warning("Config: a(z) '" + key + "' esély csak szám lehet: " + value + ".");
            return 1;
        }
        final double parsed = number.doubleValue();
        if (!Double.isFinite(parsed) || parsed < 0.0D || parsed > 1.0D) {
            logger.warning("Config: a(z) '" + key + "' esély csak 0 és 1 közötti szám lehet: "
                    + value + ".");
            return 1;
        }
        return 0;
    }

    private static boolean isDurationKey(final String leaf) {
        return leaf.endsWith("-minutes") || leaf.endsWith("-hours") || leaf.endsWith("-seconds")
                || leaf.endsWith("-ticks") || leaf.endsWith("-millis");
    }

    private static String leafOf(final String key) {
        final int dot = key.lastIndexOf('.');
        return dot < 0 ? key : key.substring(dot + 1);
    }
}
