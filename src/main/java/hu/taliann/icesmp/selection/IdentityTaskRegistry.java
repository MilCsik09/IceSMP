package hu.taliann.icesmp.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small generation-aware identity registry for transient scheduler tasks.
 *
 * <p>A completion callback may remove only the exact lease that created it. Invalidating the
 * registry advances the generation before draining it, so an install that started before reload
 * but publishes after the drain detects the generation change and fails closed.</p>
 */
public final class IdentityTaskRegistry<K, T> {

    public static final class Lease<T> {
        private final long generation;
        private volatile T task;

        private Lease(final long generation) {
            this.generation = generation;
        }

        public void attach(final T task) {
            this.task = task;
        }

        public T task() {
            return task;
        }
    }

    public record Installation<T>(Lease<T> current, Lease<T> previous, boolean active) { }

    private final AtomicLong generation = new AtomicLong();
    private final ConcurrentHashMap<K, Lease<T>> leases = new ConcurrentHashMap<>();

    public Installation<T> install(final K key) {
        if (key == null) {
            throw new IllegalArgumentException("A task-registry kulcsa nem lehet null.");
        }
        final long observedGeneration = generation.get();
        final Lease<T> current = new Lease<>(observedGeneration);
        final Lease<T> previous = leases.put(key, current);
        if (generation.get() != observedGeneration) {
            leases.remove(key, current);
            return new Installation<>(current, previous, false);
        }
        return new Installation<>(current, previous, true);
    }

    public boolean isCurrent(final K key, final Lease<T> lease) {
        return key != null && lease != null
                && lease.generation == generation.get()
                && leases.get(key) == lease;
    }

    public boolean remove(final K key, final Lease<T> lease) {
        return key != null && lease != null && leases.remove(key, lease);
    }

    public Lease<T> remove(final K key) {
        return key == null ? null : leases.remove(key);
    }

    /** Invalidates all prior leases before returning the exact task sessions that must be cancelled. */
    public List<Lease<T>> invalidateAndDrain() {
        generation.incrementAndGet();
        final List<Lease<T>> drained = new ArrayList<>(leases.values());
        leases.clear();
        return List.copyOf(drained);
    }

    public int size() {
        return leases.size();
    }
}
