package hu.taliann.icesmp.dev.weaver.projection;

import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/** Bounded, retryable wakeups after durable publication. Values and stable references only. */
public final class WeaverProjectionDispatcher {
    private static final int MAX_ACKNOWLEDGED_TARGETS = 2560;
    private record Target(String provider, SubjectRef subject) { }
    private final BiFunction<String, Set<SubjectRef>, CompletionStage<Void>> consumer;
    private final Map<Target, List<WeaverProjection>> acknowledged = new LinkedHashMap<>();
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private int cursor;
    public WeaverProjectionDispatcher(final BiFunction<String, Set<SubjectRef>, CompletionStage<Void>> consumer) { this.consumer = Objects.requireNonNull(consumer); }
    public CompletionStage<Void> pulse(final Map<UUID, WeaverProjection> current) {
        if (current.size() > 1280) return CompletableFuture.failedFuture(new IllegalArgumentException("Projection publication capacity"));
        if (!inFlight.compareAndSet(false, true)) return CompletableFuture.completedFuture(null);
        final Map<Target, List<WeaverProjection>> desired = new LinkedHashMap<>();
        current.values().stream().sorted(Comparator.comparingLong(WeaverProjection::sequence)).forEach(projection ->
                desired.computeIfAbsent(new Target(projection.providerId(), projection.subject()), ignored -> new ArrayList<>()).add(projection));
        final LinkedHashSet<Target> targets = new LinkedHashSet<>(acknowledged.keySet()); targets.addAll(desired.keySet());
        final List<Target> changed = targets.stream().filter(target -> !acknowledged.getOrDefault(target, List.of()).equals(desired.getOrDefault(target, List.of()))).toList();
        final Map<String, Set<SubjectRef>> batches = new LinkedHashMap<>();
        if (!changed.isEmpty()) {
            cursor = Math.floorMod(cursor, changed.size());
            for (int i = 0; i < Math.min(16, changed.size()); i++) {
                final Target target = changed.get((cursor + i) % changed.size()); batches.computeIfAbsent(target.provider(), ignored -> new LinkedHashSet<>()).add(target.subject());
            }
            cursor += Math.min(16, changed.size());
        }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final var batch : batches.entrySet()) chain = chain.thenCompose(ignored -> {
            final CompletionStage<Void> result;
            try { result = Objects.requireNonNull(consumer.apply(batch.getKey(), Set.copyOf(batch.getValue()))); }
            catch (final RuntimeException failure) { return CompletableFuture.completedFuture(null); }
            return result.handle((done, failure) -> {
                if (failure == null) for (final SubjectRef subject : batch.getValue()) {
                    final Target target = new Target(batch.getKey(), subject); final List<WeaverProjection> values = desired.getOrDefault(target, List.of());
                    if (values.isEmpty()) acknowledged.remove(target);
                    else if (acknowledged.containsKey(target) || acknowledged.size() < MAX_ACKNOWLEDGED_TARGETS) acknowledged.put(target, List.copyOf(values));
                }
                return (Void) null;
            });
        });
        return chain.whenComplete((ignored, failure) -> inFlight.set(false));
    }
}
