package hu.taliann.icesmp.dev.weaver.projection;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import java.util.*;

public final class JournalProjectionSource implements WeaverProjectionSource {
    private final WeaverJournal journal;
    private final ProjectionConsumerRegistry consumers;
    public JournalProjectionSource(final WeaverJournal journal, final ProjectionConsumerRegistry consumers) { this.journal = Objects.requireNonNull(journal); this.consumers = Objects.requireNonNull(consumers); }
    @Override public List<WeaverProjection> active(final String consumerId, final SubjectRef subject, final long now) {
        if (!journal.ready()) throw new WeaverDomainRejection("PROJECTION_STATE_UNAVAILABLE");
        final ProjectionConsumerDescriptor consumer = consumers.require(consumerId); final WeaverJournalState state = journal.snapshot();
        if (!consumer.subjects().contains(subject.kind())) throw new WeaverDomainRejection("PROJECTION_SUBJECT_UNSUPPORTED");
        return state.projections().values().stream().filter(projection -> projection.subject().equals(subject) && projection.activeAt(now)
                && projection.providerId().equals(consumer.providerId()) && consumer.actions().contains(projection.actionId())
                && Set.of(OperationStatus.APPLIED, OperationStatus.COMMITTED).contains(state.operations().get(projection.influence().operationId()).status()))
                .sorted(Comparator.comparingLong(WeaverProjection::sequence)).map(projection -> {
                    final Map<String, WeaverValue> fields = new HashMap<>(); projection.values().forEach((field, value) -> { if (value.type().equals(consumer.fields().get(field))) fields.put(field, value); });
                    if (fields.isEmpty()) return null;
                    return new WeaverProjection(projection.projectionId(), projection.sequence(), projection.providerId(), projection.actionId(), projection.subject(), projection.lifetime(),
                            projection.influence(), fields, projection.canonicalFingerprintAtApply(), projection.createdAt(), projection.expiresAt());
                }).filter(Objects::nonNull).toList();
    }
}
