package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.managers.ConfigManager;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Builds a validated, immutable policy snapshot from the live merged configuration. */
public final class FactionPassiveConfig {

    private static final String ROOT = "factions.passives.";
    private static final Set<String> DEFAULT_EXHAUSTION_REASONS = Set.of(
            "SPRINT", "JUMP_SPRINT", "SWIM", "WALK_ON_WATER", "WALK_UNDERWATER");
    private static final Set<String> EXHAUSTION_REASONS = Set.of(
            "ATTACK", "BLOCK_MINED", "CROUCH", "DAMAGED", "ENCHANTMENT_EFFECT",
            "HUNGER_EFFECT", "JUMP", "JUMP_SPRINT", "REGEN", "SPRINT", "SWIM",
            "UNKNOWN", "WALK", "WALK_ON_WATER", "WALK_UNDERWATER");
    private static final Set<String> DEFAULT_COMBAT_MARKERS = Set.of(
            "icesmp:scripted_combat", "icesmp:event_mob", "icesmp:minion_owner");
    private static final Set<String> DEFAULT_QUEST_MARKERS = Set.of("icesmp:quest_mob");
    private static final Set<String> DEFAULT_ADDITIONAL_NEUTRAL_TYPES = Set.of(
            "PIGLIN", "ZOMBIFIED_PIGLIN", "SPIDER", "CAVE_SPIDER");

    private final ConfigManager configManager;
    private final Logger logger;
    private volatile FactionPassiveSettings current;

    public FactionPassiveConfig(final ConfigManager configManager, final Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
        reload();
    }

    public FactionPassiveSettings snapshot() {
        return current;
    }

    /** Publishes one all-old or all-new immutable snapshot under ConfigManager's update monitor. */
    public void reload() {
        synchronized (configManager) {
            current = buildSnapshot();
        }
    }

    private FactionPassiveSettings buildSnapshot() {
        return new FactionPassiveSettings(
                toggle(ROOT + "enabled", true),
                new FactionPassiveSettings.Red(
                        toggle(ROOT + "red.enabled", true),
                        multiplier(ROOT + "red.fire-damage-multiplier", 0.25D),
                        multiplier(ROOT + "red.fire-tick-damage-multiplier", 0.25D),
                        multiplier(ROOT + "red.entity-fire-damage-multiplier", 0.75D),
                        multiplier(ROOT + "red.lava-damage-multiplier", 0.50D),
                        multiplier(ROOT + "red.hot-floor-damage-multiplier", 0.25D),
                        toggle(ROOT + "red.affect-icesmp-fire-magic", false),
                        multiplier(ROOT + "red.fire-magic-damage-multiplier", 0.75D),
                        toggle(ROOT + "red.affect-scripted-combat-fire", false)),
                new FactionPassiveSettings.Blue(
                        toggle(ROOT + "blue.enabled", true),
                        multiplier(ROOT + "blue.freeze-damage-multiplier", 0.0D),
                        multiplier(ROOT + "blue.drowning-damage-multiplier", 0.50D),
                        blueExhaustionChance(),
                        stringSet(ROOT + "blue.affected-exhaustion-reasons", DEFAULT_EXHAUSTION_REASONS)),
                new FactionPassiveSettings.Neutral(
                        toggle(ROOT + "neutral.enabled", true),
                        multiplier(ROOT + "neutral.fall-damage-multiplier", 0.50D),
                        toggle(ROOT + "neutral.passive-mob-truce.enabled", true),
                        toggle(
                                ROOT + "neutral.passive-mob-truce.include-non-monsters", true),
                        stringSet(ROOT + "neutral.passive-mob-truce.additional-entity-types",
                                DEFAULT_ADDITIONAL_NEUTRAL_TYPES),
                        toggle(ROOT + "neutral.passive-mob-truce.break-on-damage", true),
                        durationMillis(ROOT + "neutral.passive-mob-truce.retaliation-seconds", 60L),
                        toggle(ROOT + "neutral.enderman.ignore-stare-aggro", true),
                        toggle(ROOT + "neutral.enderman.allow-retaliation", true)),
                new FactionPassiveSettings.Dark(
                        toggle(ROOT + "dark.enabled", true),
                        toggle(ROOT + "dark.wither.damage-enabled", true),
                        multiplier(ROOT + "dark.wither.damage-multiplier", 0.50D),
                        toggle(ROOT + "dark.wither.duration-enabled", true),
                        multiplier(ROOT + "dark.wither.duration-multiplier", 0.50D),
                        new FactionPassiveSettings.AmbientUndead(
                                toggle(ROOT + "dark.ambient-undead.enabled", true),
                                toggle(ROOT + "dark.ambient-undead.break-on-damage", true),
                                durationMillis(ROOT + "dark.ambient-undead.retaliation-seconds", 60L),
                                nonNegative(ROOT + "dark.ambient-undead.alert-nearby-radius", 16.0D)),
                        new FactionPassiveSettings.WildUndead(
                                toggle(ROOT + "dark.wild-undead.enabled", true),
                                toggle(ROOT + "dark.wild-undead.night-only", true),
                                chance(ROOT + "dark.wild-undead.target-cancel-chance", 0.50D),
                                toggle(
                                        ROOT + "dark.wild-undead.disabled-during-blood-moon", true)),
                        new FactionPassiveSettings.Exclusions(
                                toggle(ROOT + "dark.exclusions.corruption", true),
                                toggle(ROOT + "dark.exclusions.dungeon", true),
                                toggle(ROOT + "dark.exclusions.invasion", true),
                                toggle(ROOT + "dark.exclusions.world-boss", true),
                                toggle(ROOT + "dark.exclusions.event-mobs", true),
                                toggle(ROOT + "dark.exclusions.quest-mobs", true),
                                toggle(ROOT + "dark.exclusions.crown-curse", true)),
                        markerSet(ROOT + "dark.exclusions.combat-marker-keys", DEFAULT_COMBAT_MARKERS),
                        markerSet(ROOT + "dark.exclusions.quest-marker-keys", DEFAULT_QUEST_MARKERS)),
                new FactionPassiveSettings.Whisper(
                        toggle("factions.whisper.enabled", true)
                                && toggle("factions.whisper.night-undead-truce", true),
                        toggle("factions.whisper.night-undead-night-only", true),
                        whisperTargetChance(),
                        toggle(
                                "factions.whisper.night-undead-disabled-during-blood-moon", true),
                        toggle("factions.whisper.night-undead-break-on-damage", true),
                        durationMillis("factions.whisper.night-undead-retaliation-seconds", 60L),
                        chance("factions.whisper.truce-witness-chance", 0.02D),
                        nonNegative("factions.whisper.truce-witness-radius", 16.0D),
                        nonNegative("factions.whisper.truce-witness-suspicion", 1.0D)));
    }

