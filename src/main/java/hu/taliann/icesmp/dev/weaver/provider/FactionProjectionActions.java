package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import static hu.taliann.icesmp.dev.weaver.provider.FactionWeaverProvider.*;

/** Owner validation followed by atomic journal publication; never writes canonical faction state. */
final class FactionProjectionActions {
    static final String SEVER = "faction.sever_projection", CLEAR_PLAYER = "faction.clear_player_projection", CLEAR_ENTITY = "faction.clear_entity_contexts";
    static final String CANONICAL_REVISION = "faction.canonical_revision", PROJECTION_REVISION = "faction.projection_revision";
    static final WeaverRevisionScope SCOPE = new WeaverRevisionScope(1, Set.of(CANONICAL_REVISION, PROJECTION_REVISION));
    private static final WeaverTypeId PROJECTION_REF = WeaverTypeId.parse("weaver:projection_ref@1");
    private static final Map<String, String> FIELDS = Map.of("faction.project_player_faction", FactionRuntimeProjectionSource.MEMBERSHIP,
            "faction.add_entity_context", FactionRuntimeProjectionSource.ADD, "faction.remove_entity_context", FactionRuntimeProjectionSource.REMOVE);
    private final FactionRuntimeProjectionSource source;
    private final Function<SubjectRef, Map<String, WeaverValue>> snapshots;
    private final Map<String, ActionDescriptor> actions;

