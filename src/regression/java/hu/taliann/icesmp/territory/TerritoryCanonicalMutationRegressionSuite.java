package hu.taliann.icesmp.territory;

import hu.taliann.icesmp.data.BlockCuboid;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import static hu.taliann.icesmp.territory.TerritoryAdjustmentResult.Status.*;

/** Real native TerritoryManager, real YAML, injected disk faults; no fake Bukkit domain events. */
public final class TerritoryCanonicalMutationRegressionSuite {
    private static int assertions;
    private enum Fault { NONE, BEFORE, AFTER }
    private static final class Fixture {
        final Path file = Files.createTempDirectory("territory-canonical-").resolve("territories.yml");
        final AtomicReference<Fault> fault = new AtomicReference<>(Fault.NONE);
        final AtomicInteger writes = new AtomicInteger();
        final TerritoryManager manager;
        Fixture() throws IOException {
            manager = new TerritoryManager(file.toFile(), Logger.getAnonymousLogger(), (target, yaml) -> {
                writes.incrementAndGet();
                if (fault.get() == Fault.BEFORE) throw new IOException("injected before replacement");
                YamlStore.saveAtomic(target, yaml);
                if (fault.get() == Fault.AFTER) throw new IOException("injected after replacement");
            });
            manager.load();
            manager.defineCuboid("zone", FactionType.NEUTRAL, "Before", TerritoryType.PROTECTED_CITY,
                    BlockCuboid.between("world", -2, 4, -2, 2, 9, 2));
        }
        TerritoryManager reload() {
            final var result = new TerritoryManager(file.toFile(), Logger.getAnonymousLogger(), YamlStore::saveAtomic);
            result.load(); return result;
        }
    }

    public static void main(String[] args) throws Exception {
        immutableGeometry(); fieldMutationsAndReplay(); finalAdmission(); externalDriftAndCompensation();
        faultRecovery(Fault.BEFORE); faultRecovery(Fault.AFTER); atomicNativePublication(); contention(); corruptReceipt(); receiptCapacity();
        System.out.println("Territory canonical mutation passed. assertions=" + assertions);
    }

    private static void immutableGeometry() {
        final int[] point = {0, 0}; final List<int[]> points = new ArrayList<>(List.of(point, new int[]{4, 0}, new int[]{0, 4}));
        final Territory before = new Territory("id", FactionType.NEUTRAL, "Name", TerritoryType.FACTION,
                "world", 1, 1, 8, points, 0, 10);
        final String revision = TerritoryRevision.fingerprint(before); point[0] = 100; points.clear();
        before.polygon().getFirst()[0] = 200;
        check(TerritoryRevision.fingerprint(before).equals(revision), "constructor/accessor geometry aliases cannot mutate revision");
        final Territory equivalent = new Territory("id", FactionType.NEUTRAL, "Name", TerritoryType.FACTION,
                "world", 1, 1, 8, List.of(new int[]{0, 0}, new int[]{4, 0}, new int[]{0, 4}), 0, 10);
        check(before.equals(equivalent) && before.hashCode() == equivalent.hashCode(), "geometry equality is structural");
    }

    private static void fieldMutationsAndReplay() throws Exception {
        final var fixture = new Fixture(); final var manager = fixture.manager;
        final List<TerritoryAdjustment> changes = List.of(new TerritoryAdjustment.Rename("Renamed"),
                new TerritoryAdjustment.SetType(TerritoryType.FACTION), new TerritoryAdjustment.SetOwner(FactionType.DARK),
                new TerritoryAdjustment.SetYBounds(5, 8));
        for (final var change : changes) {
            final Territory before = manager.getById("zone"); final String revision = TerritoryRevision.fingerprint(before); final UUID id = UUID.randomUUID();
            final var applied = manager.adjustConditionally(id, "zone", revision, change, 100, () -> true);
            check(applied.status() == APPLIED, "typed mutation applied");
            final Territory after = manager.getById("zone");
            check(after.equals(change.apply(before)), "only selected canonical field changes");
            check(after.world().equals(before.world()) && after.x() == before.x() && after.z() == before.z()
                    && after.radius() == before.radius() && after.polygon().size() == before.polygon().size(), "shape/identity preserved");
            final int writes = fixture.writes.get();
            check(manager.adjustConditionally(id, "zone", revision, change, 101, () -> true).status() == ALREADY_APPLIED, "exact native receipt replay");
            check(fixture.writes.get() == writes, "replay writes nothing");
            check(manager.adjustConditionally(id, "zone", revision, new TerritoryAdjustment.Rename("Different"), 101, () -> true).status() == CONFLICT,
                    "operation UUID cannot be reused with another request");
            final var reloaded = fixture.reload();
            check(reloaded.getById("zone").equals(after) && reloaded.adjustmentReceipt(id).equals(applied.receipt()), "state and real receipt survive restart together");
        }
    }

