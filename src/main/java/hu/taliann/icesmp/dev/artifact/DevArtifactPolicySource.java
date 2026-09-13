package hu.taliann.icesmp.dev.artifact;

@FunctionalInterface
public interface DevArtifactPolicySource {
    DevArtifactPolicy current();
}
