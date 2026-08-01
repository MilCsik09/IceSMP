package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.classspec.domain.MigrationState;
import hu.taliann.icesmp.classspec.domain.ProfileDiagnostics;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;
import hu.taliann.icesmp.classspec.domain.SealCause;
import hu.taliann.icesmp.classspec.domain.SealReason;
import hu.taliann.icesmp.classspec.domain.SoulbondState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Dependency-free regressions for Profile v2 application mutation policy. */
public final class ClassSpecApplicationRegressionSuite {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    private ClassSpecApplicationRegressionSuite() {
    }

    public static void main(final String[] args) {
        canonicalDarkSpecializationsAreGated();
        allDarkSelectionsRequireSatisfiedGates();
        allDarkSpecializationsSealOnMissingGate();
        activeSealPreservesProgressAndClearsActivation();
        reconcileWalksEveryMissingGateWithoutUnsafeUnseal();
        exactGateIdentityControlsUnseal();
        administrativeAndPersistenceSealsNeverAutoUnseal();
        persistenceFailureKeepsOldProfileAndFailsClosed();
        runtimeFailureKeepsCommittedProfileButBlocksSession();
        concurrentMutationsAreSerializedPerPlayer();
        shutdownDrainsAcceptedMutationsAndRejectsLateWork();
        loginActivationGateBlocksGameplayReadsUntilRuntimeRebuild();
        mirrorOnlyMutationsDoNotRequestRuntimeCleanup();
        resetRetryIsIdempotentWithinTheSession();
        disabledFlagDoesNotTouchProfileState();
        reviewAndQuarantineRejectNormalMutations();
        mutationFailurePolicyRequiresRefundAndCleanup();
        runtimeFailureReceiptMarksTheProfileAsDurablyCommitted();
        System.out.println("Class/spec application regression suite passed.");
    }

    private static void mirrorOnlyMutationsDoNotRequestRuntimeCleanup() {
        check(!ClassSpecRuntimePort.requiresRuntimeReconciliation(
                        ClassSpecRuntimePort.MutationKind.CLASS_LEVEL_MIRROR),
                "class-level mirror would despawn companion/transient runtime");
        check(!ClassSpecRuntimePort.requiresRuntimeReconciliation(
                        ClassSpecRuntimePort.MutationKind.SOULFORGE_UPGRADE),
                "Soulforge rank commit would despawn companion/transient runtime");
        check(ClassSpecRuntimePort.requiresRuntimeReconciliation(
                        ClassSpecRuntimePort.MutationKind.EXPLICIT_SEAL),
                "seal stopped requesting fail-closed runtime cleanup");
        check(ClassSpecRuntimePort.requiresRuntimeReconciliation(
                        ClassSpecRuntimePort.MutationKind.RESPEC_RESET),
                "respec stopped requesting runtime cleanup");
    }

    private static void canonicalDarkSpecializationsAreGated() {
        check(DarkSpecializationPolicy.IDS.size() == 5, "DARK specialization count changed");
        check(DarkSpecializationPolicy.IDS.equals(Set.of(
                "necromancer", "plaguebringer", "unholy", "bone_priest", "demonologist")),
                "canonical DARK specialization ids changed");
        check(!DarkSpecializationPolicy.isDark("shadow"), "non-DARK specialization classified as DARK");
        check(DarkSpecializationPolicy.isDark(" NECROMANCER "), "normalized DARK id rejected");
    }

    private static void allDarkSpecializationsSealOnMissingGate() {
        for (final String specializationId : DarkSpecializationPolicy.IDS) {
            final FakeStore store = new FakeStore(activeProfile(specializationId, simpleLoadout(specializationId)));
            final RecordingRuntime runtime = new RecordingRuntime(store);
            final DefaultClassSpecProfileGateway gateway = gateway(store, runtime);
            final ProfileMutationResult<ProfileDiagnostic> result = gateway.reconcile(PLAYER_ID,
                    reconcile(missingFaction())).toCompletableFuture().join();
            check(result.status() == ProfileMutationResult.Status.COMMITTED,
                    specializationId + " did not persist its gate seal");
            final ClassProfile durable = store.cached(PLAYER_ID).orElseThrow();
            check(durable.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.SEALED,
                    specializationId + " remained active after its faction gate failed");
            check(durable.activeSlot() == null, specializationId + " retained an invalid active slot");
        }
    }

