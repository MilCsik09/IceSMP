package hu.taliann.icesmp.moderation;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Review regressions layered on top of the original native moderation suite. */
public final class ModerationReviewRegressionSuite {
    private ModerationReviewRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        ModerationRegressionSuite.main(args);
        samePlayerSpamDecisionsAreSerializedAcrossAsyncRoutes();
        unrelatedPlayersAreNotGloballySerialized();
        visibilityRoutesRespectViewerState();
        schedulerTaskAndRetirementHaveOneWinner();
        schedulerSubmitFailureAndNullUseOneFallback();
        repeatingTaskRetirementBeforePublicationDoesNotLeak();
        replyPartnersAreFencedByJoinGeneration();
        escrowQueueSurvivesSnapshotAndClaimsExactlyOnce();
        targetCompletionIsPublishedOnlyAfterReturnQueue();
        schedulerRejectionLeavesPendingReturnForShutdownSnapshot();
        escrowSchemaRejectsCorruptAndPartialState();
        System.out.println("Moderation review regression suite passed.");
    }

    private static void samePlayerSpamDecisionsAreSerializedAcrossAsyncRoutes() throws Exception {
        final UUID playerId = new UUID(0L, 1L);
        final int[] intentionallyNonAtomicLastDecision = {0};
        final AtomicInteger accepted = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            workers.add(Thread.ofPlatform().start(() -> {
                await(start);
                final boolean blocked = ModerationSpamGuard.evaluate(playerId, () -> {
                    if (intentionallyNonAtomicLastDecision[0] == 0) {
                        Thread.yield();
                        intentionallyNonAtomicLastDecision[0] = 1;
                        return false;
                    }
                    return true;
                });
                if (!blocked) {
                    accepted.incrementAndGet();
                }
            }));
        }
        start.countDown();
        for (final Thread worker : workers) {
            worker.join(2_000L);
            check(!worker.isAlive(), "spam concurrency worker must terminate");
        }
        check(accepted.get() == 1,
                "chat and private-message routes must expose exactly one first accepted decision");
    }

    private static void unrelatedPlayersAreNotGloballySerialized() throws Exception {
        final UUID firstPlayer = new UUID(0L, 1L);
        final UUID secondPlayer = new UUID(0L, 2L);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch entered = new CountDownLatch(2);
        final CountDownLatch release = new CountDownLatch(1);
        final Thread first = guardedWorker(firstPlayer, start, entered, release);
        final Thread second = guardedWorker(secondPlayer, start, entered, release);
        start.countDown();
        check(entered.await(2L, TimeUnit.SECONDS),
                "independent player spam decisions must enter separate stripes concurrently");
        release.countDown();
        first.join(2_000L);
        second.join(2_000L);
        check(!first.isAlive() && !second.isAlive(), "striped spam workers must terminate");
    }

    private static Thread guardedWorker(final UUID playerId, final CountDownLatch start,
                                        final CountDownLatch entered, final CountDownLatch release) {
        return Thread.ofPlatform().start(() -> {
            await(start);
            ModerationSpamGuard.evaluate(playerId, () -> {
                entered.countDown();
                await(release);
                return Boolean.FALSE;
            });
        });
    }

    private static void visibilityRoutesRespectViewerState() throws Exception {
        final String chat = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ChatModerationListener.java"));
        final String privateMessage = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/PrivateMessageCommand.java"));
        final String moderationGui = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/gui/ModerationGUI.java"));
        final String moderationGuiListener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ModerationGUIListener.java"));
        final String moderationGuiCommand = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/ModerationGuiCommand.java"));
        final String invseeCommand = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/InvseeCommand.java"));

        check(chat.contains("ModerationSpamGuard.evaluate(senderId")
                        && privateMessage.contains("ModerationSpamGuard.evaluate(senderId"),
                "public chat and private messages must share the same per-player spam guard");
        check(privateMessage.contains("!sender.canSee(recipient)"),
                "private messages and /reply must treat a viewer-hidden recipient as offline");
        check(privateMessage.contains("sender.canSee(target)"),
                "private-message completion must not disclose viewer-hidden players");
        check(moderationGui.contains("viewer.canSee(target)")
                        && moderationGuiCommand.contains("viewer.canSee(target)")
                        && moderationGuiListener.contains("!viewer.canSee(target)"),
                "moderation GUI list, click, direct open and completion must preserve viewer visibility");
        check(invseeCommand.contains("!viewer.canSee(target)")
                        && invseeCommand.contains("viewer.canSee(target)"),
                "invsee direct open and completion must not grant implicit vanish visibility");
    }

    private static void schedulerTaskAndRetirementHaveOneWinner() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            final AtomicReference<Runnable> task = new AtomicReference<>();
            final AtomicReference<Runnable> retired = new AtomicReference<>();
            final AtomicInteger taskRuns = new AtomicInteger();
            final AtomicInteger fallbackRuns = new AtomicInteger();
            final boolean submitted = EntityTaskSubmission.submit((scheduled, rejection) -> {
                task.set(scheduled);
                retired.set(rejection);
                return new Object();
            }, taskRuns::incrementAndGet, fallbackRuns::incrementAndGet);
            check(submitted, "non-null scheduler handle should be accepted before either callback");

            final CountDownLatch start = new CountDownLatch(1);
            final Thread taskThread = Thread.ofPlatform().start(() -> {
                await(start);
                task.get().run();
            });
            final Thread retiredThread = Thread.ofPlatform().start(() -> {
                await(start);
                retired.get().run();
            });
            start.countDown();
            taskThread.join(2_000L);
            retiredThread.join(2_000L);
            check(taskRuns.get() + fallbackRuns.get() == 1,
                    "task and retirement callback must have exactly one winner");
        }
    }

    private static void schedulerSubmitFailureAndNullUseOneFallback() {
        final AtomicInteger thrownFallback = new AtomicInteger();
        check(!EntityTaskSubmission.submit((task, retired) -> {
                    throw new IllegalStateException("submit rejected");
                }, () -> { throw new AssertionError("task must not run"); }, thrownFallback::incrementAndGet),
                "thrown scheduler submit must be reported as rejected");
        check(thrownFallback.get() == 1, "thrown submit must run fallback exactly once");

        final AtomicInteger nullFallback = new AtomicInteger();
        check(!EntityTaskSubmission.submit((task, retired) -> null,
                        () -> { throw new AssertionError("task must not run"); }, nullFallback::incrementAndGet),
                "null scheduler handle must be reported as rejected");
        check(nullFallback.get() == 1, "null submit must run fallback exactly once");

        final AtomicInteger synchronousRetirement = new AtomicInteger();
        check(!EntityTaskSubmission.submit((task, retired) -> {
                    retired.run();
                    return new Object();
                }, () -> { throw new AssertionError("retired task must not run"); },
                synchronousRetirement::incrementAndGet),
                "retirement before submit returns must be reported as rejected");
        check(synchronousRetirement.get() == 1,
                "synchronous retirement plus a non-null handle must still run one fallback");
    }

    private static void repeatingTaskRetirementBeforePublicationDoesNotLeak() {
        final AtomicInteger cancelled = new AtomicInteger();
        final Object handle = new Object();
        final TaskLease<Object> lease = new TaskLease<>(ignored -> cancelled.incrementAndGet());
        check(lease.retire(), "first retirement must win");
        check(!lease.publish(handle), "a handle returned after retirement must not become published");
        check(cancelled.get() == 1, "late repeating-task handle must be cancelled exactly once");
        check(lease.isRetired() && !lease.isPublished(), "retired lease must expose no active handle");

        final AtomicInteger normalCancelled = new AtomicInteger();
        final TaskLease<Object> normal = new TaskLease<>(ignored -> normalCancelled.incrementAndGet());
        check(normal.publish(new Object()), "normal repeating task handle must publish");
        check(normal.retire(), "published task must retire");
        check(!normal.retire(), "second retirement must be idempotent");
        check(normalCancelled.get() == 1, "published task must be cancelled once");
    }

    private static void replyPartnersAreFencedByJoinGeneration() {
        final ReplyPartnerRegistry registry = new ReplyPartnerRegistry();
        final UUID firstId = new UUID(0L, 11L);
        final UUID secondId = new UUID(0L, 12L);
        final ReplyPartnerRegistry.Session first = registry.openSession(firstId);
        final ReplyPartnerRegistry.Session second = registry.openSession(secondId);
        check(registry.linkIfCurrent(first, second), "same-generation delivered PM must establish reply link");
        check(registry.partner(firstId).orElseThrow().equals(secondId)
                        && registry.partner(secondId).orElseThrow().equals(firstId),
                "reply link must be bidirectional");

        registry.closeSession(firstId);
        check(registry.partner(firstId).isEmpty() && registry.partner(secondId).isEmpty(),
                "quit cleanup must remove both directions");

        final ReplyPartnerRegistry.Session reconnected = registry.openSession(firstId);
        check(!registry.linkIfCurrent(first, second),
                "callback carrying a pre-quit generation must not write stale reply state");
        check(registry.partner(firstId).isEmpty() && registry.partner(secondId).isEmpty(),
                "stale callback rejection must leave both sides unlinked");
        check(registry.linkIfCurrent(reconnected, second),
                "the current post-reconnect generation may establish a new delivered link");
        registry.closeSession(secondId);
        check(registry.partner(firstId).isEmpty() && registry.partner(secondId).isEmpty(),
                "recipient quit must atomically remove the reverse link");
    }

    private static void escrowQueueSurvivesSnapshotAndClaimsExactlyOnce() throws Exception {
        final UUID playerId = new UUID(0L, 21L);
        final InventoryEscrowQueue<String> before = new InventoryEscrowQueue<>(String::new);
        before.add(playerId, "inserted");
        before.add(playerId, "displaced");
        final Map<UUID, List<String>> snapshot = before.snapshot();

        final InventoryEscrowQueue<String> after = new InventoryEscrowQueue<>(String::new);
        after.replace(snapshot);
        check(after.itemCount(playerId) == 2, "restart snapshot must retain every pending return");

        final AtomicInteger winners = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> claimers = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            claimers.add(Thread.ofPlatform().start(() -> {
                await(start);
                if (after.claimMatching(playerId, "inserted"::equals) != null) {
                    winners.incrementAndGet();
                }
            }));
        }
        start.countDown();
        for (final Thread claimer : claimers) {
            claimer.join(2_000L);
        }
        check(winners.get() == 1, "one persisted item must have one successful return claimant");
        check(after.itemCount(playerId) == 1, "successful claim must delete only its durable record");
        check("displaced".equals(after.claimFirst(playerId)), "remaining item must stay claimable");
        check(after.snapshot().isEmpty(), "successful returns must leave no restart record");

        after.restoreFirst(playerId, "retry");
        check("retry".equals(after.claimFirst(playerId)),
                "failed inventory return must be restorable at the front of the durable queue");
    }

    private static void targetCompletionIsPublishedOnlyAfterReturnQueue() {
        final UUID playerId = new UUID(0L, 25L);
        final InventoryEscrowGate gate = new InventoryEscrowGate();
        final InventoryEscrowQueue<String> queue = new InventoryEscrowQueue<>(String::new);
        check(gate.claimTarget(), "target callback must own the committed write");
        queue.add(playerId, "displaced");
        gate.completeTargetWrite();
        check(gate.state() == InventoryEscrowGate.State.COMPLETE
                        && queue.snapshot().get(playerId).equals(List.of("displaced")),
                "COMPLETE must never be observable before the return queue contains the displaced item");
    }

    private static void schedulerRejectionLeavesPendingReturnForShutdownSnapshot() {
        final UUID playerId = new UUID(0L, 31L);
        final InventoryEscrowQueue<String> queue = new InventoryEscrowQueue<>(String::new);
        final InventoryTransferBarrier barrier = new InventoryTransferBarrier();
        check(barrier.reserve(), "transfer must be admitted before shutdown");
        final boolean submitted = EntityTaskSubmission.submit((task, retired) -> null,
                () -> { throw new AssertionError("rejected task must not run"); }, () -> {
                    queue.add(playerId, "cursor-stack");
                    barrier.release();
                });
        check(!submitted, "null scheduler submit must reject the transfer callback");
        barrier.close();
        check(barrier.awaitDrained(1_000L), "shutdown must drain the rejected callback fallback");
        check(queue.snapshot().get(playerId).equals(List.of("cursor-stack")),
                "disable snapshot must retain the item after scheduler rejection");
    }

    private static void escrowSchemaRejectsCorruptAndPartialState() {
        final UUID playerId = UUID.fromString("abcdefab-cdef-abcd-efab-cdefabcdefab");
        final Map<String, Object> valid = Map.of(playerId.toString(), List.of("one", "two"));
        final List<InvseeEscrowSchema.Entry> decoded = InvseeEscrowSchema.validate(
                Set.of("schema-version", "returns"), 1, valid, 10, 10);
        check(decoded.size() == 1 && decoded.getFirst().payloads().size() == 2,
                "valid restart escrow state must decode without dropping payloads");

        expectFailure(() -> InvseeEscrowSchema.validate(Set.of("schema-version", "returns"), 1,
                Map.of("not-a-uuid", List.of("item")), 10, 10), "corrupt UUID");
        expectFailure(() -> InvseeEscrowSchema.validate(Set.of("schema-version", "returns"), 1,
                Map.of(playerId.toString(), List.of()), 10, 10), "empty payload list");
        final List<Object> withNull = new ArrayList<>();
        withNull.add(null);
        expectFailure(() -> InvseeEscrowSchema.validate(Set.of("schema-version", "returns"), 1,
                Map.of(playerId.toString(), withNull), 10, 10), "null payload");
        expectFailure(() -> InvseeEscrowSchema.validate(Set.of("schema-version", "returns"), 2,
                valid, 10, 10), "unsupported schema");
        expectFailure(() -> InvseeEscrowSchema.validate(Set.of("schema-version", "returns", "unknown"), 1,
                valid, 10, 10), "unknown root key");
        expectFailure(() -> InvseeEscrowSchema.validate(Set.of("schema-version", "returns"),
                BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE), valid, 10, 10),
                "schema count overflow");
        expectFailure(() -> InvseeEscrowSchema.validate(Set.of("schema-version", "returns"), 1,
                valid, 10, 1), "item count overflow");

        final Map<String, Object> duplicate = new LinkedHashMap<>();
        duplicate.put(playerId.toString().toLowerCase(java.util.Locale.ROOT), List.of("one"));
        duplicate.put(playerId.toString().toUpperCase(java.util.Locale.ROOT), List.of("two"));
        expectFailure(() -> InvseeEscrowSchema.validate(Set.of("schema-version", "returns"), 1,
                duplicate, 10, 10), "duplicate normalized UUID");
    }

    private static void expectFailure(final Runnable action, final String message) {
        try {
            action.run();
        } catch (final RuntimeException expected) {
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new AssertionError("test latch timed out");
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test interrupted", interrupted);
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