    private static void finalAdmission() throws Exception {
        final var fixture = new Fixture(); final var manager = fixture.manager; final var before = manager.getById("zone");
        final byte[] bytes = Files.readAllBytes(fixture.file); final int writes = fixture.writes.get();
        for (int denyAt = 1; denyAt <= 3; denyAt++) {
            final int boundary = denyAt; final AtomicInteger checks = new AtomicInteger(); final UUID id = UUID.randomUUID();
            check(manager.adjustConditionally(id, "zone", TerritoryRevision.fingerprint(before), new TerritoryAdjustment.Rename("Denied"),
                    100, () -> checks.incrementAndGet() < boundary).status() == DENIED, "final admission boundary " + boundary);
            check(manager.getById("zone").equals(before) && manager.adjustmentReceipt(id).isEmpty(), "denial publishes no staged state or receipt");
            check(manager.adjustmentStateAvailable(), "pre-write denial is not ambiguous disk failure");
        }
        check(writes == fixture.writes.get() && Arrays.equals(bytes, Files.readAllBytes(fixture.file)), "denied admission writes no bytes");
    }

    private static void externalDriftAndCompensation() throws Exception {
        final var f = new Fixture(); final var manager = f.manager; final var original = manager.getById("zone");
        final UUID first = UUID.randomUUID(); final var rename = new TerritoryAdjustment.Rename("Applied");
        final var applied = manager.adjustConditionally(first, "zone", TerritoryRevision.fingerprint(original), rename, 100, () -> true);
        final String undoFingerprint = applied.receipt().orElseThrow().afterFingerprint();
        manager.rename("zone", "External"); final byte[] drift = Files.readAllBytes(f.file);
        check(manager.adjustConditionally(UUID.randomUUID(), "zone", undoFingerprint, new TerritoryAdjustment.Rename("Before"), 101, () -> true).status() == CONFLICT,
                "conditional undo never overwrites external drift");
        check(Arrays.equals(drift, Files.readAllBytes(f.file)), "conflict has no persistence effect");
        check(manager.adjustConditionally(first, "zone", TerritoryRevision.fingerprint(original), rename, 102, () -> true).status() == ALREADY_APPLIED,
                "original acknowledgement remains available after later native mutation");
        check(manager.getById("zone").name().equals("External"), "receipt lookup/retry is not replay of original mutation");
        final var fresh = new Fixture(); final var freshBefore = fresh.manager.getById("zone"); final UUID operation = UUID.randomUUID();
        final var done = fresh.manager.adjustConditionally(operation, "zone", TerritoryRevision.fingerprint(freshBefore), rename, 100, () -> true);
        final UUID compensation = UUID.randomUUID();
        check(fresh.manager.adjustConditionally(compensation, "zone", done.receipt().orElseThrow().afterFingerprint(),
                new TerritoryAdjustment.Rename("Before"), 101, () -> true).status() == APPLIED, "matching undo is a new compensating mutation");
        final var reload = fresh.reload();
        check(reload.adjustmentReceipt(operation).isPresent() && reload.adjustmentReceipt(compensation).isPresent(), "compensation preserves both canonical receipts");
    }

