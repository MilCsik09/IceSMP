package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.api.WeaverReceipt;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent;
import hu.taliann.icesmp.dev.weaver.persistence.WeaverEffectCommit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;

/** Providers declare bounded quarantine before stages and materialize immutable effects after observed results. */
public record PreparedEffects(WeaverEffectIntent intent, Factory factory, Optional<Map<SubjectRef, Integer>> projectionReservations) {
    public PreparedEffects(final WeaverEffectIntent intent, final Factory factory) { this(intent, factory, Optional.empty()); }
    @FunctionalInterface public interface Factory {
        WeaverEffectCommit create(PreparedAction action, List<StageResult> results, WeaverReceipt receipt, long nextProjectionSequence);
    }
    public PreparedEffects {
        Objects.requireNonNull(intent); Objects.requireNonNull(factory); projectionReservations = projectionReservations.map(Map::copyOf);
        projectionReservations.ifPresent(reservations -> {
            if (reservations.size() > 128 || reservations.values().stream().anyMatch(count -> count < 1 || count > 32)
                    || reservations.values().stream().mapToInt(Integer::intValue).sum() > 128) throw new IllegalArgumentException("Projection reservation capacity");
        });
    }
}
