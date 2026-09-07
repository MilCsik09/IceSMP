package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.WeaverAuthorityToken;
import hu.taliann.icesmp.dev.weaver.WorldWeaverProviderRegistry;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;
import java.util.*;

/** Undo only prepares the provider's compensating action; receipt status changes belong to its durable operation. */
public final class WeaverUndoCoordinator {
    public record Target(WeaverReceipt receipt, WeaverUndoClaim claim, ActionDescriptor descriptor) { }
    private final WeaverJournal journal;
    private final WorldWeaverProviderRegistry providers;
    public WeaverUndoCoordinator(final WeaverJournal journal, final WorldWeaverProviderRegistry providers) {
        this.journal = Objects.requireNonNull(journal); this.providers = Objects.requireNonNull(providers);
    }
    public List<WeaverReceipt> history(final WeaverAuthorityToken authority) {
        authority.requireValid(); return journal.snapshot().receipts().values().stream().sorted(Comparator.comparingLong(WeaverReceipt::createdAt).reversed().thenComparing(WeaverReceipt::receiptId)).toList();
    }
    public Optional<WeaverReceipt> receipt(final WeaverAuthorityToken authority, final UUID id) { authority.requireValid(); return Optional.ofNullable(journal.snapshot().receipts().get(id)); }
    public Optional<OperationStatus> status(final WeaverAuthorityToken authority, final UUID operationId) { authority.requireValid(); return Optional.ofNullable(journal.snapshot().operations().get(operationId)).map(WeaverOperationRecord::status); }
    public boolean available(final WeaverAuthorityToken authority, final UUID id) {
        authority.requireValid(); final var state = journal.snapshot(); final WeaverReceipt receipt = state.receipts().get(id);
        return journal.ready() && receipt != null && receipt.status() == ReceiptStatus.COMMITTED && receipt.undo().isPresent()
                && state.operations().get(receipt.operationId()).status() == OperationStatus.COMMITTED;
    }
    public Target target(final WeaverAuthorityToken authority, final UUID id, final SubjectSnapshot snapshot) {
        authority.requireValid(); if (!journal.ready()) throw new WeaverDomainRejection("JOURNAL_UNAVAILABLE");
        final var state = journal.snapshot(); final WeaverReceipt receipt = state.receipts().get(id);
        if (receipt == null || receipt.status() != ReceiptStatus.COMMITTED || receipt.undo().isEmpty()) throw new WeaverDomainRejection("UNDO_UNAVAILABLE");
        final WeaverOperationRecord operation = state.operations().get(receipt.operationId());
        if (operation.status() != OperationStatus.COMMITTED || !snapshot.ref().equals(WeaverUndoSubject.resolve(receipt))
                || !snapshot.revisionFingerprint().equals(receipt.undo().get().expectedCurrentFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        final ActionDescriptor descriptor = providers.actions().get(receipt.undo().get().actionId());
        if (descriptor == null || !descriptor.requiresJournal() || !descriptor.subjects().contains(snapshot.ref().kind())
                || !providers.owner(descriptor.id()).equals(receipt.providerId())) throw new WeaverDomainRejection("UNDO_ACTION_UNAVAILABLE");
        return new Target(receipt, new WeaverUndoClaim(id, operation.revision(), snapshot.revisionFingerprint()), descriptor);
    }
    public PreparedAction prepare(final ProviderContext context, final WeaverUndoClaim claim, final SubjectSnapshot snapshot) {
        final Target target = target(context.authority(), claim.receiptId(), snapshot);
        if (!target.claim().equals(claim)) throw new WeaverDomainRejection("CONFLICT");
        return providers.invoke(target.receipt().providerId(), context, provider -> {
            final PreparedAction prepared = provider.prepareUndo(context, snapshot, target.receipt());
            if (!prepared.descriptor().equals(target.descriptor()) || !prepared.subject().equals(snapshot.ref()) || !prepared.expectedBeforeFingerprint().equals(claim.expectedFingerprint())) throw new IllegalArgumentException("Undo plan differs from receipt contract");
            return prepared;
        });
    }
}
