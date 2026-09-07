package hu.taliann.icesmp.dev.weaver.integrity;

import hu.taliann.icesmp.integrity.GameplayEffectGate;
import java.util.*;

/** ENDED keeps its native target fenced until the journal acknowledges the conditional end. */
public record InfluenceObservation(Status status, Optional<GameplayEffectGate.ObservationFence> fence) implements AutoCloseable {
    public enum Status { ACTIVE, ENDED, UNAVAILABLE }
    public InfluenceObservation {
        Objects.requireNonNull(status); Objects.requireNonNull(fence);
        if ((status == Status.ENDED) != fence.isPresent()) throw new IllegalArgumentException("Ended observation requires a fence");
    }
    public static InfluenceObservation active() { return new InfluenceObservation(Status.ACTIVE, Optional.empty()); }
    public static InfluenceObservation unavailable() { return new InfluenceObservation(Status.UNAVAILABLE, Optional.empty()); }
    public static InfluenceObservation ended(GameplayEffectGate.ObservationFence fence) { return new InfluenceObservation(Status.ENDED, Optional.of(fence)); }
    @Override public void close() { fence.ifPresent(GameplayEffectGate.ObservationFence::close); }
}
