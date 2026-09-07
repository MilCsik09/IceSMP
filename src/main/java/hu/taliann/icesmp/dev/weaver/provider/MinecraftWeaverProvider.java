package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Vanilla snapshots and recipient-only feedback have no canonical reward/progression side effect. */
public final class MinecraftWeaverProvider implements WorldWeaverProvider {
    private static final String FACET = "minecraft.runtime";
    private static final WeaverTypeId LOCATION = WeaverTypeId.parse("weaver:location@1");
    private static final String LOCATION_EXPORT = "minecraft.location_thread";
    private final Map<String, WeaverValueCatalog> catalogs;
    private final ActionDescriptor pulse;
    private final ProviderContribution contribution;
    public MinecraftWeaverProvider(final WeaverTypeRegistry types) {
        final Map<String, WeaverValueCatalog> catalogs = new LinkedHashMap<>();
        registerCatalog(types, catalogs, "minecraft.materials", WeaverTypeId.parse("minecraft:material@1"), () -> {
            final Map<String, Material> values = new TreeMap<>();
            for (final Material material : Material.values()) if (!material.isLegacy() && !material.isAir()) values.put(material.getKey().toString(), material);
            return Map.copyOf(values);
        });
        registerCatalog(types, catalogs, "minecraft.gamemodes", WeaverTypeId.parse("minecraft:gamemode@1"), () -> {
            final Map<String, GameMode> values = new TreeMap<>();
            for (final GameMode mode : GameMode.values()) values.put(mode.name().toLowerCase(Locale.ROOT), mode);
            return Map.copyOf(values);
        });
        this.catalogs = Map.copyOf(catalogs);
        final Set<WeaverSubjectKind> subjects = EnumSet.allOf(WeaverSubjectKind.class); subjects.remove(WeaverSubjectKind.AREA);
        pulse = new ActionDescriptor("minecraft.pulse", FACET, Component.text("Artifact visszajelzés"), RiskLevel.SAFE,
                Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX, IntegrityMode.LIVE_GM), Set.of(IntegrityImpact.NONE), subjects,
                List.of(new ActionParameter("count", Component.text("Részecskék száma"), WeaverTypeId.parse("weaver:int@1"), ActionParameter.InputKind.INTEGER,
                        true, Optional.of(scalar("int", 6)), OptionalDouble.of(1), OptionalDouble.of(16), OptionalInt.empty(), Optional.empty(), Set.of()),
                        new ActionParameter("pitch", Component.text("Hangmagasság"), WeaverTypeId.parse("weaver:double@1"), ActionParameter.InputKind.DECIMAL,
                                true, Optional.of(scalar("double", 1.0D)), OptionalDouble.of(0.5), OptionalDouble.of(2), OptionalInt.empty(), Optional.empty(), Set.of())),
                AreaSupport.NONE, Optional.empty(), false, Optional.empty(), 1);
        contribution = new ProviderContribution(List.of(new FacetDescriptor(FACET, Component.text("Minecraft"), Component.text("Natív runtime állapot"), 0)),
                List.of(pulse), this.catalogs.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry ->
                        new CatalogDescriptor(entry.getKey(), FACET, Component.text(entry.getKey()), entry.getValue().type())).toList(),
                List.of(new ExportDescriptor(LOCATION_EXPORT, FACET, LOCATION, Set.of("minecraft.location"))), List.of(), Map.of());
    }
    private static <T> void registerCatalog(final WeaverTypeRegistry types, final Map<String, WeaverValueCatalog> catalogs, final String id,
                                             final WeaverTypeId type, final Supplier<Map<String, T>> registry) {
        types.register(ScalarTypeCodec.reference(type, key -> registry.get().containsKey(key)));
        catalogs.put(id, new RegistryValueCatalog<>(type, "minecraft", FACET, Set.of(), registry, value -> Component.text(value.toString()), System::currentTimeMillis));
    }
    private static WeaverValue scalar(final String type, final Object value) {
        return new WeaverValue(new WeaverTypeId("weaver", type, 1), Map.of("value", value), "minecraft", FACET, Set.of(), 0);
    }
    @Override public String id() { return "minecraft"; }
    @Override public int contractVersion() { return 1; }
    @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.copyOf(EnumSet.allOf(WeaverSubjectKind.class)); }
    @Override public ProviderContribution contribution() { return contribution; }
    @Override public ProviderCoverage coverage() {
        return new ProviderCoverage("minecraft", CoverageLevel.FULL_PROVIDER,
                "Initial published snapshot/catalog/location export and recipient-only feedback surface. This declaration does not close the independent WW-00 full vanilla manipulation blocker; durable and influence-dependent actions remain gated by later implementation.",
                Set.of(FACET, pulse.id(), LOCATION_EXPORT, "minecraft.materials", "minecraft.gamemodes"));
    }
    @Override public ProviderDiscovery discover(final SubjectSnapshot snapshot) {
        return new ProviderDiscovery(Set.of(FACET), pulse.subjects().contains(snapshot.ref().kind()) ? Set.of(pulse.id()) : Set.of(),
                catalogs.keySet(), snapshot.facts().containsKey("minecraft.location") ? Set.of(LOCATION_EXPORT) : Set.of(), Set.of(), Map.of());
    }
    @Override public InspectionResult inspect(final ProviderContext context, final SubjectSnapshot snapshot, final String facetId) {
        context.authority().requireValid();
        if (!FACET.equals(facetId)) throw new WeaverDomainRejection("UNKNOWN_FACET");
        final Map<String, WeaverValue> facts = new TreeMap<>(); snapshot.facts().forEach((key, value) -> { if (key.startsWith("minecraft.")) facts.put(key, value); });
        return new InspectionResult(FACET, facts, List.of());
    }
    @Override public PreparedAction prepare(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request) {
        context.authority().requireValid();
        if (!pulse.id().equals(request.actionId()) || request.lifetime() != Lifetime.ONE_SHOT || !pulse.subjects().contains(snapshot.ref().kind())
                || request.integrityMode() != context.integrityMode() || !request.parameters().keySet().equals(Set.of("count", "pitch"))) throw new WeaverDomainRejection("INVALID_ACTION_REQUEST");
        pulse.parameters().forEach(parameter -> parameter.validate(request.parameters().get(parameter.id()), context.types()).requireValid());
        final int count = ((Number) request.parameters().get("count").payload().get("value")).intValue();
        final double pitch = ((Number) request.parameters().get("pitch").payload().get("value")).doubleValue();
        final ExecutionStage stage = new ExecutionStage("minecraft.pulse.feedback", new ActorOwner(), Map.of("count", count, "pitch", pitch), (execution, payload) -> {
            execution.authority().requireValid();
            final var actor = Bukkit.getPlayer(execution.authority().actor());
            if (actor == null || !Bukkit.isOwnedByCurrentRegion(actor) || !actor.isOnline()) throw new WeaverDomainRejection("OWNER_UNAVAILABLE");
            actor.spawnParticle(Particle.END_ROD, actor.getLocation().add(0, 1, 0), ((Number) payload.get("count")).intValue(), 0.2, 0.4, 0.2, 0.01);
            actor.playSound(actor.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, ((Number) payload.get("pitch")).floatValue());
            return CompletableFuture.completedFuture(new StageResult(execution.snapshot().revisionFingerprint(), Map.of(), Map.of()));
        }, Optional.empty(), 5000);
        return new PreparedAction(UUID.randomUUID(), pulse, snapshot.ref(), snapshot.revisionFingerprint(), List.of(stage),
                new OperationRecoveryPayload(1, Map.of()), (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), prepared.operationId(), id(), pulse.id(),
                        snapshot.ref(), pulse.risk(), request.lifetime(), request.integrityMode(), snapshot.revisionFingerprint(),
                        results.getLast().afterFingerprint(), Map.of(), Map.of(), Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
    @Override public PreparedAction prepareUndo(final ProviderContext context, final SubjectSnapshot snapshot, final WeaverReceipt receipt) {
        context.authority().requireValid(); throw new WeaverDomainRejection("ACTION_NOT_UNDOABLE");
    }
    @Override public Optional<WeaverValueCatalog> catalog(final ProviderContext context, final SubjectSnapshot snapshot, final String id) {
        context.authority().requireValid(); return Optional.ofNullable(catalogs.get(id));
    }
    @Override public ValueExportResult exportValue(final ProviderContext context, final SubjectSnapshot snapshot, final String id) {
        context.authority().requireValid();
        if (!LOCATION_EXPORT.equals(id)) return ValueExportResult.rejected("UNKNOWN_EXPORT");
        final WeaverValue location = snapshot.facts().get("minecraft.location");
        return location == null ? ValueExportResult.rejected("LOCATION_UNAVAILABLE") : ValueExportResult.exported(new WeaverValue(LOCATION, location.payload(),
                id(), FACET, Set.of("minecraft.location"), snapshot.capturedAt()));
    }
    @Override public ImportValidation validateImport(final ProviderContext context, final SubjectSnapshot snapshot, final String id, final WeaverValue value) {
        context.authority().requireValid(); return ImportValidation.rejected("NO_REGISTERED_IMPORTER");
    }
    @Override public RecoveryAssessment assessRecovery(final RecoveryContext context, final SubjectSnapshot snapshot, final WeaverOperationRecord operation) {
        return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "No journal action is registered by the initial adapter");
    }
}
