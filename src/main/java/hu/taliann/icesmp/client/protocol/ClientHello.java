package hu.taliann.icesmp.client.protocol;

import java.util.List;

/**
 * A kliens bemutatkozása: a mod verziója, az általa beszélt protokoll-tartomány és a
 * felkínált presentation-capability nevek. A capability-lista csak AJÁNLAT — a szerver
 * dönt arról, mi aktiválódik, és az ismeretlen neveket szó nélkül eldobja (előre-
 * kompatibilitás: újabb kliens régi szerverrel is kézfogásképes marad).
 */
public record ClientHello(
        String clientVersion,
        int protocolMin,
        int protocolMax,
        List<String> capabilities) {

    public ClientHello {
        capabilities = List.copyOf(capabilities);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, clientVersion);
            out.writeInt(protocolMin);
            out.writeInt(protocolMax);
            ClientMessageCodec.writeStringList(out, capabilities);
        });
    }

    public static ClientHello decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new ClientHello(
                ClientMessageCodec.readString(in),
                in.readInt(),
                in.readInt(),
                ClientMessageCodec.readStringList(in)));
    }
}