    private double blueExhaustionChance() {
        final String current = ROOT + "blue.natural-exhaustion-save-chance";
        final String legacy = ROOT + "blue-hunger-slow-chance";
        if (configManager.hasOverride(legacy) && !configManager.hasOverride(current)) {
            logger.warning("Legacy faction-passive override: '" + legacy + "' értéke az új '"
                    + current + "' kulcsra kerül; a jelentése már csak a felsorolt természetes "
                    + "exhaustion okokra vonatkozik.");
            return chance(legacy, 0.25D);
        }
        if (configManager.contains(current)) {
            return chance(current, 0.25D);
        }
        if (configManager.contains(legacy)) {
            logger.warning("Legacy faction-passive kulcs használatban: '" + legacy
                    + "'; migráld erre: '" + current + "'.");
        }
        return chance(legacy, 0.25D);
    }

    private double whisperTargetChance() {
        final String current = "factions.whisper.night-undead-target-cancel-chance";
        if (configManager.contains(current)) {
            return chance(current, 0.35D);
        }
        return toggle("factions.whisper.night-undead-truce", true) ? 0.35D : 0.0D;
    }

    private boolean toggle(final String path, final boolean defaultValue) {
        if (!configManager.contains(path) || configManager.getConfiguration() == null) {
            return defaultValue;
        }
        final Object raw = configManager.getConfiguration().get(path);
        if (raw instanceof Boolean value) {
            return value;
        }
        warn(path, raw, "true vagy false", "az adott kapcsoló kontrolláltan kikapcsol");
        return false;
    }

    private double multiplier(final String path, final double defaultValue) {
        final Double configured = configuredNumber(path, defaultValue, "az adott ellenállás kikapcsol (1.0)");
        if (configured == null) {
            return 1.0D;
        }
        final double value = configured;
        if (Double.isFinite(value) && value >= 0.0D) {
            return value;
        }
        warn(path, value, "véges, nem negatív szorzó", "az adott ellenállás kikapcsol (1.0)");
        return 1.0D;
    }

    private double chance(final String path, final double defaultValue) {
        final Double configured = configuredNumber(path, defaultValue,
                "az adott esély-alapú előny kikapcsol (0.0)");
        if (configured == null) {
            return 0.0D;
        }
        final double value = configured;
        if (isUnitInterval(value)) {
            return value;
        }
        warn(path, value, "0 és 1 közötti esély", "az adott esély-alapú előny kikapcsol (0.0)");
        return 0.0D;
    }

