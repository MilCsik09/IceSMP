package hu.taliann.icesmp.dev.weaver.execution;

import java.util.List;
import hu.taliann.icesmp.dev.weaver.api.WeaverReceipt;

@FunctionalInterface
public interface ReceiptFactory {
    WeaverReceipt create(PreparedAction action, List<StageResult> results, long appliedAt);
}
