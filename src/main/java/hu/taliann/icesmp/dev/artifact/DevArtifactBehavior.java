package hu.taliann.icesmp.dev.artifact;

import java.util.Map;

public interface DevArtifactBehavior {
    String artifactId();
    Map<String, Object> initialState();
    void onIssued(DevArtifactContext context);
    void onRecovered(DevArtifactContext context);
    ArtifactInteractionResult onInteract(DevArtifactInteraction interaction);
    void tick(DevArtifactContext context, long nowMillis);
    Map<String, Object> saveBehaviorState();
    void loadBehaviorState(Map<String, Object> state);
    default void validateState(final DevArtifactState state) { loadBehaviorState(state.behaviorState()); }
    default void onConfigurationReload() {}
    default void onUnavailable() {}
    /** Called when the item is temporarily absent or fails a presentation check. */
    default void onItemUnavailable() { onUnavailable(); }
    default void bindInteractions(final java.util.function.Function<DevArtifactInteraction, ArtifactInteractionResult> handler,
                                  final Runnable unavailable) {
        throw new UnsupportedOperationException("Artifact behavior does not accept a frontend binding");
    }
    void shutdown();
}
