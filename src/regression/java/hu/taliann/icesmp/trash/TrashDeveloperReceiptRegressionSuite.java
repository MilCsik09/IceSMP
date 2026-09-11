package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;

/** Real native history/WAL tests; no Bukkit item or player-save claims. */
public final class TrashDeveloperReceiptRegressionSuite {
    private static int assertions;
    private static final UUID ACTOR = HiddenDevAuthority.PRIMARY_DEVELOPER;
    private static final Logger LOGGER = Logger.getAnonymousLogger();
    private static final List<TrashDeveloperReceipt.SlotChange> SLOTS = List.of(
            new TrashDeveloperReceipt.SlotChange(0, "YQ==", "Yg=="));
    public static void main(String[] args) throws Exception {
        nativeKinds(); lostAcknowledgement(false); lostAcknowledgement(true); rollbackAndAdmission();
        heldWriter(); malformedReceipts(); boundedReceipts(); truthfulArchaeology(); nativeReversal();
        System.out.println("Trash developer receipts passed. assertions=" + assertions);
    }
    private static final class Fixture implements AutoCloseable {
        final Path root = Files.createTempDirectory("trash-developer-receipt-");
        final TrashCatalog catalog = new TrashCatalog(() -> getClass().getClassLoader().getResourceAsStream(TrashCatalog.RESOURCE), LOGGER);
        final AtomicBoolean failAfter = new AtomicBoolean();
        final TrashHistoryStore store;
        final TrashDefinition definition;
        Fixture() throws Exception {
            catalog.load(); definition = catalog.snapshot().values().stream().filter(d -> !d.successPhase().isBlank()).findFirst().orElseThrow();
            store = new TrashHistoryStore(root.resolve("history.yml").toFile(), root.resolve("history.wal").toFile(), LOGGER, catalog,
                    (journal, sequence, payload) -> { journal.append(sequence, payload); if (failAfter.getAndSet(false)) throw new IllegalStateException("lost acknowledgement"); });
            store.load();
        }
        TrashHistoryStore fresh() { return new TrashHistoryStore(root.resolve("history.yml").toFile(), root.resolve("history.wal").toFile(), LOGGER, catalog); }
        TrashDeveloperReceipt commit(TrashDeveloperReceipt.Kind kind) {
            return store.transact(() -> inside(kind), null);
        }
        TrashDeveloperReceipt inside(TrashDeveloperReceipt.Kind kind) {
            UUID operation = UUID.randomUUID(), instance = UUID.randomUUID();
            var result = store.createAndRecord(instance, definition.id(), "base",
                    kind == TrashDeveloperReceipt.Kind.TRANSITION_SUCCESS ? TrashHistoryEvent.DEV_INDIVIDUALIZED : kind.event(), ACTOR, operation.toString());
            if (kind == TrashDeveloperReceipt.Kind.TRANSITION_SUCCESS) result = store.transitionDeveloper(instance, definition.id(), "base", definition.successPhase(), ACTOR, operation);
            final var receipt = new TrashDeveloperReceipt(operation, ACTOR, kind, instance, definition.id(), "base", 0,
                    result.phase(), result.revision(), result.updatedAt(), SLOTS, false);
            store.putDeveloperReceipt(receipt); return receipt;
        }
        public void close() throws Exception {
            try (var paths = Files.walk(root)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
    private static void nativeKinds() throws Exception {
        for (var kind : TrashDeveloperReceipt.Kind.values()) {
            if (kind == TrashDeveloperReceipt.Kind.REVERT) continue;
            try (var f = new Fixture()) {
            var receipt = f.commit(kind); var loaded = f.fresh(); loaded.load();
            check(loaded.tryInspectDeveloperReceipt(receipt.operationId()).orElseThrow().orElseThrow().equals(receipt), "WAL lost exact native receipt");
            check(loaded.tryInspect(receipt.instanceId()).isEmpty(), "unobserved physical projection exposed mutable item");
            check(!loaded.matches(receipt.instanceId(), receipt.baseId(), receipt.afterPhase(), receipt.afterRevision()), "unobserved item admitted normal use");
            refuse(() -> loaded.transact(() -> loaded.record(receipt.instanceId(), receipt.baseId(), receipt.afterPhase(), TrashHistoryEvent.REPAIRED, ACTOR, ""), null));
            final byte[] beforeObservation = Files.readAllBytes(f.root.resolve("history.wal"));
            check(!loaded.tryObserveDeveloperProjection(receipt, () -> false).orElseThrow(), "invented read-only physical observation");
            check(loaded.tryObserveDeveloperProjection(receipt, () -> true).orElseThrow(), "exact pending state cannot be assessed read-only");
            check(loaded.tryInspectDeveloperReceipt(receipt.operationId()).orElseThrow().orElseThrow().equals(receipt)
                    && Arrays.equals(beforeObservation, Files.readAllBytes(f.root.resolve("history.wal"))), "read-only assessment wrote native history or observation");
            check(!loaded.tryConfirmDeveloperProjection(receipt, () -> false), "invented projection observation");
            check(loaded.tryRestoreDeveloperProjection(receipt, () -> true, () -> {}, null), "exact pending native projection refused");
            check(loaded.tryConfirmDeveloperProjection(receipt, () -> true), "fresh observation refused");
            check(!loaded.tryConfirmDeveloperProjection(receipt, () -> { throw new AssertionError("duplicate observation"); }), "confirmation replayed");
            check(!loaded.tryRestoreDeveloperProjection(receipt.withObservedProjection(), () -> true,
                    () -> { throw new AssertionError("observed operation recreated item"); }, null), "completed receipt authorized recreation");
            check(loaded.matches(receipt.instanceId(), receipt.baseId(), receipt.afterPhase(), receipt.afterRevision()), "observed item remained fenced");
            for (int i = 0; i < 70; i++) loaded.transact(() -> loaded.record(receipt.instanceId(), receipt.baseId(), receipt.afterPhase(), TrashHistoryEvent.ACTIVATED, ACTOR, ""), null);
            loaded.save(); var again = f.fresh(); again.load();
            check(again.tryInspectDeveloperReceipt(receipt.operationId()).orElseThrow().orElseThrow().equals(receipt.withObservedProjection()), "bounded history evicted retained physical witness");
            check(again.tryInspectDeveloperReceipts(UUID.randomUUID()).orElseThrow().isEmpty(), "foreign actor borrowed custody receipt");
            }
        }
    }

    private static void nativeReversal() throws Exception {
        for (var kind : List.of(TrashDeveloperReceipt.Kind.REPAIR, TrashDeveloperReceipt.Kind.TRANSITION_SUCCESS,
                TrashDeveloperReceipt.Kind.SANDBOX_COPY)) try (var f = new Fixture()) {
            final var forward = f.commit(kind);
            refuse(() -> f.store.transact(() -> f.store.revertDeveloper(forward, UUID.randomUUID()), null));
            check(f.store.tryConfirmDeveloperProjection(forward, () -> true), "forward projection not observed");
            final var observed = forward.withObservedProjection(); final var inverseId = UUID.randomUUID();
            final var inverse = f.store.transact(() -> {
                final var result = f.store.revertDeveloper(observed, inverseId);
                final var value = new TrashDeveloperReceipt(inverseId, ACTOR, TrashDeveloperReceipt.Kind.REVERT,
                        observed.instanceId(), observed.baseId(), observed.afterPhase(), observed.afterRevision(),
                        result.phase(), result.revision(), result.updatedAt(),
                        List.of(new TrashDeveloperReceipt.SlotChange(0, "Yg==", "YQ==")), false, Optional.of(observed.operationId()));
                f.store.putDeveloperReceipt(value); return value;
            }, null);
            check(inverse.instanceId().equals(forward.instanceId()) && inverse.afterRevision() == forward.afterRevision() + 1,
                    "reversal reset native UUID or history revision");
            check(f.store.find(forward.instanceId()).orElseThrow().events().getLast().type() == TrashHistoryEvent.DEV_REVERTED,
                    "reversal fabricated natural history");
            refuse(() -> f.store.transact(() -> f.store.revertDeveloper(observed, UUID.randomUUID()), null));
            check(f.store.tryConfirmDeveloperProjection(inverse, () -> true), "inverse projection not observed");
            f.store.save(); final var loaded = f.fresh(); loaded.load();
            check(loaded.tryInspectDeveloperReceipt(inverseId).orElseThrow().orElseThrow().equals(inverse.withObservedProjection()),
                    "native snapshot lost inverse relationship");
            check(loaded.tryInspectDeveloperReceipt(observed.operationId()).orElseThrow().orElseThrow().equals(observed),
                    "reversal rewrote original native receipt");
            refuse(() -> loaded.transact(() -> loaded.revertDeveloper(observed, UUID.randomUUID()), null));
            refuse(() -> loaded.transact(() -> loaded.revertDeveloper(inverse.withObservedProjection(), UUID.randomUUID()), null));
        }
        try (var f = new Fixture()) {
            final var forward = f.commit(TrashDeveloperReceipt.Kind.INDIVIDUALIZE);
            f.store.tryConfirmDeveloperProjection(forward, () -> true);
            refuse(() -> f.store.transact(() -> f.store.revertDeveloper(forward.withObservedProjection(), UUID.randomUUID()), null));
            // Legacy schema 6 has the same forward receipt, without a reversal field.
            f.store.save(); final var legacy = YamlConfiguration.loadConfiguration(f.root.resolve("history.yml").toFile());
            legacy.set("schema-version", 6); legacy.set("developer-operations." + forward.operationId() + ".reverses", null);
            Files.writeString(f.root.resolve("history.yml"), legacy.saveToString());
            final var loaded = f.fresh(); loaded.load();
            check(loaded.tryInspectDeveloperReceipt(forward.operationId()).orElseThrow().orElseThrow().equals(forward.withObservedProjection()),
                    "legacy forward receipt migration changed native evidence");
        }
    }
    private static void lostAcknowledgement(boolean confirming) throws Exception {
        try (var f = new Fixture()) {
            var receipt = confirming ? f.commit(TrashDeveloperReceipt.Kind.SANDBOX_COPY) : null;
            var candidate = new AtomicReference<TrashDeveloperReceipt>(); var projected = new AtomicBoolean(); f.failAfter.set(true);
            if (confirming) refuse(() -> f.store.tryConfirmDeveloperProjection(receipt, () -> true));
            else refuse(() -> f.store.transact(() -> { candidate.set(f.inside(TrashDeveloperReceipt.Kind.SANDBOX_COPY)); projected.set(true); return true; }, () -> projected.set(false)));
            var expected = confirming ? receipt : candidate.get();
            check(!projected.get(), "failed native acknowledgement failed to restore projection");
            check(f.store.tryInspectDeveloperReceipt(expected.operationId()).isEmpty(), "uncertain memory exposed receipt absence");
            refuse(f.store::save);
            var loaded = f.fresh(); loaded.load();
            check(loaded.tryInspectDeveloperReceipt(expected.operationId()).orElseThrow().orElseThrow()
                    .equals(confirming ? expected.withObservedProjection() : expected), "real fsynced developer receipt was lost");
        }
    }
    private static void rollbackAndAdmission() throws Exception {
        try (var f = new Fixture()) {
            var before = f.store.tryInspectDeveloperReceipts(ACTOR).orElseThrow();
            final var candidate = new AtomicReference<TrashDeveloperReceipt>();
            refuse(() -> f.store.transact(() -> { candidate.set(f.inside(TrashDeveloperReceipt.Kind.REPAIR)); throw new IllegalArgumentException("projection failure"); }, null));
            check(f.store.find(candidate.get().instanceId()).isEmpty() && f.store.tryInspectDeveloperReceipts(ACTOR).orElseThrow().equals(before), "rollback split history and native receipt");
            check(!Files.exists(f.root.resolve("history.wal")), "rejected projection appended WAL");
            var receipt = f.commit(TrashDeveloperReceipt.Kind.REPAIR);
            refuse(() -> f.store.transact(() -> { f.store.putDeveloperReceipt(receipt); return true; }, null));
            refuse(() -> new TrashDeveloperReceipt(receipt.operationId(), UUID.randomUUID(), receipt.kind(), receipt.instanceId(), receipt.baseId(), receipt.beforePhase(), 0, receipt.afterPhase(), receipt.afterRevision(), receipt.recordedAt(), SLOTS, false));
            refuse(() -> f.store.transact(() -> f.store.createAndRecord(UUID.randomUUID(), receipt.baseId(), "base", TrashHistoryEvent.DEV_REPAIRED, UUID.randomUUID(), UUID.randomUUID().toString()), null));
            refuse(() -> new TrashDeveloperReceipt.SlotChange(41, "", "YQ=="));
            refuse(() -> new TrashDeveloperReceipt.SlotChange(0, "YQ", "Yg=="));
            refuse(() -> new TrashDeveloperReceipt(receipt.operationId(), ACTOR, receipt.kind(), receipt.instanceId(), receipt.baseId(), receipt.beforePhase(), 0, receipt.afterPhase(), receipt.afterRevision(), receipt.recordedAt(), List.of(new TrashDeveloperReceipt.SlotChange(0, "", "")), false));
        }
    }
    private static void heldWriter() throws Exception {
        try (var f = new Fixture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var candidate = new AtomicReference<TrashDeveloperReceipt>();
            var future = executor.submit(() -> f.store.transact(() -> {
                candidate.set(f.inside(TrashDeveloperReceipt.Kind.INDIVIDUALIZE));
                check(f.store.tryInspectDeveloperReceipts(ACTOR).isEmpty(), "reentrant observer saw unacknowledged candidate");
                entered.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timeout"); }
                catch (InterruptedException failure) { throw new AssertionError(failure); } return true;
            }, null));
            try {
                check(entered.await(5, TimeUnit.SECONDS), "writer did not enter");
                check(executor.submit(() -> f.store.tryInspectDeveloperReceipts(ACTOR).isEmpty()).get(1, TimeUnit.SECONDS), "inspection blocked on WAL writer");
            } finally { release.countDown(); }
            check(future.get(5, TimeUnit.SECONDS), "native writer did not acknowledge");
        }
    }
    private static void malformedReceipts() throws Exception {
        for (String field : List.of("projection-observed", "after-revision", "actor", "kind", "instance", "slots", "reverses")) try (var f = new Fixture()) {
            var receipt = f.commit(TrashDeveloperReceipt.Kind.SANDBOX_COPY); f.store.save();
            var yaml = YamlConfiguration.loadConfiguration(f.root.resolve("history.yml").toFile());
            yaml.set("developer-operations." + receipt.operationId() + "." + field, "wrong");
            Files.writeString(f.root.resolve("history.yml"), yaml.saveToString()); refuse(() -> f.fresh().load());
        }
        try (var f = new Fixture()) {
            f.commit(TrashDeveloperReceipt.Kind.REPAIR); f.store.save();
            var yaml = YamlConfiguration.loadConfiguration(f.root.resolve("history.yml").toFile()); yaml.set("schema-version", 5);
            Files.writeString(f.root.resolve("history.yml"), yaml.saveToString()); refuse(() -> f.fresh().load());
        }
    }
    private static void boundedReceipts() throws Exception {
        try (var f = new Fixture()) {
            f.store.transact(() -> { for (int i = 0; i < 1024; i++) f.inside(TrashDeveloperReceipt.Kind.REPAIR); return true; }, null);
            refuse(() -> f.commit(TrashDeveloperReceipt.Kind.REPAIR));
            check(f.store.tryInspectDeveloperReceipts(ACTOR).orElseThrow().size() == 1024, "capacity silently evicted a native receipt");
            f.store.save(); var loaded = f.fresh(); loaded.load();
            check(loaded.tryInspectDeveloperReceipts(ACTOR).orElseThrow().size() == 1024, "snapshot dropped bounded unresolved receipts");
        }
    }
    private static void truthfulArchaeology() throws Exception {
        try (var f = new Fixture()) {
            final var ordinary = f.catalog.snapshot().values().stream().filter(d -> d.internalKind() == TrashKind.MUNDANE).findFirst().orElseThrow();
            final long now = System.currentTimeMillis();
            final var history = new TrashHistoryStore.Snapshot(UUID.randomUUID(), ordinary.id(), "base", 1, now, now,
                    List.of(new TrashHistoryStore.HistoryEntry(1, TrashHistoryEvent.DEV_REPAIRED, now, ACTOR, UUID.randomUUID().toString())), Set.of());
            final var facts = TrashArchaeologyFactEngine.evaluate(ordinary, Optional.of(history), 50).orElseThrow();
            check(!facts.historical() && facts.facts().stream().noneMatch(fact -> fact.id().equals("repaired")), "developer repair became natural archaeological evidence");
        }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void refuse(Runnable action) {
        try { action.run(); throw new AssertionError("expected refusal"); }
        catch (RuntimeException | hu.taliann.icesmp.storage.CriticalPersistenceWriteError
                | hu.taliann.icesmp.storage.CorruptStateFileError expected) { assertions++; }
    }
}
