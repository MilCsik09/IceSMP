package hu.taliann.icesmp.dev.weaver.integrity;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Adapter extension; unavailable entities/content retain active quarantine without force-loading. */
public interface WeaverInfluenceObserverProvider {
    List<InfluenceLifetimeDescriptor> influenceLifetimes();
    CompletionStage<InfluenceObservation> observeInfluence(WeaverInfluenceRecord influence);
}
