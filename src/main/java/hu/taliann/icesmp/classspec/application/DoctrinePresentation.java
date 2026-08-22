package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.data.SpecializationType;

import java.util.Locale;

/** Deterministic player-facing doctrine copy; gameplay authority remains in the class services. */
public record DoctrinePresentation(String title, String effect, String tierRole) {

    public static DoctrinePresentation of(final SpecializationType specialization,
                                          final int level, final String doctrineId) {
        final String id = normalize(doctrineId);
        final ClassMechanicView mechanic = specialization == null ? null
                : ClassMechanicView.forSpecialization(specialization.getId()).orElse(null);
        final String mechanicName = mechanic == null ? "specializációs mechanika"
                : mechanic.specializationMechanic();
        return new DoctrinePresentation(displayId(id), behavior(id, mechanicName), switch (level) {
            case 30 -> "Alapirány: a mechanika felépítésének ritmusát választod meg.";
            case 40 -> "Középső ág: a költés, célterület vagy védelem módját választod meg.";
            case 50 -> "Beteljesedés: a végső próba utáni capstone-játékmenetet formálja.";
            default -> "Ismeretlen doctrine-szint.";
        });
    }

    private static String behavior(final String id, final String mechanic) {
        if (contains(id, "gyors", "korai", "konnyu", "olcso", "szikra_ora", "vanguard")) {
            return "A(z) " + mechanic + " gyorsabban épül fel vagy hamarabb válik újra elkölthetővé.";
        }
        if (contains(id, "hosszu", "tarto", "szivos", "halk", "nyugodt", "defiant")) {
            return "Meghosszabbítja a(z) " + mechanic + " aktív ablakát, így kevésbé bünteti a megszakadó ritmust.";
        }
        if (contains(id, "orok", "teher_biras", "higgadt", "tiszta", "for_one")) {
            return "A(z) " + mechanic + " elköltése vagy megszakadása után a felépített erő egy részét megtartja.";
        }
        if (contains(id, "mely", "suru", "dus", "vastag", "gazdag", "nagyobb", "titan",
                "executioner", "kallan", "halalmester", "arkan_ura", "ura", "mestere")) {
            return "Növeli a(z) " + mechanic + " tárolható vagy egyszerre kiadható csúcshatását.";
        }
        if (contains(id, "szeles", "ket_", "kettos", "falka", "legio", "udvar", "jarvany",
                "tulcsordulas", "for_all", "war_signal")) {
            return "A(z) " + mechanic + " hatását több célra, társra vagy másodlagos találatra terjeszti.";
        }
        if (contains(id, "pajzs", "pancel", "bor", "kereg", "fal", "bastya", "vedo", "orzo",
                "rendithetetlen", "gyoker", "tuske", "aegis")) {
            return "A(z) " + mechanic + " védelmi ágát erősíti: több elnyelést, ellenállást vagy biztonságosabb költést ad.";
        }
        if (contains(id, "elet", "gyogy", "aldas", "kegyelem", "megvalto", "tavasz", "virag",
                "gondozo", "veno", "kegyeltje")) {
            return "A(z) " + mechanic + " gyógyító vagy szövetségest támogató kimenetét erősíti.";
        }
        if (contains(id, "vihar", "tuz", "langlehelet", "harag", "zapor", "ver", "halal", "pusztito",
                "robbano", "fekete", "sindragosa", "itelet", "haboru", "vadaszat")) {
            return "A(z) " + mechanic + " támadó költését erősíti, nagyobb burstöt vagy biztosabb finisher-ablakot ad.";
        }
        if (contains(id, "lepes", "lendulet", "tanca", "ritmus", "szoves", "rahangolodas", "egyensuly")) {
            return "Rugalmasabbá teszi a(z) " + mechanic + " felépítési sorrendjét és mozgás közbeni fenntartását.";
        }
        return "A(z) " + mechanic + " elsődleges producer→consumer ciklusát módosítja; az élő balanszértékeket a class service alkalmazza.";
    }

    private static boolean contains(final String id, final String... needles) {
        for (final String needle : needles) if (id.contains(needle)) return true;
        return false;
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String displayId(final String id) {
        if (id.isBlank()) return "Ismeretlen doctrine";
        final StringBuilder result = new StringBuilder();
        for (final String word : id.split("_")) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
