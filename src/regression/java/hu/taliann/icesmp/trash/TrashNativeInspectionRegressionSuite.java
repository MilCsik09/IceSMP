package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.storage.CriticalPersistenceWriteError;
import hu.taliann.icesmp.storage.YamlStore;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import static hu.taliann.icesmp.trash.TrashAnomalyStateStore.MemoryKey.*;

/** Actual native stores and disk writes; inspection must never wait on their storage transaction. */
public final class TrashNativeInspectionRegressionSuite {
    private static int assertions;
    private static final Logger LOGGER = Logger.getAnonymousLogger();
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    public static void main(String[] args) throws Exception {
        try {
            TrashWallReceiptRegressionSuite.main(args);
            TrashWallHandoffRegressionSuite.main(args);
            TrashRuleCreationRegressionSuite.main(args);
            TrashDeveloperReceiptRegressionSuite.main(args);
            historyTransaction(); historyWriteFailure(); anomalyTransaction();
            anomalyFailure(false); anomalyFailure(true); historyAcknowledgementFailure(); unloadedWrites();
            nativeTryAdmission(); nativeCompactionAdmission(); nativeSingleUseAdmission();
            System.out.println("Trash native inspection passed. assertions=" + assertions);
        } finally { IO.shutdownNow(); }
    }
    private static TrashCatalog catalog() {
        final var catalog = new TrashCatalog(() -> TrashNativeInspectionRegressionSuite.class
                .getClassLoader().getResourceAsStream(TrashCatalog.RESOURCE), LOGGER);
        catalog.load(); return catalog;
    }
    private static TrashHistoryStore history(Path dir, TrashCatalog catalog) {
        return new TrashHistoryStore(dir.resolve("history.yml").toFile(), dir.resolve("history.wal").toFile(), LOGGER, catalog);
    }
    private static void historyTransaction() throws Exception {
        final var dir = Files.createTempDirectory("trash-inspection-history-");
        final var catalog = catalog(); final var store = history(dir, catalog);
        final var base = catalog.snapshot().keySet().stream().sorted().findFirst().orElseThrow();
        final UUID first = UUID.randomUUID(), second = UUID.randomUUID(), actor = UUID.randomUUID();
        check(store.tryInspect(first).isEmpty(), "unloaded store is unavailable, not an absent instance");
        store.load();
        check(store.tryInspect(first).orElseThrow().history().isEmpty(), "loaded absence is explicit");
        final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1);
        final var write = IO.submit(() -> store.transact(() -> {
            store.createAndRecord(first, base, "base", TrashHistoryEvent.CREATED_AMBIENT, actor, "");
            store.createAndRecord(second, base, "base", TrashHistoryEvent.CREATED_AMBIENT, actor, "");
            check(store.tryInspect(first).isEmpty(), "reentrant read cannot expose unacknowledged history");
            entered.countDown(); await(release); return true;
        }, null));
        try {
            check(entered.await(5, TimeUnit.SECONDS), "native transaction entered");
            check(IO.submit(() -> store.tryInspect(first).isEmpty() && store.tryInspect(second).isEmpty()).get(2, TimeUnit.SECONDS),
                    "owner inspection returns unavailable during multi-item write without waiting");
        } finally { release.countDown(); }
        check(write.get(5, TimeUnit.SECONDS), "native journal acknowledged");
        final var before = store.tryInspect(first).orElseThrow();
        check(before.sequence() == 1 && before.history().orElseThrow().revision() == 1, "native acknowledgement exposes complete history generation");
        final byte[] diskBefore = Files.readAllBytes(dir.resolve("history.wal"));
        try {
            store.transact(() -> {
                store.record(first, base, "base", TrashHistoryEvent.REPAIRED, actor, "");
                throw new IllegalArgumentException("expected domain refusal");
            }, null);
            throw new AssertionError("domain refusal lost");
        } catch (IllegalArgumentException expected) { assertions++; }
        check(store.tryInspect(first).orElseThrow().equals(before), "known rollback preserves readable acknowledged state");
        check(Arrays.equals(diskBefore, Files.readAllBytes(dir.resolve("history.wal"))), "rejected candidate writes no history");
        store.transact(() -> store.record(first, base, "base", TrashHistoryEvent.REPAIRED, actor, ""), null);
        final var after = store.tryInspect(first).orElseThrow();
        check(after.sequence() == 2 && after.history().orElseThrow().revision() == 2, "new acknowledged revision visible");
        check(before.history().orElseThrow().events().size() == 1, "old read snapshot is immutable");
        try { after.history().orElseThrow().events().clear(); throw new AssertionError("mutable history"); }
        catch (UnsupportedOperationException expected) { assertions++; }
        final var reload = history(dir, catalog); reload.load();
        check(reload.tryInspect(first).equals(store.tryInspect(first)) && reload.tryInspect(second).equals(store.tryInspect(second)), "real WAL reload preserves both instances");
        store.save(); reload.load();
        check(reload.tryInspect(first).equals(store.tryInspect(first)), "snapshot and compacted WAL retain read state");
    }
    private static void historyWriteFailure() throws Exception {
        final var dir = Files.createTempDirectory("trash-inspection-failure-");
        final var catalog = catalog(); final var store = history(dir, catalog); store.load();
        final UUID id = UUID.randomUUID(); final String base = catalog.snapshot().keySet().iterator().next();
        Files.createDirectory(dir.resolve("history.wal"));
        try {
            store.transact(() -> store.createAndRecord(id, base, "base", TrashHistoryEvent.CREATED_AMBIENT, UUID.randomUUID(), ""), null);
            throw new AssertionError("failed native journal accepted");
        } catch (CriticalPersistenceWriteError expected) { assertions++; }
        check(store.find(id).isEmpty(), "failed write rolled back native working state");
        check(store.tryInspect(id).isEmpty(), "failed acknowledgement cannot be presented as healthy absence");
        refuses(() -> store.transact(() -> { throw new AssertionError("fenced mutation entered"); }, null));
        refuses(store::save);
        Files.delete(dir.resolve("history.wal")); store.load();
        check(store.tryInspect(id).orElseThrow().history().isEmpty(), "explicit disk assessment restores known absence");
    }
    private static void anomalyTransaction() throws Exception {
        final var file = Files.createTempDirectory("trash-inspection-memory-").resolve("memory.yml").toFile(); final UUID id = UUID.randomUUID();
        final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1);
        final var reference = new AtomicReference<TrashAnomalyStateStore>();
        final var store = new TrashAnomalyStateStore(file, LOGGER, (target, yaml) -> {
            check(reference.get().tryInspect(id).isEmpty(), "reentrant memory read refuses in-flight write");
            entered.countDown(); await(release); YamlStore.saveAtomic(target, yaml);
        });
        reference.set(store);
        check(store.tryInspect(id).isEmpty(), "unloaded memory is unavailable");
        store.load(); final var empty = store.tryInspect(id).orElseThrow();
        check(empty.isEmpty(), "loaded empty memory is explicit");
        final var write = IO.submit(() -> store.addDurably(id, LOCAL_PLAYER_DEATHS, 1));
        try {
            check(entered.await(5, TimeUnit.SECONDS), "native memory write entered");
            check(IO.submit(() -> store.tryInspect(id).isEmpty()).get(2, TimeUnit.SECONDS), "memory inspection does not wait for actual storage");
        } finally { release.countDown(); }
        check(write.get(5, TimeUnit.SECONDS) == 1, "memory write acknowledged");
        final var snapshot = store.tryInspect(id).orElseThrow();
        check(snapshot.equals(Map.of(LOCAL_PLAYER_DEATHS, 1L)) && empty.isEmpty(), "detached actual memory");
        store.add(id, WATCHED_TICKS, 10);
        check(!snapshot.containsKey(WATCHED_TICKS), "runtime additions cannot alter an earlier view");
        try { snapshot.clear(); throw new AssertionError("mutable memory"); }
        catch (UnsupportedOperationException expected) { assertions++; }
        final var reloaded = new TrashAnomalyStateStore(file, LOGGER); reloaded.load();
        check(reloaded.tryInspect(id).orElseThrow().equals(snapshot), "runtime inspection does not claim unsaved watched ticks survived restart");
    }
    private static void anomalyFailure(boolean afterWrite) throws Exception {
        final var file = Files.createTempDirectory("trash-memory-failure-").resolve("memory.yml").toFile();
        final UUID id = UUID.randomUUID(); final var fail = new AtomicBoolean();
        final var store = new TrashAnomalyStateStore(file, LOGGER, (target, yaml) -> {
            if (fail.get() && !afterWrite) throw new CriticalPersistenceWriteError(target, new java.io.IOException("before write"));
            YamlStore.saveAtomic(target, yaml);
            if (fail.get()) throw new CriticalPersistenceWriteError(target, new java.io.IOException("after write"));
        });
        store.load(); store.addDurably(id, LOCAL_PLAYER_DEATHS, 2); fail.set(true);
        try { store.addDurably(id, LOCAL_PLAYER_DEATHS, 3); throw new AssertionError("failure lost"); }
        catch (CriticalPersistenceWriteError expected) { assertions++; }
        check(store.get(id, LOCAL_PLAYER_DEATHS) == 2, "critical Error restores working memory just as RuntimeException does");
        check(store.tryInspect(id).isEmpty(), "ambiguous memory cannot be used as authoritative inspection");
        final byte[] uncertainDisk = Files.readAllBytes(file.toPath());
        fail.set(false);
        refuses(() -> store.add(id, WATCHED_TICKS, 7));
        refuses(() -> store.addDurably(id, LOCAL_PLAYER_DEATHS, 7));
        refuses(store::save);
        check(Arrays.equals(uncertainDisk, Files.readAllBytes(file.toPath())), "fenced save cannot overwrite an uncertain atomic replacement");
        check(store.get(id, LOCAL_PLAYER_DEATHS) == 2 && store.get(id, WATCHED_TICKS) == 0,
                "fenced mutations leave rolled-back memory untouched");
        store.load();
        check(store.tryInspect(id).orElseThrow().get(LOCAL_PLAYER_DEATHS) == (afterWrite ? 5 : 2), "disk assessment observes actual replacement without replay");
        check(store.addDurably(id, LOCAL_PLAYER_DEATHS, 1) == (afterWrite ? 6 : 3), "acknowledged reload reopens native mutation admission");
    }

    private static void historyAcknowledgementFailure() throws Exception {
        final var dir = Files.createTempDirectory("trash-history-ack-failure-");
        final var catalog = catalog(); final UUID id = UUID.randomUUID();
        final String base = catalog.snapshot().keySet().iterator().next();
        final var fail = new AtomicBoolean();
        final var store = new TrashHistoryStore(dir.resolve("history.yml").toFile(),
                dir.resolve("history.wal").toFile(), LOGGER, catalog, (journal, sequence, payload) -> {
                    journal.append(sequence, payload);
                    if (fail.get()) throw new CriticalPersistenceWriteError(dir.resolve("history.wal").toFile(),
                            new java.io.IOException("lost acknowledgement after actual fsync"));
                });
        store.load();
        store.transact(() -> store.createAndRecord(id, base, "base", TrashHistoryEvent.CREATED_AMBIENT, null, ""), null);
        final var acknowledged = store.tryInspect(id).orElseThrow();
        fail.set(true); final var restored = new AtomicBoolean();
        try {
            store.transact(() -> store.record(id, base, "base", TrashHistoryEvent.REPAIRED, null, ""), () -> restored.set(true));
            throw new AssertionError("uncertain actual WAL append accepted");
        } catch (CriticalPersistenceWriteError expected) { assertions++; }
        check(restored.get() && store.find(id).orElseThrow().revision() == acknowledged.history().orElseThrow().revision(),
                "uncertain append rolls memory and external projection back");
        final byte[] uncertainDisk = Files.readAllBytes(dir.resolve("history.wal"));
        fail.set(false);
        refuses(() -> store.transact(() -> { throw new AssertionError("uncertain history accepted another mutation"); }, null));
        refuses(() -> store.tryTransact(() -> true, () -> { throw new AssertionError("uncertain history accepted try-mutation"); }, null));
        refuses(store::save);
        check(Arrays.equals(uncertainDisk, Files.readAllBytes(dir.resolve("history.wal")))
                && !Files.exists(dir.resolve("history.yml")), "fence prevents duplicate sequence append and rolled-back snapshot compaction");
        check(store.tryInspect(id).isEmpty(), "rejected write cannot clear the uncertainty fence");
        store.load();
        check(store.tryInspect(id).orElseThrow().sequence() == acknowledged.sequence() + 1
                && store.find(id).orElseThrow().revision() == acknowledged.history().orElseThrow().revision() + 1,
                "load observes the actual fsynced event once without replaying mutation");
        store.transact(() -> store.record(id, base, "base", TrashHistoryEvent.REPAIRED, null, ""), null);
        store.save(); final var reloaded = history(dir, catalog); reloaded.load();
        check(reloaded.tryInspect(id).equals(store.tryInspect(id)), "post-assessment append and compaction remain recoverable");
    }

    private static void unloadedWrites() throws Exception {
        final var dir = Files.createTempDirectory("trash-unloaded-writes-"); final UUID id = UUID.randomUUID();
        final var history = history(dir, catalog());
        final var memory = new TrashAnomalyStateStore(dir.resolve("memory.yml").toFile(), LOGGER);
        refuses(() -> history.transact(() -> { throw new AssertionError("unloaded history mutation entered"); }, null));
        refuses(() -> history.tryTransact(() -> true, () -> { throw new AssertionError("unloaded try-mutation entered"); }, null));
        refuses(history::save);
        refuses(() -> memory.add(id, WATCHED_TICKS, 1));
        refuses(() -> memory.addDurably(id, LOCAL_PLAYER_DEATHS, 1));
        refuses(memory::save);
        try (final var files = Files.list(dir)) { check(files.findAny().isEmpty(), "unloaded stores wrote authority files"); }
    }

    private static void refuses(Runnable write) {
        try { write.run(); throw new AssertionError("unassessed store accepted a write"); }
        catch (IllegalStateException expected) { assertions++; }
    }

    private static void nativeTryAdmission() throws Exception {
        final var dir = Files.createTempDirectory("trash-try-admission-"); final var catalog = catalog();
        final var store = history(dir, catalog); store.load(); final UUID id = UUID.randomUUID();
        final String base = catalog.snapshot().keySet().iterator().next();
        final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1);
        final Runnable unexpected = () -> { throw new AssertionError("unadmitted mutation/rollback ran"); };
        final var holder = IO.submit(() -> store.transact(() -> {
            check(!store.tryTransact(() -> { throw new AssertionError("reentrant admission called"); }, unexpected, unexpected),
                    "reentrant try-transaction refused without touching the existing transaction");
            entered.countDown(); await(release); return true;
        }, null));
        try {
            check(entered.await(5, TimeUnit.SECONDS), "native writer held");
            check(!IO.submit(() -> store.tryTransact(() -> { throw new AssertionError("busy admission called"); }, unexpected, unexpected))
                    .get(2, TimeUnit.SECONDS), "owner try-transaction refuses another writer without waiting");
        } finally { release.countDown(); }
        check(holder.get(5, TimeUnit.SECONDS), "existing native writer is unaffected");
        check(!store.tryTransact(() -> false, unexpected, unexpected), "initial lifecycle rejection enters no projection");
        check(!Files.exists(dir.resolve("history.wal")), "unadmitted operation creates no WAL");
        final var admissionCalls = new AtomicInteger();
        check(!store.tryTransact(() -> admissionCalls.incrementAndGet() == 1, unexpected, unexpected)
                && admissionCalls.get() == 2, "final lifecycle recheck can reject before mutation");
        check(store.tryTransact(() -> true, () -> store.createAndRecord(id, base, "base", TrashHistoryEvent.CREATED_AMBIENT, null, ""), unexpected),
                "admitted try-transaction writes through canonical native WAL");
        final var acknowledged = store.tryInspect(id).orElseThrow();
        final byte[] before = Files.readAllBytes(dir.resolve("history.wal")); final var restored = new AtomicBoolean();
        final var failure = new IllegalArgumentException("projection refusal");
        try {
            store.tryTransact(() -> true, () -> {
                store.record(id, base, "base", TrashHistoryEvent.REPAIRED, null, ""); throw failure;
            }, () -> restored.set(true));
            throw new AssertionError("try-transaction swallowed projection failure");
        } catch (IllegalArgumentException actual) { check(actual == failure, "try-transaction preserves original failure"); }
        check(restored.get() && store.tryInspect(id).orElseThrow().equals(acknowledged)
                && Arrays.equals(before, Files.readAllBytes(dir.resolve("history.wal"))), "try-transaction keeps native item/history rollback semantics");
    }

    private static void nativeCompactionAdmission() throws Exception {
        final var dir = Files.createTempDirectory("trash-compaction-admission-"); final var catalog = catalog();
        final var store = history(dir, catalog); store.load(); final UUID id = UUID.randomUUID();
        final String base = catalog.snapshot().keySet().iterator().next();
        store.transact(() -> store.createAndRecord(id, base, "base", TrashHistoryEvent.CREATED_AMBIENT, null, ""), null);
        for (int i = 1; i < 1024; i++) store.transact(() -> store.record(id, base, "base", TrashHistoryEvent.REPAIRED, null, ""), null);
        final var before = store.tryInspect(id).orElseThrow();
        final Runnable unexpected = () -> { throw new AssertionError("post-compaction refused projection/rollback ran"); };
        check(!Files.exists(dir.resolve("history.yml")) && before.sequence() == 1024, "real journal reaches native compaction boundary");
        check(!store.tryTransact(() -> !Files.exists(dir.resolve("history.yml")), unexpected, unexpected),
                "lifecycle is rechecked after actual snapshot write and WAL compaction");
        check(store.tryInspect(id).orElseThrow().equals(before) && Files.size(dir.resolve("history.wal")) == 0,
                "compaction preserves acknowledged history without committing the refused request");
        final var reload = history(dir, catalog); reload.load();
        check(reload.tryInspect(id).orElseThrow().equals(before), "actual compacted state reloads exactly");
    }
    private static void nativeSingleUseAdmission() throws Exception {
        final var dir = Files.createTempDirectory("trash-single-use-admission-"); final var catalog = catalog();
        final var store = history(dir, catalog); store.load();
        final String base = catalog.snapshot().keySet().iterator().next(); final UUID id = UUID.randomUUID();
        final var repeat = new AtomicInteger(); final var claims = new AtomicInteger();
        final var permit = hu.taliann.icesmp.integrity.GameplayEffectPermit.guarded(() -> claims.incrementAndGet() == 1);
        final Runnable unexpected = () -> { throw new AssertionError("unadmitted mutation or rollback"); };
        check(!store.tryTransact(() -> false, permit::claim, unexpected, unexpected) && claims.get() == 0,
                "initial refusal consumed a single-use permit");
        check(!store.tryTransact(() -> repeat.incrementAndGet() == 1, permit::claim, unexpected, unexpected)
                        && repeat.get() == 2 && claims.get() == 0,
                "freshness rejection consumed a final permit");
        final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1);
        final var holder = IO.submit(() -> store.transact(() -> {
            check(!store.tryTransact(() -> true, permit::claim, unexpected, unexpected), "reentrant permit admitted");
            entered.countDown(); await(release); return true;
        }, null));
        try {
            check(entered.await(5, TimeUnit.SECONDS), "writer held before final permit");
            check(!IO.submit(() -> store.tryTransact(() -> true, permit::claim, unexpected, unexpected)).get(2, TimeUnit.SECONDS),
                    "busy writer waited or consumed final permit");
            check(claims.get() == 0, "busy/reentrant refusal consumed permit");
        } finally { release.countDown(); }
        holder.get(5, TimeUnit.SECONDS);
        repeat.set(0);
        check(store.tryTransact(() -> repeat.incrementAndGet() <= 2, permit::claim,
                () -> store.createAndRecord(id, base, "base", TrashHistoryEvent.ACTIVATED, null, ""), unexpected),
                "single-use permit cannot reach native WAL");
        check(repeat.get() == 2 && claims.get() == 1, "final permit evaluated as repeatable admission");
        final byte[] committed = Files.readAllBytes(dir.resolve("history.wal"));
        check(!store.tryTransact(() -> true, permit::claim, unexpected, unexpected), "consumed permit replayed native mutation");
        check(Arrays.equals(committed, Files.readAllBytes(dir.resolve("history.wal"))), "rejected permit wrote another WAL frame");
        for (int i = 1; i < 1024; i++) store.transact(() -> store.record(id, base, "base", TrashHistoryEvent.REPAIRED, null, ""), null);
        final var compactPermit = hu.taliann.icesmp.integrity.GameplayEffectPermit.guarded(
                () -> Files.exists(dir.resolve("history.yml")) && Files.exists(dir.resolve("history.wal")));
        check(store.tryTransact(() -> true, compactPermit::claim,
                () -> store.record(id, base, "base", TrashHistoryEvent.ACTIVATED, null, ""), unexpected),
                "final permit did not run after actual compaction");
        final var loaded = history(dir, catalog); loaded.load();
        check(loaded.find(id).orElseThrow().revision() == 1025, "compacted permitted transaction not durable");
        final var lostDir = Files.createTempDirectory("trash-permit-lost-ack-");
        final var lost = new TrashHistoryStore(lostDir.resolve("history.yml").toFile(), lostDir.resolve("history.wal").toFile(),
                LOGGER, catalog, (journal, sequence, payload) -> { journal.append(sequence, payload); throw new IllegalStateException("lost ack"); });
        lost.load(); final var external = new AtomicInteger();
        final var lostPermit = hu.taliann.icesmp.integrity.GameplayEffectPermit.guarded(() -> true);
        refuses(() -> lost.tryTransact(() -> true, lostPermit::claim, () -> {
            lost.createAndRecord(id, base, "base", TrashHistoryEvent.ACTIVATED, null, ""); external.set(1);
        }, () -> external.set(0)));
        check(external.get() == 0 && !lostPermit.claim(), "lost acknowledgement rearmed permit or lost external rollback");
        check(lost.tryInspect(id).isEmpty(), "unassessed history became visible after permit consumption");
        final var recovered = history(lostDir, catalog); recovered.load();
        check(recovered.find(id).orElseThrow().revision() == 1, "real fsynced permitted mutation disappeared");
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("held native write timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
    private static synchronized void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); assertions++;
    }
}
