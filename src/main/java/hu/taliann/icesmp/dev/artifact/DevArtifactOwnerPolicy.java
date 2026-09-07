package hu.taliann.icesmp.dev.artifact;

import java.util.UUID;
import java.util.function.BiFunction;

public sealed interface DevArtifactOwnerPolicy permits FixedArtifactOwner, ConfiguredArtifactOwner {
    UUID resolve(BiFunction<String, String, String> configuration);
}
