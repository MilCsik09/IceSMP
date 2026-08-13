package hu.taliann.icesmp.client.protocol;

import java.util.UUID;

/**
 * Egységes üzenet-boríték minden irányban.
 *
 * <p>A {@code sessionGeneration} köti az üzenetet az élő sessionhöz: reconnect után a
 * korábbi sessionből késve érkező kérés generation-eltérés miatt esik ki, nem tartalmi
 * validáción. A {@code sequence} session-en belül szigorúan monoton — duplikált/visszajátszott
 * csomag detektálására. A {@code requestId} a kérés-válasz párosítást szolgálja; a nem
 * korrelált (szerver által kezdeményezett) üzenetek a {@link #NO_REQUEST} értéket viselik.</p>
 */
public record MessageEnvelope(
        int protocolVersion,
        int messageType,
        long sessionGeneration,
        long sequence,
        UUID requestId,
        byte[] payload) {

    public static final UUID NO_REQUEST = new UUID(0L, 0L);

    public MessageEnvelope {
        if (requestId == null) {
            requestId = NO_REQUEST;
        }
        if (payload == null) {
            payload = new byte[0];
        } else {
            payload = payload.clone();
        }
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