    private static void allDarkSelectionsRequireSatisfiedGates() {
        for (final String specializationId : DarkSpecializationPolicy.IDS) {
            final ClassProfile classOnly = ClassProfile.builder()
                    .primaryClassId(ClassSpecCatalog.parentOf(specializationId))
                    .classLevel(25)
                    .build();
            final FakeStore store = new FakeStore(classOnly);
            final DefaultClassSpecProfileGateway gateway = gateway(store, new RecordingRuntime(store));

            final ProfileMutationResult<ProfileDiagnostic> rejected = gateway.select(PLAYER_ID,
                    new ClassSpecProfileGateway.SelectRequest(specializationId, LoadoutSlot.FIRST,
                            missingFaction())).toCompletableFuture().join();
            check(rejected.status() == ProfileMutationResult.Status.REJECTED,
                    specializationId + " selection bypassed a missing DARK gate");
            check(store.saveAttempts == 0, "rejected DARK selection reached persistence");

            final ProfileMutationResult<ProfileDiagnostic> selected = gateway.select(PLAYER_ID,
                    new ClassSpecProfileGateway.SelectRequest(specializationId, LoadoutSlot.FIRST,
                            allSatisfied())).toCompletableFuture().join();
            check(selected.status() == ProfileMutationResult.Status.COMMITTED,
                    specializationId + " selection failed with every gate satisfied");
            check(gateway.activeSpecId(PLAYER_ID).orElseThrow().equals(specializationId),
                    specializationId + " did not become the active Profile v2 specialization");
        }
    }

    private static void activeSealPreservesProgressAndClearsActivation() {
        final UUID companionId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        final ClassLoadout rich = new ClassLoadout("necromancer", LoadoutStatus.ACTIVE, null,
                Map.of("doctrine", "bone"), new MasteryProgress(7, 1_234L),
                new SoulbondState(UUID.fromString("00000000-0000-0000-0000-000000000203"),
                        3, List.of("ward"), 4L, ""),
                Set.of("soul_bolt"), "soul_bolt", CapstoneStatus.COMPLETED,
                Map.of(companionId, new CompanionProfile(companionId, "necromancer.court",
                        "skeleton", "Őrszem", 9, 500L, "stalwart", "guard",
                        List.of("bone_armor"), 42L, Map.of("mood", "loyal"))),
                Map.of("soulforge.damage", "4"), "preserved migration note");
        final FakeStore store = new FakeStore(activeProfile("necromancer", rich));
        final RecordingRuntime runtime = new RecordingRuntime(store);

        final ProfileMutationResult<ProfileDiagnostic> result = gateway(store, runtime)
                .reconcile(PLAYER_ID, reconcile(missingSinner())).toCompletableFuture().join();

        check(result.committed(), "seal did not commit");
        final ClassProfile durable = store.cached(PLAYER_ID).orElseThrow();
        final ClassLoadout sealed = durable.loadout(LoadoutSlot.FIRST);
        check(sealed.status() == LoadoutStatus.SEALED, "loadout was not sealed");
        check(sealed.sealReason().cause() == SealCause.SINNER_MARK_MISSING, "wrong seal cause");
        check(durable.activeSlot() == null, "sealed active slot was retained");
        check(sealed.mastery().equals(rich.mastery()), "mastery was lost while sealing");
        check(sealed.soulbond().equals(rich.soulbond()), "Soulbond was lost while sealing");
        check(sealed.companionRoster().equals(rich.companionRoster()), "roster was lost while sealing");
        check(sealed.mechanicState().equals(rich.mechanicState()), "Soulforge state was lost while sealing");
        check(runtime.commits == 1, "runtime reconciliation did not run after commit");
        check(runtime.observedDurableBeforeEffects, "runtime effects ran before the durable profile was visible");
    }

