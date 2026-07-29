package hu.taliann.icesmp.crates;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Behavioural crate lifecycle, validation, scheduler and persistence regressions. */
public final class CrateRegressionSuite {

    private CrateRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        validatesWeightsAmountsAndExactIntegers();
        validatesStrictBooleanAndWorldLists();
        validatesCommandTemplates();
        consumesExactKeysAcrossStacks();
        boundsPartialMassOpen();
        selectsWeightedRewardsDeterministically();
        preparesLedgerWithoutPhantomState();
        resetsAndRestoresStats();
        rejectsLedgerOverflowAndStaleRollback();
        openingFinalizeAndRollbackAreMutuallyExclusive();
        callbackGateHasOneWinner();
        schedulerSubmissionHandlesNullExceptionAndRetirement();
        taskLeaseRejectsRetirementBeforePublish();
        recoveryLedgerIsSingleClaimAndRestartSafe();
        commandBatchClassifiesCompensableAndPartialFailure();
        rewardProgressNeverRefundsAfterItemSideEffect();
        auditWriterSerializesRotationAndAppend();
        decimalFormattingIsThreadSafe();
        verifiesProductionWiringAndFoliaGuards();
        System.out.println("Native crate regression suite passed.");
    }

    private static void validatesWeightsAmountsAndExactIntegers() {
        check(CrateRules.positiveWeight(1.5D) == 1.5D, "positive weight changed");
        for (final Object invalid : List.of(0, -1, Double.NaN, Double.POSITIVE_INFINITY,
                CrateRules.MAX_WEIGHT + 1.0D)) {
            expectThrows(IllegalArgumentException.class, () -> CrateRules.positiveWeight(invalid));
        }

        check(CrateRules.itemAmount(64, 1) == 64, "item amount changed");
        expectThrows(IllegalArgumentException.class, () -> CrateRules.itemAmount(0, 1));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.itemAmount(CrateRules.MAX_REWARD_ITEM_AMOUNT + 1, 1));
        expectThrows(IllegalArgumentException.class, () -> CrateRules.currencyAmount(Double.NaN));
        expectThrows(IllegalArgumentException.class, () -> CrateRules.cooldownMillis(Double.POSITIVE_INFINITY));

        final long aboveDoublePrecision = 9_007_199_254_740_993L;
        check(CrateRules.exactLong(aboveDoublePrecision, 0L, Long.MIN_VALUE, Long.MAX_VALUE,
                "seed") == aboveDoublePrecision, "2^53+1 was rounded");
        check(CrateRules.exactLong(Long.MIN_VALUE, 0L, Long.MIN_VALUE, Long.MAX_VALUE,
                "value") == Long.MIN_VALUE, "Long.MIN_VALUE rejected");
        check(CrateRules.exactLong(Long.MAX_VALUE, 0L, Long.MIN_VALUE, Long.MAX_VALUE,
                "value") == Long.MAX_VALUE, "Long.MAX_VALUE rejected");
        check(CrateRules.exactLong("9223372036854775807", 0L, Long.MIN_VALUE,
                Long.MAX_VALUE, "value") == Long.MAX_VALUE, "exact positive string rejected");
        check(CrateRules.exactLong(new BigDecimal("42"), 0L, 0L, 100L,
                "value") == 42L, "exact BigDecimal integer rejected");
        check(CrateRules.exactLong(null, 17L, 0L, 100L,
                "value") == 17L, "missing integer did not use fallback");

        for (final Object invalid : List.of(
                new BigInteger("9223372036854775808"),
                new BigInteger("-9223372036854775809"),
                new BigDecimal("1.5"), 1.0D, 1.0F, Double.NaN,
                Double.NEGATIVE_INFINITY, "1.5", "NaN", Boolean.TRUE, List.of(1))) {
            expectThrows(IllegalArgumentException.class, () -> CrateRules.exactLong(
                    invalid, 0L, Long.MIN_VALUE, Long.MAX_VALUE, "value"));
        }
    }

    private static void validatesStrictBooleanAndWorldLists() {
        check(CrateRules.strictBoolean(null, true, "enabled"), "missing boolean fallback");
        check(CrateRules.strictBoolean(Boolean.TRUE, false, "enabled"), "true boolean rejected");
        check(!CrateRules.strictBoolean(Boolean.FALSE, true, "enabled"), "false boolean changed");
        for (final Object invalid : List.of("true", 1, 0L, List.of(true), new Object())) {
            expectThrows(IllegalArgumentException.class,
                    () -> CrateRules.strictBoolean(invalid, false, "enabled"));
        }
        check(CrateRules.strictStringList(null, "worlds").isEmpty(), "missing worlds fallback");
        check(CrateRules.strictStringList(List.of("world", "world_nether"), "worlds")
                        .equals(List.of("world", "world_nether")),
                "valid worlds list changed");
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.strictStringList("world", "worlds"));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.strictStringList(List.of("world", 2), "worlds"));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.strictStringList(List.of(""), "worlds"));
    }

    private static void validatesCommandTemplates() {
        final String command = CrateRules.validateCommand(
                "give {player} minecraft:diamond {amount} # {uuid} {crate}");
        check(CrateRules.renderCommand(command, "Alice", "uuid", "rare", 3)
                        .equals("give Alice minecraft:diamond 3 # uuid rare"),
                "documented placeholders rendered incorrectly");
        for (final String invalid : List.of(
                "give {world} minecraft:diamond 1",
                "give {PLAYER} minecraft:diamond 1",
                "give {player minecraft:diamond 1",
                "/give {player} minecraft:diamond 1",
                "say ok\nop @a")) {
            expectThrows(IllegalArgumentException.class, () -> CrateRules.validateCommand(invalid));
        }
    }

    private static void consumesExactKeysAcrossStacks() {
        final List<KeyConsumption.Take> plan = KeyConsumption.plan(List.of(2, 0, 4, 7), 9);
        check(plan.equals(List.of(new KeyConsumption.Take(0, 2),
                        new KeyConsumption.Take(2, 4), new KeyConsumption.Take(3, 3))),
                "multi-stack exact key plan is wrong");
        check(KeyConsumption.plan(List.of(2, 3), 6).isEmpty(),
                "insufficient key plan must not partially consume");
        check(KeyConsumption.plan(List.of(64), 64)
                        .equals(List.of(new KeyConsumption.Take(0, 64))),
                "single-stack exact plan is wrong");
    }

    private static void boundsPartialMassOpen() {
        check(CrateRules.maxOpenable(9, 2, 10, true, 4) == 4,
                "mass-open must respect per-crate maximum");
        check(CrateRules.maxOpenable(7, 3, 10, true, 10) == 2,
                "mass-open must partially complete funded openings");
        check(CrateRules.maxOpenable(100, 2, 10, false, 10) == 1,
                "disabled mass-open must allow exactly one opening");
        check(CrateRules.maxOpenable(1, 2, 10, true, 10) == 0,
                "insufficient keys must yield no opening");
    }

    private static void selectsWeightedRewardsDeterministically() {
        final List<WeightedSelector.Weighted<String>> entries = List.of(
                new WeightedSelector.Weighted<>(1.0D, "a"),
                new WeightedSelector.Weighted<>(3.0D, "b"),
                new WeightedSelector.Weighted<>(6.0D, "c"));
        check(WeightedSelector.select(entries, 0.0D).equals("a"), "lower edge wrong");
        check(WeightedSelector.select(entries, 0.099999D).equals("a"), "first range wrong");
        check(WeightedSelector.select(entries, 0.10D).equals("b"), "second lower edge wrong");
        check(WeightedSelector.select(entries, 0.399999D).equals("b"), "second range wrong");
        check(WeightedSelector.select(entries, 0.40D).equals("c"), "third lower edge wrong");
        check(WeightedSelector.select(entries, Math.nextDown(1.0D)).equals("c"), "upper edge wrong");
        expectThrows(IllegalArgumentException.class, () -> WeightedSelector.select(entries, 1.0D));
    }

    private static void preparesLedgerWithoutPhantomState() {
        final CrateLedger ledger = new CrateLedger();
        final UUID player = UUID.randomUUID();
        final CrateLedger.Mutation mutation = ledger.prepare(player, "Alice", "rare", 2,
                1_000L, 5_000L);
        check(ledger.count(player, "rare") == 0L && ledger.total(player) == 0L,
                "prepare created phantom statistics");
        check(ledger.remainingCooldown(player, "rare", 1_000L) == 0L,
                "prepare created phantom cooldown");
        check(ledger.canApply(mutation), "fresh settlement token not applicable");
        ledger.apply(mutation);
        check(ledger.count(player, "rare") == 2L, "settlement statistic missing");
        check(ledger.remainingCooldown(player, "rare", 3_000L) == 3_000L, "cooldown wrong");
        ledger.rollback(mutation);
        check(ledger.total(player) == 0L, "settlement rollback failed");

        ledger.replace(Map.of(player, new CrateLedger.PlayerSnapshot("Overflow",
                Map.of("rare", 1L), Map.of("rare", Long.MAX_VALUE))));
        check(ledger.remainingCooldown(player, "rare", Long.MIN_VALUE) == Long.MAX_VALUE,
                "cooldown subtraction overflowed");
    }

    private static void resetsAndRestoresStats() {
        final CrateLedger ledger = new CrateLedger();
        final UUID player = UUID.randomUUID();
        ledger.record(player, "Bob", "common", 3, 100L, 0L);
        ledger.record(player, "Bob", "rare", 2, 200L, 10L);
        final CrateLedger.ResetToken one = ledger.reset(player, "rare");
        check(ledger.count(player, "rare") == 0L && ledger.count(player, "common") == 3L,
                "per-crate reset removed wrong data");
        ledger.rollbackReset(one);
        check(ledger.count(player, "rare") == 2L && ledger.count(player, "common") == 3L,
                "per-crate reset rollback failed");
        final Map<UUID, CrateLedger.PlayerSnapshot> snapshot = ledger.snapshot();
        final CrateLedger restored = new CrateLedger();
        restored.replace(snapshot);
        check(restored.total(player) == 5L, "restart snapshot round-trip failed");
    }

    private static void rejectsLedgerOverflowAndStaleRollback() {
        final UUID player = UUID.randomUUID();
        final CrateLedger overflow = new CrateLedger();
        overflow.replace(Map.of(player, new CrateLedger.PlayerSnapshot("Overflow",
                Map.of("rare", Long.MAX_VALUE), Map.of())));
        expectThrows(ArithmeticException.class,
                () -> overflow.prepare(player, "Overflow", "rare", 1, 1L, 0L));

        final CrateLedger cooldownOverflow = new CrateLedger();
        expectThrows(ArithmeticException.class,
                () -> cooldownOverflow.prepare(player, "Alice", "rare", 1, Long.MAX_VALUE, 1L));
        check(cooldownOverflow.total(player) == 0L,
                "failed cooldown computation partially mutated ledger");

        final CrateLedger ledger = new CrateLedger();
        final CrateLedger.Mutation old = ledger.record(player, "Alice", "rare", 1, 1L, 0L);
        ledger.record(player, "Alice", "rare", 1, 2L, 0L);
        expectThrows(IllegalStateException.class, () -> ledger.rollback(old));
    }

    private static void openingFinalizeAndRollbackAreMutuallyExclusive() throws Exception {
        final CrateOpeningLifecycle lifecycle = new CrateOpeningLifecycle();
        check(lifecycle.markPersisted(), "persist transition failed");
        final AtomicInteger grants = new AtomicInteger();
        final AtomicInteger rollbacks = new AtomicInteger();
        final List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            final boolean grant = (index & 1) == 0;
            threads.add(Thread.ofPlatform().start(() -> {
                if (grant ? lifecycle.claimGrant() : lifecycle.rollbackBeforeGrant()) {
                    (grant ? grants : rollbacks).incrementAndGet();
                }
            }));
        }
        for (final Thread thread : threads) {
            thread.join(5_000L);
            check(!thread.isAlive(), "lifecycle race thread did not finish");
        }
        check(grants.get() + rollbacks.get() == 1,
                "grant and rollback did not have one atomic winner");
        if (grants.get() == 1) {
            check(lifecycle.complete(), "grant winner could not complete");
            check(!lifecycle.rollbackBeforeGrant(), "completed opening rolled back");
        } else {
            check(!lifecycle.claimGrant(), "rolled-back opening granted later");
            check(!lifecycle.complete(), "rolled-back opening completed");
        }
    }

    private static void callbackGateHasOneWinner() throws Exception {
        final CrateCallbackGate gate = new CrateCallbackGate();
        final AtomicInteger tasks = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        final List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            final boolean task = (index & 1) == 0;
            threads.add(Thread.ofPlatform().start(() -> {
                if (task) {
                    gate.runTask(tasks::incrementAndGet);
                } else {
                    gate.runRejected(rejected::incrementAndGet);
                }
            }));
        }
        for (final Thread thread : threads) {
            thread.join(5_000L);
        }
        check(tasks.get() + rejected.get() == 1, "scheduler task/fallback ran more than once");
    }

    private static void schedulerSubmissionHandlesNullExceptionAndRetirement() {
        final Plugin plugin = proxy(Plugin.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
        final ScheduledTask handle = proxy(ScheduledTask.class, (proxy, method, args) -> defaultValue(method.getReturnType()));

        final AtomicInteger task = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        final EntityScheduler nullScheduler = proxy(EntityScheduler.class, (proxy, method, args) -> null);
        check(!CrateTaskSubmission.entity(plugin, nullScheduler, task::incrementAndGet,
                        rejected::incrementAndGet), "null entity handle reported success");
        check(task.get() == 0 && rejected.get() == 1, "null entity handle fallback count");

        final EntityScheduler throwing = proxy(EntityScheduler.class, (proxy, method, args) -> {
            throw new IllegalStateException("retired");
        });
        check(!CrateTaskSubmission.entity(plugin, throwing, task::incrementAndGet,
                        rejected::incrementAndGet), "submit exception reported success");
        check(rejected.get() == 2, "submit exception fallback count");

        final EntityScheduler synchronouslyRetired = proxy(EntityScheduler.class, (proxy, method, args) -> {
            if ("run".equals(method.getName())) {
                ((Runnable) args[2]).run();
                return handle;
            }
            return defaultValue(method.getReturnType());
        });
        check(!CrateTaskSubmission.entity(plugin, synchronouslyRetired, task::incrementAndGet,
                        rejected::incrementAndGet), "synchronous retirement reported success");
        check(rejected.get() == 3 && task.get() == 0, "retirement/task gate not single-winner");

        final EntityScheduler accepted = proxy(EntityScheduler.class, (proxy, method, args) -> {
            if ("run".equals(method.getName())) {
                @SuppressWarnings("unchecked")
                final java.util.function.Consumer<ScheduledTask> consumer =
                        (java.util.function.Consumer<ScheduledTask>) args[1];
                consumer.accept(handle);
                return handle;
            }
            return defaultValue(method.getReturnType());
        });
        check(CrateTaskSubmission.entity(plugin, accepted, task::incrementAndGet,
                        rejected::incrementAndGet), "accepted entity task reported rejection");
        check(task.get() == 1 && rejected.get() == 3, "accepted entity task did not run once");
    }

    private static void taskLeaseRejectsRetirementBeforePublish() {
        final AtomicInteger cancellations = new AtomicInteger();
        final ScheduledTask handle = proxy(ScheduledTask.class, (proxy, method, args) -> {
            if ("cancel".equals(method.getName())) {
                cancellations.incrementAndGet();
            }
            return defaultValue(method.getReturnType());
        });
        final CrateTaskLease retiredFirst = new CrateTaskLease();
        retiredFirst.retire();
        check(!retiredFirst.publish(handle), "retired lease accepted a late handle");
        check(cancellations.get() == 1, "late handle was not cancelled");

        final CrateTaskLease normal = new CrateTaskLease();
        check(normal.publish(handle), "live lease rejected handle");
        normal.retire();
        check(cancellations.get() == 2, "published handle was not cancelled on retirement");
    }

    private static void recoveryLedgerIsSingleClaimAndRestartSafe() {
        final UUID player = UUID.randomUUID();
        final CrateLedger ledger = new CrateLedger();
        final CrateLedger.Mutation mutation = ledger.prepare(player, "Alice", "rare", 1, 1L, 0L);
        final CrateRecoveryLedger.Recovery recovery = new CrateRecoveryLedger.Recovery(
                UUID.randomUUID(), player, "Alice", "rare", 2,
                new CrateRecoveryLedger.KeySpec("TRIPWIRE_HOOK", "Key", null), mutation,
                CrateRecoveryLedger.Disposition.ROLLBACK_ONLY, "prepared");
        final CrateRecoveryLedger recoveries = new CrateRecoveryLedger();
        recoveries.add(recovery);
        expectThrows(IllegalStateException.class, () -> recoveries.add(new CrateRecoveryLedger.Recovery(
                UUID.randomUUID(), player, "Alice", "rare", 1, recovery.keySpec(), mutation,
                CrateRecoveryLedger.Disposition.ROLLBACK_ONLY, "duplicate-player")));
        check(recoveries.transition(recovery.openingId(),
                        CrateRecoveryLedger.Disposition.ROLLBACK_ONLY,
                        CrateRecoveryLedger.Disposition.REFUND_KEYS, "consumed") != null,
                "expected recovery transition failed");
        check(recoveries.transition(recovery.openingId(),
                        CrateRecoveryLedger.Disposition.ROLLBACK_ONLY,
                        CrateRecoveryLedger.Disposition.MANUAL_REVIEW, "stale") == null,
                "stale expected-state transition succeeded");
        final Map<UUID, CrateRecoveryLedger.Recovery> snapshot = recoveries.snapshot();
        final CrateRecoveryLedger restored = new CrateRecoveryLedger();
        restored.replace(snapshot);
        check(restored.forPlayer(player).disposition() == CrateRecoveryLedger.Disposition.REFUND_KEYS,
                "recovery restart snapshot changed disposition");
        check(restored.remove(recovery.openingId()) != null
                        && restored.remove(recovery.openingId()) == null,
                "recovery record was claimable more than once");
    }

    private static void commandBatchClassifiesCompensableAndPartialFailure() {
        check(CrateCommandBatch.classify(2, 2, true) == CrateCommandBatch.Outcome.SUCCESS,
                "successful command batch misclassified");
        check(CrateCommandBatch.classify(2, 0, false)
                        == CrateCommandBatch.Outcome.COMPENSATABLE_FAILURE,
                "zero-success command failure must be compensable");
        check(CrateCommandBatch.classify(2, 1, false) == CrateCommandBatch.Outcome.PARTIAL_FAILURE,
                "partial command execution must require manual review");
        expectThrows(IllegalArgumentException.class, () -> CrateCommandBatch.classify(1, 2, false));
    }

    private static void rewardProgressNeverRefundsAfterItemSideEffect() {
        check(CrateRewardProgress.recoveryFor(0, 0, false)
                        == CrateRewardProgress.Recovery.REFUND_KEY,
                "side-effect-free failure should refund the key");
        check(CrateRewardProgress.recoveryFor(0, 0, true)
                        == CrateRewardProgress.Recovery.ROLLBACK_CURRENCY_THEN_REFUND_KEY,
                "currency-only failure should roll back currency before key refund");
        check(CrateRewardProgress.recoveryFor(0, 1, false)
                        == CrateRewardProgress.Recovery.MANUAL_REVIEW,
                "a delivered item must prevent automatic key refund");
        check(CrateRewardProgress.recoveryFor(1, 0, true)
                        == CrateRewardProgress.Recovery.MANUAL_REVIEW,
                "a successful command must prevent automatic compensation");
        expectThrows(IllegalArgumentException.class,
                () -> CrateRewardProgress.recoveryFor(-1, 0, false));
    }

    private static void auditWriterSerializesRotationAndAppend() throws Exception {
        final Path directory = Files.createTempDirectory("crate-audit-");
        final Path file = directory.resolve("crate.log");
        final CrateAuditWriter writer = new CrateAuditWriter(file, 650L);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> threads = new ArrayList<>();
        for (int thread = 0; thread < 2; thread++) {
            final int id = thread;
            threads.add(Thread.ofPlatform().start(() -> {
                await(start);
                for (int line = 0; line < 10; line++) {
                    try {
                        writer.append("t" + id + "-" + line + "-" + "x".repeat(40) + "\n");
                    } catch (final IOException failure) {
                        throw new AssertionError(failure);
                    }
                }
            }));
        }
        start.countDown();
        for (final Thread thread : threads) {
            thread.join(5_000L);
            check(!thread.isAlive(), "audit writer thread did not finish");
        }
        final String combined = (Files.exists(file.resolveSibling("crate.log.1"))
                ? Files.readString(file.resolveSibling("crate.log.1")) : "") + Files.readString(file);
        check(combined.lines().count() == 20L, "serialized rotation lost or duplicated audit lines");
        deleteTree(directory);
    }

    private static void decimalFormattingIsThreadSafe() throws Exception {
        final AtomicInteger failures = new AtomicInteger();
        final List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            threads.add(Thread.ofPlatform().start(() -> {
                for (int attempt = 0; attempt < 1_000; attempt++) {
                    if (!"123456789.125".equals(CrateFormatting.decimal(123456789.125D))) {
                        failures.incrementAndGet();
                    }
                }
            }));
        }
        for (final Thread thread : threads) {
            thread.join(5_000L);
        }
        check(failures.get() == 0, "shared decimal formatting produced corrupt output");
    }

    private static void verifiesProductionWiringAndFoliaGuards() throws IOException {
        final String manager = source("src/main/java/hu/taliann/icesmp/managers/CrateManager.java");
        final String listener = source("src/main/java/hu/taliann/icesmp/listeners/CrateListener.java");
        final String command = source("src/main/java/hu/taliann/icesmp/commands/CrateCommand.java");
        final String core = source("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java");
        final String keyFactory = source("src/main/java/hu/taliann/icesmp/items/CrateKeyFactory.java");
        final String browser = source("src/main/java/hu/taliann/icesmp/gui/CrateBrowserGUI.java");
        final String spin = source("src/main/java/hu/taliann/icesmp/gui/CrateSpinGUI.java");
        final String currency = source("src/main/java/hu/taliann/icesmp/managers/CurrencyManager.java");

        check(listener.contains("event.getHand() != EquipmentSlot.HAND"),
                "off-hand duplicate interaction gate missing");
        check(listener.contains("crateManager.accessDecision(player, definition)"),
                "physical info path bypasses central access policy");
        check(manager.contains("implements PersistentStore, PlayerStateCleanup"),
                "crate manager must reuse persistence and cleanup contracts");
        check(manager.contains("final CrateOpeningLifecycle lifecycle"),
                "atomic opening lifecycle missing");
        check(manager.contains("pending.lifecycle.claimGrant()"),
                "PERSISTED to GRANTING atomic claim missing");
        check(manager.contains("ledger.prepare(") && manager.contains("ledger.apply(pending.ledgerMutation)"),
                "statistics/cooldown must be prepared without mutation and applied only at settlement");
        check(manager.contains("CrateRecoveryLedger.Disposition.REFUND_KEYS"),
                "durable key compensation fence missing");
        check(manager.contains("currencyManager.addBalancesDurably"),
                "currency reward is not durably settled before completion");
        check(currency.contains("rollbackDurably") && currency.contains("writeBalancesLocked()"),
                "durable wallet rollback/persistence integration missing");
        check(manager.contains("dispatched = Bukkit.dispatchCommand")
                        && manager.contains("handleCommandBatchFailure"),
                "command result/exception is not part of settlement outcome");
        check(manager.contains("CrateRules.strictStringList(section.get(\"worlds\")"),
                "malformed worlds config can still fail open");
        check(manager.contains("CrateRules.exactLong(section.get(rawId)"),
                "persistent counts/cooldowns still pass through double");
        check(command.contains("crateManager.accessibleCrateIds(player)"),
                "player completion does not use central access policy");
        check(browser.contains("accessibleCrateIds(player)"),
                "browser exposes inaccessible crate entries");
        check(manager.contains("configSnapshot.generation() != pending.snapshot.generation()")
                        && manager.contains("pending.definition.equals(configSnapshot.definitions().get(pending.crateId))")
                        && manager.contains("pending.crateId.equals(crateBlocks.get(pending.source))"),
                "finalize does not revalidate generation/definition/location");
        check(manager.contains("ismeretlen vagy törölt crate-id"),
                "unknown persistent crate location is not fail-closed");
        check(manager.contains("pendingOpens.containsKey(playerId) || recoveryLedger.containsPlayer(playerId)"),
                "stats reset can race a pending opening/recovery");
        check(manager.contains("new CrateAuditWriter") && manager.contains("auditWriter.append(line)"),
                "audit append/rotation is not behind a serialized writer");
        check(spin.contains("CrateTaskLease") && spin.contains("CrateTaskSubmission.entityDelayed"),
                "spin scheduler rejection cleanup is missing");
        check(manager.contains("display.remove()") && manager.contains("lease.publish(task)"),
                "reveal scheduler rejection can leak a display holder/entity");
        check(manager.contains("KeyConsumption.plan(amounts, pending.keysRequired)"),
                "exact multi-stack key consumption planner missing");
        check(manager.contains("addItem(item).values().forEach"),
                "full inventory overflow is not delivered or dropped");
        check(keyFactory.contains("createKey(final CrateManager.CrateDefinition definition"),
                "key purchase does not use one captured config generation");
        check(manager.contains("configSnapshot.generation() != snapshot.generation()"),
                "key purchase generation race is not fenced");
        check(manager.contains("deferredCurrencyRollbacks.add(mutation)")
                        && manager.contains("drainDeferredCurrencyRollbacks()"),
                "key purchase rollback can be lost when the async scheduler retires during disable");
        check(manager.contains("YamlStore.saveAtomic(storageFile, yaml)"),
                "crate state must use atomic YAML persistence");
        check(manager.contains("yaml.set(\"schema\", SCHEMA)"), "schema marker missing");
        check(core.contains("crateManager::shutdown") && core.contains("crateManager,"),
                "crate manager missing shutdown/store lifecycle wiring");
        check(!browser.contains("static final DecimalFormat")
                        && !command.contains("static final DecimalFormat")
                        && !keyFactory.contains("static final DecimalFormat"),
                "mutable DecimalFormat shared across region threads");
        check(!manager.substring(manager.indexOf("private void completeJoinRecovery"),
                        manager.indexOf("private void failJoinRecovery"))
                        .contains("ledger.rollback"),
                "restart key refund rolls back a ledger mutation that was never applied");

        final List<String> crateSources = List.of(manager, listener, command, core, keyFactory, browser,
                spin, source("src/main/java/hu/taliann/icesmp/listeners/CrateBrowserGUIListener.java"));
        for (final String code : crateSources) {
            check(!code.contains("Bukkit.getScheduler()"), "forbidden Bukkit scheduler introduced");
            check(!code.contains("new BukkitRunnable"), "forbidden BukkitRunnable introduced");
            check(!code.contains("new Thread("), "raw thread introduced");
            check(!code.contains("new Timer("), "raw timer introduced");
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timeout");
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void deleteTree(final Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (final Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        return null;
    }

    private static String source(final String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static <T extends Throwable> T expectThrows(final Class<T> type,
                                                         final ThrowingRunnable action) {
        try {
            action.run();
        } catch (final Throwable thrown) {
            if (type.isInstance(thrown)) {
                return type.cast(thrown);
            }
            throw new AssertionError("Expected " + type.getName() + " but got " + thrown, thrown);
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
