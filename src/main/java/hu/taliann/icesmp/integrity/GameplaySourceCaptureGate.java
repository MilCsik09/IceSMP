package hu.taliann.icesmp.integrity;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Native owner-side provenance extension; unknown/failed capture is never interpreted as a clean source. */
public final class GameplaySourceCaptureGate {
    private static final AtomicReference<Binding> CAPTURE = new AtomicReference<>();
    private GameplaySourceCaptureGate() { }
    public static final class Binding implements AutoCloseable {
        private final Function<GameplaySourceSubject, List<RewardSource>> capture;
        private Binding(Function<GameplaySourceSubject, List<RewardSource>> capture) { this.capture = Objects.requireNonNull(capture); }
        @Override public void close() { CAPTURE.compareAndSet(this, null); }
    }
    public static Binding install(Function<GameplaySourceSubject, List<RewardSource>> capture) {
        final var binding = new Binding(capture);
        if (!CAPTURE.compareAndSet(null, binding)) throw new IllegalStateException("Source capture already installed");
        return binding;
    }
    public static List<RewardSource> capture(GameplaySourceSubject subject) {
        Objects.requireNonNull(subject); final var binding = CAPTURE.get();
        if (binding == null) throw new IllegalStateException("Source capture unavailable");
        try {
            final var result = List.copyOf(binding.capture.apply(subject));
            if (result.size() > 32 || CAPTURE.get() != binding) throw new IllegalStateException("Source capture unavailable");
            return result;
        } catch (RuntimeException | LinkageError unavailable) { throw new IllegalStateException("Source capture unavailable"); }
    }
}
