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

    /** Abszolút vezeték-szintű csomagplafon; a config ezt csak szűkítheti. */
    public static final int MAX_PACKET_BYTES = 65536;
    /** A fix envelope-fejléc mérete a payload nélkül. */
    public static final int ENVELOPE_HEADER_BYTES = 40;
    /** Legnagyobb payload, amely a fejléccel együtt még belefér a csomagplafonba. */
    public static final int MAX_PAYLOAD_BYTES = MAX_PACKET_BYTES - ENVELOPE_HEADER_BYTES;
    public static final int MAX_STRING_BYTES = 8192;
    public static final int MAX_LIST_ELEMENTS = 64;

    public static final int MSG_CLIENT_HELLO = 0x01;
    public static final int MSG_SERVER_HELLO = 0x02;
    public static final int MSG_PROTOCOL_REJECT = 0x03;
    public static final int MSG_RESYNC_REQUEST = 0x04;
    public static final int MSG_RESYNC_BEGIN = 0x05;
    public static final int MSG_RESYNC_END = 0x06;
    public static final int MSG_PING = 0x07;
    public static final int MSG_PONG = 0x08;

    // State-sáv (0x20-0x3F): szerver → kliens read-only projekciók.
    public static final int MSG_HUD_STATE = 0x20;
    public static final int MSG_ABILITY_KIT_STATE = 0x21;
    public static final int MSG_SPELLBOOK_STATE = 0x22;
    public static final int MSG_PROFILE_STATE = 0x23;
    // 0x24 a terv szerint a FACTION_STATE-nek fenntartva.
    public static final int MSG_RELIC_STATE = 0x25;
    // 0x26/0x27 a terv szerint a PARTY/EVENT_STATE-nek fenntartva.
    public static final int MSG_TALENT_STATE = 0x28;
    public static final int MSG_QUEST_STATE = 0x29;
    public static final int MSG_PROFESSION_STATE = 0x2A;

    // Action-sáv (0x40-0x4F): kliens → szerver intent-kérések (a szerver mindent újravalidál).
    public static final int MSG_CAST_SLOT = 0x40;
    public static final int MSG_SELECT_SPELL = 0x41;
    public static final int MSG_TOGGLE_FAVORITE = 0x42;
    public static final int MSG_PURCHASE_TALENT = 0x43;
    public static final int MSG_TRACK_QUEST = 0x44;
    public static final int MSG_SELECT_PROFESSION = 0x45;
    public static final int MSG_SELECT_PROFESSION_SPEC = 0x46;

    // Result-sáv (0x50-0x5F): szerver → kliens gépi action-válaszok (requestId-korrelációval).
    public static final int MSG_ACTION_RESULT = 0x50;

    // Gépi action-eredménykódok; a lokalizált szöveg kliensoldali felelősség.
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_REJECTED = "REJECTED";
    public static final String RESULT_NOT_READY = "NOT_READY";
    public static final String RESULT_INVALID_STATE = "INVALID_STATE";
    public static final String RESULT_NOT_ALLOWED = "NOT_ALLOWED";
    public static final String RESULT_RATE_LIMITED = "RATE_LIMITED";
    public static final String RESULT_SERVER_ERROR = "SERVER_ERROR";

    public static final String REJECT_INVALID_HELLO = "INVALID_HELLO";
    public static final String REJECT_PROTOCOL_INCOMPATIBLE = "PROTOCOL_INCOMPATIBLE";
    public static final String REJECT_CLIENT_DISABLED = "CLIENT_DISABLED";

    private ClientProtocol() {
    }
}
