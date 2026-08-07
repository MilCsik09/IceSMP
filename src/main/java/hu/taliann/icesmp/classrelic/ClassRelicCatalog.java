package hu.taliann.icesmp.classrelic;

import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, teljesen validált Class Relic katalógus-pillanatkép. A reload egy ÚJ
 * példányt épít és csak sikeres validáció után publikálja (atomikus referenciacsere) —
 * hibás config nem hagyhat félbetöltött registryt.
 */
public final class ClassRelicCatalog {

    /** Fejlesztési állapotban a katalógus lehet részleges; true = 13/13 + 35/35 kötelező. */
    private final boolean requireCompleteCatalog;
    private final Map<String, ClassRelicBinding> byRelicId;
    private final Map<String, ClassRelicBinding> byClassId;

    public ClassRelicCatalog(final Map<String, ClassRelicBinding> bindings,
                             final boolean requireCompleteCatalog) {
        Objects.requireNonNull(bindings, "bindings");
        this.requireCompleteCatalog = requireCompleteCatalog;
        final LinkedHashMap<String, ClassRelicBinding> relics = new LinkedHashMap<>();
        final LinkedHashMap<String, ClassRelicBinding> classes = new LinkedHashMap<>();
        for (final ClassRelicBinding binding : bindings.values()) {
            validate(binding);
            if (relics.putIfAbsent(binding.relicId(), binding) != null) {
                throw new IllegalArgumentException("duplicate class-relic binding for relic: "
                        + binding.relicId());
            }
            if (classes.putIfAbsent(binding.classId(), binding) != null) {
                throw new IllegalArgumentException("duplicate class-relic binding for class: "
                        + binding.classId());
            }
        }
        if (requireCompleteCatalog) {
            requireCompleteness(relics, classes);
        }
        this.byRelicId = Map.copyOf(relics);
        this.byClassId = Map.copyOf(classes);
    }

    public static ClassRelicCatalog empty() {
        return new ClassRelicCatalog(Map.of(), false);
    }

    public Optional<ClassRelicBinding> byRelic(final String relicId) {
        return relicId == null ? Optional.empty()
                : Optional.ofNullable(byRelicId.get(relicId.toLowerCase(java.util.Locale.ROOT)));
    }

    public Optional<ClassRelicBinding> byClass(final String classId) {
        return classId == null ? Optional.empty()
                : Optional.ofNullable(byClassId.get(classId.toLowerCase(java.util.Locale.ROOT)));
    }

    public int size() {
        return byRelicId.size();
    }

    public boolean requiresCompleteCatalog() {
        return requireCompleteCatalog;
    }

    private static void validate(final ClassRelicBinding binding) {
        if (!ClassSpecCatalog.isKnownClass(binding.classId())) {
            throw new IllegalArgumentException("unknown class in class-relic binding: "
                    + binding.classId());
        }
        binding.resonances().forEach((specId, resonance) -> {
            if (!ClassSpecCatalog.isKnownSpecialization(specId)) {
                throw new IllegalArgumentException("unknown specialization in class-relic binding "
                        + binding.relicId() + ": " + specId);
            }
            if (!ClassSpecCatalog.belongsTo(specId, binding.classId())) {
                throw new IllegalArgumentException("specialization " + specId
                        + " does not belong to class " + binding.classId()
                        + " (relic " + binding.relicId() + ")");
            }
        });
    }

    /** A végcél-szerződés: 13/13 class → 1 relic, 35/35 spec → 1 resonance. */
    private static void requireCompleteness(final Map<String, ClassRelicBinding> relics,
                                            final Map<String, ClassRelicBinding> classes) {
        for (final String classId : ClassSpecCatalog.classIds()) {
            final ClassRelicBinding binding = classes.get(classId);
            if (binding == null) {
                throw new IllegalArgumentException(
                        "complete catalog required: class has no relic: " + classId);
            }
            for (final String specId : ClassSpecCatalog.specializationIds()) {
                if (classId.equals(ClassSpecCatalog.parentOf(specId))
                        && !binding.resonances().containsKey(specId)) {
                    throw new IllegalArgumentException(
                            "complete catalog required: specialization has no resonance: " + specId);
                }
            }
        }
    }
}
