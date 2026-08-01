package hu.taliann.icesmp.classspec.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Idempotency marker and bounded preservation area for uncertain legacy fields. */
public record MigrationState(String lastSuccessfulMigration, List<String> reviewReasons,
                             Map<String, String> preservedLegacy) {

    public MigrationState {
        lastSuccessfulMigration = clean(lastSuccessfulMigration);
        reviewReasons = List.copyOf(Objects.requireNonNull(reviewReasons, "reviewReasons"));
        preservedLegacy = collisionSafeMap(preservedLegacy);
        if (reviewReasons.stream().anyMatch(reason -> reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Migration review reasons must be non-blank");
        }
    }

    public static MigrationState none() {
        return new MigrationState("", List.of(), Map.of());
    }

    public boolean requiresReview() {
        return !reviewReasons.isEmpty();
    }

    private static String clean(final String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, String> collisionSafeMap(final Map<String, String> source) {
        Objects.requireNonNull(source, "preservedLegacy");
        final Map<String, String> result = new LinkedHashMap<>();
        final Set<String> normalizedKeys = new LinkedHashSet<>();
        for (final Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("Preserved legacy keys and values must be present");
            }
            if (!normalizedKeys.add(ClassSpecCatalog.normalize(entry.getKey()))) {
                throw new IllegalArgumentException("Normalized preserved legacy key collision: "
                        + entry.getKey());
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(result);
    }
}
