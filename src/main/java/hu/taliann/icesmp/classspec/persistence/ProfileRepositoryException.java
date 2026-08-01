package hu.taliann.icesmp.classspec.persistence;

/** A durable profile operation failed before the candidate became authoritative. */
public class ProfileRepositoryException extends RuntimeException {

    public ProfileRepositoryException(final String message) {
        super(message);
    }

    public ProfileRepositoryException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /** The caller used a stale or skipped revision. */
    public static final class RevisionConflict extends ProfileRepositoryException {
        private final long expected;
        private final long actual;

        public RevisionConflict(final long expected, final long actual, final String detail) {
            super(detail);
            this.expected = expected;
            this.actual = actual;
        }

        public long expected() {
            return expected;
        }

        public long actual() {
            return actual;
        }
    }
}
