package hu.taliann.icesmp.client.protocol;

/**
 * Hibás/csonka/limitsértő kliens-payload. A bridge minden ilyen üzenetet
 * válasz nélkül eldob (fail closed) — a kivétel sosem jut játékos-üzenetig.
 */
public final class ClientProtocolException extends Exception {

    public ClientProtocolException(final String message) {
        super(message);
    }

    public ClientProtocolException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
