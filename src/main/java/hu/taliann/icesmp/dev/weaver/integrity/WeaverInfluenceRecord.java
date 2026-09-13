package hu.taliann.icesmp.dev.weaver.integrity;

import hu.taliann.icesmp.dev.weaver.api.*;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;

/** Entity/item/event taint is monotonic; player/world/spatial effects keep a five-minute tail. */
public record WeaverInfluenceRecord(UUID id, DeveloperInfluence influence, WeaverInfluenceTarget target, boolean active, long quarantinedUntil,
        Optional<WeaverValue> observedLifetime) {
    public WeaverInfluenceRecord(UUID id, DeveloperInfluence influence, WeaverInfluenceTarget target, boolean active, long quarantinedUntil) {
        this(id, influence, target, active, quarantinedUntil, Optional.empty());
    }
    public WeaverInfluenceRecord {
        Objects.requireNonNull(id); Objects.requireNonNull(influence); Objects.requireNonNull(target);
        Objects.requireNonNull(observedLifetime);
        if (target.monotonic() && observedLifetime.isPresent()) throw new IllegalArgumentException("Monotonic influence needs no expiry observer");
        if (quarantinedUntil < 0 || target.monotonic() && quarantinedUntil != 0
                || !target.monotonic() && quarantinedUntil < Math.addExact(influence.appliedAt(), PlayerQuarantine.MINIMUM_TAIL_MILLIS)) throw new IllegalArgumentException("Influence lifetime weakens quarantine");
    }
    public static WeaverInfluenceRecord applied(final DeveloperInfluence origin, final WeaverInfluenceTarget target, final boolean active) {
        return new WeaverInfluenceRecord(UUID.randomUUID(), origin, target, active, target.monotonic() ? 0 : Math.addExact(origin.appliedAt(), PlayerQuarantine.MINIMUM_TAIL_MILLIS));
    }
    public WeaverInfluenceRecord ended(final long now) {
        if (now < influence.appliedAt()) throw new IllegalArgumentException("Influence time drift");
        return target.monotonic() ? this : new WeaverInfluenceRecord(id, influence, target, false,
                Math.max(quarantinedUntil, Math.addExact(now, PlayerQuarantine.MINIMUM_TAIL_MILLIS)), observedLifetime);
    }
    public boolean quarantines(final long now) { return influence.quarantinesRewards() && (target.monotonic() || active || now < quarantinedUntil); }
}
