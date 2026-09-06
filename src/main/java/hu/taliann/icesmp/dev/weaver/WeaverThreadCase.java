package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.WeaverValue;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Session-owned typed values are never written to the durable store. */
public final class WeaverThreadCase {
    public record Thread(UUID id, WeaverValue value) {
        public Thread { java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(value); }
    }
    private final ArrayDeque<Thread> threads = new ArrayDeque<>();
    private UUID active;
    public synchronized Thread add(final WeaverValue value) {
        final Thread thread = new Thread(UUID.randomUUID(), value); threads.addFirst(thread); active = thread.id();
        while (threads.size() > 12) threads.removeLast();
        return thread;
    }
    public synchronized Optional<Thread> active() { return threads.stream().filter(thread -> thread.id().equals(active)).findFirst(); }
    public synchronized List<Thread> snapshot() { return List.copyOf(threads); }
    public synchronized boolean select(final UUID id) {
        if (threads.stream().noneMatch(thread -> thread.id().equals(id))) return false;
        active = id; return true;
    }
    public synchronized void remove(final UUID id) {
        threads.removeIf(thread -> thread.id().equals(id));
        if (id.equals(active)) active = threads.isEmpty() ? null : threads.getFirst().id();
    }
    public synchronized void clear() { active = null; threads.clear(); }
}
