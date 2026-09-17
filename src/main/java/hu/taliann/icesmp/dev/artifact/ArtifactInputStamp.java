package hu.taliann.icesmp.dev.artifact;

import java.util.UUID;

/** Entity use can be followed by generic use packets from the same client gesture. */
public record ArtifactInputStamp(long tick, DevArtifactInteraction.Kind kind, UUID entity,
                                 DevArtifactInteraction.BlockPosition block) {
    public boolean suppresses(final ArtifactInputStamp next) {
        final long age = next.tick - tick;
        if (age < 0 || age > 2 || kind == DevArtifactInteraction.Kind.SWAP_HAND
                || next.kind == DevArtifactInteraction.Kind.SWAP_HAND) return equals(next);
        if (kind == DevArtifactInteraction.Kind.RIGHT_CLICK_ENTITY) {
            return next.kind != DevArtifactInteraction.Kind.RIGHT_CLICK_ENTITY
                    || java.util.Objects.equals(entity, next.entity);
        }
        return equals(next);
    }
}