    private static void faultRecovery(final Fault fault) throws Exception {
        final var f = new Fixture(); final var before = f.manager.getById("zone"); final String revision = TerritoryRevision.fingerprint(before);
        final UUID id = UUID.randomUUID(); final var change = new TerritoryAdjustment.Rename("After crash"); final byte[] initial = Files.readAllBytes(f.file);
        f.fault.set(fault);
        expectFailure(() -> f.manager.adjustConditionally(id, "zone", revision, change, 100, () -> true));
        check(f.manager.getById("zone").equals(before) && f.manager.adjustmentReceipt(id).isEmpty(), "failed write does not publish staged state");
        check(f.manager.getTerritoryColumnAt("world", 0, 0).equals(before), "failed write does not publish staged index");
        check(!f.manager.adjustmentStateAvailable(), "uncertain write fences later canonical writes");
        final byte[] uncertain = Files.readAllBytes(f.file); expectFailure(f.manager::save);
        check(Arrays.equals(uncertain, Files.readAllBytes(f.file)), "autosave cannot overwrite a possibly acknowledged replacement");
        final var recovered = f.reload();
        if (fault == Fault.BEFORE) {
            check(Arrays.equals(initial, uncertain), "pre-replace crash preserves bytes");
            check(recovered.getById("zone").equals(before) && recovered.adjustmentReceipt(id).isEmpty(), "pre-replace recovery observes before state");
        } else {
            check(recovered.getById("zone").equals(change.apply(before)) && recovered.adjustmentReceipt(id).isPresent(), "post-replace recovery observes actual applied receipt/state");
            check(recovered.adjustConditionally(id, "zone", revision, change, 101, () -> true).status() == ALREADY_APPLIED, "post-crash retry is acknowledgement only");
        }
    }

