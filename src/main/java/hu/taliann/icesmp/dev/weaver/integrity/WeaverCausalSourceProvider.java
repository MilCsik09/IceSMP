package hu.taliann.icesmp.dev.weaver.integrity;

import hu.taliann.icesmp.integrity.*;
import java.util.*;

/** Native source consumer, invoked synchronously on the source owner; discovery uses immutable kinds only. */
public interface WeaverCausalSourceProvider {
    Set<GameplaySourceSubject.Kind> causalSourceKinds();
    List<RewardSource> captureCausalSources(GameplaySourceSubject subject);
}
