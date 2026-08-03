package hu.taliann.icesmp.playerprofile.domain.section;

import hu.taliann.icesmp.playerprofile.domain.ImmutableValues;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSection;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;

import java.util.Map;

/** Durable lifecycle metadata. Runtime session tokens are never persisted. */
public record LifecycleSection(String status, long profileCreatedAt, long updatedAt,
                               long lastJoinAt, long lastQuitAt, long sessionGeneration,
                               Map<String, Object> extensions) implements PlayerProfileSection {
    public LifecycleSection {
        status = ImmutableValues.id(status == null || status.isBlank() ? "active" : status, "status");
        profileCreatedAt = ImmutableValues.nonNegative(profileCreatedAt, "profileCreatedAt");
        updatedAt = ImmutableValues.nonNegative(updatedAt, "updatedAt");
        lastJoinAt = ImmutableValues.nonNegative(lastJoinAt, "lastJoinAt");
        lastQuitAt = ImmutableValues.nonNegative(lastQuitAt, "lastQuitAt");
        sessionGeneration = ImmutableValues.nonNegative(sessionGeneration, "sessionGeneration");
        extensions = ImmutableValues.map(extensions);
    }

    @Override public ProfileSectionId sectionId() { return ProfileSectionId.LIFECYCLE; }

    public static LifecycleSection empty(final long now) {
        return new LifecycleSection("active", now, now, 0, 0, 0, Map.of());
    }
}
