package hu.taliann.icesmp.classrelic;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A `relics.class-relics` config-szekció fail-fast fordítója. Pure (nyers Map-fán dolgozik,
 * Bukkit nélkül tesztelhető); BÁRMELY hibánál kivétellel áll le, hogy a hívó a régi
 * katalógus-pillanatképet tarthassa meg — félbetöltött registry nem publikálható.
 */
public final class ClassRelicCatalogLoader {

    private ClassRelicCatalogLoader() {
    }

    /**
     * @param root a `relics.class-relics` szekció nyers kulcs→érték fája (yaml-load alak);
     *             {@code null} = nincs szekció → üres katalógus
     * @param requireCompleteCatalog true esetén a 13/13 + 35/35 teljesség kötelező
     */
    public static ClassRelicCatalog load(final Map<String, Object> root,
                                         final boolean requireCompleteCatalog) {
        final LinkedHashMap<String, ClassRelicBinding> bindings = new LinkedHashMap<>();
        if (root != null) {
            for (final Map.Entry<String, Object> entry : root.entrySet()) {
                final String relicId = entry.getKey() == null ? ""
                        : entry.getKey().trim().toLowerCase(Locale.ROOT);
                final ClassRelicBinding binding = binding(relicId, section(entry.getValue(),
                        "class-relics." + relicId));
                if (bindings.putIfAbsent(binding.relicId(), binding) != null) {
                    throw new IllegalArgumentException("duplicate class-relic entry: "
                            + binding.relicId());
                }
            }
        }
        return new ClassRelicCatalog(bindings, requireCompleteCatalog);
    }

    /**
     * Az Awakening cooldown integer-szemantikájú és korlátos: a felső határ (100 év) mellett
     * a ready-at aritmetika (now + cooldown*1000) normál epoch-millis értéknél sem tud
     * túlcsordulni — a hibás config reloadkor bukik, nem használatkor.
     */
    public static final long MAX_AWAKENING_COOLDOWN_SECONDS = 3_153_600_000L;

    private static ClassRelicBinding binding(final String relicId, final Map<String, Object> node) {
        final String classId = string(node.get("class"), relicId + ".class");
        final Map<String, Object> activation = optionalSection(node.get("activation"),
                relicId + ".activation");
        final boolean requiresPossession = bool(
                activation.getOrDefault("requires-physical-possession", Boolean.TRUE),
                relicId + ".activation.requires-physical-possession");

        final Map<String, Object> basePowerNode = section(node.get("base-power"),
                relicId + ".base-power");
        final LinkedHashMap<RelicModifier, Double> modifiers = new LinkedHashMap<>();
        final Object maxResource = basePowerNode.get("max-resource-percent");
        if (maxResource != null) {
            modifiers.put(RelicModifier.CLASS_RESOURCE_MAX,
                    number(maxResource, relicId + ".base-power.max-resource-percent"));
        }
        final Object regen = basePowerNode.get("resource-regen-percent");
        if (regen != null) {
            modifiers.put(RelicModifier.CLASS_RESOURCE_REGEN,
                    number(regen, relicId + ".base-power.resource-regen-percent"));
        }
        final ClassRelicBinding.BasePower basePower = new ClassRelicBinding.BasePower(
                string(basePowerNode.get("id"), relicId + ".base-power.id"), modifiers);

        final LinkedHashMap<String, ClassRelicBinding.Resonance> resonances = new LinkedHashMap<>();
        optionalSection(node.get("resonances"), relicId + ".resonances")
                .forEach((specKey, value) -> {
            final String specId = specKey == null ? ""
                    : specKey.trim().toLowerCase(Locale.ROOT);
            final Map<String, Object> resonanceNode = section(value,
                    relicId + ".resonances." + specId);
            resonances.put(specId, new ClassRelicBinding.Resonance(
                    string(resonanceNode.get("id"), relicId + ".resonances." + specId + ".id"),
                    bool(resonanceNode.getOrDefault("enabled", Boolean.FALSE),
                            relicId + ".resonances." + specId + ".enabled")));
        });

        final Map<String, Object> awakeningNode = section(node.get("awakening"),
                relicId + ".awakening");
        final ClassRelicBinding.Awakening awakening = new ClassRelicBinding.Awakening(
                string(awakeningNode.get("id"), relicId + ".awakening.id"),
                bool(awakeningNode.getOrDefault("enabled", Boolean.FALSE),
                        relicId + ".awakening.enabled"),
                integerSeconds(awakeningNode.getOrDefault("cooldown-seconds", 0L),
                        relicId + ".awakening.cooldown-seconds"));

        return new ClassRelicBinding(relicId, classId, requiresPossession, basePower,
                resonances, awakening);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(final Object value, final String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("missing/invalid config section: " + path);
        }
        return (Map<String, Object>) map;
    }

    /**
     * A hiányzó és a rossz típusú szekció KÜLÖN eset: az absent mező defaultolhat, a
     * jelen lévő, de nem szekció alakú érték (pl. {@code resonances: "abc"}) hibás
     * config, amit a fail-fast szerződés szerint el kell utasítani — nem normalizálható
     * csendben üresre.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> optionalSection(final Object value, final String path) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("invalid config section (wrong type): " + path);
    }

    private static String string(final Object value, final String path) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("missing/invalid config string: " + path);
        }
        return text;
    }

    private static boolean bool(final Object value, final String path) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("missing/invalid config boolean: " + path);
    }

    private static double number(final Object value, final String path) {
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("missing/invalid config number: " + path);
    }

    /** Integer-szemantika: tört érték (120.9) nem csonkolható; negatív és túl nagy érték hibás. */
    private static long integerSeconds(final Object value, final String path) {
        if (!(value instanceof Number number)
                || !(value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(
                    "missing/invalid config integer (whole seconds required): " + path);
        }
        final long seconds = number.longValue();
        if (seconds < 0L || seconds > MAX_AWAKENING_COOLDOWN_SECONDS) {
            throw new IllegalArgumentException("config integer out of range [0, "
                    + MAX_AWAKENING_COOLDOWN_SECONDS + "]: " + path);
        }
        return seconds;
    }
}
