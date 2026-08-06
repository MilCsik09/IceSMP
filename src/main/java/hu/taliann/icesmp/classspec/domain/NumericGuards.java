package hu.taliann.icesmp.classspec.domain;

/** Checked arithmetic for durable numeric state. */
public final class NumericGuards {
    private NumericGuards() { }

    public static int addInt(final int current, final int delta, final String label) {
        if (delta < 0) {
            throw new IllegalArgumentException(label + " delta must be non-negative");
        }
        try {
            return Math.addExact(current, delta);
        } catch (final ArithmeticException overflow) {
            throw new IllegalArgumentException(label + " overflow", overflow);
        }
    }

    public static long addLong(final long current, final long delta, final String label) {
        if (delta < 0L) {
            throw new IllegalArgumentException(label + " delta must be non-negative");
        }
        try {
            return Math.addExact(current, delta);
        } catch (final ArithmeticException overflow) {
            throw new IllegalArgumentException(label + " overflow", overflow);
        }
    }

    public static long nextRevision(final long revision) {
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        try {
            return Math.addExact(revision, 1L);
        } catch (final ArithmeticException overflow) {
            throw new IllegalStateException("Profile revision exhausted", overflow);
        }
    }
}
