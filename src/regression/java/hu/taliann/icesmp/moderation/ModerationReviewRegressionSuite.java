package hu.taliann.icesmp.moderation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Review regressions layered on top of the original native moderation suite. */
public final class ModerationReviewRegressionSuite {
    private ModerationReviewRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        ModerationRegressionSuite.main(args);
        samePlayerSpamDecisionsAreSerializedAcrossAsyncRoutes();
        unrelatedPlayersAreNotGloballySerialized();
        visibilityRoutesRespectViewerState();
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
                        && moderationGuiCommand.contains("viewer.canSee(target)"),
                "moderation GUI list, direct open and completion must preserve viewer visibility");
        check(invseeCommand.contains("!viewer.canSee(target)")
                        && invseeCommand.contains("viewer.canSee(target)"),
                "invsee direct open and completion must not grant implicit vanish visibility");
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
