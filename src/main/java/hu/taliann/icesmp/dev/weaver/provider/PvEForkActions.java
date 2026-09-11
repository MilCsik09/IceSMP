package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.RewardSource;
import hu.taliann.icesmp.pve.*;
import hu.taliann.icesmp.managers.MobScalingManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Native authored spawn ownership; a fork stays inert until its durable reward denial commits. */
final class PvEForkActions {
    static final String FORK = "pve.fork", CLEANUP = "pve.cleanup_fork", ORIGIN = "pve.fork_origin";
    private final WeaverOwnerRouter owners;
    private final MobScalingManager scaling;
    private final MobAbilityRuntime runtime;
    private final Function<SubjectRef, Map<String, WeaverValue>> snapshots;
    private final Set<UUID> admitted = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> forkEntityByOperation = new ConcurrentHashMap<>();
    private final Map<String, ActionDescriptor> actions;
    PvEForkActions(WeaverOwnerRouter owners, MobScalingManager scaling, MobAbilityRuntime runtime, Function<SubjectRef, Map<String, WeaverValue>> snapshots) {
        this.owners = owners; this.scaling = scaling; this.runtime = runtime; this.snapshots = snapshots;
        actions = Map.of(FORK, descriptor(FORK, "Sandbox Fork · 120 s", IntegrityImpact.TAINT_CREATED), CLEANUP, descriptor(CLEANUP, "Sandbox Fork eltávolítása", IntegrityImpact.TAINT_SUBJECT));
    }
    private static ActionDescriptor descriptor(String id, String label, IntegrityImpact impact) {
        return new ActionDescriptor(id, PvEWeaverProvider.FACET, Component.text(label), RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX),
                Set.of(impact), Set.of(WeaverSubjectKind.ENTITY), List.of(), AreaSupport.NONE, Optional.empty(), false,
                Optional.of("A tesztlény nem persistent; eltávolítás után új Fork készíthető."), 10, PvEProjectionActions.SCOPE);
    }
    List<ActionDescriptor> descriptors() { return List.copyOf(actions.values()); }
    boolean owns(String id) { return actions.containsKey(id); }
    Set<String> visible(SubjectSnapshot snapshot) { return snapshot.facts().containsKey(ORIGIN) ? Set.of(CLEANUP) : Set.of(FORK); }
    private synchronized void admit(UUID operation) {
        if (admitted.size() >= 16) throw new WeaverDomainRejection("FORK_SESSION_CAPACITY"); admitted.add(operation);
    }
    void clearSession() {
        admitted.clear();
        final var nativeSpawns = AuthoredCreatureSpawnService.current();
        final Map<UUID, UUID> created = Map.copyOf(forkEntityByOperation); forkEntityByOperation.clear();
        if (nativeSpawns != null) created.forEach((operation, entity) -> nativeSpawns.cleanupSandboxFork(entity, operation));
    }
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid(); final ActionDescriptor action = actions.get(request.actionId());
        if (action == null || !(snapshot.ref() instanceof EntityRef ref) || request.integrityMode() != IntegrityMode.SANDBOX || context.integrityMode() != IntegrityMode.SANDBOX
                || request.lifetime() != Lifetime.ONE_SHOT || context.lifetime() != Lifetime.ONE_SHOT || !request.parameters().isEmpty()) throw new WeaverDomainRejection("INVALID_FORK_REQUEST");
        final boolean cleanup = CLEANUP.equals(action.id());
        if (cleanup && !snapshot.facts().containsKey(ORIGIN) || !cleanup && snapshot.facts().containsKey(ORIGIN)) throw new WeaverDomainRejection("FORK_SUBJECT_INVALID");
        final UUID operation = UUID.randomUUID();
        final UUID group = cleanup ? UUID.fromString((String) snapshot.facts().get(ORIGIN).payload().get("value")) : operation;
        if (!cleanup) admit(operation);
        final ExecutionStage stage = new ExecutionStage("pve.fork.native", new EntityOwner(ref.entityId()), Map.of(), (execution, payload) -> {
            execution.authority().requireValid(); execution.nativeEffects().orElseThrow().requireAction("pve", action.id(), IntegrityMode.SANDBOX, ref);
            final var live = Bukkit.getEntity(ref.entityId());
            if (!(live instanceof Mob mob) || !Bukkit.isOwnedByCurrentRegion(mob) || !mob.isValid() || mob.isDead()) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
            final var fresh = PvEProjectionActions.SCOPE.apply(new SubjectSnapshot(ref, System.currentTimeMillis(), snapshot.revisionFingerprint(), snapshots.apply(ref)));
            if (!snapshot.revisionFingerprint().equals(fresh.revisionFingerprint())) throw new WeaverDomainRejection("STALE_SUBJECT");
            final AuthoredCreatureSpawnService nativeSpawns = Objects.requireNonNull(AuthoredCreatureSpawnService.current());
            if (cleanup) {
                if (AuthoredCreatureSpawnService.sandboxEventOrigin(mob).filter(origin -> origin.instanceId().equals(group)).isEmpty()) throw new WeaverDomainRejection("FORK_ORIGIN_CONFLICT");
                nativeSpawns.cleanupSandboxFork(ref.entityId(), group); admitted.remove(group); forkEntityByOperation.remove(group);
                return CompletableFuture.completedFuture(new StageResult(snapshot.revisionFingerprint(), Map.of(), Map.of()));
            }
            if (!admitted.contains(operation)) throw new WeaverDomainRejection("SESSION_ENDED");
            final var location = mob.getLocation().add(2, 0, 0);
            if (!Bukkit.isOwnedByCurrentRegion(location) || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) throw new WeaverDomainRejection("FORK_LOCATION_UNAVAILABLE");
            final String template = scaling.getTemplateId(mob);
            final var spawn = template == null || template.isBlank()
                    ? AuthoredCreatureSpawnService.Request.generic("world_weaver", group.toString(), "fork", mob.getType(), scaling.getLevel(mob), scaling.getRank(mob), scaling.getArchetypeId(mob), AuthoredCreatureSpawnService.RewardOwner.NONE, true, 2400)
                    : AuthoredCreatureSpawnService.Request.template("world_weaver", group.toString(), "fork", template, scaling.getLevel(mob), AuthoredCreatureSpawnService.RewardOwner.NONE, true, 1, 1, 2400);
            final Mob fork = nativeSpawns.spawn(location, spawn);
            if (fork == null) throw new WeaverDomainRejection("FORK_SPAWN_REJECTED");
            forkEntityByOperation.put(operation, fork.getUniqueId());
            fork.setAI(false); fork.setInvulnerable(true); runtime.pause(fork);
            if (!admitted.contains(operation)) { nativeSpawns.cleanupSandboxFork(fork.getUniqueId(), group); forkEntityByOperation.remove(operation); throw new WeaverDomainRejection("SESSION_ENDED"); }
            final WeaverValue created = PvEWeaverProvider.scalar("uuid", fork.getUniqueId().toString(), System.currentTimeMillis());
            return CompletableFuture.completedFuture(new StageResult(snapshot.revisionFingerprint(), Map.of(WeaverOperationScope.CREATED_ENTITY, created), Map.of()));
        }, Optional.empty(), 5000);
        return new PreparedAction(operation, action, ref, snapshot.revisionFingerprint(), List.of(stage), new OperationRecoveryPayload(1, Map.of("pve.fork_group", group.toString())),
                (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), operation, "pve", action.id(), ref, action.risk(), request.lifetime(), request.integrityMode(),
                        snapshot.revisionFingerprint(), results.getLast().afterFingerprint(), Map.of(), results.getLast().facts(), Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
    PreparedEffects effects(ProviderContext context, SubjectSnapshot snapshot, PreparedAction prepared) {
        final var target = FORK.equals(prepared.descriptor().id()) ? WeaverInfluenceTarget.exact(new RewardSource.Event("world_weaver", prepared.operationId())) : WeaverInfluenceTarget.subject(snapshot.ref());
        return new PreparedEffects(new WeaverEffectIntent(Set.of(target)), (action, results, receipt, sequence) -> WeaverEffectCommit.none());
    }
    CompletionStage<Void> afterCommit(WeaverReceipt receipt) {
        if (!FORK.equals(receipt.actionId())) return CompletableFuture.completedFuture(null);
        final UUID entity = UUID.fromString((String) receipt.after().get(WeaverOperationScope.CREATED_ENTITY).payload().get("value"));
        return owners.submit(new EntityOwner(entity), hu.taliann.icesmp.security.HiddenDevAuthority.PRIMARY_DEVELOPER, java.time.Duration.ofSeconds(5), () -> {
            final var nativeSpawns = AuthoredCreatureSpawnService.current(); final var live = Bukkit.getEntity(entity);
            if (!admitted.contains(receipt.operationId())) { if (nativeSpawns != null) nativeSpawns.cleanupSandboxFork(entity, receipt.operationId()); return CompletableFuture.<Void>completedFuture(null); }
            if (!(live instanceof Mob mob) || !Bukkit.isOwnedByCurrentRegion(mob) || AuthoredCreatureSpawnService.sandboxEventOrigin(mob).filter(origin -> origin.instanceId().equals(receipt.operationId())).isEmpty()) throw new WeaverDomainRejection("FORK_UNAVAILABLE");
            mob.setInvulnerable(false); mob.setAI(true); runtime.resume(mob); return CompletableFuture.<Void>completedFuture(null);
        }).handle((done, failure) -> {
            if (failure != null) { final var nativeSpawns = AuthoredCreatureSpawnService.current(); if (nativeSpawns != null) nativeSpawns.cleanupSandboxFork(entity, receipt.operationId()); admitted.remove(receipt.operationId()); forkEntityByOperation.remove(receipt.operationId()); }
            return null;
        });
    }
    RecoveryAssessment assess(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "Forks are nonpersistent and expire; uncertain spawn/cleanup is never replayed.");
    }
}
