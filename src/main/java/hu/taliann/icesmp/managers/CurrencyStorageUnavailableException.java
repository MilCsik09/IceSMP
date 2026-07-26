package hu.taliann.icesmp.managers;

/** Raised when a void wallet API cannot honestly report success while persistence is blocked. */
public final class CurrencyStorageUnavailableException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public CurrencyStorageUnavailableException(final String message) {
        super(message);
    }
}
