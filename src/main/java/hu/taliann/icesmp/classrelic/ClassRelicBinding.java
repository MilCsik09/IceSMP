package hu.taliann.icesmp.classrelic;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Egy világ-egyedi Class Relic kaszt-kötése. Külön domainréteg a generikus
 * {@code RelicDefinition} fölött: a generikus relikviák (Mételytépő, szárnyak, …)
 * érintetlenek maradnak, a kaszt-fogalmak (class, resonance, awakening) CSAK itt élnek.
 *
 * @param relicId a generikus relic-registry azonosítója (ownership/lost/reclaim authority)
 * @param classId a kaszt, amelyhez a relic kötődik (ClassSpecCatalog kanonikus id)
 * @param requiresPhysicalPossession true esetén az ownership önmagában nem elég:
 *        a használható fizikai tárgynak a játékosnál kell lennie
 * @param basePower a kaszt-szintű állandó modifier-csomag
 * @param resonances specialization-id → resonance-kötés (csak a parent class specjei)
 * @param awakening a világ-egyedi nagy képesség kerete (durable cooldown a relickel utazik)
 */
public record ClassRelicBinding(
        String relicId,
        String classId,
        boolean requiresPhysicalPossession,
        BasePower basePower,
        Map<String, Resonance> resonances,
        Awakening awakening) {

    public ClassRelicBinding {
        relicId = requireId(relicId, "relicId");
        classId = requireId(classId, "classId");
        Objects.requireNonNull(basePower, "basePower");
        Objects.requireNonNull(awakening, "awakening");
        resonances = Map.copyOf(Objects.requireNonNull(resonances, "resonances"));
    }

    /** Kaszt-szintű állandó erő: modifier-csatorna → százalék (0 = nincs bónusz). */
    public record BasePower(String id, Map<RelicModifier, Double> percentByModifier) {
        public BasePower {
            id = requireId(id, "basePower.id");
            final Map<RelicModifier, Double> copy =
                    Map.copyOf(Objects.requireNonNull(percentByModifier, "percentByModifier"));
            copy.forEach((modifier, percent) -> {
                if (percent == null || !Double.isFinite(percent) || percent < 0.0D) {
                    throw new IllegalArgumentException(
                            "invalid base-power percent for " + modifier + ": " + percent);
                }
            });
            percentByModifier = copy;
        }
    }

    /** Specializációhoz kötött resonance; {@code enabled=false} = routolható, de inert. */
    public record Resonance(String id, boolean enabled) {
        public Resonance {
            id = requireId(id, "resonance.id");
        }
    }

    /** A világ-egyedi aktív képesség kerete; a durable cooldown authority a relic-oldalon él. */
    public record Awakening(String id, boolean enabled, long cooldownSeconds) {
        public Awakening {
            id = requireId(id, "awakening.id");
            if (cooldownSeconds < 0L) {
                throw new IllegalArgumentException("awakening cooldown cannot be negative");
            }
        }
    }

    private static String requireId(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        final String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(field + " must be [a-z0-9_]: " + value);
        }
        return normalized;
    }
}
