package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.WorldWeaverProviderRegistry;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.*;
import java.util.concurrent.*;

/** Startup observes current state and settles a journal; it never calls prepare or replays a stage. */
public final class WeaverRecoveryCoordinator {
    public enum PendingReason { PENDING_ENTITY_LOAD, PENDING_CHUNK_LOAD, UNRESOLVED_WORLD, STALE_UNRESOLVED }
    private final WeaverJournal journal;
    private final SubjectSnapshotSource snapshots;
    private final WorldWeaverProviderRegistry providers;
    private final WeaverTypeRegistry types;
    private final hu.taliann.icesmp.dev.weaver.area.WeaverAreaEngine areas;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingReason> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicLong availabilityRevision = new java.util.concurrent.atomic.AtomicLong();
    public WeaverRecoveryCoordinator(final WeaverJournal journal, final SubjectSnapshotSource snapshots,
                                     final WorldWeaverProviderRegistry providers, final WeaverTypeRegistry types) {
        this(journal, snapshots, providers, types, null);
    }
    public WeaverRecoveryCoordinator(final WeaverJournal journal, final SubjectSnapshotSource snapshots,
                                     final WorldWeaverProviderRegistry providers, final WeaverTypeRegistry types, final hu.taliann.icesmp.dev.weaver.area.WeaverAreaEngine areas) {
        this.journal = Objects.requireNonNull(journal); this.snapshots = Objects.requireNonNull(snapshots);
        this.providers = Objects.requireNonNull(providers); this.types = Objects.requireNonNull(types);
        this.areas = areas;
    }
    public CompletionStage<Void> start() {
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final WeaverOperationRecord operation : journal.snapshot().operations().values()) {
            chain = chain.thenCompose(ignored -> reconcile(operation.operationId()).handle((value, failure) -> null));
        }
        return chain;
    }
    public CompletionStage<Void> reconcile(final UUID id) { return reconcile(id, false); }
    private CompletionStage<Void> reconcile(final UUID id, final boolean retried) {
        final long availability = availabilityRevision.get();
        if (closed || !journal.ready()) return CompletableFuture.failedFuture(new WeaverDomainRejection("RECOVERY_UNAVAILABLE"));
        if (!admit(id)) return CompletableFuture.failedFuture(new WeaverDomainRejection("RECOVERY_BUSY"));
        final WeaverOperationRecord operation = journal.snapshot().operations().get(id);
        if (operation == null) { active.remove(id); return CompletableFuture.completedFuture(null); }
        if (operation.status() != OperationStatus.PREPARED && operation.status() != OperationStatus.APPLIED) {
            final CompletionStage<?> settled = operation.pendingAudit() ? journal.finishAudit(id, operation.revision()) : CompletableFuture.completedFuture(null);
            return settled.<Void>thenApply(ignored -> null).whenComplete((ignored, failure) -> active.remove(id));
        }
        final WeaverRecoveryAuthority authority = new WeaverRecoveryAuthority(operation, () -> journal.ready() ? journal.snapshot().operations().get(id) : null);
        final CompletionStage<Void> result;
        try {
            result = snapshots.capture(operation.actorId(), operation.subject()).thenCompose(snapshot -> {
                if (closed) throw new WeaverDomainRejection("RECOVERY_UNAVAILABLE");
                final RecoveryContext context = new RecoveryContext(authority, types, operation);
                final ActionDescriptor descriptor = providers.actions().get(operation.request().actionId());
                if (operation.subject() instanceof AreaRef && descriptor != null && (descriptor.areaSupport() == AreaSupport.ENTITY_FANOUT || descriptor.areaSupport() == AreaSupport.BLOCK_FANOUT)) {
                    if (areas == null) throw new WeaverDomainRejection("AREA_RECOVERY_ENGINE_UNAVAILABLE");
                    return areas.recover(context, descriptor).thenCompose(collection -> settle(operation,
                            providers.assessRecovery(operation.providerId(), context, collection.decorate(snapshot), operation, Optional.of(collection))));
                }
                final RecoveryAssessment assessment = providers.assessRecovery(operation.providerId(), context, snapshot, operation);
                return settle(operation, assessment);
            });
        } catch (final RuntimeException failure) {
            active.remove(id); return failed(operation, failure);
        }
        return result.handle((ignored, failure) -> failure == null ? CompletableFuture.<Void>completedFuture(null) : failed(operation, failure))
                .thenCompose(value -> value).whenComplete((ignored, failure) -> active.remove(id))
                .thenCompose(ignored -> !retried && !closed && pending.containsKey(id) && availability != availabilityRevision.get()
                        ? reconcile(id, true) : CompletableFuture.completedFuture(null));
    }
    private CompletionStage<Void> settle(final WeaverOperationRecord operation, final RecoveryAssessment assessment) {
        final CompletionStage<WeaverOperationRecord> changed;
        if (assessment.observed() == ObservedOperationState.APPLIED) {
            if (operation.status() == OperationStatus.PREPARED) {
                if (assessment.receipt().isEmpty() || operation.request().lifetime() != Lifetime.ONE_SHOT && assessment.effects().isEmpty()) return review(operation);
                changed = journal.applied(operation.operationId(), operation.revision(), assessment.receipt().get(), assessment.effects().orElseGet(WeaverEffectCommit::none), System.currentTimeMillis());
            } else {
                if (assessment.receipt().isPresent() && !assessment.receipt().get().equals(operation.receipt().orElseThrow())) return review(operation);
                changed = CompletableFuture.completedFuture(operation);
            }
        } else if (assessment.observed() == ObservedOperationState.BEFORE && operation.status() == OperationStatus.PREPARED) {
            changed = journal.resolve(operation.operationId(), operation.revision(), OperationStatus.ABORTED, System.currentTimeMillis());
        } else if (assessment.observed() == ObservedOperationState.BEFORE && operation.status() == OperationStatus.APPLIED && assessment.exactCompensationObserved()) {
            changed = journal.resolve(operation.operationId(), operation.revision(), OperationStatus.COMPENSATED, System.currentTimeMillis());
        } else return review(operation);
        return changed.thenCompose(value -> journal.finishAudit(value.operationId(), value.revision())).thenAccept(value -> pending.remove(value.operationId()));
    }
    private CompletionStage<Void> review(final WeaverOperationRecord operation) {
        return journal.resolve(operation.operationId(), operation.revision(), OperationStatus.NEEDS_REVIEW, System.currentTimeMillis())
                .thenCompose(value -> journal.finishAudit(value.operationId(), value.revision())).thenAccept(value -> pending.remove(value.operationId()));
    }
    private CompletionStage<Void> failed(final WeaverOperationRecord operation, final Throwable failure) {
        Throwable root = failure;
        while (root instanceof CompletionException && root.getCause() != null) root = root.getCause();
        final String code = root instanceof WeaverDomainRejection rejection ? rejection.code() : "RECOVERY_FAILURE";
        final PendingReason reason = switch (code) {
            case "ENTITY_UNAVAILABLE", "PLAYER_UNAVAILABLE", "OWNER_RETIRED", "OWNER_UNAVAILABLE" -> PendingReason.PENDING_ENTITY_LOAD;
            case "CHUNK_UNAVAILABLE" -> PendingReason.PENDING_CHUNK_LOAD;
            case "WORLD_UNAVAILABLE" -> PendingReason.UNRESOLVED_WORLD;
            default -> null;
        };
        if (reason != null) {
            pending.put(operation.operationId(), System.currentTimeMillis() - operation.preparedAt() >= 30L * 24 * 60 * 60 * 1000 ? PendingReason.STALE_UNRESOLVED : reason);
            return CompletableFuture.completedFuture(null);
        }
        if (!journal.ready() || closed || code.equals("JOURNAL_CONFLICT")) return CompletableFuture.failedFuture(new WeaverDomainRejection(code));
        return review(operation);
    }
    public CompletionStage<Void> entityAvailable(final UUID id) {
        availabilityRevision.incrementAndGet();
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final UUID operationId : List.copyOf(pending.keySet())) {
            final WeaverOperationRecord operation = journal.snapshot().operations().get(operationId);
            if (operation == null) continue;
            final boolean matches = switch (operation.subject()) {
                case PlayerRef player -> player.playerId().equals(id);
                case EntityRef entity -> entity.entityId().equals(id);
                case ItemSlotRef item -> item.holderId().equals(id);
                case AreaRef area -> operation.recoveryPayload().fields().containsKey(hu.taliann.icesmp.dev.weaver.area.WeaverAreaRecoveryEvidence.KEY)
                        && hu.taliann.icesmp.dev.weaver.area.WeaverAreaRecoveryEvidence.decode(area, operation.recoveryPayload().fields().get(hu.taliann.icesmp.dev.weaver.area.WeaverAreaRecoveryEvidence.KEY))
                            .targets().stream().anyMatch(ref -> ref instanceof PlayerRef player && player.playerId().equals(id) || ref instanceof EntityRef entity && entity.entityId().equals(id));
                default -> false;
            };
            if (matches) chain = chain.thenCompose(ignored -> reconcile(operationId).handle((value, failure) -> null));
        }
        return chain;
    }
    public CompletionStage<Void> worldAvailable(final UUID world, final OptionalLong chunk) {
        availabilityRevision.incrementAndGet();
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final UUID operationId : List.copyOf(pending.keySet())) {
            final WeaverOperationRecord operation = journal.snapshot().operations().get(operationId);
            if (operation == null) continue;
            final boolean matches = switch (operation.subject()) {
                case BlockRef block -> block.worldId().equals(world) && matchesChunk(chunk, block.x() >> 4, block.z() >> 4);
                case LocationRef location -> location.worldId().equals(world) && matchesChunk(chunk, (int) Math.floor(location.x()) >> 4, (int) Math.floor(location.z()) >> 4);
                case WorldRef reference -> reference.worldId().equals(world);
                case AreaRef area -> area.worldId().equals(world) && hu.taliann.icesmp.dev.weaver.area.WeaverAreaEngine.chunks(area).stream().anyMatch(owner -> matchesChunk(chunk, owner.chunkX(), owner.chunkZ()));
                default -> false;
            };
            if (matches) chain = chain.thenCompose(ignored -> reconcile(operationId).handle((value, failure) -> null));
        }
        return chain;
    }
    private static boolean matchesChunk(final OptionalLong chunk, final int x, final int z) {
        return chunk.isEmpty() || chunk.getAsLong() == (((long) x << 32) | (z & 0xffffffffL));
    }
    private synchronized boolean admit(final UUID id) { return active.size() < 8 && active.add(id); }
    public Map<UUID, PendingReason> pending() { return Map.copyOf(pending); }
    public void close() { closed = true; }
}