    FactionProjectionActions(FactionRuntimeProjectionSource source, Function<SubjectRef, Map<String, WeaverValue>> snapshots) {
        this.source = Objects.requireNonNull(source); this.snapshots = Objects.requireNonNull(snapshots);
        final Map<String, ActionDescriptor> all = new LinkedHashMap<>();
        add(all, "faction.project_player_faction", "Frakció rávetítése", FACTION, "faction.memberships", "faction.membership", WeaverSubjectKind.PLAYER);
        add(all, "faction.add_entity_context", "Szemantikus kontextus hozzáadása", CONTEXT, "faction.contexts", "faction.context", WeaverSubjectKind.ENTITY);
        add(all, "faction.remove_entity_context", "Szemantikus kontextus elnyomása", CONTEXT, "faction.contexts", "faction.context", WeaverSubjectKind.ENTITY);
        for (final var clear : Map.of(CLEAR_PLAYER, WeaverSubjectKind.PLAYER, CLEAR_ENTITY, WeaverSubjectKind.ENTITY).entrySet()) {
            all.put(clear.getKey(), descriptor(clear.getKey(), "Projectionök elvágása", Set.of(Lifetime.ONE_SHOT), Set.of(clear.getValue()), List.of(), false));
        }
        all.put(SEVER, descriptor(SEVER, "Projection elvágása", Set.of(Lifetime.ONE_SHOT), Set.of(WeaverSubjectKind.PLAYER, WeaverSubjectKind.ENTITY),
                List.of(parameter(PROJECTION_REF, Optional.empty(), "faction.projection")), false));
        actions = Map.copyOf(all);
    }
    private static void add(Map<String, ActionDescriptor> all, String id, String label, WeaverTypeId type, String catalog, String capability, WeaverSubjectKind kind) {
        all.put(id, descriptor(id, label, Set.of(Lifetime.SESSION, Lifetime.PERSISTENT), Set.of(kind), List.of(parameter(type, Optional.of(catalog), capability)), true));
    }
    private static ActionDescriptor descriptor(String id, String label, Set<Lifetime> lifetimes, Set<WeaverSubjectKind> subjects,
            List<ActionParameter> parameters, boolean undoable) {
        return new ActionDescriptor(id, FACET, Component.text(label), RiskLevel.MUTATING, lifetimes, Set.of(IntegrityMode.SANDBOX, IntegrityMode.LIVE_GM),
                Set.of(IntegrityImpact.TAINT_SUBJECT), subjects, parameters, AreaSupport.NONE, Optional.empty(), undoable,
                undoable ? Optional.empty() : Optional.of("Elvágás után új projection hozható létre; a korábbi receipt és influence megmarad."), 1, SCOPE);
    }
    private static ActionParameter parameter(WeaverTypeId type, Optional<String> catalog, String capability) {
        return new ActionParameter("value", Component.text("Érték"), type, catalog.isPresent() ? ActionParameter.InputKind.CATALOG : ActionParameter.InputKind.THREAD,
                true, Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalInt.empty(), catalog, Set.of(capability));
    }
    List<ActionDescriptor> descriptors() { return actions.values().stream().sorted(Comparator.comparing(ActionDescriptor::id)).toList(); }
    Set<String> visible(WeaverSubjectKind kind) { return actions.values().stream().filter(a -> !a.id().equals(SEVER) && a.subjects().contains(kind)).map(ActionDescriptor::id).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    List<ImportDescriptor> imports() {
        return List.of(new ImportDescriptor("faction.import_membership", "faction.project_player_faction", FACTION, Set.of("faction.membership"), "value"),
                new ImportDescriptor("faction.import_context", "faction.add_entity_context", CONTEXT, Set.of("faction.context"), "value"));
    }
    List<ProjectionConsumerDescriptor> consumers() {
        final Set<String> canonical = Set.of("faction.history", "faction.tax", "faction.treasury", "faction.season", "faction.quest", "faction.territory_ownership", "faction.hud", "faction.crown_curse");
        return List.of(new ProjectionConsumerDescriptor(FactionRuntimeProjectionSource.MEMBERSHIP_CONSUMER, "faction", "hu.taliann.icesmp.managers.FactionManager#getEffectiveMembership",
                        Set.of("faction.project_player_faction"), Set.of(WeaverSubjectKind.PLAYER), Map.of(FactionRuntimeProjectionSource.MEMBERSHIP, FACTION), canonical),
                new ProjectionConsumerDescriptor(FactionRuntimeProjectionSource.CONTEXT_CONSUMER, "faction", "hu.taliann.icesmp.factions.FactionMobContextResolver#effectiveContentContexts",
                        Set.of("faction.add_entity_context", "faction.remove_entity_context"), Set.of(WeaverSubjectKind.ENTITY),
                        Map.of(FactionRuntimeProjectionSource.ADD, CONTEXT, FactionRuntimeProjectionSource.REMOVE, CONTEXT), canonical));
    }
    ImportValidation validateImport(ProviderContext context, SubjectSnapshot snapshot, String id, WeaverValue value) {
        final var importer = imports().stream().filter(i -> i.id().equals(id)).findFirst().orElse(null);
        return importer != null && actions.get(importer.actionId()).subjects().contains(snapshot.ref().kind())
                && snapshot.facts().containsKey(CANONICAL_REVISION) && context.types().compatible(value, importer.acceptedType(), importer.requiredCapabilities())
                ? ImportValidation.accepted() : ImportValidation.rejected("THREAD_INCOMPATIBLE");
    }
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid(); final ActionDescriptor descriptor = actions.get(request.actionId());
        if (descriptor == null || !descriptor.subjects().contains(snapshot.ref().kind()) || !snapshot.facts().containsKey(CANONICAL_REVISION)
                || !descriptor.lifetimes().contains(request.lifetime()) || !descriptor.integrityModes().contains(request.integrityMode())) throw new WeaverDomainRejection("ACTION_UNAVAILABLE");
        final boolean add = FIELDS.containsKey(request.actionId()), clear = request.actionId().equals(CLEAR_PLAYER) || request.actionId().equals(CLEAR_ENTITY);
        final WeaverValue value = request.parameters().get("value");
        if (clear ? !request.parameters().isEmpty() : value == null || !request.parameters().keySet().equals(Set.of("value"))) throw new WeaverDomainRejection("INVALID_PARAMETERS");
        if (!clear) descriptor.parameters().getFirst().validate(value, context.types()).requireValid();
        final List<WeaverProjection> before = source.active(snapshot.ref()); final String expected = WeaverProjectionFingerprint.of(before);
        if (!expected.equals(text(snapshot, PROJECTION_REVISION)) || !SCOPE.apply(snapshot).revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        final UUID operation = UUID.randomUUID(); final long now = System.currentTimeMillis(); final Set<UUID> removed = new HashSet<>();
        final List<WeaverProjection> after = new ArrayList<>(before);
        if (add) after.add(projection(operation, before.stream().mapToLong(WeaverProjection::sequence).max().orElse(0) + 1, context.authority().actor(), request, snapshot, value, now));
        else {
            if (clear) before.forEach(p -> removed.add(p.projectionId()));
            else {
                if (!"faction".equals(value.payload().get("provider"))) throw new WeaverDomainRejection("FOREIGN_PROJECTION");
                removed.add(UUID.fromString(FactionRuntimeProjectionSource.id(value)));
            }
            if (removed.isEmpty() || !before.stream().map(WeaverProjection::projectionId).collect(java.util.stream.Collectors.toSet()).containsAll(removed)) throw new WeaverDomainRejection("CONFLICT");
            after.removeIf(p -> removed.contains(p.projectionId()));
        }
        final Map<String, WeaverValue> beforeFacts = Map.of(CANONICAL_REVISION, snapshot.facts().get(CANONICAL_REVISION), PROJECTION_REVISION, snapshot.facts().get(PROJECTION_REVISION));
        final Map<String, WeaverValue> afterFacts = new HashMap<>(beforeFacts); afterFacts.put(PROJECTION_REVISION, scalar(WeaverProjectionFingerprint.of(after), now));
        final String afterFingerprint = SCOPE.apply(new SubjectSnapshot(snapshot.ref(), now, "pending", afterFacts)).revisionFingerprint();
        final UUID target = snapshot.ref() instanceof PlayerRef player ? player.playerId() : ((EntityRef) snapshot.ref()).entityId();
        final var stage = new ExecutionStage("faction.validate_projection", new EntityOwner(target), Map.of(), (execution, payload) -> {
            execution.authority().requireValid();
            final var fresh = SCOPE.apply(new SubjectSnapshot(snapshot.ref(), System.currentTimeMillis(), "owner", snapshots.apply(snapshot.ref())));
            if (!fresh.revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
            return CompletableFuture.completedFuture(new StageResult(afterFingerprint, afterFacts, Map.of()));
        }, Optional.empty(), 5000);
        final var reference = new WeaverValue(PROJECTION_REF, Map.of("id", operation.toString(), "provider", "faction"), "faction", FACET, Set.of("faction.projection"), now);
        return new PreparedAction(operation, descriptor, snapshot.ref(), snapshot.revisionFingerprint(), List.of(stage),
                new OperationRecoveryPayload(1, Map.of("faction.kind", "journal_projection", "faction.before", expected,
                        "faction.removed", removed.stream().map(UUID::toString).sorted().toList())),
                (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), operation, "faction", descriptor.id(), snapshot.ref(), descriptor.risk(), request.lifetime(), request.integrityMode(),
                        snapshot.revisionFingerprint(), afterFingerprint, beforeFacts, afterFacts,
                        add ? Optional.of(new UndoSpec(SEVER, afterFingerprint, Map.of("value", reference))) : Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
    PreparedEffects effects(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request, PreparedAction prepared) {
        context.authority().requireValid(); final UUID actor = context.authority().actor();
        final String expected = (String) prepared.recoveryPayload().fields().get("faction.before");
        final Set<UUID> removed = ((List<?>) prepared.recoveryPayload().fields().get("faction.removed")).stream().map(v -> UUID.fromString((String) v)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new PreparedEffects(WeaverEffectIntent.none(), (action, results, receipt, sequence) -> new WeaverEffectCommit(
                FIELDS.containsKey(request.actionId()) ? List.of(projection(prepared.operationId(), sequence, actor, request, snapshot, request.parameters().get("value"), receipt.createdAt())) : List.of(),
                removed, List.of(), Optional.empty(), Map.of(snapshot.ref(), expected)));
    }
    PreparedAction undo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) {
        final UndoSpec undo = receipt.undo().orElseThrow(() -> new WeaverDomainRejection("UNDO_UNAVAILABLE"));
        if (!SEVER.equals(undo.actionId()) || !receipt.providerId().equals("faction") || !undo.expectedCurrentFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        return prepare(context, snapshot, new ActionRequest(SEVER, undo.parameters(), context.lifetime(), context.integrityMode()));
    }
    RecoveryAssessment assess(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        if (!actions.containsKey(operation.request().actionId()) || operation.recoveryPayload().schemaVersion() != 1
                || !"journal_projection".equals(operation.recoveryPayload().fields().get("faction.kind"))) return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "PROJECTION_PLAN_UNAVAILABLE");
        if (operation.status() == OperationStatus.PREPARED) return new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "NO_PROJECTION_PUBLICATION");
        if (operation.receipt().isPresent() && snapshot.revisionFingerprint().equals(operation.receipt().get().afterFingerprint()))
            return new RecoveryAssessment(ObservedOperationState.APPLIED, false, operation.receipt(), "DURABLE_PROJECTION_OBSERVED");
        return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "PROJECTION_DRIFT");
    }
    private static WeaverProjection projection(UUID operation, long sequence, UUID actor, ActionRequest request, SubjectSnapshot snapshot, WeaverValue value, long now) {
        final WeaverValue owned = new WeaverValue(value.type(), value.payload(), "faction", FACET, value.sourceCapabilities(), now);
        return new WeaverProjection(operation, sequence, "faction", request.actionId(), snapshot.ref(), request.lifetime(),
                new DeveloperInfluence(operation, request.integrityMode(), request.actionId(), actor, now), Map.of(FIELDS.get(request.actionId()), owned),
                snapshot.revisionFingerprint(), now, OptionalLong.empty());
    }
    private static String text(SubjectSnapshot snapshot, String key) {
        if (snapshot.facts().get(key) == null || !(snapshot.facts().get(key).payload().get("value") instanceof String text)) throw new WeaverDomainRejection("REVISION_FIELD_UNAVAILABLE"); return text;
    }
}
