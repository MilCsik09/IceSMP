package hu.taliann.icesmp.client.protocol;

import java.util.List;

/**
 * A szerver kézfogás-válasza: a kiválasztott közös protokoll, a resource-pack séma
 * verziója és a ténylegesen engedélyezett capability-k. A kliens KIZÁRÓLAG az itt
 * visszaigazolt capability-khez tartozó viselkedést aktiválhatja.
 */
public record ServerHello(
        String pluginVersion,
        int selectedProtocol,
        int resourcePackSchema,
        List<String> enabledCapabilities) {

    public ServerHello {
        enabledCapabilities = List.copyOf(enabledCapabilities);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, pluginVersion);
            out.writeInt(selectedProtocol);
            out.writeInt(resourcePackSchema);
            ClientMessageCodec.writeStringList(out, enabledCapabilities);
        });
    }

    public static ServerHello decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new ServerHello(
                ClientMessageCodec.readString(in),
                in.readInt(),
                in.readInt(),
                ClientMessageCodec.readStringList(in)));
    }
}
