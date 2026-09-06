package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

public final class WeaverSelectionStore {
    private SubjectRef current;
    private final ArrayDeque<SubjectRef> recent = new ArrayDeque<>();
    public synchronized void select(final SubjectRef subject) {
        java.util.Objects.requireNonNull(subject);
        if (subject.equals(current)) return;
        if (current != null) { recent.remove(current); recent.addFirst(current); }
        current = subject; recent.remove(subject);
        while (recent.size() > 32) recent.removeLast();
    }
    public synchronized Optional<SubjectRef> current() { return Optional.ofNullable(current); }
    public synchronized List<SubjectRef> recent() { return List.copyOf(recent); }
    public synchronized Optional<SubjectRef> previous() {
        if (recent.isEmpty()) return Optional.ofNullable(current);
        final SubjectRef previous = recent.removeFirst(); select(previous); return Optional.of(previous);
    }
    public synchronized void clear() { current = null; recent.clear(); }
}
