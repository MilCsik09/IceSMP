package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.playerprofile.application.*;
import hu.taliann.icesmp.playerprofile.domain.*;
import hu.taliann.icesmp.playerprofile.domain.section.*;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real conditional profile CAS/WAL, profession XP and weekly-credit admission under queued drift. */
public final class WeaverProfessionRewardRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000961");
    private static final ProfessionType PROFESSION = ProfessionType.MINER;
    private static final RewardSource BLOCK = new RewardSource.Location(UUID.randomUUID(), 17, 70, 18);
    private static final Set<RewardSource> TAINTED = new HashSet<>();
    private static final AtomicBoolean QUARANTINED = new AtomicBoolean();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        try (var binding = GameplayRewardGate.install(context ->
                QUARANTINED.get() || context.sources().stream().anyMatch(TAINTED::contains)
                        ? RewardDecision.deny("QUARANTINED") : RewardDecision.allow())) {
            admissionAndRetry();
            weeklyCreditsSurviveRestart();
            walAcknowledgementLoss();
        }
        unavailablePolicy();
        nativeCallSiteContracts();
        System.out.println("Profession reward admission passed: " + assertions
                + " assertions; real generation-fenced CAS, queued source/recipient refusal, bounded retry, weekly entitlement and accepted WAL recovery.");
    }

    private static void admissionAndRetry() throws Exception {
        final Path root = Files.createTempDirectory("weaver-profession-cas-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); final var initial = h.snapshot(); final var disk = bytes(root);
            final var notifications = new AtomicInteger();
            h.service.subscribe((id, revision, sections) -> notifications.incrementAndGet());
            var sourceDenied = h.xp(11, new AtomicInteger());
            check(!sourceDenied.toCompletableFuture().isDone(), "XP did not await canonical storage admission");
            TAINTED.add(BLOCK); h.denied(sourceDenied);
            check(h.snapshot().profileRevision() == initial.profileRevision()
                    && h.snapshot().section(ProfileSectionId.PROFESSIONS).orElseThrow().revision()
                    == initial.section(ProfileSectionId.PROFESSIONS).orElseThrow().revision()
                    && bytes(root).equals(disk), "denied source changed durable profile, revision or WAL");
            check(notifications.get() == 0, "denied reward emitted a committed profile change"); TAINTED.clear();
            var recipientDenied = h.xp(12, new AtomicInteger()); QUARANTINED.set(true); h.denied(recipientDenied);
            check(bytes(root).equals(disk), "queued recipient quarantine wrote XP"); QUARANTINED.set(false);

            final AtomicInteger attempts = new AtomicInteger();
            var first = h.xp(10, new AtomicInteger()); var conflicting = h.xp(20, attempts);
            h.io.one(); check(first.toCompletableFuture().isDone(), "first canonical reward not acknowledged");
            h.io.one(); check(attempts.get() == 2 && !conflicting.toCompletableFuture().isDone(), "stale CAS did not retry from durable cache");
            TAINTED.add(BLOCK); h.denied(conflicting);
            check(h.xp() == 10 && notifications.get() == 1, "CAS retry lost original source context or duplicated notification"); TAINTED.clear();

            // A different section changes the profile generation while the profession revision stays stable.
            var other = h.service.mutateSection(PLAYER, ProfileSectionId.IDENTITY, IdentitySection.class, current -> current);
            final long professionRevision = h.snapshot().section(ProfileSectionId.PROFESSIONS).orElseThrow().revision();
            final AtomicInteger generationAttempts = new AtomicInteger();
            var afterGeneration = h.xp(7, generationAttempts);
            h.finish(other);
            check(h.snapshot().section(ProfileSectionId.PROFESSIONS).orElseThrow().revision() == professionRevision,
                    "unrelated generation fixture changed the profession revision");
            h.finish(afterGeneration);
            check(generationAttempts.get() == 2 && h.xp() == 17, "profile generation fence failed or applied reward twice");

            final var mismatched = new RewardContext(RewardChannel.PROFESSION_XP, UUID.randomUUID(), List.of(BLOCK));
            try {
                h.service.mutateRewardSectionConditional(PLAYER, ProfileSectionId.PROFESSIONS, ProfessionSection.class,
                        mismatched, current -> PlayerProfileService.ConditionalMutation.unchanged(false));
                throw new AssertionError("wrong recipient accepted");
            } catch (IllegalArgumentException expected) { assertions++; }
            final var beforeNoop = bytes(root); QUARANTINED.set(true);
            check(!h.finish(h.service.mutateRewardSectionConditional(PLAYER, ProfileSectionId.PROFESSIONS, ProfessionSection.class,
                    reward(RewardChannel.PROFESSION_XP), current -> PlayerProfileService.ConditionalMutation.unchanged(false))),
                    "no-change result was replaced with a reward");
            check(bytes(root).equals(beforeNoop), "no-op allocated a new receipt or revision");
        } finally { TAINTED.clear(); QUARANTINED.set(false); delete(root); }
    }

    private static void weeklyCreditsSurviveRestart() throws Exception {
        final Path root = Files.createTempDirectory("weaver-weekly-admission-");
        try {
            try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
                h.load(); final var store = new PlayerProfileWeeklyGoalStore();
                check(h.finish(store.recordContribution(PLAYER, PROFESSION, 100, 7, reward(RewardChannel.WEEKLY_GOAL))) == 100,
                        "clean contribution not committed");
                final var disk = bytes(root); final var denied = store.recordContribution(PLAYER, PROFESSION, 50, 7, reward(RewardChannel.WEEKLY_GOAL));
                TAINTED.add(BLOCK); h.denied(denied);
                check(bytes(root).equals(disk) && h.profession().weeklyProgress().get(PROFESSION.getId()) == 100,
                        "denied contribution advanced durable weekly credit"); TAINTED.clear();
                QUARANTINED.set(true);
                check(h.finish(store.award(PLAYER, 7, Map.of(PROFESSION.getId(), 300), 100)).get(PROFESSION.getId()) == 300,
                        "later quarantine revoked legitimately admitted weekly credit");
                final var awarded = bytes(root);
                check(h.finish(store.award(PLAYER, 7, Map.of(PROFESSION.getId(), 300), 100)).isEmpty()
                        && bytes(root).equals(awarded), "award replay wrote another pending reward");
            }
            try (var restarted = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
                restarted.load(); final var store = new PlayerProfileWeeklyGoalStore();
                check(PlayerProfileWeeklyGoalStore.pendingOf(restarted.profession()).get(PROFESSION.getId()) == 300,
                        "restart lost pending earned reward");
                check(restarted.finish(store.claim(PLAYER, 100, 15, 50)).size() == 1 && restarted.xp() == 300,
                        "restart quarantine revoked pending legitimate payout");
                final var disk = bytes(root);
                check(restarted.finish(store.claim(PLAYER, 100, 15, 50)).isEmpty() && bytes(root).equals(disk),
                        "claim replay duplicated profession XP");
                restarted.denied(store.recordContribution(PLAYER, PROFESSION, 1, 8));
                check(bytes(root).equals(disk), "compatibility contribution bypassed recipient quarantine");
                try {
                    store.recordContribution(PLAYER, PROFESSION, 1, 8, reward(RewardChannel.PROFESSION_XP));
                    throw new AssertionError("wrong contribution channel accepted");
                } catch (IllegalArgumentException expected) { assertions++; }
            }
        } finally { TAINTED.clear(); QUARANTINED.set(false); delete(root); }
    }

    private static void walAcknowledgementLoss() throws Exception {
        for (final boolean beforeManifest : List.of(true, false)) {
            final Path root = Files.createTempDirectory("weaver-profession-wal-"); final AtomicBoolean armed = new AtomicBoolean();
            final var fault = new YamlPlayerProfileRepository.FaultInjector() {
                public void afterSectionsMovedBeforeManifest(UUID id, Set<ProfileSectionId> sections) throws IOException {
                    if (beforeManifest && armed.getAndSet(false)) throw new IOException("injected WAL interruption");
                }
                public void afterManifestMovedBeforeCleanup(UUID id, Set<ProfileSectionId> sections) throws IOException {
                    if (!beforeManifest && armed.getAndSet(false)) throw new IOException("injected acknowledgement loss");
                }
            };
            try {
                try (var h = new Harness(root, fault)) {
                    h.load(); armed.set(true);
                    try { h.finish(h.xp(23, new AtomicInteger())); throw new AssertionError("fault not injected"); }
                    catch (CompletionException expected) { check(expected.getCause() instanceof IOException, "unexpected failure"); }
                }
                QUARANTINED.set(true);
                try (var restarted = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
                    restarted.load();
                    // The canonical repository rolls back before manifest publication and keeps
                    // the committed section after publication. Neither path reruns a reward.
                    final long expectedXp = beforeManifest ? 0 : 23;
                    check(restarted.xp() == expectedXp, "canonical WAL recovery violated its manifest commit boundary");
                    final var disk = bytes(root); restarted.denied(restarted.xp(1, new AtomicInteger()));
                    check(restarted.xp() == expectedXp && bytes(root).equals(disk), "new post-restart reward bypassed admission");
                }
            } finally { QUARANTINED.set(false); delete(root); }
        }
    }

    private static void unavailablePolicy() throws Exception {
        final Path root = Files.createTempDirectory("weaver-profession-unbound-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); final var disk = bytes(root); h.denied(h.xp(1, new AtomicInteger()));
            check(bytes(root).equals(disk), "unbound reward policy wrote profession XP");
        } finally { delete(root); }
    }

    private static void nativeCallSiteContracts() throws Exception {
        final String manager = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/managers/ProfessionManager.java"));
        final String listener = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/listeners/ProfessionXpListener.java"));
        check(manager.contains("mutateRewardSectionConditional") && manager.contains("require(RewardChannel.PROFESSION_XP")
                && manager.contains("cause instanceof RewardEligibilityDeniedException"), "native profession XP bypassed guarded CAS");
        check(listener.contains("BukkitRewardSources.block(block)") && listener.contains("BukkitRewardSources.causal(entity)")
                && listener.contains("totalXp, reward") && listener.contains("totalXp, contribution"), "native activity lost source lineage");
        check(manager.contains("runOnOwnerThread(playerId, owned ->") && manager.contains("Bukkit.isOwnedByCurrentRegion(owned)"),
                "post-commit profession feedback lost owner resolution");
        final String weekly = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/managers/ProfessionWeeklyGoalManager.java"));
        final int absent = weekly.indexOf("if (PlayerProfileAuthority.installed().isEmpty())");
        check(!weekly.substring(absent, weekly.indexOf("final long operationWeek", absent)).contains("addAndGet"),
                "unavailable canonical authority still minted shared weekly progress");
    }

    private static RewardContext reward(RewardChannel channel) { return new RewardContext(channel, PLAYER, List.of(BLOCK)); }
    private static final class Harness implements AutoCloseable {
        final ManualExecutor io = new ManualExecutor(); final YamlPlayerProfileRepository repository;
        final PlayerProfileService service; final PlayerProfileAuthority authority;
        Harness(Path root, YamlPlayerProfileRepository.FaultInjector fault) {
            repository = new YamlPlayerProfileRepository(root, Clock.systemUTC(), io, fault);
            final var transactions = new YamlPlayerProfileTransactionManager(repository);
            service = new PlayerProfileService(repository, transactions); authority = PlayerProfileAuthority.install(service, repository, transactions);
        }
        void load() { finish(repository.load(PLAYER)); }
        PlayerProfileSnapshot snapshot() { return repository.cached(PLAYER).orElseThrow(); }
        ProfessionSection profession() { return (ProfessionSection) snapshot().section(ProfileSectionId.PROFESSIONS).orElseThrow().value(); }
        long xp() { return ProfessionProfileState.experience(profession(), PROFESSION); }
        CompletionStage<Long> xp(int amount, AtomicInteger attempts) {
            return service.mutateRewardSectionConditional(PLAYER, ProfileSectionId.PROFESSIONS, ProfessionSection.class,
                    reward(RewardChannel.PROFESSION_XP), current -> {
                        attempts.incrementAndGet(); final var next = ProfessionProfileState.addExperience(current, PROFESSION, amount, 100, 15, 50);
                        return PlayerProfileService.ConditionalMutation.changed(next.section(), next.experience());
                    });
        }
        <T> T finish(CompletionStage<T> stage) {
            for (int n = 0; !stage.toCompletableFuture().isDone() && n < 100; n++) io.one();
            check(stage.toCompletableFuture().isDone(), "bounded IO drain failed"); return stage.toCompletableFuture().join();
        }
        void denied(CompletionStage<?> stage) {
            try { finish(stage); throw new AssertionError("reward admitted"); }
            catch (CompletionException expected) { check(expected.getCause() instanceof RewardEligibilityDeniedException, "unexpected denial cause"); }
        }
        public void close() throws Exception {
            authority.uninstall(); check(repository.shutdown(Duration.ofSeconds(1)).toCompletableFuture().get(5, TimeUnit.SECONDS).drained(), "shutdown did not drain");
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
            for (final Path p : files.filter(Files::isRegularFile).toList()) result.put(root.relativize(p).toString(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));
        }
        return result;
    }
    private static void delete(Path root) throws IOException {
        try (var paths = Files.walk(root)) { for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
