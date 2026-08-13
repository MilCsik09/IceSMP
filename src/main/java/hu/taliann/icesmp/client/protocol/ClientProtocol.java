package hu.taliann.icesmp.client.protocol;

/**
 * Az IceSMP Client Bridge vezeték-szintű protokoll-konstansai.
 *
 * <p>A protokoll szándékosan pure-Java (nincs Bukkit/Fabric/Minecraft típus a
 * kontraktusban), hogy a kódoló-dekódoló réteg változtatás nélkül átemelhető legyen
 * a Fabric kliensmodba, és dependency-free regressziós suite tesztelhesse. A
 * mezőméretek fix szélességűek (nem varint), így a golden-vector tesztek bájtra
 * pontosan determinisztikusak.</p>
 */
public final class ClientProtocol {

    /** Plugin messaging csatorna. A kliensmod ugyanezt regisztrálja Fabric-oldalon. */
    public static final String CHANNEL = "icesmp:client";

    /**
     * Envelope-magic: hibás/idegen payload az első két bájton elbukik, mielőtt bármilyen
     * hosszmezőt értelmeznénk (fail closed).
     */
    public static final short MAGIC = 0x1CE5;

    /** A szerver-kód által ténylegesen beszélt protokoll-tartomány. */
    public static final int PROTOCOL_MIN = 1;
    public static final int PROTOCOL_MAX = 1;

    /**
     * Abszolút payload-plafon. A config (client.limits.max-payload-bytes) ez alá
     * szoríthat, fölé soha — a dekóder minden hosszmezőt ehhez is mér.
     */
    public static final int MAX_PACKET_BYTES = 65536;
    public static final int MAX_STRING_BYTES = 8192;
    public static final int MAX_LIST_ELEMENTS = 64;

    // Control üzenettípusok (0x01-0x1F a control-sáv; state/action/presentation sávok
    // a későbbi fázisokban nyílnak).
    public static final int MSG_CLIENT_HELLO = 0x01;
    public static final int MSG_SERVER_HELLO = 0x02;
    public static final int MSG_PROTOCOL_REJECT = 0x03;
    public static final int MSG_RESYNC_REQUEST = 0x04;
    public static final int MSG_RESYNC_BEGIN = 0x05;
    public static final int MSG_RESYNC_END = 0x06;
    public static final int MSG_PING = 0x07;
    public static final int MSG_PONG = 0x08;

    // Gépi olvasásra szánt elutasítás-kódok (a lokalizált szöveg kliens-oldali felelősség).
    public static final String REJECT_INVALID_HELLO = "INVALID_HELLO";
    public static final String REJECT_PROTOCOL_INCOMPATIBLE = "PROTOCOL_INCOMPATIBLE";
    public static final String REJECT_CLIENT_DISABLED = "CLIENT_DISABLED";

    private ClientProtocol() {
    }
}
