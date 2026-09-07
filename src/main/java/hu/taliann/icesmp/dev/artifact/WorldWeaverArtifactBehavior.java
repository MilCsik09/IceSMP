package hu.taliann.icesmp.dev.artifact;

import hu.taliann.icesmp.security.HiddenDevAuthority;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class WorldWeaverArtifactBehavior implements DevArtifactBehavior {
    public static final String ID = "dev_world_weaver";
    private final Function<DevArtifactInteraction, ArtifactInteractionResult> interactions;

    public WorldWeaverArtifactBehavior(final Function<DevArtifactInteraction, ArtifactInteractionResult> interactions) {
        this.interactions = java.util.Objects.requireNonNull(interactions);
    }

    public static DevArtifactDefinition definition(final BooleanSupplier enabled) {
        return new DevArtifactDefinition(ID, new FixedArtifactOwner(HiddenDevAuthority.PRIMARY_DEVELOPER),
                () -> new DevArtifactPresentation("ECHO_SHARD", "&5&lVilágszövő",
                        List.of("&8A világ szálai a kezedben."), Map.of(
                        DevArtifactPresentation.ModelState.IDLE, "icesmp:dev_world_weaver_idle",
                        DevArtifactPresentation.ModelState.SUBJECT_LOCKED, "icesmp:dev_world_weaver_subject",
                        DevArtifactPresentation.ModelState.THREAD_HELD, "icesmp:dev_world_weaver_thread",
                        DevArtifactPresentation.ModelState.CANON_ARMED, "icesmp:dev_world_weaver_canon")),
                () -> new DevArtifactPolicy(enabled.getAsBoolean(), true, true, true, 20));
    }

    @Override public String artifactId() { return ID; }
    @Override public Map<String, Object> initialState() { return Map.of(); }
    @Override public void onIssued(final DevArtifactContext context) {}
    @Override public void onRecovered(final DevArtifactContext context) {}
    @Override public ArtifactInteractionResult onInteract(final DevArtifactInteraction interaction) {
        if (!HiddenDevAuthority.isDeveloper(interaction.context().owner()) || !interaction.context().valid()) {
            return ArtifactInteractionResult.AUTHORITY_REJECTED;
        }
        return interactions.apply(interaction);
    }
    @Override public void tick(final DevArtifactContext context, final long nowMillis) {}
    @Override public Map<String, Object> saveBehaviorState() { return Map.of(); }
    @Override public void loadBehaviorState(final Map<String, Object> state) {
        if (!state.isEmpty()) throw new IllegalArgumentException("Unexpected artifact-shell state");
    }
    @Override public void shutdown() {}
}
