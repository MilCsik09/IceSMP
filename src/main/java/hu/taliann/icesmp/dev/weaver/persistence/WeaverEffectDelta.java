package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceRecord;
import hu.taliann.icesmp.dev.weaver.projection.WeaverProjection;
import java.util.*;

/** Before-images belong to the effect transaction, never to a canonical gameplay registry. */
public record WeaverEffectDelta(Map<UUID, WeaverProjection> added, Map<UUID, WeaverProjection> removed,
                                Map<UUID, WeaverInfluenceRecord> endedBefore, boolean complete) {
    public WeaverEffectDelta {
        added = Map.copyOf(added); removed = Map.copyOf(removed); endedBefore = Map.copyOf(endedBefore);
        if (added.size() > 128 || removed.size() > 128 || endedBefore.size() > WeaverJournalState.MAX_INFLUENCES
                || !Collections.disjoint(added.keySet(), removed.keySet())) throw new IllegalArgumentException("Invalid effect delta bounds");
        if (!complete && (!added.isEmpty() || !removed.isEmpty() || !endedBefore.isEmpty())) throw new IllegalArgumentException("Partial legacy effect delta");
        for (final var entry : added.entrySet()) if (!entry.getKey().equals(entry.getValue().projectionId())) throw new IllegalArgumentException("Added projection key mismatch");
        for (final var entry : removed.entrySet()) if (!entry.getKey().equals(entry.getValue().projectionId())) throw new IllegalArgumentException("Removed projection key mismatch");
        for (final var entry : endedBefore.entrySet()) if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("Influence before-image key mismatch");
    }
    public static WeaverEffectDelta unavailable() { return new WeaverEffectDelta(Map.of(), Map.of(), Map.of(), false); }
}
