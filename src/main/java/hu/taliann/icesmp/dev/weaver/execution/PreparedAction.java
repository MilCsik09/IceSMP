package hu.taliann.icesmp.dev.weaver.execution;

import java.util.UUID;
import java.util.List;
import hu.taliann.icesmp.dev.weaver.api.ActionDescriptor;
import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;

public record PreparedAction(UUID operationId, ActionDescriptor descriptor, SubjectRef subject, String expectedBeforeFingerprint,
                             List<ExecutionStage> stages, OperationRecoveryPayload recoveryPayload, ReceiptFactory receiptFactory) {
    public PreparedAction {
        java.util.Objects.requireNonNull(operationId); java.util.Objects.requireNonNull(descriptor); java.util.Objects.requireNonNull(subject);
        stages = List.copyOf(stages); java.util.Objects.requireNonNull(recoveryPayload); java.util.Objects.requireNonNull(receiptFactory);
        final int cap = descriptor.areaSupport() == hu.taliann.icesmp.dev.weaver.api.AreaSupport.ENTITY_FANOUT
                ? descriptor.areaLimits().orElseThrow().maxEntities() + 1 : descriptor.areaSupport() == hu.taliann.icesmp.dev.weaver.api.AreaSupport.BLOCK_FANOUT ? descriptor.areaLimits().orElseThrow().maxBlocks() + 1 : 8;
        if (stages.isEmpty() || stages.size() > cap || stages.stream().map(ExecutionStage::id).distinct().count() != stages.size()
                || expectedBeforeFingerprint == null || expectedBeforeFingerprint.isBlank() || expectedBeforeFingerprint.length() > 256) {
            throw new IllegalArgumentException("Invalid prepared execution plan");
        }
    }
}
