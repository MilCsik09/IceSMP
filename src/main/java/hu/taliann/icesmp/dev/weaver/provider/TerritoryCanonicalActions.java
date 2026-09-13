package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.territory.*;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import static hu.taliann.icesmp.dev.weaver.provider.TerritoryWeaverProvider.*;

/** Durable PREPARED precedes the native storage transaction; Undo is a new conditional native receipt. */
final class TerritoryCanonicalActions {
    static final String RENAME = "territory.rename_canonical", TYPE = "territory.set_type_canonical",
            OWNER = "territory.set_owner_canonical", Y = "territory.set_y_bounds_canonical";
    static final WeaverTypeId ZONE_TYPE = WeaverTypeId.parse("icesmp:territory_type@1"), ZONE_OWNER = WeaverTypeId.parse("icesmp:territory_owner_ref@1");
    private final TerritoryManager manager;
    private final Supplier<Map<String, Territory>> territories;
    private final Function<SubjectRef, Map<String, WeaverValue>> capture;
    private final Map<String, ActionDescriptor> actions;
    TerritoryCanonicalActions(TerritoryManager manager, Supplier<Map<String, Territory>> territories, Function<SubjectRef, Map<String, WeaverValue>> capture) {
        this.manager = manager; this.territories = territories; this.capture = capture;
        actions = Map.of(RENAME, descriptor(RENAME, "Terület átnevezése", List.of(textParameter())),
                TYPE, descriptor(TYPE, "Területtípus módosítása", List.of(catalogParameter("value", ZONE_TYPE, "territory.types", "territory.type"))),
                OWNER, descriptor(OWNER, "Terület tulajdonosának módosítása", List.of(catalogParameter("value", ZONE_OWNER, "territory.owners", "territory.owner"))),
                Y, descriptor(Y, "Terület magassághatárainak módosítása", List.of(integerParameter("minimum"), integerParameter("maximum"))));
    }
    private static ActionParameter catalogParameter(String id, WeaverTypeId type, String catalog, String capability) {
        return new ActionParameter(id, Component.text(id), type, ActionParameter.InputKind.CATALOG, true, Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalInt.empty(), Optional.of(catalog), Set.of(capability));
    }
    private static ActionParameter textParameter() {
        return new ActionParameter("value", Component.text("Név"), WeaverTypeId.parse("weaver:text@1"), ActionParameter.InputKind.TEXT, true,
                Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalInt.of(128), Optional.empty(), Set.of());
    }
    private static ActionParameter integerParameter(String id) {
        return new ActionParameter(id, Component.text(id), WeaverTypeId.parse("weaver:int@1"), ActionParameter.InputKind.INTEGER, true,
                Optional.empty(), OptionalDouble.of(Integer.MIN_VALUE), OptionalDouble.of(Integer.MAX_VALUE), OptionalInt.empty(), Optional.empty(), Set.of());
    }
    private static ActionDescriptor descriptor(String id, String label, List<ActionParameter> fields) {
        final var parameters = new ArrayList<ActionParameter>(); parameters.add(catalogParameter("territory", TERRITORY, "territory.zones", "territory.zone")); parameters.addAll(fields);
        return new ActionDescriptor(id, FACET, Component.text(label), RiskLevel.CANONICAL, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.LIVE_GM),
                Set.of(IntegrityImpact.NONE), Set.of(WeaverSubjectKind.WORLD), parameters, AreaSupport.NONE, Optional.empty(), true, Optional.empty(), 10, SCOPE);
    }
    boolean owns(String id) { return actions.containsKey(id); }
    List<ActionDescriptor> descriptors() { return actions.values().stream().sorted(Comparator.comparing(ActionDescriptor::id)).toList(); }
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid();
        if (!owns(request.actionId()) || !(snapshot.ref() instanceof WorldRef) || request.lifetime() != Lifetime.ONE_SHOT || context.lifetime() != Lifetime.ONE_SHOT
                || request.integrityMode() != IntegrityMode.LIVE_GM || context.integrityMode() != IntegrityMode.LIVE_GM) throw new WeaverDomainRejection("ACTION_UNAVAILABLE");
        final var descriptor = actions.get(request.actionId()); validate(request.actionId(), request.parameters(), context.types());
        if (!SCOPE.apply(snapshot).revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        final var zones = territories.get(); final Territory before = zones.get(request.parameters().get("territory").payload().get("id"));
        final String world = text(snapshot, WORLD);
        if (before == null || !before.world().equals(world)) throw new WeaverDomainRejection("TERRITORY_WORLD_MISMATCH");
        if (!canonical(zones.values(), world).equals(text(snapshot, CANONICAL))) throw new WeaverDomainRejection("CONFLICT");
        final var adjustment = adjustment(request.actionId(), request.parameters()); final Territory after = adjustment.apply(before);
        if (before.equals(after)) throw new WeaverDomainRejection("NO_CHANGE");
        final var reverse = reverse(request.actionId(), before, request.parameters().get("territory"), System.currentTimeMillis());
        validate(request.actionId(), reverse, context.types()); adjustment(request.actionId(), reverse);
        final Map<String, WeaverValue> beforeFacts = scoped(snapshot); final var afterFacts = new HashMap<>(beforeFacts);
        final var afterZones = new ArrayList<>(zones.values()); afterZones.remove(before); afterZones.add(after);
        final long now = System.currentTimeMillis(); afterFacts.put(CANONICAL, scalar(canonical(afterZones, world), now));
        final UUID operation = UUID.randomUUID(); final String beforeNative = TerritoryRevision.fingerprint(before), afterNative = TerritoryRevision.fingerprint(after);
        final var payload = encode(operation, before.id(), beforeNative, afterNative, beforeFacts, afterFacts, reverse, now);
        final String afterFingerprint = fingerprint(snapshot.ref(), afterFacts);
        final var owner = new ExecutionStage("territory.validate_canonical", new GlobalOwner(), Map.of(), (execution, ignored) -> {
            execution.authority().requireValid();
            if (!fingerprint(snapshot.ref(), capture.apply(snapshot.ref())).equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
            return CompletableFuture.completedFuture(new StageResult(snapshot.revisionFingerprint(), beforeFacts, Map.of()));
        }, Optional.empty(), 5000);
        final var commit = new ExecutionStage("territory.commit_canonical", new AsyncIoOwner(), Map.of(), (execution, ignored) -> {
            execution.authority().requireValid();
            final var result = manager.adjustConditionally(operation, before.id(), beforeNative, adjustment, now, () -> {
                execution.authority().requireValid();
                return manager.adjustmentStateAvailable() && canonical(manager.all(), world).equals(text(snapshot, CANONICAL));
            });
            final var nativeReceipt = result.receipt().orElseThrow(() -> new WeaverDomainRejection(result.status().name()));
            if (!nativeReceipt.afterFingerprint().equals(afterNative) || !nativeReceipt.beforeFingerprint().equals(beforeNative)
                    || !nativeReceipt.adjustmentFingerprint().equals(adjustment.fingerprint())) throw new WeaverDomainRejection("NATIVE_RECEIPT_CONFLICT");
            return CompletableFuture.completedFuture(new StageResult(afterFingerprint, afterFacts, Map.of()));
        }, Optional.empty(), 10000);
        return new PreparedAction(operation, descriptor, snapshot.ref(), snapshot.revisionFingerprint(), List.of(owner, commit), payload,
                (prepared, results, time) -> receipt(operation, request.actionId(), snapshot.ref(), beforeFacts, afterFacts, reverse, time));
    }
    private void validate(String action, Map<String, WeaverValue> parameters, WeaverTypeRegistry types) {
        final var descriptor = actions.get(action);
        if (descriptor == null || !parameters.keySet().equals(descriptor.parameters().stream().map(ActionParameter::id).collect(java.util.stream.Collectors.toSet()))) throw new WeaverDomainRejection("INVALID_PARAMETERS");
        descriptor.parameters().forEach(p -> p.validate(parameters.get(p.id()), types).requireValid());
    }
    private static TerritoryAdjustment adjustment(String action, Map<String, WeaverValue> values) {
        return switch (action) {
            case RENAME -> new TerritoryAdjustment.Rename((String) values.get("value").payload().get("value"));
            case TYPE -> new TerritoryAdjustment.SetType(TerritoryType.valueOf(((String) values.get("value").payload().get("id")).toUpperCase(Locale.ROOT)));
            case OWNER -> new TerritoryAdjustment.SetOwner(FactionType.valueOf(((String) values.get("value").payload().get("id")).toUpperCase(Locale.ROOT)));
            case Y -> new TerritoryAdjustment.SetYBounds(((Number) values.get("minimum").payload().get("value")).intValue(), ((Number) values.get("maximum").payload().get("value")).intValue());
            default -> throw new WeaverDomainRejection("UNKNOWN_ACTION");
        };
    }
    private static Map<String, WeaverValue> reverse(String action, Territory before, WeaverValue territory, long now) {
        final Map<String, WeaverValue> result = new HashMap<>(); result.put("territory", territory);
        switch (action) {
            case RENAME -> result.put("value", scalar(before.name(), now));
            case TYPE -> result.put("value", value(ZONE_TYPE, Map.of("id", before.type().name().toLowerCase(Locale.ROOT)), Set.of("territory.type"), now));
            case OWNER -> result.put("value", value(ZONE_OWNER, Map.of("id", before.faction().name().toLowerCase(Locale.ROOT)), Set.of("territory.owner"), now));
            case Y -> { result.put("minimum", value(WeaverTypeId.parse("weaver:int@1"), Map.of("value", before.minY()), Set.of(), now));
                result.put("maximum", value(WeaverTypeId.parse("weaver:int@1"), Map.of("value", before.maxY()), Set.of(), now)); }
            default -> throw new WeaverDomainRejection("UNKNOWN_ACTION");
        }
        return Map.copyOf(result);
    }
    static String canonical(Collection<Territory> zones, String world) { return digest(zones.stream().filter(z -> z.world().equals(world)).sorted(Comparator.comparing(Territory::id)).map(TerritoryRevision::fingerprint).toList()); }
    private static Map<String, WeaverValue> scoped(SubjectSnapshot snapshot) {
        final Map<String, WeaverValue> result = new HashMap<>(); SCOPE.fields().forEach(f -> result.put(f, snapshot.facts().get(f))); return Map.copyOf(result);
    }
    private static String fingerprint(SubjectRef subject, Map<String, WeaverValue> facts) { return SCOPE.apply(new SubjectSnapshot(subject, 0, "canonical", facts)).revisionFingerprint(); }
    private static WeaverValue value(WeaverTypeId type, Map<String, Object> fields, Set<String> capabilities, long now) { return new WeaverValue(type, fields, "territory", FACET, capabilities, now); }
    PreparedEffects effects(ProviderContext context) {
        context.authority().requireValid(); return new PreparedEffects(WeaverEffectIntent.none(), (action, results, receipt, sequence) -> WeaverEffectCommit.none());
    }
    PreparedAction undo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) {
        context.authority().requireValid(); final var undo = receipt.undo().orElseThrow(() -> new WeaverDomainRejection("UNDO_UNAVAILABLE"));
        if (!"territory".equals(receipt.providerId()) || !owns(undo.actionId()) || !receipt.subject().equals(snapshot.ref()) || !undo.expectedCurrentFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        return prepare(context, snapshot, new ActionRequest(undo.actionId(), undo.parameters(), context.lifetime(), context.integrityMode()));
    }
    private static WeaverReceipt receipt(UUID operation, String action, SubjectRef subject, Map<String, WeaverValue> before, Map<String, WeaverValue> after, Map<String, WeaverValue> reverse, long now) {
        final String afterFingerprint = fingerprint(subject, after);
        return new WeaverReceipt(operation, operation, "territory", action, subject, RiskLevel.CANONICAL, Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM,
                fingerprint(subject, before), afterFingerprint, before, after, Optional.of(new UndoSpec(action, afterFingerprint, reverse)), now, ReceiptStatus.COMMITTED);
    }
    private static Map<String, Object> encodeValues(Map<String, WeaverValue> values) {
        final Map<String, Object> result = new HashMap<>(); values.forEach((key, value) -> result.put(key, Map.of("type", value.type().canonical(), "payload", value.payload(), "capabilities", value.sourceCapabilities().stream().sorted().toList(), "captured", value.capturedAt()))); return Map.copyOf(result);
    }
    private static OperationRecoveryPayload encode(UUID operation, String id, String beforeNative, String afterNative, Map<String, WeaverValue> before, Map<String, WeaverValue> after, Map<String, WeaverValue> reverse, long now) {
        return new OperationRecoveryPayload(1, Map.of("kind", "territory_canonical", "operation", operation.toString(), "territory", id,
                "before_native", beforeNative, "after_native", afterNative, "before", encodeValues(before), "after", encodeValues(after), "reverse", encodeValues(reverse), "at", now));
    }
    @SuppressWarnings("unchecked") private static Map<String, WeaverValue> decodeValues(Object raw, long at, WeaverTypeRegistry types) {
        final Map<String, WeaverValue> result = new HashMap<>();
        ((Map<String, Object>) raw).forEach((key, encoded) -> {
            final var f = (Map<String, Object>) encoded;
            if (!f.keySet().equals(Set.of("type", "payload", "capabilities", "captured"))) throw new WeaverDomainRejection("INVALID_RECOVERY_PLAN");
            final var value = value(WeaverTypeId.parse((String) f.get("type")), (Map<String, Object>) f.get("payload"), Set.copyOf((List<String>) f.get("capabilities")), nonnegative(f.get("captured")));
            types.validate(value); result.put(key, value);
        }); return Map.copyOf(result);
    }
    RecoveryAssessment assess(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        if (!manager.adjustmentStateAvailable()) throw new WeaverDomainRejection("TERRITORY_UNAVAILABLE");
        try {
            final var f = new HashMap<>(operation.recoveryPayload().fields()); f.remove(WeaverOperationScope.RESERVATIONS);
            if (!owns(operation.request().actionId()) || !(snapshot.ref() instanceof WorldRef) || !snapshot.ref().equals(operation.subject())
                    || operation.request().integrityMode() != IntegrityMode.LIVE_GM || operation.request().lifetime() != Lifetime.ONE_SHOT
                    || operation.recoveryPayload().schemaVersion() != 1 || !f.keySet().equals(Set.of("kind", "operation", "territory", "before_native", "after_native", "before", "after", "reverse", "at"))
                    || !"territory_canonical".equals(f.get("kind")) || !operation.operationId().toString().equals(f.get("operation"))) return conflict("CANONICAL_PLAN_MISMATCH");
            final long at = nonnegative(f.get("at"));
            final var before = decodeValues(f.get("before"), at, context.types()); final var after = decodeValues(f.get("after"), at, context.types());
            final var reverse = decodeValues(f.get("reverse"), at, context.types());
            validate(operation.request().actionId(), operation.request().parameters(), context.types()); validate(operation.request().actionId(), reverse, context.types());
            if (!before.keySet().equals(SCOPE.fields()) || !after.keySet().equals(SCOPE.fields()) || !fingerprint(snapshot.ref(), before).equals(operation.beforeFingerprint())
                    || !zoneKey((String) f.get("territory")).equals(operation.request().parameters().get("territory").payload().get("id"))
                    || !reverse.get("territory").payload().equals(operation.request().parameters().get("territory").payload())) return conflict("CANONICAL_PLAN_MISMATCH");
            final var observed = manager.adjustmentReceipt(operation.operationId());
            if (observed.isEmpty()) return snapshot.revisionFingerprint().equals(operation.beforeFingerprint())
                    ? new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "NATIVE_BEFORE_OBSERVED") : conflict("NATIVE_RECEIPT_UNAVAILABLE");
            final var nativeReceipt = observed.orElseThrow();
            if (!nativeReceipt.territoryId().equals(f.get("territory")) || !nativeReceipt.beforeFingerprint().equals(f.get("before_native")) || !nativeReceipt.afterFingerprint().equals(f.get("after_native"))
                    || nativeReceipt.committedAt() != at || !nativeReceipt.adjustmentFingerprint().equals(adjustment(operation.request().actionId(), operation.request().parameters()).fingerprint())) return conflict("NATIVE_RECEIPT_CONFLICT");
            final var zones = manager.all(); final var current = zones.stream().filter(z -> z.id().equals(nativeReceipt.territoryId())).findFirst().orElseThrow();
            if (!TerritoryRevision.fingerprint(current).equals(nativeReceipt.afterFingerprint())) return conflict("CANONICAL_DRIFT");
            final var restored = adjustment(operation.request().actionId(), reverse).apply(current);
            if (!TerritoryRevision.fingerprint(restored).equals(nativeReceipt.beforeFingerprint())
                    || !adjustment(operation.request().actionId(), operation.request().parameters()).apply(restored).equals(current)) return conflict("COMPENSATION_PLAN_MISMATCH");
            final var restoredZones = new ArrayList<>(zones); restoredZones.remove(current); restoredZones.add(restored);
            final String world = (String) before.get(WORLD).payload().get("value");
            if (!canonical(restoredZones, world).equals(before.get(CANONICAL).payload().get("value"))) return conflict("CANONICAL_PLAN_MISMATCH");
            final var expected = receipt(operation.operationId(), operation.request().actionId(), snapshot.ref(), before, after, reverse,
                    operation.receipt().map(WeaverReceipt::createdAt).orElseGet(() -> Math.max(System.currentTimeMillis(), operation.preparedAt())));
            if (!snapshot.revisionFingerprint().equals(expected.afterFingerprint()) || operation.receipt().isPresent() && !operation.receipt().orElseThrow().equals(expected)) return conflict("CANONICAL_DRIFT");
            return new RecoveryAssessment(ObservedOperationState.APPLIED, false, Optional.of(expected), "NATIVE_RECEIPT_AND_STATE_OBSERVED");
        } catch (RuntimeException invalid) { return conflict("CANONICAL_PLAN_UNAVAILABLE"); }
    }
    private static RecoveryAssessment conflict(String code) { return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), code); }
    private static long nonnegative(Object value) {
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0) throw new WeaverDomainRejection("INVALID_RECOVERY_PLAN");
        return ((Number) value).longValue();
    }
}
