package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;
import hu.taliann.icesmp.classspec.domain.SealReason;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Default cache-first Profile v2 facade. It serializes mutations per player,
 * publishes candidates only after a successful CAS save, and asks a
 * scheduler-owning runtime adapter to apply side effects after that commit.
 */
public final class DefaultClassSpecProfileGateway implements ClassSpecProfileGateway {

    private static final int MAX_RESET_RECEIPTS_PER_PLAYER = 32;

    private final BooleanSupplier enabled;
    private final ClassProfileMutationStore store;
    private final ClassSpecRuntimePort runtime;
    private final ProfileMutationPolicy mutationPolicy;
    private final Map<UUID, CompletableFuture<Void>> mutationTails = new ConcurrentHashMap<>();
    private final Set<UUID> activationPending = ConcurrentHashMap.newKeySet();
    private final Object admissionLock = new Object();
    private boolean accepting = true;
    private final Map<UUID, Map<String, ProfileMutationResult<ProfileDiagnostic>>> resetReceipts =
            new ConcurrentHashMap<>();

    public DefaultClassSpecProfileGateway(final BooleanSupplier enabled,
                                          final ClassProfileMutationStore store,
                                          final ClassSpecRuntimePort runtime) {
        this(enabled, store, runtime, new ProfileMutationPolicy());
    }

