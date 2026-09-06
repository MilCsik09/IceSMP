package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.WeaverDomainRejection;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class WeaverFoliaOwnershipRegressionSuite {
    public static void main(final String[] args) throws InterruptedException {
        final UUID id = UUID.randomUUID();
        check(SubjectRoute.owner(new PlayerRef(id)).equals(new EntityOwner(id)), "player ownership");
        check(SubjectRoute.owner(new EntityRef(id)).equals(new EntityOwner(id)), "entity ownership");
        check(SubjectRoute.owner(new BlockRef(id, -17, 64, 16)).equals(new RegionOwner(id, -2, 1)), "negative block chunk");
        check(SubjectRoute.owner(new LocationRef(id, -0.1, 64, -16.1, 0, 0)).equals(new RegionOwner(id, -1, -2)), "floor location before shift");
        check(SubjectRoute.owner(new WorldRef(id)) instanceof GlobalOwner, "world ownership");
        check(SubjectRoute.owner(new AreaRef(id, new RadiusArea(0, 64, 0, 4))) instanceof GlobalOwner, "pure AREA descriptor route");
        final AtomicInteger effects = new AtomicInteger();
        final OwnerTaskAdmission<Integer> queued = new OwnerTaskAdmission<>();
        queued.timeout(); queued.run(() -> CompletableFuture.completedFuture(effects.incrementAndGet()));
        check(effects.get() == 0, "timed-out queued work executed late");
        assertFailure(queued, "OWNER_TIMEOUT_UNSTARTED");
        final OwnerTaskAdmission<Integer> retired = new OwnerTaskAdmission<>();
        retired.unavailable(); retired.run(() -> CompletableFuture.completedFuture(effects.incrementAndGet()));
        check(effects.get() == 0, "retired entity task executed"); assertFailure(retired, "OWNER_UNAVAILABLE");
        final OwnerTaskAdmission<Integer> started = new OwnerTaskAdmission<>();
        final CompletableFuture<Integer> domain = new CompletableFuture<>();
        started.run(() -> { effects.incrementAndGet(); return domain; }); started.timeout(); domain.complete(1);
        check(effects.get() == 1, "started operation replayed"); assertFailure(started, "OWNER_TIMEOUT_STARTED");
        final OwnerTaskAdmission<Integer> shutdown = new OwnerTaskAdmission<>();
        shutdown.shutdown(); shutdown.run(() -> CompletableFuture.completedFuture(effects.incrementAndGet()));
        check(effects.get() == 1, "disable admitted queued task"); assertFailure(shutdown, "OWNER_SHUTDOWN_UNSTARTED");
        final CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(2);
        final OwnerTaskAdmission<Integer> racing = new OwnerTaskAdmission<>();
        final AtomicInteger raced = new AtomicInteger();
        final Runnable run = () -> {
            try { start.await(); racing.run(() -> CompletableFuture.completedFuture(raced.incrementAndGet())); }
            catch (final InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { done.countDown(); }
        };
        new Thread(run, "weaver-owner-fixture-a").start(); new Thread(run, "weaver-owner-fixture-b").start(); start.countDown();
        check(done.await(5, TimeUnit.SECONDS) && raced.get() == 1, "scheduler/retirement duplicate admission");
        System.out.println("Weaver Folia routing/admission regression passed; live cross-region execution remains a server evidence gate.");
    }
    private static void assertFailure(final OwnerTaskAdmission<?> admission, final String code) {
        final java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        admission.result().whenComplete((value, failure) -> error.set(failure));
        Throwable failure = error.get();
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) failure = failure.getCause();
        check(failure instanceof WeaverDomainRejection rejection && rejection.code().equals(code), "wrong owner failure classification: " + code);
    }
    private static void check(final boolean condition, final String reason) { if (!condition) throw new AssertionError(reason); }
}
