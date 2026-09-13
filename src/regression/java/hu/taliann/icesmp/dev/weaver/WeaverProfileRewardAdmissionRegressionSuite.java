package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.classspec.application.*;
import hu.taliann.icesmp.classspec.domain.*;
import hu.taliann.icesmp.classspec.persistence.*;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.playerprofile.domain.*;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Uses the production class/pet gateway, adapters, YAML CAS, WAL and restart recovery. */
public final class WeaverProfileRewardAdmissionRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000951");
    private static final UUID COMPANION = UUID.fromString("00000000-0000-0000-0000-000000000952");
    private static final RewardSource VICTIM = new RewardSource.Entity(UUID.randomUUID());
    private static final RewardSource CAUSE = new RewardSource.Entity(UUID.randomUUID());
    private static int assertions;

    public static void main(final String[] args) throws Exception {
        final Set<RewardSource> tainted = new HashSet<>();
        final AtomicBoolean playerTainted = new AtomicBoolean();
        final var policy = new InfluenceRewardEligibilityPolicy(new InfluenceRewardEligibilityPolicy.Lookup() {
            @Override public InfluenceRewardEligibilityPolicy.Evidence recipient(UUID player) {
                return playerTainted.get() ? InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED : InfluenceRewardEligibilityPolicy.Evidence.CLEAN;
            }
            @Override public InfluenceRewardEligibilityPolicy.Evidence source(RewardSource source) {
                return tainted.contains(source) ? InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED : InfluenceRewardEligibilityPolicy.Evidence.CLEAN;
            }
        });
        try (final var binding = GameplayRewardGate.install(policy)) {
            queuedAdmissionAndReplay(tainted, playerTainted);
            walRecoveryPreservesAcceptedReward(tainted);
            unsupportedPortRejects();
        }
        unboundRepositoryRejects();
        System.out.println("Profile reward admission passed: " + assertions + " assertions; real YAML CAS, queued source/recipient denial, no rejected receipts/revisions, restart replay and accepted WAL recovery.");
    }

    private static void queuedAdmissionAndReplay(Set<RewardSource> tainted, AtomicBoolean playerTainted) throws Exception {
        final Path root = Files.createTempDirectory("weaver-profile-reward-");
        try (final Harness h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.seed();
            final var initial = h.profile(); final var disk = bytes(root);
            final var denied = h.gateway.mutateClassExperience(PLAYER, classRequest("denied-cause", 11));
            check(!denied.toCompletableFuture().isDone(), "reward was not queued for actual storage");
            tainted.add(CAUSE);
            check(h.finish(denied).status() == ProfileMutationResult.Status.REJECTED, "late causal taint allowed class XP");
            check(h.profile().equals(initial) && bytes(root).equals(disk), "denied XP wrote profile, receipt, revision or WAL");
            check(h.gateway.isSessionReady(PLAYER), "expected reward denial poisoned the session");
            tainted.clear();
            final var first = h.gateway.mutateClassExperience(PLAYER, classRequest("accepted-class", 12));
            final var second = h.gateway.mutateClassExperience(PLAYER, classRequest("queued-class", 13));
            h.io.one(); check(first.toCompletableFuture().isDone() && !second.toCompletableFuture().isDone(), "gateway did not serialize rewards");
            playerTainted.set(true);
            check(h.finish(second).status() == ProfileMutationResult.Status.REJECTED, "queued recipient quarantine bypassed admission");
            check(h.profile().classExperience() == 12 && h.profile().operation("queued-class").isEmpty(), "rejected class reward changed durable state");
            playerTainted.set(false);
            final var pet = h.gateway.mutateCompanionProgress(PLAYER, petRequest("denied-pet"));
            tainted.add(VICTIM);
            final long beforePet = h.profile().revision();
            check(h.finish(pet).status() == ProfileMutationResult.Status.REJECTED, "victim taint allowed pet XP");
            check(h.profile().revision() == beforePet && h.profile().operation("denied-pet").isEmpty(), "denied pet XP revised profile");
            tainted.clear();
            check(h.finish(h.gateway.mutateCompanionProgress(PLAYER, petRequest("accepted-pet"))).committed(), "clean pet XP did not commit");
            check(h.profile().loadout(LoadoutSlot.FIRST).companionRoster().get(COMPANION).experience() == 2, "pet XP was not applied");
            final var wrongRecipient = new RewardContext(RewardChannel.CLASS_XP, UUID.randomUUID(), List.of(VICTIM));
            final var bad = new ClassSpecProfileGateway.ClassExperienceRequest(ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD,
                    1, 100, 20, 28, "wrong-recipient", Optional.of(wrongRecipient));
            check(h.finish(h.gateway.mutateClassExperience(PLAYER, bad)).status() == ProfileMutationResult.Status.REJECTED, "mismatched recipient accepted");
            final var wrongChannel = new ClassSpecProfileGateway.ClassExperienceRequest(ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD,
                    1, 100, 20, 28, "wrong-channel", Optional.of(context(RewardChannel.PET_XP)));
            check(h.finish(h.gateway.mutateClassExperience(PLAYER, wrongChannel)).status() == ProfileMutationResult.Status.REJECTED, "mismatched channel accepted");
            playerTainted.set(true);
            final var implicit = new ClassSpecProfileGateway.ClassExperienceRequest(ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD, 1, 100, 20, "implicit-recipient");
            check(h.finish(h.gateway.mutateClassExperience(PLAYER, implicit)).status() == ProfileMutationResult.Status.REJECTED, "compatibility ADD bypassed recipient quarantine");
        }
        // Existing canonical operation receipts carry the accepted outcome across restart.
        try (final Harness restarted = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            restarted.load(); final var before = restarted.profile(); final var disk = bytes(root);
            tainted.add(CAUSE);
            check(restarted.finish(restarted.gateway.mutateClassExperience(PLAYER, classRequest("accepted-class", 12))).status()
                    == ProfileMutationResult.Status.NO_CHANGE, "legitimate class receipt replay was revoked by later quarantine");
            check(restarted.finish(restarted.gateway.mutateCompanionProgress(PLAYER, petRequest("accepted-pet"))).status()
                    == ProfileMutationResult.Status.NO_CHANGE, "legitimate pet receipt replay was revoked");
            check(restarted.profile().equals(before) && bytes(root).equals(disk), "replay rewrote or duplicated an accepted reward");
            check(restarted.finish(restarted.gateway.mutateClassExperience(PLAYER, classRequest("accepted-class", 99))).status()
                    == ProfileMutationResult.Status.REJECTED, "reused receipt with another amount accepted");
        } finally { tainted.clear(); playerTainted.set(false); delete(root); }
    }

    private static void walRecoveryPreservesAcceptedReward(Set<RewardSource> tainted) throws Exception {
        final Path root = Files.createTempDirectory("weaver-reward-wal-");
        final AtomicBoolean fault = new AtomicBoolean();
        final var injection = new YamlPlayerProfileRepository.FaultInjector() {
            @Override public void afterSectionsMovedBeforeManifest(UUID id, Set<ProfileSectionId> sections) { }
            @Override public void afterManifestMovedBeforeCleanup(UUID id, Set<ProfileSectionId> sections) throws IOException {
                if (fault.getAndSet(false)) throw new IOException("injected accepted reward acknowledgement loss");
            }
        };
        try (final Harness h = new Harness(root, injection)) {
            h.seed(); fault.set(true);
            check(h.finish(h.gateway.mutateClassExperience(PLAYER, classRequest("wal-accepted", 21))).status()
                    == ProfileMutationResult.Status.PERSISTENCE_FAILED, "fault did not interrupt acknowledgement");
        }
        tainted.add(CAUSE);
        try (final Harness recovered = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            recovered.load();
            check(recovered.profile().classExperience() == 21, "accepted WAL did not recover its canonical reward");
            final long revision = recovered.profile().revision();
            check(recovered.finish(recovered.gateway.mutateClassExperience(PLAYER, classRequest("wal-accepted", 21))).status()
                    == ProfileMutationResult.Status.NO_CHANGE, "accepted WAL receipt could not replay under later quarantine");
            check(recovered.profile().revision() == revision && recovered.profile().classExperience() == 21, "WAL replay duplicated reward");
        } finally { tainted.clear(); delete(root); }
    }

    private static void unsupportedPortRejects() {
        final ClassSpecSectionMutationStore store = new ClassSpecSectionMutationStore() {
            public Optional<ClassSpecSection> cached(UUID id) { return Optional.of(ClassSpecSection.empty(0)); }
            public Optional<String> sessionBlockReason(UUID id) { return Optional.empty(); }
            public CompletionStage<SaveResult> save(UUID id,long revision,ClassSpecSection candidate) { throw new AssertionError("unsupported reward port fell back to unguarded save"); }
            public CompletionStage<ClassSpecSection> recover(UUID id,String evidence,String audit) { throw new UnsupportedOperationException(); }
            public void blockSession(UUID id,String reason) { }
        };
        check(store.saveReward(PLAYER, 0, ClassSpecSection.empty(1), context(RewardChannel.CLASS_XP)).toCompletableFuture().join().status()
                == ClassSpecSectionMutationStore.SaveResult.Status.REWARD_DENIED, "unsupported store admitted reward");
    }

    private static void unboundRepositoryRejects() throws Exception {
        final Path root = Files.createTempDirectory("weaver-reward-unbound-");
        try (final Harness h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.seed(); final var before = bytes(root);
            check(h.finish(h.gateway.mutateClassExperience(PLAYER, classRequest("unbound", 1))).status()
                    == ProfileMutationResult.Status.REJECTED, "unavailable policy allowed profile reward");
            check(bytes(root).equals(before), "unavailable policy wrote a reward");
        } finally { delete(root); }
    }

    private static RewardContext context(RewardChannel channel) { return new RewardContext(channel, PLAYER, List.of(VICTIM, CAUSE)); }
    private static ClassSpecProfileGateway.ClassExperienceRequest classRequest(String operation, int amount) {
        return new ClassSpecProfileGateway.ClassExperienceRequest(ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD,
                amount, 100, 20, 28, operation, Optional.of(context(RewardChannel.CLASS_XP)));
    }
    private static ClassSpecProfileGateway.CompanionProgressRequest petRequest(String operation) {
        return new ClassSpecProfileGateway.CompanionProgressRequest(LoadoutSlot.FIRST, COMPANION, 2, 100, 0, 30,
                operation, Optional.of(context(RewardChannel.PET_XP)));
    }
    private static final class Harness implements AutoCloseable {
        final ManualExecutor io = new ManualExecutor();
        final YamlPlayerProfileRepository repository;
        final DefaultClassSpecProfileGateway gateway;
        Harness(Path root, YamlPlayerProfileRepository.FaultInjector fault) {
            repository = new YamlPlayerProfileRepository(root, Clock.systemUTC(), io, fault);
            final var sessions = new ProfileSessionRegistry(); final UUID token = sessions.begin(PLAYER); sessions.markReady(PLAYER, token);
            gateway = new DefaultClassSpecProfileGateway(new ClassSpecSectionMutationStoreAdapter(
                    new PlayerProfileClassSpecSectionRepository(repository)), ClassSpecRuntimePort.noop(), sessions);
        }
        void load() { finish(repository.load(PLAYER)); }
        void seed() {
            load();
            final var companion = new CompanionProfile(COMPANION, "beast_master.stable", "WOLF", "Fang", 1, 0, "", "ACTIVE", List.of(), 0, Map.of());
            final var loadout = new ClassLoadout("beast_master", LoadoutStatus.ACTIVE, null, Map.of(), MasteryProgress.empty(), null,
                    Set.of(), "", CapstoneStatus.LOCKED, Map.of(COMPANION, companion), Map.of("companion.active_id", COMPANION.toString()), "");
            final var seed = ClassSpecSection.builder().revision(1).primaryClassId("archer").classLevel(25)
                    .activeSlot(LoadoutSlot.FIRST).loadout(LoadoutSlot.FIRST, loadout).build();
            finish(repository.saveSection(PLAYER, ProfileSectionId.CLASS_SPEC, 0, new ProfileSectionSnapshot<>(ProfileSectionId.CLASS_SPEC,
                    ProfileSectionId.CLASS_SPEC.currentSchema(), 1, Instant.now(), seed, SectionHealth.healthy())));
        }
        ClassSpecSection profile() { return repository.cached(PLAYER).orElseThrow().classSpec().value(); }
        <T> T finish(CompletionStage<T> stage) {
            final var future = stage.toCompletableFuture();
            for (int i = 0; !future.isDone() && i < 100; i++) io.one();
            check(future.isDone(), "bounded storage drain failed"); return future.join();
        }
        @Override public void close() throws Exception {
            check(repository.shutdown(Duration.ofSeconds(1)).toCompletableFuture().get(5, TimeUnit.SECONDS).drained(), "repository shutdown did not drain");
        }
    }
    private static final class ManualExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>(); boolean closed;
        @Override public void execute(Runnable command) { if (closed) throw new RejectedExecutionException(); queue.add(command); }
        void one() { final Runnable next = queue.poll(); if (next == null) throw new AssertionError("expected queued storage task"); next.run(); }
        @Override public void shutdown() { closed = true; }
        @Override public List<Runnable> shutdownNow() { closed = true; final var remaining = List.copyOf(queue); queue.clear(); return remaining; }
        @Override public boolean isShutdown() { return closed; }
        @Override public boolean isTerminated() { return closed && queue.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }
    private static Map<String, String> bytes(Path root) throws Exception {
        final Map<String, String> result = new TreeMap<>();
        try (final var files = Files.walk(root)) {
            for (final Path path : files.filter(Files::isRegularFile).toList()) result.put(root.relativize(path).toString(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        }
        return result;
    }
    private static void delete(Path root) throws IOException {
        try (final var paths = Files.walk(root)) { for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
