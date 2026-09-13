package hu.taliann.icesmp.trash;

import java.util.Locale;

/** Closed Phase D vocabulary for the 42 authored Anomaly identities. */
public enum TrashAnomalyBehavior {
    FELREVERT_GARAS(Primitive.TOSS),
    TOROTT_IRANYTU(Primitive.CONTEXTUAL_PRESENTATION),
    HETPARTOS_KAVICS(Primitive.TOSS),
    MELYNEPI_CSAPAGYGOLYO(Primitive.PHYSICS),
    TUL_NEHEZ_ALATET(Primitive.PHYSICS),
    FELFELE_HULLO_HOPEHELY(Primitive.PHYSICS),
    BOKICNAK_ELLENTMONDO_LEVEL(Primitive.PHYSICS),
    KIKOPOTT_OBSZIDIANSZILANK(Primitive.PORTAL),
    A_PENZTAR_UTOLSO_GARASA(Primitive.CONTAINER),
    BOTERAI_NE_VIDD_CIMKE(Primitive.CONTAINER),
    CALDESTERAI_SORSZAM_999(Primitive.INVENTORY),
    URES_ERSZENY(Primitive.PHYSICS),
    HAZUG_MERLEGNYELV(Primitive.REDSTONE),
    ELSZENESEDETT_VERFAAG(Primitive.ITEM_DAMAGE),
    VERFA_SZALKAJA(Primitive.PHYSICS),
    FAGYOTT_TINTAS_CETLI(Primitive.CONTEXTUAL_PRESENTATION),
    SZARAZ_GYUFA(Primitive.CONTEXTUAL_PRESENTATION),
    RADICORAI_ARNYEKSZALKA(Primitive.PHYSICS),
    UDVARI_GOMB(Primitive.RECOGNITION),
    SZAKADT_HADIJEL(Primitive.RECOGNITION),
    TOROTT_HADISIP(Primitive.SOUND),
    SARKANYISTALLO_CSATJA(Primitive.RECOGNITION),
    NEVTELEN_DOGCEDULA(Primitive.RECOGNITION),
    URES_CSONTZACSKO(Primitive.SOUND),
    JEGMEZOI_CSENGONYELV(Primitive.SOUND),
    CSENDVERTE_CSENGONYELV(Primitive.SOUND),
    ELKESO_VISSZHANGDARAB(Primitive.SOUND),
    BALMENETES_CSAVAR(Primitive.ITEM_FRAME),
    MELYNEPI_KIEGETT_BIZTOSITEK(Primitive.MECHANISM),
    MELYNEPI_VAKLENCSE(Primitive.MECHANISM),
    GORBE_SATORSZOG(Primitive.RETURN),
    EZUSTOZOTT_KANAL(Primitive.RECOGNITION),
    CSONTSZAMVEVO_CERUZACSONKJA(Primitive.MEMORY),
    TOROTT_ZSEBORA(Primitive.MEMORY),
    BAL_ZOKNI(Primitive.PAIR),
    JOBB_ZOKNI(Primitive.PAIR),
    BAL_LANCSZEM(Primitive.PAIR),
    JOBB_LANCSZEM(Primitive.PAIR),
    SUTTOGO_CETLI(Primitive.CONTEXTUAL_PRESENTATION),
    A_KRUMPLI_AMI_NEM_AKAR_ELULTETODNI(Primitive.INTERACTION),
    HAZUDNI_NEM_TUDO_DOBOKOCKA(Primitive.TOSS),
    PORTALKOROM(Primitive.PHYSICS);

    private final Primitive primitive;

    TrashAnomalyBehavior(final Primitive primitive) {
        this.primitive = primitive;
    }

    public Primitive primitive() {
        return primitive;
    }

    public static TrashAnomalyBehavior parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("hiányzó Anomaly behavior");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("ismeretlen Anomaly behavior: " + raw, invalid);
        }
    }

    public enum Primitive {
        TOSS,
        PHYSICS,
        PORTAL,
        CONTAINER,
        INVENTORY,
        REDSTONE,
        ITEM_DAMAGE,
        CONTEXTUAL_PRESENTATION,
        RECOGNITION,
        SOUND,
        ITEM_FRAME,
        MECHANISM,
        RETURN,
        MEMORY,
        PAIR,
        INTERACTION
    }
}
