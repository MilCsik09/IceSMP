package hu.taliann.icesmp.client.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure-Java envelope- és payload-kodek.
 *
 * <p>Minden hosszmező explicit felső korláttal dekódolódik, MIELŐTT allokálnánk —
 * egy hamis hosszmező így nem tud memóriát foglaltatni vagy csonka olvasásba futni.
 * Minden hibaút {@link ClientProtocolException}: a hívó fail closed módon eldobja a
 * csomagot. A stringek szigorúan UTF-8-ak (nem a DataOutputStream modified-UTF-8-a),
 * hogy a Fabric-oldali implementáció bájtazonos maradhasson.</p>
 */
public final class ClientMessageCodec {

    private ClientMessageCodec() {
    }

    public static byte[] encodeEnvelope(final MessageEnvelope envelope) {
        if (envelope.payload().length > ClientProtocol.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload exceeds envelope limit");
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 + envelope.payload().length);
        final DataOutputStream out = new DataOutputStream(buffer);
        try {
            out.writeShort(ClientProtocol.MAGIC);
            out.writeByte(envelope.protocolVersion());
            out.writeByte(envelope.messageType());
            out.writeLong(envelope.sessionGeneration());
            out.writeLong(envelope.sequence());
            out.writeLong(envelope.requestId().getMostSignificantBits());
            out.writeLong(envelope.requestId().getLeastSignificantBits());
            out.writeInt(envelope.payload().length);
            out.write(envelope.payload());
        } catch (final IOException impossible) {
            // ByteArrayOutputStream nem dob IOException-t; a szignatúra-kényszer miatt kell.
            throw new IllegalStateException("in-memory encode failed", impossible);
        }
        return buffer.toByteArray();
    }

    public static MessageEnvelope decodeEnvelope(final byte[] wire) throws ClientProtocolException {
        if (wire == null || wire.length > ClientProtocol.MAX_PACKET_BYTES) {
            throw new ClientProtocolException("packet size out of bounds");
        }
        final DataInputStream in = new DataInputStream(new ByteArrayInputStream(wire));
        try {
            if (in.readShort() != ClientProtocol.MAGIC) {
                throw new ClientProtocolException("bad magic");
            }
            final int protocolVersion = in.readUnsignedByte();
            final int messageType = in.readUnsignedByte();
            final long generation = in.readLong();
            final long sequence = in.readLong();
            final UUID requestId = new UUID(in.readLong(), in.readLong());
            final int payloadLength = in.readInt();
            if (payloadLength < 0 || payloadLength > ClientProtocol.MAX_PAYLOAD_BYTES) {
                throw new ClientProtocolException("payload length out of bounds: " + payloadLength);
            }
            final byte[] payload = new byte[payloadLength];
            in.readFully(payload);
            if (in.read() != -1) {
                throw new ClientProtocolException("trailing bytes after payload");
            }
            return new MessageEnvelope(protocolVersion, messageType, generation, sequence, requestId, payload);
        } catch (final EOFException truncated) {
            throw new ClientProtocolException("truncated envelope", truncated);
        } catch (final IOException failure) {
            throw new ClientProtocolException("unreadable envelope", failure);
        }
    }

    static void writeString(final DataOutputStream out, final String value) throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ClientProtocol.MAX_STRING_BYTES) {
            throw new IOException("string exceeds protocol limit");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static String readString(final DataInputStream in) throws IOException, ClientProtocolException {
        final int length = in.readInt();
        if (length < 0 || length > ClientProtocol.MAX_STRING_BYTES) {
            throw new ClientProtocolException("string length out of bounds: " + length);
        }
        final byte[] bytes = new byte[length];
        in.readFully(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (final CharacterCodingException malformed) {
            throw new ClientProtocolException("invalid UTF-8 string", malformed);
        }
    }

    static void writeStringList(final DataOutputStream out, final List<String> values) throws IOException {
        if (values.size() > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new IOException("list exceeds protocol limit");
        }
        out.writeInt(values.size());
        for (final String value : values) {
            writeString(out, value);
        }
    }

    static List<String> readStringList(final DataInputStream in) throws IOException, ClientProtocolException {
        final int count = in.readInt();
        if (count < 0 || count > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new ClientProtocolException("list length out of bounds: " + count);
        }
        final List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(readString(in));
        }
        return values;
    }

    static byte[] encodePayload(final PayloadWriter writer) {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        final DataOutputStream out = new DataOutputStream(buffer);
        try {
            writer.write(out);
        } catch (final IOException invalid) {
            throw new IllegalArgumentException("payload violates protocol limits", invalid);
        }
        return buffer.toByteArray();
    }

    static <T> T decodePayload(final byte[] payload, final PayloadReader<T> reader) throws ClientProtocolException {
        final DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        try {
            final T value = reader.read(in);
            if (in.read() != -1) {
                throw new ClientProtocolException("trailing bytes in payload");
            }
            return value;
        } catch (final EOFException truncated) {
            throw new ClientProtocolException("truncated payload", truncated);
        } catch (final IOException failure) {
            throw new ClientProtocolException("unreadable payload", failure);
        }
    }

    @FunctionalInterface
    interface PayloadWriter {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    interface PayloadReader<T> {
        T read(DataInputStream in) throws IOException, ClientProtocolException;
    }
}
