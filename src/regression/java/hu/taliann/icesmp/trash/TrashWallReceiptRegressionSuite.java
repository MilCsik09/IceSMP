package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;

/** Exercises native history frames and real WAL acknowledgements, not a simulated receipt store. */
public final class TrashWallReceiptRegressionSuite {
    private static final Logger LOGGER = Logger.getAnonymousLogger();
    private static int assertions;
    private static final UUID ACTOR = UUID.randomUUID(), WORLD = UUID.randomUUID();

    public static void main(String[] args) throws Exception {
        atomicConsumeAndObservedRemoval();
        acknowledgedProjectionRecovery();
        lostAcknowledgement(false); lostAcknowledgement(true); rejectedAppend();
        busyWriterAndReentrantObservation();
        legacySnapshotAndMalformedReceipt();
        corruptedPendingSnapshots();
        boundedPendingReceipts();
        System.out.println("Trash wall receipts passed. assertions=" + assertions);
    }

    private static final class Fixture implements AutoCloseable {
        final Path root = Files.createTempDirectory("trash-wall-receipt-");
        final TrashCatalog catalog;
        final TrashDefinition brick;
        final AtomicBoolean failAcknowledgement = new AtomicBoolean();
        final AtomicBoolean failBeforeAppend = new AtomicBoolean();
        final TrashHistoryStore store;
        Fixture() throws Exception {
            catalog = new TrashCatalog(() -> TrashWallReceiptRegressionSuite.class.getClassLoader()
                    .getResourceAsStream(TrashCatalog.RESOURCE), LOGGER);
            catalog.load();
            brick = catalog.snapshot().values().stream().filter(d -> d.behavior().equals("TEGLA"))
                    .findFirst().orElseThrow();
            store = new TrashHistoryStore(root.resolve("history.yml").toFile(),
                    root.resolve("history.wal").toFile(), LOGGER, catalog, (journal, sequence, payload) -> {
                if (failBeforeAppend.getAndSet(false)) throw new IllegalStateException("injected pre-append refusal");
                journal.append(sequence, payload);
                if (failAcknowledgement.getAndSet(false)) throw new IllegalStateException("injected lost acknowledgement");
            });
            store.load();
        }
        TrashHistoryStore fresh() {
            return new TrashHistoryStore(root.resolve("history.yml").toFile(),
                    root.resolve("history.wal").toFile(), LOGGER, catalog);
        }
        UUID create() {
            UUID id = UUID.randomUUID();
            store.transact(() -> store.createAndRecord(id, brick.id(), "base",
                    TrashHistoryEvent.CREATED_AMBIENT, ACTOR, ""), null);
            return id;
        }
        TrashHistoryStore.WallReceipt consume(UUID id) {
            return store.transact(() -> consumeInside(id), null);
        }
        TrashHistoryStore.WallReceipt consumeInside(UUID id) {
            long beforeRevision = store.find(id).orElseThrow().revision();
            var result = store.transform(id, brick.id(), "base", brick.successPhase(), ACTOR);
            var field = new TrashRuleFieldService.RuleField(UUID.randomUUID(),
                    TrashRuleFieldService.FieldKind.PROJECTILE_WALL,
                    new TrashRuleFieldService.Point(WORLD, 12, 64, -9), 2.5,
                    System.currentTimeMillis() + 20_000, ACTOR, UUID.randomUUID().toString());
            var receipt = new TrashHistoryStore.WallReceipt(field.id(), ACTOR, WORLD,
                    UUID.randomUUID(), id, result.revision(), brick.id(), brick.successPhase(),
                    System.currentTimeMillis(), field, beforeRevision);
            store.putWallReceipt(receipt);
            return receipt;
        }
        public void close() throws Exception {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void atomicConsumeAndObservedRemoval() throws Exception {
        try (var f = new Fixture()) {
            UUID id = f.create(); var receipt = f.consume(id);
            check(f.store.tryInspectWallReceipts().orElseThrow().equals(List.of(receipt)), "native pending receipt absent");
            var loaded = f.fresh(); loaded.load();
            check(loaded.tryInspectWallReceipts().orElseThrow().equals(List.of(receipt)), "WAL did not recover exact receipt");
            check(loaded.find(id).orElseThrow().revision() == receipt.revision(), "receipt and history split across acknowledgements");
            var inspected = loaded.tryInspect(id).orElseThrow();
            check(inspected.pendingWall().orElseThrow().equals(receipt)
                            && inspected.history().orElseThrow().revision() == receipt.revision(),
                    "native item inspection separates history and receipt generations");
            expectFailure(() -> loaded.transact(() -> loaded.record(id, f.brick.id(), f.brick.successPhase(),
                    TrashHistoryEvent.OWNER_OBSERVED, UUID.randomUUID(), ""), null));
            check(!loaded.tryConfirmWallRemoval(receipt, () -> false), "unobserved removal cleared receipt");
            var forged = new TrashHistoryStore.WallReceipt(receipt.operationId(), ACTOR,
                    WORLD, UUID.randomUUID(), id, receipt.revision(), receipt.baseId(), receipt.phase(), receipt.consumedAt(),
                    receipt.field(), receipt.beforeRevision());
            check(!loaded.tryConfirmWallRemoval(forged, () -> { throw new AssertionError("stale receipt observation invoked"); }),
                    "mismatched operation receipt accepted");
            check(loaded.tryConfirmWallRemoval(receipt, () -> true), "observed removal did not commit");
            check(!loaded.tryConfirmWallRemoval(receipt, () -> true), "duplicate completion replayed");
            loaded.transact(() -> loaded.record(id, f.brick.id(), f.brick.successPhase(),
                    TrashHistoryEvent.OWNER_OBSERVED, ACTOR, ""), null);
            loaded.save(); var finalLoad = f.fresh(); finalLoad.load();
            check(finalLoad.tryInspectWallReceipts().orElseThrow().isEmpty(), "snapshot resurrected completed receipt");
        }
    }

    private static void lostAcknowledgement(boolean completing) throws Exception {
        try (var f = new Fixture()) {
            UUID id = f.create();
            TrashHistoryStore.WallReceipt prior = completing ? f.consume(id) : null;
            AtomicInteger projection = new AtomicInteger();
            f.failAcknowledgement.set(true);
            if (completing) expectFailure(() -> f.store.tryConfirmWallRemoval(prior, () -> true));
            else expectFailure(() -> f.store.transact(() -> {
                projection.set(1); return f.consumeInside(id);
            }, () -> projection.set(0)));
            check(f.store.tryInspectWallReceipts().isEmpty(), "uncertain write exposed rollback as acknowledged recovery state");
            check(projection.get() == 0, "failed consume did not restore external projection");
            expectFailure(() -> f.store.transact(() -> true, null));
            expectFailure(f.store::save);
            var loaded = f.fresh(); loaded.load();
            check(loaded.find(id).orElseThrow().phase().equals(f.brick.successPhase()), "real fsynced consumption was lost");
            check(loaded.tryInspectWallReceipts().orElseThrow().size() == (completing ? 0 : 1),
                    "recovery did not distinguish consumed-pending from durably completed removal");
        }
    }

    private static void acknowledgedProjectionRecovery() throws Exception {
        try (var f = new Fixture()) {
            final var receipt = f.consume(f.create()); f.store.load();
            final var before = f.store.find(receipt.instanceId()).orElseThrow();
            final byte[] wal = Files.readAllBytes(f.root.resolve("history.wal"));
            final AtomicInteger projection = new AtomicInteger();
            check(!f.store.tryRestoreWallProjection(receipt, () -> false,
                    snapshot -> { throw new AssertionError("refused projection ran"); },
                    () -> { throw new AssertionError("untouched projection restored"); }), "owner refusal was ignored");
            expectFailure(() -> f.store.tryRestoreWallProjection(receipt, () -> true, snapshot -> {
                check(snapshot.equals(before), "recovery invented a history generation");
                projection.set(1); throw new IllegalStateException("injected projection failure");
            }, () -> projection.set(0)));
            check(projection.get() == 0 && f.store.tryInspectWallReceipts().orElseThrow().equals(List.of(receipt)),
                    "failed projection lost either before state or native pending operation");
            check(f.store.tryRestoreWallProjection(receipt, () -> true, snapshot -> {
                check(snapshot.equals(before), "recovery used another native revision"); projection.set(1);
            }, () -> projection.set(0)), "acknowledged projection recovery refused");
            check(projection.get() == 1 && f.store.find(receipt.instanceId()).orElseThrow().equals(before),
                    "projection recovery rewrote immutable history");
            check(Arrays.equals(wal, Files.readAllBytes(f.root.resolve("history.wal"))),
                    "projection recovery fabricated a second history/WAL event");
            check(f.store.tryInspectWallReceipts().orElseThrow().equals(List.of(receipt)),
                    "inventory projection was mistaken for projectile removal");
            check(f.store.tryConfirmWallRemoval(receipt, () -> true), "observed fixture completion failed");
            check(!f.store.tryRestoreWallProjection(receipt,
                    () -> { throw new AssertionError("obsolete receipt entered owner admission"); },
                    snapshot -> { throw new AssertionError("obsolete receipt recreated an item"); }, null),
                    "completed operation still authorizes a recovery projection");
        }
    }

    private static void rejectedAppend() throws Exception {
        try (var f = new Fixture()) {
            UUID id = f.create(); f.failBeforeAppend.set(true);
            expectFailure(() -> f.consume(id));
            check(f.store.tryInspectWallReceipts().isEmpty(), "failed append did not fence unassessed memory");
            f.store.load();
            check(f.store.find(id).orElseThrow().phase().equals("base"), "refused append consumed durable history");
            check(f.store.tryInspectWallReceipts().orElseThrow().isEmpty(), "refused append fabricated a pending receipt");
            check(f.consume(id) != null, "successful assessment did not permit a fresh attempt");
        }
    }

    private static void busyWriterAndReentrantObservation() throws Exception {
        try (var f = new Fixture(); var executor = Executors.newSingleThreadExecutor()) {
            var receipt = f.consume(f.create());
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var writer = executor.submit(() -> f.store.transact(() -> {
                check(f.store.tryInspectWallReceipts().isEmpty(), "reentrant inspection exposed candidate receipts");
                check(!f.store.tryConfirmWallRemoval(receipt, () -> true), "reentrant confirmation entered transaction");
                check(!f.store.tryRestoreWallProjection(receipt, () -> true,
                        snapshot -> { throw new AssertionError("reentrant projection ran"); }, null),
                        "reentrant projection recovery entered a write");
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("writer release timeout"); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return true;
            }, null));
            try {
                check(entered.await(5, TimeUnit.SECONDS), "writer did not enter");
                check(f.store.tryInspectWallReceipts().isEmpty(), "busy recovery inspection waited or exposed state");
                check(!f.store.tryConfirmWallRemoval(receipt, () -> true), "busy completion waited or mutated native state");
                check(!f.store.tryRestoreWallProjection(receipt, () -> true,
                        snapshot -> { throw new AssertionError("busy projection ran"); }, null),
                        "busy projection recovery waited or mutated a physical unit");
            } finally { release.countDown(); }
            writer.get(5, TimeUnit.SECONDS);
            check(f.store.tryInspectWallReceipts().orElseThrow().equals(List.of(receipt)), "busy refusal deleted pending receipt");
        }
    }

    private static void legacySnapshotAndMalformedReceipt() throws Exception {
        try (var f = new Fixture()) {
            UUID id = f.create(); f.store.save();
            var yaml = YamlConfiguration.loadConfiguration(f.root.resolve("history.yml").toFile());
            check(yaml.getInt("schema-version") == 4, "new snapshot does not fence older binaries");
            yaml.set("schema-version", 3); YamlStore.saveAtomic(f.root.resolve("history.yml").toFile(), yaml);
            var legacy = f.fresh(); legacy.load();
            check(legacy.find(id).orElseThrow().phase().equals("base"), "legacy snapshot data was not preserved");
            legacy.save();
            new TrashHistoryJournal(LOGGER, f.root.resolve("history.wal").toFile()).append(2L,
                    "schema-version: 2\nwall-receipts: broken\n");
            var corrupted = f.fresh(); expectFailure(corrupted::load);
            check(corrupted.tryInspectWallReceipts().isEmpty(), "malformed receipt became an empty ready store");
            expectFailure(corrupted::save);
        }
    }

    private static void boundedPendingReceipts() throws Exception {
        try (var f = new Fixture()) {
            List<UUID> ids = new ArrayList<>();
            f.store.transact(() -> {
                for (int i = 0; i < 1025; i++) {
                    UUID id = UUID.randomUUID(); ids.add(id);
                    f.store.createAndRecord(id, f.brick.id(), "base", TrashHistoryEvent.CREATED_AMBIENT, ACTOR, "");
                }
                return true;
            }, null);
            List<TrashHistoryStore.WallReceipt> pending = new ArrayList<>();
            f.store.transact(() -> { for (UUID id : ids.subList(0, 1024)) pending.add(f.consumeInside(id)); return true; }, null);
            check(f.store.tryInspectWallReceipts().orElseThrow().size() == 1024, "native receipt cap denominator changed");
            expectFailure(() -> f.consume(ids.get(1024)));
            check(f.store.find(ids.get(1024)).orElseThrow().phase().equals("base"), "capacity rejection consumed an extra unit");
            check(f.store.tryInspectWallReceipts().orElseThrow().equals(pending), "capacity pressure evicted unresolved receipts");
            check(f.store.tryConfirmWallRemoval(pending.getFirst(), () -> true), "observed completion could not free capacity");
            f.consume(ids.get(1024));
            f.store.save(); var loaded = f.fresh(); loaded.load();
            check(loaded.tryInspectWallReceipts().orElseThrow().size() == 1024, "bounded pending receipts lost across compaction");
        }
    }

    private static void corruptedPendingSnapshots() throws Exception {
        for (final String field : List.of("revision", "before-revision", "projectile", "reservation", "radius", "actor", "phase")) {
            try (var f = new Fixture()) {
                var receipt = f.consume(f.create()); f.store.save();
                var yaml = YamlConfiguration.loadConfiguration(f.root.resolve("history.yml").toFile());
                final Object invalid = switch (field) {
                    case "revision" -> 3.5D;
                    case "before-revision" -> receipt.revision();
                    case "radius" -> Double.NaN;
                    case "actor" -> UUID.randomUUID().toString();
                    default -> "invalid";
                };
                yaml.set("wall-operations." + receipt.operationId() + "." + field, invalid);
                YamlStore.saveAtomic(f.root.resolve("history.yml").toFile(), yaml);
                var loaded = f.fresh(); expectFailure(loaded::load);
                check(loaded.tryInspectWallReceipts().isEmpty(), "corrupt " + field + " produced an acknowledged recovery view");
                expectFailure(loaded::save);
            }
        }
        try (var f = new Fixture()) {
            f.create(); f.store.save();
            new TrashHistoryJournal(LOGGER, f.root.resolve("history.wal").toFile()).append(2L,
                    "schema-version: 2\nremoved-wall-receipts: [" + UUID.randomUUID() + "]\n");
            var loaded = f.fresh(); expectFailure(loaded::load);
            check(loaded.tryInspectWallReceipts().isEmpty(), "unknown completion accepted as observed removal");
        }
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); }
        catch (RuntimeException | hu.taliann.icesmp.storage.CriticalPersistenceWriteError
                | hu.taliann.icesmp.storage.CorruptStateFileError expected) { assertions++; return; }
        throw new AssertionError("unsafe receipt operation was accepted");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
