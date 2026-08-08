package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.*;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/** Sole Profile v2 gateway with per-player serialization and session-generation fencing. */
public final class DefaultClassSpecProfileGateway implements ClassSpecProfileGateway {
    private final ClassSpecSectionMutationStore store;
    private final ClassSpecRuntimePort runtime;
    private final ProfileSessionRegistry sessions;
    private final ProfileMutationPolicy policy;
    private final Map<UUID, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();
    private final Object admissionLock = new Object();
    private boolean accepting = true;

    public DefaultClassSpecProfileGateway(final ClassSpecSectionMutationStore store,
                                          final ClassSpecRuntimePort runtime,
                                          final ProfileSessionRegistry sessions) {
        this(store, runtime, sessions, new ProfileMutationPolicy());
    }

    DefaultClassSpecProfileGateway(final ClassSpecSectionMutationStore store,
                                   final ClassSpecRuntimePort runtime,
                                   final ProfileSessionRegistry sessions,
                                   final ProfileMutationPolicy policy) {
        this.store = Objects.requireNonNull(store);
        this.runtime = Objects.requireNonNull(runtime);
        this.sessions = Objects.requireNonNull(sessions);
        this.policy = Objects.requireNonNull(policy);
    }

    public ProfileSessionRegistry sessions() {
        return sessions;
    }

    @Override
    public boolean isSessionReady(final UUID id) {
        Objects.requireNonNull(id);
        final Optional<ClassSpecSection> profile = store.cached(id);
        return sessions.isReady(id) && profile.isPresent() && profile.orElseThrow().isGameplayUsable()
                && store.sessionBlockReason(id).isEmpty();
    }

    @Override
    public Optional<UUID> currentSessionToken(final UUID id) {
        return sessions.currentToken(Objects.requireNonNull(id));
    }

    @Override
    public boolean isCurrentSession(final UUID id, final UUID token) {
        return sessions.isCurrent(Objects.requireNonNull(id), Objects.requireNonNull(token));
    }

    @Override
    public void beginSessionActivation(final UUID id, final UUID token) {
        if (!sessions.isCurrent(id, token)) {
            throw new ProfileSessionRegistry.StaleSessionException(id, token);
        }
    }

    @Override
    public void completeSessionActivation(final UUID id, final UUID token) {
        sessions.markReady(id, token);
    }

    @Override
    public void cancelSessionActivation(final UUID id, final UUID token) {
        sessions.close(id, token);
    }

    @Override
    public Optional<ClassSpecSection> currentProfile(final UUID id) {
        return store.cached(Objects.requireNonNull(id));
    }

    @Override
    public Optional<String> activeSpecId(final UUID id) {
        if (!isSessionReady(id)) return Optional.empty();
        final ClassSpecSection profile = store.cached(id).orElseThrow();
        if (profile.activeSlot() == null) return Optional.empty();
        final ClassLoadout loadout = profile.loadout(profile.activeSlot());
        return loadout.status() == LoadoutStatus.ACTIVE
                ? Optional.of(loadout.specializationId()) : Optional.empty();
    }

    @Override
    public Optional<String> activeMechanic(final UUID id, final String key) {
        final String normalized = ClassSpecCatalog.normalize(key);
        if (!isSessionReady(id) || normalized.isEmpty()) return Optional.empty();
        final ClassSpecSection profile = store.cached(id).orElseThrow();
        return profile.activeSlot() == null ? Optional.empty()
                : Optional.ofNullable(profile.loadout(profile.activeSlot())
                .mechanicState().get(normalized));
    }

    @Override
    public Optional<CompanionProfile> activeCompanion(final UUID id) {
        if (!isSessionReady(id)) return Optional.empty();
        final ClassSpecSection profile = store.cached(id).orElseThrow();
        if (profile.activeSlot() == null) return Optional.empty();
        final ClassLoadout loadout = profile.loadout(profile.activeSlot());
        final String active = loadout.mechanicState().get("companion.active_id");
        if (active == null || active.isBlank()) return Optional.empty();
        try {
            return Optional.ofNullable(loadout.companionRoster().get(UUID.fromString(active)));
        } catch (final IllegalArgumentException ignored) {
            store.blockSession(id, "Invalid active companion identity");
            return Optional.empty();
        }
    }

    @Override
    public Optional<ProfileOperation> operation(final UUID id, final String operationId) {
        return store.cached(Objects.requireNonNull(id))
                .flatMap(profile -> profile.operation(operationId));
    }

    @Override
    public ProfileDiagnostic diagnostic(final UUID id) {
        final Optional<ClassSpecSection> profile = store.cached(id);
        if (profile.isEmpty()) {
            final Optional<String> quarantine = store.quarantineReason(id);
            return quarantine.isPresent()
                    ? ProfileDiagnostic.quarantined(quarantine.orElseThrow())
                    : ProfileDiagnostic.unavailable(
                    store.sessionBlockReason(id).orElse("profile loading"));
        }
        return project(id, profile.orElseThrow());
    }

