package hu.taliann.icesmp.dev.artifact;

import java.util.UUID;

/** Event data can outlive the callback without retaining an entity, item or block. */
public record DevArtifactInteraction(DevArtifactContext context, Kind kind, boolean sneaking,
                                      UUID entityId, BlockPosition block) {
    public enum Kind { RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK, RIGHT_CLICK_ENTITY, SWAP_HAND }
    public record BlockPosition(UUID worldId, int x, int y, int z) {}
}
