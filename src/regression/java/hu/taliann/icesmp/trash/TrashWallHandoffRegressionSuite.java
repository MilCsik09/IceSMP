package hu.taliann.icesmp.trash;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.OperationRecoveryPayload;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;

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
        for (String failure : List.of("success", "deadline", "retired", "rejected", "failed", "null", "drift")) delayedPreparation(failure);
        deniedPermit(false); deniedPermit(true); deadlineDuringAcknowledgement();
        for (String drift : List.of("none", "inventory", "field-before", "field-after", "projectile", "binding")) durableInfluence(drift);
        deadlineBeforeRegistration(); preparationException();
        System.out.println("Trash wall owner handoff passed. assertions=" + assertions);
    }
    private static final class Queue {
        final String name;
        volatile Runnable action, retired;
        boolean accept = true;
        RuntimeException failure;
        Queue(String name) { this.name = name; }
        boolean schedule(Runnable action, Runnable retired) {
            if (failure != null) throw failure;
            if (!accept) return false;
            if (name.equals(OWNER.get())) { action.run(); return true; }
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
        Runnable deadline;
        int deadlineCancellations;
        boolean expireDuringRegistration;
        boolean failPreparation;
        List<RewardSource> inventorySources = List.of(new RewardSource.Item(instance));
        List<RewardSource> projectileSources = List.of(new RewardSource.Entity(projectile));
        CompletableFuture<TrashRelicActivationService.WallPermits> preparation = CompletableFuture.completedFuture(
                new TrashRelicActivationService.WallPermits(GameplayEffectPermit.guarded(() -> true), GameplayEffectPermit.guarded(() -> true)));
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
            }, errors::incrementAndGet, (delay, expired) -> {
                check(delay == 5000, "unbounded native wall preparation");
                if (expireDuringRegistration) expired.run();
                deadline = expired; return () -> deadlineCancellations++;
            });
        }
        boolean dispatch() {
            return activation.dispatchWall(claim, ticket, new TrashRelicActivationService.InventoryOwner() {
                public boolean schedule(Runnable action, Runnable retired) { return inventory.schedule(action, retired); }
                public boolean admitted() { check("inventory".equals(OWNER.get()), "foreign inventory admission"); return inventoryReady.get(); }
                public CompletionStage<TrashRelicActivationService.WallPermits> prepare() {
                    check("inventory".equals(OWNER.get()), "foreign inventory preparation");
                    if (failPreparation) throw new IllegalStateException("injected synchronous preparation refusal");
                    return preparation;
                }
                public List<RewardSource> sources() {
                    check("inventory".equals(OWNER.get()), "foreign inventory influence read"); return inventorySources;
                }
                public TrashHistoryStore.WallReceipt consume(BooleanSupplier admission, BooleanSupplier finalAdmission) {
                    check("inventory".equals(OWNER.get()), "foreign inventory mutation");
                    final var receipt = new AtomicReference<TrashHistoryStore.WallReceipt>();
                    final boolean accepted = store.tryTransact(admission, finalAdmission, () -> {
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
                public List<RewardSource> sources() {
                    check("projectile".equals(OWNER.get()), "foreign projectile influence read"); return projectileSources;
                }
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
            f.inventory.run();
            check(f.errors.get() == 1, "asynchronous lost acknowledgement was not reported");
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
            if (afterConsume) {
                f.inventory.run(); check(f.errors.get() == 1, "asynchronous scheduler exception was not reported");
            } else {
                try { f.dispatch(); throw new AssertionError("initial scheduler exception swallowed"); }
                catch (IllegalStateException actual) { check(actual == failure, "original scheduler failure was replaced"); }
            }
            f.clean();
            check(f.removed.get() == 0 && f.acknowledged.get() == 0, "scheduler exception projected an effect");
            check(f.reload().tryInspectWallReceipts().orElseThrow().size() == (afterConsume ? 1 : 0),
                    "scheduler exception crossed the durable consume boundary");
        }
    }
    private static TrashRelicActivationService.WallPermits cleanPermits() {
        return new TrashRelicActivationService.WallPermits(GameplayEffectPermit.guarded(() -> true), GameplayEffectPermit.guarded(() -> true));
    }
    private static void delayedPreparation(String failure) throws Exception {
        try (var f = new Fixture()) {
            f.preparation = new CompletableFuture<>(); check(f.dispatch(), "delayed preparation not dispatched");
            final Runnable stalePreparation = f.inventory.action;
            f.inventory.run(); f.inventory.action = null;
            check(f.consumed.get() == 0 && f.removed.get() == 0 && f.fields.isClaimed(f.claim),
                    "waiting for journal touched gameplay or lost the admitted claim");
            TrashRuleFieldService.FieldClaim replacement = null;
            switch (failure) {
                case "deadline" -> {
                    f.deadline.run(); replacement = f.fields.claim(f.field.center(), f.field.kind()).orElseThrow();
                }
                case "retired" -> f.inventory.retired.run();
                case "rejected" -> f.inventory.accept = false;
                case "drift" -> f.inventoryReady.set(false);
                default -> { }
            }
            if (failure.equals("failed")) f.preparation.completeExceptionally(new IllegalStateException("journal refused"));
            else f.preparation.complete(failure.equals("null") ? null : cleanPermits());
            if (f.inventory.action != null) f.inventory.run();
            if (f.projectileQueue.action != null) f.projectileQueue.run();
            stalePreparation.run(); f.inventory.retired.run();
            boolean success = failure.equals("success");
            check(f.consumed.get() == (success ? 1 : 0) && f.removed.get() == (success ? 1 : 0),
                    "delayed journal continuation crossed refusal boundary: " + failure);
            check(f.acknowledged.get() == (success ? 1 : 0) && f.errors.get() == (failure.equals("failed") ? 1 : 0),
                    "delayed journal outcome was fabricated or not reported: " + failure);
            if (replacement != null) {
                check(f.fields.isClaimed(replacement), "late preparation released the replacement field claim");
                f.fields.releaseClaim(replacement);
            }
            f.clean(); check(f.deadlineCancellations == 1, "wall deadline task was retained or cancelled twice");
            check(f.reload().tryInspectWallReceipts().orElseThrow().size() == 0,
                    "failed preparation wrote native consumption, or success remained unresolved");
        }
    }
    private static void preparationException() throws Exception {
        try (var f = new Fixture()) {
            f.failPreparation = true; check(f.dispatch(), "preparation exception fixture not dispatched");
            f.inventory.run(); f.inventory.run(); f.inventory.retired.run(); f.deadline.run();
            f.clean();
            check(f.errors.get() == 1 && f.deadlineCancellations == 1,
                    "synchronous preparation refusal lost or duplicated telemetry/cleanup");
            check(f.consumed.get() == 0 && f.removed.get() == 0 && f.projectileQueue.action == null
                            && f.reload().tryInspectWallReceipts().orElseThrow().isEmpty(),
                    "synchronous preparation refusal wrote history or queued a projectile consequence");
        }
    }
    private static void deniedPermit(boolean afterConsume) throws Exception {
        try (var f = new Fixture()) {
            f.preparation = CompletableFuture.completedFuture(new TrashRelicActivationService.WallPermits(
                    afterConsume ? GameplayEffectPermit.guarded(() -> true) : GameplayEffectPermit.denied(),
                    GameplayEffectPermit.denied()));
            check(f.dispatch(), "denied permit fixture not dispatched"); f.inventory.run();
            if (f.projectileQueue.action != null) f.projectileQueue.run();
            f.clean();
            check(f.consumed.get() == (afterConsume ? 1 : 0) && f.removed.get() == 0 && f.acknowledged.get() == 0,
                    "denied influence permit crossed its native mutation boundary");
            check(f.reload().tryInspectWallReceipts().orElseThrow().size() == (afterConsume ? 1 : 0),
                    "post-consume refusal lost its durable pending receipt");
        }
    }
    private static void deadlineDuringAcknowledgement() throws Exception {
        try (var f = new Fixture(); var executor = Executors.newSingleThreadExecutor()) {
            f.pauseAppend.set(true); check(f.dispatch(), "deadline/WAL fixture not dispatched");
            var owner = executor.submit(f.inventory::run);
            try {
                check(f.appended.await(5, TimeUnit.SECONDS), "deadline fixture never reached real native WAL append");
                f.deadline.run();
                check(f.fields.snapshot().claimed().contains(f.field.id()) && f.tracking.active(f.ticket),
                        "deadline released native claim/capacity while its consumption WAL remained in flight");
                check(f.fields.claim(f.field.center(), f.field.kind()).isEmpty(),
                        "a second projectile claimed the field before the first native acknowledgement");
            } finally { f.releaseAppend.countDown(); }
            owner.get(5, TimeUnit.SECONDS); f.clean();
            check(f.consumed.get() == 1 && f.removed.get() == 0 && f.errors.get() == 1,
                    "expired entered WAL acknowledgement projected an effect or hid uncertainty");
            check(f.reload().tryInspectWallReceipts().orElseThrow().size() == 1, "deadline erased actual durable consumption");
        }
    }
    private static void deadlineBeforeRegistration() throws Exception {
        try (var f = new Fixture()) {
            f.expireDuringRegistration = true;
            check(!f.dispatch(), "already retired deadline still dispatched an inventory owner");
            f.clean();
            check(f.inventory.action == null && f.deadlineCancellations == 1,
                    "early deadline retained or cancelled its scheduler handle more than once");
        }
    }
    private static void durableInfluence(String drift) throws Exception {
        try (var f = new Fixture()) {
            var types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
            var storage = new YamlWeaverJournalStorage(f.root.resolve("weaver").toFile(), new WeaverJournalCodec(types), LOGGER);
            var journal = new WeaverJournal(storage); completed(journal.load());
            try (var binding = GameplayEffectGate.install(journal::prepareDerivedEffect)) {
                final var fieldSource = new RewardSource.Event("trash.rule_field", f.field.id());
                final UUID creator = UUID.randomUUID();
                final var root = taint(journal, new EntityRef(creator));
                check(completed(journal.prepareDerivedEffect(new GameplayEffectContext(
                        List.of(new RewardSource.Entity(creator)), Set.of(fieldSource), 0))).claim(),
                        "native field creation lineage refused");
                f.inventorySources = List.of(fieldSource, new RewardSource.Item(f.instance), new RewardSource.Player(f.actor),
                        new RewardSource.World(f.world), new RewardSource.Location(f.world, 1, 64, 2));
                final var context = new GameplayEffectContext(f.inventorySources,
                        Set.of(new RewardSource.Item(f.instance), new RewardSource.Entity(f.projectile)), 0);
                f.preparation = new CompletableFuture<>(); check(f.dispatch(), "durable influence fixture not dispatched");
                f.inventory.run(); f.inventory.action = null;
                final var consumption = completed(GameplayEffectGate.prepare(context));
                final var removal = completed(GameplayEffectGate.prepare(context));
                check(storage.readState().equals(journal.snapshot()) && f.consumed.get() == 0,
                        "native consumption preceded the real durable influence state");
                for (RewardSource target : context.targets()) {
                    check(journal.influenceIndex().trace(List.of(target), System.currentTimeMillis()).origins().stream()
                            .anyMatch(origin -> origin.operationId().equals(root)), "native target lost field origin");
                }
                f.preparation.complete(new TrashRelicActivationService.WallPermits(consumption, removal));
                if (drift.equals("inventory")) {
                    final UUID enteredWorld = UUID.randomUUID(); taint(journal, new WorldRef(enteredWorld));
                    f.inventorySources = List.of(fieldSource, new RewardSource.World(enteredWorld));
                }
                if (drift.equals("field-before")) taintField(journal, fieldSource);
                f.inventory.run();
                if (drift.equals("projectile")) {
                    final UUID foreignWorld = UUID.randomUUID(); taint(journal, new WorldRef(foreignWorld));
                    f.projectileSources = List.of(new RewardSource.Entity(f.projectile), new RewardSource.World(foreignWorld));
                }
                if (drift.equals("field-after")) taintField(journal, fieldSource);
                if (drift.equals("binding")) binding.close();
                if (f.projectileQueue.action != null) f.projectileQueue.run();
                f.clean();
                boolean consumed = !drift.equals("inventory") && !drift.equals("field-before");
                boolean removed = drift.equals("none");
                check(f.consumed.get() == (consumed ? 1 : 0) && f.removed.get() == (removed ? 1 : 0),
                        "final owner permit did not recheck source lineage or binding: " + drift);
                check(!consumption.claim(f.inventorySources), "native consumption reused a permit");
                if (consumed) check(!removal.claim(f.projectileSources), "native removal reused a permit");
                check(f.reload().tryInspectWallReceipts().orElseThrow().size() == (consumed && !removed ? 1 : 0),
                        "native observation and durable receipt disagree after influence admission");
                var restarted = new WeaverJournal(storage); completed(restarted.load());
                try {
                    for (RewardSource target : context.targets()) check(restarted.influenceIndex().quarantined(target, System.currentTimeMillis()),
                            "restart washed consumed item/projectile origin");
                } finally { completed(restarted.close()); }
            } finally { completed(journal.close()); }
        }
    }
    private static void taintField(WeaverJournal journal, RewardSource field) throws Exception {
        final UUID source = UUID.randomUUID(); taint(journal, new EntityRef(source));
        check(completed(GameplayEffectGate.prepare(new GameplayEffectContext(
                List.of(new RewardSource.Entity(source)), Set.of(field), 0))).claim(), "second field origin was not published");
    }
    private static UUID taint(WeaverJournal journal, SubjectRef subject) throws Exception {
        final long now = System.currentTimeMillis();
        var op = new WeaverOperationRecord(UUID.randomUUID(), HiddenDevAuthority.PRIMARY_DEVELOPER, "fixture",
                new ActionRequest("fixture.mutate", Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), subject, "before",
                Optional.empty(), new OperationRecoveryPayload(1, Map.of()), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false);
        completed(journal.prepare(op));
        var receipt = new WeaverReceipt(UUID.randomUUID(), op.operationId(), "fixture", "fixture.mutate", subject,
                RiskLevel.MUTATING, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX, "before", "after", Map.of(), Map.of(), Optional.empty(), now, ReceiptStatus.COMMITTED);
        var applied = completed(journal.applied(op.operationId(), 0, receipt, now));
        completed(journal.finishAudit(op.operationId(), applied.revision())); return op.operationId();
    }
    private static <T> T completed(CompletionStage<T> result) throws Exception { return result.toCompletableFuture().get(5, TimeUnit.SECONDS); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("owner fixture wait timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
}
