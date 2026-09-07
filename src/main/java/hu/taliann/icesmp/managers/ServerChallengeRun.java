package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.integrity.*;
import java.util.*;

/** Native challenge identity and counter share one transition lock; snapshots contain no live objects. */
public final class ServerChallengeRun {
    public enum Status { ACTIVE, SUCCEEDED, FAILED, STOPPED }
    public record Snapshot(UUID id, ServerChallengeManager.ChallengeType type, long target, long progress,
                           long activeUntil, Status status) {
        public RewardSource.Event source() { return new RewardSource.Event("server-challenge", id); }
    }
    private final UUID id = UUID.randomUUID();
    private final ServerChallengeManager.ChallengeType type;
    private final long target;
    private final long startedAt;
    private final long activeUntil;
    private long progress;
    private Status status = Status.ACTIVE;

    public ServerChallengeRun(ServerChallengeManager.ChallengeType type, long target, long startedAt, long duration) {
        this.type = Objects.requireNonNull(type);
        if (target < 1 || startedAt < 0 || duration < 1) throw new IllegalArgumentException("Challenge bounds");
        this.target = target; this.startedAt = startedAt; this.activeUntil = Math.addExact(startedAt, duration);
    }
    public synchronized Snapshot snapshot() { return new Snapshot(id, type, target, progress, activeUntil, status); }
    public synchronized boolean record(ServerChallengeManager.ChallengeType kind, RewardContext contribution, long now) {
        Objects.requireNonNull(contribution).require(RewardChannel.SERVER_CHALLENGE, contribution.recipient());
        if (status != Status.ACTIVE || kind != type || now < startedAt || now >= activeUntil) return false;
        final Set<RewardSource> sources = new LinkedHashSet<>(contribution.sources()); sources.add(snapshot().source());
        if (sources.size() > 64 || !GameplayRewardGate.evaluate(new RewardContext(RewardChannel.SERVER_CHALLENGE,
                contribution.recipient(), List.copyOf(sources))).allowed()) return false;
        progress++;
        if (progress >= target) status = Status.SUCCEEDED;
        return true;
    }
    public synchronized boolean expire(long now) {
        if (status != Status.ACTIVE || now < activeUntil) return false;
        status = Status.FAILED; return true;
    }
    public synchronized boolean stop() {
        if (status != Status.ACTIVE) return false;
        status = Status.STOPPED; return true;
    }
    /** Recipient/source admission is repeated at delivery, including after a different run has started. */
    public static boolean rewardAllowed(Snapshot earned, RewardContext recipient) {
        Objects.requireNonNull(earned); Objects.requireNonNull(recipient);
        if (earned.status() != Status.SUCCEEDED) return false;
        final Set<RewardSource> sources = new LinkedHashSet<>(recipient.sources()); sources.add(earned.source());
        return sources.size() <= 64 && GameplayRewardGate.evaluate(new RewardContext(recipient.channel(),
                recipient.recipient(), List.copyOf(sources))).allowed();
    }
}
