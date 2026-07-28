package hu.taliann.icesmp.moderation;

import java.util.Objects;
import java.util.UUID;

/** Strict durable logout-location snapshot for /offlinetp. */
public record LastKnownLocation(UUID playerId, String playerName, UUID worldId, String worldName,
                                double x, double y, double z, float yaw, float pitch,
                                long capturedAtMillis) {
    public LastKnownLocation {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(worldId, "worldId");
        playerName = requireText(playerName, "playerName", 64);
        worldName = requireText(worldName, "worldName", 128);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("location contains non-finite coordinate");
        }
        if (capturedAtMillis <= 0L) {
            throw new IllegalArgumentException("capturedAtMillis must be positive");
        }
    }

    private static String requireText(final String value, final String field, final int max) {
        final String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " must be 1.." + max + " characters");
        }
        return normalized;
    }
}
