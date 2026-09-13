package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.integrity.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static hu.taliann.icesmp.managers.ServerChallengeManager.ChallengeType.*;

public final class ServerChallengeIntegrityRegressionSuite {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final RewardSource ENTITY = new RewardSource.Entity(UUID.randomUUID());
    private static final Set<RewardSource> TAINTED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> QUARANTINED = ConcurrentHashMap.newKeySet();
    private static int assertions;
    public static void main(String[] args) throws Exception {
        try (var binding = GameplayRewardGate.install(new InfluenceRewardEligibilityPolicy(new InfluenceRewardEligibilityPolicy.Lookup() {
            public InfluenceRewardEligibilityPolicy.Evidence recipient(UUID id) { return QUARANTINED.contains(id)
                    ? InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED : InfluenceRewardEligibilityPolicy.Evidence.CLEAN; }
            public InfluenceRewardEligibilityPolicy.Evidence source(RewardSource source) { return TAINTED.contains(source)
                    ? InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED : InfluenceRewardEligibilityPolicy.Evidence.CLEAN; }
        }))) { sources(); lifecycle(); contention(); delivery(); }
        final var unbound = run(1); check(!unbound.record(SLAY, context(ENTITY), 100), "unbound progress");
        try (var binding = GameplayRewardGate.install(context -> { throw new IllegalStateException("unavailable"); })) {
            check(!unbound.record(SLAY, context(ENTITY), 100), "broken policy progress");
        }
        check(unbound.snapshot().progress() == 0, "unbound/broken policy mutated counter");
        contracts();
        System.out.println("Server challenge integrity passed: " + assertions + " assertions; native source admission, lifecycle contention and final recipient/event checks.");
    }
    private static void sources() {
        for (var source : List.of(ENTITY, new RewardSource.Player(UUID.randomUUID()), new RewardSource.Item(UUID.randomUUID()),
                new RewardSource.Event("fixture", UUID.randomUUID()), new RewardSource.World(UUID.randomUUID()),
                new RewardSource.Location(UUID.randomUUID(), 4, 64, 8))) {
            final var run = run(2); final var before = run.snapshot(); TAINTED.add(source);
            check(!run.record(SLAY, context(source), 100) && run.snapshot().equals(before), "tainted source changed native counter");
            TAINTED.clear(); check(run.record(SLAY, context(source), 100), "clean native contribution refused");
        }
        final var run = run(2); QUARANTINED.add(PLAYER);
        check(!run.record(SLAY, context(ENTITY), 100), "quarantined contributor counted"); QUARANTINED.clear();
        TAINTED.add(run.snapshot().source()); check(!run.record(SLAY, context(ENTITY), 100), "tainted event counted clean contribution"); TAINTED.clear();
        final List<RewardSource> cap = new ArrayList<>(); for (int i = 0; i < 64; i++) cap.add(new RewardSource.Entity(UUID.randomUUID()));
        check(!run.record(SLAY, new RewardContext(RewardChannel.SERVER_CHALLENGE, PLAYER, cap), 100), "event source truncated at cap");
        cap.removeLast(); check(run.record(SLAY, new RewardContext(RewardChannel.SERVER_CHALLENGE, PLAYER, cap), 100), "64-source context refused");
        expect(() -> run.record(SLAY, RewardContext.recipientOnly(RewardChannel.KILL_REWARD, PLAYER), 100));
    }
    private static void lifecycle() {
        final var run = run(1); final var before = run.snapshot();
        check(!run.record(MINE, context(ENTITY), 100), "wrong objective counted");
        check(!run.record(SLAY, context(ENTITY), 99), "clock rollback counted");
        check(!run.record(SLAY, context(ENTITY), 200), "expired window counted before tick");
        check(run.snapshot().equals(before), "refusal changed snapshot");
        check(run.record(SLAY, context(ENTITY), 199), "last valid contribution refused");
        check(run.snapshot().status() == ServerChallengeRun.Status.SUCCEEDED && run.snapshot().progress() == 1, "success was not atomic");
        check(!run.record(SLAY, context(ENTITY), 199) && !run.expire(200) && !run.stop(), "completed window reopened");
        final var next = run(1); check(!next.snapshot().id().equals(run.snapshot().id()), "new lifecycle reused event identity");
        check(next.expire(200) && !next.expire(201) && !next.record(SLAY, context(ENTITY), 199), "expiry was not terminal");
        final var stopped = run(1); check(stopped.stop() && !stopped.stop() && !stopped.record(SLAY, context(ENTITY), 100), "stop was not terminal");
        check(run.snapshot().status() == ServerChallengeRun.Status.SUCCEEDED, "next lifecycle mutated earned result");
        expect(() -> new ServerChallengeRun(SLAY, 0, 100, 100));
        expect(() -> new ServerChallengeRun(SLAY, 1, -1, 100));
        expect(() -> new ServerChallengeRun(SLAY, 1, 100, 0));
    }
    private static void contention() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int round = 0; round < 30; round++) {
                final var run = run(37); final AtomicInteger accepted = new AtomicInteger();
                final List<Future<?>> workers = new ArrayList<>(); final CountDownLatch start = new CountDownLatch(1);
                for (int i = 0; i < 8; i++) workers.add(executor.submit(() -> {
                    start.await(); for (int j = 0; j < 20; j++) if (run.record(SLAY, context(ENTITY), 100)) accepted.incrementAndGet(); return null;
                }));
                start.countDown(); for (var worker : workers) worker.get(5, TimeUnit.SECONDS);
                check(accepted.get() == 37 && run.snapshot().progress() == 37 && run.snapshot().status() == ServerChallengeRun.Status.SUCCEEDED,
                        "concurrent contributions duplicated completion or lost progress");
                final var race = run(1); final CountDownLatch raceStart = new CountDownLatch(1);
                var record = executor.submit(() -> { raceStart.await(); return race.record(SLAY, context(ENTITY), 199); });
                var expire = executor.submit(() -> { raceStart.await(); return race.expire(200); });
                raceStart.countDown(); final boolean counted = record.get(5, TimeUnit.SECONDS), expired = expire.get(5, TimeUnit.SECONDS);
                check(counted != expired && race.snapshot().progress() == (counted ? 1 : 0), "deadline race had two winners");
            }
        }
    }
    private static void delivery() {
        final var run = run(1); final var active = run.snapshot(); run.record(SLAY, context(ENTITY), 100); final var earned = run.snapshot();
        for (var channel : List.of(RewardChannel.SERVER_CHALLENGE, RewardChannel.ITEM_ACQUISITION, RewardChannel.VANILLA_XP, RewardChannel.EVENT_REWARD)) {
            final var reward = new RewardContext(channel, PLAYER, List.of(ENTITY));
            check(!ServerChallengeRun.rewardAllowed(active, reward), "unearned challenge paid");
            check(ServerChallengeRun.rewardAllowed(earned, reward), "clean earned challenge refused");
            QUARANTINED.add(PLAYER); check(!ServerChallengeRun.rewardAllowed(earned, reward), "recipient drift paid"); QUARANTINED.clear();
            TAINTED.add(earned.source()); check(!ServerChallengeRun.rewardAllowed(earned, reward), "old event drift paid"); TAINTED.clear();
            TAINTED.add(ENTITY); check(!ServerChallengeRun.rewardAllowed(earned, reward), "delivery source drift paid"); TAINTED.clear();
            final var next = run(1); TAINTED.add(next.snapshot().source());
            check(ServerChallengeRun.rewardAllowed(earned, reward), "new event replaced original reward identity"); TAINTED.clear();
        }
    }
    private static void contracts() throws Exception {
        final String manager = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/managers/ServerChallengeManager.java"));
        final String listener = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/listeners/ServerChallengeListener.java"));
        check(manager.contains("public synchronized void record(") && manager.contains("public synchronized void tick()")
                && manager.contains("public synchronized boolean forceStart()") && manager.contains("public synchronized void shutdown()"), "native manager lifecycle lock missing");
        check(listener.contains("kill.rewardContext(RewardChannel.SERVER_CHALLENGE)") && listener.contains("BukkitRewardSources.block(block)")
                && listener.contains("BukkitRewardSources.causal(player)"), "native source ingress dropped provenance");
        check(manager.contains("final Player player = Bukkit.getPlayer(recipient)") && manager.contains("Bukkit.isOwnedByCurrentRegion(player)")
                && manager.contains("player.isOnline()") && manager.contains("Bukkit.getGlobalRegionScheduler()"), "owner/global delivery continuation missing");
        for (String channel : List.of("SERVER_CHALLENGE", "ITEM_ACQUISITION", "VANILLA_XP", "EVENT_REWARD"))
            check(manager.contains("rewardAllowed(completed, player, RewardChannel." + channel + ")"), "native payout channel missing");
    }
    private static ServerChallengeRun run(long target) { return new ServerChallengeRun(SLAY, target, 100, 100); }
    private static RewardContext context(RewardSource source) { return new RewardContext(RewardChannel.SERVER_CHALLENGE, PLAYER, List.of(source)); }
    private static void expect(Runnable action) { try { action.run(); throw new AssertionError("invalid input accepted"); } catch (IllegalArgumentException expected) { assertions++; } }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
