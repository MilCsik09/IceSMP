package hu.taliann.icesmp.dev.weaver.execution;

import java.util.Map;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface StageCompensation {
    CompletionStage<StageResult> execute(CompensationContext context, Map<String, Object> payload);
}
