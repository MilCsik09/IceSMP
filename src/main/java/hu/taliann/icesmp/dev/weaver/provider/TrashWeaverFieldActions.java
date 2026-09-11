package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.RewardSource;
import hu.taliann.icesmp.trash.TrashRuleFieldService;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.*;

/** Sandbox fields use the existing native field authority, never a second geometry/effect store. */
final class TrashWeaverFieldActions {
    static final String CREATE = "trash.create_sandbox_field", CLEAR = "trash.clear_sandbox_fields", CATALOG = "trash.sandbox_rules";
    private static final String REVISION = "trash.fields_revision", IDS = "trash.sandbox_fields";
    private static final WeaverRevisionScope SCOPE = new WeaverRevisionScope(1, Set.of(REVISION));
    private final TrashRuleFieldService fields;
    private final Set<UUID> owned = ConcurrentHashMap.newKeySet();
    private volatile long sessionGeneration;
    private final Map<String, ActionDescriptor> actions;
    TrashWeaverFieldActions(TrashRuleFieldService fields) {
        this.fields = fields;
        final ActionParameter rule = new ActionParameter("rule", Component.text("Szabály"), TrashWeaverProvider.RULE, ActionParameter.InputKind.CATALOG,
                true, Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalInt.empty(), Optional.of(CATALOG), Set.of("trash.rule"));
        actions = Map.of(CREATE, action(CREATE, "Sandbox szabálymező · 20 s / 3 blokk", List.of(rule)), CLEAR, action(CLEAR, "Saját sandbox mezők eltávolítása", List.of()));
    }
    private static ActionDescriptor action(String id, String label, List<ActionParameter> parameters) {
        return new ActionDescriptor(id, TrashWeaverProvider.FACET, Component.text(label), RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX),
                Set.of(IntegrityImpact.EVENT_ORIGIN), Set.of(WeaverSubjectKind.AREA), parameters, AreaSupport.SPATIAL, Optional.of(new AreaLimits(0, 0, 9, 9, 1)), false,
                Optional.of("A rövid életű natív mező eltávolítható; korábbi hatásait nem játssza vissza."), 10, SCOPE);
    }
    static Map<String, TrashRuleFieldService.FieldKind> rules() { return Map.of("acoustic_null", TrashRuleFieldService.FieldKind.ACOUSTIC_NULL,
            "ceasefire", TrashRuleFieldService.FieldKind.CEASEFIRE, "spatial_anchor", TrashRuleFieldService.FieldKind.SPATIAL_ANCHOR); }
    WeaverValueCatalog catalog() { return new RegistryValueCatalog<>(TrashWeaverProvider.RULE, "trash", TrashWeaverProvider.FACET, Set.of("trash.rule"),
            TrashWeaverFieldActions::rules, rule -> Component.text(rule.name()), System::currentTimeMillis); }
    List<ActionDescriptor> descriptors() { return List.copyOf(actions.values()); }
    boolean owns(String action) { return actions.containsKey(action); }
    private static boolean in(AreaRef area, TrashRuleFieldService.RuleField field) {
        return area.worldId().equals(field.center().world()) && area.shape().contains((int) Math.floor(field.center().x()), (int) Math.floor(field.center().y()), (int) Math.floor(field.center().z()));
    }
    private List<TrashRuleFieldService.RuleField> selected(AreaRef area) {
        final var snapshot = fields.snapshot(); if (!snapshot.open()) throw new WeaverDomainRejection("TRASH_FIELDS_UNAVAILABLE");
        return snapshot.fields().stream().filter(field -> owned.contains(field.id()) && in(area, field) && field.active(System.currentTimeMillis())).toList();
    }
    Map<String, WeaverValue> capture(SubjectRef ref) {
        if (!(ref instanceof AreaRef area)) return Map.of();
        final var snapshot = fields.snapshot(); if (!snapshot.open()) throw new WeaverDomainRejection("TRASH_FIELDS_UNAVAILABLE");
        return Map.of(REVISION, text(Long.toString(snapshot.revision())), IDS, text(String.join(",", selected(area).stream().map(f -> f.id().toString()).toList())));
    }
    private static WeaverValue text(String value) { return new WeaverValue(WeaverTypeId.parse("weaver:text@1"), Map.of("value", value), "trash", TrashWeaverProvider.FACET, Set.of(), System.currentTimeMillis()); }
    Set<String> discover(SubjectSnapshot snapshot) {
        if (!(snapshot.ref() instanceof AreaRef) || !snapshot.facts().containsKey(REVISION)) return Set.of();
        return "".equals(snapshot.facts().get(IDS).payload().get("value")) ? Set.of(CREATE) : actions.keySet();
    }
    synchronized void clearSession() {
        sessionGeneration++;
        for (final var field : fields.snapshot().fields()) if (owned.contains(field.id())) fields.remove(field);
        owned.clear();
    }
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid(); final ActionDescriptor action = actions.get(request.actionId());
        if (action == null || !(snapshot.ref() instanceof AreaRef area) || request.integrityMode() != IntegrityMode.SANDBOX || context.integrityMode() != IntegrityMode.SANDBOX
                || request.lifetime() != Lifetime.ONE_SHOT || context.lifetime() != Lifetime.ONE_SHOT) throw new WeaverDomainRejection("INVALID_FIELD_REQUEST");
        final boolean create = CREATE.equals(action.id());
        if (!request.parameters().keySet().equals(create ? Set.of("rule") : Set.of())) throw new WeaverDomainRejection("INVALID_PARAMETERS");
        final TrashRuleFieldService.FieldKind rule;
        if (create) {
            action.parameters().getFirst().validate(request.parameters().get("rule"), context.types()).requireValid();
            rule = rules().get((String) request.parameters().get("rule").payload().get("id"));
            if (rule == null) throw new WeaverDomainRejection("RULE_NOT_SANDBOXABLE");
        } else rule = null;
        final UUID operation = UUID.randomUUID(); final long now = System.currentTimeMillis();
        final long generation = sessionGeneration;
        final List<TrashRuleFieldService.RuleField> selected = create ? List.of() : selected(area);
        if (!create && selected.isEmpty()) throw new WeaverDomainRejection("NO_SANDBOX_FIELDS");
        final var bounds = area.shape().bounds();
        final var field = create ? new TrashRuleFieldService.RuleField(operation, rule,
                new TrashRuleFieldService.Point(area.worldId(), (bounds.minX() + bounds.maxX()) / 2.0 + 0.5, (bounds.minY() + bounds.maxY()) / 2.0 + 0.5, (bounds.minZ() + bounds.maxZ()) / 2.0 + 0.5),
                3, now + 20_000, context.authority().actor(), null) : null;
        if (create && !in(area, field)) throw new WeaverDomainRejection("FIELD_CENTER_OUTSIDE_AREA");
        final var stage = new ExecutionStage("trash.field.native", new GlobalOwner(), Map.of(), (execution, payload) -> {
            synchronized (this) {
            execution.authority().requireValid(); execution.nativeEffects().orElseThrow().requireAction("trash", action.id(), IntegrityMode.SANDBOX, area);
            if (generation != sessionGeneration) throw new WeaverDomainRejection("SESSION_ENDED");
            if (!SCOPE.apply(new SubjectSnapshot(area, now, snapshot.revisionFingerprint(), capture(area))).revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
            if (create) {
                owned.retainAll(fields.snapshot().fields().stream().map(TrashRuleFieldService.RuleField::id).collect(java.util.stream.Collectors.toSet()));
                if (owned.size() >= 16 || !fields.add(field)) throw new WeaverDomainRejection("FIELD_CAPACITY"); owned.add(field.id());
            } else for (final var current : selected) if (!fields.removeIdle(current, fields.snapshot().revision())) throw new WeaverDomainRejection("FIELD_CLAIMED"); else owned.remove(current.id());
            final var after = capture(area); final String hash = SCOPE.apply(new SubjectSnapshot(area, now, snapshot.revisionFingerprint(), after)).revisionFingerprint();
            return CompletableFuture.completedFuture(new StageResult(hash, after, Map.of()));
            }
        }, Optional.empty(), 5000);
        return new PreparedAction(operation, action, area, snapshot.revisionFingerprint(), List.of(stage),
                new OperationRecoveryPayload(1, Map.of("trash.field_ids", create ? List.of(operation.toString()) : selected.stream().map(f -> f.id().toString()).toList())),
                (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), operation, "trash", action.id(), area, action.risk(), request.lifetime(), request.integrityMode(),
                        snapshot.revisionFingerprint(), results.getLast().afterFingerprint(), Map.of(REVISION, snapshot.facts().get(REVISION)), results.getLast().facts(), Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
    PreparedEffects effects(PreparedAction prepared) {
        final Set<WeaverInfluenceTarget> targets = new HashSet<>();
        for (final Object id : (List<?>) prepared.recoveryPayload().fields().get("trash.field_ids")) targets.add(WeaverInfluenceTarget.exact(new RewardSource.Event("trash.rule_field", UUID.fromString((String) id))));
        return new PreparedEffects(new WeaverEffectIntent(targets), (action, results, receipt, sequence) -> WeaverEffectCommit.none());
    }
    RecoveryAssessment assess(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        return new RecoveryAssessment(snapshot.revisionFingerprint().equals(operation.beforeFingerprint()) ? ObservedOperationState.BEFORE
                : operation.receipt().filter(receipt -> receipt.afterFingerprint().equals(snapshot.revisionFingerprint())).isPresent() ? ObservedOperationState.APPLIED : ObservedOperationState.PARTIAL_OR_CONFLICT,
                false, Optional.empty(), "Observe native transient fields; never recreate an expired field.");
    }
}
