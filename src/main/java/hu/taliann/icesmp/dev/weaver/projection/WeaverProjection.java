package hu.taliann.icesmp.dev.weaver.projection;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import java.util.*;

public record WeaverProjection(UUID projectionId, long sequence, String providerId, String actionId, SubjectRef subject,
                               Lifetime lifetime, DeveloperInfluence influence, Map<String, WeaverValue> values,
                               String canonicalFingerprintAtApply, long createdAt, OptionalLong expiresAt) {
    public WeaverProjection {
        Objects.requireNonNull(projectionId); WeaverIds.descriptor(providerId); WeaverIds.descriptor(actionId); Objects.requireNonNull(subject);
        Objects.requireNonNull(lifetime); Objects.requireNonNull(influence); Objects.requireNonNull(expiresAt); values = Map.copyOf(values);
        if (sequence < 1 || lifetime == Lifetime.ONE_SHOT || values.isEmpty() || values.size() > 128 || createdAt < 0
                || createdAt != influence.appliedAt() || !actionId.startsWith(providerId + ".") || !influence.actionId().equals(actionId)
                || canonicalFingerprintAtApply == null || canonicalFingerprintAtApply.isBlank() || canonicalFingerprintAtApply.length() > 256
                || expiresAt.isPresent() && expiresAt.getAsLong() <= createdAt) throw new IllegalArgumentException("Invalid projection metadata");
        for (final var entry : values.entrySet()) {
            WeaverIds.descriptor(entry.getKey());
            if (!entry.getKey().startsWith(providerId + ".") || !entry.getValue().sourceProvider().equals(providerId)) throw new IllegalArgumentException("Foreign projection field");
        }
    }
    public boolean activeAt(final long now) { return expiresAt.isEmpty() || now < expiresAt.getAsLong(); }
}
