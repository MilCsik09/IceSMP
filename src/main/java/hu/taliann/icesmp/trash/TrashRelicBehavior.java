package hu.taliann.icesmp.trash;

import java.util.Locale;

/** Closed Phase E vocabulary for the 23 authored consuming identities. */
public enum TrashRelicBehavior {
    LYUKAS_VODOR,
    BOT,
    A_VILAG_LEGELESEBB_KESE,
    A_LEGBIZTONSAGOSABB_SISAK,
    A_KARD_AMELY_MINDEN_CSATAT_MEGNYER,
    REGI_KOTES,
    TOROTT_PAJZSDESZKA,
    PALACKOZOTT_NEM,
    VISSZA_A_FELADONAK,
    REPEDT_BOGRE,
    SZERENCSES_GARAS,
    TEGLA,
    FEL_PAR_PAPUCS,
    KOPORSOSZOG,
    KOEK,
    FEKETE_VIASZDUGO,
    SZAKADT_FEHER_ZASZLO,
    KORMOS_SATORSZOG,
    MELYNEPI_SELEJTEK,
    A_NAGYON_ROSSZ_OTLET,
    SZAKADT_KOTEL,
    ELSZAKADT_VIRRASZTOKANOC,
    REPEDT_VIRRASZTOUVEG;

    public static TrashRelicBehavior parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("hiányzó consuming behavior");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("ismeretlen consuming behavior: " + raw, invalid);
        }
    }
}
