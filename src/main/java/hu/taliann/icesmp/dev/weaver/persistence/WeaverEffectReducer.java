package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.projection.WeaverProjection;
import java.util.*;

/** Pure transitions make the storage acknowledgement the only publication boundary. */
final class WeaverEffectReducer {
    private WeaverEffectReducer() { }
    static WeaverJournalState prepared(final WeaverJournalState state, final WeaverOperationRecord operation, final WeaverEffectIntent intent) {
        final Set<WeaverInfluenceTarget> targets = new HashSet<>(intent.targets());
        if (operation.request().integrityMode() == IntegrityMode.SANDBOX) targets.add(WeaverInfluenceTarget.subject(operation.subject()));
        final Map<UUID, WeaverEffectIntent> intents = new HashMap<>(state.intents());
        if (!targets.isEmpty()) intents.put(operation.operationId(), new WeaverEffectIntent(targets));
        return state.replace(operation, intents, state.projections(), state.influences(), state.projectionSequence());
    }
    static WeaverJournalState applied(final WeaverJournalState state, final WeaverOperationRecord operation, final WeaverEffectCommit effects) {
        if (effects.undoneReceipt().isPresent()) throw new WeaverDomainRejection("UNDO_EXECUTION_UNAVAILABLE");
        final WeaverReceipt receipt = operation.receipt().orElseThrow();
        if (receipt.status() != ReceiptStatus.COMMITTED || receipt.createdAt() < operation.preparedAt()) throw new WeaverDomainRejection("INVALID_APPLIED_RECEIPT");
        final DeveloperInfluence origin = new DeveloperInfluence(operation.operationId(), operation.request().integrityMode(), operation.request().actionId(), operation.actorId(), receipt.createdAt());
        final Map<UUID, WeaverProjection> projections = new HashMap<>(state.projections());
        final Set<UUID> endedOrigins = new HashSet<>();
        for (final UUID id : effects.removedProjections()) {
            final WeaverProjection removed = projections.get(id);
            if (removed == null || !removed.providerId().equals(operation.providerId()) || !removed.subject().equals(operation.subject())) throw new WeaverDomainRejection("PROJECTION_CONFLICT");
            projections.remove(id); endedOrigins.add(removed.influence().operationId());
        }
        long sequence = state.projectionSequence();
        for (final WeaverProjection projection : effects.projections().stream().sorted(Comparator.comparingLong(WeaverProjection::sequence)).toList()) {
            if (!projection.influence().equals(origin) || projection.sequence() != Math.addExact(sequence, 1)
                    || state.projections().containsKey(projection.projectionId()) || !projection.subject().equals(operation.subject())) throw new WeaverDomainRejection("PROJECTION_CONFLICT");
            sequence = projection.sequence(); projections.put(projection.projectionId(), projection);
        }
        final Set<WeaverInfluenceTarget> targets = new HashSet<>(state.intents().getOrDefault(operation.operationId(), WeaverEffectIntent.none()).targets());
        if (!effects.projections().isEmpty()) targets.add(WeaverInfluenceTarget.subject(operation.subject()));
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
        return state.replace(operation, intents, projections, influences, sequence);
    }
    static WeaverJournalState resolved(final WeaverJournalState state, final WeaverOperationRecord operation) {
        final Map<UUID, WeaverEffectIntent> intents = new HashMap<>(state.intents());
        final Map<UUID, WeaverProjection> projections = new HashMap<>(state.projections());
        final Map<UUID, WeaverInfluenceRecord> influences = new HashMap<>(state.influences());
        if (operation.status() == OperationStatus.ABORTED || operation.status() == OperationStatus.COMPENSATED) intents.remove(operation.operationId());
        if (operation.status() == OperationStatus.COMPENSATED) {
            projections.values().removeIf(projection -> projection.influence().operationId().equals(operation.operationId()));
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
        return new WeaverJournalState(Math.addExact(state.revision(), 1), state.operations(), state.receipts(), state.projectionSequence(), state.intents(), projections, influences);
    }
    private static void end(final Map<UUID, WeaverInfluenceRecord> influences, final UUID origin, final long now) {
        influences.replaceAll((id, influence) -> influence.influence().operationId().equals(origin)
                ? influence.ended(Math.max(now, influence.influence().appliedAt())) : influence);
    }
}
