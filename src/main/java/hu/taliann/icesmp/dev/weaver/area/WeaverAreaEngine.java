package hu.taliann.icesmp.dev.weaver.area;

import hu.taliann.icesmp.dev.weaver.WeaverAuthorityToken;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Region collection never transfers a live entity; every child snapshot and stage reacquires its own owner. */
public final class WeaverAreaEngine implements AutoCloseable {
    private record ChunkResult(RegionOwner owner, WeaverAreaAccess.ChunkSelection selection) { }
    private record TargetResult(SubjectRef ref, Optional<SubjectSnapshot> snapshot, Optional<String> skipped) { }
    private final WeaverOwnerRouter router;
    private final WeaverAreaAccess access;
    private final AtomicBoolean collecting = new AtomicBoolean();
    private volatile boolean closed;
    public WeaverAreaEngine(final WeaverOwnerRouter router, final WeaverAreaAccess access) {
        this.router = Objects.requireNonNull(router); this.access = Objects.requireNonNull(access);
    }
    public static List<RegionOwner> chunks(final AreaRef area) {
        final AreaBounds bounds = area.shape().bounds(); final List<RegionOwner> result = new ArrayList<>();
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) result.add(new RegionOwner(area.worldId(), x, z));
        return List.copyOf(result);
    }
    public CompletionStage<WeaverAreaCollection> collect(final WeaverAuthorityToken authority, final AreaRef area, final ActionDescriptor descriptor) {
        authority.requireValid(); validate(area, descriptor);
        if (closed || !collecting.compareAndSet(false, true)) return CompletableFuture.failedFuture(new WeaverDomainRejection("AREA_COLLECTION_BUSY_OR_CLOSED"));
        final AreaLimits limits = descriptor.areaLimits().orElseThrow(); final int cap = targetCap(descriptor);
        final CompletionStage<WeaverAreaCollection> result = WeaverBoundedTasks.map(chunks(area), Math.min(limits.concurrency(), limits.maxRegions()), chunk ->
                router.submit(chunk, authority.actor(), Duration.ofSeconds(5), () -> {
                    require(authority); return CompletableFuture.completedFuture(access.collectOnOwner(area, chunk, descriptor.areaSupport(), cap));
                }).handle((selection, failure) -> {
                    if (failure != null) return new ChunkResult(chunk, WeaverAreaAccess.ChunkSelection.unavailable(unavailable(failure)));
                    if (selection.targets().size() > cap) throw new WeaverDomainRejection("AREA_TARGET_CAP");
                    return new ChunkResult(chunk, selection);
                })).thenCompose(collected -> {
            require(authority);
            final Map<RegionOwner, String> skippedChunks = new LinkedHashMap<>(); final Set<SubjectRef> refs = new HashSet<>();
            for (final ChunkResult chunk : collected) {
                chunk.selection().unavailable().ifPresent(code -> skippedChunks.put(chunk.owner(), code));
                for (final SubjectRef ref : chunk.selection().targets()) {
                    if (!WeaverAreaCollection.kind(descriptor.areaSupport(), ref)) throw new IllegalArgumentException("Foreign AREA target kind");
                    if (ref instanceof BlockRef block && (!SubjectRoute.owner(block).equals(chunk.owner())
                            || !block.worldId().equals(area.worldId()) || !area.shape().contains(block.x(), block.y(), block.z()))) throw new IllegalArgumentException("Foreign AREA block");
                    refs.add(ref); if (refs.size() > cap) throw new WeaverDomainRejection("AREA_TARGET_CAP");
                }
            }
            return WeaverBoundedTasks.map(refs.stream().sorted(Comparator.comparing(SubjectKeyCodec::encode)).toList(), limits.concurrency(), ref ->
                    router.submit(SubjectRoute.owner(ref), authority.actor(), Duration.ofSeconds(5), () -> {
                        require(authority); final SubjectSnapshot snapshot = access.snapshotOnOwner(ref);
                        if (!snapshot.ref().equals(ref)) throw new IllegalArgumentException("AREA snapshot identity changed");
                        if (!WeaverAreaCollection.contains(area, snapshot)) return CompletableFuture.completedFuture(new TargetResult(ref, Optional.empty(), Optional.of("LEFT_AREA")));
                        return CompletableFuture.completedFuture(new TargetResult(ref, Optional.of(snapshot), Optional.empty()));
                    }).exceptionally(failure -> new TargetResult(ref, Optional.empty(), Optional.of(unavailable(failure))))).thenApply(targets -> {
                require(authority); final List<SubjectSnapshot> snapshots = new ArrayList<>(); final Map<SubjectRef, String> skippedTargets = new LinkedHashMap<>();
                for (final TargetResult target : targets) { target.snapshot().ifPresent(snapshots::add); target.skipped().ifPresent(code -> skippedTargets.put(target.ref(), code)); }
                return new WeaverAreaCollection(area, descriptor.areaSupport(), snapshots, skippedChunks, skippedTargets);
            });
        });
        return result.whenComplete((ignored, failure) -> collecting.set(false));
    }
    public PreparedAction guard(final WeaverAreaCollection collection, final PreparedAction prepared) {
        validate(collection.area(), prepared.descriptor());
        if (!prepared.subject().equals(collection.area()) || !prepared.expectedBeforeFingerprint().equals(collection.fingerprint()) || prepared.descriptor().areaSupport() != collection.support()
                || collection.targets().size() > targetCap(prepared.descriptor()) || prepared.stages().size() != collection.targets().size()) throw new IllegalArgumentException("AREA child plan differs from collected targets");
        final List<ExecutionStage> guarded = new ArrayList<>();
        for (int i = 0; i < collection.targets().size(); i++) {
            final SubjectSnapshot before = collection.targets().get(i); final ExecutionStage stage = prepared.stages().get(i);
            if (!stage.owner().equals(SubjectRoute.owner(before.ref())) || prepared.descriptor().requiresJournal() && stage.compensate().isEmpty()) throw new IllegalArgumentException("AREA child ownership or compensation missing");
            guarded.add(new ExecutionStage(stage.id(), stage.owner(), stage.payload(), (context, payload) -> {
                require(context.authority()); final SubjectSnapshot current = access.snapshotOnOwner(before.ref());
                if (!current.ref().equals(before.ref()) || !WeaverAreaCollection.contains(collection.area(), current)
                        || !current.revisionFingerprint().equals(before.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
                return stage.apply().execute(new ExecutionContext(context.authority(), current, List.of()), payload);
            }, stage.compensate().map(compensation -> (context, payload) -> {
                final SubjectSnapshot current = access.snapshotOnOwner(before.ref());
                if (!current.ref().equals(before.ref())) throw new WeaverDomainRejection("CONFLICT");
                context.requireCurrentFingerprint(current.revisionFingerprint());
                return compensation.execute(new CompensationContext(context.authority(), context.stageId(), before, context.applied()), payload);
            }), stage.timeoutMillis()));
        }
        guarded.add(new ExecutionStage("weaver.area.aggregate", new ActorOwner(), Map.of(), (context, payload) -> {
            if (context.previousResults().size() != collection.targets().size()) throw new IllegalArgumentException("AREA result count");
            final List<SubjectSnapshot> after = new ArrayList<>();
            for (int i = 0; i < collection.targets().size(); i++) {
                final SubjectSnapshot target = collection.targets().get(i);
                after.add(new SubjectSnapshot(target.ref(), target.capturedAt(), context.previousResults().get(i).afterFingerprint(), target.facts()));
            }
            final var selected = new WeaverAreaCollection(collection.area(), collection.support(), after, collection.skippedChunks(), collection.skippedTargets());
            return CompletableFuture.completedFuture(new StageResult(selected.fingerprint(), Map.of(), Map.of()));
        }, Optional.empty(), 5000));
        final Map<String, Object> recovery = new HashMap<>(prepared.recoveryPayload().fields());
        if (recovery.putIfAbsent(WeaverAreaRecoveryEvidence.KEY, WeaverAreaRecoveryEvidence.of(collection).encode()) != null) throw new IllegalArgumentException("Provider supplied reserved AREA recovery evidence");
        return new PreparedAction(prepared.operationId(), prepared.descriptor(), prepared.subject(), prepared.expectedBeforeFingerprint(), guarded,
                new OperationRecoveryPayload(prepared.recoveryPayload().schemaVersion(), recovery), prepared.receiptFactory());
    }
    public CompletionStage<WeaverAreaCollection> recover(final RecoveryContext context, final ActionDescriptor descriptor) {
        context.authority().require(context.operation());
        if (!(context.operation().subject() instanceof AreaRef area) || !context.operation().request().actionId().equals(descriptor.id())) throw new SecurityException("AREA recovery operation differs");
        validate(area, descriptor);
        final var evidence = WeaverAreaRecoveryEvidence.decode(area, context.operation().recoveryPayload().fields().get(WeaverAreaRecoveryEvidence.KEY));
        if (evidence.support() != descriptor.areaSupport() || evidence.targets().size() > targetCap(descriptor)) throw new WeaverDomainRejection("AREA_RECOVERY_CONTRACT_CHANGED");
        return WeaverBoundedTasks.map(evidence.targets(), descriptor.areaLimits().orElseThrow().concurrency(), ref ->
                router.submit(SubjectRoute.owner(ref), context.operation().actorId(), Duration.ofSeconds(5), () -> {
                    if (closed) throw new WeaverDomainRejection("AREA_CLOSED"); context.authority().require(context.operation());
                    final SubjectSnapshot snapshot = access.snapshotOnOwner(ref);
                    if (!snapshot.ref().equals(ref)) throw new IllegalArgumentException("AREA recovery target identity");
                    return CompletableFuture.completedFuture(snapshot);
                })).thenApply(targets -> new WeaverAreaCollection(area, descriptor.areaSupport(), targets, evidence.skippedChunks(), evidence.skippedTargets()));
    }
    private static int targetCap(final ActionDescriptor descriptor) {
        final AreaLimits limits = descriptor.areaLimits().orElseThrow();
        return descriptor.areaSupport() == AreaSupport.ENTITY_FANOUT ? limits.maxEntities() : limits.maxBlocks();
    }
    private static void validate(final AreaRef area, final ActionDescriptor descriptor) {
        if (descriptor.risk().ordinal() >= RiskLevel.DESTRUCTIVE.ordinal() || !descriptor.subjects().contains(WeaverSubjectKind.AREA)
                || descriptor.areaLimits().isEmpty() || descriptor.areaSupport() != AreaSupport.ENTITY_FANOUT && descriptor.areaSupport() != AreaSupport.BLOCK_FANOUT) throw new WeaverDomainRejection("AREA_ACTION_FORBIDDEN");
        final AreaLimits limits = descriptor.areaLimits().get();
        if (area.shape().bounds().chunkCount() > Math.min(limits.maxChunks(), limits.maxRegions()) || targetCap(descriptor) == 0) throw new WeaverDomainRejection("AREA_LIMIT");
    }
    private void require(final WeaverAuthorityToken authority) { authority.requireValid(); if (closed) throw new WeaverDomainRejection("AREA_CLOSED"); }
    private static String unavailable(final Throwable error) {
        Throwable cause = error; while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof WeaverDomainRejection rejection && Set.of("OWNER_UNAVAILABLE", "ENTITY_UNAVAILABLE", "PLAYER_UNAVAILABLE", "CHUNK_UNAVAILABLE", "WORLD_UNAVAILABLE").contains(rejection.code())) return rejection.code();
        throw new CompletionException(cause);
    }
    @Override public void close() { closed = true; }
}