    private static void reconcileWalksEveryMissingGateWithoutUnsafeUnseal() {
        final FakeStore store = new FakeStore(activeProfile("necromancer", simpleLoadout("necromancer")));
        final DefaultClassSpecProfileGateway gateway = gateway(store, new RecordingRuntime(store));

        gateway.reconcile(PLAYER_ID, reconcile(snapshot(false, false, false,
                "dark-faction", "sinner-mark", "necromancer-initiation")))
                .toCompletableFuture().join();
        check(sealCause(store) == SealCause.FACTION_MISSING, "faction gate was not first seal cause");

        gateway.reconcile(PLAYER_ID, reconcile(snapshot(true, false, false,
                "dark-faction", "sinner-mark", "necromancer-initiation")))
                .toCompletableFuture().join();
        check(sealCause(store) == SealCause.SINNER_MARK_MISSING,
                "restoring faction unsafely unsealed while sinner gate was missing");

        gateway.reconcile(PLAYER_ID, reconcile(snapshot(true, true, false,
                "dark-faction", "sinner-mark", "necromancer-initiation")))
                .toCompletableFuture().join();
        check(sealCause(store) == SealCause.QUEST_REQUIREMENT_MISSING,
                "restoring sinner unsafely unsealed while quest gate was missing");

        gateway.reconcile(PLAYER_ID, reconcile(allSatisfied())).toCompletableFuture().join();
        final ClassProfile durable = store.cached(PLAYER_ID).orElseThrow();
        check(durable.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.INACTIVE,
                "restoring every gate did not unseal the loadout");
        check(durable.activeSlot() == null, "unseal automatically reactivated the loadout");
    }

    private static void exactGateIdentityControlsUnseal() {
        final SealReason seal = new SealReason(SealCause.FACTION_MISSING, "dark-faction", "left faction");
        final FakeStore store = new FakeStore(sealedProfile("necromancer", seal));
        final DefaultClassSpecProfileGateway gateway = gateway(store, new RecordingRuntime(store));

        final GateSnapshot renamedGate = snapshot(true, true, true,
                "renamed-dark-faction", "sinner-mark", "necromancer-initiation");
        final ProfileMutationResult<ProfileDiagnostic> wrong = gateway.reconcile(PLAYER_ID,
                reconcile(renamedGate)).toCompletableFuture().join();
        check(wrong.status() == ProfileMutationResult.Status.NO_CHANGE,
                "a different faction gate id cleared the seal");
        check(store.cached(PLAYER_ID).orElseThrow().loadout(LoadoutSlot.FIRST).status()
                == LoadoutStatus.SEALED, "wrong gate identity unsealed the loadout");

        final ProfileMutationResult<ProfileDiagnostic> right = gateway.reconcile(PLAYER_ID,
                reconcile(allSatisfied())).toCompletableFuture().join();
        check(right.status() == ProfileMutationResult.Status.COMMITTED,
                "the matching restored gate did not unseal");
    }

    private static void administrativeAndPersistenceSealsNeverAutoUnseal() {
        for (final SealCause cause : List.of(SealCause.ADMINISTRATIVE,
                SealCause.PERSISTENCE_FAILURE, SealCause.RECOVERY_BLOCK)) {
            final FakeStore store = new FakeStore(sealedProfile("necromancer",
                    new SealReason(cause, "", "manual block")));
            final ProfileMutationResult<ProfileDiagnostic> result = gateway(store, new RecordingRuntime(store))
                    .reconcile(PLAYER_ID, reconcile(allSatisfied())).toCompletableFuture().join();
            check(result.status() == ProfileMutationResult.Status.NO_CHANGE,
                    cause + " seal was treated as gate-restorable");
            check(store.cached(PLAYER_ID).orElseThrow().loadout(LoadoutSlot.FIRST).status()
                    == LoadoutStatus.SEALED, cause + " seal was automatically cleared");
        }
    }

