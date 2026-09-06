package hu.taliann.icesmp.dev.weaver.api;

import java.util.Set;
import java.util.Optional;
import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;
import hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind;
import hu.taliann.icesmp.dev.weaver.execution.PreparedAction;
import hu.taliann.icesmp.dev.weaver.persistence.RecoveryAssessment;
import hu.taliann.icesmp.dev.weaver.persistence.WeaverOperationRecord;

public interface WorldWeaverProvider {
    String id();
    int contractVersion();
    Set<WeaverSubjectKind> supportedKinds();
    ProviderContribution contribution();
    ProviderCoverage coverage();
    ProviderDiscovery discover(SubjectSnapshot snapshot);
    InspectionResult inspect(ProviderContext context, SubjectSnapshot snapshot, String facetId);
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request);
    default hu.taliann.icesmp.dev.weaver.execution.PreparedEffects prepareEffects(final ProviderContext context, final SubjectSnapshot snapshot,
            final ActionRequest request, final PreparedAction prepared) {
        throw new WeaverDomainRejection("DURABLE_EFFECT_PLAN_UNAVAILABLE");
    }
    PreparedAction prepareUndo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt);
    Optional<WeaverValueCatalog> catalog(ProviderContext context, SubjectSnapshot snapshot, String catalogId);
    ValueExportResult exportValue(ProviderContext context, SubjectSnapshot snapshot, String exportId);
    ImportValidation validateImport(ProviderContext context, SubjectSnapshot snapshot, String importId, WeaverValue value);
    RecoveryAssessment assessRecovery(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation);
}
