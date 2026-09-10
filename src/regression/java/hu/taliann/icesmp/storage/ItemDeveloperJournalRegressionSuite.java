package hu.taliann.icesmp.storage;

import hu.taliann.icesmp.itemization.ItemDeveloperMutation;
import hu.taliann.icesmp.itemization.ItemDeveloperMutation.Kind;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Actual YAML/fsync/reload evidence; no simulated player or physical inventory claim. */
public final class ItemDeveloperJournalRegressionSuite {
    private static int assertions;
    private static final Logger LOG = Logger.getLogger("item-developer-journal-test");
    public static void main(String[] args) throws Exception {
        for (final var kind : Kind.values()) roundTrip(kind);
        legacyCannotEraseDeveloperReceipt();
        lostAcknowledgementsRequireDiskReload();
        malformedAndConflictingPersistenceFailsClosed();
        receiptAndRequestContracts();
        rejectedSchedulerMakesNoDiskClaim();
        ownerInspectionDoesNotWaitForFsync();
        System.out.println("Item developer journal regression suite passed. assertions=" + assertions);
    }
    private static File file() throws IOException { return Files.createTempDirectory("item-dev-wal-").resolve("journal.yml").toFile(); }
    private static ItemMutationJournal open(File file) {
        final var journal = new ItemMutationJournal(file, LOG, Runnable::run, YamlStore::saveAtomic);
        journal.load(); return journal;
    }
    private static ItemDeveloperReceipt receipt(Kind kind) {
        final var before = new ArrayList<>(Collections.nCopies(41, "-")); before.set(0, "AQID");
        final var after = new ArrayList<>(before); final int target = kind == Kind.CLONE_PROTOTYPE ? 1 : 0;
        after.set(target, "BAUG"); final UUID original = UUID.randomUUID();
        return new ItemDeveloperReceipt(new ItemMutationJournal.Entry(UUID.randomUUID(), HiddenDevAuthority.PRIMARY_DEVELOPER,
                "DEV_" + kind, original, before, after, 100), kind, 0, target,
                kind == Kind.CLONE_PROTOTYPE ? UUID.randomUUID() : original, 4,
                kind == Kind.CLONE_PROTOTYPE ? 0 : kind == Kind.REFRESH_PRESENTATION ? 4 : 5,
                ItemDeveloperReceipt.State.PENDING, 0);
    }
    private static boolean done(CompletionStage<Boolean> stage) { return stage.toCompletableFuture().join(); }
    private static void roundTrip(Kind kind) throws Exception {
        final var receipt = receipt(kind); final File path = file(); var journal = open(path);
        check(done(journal.prepareDeveloper(receipt)), kind + " durable prepare");
        journal = open(path);
        check(journal.isHealthy() && journal.findDeveloper(receipt.entry().operationId()).orElseThrow().equals(receipt), "pending exact disk round trip");
        check(journal.entriesFor(HiddenDevAuthority.PRIMARY_DEVELOPER).equals(List.of(receipt.entry())), "one native player reservation");
        check(!done(journal.prepareDeveloper(receipt)), "same operation cannot prepare twice");
        check(!done(journal.prepareDeveloper(receipt(kind))), "same player cannot hold another pending operation");
        check(done(journal.resolveDeveloper(receipt, ItemDeveloperReceipt.State.OBSERVED)), "owner observation retained");
        journal = open(path);
        check(journal.entriesFor(HiddenDevAuthority.PRIMARY_DEVELOPER).isEmpty(), "observed releases pending slot");
        final var observed = journal.findDeveloper(receipt.entry().operationId()).orElseThrow();
        check(observed.entry().equals(receipt.entry()) && observed.state() == ItemDeveloperReceipt.State.OBSERVED, "terminal retains full projection");
        check(done(journal.resolveDeveloper(receipt, ItemDeveloperReceipt.State.OBSERVED)), "same observed result is idempotent");
        check(!done(journal.resolveDeveloper(receipt, ItemDeveloperReceipt.State.ABORTED)), "observed cannot become aborted");
        check(!done(journal.prepareDeveloper(receipt)), "missing physical copy cannot reissue an observed operation");
        final var next = receipt(kind);
        check(done(journal.prepareDeveloper(next)) && done(journal.resolveDeveloper(next, ItemDeveloperReceipt.State.ABORTED)), "explicit abort retained");
        journal = open(path);
        check(journal.findDeveloper(next.entry().operationId()).orElseThrow().state() == ItemDeveloperReceipt.State.ABORTED
                && !done(journal.prepareDeveloper(next)), "aborted operation identity cannot be recycled");
    }
    private static void legacyCannotEraseDeveloperReceipt() throws Exception {
        final var journal = open(file()); final var receipt = receipt(Kind.CLONE_PROTOTYPE);
        check(!done(journal.prepare(receipt.entry())), "legacy route cannot prepare developer work without metadata");
        check(done(journal.prepareDeveloper(receipt)), "typed preparation accepted");
        check(!done(journal.complete(receipt.entry().operationId())), "legacy completion cannot erase pending developer intent");
        check(done(journal.resolveDeveloper(receipt, ItemDeveloperReceipt.State.OBSERVED)), "retained observed result");
        check(!done(journal.complete(receipt.entry().operationId())), "legacy completion cannot erase terminal developer receipt");
        final var plain = new ItemMutationJournal.Entry(UUID.randomUUID(), UUID.randomUUID(), "REROLL", UUID.randomUUID(), List.of("AQID"), List.of("BAUG"), 100);
        check(done(journal.prepare(plain)) && done(journal.complete(plain.operationId())), "ordinary mutation lifecycle still completes");
    }
    private static void lostAcknowledgementsRequireDiskReload() throws Exception {
        for (int failAt : List.of(1, 2)) {
            final File path = file(); final var count = new AtomicInteger(); final var receipt = receipt(Kind.CLONE_PROTOTYPE);
            final var journal = new ItemMutationJournal(path, LOG, Runnable::run, (file, yaml) -> {
                YamlStore.saveAtomic(file, yaml);
                if (count.incrementAndGet() == failAt) throw new IOException("ack lost after actual fsync");
            });
            journal.load(); final boolean prepared = done(journal.prepareDeveloper(receipt));
            check(prepared == (failAt != 1), "prepare acknowledgement is truthful");
            if (failAt == 2) check(!done(journal.resolveDeveloper(receipt, ItemDeveloperReceipt.State.OBSERVED)), "terminal lost acknowledgement reported");
            check(!journal.isHealthy(), "uncertain writer closed");
            expectFailure(() -> journal.findDeveloper(receipt.entry().operationId()), "uncertain state is not a missing receipt");
            check(!done(journal.complete(receipt.entry().operationId())) && !done(journal.prepareDeveloper(receipt(Kind.CLONE_PROTOTYPE))), "uncertain journal cannot overwrite actual disk");
            final var reloaded = open(path);
            check(reloaded.isHealthy() && reloaded.findDeveloper(receipt.entry().operationId()).orElseThrow().state()
                    == (failAt == 1 ? ItemDeveloperReceipt.State.PENDING : ItemDeveloperReceipt.State.OBSERVED), "actual durable state recovered");
            check(!done(reloaded.prepareDeveloper(receipt)), "lost acknowledgement cannot duplicate original operation");
        }
    }
    private static void malformedAndConflictingPersistenceFailsClosed() throws Exception {
        for (final String scenario : List.of("orphan", "terminal_with_pending", "missing_pending", "unknown_state", "unknown_kind", "wrong_actor", "wrong_result", "foreign_slot", "future_schema")) {
            final File path = file(); final var journal = open(path); final var receipt = receipt(Kind.REROLL_CANONICAL);
            check(done(journal.prepareDeveloper(receipt)), "corruption fixture uses real persisted intent");
            final var yaml = YamlConfiguration.loadConfiguration(path); final String prefix = "developer-receipts." + receipt.entry().operationId() + ".";
            switch (scenario) {
                case "orphan" -> yaml.set("developer-receipts", null);
                case "terminal_with_pending" -> { yaml.set(prefix + "state", "OBSERVED"); yaml.set(prefix + "resolved-at", 101); }
                case "missing_pending" -> yaml.set("operations", null);
                case "unknown_state" -> yaml.set(prefix + "state", "SUCCESS_MAYBE");
                case "unknown_kind" -> yaml.set(prefix + "kind", "GIVE_ANYTHING");
                case "wrong_actor" -> yaml.set(prefix + "player", UUID.randomUUID().toString());
                case "wrong_result" -> yaml.set(prefix + "result-item", UUID.randomUUID().toString());
                case "foreign_slot" -> yaml.set(prefix + "target-slot", 40);
                case "future_schema" -> yaml.set("schema", 999);
            }
            YamlStore.saveAtomic(path, yaml); final byte[] bytes = Files.readAllBytes(path.toPath());
            final var broken = new ItemMutationJournal(path, LOG, Runnable::run, YamlStore::saveAtomic);
            try { broken.load(); throw new AssertionError("Critical corruption must reject startup"); }
            catch (CorruptStateFileError expected) { assertions++; }
            check(!broken.isHealthy(), scenario + " rejected on load");
            check(!done(broken.prepareDeveloper(receipt)) && Arrays.equals(bytes, Files.readAllBytes(path.toPath())), "bad evidence is never overwritten");
        }
    }
    private static void receiptAndRequestContracts() {
        expectFailure(() -> ItemDeveloperMutation.simple(Kind.ADD_RUNE_PROTOTYPE), "rune insertion requires explicit rune");
        expectFailure(() -> new ItemDeveloperMutation(Kind.REROLL_CANONICAL, "", Double.NaN, false, "", -1), "NaN quality rejected");
        expectFailure(() -> new ItemDeveloperMutation(Kind.CLONE_PROTOTYPE, "attack_damage", 0, false, "", -1), "irrelevant lock refused");
        expectFailure(() -> new ItemDeveloperMutation(Kind.REMOVE_RUNE_CANONICAL, "", 0, false, "", 2), "socket bound enforced");
        final var original = receipt(Kind.CLONE_PROTOTYPE);
        expectFailure(() -> new ItemDeveloperReceipt(original.entry(), original.kind(), 0, 1, original.entry().itemId(), 4, 0, original.state(), 0), "copy identity must be new");
        expectFailure(() -> original.resolve(ItemDeveloperReceipt.State.PENDING, 0), "pending cannot settle pending");
        expectFailure(() -> original.resolve(ItemDeveloperReceipt.State.OBSERVED, 101).resolve(ItemDeveloperReceipt.State.ABORTED, 102), "terminal receipt immutable");
    }
    private static void rejectedSchedulerMakesNoDiskClaim() throws Exception {
        final File file = file(); final var journal = new ItemMutationJournal(file, LOG, action -> { throw new IllegalStateException("scheduler closed"); }, YamlStore::saveAtomic);
        check(journal.prepareDeveloper(receipt(Kind.CLONE_PROTOTYPE)).toCompletableFuture().isCompletedExceptionally(), "rejected registration completes exceptionally");
        check(!file.exists(), "rejected registration publishes nothing");
    }
    private static void ownerInspectionDoesNotWaitForFsync() throws Exception {
        final var writing = new java.util.concurrent.CountDownLatch(1);
        final var release = new java.util.concurrent.CountDownLatch(1);
        try (final var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            final var journal = new ItemMutationJournal(file(), LOG, workers::execute, (file, yaml) -> {
                YamlStore.saveAtomic(file, yaml); writing.countDown();
                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("test writer release missing"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            });
            journal.load(); final var receipt = receipt(Kind.CLONE_PROTOTYPE);
            final var pending = journal.prepareDeveloper(receipt);
            try {
                check(writing.await(3, java.util.concurrent.TimeUnit.SECONDS), "actual writer entered");
                final var read = workers.submit(() -> {
                    expectFailure(() -> journal.findDeveloper(receipt.entry().operationId()), "unacknowledged write is unavailable");
                    expectFailure(() -> journal.hasPendingForDeveloper(HiddenDevAuthority.PRIMARY_DEVELOPER), "owner preparation cannot mistake unacknowledged write for idle");
                });
                read.get(1, java.util.concurrent.TimeUnit.SECONDS);
                check(!pending.toCompletableFuture().isDone(), "owner inspection did not force a writer acknowledgement");
            } finally { release.countDown(); }
            check(pending.toCompletableFuture().get(3, java.util.concurrent.TimeUnit.SECONDS), "released writer acknowledges");
            check(journal.hasPendingForDeveloper(HiddenDevAuthority.PRIMARY_DEVELOPER), "acknowledged immutable view retains pending player");
        }
    }
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static void expectFailure(Runnable run, String label) {
        try { run.run(); } catch (IllegalArgumentException | IllegalStateException expected) { assertions++; return; }
        throw new AssertionError(label);
    }
}
