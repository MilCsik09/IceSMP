package hu.taliann.icesmp.utils;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Thread-safe pozíció-tükör a kereszt-régiós közelség-döntésekhez (párt-XP-megosztás,
 * personal loot). Idegen régió játékosának pozícióját tilos közvetlenül olvasni Folián —
 * a tükröt a játékos SAJÁT szálán futó move/teleport/join eventek töltik, a jutalom-utak
 * pedig innen olvasnak. A blokk-váltásra szűrt frissítés miatt a pontosság ±1 blokk,
 * ami a több tíz blokkos megosztási sugarakhoz bőven elég.
 */
public final class PositionCache {

    private static final Map<UUID, Location> POSITIONS = new ConcurrentHashMap<>();

    private PositionCache() {
    }

    public static void update(final UUID playerId, final Location location) {
        if (playerId != null && location != null && location.getWorld() != null) {
            POSITIONS.put(playerId, location.clone());
        }
    }

    public static void remove(final UUID playerId) {
        if (playerId != null) {
            POSITIONS.remove(playerId);
        }
    }

    /** Az utolsó ismert pozíció másolata, vagy null, ha nincs adat (fail-closed a hívóknál). */
    public static Location get(final UUID playerId) {
        final Location location = playerId == null ? null : POSITIONS.get(playerId);
        return location == null ? null : location.clone();
    }

    /** Cross-region-safe nearby-player list using only owner-thread-maintained mirrors. */
    public static java.util.List<UUID> nearbyPlayerIds(final UUID sourceId, final double radius) {
        final Location source = get(sourceId);
        return nearbyPlayerIds(source, radius, id -> true, Integer.MAX_VALUE);
    }

    /**
     * Cross-region-safe bounded nearby-player snapshot around an arbitrary world location.
     * Callers can apply cache-only eligibility predicates (for example GameModeCache) without
     * touching a foreign-region Player object. Stale/unloaded-world entries fail closed.
     */
    public static java.util.List<UUID> nearbyPlayerIds(final Location source, final double radius,
                                                       final Predicate<UUID> filter,
                                                       final int maximumResults) {
        if (source == null || radius < 0.0D || !Double.isFinite(radius)
                || filter == null || maximumResults < 1) {
            return java.util.List.of();
        }
        final org.bukkit.World sourceWorld;
        try {
            sourceWorld = source.getWorld();
        } catch (final Exception unloadedWorld) {
            return java.util.List.of();
        }
        if (sourceWorld == null) return java.util.List.of();
        final double radiusSquared = radius * radius;
        final java.util.ArrayList<UUID> nearby = new java.util.ArrayList<>(
                Math.min(maximumResults, 128));
        for (final Map.Entry<UUID, Location> entry : POSITIONS.entrySet()) {
            if (!filter.test(entry.getKey())) continue;
            final Location candidate = entry.getValue();
            try {
                final org.bukkit.World candidateWorld = candidate.getWorld();
                if (candidateWorld != null && candidateWorld.getUID().equals(sourceWorld.getUID())
                        && candidate.distanceSquared(source) <= radiusSquared) {
                    nearby.add(entry.getKey());
                    if (nearby.size() >= maximumResults) break;
                }
            } catch (final Exception unloadedWorld) {
                // Stale mirror entries are ignored; no Bukkit Player access or cross-region hop.
            }
        }
        return java.util.List.copyOf(nearby);
    }

    /** Cross-region-safe nearby-player query using only owner-thread-maintained mirrors. */
    public static boolean hasNearbyPlayer(final UUID sourceId, final double radius,
                                          final Predicate<UUID> filter) {
        final Location source = get(sourceId);
        if (source == null || source.getWorld() == null || radius < 0.0D) {
            return false;
        }
        final double radiusSquared = radius * radius;
        for (final Map.Entry<UUID, Location> entry : POSITIONS.entrySet()) {
            if (entry.getKey().equals(sourceId) || !filter.test(entry.getKey())) {
                continue;
            }
            final Location candidate = entry.getValue();
            if (candidate.getWorld() != null && candidate.getWorld().getUID().equals(source.getWorld().getUID())
                    && candidate.distanceSquared(source) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }
}
