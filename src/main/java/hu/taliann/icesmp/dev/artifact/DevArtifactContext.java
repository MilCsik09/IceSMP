package hu.taliann.icesmp.dev.artifact;

import hu.taliann.icesmp.managers.DevItemManager;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Only immutable identity crosses a storage continuation; player access resolves on its owner. */
public record DevArtifactContext(DevItemManager manager, String artifactId, UUID owner,
                                  UUID instanceId, long session) {
    public Player player() { return manager.contextPlayer(this); }
    public DevArtifactState state() { return manager.state(artifactId); }
    public boolean valid() { return manager.contextValid(this); }
    public boolean updateVolatile(final long revision, final Map<String, Object> state) {
        return manager.updateBehavior(this, revision, state);
    }
    public CompletionStage<DevArtifactState> commit(final long revision, final Map<String, Object> state) {
        return manager.commitBehavior(this, revision, state);
    }
    public void onOwner(final Consumer<Player> action, final Runnable unavailable) {
        manager.onOwner(this, action, unavailable);
    }
}
