package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.factions.FactionMembershipAdjustmentRuntime;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionStore.*;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.*;
import static hu.taliann.icesmp.dev.weaver.provider.FactionWeaverProvider.*;

/** Explicit LIVE_GM profile transaction; the native faction outbox owns post-commit role cleanup. */
final class FactionCanonicalActions {
    static final String SET = "faction.set_membership_canonical", REMOVE = "faction.remove_membership_canonical";
    static final String REVISION = "faction.profile_revision", MEMBERSHIP = "faction.canonical_membership";
    static final String PENDING = "faction.profile_effects_pending", CHANGED_AT = "faction.membership_changed_at";
    static final WeaverRevisionScope SCOPE = new WeaverRevisionScope(1, Set.of(REVISION, MEMBERSHIP));
    interface Port {
        CompletionStage<AdjustmentResult> adjust(UUID player, MembershipAdjustment request, Runnable finalAdmission);
        AdjustmentObservation observe(UUID player, MembershipAdjustment request);
        boolean completed(UUID player, MembershipAdjustment request);
    }
    private final Port port;
    private final Map<String, ActionDescriptor> actions;
    FactionCanonicalActions(Port port) {
        this.port = Objects.requireNonNull(port);
        final var parameter = new ActionParameter("value", Component.text("Frakció"), FACTION, ActionParameter.InputKind.CATALOG,
                true, Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalInt.empty(), Optional.of("faction.memberships"), Set.of("faction.membership"));
        actions = Map.of(SET, descriptor(SET, "Tagság kanonikus módosítása", List.of(parameter)),
                REMOVE, descriptor(REMOVE, "Tagság kanonikus megszüntetése", List.of()));
    }
    private static ActionDescriptor descriptor(String id, String label, List<ActionParameter> parameters) {
        return new ActionDescriptor(id, FACET, Component.text(label), RiskLevel.CANONICAL, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.LIVE_GM),
                Set.of(IntegrityImpact.NONE), Set.of(WeaverSubjectKind.PLAYER), parameters, AreaSupport.NONE, Optional.empty(), false,
                Optional.of("A tagsági történet megmarad; a céhes és politikai tisztségek elveszhetnek. Visszaállítás csak új, explicit tagsági tranzakcióval."), 10, SCOPE);
    }
    boolean owns(String id) { return actions.containsKey(id); }
    List<ActionDescriptor> descriptors() { return actions.values().stream().sorted(Comparator.comparing(ActionDescriptor::id)).toList(); }
    Set<String> visible(SubjectSnapshot snapshot) {
        return snapshot.ref() instanceof PlayerRef && snapshot.facts().containsKey(REVISION)
                && "false".equals(text(snapshot, PENDING)) ? actions.keySet() : Set.of();
    }
    static Map<String, WeaverValue> facts(MembershipView view, boolean pending, long now) {
        final Map<String, WeaverValue> result = new HashMap<>(revisionFacts(view.sectionRevision(), view.state().membership(), now));
        result.put(PENDING, scalar(Boolean.toString(pending), now));
        result.put(CHANGED_AT, scalar(Long.toString(Math.max(view.state().joinedAt(), view.state().leftAt())), now));
        return Map.copyOf(result);
    }
    private static Map<String, WeaverValue> revisionFacts(long revision, Optional<FactionType> membership, long now) {
        return Map.of(REVISION, scalar(Long.toString(revision), now), MEMBERSHIP, scalar(name(membership), now));
    }
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid();
        final var descriptor = actions.get(request.actionId());
        if (descriptor == null || !(snapshot.ref() instanceof PlayerRef player) || request.integrityMode() != IntegrityMode.LIVE_GM
                || context.integrityMode() != IntegrityMode.LIVE_GM || request.lifetime() != Lifetime.ONE_SHOT || context.lifetime() != Lifetime.ONE_SHOT)
            throw new WeaverDomainRejection("ACTION_UNAVAILABLE");
        if (!"false".equals(text(snapshot, PENDING))) throw new WeaverDomainRejection("PROFILE_EFFECTS_PENDING");
        if (!snapshot.revisionFingerprint().equals(SCOPE.apply(snapshot).revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        final Optional<FactionType> target = target(request, context.types());
        final var before = membership(text(snapshot, MEMBERSHIP));
        if (before.equals(target)) throw new WeaverDomainRejection("NO_CHANGE");
        final var adjustment = new MembershipAdjustment(UUID.randomUUID(), number(text(snapshot, REVISION)), before, target,
                Math.max(System.currentTimeMillis(), number(text(snapshot, CHANGED_AT))));
        final var afterFacts = revisionFacts(adjustment.expectedRevision() + 1, target, adjustment.occurredAt());
        final String after = fingerprint(snapshot.ref(), afterFacts);
        final var stage = new ExecutionStage("faction.commit_membership", new ProfileOwner(player.playerId()), Map.of(), (execution, payload) -> {
            execution.authority().requireValid();
            try {
                return port.adjust(player.playerId(), adjustment, execution.authority()::requireValid).handle((result, failure) -> {
                    if (failure != null) throw rejection(failure);
                    if (result.after().sectionRevision() != adjustment.expectedRevision() + 1 || !result.after().state().membership().equals(target))
                        throw new WeaverDomainRejection("CONFLICT");
                    return new StageResult(after, afterFacts, Map.of());
                });
            } catch (RuntimeException failure) { throw rejection(failure); }
        }, Optional.empty(), 10000);
        return new PreparedAction(adjustment.operationId(), descriptor, snapshot.ref(), snapshot.revisionFingerprint(), List.of(stage), encode(adjustment),
                (prepared, results, time) -> {
                    if (results.size() != 1 || !results.getFirst().afterFingerprint().equals(after)) throw new WeaverDomainRejection("RESULT_MISMATCH");
                    return receipt(snapshot.ref(), request.actionId(), adjustment, time);
                });
    }
    private Optional<FactionType> target(ActionRequest request, WeaverTypeRegistry types) {
        if (SET.equals(request.actionId())) {
            if (!request.parameters().keySet().equals(Set.of("value"))) throw new WeaverDomainRejection("INVALID_PARAMETERS");
            final var value = request.parameters().get("value"); actions.get(SET).parameters().getFirst().validate(value, types).requireValid();
            final var result = membership((String) value.payload().get("id"));
            if (result.isEmpty()) throw new WeaverDomainRejection("INVALID_PARAMETERS");
            return result;
        }
        if (!REMOVE.equals(request.actionId()) || !request.parameters().isEmpty()) throw new WeaverDomainRejection("INVALID_PARAMETERS");
        return Optional.empty();
    }
    PreparedEffects effects(ProviderContext context) {
        context.authority().requireValid();
        return new PreparedEffects(WeaverEffectIntent.none(), (action, results, receipt, sequence) -> WeaverEffectCommit.none());
    }
    RecoveryAssessment assess(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        if (!(snapshot.ref() instanceof PlayerRef player) || !owns(operation.request().actionId())
                || operation.request().integrityMode() != IntegrityMode.LIVE_GM || operation.request().lifetime() != Lifetime.ONE_SHOT)
            return conflict("CANONICAL_PLAN_UNAVAILABLE");
        final MembershipAdjustment adjustment;
        try {
            adjustment = decode(operation.recoveryPayload());
            if (!target(operation.request(), context.types()).equals(adjustment.target())) return conflict("CANONICAL_PLAN_MISMATCH");
        } catch (RuntimeException invalid) { return conflict("CANONICAL_PLAN_UNAVAILABLE"); }
        final var expected = receipt(snapshot.ref(), operation.request().actionId(), adjustment,
                operation.receipt().map(WeaverReceipt::createdAt).orElseGet(() -> Math.max(System.currentTimeMillis(), operation.preparedAt())));
        if (!adjustment.operationId().equals(operation.operationId()) || !expected.beforeFingerprint().equals(operation.beforeFingerprint()))
            return conflict("CANONICAL_PLAN_MISMATCH");
        final AdjustmentObservation observed;
        final boolean completed;
        try { observed = port.observe(player.playerId(), adjustment); completed = observed == AdjustmentObservation.APPLIED && port.completed(player.playerId(), adjustment); }
        catch (RuntimeException failure) { throw rejection(failure); }
        if (observed == AdjustmentObservation.BEFORE) return new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "PROFILE_BEFORE");
        if (observed != AdjustmentObservation.APPLIED) return conflict("PROFILE_REVISION_CONFLICT");
        if (!completed) throw new WeaverDomainRejection("PROFILE_EFFECTS_PENDING");
        if (!snapshot.revisionFingerprint().equals(expected.afterFingerprint())) return conflict("PROFILE_SNAPSHOT_DRIFT");
        if (operation.receipt().isPresent() && !operation.receipt().get().equals(expected)) return conflict("RECEIPT_MISMATCH");
        return new RecoveryAssessment(ObservedOperationState.APPLIED, false, Optional.of(expected), "PROFILE_AND_EFFECTS_OBSERVED");
    }
    private static RecoveryAssessment conflict(String code) { return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), code); }
    private static WeaverReceipt receipt(SubjectRef subject, String action, MembershipAdjustment request, long createdAt) {
        final var before = revisionFacts(request.expectedRevision(), request.expectedMembership(), request.occurredAt());
        final var after = revisionFacts(request.expectedRevision() + 1, request.target(), request.occurredAt());
        return new WeaverReceipt(request.operationId(), request.operationId(), "faction", action, subject, RiskLevel.CANONICAL, Lifetime.ONE_SHOT,
                IntegrityMode.LIVE_GM, fingerprint(subject, before), fingerprint(subject, after), before, after, Optional.empty(), createdAt, ReceiptStatus.COMMITTED);
    }
    private static String fingerprint(SubjectRef subject, Map<String, WeaverValue> facts) { return SCOPE.apply(new SubjectSnapshot(subject, 0, "profile", facts)).revisionFingerprint(); }
    static OperationRecoveryPayload encode(MembershipAdjustment request) {
        return new OperationRecoveryPayload(1, Map.of("kind", "faction_membership", "operation", request.operationId().toString(), "expected_revision", request.expectedRevision(),
                "expected_membership", name(request.expectedMembership()), "target", name(request.target()), "occurred_at", request.occurredAt()));
    }
    static MembershipAdjustment decode(OperationRecoveryPayload payload) {
        // The journal adds its own reservation envelope, validated by WeaverOperationRecord.
        // It is outside this provider's plan schema; all other unknown fields remain invalid.
        final var f = new HashMap<>(payload.fields());
        f.remove(WeaverOperationScope.RESERVATIONS);
        if (payload.schemaVersion() != 1 || !f.keySet().equals(Set.of("kind", "operation", "expected_revision", "expected_membership", "target", "occurred_at"))
                || !"faction_membership".equals(f.get("kind"))) throw new WeaverDomainRejection("INVALID_RECOVERY_PLAN");
        return new MembershipAdjustment(UUID.fromString((String) f.get("operation")), number(f.get("expected_revision").toString()),
                membership((String) f.get("expected_membership")), membership((String) f.get("target")), number(f.get("occurred_at").toString()));
    }
    private static Optional<FactionType> membership(String value) {
        if ("guest".equals(value)) return Optional.empty();
        try { return Optional.of(FactionType.valueOf(value.toUpperCase(Locale.ROOT))); }
        catch (RuntimeException invalid) { throw new WeaverDomainRejection("INVALID_FACTION"); }
    }
    private static String name(Optional<FactionType> membership) { return membership.map(f -> f.name().toLowerCase(Locale.ROOT)).orElse("guest"); }
    private static String text(SubjectSnapshot snapshot, String key) {
        final var value = snapshot.facts().get(key);
        if (value == null || !(value.payload().get("value") instanceof String text)) throw new WeaverDomainRejection("REVISION_FIELD_UNAVAILABLE");
        return text;
    }
    private static long number(String value) {
        try { final long result = Long.parseLong(value); if (result < 0) throw new NumberFormatException(); return result; }
        catch (NumberFormatException invalid) { throw new WeaverDomainRejection("INVALID_REVISION"); }
    }
    static RuntimeException rejection(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        if (failure instanceof AdjustmentRejected rejected) return new WeaverDomainRejection(rejected.code());
        if (failure instanceof hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.ProfileNotReadyException) return new WeaverDomainRejection("PROFILE_UNAVAILABLE");
        if (failure instanceof FactionMembershipAdjustmentRuntime.Rejected rejected) return new WeaverDomainRejection(rejected.code());
        if (failure instanceof hu.taliann.icesmp.playerprofile.persistence.PlayerProfileRepositoryException.RevisionConflict) return new WeaverDomainRejection("CONFLICT");
        if (failure instanceof RuntimeException runtime) return runtime;
        return new CompletionException(failure);
    }
}
