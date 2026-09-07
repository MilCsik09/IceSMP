package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WeaverProjectionDispatchRegressionSuite {
    private static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
    private static void await(final CompletionStage<?> value) throws Exception { value.toCompletableFuture().get(10, TimeUnit.SECONDS); }
    private static WeaverProjection projection(final String provider, final int sequence, final EntityRef subject) {
        final UUID operation = UUID.randomUUID(); final String action = provider + ".project";
        return new WeaverProjection(UUID.randomUUID(), sequence, provider, action, subject, Lifetime.PERSISTENT,
                new DeveloperInfluence(operation, IntegrityMode.SANDBOX, action, HiddenDevAuthority.PRIMARY_DEVELOPER, 1),
                Map.of(provider + ".field", new WeaverValue(WeaverTypeId.parse("weaver:text@1"), Map.of("value", "effective"), provider, provider + ".facet", Set.of(), 1)), "before", 1, OptionalLong.empty());
    }
    public static void main(final String[] args) throws Exception {
        final Map<UUID, WeaverProjection> publication = new LinkedHashMap<>();
        for (int i = 1; i <= 320; i++) { final var projection = projection("fixture", i, new EntityRef(UUID.randomUUID())); publication.put(projection.projectionId(), projection); }
        final Set<SubjectRef> delivered = new HashSet<>(); final AtomicInteger calls = new AtomicInteger();
        final var dispatcher = new WeaverProjectionDispatcher((provider, subjects) -> { check(subjects.size() <= 16, "unbounded owner fanout"); delivered.addAll(subjects); calls.incrementAndGet(); return CompletableFuture.completedFuture(null); });
        for (int pulse = 0; pulse < 20; pulse++) await(dispatcher.pulse(publication));
        check(delivered.size() == 320, "startup publication lost dormant entity wakeups"); final int settledCalls = calls.get();
        await(dispatcher.pulse(publication)); check(calls.get() == settledCalls, "unchanged publication rescheduled every entity");
        delivered.clear(); for (int pulse = 0; pulse < 20; pulse++) await(dispatcher.pulse(Map.of()));
        check(delivered.size() == 320, "sever/session cleanup lost removed subject wakeups");

        final var first = publication.values().iterator().next(); final var gate = new CompletableFuture<Void>(); final AtomicInteger raced = new AtomicInteger();
        final var racing = new WeaverProjectionDispatcher((provider, subjects) -> raced.incrementAndGet() == 1 ? gate : CompletableFuture.completedFuture(null));
        final var initial = racing.pulse(Map.of(first.projectionId(), first)); await(racing.pulse(Map.of()));
        check(raced.get() == 1 && !initial.toCompletableFuture().isDone(), "concurrent pulses escaped bounded admission");
        gate.complete(null); await(initial); await(racing.pulse(Map.of())); check(raced.get() == 2, "publication changed during callback was lost");

        final Map<UUID, WeaverProjection> isolated = new LinkedHashMap<>(); final Set<SubjectRef> healthy = new HashSet<>();
        for (int i = 1; i <= 160; i++) {
            final var projection = projection(i <= 80 ? "broken" : "healthy", i, new EntityRef(UUID.randomUUID())); isolated.put(projection.projectionId(), projection);
        }
        final var isolation = new WeaverProjectionDispatcher((provider, subjects) -> {
            if (provider.equals("broken")) return CompletableFuture.failedFuture(new IllegalStateException("fixture failure"));
            healthy.addAll(subjects); return CompletableFuture.completedFuture(null);
        });
        for (int pulse = 0; pulse < 30; pulse++) await(isolation.pulse(isolated));
        check(healthy.size() == 80, "failing provider starved another provider's runtime consumer");
        System.out.println("Projection dispatch passed: 16-target admission, 320-subject startup and cleanup, coalesced publication races, no unchanged rescheduling and failure isolation without starvation.");
    }
}
