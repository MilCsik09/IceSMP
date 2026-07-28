package hu.taliann.icesmp.moderation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free moderation ledger, expiry and inventory-escrow regressions. */
public final class ModerationRegressionSuite {
    private static final UUID TARGET = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private ModerationRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        warningAndKickRemainInHistory();
        muteSurvivesSnapshotRestart();
        temporaryMuteExpiresAndCanBeReissued();
        temporaryBanBlocksUntilExpiry();
        unmuteAndUnbanCreateLinkedAuditRecords();
        duplicateAndContradictoryStateFailsClosed();
        orphanAndMismatchedRevocationStateFailsClosed();
        partialAndInvalidRecordsAreRejected();
        historyIsNewestFirst();
        escrowInsertHasExactlyOneOwner();
        escrowDisplacedItemHasExactlyOneReturnOwner();
        locationRejectsNonFiniteCoordinates();
        durationParserRejectsOverflowWithoutTurningItIntoAReason();
        sourceInvariants();
        System.out.println("Moderation regression suite passed.");
    }

    private static void warningAndKickRemainInHistory() {
        final PunishmentLedger ledger = new PunishmentLedger();
        ledger.issue(PunishmentType.WARNING, TARGET, "Target", ADMIN, "Admin", "warn", 1_000L, null);
        ledger.issue(PunishmentType.KICK, TARGET, "Target", ADMIN, "Admin", "kick", 2_000L, null);
        check(ledger.history(TARGET).size() == 2, "warning and kick must remain in full history");
        check(ledger.activeAll(3_000L).isEmpty(), "warning and kick must never become active restrictions");
    }

    private static void muteSurvivesSnapshotRestart() {
        final PunishmentLedger before = new PunishmentLedger();
        before.issue(PunishmentType.MUTE, TARGET, "Target", ADMIN, "Admin", "reason", 10_000L, null);
        final PunishmentLedger after = new PunishmentLedger(before.snapshot());
        check(after.active(TARGET, PunishmentType.Family.MUTE, 11_000L).isPresent(),
                "permanent mute must survive a strict snapshot restart");
    }

    private static void temporaryMuteExpiresAndCanBeReissued() {
        final PunishmentLedger ledger = new PunishmentLedger();
        final PunishmentRecord first = ledger.issue(PunishmentType.TEMPORARY_MUTE, TARGET, "Target", ADMIN,
                "Admin", "first", 1_000L, 2_000L);
        check(ledger.active(TARGET, PunishmentType.Family.MUTE, 1_999L).isPresent(),
                "temporary mute must be active before expiry");
        check(ledger.expireDue(2_000L) == 1, "exactly one due mute must expire");
        check(ledger.active(TARGET, PunishmentType.Family.MUTE, 2_000L).isEmpty(),
                "expired mute must no longer be active");
        check(ledger.history(TARGET).stream().filter(r -> r.id().equals(first.id())).findFirst().orElseThrow()
                .state() == PunishmentState.EXPIRED, "expiry must be materialized in history");
        ledger.issue(PunishmentType.TEMPORARY_MUTE, TARGET, "Target", ADMIN, "Admin", "second",
                2_001L, 3_000L);
        check(ledger.active(TARGET, PunishmentType.Family.MUTE, 2_500L).isPresent(),
                "a replacement mute must be issuable after expiry");
    }

    private static void temporaryBanBlocksUntilExpiry() {
        final PunishmentLedger ledger = new PunishmentLedger();
        ledger.issue(PunishmentType.TEMPORARY_BAN, TARGET, "Target", ADMIN, "Admin", "ban",
                10_000L, 20_000L);
        check(ledger.active(TARGET, PunishmentType.Family.BAN, 19_999L).isPresent(),
                "tempban must produce the login-block read model before expiry");
        check(ledger.active(TARGET, PunishmentType.Family.BAN, 20_000L).isEmpty(),
                "tempban must not block at or after expiry even before the maintenance tick");
    }

    private static void unmuteAndUnbanCreateLinkedAuditRecords() {
        final PunishmentLedger ledger = new PunishmentLedger();
        final PunishmentRecord mute = ledger.issue(PunishmentType.MUTE, TARGET, "Target", ADMIN,
                "Admin", "mute", 1_000L, null);
        final PunishmentLedger.RevocationResult unmute = ledger.revoke(TARGET, PunishmentType.Family.MUTE,
                ADMIN, "Admin", "appeal", 2_000L);
        check(unmute.revoked().state() == PunishmentState.REVOKED, "unmute must revoke the active mute");
        check(unmute.action().type() == PunishmentType.UNMUTE
                        && mute.id().equals(unmute.action().linkedPunishmentId()),
                "unmute history row must link the revoked mute");

        final PunishmentRecord ban = ledger.issue(PunishmentType.BAN, TARGET, "Target", ADMIN,
                "Admin", "ban", 3_000L, null);
        final PunishmentLedger.RevocationResult unban = ledger.revoke(TARGET, PunishmentType.Family.BAN,
                ADMIN, "Admin", "appeal", 4_000L);
        check(unban.action().type() == PunishmentType.UNBAN
                        && ban.id().equals(unban.action().linkedPunishmentId()),
                "unban history row must link the revoked ban");
        new PunishmentLedger(ledger.snapshot()); // strict restart validation of both links
    }

    private static void duplicateAndContradictoryStateFailsClosed() {
        final PunishmentRecord active = new PunishmentRecord(UUID.randomUUID(), PunishmentType.MUTE,
                TARGET, "Target", ADMIN, "Admin", "one", 1_000L, null, PunishmentState.ACTIVE,
                null, null, null, "", false, null);
        expectThrows(IllegalArgumentException.class, () -> new PunishmentLedger(List.of(active, active)));
        final PunishmentRecord conflicting = new PunishmentRecord(UUID.randomUUID(), PunishmentType.TEMPORARY_MUTE,
                TARGET, "Target", ADMIN, "Admin", "two", 2_000L, 5_000L, PunishmentState.ACTIVE,
                null, null, null, "", false, null);
        expectThrows(IllegalArgumentException.class, () -> new PunishmentLedger(List.of(active, conflicting)));
    }


    private static void orphanAndMismatchedRevocationStateFailsClosed() {
        final PunishmentRecord active = new PunishmentRecord(UUID.randomUUID(), PunishmentType.MUTE,
                TARGET, "Target", ADMIN, "Admin", "mute", 1_000L, null, PunishmentState.ACTIVE,
                null, null, null, "", false, null);
        final PunishmentRecord revoked = active.revoked(ADMIN, "Admin", 2_000L, "appeal");
        expectThrows(IllegalArgumentException.class, () -> new PunishmentLedger(List.of(revoked)));

        final PunishmentRecord action = new PunishmentRecord(UUID.randomUUID(), PunishmentType.UNMUTE,
                TARGET, "Target", ADMIN, "Admin", "different reason", 2_000L, null,
                PunishmentState.RECORDED, null, null, null, "", false, active.id());
        expectThrows(IllegalArgumentException.class, () -> new PunishmentLedger(List.of(revoked, action)));

        expectThrows(IllegalArgumentException.class, () -> new PunishmentRecord(UUID.randomUUID(),
                PunishmentType.TEMPORARY_MUTE, TARGET, "Target", ADMIN, "Admin", "mute", 1_000L,
                2_000L, PunishmentState.REVOKED, ADMIN, "Admin", 2_000L,
                "too late", false, null));
        expectThrows(IllegalArgumentException.class, () -> new PunishmentRecord(UUID.randomUUID(),
                PunishmentType.TEMPORARY_BAN, TARGET, "Target", ADMIN, "Admin", "ban", 1_000L,
                3_000L, PunishmentState.EXPIRED, null, "SYSTEM", 2_999L,
                "early", true, null));
    }

    private static void partialAndInvalidRecordsAreRejected() {
        expectThrows(NullPointerException.class, () -> new PunishmentRecord(null, PunishmentType.WARNING,
                TARGET, "Target", ADMIN, "Admin", "", 1_000L, null, PunishmentState.RECORDED,
                null, null, null, "", false, null));
        expectThrows(IllegalArgumentException.class, () -> new PunishmentRecord(UUID.randomUUID(),
                PunishmentType.TEMPORARY_BAN, TARGET, "Target", ADMIN, "Admin", "", 1_000L,
                null, PunishmentState.ACTIVE, null, null, null, "", false, null));
        expectThrows(IllegalArgumentException.class, () -> new PunishmentRecord(UUID.randomUUID(),
                PunishmentType.WARNING, TARGET, "", ADMIN, "Admin", "", 1_000L, null,
                PunishmentState.RECORDED, null, null, null, "", false, null));
    }

    private static void historyIsNewestFirst() {
        final PunishmentLedger ledger = new PunishmentLedger();
        ledger.issue(PunishmentType.WARNING, TARGET, "Target", ADMIN, "Admin", "old", 1_000L, null);
        ledger.issue(PunishmentType.KICK, TARGET, "Target", ADMIN, "Admin", "new", 2_000L, null);
        check("new".equals(ledger.history(TARGET).getFirst().reason()), "history must be newest first");
    }

    private static void escrowInsertHasExactlyOneOwner() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            final InventoryEscrowGate gate = new InventoryEscrowGate();
            final AtomicInteger winners = new AtomicInteger();
            final CountDownLatch start = new CountDownLatch(1);
            final Thread target = Thread.ofPlatform().start(() -> {
                await(start);
                if (gate.claimTarget()) winners.incrementAndGet();
            });
            final Thread returner = Thread.ofPlatform().start(() -> {
                await(start);
                if (gate.claimInsertedReturn()) winners.incrementAndGet();
            });
            start.countDown();
            target.join(2_000L);
            returner.join(2_000L);
            check(winners.get() == 1, "inserted stack must have exactly one owner");
        }
    }

    private static void escrowDisplacedItemHasExactlyOneReturnOwner() throws Exception {
        final InventoryEscrowGate gate = new InventoryEscrowGate();
        check(gate.claimTarget(), "target must claim initial ownership");
        gate.completeTargetWrite();
        final int workers = 8;
        final AtomicInteger winners = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            threads.add(Thread.ofPlatform().start(() -> {
                await(start);
                if (gate.claimDisplacedReturn()) winners.incrementAndGet();
            }));
        }
        start.countDown();
        for (final Thread thread : threads) thread.join(2_000L);
        check(winners.get() == 1, "displaced stack must be returned by exactly one cleanup path");
    }

    private static void locationRejectsNonFiniteCoordinates() {
        expectThrows(IllegalArgumentException.class, () -> new LastKnownLocation(TARGET, "Target",
                UUID.randomUUID(), "world", Double.NaN, 64, 0, 0, 0, 1_000L));
        expectThrows(IllegalArgumentException.class, () -> new LastKnownLocation(TARGET, "Target",
                UUID.randomUUID(), "world", 0, 64, Double.POSITIVE_INFINITY, 0, 0, 1_000L));
    }

    private static void durationParserRejectsOverflowWithoutTurningItIntoAReason() {
        check(ModerationDuration.parseMillis("30m") == 1_800_000L,
                "valid minute duration must parse");
        check(ModerationDuration.parseMillis("7d") == 604_800_000L,
                "valid day duration must parse");
        check(ModerationDuration.parseMillis("végleges") == 0L,
                "permanent duration token must parse");
        check(ModerationDuration.parseMillis("999999d") == null,
                "over-limit duration must be rejected");
        check(ModerationDuration.looksLikeToken("999999d"),
                "an invalid-looking duration must not silently become the punishment reason");
        check(!ModerationDuration.looksLikeToken("csúnya beszéd"),
                "ordinary reason text must remain eligible for escalation mute syntax");
    }

    /** Supplementary integration guards; behavior is covered above rather than only by source search. */
    private static void sourceInvariants() throws Exception {
        final String manager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/ModerationManager.java"));
        final String invsee = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/InvseeManager.java"));
        final String core = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"));
        final String privateMessage = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/PrivateMessageCommand.java"));
        final String moderationGui = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ModerationGUIListener.java"));
        final String vanishCommand = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/VanishCommand.java"));
        final String moderationMessages = Files.readString(Path.of(
                "src/main/resources/messages/moderation.yml"));
        check(manager.contains("implements PersistentStore, PlayerStateCleanup"),
                "moderation must use the shared persistence and cleanup lifecycle");
        check(manager.contains("YamlStore.saveAtomic") && manager.contains("restoreLocked(before)"),
                "authoritative mutation must use atomic save plus rollback");
        check(core.contains("moderationManager") && core.contains("storeCoordinator"),
                "moderation store must be wired through the shared coordinator");
        check(invsee.contains("target.getScheduler().run") && invsee.contains("viewer.getScheduler().run"),
                "live inventory must hop to both entity owners");
        check(invsee.contains("InventoryEscrowGate"), "live inventory must use the tested single-claim gate");
        check(manager.contains("storageFile.isFile() && yaml.getKeys(false).isEmpty()"),
                "an existing empty authoritative moderation file must fail closed");
        check(manager.contains("prepareShutdown") && manager.contains("inFlightMutations")
                        && core.contains("moderationExpiryTask.cancel()")
                        && core.indexOf("moderationManager.prepareShutdown")
                        < core.indexOf("storeCoordinator.beginShutdown"),
                "shutdown must cancel expiry and drain durable moderation mutations before final save");
        check(privateMessage.contains("final UUID senderId = sender.getUniqueId()")
                        && privateMessage.contains("manager.logChatEvent(\"PM_DELIVERED\", senderId, senderName"),
                "private-message delivery must carry scalar sender identity across entity schedulers");
        check(moderationGui.contains("permissionForSlot(slot)")
                        && moderationGui.contains("!viewer.hasPermission(requiredPermission)"),
                "GUI actions must re-check the same permission even when a filler slot is clicked");
        check(vanishCommand.contains("enabled ? \"bekapcsolva\" : \"kikapcsolva\""),
                "vanish self feedback must supply the configured format argument");
        check(moderationMessages.contains("disconnect: '&c%s\\n&7Ok: &f%s'"),
                "disconnect message must preserve both action type and reason");
        for (final String forbidden : List.of("Bukkit.getScheduler()", "BukkitRunnable", "new Thread(", "new Timer(")) {
            check(!manager.contains(forbidden) && !invsee.contains(forbidden),
                    "moderation code contains forbidden scheduler primitive: " + forbidden);
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("test latch timed out");
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test interrupted", interrupted);
        }
    }

    private static <T extends Throwable> T expectThrows(final Class<T> type, final ThrowingRunnable action) {
        try {
            action.run();
        } catch (final Throwable thrown) {
            if (type.isInstance(thrown)) return type.cast(thrown);
            throw new AssertionError("Expected " + type.getName() + " but got " + thrown, thrown);
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Throwable; }
}
