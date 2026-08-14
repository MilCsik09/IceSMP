package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.EventSpawnPointManager;
import org.bukkit.Location;

import java.util.Objects;

/** Builder-defined Prologue anchors; no authoritative map coordinates live in code. */
public final class PrologueWorldAccess {
    private final EventSpawnPointManager spawnPoints;

    public PrologueWorldAccess(final EventSpawnPointManager spawnPoints) {
        this.spawnPoints = Objects.requireNonNull(spawnPoints);
    }

    public Location gateAnchor() {
        return spawnPoints.resolveAnchorLocation("prologue-gate");
    }

    public Location gatheringAnchor() {
        final Location configured = spawnPoints.resolveAnchorLocation("prologue-gathering");
        return configured == null ? gateAnchor() : configured;
    }

    public Location breachAnchor() {
        final Location configured = spawnPoints.resolveAnchorLocation("prologue-breach");
        return configured == null ? gateAnchor() : configured;
    }

    public Location bossAnchor() {
        final Location configured = spawnPoints.resolveAnchorLocation("prologue-boss");
        return configured == null ? gateAnchor() : configured;
    }

    public boolean isAtGate(final Location location, final double radius) {
        return within(location, gateAnchor(), radius);
    }

    public static boolean within(final Location location, final Location anchor, final double radius) {
        if (location == null || location.getWorld() == null || anchor == null || anchor.getWorld() == null
                || !location.getWorld().equals(anchor.getWorld())) {
            return false;
        }
        final double safeRadius = Math.max(0.0D, radius);
        return location.distanceSquared(anchor) <= safeRadius * safeRadius;
    }
}
