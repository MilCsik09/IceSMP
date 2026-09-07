package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.factions.FactionMembershipAdjustmentRuntime;
import hu.taliann.icesmp.factions.FactionMembershipTransitionGate;
import hu.taliann.icesmp.playerprofile.application.*;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionStore.*;
import hu.taliann.icesmp.playerprofile.domain.*;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;
import hu.taliann.icesmp.playerprofile.domain.section.OperationSection;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real profile transaction/WAL tests; no synthetic Bukkit events or alternate membership store. */
public final class WeaverFactionAdjustmentRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000964");
    private static int assertions;

    public static void main(String[] args) throws Exception {
        admissionAndHistory();
        revisionAndConcurrentWriters();
        outboxRetentionAndDrift();
        membershipWhisperAtomicity();
        nativeContinuationAndAdmission();
        nativeRecoveryBoundaries();
        transitionLeaseBounds();
        walBoundary(false);
        walBoundary(true);
        System.out.println("Faction adjustment passed: " + assertions + " assertions; real profile WAL, final authority admission, exact revision/ABA, compensating history, concurrent CAS and observed restart recovery.");
    }

    private static void admissionAndHistory() throws Exception {
        final Path root = Files.createTempDirectory("weaver-faction-adjustment-");
        try (final Harness h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            final AtomicBoolean session = new AtomicBoolean(true);
            final AtomicLong clock = new AtomicLong(1);
            final var token = new WeaverAuthorityToken(HiddenDevAuthority.PRIMARY_DEVELOPER, UUID.randomUUID(), 100, session::get, clock::get);
            final var denied = h.request(FactionType.RED);
            final var initial = h.profile(); final var disk = bytes(root);
            final var pending = h.factions.adjustMembership(PLAYER, denied, token::requireValid);
            check(!pending.toCompletableFuture().isDone(), "commit did not cross the repository queue");
            session.set(false);
            check(h.failure(pending) instanceof SecurityException, "logout between plan and WAL was admitted");
            check(h.profile().profileRevision() == initial.profileRevision()
                    && h.profile().faction().revision() == initial.faction().revision()
                    && h.profile().faction().value().equals(initial.faction().value())
                    && h.profile().operations().value().equals(initial.operations().value())
                    && bytes(root).equals(disk), "denied adjustment wrote history, receipt, revision or WAL");
            session.set(true); clock.set(100);
            check(h.failure(h.factions.adjustMembership(PLAYER, denied, token::requireValid)) instanceof SecurityException, "expired token admitted");
            check(bytes(root).equals(disk), "expired admission wrote profile data");
            clock.set(1);
            final var red = h.finish(h.factions.adjustMembership(PLAYER, denied, token::requireValid));
            check(!red.replayed() && red.after().state().membership().orElseThrow() == FactionType.RED, "canonical assignment failed");
            check(red.after().sectionRevision() == initial.faction().revision() + 1, "section did not advance exactly once");
            check(h.profile().operations().value().operations().get(denied.receiptId()).fingerprint().equals(denied.fingerprint()), "canonical operation receipt missing");
            check(h.factions.pendingAdjustmentEffects(PLAYER).equals(List.of(denied)), "adjustment outbox was not atomic with membership");
            check(!h.factions.adjustmentEffectsCompleted(PLAYER, denied), "new profile commit fabricated domain cleanup");
            token.revoke(); final var acceptedDisk = bytes(root);
            check(h.finish(h.factions.adjustMembership(PLAYER, denied, token::requireValid)).replayed(), "accepted receipt replay reran admission");
            check(bytes(root).equals(acceptedDisk), "accepted replay rewrote history");
            final var collision = new MembershipAdjustment(denied.operationId(), denied.expectedRevision(), denied.expectedMembership(), Optional.of(FactionType.BLUE), denied.occurredAt());
            check(h.failure(h.factions.adjustMembership(PLAYER, collision)) instanceof AdjustmentRejected, "operation identity collision accepted");
            check(bytes(root).equals(acceptedDisk), "rejected collision mutated state");
            final var blocked = h.request(FactionType.BLUE);
            check(h.failure(h.factions.adjustMembership(PLAYER, blocked)) instanceof AdjustmentRejected r && r.code().equals("FACTION_EFFECTS_PENDING"), "pending cleanup allowed overlapping adjustment");
            check(h.finish(h.factions.completeAdjustmentEffects(PLAYER, denied)), "domain effect acknowledgement failed");
            check(!h.finish(h.factions.completeAdjustmentEffects(PLAYER, denied)), "duplicate completion wrote another acknowledgement");
            check(h.factions.pendingAdjustmentEffects(PLAYER).isEmpty() && h.factions.adjustmentEffectsCompleted(PLAYER, denied), "completed effects remained pending");
            final var dark = h.request(FactionType.DARK); final var beforeDark = bytes(root);
            check(h.failure(h.factions.adjustMembership(PLAYER, dark)) instanceof AdjustmentRejected r && r.code().equals("DARK_OATH_REQUIRED"), "DARK bypassed exile/oath");
            check(bytes(root).equals(beforeDark), "rejected oath mutated state");
            final var blue = h.request(FactionType.BLUE);
            h.finish(h.factions.adjustMembership(PLAYER, blue));
            h.finish(h.factions.completeAdjustmentEffects(PLAYER, blue));
            final var compensate = h.request(FactionType.RED);
            h.finish(h.factions.adjustMembership(PLAYER, compensate));
            h.finish(h.factions.completeAdjustmentEffects(PLAYER, compensate));
            check(h.factions.readCached(PLAYER).history().equals(List.of(FactionType.RED, FactionType.BLUE, FactionType.RED)), "compensating adjustment erased logical history");
            check(h.profile().operations().value().operations().containsKey(blue.receiptId()), "compensation removed original receipt");
            check(h.factions.observeAdjustment(PLAYER, blue) == AdjustmentObservation.CONFLICT, "compensated state still observed as original current state");
            final var remove = h.request(null);
            final var beforeRemove = h.factions.readCached(PLAYER);
            h.finish(h.factions.adjustMembership(PLAYER, remove));
            check(h.factions.readCached(PLAYER).membership().isEmpty() && h.factions.readCached(PLAYER).history().equals(beforeRemove.history()), "removal fabricated or erased history");
            check(h.factions.readCached(PLAYER).lastChosen().equals(beforeRemove.lastChosen()) && h.factions.readCached(PLAYER).everChosen(), "removal reset historical membership identity");
            h.repository.invalidate(PLAYER); h.finish(h.repository.loadSnapshot(PLAYER));
            check(h.factions.observeAdjustment(PLAYER, remove) == AdjustmentObservation.APPLIED, "restart could not observe canonical removal");
            check(h.factions.pendingAdjustmentEffects(PLAYER).equals(List.of(remove)), "restart lost pending domain effects");
            h.finish(h.factions.completeAdjustmentEffects(PLAYER, remove));
            final var sins = new PlayerProfileSinStore();
            h.finish(sins.add(PLAYER, 4, 4)); h.finish(sins.sealDarkPact(PLAYER));
            final var factionBeforeDark = h.profile().faction().value();
            final var allowedDark = h.request(FactionType.DARK); h.finish(h.factions.adjustMembership(PLAYER, allowedDark));
            final var factionAfterDark = h.profile().faction().value();
            check(factionAfterDark.membershipId().equals("DARK"), "valid DARK adjustment refused");
            check(factionAfterDark.extensions().equals(factionBeforeDark.extensions()) && factionAfterDark.cooldowns().equals(factionBeforeDark.cooldowns())
                    && factionAfterDark.reputation().equals(factionBeforeDark.reputation()), "adjustment altered independent crime/reputation/switch axes");
        } finally { delete(root); }
    }

    private static void revisionAndConcurrentWriters() throws Exception {
        final Path root = Files.createTempDirectory("weaver-faction-cas-");
        try (final Harness h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.finish(h.factions.assign(PLAYER, FactionType.RED));
            final var stale = h.request(FactionType.BLUE);
            h.finish(h.factions.assign(PLAYER, FactionType.BLUE)); h.finish(h.factions.assign(PLAYER, FactionType.RED));
            final var disk = bytes(root);
            check(h.factions.observeAdjustment(PLAYER, stale) == AdjustmentObservation.CONFLICT, "membership ABA escaped revision check");
            check(h.failure(h.factions.adjustMembership(PLAYER, stale)) instanceof AdjustmentRejected, "stale adjustment overwrote external history");
            check(bytes(root).equals(disk), "stale adjustment wrote state");
            final var changedAxis = h.request(FactionType.BLUE);
            h.finish(h.authority.putExtension(PLAYER, ProfileSectionId.FACTION, FactionSection.class, "fixture.axis", 1L));
            check(h.failure(h.factions.adjustMembership(PLAYER, changedAxis)) instanceof AdjustmentRejected, "independent faction revision drift was ignored");
            final var first = h.request(FactionType.BLUE); final var second = h.request(FactionType.NEUTRAL);
            final var a = h.factions.adjustMembership(PLAYER, first); final var b = h.factions.adjustMembership(PLAYER, second);
            check(!a.toCompletableFuture().isDone() && !b.toCompletableFuture().isDone(), "fixture did not queue competing final commits");
            check(h.finish(a).after().state().membership().orElseThrow() == FactionType.BLUE, "first conditional transaction failed");
            h.failure(b);
            check(h.factions.observeAdjustment(PLAYER, first) == AdjustmentObservation.APPLIED
                    && h.factions.observeAdjustment(PLAYER, second) == AdjustmentObservation.CONFLICT, "concurrent loser overwrote winner");
            check(!h.profile().operations().value().operations().containsKey(second.receiptId()), "loser received a receipt");
            h.finish(h.factions.completeAdjustmentEffects(PLAYER, first));
            final var denied = h.request(FactionType.RED); final var before = bytes(root);
            check(h.failure(h.factions.adjustMembership(PLAYER, denied, () -> { throw new LinkageError("fixture unavailable"); })) instanceof LinkageError,
                    "failing admission did not fail closed");
            check(bytes(root).equals(before), "broken admission wrote WAL");
        } finally { delete(root); }
    }

    private static void walBoundary(boolean afterManifest) throws Exception {
        final Path root = Files.createTempDirectory("weaver-faction-wal-");
        final AtomicBoolean armed = new AtomicBoolean();
        final var injection = new YamlPlayerProfileRepository.FaultInjector() {
            @Override public void afterSectionsMovedBeforeManifest(UUID id, Set<ProfileSectionId> changed) throws IOException {
                if (!afterManifest && armed.getAndSet(false)) throw new IOException("fixture before manifest");
            }
            @Override public void afterManifestMovedBeforeCleanup(UUID id, Set<ProfileSectionId> changed) throws IOException {
                if (afterManifest && armed.getAndSet(false)) throw new IOException("fixture after manifest");
            }
        };
        final MembershipAdjustment request;
        try (final Harness h = new Harness(root, injection)) {
            request = h.request(FactionType.RED); armed.set(true);
            h.failure(h.factions.adjustMembership(PLAYER, request));
        }
        try (final Harness recovered = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            check(recovered.factions.observeAdjustment(PLAYER, request) == (afterManifest ? AdjustmentObservation.APPLIED : AdjustmentObservation.BEFORE), "WAL observed state mismatch");
            check(recovered.factions.readCached(PLAYER).membership().equals(afterManifest ? Optional.of(FactionType.RED) : Optional.empty()), "WAL recovered mixed membership");
            check(recovered.profile().operations().value().operations().containsKey(request.receiptId()) == afterManifest, "WAL recovered a ghost or lost receipt");
            check(recovered.factions.pendingAdjustmentEffects(PLAYER).equals(afterManifest ? List.of(request) : List.of()), "WAL lost or invented domain effects");
            final var disk = bytes(root);
            if (afterManifest) check(recovered.finish(recovered.factions.adjustMembership(PLAYER, request, () -> { throw new AssertionError("replay called admission"); })).replayed(), "accepted WAL replay was not idempotent");
            check(bytes(root).equals(disk), "observation/replay changed durable state");
        } finally { delete(root); }
    }

    private static void outboxRetentionAndDrift() throws Exception {
        final Path root = Files.createTempDirectory("weaver-faction-outbox-");
        try (final Harness h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            final var request = h.request(FactionType.RED);
            h.finish(h.factions.adjustMembership(PLAYER, request));
            h.finish(h.authority.mutateSection(PLAYER, ProfileSectionId.OPERATIONS, OperationSection.class, current -> {
                final Map<String, PlayerProfileOperation> ledger = new LinkedHashMap<>(current.operations());
                final Instant now = Instant.now();
                for (int i = 0; i < 511; i++) {
                    final String id = "fixture-pending-" + i;
                    ledger.put(id, new PlayerProfileOperation(id, "fixture", PlayerProfileOperation.Status.PREPARED, id, now, now, Map.of()));
                }
                return new OperationSection(ledger, current.extensions());
            }));
            final var operations = new PlayerProfileOperationStore(); final var before = bytes(root);
            check(h.failure(operations.prepare(PLAYER, "fixture-513", "fixture", "fixture-513", Map.of()))
                    instanceof hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionManager.LedgerSaturated,
                    "operation store evicted a committed pending domain outbox");
            check(bytes(root).equals(before) && h.factions.pendingAdjustmentEffects(PLAYER).equals(List.of(request)), "saturation lost pending evidence");
            h.finish(h.factions.completeAdjustmentEffects(PLAYER, request));
            h.finish(operations.prepare(PLAYER, "fixture-513", "fixture", "fixture-513", Map.of()));
            check(h.profile().operations().value().operations().size() == 512
                    && !h.profile().operations().value().operations().containsKey(request.receiptId()), "completed outbox did not release bounded retention slot");
            check(h.factions.observeAdjustment(PLAYER, request) == AdjustmentObservation.CONFLICT, "evicted identity was guessed as applied");
            final var badState = new PlayerProfileOperation("fixture-unknown", "fixture", PlayerProfileOperation.Status.COMMITTED, "unknown", Instant.EPOCH, Instant.EPOCH, Map.of("effects-state", "future-state"));
            check(badState.requiresReconciliation(), "unknown effect state was evictable");
        } finally { delete(root); }
        final Path driftRoot = Files.createTempDirectory("weaver-faction-outbox-drift-");
        try (final Harness h = new Harness(driftRoot, YamlPlayerProfileRepository.FaultInjector.none())) {
            final var request = h.request(FactionType.RED); h.finish(h.factions.adjustMembership(PLAYER, request));
            h.finish(h.factions.assign(PLAYER, FactionType.BLUE));
            final var before = bytes(driftRoot);
            check(h.failure(h.factions.completeAdjustmentEffects(PLAYER, request)) instanceof AdjustmentRejected, "cleanup acknowledgement ignored external membership drift");
            check(bytes(driftRoot).equals(before) && h.factions.pendingAdjustmentEffects(PLAYER).equals(List.of(request)), "drift discarded unfinished outbox");
        } finally { delete(driftRoot); }
    }

    private static void membershipWhisperAtomicity() throws Exception {
        for (int route = 0; route < 4; route++) {
            final Path root = Files.createTempDirectory("weaver-faction-whisper-");
            try (final Harness h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
                h.finish(h.factions.assign(PLAYER, FactionType.RED));
                final var whispers = new PlayerProfileWhisperStore();
                h.finish(whispers.makeWhisperer(PLAYER)); h.finish(whispers.advance(PLAYER));
                check(whispers.read(PLAYER).whisperer(), "fixture has no canonical whisper role");
                if (route == 0 || route == 2) {
                    final var sins = new PlayerProfileSinStore();
                    h.finish(sins.add(PLAYER, 4, 4)); h.finish(sins.sealDarkPact(PLAYER));
                }
                final var before = h.profile();
                final MembershipAdjustment adjustment = route < 2 ? h.request(route == 0 ? FactionType.DARK : null) : null;
                if (adjustment != null) h.finish(h.factions.adjustMembership(PLAYER, adjustment));
                else if (route == 2) check(h.finish(h.factions.joinDark(PLAYER, FactionType.RED, 1, 2)), "native DARK route failed");
                else h.finish(h.factions.remove(PLAYER));
                check(!whispers.read(PLAYER).whisperer() && whispers.read(PLAYER).stage() == PlayerProfileWhisperStore.Stage.CLEAN,
                        "membership committed before canonical whisper cleanup");
                check(h.profile().faction().revision() == before.faction().revision() + 1, "whisper cleanup needed a second faction revision");
                final var after = h.profile(); final var disk = bytes(root);
                h.finish(whispers.clear(PLAYER));
                check(h.profile().faction().revision() == after.faction().revision() && bytes(root).equals(disk), "native follow-up drifted acknowledged faction state");
                check(Objects.equals(h.profile().faction().value().extensions().get("sin.exiled"), before.faction().value().extensions().get("sin.exiled"))
                        && Objects.equals(h.profile().faction().value().extensions().get("sin.dark-pact"), before.faction().value().extensions().get("sin.dark-pact")), "whisper cleanup rewrote legal axes");
                if (adjustment != null) {
                    check(h.factions.observeAdjustment(PLAYER, adjustment) == AdjustmentObservation.APPLIED, "canonical cleanup invalidated operation observation");
                    h.finish(h.factions.completeAdjustmentEffects(PLAYER, adjustment));
                }
            } finally { delete(root); }
        }
    }

    private static void nativeContinuationAndAdmission() throws Exception {
        final Path root = Files.createTempDirectory("weaver-faction-native-continuation-");
        try (final Harness h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            final AtomicInteger cleanup = new AtomicInteger(); final AtomicBoolean failCleanup = new AtomicBoolean();
            final Object roles = new Object();
            final var runtime = new FactionMembershipAdjustmentRuntime(h.factions, claim -> { synchronized (roles) { claim.run(); } }, id -> {
                check(runtimeProfileApplied(h), "cleanup ran before durable membership");
                cleanup.incrementAndGet();
                if (failCleanup.getAndSet(false)) throw new IllegalStateException("fixture domain save failed");
            });
            final var request = h.request(FactionType.RED);
            final var first = runtime.adjust(PLAYER, request, () -> { });
            check(runtime.pending(PLAYER), "queued adjustment did not close role admission");
            check(h.failure(runtime.adjust(PLAYER, request, () -> { })) instanceof FactionMembershipAdjustmentRuntime.Rejected,
                    "duplicate mutation entered a running lease");
            check(h.failure(runtime.reconcile(PLAYER, request)) instanceof FactionMembershipAdjustmentRuntime.Rejected,
                    "duplicate rejection released or paused the original lease");
            h.finish(first);
            check(!runtime.pending(PLAYER) && cleanup.get() == 1 && h.factions.adjustmentEffectsCompleted(PLAYER, request), "completed native effects did not release admission");
            check(h.finish(runtime.adjust(PLAYER, request, () -> { throw new AssertionError("accepted receipt admission replayed"); })).replayed(), "accepted native receipt replay failed");
            check(cleanup.get() == 1, "accepted native receipt repeated cleanup");
            final var next = h.request(FactionType.BLUE); failCleanup.set(true);
            h.failure(runtime.adjust(PLAYER, next, () -> { }));
            check(runtime.pending(PLAYER) && h.factions.observeAdjustment(PLAYER, next) == AdjustmentObservation.APPLIED,
                    "failed domain write lost applied membership evidence");
            final long revision = h.profile().faction().revision();
            final var recovery = runtime.reconcile(PLAYER, next);
            check(!recovery.toCompletableFuture().isDone(), "recovery used cached evidence instead of serialized durable refresh");
            check(h.failure(runtime.reconcile(PLAYER, next)) instanceof FactionMembershipAdjustmentRuntime.Rejected,
                    "duplicate recovery entered the same unfinished effect");
            check(h.finish(recovery) == AdjustmentObservation.APPLIED, "native cleanup recovery failed");
            check(!runtime.pending(PLAYER) && h.profile().faction().revision() == revision && cleanup.get() == 3,
                    "native recovery replayed membership or skipped required cleanup flush");
            final var denied = h.request(FactionType.RED); final var before = bytes(root);
            check(h.failure(runtime.adjust(PLAYER, denied, () -> { throw new SecurityException("fixture revoked"); })) instanceof SecurityException,
                    "native admission bypassed final authority");
            check(h.finish(runtime.reconcile(PLAYER, denied)) == AdjustmentObservation.BEFORE && !runtime.pending(PLAYER),
                    "unapplied mutation recovery did not release its lease");
            check(bytes(root).equals(before) && cleanup.get() == 3, "BEFORE recovery mutated state");
            final var superseded = h.request(FactionType.RED);
            h.finish(h.authority.putExtension(PLAYER, ProfileSectionId.FACTION, FactionSection.class, "fixture.new-axis", 1L));
            h.failure(runtime.adjust(PLAYER, superseded, () -> { }));
            check(h.failure(runtime.reconcile(PLAYER, superseded)) instanceof FactionMembershipAdjustmentRuntime.Rejected
                    && !runtime.pending(PLAYER), "unapplied revision conflict permanently locked membership admission");
            final var drift = h.request(FactionType.RED); failCleanup.set(true); h.failure(runtime.adjust(PLAYER, drift, () -> { }));
            h.finish(h.factions.assign(PLAYER, FactionType.NEUTRAL));
            final int calls = cleanup.get(); final var driftDisk = bytes(root);
            check(h.failure(runtime.reconcile(PLAYER, drift)) instanceof FactionMembershipAdjustmentRuntime.Rejected,
                    "native recovery overwrote external membership drift");
            check(runtime.pending(PLAYER) && cleanup.get() == calls && bytes(root).equals(driftDisk), "conflicting native cleanup discarded evidence");
        } finally { delete(root); }
    }

    private static boolean runtimeProfileApplied(Harness h) {
        return !h.factions.pendingAdjustmentEffects(PLAYER).isEmpty();
    }

    private static void nativeRecoveryBoundaries() throws Exception {
        for (int boundary = 0; boundary < 3; boundary++) {
            final int point = boundary;
            final Path root = Files.createTempDirectory("weaver-faction-native-recovery-");
            final AtomicBoolean armed = new AtomicBoolean(); final AtomicInteger cleanup = new AtomicInteger();
            final var fault = new YamlPlayerProfileRepository.FaultInjector() {
                @Override public void afterSectionsMovedBeforeManifest(UUID id, Set<ProfileSectionId> changed) throws IOException {
                    if (point == 0 && armed.getAndSet(false)) throw new IOException("fixture before membership manifest");
                }
                @Override public void afterManifestMovedBeforeCleanup(UUID id, Set<ProfileSectionId> changed) throws IOException {
                    if ((point == 1 && changed.contains(ProfileSectionId.FACTION)
                            || point == 2 && changed.equals(Set.of(ProfileSectionId.OPERATIONS))) && armed.getAndSet(false)) {
                        throw new IOException("fixture accepted WAL acknowledgement loss");
                    }
                }
            };
            try (final Harness h = new Harness(root, fault)) {
                final var runtime = new FactionMembershipAdjustmentRuntime(h.factions, Runnable::run, id -> cleanup.incrementAndGet());
                final var request = h.request(FactionType.RED); armed.set(true);
                h.failure(runtime.adjust(PLAYER, request, () -> { }));
                check(runtime.pending(PLAYER), "ambiguous failure released native admission");
                final var recovered = h.finish(runtime.reconcile(PLAYER, request));
                check(recovered == (point == 0 ? AdjustmentObservation.BEFORE : AdjustmentObservation.APPLIED), "native WAL assessment mismatch");
                check(cleanup.get() == (point == 0 ? 0 : 1), "accepted effect acknowledgement loss repeated cleanup");
                check(h.profile().faction().revision() == (point == 0 ? 0 : 1), "native recovery repeated membership WAL");
                check(!runtime.pending(PLAYER), "settled native recovery retained admission lease");
            } finally { delete(root); }
        }
    }

    private static void transitionLeaseBounds() {
        final var gate = new FactionMembershipTransitionGate(); final UUID operation = UUID.randomUUID();
        final List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 128; i++) { final UUID id = new UUID(10, i); players.add(id); check(gate.claim(id, operation), "bounded lease rejected early"); }
        check(!gate.claim(UUID.randomUUID(), operation), "transition lease cap exceeded");
        final UUID player = players.getFirst();
        check(!gate.release(player, UUID.randomUUID()) && gate.pending(player), "foreign operation released a live lease");
        check(!gate.resume(player, operation), "running lease was resumed concurrently");
        gate.pause(player, operation);
        check(!gate.resume(player, UUID.randomUUID()) && gate.resume(player, operation), "paused lease was stolen or could not resume");
        check(gate.release(player, operation) && gate.claim(player, UUID.randomUUID()), "settled lease did not release capacity");
        check(!gate.release(player, operation), "old operation released replacement lease");
    }

    private static final class Harness implements AutoCloseable {
        final ManualExecutor io = new ManualExecutor();
        final YamlPlayerProfileRepository repository;
        final PlayerProfileAuthority authority;
        final PlayerProfileFactionStore factions = new PlayerProfileFactionStore();
        Harness(Path root, YamlPlayerProfileRepository.FaultInjector fault) {
            repository = new YamlPlayerProfileRepository(root, Clock.systemUTC(), io, fault);
            final var transactions = new YamlPlayerProfileTransactionManager(repository);
            authority = PlayerProfileAuthority.install(new PlayerProfileService(repository, transactions), repository, transactions);
            finish(repository.loadSnapshot(PLAYER));
        }
        PlayerProfileSnapshot profile() { return repository.cached(PLAYER).orElseThrow(); }
        MembershipAdjustment request(FactionType target) {
            final var view = factions.membershipView(PLAYER);
            return new MembershipAdjustment(UUID.randomUUID(), view.sectionRevision(), view.state().membership(), Optional.ofNullable(target),
                    Math.max(System.currentTimeMillis(), Math.max(view.state().joinedAt(), view.state().leftAt())));
        }
        <T> T finish(CompletionStage<T> stage) {
            final var future = stage.toCompletableFuture();
            for (int i = 0; !future.isDone() && i < 100; i++) io.one();
            check(future.isDone(), "bounded storage queue did not drain"); return future.join();
        }
        Throwable failure(CompletionStage<?> stage) {
            try { finish(stage); throw new AssertionError("expected rejection"); }
            catch (CompletionException failure) {
                Throwable root = failure;
                while (root instanceof CompletionException && root.getCause() != null) root = root.getCause();
                return root;
            }
        }
        @Override public void close() throws Exception {
            check(repository.shutdown(Duration.ofSeconds(2)).toCompletableFuture().get(5, TimeUnit.SECONDS).drained(), "repository did not drain");
            authority.uninstall();
        }
    }
    private static final class ManualExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>(); boolean closed;
        @Override public void execute(Runnable command) { if (closed) throw new RejectedExecutionException(); queue.add(command); }
        void one() { final Runnable next = queue.poll(); if (next == null) throw new AssertionError("missing queued task"); next.run(); }
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
