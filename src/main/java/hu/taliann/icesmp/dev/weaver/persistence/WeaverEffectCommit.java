package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceRecord;
import hu.taliann.icesmp.dev.weaver.projection.WeaverProjection;
import java.util.*;

/** Effects and original-receipt compensation are published in the same durable APPLIED snapshot. */
public record WeaverEffectCommit(List<WeaverProjection> projections, Set<UUID> removedProjections,
                                 List<WeaverInfluenceRecord> influences, Optional<UUID> undoneReceipt,
                                 Map<hu.taliann.icesmp.dev.weaver.subject.SubjectRef, String> expectedProjectionFingerprints) {
    public WeaverEffectCommit(final List<WeaverProjection> projections, final Set<UUID> removedProjections,
            final List<WeaverInfluenceRecord> influences, final Optional<UUID> undoneReceipt) {
        this(projections, removedProjections, influences, undoneReceipt, Map.of());
    }
    public WeaverEffectCommit {
        projections = List.copyOf(projections); removedProjections = Set.copyOf(removedProjections); influences = List.copyOf(influences); Objects.requireNonNull(undoneReceipt);
        expectedProjectionFingerprints = Map.copyOf(expectedProjectionFingerprints);
        if (expectedProjectionFingerprints.size() > 128 || expectedProjectionFingerprints.values().stream().anyMatch(value -> !value.matches("[0-9a-f]{64}"))) throw new IllegalArgumentException("Projection fingerprint bounds");
        if (projections.size() > 128 || removedProjections.size() > 128 || influences.size() > 128
                || projections.stream().map(WeaverProjection::projectionId).distinct().count() != projections.size()
                || influences.stream().map(WeaverInfluenceRecord::id).distinct().count() != influences.size()) throw new IllegalArgumentException("Effect commit bounds or duplicates");
    }
    public static WeaverEffectCommit none() { return new WeaverEffectCommit(List.of(), Set.of(), List.of(), Optional.empty()); }
}