    private static void persistenceFailureKeepsOldProfileAndFailsClosed() {
        final ClassProfile original = activeProfile("necromancer", simpleLoadout("necromancer"));
        final FakeStore store = new FakeStore(original);
        store.failNext = true;
        final RecordingRuntime runtime = new RecordingRuntime(store);

        final ProfileMutationResult<ProfileDiagnostic> result = gateway(store, runtime)
                .reconcile(PLAYER_ID, reconcile(missingQuest())).toCompletableFuture().join();

        check(result.status() == ProfileMutationResult.Status.PERSISTENCE_FAILED,
                "persistence failure was reported as success");
        check(result.sessionBlocked(), "persistence failure did not block the session");
        check(store.cached(PLAYER_ID).orElseThrow().equals(original),
                "failed candidate replaced the old durable profile");
        check(runtime.commits == 0, "runtime activated an uncommitted candidate");
        check(runtime.failClosed == 1, "runtime was not cleaned after persistence failure");
    }

    private static void resetRetryIsIdempotentWithinTheSession() {
        final FakeStore store = new FakeStore(activeProfile("necromancer", simpleLoadout("necromancer")));
        final RecordingRuntime runtime = new RecordingRuntime(store);
        final DefaultClassSpecProfileGateway gateway = gateway(store, runtime);
        final ClassSpecProfileGateway.ResetRequest request = new ClassSpecProfileGateway.ResetRequest(
                ClassSpecProfileGateway.ResetMode.LOADOUT_RESPEC, Optional.of(LoadoutSlot.FIRST),
                "respec-operation-42");

        final ProfileMutationResult<ProfileDiagnostic> first = gateway.reset(PLAYER_ID, request)
                .toCompletableFuture().join();
        final ProfileMutationResult<ProfileDiagnostic> retry = gateway.reset(PLAYER_ID, request)
                .toCompletableFuture().join();

        check(first.status() == ProfileMutationResult.Status.COMMITTED, "initial reset did not commit");
        check(retry.status() == ProfileMutationResult.Status.COMMITTED,
                "idempotent retry did not return the original receipt");
        check(store.saveAttempts == 1, "retry performed a second persistence write");
        check(runtime.commits == 1, "retry applied runtime cleanup twice");
    }

    private static void runtimeFailureKeepsCommittedProfileButBlocksSession() {
        final FakeStore store = new FakeStore(activeProfile("necromancer", simpleLoadout("necromancer")));
        final RecordingRuntime runtime = new RecordingRuntime(store);
        runtime.failCommit = true;

        final ProfileMutationResult<ProfileDiagnostic> result = gateway(store, runtime)
                .reconcile(PLAYER_ID, reconcile(missingFaction())).toCompletableFuture().join();

        check(result.status() == ProfileMutationResult.Status.RUNTIME_EFFECT_FAILED,
                "runtime failure was reported as a clean commit");
        check(result.sessionBlocked(), "runtime failure did not block the session");
        check(store.cached(PLAYER_ID).orElseThrow().revision() == 1L,
                "runtime failure rolled back an already durable candidate");
        check(store.cached(PLAYER_ID).orElseThrow().loadout(LoadoutSlot.FIRST).status()
                == LoadoutStatus.SEALED, "runtime failure lost the committed seal");
        check(runtime.failClosed == 1, "runtime failure did not request safe cleanup");
    }

