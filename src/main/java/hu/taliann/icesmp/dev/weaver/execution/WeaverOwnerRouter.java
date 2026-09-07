package hu.taliann.icesmp.dev.weaver.execution;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Callbacks resolve live handles inside their owner and return immutable data only. */
public interface WeaverOwnerRouter extends AutoCloseable {
    <T> CompletionStage<T> submit(ExecutionOwner owner, UUID actor, Duration timeout, Supplier<CompletionStage<T>> task);
    @Override void close();
}
