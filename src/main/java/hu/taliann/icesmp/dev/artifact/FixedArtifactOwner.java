package hu.taliann.icesmp.dev.artifact;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

public record FixedArtifactOwner(UUID owner) implements DevArtifactOwnerPolicy {
    public FixedArtifactOwner {
        Objects.requireNonNull(owner, "owner");
    }

    @Override
    public UUID resolve(final BiFunction<String, String, String> configuration) {
        return owner;
    }
}
