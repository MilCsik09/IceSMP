package hu.taliann.icesmp.quest;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Executes the native queue with held acknowledgements, reentrancy, retirement and real contention. */
public final class QuestMutationQueueRegressionSuite {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        orderedFailureIsolation(); boundsAndCancellation(); retirementAndShutdown();
        reentrantCompletion(); concurrentAdmission(); nativeWiring();
        System.out.println("Quest mutation queue passed: " + assertions
                + " assertions; bounded FIFO, dependency refusal, nonblocking retirement/shutdown and concurrent admission.");
    }

    private static void orderedFailureIsolation() {
        var queue = new QuestMutationQueue(); UUID player = UUID.randomUUID();
        var first = new CompletableFuture<Void>(); var second = new CompletableFuture<Void>();
        List<Integer> entered = new ArrayList<>();
        var a = queue.submit(player, () -> { entered.add(1); return first; });
        var b = queue.submit(player, () -> { entered.add(2); return second; });
        var c = queue.submit(player, () -> { entered.add(3); return CompletableFuture.completedFuture(null); });
        var independent = queue.submit(UUID.randomUUID(), () -> CompletableFuture.completedFuture(null));
        check(entered.equals(List.of(1)) && independent.toCompletableFuture().isDone(), "one profile blocked another or queue lost FIFO");
        first.complete(null); check(entered.equals(List.of(1, 2)) && a.toCompletableFuture().isDone(), "acknowledged work did not start the next entry");
        second.completeExceptionally(new IOException("profile write failed"));
        failed(b, IOException.class); failed(c, IOException.class);
        check(entered.equals(List.of(1, 2)) && queue.state().pending() == 0, "failed predecessor entered dependent completion");
        queue.submit(player, () -> { entered.add(4); return CompletableFuture.completedFuture(null); }).toCompletableFuture().join();
        check(entered.equals(List.of(1, 2, 4)) && queue.state().players() == 0, "drained failure poisoned later independent activity");
        failed(queue.submit(player, () -> { throw new IllegalStateException("domain entry failed"); }), IllegalStateException.class);
        failed(queue.submit(player, () -> null), NullPointerException.class);
        check(queue.state().pending() == 0, "synchronous domain failure retained capacity");
    }

    private static void boundsAndCancellation() {
        var queue = new QuestMutationQueue(2, 2, 3); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var held = new CompletableFuture<Void>(); var other = new CompletableFuture<Void>();
        var first = queue.submit(a, () -> held);
        var copy = first.toCompletableFuture(); copy.cancel(false);
        check(!held.isCancelled() && queue.state().pending() == 1, "caller cancellation cancelled an entered canonical write");
        var queued = queue.submit(a, () -> CompletableFuture.completedFuture(null));
        failed(queue.submit(a, () -> { throw new AssertionError("per-player cap entered work"); }), RejectedExecutionException.class);
        var active = queue.submit(b, () -> other);
        failed(queue.submit(b, () -> { throw new AssertionError("global cap entered work"); }), RejectedExecutionException.class);
        failed(queue.submit(UUID.randomUUID(), () -> { throw new AssertionError("player cap entered work"); }), RejectedExecutionException.class);
        check(queue.state().equals(new QuestMutationQueue.State(2, 3, false)), "refused capacity changed the active queue");
        held.complete(null); other.complete(null); queued.toCompletableFuture().join(); active.toCompletableFuture().join();
        check(queue.state().pending() == 0, "capacity did not return after held acknowledgement");
        for (int[] caps : List.of(new int[]{0, 1, 1}, new int[]{257, 1, 1}, new int[]{1, 65, 1}, new int[]{1, 1, 4097})) {
            try { new QuestMutationQueue(caps[0], caps[1], caps[2]); throw new AssertionError("invalid cap accepted"); }
            catch (IllegalArgumentException expected) { assertions++; }
        }
    }

    private static void retirementAndShutdown() {
        var queue = new QuestMutationQueue(); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var first = new CompletableFuture<Void>(); var second = new CompletableFuture<Void>();
        var active = queue.submit(a, () -> first);
        var dropped = queue.submit(a, () -> { throw new AssertionError("logout entered queued activity"); });
        var retired = queue.retire(a);
        failed(dropped, RejectedExecutionException.class);
        check(!retired.toCompletableFuture().isDone() && !active.toCompletableFuture().isDone() && !first.isCancelled(),
                "logout cancelled an entered domain write or falsely acknowledged drain");
        failed(queue.submit(a, () -> { throw new AssertionError("retired profile reentered before drain"); }), RejectedExecutionException.class);
        queue.submit(b, () -> second);
        var closing = queue.close();
        check(!closing.toCompletableFuture().isDone() && queue.state().stopping(), "shutdown blocked or completed before native writes acknowledged");
        failed(queue.submit(UUID.randomUUID(), () -> { throw new AssertionError("shutdown admitted new activity"); }), RejectedExecutionException.class);
        first.complete(null); retired.toCompletableFuture().join();
        check(!closing.toCompletableFuture().isDone(), "one player drain completed the whole shutdown");
        second.completeExceptionally(new IOException("disable interrupted storage"));
        closing.toCompletableFuture().join(); queue.close().toCompletableFuture().join();
        check(queue.state().pending() == 0 && queue.state().players() == 0, "shutdown retained retired profile lanes");

        var live = new QuestMutationQueue(); var held = new CompletableFuture<Void>();
        live.submit(a, () -> held); var finished = live.retire(a); held.complete(null); finished.toCompletableFuture().join();
        live.submit(a, () -> CompletableFuture.completedFuture(null)).toCompletableFuture().join();
        check(live.state().pending() == 0, "a reconnected profile could not start after its old lane drained");
    }

    private static void reentrantCompletion() {
        var queue = new QuestMutationQueue(); UUID player = UUID.randomUUID();
        var held = new CompletableFuture<Void>(); AtomicInteger entered = new AtomicInteger();
        var first = queue.submit(player, () -> held);
        var next = queue.submit(player, () -> { entered.incrementAndGet(); return CompletableFuture.completedFuture(null); });
        first.whenComplete((ignored, failure) -> queue.retire(player));
        held.complete(null); failed(next, RejectedExecutionException.class);
        check(entered.get() == 0 && queue.state().pending() == 0, "retirement from completion raced the next native entry");

        var immediate = new QuestMutationQueue(); List<CompletionStage<Void>> nested = new ArrayList<>();
        immediate.submit(player, () -> {
            for (int n = 0; n < 63; n++) nested.add(immediate.submit(player, () -> CompletableFuture.completedFuture(null)));
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture().join();
        check(nested.stream().allMatch(stage -> stage.toCompletableFuture().isDone())
                && immediate.state().pending() == 0, "synchronous full-lane completion stalled/reentered a map mutation");
    }

    private static void concurrentAdmission() throws Exception {
        for (int iteration = 0; iteration < 30; iteration++) {
            var queue = new QuestMutationQueue(1, 8, 8); UUID player = UUID.randomUUID();
            var held = new CompletableFuture<Void>(); AtomicInteger entered = new AtomicInteger();
            queue.submit(player, () -> { entered.incrementAndGet(); return held; });
            var pool = Executors.newFixedThreadPool(4);
            try {
                var release = new CountDownLatch(1); List<Future<CompletionStage<Void>>> futures = new ArrayList<>();
                for (int n = 0; n < 32; n++) futures.add(pool.submit(() -> {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test barrier timed out");
                    return queue.submit(player, () -> { entered.incrementAndGet(); return CompletableFuture.completedFuture(null); });
                }));
                release.countDown(); List<CompletionStage<Void>> outcomes = new ArrayList<>();
                for (var future : futures) outcomes.add(future.get(5, TimeUnit.SECONDS));
                check(queue.state().pending() == 8 && entered.get() == 1, "concurrent admission exceeded a cap or executed two profile mutations");
                var closing = queue.close(); held.complete(null); closing.toCompletableFuture().get(5, TimeUnit.SECONDS);
                for (var outcome : outcomes) failed(outcome, RejectedExecutionException.class);
                check(entered.get() == 1 && queue.state().pending() == 0, "shutdown lost/called concurrently queued work");
            } finally { pool.shutdownNow(); check(pool.awaitTermination(5, TimeUnit.SECONDS), "test pool failed to stop"); }
        }
    }

    private static void nativeWiring() throws Exception {
        final String manager = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/managers/QuestManager.java"));
        final String core = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"));
        check(manager.contains("mutations.submit(playerId, work)") && manager.contains("mutations.retire(playerId)")
                && manager.contains("mutations.close()") && !manager.contains("mutationTails"), "native manager bypassed queue admission/retirement/shutdown");
        check(manager.contains("mirrors.remove(playerId, speculative)") && manager.contains("mirrors.remove(playerId, retired)"),
                "old callback could clear a newer session's mirror");
        check(core.indexOf("shutdownStep(\"questManager\"") < core.indexOf("moderationManager.prepareShutdown"),
                "quest admission remained open during native shutdown preparation");
    }

    private static void failed(CompletionStage<?> stage, Class<? extends Throwable> type) {
        assertions++; try { stage.toCompletableFuture().join(); throw new AssertionError("expected " + type.getSimpleName()); }
        catch (CompletionException failure) {
            Throwable root = failure; while (root instanceof CompletionException && root.getCause() != null) root = root.getCause();
            if (!type.isInstance(root)) throw new AssertionError("expected " + type.getSimpleName() + ", got " + root, root);
        }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
