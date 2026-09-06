package hu.taliann.icesmp.dev.weaver.execution;

import java.util.Map;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface StageOperation {
    CompletionStage<StageResult> execute(ExecutionContext context, Map<String, Object> payload);
}
