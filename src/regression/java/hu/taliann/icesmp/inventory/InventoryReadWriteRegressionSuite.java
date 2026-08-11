package hu.taliann.icesmp.inventory;

import hu.taliann.icesmp.moderation.InventoryWriteLock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free writer-lock tests plus source contracts for both inventory GUIs. */
public final class InventoryReadWriteRegressionSuite {

    private InventoryReadWriteRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        exactlyOneWriterPerTarget();
        writerReleaseAllowsNextEditor();
        oneViewerOwnsAtMostOneLease();
        staleCloseCannotReleaseNewerLease();
        concurrentAcquisitionHasOneWinner();
        commandIsAutomaticAndSingleArgument();
        moderationGuiUsesTheSameAutomaticCommand();
        invseeLifecycleReleasesLeases();
        donationGuiHasOneWayInputZone();
        donationManagerOwnsExactSourceAmounts();
        System.out.println("Inventory read/write regression suite passed.");
    }

    private static void exactlyOneWriterPerTarget() {
        final InventoryWriteLock lock = new InventoryWriteLock();
        final UUID target = UUID.randomUUID();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        check(lock.acquire(first, target), "first viewer must receive write mode");
        check(!lock.acquire(second, target), "second simultaneous viewer must be read-only");
        check(lock.holds(first, target), "first writer lease must remain authoritative");
        check(lock.activeWriters() == 1, "one target must expose exactly one writer");
    }

    private static void writerReleaseAllowsNextEditor() {
        final InventoryWriteLock lock = new InventoryWriteLock();
        final UUID target = UUID.randomUUID();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        check(lock.acquire(first, target), "first acquire failed");
        lock.releaseViewer(first);
        check(lock.acquire(second, target), "closing writer must allow the next editor");
        lock.releaseTarget(target);
        check(lock.activeWriters() == 0, "target disconnect must release its writer");
    }

    private static void oneViewerOwnsAtMostOneLease() {
        final InventoryWriteLock lock = new InventoryWriteLock();
        final UUID viewer = UUID.randomUUID();
        final UUID firstTarget = UUID.randomUUID();
        final UUID secondTarget = UUID.randomUUID();
        check(lock.acquire(viewer, firstTarget), "first target acquire failed");
        check(lock.acquire(viewer, secondTarget), "second target acquire failed");
        check(!lock.holds(viewer, firstTarget) && lock.holds(viewer, secondTarget),
                "opening another target must release the previous writer lease");
    }

    private static void staleCloseCannotReleaseNewerLease() {
        final InventoryWriteLock lock = new InventoryWriteLock();
        final UUID viewer = UUID.randomUUID();
        final UUID oldTarget = UUID.randomUUID();
        final UUID newTarget = UUID.randomUUID();
        check(lock.acquire(viewer, oldTarget), "old target acquire failed");
        check(lock.acquire(viewer, newTarget), "new target acquire failed");
        lock.release(viewer, oldTarget);
        check(lock.holds(viewer, newTarget),
                "stale close callback must not release a newer writer lease");
    }

    private static void concurrentAcquisitionHasOneWinner() throws Exception {
        final InventoryWriteLock lock = new InventoryWriteLock();
        final UUID target = UUID.randomUUID();
        final AtomicInteger winners = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            final UUID viewer = UUID.randomUUID();
            threads.add(Thread.ofPlatform().start(() -> {
                await(start);
                if (lock.acquire(viewer, target)) {
                    winners.incrementAndGet();
                }
            }));
        }
        start.countDown();
        for (final Thread thread : threads) {
            thread.join(2_000L);
        }
        check(winners.get() == 1, "concurrent writer acquisition must have exactly one winner");
    }

    private static void commandIsAutomaticAndSingleArgument() throws Exception {
        final String source = read("src/main/java/hu/taliann/icesmp/commands/InvseeCommand.java");
        check(source.contains("args.length != 1")
                        && source.contains("InvseeWriteCoordinator.chooseMode")
                        && source.contains("InvseeHolder.View.MAIN")
                        && source.contains("invsee-writer-busy"),
                "/invsee must automatically choose write/read mode");
        check(!source.contains("[read|edit]")
                        && !source.contains("List.of(\"read\", \"edit\")"),
                "manual invsee mode arguments must not return");
    }

    private static void moderationGuiUsesTheSameAutomaticCommand() throws Exception {
        final String gui = read("src/main/java/hu/taliann/icesmp/gui/ModerationGUI.java");
        final String listener = read("src/main/java/hu/taliann/icesmp/listeners/ModerationGUIListener.java");
        final String messages = read("src/main/resources/messages/moderation.yml");
        check(gui.contains("putInvsee")
                        && gui.contains("/invsee — automatikus WRITE/READ")
                        && listener.contains("case 22 -> viewer.performCommand(\"invsee \" + name)")
                        && !listener.contains("invsee \" + name + \" read")
                        && !listener.contains("invsee \" + name + \" edit"),
                "moderation GUI must route through the one automatic invsee command");
        check(messages.contains("invsee-usage: '&cHasználat: /invsee <online játékos>'")
                        && messages.contains("invsee-writer-busy")
                        && !messages.contains("[read|edit]"),
                "packaged invsee messages still expose removed mode arguments");
    }

    private static void invseeLifecycleReleasesLeases() throws Exception {
        final String coordinator = read("src/main/java/hu/taliann/icesmp/moderation/InvseeWriteCoordinator.java");
        final String listener = read("src/main/java/hu/taliann/icesmp/listeners/InvseeGUIListener.java");
        check(coordinator.contains("releaseAfterClose")
                        && coordinator.contains("verifyOpened")
                        && coordinator.contains("LOCK.release(viewerId, targetId)")
                        && coordinator.contains("canWrite")
                        && coordinator.contains("releasePlayer"),
                "writer lease lacks close/open-failure lifecycle fencing");
        check(listener.contains("releaseAfterClose")
                        && listener.contains("PlayerQuitEvent")
                        && listener.contains("PluginDisableEvent"),
                "invsee listener does not release writer leases on lifecycle edges");
    }

    private static void donationGuiHasOneWayInputZone() throws Exception {
        final String gui = read("src/main/java/hu/taliann/icesmp/gui/DonationChestGUI.java");
        final String listener = read("src/main/java/hu/taliann/icesmp/listeners/DonationChestListener.java");
        check(gui.contains("DEPOSIT_START = 0")
                        && gui.contains("DEPOSIT_END = 8")
                        && gui.contains("CONTENT_START = 9")
                        && gui.contains("isDepositSlot"),
                "donation chest must reserve a visible input row");
        check(listener.contains("MOVE_TO_OTHER_INVENTORY")
                        && listener.contains("ClickType.NUMBER_KEY")
                        && listener.contains("ClickType.SWAP_OFFHAND")
                        && listener.contains("InventoryDragEvent")
                        && listener.contains("getOldCursor")
                        && listener.contains("getNewItems")
                        && listener.contains("submitDonation")
                        && listener.contains("player.getScheduler().run")
                        && !listener.contains("event.setCursor"),
                "donation input must cover shift, hotbar, offhand and drag gestures");
        check(listener.contains("event.setCancelled(true)"),
                "custom top inventory mutations must remain cancelled");
    }

    private static void donationManagerOwnsExactSourceAmounts() throws Exception {
        final String source = read("src/main/java/hu/taliann/icesmp/managers/DonationChestManager.java");
        check(source.contains("donateCursor")
                        && source.contains("donateInventorySlot")
                        && source.contains("donateOffHand")
                        && source.contains("sameItem(current, expected)")
                        && source.contains("requestedAmount > current.getAmount()")
                        && source.contains("entries.remove(id, entry)")
                        && source.contains("source.writer().write(current)"),
                "donation manager lost exact source ownership or rollback");
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
