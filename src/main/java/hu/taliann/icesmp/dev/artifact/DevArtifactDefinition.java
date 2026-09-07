package hu.taliann.icesmp.dev.artifact;

import java.util.Objects;

public record DevArtifactDefinition(String id, DevArtifactOwnerPolicy ownerPolicy,
                                    DevArtifactPresentationSource presentationSource,
                                    DevArtifactPolicySource policySource) {
    public DevArtifactDefinition {
        if (id == null || !id.matches("[a-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Invalid artifact id");
        }
        Objects.requireNonNull(ownerPolicy, "ownerPolicy");
        Objects.requireNonNull(presentationSource, "presentationSource");
        Objects.requireNonNull(policySource, "policySource");
    }
}
