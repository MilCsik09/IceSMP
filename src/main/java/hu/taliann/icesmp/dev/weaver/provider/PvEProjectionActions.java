package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.pve.*;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import static hu.taliann.icesmp.dev.weaver.provider.PvEWeaverProvider.*;

/** Projection-only transactions: the owner stage validates; APPLIED is the sole mutation boundary. */
final class PvEProjectionActions {
    static final String SEVER = "pve.sever_projection";
    static final String CLEAR = "pve.clear_projection", CATALOG = "pve.projections";
    static final String CANONICAL_REVISION = "pve.canonical_revision", PROJECTION_REVISION = "pve.projection_revision";
    static final WeaverRevisionScope SCOPE = new WeaverRevisionScope(1, Set.of(CANONICAL_REVISION, PROJECTION_REVISION));
    private static final WeaverTypeId PROJECTION_REF = WeaverTypeId.parse("weaver:projection_ref@1");
    private static final Map<String, String> FIELDS = Map.of("pve.add_ability", PvEMobProjectionSource.ADD, "pve.remove_ability", PvEMobProjectionSource.REMOVE,
            "pve.override_rank", PvEMobProjectionSource.RANK, "pve.override_archetype", PvEMobProjectionSource.ARCHETYPE, "pve.apply_template_projection", PvEMobProjectionSource.TEMPLATE, "pve.apply_imprint", PvEMobProjectionSource.IMPRINT);
    private final PvEMobProjectionSource source;
    private final Function<SubjectRef, Map<String, WeaverValue>> snapshots;
    private final Map<String, ActionDescriptor> actions;
    private final java.util.function.Supplier<Map<String, MobAbilityDefinition>> abilities;
    PvEProjectionActions(final PvEMobProjectionSource source, final Function<SubjectRef, Map<String, WeaverValue>> snapshots,
            final java.util.function.Supplier<Map<String, MobAbilityDefinition>> abilities) {
        this.source = Objects.requireNonNull(source); this.snapshots = Objects.requireNonNull(snapshots);
        this.abilities = Objects.requireNonNull(abilities);
        final Map<String, ActionDescriptor> descriptors = new LinkedHashMap<>();
        add(descriptors, "pve.add_ability", "Képesség rávetítése", ABILITY, "pve.abilities", "pve.ability");
        add(descriptors, "pve.remove_ability", "Képesség elnyomása", ABILITY, "pve.abilities", "pve.ability");
        add(descriptors, "pve.override_rank", "Combat rang rávetítése", RANK, "pve.ranks", "pve.rank");
        add(descriptors, "pve.override_archetype", "Archetípus rávetítése", ARCHETYPE, "pve.archetypes", "pve.archetype");
        add(descriptors, "pve.apply_template_projection", "Combat sablon rávetítése", TEMPLATE, "pve.templates", "pve.template");
        descriptors.put("pve.apply_imprint", new ActionDescriptor("pve.apply_imprint", FACET, Component.text("Combat lenyomat rávetítése"), RiskLevel.MUTATING,
                Set.of(Lifetime.SESSION), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT), Set.of(WeaverSubjectKind.ENTITY),
                List.of(parameter(PvEImprintCodec.TYPE, Optional.empty(), Set.of("pve.imprint"))), AreaSupport.NONE, Optional.empty(), true, Optional.empty(), 1, SCOPE));
        descriptors.put(SEVER, new ActionDescriptor(SEVER, FACET, Component.text("Projection elvágása"), RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX, IntegrityMode.LIVE_GM),
                Set.of(IntegrityImpact.TAINT_SUBJECT), Set.of(WeaverSubjectKind.ENTITY), List.of(parameter(PROJECTION_REF, Optional.of(CATALOG), Set.of("pve.projection"))),
                AreaSupport.NONE, Optional.empty(), false, Optional.of("A korábbi receipt és influence megmarad; visszaállításhoz új projection szükséges."), 1, SCOPE));
        descriptors.put(CLEAR, new ActionDescriptor(CLEAR, FACET, Component.text("PvE projectionök elvágása"), RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT),
                Set.of(IntegrityMode.SANDBOX, IntegrityMode.LIVE_GM), Set.of(IntegrityImpact.TAINT_SUBJECT), Set.of(WeaverSubjectKind.ENTITY), List.of(),
                AreaSupport.NONE, Optional.empty(), false, Optional.of("A korábbi receiptek és influence megmaradnak; visszaállításhoz új projection szükséges."), 1, SCOPE));
        actions = Map.copyOf(descriptors);
    }
    private static void add(final Map<String, ActionDescriptor> actions, final String id, final String label, final WeaverTypeId type, final String catalog, final String capability) {
        actions.put(id, new ActionDescriptor(id, FACET, Component.text(label), RiskLevel.MUTATING, Set.of(Lifetime.SESSION, Lifetime.PERSISTENT),
                Set.of(IntegrityMode.SANDBOX, IntegrityMode.LIVE_GM), Set.of(IntegrityImpact.TAINT_SUBJECT), Set.of(WeaverSubjectKind.ENTITY),
                List.of(parameter(type, Optional.of(catalog), Set.of(capability))), AreaSupport.NONE, Optional.empty(), true, Optional.empty(), 1, SCOPE));
    }
    private static ActionParameter parameter(final WeaverTypeId type, final Optional<String> catalog, final Set<String> capabilities) {
        return new ActionParameter("value", Component.text("Érték"), type, catalog.isPresent() ? ActionParameter.InputKind.CATALOG : ActionParameter.InputKind.THREAD,
                true, Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalInt.empty(), catalog, capabilities);
    }
    List<ActionDescriptor> descriptors() { return actions.values().stream().sorted(Comparator.comparing(ActionDescriptor::id)).toList(); }
    Set<String> visible(SubjectSnapshot snapshot) {
        final var visible = new HashSet<>(FIELDS.keySet());
        if (!text(snapshot, "pve.projections").isEmpty()) { visible.add(SEVER); visible.add(CLEAR); }
        return Set.copyOf(visible);
    }
    CatalogDescriptor catalogDescriptor() { return new CatalogDescriptor(CATALOG, FACET, Component.text("Aktív PvE projectionök"), PROJECTION_REF); }
    Optional<WeaverValueCatalog> catalog(SubjectSnapshot snapshot) {
        return snapshot.ref() instanceof EntityRef entity ? Optional.of(new ProjectionValueCatalog("pve", FACET, "pve.projection", () -> source.active(entity.entityId()))) : Optional.empty();
    }
    List<ImportDescriptor> imports() {
        return List.of(new ImportDescriptor("pve.import_ability", "pve.add_ability", ABILITY, Set.of("pve.ability"), "value"),
                new ImportDescriptor("pve.import_rank", "pve.override_rank", RANK, Set.of("pve.rank"), "value"),
                new ImportDescriptor("pve.import_archetype", "pve.override_archetype", ARCHETYPE, Set.of("pve.archetype"), "value"),
                new ImportDescriptor("pve.import_template", "pve.apply_template_projection", TEMPLATE, Set.of("pve.template"), "value"),
                new ImportDescriptor("pve.import_imprint", "pve.apply_imprint", PvEImprintCodec.TYPE, Set.of("pve.imprint"), "value"));
    }
    List<ProjectionConsumerDescriptor> consumers() {
        return List.of(new ProjectionConsumerDescriptor(PvEMobProjectionSource.CONSUMER, "pve", "hu.taliann.icesmp.pve.MobAbilityRuntime#effectiveProfile", FIELDS.keySet(), Set.of(WeaverSubjectKind.ENTITY),
                Map.of(PvEMobProjectionSource.ADD, ABILITY, PvEMobProjectionSource.REMOVE, ABILITY, PvEMobProjectionSource.RANK, RANK,
                        PvEMobProjectionSource.ARCHETYPE, ARCHETYPE, PvEMobProjectionSource.TEMPLATE, TEMPLATE, PvEMobProjectionSource.IMPRINT, PvEImprintCodec.TYPE),
                Set.of("pve.loot", "pve.bestiary", "pve.quest_identity", "pve.reward_level", "pve.provenance")));
    }
    ImportValidation validateImport(final ProviderContext context, final SubjectSnapshot snapshot, final String id, final WeaverValue value) {
        final ImportDescriptor importer = imports().stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
        if (importer == null || !context.types().compatible(value, importer.acceptedType(), importer.requiredCapabilities())) return ImportValidation.rejected("THREAD_INCOMPATIBLE");
        if (!(snapshot.ref() instanceof EntityRef) || !snapshot.facts().containsKey(CANONICAL_REVISION)) return ImportValidation.rejected("SUBJECT_INCOMPATIBLE");
        if (importer.actionId().equals("pve.add_ability") && !abilityCompatible(snapshot, value)) return ImportValidation.rejected("ABILITY_INCOMPATIBLE");
        return ImportValidation.accepted();
    }
    private boolean abilityCompatible(final SubjectSnapshot snapshot, final WeaverValue value) {
        final MobAbilityDefinition definition = abilities.get().get(PvEMobProjectionSource.id(value));
        final WeaverValue rank = snapshot.facts().get("pve.effective_rank");
        if (definition == null || rank == null) return false;
        final String archetype = text(snapshot, "pve.effective_archetype");
        return definition.eligible(MobRank.valueOf(PvEMobProjectionSource.id(rank).toUpperCase(Locale.ROOT)), archetype.isEmpty() ? null : MobArchetype.valueOf(archetype.toUpperCase(Locale.ROOT)));
    }
    PreparedAction prepare(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request) {
        context.authority().requireValid();
        final ActionDescriptor descriptor = actions.get(request.actionId());
        if (descriptor == null || !(snapshot.ref() instanceof EntityRef entity) || !snapshot.facts().containsKey(CANONICAL_REVISION)
                || !descriptor.lifetimes().contains(request.lifetime()) || !descriptor.integrityModes().contains(request.integrityMode())) throw new WeaverDomainRejection("ACTION_UNAVAILABLE");
        final WeaverValue value = request.parameters().get("value");
        final boolean clear = request.actionId().equals(CLEAR);
        if (clear ? !request.parameters().isEmpty() : value == null || !request.parameters().keySet().equals(Set.of("value"))) throw new WeaverDomainRejection("INVALID_PARAMETERS");
        if (!clear) descriptor.parameters().getFirst().validate(value, context.types()).requireValid();
        if (request.actionId().equals("pve.add_ability") && !abilityCompatible(snapshot, value)) throw new WeaverDomainRejection("ABILITY_INCOMPATIBLE");
        final List<WeaverProjection> before = source.active(entity.entityId());
        final String expected = WeaverProjectionFingerprint.of(before);
        if (!expected.equals(text(snapshot, PROJECTION_REVISION)) || !SCOPE.apply(snapshot).revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        final UUID operationId = UUID.randomUUID(); final long now = System.currentTimeMillis();
        final List<WeaverProjection> after = new ArrayList<>(before);
        final UUID projectionId;
        if (clear) {
            if (before.isEmpty()) throw new WeaverDomainRejection("NO_CHANGE");
            projectionId = operationId; after.clear();
        } else if (request.actionId().equals(SEVER)) {
            if (!"pve".equals(value.payload().get("provider"))) throw new WeaverDomainRejection("FOREIGN_PROJECTION");
            projectionId = UUID.fromString(PvEMobProjectionSource.id(value));
            if (!after.removeIf(projection -> projection.projectionId().equals(projectionId))) throw new WeaverDomainRejection("CONFLICT");
        } else {
            projectionId = operationId;
            after.add(projection(operationId, projectionId, before.stream().mapToLong(WeaverProjection::sequence).max().orElse(0) + 1,
                    context.authority().actor(), request, snapshot, value, now));
        }
        final Map<String, WeaverValue> beforeFacts = Map.of(CANONICAL_REVISION, snapshot.facts().get(CANONICAL_REVISION), PROJECTION_REVISION, snapshot.facts().get(PROJECTION_REVISION));
        final Map<String, WeaverValue> afterFacts = new HashMap<>(beforeFacts); afterFacts.put(PROJECTION_REVISION, scalar("text", WeaverProjectionFingerprint.of(after), now));
        final String afterFingerprint = SCOPE.apply(new SubjectSnapshot(snapshot.ref(), now, "pending", afterFacts)).revisionFingerprint();
        final WeaverValue projectionRef = new WeaverValue(PROJECTION_REF, Map.of("id", projectionId.toString(), "provider", "pve"), "pve", FACET, Set.of("pve.projection"), now);
        final var stage = new ExecutionStage("pve.validate_projection", new EntityOwner(entity.entityId()), Map.of(), (execution, payload) -> {
            execution.authority().requireValid(); final long captured = System.currentTimeMillis();
            final SubjectSnapshot fresh = SCOPE.apply(new SubjectSnapshot(snapshot.ref(), captured, "owner", snapshots.apply(snapshot.ref())));
            if (!fresh.revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
            return CompletableFuture.completedFuture(new StageResult(afterFingerprint, afterFacts, Map.of()));
        }, Optional.empty(), 5000);
        return new PreparedAction(operationId, descriptor, snapshot.ref(), snapshot.revisionFingerprint(), List.of(stage),
                new OperationRecoveryPayload(1, Map.of("pve.kind", "journal_projection", "pve.projection", projectionId.toString(), "pve.before", expected,
                        "pve.removed", clear ? before.stream().map(p -> p.projectionId().toString()).sorted().toList() : List.of())),
                (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), prepared.operationId(), "pve", descriptor.id(), snapshot.ref(), descriptor.risk(), request.lifetime(), request.integrityMode(),
                        snapshot.revisionFingerprint(), afterFingerprint, beforeFacts, afterFacts, descriptor.undoable() ? Optional.of(new UndoSpec(SEVER, afterFingerprint, Map.of("value", projectionRef))) : Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
    PreparedEffects effects(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request, final PreparedAction prepared) {
        context.authority().requireValid();
        final UUID projectionId = UUID.fromString((String) prepared.recoveryPayload().fields().get("pve.projection"));
        final String expected = (String) prepared.recoveryPayload().fields().get("pve.before"); final UUID actor = context.authority().actor();
        return new PreparedEffects(WeaverEffectIntent.none(), (action, results, receipt, sequence) -> {
            final boolean sever = request.actionId().equals(SEVER), clear = request.actionId().equals(CLEAR);
            final Set<UUID> removed = clear ? ((List<?>) prepared.recoveryPayload().fields().get("pve.removed")).stream()
                    .map(id -> UUID.fromString((String) id)).collect(java.util.stream.Collectors.toUnmodifiableSet()) : sever ? Set.of(projectionId) : Set.of();
            return new WeaverEffectCommit(sever || clear ? List.of() : List.of(projection(prepared.operationId(), projectionId, sequence, actor, request, snapshot, request.parameters().get("value"), receipt.createdAt())),
                    removed, List.of(), Optional.empty(), Map.of(snapshot.ref(), expected));
        });
    }
    PreparedAction undo(final ProviderContext context, final SubjectSnapshot snapshot, final WeaverReceipt receipt) {
        context.authority().requireValid(); final UndoSpec undo = receipt.undo().orElseThrow(() -> new WeaverDomainRejection("UNDO_UNAVAILABLE"));
        if (!SEVER.equals(undo.actionId()) || !receipt.providerId().equals("pve") || !undo.expectedCurrentFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        return prepare(context, snapshot, new ActionRequest(SEVER, undo.parameters(), context.lifetime(), context.integrityMode()));
    }
    RecoveryAssessment assess(final RecoveryContext context, final SubjectSnapshot snapshot, final WeaverOperationRecord operation) {
        context.authority().require(operation);
        if (!actions.containsKey(operation.request().actionId()) || operation.recoveryPayload().schemaVersion() != 1
                || !"journal_projection".equals(operation.recoveryPayload().fields().get("pve.kind"))) return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "PROJECTION_PLAN_UNAVAILABLE");
        // This provider never mutates a native object before the atomic journal APPLIED publication.
        if (operation.status() == OperationStatus.PREPARED) return new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "NO_PROJECTION_PUBLICATION");
        if (operation.receipt().isPresent() && snapshot.revisionFingerprint().equals(operation.receipt().get().afterFingerprint()))
            return new RecoveryAssessment(ObservedOperationState.APPLIED, false, operation.receipt(), "DURABLE_PROJECTION_OBSERVED");
        return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "PROJECTION_DRIFT");
    }
    private static WeaverProjection projection(final UUID operationId, final UUID projectionId, final long sequence, final UUID actor, final ActionRequest request,
            final SubjectSnapshot snapshot, final WeaverValue value, final long now) {
        final WeaverValue owned = new WeaverValue(value.type(), value.payload(), "pve", FACET, value.sourceCapabilities(), now);
        return new WeaverProjection(projectionId, sequence, "pve", request.actionId(), snapshot.ref(), request.lifetime(),
                new DeveloperInfluence(operationId, request.integrityMode(), request.actionId(), actor, now), Map.of(FIELDS.get(request.actionId()), owned), snapshot.revisionFingerprint(), now, OptionalLong.empty());
    }
    private static String text(final SubjectSnapshot snapshot, final String key) {
        final WeaverValue value = snapshot.facts().get(key);
        if (value == null || !(value.payload().get("value") instanceof String text)) throw new WeaverDomainRejection("REVISION_FIELD_UNAVAILABLE"); return text;
    }
}
