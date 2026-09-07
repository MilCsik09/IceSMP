package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.application.*;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WeaverSeasonRewardRegressionSuite {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final RewardSource ENTITY = new RewardSource.Entity(UUID.randomUUID());
    private static final Set<RewardSource> TAINTED = new HashSet<>();
    private static final AtomicBoolean QUARANTINED = new AtomicBoolean();
    private static int assertions;
    public static void main(String[] args) throws Exception {
        try (var binding = GameplayRewardGate.install(context -> QUARANTINED.get() || context.sources().stream().anyMatch(TAINTED::contains)
                ? RewardDecision.deny("QUARANTINED") : RewardDecision.allow())) {
            admissions(); generations();
        }
        unbound(); contracts();
        System.out.println("Season participation reward admission passed: " + assertions + " assertions; canonical source WAL gate, deduplication, membership and generation isolation.");
    }
    private static void admissions() throws Exception {
        final Path root = Files.createTempDirectory("weaver-season-admit-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); final var before = bytes(root);
            for (var source : List.of(ENTITY, new RewardSource.Player(UUID.randomUUID()), new RewardSource.Item(UUID.randomUUID()),
                    new RewardSource.Event("fixture", UUID.randomUUID()), new RewardSource.World(UUID.randomUUID()),
                    new RewardSource.Location(UUID.randomUUID(), 1, 64, 2))) {
                var pending = h.store.record(PLAYER, FactionType.RED, 1, "community", context(source));
                TAINTED.add(source); h.denied(pending); TAINTED.clear();
                check(bytes(root).equals(before) && h.store.contributions(PLAYER, FactionType.RED, 1) == 0, "denied source wrote participation or receipt");
            }
            var pending = h.store.record(PLAYER, FactionType.RED, 1, "community");
            QUARANTINED.set(true); h.denied(pending); QUARANTINED.set(false);
            check(bytes(root).equals(before), "compatibility recipient quarantine wrote state");
            expect(IllegalArgumentException.class, () -> h.store.record(PLAYER, FactionType.RED, 1, "community",
                    new RewardContext(RewardChannel.SEASON_CREDIT, UUID.randomUUID(), List.of(ENTITY))));
            expect(IllegalArgumentException.class, () -> h.store.record(PLAYER, FactionType.RED, 1, "community",
                    RewardContext.recipientOnly(RewardChannel.COMMUNITY_GOAL, PLAYER)));
            check(!h.finish(h.store.record(PLAYER, FactionType.BLUE, 1, "community", context(ENTITY))), "wrong faction gained activity");
            check(bytes(root).equals(before), "wrong faction rewrote profile");
            check(h.finish(h.store.record(PLAYER, FactionType.RED, 1, "community", context(ENTITY))), "clean contribution refused");
            final var earned = bytes(root); final var first = h.finish(h.store.load(PLAYER)).orElseThrow();
            check(first.profileRevision() == h.repository.cached(PLAYER).orElseThrow().profileRevision(), "participation omitted observed generation");
            check(!h.finish(h.store.record(PLAYER, FactionType.RED, 1, "community", context(ENTITY))) && bytes(root).equals(earned), "daily category replay wrote again");
            QUARANTINED.set(true); h.repository.invalidate(PLAYER); h.finish(h.repository.loadSnapshot(PLAYER));
            check(h.store.contributions(PLAYER, FactionType.RED, 1) == 1 && h.finish(h.store.load(PLAYER)).orElseThrow().equals(first), "later quarantine erased accepted activity");
            QUARANTINED.set(false);
            h.clock.addAndGet(-10);
            check(h.finish(h.store.record(PLAYER, FactionType.RED, 1, "raid", context(ENTITY))), "different category refused");
            final var newer = h.finish(h.store.load(PLAYER)).orElseThrow();
            check(newer.lastActive() == first.lastActive(), "out-of-order activity moved last-active backwards");
            check(PlayerProfileSeasonParticipationStore.State.newest(newer, first).equals(newer)
                    && PlayerProfileSeasonParticipationStore.State.newest(first, newer).equals(newer), "out-of-order callback rolled back projection");
            check(h.store.matchesCurrentMembership(PLAYER, newer), "current membership refused");
            h.finish(h.factions.assign(PLAYER, FactionType.BLUE));
            check(!h.store.matchesCurrentMembership(PLAYER, newer) && h.finish(h.store.load(PLAYER)).isEmpty(), "retired membership projection remained eligible");
            check(h.store.contributions(PLAYER, FactionType.BLUE, 1) == 0, "membership change inherited activity");
            check(h.finish(h.store.record(PLAYER, FactionType.BLUE, 2, "community", context(ENTITY))), "new season/faction activity refused");
            check(h.store.contributions(PLAYER, FactionType.BLUE, 2) == 1 && h.store.contributions(PLAYER, FactionType.RED, 1) == 0, "new membership/season mixed old receipts");
            final var nextSeason = bytes(root);
            check(!h.finish(h.store.record(PLAYER, FactionType.BLUE, 1, "war", context(ENTITY))) && bytes(root).equals(nextSeason),
                    "late previous-season contribution overwrote current season activity");
        } finally { TAINTED.clear(); QUARANTINED.set(false); delete(root); }
    }
    private static void generations() {
        final var first = new PlayerProfileSeasonParticipationStore.State(1, "RED", 100, 101, Set.of("community:1"), 9);
        final var later = new PlayerProfileSeasonParticipationStore.State(2, "BLUE", 200, 201, Set.of("raid:2"), 10);
        check(PlayerProfileSeasonParticipationStore.State.newest(later, first).equals(later), "cross-season late callback won");
        check(PlayerProfileSeasonParticipationStore.State.newest(first, first).equals(first), "identical generation conflicted");
        final var conflict = new PlayerProfileSeasonParticipationStore.State(1, "RED", 100, 101, Set.of("raid:1"), 9);
        expect(IllegalStateException.class, () -> PlayerProfileSeasonParticipationStore.State.newest(first, conflict));
    }
    private static void unbound() throws Exception {
        final Path root = Files.createTempDirectory("weaver-season-unbound-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); final var before = bytes(root);
            h.denied(h.store.record(PLAYER, FactionType.RED, 1, "community", context(ENTITY)));
            check(bytes(root).equals(before), "unbound season write");
        } finally { delete(root); }
    }
    private static void contracts() throws Exception {
        final var community = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/managers/CommunityGoalManager.java"));
        final var listener = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/listeners/QuestProgressListener.java"));
        final var season = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/managers/SeasonManager.java"));
        check(community.contains("sources.addAll(fresh.sources())") && community.contains("GameplayRewardGate.evaluate(complete)")
                && community.contains("Bukkit.isOwnedByCurrentRegion(player)"), "community admission discarded current owner sources");
        check(community.contains("new RewardContext(RewardChannel.SEASON_CREDIT, playerId, reward.sources())"), "community contribution lost season provenance");
        check(community.contains("new RewardSource.Event(\"community-goal\", pending.completionId())")
                && community.contains("final Player online = Bukkit.getPlayer(playerId)") && community.contains("RewardChannel.EVENT_REWARD"), "native community buff lost owner/event admission");
        check(listener.contains("kill.rewardContext(RewardChannel.COMMUNITY_GOAL)") && listener.contains("communityContext(reward)"), "quest listener lost community provenance");
        check(season.contains("participation.record(playerId, faction, season, source, reward)")
                && season.contains("State::newest") && season.contains("participation.matchesCurrentMembership(playerId, state)"), "season projection dropped source or generation boundary");
    }
    private static RewardContext context(RewardSource source) { return new RewardContext(RewardChannel.SEASON_CREDIT, PLAYER, List.of(source)); }
    private static final class Harness implements AutoCloseable {
        final ManualExecutor io = new ManualExecutor(); final YamlPlayerProfileRepository repository;
        final AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        final PlayerProfileSeasonParticipationStore store = new PlayerProfileSeasonParticipationStore(clock::get);
        final PlayerProfileFactionStore factions = new PlayerProfileFactionStore(); final PlayerProfileAuthority authority;
        Harness(Path root, YamlPlayerProfileRepository.FaultInjector fault) {
            repository = new YamlPlayerProfileRepository(root, Clock.systemUTC(), io, fault);
            final var transactions = new YamlPlayerProfileTransactionManager(repository);
            authority = PlayerProfileAuthority.install(new PlayerProfileService(repository, transactions), repository, transactions);
        }
        void load() { finish(repository.load(PLAYER)); finish(factions.assign(PLAYER, FactionType.RED)); }
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
