package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.managers.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Folia-safe asynchronous party-position aggregation. */
public final class PartyRewardResolver {

    private static final long TIMEOUT_TICKS = 4L;

    private PartyRewardResolver() {
    }

    public static boolean hasPartyPair(final PartyManager partyManager,
                                       final UUID sourceId) {
        if (partyManager == null || sourceId == null) {
            return false;
        }
        synchronized (partyManager) {
            final PartyManager.Party party = partyManager.getParty(sourceId);
            return party != null && party.size() >= 2;
        }
    }

    /**
     * Resolves party members near an immutable coordinate snapshot. Party membership is copied while
     * holding the manager monitor; every live Player field is then read on that player's scheduler.
     */
    public static void resolveNearby(final JavaPlugin plugin, final PartyManager partyManager,
                                     final UUID sourceId, final UUID worldId,
                                     final double x, final double y, final double z,
                                     final double radius, final Consumer<List<UUID>> callback) {
        if (plugin == null || partyManager == null || sourceId == null
                || worldId == null || callback == null) {
            if (callback != null) {
                callback.accept(List.of());
            }
            return;
        }
        final List<UUID> members;
        synchronized (partyManager) {
            final PartyManager.Party party = partyManager.getParty(sourceId);
            members = party == null ? List.of() : party.getMembers();
        }
        if (members.isEmpty()) {
            callback.accept(List.of());
            return;
        }

        final double safeRadius = Math.max(0.0D, radius);
        final double radiusSq = safeRadius * safeRadius;
        final Set<UUID> nearby = ConcurrentHashMap.newKeySet();
        final AtomicInteger remaining = new AtomicInteger(members.size());
        final AtomicBoolean completed = new AtomicBoolean(false);

        final Runnable complete = () -> {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            final List<UUID> result = new ArrayList<>(nearby);
            result.sort((a, b) -> {
                if (a.equals(sourceId)) {
                    return -1;
                }
                if (b.equals(sourceId)) {
                    return 1;
                }
                return a.compareTo(b);
            });
            callback.accept(List.copyOf(result));
        };
        final Runnable answered = () -> {
            if (remaining.decrementAndGet() == 0) {
                complete.run();
            }
        };

        for (final UUID memberId : members) {
            final Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                answered.run();
                continue;
            }
            member.getScheduler().run(plugin, task -> {
                try {
                    if (member.isOnline() && member.getWorld().getUID().equals(worldId)) {
                        final var location = member.getLocation();
                        final double dx = location.getX() - x;
                        final double dy = location.getY() - y;
                        final double dz = location.getZ() - z;
                        if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                            nearby.add(memberId);
                        }
                    }
                } finally {
                    answered.run();
                }
            }, null);
        }
        Bukkit.getGlobalRegionScheduler().runDelayed(
                plugin, task -> complete.run(), TIMEOUT_TICKS);
    }
}
