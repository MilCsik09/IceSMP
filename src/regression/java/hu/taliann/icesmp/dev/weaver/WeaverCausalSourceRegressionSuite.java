package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverProjectionRegressionSuite.*;

/** Actual generic registry/journal/gates, with an immutable stand-in for owner-read canonical summon metadata. */
public final class WeaverCausalSourceRegressionSuite {
    private static int assertions;
    private static final class SourceProvider extends WeaverContractRegressionSuite.FixtureProvider implements WeaverCausalSourceProvider {
        final Map<UUID, UUID> parents = new HashMap<>(); int reads; boolean broken; int count = 1;
        SourceProvider() { super("origin", WeaverContractRegressionSuite.safe("origin"), CoverageLevel.FULL_PROVIDER, Map.of()); }
        public Set<GameplaySourceSubject.Kind> causalSourceKinds() { return Set.of(GameplaySourceSubject.Kind.MOB); }
        public List<RewardSource> captureCausalSources(GameplaySourceSubject subject) {
            reads++; if (broken) throw new IllegalArgumentException("fixture private provenance");
            final UUID parent = parents.get(subject.id()); if (parent == null) return List.of();
            return java.util.stream.IntStream.range(0, count).mapToObj(i -> (RewardSource) new RewardSource.Entity(parent)).toList();
        }
    }
    public static void main(String[] args) throws Exception {
        discovery(); parentBarrier(); binding();
        System.out.println("Causal source capture passed: " + assertions + " assertions; generic provider discovery, owner routing identity, monotonic parent-before-creation, expired spatial origin, descendant lineage, durable restart and fail-closed capture.");
    }
    private static WorldWeaverProviderRegistry registry(SourceProvider provider) {
        final var registry = new WorldWeaverProviderRegistry(types(), System::currentTimeMillis); registry.register(provider); registry.freezeAndValidate(); return registry;
    }
    private static void discovery() {
        final var provider = new SourceProvider(); final var registry = registry(provider);
        final UUID child = UUID.randomUUID(), parent = UUID.randomUUID(); provider.parents.put(child, parent);
        final var subject = new GameplaySourceSubject(child, GameplaySourceSubject.Kind.MOB);
        check(registry.captureCausalSources(subject).equals(List.of(new RewardSource.Entity(parent))), "dummy provider origin absent without frontend changes");
        rejects(() -> registry.captureCausalSources(subject).clear());
        check(registry.captureCausalSources(new GameplaySourceSubject(child, GameplaySourceSubject.Kind.PLAYER)).isEmpty() && provider.reads == 2,
                "immutable source-kind discovery invoked foreign domain");
        provider.broken = true; for (int i = 0; i < 3; i++) rejects(() -> registry.captureCausalSources(subject));
        check(registry.quarantined("origin"), "causal source errors bypassed provider breaker");
        check(registry.captureCausalSources(new GameplaySourceSubject(child, GameplaySourceSubject.Kind.PLAYER)).isEmpty(), "failed mob source consumer broke unrelated player source");
        final int reads = provider.reads; rejects(() -> registry.captureCausalSources(subject)); check(reads == provider.reads, "quarantined source consumer called again");
        final var oversized = new SourceProvider(); oversized.parents.put(child, parent); oversized.count = 17;
        final var bounded = registry(oversized); rejects(() -> bounded.captureCausalSources(subject));
    }
    private static void parentBarrier() throws Exception {
        final var storage = new WeaverPersistenceRegressionSuite.Storage(); final var clock = new AtomicLong(100);
        final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1); final var hold = new AtomicBoolean();
        final WeaverJournalStorage paused = new WeaverJournalStorage() {
            public WeaverJournalState readState() { return storage.state; }
            public Map<String, WeaverAuditEntry> readAudit() { return storage.audit; }
            public void writeAudit(Map<String, WeaverAuditEntry> value) throws Exception { storage.writeAudit(value); }
            public void writeState(WeaverJournalState value) throws Exception { if (hold.get()) { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture IO timeout"); } storage.writeState(value); }
        };
        final var journal = new WeaverJournal(paused, ignored -> { }, clock::get); await(journal.load());
        final UUID world = UUID.randomUUID(), parent = UUID.randomUUID(), child = UUID.randomUUID(), grandchild = UUID.randomUUID();
        final var root = operation(new WorldRef(world), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX); apply(journal, root, WeaverEffectCommit.none());
        final var original = journal.snapshot(); final var parentSource = new RewardSource.Entity(parent);
        final var context = new GameplayEffectContext(List.of(parentSource, new RewardSource.World(world)), Set.of(parentSource), 0);
        final var provider = new SourceProvider(); final var registry = registry(provider);
        try (final var capture = GameplaySourceCaptureGate.install(registry::captureCausalSources); final var effects = GameplayEffectGate.install(journal::prepareDerivedEffect)) {
            hold.set(true); final var permission = GameplayEffectGate.prepare(context);
            try { check(entered.await(5, TimeUnit.SECONDS) && !permission.toCompletableFuture().isDone(), "native creation escaped parent fsync barrier"); }
            finally { hold.set(false); release.countDown(); }
            check(await(permission).claim() && journal.influenceIndex().quarantined(parentSource, clock.get()), "known parent not durable before child creation");
            provider.parents.put(child, parent);
            clock.set(1_000_000); check(!journal.influenceIndex().quarantined(new RewardSource.World(world), clock.get()), "world fixture did not expire");
            final var childOrigins = GameplaySourceCaptureGate.capture(new GameplaySourceSubject(child, GameplaySourceSubject.Kind.MOB));
            final var policy = new InfluenceRewardEligibilityPolicy(new WeaverInfluenceLookup(journal, clock::get));
            for (final var channel : RewardChannel.values()) check(!policy.evaluate(new RewardContext(channel, UUID.randomUUID(), childOrigins)).allowed(), "expired world washed created child");
            check(await(GameplayEffectGate.prepare(new GameplayEffectContext(childOrigins, Set.of(new RewardSource.Entity(child)), 0))).claim(), "child could not become durable parent");
            provider.parents.put(grandchild, child);
            final var descendant = GameplaySourceCaptureGate.capture(new GameplaySourceSubject(grandchild, GameplaySourceSubject.Kind.MOB));
            check(journal.influenceIndex().trace(descendant, clock.get()).origins().equals(journal.influenceIndex().trace(List.of(parentSource), clock.get()).origins()), "grandchild lost original developer origin");
            check(journal.snapshot().operations().equals(original.operations()) && journal.snapshot().receipts().equals(original.receipts()), "creation provenance spoofed a canonical receipt");
            final var path = Files.createTempDirectory("weaver-causal-parent"); final var yaml = new YamlWeaverJournalStorage(path.toFile(), new WeaverJournalCodec(), java.util.logging.Logger.getLogger("fixture"));
            yaml.writeState(journal.snapshot()); yaml.writeAudit(storage.audit);
            final var restart = new WeaverJournal(yaml); await(restart.load());
            check(restart.influenceIndex().trace(descendant, clock.get()).origins().equals(journal.influenceIndex().trace(descendant, clock.get()).origins()), "restart lost parent lineage when parent no longer exists"); await(restart.close());
        }
        await(journal.close());
    }
    private static void binding() {
        final var subject = new GameplaySourceSubject(UUID.randomUUID(), GameplaySourceSubject.Kind.MOB);
        rejects(() -> GameplaySourceCaptureGate.capture(subject));
        final var first = GameplaySourceCaptureGate.install(value -> List.of());
        rejects(() -> GameplaySourceCaptureGate.install(value -> List.of())); first.close();
        try (final var next = GameplaySourceCaptureGate.install(value -> List.of())) {
            first.close(); check(GameplaySourceCaptureGate.capture(subject).isEmpty(), "old binding retired new source consumer");
        }
        try (final var broken = GameplaySourceCaptureGate.install(value -> { throw new LinkageError("private details"); })) { rejects(() -> GameplaySourceCaptureGate.capture(subject)); }
        final AtomicReference<GameplaySourceCaptureGate.Binding> current = new AtomicReference<>();
        current.set(GameplaySourceCaptureGate.install(value -> { current.get().close(); return List.of(); }));
        rejects(() -> GameplaySourceCaptureGate.capture(subject));
        try (final var oversized = GameplaySourceCaptureGate.install(value -> java.util.stream.IntStream.range(0, 33).mapToObj(i -> (RewardSource) new RewardSource.Entity(UUID.randomUUID())).toList())) {
            rejects(() -> GameplaySourceCaptureGate.capture(subject));
        }
    }
    private static void rejects(Runnable action) { try { action.run(); throw new AssertionError("expected refusal"); } catch (RuntimeException expected) { assertions++; } }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
