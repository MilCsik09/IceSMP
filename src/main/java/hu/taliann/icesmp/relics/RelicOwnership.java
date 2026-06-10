package hu.taliann.icesmp.relics;

import java.util.UUID;

/**
 * Persistent ownership record for a singleton relic.
 *
 * @param owner the current owner of the relic
 * @param lastSeenMillis the last time the owner was seen online (epoch millis)
 */
public record RelicOwnership(
        UUID owner,
        long lastSeenMillis
) {
}