    private static void concurrentMutationsAreSerializedPerPlayer() {
        final FakeStore store = new FakeStore(activeProfile("necromancer", simpleLoadout("necromancer")));
        store.deferNext = true;
        final DefaultClassSpecProfileGateway gateway = gateway(store, new RecordingRuntime(store));

        final CompletionStage<ProfileMutationResult<ProfileDiagnostic>> first = gateway.reconcile(
                PLAYER_ID, reconcile(missingFaction()));
        final CompletionStage<ProfileMutationResult<ProfileDiagnostic>> second = gateway.reconcile(
                PLAYER_ID, reconcile(missingSinner()));
        check(store.saveAttempts == 1, "second mutation overtook the in-flight CAS save");

        store.completeDeferred();
        check(first.toCompletableFuture().join().committed(), "first serialized mutation failed");
        check(second.toCompletableFuture().join().committed(), "second serialized mutation failed");
        check(store.saveAttempts == 2, "serialized second mutation did not reach persistence");
        final ClassProfile durable = store.cached(PLAYER_ID).orElseThrow();
        check(durable.revision() == 2L, "serialized mutations did not each increment revision once");
        check(durable.loadout(LoadoutSlot.FIRST).sealReason().cause() == SealCause.SINNER_MARK_MISSING,
                "second mutation did not observe the first committed profile");
        check(store.sessionBlockReason(PLAYER_ID).isEmpty(), "serialized writes caused a stale conflict");
    }

    private static void shutdownDrainsAcceptedMutationsAndRejectsLateWork() {
        final FakeStore store = new FakeStore(activeProfile("necromancer", simpleLoadout("necromancer")));
        store.deferNext = true;
        final DefaultClassSpecProfileGateway gateway = gateway(store, new RecordingRuntime(store));

        final CompletionStage<ProfileMutationResult<ProfileDiagnostic>> accepted = gateway.reconcile(
                PLAYER_ID, reconcile(missingFaction()));
        final CompletionStage<Void> shutdown = gateway.prepareShutdown();
        check(!shutdown.toCompletableFuture().isDone(),
                "gateway shutdown returned before an accepted mutation drained");

        final CompletionStage<ProfileMutationResult<ProfileDiagnostic>> late = gateway.reconcile(
                PLAYER_ID, reconcile(missingSinner()));
        check(late.toCompletableFuture().isCompletedExceptionally(),
                "gateway accepted a mutation after shutdown admission closed");

        store.completeDeferred();
        check(accepted.toCompletableFuture().join().committed(),
                "accepted mutation did not finish during shutdown drain");
        shutdown.toCompletableFuture().join();
        check(store.saveAttempts == 1, "late mutation reached persistence after shutdown");
    }

    private static void loginActivationGateBlocksGameplayReadsUntilRuntimeRebuild() {
        final FakeStore store = new FakeStore(
                activeProfile("necromancer", simpleLoadout("necromancer")));
        final DefaultClassSpecProfileGateway gateway = gateway(store, new RecordingRuntime(store));

        check(gateway.isSessionReady(PLAYER_ID), "loaded READY profile was not initially usable");
        gateway.beginSessionActivation(PLAYER_ID);
        check(!gateway.isSessionReady(PLAYER_ID),
                "login exposed class/spec gameplay before gate/runtime activation finished");
        check(gateway.activeSpecId(PLAYER_ID).isEmpty(),
                "active specialization leaked through the login activation gate");
        check(gateway.diagnostic(PLAYER_ID).loaded()
                        && gateway.diagnostic(PLAYER_ID).sessionBlockReason().isPresent(),
                "activation gate hid the profile from admin diagnostics");

        final ProfileMutationResult<ProfileDiagnostic> reconcile = gateway.reconcile(
                PLAYER_ID, reconcile(allSatisfied())).toCompletableFuture().join();
        check(reconcile.status() == ProfileMutationResult.Status.NO_CHANGE,
                "activation gate incorrectly blocked the internal DARK gate check");
        gateway.completeSessionActivation(PLAYER_ID);
        check(gateway.isSessionReady(PLAYER_ID)
                        && gateway.activeSpecId(PLAYER_ID).orElseThrow().equals("necromancer"),
                "successful runtime rebuild did not open the gameplay session");

        gateway.beginSessionActivation(PLAYER_ID);
        gateway.clearSession(PLAYER_ID);
        check(!gateway.isSessionReady(PLAYER_ID),
                "old-session receipt cleanup opened a newer pending login generation");
        gateway.cancelSessionActivation(PLAYER_ID);
        check(gateway.isSessionReady(PLAYER_ID),
                "ended session generation retained a stale activation gate");
    }

