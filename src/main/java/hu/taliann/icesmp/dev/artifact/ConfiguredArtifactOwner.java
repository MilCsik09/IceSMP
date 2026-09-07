package hu.taliann.icesmp.dev.artifact;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

public record ConfiguredArtifactOwner(String configPath, UUID fallback) implements DevArtifactOwnerPolicy {
    public ConfiguredArtifactOwner {
        Objects.requireNonNull(configPath, "configPath");
        Objects.requireNonNull(fallback, "fallback");
        if (configPath.isBlank()) throw new IllegalArgumentException("Empty owner configuration path");
    }

    @Override
    public UUID resolve(final BiFunction<String, String, String> configuration) {
        return UUID.fromString(configuration.apply(configPath, fallback.toString()).trim());
    }
}
