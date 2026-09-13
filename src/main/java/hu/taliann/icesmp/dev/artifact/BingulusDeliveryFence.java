package hu.taliann.icesmp.dev.artifact;

import java.util.LinkedHashMap;
import java.util.Map;

/** A persisted inventory-delivery claim is ambiguous after a crash and cannot be replayed. */
public final class BingulusDeliveryFence {
    private BingulusDeliveryFence() {}
    public static boolean held(final Map<String, Object> behavior) { return behavior.containsKey("delivery"); }
    public static Map<String, Object> claim(final Map<String, Object> behavior) {
        if (held(behavior) || !behavior.containsKey("pending")) throw new IllegalStateException("Reward claim unavailable");
        final Map<String, Object> claimed = new LinkedHashMap<>(behavior);
        claimed.put("delivery", "NEEDS_REVIEW");
        return ArtifactStateValue.freeze(claimed);
    }
}
