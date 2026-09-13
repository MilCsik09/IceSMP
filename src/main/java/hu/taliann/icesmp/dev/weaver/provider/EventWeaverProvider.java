package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.RewardSource;
import hu.taliann.icesmp.managers.GatheringBuffManager;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** A limited event adapter over the native gathering-window lifecycle; there is no WW event ledger. */
public final class EventWeaverProvider implements WorldWeaverProvider, WeaverSnapshotContributor {
    private static final String FACET = "event.gathering", CATALOG = "event.available", START = "event.start_sandbox", LIVE = "event.start_live", STOP = "event.stop_expected", REVISION = "event.instance";
    private static final WeaverTypeId TYPE = WeaverTypeId.parse("icesmp:gathering_event@1");
    private static final WeaverRevisionScope SCOPE = new WeaverRevisionScope(1, Set.of(REVISION));
    private final GatheringBuffManager nativeEvents;
    private final WeaverValueCatalog catalog;
    private final Map<String, ActionDescriptor> actions;
    public EventWeaverProvider(WeaverTypeRegistry types, GatheringBuffManager nativeEvents) {
        this.nativeEvents = Objects.requireNonNull(nativeEvents);
        types.register(ScalarTypeCodec.reference(TYPE, id -> available().containsKey(id)));
        catalog = new RegistryValueCatalog<>(TYPE, id(), FACET, Set.of(), this::available, value -> Component.text(value.name()), System::currentTimeMillis);
        final var parameter = new ActionParameter("event", Component.text("Esemény"), TYPE, ActionParameter.InputKind.CATALOG, true, Optional.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalInt.empty(), Optional.of(CATALOG), Set.of());
        actions = Map.of(START, action(START, "Sandbox esemény indítása · 120 s", RiskLevel.MUTATING, Set.of(IntegrityMode.SANDBOX), List.of(parameter)),
                LIVE, action(LIVE, "Éles esemény indítása", RiskLevel.CANONICAL, Set.of(IntegrityMode.LIVE_GM), List.of(parameter)),
                STOP, action(STOP, "Megtekintett példány leállítása", RiskLevel.MUTATING, Set.of(IntegrityMode.SANDBOX, IntegrityMode.LIVE_GM), List.of()));
    }
    private Map<String, GatheringBuffManager.GatheringBuff> available() {
        final Map<String, GatheringBuffManager.GatheringBuff> result = new TreeMap<>();
        nativeEvents.available().forEach(buff -> result.put(buff.name().toLowerCase(Locale.ROOT), buff)); return Map.copyOf(result);
    }
    private static ActionDescriptor action(String id, String label, RiskLevel risk, Set<IntegrityMode> modes, List<ActionParameter> parameters) {
        return new ActionDescriptor(id, FACET, Component.text(label), risk, Set.of(Lifetime.ONE_SHOT), modes, Set.of(IntegrityImpact.EVENT_ORIGIN),
                Set.of(WeaverSubjectKind.WORLD), parameters, AreaSupport.NONE, Optional.empty(), false,
                Optional.of("A lezárt eseményt és kiosztott éles bónuszokat nem játssza vissza."), 10, SCOPE);
    }
    @Override public String id() { return "event"; }
    @Override public int contractVersion() { return 1; }
    @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.WORLD); }
    @Override public ProviderContribution contribution() { return new ProviderContribution(List.of(new FacetDescriptor(FACET, Component.text("Gyűjtögető események"), Component.text("Natív, példányhoz kötött eseményvezérlés"), 20)),
            List.copyOf(actions.values()), List.of(new CatalogDescriptor(CATALOG, FACET, Component.text("Elérhető események"), TYPE)), List.of(), List.of(),
            Map.of(START, "event.native_window", LIVE, "event.native_window", STOP, "event.native_window")); }
    @Override public ProviderCoverage coverage() { return new ProviderCoverage("event.gathering", CoverageLevel.FULL_PROVIDER,
            "Four native gathering windows; exact instance stop and native sandbox reward suppression. Other event families are optional.", Set.of(FACET, CATALOG, START, LIVE, STOP)); }
    private static WeaverValue text(String value) { return new WeaverValue(WeaverTypeId.parse("weaver:text@1"), Map.of("value", value), "event", FACET, Set.of(), System.currentTimeMillis()); }
    @Override public Map<String, WeaverValue> captureOnOwner(SubjectRef subject) {
        if (!(subject instanceof WorldRef)) return Map.of();
        if (!org.bukkit.Bukkit.isGlobalTickThread()) throw new WeaverDomainRejection("OWNER_UNAVAILABLE");
        return facts(nativeEvents.activeWindow());
    }
    private static Map<String, WeaverValue> facts(GatheringBuffManager.Window window) { return Map.of(REVISION, text(window == null ? "NONE" : window.instanceId().toString()),
            "event.kind", text(window == null ? "NONE" : window.buff().name()), "event.mode", text(window != null && window.sandbox() ? "SANDBOX" : "LIVE_GM")); }
    @Override public ProviderDiscovery discover(SubjectSnapshot snapshot) {
        final boolean active = !"NONE".equals(value(snapshot, REVISION));
        return new ProviderDiscovery(Set.of(FACET), active ? Set.of(STOP) : Set.of(START, LIVE), Set.of(CATALOG), Set.of(), Set.of(), Map.of());
    }
    @Override public InspectionResult inspect(ProviderContext context, SubjectSnapshot snapshot, String facet) {
        context.authority().requireValid(); if (!FACET.equals(facet)) throw new WeaverDomainRejection("UNKNOWN_FACET");
        final Map<String, WeaverValue> result = new TreeMap<>(); snapshot.facts().forEach((key, value) -> { if (key.startsWith("event.")) result.put(key, value); });
        return new InspectionResult(FACET, result, List.of(Component.text("Sandbox: legfeljebb 120 s; nincs extra drop vagy XP-szorzó; leállítás csak az ellenőrzött példányra.")));
    }
    private static String value(SubjectSnapshot snapshot, String key) { return (String) snapshot.facts().get(key).payload().get("value"); }
    @Override public PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid(); final ActionDescriptor action = actions.get(request.actionId());
        if (action == null || !(snapshot.ref() instanceof WorldRef) || !action.integrityModes().contains(context.integrityMode()) || request.integrityMode() != context.integrityMode()
                || request.lifetime() != Lifetime.ONE_SHOT || context.lifetime() != Lifetime.ONE_SHOT) throw new WeaverDomainRejection("INVALID_EVENT_REQUEST");
        final boolean stop = STOP.equals(action.id());
        if (!request.parameters().keySet().equals(stop ? Set.of() : Set.of("event"))) throw new WeaverDomainRejection("INVALID_PARAMETERS");
        final GatheringBuffManager.GatheringBuff buff;
        if (stop) buff = null; else {
            action.parameters().getFirst().validate(request.parameters().get("event"), context.types()).requireValid();
            buff = available().get((String) request.parameters().get("event").payload().get("id"));
        }
        final String beforeId = value(snapshot, REVISION);
        if (stop == beforeId.equals("NONE") || stop && context.integrityMode() == IntegrityMode.SANDBOX && !value(snapshot, "event.mode").equals("SANDBOX")) throw new WeaverDomainRejection("EVENT_INSTANCE_CONFLICT");
        final UUID operation = UUID.randomUUID(), instance = stop ? UUID.fromString(beforeId) : UUID.randomUUID();
        final Map<String, WeaverValue> after = Map.of(REVISION, text(stop ? "NONE" : instance.toString()));
        final String afterHash = SCOPE.apply(new SubjectSnapshot(snapshot.ref(), snapshot.capturedAt(), snapshot.revisionFingerprint(), after)).revisionFingerprint();
        final ExecutionStage stage = new ExecutionStage("event.lifecycle", new GlobalOwner(), Map.of(), (execution, payload) -> {
            execution.authority().requireValid();
            execution.nativeEffects().orElseThrow().requireAction(id(), action.id(), context.integrityMode(), snapshot.ref());
            final var current = nativeEvents.activeWindow();
            if (!Objects.equals(current == null ? "NONE" : current.instanceId().toString(), beforeId)) throw new WeaverDomainRejection("EVENT_INSTANCE_CONFLICT");
            final boolean applied = stop ? nativeEvents.stopExpected(instance, context.integrityMode() == IntegrityMode.SANDBOX)
                    : nativeEvents.startControlled(instance, buff, context.integrityMode() == IntegrityMode.SANDBOX);
            if (!applied) throw new WeaverDomainRejection("EVENT_INSTANCE_CONFLICT");
            return CompletableFuture.completedFuture(new StageResult(afterHash, after, Map.of()));
        }, Optional.empty(), 5000);
        return new PreparedAction(operation, action, snapshot.ref(), snapshot.revisionFingerprint(), List.of(stage), new OperationRecoveryPayload(1, Map.of("event.instance", instance.toString())),
                (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), operation, id(), action.id(), snapshot.ref(), action.risk(), request.lifetime(), request.integrityMode(),
                        snapshot.revisionFingerprint(), afterHash, Map.of(REVISION, snapshot.facts().get(REVISION)), after, Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
    @Override public PreparedEffects prepareEffects(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request, PreparedAction prepared) {
        final var target = WeaverInfluenceTarget.exact(new RewardSource.Event("gathering", UUID.fromString((String) prepared.recoveryPayload().fields().get("event.instance"))));
        return new PreparedEffects(new WeaverEffectIntent(Set.of(target)), (action, results, receipt, sequence) -> new WeaverEffectCommit(List.of(), Set.of(),
                List.of(WeaverInfluenceRecord.applied(new DeveloperInfluence(action.operationId(), context.integrityMode(), action.descriptor().id(), context.authority().actor(), receipt.createdAt()), target, false)), Optional.empty()));
    }
    @Override public PreparedAction prepareUndo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) { throw new WeaverDomainRejection("ACTION_NOT_UNDOABLE"); }
    @Override public Optional<WeaverValueCatalog> catalog(ProviderContext context, SubjectSnapshot snapshot, String id) { context.authority().requireValid(); return CATALOG.equals(id) ? Optional.of(catalog) : Optional.empty(); }
    @Override public ValueExportResult exportValue(ProviderContext context, SubjectSnapshot snapshot, String id) { return ValueExportResult.rejected("UNKNOWN_EXPORT"); }
    @Override public ImportValidation validateImport(ProviderContext context, SubjectSnapshot snapshot, String id, WeaverValue value) { return ImportValidation.rejected("UNKNOWN_IMPORT"); }
    @Override public RecoveryAssessment assessRecovery(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        if (snapshot.revisionFingerprint().equals(operation.beforeFingerprint())) return new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "Native event is before the recorded transition.");
        if (operation.receipt().filter(receipt -> receipt.afterFingerprint().equals(snapshot.revisionFingerprint())).isPresent())
            return new RecoveryAssessment(ObservedOperationState.APPLIED, false, Optional.empty(), "Exact native instance observed; no event replay.");
        return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "Native instance changed or expired; never stop a replacement.");
    }
}
