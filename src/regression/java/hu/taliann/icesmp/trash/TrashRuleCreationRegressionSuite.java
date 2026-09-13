package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.execution.OperationRecoveryPayload;
import hu.taliann.icesmp.dev.weaver.subject.EntityRef;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/** Actual native fields and fsynced history; owner queues are deterministic fixtures, not live Folia regions. */
public final class TrashRuleCreationRegressionSuite {
    private static int assertions;
    private static final ThreadLocal<Boolean> OWNER = ThreadLocal.withInitial(() -> false);
    public static void main(String[] args) throws Exception {
        for (var kind : TrashRuleFieldService.FieldKind.values()) {
            success(kind);
            for (String refusal : List.of("history", "field", "deadline", "source", "journal", "null", "scheduler", "throw", "shutdown")) refusal(kind, refusal);
        }
        enteredAcknowledgement(); earlyDeadline(); finalAdmissionClose(); durableFieldConsequence();
        System.out.println("Trash rule creation passed. assertions=" + assertions);
    }
    private static final class Fixture implements AutoCloseable {
        final Path directory = Files.createTempDirectory("trash-rule-creation-");
        final UUID actor = UUID.randomUUID(), world = UUID.randomUUID(), instance = UUID.randomUUID();
        final TrashRuleFieldService fields = new TrashRuleFieldService(() -> 1000L);
        final TrashRelicPolicy.ProjectileTracking tracking = new TrashRelicPolicy.ProjectileTracking(256);
        final AtomicBoolean open = new AtomicBoolean(true), valid = new AtomicBoolean(true), hold = new AtomicBoolean();
        final CountDownLatch appended = new CountDownLatch(1), release = new CountDownLatch(1);
        final TrashHistoryStore history;
        final TrashDefinition definition;
        final TrashRuleFieldService.RuleField field;
        final TrashRuleFieldService.CreationClaim claim;
        final TrashRelicActivationService activation;
        final AtomicInteger commits = new AtomicInteger(), abandoned = new AtomicInteger(), errors = new AtomicInteger(), cancellations = new AtomicInteger();
        final Queue<Runnable> actions = new ArrayDeque<>();
        Runnable deadline;
        boolean accepted = true, scheduleFailure, immediateDeadline;
        final CompletableFuture<TrashRelicActivationService.CreationPermits> preparation = new CompletableFuture<>();
        Fixture(TrashRuleFieldService.FieldKind kind) throws Exception {
            final var catalog = new TrashCatalog(() -> getClass().getClassLoader().getResourceAsStream(TrashCatalog.RESOURCE), Logger.getAnonymousLogger());
            catalog.load();
            final String behavior = switch (kind) {
                case PROJECTILE_WALL -> "TEGLA"; case ACOUSTIC_NULL -> "FEKETE_VIASZDUGO";
                case CEASEFIRE -> "SZAKADT_FEHER_ZASZLO"; case SPATIAL_ANCHOR -> "MELYNEPI_SELEJTEK";
            };
            definition = catalog.snapshot().values().stream().filter(d -> d.behavior().equals(behavior)).findFirst().orElseThrow();
            history = new TrashHistoryStore(directory.resolve("history.yml").toFile(), directory.resolve("history.wal").toFile(),
                    Logger.getAnonymousLogger(), catalog, (wal, sequence, payload) -> {
                wal.append(sequence, payload);
                if (hold.getAndSet(false)) { appended.countDown(); await(release); }
            });
            history.load();
            field = new TrashRuleFieldService.RuleField(UUID.randomUUID(), kind,
                    new TrashRuleFieldService.Point(world, 1, 64, 2), 2.5, 3000, actor,
                    kind == TrashRuleFieldService.FieldKind.PROJECTILE_WALL ? UUID.randomUUID().toString() : null);
            claim = fields.reserveCreation(field).orElseThrow();
            activation = new TrashRelicActivationService(fields, tracking, open::get,
                    receipt -> { throw new AssertionError("creation acknowledged a projectile receipt"); }, errors::incrementAndGet,
                    (millis, expired) -> {
                        check(millis == 5000, "unbounded preparation deadline"); deadline = expired;
                        if (immediateDeadline) expired.run();
                        return cancellations::incrementAndGet;
                    });
        }
        boolean dispatch() {
            return activation.dispatchCreation(claim, new TrashRelicActivationService.CreationOwner() {
                public boolean schedule(Runnable action, Runnable retired) {
                    if (scheduleFailure) throw new IllegalStateException("injected owner scheduler refusal");
                    if (!accepted) return false;
                    actions.add(action); return true;
                }
                public boolean admitted() { owner(); return valid.get(); }
                public CompletionStage<TrashRelicActivationService.CreationPermits> prepare() { owner(); return preparation; }
                public List<RewardSource> sources() { owner(); return List.of(new RewardSource.Player(actor)); }
                public boolean commit(BooleanSupplier admission, BooleanSupplier finalAdmission) {
                    owner();
                    boolean result = history.tryTransact(admission, finalAdmission, () -> {
                        var activated = history.createAndRecord(instance, definition.id(), "base", TrashHistoryEvent.ACTIVATED, actor, "");
                        if (field.kind() != TrashRuleFieldService.FieldKind.PROJECTILE_WALL)
                            history.transform(instance, definition.id(), activated.phase(), definition.successPhase(), actor);
                        check(fields.snapshot().fields().isEmpty(), "field visible before native history acknowledgement");
                    }, null);
                    if (result) commits.incrementAndGet(); return result;
                }
                public void abandonCommitted() { owner(); abandoned.incrementAndGet(); }
            });
        }
        void run() { boolean previous = OWNER.get(); OWNER.set(true); try { actions.remove().run(); } finally { OWNER.set(previous); } }
        void ready(boolean historyPermit, boolean fieldPermit) {
            preparation.complete(new TrashRelicActivationService.CreationPermits(
                    GameplayEffectPermit.guarded(() -> historyPermit), GameplayEffectPermit.guarded(() -> fieldPermit)));
        }
        void clean() { check(fields.snapshot().preparing().isEmpty() && cancellations.get() == 1, "creation reservation or deadline leaked"); }
        public void close() throws Exception {
            release.countDown(); fields.close();
            try (var files = Files.walk(directory)) { for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
    private static void success(TrashRuleFieldService.FieldKind kind) throws Exception {
        try (var f = new Fixture(kind)) {
            check(f.dispatch(), "creation not dispatched"); f.run();
            check(f.fields.isPreparing(f.claim) && f.commits.get() == 0 && f.fields.snapshot().fields().isEmpty(), "journal wait changed gameplay");
            f.ready(true, true); Runnable duplicate = f.actions.element(); f.run();
            OWNER.set(true); try { duplicate.run(); } finally { OWNER.set(false); }
            f.clean(); check(f.commits.get() == 1 && f.fields.contains(f.field) && f.abandoned.get() == 0 && f.errors.get() == 0, "native publication failed or replayed");
            f.history.load();
            final var stored = f.history.find(f.instance).orElseThrow();
            check(stored.phase().equals(kind == TrashRuleFieldService.FieldKind.PROJECTILE_WALL ? "base" : f.definition.successPhase()), "native consumed phase lost on restart");
            f.deadline.run(); check(f.fields.contains(f.field), "retired preparation removed an active field");
        }
    }
    private static void refusal(TrashRuleFieldService.FieldKind kind, String refusal) throws Exception {
        try (var f = new Fixture(kind)) {
            check(f.dispatch(), "refusal fixture not dispatched"); f.run();
            switch (refusal) {
                case "deadline" -> f.deadline.run();
                case "source" -> f.valid.set(false);
                case "scheduler" -> f.accepted = false;
                case "throw" -> f.scheduleFailure = true;
                case "shutdown" -> { f.open.set(false); f.fields.close(); }
                default -> { }
            }
            if (refusal.equals("journal")) f.preparation.completeExceptionally(new IllegalStateException("journal unavailable"));
            else if (refusal.equals("null")) f.preparation.complete(null);
            else f.ready(!refusal.equals("history"), !refusal.equals("field"));
            while (!f.actions.isEmpty()) f.run(); f.deadline.run(); f.clean();
            boolean consumed = refusal.equals("field");
            check(f.fields.snapshot().fields().isEmpty() && f.commits.get() == (consumed ? 1 : 0)
                    && f.abandoned.get() == (consumed ? 1 : 0), "refused creation crossed native boundary: " + refusal);
            f.history.load(); check(f.history.find(f.instance).isPresent() == consumed, "refusal erased or invented native history");
            check(f.errors.get() == (Set.of("journal", "throw", "field").contains(refusal) ? 1 : 0), "refusal telemetry lost or duplicated");
        }
    }
    private static void enteredAcknowledgement() throws Exception {
        try (var f = new Fixture(TrashRuleFieldService.FieldKind.CEASEFIRE); var executor = Executors.newSingleThreadExecutor()) {
            check(f.dispatch(), "entered fixture not dispatched"); f.run(); f.ready(true, true); f.hold.set(true);
            var committed = executor.submit(f::run);
            try {
                check(f.appended.await(5, TimeUnit.SECONDS), "native WAL acknowledgement never entered");
                f.deadline.run();
                check(f.fields.cancelCreationsForOwner(f.actor).isEmpty(), "owner cleanup evicted an entered native write");
                check(f.fields.snapshot().preparing().size() == 1, "deadline released capacity before native acknowledgement");
                check(!f.fields.isPreparing(f.claim) && f.fields.beginCreationWrite(f.claim).isEmpty(), "cancelled entered creation allowed another writer");
            } finally { f.release.countDown(); }
            committed.get(5, TimeUnit.SECONDS); f.clean();
            check(f.fields.snapshot().fields().isEmpty() && f.commits.get() == 1 && f.abandoned.get() == 1 && f.errors.get() == 1,
                    "expired native acknowledgement published a field or concealed consumption");
            f.history.load(); check(f.history.find(f.instance).isPresent(), "expired acknowledgement erased real history");
        }
    }
    private static void earlyDeadline() throws Exception {
        try (var f = new Fixture(TrashRuleFieldService.FieldKind.ACOUSTIC_NULL)) {
            f.immediateDeadline = true; check(!f.dispatch(), "early deadline queued an owner");
            f.clean(); check(f.actions.isEmpty() && f.commits.get() == 0, "early deadline touched gameplay");
        }
    }
    private static void finalAdmissionClose() {
        final var fields = new TrashRuleFieldService(() -> 1000L);
        final var field = new TrashRuleFieldService.RuleField(UUID.randomUUID(), TrashRuleFieldService.FieldKind.CEASEFIRE,
                new TrashRuleFieldService.Point(UUID.randomUUID(), 1, 64, 2), 3, 2000, UUID.randomUUID(), null);
        final var claim = fields.reserveCreation(field).orElseThrow(); final var calls = new AtomicInteger();
        check(!fields.tryActivateCreation(claim, () -> { calls.incrementAndGet(); return false; }) && fields.isPreparing(claim), "refused final admission exposed field");
        fields.close();
        check(!fields.tryActivateCreation(claim, () -> { calls.incrementAndGet(); return true; }) && calls.get() == 1, "closed authority spent final permit");
    }
    private static void durableFieldConsequence() throws Exception {
        try (var f = new Fixture(TrashRuleFieldService.FieldKind.CEASEFIRE)) {
            check(f.dispatch(), "field consequence fixture not dispatched"); f.run(); f.ready(true, true); f.run();
            final var types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
            final var storage = new YamlWeaverJournalStorage(f.directory.resolve("weaver").toFile(), new WeaverJournalCodec(types), Logger.getAnonymousLogger());
            final var journal = new WeaverJournal(storage); done(journal.load());
            final RewardSource target = new RewardSource.Player(UUID.randomUUID());
            final RewardSource field = new RewardSource.Event("trash.rule_field", f.field.id());
            final List<RewardSource> nativeSources = List.of(new RewardSource.Entity(f.actor));
            final var effects = new AtomicInteger();
            try (var binding = GameplayEffectGate.install(journal::prepareDerivedEffect)) {
                check(f.activation.applyFieldEffect(f.field.center(), f.field.kind(), nativeSources, Set.of(target),
                        () -> { effects.incrementAndGet(); return true; }), "clean native field required an asynchronous write");
                final long now = System.currentTimeMillis(); final var subject = new EntityRef(UUID.randomUUID());
                final var op = new WeaverOperationRecord(UUID.randomUUID(), HiddenDevAuthority.PRIMARY_DEVELOPER, "fixture",
                        new ActionRequest("fixture.mutate", Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), subject, "before",
                        Optional.empty(), new OperationRecoveryPayload(1, Map.of()), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false);
                done(journal.prepare(op));
                final var receipt = new WeaverReceipt(UUID.randomUUID(), op.operationId(), "fixture", "fixture.mutate", subject,
                        RiskLevel.MUTATING, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX, "before", "after", Map.of(), Map.of(), Optional.empty(), now, ReceiptStatus.COMMITTED);
                final var applied = done(journal.applied(op.operationId(), 0, receipt, now)); done(journal.finishAudit(op.operationId(), applied.revision()));
                check(done(GameplayEffectGate.prepare(new GameplayEffectContext(List.of(new RewardSource.Entity(subject.entityId())), Set.of(field), 0))).claim(),
                        "native field origin not durably propagated");
                f.activation.applyFieldEffect(f.field.center(), f.field.kind(), nativeSources, Set.of(target),
                        () -> { check(journal.influenceIndex().quarantined(target, System.currentTimeMillis()), "native consequence preceded durable target lineage"); effects.incrementAndGet(); return true; });
                final int afterFirstAttempt = effects.get();
                final var context = new GameplayEffectContext(List.of(nativeSources.getFirst(), field), Set.of(target), 0);
                check(done(GameplayEffectGate.prepare(context)).claim(), "native consequence lineage never acknowledged");
                check(effects.get() == afterFirstAttempt, "asynchronous journal completion replayed the earlier native event");
                check(f.activation.applyFieldEffect(f.field.center(), f.field.kind(), nativeSources, Set.of(target),
                        () -> { effects.incrementAndGet(); return true; }), "acknowledged instant field consequence remained unavailable");
                check(journal.influenceIndex().quarantined(target, System.currentTimeMillis()), "native consequence lost target quarantine");
                f.fields.remove(f.field);
                check(!f.activation.applyFieldEffect(f.field.center(), f.field.kind(), nativeSources, Set.of(target),
                        () -> { throw new AssertionError("removed field applied a consequence"); }), "removed field admitted an effect");
                check(journal.influenceIndex().quarantined(target, System.currentTimeMillis()), "field removal washed player consequence tail");
                final var restarted = new WeaverJournal(storage); done(restarted.load());
                try { check(restarted.influenceIndex().quarantined(target, System.currentTimeMillis()), "restart washed native field consequence"); }
                finally { done(restarted.close()); }
            } finally { done(journal.close()); }
        }
    }
    private static <T> T done(CompletionStage<T> result) throws Exception { return result.toCompletableFuture().get(5, TimeUnit.SECONDS); }
    private static void owner() { check(OWNER.get(), "foreign creation owner access"); }
    private static void await(CountDownLatch latch) { try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("held WAL timed out"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } }
    private static void check(boolean value, String detail) { if (!value) throw new AssertionError(detail); assertions++; }
}
