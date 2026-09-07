package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.pve.MobRuntimeControlLedger;
import hu.taliann.icesmp.pve.MobRuntimeControlLedger.*;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import static hu.taliann.icesmp.dev.weaver.provider.PvEWeaverProvider.*;

/** One-shot native controls use observed operation evidence, never cast replay or cooldown restoration. */
final class PvERuntimeActions {
    static final String FORCE = "pve.force_ability", REFRESH = "pve.refresh_runtime";
    static final String REVISION = "pve.control_revision", OBSERVED = "pve.control_observations";
    static final WeaverTypeId OBSERVATIONS = WeaverTypeId.parse("icesmp:pve_runtime_observations@1");
    static final WeaverRevisionScope SCOPE = new WeaverRevisionScope(1, Set.of(PvEProjectionActions.CANONICAL_REVISION, PvEProjectionActions.PROJECTION_REVISION, REVISION));
    interface Port { Accepted execute(EntityRef subject, Request request, Runnable finalAdmission); }
    private final Port port;
    private final Function<SubjectRef, Map<String, WeaverValue>> snapshots;
    private final Map<String, ActionDescriptor> actions;
    PvERuntimeActions(WeaverTypeRegistry types, Port port, Function<SubjectRef, Map<String, WeaverValue>> snapshots) {
        this.port = Objects.requireNonNull(port); this.snapshots = Objects.requireNonNull(snapshots);
        types.register(new WeaverTypeCodec() {
            public WeaverTypeId type() { return OBSERVATIONS; }
            public ValidationResult validate(Map<String, Object> payload) {
                try { decodeView(payload); return ValidationResult.accepted(); }
                catch (RuntimeException invalid) { return ValidationResult.rejected("INVALID_RUNTIME_OBSERVATION"); }
            }
            public byte[] canonicalBytes(Map<String, Object> payload) { validate(payload).requireValid(); return CanonicalValueBytes.encode(payload); }
        });
        final var ability = new ActionParameter("value", Component.text("Képesség"), ABILITY, ActionParameter.InputKind.CATALOG, true, Optional.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalInt.empty(), Optional.of("pve.abilities"), Set.of("pve.ability"));
        actions = Map.of(FORCE, descriptor(FORCE, "Képesség indítása", List.of(ability)), REFRESH, descriptor(REFRESH, "Combat runtime frissítése", List.of()));
    }
    private static ActionDescriptor descriptor(String id, String label, List<ActionParameter> parameters) {
        return new ActionDescriptor(id, FACET, Component.text(label), RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX, IntegrityMode.LIVE_GM),
                Set.of(IntegrityImpact.TAINT_SUBJECT), Set.of(WeaverSubjectKind.ENTITY), parameters, AreaSupport.NONE, Optional.empty(), false,
                Optional.of("A cast és következményei nem vonhatók vissza. A cooldown és combat történet megmarad."), 1, SCOPE);
    }
    boolean owns(String id) { return actions.containsKey(id); }
    List<ActionDescriptor> descriptors() { return actions.values().stream().sorted(Comparator.comparing(ActionDescriptor::id)).toList(); }
    Set<String> visible(SubjectSnapshot snapshot) { return snapshot.ref() instanceof EntityRef && snapshot.facts().containsKey(REVISION) ? actions.keySet() : Set.of(); }
    static Map<String, WeaverValue> facts(View view, long now) {
        return Map.of(REVISION, scalar("text", view.stamp(), now), OBSERVED,
                new WeaverValue(OBSERVATIONS, encodeView(view), "pve", FACET, Set.of(), now));
    }
    private static Map<String, Object> encodeView(View view) {
        return Map.of("stamp", view.stamp(), "accepted", view.accepted().values().stream().sorted(Comparator.comparing(a -> a.request().operationId()))
                .map(a -> Map.<String, Object>of("operation", a.request().operationId().toString(), "kind", a.request().kind().name(), "ability", a.request().abilityId(),
                        "before", a.request().expectedStamp(), "after", a.afterStamp(), "accepted_at", a.acceptedAt())).toList());
    }
    private static View decodeView(Map<String, Object> fields) {
        if (!fields.keySet().equals(Set.of("stamp", "accepted")) || !(fields.get("accepted") instanceof List<?> rows) || rows.size() > 32)
            throw new IllegalArgumentException("Observation schema");
        final Map<UUID, Accepted> accepted = new HashMap<>();
        for (Object value : rows) {
            final var row = WeaverJournalCodec.map(value);
            if (!row.keySet().equals(Set.of("operation", "kind", "ability", "before", "after", "accepted_at"))) throw new IllegalArgumentException("Acceptance schema");
            final UUID operation = UUID.fromString((String) row.get("operation"));
            final var request = new Request(operation, Kind.valueOf((String) row.get("kind")), (String) row.get("ability"), (String) row.get("before"));
            if (accepted.putIfAbsent(operation, new Accepted(request, (String) row.get("after"), integer(row.get("accepted_at")))) != null)
                throw new IllegalArgumentException("Duplicate acceptance");
        }
        return new View((String) fields.get("stamp"), accepted);
    }
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid();
        final var descriptor = actions.get(request.actionId());
        if (descriptor == null || !(snapshot.ref() instanceof EntityRef entity) || request.lifetime() != Lifetime.ONE_SHOT || context.lifetime() != Lifetime.ONE_SHOT
                || request.integrityMode() != context.integrityMode()) throw new WeaverDomainRejection("ACTION_UNAVAILABLE");
        final String ability = ability(request, context.types());
        if (!SCOPE.apply(snapshot).revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        final var nativeRequest = new Request(UUID.randomUUID(), kind(request.actionId()), ability, text(snapshot, REVISION));
        final long capturedAt = System.currentTimeMillis();
        final Map<String, WeaverValue> before = revisionFacts(snapshot, nativeRequest.expectedStamp(), capturedAt);
        final var payload = new OperationRecoveryPayload(1, Map.of("kind", "native_control", "operation", nativeRequest.operationId().toString(), "control", nativeRequest.kind().name(),
                "ability", ability, "canonical", text(snapshot, PvEProjectionActions.CANONICAL_REVISION), "projection", text(snapshot, PvEProjectionActions.PROJECTION_REVISION),
                "runtime", nativeRequest.expectedStamp(), "captured_at", capturedAt));
        final var stage = new ExecutionStage("pve.native_control", new EntityOwner(entity.entityId()), Map.of(), (execution, parameters) -> {
            execution.authority().requireValid();
            final var fresh = SCOPE.apply(new SubjectSnapshot(entity, System.currentTimeMillis(), "owner", snapshots.apply(entity)));
            if (!fresh.revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
            final Accepted accepted;
            try { accepted = port.execute(entity, nativeRequest, execution.authority()::requireValid); }
            catch (MobRuntimeControlLedger.Rejected rejected) { throw new WeaverDomainRejection(rejected.code()); }
            if (!accepted.request().equals(nativeRequest)) throw new WeaverDomainRejection("CONTROL_RESULT_MISMATCH");
            final var after = revisionFacts(snapshot, accepted.afterStamp(), capturedAt);
            return CompletableFuture.completedFuture(new StageResult(fingerprint(entity, after), after, Map.of()));
        }, Optional.empty(), 5000);
        return new PreparedAction(nativeRequest.operationId(), descriptor, entity, snapshot.revisionFingerprint(), List.of(stage), payload,
                (prepared, results, time) -> {
                    if (results.size() != 1) throw new WeaverDomainRejection("CONTROL_RESULT_MISMATCH");
                    return receipt(entity, request, nativeRequest.operationId(), before, results.getFirst().facts(), time);
                });
    }
    private String ability(ActionRequest request, WeaverTypeRegistry types) {
        if (FORCE.equals(request.actionId())) {
            if (!request.parameters().keySet().equals(Set.of("value"))) throw new WeaverDomainRejection("INVALID_PARAMETERS");
            actions.get(FORCE).parameters().getFirst().validate(request.parameters().get("value"), types).requireValid();
            return PvEMobProjectionSource.id(request.parameters().get("value"));
        }
        if (!REFRESH.equals(request.actionId()) || !request.parameters().isEmpty()) throw new WeaverDomainRejection("INVALID_PARAMETERS");
        return "";
    }
    PreparedEffects effects(ProviderContext context) {
        context.authority().requireValid();
        return new PreparedEffects(WeaverEffectIntent.none(), (action, results, receipt, sequence) -> WeaverEffectCommit.none());
    }
    RecoveryAssessment assess(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        if (!(snapshot.ref() instanceof EntityRef) || !owns(operation.request().actionId()) || operation.request().lifetime() != Lifetime.ONE_SHOT)
            return conflict("CONTROL_PLAN_UNAVAILABLE");
        try {
            final var fields = new HashMap<>(operation.recoveryPayload().fields()); fields.remove(WeaverOperationScope.RESERVATIONS);
            if (operation.recoveryPayload().schemaVersion() != 1 || !fields.keySet().equals(Set.of("kind", "operation", "control", "ability", "canonical", "projection", "runtime", "captured_at"))
                    || !"native_control".equals(fields.get("kind"))) return conflict("CONTROL_PLAN_UNAVAILABLE");
            final var request = new Request(UUID.fromString((String) fields.get("operation")), Kind.valueOf((String) fields.get("control")), (String) fields.get("ability"), (String) fields.get("runtime"));
            final var expectedParameters = request.kind() == Kind.REFRESH ? Set.of() : Set.of("value");
            if (!request.operationId().equals(operation.operationId()) || request.kind() != kind(operation.request().actionId())
                    || !operation.request().parameters().keySet().equals(expectedParameters)
                    || request.kind() == Kind.FORCE_ABILITY && !request.abilityId().equals(PvEMobProjectionSource.id(operation.request().parameters().get("value")))) return conflict("CONTROL_PLAN_MISMATCH");
            final long capturedAt = integer(fields.get("captured_at"));
            final Map<String, WeaverValue> before = Map.of(PvEProjectionActions.CANONICAL_REVISION, scalar("text", fields.get("canonical"), capturedAt),
                    PvEProjectionActions.PROJECTION_REVISION, scalar("text", fields.get("projection"), capturedAt), REVISION, scalar("text", request.expectedStamp(), capturedAt));
            if (!fingerprint(snapshot.ref(), before).equals(operation.beforeFingerprint())) return conflict("CONTROL_PLAN_MISMATCH");
            final var observed = snapshot.facts().get(OBSERVED);
            if (observed == null || !observed.type().equals(OBSERVATIONS)) return conflict("RUNTIME_EVIDENCE_UNAVAILABLE");
            final var view = decodeView(observed.payload()); final Accepted accepted = view.accepted().get(operation.operationId());
            if (accepted == null) return view.stamp().equals(request.expectedStamp()) && snapshot.revisionFingerprint().equals(operation.beforeFingerprint())
                    ? new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "NATIVE_RUNTIME_UNTOUCHED") : conflict("RUNTIME_OUTCOME_UNKNOWN");
            if (!accepted.request().equals(request)) return conflict("CONTROL_ACCEPTANCE_MISMATCH");
            final var after = new HashMap<>(before); after.put(REVISION, scalar("text", accepted.afterStamp(), capturedAt));
            final var receipt = receipt(snapshot.ref(), operation.request(), operation.operationId(), before, after,
                    operation.receipt().map(WeaverReceipt::createdAt).orElseGet(() -> Math.max(System.currentTimeMillis(), operation.preparedAt())));
            if (operation.receipt().isPresent() && !operation.receipt().get().equals(receipt)) return conflict("CONTROL_RECEIPT_MISMATCH");
            return new RecoveryAssessment(ObservedOperationState.APPLIED, false, Optional.of(receipt), "NATIVE_CONTROL_ACCEPTED");
        } catch (RuntimeException invalid) { return conflict("CONTROL_EVIDENCE_INVALID"); }
    }
    private static Kind kind(String action) { return FORCE.equals(action) ? Kind.FORCE_ABILITY : Kind.REFRESH; }
    private static RecoveryAssessment conflict(String code) { return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), code); }
    private static Map<String, WeaverValue> revisionFacts(SubjectSnapshot snapshot, String stamp, long time) {
        return Map.of(PvEProjectionActions.CANONICAL_REVISION, scalar("text", text(snapshot, PvEProjectionActions.CANONICAL_REVISION), time),
                PvEProjectionActions.PROJECTION_REVISION, scalar("text", text(snapshot, PvEProjectionActions.PROJECTION_REVISION), time), REVISION, scalar("text", stamp, time));
    }
    private static String fingerprint(SubjectRef subject, Map<String, WeaverValue> facts) { return SCOPE.apply(new SubjectSnapshot(subject, 0, "runtime", facts)).revisionFingerprint(); }
    private static WeaverReceipt receipt(SubjectRef subject, ActionRequest request, UUID operation, Map<String, WeaverValue> before, Map<String, WeaverValue> after, long time) {
        return new WeaverReceipt(operation, operation, "pve", request.actionId(), subject, RiskLevel.MUTATING, request.lifetime(), request.integrityMode(),
                fingerprint(subject, before), fingerprint(subject, after), before, after, Optional.empty(), time, ReceiptStatus.COMMITTED);
    }
    private static String text(SubjectSnapshot snapshot, String key) {
        final var value = snapshot.facts().get(key);
        if (value == null || !(value.payload().get("value") instanceof String text)) throw new WeaverDomainRejection("RUNTIME_UNAVAILABLE");
        return text;
    }
    private static long integer(Object value) {
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 1) throw new IllegalArgumentException("Observation timestamp");
        return ((Number) value).longValue();
    }
}