    private static void atomicNativePublication() throws Exception {
        final Path file = Files.createTempDirectory("territory-publication-").resolve("territories.yml");
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1); final AtomicBoolean block = new AtomicBoolean();
        final var manager = new TerritoryManager(file.toFile(), Logger.getAnonymousLogger(), (target, yaml) -> {
            if (block.get()) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test release timeout"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            YamlStore.saveAtomic(target, yaml);
        });
        manager.load(); manager.defineCuboid("old", FactionType.DARK, "Old", TerritoryType.CAPITAL, BlockCuboid.between("world", 0, 0, 0, 2, 2, 2));
        manager.defineCuboid("new", FactionType.DARK, "New", TerritoryType.FACTION, BlockCuboid.between("world", 20, 0, 20, 22, 2, 22));
        check(manager.adjustConditionally(UUID.randomUUID(), "new", TerritoryRevision.fingerprint(manager.getById("new")),
                new TerritoryAdjustment.SetType(TerritoryType.CAPITAL), 100, () -> true).status() == CAPITAL_CONFLICT, "bounded adjustment cannot silently demote another capital");
        final var retained = manager.all(); block.set(true); final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<Territory> mutation = executor.submit(() -> manager.setType("new", TerritoryType.CAPITAL));
            check(entered.await(5, TimeUnit.SECONDS), "native mutation entered storage");
            check(manager.getCapital(FactionType.DARK).id().equals("old") && manager.getById("new").type() == TerritoryType.FACTION,
                    "canonical map remains old until write acknowledgement");
            check(manager.getTerritoryColumnAt("world", 20, 20).type() == TerritoryType.FACTION, "index remains same acknowledged generation");
            release.countDown(); mutation.get(5, TimeUnit.SECONDS);
            check(manager.getCapital(FactionType.DARK).id().equals("new") && manager.getById("old").type() == TerritoryType.FACTION, "native capital workflow publishes complete demotion/promotion");
            check(retained.stream().filter(Territory::capital).findFirst().orElseThrow().id().equals("old"), "previous all() snapshot remains immutable");
            try { retained.clear(); throw new AssertionError("mutable canonical view"); } catch (UnsupportedOperationException expected) { assertions++; }
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private static void contention() throws Exception {
        final var f = new Fixture(); final String revision = TerritoryRevision.fingerprint(f.manager.getById("zone"));
        final ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            final List<Future<TerritoryAdjustmentResult>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) { final String name = "Candidate " + i; futures.add(executor.submit(() ->
                    f.manager.adjustConditionally(UUID.randomUUID(), "zone", revision, new TerritoryAdjustment.Rename(name), 100, () -> true))); }
            int applied = 0, conflicts = 0;
            for (final var future : futures) { final var status = future.get(5, TimeUnit.SECONDS).status(); if (status == APPLIED) applied++; else if (status == CONFLICT) conflicts++; }
            check(applied == 1 && conflicts == 7 && f.writes.get() == 2, "only one same-fingerprint canonical writer commits");
        } finally { executor.shutdownNow(); }
    }

    private static void corruptReceipt() throws Exception {
        final var f = new Fixture(); final var yaml = YamlConfiguration.loadConfiguration(f.file.toFile());
        yaml.set("developer-adjustments." + UUID.randomUUID() + ".territory", "zone"); YamlStore.saveAtomic(f.file.toFile(), yaml);
        final byte[] corrupt = Files.readAllBytes(f.file); expectFailure(f::reload);
        check(Arrays.equals(corrupt, Files.readAllBytes(f.file)), "malformed canonical receipt is not silently dropped or rewritten");
        for (final String field : List.of("territory", "before", "after", "request", "committed-at")) {
            final var fixture = new Fixture(); final var invalid = YamlConfiguration.loadConfiguration(fixture.file.toFile());
            final String key = UUID.randomUUID().toString(); addReceipt(invalid, key);
            invalid.set("developer-adjustments." + key + "." + field, field.equals("committed-at") ? "100" : 100);
            YamlStore.saveAtomic(fixture.file.toFile(), invalid); expectFailure(fixture::reload);
        }
        for (final String key : List.of("1-1-1-1-1", UUID.randomUUID().toString())) {
            final var fixture = new Fixture(); final var invalid = YamlConfiguration.loadConfiguration(fixture.file.toFile());
            addReceipt(invalid, key);
            if (key.length() == 36) invalid.set("developer-adjustments." + key + ".after", "a".repeat(64));
            YamlStore.saveAtomic(fixture.file.toFile(), invalid); expectFailure(fixture::reload);
        }
    }

    private static void addReceipt(YamlConfiguration yaml, String key) {
        final String base = "developer-adjustments." + key;
        yaml.set(base + ".territory", "zone"); yaml.set(base + ".before", "a".repeat(64));
        yaml.set(base + ".after", "b".repeat(64)); yaml.set(base + ".request", "c".repeat(64));
        yaml.set(base + ".committed-at", 100L);
    }

    private static void receiptCapacity() throws Exception {
        final var fixture = new Fixture(); final var yaml = YamlConfiguration.loadConfiguration(fixture.file.toFile());
        for (int i = 0; i < 4096; i++) addReceipt(yaml, new UUID(0, i).toString());
        YamlStore.saveAtomic(fixture.file.toFile(), yaml);
        final var manager = fixture.reload(); final byte[] bytes = Files.readAllBytes(fixture.file);
        check(manager.adjustmentReceipt(new UUID(0, 0)).isPresent(), "capacity retains oldest acknowledgement");
        check(manager.adjustConditionally(UUID.randomUUID(), "zone", TerritoryRevision.fingerprint(manager.getById("zone")),
                new TerritoryAdjustment.Rename("Over capacity"), 101, () -> true).status() == CAPACITY, "full ledger fails closed");
        check(Arrays.equals(bytes, Files.readAllBytes(fixture.file)), "capacity does not evict history or mutate state");
        addReceipt(yaml, new UUID(0, 4096).toString()); YamlStore.saveAtomic(fixture.file.toFile(), yaml);
        expectFailure(fixture::reload);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
    private static void expectFailure(Runnable operation) {
        try { operation.run(); throw new AssertionError("failure expected"); } catch (RuntimeException expected) { assertions++; }
    }
}