    private static void disabledFlagDoesNotTouchProfileState() {
        final ClassProfile original = activeProfile("necromancer", simpleLoadout("necromancer"));
        final FakeStore store = new FakeStore(original);
        final RecordingRuntime runtime = new RecordingRuntime(store);
        final DefaultClassSpecProfileGateway gateway = new DefaultClassSpecProfileGateway(
                () -> false, store, runtime);

        final ProfileMutationResult<ProfileDiagnostic> result = gateway.reconcile(
                PLAYER_ID, reconcile(missingFaction())).toCompletableFuture().join();
        check(result.status() == ProfileMutationResult.Status.REJECTED,
                "disabled Profile v2 accepted a mutation");
        check(store.saveAttempts == 0, "disabled Profile v2 wrote player data");
        check(store.cached(PLAYER_ID).orElseThrow().equals(original),
                "disabled Profile v2 altered legacy-compatible state");
        check(runtime.commits == 0 && runtime.failClosed == 0,
                "disabled Profile v2 activated partial runtime behavior");
    }

    private static void reviewAndQuarantineRejectNormalMutations() {
        final ClassProfile review = ClassProfile.builder()
                .status(ProfileStatus.MIGRATION_REVIEW)
                .migrationState(new MigrationState("", List.of("ambiguous legacy spec"),
                        Map.of("legacy.spec", "unknown")))
                .build();
        final FakeStore reviewStore = new FakeStore(review);
        final ProfileMutationResult<ProfileDiagnostic> reviewResult = gateway(reviewStore,
                new RecordingRuntime(reviewStore)).reset(PLAYER_ID,
                new ClassSpecProfileGateway.ResetRequest(ClassSpecProfileGateway.ResetMode.ADMIN_CLASS,
                        Optional.empty(), "admin-reset-review"))
                .toCompletableFuture().join();
        check(reviewResult.status() == ProfileMutationResult.Status.REJECTED,
                "migration-review profile accepted normal admin reset");
        check(reviewStore.saveAttempts == 0, "migration-review evidence reached persistence mutation");

        final ClassProfile quarantine = ClassProfile.builder()
                .status(ProfileStatus.CORRUPT_QUARANTINE)
                .diagnostics(new ProfileDiagnostics("checksum mismatch", ""))
                .build();
        final FakeStore quarantineStore = new FakeStore(quarantine);
        final ProfileMutationResult<ProfileDiagnostic> quarantineResult = gateway(quarantineStore,
                new RecordingRuntime(quarantineStore)).reset(PLAYER_ID,
                new ClassSpecProfileGateway.ResetRequest(ClassSpecProfileGateway.ResetMode.ADMIN_CLASS,
                        Optional.empty(), "admin-reset-quarantine"))
                .toCompletableFuture().join();
        check(quarantineResult.status() == ProfileMutationResult.Status.REJECTED,
                "quarantined profile accepted normal admin reset");
        check(quarantineStore.saveAttempts == 0, "quarantined evidence reached persistence mutation");
    }

