package hu.taliann.icesmp.quest;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Bounded native quest continuations. Entered domain writes are observed, never cancelled/replayed. */
public final class QuestMutationQueue {
    private final int maxPlayers;
    private final int maxPerPlayer;
    private final int maxTotal;
    private final Map<UUID, Lane> lanes = new HashMap<>();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private boolean stopping;
    private int pending;

    public QuestMutationQueue() { this(256, 64, 4096); }

    public QuestMutationQueue(int maxPlayers, int maxPerPlayer, int maxTotal) {
        if (maxPlayers < 1 || maxPlayers > 256 || maxPerPlayer < 1 || maxPerPlayer > 64
                || maxTotal < 1 || maxTotal > 4096) throw new IllegalArgumentException("invalid quest queue limits");
        this.maxPlayers = maxPlayers; this.maxPerPlayer = maxPerPlayer; this.maxTotal = maxTotal;
    }

    public CompletionStage<Void> submit(UUID player, Supplier<CompletionStage<Void>> work) {
        Objects.requireNonNull(player); Objects.requireNonNull(work);
        final Entry entry = new Entry(work);
        final Lane lane;
        final boolean start;
        synchronized (this) {
            final Lane existing = lanes.get(player);
            if (stopping || pending >= maxTotal || existing != null && (existing.retired
                    || existing.waiting.size() + (existing.active == null ? 0 : 1) >= maxPerPlayer)
                    || existing == null && lanes.size() >= maxPlayers) return refused();
            lane = existing == null ? new Lane(player) : existing;
            lanes.putIfAbsent(player, lane);
            pending++;
            start = lane.active == null;
            if (start) lane.active = entry; else lane.waiting.addLast(entry);
        }
        if (start) start(lane, entry);
        return entry.result.minimalCompletionStage();
    }

    /** Drops not-yet-entered work on logout; an in-flight profile acknowledgement can still drain. */
    public CompletionStage<Void> retire(UUID player) {
        final Lane lane;
        final List<Entry> dropped;
        synchronized (this) {
            lane = lanes.get(Objects.requireNonNull(player));
            if (lane == null) return CompletableFuture.completedFuture(null);
            lane.retired = true;
            dropped = dropWaiting(lane);
        }
        refuse(dropped);
        return lane.drained.minimalCompletionStage();
    }

    /** Nonblocking shutdown barrier; the profile authority owns durability of already entered work. */
    public CompletionStage<Void> close() {
        final List<Entry> dropped = new ArrayList<>();
        synchronized (this) {
            stopping = true;
            for (Lane lane : lanes.values()) { lane.retired = true; dropped.addAll(dropWaiting(lane)); }
        }
        refuse(dropped);
        completeClosedIfDrained();
        return closed.minimalCompletionStage();
    }

    public synchronized State state() { return new State(lanes.size(), pending, stopping); }
    public record State(int players, int pending, boolean stopping) { }

    private void start(Lane lane, Entry entry) {
        final boolean allowed;
        synchronized (this) { allowed = !lane.retired && lane.active == entry; }
        if (!allowed) { finish(lane, entry, new RejectedExecutionException("Quest queue retired")); return; }
        // Domain entry is linearized above. No queue monitor is held while calling domain code.
        try {
            Objects.requireNonNull(entry.work.get(), "quest mutation stage")
                    .whenComplete((value, failure) -> finish(lane, entry, failure));
        } catch (Throwable failure) { finish(lane, entry, failure); }
    }

    private void finish(Lane lane, Entry entry, Throwable failure) {
        final List<Entry> dropped;
        final Entry next;
        final boolean drained;
        synchronized (this) {
            if (lane.active != entry) return;
            pending--;
            dropped = failure != null || lane.retired ? dropWaiting(lane) : List.of();
            next = lane.waiting.pollFirst();
            lane.active = next;
            drained = next == null;
            if (drained) lanes.remove(lane.player, lane);
        }
        // Completion callbacks may re-enter submit/retire. They run outside the queue monitor.
        if (failure == null) entry.result.complete(null); else entry.result.completeExceptionally(failure);
        for (Entry dependent : dropped) dependent.result.completeExceptionally(failure == null
                ? new RejectedExecutionException("Quest queue retired") : failure);
        if (drained) lane.drained.complete(null);
        else start(lane, next);
        completeClosedIfDrained();
    }

    private List<Entry> dropWaiting(Lane lane) {
        final List<Entry> dropped = List.copyOf(lane.waiting);
        pending -= dropped.size(); lane.waiting.clear(); return dropped;
    }

    private void completeClosedIfDrained() {
        final boolean drained;
        synchronized (this) { drained = stopping && pending == 0; }
        if (drained) closed.complete(null);
    }

    private static CompletionStage<Void> refused() {
        return CompletableFuture.failedFuture(new RejectedExecutionException("Quest queue unavailable or full"));
    }
    private static void refuse(List<Entry> entries) {
        for (Entry entry : entries) entry.result.completeExceptionally(new RejectedExecutionException("Quest queue retired"));
    }
    private static final class Lane {
        final UUID player;
        final ArrayDeque<Entry> waiting = new ArrayDeque<>();
        final CompletableFuture<Void> drained = new CompletableFuture<>();
        Entry active; boolean retired;
        Lane(UUID player) { this.player = player; }
    }
    private static final class Entry {
        final Supplier<CompletionStage<Void>> work;
        final CompletableFuture<Void> result = new CompletableFuture<>();
        Entry(Supplier<CompletionStage<Void>> work) { this.work = work; }
    }
}
