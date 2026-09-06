package hu.taliann.icesmp.dev.weaver.subject;

import java.util.UUID;
import java.util.Optional;
import java.util.OptionalLong;

public record ItemSlotRef(UUID holderId, WeaverSlot slot, Optional<String> logicalId,
                          Optional<UUID> instanceId, OptionalLong revision, String fingerprint) implements SubjectRef {
    public ItemSlotRef {
        java.util.Objects.requireNonNull(holderId);
        java.util.Objects.requireNonNull(slot);
        java.util.Objects.requireNonNull(logicalId);
        java.util.Objects.requireNonNull(instanceId);
        java.util.Objects.requireNonNull(revision);
        if (logicalId.isPresent()) hu.taliann.icesmp.dev.weaver.api.WeaverIds.content(logicalId.get());
        if (revision.isPresent() && revision.getAsLong() < 0) throw new IllegalArgumentException("Negative managed item revision");
        if (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid item fingerprint");
    }
    @Override public WeaverSubjectKind kind() { return WeaverSubjectKind.ITEM_SLOT; }
}
