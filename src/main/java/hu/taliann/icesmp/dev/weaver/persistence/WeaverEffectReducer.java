package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.projection.WeaverProjection;
import java.util.*;

/** Pure transitions make the storage acknowledgement the only publication boundary. */
final class WeaverEffectReducer {
    private WeaverEffectReducer() { }
    /** Adds only derived quarantine; canonical receipts and original operation revisions remain untouched. */
    static WeaverJournalState propagated(final WeaverJournalState state, final Set<DeveloperInfluence> origins,
            final Set<hu.taliann.icesmp.integrity.RewardSource> targets, final long until, final Optional<WeaverValue> observedLifetime) {
        if (origins.isEmpty() || origins.size() > 128 || targets.isEmpty() || targets.size() > 32) throw new WeaverDomainRejection("PROPAGATION_CAPACITY");
        final Set<DeveloperInfluence> recorded = new HashSet<>(); state.influences().values().forEach(value -> recorded.add(value.influence()));
        final Map<UUID, WeaverInfluenceRecord> influences = new HashMap<>(state.influences());
        for (final DeveloperInfluence origin : origins) {
            if (!origin.quarantinesRewards() || !recorded.contains(origin)) throw new WeaverDomainRejection("PROPAGATION_ORIGIN_UNAVAILABLE");
            WeaverJournalState.origin(state.operations(), origin);
            for (final var source : targets) {
                final var target = propagationTarget(source);
                final Optional<WeaverValue> lifetime = target.monotonic() ? Optional.empty() : observedLifetime;
                final UUID id = propagatedId(origin, target, lifetime);
                final var previous = influences.get(id);
                if (previous != null && (!previous.influence().equals(origin) || !previous.target().equals(target) || !previous.observedLifetime().equals(lifetime))) throw new WeaverDomainRejection("PROPAGATION_CONFLICT");
                final long deadline = target.monotonic() ? 0 : Math.max(until, Math.addExact(origin.appliedAt(), PlayerQuarantine.MINIMUM_TAIL_MILLIS));
                influences.put(id, new WeaverInfluenceRecord(id, origin, target, lifetime.isPresent() || previous != null && previous.active(),
                        previous == null ? deadline : Math.max(previous.quarantinedUntil(), deadline), lifetime));
            }
        }
        final long reserved = state.intents().values().stream().mapToLong(intent -> intent.targets().size()).sum();
        if (influences.size() + reserved > WeaverJournalState.MAX_INFLUENCES) throw new WeaverDomainRejection("PROPAGATION_CAPACITY");
        if (influences.equals(state.influences())) return state;
        return new WeaverJournalState(Math.addExact(state.revision(), 1), state.operations(), state.receipts(), state.projectionSequence(),
                state.intents(), state.projections(), influences, state.effectDeltas());
    }
    static UUID propagatedId(final DeveloperInfluence origin, final WeaverInfluenceTarget target) {
        return propagatedId(origin, target, Optional.empty());
    }
    static UUID propagatedId(final DeveloperInfluence origin, final WeaverInfluenceTarget target, final Optional<WeaverValue> lifetime) {
        final Map<String, Object> identity = new HashMap<>(Map.of("schema", "weaver-derived-influence@1",
                "operation", origin.operationId().toString(), "target", WeaverEffectCodec.target(target)));
        lifetime.ifPresent(value -> identity.put("lifetime", Map.of("type", value.type().canonical(), "provider", value.sourceProvider(),
                "facet", value.sourceFacet(), "parameters", value.payload())));
        return UUID.nameUUIDFromBytes(CanonicalValueBytes.encode(identity));
    }
    static WeaverInfluenceTarget propagationTarget(final hu.taliann.icesmp.integrity.RewardSource source) {
        return WeaverInfluenceTarget.exact(source instanceof hu.taliann.icesmp.integrity.RewardSource.Location point
                ? new hu.taliann.icesmp.integrity.RewardSource.Location(point.world(), Math.floor(point.x()), Math.floor(point.y()), Math.floor(point.z())) : source);
    }
    static WeaverJournalState prepared(final WeaverJournalState state, final WeaverOperationRecord operation, final WeaverEffectIntent intent) {
        validateUndo(state, operation);
        final Set<WeaverInfluenceTarget> targets = new HashSet<>(intent.targets());
        if (operation.request().integrityMode() == IntegrityMode.SANDBOX) targets.add(WeaverInfluenceTarget.subject(operation.subject()));
        final Map<UUID, WeaverEffectIntent> intents = new HashMap<>(state.intents());
        if (!targets.isEmpty()) intents.put(operation.operationId(), new WeaverEffectIntent(targets));
        return state.replace(operation, intents, state.projections(), state.influences(), state.projectionSequence());
    }
    static WeaverJournalState applied(final WeaverJournalState state, final WeaverOperationRecord operation, final WeaverEffectCommit effects) {
        validateUndo(state, operation);
        if (effects.undoneReceipt().isPresent() && (operation.undoClaim().isEmpty() || !effects.undoneReceipt().get().equals(operation.undoClaim().get().receiptId()))) throw new WeaverDomainRejection("UNDO_CLAIM_REQUIRED");
        final WeaverReceipt receipt = operation.receipt().orElseThrow();
        if (receipt.status() != ReceiptStatus.COMMITTED || receipt.createdAt() < operation.preparedAt()) throw new WeaverDomainRejection("INVALID_APPLIED_RECEIPT");
        final DeveloperInfluence origin = new DeveloperInfluence(operation.operationId(), operation.request().integrityMode(), operation.request().actionId(), operation.actorId(), receipt.createdAt());
        final Map<UUID, WeaverProjection> projections = new HashMap<>(state.projections());
        final var scope = WeaverOperationScope.fingerprints(operation.subject(), operation.beforeFingerprint(), operation.recoveryPayload());
        for (final var guard : effects.expectedProjectionFingerprints().entrySet()) {
            if (!scope.containsKey(guard.getKey()) || !guard.getValue().equals(hu.taliann.icesmp.dev.weaver.projection.WeaverProjectionFingerprint.of(
                    state.projections().values().stream().filter(projection -> projection.providerId().equals(operation.providerId())
                            && projection.subject().equals(guard.getKey()) && projection.activeAt(operation.updatedAt())).toList()))) throw new WeaverDomainRejection("PROJECTION_CONFLICT");
        }
        final var reservations = WeaverOperationScope.reservations(operation);
        final Map<hu.taliann.icesmp.dev.weaver.subject.SubjectRef, Integer> addedCounts = new HashMap<>();
        final Set<UUID> endedOrigins = new HashSet<>(); final Map<UUID, WeaverProjection> removedBefore = new HashMap<>();
        for (final UUID id : effects.removedProjections()) {
            final WeaverProjection removed = projections.get(id);
            if (removed == null || !removed.providerId().equals(operation.providerId()) || !scope.containsKey(removed.subject())) throw new WeaverDomainRejection("PROJECTION_CONFLICT");
            removedBefore.put(id, removed); projections.remove(id); endedOrigins.add(removed.influence().operationId());
        }
        long sequence = state.projectionSequence();
        for (final WeaverProjection projection : effects.projections().stream().sorted(Comparator.comparingLong(WeaverProjection::sequence)).toList()) {
            if (!projection.influence().equals(origin) || projection.sequence() != Math.addExact(sequence, 1)
                    || state.projections().containsKey(projection.projectionId()) || !projection.canonicalFingerprintAtApply().equals(scope.get(projection.subject()))
                    || addedCounts.merge(projection.subject(), 1, Integer::sum) > reservations.getOrDefault(projection.subject(), 0)) throw new WeaverDomainRejection("PROJECTION_CONFLICT");
            sequence = projection.sequence(); projections.put(projection.projectionId(), projection);
        }
        final Set<WeaverInfluenceTarget> targets = new HashSet<>(state.intents().getOrDefault(operation.operationId(), WeaverEffectIntent.none()).targets());
        effects.projections().forEach(projection -> targets.add(WeaverInfluenceTarget.subject(projection.subject())));
        final Map<UUID, WeaverInfluenceRecord> influences = new HashMap<>(state.influences());
        final Set<WeaverInfluenceTarget> supplied = new HashSet<>();
        for (final WeaverInfluenceRecord influence : effects.influences()) {
            if (!influence.influence().equals(origin) || !targets.contains(influence.target()) || !supplied.add(influence.target())
                    || influences.putIfAbsent(influence.id(), influence) != null) throw new WeaverDomainRejection("INFLUENCE_CONFLICT");
        }
        for (final WeaverInfluenceTarget target : targets) if (!supplied.contains(target)) {
            final WeaverInfluenceRecord influence = WeaverInfluenceRecord.applied(origin, target, operation.request().lifetime() != Lifetime.ONE_SHOT);
            influences.put(influence.id(), influence);
        }
        for (final UUID ended : endedOrigins) if (projections.values().stream().noneMatch(projection -> projection.influence().operationId().equals(ended))) {
            end(influences, ended, operation.updatedAt());
        }
        final Map<UUID, WeaverEffectIntent> intents = new HashMap<>(state.intents()); intents.remove(operation.operationId());
        final Map<UUID, WeaverInfluenceRecord> endedBefore = new HashMap<>();
        for (final var entry : state.influences().entrySet()) if (!entry.getValue().equals(influences.get(entry.getKey()))) endedBefore.put(entry.getKey(), entry.getValue());
        final Map<UUID, WeaverProjection> added = new HashMap<>(); effects.projections().forEach(projection -> added.put(projection.projectionId(), projection));
        final Map<UUID, WeaverEffectDelta> deltas = new HashMap<>(state.effectDeltas()); deltas.put(operation.operationId(), new WeaverEffectDelta(added, removedBefore, endedBefore, true));
        final Map<UUID, WeaverOperationRecord> operations = new HashMap<>(state.operations()); operations.put(operation.operationId(), operation);
        final Map<UUID, WeaverReceipt> receipts = new HashMap<>(state.receipts()); receipts.put(receipt.receiptId(), receipt);
        if (operation.undoClaim().isPresent()) {
            final WeaverReceipt original = receipts.get(operation.undoClaim().get().receiptId()); final WeaverOperationRecord originalOperation = operations.get(original.operationId());
            final WeaverReceipt undone = new WeaverReceipt(original.receiptId(), original.operationId(), original.providerId(), original.actionId(), original.subject(), original.risk(), original.lifetime(), original.integrityMode(),
                    original.beforeFingerprint(), original.afterFingerprint(), original.before(), original.after(), original.undo(), original.createdAt(), ReceiptStatus.UNDONE);
            final WeaverOperationRecord marked = new WeaverOperationRecord(originalOperation.operationId(), originalOperation.actorId(), originalOperation.providerId(), originalOperation.request(), originalOperation.subject(), originalOperation.beforeFingerprint(),
                    originalOperation.afterFingerprint(), originalOperation.recoveryPayload(), originalOperation.status(), Math.addExact(originalOperation.revision(), 1), originalOperation.preparedAt(),
                    Math.max(originalOperation.updatedAt(), operation.updatedAt()), Optional.of(undone), originalOperation.pendingAudit(), originalOperation.undoClaim());
            receipts.put(undone.receiptId(), undone); operations.put(marked.operationId(), marked);
        }
        return new WeaverJournalState(Math.addExact(state.revision(), 1), operations, receipts, sequence, intents, projections, influences, deltas);
    }
    static WeaverJournalState resolved(final WeaverJournalState state, final WeaverOperationRecord operation) {
        final Map<UUID, WeaverEffectIntent> intents = new HashMap<>(state.intents());
        final Map<UUID, WeaverProjection> projections = new HashMap<>(state.projections());
        final Map<UUID, WeaverInfluenceRecord> influences = new HashMap<>(state.influences());
        if (operation.status() == OperationStatus.ABORTED || operation.status() == OperationStatus.COMPENSATED) intents.remove(operation.operationId());
        if (operation.status() == OperationStatus.COMPENSATED) {
            final WeaverEffectDelta delta = state.effectDeltas().get(operation.operationId());
            if (delta == null || !delta.complete()) throw new WeaverDomainRejection("COMPENSATION_EVIDENCE_UNAVAILABLE");
            for (final var entry : delta.added().entrySet()) {
                if (!entry.getValue().equals(projections.get(entry.getKey()))) throw new WeaverDomainRejection("CONFLICT"); projections.remove(entry.getKey());
            }
            for (final var entry : delta.removed().entrySet()) if (projections.putIfAbsent(entry.getKey(), entry.getValue()) != null) throw new WeaverDomainRejection("CONFLICT");
            for (final var entry : delta.endedBefore().entrySet()) {
                final WeaverInfluenceRecord current = influences.get(entry.getKey()), before = entry.getValue();
                if (current == null || !current.influence().equals(before.influence()) || !current.target().equals(before.target())) throw new WeaverDomainRejection("CONFLICT");
                influences.put(entry.getKey(), new WeaverInfluenceRecord(before.id(), before.influence(), before.target(), before.active(),
                        Math.max(before.quarantinedUntil(), current.quarantinedUntil()), before.observedLifetime()));
            }
            end(influences, operation.operationId(), operation.updatedAt());
        }
        return state.replace(operation, intents, projections, influences, state.projectionSequence());
    }
    static WeaverJournalState expired(final WeaverJournalState state, final long now, final boolean clearSession) {
        final Map<UUID, WeaverProjection> projections = new HashMap<>(state.projections()); final Set<UUID> endedOrigins = new HashSet<>();
        projections.values().removeIf(projection -> {
            final boolean remove = state.operations().get(projection.influence().operationId()).status() == OperationStatus.COMMITTED
                    && (!projection.activeAt(now) || clearSession && projection.lifetime() == Lifetime.SESSION);
            if (remove) endedOrigins.add(projection.influence().operationId()); return remove;
        });
        if (endedOrigins.isEmpty()) return state;
        final Map<UUID, WeaverInfluenceRecord> influences = new HashMap<>(state.influences());
        for (final UUID ended : endedOrigins) if (projections.values().stream().noneMatch(projection -> projection.influence().operationId().equals(ended))) end(influences, ended, now);
        return new WeaverJournalState(Math.addExact(state.revision(), 1), state.operations(), state.receipts(), state.projectionSequence(), state.intents(), projections, influences, state.effectDeltas());
    }
    static void validateUndo(final WeaverJournalState state, final WeaverOperationRecord operation) {
        if (operation.undoClaim().isEmpty()) return;
        final WeaverUndoClaim claim = operation.undoClaim().get(); final WeaverReceipt receipt = state.receipts().get(claim.receiptId());
        final WeaverOperationRecord original = receipt == null ? null : state.operations().get(receipt.operationId());
        if (receipt == null || receipt.status() != ReceiptStatus.COMMITTED || original == null || original.status() != OperationStatus.COMMITTED
                || original.revision() != claim.operationRevision() || receipt.undo().isEmpty() || !receipt.providerId().equals(operation.providerId())
                || !WeaverUndoSubject.resolve(receipt).equals(operation.subject()) || !receipt.undo().get().actionId().equals(operation.request().actionId())
                || !receipt.undo().get().parameters().equals(operation.request().parameters()) || !claim.expectedFingerprint().equals(receipt.undo().get().expectedCurrentFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        for (final WeaverOperationRecord other : state.operations().values()) if (!other.operationId().equals(operation.operationId()) && other.undoClaim().isPresent()
                && other.undoClaim().get().receiptId().equals(claim.receiptId()) && Set.of(OperationStatus.PREPARED, OperationStatus.APPLIED, OperationStatus.NEEDS_REVIEW).contains(other.status())) throw new WeaverDomainRejection("UNDO_ALREADY_PENDING");
    }
    private static void end(final Map<UUID, WeaverInfluenceRecord> influences, final UUID origin, final long now) {
        influences.replaceAll((id, influence) -> influence.influence().operationId().equals(origin) && influence.observedLifetime().isEmpty()
                ? influence.ended(Math.max(now, influence.influence().appliedAt())) : influence);
    }
}
