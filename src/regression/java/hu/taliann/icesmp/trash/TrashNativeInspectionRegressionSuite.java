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
            historyTransaction(); historyWriteFailure(); anomalyTransaction();
            anomalyFailure(false); anomalyFailure(true);
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
        store.load();
        check(store.tryInspect(id).orElseThrow().get(LOCAL_PLAYER_DEATHS) == (afterWrite ? 5 : 2), "disk assessment observes actual replacement without replay");
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("held native write timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
    private static synchronized void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); assertions++;
    }
}
