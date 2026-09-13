package hu.taliann.icesmp.dev.artifact;

import java.util.Objects;

public record DevArtifactRegistration(DevArtifactDefinition definition, DevArtifactBehavior behavior) {
    public DevArtifactRegistration {
        Objects.requireNonNull(definition);
        Objects.requireNonNull(behavior);
        if (!definition.id().equals(behavior.artifactId())) throw new IllegalArgumentException("Artifact registration id mismatch");
    }
}
