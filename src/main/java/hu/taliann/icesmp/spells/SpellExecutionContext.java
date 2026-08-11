package hu.taliann.icesmp.spells;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Synchronous cast-output context used by legacy/bespoke spells that already
 * route damage and healing through common primitives.
 *
 * <p>The context is deliberately thread-local and contains no Bukkit objects.
 * Folia region threads therefore cannot leak modifiers into one another. A
 * delayed/channel/projectile behavior must capture the immutable
 * {@link CastModifiers} explicitly; it must not assume this scope survives a
 * scheduler hop.</p>
 */
public final class SpellExecutionContext {

    private static final ThreadLocal<Deque<CastModifiers>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private SpellExecutionContext() {
    }

    public static CastModifiers current() {
        final CastModifiers current = STACK.get().peek();
        return current == null ? CastModifiers.IDENTITY : current;
    }

    public static CastModifiers capture() {
        return current();
    }

    public static Scope open(final CastModifiers modifiers) {
        final Deque<CastModifiers> stack = STACK.get();
        stack.push(modifiers == null ? CastModifiers.IDENTITY : modifiers);
        return new Scope(Thread.currentThread(), stack);
    }

    public static final class Scope implements AutoCloseable {
        private final Thread ownerThread;
        private final Deque<CastModifiers> ownerStack;
        private boolean closed;

        private Scope(final Thread ownerThread, final Deque<CastModifiers> ownerStack) {
            this.ownerThread = ownerThread;
            this.ownerStack = ownerStack;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != ownerThread) {
                throw new IllegalStateException("A spell execution scope must close on the thread that opened it");
            }
            if (ownerStack.isEmpty()) {
                throw new IllegalStateException("Spell execution context stack underflow");
            }
            ownerStack.pop();
            closed = true;
            if (ownerStack.isEmpty()) {
                STACK.remove();
            }
        }
    }
}
