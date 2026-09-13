package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.territory.TerritoryRevision;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import static hu.taliann.icesmp.dev.weaver.provider.TerritoryWeaverProvider.*;

/** Validates on the world owner; the existing journal atomically publishes projection and influence. */
final class TerritoryProjectionActions {
    static final String APPLY = "territory.project_rule", SEVER = "territory.sever_projection", CLEAR = "territory.clear_projections";
    private static final WeaverTypeId PROJECTION_REF = WeaverTypeId.parse("weaver:projection_ref@1");
    private final TerritoryRuntimeProjectionSource source;
    private final Function<SubjectRef, Map<String, WeaverValue>> capture;
    private final Supplier<Map<String, Territory>> territories;
    private final Map<String, ActionDescriptor> actions;
    TerritoryProjectionActions(TerritoryRuntimeProjectionSource source, Function<SubjectRef, Map<String, WeaverValue>> capture, Supplier<Map<String, Territory>> territories) {
        this.source = source; this.capture = capture; this.territories = territories;
        actions = Map.of(APPLY, descriptor(APPLY, "Védelmi szabály rávetítése", Set.of(Lifetime.SESSION, Lifetime.PERSISTENT),
                        List.of(parameter("territory", TERRITORY, "territory.zones", "territory.zone"), parameter("value", RULE, "territory.rules", "territory.rule")), true),
                CLEAR, descriptor(CLEAR, "Világ területi rávetítéseinek elvágása", Set.of(Lifetime.ONE_SHOT), List.of(), false),
                SEVER, descriptor(SEVER, "Területi rávetítés elvágása", Set.of(Lifetime.ONE_SHOT), List.of(parameter("value", PROJECTION_REF, null, "territory.projection")), false));
    }
    private static ActionParameter parameter(String id, WeaverTypeId type, String catalog, String capability) {
        return new ActionParameter(id, Component.text(id.equals("territory") ? "Kanonikus terület" : "Szabály / érték"), type,
                catalog == null ? ActionParameter.InputKind.THREAD : ActionParameter.InputKind.CATALOG, true, Optional.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalInt.empty(), Optional.ofNullable(catalog), Set.of(capability));
    }
    private static ActionDescriptor descriptor(String id, String label, Set<Lifetime> lifetimes, List<ActionParameter> parameters, boolean undoable) {
        return new ActionDescriptor(id, FACET, Component.text(label), RiskLevel.MUTATING, lifetimes, Set.of(IntegrityMode.SANDBOX, IntegrityMode.LIVE_GM),
                Set.of(IntegrityImpact.TAINT_SUBJECT), Set.of(WeaverSubjectKind.WORLD), parameters, AreaSupport.NONE, Optional.empty(), undoable,
                undoable ? Optional.empty() : Optional.of("Az elvágás megőrzi a történetet és a karantént; a rávetítés új művelettel pótolható."), 2, SCOPE);
    }
    List<ActionDescriptor> descriptors() { return actions.values().stream().sorted(Comparator.comparing(ActionDescriptor::id)).toList(); }
    List<ProjectionConsumerDescriptor> consumers() {
        return List.of(new ProjectionConsumerDescriptor(TerritoryRuntimeProjectionSource.CONSUMER, "territory",
                "hu.taliann.icesmp.managers.TerritoryProtectionService#evaluateAt", Set.of(APPLY), Set.of(WeaverSubjectKind.WORLD),
                Map.of(TerritoryRuntimeProjectionSource.FIELD, TerritoryRuntimeProjectionSource.TYPE),
                Set.of("territory.storage", "territory.ownership", "territory.claims", "territory.tax", "territory.history", "territory.reward_identity")));
    }
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid(); final var descriptor = actions.get(request.actionId());
        if (descriptor == null || !(snapshot.ref() instanceof WorldRef) || context.lifetime() != request.lifetime()
                || context.integrityMode() != request.integrityMode() || !descriptor.lifetimes().contains(request.lifetime())
                || !descriptor.integrityModes().contains(request.integrityMode())) throw new WeaverDomainRejection("ACTION_UNAVAILABLE");
        if (!descriptor.parameters().stream().map(ActionParameter::id).collect(java.util.stream.Collectors.toSet()).equals(request.parameters().keySet())) throw new WeaverDomainRejection("INVALID_PARAMETERS");
        descriptor.parameters().forEach(p -> p.validate(request.parameters().get(p.id()), context.types()).requireValid());
        if (!SCOPE.apply(snapshot).revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        final var before = source.active(snapshot.ref()); final String expected = WeaverProjectionFingerprint.of(before);
        if (!expected.equals(text(snapshot, PROJECTIONS))) throw new WeaverDomainRejection("CONFLICT");
        final UUID operation = UUID.randomUUID(); final long now = System.currentTimeMillis();
        final Map<String, Object> fields = new HashMap<>(); final Set<UUID> removed = new HashSet<>(); final var after = new ArrayList<>(before);
        fields.put("kind", "territory_projection"); fields.put("before", expected);
        if (APPLY.equals(request.actionId())) {
            final var zone = territories.get().get(request.parameters().get("territory").payload().get("id"));
            if (zone == null || !zone.world().equals(text(snapshot, WORLD))) throw new WeaverDomainRejection("TERRITORY_WORLD_MISMATCH");
            final String[] rule = ((String) request.parameters().get("value").payload().get("id")).split("/");
            final Map<String, Object> value = Map.of("territory", zone.id(), "canonical", TerritoryRevision.fingerprint(zone),
                    "rule", rule[0].toUpperCase(Locale.ROOT), "overlay", rule[1].toUpperCase(Locale.ROOT));
            TerritoryRuntimeProjectionSource.codec().validate(value).requireValid(); fields.put("value", value);
            if ("ALLOW".equals(value.get("overlay")) && request.integrityMode() != IntegrityMode.LIVE_GM) throw new WeaverDomainRejection("ALLOW_REQUIRES_LIVE_GM");
            after.add(projection(operation, before.stream().mapToLong(WeaverProjection::sequence).max().orElse(0) + 1, context.authority().actor(), request, snapshot, value, now));
        } else {
            if (CLEAR.equals(request.actionId())) before.forEach(p -> removed.add(p.projectionId()));
            else {
                final var value = request.parameters().get("value");
                if (!"territory".equals(value.payload().get("provider"))) throw new WeaverDomainRejection("FOREIGN_PROJECTION");
                removed.add(UUID.fromString((String) value.payload().get("id")));
            }
            if (removed.isEmpty() || !before.stream().map(WeaverProjection::projectionId).collect(java.util.stream.Collectors.toSet()).containsAll(removed)) throw new WeaverDomainRejection("CONFLICT");
            after.removeIf(p -> removed.contains(p.projectionId()));
            if (request.integrityMode() != IntegrityMode.LIVE_GM) {
                final var oldRules = effective(before); final var newRules = effective(after);
                for (final var entry : newRules.entrySet()) if (entry.getValue().equals("ALLOW") && !entry.getValue().equals(oldRules.get(entry.getKey())))
                    throw new WeaverDomainRejection("ALLOW_REQUIRES_LIVE_GM");
            }
        }
        fields.put("removed", removed.stream().map(UUID::toString).sorted().toList());
        final Map<String, WeaverValue> beforeFacts = new HashMap<>(); SCOPE.fields().forEach(f -> beforeFacts.put(f, snapshot.facts().get(f)));
        final var afterFacts = new HashMap<>(beforeFacts); afterFacts.put(PROJECTIONS, scalar(WeaverProjectionFingerprint.of(after), now));
        final String afterFingerprint = SCOPE.apply(new SubjectSnapshot(snapshot.ref(), now, "pending", afterFacts)).revisionFingerprint();
        final var stage = new ExecutionStage("territory.validate_projection", new GlobalOwner(), Map.of(), (execution, payload) -> {
            execution.authority().requireValid();
            final var fresh = SCOPE.apply(new SubjectSnapshot(snapshot.ref(), System.currentTimeMillis(), "owner", capture.apply(snapshot.ref())));
            if (!fresh.revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
            return CompletableFuture.completedFuture(new StageResult(afterFingerprint, afterFacts, Map.of()));
        }, Optional.empty(), 5000);
        final var ref = new WeaverValue(PROJECTION_REF, Map.of("id", operation.toString(), "provider", "territory"), "territory", FACET, Set.of("territory.projection"), now);
        return new PreparedAction(operation, descriptor, snapshot.ref(), snapshot.revisionFingerprint(), List.of(stage), new OperationRecoveryPayload(1, fields),
                (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), operation, "territory", descriptor.id(), snapshot.ref(), descriptor.risk(), request.lifetime(), request.integrityMode(),
                        snapshot.revisionFingerprint(), afterFingerprint, beforeFacts, afterFacts,
                        APPLY.equals(request.actionId()) ? Optional.of(new UndoSpec(SEVER, afterFingerprint, Map.of("value", ref))) : Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
    @SuppressWarnings("unchecked")
    PreparedEffects effects(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request, PreparedAction prepared) {
        context.authority().requireValid(); final UUID actor = context.authority().actor(); final var fields = prepared.recoveryPayload().fields();
        final String expected = (String) fields.get("before");
        final var removed = ((List<?>) fields.get("removed")).stream().map(v -> UUID.fromString((String) v)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new PreparedEffects(WeaverEffectIntent.none(), (action, results, receipt, sequence) -> new WeaverEffectCommit(
                APPLY.equals(request.actionId()) ? List.of(projection(prepared.operationId(), sequence, actor, request, snapshot, (Map<String, Object>) fields.get("value"), receipt.createdAt())) : List.of(),
                removed, List.of(), Optional.empty(), Map.of(snapshot.ref(), expected)));
    }
    private static WeaverProjection projection(UUID operation, long sequence, UUID actor, ActionRequest request, SubjectSnapshot snapshot, Map<String, Object> fields, long now) {
        final var value = new WeaverValue(TerritoryRuntimeProjectionSource.TYPE, fields, "territory", FACET, Set.of("territory.protection"), now);
        return new WeaverProjection(operation, sequence, "territory", request.actionId(), snapshot.ref(), request.lifetime(),
                new DeveloperInfluence(operation, request.integrityMode(), request.actionId(), actor, now), Map.of(TerritoryRuntimeProjectionSource.FIELD, value),
                snapshot.revisionFingerprint(), now, OptionalLong.empty());
    }
    PreparedAction undo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) {
        context.authority().requireValid(); final var undo = receipt.undo().orElseThrow(() -> new WeaverDomainRejection("UNDO_UNAVAILABLE"));
        if (!"territory".equals(receipt.providerId()) || !SEVER.equals(undo.actionId()) || !receipt.subject().equals(snapshot.ref())
                || !undo.expectedCurrentFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        return prepare(context, snapshot, new ActionRequest(SEVER, undo.parameters(), context.lifetime(), context.integrityMode()));
    }
    RecoveryAssessment assess(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        if (!actions.containsKey(operation.request().actionId()) || operation.recoveryPayload().schemaVersion() != 1
                || !"territory_projection".equals(operation.recoveryPayload().fields().get("kind"))) return conflict("PROJECTION_PLAN_UNAVAILABLE");
        if (operation.status() == OperationStatus.PREPARED) return new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "NO_PROJECTION_PUBLICATION");
        if (operation.receipt().isPresent() && snapshot.revisionFingerprint().equals(operation.receipt().get().afterFingerprint()))
            return new RecoveryAssessment(ObservedOperationState.APPLIED, false, operation.receipt(), "DURABLE_PROJECTION_OBSERVED");
        return conflict("PROJECTION_DRIFT");
    }
    private static RecoveryAssessment conflict(String code) { return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), code); }
    private static Map<List<String>, String> effective(List<WeaverProjection> projections) {
        final Map<List<String>, String> result = new HashMap<>();
        projections.stream().sorted(Comparator.comparingLong(WeaverProjection::sequence)).forEach(p -> {
            final var fields = p.values().get(TerritoryRuntimeProjectionSource.FIELD).payload();
            result.put(List.of((String) fields.get("territory"), (String) fields.get("rule")), (String) fields.get("overlay"));
        });
        return Map.copyOf(result);
    }
}
