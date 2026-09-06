package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.api.WeaverReceipt;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent;
import hu.taliann.icesmp.dev.weaver.persistence.WeaverEffectCommit;
import java.util.List;
import java.util.Objects;

/** Providers declare bounded quarantine before stages and materialize immutable effects after observed results. */
public record PreparedEffects(WeaverEffectIntent intent, Factory factory) {
    @FunctionalInterface public interface Factory {
        WeaverEffectCommit create(PreparedAction action, List<StageResult> results, WeaverReceipt receipt, long nextProjectionSequence);
    }
    public PreparedEffects { Objects.requireNonNull(intent); Objects.requireNonNull(factory); }
}
