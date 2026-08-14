package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A HUD_STATE üzenet vezeték-DTO-ja: a szerveroldali {@code HudManager.HudSnapshot} és
 * {@code ClassHudState} projekciók szemantikáját hordozza — nem új state-modell, csak
 * a meglévő immutable snapshot sorosítása. Pure Java (nincs Bukkit/Fabric típus), a
 * Fabric-oldali példány bájtazonos port.
 *
 * <p>A kliens felé kizárólag megjelenítési adat megy: a snapshot már eleve display-only
 * (a PlaceholderAPI-bridge is ezt olvassa), rejtett szerver-state nincs benne. A double
 * mezők fix 8 bájtos IEEE-754 alakban utaznak, így a golden-vector tesztek bájtra
 * determinisztikusak maradnak.</p>
 */
public record HudStatePayload(
        String faction, String factionId, String factionTheme,
        String factionAccent, String factionAccentSoft,
        String className, int classLevel, String balance, boolean hasClass,
        int resource, int resourceMax, int resourcePercent, String resourceName,
        String event, List<String> partyLines, List<Currency> currencies,
        ClassHud classHud) {

    /** Immutable pénznem-sor; az {@code amount} formázott megjelenítési string. */
    public record Currency(String id, String displayName, String amount, boolean primary) {
    }

    /** Egy általános mechanika-csatorna (szám vagy szöveg) a kaszt-HUD-hoz. */
    public record Metric(String id, String label, String text,
                         double value, double maximum, String state) {
    }

    /** Egy diszkrét mechanika-slot (charge-pip, rúna, totemkerék-elem). */
    public record Slot(String id, String kind, String state, int progress, String label) {
    }

    /** A kaszt-specifikus valós idejű állapot; üres {@code classId} = nincs kaszt-HUD. */
    public record ClassHud(String classId, String specId, String specName,
                           String mechanicPrimary, String mechanicSecondary,
                           String state, String proc, int charges, int chargesMax,
                           List<String> mechanics, List<Metric> metrics, List<Slot> slots) {

        public ClassHud {
            mechanics = List.copyOf(mechanics);
            metrics = List.copyOf(metrics);
            slots = List.copyOf(slots);
        }

        public static ClassHud empty() {
            return new ClassHud("", "", "", "", "", "", "", 0, 0, List.of(), List.of(), List.of());
        }
    }

    public HudStatePayload {
        partyLines = List.copyOf(partyLines);
        currencies = List.copyOf(currencies);
        if (classHud == null) {
            classHud = ClassHud.empty();
        }
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, faction);
            ClientMessageCodec.writeString(out, factionId);
            ClientMessageCodec.writeString(out, factionTheme);
            ClientMessageCodec.writeString(out, factionAccent);
            ClientMessageCodec.writeString(out, factionAccentSoft);
            ClientMessageCodec.writeString(out, className);
            out.writeInt(classLevel);
            ClientMessageCodec.writeString(out, balance);
            out.writeBoolean(hasClass);
            out.writeInt(resource);
            out.writeInt(resourceMax);
            out.writeInt(resourcePercent);
            ClientMessageCodec.writeString(out, resourceName);
            ClientMessageCodec.writeString(out, event);
            ClientMessageCodec.writeStringList(out, partyLines);
            writeCount(out, currencies.size());
            for (final Currency currency : currencies) {
                ClientMessageCodec.writeString(out, currency.id());
                ClientMessageCodec.writeString(out, currency.displayName());
                ClientMessageCodec.writeString(out, currency.amount());
                out.writeBoolean(currency.primary());
            }
            ClientMessageCodec.writeString(out, classHud.classId());
            ClientMessageCodec.writeString(out, classHud.specId());
            ClientMessageCodec.writeString(out, classHud.specName());
            ClientMessageCodec.writeString(out, classHud.mechanicPrimary());
            ClientMessageCodec.writeString(out, classHud.mechanicSecondary());
            ClientMessageCodec.writeString(out, classHud.state());
            ClientMessageCodec.writeString(out, classHud.proc());
            out.writeInt(classHud.charges());
            out.writeInt(classHud.chargesMax());
            ClientMessageCodec.writeStringList(out, classHud.mechanics());
            writeCount(out, classHud.metrics().size());
            for (final Metric metric : classHud.metrics()) {
                ClientMessageCodec.writeString(out, metric.id());
                ClientMessageCodec.writeString(out, metric.label());
                ClientMessageCodec.writeString(out, metric.text());
                out.writeDouble(metric.value());
                out.writeDouble(metric.maximum());
                ClientMessageCodec.writeString(out, metric.state());
            }
            writeCount(out, classHud.slots().size());
            for (final Slot slot : classHud.slots()) {
                ClientMessageCodec.writeString(out, slot.id());
                ClientMessageCodec.writeString(out, slot.kind());
                ClientMessageCodec.writeString(out, slot.state());
                out.writeInt(slot.progress());
                ClientMessageCodec.writeString(out, slot.label());
            }
        });
    }

    public static HudStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> {
            final String faction = ClientMessageCodec.readString(in);
            final String factionId = ClientMessageCodec.readString(in);
            final String factionTheme = ClientMessageCodec.readString(in);
            final String factionAccent = ClientMessageCodec.readString(in);
            final String factionAccentSoft = ClientMessageCodec.readString(in);
            final String className = ClientMessageCodec.readString(in);
            final int classLevel = in.readInt();
            final String balance = ClientMessageCodec.readString(in);
            final boolean hasClass = in.readBoolean();
            final int resource = in.readInt();
            final int resourceMax = in.readInt();
            final int resourcePercent = in.readInt();
            final String resourceName = ClientMessageCodec.readString(in);
            final String event = ClientMessageCodec.readString(in);
            final List<String> partyLines = ClientMessageCodec.readStringList(in);
            final int currencyCount = readCount(in);
            final List<Currency> currencies = new ArrayList<>(currencyCount);
            for (int i = 0; i < currencyCount; i++) {
                currencies.add(new Currency(
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        in.readBoolean()));
            }
            final ClassHud classHud = readClassHud(in);
            return new HudStatePayload(faction, factionId, factionTheme, factionAccent,
                    factionAccentSoft, className, classLevel, balance, hasClass, resource,
                    resourceMax, resourcePercent, resourceName, event, partyLines, currencies,
                    classHud);
        });
    }

    private static ClassHud readClassHud(final DataInputStream in)
            throws IOException, ClientProtocolException {
        final String classId = ClientMessageCodec.readString(in);
        final String specId = ClientMessageCodec.readString(in);
        final String specName = ClientMessageCodec.readString(in);
        final String mechanicPrimary = ClientMessageCodec.readString(in);
        final String mechanicSecondary = ClientMessageCodec.readString(in);
        final String state = ClientMessageCodec.readString(in);
        final String proc = ClientMessageCodec.readString(in);
        final int charges = in.readInt();
        final int chargesMax = in.readInt();
        final List<String> mechanics = ClientMessageCodec.readStringList(in);
        final int metricCount = readCount(in);
        final List<Metric> metrics = new ArrayList<>(metricCount);
        for (int i = 0; i < metricCount; i++) {
            metrics.add(new Metric(
                    ClientMessageCodec.readString(in),
                    ClientMessageCodec.readString(in),
                    ClientMessageCodec.readString(in),
                    in.readDouble(),
                    in.readDouble(),
                    ClientMessageCodec.readString(in)));
        }
        final int slotCount = readCount(in);
        final List<Slot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new Slot(
                    ClientMessageCodec.readString(in),
                    ClientMessageCodec.readString(in),
                    ClientMessageCodec.readString(in),
                    in.readInt(),
                    ClientMessageCodec.readString(in)));
        }
        return new ClassHud(classId, specId, specName, mechanicPrimary, mechanicSecondary,
                state, proc, charges, chargesMax, mechanics, metrics, slots);
    }

    private static void writeCount(final DataOutputStream out, final int count) throws IOException {
        if (count > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new IOException("list exceeds protocol limit");
        }
        out.writeInt(count);
    }

    private static int readCount(final DataInputStream in) throws IOException, ClientProtocolException {
        final int count = in.readInt();
        if (count < 0 || count > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new ClientProtocolException("list length out of bounds: " + count);
        }
        return count;
    }
}