    private static void mutationFailurePolicyRequiresRefundAndCleanup() {
        final ProfileMutationFailurePolicy policy = new ProfileMutationFailurePolicy();
        for (final ProfileMutationFailurePolicy.Failure failure : List.of(
                ProfileMutationFailurePolicy.Failure.REVISION_CONFLICT,
                ProfileMutationFailurePolicy.Failure.PERSISTENCE_WRITE)) {
            final ProfileMutationFailurePolicy.Action action = policy.actionFor(failure);
            check(action.blockSession(), failure + " did not block uncertain session state");
            check(action.cleanRuntime(), failure + " did not require runtime cleanup");
            check(action.refundEconomicCost(), failure + " did not require refund");
            check(action.preserveDurableAndLegacyState(), failure + " allowed evidence loss");
        }
        final ProfileMutationFailurePolicy.Action runtime = policy.actionFor(
                ProfileMutationFailurePolicy.Failure.RUNTIME_EFFECT);
        check(runtime.blockSession() && runtime.cleanRuntime(),
                "runtime-effect failure did not fail closed");
        check(!runtime.refundEconomicCost(),
                "already committed mutation would be refunded into a free operation");
    }

    private static void runtimeFailureReceiptMarksTheProfileAsDurablyCommitted() {
        final ProfileDiagnostic durable = ProfileDiagnostic.loading();
        final ProfileMutationResult<ProfileDiagnostic> runtimeFailure =
                ProfileMutationResult.failed(durable,
                        ProfileMutationResult.Status.RUNTIME_EFFECT_FAILED,
                        "injected scheduler failure");
        check(runtimeFailure.durableMutationApplied(),
                "runtime failure hid an already committed profile from economic failure handling");
        check(!runtimeFailure.committed(),
                "runtime failure was presented as a clean gameplay success");

        final ProfileMutationResult<ProfileDiagnostic> persistenceFailure =
                ProfileMutationResult.failed(durable,
                        ProfileMutationResult.Status.PERSISTENCE_FAILED,
                        "injected durable write failure");
        check(!persistenceFailure.durableMutationApplied(),
                "persistence failure would incorrectly retain the economic cost");
    }

    private static DefaultClassSpecProfileGateway gateway(final FakeStore store,
                                                          final RecordingRuntime runtime) {
        return new DefaultClassSpecProfileGateway(() -> true, store, runtime);
    }

    private static ClassSpecProfileGateway.ReconcileRequest reconcile(final GateSnapshot snapshot) {
        return new ClassSpecProfileGateway.ReconcileRequest(Map.of(LoadoutSlot.FIRST, snapshot));
    }

    private static GateSnapshot missingFaction() {
        return snapshot(false, true, true, "dark-faction", "sinner-mark", "necromancer-initiation");
    }

    private static GateSnapshot missingSinner() {
        return snapshot(true, false, true, "dark-faction", "sinner-mark", "necromancer-initiation");
    }

    private static GateSnapshot missingQuest() {
        return snapshot(true, true, false, "dark-faction", "sinner-mark", "necromancer-initiation");
    }

    private static GateSnapshot allSatisfied() {
        return snapshot(true, true, true, "dark-faction", "sinner-mark", "necromancer-initiation");
    }

    private static GateSnapshot snapshot(final boolean faction,
                                         final boolean sinner,
                                         final boolean quest,
                                         final String factionId,
                                         final String sinnerId,
                                         final String questId) {
        return new GateSnapshot(GateState.ofRequirements(true, faction, true, sinner, true, quest),
                Map.of(GateState.Gate.FACTION, factionId,
                        GateState.Gate.SINNER, sinnerId,
                        GateState.Gate.QUEST, questId));
    }

    private static ClassProfile activeProfile(final String specializationId,
                                              final ClassLoadout loadout) {
        return ClassProfile.builder()
                .primaryClassId(ClassSpecCatalog.parentOf(specializationId))
                .classLevel(25)
                .loadout(LoadoutSlot.FIRST, loadout)
                .activeSlot(LoadoutSlot.FIRST)
                .build();
    }

    private static ClassProfile sealedProfile(final String specializationId, final SealReason reason) {
        return ClassProfile.builder()
                .primaryClassId(ClassSpecCatalog.parentOf(specializationId))
                .classLevel(25)
                .loadout(LoadoutSlot.FIRST,
                        simpleLoadout(specializationId).withStatus(LoadoutStatus.SEALED, reason))
                .build();
    }