    DefaultClassSpecProfileGateway(final BooleanSupplier enabled,
                                   final ClassProfileMutationStore store,
                                   final ClassSpecRuntimePort runtime,
                                   final ProfileMutationPolicy mutationPolicy) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.store = Objects.requireNonNull(store, "store");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.mutationPolicy = Objects.requireNonNull(mutationPolicy, "mutationPolicy");
    }

    @Override
    public boolean enabled() {
        return enabled.getAsBoolean();
    }

    @Override
    public boolean isSessionReady(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!enabled()) {
            return false;
        }
        final Optional<ClassProfile> cached = store.cached(playerId);
        return cached.isPresent() && cached.orElseThrow().isGameplayUsable()
                && store.sessionBlockReason(playerId).isEmpty()
                && !activationPending.contains(playerId);
    }

    @Override
    public void beginSessionActivation(final UUID playerId) {
        activationPending.add(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public void completeSessionActivation(final UUID playerId) {
        activationPending.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public void cancelSessionActivation(final UUID playerId) {
        activationPending.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public Optional<String> activeSpecId(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!isSessionReady(playerId)) {
            return Optional.empty();
        }
        final ClassProfile profile = store.cached(playerId).orElseThrow();
        if (profile.activeSlot() == null) {
            return Optional.empty();
        }
        final ClassLoadout loadout = profile.loadout(profile.activeSlot());
        return loadout.status() == LoadoutStatus.ACTIVE
                ? Optional.of(loadout.specializationId()) : Optional.empty();
    }

    @Override
    public Optional<String> activeMechanic(final UUID playerId, final String key) {
        Objects.requireNonNull(playerId, "playerId");
        final String normalized = ClassSpecCatalog.normalize(key);
        if (!isSessionReady(playerId) || normalized.isEmpty()) {
            return Optional.empty();
        }
        final ClassProfile profile = store.cached(playerId).orElseThrow();
        return profile.activeSlot() == null ? Optional.empty()
                : Optional.ofNullable(profile.loadout(profile.activeSlot()).mechanicState().get(normalized));
    }

    @Override
    public Optional<CompanionProfile> activeCompanion(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!isSessionReady(playerId)) {
            return Optional.empty();
        }
        final ClassProfile profile = store.cached(playerId).orElseThrow();
        if (profile.activeSlot() == null) {
            return Optional.empty();
        }
        return profile.loadout(profile.activeSlot()).companionRoster().values().stream()
                .sorted(java.util.Comparator.comparing(companion -> companion.companionId().toString()))
                .findFirst();
    }

    @Override
    public ProfileDiagnostic diagnostic(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!enabled()) {
            return ProfileDiagnostic.disabled();
        }
        final Optional<ClassProfile> cached = store.cached(playerId);
        if (cached.isEmpty()) {
            final Optional<String> quarantine = store.quarantineReason(playerId);
            if (quarantine.isPresent()) {
                return ProfileDiagnostic.quarantined(quarantine.orElseThrow());
            }
            return ProfileDiagnostic.unavailable(true,
                    store.sessionBlockReason(playerId).orElse("profile loading"));
        }
        return project(playerId, cached.orElseThrow());
    }

    @Override
    public void blockSession(final UUID playerId, final String reason) {
        Objects.requireNonNull(playerId, "playerId");
        final String detail = reason == null || reason.isBlank()
                ? "Profile v2 session blocked" : reason.trim();
        store.blockSession(playerId, detail);
        failClosed(playerId, detail);
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> select(
            final UUID playerId,
            final SelectRequest request) {
        Objects.requireNonNull(request, "request");
        return mutate(playerId, ProfileMutationPolicy.Operation.SELECT,
                ClassSpecRuntimePort.MutationKind.SELECT,
                profile -> planSelection(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> reset(
            final UUID playerId,
            final ResetRequest request) {
        Objects.requireNonNull(request, "request");
        final ProfileMutationPolicy.Operation operation = request.mode() == ResetMode.ADMIN_CLASS
                ? ProfileMutationPolicy.Operation.ADMIN_RESET
                : ProfileMutationPolicy.Operation.LOADOUT_RESET;
        final ClassSpecRuntimePort.MutationKind kind = request.mode() == ResetMode.ADMIN_CLASS
                ? ClassSpecRuntimePort.MutationKind.ADMIN_RESET
                : ClassSpecRuntimePort.MutationKind.RESPEC_RESET;

        return enqueue(Objects.requireNonNull(playerId, "playerId"), () -> {
            final ProfileMutationResult<ProfileDiagnostic> receipt = receipt(playerId, request.operationId());
            if (receipt != null) {
                return CompletableFuture.completedFuture(receipt);
            }
            return performMutation(playerId, operation, kind, profile -> planReset(profile, request))
                    .thenApply(result -> {
                        if (result.status() == ProfileMutationResult.Status.COMMITTED
                                || result.status() == ProfileMutationResult.Status.NO_CHANGE) {
                            rememberReceipt(playerId, request.operationId(), result);
                        }
                        return result;
                    });
        });
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> seal(
            final UUID playerId,
            final SealRequest request) {
        Objects.requireNonNull(request, "request");
        return mutate(playerId, ProfileMutationPolicy.Operation.EXPLICIT_SEAL,
                ClassSpecRuntimePort.MutationKind.EXPLICIT_SEAL,
                profile -> planSeal(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> reconcile(
            final UUID playerId,
            final ReconcileRequest request) {
        Objects.requireNonNull(request, "request");
        return mutate(playerId, ProfileMutationPolicy.Operation.GATE_RECONCILE,
                ClassSpecRuntimePort.MutationKind.GATE_RECONCILE,
                profile -> planReconcile(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> assignClass(
            final UUID playerId, final ClassAssignmentRequest request) {
        Objects.requireNonNull(request, "request");
        return mutate(playerId, ProfileMutationPolicy.Operation.CLASS_ASSIGN,
                ClassSpecRuntimePort.MutationKind.CLASS_ASSIGN,
                profile -> planClassAssignment(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mirrorClassLevel(
            final UUID playerId, final int classLevel) {
        return mutate(playerId, ProfileMutationPolicy.Operation.CLASS_LEVEL_MIRROR,
                ClassSpecRuntimePort.MutationKind.CLASS_LEVEL_MIRROR,
                profile -> planClassLevelMirror(profile, classLevel));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> incrementSoulforge(
            final UUID playerId, final String branch, final String operationId) {
        final String cleanOperationId = operationId == null ? "" : operationId.trim();
        if (cleanOperationId.isEmpty()) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    diagnostic(playerId), "operationId is required"));
        }
        return enqueue(Objects.requireNonNull(playerId, "playerId"), () -> {
            final ProfileMutationResult<ProfileDiagnostic> receipt = receipt(playerId, cleanOperationId);
            if (receipt != null) {
                return CompletableFuture.completedFuture(receipt);
            }
            return performMutation(playerId, ProfileMutationPolicy.Operation.SOULFORGE_UPGRADE,
                    ClassSpecRuntimePort.MutationKind.SOULFORGE_UPGRADE,
                    profile -> planSoulforgeIncrement(profile, branch)).thenApply(result -> {
                if (result.status() == ProfileMutationResult.Status.COMMITTED
                        || result.status() == ProfileMutationResult.Status.NO_CHANGE) {
                    rememberReceipt(playerId, cleanOperationId, result);
                }
                return result;
            });
        });
    }

    @Override
    public void clearSession(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        resetReceipts.remove(playerId);
        // A running mutation owns its gate until completion; removing that gate here would
        // allow a reconnecting session to overtake an older in-flight CAS write.
        final CompletableFuture<Void> tail = mutationTails.get(playerId);
        if (tail != null) {
            tail.whenComplete((ignored, failure) -> mutationTails.remove(playerId, tail));
        }
    }

    @Override
    public CompletionStage<Void> prepareShutdown() {
        final CompletableFuture<?>[] tails;
        synchronized (admissionLock) {
            accepting = false;
            tails = mutationTails.values().toArray(CompletableFuture[]::new);
        }
        return CompletableFuture.allOf(tails).thenRun(activationPending::clear);
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mutate(
            final UUID playerId,
            final ProfileMutationPolicy.Operation operation,
            final ClassSpecRuntimePort.MutationKind kind,
            final Function<ClassProfile, MutationPlan> planner) {
        Objects.requireNonNull(playerId, "playerId");
        return enqueue(playerId, () -> performMutation(playerId, operation, kind, planner));
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> performMutation(
            final UUID playerId,
            final ProfileMutationPolicy.Operation operation,
            final ClassSpecRuntimePort.MutationKind kind,
            final Function<ClassProfile, MutationPlan> planner) {
        if (!enabled()) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    ProfileDiagnostic.disabled(), "Profile v2 is disabled"));
        }
        final Optional<ClassProfile> loaded = store.cached(playerId);
        if (loaded.isEmpty()) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    diagnostic(playerId), "profile is not loaded"));
        }

        final ClassProfile previous = loaded.orElseThrow();
        final ProfileMutationPolicy.Decision access = mutationPolicy.assess(previous,
                store.sessionBlockReason(playerId), operation);
        if (!access.allowed()) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    project(playerId, previous), access.detail()));
        }

        final MutationPlan plan;
        try {
            plan = Objects.requireNonNull(planner.apply(previous), "mutation plan");
        } catch (final IllegalArgumentException | IllegalStateException rejected) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    project(playerId, previous), rejected.getMessage()));
        }
        if (plan.candidate() == null) {
            return CompletableFuture.completedFuture(plan.rejected()
                    ? ProfileMutationResult.rejected(project(playerId, previous), plan.detail())
                    : ProfileMutationResult.noChange(project(playerId, previous), plan.detail()));
        }

        final ClassProfile candidate = plan.candidate();
        if (previous.revision() == Long.MAX_VALUE || candidate.revision() != previous.revision() + 1L) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    project(playerId, previous), "mutation must increment revision exactly once"));
        }

        final CompletionStage<ClassProfileMutationStore.SaveResult> save;
        try {
            save = Objects.requireNonNull(store.save(playerId, previous.revision(), candidate), "save stage");
        } catch (final RuntimeException failure) {
            return persistenceFailure(playerId, previous, ProfileMutationResult.Status.PERSISTENCE_FAILED,
                    failureMessage(failure));
        }
        return save.handle((result, failure) -> new SaveCompletion(result, failure))
                .thenCompose(completion -> finishSave(playerId, previous, candidate, kind, completion));
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> finishSave(
            final UUID playerId,
            final ClassProfile previous,
            final ClassProfile candidate,
            final ClassSpecRuntimePort.MutationKind kind,
            final SaveCompletion completion) {
        if (completion.failure() != null || completion.result() == null) {
            return persistenceFailure(playerId, previous, ProfileMutationResult.Status.PERSISTENCE_FAILED,
                    completion.failure() == null ? "repository returned no result"
                            : failureMessage(completion.failure()));
        }
        final ClassProfileMutationStore.SaveResult result = completion.result();
        return switch (result.status()) {
            case COMMITTED -> {
                final ClassProfile durable = result.durableProfile();
                if (!candidate.equals(durable)) {
                    yield persistenceFailure(playerId, previous,
                            ProfileMutationResult.Status.PERSISTENCE_FAILED,
                            "repository committed a different profile candidate");
                }
                yield applyRuntimeAfterCommit(playerId, previous, durable, kind);
            }
            case REVISION_CONFLICT -> persistenceFailure(playerId,
                    durableOr(result, previous), ProfileMutationResult.Status.REVISION_CONFLICT,
                    result.detail() + " (actual=" + result.actualRevision() + ")");
            case PERSISTENCE_FAILED -> persistenceFailure(playerId,
                    durableOr(result, previous), ProfileMutationResult.Status.PERSISTENCE_FAILED,
                    result.detail());
            case LIFECYCLE_STOPPED -> lifecycleStopped(playerId, durableOr(result, previous), result.detail());
        };
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> applyRuntimeAfterCommit(
            final UUID playerId,
            final ClassProfile previous,
            final ClassProfile durable,
            final ClassSpecRuntimePort.MutationKind kind) {
        final CompletionStage<Void> effects;
        try {
            effects = Objects.requireNonNull(
                    runtime.profileCommitted(playerId, previous, durable, kind), "runtime stage");
        } catch (final RuntimeException failure) {
            return runtimeFailure(playerId, durable, failureMessage(failure));
        }
        return effects.handle((ignored, failure) -> failure)
                .thenCompose(failure -> failure == null
                        ? CompletableFuture.completedFuture(ProfileMutationResult.committed(
                        project(playerId, durable)))
                        : runtimeFailure(playerId, durable, failureMessage(failure)));
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> persistenceFailure(
            final UUID playerId,
            final ClassProfile durable,
            final ProfileMutationResult.Status status,
            final String detail) {
        final String reason = detail == null || detail.isBlank() ? status.name() : detail;
        store.blockSession(playerId, reason);
        return failClosed(playerId, reason).thenApply(ignored -> ProfileMutationResult.failed(
                project(playerId, durable), status, reason));
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> runtimeFailure(
            final UUID playerId,
            final ClassProfile durable,
            final String detail) {
        final String reason = "runtime effect failed: " + detail;
        store.blockSession(playerId, reason);
        return failClosed(playerId, reason).thenApply(ignored -> ProfileMutationResult.failed(
                project(playerId, durable), ProfileMutationResult.Status.RUNTIME_EFFECT_FAILED, reason));
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> lifecycleStopped(
            final UUID playerId,
            final ClassProfile durable,
            final String detail) {
        return failClosed(playerId, detail).thenApply(ignored -> ProfileMutationResult.failed(
                project(playerId, durable), ProfileMutationResult.Status.LIFECYCLE_STOPPED, detail));
    }

    private CompletionStage<Void> failClosed(final UUID playerId, final String reason) {
        try {
            final CompletionStage<Void> cleanup = runtime.failClosed(playerId, reason);
            if (cleanup == null) {
                return CompletableFuture.completedFuture(null);
            }
            return cleanup.handle((ignored, failure) -> null);
        } catch (final RuntimeException ignored) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private MutationPlan planSelection(final ClassProfile profile, final SelectRequest request) {
        final String specializationId = ClassSpecCatalog.normalize(request.specializationId());
        if (!ClassSpecCatalog.isKnownSpecialization(specializationId)) {
            return MutationPlan.rejected("unknown specialization: " + specializationId);
        }
        if (!ClassSpecCatalog.belongsTo(specializationId, profile.primaryClassId())) {
            return MutationPlan.rejected("specialization does not belong to the primary class");
        }
        if (DarkSpecializationPolicy.isDark(specializationId) && !request.gates().state().allSatisfied()) {
            return MutationPlan.rejected("DARK specialization gates are not satisfied");
        }
        if (request.slot() == LoadoutSlot.SECOND && !profile.secondSpecUnlocked()) {
            return MutationPlan.rejected("second specialization slot is locked");
        }
        if (profile.loadout(request.slot()).status() != LoadoutStatus.EMPTY) {
            return MutationPlan.rejected("target loadout is not empty");
        }
        for (final ClassLoadout existing : profile.loadouts()) {
            if (specializationId.equals(existing.specializationId())) {
                return MutationPlan.rejected("specialization already exists in another loadout");
            }
        }

        final boolean activate = profile.activeSlot() == null;
        final ClassLoadout loadout = new ClassLoadout(specializationId,
                activate ? LoadoutStatus.ACTIVE : LoadoutStatus.INACTIVE,
                null, Map.of(), MasteryProgress.empty(), null, Set.of(), "",
                CapstoneStatus.LOCKED, Map.of(), Map.of(), "");
        final ClassProfile candidate = profile.toBuilder()
                .revision(nextRevision(profile))
                .loadout(request.slot(), loadout)
                .activeSlot(activate ? request.slot() : profile.activeSlot())
                .build();
        return MutationPlan.candidate(candidate);
    }

    private MutationPlan planClassAssignment(final ClassProfile profile,
                                             final ClassAssignmentRequest request) {
        final String classId = ClassSpecCatalog.normalize(request.classId());
        if (!ClassSpecCatalog.isKnownClass(classId)) {
            return MutationPlan.rejected("unknown class: " + classId);
        }
        if (!profile.primaryClassId().isEmpty()) {
            return profile.primaryClassId().equals(classId)
                    ? MutationPlan.noChange("class already assigned")
                    : MutationPlan.rejected("profile already has another primary class");
        }
        if (request.classLevel() > ClassProfile.MAX_CLASS_LEVEL) {
            return MutationPlan.rejected("class level exceeds supported mirror range");
        }
        return MutationPlan.candidate(profile.toBuilder()
                .revision(nextRevision(profile))
                .primaryClassId(classId).classLevel(request.classLevel()).build());
    }

    private MutationPlan planClassLevelMirror(final ClassProfile profile, final int classLevel) {
        if (profile.primaryClassId().isEmpty()) {
            return MutationPlan.rejected("classless profile cannot mirror a class level");
        }
        if (classLevel < 1 || classLevel > ClassProfile.MAX_CLASS_LEVEL) {
            return MutationPlan.rejected("class level outside supported mirror range");
        }
        if (profile.classLevel() == classLevel) {
            return MutationPlan.noChange("class level mirror already current");
        }
        return MutationPlan.candidate(profile.toBuilder()
                .revision(nextRevision(profile)).classLevel(classLevel).build());
    }

    private MutationPlan planSoulforgeIncrement(final ClassProfile profile, final String rawBranch) {
        if (profile.activeSlot() == null) {
            return MutationPlan.rejected("Soulforge requires an active necromancer loadout");
        }
        final LoadoutSlot slot = profile.activeSlot();
        final ClassLoadout current = profile.loadout(slot);
        if (current.status() != LoadoutStatus.ACTIVE
                || !"necromancer".equals(current.specializationId())) {
            return MutationPlan.rejected("Soulforge requires an active necromancer loadout");
        }
        final String branch = ClassSpecCatalog.normalize(rawBranch);
        if (!Set.of("elet", "sebzes", "letszam").contains(branch)) {
            return MutationPlan.rejected("unknown Soulforge branch");
        }
        final String key = "necromancer.soulforge." + branch;
        final int rank;
        try {
            rank = Integer.parseInt(current.mechanicState().getOrDefault(key, "0"));
        } catch (final NumberFormatException invalid) {
            return MutationPlan.rejected("invalid durable Soulforge rank");
        }
        if (rank < 0 || rank >= 5) {
            return MutationPlan.rejected(rank >= 5 ? "Soulforge branch is maxed"
                    : "invalid durable Soulforge rank");
        }
        final Map<String, String> mechanics = new LinkedHashMap<>(current.mechanicState());
        mechanics.put(key, Integer.toString(rank + 1));
        final ClassLoadout upgraded = new ClassLoadout(current.specializationId(), current.status(),
                current.sealReason(), current.doctrineChoices(), current.mastery(), current.soulbond(),
                current.favoriteSpells(), current.selectedSpell(), current.capstoneStatus(),
                current.companionRoster(), mechanics, current.migrationNote());
        return MutationPlan.candidate(profile.toBuilder().revision(nextRevision(profile))
                .loadout(slot, upgraded).build());
    }

    private MutationPlan planReset(final ClassProfile profile, final ResetRequest request) {
        if (request.mode() == ResetMode.ADMIN_CLASS) {
            if (profile.primaryClassId().isEmpty()) {
                return MutationPlan.noChange("profile already has no class");
            }
            return MutationPlan.candidate(profile.withoutClass());
        }

        final LoadoutSlot slot = request.slot().orElseThrow();
        if (profile.loadout(slot).status() == LoadoutStatus.EMPTY) {
            return MutationPlan.noChange("loadout already empty");
        }
        final ClassProfile candidate = profile.toBuilder()
                .revision(nextRevision(profile))
                .loadout(slot, ClassLoadout.empty())
                .activeSlot(profile.activeSlot() == slot ? null : profile.activeSlot())
                .build();
        return MutationPlan.candidate(candidate);
    }

    private MutationPlan planSeal(final ClassProfile profile, final SealRequest request) {
        final ClassLoadout current = profile.loadout(request.slot());
        if (current.status() == LoadoutStatus.EMPTY) {
            return MutationPlan.rejected("an empty loadout cannot be sealed");
        }
        if (current.status() == LoadoutStatus.MIGRATION_REVIEW) {
            return MutationPlan.rejected("a migration-review loadout requires explicit recovery");
        }
        if (current.status() == LoadoutStatus.SEALED) {
            return current.sealReason().equals(request.reason())
                    ? MutationPlan.noChange("loadout already has this seal")
                    : MutationPlan.rejected("an existing seal requires explicit recovery");
        }
        final ClassProfile candidate = profile.toBuilder()
                .revision(nextRevision(profile))
                .loadout(request.slot(), current.withStatus(LoadoutStatus.SEALED, request.reason()))
                .activeSlot(profile.activeSlot() == request.slot() ? null : profile.activeSlot())
                .build();
        return MutationPlan.candidate(candidate);
    }

    private MutationPlan planReconcile(final ClassProfile profile, final ReconcileRequest request) {
        final EnumMap<LoadoutSlot, ClassLoadout> replacements = new EnumMap<>(LoadoutSlot.class);
        LoadoutSlot nextActive = profile.activeSlot();

        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            final ClassLoadout current = profile.loadout(slot);
            if (current.status() == LoadoutStatus.EMPTY
                    || !DarkSpecializationPolicy.isDark(current.specializationId())) {
                continue;
            }
            if (current.status() == LoadoutStatus.MIGRATION_REVIEW) {
                return MutationPlan.rejected("DARK loadout requires migration review");
            }
            final GateSnapshot gates = request.gatesBySlot().get(slot);
            if (gates == null) {
                return MutationPlan.rejected("missing gate snapshot for DARK loadout " + slot);
            }
            final SealReason missing = gates.missingReason();
            if (missing != null) {
                if (current.status() == LoadoutStatus.SEALED) {
                    if (current.sealReason().equals(missing)
                            || !current.sealReason().cause().gateRestorable()
                            || !gates.authorizesRecovery(current.sealReason())) {
                        continue;
                    }
                }
                replacements.put(slot, current.withStatus(LoadoutStatus.SEALED, missing));
                if (nextActive == slot) {
                    nextActive = null;
                }
            } else if (current.status() == LoadoutStatus.SEALED
                    && gates.authorizesRecovery(current.sealReason())) {
                // Restoring a gate makes the loadout usable again, but deliberately does not
                // auto-activate it or switch away from another slot.
                replacements.put(slot, current.withStatus(LoadoutStatus.INACTIVE, null));
            }
        }

        if (replacements.isEmpty()) {
            return MutationPlan.noChange("gate state already reconciled");
        }
        ClassProfile.Builder builder = profile.toBuilder()
                .revision(nextRevision(profile))
                .activeSlot(nextActive);
        for (final Map.Entry<LoadoutSlot, ClassLoadout> replacement : replacements.entrySet()) {
            builder = builder.loadout(replacement.getKey(), replacement.getValue());
        }
        return MutationPlan.candidate(builder.build());
    }

    private ProfileDiagnostic project(final UUID playerId, final ClassProfile profile) {
        final EnumMap<LoadoutSlot, ProfileDiagnostic.SlotDiagnostic> slots =
                new EnumMap<>(LoadoutSlot.class);
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            final ClassLoadout loadout = profile.loadout(slot);
            slots.put(slot, new ProfileDiagnostic.SlotDiagnostic(
                    optionalText(loadout.specializationId()), loadout.status(),
                    Optional.ofNullable(loadout.sealReason()), loadout.mastery().rank(),
                    loadout.mastery().experience()));
        }
        final String review = String.join("; ", profile.migrationState().reviewReasons());
        final String externalBlock = store.sessionBlockReason(playerId)
                .orElseGet(() -> activationPending.contains(playerId)
                        ? "profile login activation pending" : "");
        final String durableBlock = profile.diagnostics().sessionBlockReason();
        return new ProfileDiagnostic(true, true, profile.schemaVersion(), profile.revision(),
                profile.status(), optionalText(profile.primaryClassId()),
                Optional.ofNullable(profile.activeSlot()), profile.secondSpecUnlocked(), slots,
                optionalText(review), optionalText(profile.diagnostics().quarantineReason()),
                optionalText(externalBlock.isBlank() ? durableBlock : externalBlock));
    }

    private static ClassProfile durableOr(final ClassProfileMutationStore.SaveResult result,
                                          final ClassProfile fallback) {
        return result.durableProfile() == null ? fallback : result.durableProfile();
    }

    private static long nextRevision(final ClassProfile profile) {
        if (profile.revision() == Long.MAX_VALUE) {
            throw new IllegalStateException("profile revision exhausted");
        }
        return profile.revision() + 1L;
    }

    private static Optional<String> optionalText(final String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static String failureMessage(final Throwable failure) {
        final Throwable cause = failure instanceof java.util.concurrent.CompletionException
                && failure.getCause() != null ? failure.getCause() : failure;
        final String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private ProfileMutationResult<ProfileDiagnostic> receipt(final UUID playerId,
                                                              final String operationId) {
        final Map<String, ProfileMutationResult<ProfileDiagnostic>> receipts = resetReceipts.get(playerId);
        if (receipts == null) {
            return null;
        }
        synchronized (receipts) {
            return receipts.get(operationId);
        }
    }

    private void rememberReceipt(final UUID playerId,
                                 final String operationId,
                                 final ProfileMutationResult<ProfileDiagnostic> result) {
        final Map<String, ProfileMutationResult<ProfileDiagnostic>> receipts = resetReceipts.computeIfAbsent(
                playerId, ignored -> new LinkedHashMap<>());
        synchronized (receipts) {
            receipts.put(operationId, result);
            while (receipts.size() > MAX_RESET_RECEIPTS_PER_PLAYER) {
                receipts.remove(receipts.keySet().iterator().next());
            }
        }
    }

    private <T> CompletionStage<T> enqueue(final UUID playerId,
                                           final Supplier<CompletionStage<T>> operation) {
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        final CompletableFuture<Void> previous;
        synchronized (admissionLock) {
            if (!accepting) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Profile v2 mutation lifecycle stopped"));
            }
            previous = mutationTails.put(playerId, gate);
        }
        final CompletionStage<Void> start = previous == null
                ? CompletableFuture.completedFuture(null)
                : previous.handle((ignored, failure) -> null);
        final CompletableFuture<T> result = new CompletableFuture<>();
        start.thenCompose(ignored -> {
            try {
                return Objects.requireNonNull(operation.get(), "operation stage");
            } catch (final Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }).whenComplete((value, failure) -> {
            if (failure == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(failure);
            }
            gate.complete(null);
            mutationTails.remove(playerId, gate);
        });
        return result;
    }

    private record MutationPlan(ClassProfile candidate, boolean rejected, String detail) {

        private MutationPlan {
            detail = detail == null ? "" : detail;
        }

        private static MutationPlan candidate(final ClassProfile candidate) {
            return new MutationPlan(Objects.requireNonNull(candidate, "candidate"), false, "");
        }

        private static MutationPlan noChange(final String detail) {
            return new MutationPlan(null, false, detail);
        }

        private static MutationPlan rejected(final String detail) {
            return new MutationPlan(null, true, detail);
        }
    }

    private record SaveCompletion(ClassProfileMutationStore.SaveResult result, Throwable failure) {
    }
}
