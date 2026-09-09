package hu.taliann.icesmp.trash;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/** Deterministic owner queues over real native fields/tracking/WAL; not a connected Folia region test. */
public final class TrashWallHandoffRegressionSuite {
    private static int assertions;
    private static final Logger LOGGER = Logger.getAnonymousLogger();
    private static final ThreadLocal<String> OWNER = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        successAndDuplicateCallbacks(false); successAndDuplicateCallbacks(true);
        for (String failure : List.of("inventory-reject", "inventory-retire", "inventory-refuse", "projectile-reject",
                "projectile-retire", "projectile-drift", "remove-unobserved", "expiry", "shutdown")) refusal(failure);
        staleClaim(); heldWriter(); shutdownDuringAcknowledgement(); lostAcknowledgement();
        schedulingFailure(false); schedulingFailure(true);
        System.out.println("Trash wall owner handoff passed. assertions=" + assertions);
    }
    private static final class Queue {
        final String name;
        Runnable action, retired;
        boolean accept = true;
        RuntimeException failure;
        Queue(String name) { this.name = name; }
        boolean schedule(Runnable action, Runnable retired) {
            if (failure != null) throw failure;
            if (!accept) return false;
            check(this.action == null, "owner hop was queued twice");
            this.action = action; this.retired = retired; return true;
        }
        void run() {
            final String previous = OWNER.get(); OWNER.set(name);
            try { action.run(); } finally { if (previous == null) OWNER.remove(); else OWNER.set(previous); }
        }
    }
    private static final class Fixture implements AutoCloseable {
        final Path root = Files.createTempDirectory("trash-native-handoff-");
        final TrashCatalog catalog;
        final TrashDefinition brick;
        final AtomicLong now = new AtomicLong(1000);
        final UUID actor = UUID.randomUUID(), world = UUID.randomUUID(), projectile = UUID.randomUUID(), instance = UUID.randomUUID();
        final TrashRuleFieldService fields = new TrashRuleFieldService(now::get);
        final TrashRelicPolicy.ProjectileTracking tracking = new TrashRelicPolicy.ProjectileTracking(256);
        final TrashRuleFieldService.RuleField field = new TrashRuleFieldService.RuleField(UUID.randomUUID(),
                TrashRuleFieldService.FieldKind.PROJECTILE_WALL, new TrashRuleFieldService.Point(world, 1, 64, 2),
                2.5, 2000, actor, UUID.randomUUID().toString());
        final TrashRuleFieldService.FieldClaim claim;
        final TrashRelicPolicy.ProjectileTracking.Ticket ticket = tracking.admit(projectile);
        final Queue inventory = new Queue("inventory"), projectileQueue = new Queue("projectile");
        final AtomicBoolean open = new AtomicBoolean(true), inventoryReady = new AtomicBoolean(true), projectileReady = new AtomicBoolean(true);
        final AtomicBoolean observed = new AtomicBoolean(true), pauseAppend = new AtomicBoolean();
        final AtomicBoolean failAcknowledgement = new AtomicBoolean();
        boolean retireOnRemoval;
        final CountDownLatch appended = new CountDownLatch(1), releaseAppend = new CountDownLatch(1);
        final AtomicInteger consumed = new AtomicInteger(), removed = new AtomicInteger(), acknowledged = new AtomicInteger(), errors = new AtomicInteger();
        final TrashHistoryStore store;
        final TrashRelicActivationService activation;
        Fixture() throws Exception {
            catalog = new TrashCatalog(() -> getClass().getClassLoader().getResourceAsStream(TrashCatalog.RESOURCE), LOGGER);
            catalog.load(); brick = catalog.snapshot().values().stream().filter(d -> d.behavior().equals("TEGLA")).findFirst().orElseThrow();
            store = new TrashHistoryStore(root.resolve("history.yml").toFile(), root.resolve("history.wal").toFile(),
                    LOGGER, catalog, (journal, sequence, payload) -> {
                journal.append(sequence, payload);
                if (failAcknowledgement.getAndSet(false)) throw new IllegalStateException("injected lost native acknowledgement");
                if (pauseAppend.getAndSet(false)) { appended.countDown(); await(releaseAppend); }
            });
            store.load(); store.transact(() -> store.createAndRecord(instance, brick.id(), "base", TrashHistoryEvent.CREATED_AMBIENT, actor, ""), null);
            check(fields.add(field), "native field setup failed");
            claim = fields.claim(field.center(), field.kind()).orElseThrow();
            activation = new TrashRelicActivationService(fields, tracking, open::get, receipt -> {
                check("projectile".equals(OWNER.get()), "acknowledgement preceded the projectile owner observation");
                check(store.tryConfirmWallRemoval(receipt, () -> observed.get()), "native acknowledgement failed");
                acknowledged.incrementAndGet();
            }, errors::incrementAndGet);
        }
        boolean dispatch() {
            return activation.dispatchWall(claim, ticket, new TrashRelicActivationService.InventoryOwner() {
                public boolean schedule(Runnable action, Runnable retired) { return inventory.schedule(action, retired); }
                public boolean admitted() { check("inventory".equals(OWNER.get()), "foreign inventory admission"); return inventoryReady.get(); }
                public TrashHistoryStore.WallReceipt consume(BooleanSupplier admission) {
                    check("inventory".equals(OWNER.get()), "foreign inventory mutation");
                    final var receipt = new AtomicReference<TrashHistoryStore.WallReceipt>();
                    final boolean accepted = store.tryTransact(admission, () -> {
                        var before = store.find(instance).orElseThrow();
                        var after = store.transform(instance, brick.id(), "base", brick.successPhase(), actor);
                        var value = new TrashHistoryStore.WallReceipt(field.id(), actor, world, projectile, instance,
                                after.revision(), brick.id(), brick.successPhase(), now.get(), field, before.revision());
                        store.putWallReceipt(value); receipt.set(value);
                    }, null);
                    if (accepted) consumed.incrementAndGet();
                    return accepted ? receipt.get() : null;
                }
            }, new TrashRelicActivationService.ProjectileOwner() {
                public boolean schedule(Runnable action, Runnable retired) { return projectileQueue.schedule(action, retired); }
                public boolean admitted() { check("projectile".equals(OWNER.get()), "foreign projectile admission"); return projectileReady.get(); }
                public boolean removeObserved() {
                    check("projectile".equals(OWNER.get()), "foreign projectile mutation");
                    check(store.tryInspectWallReceipts().orElseThrow().size() == 1, "effect ran before native WAL acknowledgement");
                    removed.incrementAndGet();
                    if (retireOnRemoval) projectileQueue.retired.run();
                    return observed.get();
                }
            });
        }
        void clean() {
            check(tracking.snapshot().active() == 0 && fields.snapshot().claimed().isEmpty(), "handoff leaked native tracking/claim");
        }
        TrashHistoryStore reload() {
            var loaded = new TrashHistoryStore(root.resolve("history.yml").toFile(), root.resolve("history.wal").toFile(), LOGGER, catalog);
            loaded.load(); return loaded;
        }
        public void close() throws Exception {
            releaseAppend.countDown();
            try (var paths = Files.walk(root)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        }
    }
    private static void successAndDuplicateCallbacks(boolean retireOnRemoval) throws Exception {
        try (var f = new Fixture()) {
            f.retireOnRemoval = retireOnRemoval;
            check(f.dispatch(), "owner handoff rejected");
            check(f.consumed.get() == 0 && f.removed.get() == 0, "dispatch touched an entity before its owner callback");
            f.inventory.run(); f.inventory.run();
            check(f.consumed.get() == 1 && f.removed.get() == 0, "inventory hop duplicated consumption or crossed to projectile");
            f.projectileQueue.run(); f.projectileQueue.run(); f.inventory.retired.run(); f.projectileQueue.retired.run();
            check(f.removed.get() == 1 && f.acknowledged.get() == 1 && f.errors.get() == 0, "effect replayed or acknowledgement was fabricated");
            f.clean(); check(f.fields.snapshot().fields().isEmpty(), "consumed field survived completion");
            check(f.reload().tryInspectWallRecoveryReceipts(Set.of(f.instance)).orElseThrow().get(f.instance).removalObserved(),
                    "actual owner handoff receipt did not survive native WAL replay");
        }
    }
    private static void refusal(String failure) throws Exception {
        try (var f = new Fixture()) {
            if (failure.equals("inventory-reject")) f.inventory.accept = false;
            if (failure.equals("inventory-refuse")) f.inventoryReady.set(false);
            boolean queued = f.dispatch();
            if (failure.equals("inventory-reject")) check(!queued, "rejected inventory scheduler accepted handoff");
            else {
                check(queued, "fixture was not queued");
                if (failure.equals("inventory-retire")) { f.inventory.retired.run(); f.inventory.run(); }
                else {
                    if (failure.equals("projectile-reject")) f.projectileQueue.accept = false;
                    f.inventory.run();
                    if (f.projectileQueue.action != null) {
                        switch (failure) {
                            case "projectile-retire" -> f.projectileQueue.retired.run();
                            case "projectile-drift" -> f.projectileReady.set(false);
                            case "remove-unobserved" -> f.observed.set(false);
                            case "expiry" -> f.now.set(2000);
                            case "shutdown" -> { f.open.set(false); f.tracking.close(); f.fields.close(); }
                            default -> { }
                        }
                        f.projectileQueue.run();
                    }
                }
            }
            f.clean(); check(f.acknowledged.get() == 0, "refused handoff fabricated observation: " + failure);
            boolean beforeConsume = failure.startsWith("inventory-");
            check(f.consumed.get() == (beforeConsume ? 0 : 1), "consume boundary changed: " + failure);
            check(f.removed.get() == (failure.equals("remove-unobserved") ? 1 : 0), "refusal crossed projectile boundary: " + failure);
            check(f.reload().tryInspectWallReceipts().orElseThrow().size() == (beforeConsume ? 0 : 1),
                    "acknowledged pending operation was lost: " + failure);
            check(f.errors.get() == (beforeConsume ? 0 : 1), "uncertain acknowledged effect was not reported exactly once: " + failure);
        }
    }
    private static void staleClaim() throws Exception {
        try (var f = new Fixture()) {
            check(f.dispatch(), "stale-claim fixture not queued");
            f.fields.releaseClaim(f.claim); var replacement = f.fields.claim(f.field.center(), f.field.kind()).orElseThrow();
            f.inventory.run(); f.inventory.retired.run();
            check(f.fields.isClaimed(replacement) && f.consumed.get() == 0 && f.removed.get() == 0,
                    "old owner callback consumed or released a replacement claim");
            f.fields.releaseClaim(replacement); f.clean();
        }
    }
    private static void heldWriter() throws Exception {
        try (var f = new Fixture(); var executor = Executors.newSingleThreadExecutor()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var writer = executor.submit(() -> f.store.transact(() -> { entered.countDown(); await(release); return true; }, null));
            try {
                check(entered.await(5, TimeUnit.SECONDS), "native writer did not enter");
                check(f.dispatch(), "busy fixture not queued"); f.inventory.run();
                check(f.consumed.get() == 0 && f.projectileQueue.action == null, "busy native writer was bypassed"); f.clean();
            } finally { release.countDown(); }
            writer.get(5, TimeUnit.SECONDS);
            check(f.reload().find(f.instance).orElseThrow().phase().equals("base"), "busy refusal changed canonical item state");
        }
    }
    private static void shutdownDuringAcknowledgement() throws Exception {
        try (var f = new Fixture(); var executor = Executors.newSingleThreadExecutor()) {
            f.pauseAppend.set(true); check(f.dispatch(), "held-ack fixture not queued");
            var owner = executor.submit(f.inventory::run);
            try {
                check(f.appended.await(5, TimeUnit.SECONDS), "real WAL append did not enter");
                f.inventory.retired.run(); f.open.set(false); f.tracking.close(); f.fields.close();
            } finally { f.releaseAppend.countDown(); }
            owner.get(5, TimeUnit.SECONDS); f.clean();
            check(f.consumed.get() == 1 && f.removed.get() == 0 && f.projectileQueue.action == null && f.errors.get() == 1,
                    "late WAL acknowledgement crossed retirement or lost uncertainty telemetry");
            check(f.reload().tryInspectWallReceipts().orElseThrow().size() == 1, "late durable consumption vanished at shutdown");
        }
    }
    private static void lostAcknowledgement() throws Exception {
        try (var f = new Fixture()) {
            check(f.dispatch(), "lost-ack fixture not queued"); f.failAcknowledgement.set(true);
            try { f.inventory.run(); throw new AssertionError("lost acknowledgement swallowed"); }
            catch (IllegalStateException expected) { assertions++; }
            f.clean();
            check(f.removed.get() == 0 && f.projectileQueue.action == null && f.store.tryInspectWallReceipts().isEmpty(),
                    "uncertain native write entered projectile effect or exposed a false acknowledged snapshot");
            check(f.reload().tryInspectWallReceipts().orElseThrow().size() == 1,
                    "real fsynced consume vanished after a failed owner callback acknowledgement");
        }
    }
    private static void schedulingFailure(boolean afterConsume) throws Exception {
        try (var f = new Fixture()) {
            final var failure = new IllegalStateException("injected scheduler rejection");
            if (afterConsume) { check(f.dispatch(), "scheduler failure fixture not queued"); f.projectileQueue.failure = failure; }
            else f.inventory.failure = failure;
            try {
                if (afterConsume) f.inventory.run(); else f.dispatch();
                throw new AssertionError("scheduler exception swallowed");
            } catch (IllegalStateException actual) { check(actual == failure, "original scheduler failure was replaced"); }
            f.clean();
            check(f.removed.get() == 0 && f.acknowledged.get() == 0, "scheduler exception projected an effect");
            check(f.reload().tryInspectWallReceipts().orElseThrow().size() == (afterConsume ? 1 : 0),
                    "scheduler exception crossed the durable consume boundary");
        }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("owner fixture wait timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
}