    private static ClassLoadout simpleLoadout(final String specializationId) {
        return new ClassLoadout(specializationId, LoadoutStatus.ACTIVE, null, Map.of(),
                MasteryProgress.empty(), null, Set.of(), "", CapstoneStatus.LOCKED,
                Map.of(), Map.of(), "");
    }

    private static SealCause sealCause(final FakeStore store) {
        return store.cached(PLAYER_ID).orElseThrow().loadout(LoadoutSlot.FIRST).sealReason().cause();
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeStore implements ClassProfileMutationStore {
        private final Map<UUID, ClassProfile> profiles = new LinkedHashMap<>();
        private final Map<UUID, String> blocks = new LinkedHashMap<>();
        private boolean failNext;
        private boolean deferNext;
        private int saveAttempts;
        private UUID deferredPlayer;
        private long deferredExpected;
        private ClassProfile deferredCandidate;
        private CompletableFuture<SaveResult> deferredResult;

        private FakeStore(final ClassProfile initial) {
            profiles.put(PLAYER_ID, initial);
        }

        @Override
        public Optional<ClassProfile> cached(final UUID playerId) {
            return Optional.ofNullable(profiles.get(playerId));
        }

        @Override
        public Optional<String> sessionBlockReason(final UUID playerId) {
            return Optional.ofNullable(blocks.get(playerId));
        }

        @Override
        public CompletionStage<SaveResult> save(final UUID playerId,
                                                final long expectedRevision,
                                                final ClassProfile candidate) {
            saveAttempts++;
            final ClassProfile current = profiles.get(playerId);
            if (failNext) {
                failNext = false;
                return CompletableFuture.completedFuture(SaveResult.failed(current, "simulated write failure"));
            }
            if (deferNext) {
                deferNext = false;
                deferredPlayer = playerId;
                deferredExpected = expectedRevision;
                deferredCandidate = candidate;
                deferredResult = new CompletableFuture<>();
                return deferredResult;
            }
            if (current == null || current.revision() != expectedRevision) {
                return CompletableFuture.completedFuture(SaveResult.conflict(current,
                        current == null ? -1L : current.revision()));
            }
            profiles.put(playerId, candidate);
            return CompletableFuture.completedFuture(SaveResult.committed(candidate));
        }

        private void completeDeferred() {
            check(deferredResult != null, "no deferred save to complete");
            final ClassProfile current = profiles.get(deferredPlayer);
            if (current == null || current.revision() != deferredExpected) {
                deferredResult.complete(SaveResult.conflict(current,
                        current == null ? -1L : current.revision()));
            } else {
                profiles.put(deferredPlayer, deferredCandidate);
                deferredResult.complete(SaveResult.committed(deferredCandidate));
            }
            deferredResult = null;
            deferredPlayer = null;
            deferredCandidate = null;
        }

        @Override
        public void blockSession(final UUID playerId, final String reason) {
            blocks.put(playerId, reason);
        }
    }

    private static final class RecordingRuntime implements ClassSpecRuntimePort {
        private final FakeStore store;
        private int commits;
        private int failClosed;
        private boolean failCommit;
        private boolean observedDurableBeforeEffects = true;

        private RecordingRuntime(final FakeStore store) {
            this.store = store;
        }

        @Override
        public CompletionStage<Void> profileCommitted(final UUID playerId,
                                                      final ClassProfile previous,
                                                      final ClassProfile durable,
                                                      final MutationKind kind) {
            commits++;
            observedDurableBeforeEffects &= store.cached(playerId).orElse(null) == durable;
            if (failCommit) {
                return CompletableFuture.failedFuture(new IllegalStateException("simulated runtime failure"));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> failClosed(final UUID playerId, final String reason) {
            failClosed++;
            return CompletableFuture.completedFuture(null);
        }
    }
}
