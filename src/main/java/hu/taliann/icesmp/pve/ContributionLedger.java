package hu.taliann.icesmp.pve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded, encounter-scoped contribution authority with one-shot personal settlement. */
public final class ContributionLedger {
    public static final int MAX_PARTICIPANTS = 128;

    public record Contribution(double damage, double tanking, double support,
                               int objectives, long lastMeaningfulAt) {
        public double score() {
            return damage + tanking * 0.55D + support * 0.7D + objectives * 100.0D;
        }

        /** Activity produced by the encounter itself, excluding support credited by another player. */
        public boolean encounterActive() {
            return damage > 0.0D || tanking > 0.0D || objectives > 0;
        }
    }

    private final UUID encounterId;
    private final long startedAt;
    private final Map<UUID, Contribution> entries = new LinkedHashMap<>();
    private final Set<UUID> settled = ConcurrentHashMap.newKeySet();
    private boolean closed;

    public ContributionLedger(final UUID encounterId, final long startedAt,
                              final Set<UUID> initialParticipants) {
        this.encounterId = Objects.requireNonNull(encounterId, "encounterId");
        if (startedAt < 0L) throw new IllegalArgumentException("invalid encounter time");
        this.startedAt = startedAt;
        if (initialParticipants != null) {
            for (final UUID participant : initialParticipants) register(participant);
        }
    }

    public UUID encounterId() { return encounterId; }

    public synchronized boolean register(final UUID playerId) {
        if (closed || playerId == null || entries.size() >= MAX_PARTICIPANTS) return false;
        entries.putIfAbsent(playerId, new Contribution(0.0D, 0.0D, 0.0D, 0, startedAt));
        return true;
    }

    public synchronized boolean recordDamage(final UUID playerId, final double amount,
                                             final long atMillis) {
        return add(playerId, amount, 0.0D, 0.0D, 0, atMillis);
    }

    public synchronized boolean recordTanking(final UUID playerId, final double amount,
                                              final long atMillis) {
        return add(playerId, 0.0D, amount, 0.0D, 0, atMillis);
    }

    /**
     * Support is credited only for a different player who has already produced encounter-owned
     * activity. This rejects pre-combat/unrelated healing while still allowing a healer to
     * late-register once their ally is demonstrably participating in the boss encounter.
     */
    public synchronized boolean recordSupport(final UUID actor, final UUID target,
                                              final double amount, final long atMillis) {
        if (actor == null || target == null || actor.equals(target)) return false;
        final Contribution targetContribution = entries.get(target);
        if (targetContribution == null || !targetContribution.encounterActive()) return false;
        return add(actor, 0.0D, 0.0D, amount, 0, atMillis);
    }

    public synchronized boolean recordObjective(final UUID playerId, final long atMillis) {
        return add(playerId, 0.0D, 0.0D, 0.0D, 1, atMillis);
    }

    public synchronized Contribution contribution(final UUID playerId) {
        return entries.getOrDefault(playerId, new Contribution(0.0D, 0.0D, 0.0D, 0, startedAt));
    }

    public synchronized List<Map.Entry<UUID, Contribution>> qualified(final double minimumScore,
                                                                       final long activeSince) {
        if (!Double.isFinite(minimumScore) || minimumScore < 0.0D) {
            throw new IllegalArgumentException("invalid contribution threshold");
        }
        final ArrayList<Map.Entry<UUID, Contribution>> result = new ArrayList<>();
        entries.forEach((player, contribution) -> {
            if (contribution.score() >= minimumScore
                    && contribution.lastMeaningfulAt() >= activeSince) {
                result.add(Map.entry(player, contribution));
            }
        });
        result.sort(Map.Entry.<UUID, Contribution>comparingByValue(
                Comparator.comparingDouble(Contribution::score)).reversed());
        return List.copyOf(result);
    }

    public boolean claimSettlement(final UUID playerId) {
        return playerId != null && settled.add(playerId);
    }

    public synchronized void close() {
        closed = true;
    }

    public synchronized boolean closed() { return closed; }

    public synchronized int size() { return entries.size(); }

    private boolean add(final UUID playerId, final double damage, final double tanking,
                        final double support, final int objectives, final long atMillis) {
        if (closed || playerId == null || atMillis < startedAt
                || !boundedAmount(damage) || !boundedAmount(tanking) || !boundedAmount(support)) {
            return false;
        }
        if (!entries.containsKey(playerId) && !register(playerId)) return false;
        final Contribution before = entries.get(playerId);
        entries.put(playerId, new Contribution(
                boundedSum(before.damage(), damage), boundedSum(before.tanking(), tanking),
                boundedSum(before.support(), support),
                Math.min(10_000, before.objectives() + objectives), atMillis));
        return damage > 0.0D || tanking > 0.0D || support > 0.0D || objectives > 0;
    }

    private static boolean boundedAmount(final double amount) {
        return Double.isFinite(amount) && amount >= 0.0D && amount <= 1_000_000.0D;
    }

    private static double boundedSum(final double left, final double right) {
        return Math.min(1_000_000_000.0D, left + right);
    }
}
