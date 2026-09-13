package hu.taliann.icesmp.dev.weaver.api;

import java.util.Set;
import java.util.Optional;
import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;
import hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind;
import hu.taliann.icesmp.dev.weaver.execution.PreparedAction;
import hu.taliann.icesmp.dev.weaver.persistence.RecoveryAssessment;
import hu.taliann.icesmp.dev.weaver.persistence.WeaverOperationRecord;

public interface WorldWeaverProvider {
    default void clearSession() { }
    default java.util.concurrent.CompletionStage<Void> afterCommit(WeaverReceipt receipt) { return java.util.concurrent.CompletableFuture.completedFuture(null); }
    String id();
    int contractVersion();
    Set<WeaverSubjectKind> supportedKinds();
    ProviderContribution contribution();
    ProviderCoverage coverage();
    ProviderDiscovery discover(SubjectSnapshot snapshot);
    InspectionResult inspect(ProviderContext context, SubjectSnapshot snapshot, String facetId);
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request);
    default PreparedAction prepareArea(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request,
            final hu.taliann.icesmp.dev.weaver.area.WeaverAreaCollection collection) {
        throw new WeaverDomainRejection("AREA_PREPARATION_UNAVAILABLE");
    }
    default hu.taliann.icesmp.dev.weaver.execution.PreparedEffects prepareEffects(final ProviderContext context, final SubjectSnapshot snapshot,
            final ActionRequest request, final PreparedAction prepared) {
        throw new WeaverDomainRejection("DURABLE_EFFECT_PLAN_UNAVAILABLE");
    }
    PreparedAction prepareUndo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt);
    default PreparedAction prepareAreaUndo(final ProviderContext context, final SubjectSnapshot snapshot, final WeaverReceipt receipt,
            final hu.taliann.icesmp.dev.weaver.area.WeaverAreaCollection collection) {
        throw new WeaverDomainRejection("AREA_UNDO_UNAVAILABLE");
    }
    Optional<WeaverValueCatalog> catalog(ProviderContext context, SubjectSnapshot snapshot, String catalogId);
    ValueExportResult exportValue(ProviderContext context, SubjectSnapshot snapshot, String exportId);
    ImportValidation validateImport(ProviderContext context, SubjectSnapshot snapshot, String importId, WeaverValue value);
    RecoveryAssessment assessRecovery(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation);
    default RecoveryAssessment assessAreaRecovery(final RecoveryContext context, final SubjectSnapshot snapshot,
            final hu.taliann.icesmp.dev.weaver.area.WeaverAreaCollection collection, final WeaverOperationRecord operation) {
        return new RecoveryAssessment(hu.taliann.icesmp.dev.weaver.persistence.ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "AREA_ASSESSMENT_UNAVAILABLE");
    }
}