    @Override
    public void blockSession(final UUID id, final String reason) {
        final String detail = reason == null || reason.isBlank()
                ? "Profile v2 session blocked" : reason.trim();
        store.blockSession(id, detail);
        sessions.currentToken(id).ifPresent(token -> {
            if (sessions.isCurrent(id, token)) {
                sessions.markReconciliationRequired(id, token, detail);
                try {
                    runtime.failClosed(id, token, detail).whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            store.blockSession(id, detail + "; fail-closed cleanup failed: "
                                    + message(failure));
                        }
                    });
                } catch (final RuntimeException failure) {
                    store.blockSession(id, detail + "; fail-closed cleanup failed: "
                            + message(failure));
                }
            }
        });
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> select(
            final UUID id, final SelectRequest request) {
        return mutate(id, ProfileMutationPolicy.Operation.SELECT,
                ClassSpecRuntimePort.MutationKind.SELECT,
                profile -> planSelection(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> switchActiveLoadout(
            final UUID id, final SwitchLoadoutRequest request) {
        return mutate(id, ProfileMutationPolicy.Operation.LOADOUT_SWITCH,
                ClassSpecRuntimePort.MutationKind.LOADOUT_SWITCH,
                profile -> planSwitch(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> chooseDoctrine(
            final UUID id, final DoctrineChoiceRequest request) {
        return mutate(id, ProfileMutationPolicy.Operation.DOCTRINE_CHOICE,
                ClassSpecRuntimePort.MutationKind.DOCTRINE_CHANGE,
                profile -> planDoctrine(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> assignClass(
            final UUID id, final ClassAssignmentRequest request) {
        return mutate(id, ProfileMutationPolicy.Operation.CLASS_ASSIGN,
                ClassSpecRuntimePort.MutationKind.CLASS_ASSIGN,
                profile -> planClassAssignment(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mutateClassExperience(
            final UUID id, final ClassExperienceRequest request) {
        return mutate(id, ProfileMutationPolicy.Operation.CLASS_EXPERIENCE,
                ClassSpecRuntimePort.MutationKind.CLASS_EXPERIENCE,
                profile -> planClassExperience(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> seal(
            final UUID id, final SealRequest request) {
        return mutate(id, ProfileMutationPolicy.Operation.EXPLICIT_SEAL,
                ClassSpecRuntimePort.MutationKind.EXPLICIT_SEAL,
                profile -> planSeal(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> reconcile(
            final UUID id, final ReconcileRequest request) {
        return mutate(id, ProfileMutationPolicy.Operation.GATE_RECONCILE,
                ClassSpecRuntimePort.MutationKind.GATE_RECONCILE,
                profile -> planReconcile(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> reset(
            final UUID id, final ResetRequest request) {
        return mutate(id,
                request.mode() == ResetMode.ADMIN_CLASS
                        ? ProfileMutationPolicy.Operation.ADMIN_RESET
                        : ProfileMutationPolicy.Operation.LOADOUT_RESET,
                request.mode() == ResetMode.ADMIN_CLASS
                        ? ClassSpecRuntimePort.MutationKind.ADMIN_RESET
                        : ClassSpecRuntimePort.MutationKind.RESPEC_RESET,
                profile -> planReset(profile, request));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> incrementSoulforge(
            final UUID id, final String branch, final int shardCost, final String operationId) {
        return mutate(id, ProfileMutationPolicy.Operation.SOULFORGE_UPGRADE,
                ClassSpecRuntimePort.MutationKind.SOULFORGE_UPGRADE,
                profile -> planSoulforge(profile, branch, shardCost, operationId));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mutateSoulShards(
            final UUID id, final int delta, final String operationId) {
        return mutate(id, ProfileMutationPolicy.Operation.SOUL_SHARD_MUTATION,
                ClassSpecRuntimePort.MutationKind.SOUL_SHARD_MUTATION,
                profile -> planSoulShards(profile, delta, operationId));
    }

    @Override
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mutateCompanion(
            final UUID id, final CompanionMutationRequest request) {
        return mutate(id, ProfileMutationPolicy.Operation.COMPANION_MUTATION,
                request.kind() == CompanionMutationRequest.Kind.PROGRESS
                        ? ClassSpecRuntimePort.MutationKind.COMPANION_PROGRESS
                        : ClassSpecRuntimePort.MutationKind.COMPANION_MUTATION,
                profile -> planCompanion(profile, request));
    }

    @Override
    public Optional<String> quarantineEvidenceId(final UUID id) {
        return store.quarantineEvidenceId(id);
    }

    @Override
    public CompletionStage<RecoveryResult> recoverQuarantined(final UUID id,
                                                               final String evidenceId,
                                                               final String auditId) {
        Objects.requireNonNull(id);
        final String evidence = Objects.requireNonNull(evidenceId).trim();
        final String audit = Objects.requireNonNull(auditId).trim();
        return enqueue(id, () -> store.recover(id, evidence, audit).thenApply(profile -> {
            store.blockSession(id, "Profile recovered; reconnect required before activation");
            sessions.currentToken(id).ifPresent(token -> sessions.markReconciliationRequired(
                    id, token, "Profile recovered; reconnect required"));
            return new RecoveryResult(profile, evidence, audit,
                    audit.equals(profile.diagnostics().recoveryAuditId()));
        }));
    }

    @Override
    public CompletionStage<Void> awaitPlayerMutations(final UUID id) {
        final CompletableFuture<Void> tail = tails.get(id);
        return tail == null ? CompletableFuture.completedFuture(null) : tail.handle((v, x) -> null);
    }

    @Override
    public CompletionStage<Void> prepareShutdown() {
        final CompletableFuture<?>[] pending;
        synchronized (admissionLock) {
            accepting = false;
            pending = tails.values().toArray(CompletableFuture[]::new);
        }
        sessions.stop();
        return CompletableFuture.allOf(pending);
    }

    @Override
    public void clearSession(final UUID id) {
        final CompletableFuture<Void> tail = tails.get(id);
        if (tail != null) tail.whenComplete((v, x) -> tails.remove(id, tail));
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mutate(
            final UUID id, final ProfileMutationPolicy.Operation operation,
            final ClassSpecRuntimePort.MutationKind kind,
            final Function<ClassSpecSection, Plan> planner) {
        Objects.requireNonNull(id);
        final Optional<UUID> token = sessions.currentToken(id);
        if (token.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ProfileMutationResult.rejected(diagnostic(id), "no active Profile v2 session"));
        }
        final UUID captured = token.orElseThrow();
        return enqueue(id, () -> perform(id, captured, operation, kind, planner));
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> perform(
            final UUID id, final UUID token, final ProfileMutationPolicy.Operation operation,
            final ClassSpecRuntimePort.MutationKind kind,
            final Function<ClassSpecSection, Plan> planner) {
        if (!sessions.isCurrent(id, token)) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    diagnostic(id), "stale session before mutation"));
        }
        final Optional<ClassSpecSection> loaded = store.cached(id);
        if (loaded.isEmpty()) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    diagnostic(id), "profile is not loaded"));
        }
        final ClassSpecSection previous = loaded.orElseThrow();
        final ProfileMutationPolicy.Decision access = policy.assess(
                previous, store.sessionBlockReason(id), operation);
        if (!access.allowed()) {
            return CompletableFuture.completedFuture(
                    ProfileMutationResult.rejected(project(id, previous), access.detail()));
        }

        final Plan plan;
        try {
            plan = Objects.requireNonNull(planner.apply(previous));
        } catch (final IllegalArgumentException | IllegalStateException failure) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    project(id, previous), failure.getMessage()));
        }
        if (plan.candidate == null) {
            return CompletableFuture.completedFuture(plan.rejected
                    ? ProfileMutationResult.rejected(project(id, previous), plan.detail)
                    : ProfileMutationResult.noChange(project(id, previous), plan.detail));
        }
        final ClassSpecSection candidate = plan.candidate;
        if (candidate.revision() != NumericGuards.nextRevision(previous.revision())) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    project(id, previous), "mutation must increment revision exactly once"));
        }

        final CompletionStage<ClassSpecSectionMutationStore.SaveResult> save;
        try {
            save = Objects.requireNonNull(store.save(id, previous.revision(), candidate));
        } catch (final RuntimeException failure) {
            return persistenceFailure(id, token, previous,
                    ProfileMutationResult.Status.PERSISTENCE_FAILED, message(failure));
        }
        return save.handle(SaveCompletion::new).thenCompose(done -> {
            if (done.failure != null) {
                return persistenceFailure(id, token, previous,
                        ProfileMutationResult.Status.PERSISTENCE_FAILED, message(done.failure));
            }
            final ClassSpecSectionMutationStore.SaveResult result = done.result;
            final ClassSpecSection durable = result.durableProfile() == null
                    ? previous : result.durableProfile();
            return switch (result.status()) {
                case COMMITTED -> afterCommit(id, token, previous, result.durableProfile(), kind);
                case REVISION_CONFLICT -> persistenceFailure(id, token, durable,
                        ProfileMutationResult.Status.REVISION_CONFLICT, result.detail());
                case PERSISTENCE_FAILED -> persistenceFailure(id, token, durable,
                        ProfileMutationResult.Status.PERSISTENCE_FAILED, result.detail());
                case LIFECYCLE_STOPPED -> CompletableFuture.completedFuture(
                        ProfileMutationResult.failed(project(id, durable),
                                ProfileMutationResult.Status.LIFECYCLE_STOPPED, result.detail()));
            };
        });
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> afterCommit(
            final UUID id, final UUID token, final ClassSpecSection previous,
            final ClassSpecSection durable, final ClassSpecRuntimePort.MutationKind kind) {
        if (!sessions.isCurrent(id, token)) {
            return CompletableFuture.completedFuture(ProfileMutationResult.stale(
                    project(id, durable),
                    "durable commit completed for a retired session; runtime effects fenced"));
        }
        CompletionStage<Void> effect;
        try {
            effect = Objects.requireNonNull(
                    runtime.profileCommitted(id, token, previous, durable, kind));
        } catch (final RuntimeException failure) {
            effect = CompletableFuture.failedFuture(failure);
        }
        return effect.handle((v, failure) -> {
            if (failure == null) return ProfileMutationResult.committed(project(id, durable));
            final String detail = "durable commit succeeded but runtime reconciliation failed: "
                    + message(failure);
            store.blockSession(id, detail);
            if (sessions.isCurrent(id, token)) {
                sessions.markReconciliationRequired(id, token, detail);
            }
            return ProfileMutationResult.failed(project(id, durable),
                    ProfileMutationResult.Status.RUNTIME_EFFECT_FAILED, detail);
        });
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> persistenceFailure(
            final UUID id, final UUID token, final ClassSpecSection durable,
            final ProfileMutationResult.Status status, final String detail) {
        store.blockSession(id, detail);
        if (sessions.isCurrent(id, token)) sessions.markReconciliationRequired(id, token, detail);
        CompletionStage<Void> cleanup;
        try {
            cleanup = runtime.failClosed(id, token, detail);
        } catch (final RuntimeException failure) {
            cleanup = CompletableFuture.failedFuture(failure);
        }
        return cleanup.handle((v, failure) -> ProfileMutationResult.failed(project(id, durable),
                status, failure == null ? detail
                        : detail + "; fail-closed cleanup also failed: " + message(failure)));
    }

    private Plan planClassAssignment(final ClassSpecSection profile,
                                     final ClassAssignmentRequest request) {
        final String clazz = ClassSpecCatalog.normalize(request.classId());
        if (!ClassSpecCatalog.isKnownClass(clazz)) return Plan.reject("unknown class: " + clazz);
        if (!profile.primaryClassId().isEmpty()) {
            return profile.primaryClassId().equals(clazz)
                    ? Plan.no("class already assigned")
                    : Plan.reject("profile already has another class");
        }
        if (request.classLevel() > ClassSpecSection.MAX_CLASS_LEVEL) {
            return Plan.reject("class level exceeds supported range");
        }
        return Plan.of(profile.toBuilder()
                .revision(NumericGuards.nextRevision(profile.revision()))
                .primaryClassId(clazz)
                .classLevel(request.classLevel())
                .classExperience(request.classExperience())
                .build());
    }

    private Plan planClassExperience(final ClassSpecSection profile,
                                     final ClassExperienceRequest request) {
        if (profile.primaryClassId().isEmpty()) {
            return Plan.reject("classless profile cannot mutate experience");
        }
        final Optional<ProfileOperation> existing = profile.operation(request.operationId());
        if (existing.isPresent()) {
            return replay(existing.orElseThrow(), ProfileOperationType.CLASS_EXPERIENCE,
                    request.mode().name(), Integer.toString(request.value()), "class_xp",
                    "class experience");
        }

        final int nextExperience;
        try {
            nextExperience = request.mode() == ClassExperienceRequest.Mode.ADD
                    ? Math.addExact(profile.classExperience(), request.value()) : request.value();
        } catch (final ArithmeticException overflow) {
            return Plan.reject("class experience overflow");
        }
        if (nextExperience < 0) return Plan.reject("class experience cannot be negative");

        final int nextLevel = levelForExperience(nextExperience, request.baseXp(),
                request.incrementPerLevel());
        final ClassSpecSection.Builder builder = profile.toBuilder()
                .revision(NumericGuards.nextRevision(profile.revision()))
                .classExperience(nextExperience)
                .classLevel(nextLevel);

        if (!profile.secondSpecUnlocked() && request.secondSpecUnlockLevel() > 0
                && nextLevel >= request.secondSpecUnlockLevel()) {
            builder.secondSpecUnlocked(true);
        }

        if (request.mode() == ClassExperienceRequest.Mode.ADD
                && request.masteryExperienceGain() > 0 && profile.activeSlot() != null) {
            final LoadoutSlot activeSlot = profile.activeSlot();
            final ClassLoadout active = profile.loadout(activeSlot);
            if (active.status() == LoadoutStatus.ACTIVE) {
                final long rawMasteryExperience;
                try {
                    rawMasteryExperience = Math.addExact(active.mastery().experience(),
                            (long) request.masteryExperienceGain());
                } catch (final ArithmeticException overflow) {
                    return Plan.reject("specialization mastery experience overflow");
                }
                final long masteryCap;
                try {
                    masteryCap = Math.multiplyExact((long) request.masteryExperiencePerRank(),
                            MasteryProgress.MAX_RANK);
                } catch (final ArithmeticException overflow) {
                    return Plan.reject("specialization mastery curve overflow");
                }
                final long nextMasteryExperience = Math.min(rawMasteryExperience, masteryCap);
                final int nextMasteryRank = Math.min(MasteryProgress.MAX_RANK,
                        (int) (nextMasteryExperience / request.masteryExperiencePerRank()));
                CapstoneStatus capstone = active.capstoneStatus();
                if (nextMasteryRank >= MasteryProgress.MAX_RANK
                        && capstone == CapstoneStatus.LOCKED) {
                    capstone = CapstoneStatus.AVAILABLE;
                }
                // A mastery-rankot elérő ugyanazon kill még nem zárja le a trialt: a
                // következő ritka mob a tényleges, jól látható capstone-próba.
                if (request.capstoneTrialVictory()
                        && active.mastery().rank() >= MasteryProgress.MAX_RANK
                        && capstone == CapstoneStatus.AVAILABLE) {
                    capstone = CapstoneStatus.COMPLETED;
                }
                builder.loadout(activeSlot, active.withProgression(
                        new MasteryProgress(nextMasteryRank, nextMasteryExperience), capstone));
            }
        }

        final ClassSpecSection candidate = builder.build();
        final ProfileOperation receipt = new ProfileOperation(request.operationId(),
                ProfileOperationType.CLASS_EXPERIENCE, ProfileOperationStatus.COMMITTED,
                request.mode().name(), Integer.toString(request.value()), "class_xp",
                profile.revision(), "class experience and active mastery committed");
        return Plan.of(withReceipt(candidate, receipt));
    }

    private Plan planSelection(final ClassSpecSection profile, final SelectRequest request) {
        final String spec = ClassSpecCatalog.normalize(request.specializationId());
        if (!ClassSpecCatalog.isKnownSpecialization(spec)
                || !ClassSpecCatalog.belongsTo(spec, profile.primaryClassId())) {
            return Plan.reject("specialization does not belong to primary class");
        }
        if (DarkSpecializationPolicy.isDark(spec) && !request.gates().state().allSatisfied()) {
            return Plan.reject("DARK specialization gates are not satisfied");
        }
        if (request.slot() == LoadoutSlot.SECOND && !profile.secondSpecUnlocked()) {
            return Plan.reject("second slot locked");
        }
        if (profile.loadout(request.slot()).status() != LoadoutStatus.EMPTY) {
            return Plan.reject("target loadout is not empty");
        }
        if (profile.loadouts().stream().anyMatch(loadout -> spec.equals(loadout.specializationId()))) {
            return Plan.reject("specialization already exists");
        }
        final boolean activate = profile.activeSlot() == null;
        final ClassLoadout loadout = new ClassLoadout(spec,
                activate ? LoadoutStatus.ACTIVE : LoadoutStatus.INACTIVE, null, Map.of(),
                MasteryProgress.empty(), null, Set.of(), "", CapstoneStatus.LOCKED,
                Map.of(), Map.of(), "");
        return Plan.of(profile.toBuilder()
                .revision(NumericGuards.nextRevision(profile.revision()))
                .loadout(request.slot(), loadout)
                .activeSlot(activate ? request.slot() : profile.activeSlot())
                .build());
    }

    private Plan planSwitch(final ClassSpecSection profile, final SwitchLoadoutRequest request) {
        final LoadoutSlot targetSlot = request.targetSlot();
        if (targetSlot == LoadoutSlot.SECOND && !profile.secondSpecUnlocked()) {
            return Plan.reject("second slot locked");
        }
        final ClassLoadout target = profile.loadout(targetSlot);
        if (!target.isActivatable()) {
            return Plan.reject(target.status() == LoadoutStatus.SEALED
                    ? "sealed loadout cannot be activated"
                    : "target loadout is empty");
        }
        if (profile.activeSlot() == targetSlot && target.status() == LoadoutStatus.ACTIVE) {
            return Plan.no("loadout already active");
        }

        final ClassSpecSection.Builder builder = profile.toBuilder()
                .revision(NumericGuards.nextRevision(profile.revision()));
        if (profile.activeSlot() != null) {
            final LoadoutSlot previousSlot = profile.activeSlot();
            final ClassLoadout previous = profile.loadout(previousSlot);
            if (previous.status() == LoadoutStatus.ACTIVE) {
                builder.loadout(previousSlot, previous.withStatus(LoadoutStatus.INACTIVE, null));
            }
        }
        builder.loadout(targetSlot, target.withStatus(LoadoutStatus.ACTIVE, null));
        builder.activeSlot(targetSlot);
        return Plan.of(builder.build());
    }

    private Plan planDoctrine(final ClassSpecSection profile,
                              final DoctrineChoiceRequest request) {
        final ClassLoadout current = profile.loadout(request.slot());
        if (current.status() == LoadoutStatus.EMPTY) {
            return Plan.reject("empty loadout has no doctrine");
        }
        if (current.status() == LoadoutStatus.SEALED) {
            return Plan.reject("sealed loadout doctrine cannot be changed");
        }
        final String branch = ClassSpecCatalog.normalize(request.branchId());
        final String choice = ClassSpecCatalog.normalize(request.choiceId());
        if (choice.equals(current.doctrineChoices().get(branch))) {
            return Plan.no("doctrine choice already active");
        }
        return Plan.of(profile.toBuilder()
                .revision(NumericGuards.nextRevision(profile.revision()))
                .loadout(request.slot(), current.withDoctrineChoice(branch, choice))
                .build());
    }

    private Plan planReset(final ClassSpecSection profile, final ResetRequest request) {
        final Optional<ProfileOperation> existing = profile.operation(request.operationId());
        if (existing.isPresent()) {
            return replay(existing.orElseThrow(), ProfileOperationType.RESPEC,
                    request.mode().name(), request.amount(),
                    request.currencyId().isEmpty() ? "none" : request.currencyId(), "reset");
        }
        final ClassSpecSection candidate;
        if (request.mode() == ResetMode.ADMIN_CLASS) {
            if (profile.primaryClassId().isEmpty()) return Plan.no("profile already classless");
            candidate = profile.withoutClass();
        } else {
            final LoadoutSlot slot = request.slot().orElseThrow();
            if (profile.loadout(slot).status() == LoadoutStatus.EMPTY) {
                return Plan.no("loadout already empty");
            }
            candidate = profile.toBuilder()
                    .revision(NumericGuards.nextRevision(profile.revision()))
                    .loadout(slot, ClassLoadout.empty())
                    .activeSlot(profile.activeSlot() == slot ? null : profile.activeSlot())
                    .build();
        }
        final ProfileOperation receipt = new ProfileOperation(request.operationId(),
                ProfileOperationType.RESPEC, ProfileOperationStatus.COMMITTED,
                request.mode().name(), request.amount(),
                request.currencyId().isEmpty() ? "none" : request.currencyId(),
                profile.revision(), "profile reset committed");
        return Plan.of(withReceipt(candidate, receipt));
    }

    private static int levelForExperience(final int experience, final int baseXp,
                                          final int increment) {
        int level = 1;
        int remaining = experience;
        while (level < ClassSpecSection.MAX_CLASS_LEVEL) {
            final int cost;
            try {
                cost = Math.addExact(baseXp, Math.multiplyExact(level - 1, increment));
            } catch (final ArithmeticException overflow) {
                throw new IllegalArgumentException("class level curve overflow", overflow);
            }
            if (remaining < cost) break;
            remaining -= cost;
            level++;
        }
        return level;
    }

    private Plan planSoulforge(final ClassSpecSection profile, final String rawBranch,
                               final int shardCost, final String operationId) {
        final String opId = operationId == null ? "" : operationId.trim();
        if (opId.isEmpty()) return Plan.reject("operationId is required");
        if (shardCost <= 0) return Plan.reject("Soulforge shard cost must be positive");
        final String branch = ClassSpecCatalog.normalize(rawBranch);
        if (!Set.of("elet", "sebzes", "letszam").contains(branch)) {
            return Plan.reject("unknown Soulforge branch");
        }
        final Optional<ProfileOperation> existing = profile.operation(opId);
        if (existing.isPresent()) {
            return replay(existing.orElseThrow(), ProfileOperationType.SOULFORGE_UPGRADE,
                    branch, Integer.toString(shardCost), "soul_shard", "Soulforge");
        }
        if (profile.activeSlot() == null) return Plan.reject("Soulforge requires active necromancer");
        final LoadoutSlot slot = profile.activeSlot();
        final ClassLoadout current = profile.loadout(slot);
        if (current.status() != LoadoutStatus.ACTIVE
                || !"necromancer".equals(current.specializationId())) {
            return Plan.reject("Soulforge requires active necromancer");
        }
        final String rankKey = "necromancer.soulforge." + branch;
        final String shardKey = "necromancer.soulforge.shards";
        final int rank;
        final int shards;
        try {
            rank = Integer.parseInt(current.mechanicState().getOrDefault(rankKey, "0"));
            shards = Integer.parseInt(current.mechanicState().getOrDefault(shardKey, "0"));
        } catch (final NumberFormatException failure) {
            return Plan.reject("invalid Soulforge numeric state");
        }
        if (rank < 0 || rank >= 5) {
            return Plan.reject(rank >= 5 ? "Soulforge branch maxed" : "invalid Soulforge rank");
        }
        if (shards < shardCost) return Plan.reject("insufficient soul shards");
        final Map<String, String> mechanics = new LinkedHashMap<>(current.mechanicState());
        mechanics.put(rankKey, Integer.toString(Math.addExact(rank, 1)));
        mechanics.put(shardKey, Integer.toString(Math.subtractExact(shards, shardCost)));
        final ClassSpecSection candidate = profile.toBuilder()
                .revision(NumericGuards.nextRevision(profile.revision()))
                .loadout(slot, current.withMechanicState(mechanics))
                .build();
        return Plan.of(withReceipt(candidate, new ProfileOperation(opId,
                ProfileOperationType.SOULFORGE_UPGRADE, ProfileOperationStatus.COMMITTED,
                branch, Integer.toString(shardCost), "soul_shard", profile.revision(),
                "rank and shard debit committed atomically")));
    }

    private Plan planSoulShards(final ClassSpecSection profile, final int delta,
                                final String operationId) {
        if (delta == 0) return Plan.no("zero soul shard delta");
        final String opId = operationId == null ? "" : operationId.trim();
        if (opId.isEmpty()) return Plan.reject("operationId is required");
        final Optional<ProfileOperation> existing = profile.operation(opId);
        if (existing.isPresent()) {
            return replay(existing.orElseThrow(), ProfileOperationType.SOUL_SHARD_MUTATION,
                    "shards", Integer.toString(delta), "soul_shard", "soul shard");
        }
        if (profile.activeSlot() == null) return Plan.reject("soul shards require active necromancer");
        final LoadoutSlot slot = profile.activeSlot();
        final ClassLoadout current = profile.loadout(slot);
        if (current.status() != LoadoutStatus.ACTIVE
                || !"necromancer".equals(current.specializationId())) {
            return Plan.reject("soul shards require active necromancer");
        }
        final String key = "necromancer.soulforge.shards";
        final int currentAmount;
        try {
            currentAmount = Integer.parseInt(current.mechanicState().getOrDefault(key, "0"));
        } catch (final NumberFormatException failure) {
            return Plan.reject("invalid soul shard state");
        }
        final int next;
        try {
            next = Math.addExact(currentAmount, delta);
        } catch (final ArithmeticException failure) {
            return Plan.reject("soul shard overflow");
        }
        if (next < 0) return Plan.reject("insufficient soul shards");
        final Map<String, String> mechanics = new LinkedHashMap<>(current.mechanicState());
        mechanics.put(key, Integer.toString(next));
        final ClassSpecSection candidate = profile.toBuilder()
                .revision(NumericGuards.nextRevision(profile.revision()))
                .loadout(slot, current.withMechanicState(mechanics))
                .build();
        return Plan.of(withReceipt(candidate, new ProfileOperation(opId,
                ProfileOperationType.SOUL_SHARD_MUTATION, ProfileOperationStatus.COMMITTED,
                "shards", Integer.toString(delta), "soul_shard", profile.revision(),
                "soul shard balance committed")));
    }

    private Plan planCompanion(final ClassSpecSection profile,
                               final CompanionMutationRequest request) {
        final Optional<ProfileOperation> existing = profile.operation(request.operationId());
        if (existing.isPresent()) {
            return replay(existing.orElseThrow(), ProfileOperationType.COMPANION_MUTATION,
                    request.kind().name(), "0", "none", "companion");
        }
        final ClassLoadout current = profile.loadout(request.slot());
        if (current.status() == LoadoutStatus.EMPTY || current.status() == LoadoutStatus.SEALED) {
            return Plan.reject("companion loadout is unavailable");
        }
        final Map<UUID, CompanionProfile> roster = new LinkedHashMap<>(current.companionRoster());
        final Map<String, String> mechanics = new LinkedHashMap<>(current.mechanicState());
        final UUID id = request.companionId();
        switch (request.kind()) {
            case ADD -> {
                if (request.companion() == null) return Plan.reject("companion payload required");
                if (!Objects.equals(ClassSpecCatalog.companionNamespace(current.specializationId()),
                        request.companion().namespace())) {
                    return Plan.reject("companion namespace does not belong to loadout");
                }
                if (roster.putIfAbsent(request.companion().companionId(), request.companion()) != null) {
                    return Plan.reject("companion already exists");
                }
                mechanics.put("companion.active_id", request.companion().companionId().toString());
            }
            case REMOVE -> {
                if (id == null || roster.remove(id) == null) return Plan.no("companion absent");
                if (id.toString().equals(mechanics.get("companion.active_id"))) {
                    mechanics.remove("companion.active_id");
                }
            }
            case DISMISS -> {
                if (mechanics.remove("companion.active_id") == null) {
                    return Plan.no("companion already dismissed");
                }
            }
            case SET_ACTIVE -> {
                if (id == null || !roster.containsKey(id)) return Plan.reject("unknown companion");
                if (id.toString().equals(mechanics.get("companion.active_id"))) {
                    return Plan.no("companion already active");
                }
                mechanics.put("companion.active_id", id.toString());
            }
            case RENAME -> {
                final CompanionProfile companion = requiredCompanion(roster, id);
                roster.put(id, companion.withName(request.text()));
            }
            case STANCE -> {
                final CompanionProfile companion = requiredCompanion(roster, id);
                roster.put(id, companion.withStance(request.text()));
            }
            case PROGRESS -> {
                final CompanionProfile companion = requiredCompanion(roster, id);
                roster.put(id, companion.withProgress(request.level(), request.experience()));
            }
            case EQUIPMENT -> {
                final CompanionProfile companion = requiredCompanion(roster, id);
                roster.put(id, companion.withEquipment(request.equipment()));
            }
            case STATE -> {
                final CompanionProfile companion = requiredCompanion(roster, id);
                roster.put(id, companion.withPersistentState(request.state()));
            }
            case RESPAWN_AT -> {
                final CompanionProfile companion = requiredCompanion(roster, id);
                roster.put(id, companion.withResummonAt(request.resummonAtEpochMillis()));
            }
        }
        final ClassSpecSection candidate = profile.toBuilder()
                .revision(NumericGuards.nextRevision(profile.revision()))
                .loadout(request.slot(), new ClassLoadout(current.specializationId(), current.status(),
                        current.sealReason(), current.doctrineChoices(), current.mastery(),
                        current.soulbond(), current.favoriteSpells(), current.selectedSpell(),
                        current.capstoneStatus(), roster, mechanics, current.diagnosticNote()))
                .build();
        return Plan.of(withReceipt(candidate, new ProfileOperation(request.operationId(),
                ProfileOperationType.COMPANION_MUTATION, ProfileOperationStatus.COMMITTED,
                request.kind().name(), "0", "none", profile.revision(),
                "companion mutation committed")));
    }

    private static CompanionProfile requiredCompanion(final Map<UUID, CompanionProfile> roster,
                                                       final UUID id) {
        if (id == null || !roster.containsKey(id)) {
            throw new IllegalArgumentException("unknown companion");
        }
        return roster.get(id);
    }

    private Plan planSeal(final ClassSpecSection profile, final SealRequest request) {
        final ClassLoadout current = profile.loadout(request.slot());
        if (current.status() == LoadoutStatus.EMPTY) return Plan.reject("empty loadout cannot be sealed");
        if (current.status() == LoadoutStatus.SEALED) {
            return current.sealReason().equals(request.reason())
                    ? Plan.no("already sealed")
                    : Plan.reject("existing seal requires explicit recovery");
        }
        return Plan.of(profile.toBuilder()
                .revision(NumericGuards.nextRevision(profile.revision()))
                .loadout(request.slot(), current.withStatus(LoadoutStatus.SEALED, request.reason()))
                .activeSlot(profile.activeSlot() == request.slot() ? null : profile.activeSlot())
                .build());
    }

    private Plan planReconcile(final ClassSpecSection profile, final ReconcileRequest request) {
        final EnumMap<LoadoutSlot, ClassLoadout> replacements = new EnumMap<>(LoadoutSlot.class);
        LoadoutSlot active = profile.activeSlot();
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            final ClassLoadout current = profile.loadout(slot);
            if (current.status() == LoadoutStatus.EMPTY
                    || !DarkSpecializationPolicy.isDark(current.specializationId())) continue;
            final GateSnapshot gates = request.gatesBySlot().get(slot);
            if (gates == null) return Plan.reject("missing DARK gate snapshot");
            final SealReason missing = gates.missingReason();
            if (missing != null) {
                if (current.status() == LoadoutStatus.SEALED
                        && !current.sealReason().gateRestorableOnly()) continue;
                if (current.status() == LoadoutStatus.SEALED
                        && current.sealReason().equals(missing)) continue;
                replacements.put(slot, current.withStatus(LoadoutStatus.SEALED, missing));
                if (active == slot) active = null;
            } else if (current.status() == LoadoutStatus.SEALED
                    && current.sealReason().gateRestorableOnly()
                    && gates.authorizesRecovery(current.sealReason())) {
                replacements.put(slot, current.withStatus(LoadoutStatus.INACTIVE, null));
            }
        }
        if (replacements.isEmpty()) return Plan.no("gate state already reconciled");
        final ClassSpecSection.Builder builder = profile.toBuilder()
                .revision(NumericGuards.nextRevision(profile.revision()))
                .activeSlot(active);
        for (final var entry : replacements.entrySet()) builder.loadout(entry.getKey(), entry.getValue());
        return Plan.of(builder.build());
    }

    private static Plan replay(final ProfileOperation operation,
                               final ProfileOperationType expectedType,
                               final String target, final String amount,
                               final String currency, final String label) {
        final ProfileOperation expected = new ProfileOperation(operation.operationId(), expectedType,
                operation.status(), target, amount, currency, operation.startedRevision(),
                operation.detail());
        if (operation.type() != expected.type()
                || !operation.target().equals(expected.target())
                || !operation.amount().equals(expected.amount())
                || !operation.currencyId().equals(expected.currencyId())) {
            return Plan.reject(label + " operation id was reused with different parameters");
        }
        return operation.status() == ProfileOperationStatus.COMMITTED
                ? Plan.no(label + " operation already committed")
                : Plan.reject(label + " operation is pending recovery");
    }

    private static ClassSpecSection withReceipt(final ClassSpecSection profile,
                                                final ProfileOperation receipt) {
        final Map<String, ProfileOperation> operations = new LinkedHashMap<>(profile.operations());
        if (operations.size() >= ClassSpecSection.MAX_OPERATION_RECEIPTS
                && !operations.containsKey(receipt.operationId())) {
            final String oldest = operations.values().stream()
                    .filter(operation -> operation.status() != ProfileOperationStatus.PENDING)
                    .min(Comparator.comparingLong(ProfileOperation::startedRevision)
                            .thenComparing(ProfileOperation::operationId))
                    .map(ProfileOperation::operationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "operation ledger full with pending receipts"));
            operations.remove(oldest);
        }
        operations.put(receipt.operationId(), receipt);
        return profile.toRecoveryBuilder().operations(operations).build();
    }

    private ProfileDiagnostic project(final UUID id, final ClassSpecSection profile) {
        final EnumMap<LoadoutSlot, ProfileDiagnostic.SlotDiagnostic> slots =
                new EnumMap<>(LoadoutSlot.class);
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            final ClassLoadout loadout = profile.loadout(slot);
            slots.put(slot, new ProfileDiagnostic.SlotDiagnostic(text(loadout.specializationId()),
                    loadout.status(), Optional.ofNullable(loadout.sealReason()),
                    loadout.mastery().rank(), loadout.mastery().experience()));
        }
        final String block = store.sessionBlockReason(id).orElseGet(() -> sessions.session(id)
                .filter(session -> session.state() != ProfileSessionRegistry.State.READY)
                .map(ProfileSessionRegistry.Session::detail).orElse(""));
        final String review = profile.status() == ProfileStatus.REVIEW
                ? profile.diagnostics().sessionBlockReason() : "";
        return new ProfileDiagnostic(true, true, profile.schemaVersion(), profile.revision(),
                profile.status(), text(profile.primaryClassId()), profile.classLevel(),
                profile.classExperience(), Optional.ofNullable(profile.activeSlot()),
                profile.secondSpecUnlocked(), slots, text(review),
                text(profile.diagnostics().quarantineReason()),
                text(block.isBlank() ? profile.diagnostics().sessionBlockReason() : block));
    }

    private <T> CompletionStage<T> enqueue(final UUID id,
                                           final Supplier<CompletionStage<T>> operation) {
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        final CompletableFuture<Void> previous;
        synchronized (admissionLock) {
            if (!accepting) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Profile mutation lifecycle stopped"));
            }
            previous = tails.put(id, gate);
        }
        final CompletionStage<Void> start = previous == null
                ? CompletableFuture.completedFuture(null) : previous.handle((v, x) -> null);
        final CompletableFuture<T> result = new CompletableFuture<>();
        start.thenCompose(v -> {
            try {
                return Objects.requireNonNull(operation.get());
            } catch (final Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }).whenComplete((value, failure) -> {
            if (failure == null) result.complete(value);
            else result.completeExceptionally(failure);
            gate.complete(null);
            tails.remove(id, gate);
        });
        return result;
    }

    private static Optional<String> text(final String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static String message(final Throwable failure) {
        Throwable current = failure;
        while (current instanceof java.util.concurrent.CompletionException
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private record Plan(ClassSpecSection candidate, boolean rejected, String detail) {
        static Plan of(final ClassSpecSection profile) {
            return new Plan(Objects.requireNonNull(profile), false, "");
        }

        static Plan no(final String detail) {
            return new Plan(null, false, detail);
        }

        static Plan reject(final String detail) {
            return new Plan(null, true, detail);
        }
    }

    private record SaveCompletion(ClassSpecSectionMutationStore.SaveResult result,
                                  Throwable failure) { }
}
