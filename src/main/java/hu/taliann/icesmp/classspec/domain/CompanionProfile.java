package hu.taliann.icesmp.classspec.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable logical companion data; a live entity UUID is intentionally absent. */
public record CompanionProfile(UUID companionId, String namespace, String typeId, String name,
                               int level, long experience, String traitId, String stance,
                               List<String> equipmentIds, long resummonAtEpochMillis,
                               Map<String, String> persistentState) {

    public CompanionProfile {
        Objects.requireNonNull(companionId, "companionId");
        namespace = required(namespace, "namespace");
        typeId = required(typeId, "typeId");
        name = name == null ? "" : name.trim();
        traitId = traitId == null ? "" : traitId.trim();
        stance = stance == null ? "" : stance.trim();
        if (level < 1 || experience < 0L || resummonAtEpochMillis < 0L) {
            throw new IllegalArgumentException("Companion level/timestamps must be non-negative");
        }
        equipmentIds = List.copyOf(Objects.requireNonNull(equipmentIds, "equipmentIds"));
        persistentState = collisionSafeMap(persistentState);
        if (equipmentIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Companion equipment ids must be non-blank");
        }
    }

    private static String required(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.trim();
    }

    private static Map<String, String> collisionSafeMap(final Map<String, String> source) {
        Objects.requireNonNull(source, "persistentState");
        final Map<String, String> result = new LinkedHashMap<>();
        final Set<String> normalizedKeys = new LinkedHashSet<>();
        for (final Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("Companion state keys and values must be present");
            }
            if (!normalizedKeys.add(ClassSpecCatalog.normalize(entry.getKey()))) {
                throw new IllegalArgumentException("Normalized companion state key collision: "
                        + entry.getKey());
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(result);
    }
}
