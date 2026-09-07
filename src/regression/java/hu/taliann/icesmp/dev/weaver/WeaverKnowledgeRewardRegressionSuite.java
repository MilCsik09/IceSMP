package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.playerprofile.application.*;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore.*;
import hu.taliann.icesmp.playerprofile.domain.*;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real achievement/Bestiary/discovery CAS, atomic milestone outbox and canonical economy replay. */
public final class WeaverKnowledgeRewardRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000971");
    private static final RewardSource SOURCE = new RewardSource.Entity(UUID.randomUUID());
    private static final Set<RewardSource> TAINTED = new HashSet<>();
    private static final AtomicBoolean QUARANTINED = new AtomicBoolean();
    private static final PendingReward MILESTONE = new PendingReward("bestiary:mobs:1", RewardKind.CURRENCY, 10, "neutral");
    private static int assertions;

    public static void main(String[] args) throws Exception {
        try (var binding = GameplayRewardGate.install(context -> QUARANTINED.get() || context.sources().stream().anyMatch(TAINTED::contains)
                ? RewardDecision.deny("QUARANTINED") : RewardDecision.allow())) {
            queuedAdmissions();
            atomicMilestoneAndDelivery();
            milestoneCrashBoundaries();
            acceptedCurrencyReplay();
        }
        unboundRefusesNewState();
        nativeContracts();
        System.out.println("Knowledge reward admission passed: " + assertions
                + " assertions; queued provenance, atomic Bestiary/outbox WAL, exact pending delivery and canonical economy receipt replay.");
    }

    private static void queuedAdmissions() throws Exception {
        final Path root = Files.createTempDirectory("weaver-knowledge-admit-");
        final List<RewardSource> sources = List.of(SOURCE, new RewardSource.Player(UUID.randomUUID()),
                new RewardSource.Item(UUID.randomUUID()), new RewardSource.Event("fixture", UUID.randomUUID()),
                new RewardSource.World(UUID.randomUUID()), new RewardSource.Location(UUID.randomUUID(), 1, 64, 2));
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); final var disk = bytes(root); int number = 0;
            for (var source : sources) {
                final String key = "fixture_" + number++;
                var unlock = h.store.unlock(PLAYER, key, context(RewardChannel.ACHIEVEMENT, source));
                var discover = h.store.markHiddenSpotVisited(PLAYER, key, context(RewardChannel.DISCOVERY, source));
                var bestiary = h.store.recordBestiaryWithRewards(PLAYER, "mobs", key, Map.of(1, MILESTONE), context(RewardChannel.BESTIARY, source));
                var reserve = h.store.reserveReward(PLAYER, new PendingReward("achievement:" + key, RewardKind.NONE, 0, ""), context(RewardChannel.ACHIEVEMENT, source));
                TAINTED.add(source);
                h.denied(unlock); h.denied(discover); h.denied(bestiary); h.denied(reserve);
                check(bytes(root).equals(disk), "denied knowledge source wrote discovery, milestone, receipt or revision");
                TAINTED.clear();
            }
            var recipient = h.store.recordBestiary(PLAYER, "mobs", "clean_source", context(RewardChannel.BESTIARY, SOURCE));
            QUARANTINED.set(true); h.denied(recipient);
            check(bytes(root).equals(disk), "recipient quarantine did not fence final knowledge admission"); QUARANTINED.set(false);
            expect(IllegalArgumentException.class, () -> h.store.recordBestiary(PLAYER, "mobs", "wrong", context(RewardChannel.ACHIEVEMENT, SOURCE)));
            expect(IllegalArgumentException.class, () -> h.store.reserveReward(PLAYER, MILESTONE, context(RewardChannel.ACHIEVEMENT, SOURCE)));
            expect(IllegalArgumentException.class, () -> h.store.unlock(PLAYER, "wrong", context(RewardChannel.DISCOVERY, SOURCE)));
            expect(IllegalArgumentException.class, () -> h.store.markHiddenSpotVisited(PLAYER, "wrong", context(RewardChannel.ACHIEVEMENT, SOURCE)));
            expect(IllegalArgumentException.class, () -> h.store.recordBestiaryWithRewards(PLAYER, "mobs", "wrong", Map.of(2, MILESTONE), context(RewardChannel.BESTIARY, SOURCE)));
            final Map<Integer, PendingReward> tooMany = new HashMap<>();
            for (int n = 1; n <= 129; n++) tooMany.put(n, new PendingReward("bestiary:mobs:" + n, RewardKind.NONE, 0, ""));
            expect(IllegalArgumentException.class, () -> h.store.recordBestiaryWithRewards(PLAYER, "mobs", "wrong", tooMany, context(RewardChannel.BESTIARY, SOURCE)));
        } finally { TAINTED.clear(); QUARANTINED.set(false); delete(root); }
    }

    private static void atomicMilestoneAndDelivery() throws Exception {
        final Path root = Files.createTempDirectory("weaver-bestiary-outbox-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); final long initial = h.snapshot().profileRevision();
            var accepted = h.finish(h.record());
            check(accepted.record().created() && accepted.record().categoryCount() == 1 && accepted.pending().equals(List.of(MILESTONE)),
                    "entry and milestone did not share the admitted result");
            check(h.snapshot().profileRevision() == initial + 1 && h.store.pendingReward(PLAYER, MILESTONE.receiptId()).orElseThrow().equals(MILESTONE),
                    "entry and pending reward were split into different WAL generations");
            QUARANTINED.set(true); final var disk = bytes(root);
            final var changedCurrency = new PendingReward(MILESTONE.receiptId(), RewardKind.CURRENCY, 100, "red");
            var replay = h.finish(h.store.recordBestiaryWithRewards(PLAYER, "mobs", "zombie", Map.of(1, changedCurrency), context(RewardChannel.BESTIARY, SOURCE)));
            check(!replay.record().created() && replay.pending().equals(List.of(MILESTONE)) && bytes(root).equals(disk),
                    "later config/faction/quarantine changed an accepted pending payload");
            expect(IllegalStateException.class, () -> h.store.deliverReward(PLAYER, changedCurrency, pending -> { throw new AssertionError("forged delivery entered"); }));
            final PendingReward missing = new PendingReward("achievement:missing", RewardKind.CURRENCY, 10, "neutral");
            check(!h.finish(h.store.deliverReward(PLAYER, missing, pending -> { throw new AssertionError("missing outbox delivered"); })),
                    "missing pending receipt authorized a delivery");
            final var held = new CompletableFuture<Boolean>();
            var delivery = h.store.deliverReward(PLAYER, MILESTONE, pending -> held);
            check(!delivery.toCompletableFuture().isDone() && h.store.pendingReward(PLAYER, MILESTONE.receiptId()).isPresent()
                    && bytes(root).equals(disk), "pending settled before canonical delivery acknowledgement");
            held.complete(false); check(!h.finish(delivery) && bytes(root).equals(disk), "refused delivery consumed its pending reward");
            var failure = h.store.deliverReward(PLAYER, MILESTONE, pending -> CompletableFuture.failedFuture(new IOException("native delivery failed")));
            expect(IOException.class, () -> h.finish(failure));
            check(bytes(root).equals(disk), "failed delivery erased pending state");
            check(h.finish(h.currencyDelivery()), "canonical currency payout did not settle");
            check(h.balance() == 10_000 && h.store.rewardSettled(PLAYER, MILESTONE.receiptId()), "wallet acknowledgement and settlement disagree");
            final var paid = bytes(root);
            check(!h.finish(h.currencyDelivery()) && h.balance() == 10_000 && bytes(root).equals(paid), "settled payout was delivered twice");
        } finally { QUARANTINED.set(false); delete(root); }
    }

    private static void milestoneCrashBoundaries() throws Exception {
        for (boolean afterManifest : List.of(false, true)) {
            final Path root = Files.createTempDirectory("weaver-bestiary-crash-"); final AtomicBoolean armed = new AtomicBoolean();
            try {
                try (var h = new Harness(root, fault(armed, afterManifest))) {
                    h.load(); armed.set(true); expect(IOException.class, () -> h.finish(h.record()));
                }
                QUARANTINED.set(true);
                try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
                    h.load();
                    check(h.store.bestiaryEntries(PLAYER, "mobs").contains("zombie") == afterManifest, "entry violated canonical crash boundary");
                    check(h.store.pendingReward(PLAYER, MILESTONE.receiptId()).isPresent() == afterManifest,
                            "crash split Bestiary progression from milestone entitlement");
                    if (afterManifest) check(h.finish(h.currencyDelivery()) && h.balance() == 10_000, "accepted restart milestone was lost");
                    else h.denied(h.record());
                }
            } finally { QUARANTINED.set(false); delete(root); }
        }
    }

    private static void acceptedCurrencyReplay() throws Exception {
        final Path root = Files.createTempDirectory("weaver-knowledge-wallet-replay-"); final AtomicBoolean armed = new AtomicBoolean();
        try {
            try (var h = new Harness(root, fault(armed, true))) {
                h.load(); h.finish(h.record()); armed.set(true);
                expect(IOException.class, () -> h.finish(h.currencyDelivery()));
                check(h.store.pendingReward(PLAYER, MILESTONE.receiptId()).isPresent(), "lost wallet acknowledgement prematurely settled outbox");
            }
            QUARANTINED.set(true);
            try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
                h.load(); check(h.balance() == 10_000, "committed wallet receipt did not survive restart");
                check(h.finish(h.currencyDelivery()), "canonical wallet receipt replay could not settle the existing outbox");
                check(h.balance() == 10_000 && h.store.pendingRewards(PLAYER).isEmpty(), "restart replay duplicated or lost currency reward");
            }
        } finally { QUARANTINED.set(false); delete(root); }
    }

    private static void unboundRefusesNewState() throws Exception {
        final Path root = Files.createTempDirectory("weaver-knowledge-unbound-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); final var disk = bytes(root); h.denied(h.store.unlock(PLAYER, "unbound"));
            h.denied(h.store.recordBestiary(PLAYER, "mobs", "unbound"));
            h.denied(h.store.markHiddenSpotVisited(PLAYER, "unbound")); h.denied(h.store.reserveReward(PLAYER, MILESTONE));
            check(bytes(root).equals(disk), "unavailable reward policy admitted canonical knowledge state");
        } finally { delete(root); }
    }

    private static void nativeContracts() throws Exception {
        final String base = "src/main/java/hu/taliann/icesmp/";
        final String bestiary = Files.readString(Path.of(base + "managers/BestiaryManager.java"));
        final String achievements = Files.readString(Path.of(base + "managers/AchievementManager.java"));
        final String kill = Files.readString(Path.of(base + "listeners/BestiaryListener.java"));
        check(bestiary.contains("recordBestiaryWithRewards") && bestiary.contains("delivery.settlePendingReward(playerId, pending)")
                && !bestiary.contains("creditOnceDurably("), "native Bestiary bypassed atomic/shared owner-safe delivery");
        check(achievements.contains("store.deliverReward(playerId, expected") && achievements.contains("delivering.size() >= 128")
                && achievements.contains("Bukkit.isOwnedByCurrentRegion(owned)"), "native settlement lost exact outbox/bounded owner admission");
        final String currency = achievements.substring(achievements.indexOf("private CompletionStage<Boolean> deliverCurrency"), achievements.indexOf("public boolean isEarned"));
        check(currency.contains("CompletableFuture.supplyAsync") && currency.contains("creditOnceDurably(playerId") && !currency.contains("player."),
                "currency durability still blocks an owner or retains a live player");
        check(kill.contains("kill.rewardContext(hu.taliann.icesmp.integrity.RewardChannel.BESTIARY)"), "native kill source did not reach profile admission");
    }

    private static RewardContext context(RewardChannel channel, RewardSource source) { return new RewardContext(channel, PLAYER, List.of(source)); }
    private static YamlPlayerProfileRepository.FaultInjector fault(AtomicBoolean armed, boolean afterManifest) {
        return new YamlPlayerProfileRepository.FaultInjector() {
            public void afterSectionsMovedBeforeManifest(UUID id, Set<ProfileSectionId> sections) throws IOException {
                if (!afterManifest && armed.getAndSet(false)) throw new IOException("injected before manifest");
            }
            public void afterManifestMovedBeforeCleanup(UUID id, Set<ProfileSectionId> sections) throws IOException {
                if (afterManifest && armed.getAndSet(false)) throw new IOException("injected after manifest");
            }
        };
    }
    private static final class Harness implements AutoCloseable {
        final ManualExecutor io = new ManualExecutor(); final YamlPlayerProfileRepository repository;
        final PlayerProfileAchievementStore store = new PlayerProfileAchievementStore();
        final PlayerProfileEconomyStore economy = new PlayerProfileEconomyStore(); final PlayerProfileAuthority authority;
        Harness(Path root, YamlPlayerProfileRepository.FaultInjector fault) {
            repository = new YamlPlayerProfileRepository(root, Clock.systemUTC(), io, fault);
            final var transactions = new YamlPlayerProfileTransactionManager(repository);
            authority = PlayerProfileAuthority.install(new PlayerProfileService(repository, transactions), repository, transactions);
        }
        void load() { finish(repository.load(PLAYER)); }
        PlayerProfileSnapshot snapshot() { return repository.cached(PLAYER).orElseThrow(); }
        long balance() { return economy.readCached(PLAYER).milli(CurrencyType.NEUTRAL); }
        CompletionStage<BestiaryAdmission> record() { return store.recordBestiaryWithRewards(PLAYER, "mobs", "zombie", Map.of(1, MILESTONE), context(RewardChannel.BESTIARY, SOURCE)); }
        CompletionStage<Boolean> currencyDelivery() {
            return store.deliverReward(PLAYER, MILESTONE, pending -> economy.creditOnce(PLAYER, CurrencyType.NEUTRAL,
                    Math.multiplyExact(pending.amount(), 1000), "achievement-currency:" + pending.receiptId()).thenApply(ignored -> true));
        }
        <T> T finish(CompletionStage<T> stage) {
            for (int n = 0; !stage.toCompletableFuture().isDone() && n < 100; n++) io.one();
            check(stage.toCompletableFuture().isDone(), "bounded storage drain failed"); return stage.toCompletableFuture().join();
        }
        void denied(CompletionStage<?> stage) { expect(RewardEligibilityDeniedException.class, () -> finish(stage)); }
        public void close() throws Exception {
            authority.uninstall(); check(repository.shutdown(Duration.ofSeconds(1)).toCompletableFuture().get(5, TimeUnit.SECONDS).drained(), "storage shutdown did not drain");
        }
    }
    private static final class ManualExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>(); boolean stopped;
        public void execute(Runnable command) { if (stopped) throw new RejectedExecutionException(); queue.add(command); }
        void one() { final var next = queue.poll(); if (next == null) throw new AssertionError("no storage task"); next.run(); }
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; final var rest = List.copyOf(queue); queue.clear(); return rest; }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped && queue.isEmpty(); }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }
    private static Map<String, String> bytes(Path root) throws Exception {
        final Map<String, String> result = new TreeMap<>();
        try (var files = Files.walk(root)) {
            for (var p : files.filter(Files::isRegularFile).toList()) result.put(root.relativize(p).toString(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));
        }
        return result;
    }
    private static void expect(Class<? extends Throwable> type, Runnable action) {
        assertions++; try { action.run(); throw new AssertionError("Expected " + type.getSimpleName()); }
        catch (Throwable failure) {
            while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
            if (!type.isInstance(failure)) throw new AssertionError("Expected " + type.getSimpleName() + ", got " + failure, failure);
        }
    }
    private static void delete(Path root) throws IOException { try (var paths = Files.walk(root)) { for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
