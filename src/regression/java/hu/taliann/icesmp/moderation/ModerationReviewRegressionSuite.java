package hu.taliann.icesmp.moderation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Review regressions layered on top of the original native moderation suite. */
public final class ModerationReviewRegressionSuite {
    private ModerationReviewRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        ModerationRegressionSuite.main(args);
        spamDecisionsAreSerializedAcrossAsyncRoutes();
        visibilityRoutesRespectViewerState();
        System.out.println("Moderation review regression suite passed.");
    }

    private static void spamDecisionsAreSerializedAcrossAsyncRoutes() throws Exception {
        final Object sharedManager = new Object();
        final int[] intentionallyNonAtomicLastDecision = {0};
        final AtomicInteger accepted = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            workers.add(Thread.ofPlatform().start(() -> {
                await(start);
                final boolean blocked = ModerationSpamGuard.evaluate(sharedManager, () -> {
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

    private static void visibilityRoutesRespectViewerState() throws Exception {
        final String chat = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ChatModerationListener.java"));
        final String privateMessage = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/PrivateMessageCommand.java"));
        final String moderationGui = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/gui/ModerationGUI.java"));
        final String moderationGuiCommand = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/ModerationGuiCommand.java"));

        check(chat.contains("ModerationSpamGuard.evaluate(moderationManager")
                        && privateMessage.contains("ModerationSpamGuard.evaluate(manager"),
                "public chat and private messages must share the same serialized spam decision lock");
        check(privateMessage.contains("!sender.canSee(recipient)"),
                "private messages and /reply must treat a viewer-hidden recipient as offline");
        check(privateMessage.contains("sender.canSee(target)"),
                "private-message completion must not disclose viewer-hidden players");
        check(moderationGui.contains("viewer.canSee(target)")
                        && moderationGuiCommand.contains("viewer.canSee(target)"),
                "moderation GUI list, direct open and completion must preserve viewer visibility");
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
