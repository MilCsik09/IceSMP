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
    private final Map<String, WeaverValueCatalog> catalogs;
    private final ActionDescriptor pulse;
    private final ActionDescriptor gamemode;
    private static final WeaverRevisionScope MODE_SCOPE = new WeaverRevisionScope(1, Set.of("minecraft.gamemode"));
    private final ProviderContribution contribution;
    public MinecraftWeaverProvider(final WeaverTypeRegistry types) {
        final Map<String, WeaverValueCatalog> catalogs = new LinkedHashMap<>();
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
        gamemode = new ActionDescriptor("minecraft.set_gamemode", FACET, Component.text("Játékmód módosítása"), RiskLevel.CANONICAL,
                Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.LIVE_GM), Set.of(IntegrityImpact.NONE), Set.of(WeaverSubjectKind.PLAYER),
                List.of(new ActionParameter("mode", Component.text("Játékmód"), WeaverTypeId.parse("minecraft:gamemode@1"), ActionParameter.InputKind.CATALOG,
                        true, Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalInt.empty(), Optional.of("minecraft.gamemodes"), Set.of())),
                AreaSupport.NONE, Optional.empty(), false, Optional.of("Az éles játékmód hatásai nem vonhatók vissza automatikusan."), 10, MODE_SCOPE);
        contribution = new ProviderContribution(List.of(new FacetDescriptor(FACET, Component.text("Minecraft"), Component.text("Natív runtime állapot"), 0)),
                List.of(pulse, gamemode), this.catalogs.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry ->
                        new CatalogDescriptor(entry.getKey(), FACET, Component.text(entry.getKey()), entry.getValue().type())).toList(),
                List.of(), List.of(), Map.of(gamemode.id(), "minecraft.observed_gamemode"));
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
                "Native subject inspection, recipient feedback and guarded LIVE_GM game mode control.",
                Set.of(FACET, pulse.id(), gamemode.id(), "minecraft.gamemodes"));
    }
    @Override public ProviderDiscovery discover(final SubjectSnapshot snapshot) {
        final Set<String> visible = new HashSet<>();
        if (pulse.subjects().contains(snapshot.ref().kind())) visible.add(pulse.id());
        if (snapshot.ref() instanceof PlayerRef) visible.add(gamemode.id());
        return new ProviderDiscovery(Set.of(FACET), visible, snapshot.ref() instanceof PlayerRef ? catalogs.keySet() : Set.of(), Set.of(), Set.of(), Map.of());
    }
    @Override public InspectionResult inspect(final ProviderContext context, final SubjectSnapshot snapshot, final String facetId) {
        context.authority().requireValid();
        if (!FACET.equals(facetId)) throw new WeaverDomainRejection("UNKNOWN_FACET");
        final Map<String, WeaverValue> facts = new TreeMap<>(); snapshot.facts().forEach((key, value) -> { if (key.startsWith("minecraft.")) facts.put(key, value); });
        return new InspectionResult(FACET, facts, List.of());
    }
    @Override public PreparedAction prepare(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request) {
        context.authority().requireValid();
        if (gamemode.id().equals(request.actionId())) return prepareMode(context, snapshot, request);
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
    private PreparedAction prepareMode(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        if (!(snapshot.ref() instanceof PlayerRef target) || request.integrityMode() != IntegrityMode.LIVE_GM || context.integrityMode() != IntegrityMode.LIVE_GM
                || request.lifetime() != Lifetime.ONE_SHOT || context.lifetime() != Lifetime.ONE_SHOT || !request.parameters().keySet().equals(Set.of("mode"))) throw new WeaverDomainRejection("INVALID_ACTION_REQUEST");
        gamemode.parameters().getFirst().validate(request.parameters().get("mode"), context.types()).requireValid();
        final GameMode requested = GameMode.valueOf(((String) request.parameters().get("mode").payload().get("id")).toUpperCase(Locale.ROOT));
        final String before = (String) snapshot.facts().get("minecraft.gamemode").payload().get("value");
        final Map<String, WeaverValue> after = Map.of("minecraft.gamemode", scalar("text", requested.name()));
        final String hash = MODE_SCOPE.apply(new SubjectSnapshot(target, snapshot.capturedAt(), snapshot.revisionFingerprint(), after)).revisionFingerprint();
        final var stage = new ExecutionStage("minecraft.mode.native", new EntityOwner(target.playerId()), Map.of(), (execution, payload) -> {
            execution.authority().requireValid(); final var player = Bukkit.getPlayer(target.playerId());
            if (player == null || !Bukkit.isOwnedByCurrentRegion(player) || !player.getGameMode().name().equals(before)) throw new WeaverDomainRejection("STALE_SUBJECT");
            player.setGameMode(requested);
            if (player.getGameMode() != requested) throw new WeaverDomainRejection("GAME_MODE_CANCELLED");
            return CompletableFuture.completedFuture(new StageResult(hash, after, Map.of()));
        }, Optional.empty(), 5000);
        return new PreparedAction(UUID.randomUUID(), gamemode, target, snapshot.revisionFingerprint(), List.of(stage), new OperationRecoveryPayload(1, Map.of()),
                (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), prepared.operationId(), id(), gamemode.id(), target, gamemode.risk(), request.lifetime(), request.integrityMode(),
                        snapshot.revisionFingerprint(), hash, Map.of("minecraft.gamemode", snapshot.facts().get("minecraft.gamemode")), after, Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
    @Override public PreparedEffects prepareEffects(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request, PreparedAction prepared) {
        if (!gamemode.id().equals(request.actionId())) throw new WeaverDomainRejection("UNKNOWN_ACTION");
        return new PreparedEffects(hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent.none(), (action, results, receipt, sequence) -> WeaverEffectCommit.none());
    }
    @Override public PreparedAction prepareUndo(final ProviderContext context, final SubjectSnapshot snapshot, final WeaverReceipt receipt) {
        context.authority().requireValid(); throw new WeaverDomainRejection("ACTION_NOT_UNDOABLE");
    }
    @Override public Optional<WeaverValueCatalog> catalog(final ProviderContext context, final SubjectSnapshot snapshot, final String id) {
        context.authority().requireValid(); return Optional.ofNullable(catalogs.get(id));
    }
    @Override public ValueExportResult exportValue(final ProviderContext context, final SubjectSnapshot snapshot, final String id) {
        context.authority().requireValid();
        return ValueExportResult.rejected("UNKNOWN_EXPORT");
    }
    @Override public ImportValidation validateImport(final ProviderContext context, final SubjectSnapshot snapshot, final String id, final WeaverValue value) {
        context.authority().requireValid(); return ImportValidation.rejected("NO_REGISTERED_IMPORTER");
    }
    @Override public RecoveryAssessment assessRecovery(final RecoveryContext context, final SubjectSnapshot snapshot, final WeaverOperationRecord operation) {
        context.authority().require(operation);
        return new RecoveryAssessment(snapshot.revisionFingerprint().equals(operation.beforeFingerprint()) ? ObservedOperationState.BEFORE
                : operation.receipt().filter(r -> r.afterFingerprint().equals(snapshot.revisionFingerprint())).isPresent() ? ObservedOperationState.APPLIED : ObservedOperationState.PARTIAL_OR_CONFLICT,
                false, Optional.empty(), "Observed native game mode; never replay an uncertain change.");
    }
}
