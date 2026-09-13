package hu.taliann.icesmp.integrity;

import java.util.*;

/** Native observation recipe, interpreted only by a registered provider codec/consumer. */
public record GameplayEffectLifetime(String type, Map<String, String> parameters) {
    public GameplayEffectLifetime {
        if (type == null || !type.matches("[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,96}@[1-9][0-9]{0,3}")) throw new IllegalArgumentException("Effect lifetime type/schema");
        parameters = Map.copyOf(parameters);
        if (parameters.isEmpty() || parameters.size() > 8 || parameters.entrySet().stream().anyMatch(entry -> !entry.getKey().matches("[a-z0-9_.-]{1,64}")
                || entry.getValue().length() > 256)) throw new IllegalArgumentException("Effect lifetime parameters");
    }
}
