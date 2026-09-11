package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.WeaverBindingBudget;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Particle;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.*;

/** Session controls live in the kernel; this provider owns only the AREA-enter -> VFX recipe. */
public final class DeveloperWeaverProvider implements WorldWeaverProvider, WeaverSnapshotContributor, WeaverProjectionProvider, Listener {
    private static final String FACET = "developer.self", CONSUMER = "developer.area_enter_vfx", FIELD = "developer.binding";
    private static final String CREATE = "developer.bind_area_vfx", CLEAR = "developer.clear_area_bindings", REVISION = "developer.binding_revision";
    private static final WeaverTypeId BOOLEAN = WeaverTypeId.parse("weaver:boolean@1");
    private static final WeaverRevisionScope SCOPE = new WeaverRevisionScope(1, Set.of(REVISION));
    private final WeaverProjectionSource source;
    private JavaPlugin plugin;
    private volatile long retiredBefore = -1;
    private final Map<String, ActionDescriptor> actions;
    private final Map<AreaRef, List<WeaverProjection>> active = new ConcurrentHashMap<>();
    private final Map<UUID, WeaverBindingBudget> budgets = new ConcurrentHashMap<>();
    public DeveloperWeaverProvider(WeaverProviderServices services, JavaPlugin plugin) {
        this(services.projections());
        this.plugin = Objects.requireNonNull(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    DeveloperWeaverProvider(WeaverProjectionSource source) {
        this.source = Objects.requireNonNull(source);
        actions = Map.of(CREATE, descriptor(CREATE, "AREA belépés → fényjel", Lifetime.SESSION), CLEAR, descriptor(CLEAR, "AREA kötések elvágása", Lifetime.ONE_SHOT));
    }
    private static ActionDescriptor descriptor(String id, String label, Lifetime lifetime) {
        return new ActionDescriptor(id, FACET, Component.text(label), RiskLevel.SAFE, Set.of(lifetime), Set.of(IntegrityMode.SANDBOX),
                Set.of(IntegrityImpact.TAINT_SUBJECT), Set.of(WeaverSubjectKind.AREA), List.of(), AreaSupport.SPATIAL,
                Optional.of(new AreaLimits(0, 0, 9, 9, 1)), false, Optional.empty(), 1, SCOPE);
    }
    @Override public void clearSession() { retiredBefore = System.currentTimeMillis(); active.clear(); budgets.clear(); }
    @Override public String id() { return "developer"; }
    @Override public int contractVersion() { return 1; }
    @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.PLAYER, WeaverSubjectKind.AREA); }
    @Override public ProviderContribution contribution() {
        return new ProviderContribution(List.of(new FacetDescriptor(FACET, Component.text("Fejlesztő / Kötések"), Component.text("Session és korlátos AREA fényjel"), 0)),
                List.copyOf(actions.values()), List.of(), List.of(), List.of(), Map.of(CREATE, "developer.journal_binding", CLEAR, "developer.journal_binding"));
    }
    @Override public ProviderCoverage coverage() { return new ProviderCoverage("developer_self", CoverageLevel.FULL_PROVIDER,
            "Kernel-owned session/mode/arming/history and journal-owned bounded AREA-enter VFX binding.", Set.of(FACET, CREATE, CLEAR)); }
    private List<WeaverProjection> bindings(SubjectRef ref) { return source.active(CONSUMER, ref, System.currentTimeMillis()); }
    @Override public Map<String, WeaverValue> captureOnOwner(SubjectRef ref) {
        if (ref instanceof AreaRef) return Map.of(REVISION, text(WeaverProjectionFingerprint.of(bindings(ref))));
        return Map.of();
    }
    private static WeaverValue text(String text) { return new WeaverValue(WeaverTypeId.parse("weaver:text@1"), Map.of("value", text), "developer", FACET, Set.of(), System.currentTimeMillis()); }
    @Override public ProviderDiscovery discover(SubjectSnapshot snapshot) {
        if (snapshot.ref() instanceof PlayerRef player && !player.playerId().equals(hu.taliann.icesmp.security.HiddenDevAuthority.PRIMARY_DEVELOPER))
            return new ProviderDiscovery(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of());
        return new ProviderDiscovery(Set.of(FACET), snapshot.ref() instanceof AreaRef ? actions.keySet() : Set.of(), Set.of(), Set.of(), Set.of(), Map.of());
    }
    @Override public InspectionResult inspect(ProviderContext context, SubjectSnapshot snapshot, String facet) {
        context.authority().requireValid(); if (!FACET.equals(facet)) throw new WeaverDomainRejection("UNKNOWN_FACET");
        return new InspectionResult(FACET, Map.of("developer.session", text(context.authority().session().toString()), "developer.mode", text(context.integrityMode().name())),
                List.of(Component.text("A Fejlesztő gombon: mód, élesítés, Szálak, bizonylatok és recovery állapot."),
                        Component.text("Kötés: SANDBOX; 16 futás; 2 s cooldown; 120 s; nincs rekurzió vagy gameplay módosítás.")));
    }
    @Override public PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid(); final ActionDescriptor action = actions.get(request.actionId());
        if (action == null || !(snapshot.ref() instanceof AreaRef) || request.integrityMode() != IntegrityMode.SANDBOX || context.integrityMode() != IntegrityMode.SANDBOX
                || request.lifetime() != context.lifetime() || !action.lifetimes().contains(request.lifetime()) || !request.parameters().isEmpty()) throw new WeaverDomainRejection("INVALID_BINDING_REQUEST");
        if (CREATE.equals(action.id()) && active.size() >= 16) throw new WeaverDomainRejection("BINDING_CAPACITY");
        final List<WeaverProjection> before = bindings(snapshot.ref());
        if (CREATE.equals(action.id()) && !before.isEmpty()) throw new WeaverDomainRejection("AREA_ALREADY_BOUND");
        if (!SCOPE.apply(snapshot).revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("STALE_SUBJECT");
        final UUID operation = UUID.randomUUID(), projection = UUID.randomUUID(); final long now = System.currentTimeMillis();
        final List<WeaverProjection> after = CREATE.equals(action.id()) ? List.of(projection(context, snapshot.ref(), operation, projection, now, 1, now + 120_000, snapshot.revisionFingerprint())) : List.of();
        final Map<String, WeaverValue> afterFacts = Map.of(REVISION, text(WeaverProjectionFingerprint.of(after)));
        final ExecutionStage stage = new ExecutionStage("developer.binding.observe", new GlobalOwner(), Map.of(), (execution, payload) -> {
            execution.authority().requireValid();
            if (!WeaverProjectionFingerprint.of(bindings(snapshot.ref())).equals(WeaverProjectionFingerprint.of(before))) throw new WeaverDomainRejection("CONFLICT");
            return CompletableFuture.completedFuture(new StageResult(SCOPE.apply(new SubjectSnapshot(snapshot.ref(), now, snapshot.revisionFingerprint(), afterFacts)).revisionFingerprint(), afterFacts, Map.of()));
        }, Optional.empty(), 5000);
        return new PreparedAction(operation, action, snapshot.ref(), snapshot.revisionFingerprint(), List.of(stage),
                new OperationRecoveryPayload(1, Map.of("developer.projection", projection.toString(), "developer.created", now, "developer.removed", before.stream().map(p -> p.projectionId().toString()).toList())),
                (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), operation, id(), action.id(), snapshot.ref(), action.risk(), request.lifetime(), request.integrityMode(),
                        snapshot.revisionFingerprint(), results.getLast().afterFingerprint(), Map.of(REVISION, snapshot.facts().get(REVISION)), afterFacts, Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
    private static WeaverProjection projection(ProviderContext context, SubjectRef subject, UUID operation, UUID id, long created, long sequence, long expires, String before) {
        return new WeaverProjection(id, sequence, "developer", CREATE, subject, Lifetime.SESSION, new DeveloperInfluence(operation, context.integrityMode(), CREATE, context.authority().actor(), created),
                Map.of(FIELD, new WeaverValue(BOOLEAN, Map.of("value", true), "developer", FACET, Set.of(), created)), before, created, OptionalLong.of(expires));
    }
    @Override public PreparedEffects prepareEffects(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request, PreparedAction prepared) {
        return new PreparedEffects(new WeaverEffectIntent(Set.of(hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceTarget.subject(snapshot.ref()))), (action, results, receipt, sequence) -> {
            final Map<String, Object> data = action.recoveryPayload().fields();
            final Set<UUID> removed = new HashSet<>(); for (Object id : (List<?>) data.get("developer.removed")) removed.add(UUID.fromString((String) id));
            return new WeaverEffectCommit(CREATE.equals(action.descriptor().id()) ? List.of(projection(context, snapshot.ref(), action.operationId(), UUID.fromString((String) data.get("developer.projection")), receipt.createdAt(), sequence, ((Number) data.get("developer.created")).longValue() + 120_000, snapshot.revisionFingerprint())) : List.of(),
                    removed, List.of(), Optional.empty(), Map.of(snapshot.ref(), (String) snapshot.facts().get(REVISION).payload().get("value")));
        }, CREATE.equals(request.actionId()) ? Optional.of(Map.of(snapshot.ref(), 1)) : Optional.empty());
    }
    @Override public List<ProjectionConsumerDescriptor> projectionConsumers() { return List.of(new ProjectionConsumerDescriptor(CONSUMER, id(), getClass().getName() + "#entered",
            Set.of(CREATE), Set.of(WeaverSubjectKind.AREA), Map.of(FIELD, BOOLEAN), Set.of("gameplay", "rewards"))); }
    @Override public CompletionStage<Void> reconcileProjections(Set<SubjectRef> subjects) {
        for (SubjectRef subject : subjects) if (subject instanceof AreaRef area) {
            final List<WeaverProjection> live = bindings(area).stream().filter(p -> p.createdAt() > retiredBefore).toList();
            if (live.isEmpty()) active.remove(area); else active.put(area, live);
        }
        final Set<UUID> ids = new HashSet<>(); active.values().forEach(list -> list.forEach(p -> ids.add(p.projectionId()))); budgets.keySet().retainAll(ids);
        return CompletableFuture.completedFuture(null);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void entered(PlayerMoveEvent event) {
        entered(event.getPlayer(), event.getFrom(), event.getTo());
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void teleported(org.bukkit.event.player.PlayerTeleportEvent event) { entered(event); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void vehicleMoved(org.bukkit.event.vehicle.VehicleMoveEvent event) {
        final var from = event.getFrom().clone();
        for (final var passenger : event.getVehicle().getPassengers()) if (passenger instanceof org.bukkit.entity.Player player)
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline()) entered(player, from, player.getLocation());
            }, () -> { });
    }
    private void entered(org.bukkit.entity.Player player, org.bukkit.Location from, org.bukkit.Location to) {
        if (to == null) return;
        final long now = System.currentTimeMillis();
        for (final var entry : active.entrySet()) {
            final AreaRef area = entry.getKey();
            if (!area.worldId().equals(to.getWorld().getUID()) || !area.shape().contains(to.getBlockX(), to.getBlockY(), to.getBlockZ())
                    || area.worldId().equals(from.getWorld().getUID()) && area.shape().contains(from.getBlockX(), from.getBlockY(), from.getBlockZ())) continue;
            final List<WeaverProjection> live;
            try { live = bindings(area); } catch (WeaverDomainRejection unavailable) { return; }
            for (final var binding : live) if (binding.createdAt() > retiredBefore && binding.activeAt(now) && budgets.computeIfAbsent(binding.projectionId(), ignored -> new WeaverBindingBudget(binding.createdAt())).tryFire(now, 0))
                player.spawnParticle(Particle.END_ROD, to.clone().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.01);
        }
    }
    @Override public PreparedAction prepareUndo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) { throw new WeaverDomainRejection("ACTION_NOT_UNDOABLE"); }
    @Override public Optional<WeaverValueCatalog> catalog(ProviderContext context, SubjectSnapshot snapshot, String id) { return Optional.empty(); }
    @Override public ValueExportResult exportValue(ProviderContext context, SubjectSnapshot snapshot, String id) { return ValueExportResult.rejected("UNKNOWN_EXPORT"); }
    @Override public ImportValidation validateImport(ProviderContext context, SubjectSnapshot snapshot, String id, WeaverValue value) { return ImportValidation.rejected("UNKNOWN_IMPORT"); }
    @Override public RecoveryAssessment assessRecovery(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        return new RecoveryAssessment(operation.status() == OperationStatus.PREPARED ? ObservedOperationState.BEFORE : operation.receipt().filter(r -> r.afterFingerprint().equals(snapshot.revisionFingerprint())).isPresent()
                ? ObservedOperationState.APPLIED : ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "Journal projection only; VFX signals are never replayed.");
    }
}