    private double nonNegative(final String path, final double defaultValue) {
        final Double configured = configuredNumber(path, defaultValue,
                "az adott sugaras/erősségi ág kikapcsol (0.0)");
        if (configured == null) {
            return 0.0D;
        }
        final double value = configured;
        if (Double.isFinite(value) && value >= 0.0D) {
            return value;
        }
        warn(path, value, "véges, nem negatív érték", "az adott sugaras/erősségi ág kikapcsol (0.0)");
        return 0.0D;
    }

    private long durationMillis(final String path, final long defaultSeconds) {
        final Object raw = configManager.contains(path) && configManager.getConfiguration() != null
                ? configManager.getConfiguration().get(path) : defaultSeconds;
        if (!(raw instanceof Number number)) {
            warn(path, raw, "egész számú másodperc", "a megtorlási állapot kikapcsol");
            return 0L;
        }
        final double numeric = number.doubleValue();
        if (!Double.isFinite(numeric) || numeric < 0.0D || numeric != Math.rint(numeric)
                || numeric > Long.MAX_VALUE) {
            warn(path, raw, "véges, nem negatív egész számú másodperc",
                    "a megtorlási állapot kikapcsol");
            return 0L;
        }
        final long seconds = number.longValue();
        try {
            return Math.multiplyExact(seconds, 1_000L);
        } catch (final ArithmeticException exception) {
            warn(path, seconds, "milliszekundumban is ábrázolható időtartam",
                    "a megtorlási állapot kikapcsol");
            return 0L;
        }
    }

    private Set<String> stringSet(final String path, final Set<String> defaults) {
        if (!configManager.contains(path)) {
            return defaults;
        }
        final Object raw = configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().get(path);
        if (!(raw instanceof java.util.List<?> list)) {
            warn(path, raw, "YAML-lista", "az adott lista kontrolláltan kiürül");
            return Set.of();
        }
        if (list.stream().anyMatch(value -> !(value instanceof String))) {
            warn(path, raw, "csak szöveges elemeket tartalmazó YAML-lista",
                    "a nem szöveges elemek kimaradnak");
        }
        final Set<String> values = list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (path.endsWith("affected-exhaustion-reasons")) {
            final Set<String> invalid = values.stream()
                    .filter(value -> !EXHAUSTION_REASONS.contains(value))
                    .collect(Collectors.toUnmodifiableSet());
            if (!invalid.isEmpty()) {
                logger.warning("Config: ismeretlen EntityExhaustionEvent ok a(z) '" + path
                        + "' listában: " + invalid + " — ezek az ágak kontrolláltan kimaradnak.");
            }
            return values.stream().filter(EXHAUSTION_REASONS::contains)
                    .collect(Collectors.toUnmodifiableSet());
        }
        final Set<String> invalid = values.stream().filter(value -> {
            try {
                org.bukkit.entity.EntityType.valueOf(value);
                return false;
            } catch (final IllegalArgumentException exception) {
                return true;
            }
        }).collect(Collectors.toUnmodifiableSet());
        if (!invalid.isEmpty()) {
            logger.warning("Config: ismeretlen entity type a(z) '" + path + "' listában: "
                    + invalid + " — ezek az ágak kontrolláltan kimaradnak.");
        }
        return values.stream().filter(value -> !invalid.contains(value))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> markerSet(final String path, final Set<String> defaults) {
        if (!configManager.contains(path)) {
            return defaults;
        }
        final Object raw = configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().get(path);
        if (!(raw instanceof java.util.List<?> list)) {
            warn(path, raw, "YAML-lista", "az adott marker-lista kontrolláltan kiürül");
            return Set.of();
        }
        if (list.stream().anyMatch(value -> !(value instanceof String))) {
            warn(path, raw, "csak szöveges elemeket tartalmazó YAML-lista",
                    "a nem szöveges markerek kimaradnak");
        }
        final Set<String> values = list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        final Set<String> invalid = values.stream()
                .filter(value -> org.bukkit.NamespacedKey.fromString(value) == null)
                .collect(Collectors.toUnmodifiableSet());
        if (!invalid.isEmpty()) {
            logger.warning("Config: hibás namespaced marker a(z) '" + path + "' listában: "
                    + invalid + " — ezek az ágak kontrolláltan kimaradnak.");
        }
        return values.stream().filter(value -> !invalid.contains(value))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isUnitInterval(final double value) {
        return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
    }

    private Double configuredNumber(final String path, final double defaultValue,
                                    final String fallback) {
        if (!configManager.contains(path) || configManager.getConfiguration() == null) {
            return defaultValue;
        }
        final Object raw = configManager.getConfiguration().get(path);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        warn(path, raw, "szám", fallback);
        return null;
    }

    private void warn(final String path, final Object value, final String expected,
                      final String fallback) {
        logger.warning("Config: hibás '" + path + "' érték (" + value + "); elvárt: "
                + expected + " — " + fallback + ". Az adminértéket a plugin nem clampeli.");
    }
}
