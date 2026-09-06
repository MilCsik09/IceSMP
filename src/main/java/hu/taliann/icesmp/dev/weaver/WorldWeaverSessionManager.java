package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import hu.taliann.icesmp.dev.weaver.api.IntegrityMode;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Only the fixed primary developer can own a frontend session; there is no permission fallback. */
public final class WorldWeaverSessionManager {
    public static final class Session {
        private final UUID actor;
        private final UUID id = UUID.randomUUID();
        private final UUID artifactInstance;
        private final long artifactGeneration;
        private final WeaverSelectionStore selection = new WeaverSelectionStore();
        private final WeaverThreadCase threads = new WeaverThreadCase();
        private final WeaverArming arming;
        private volatile boolean active = true;
        private volatile long viewRevision;
        private volatile IntegrityMode mode = IntegrityMode.SANDBOX;
        private Session(final UUID actor, final UUID artifactInstance, final long generation, final LongSupplier clock) {
            this.actor = actor; this.artifactInstance = artifactInstance; artifactGeneration = generation; arming = new WeaverArming(clock);
        }
        public UUID actor() { return actor; }
        public UUID id() { return id; }
        public UUID artifactInstance() { return artifactInstance; }
        public long artifactGeneration() { return artifactGeneration; }
        public boolean active() { return active; }
        public long viewRevision() { return viewRevision; }
        public synchronized long nextView() { if (!active) throw new IllegalStateException("Closed Weaver session"); return viewRevision = Math.incrementExact(viewRevision); }
        public WeaverSelectionStore selection() { return selection; }
        public WeaverThreadCase threads() { return threads; }
        public WeaverArming arming() { return arming; }
        public IntegrityMode mode() { return mode; }
        public void mode(final IntegrityMode mode) { this.mode = java.util.Objects.requireNonNull(mode); arming.clear(); }
        private void close() { active = false; arming.clear(); selection.clear(); threads.clear(); }
    }
    private final LongSupplier monotonicMillis;
    private Session current;
    public WorldWeaverSessionManager(final LongSupplier clock) { monotonicMillis = java.util.Objects.requireNonNull(clock); }
    public synchronized Session open(final UUID actor, final UUID artifactInstance, final long generation) {
        if (!HiddenDevAuthority.isDeveloper(actor) || artifactInstance == null || generation < 1) throw new SecurityException("Primary developer artifact session required");
        if (current != null && current.active() && current.actor().equals(actor) && current.artifactInstance().equals(artifactInstance)
                && current.artifactGeneration() == generation) return current;
        if (current != null) current.close();
        current = new Session(actor, artifactInstance, generation, monotonicMillis); return current;
    }
    public synchronized Optional<Session> current(final UUID actor) {
        return current != null && current.active() && current.actor().equals(actor) ? Optional.of(current) : Optional.empty();
    }
    public synchronized boolean matches(final UUID actor, final UUID session, final long view) {
        return current(actor).filter(value -> value.id().equals(session) && value.viewRevision() == view).isPresent();
    }
    public synchronized void close(final UUID actor) {
        if (current != null && current.actor().equals(actor)) { current.close(); current = null; }
    }
    public synchronized void shutdown() { if (current != null) current.close(); current = null; }
}
