package hu.taliann.icesmp.spells;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Complete fallback copy for bespoke spells whose effect is not built by ConfiguredSpell. */
public final class SpellDescriptionCatalog {
    private static final Map<String, List<String>> DESCRIPTIONS = Map.ofEntries(
            Map.entry("life_drain", List.of("Életerőt szív el a célponttól, és ugyanebből gyógyítja a kasztert.")),
            Map.entry("deep_breath", List.of("Irányítható, rövid csatornázás: a lángkúp többször sebez és felgyújt.")),
            Map.entry("eagle_eye", List.of("Kitágítja a látómezőt és kiemeli a távoli ellenfeleket.")),
            Map.entry("whirlwind", List.of("Forgó közelharci csapás, amely a körülötted álló ellenfeleket találja el.")),
            Map.entry("blink", List.of("A nézésirányba teleportál, és az első szilárd akadály előtt megáll.")),
            Map.entry("phase_blink", List.of("Árnyékos rövid teleport a nézésirányba, biztonságosan megállva az akadály előtt.")),
            Map.entry("mind_blast", List.of("A kijelölt célpont elméjét támadja közvetlen árnyéksebzéssel.")),
            Map.entry("spectral_sight", List.of("Átmenetileg felfedi a közeli élőlényeket és a rejtett fenyegetéseket.")),
            Map.entry("bone_chill", List.of("A környező ellenfeleket lelassítja és meggyengíti.")),
            Map.entry("living_flame", List.of("A célponttól függően sebez vagy gyógyít; cél nélkül önmagadra hat.")),
            Map.entry("throw_glaive", List.of("Glaive-et dob a célpontra; a találat a démonvadász mechanikáját is táplálja.")),
            Map.entry("primal_bond", List.of("Elkölti a Vadmester Kötelékét, és a társ közös célpontját támadja.")),
            Map.entry("rain_dance", List.of("Esőt idéz, és a környéket a sámán víz- és gyógyító hatásaihoz hangolja.")),
            Map.entry("gust", List.of("Széllökéssel hátralöki a nézésirányban lévő célpontot.")),
            Map.entry("wisplight", List.of("Ideiglenes lidércfényt helyez el, amely megvilágítja a területet.")),
            Map.entry("confusion", List.of("A játékosokat elvakítja; a mobokat lassítja, gyengíti és rendszeresen törli az aggrójukat.")),
            Map.entry("bee_swarm", List.of("Dühös méhrajt küld a kijelölt célpontra; célpont nélkül nem fogyaszt költséget.")),
            Map.entry("armament", List.of("Ideiglenesen felerősíti a kézben tartott fegyver harci jelenlétét.")),
            Map.entry("smoke_bomb", List.of("Füstzónát hoz létre, amely megszakítja a tiszta célzást és fedezéket ad.")),
            Map.entry("shadowburn", List.of("Árnyéklánggal sújtja a célpontot; kivégzési helyzetben erősebb.")),
            Map.entry("shadowstep", List.of("A kijelölt célpont mögé teleportál; célpont nélkül nem sül el.")),
            Map.entry("friendship", List.of("Megbarátkoztat egy alkalmas állatot, vagy a legközelebbi használható célpontot választja.")),
            Map.entry("venom_strike", List.of("Mérgezett közelharci találatot visz be a kijelölt ellenfélre.")),
            Map.entry("multishot", List.of("Egyszerre több nyilat lő ki legyező alakban.")),
            Map.entry("lucky_star", List.of("Rövid, véletlenszerűen kedvező csillagáldást ad a kaszternek.")),
            Map.entry("wolf_call", List.of("Harci farkast hív segítségül a jelenlegi ellenfél ellen.")),
            Map.entry("sun_dance", List.of("Napsütést idéz, és a környéket a természet- és tűzhatásokhoz hangolja.")),
            Map.entry("chains_of_ice", List.of("Jéglánccal erősen lelassítja a kijelölt célpontot.")),
            Map.entry("frost_fever", List.of("Fagybetegséget helyez a célpontra, amely idővel fejti ki a hatását.")),
            Map.entry("featherfoot", List.of("Átmenetileg könnyűvé teszi a lépteket és javítja a mozgást.")),
            Map.entry("shear", List.of("Lélekcserével a démonvadász erőforrását és célpontját módosítja.")),
            Map.entry("wild_mushroom", List.of("Eldobott gombát helyez el; késleltetve felrobban és gyengíti a környék ellenfeleit.")),
            Map.entry("inner_focus", List.of("Belső fókuszt készít elő, amely a következő papi képességet erősíti.")),
            Map.entry("root", List.of("Gyökerekkel helyhez köti a kijelölt ellenfelet.")),
            Map.entry("flying_serpent_kick", List.of("Előrerúgva gyorsan átszeli a teret, és találatkor ellöki az ellenfelet.")),
            Map.entry("angry_chicken", List.of("Mérges csirkét hajít, amely rövid késleltetés után területi sebzést okoz.")),
            Map.entry("spinning_crane_kick", List.of("Forgó rúgássorozat, amely a közelben álló ellenfeleket sebzi.")),
            Map.entry("demonic_circle", List.of("Démoni kört rögzít, majd egy későbbi használat visszateleportál rá.")),
            Map.entry("antidote", List.of("Eltávolítja a mérgező és gyengítő állapotokat, majd rövid regenerációt ad.")),
            Map.entry("hide", List.of("Véges lopakodást indít; támadás vagy észlelés megszakíthatja.")),
            Map.entry("rune_strike", List.of("Rúnacsapással sebez, és a Halállovag aktuális rúnakörével lép kölcsönhatásba.")),
            Map.entry("bulwark", List.of("Rövid védelmi állás: ellenállást és elnyelő szíveket ad.")),
            Map.entry("feast", List.of("Lakomaasztalt készít a közelben álló szövetségesek támogatására.")),
            Map.entry("double_jump", List.of("A levegőben egy második elrugaszkodást enged.")),
            Map.entry("expel_harm", List.of("Öngyógyítás, amely a közeli ellenfélre is visszaüti a kiűzött ártalmat.")),
            Map.entry("holy_wrath", List.of("Szent energiával sújtja a közeli ellenségeket.")),
            Map.entry("piercing_bolt", List.of("Gyors, átütő lövedéket lő ki a nézésirányba.")),
            Map.entry("arrow_storm", List.of("Széles nyílzáport lő ki legyező alakban.")),
            Map.entry("dagger_throw", List.of("Gyors tőrt hajít a nézésirányba.")),
            Map.entry("fireball", List.of("Lángoló lövedéket indít a nézésirányba.")),
            Map.entry("gale_burst", List.of("Széllövedéket indít a nézésirányba.")),
            Map.entry("bone_spear", List.of("Átütő csontdárdát lő ki a nézésirányba.")),
            Map.entry("double_tap", List.of("Két gyors lövedéket lő ki kis szórással.")),
            Map.entry("spectral_volley", List.of("Szellemlövedékek széles sortüzét indítja el.")),
            Map.entry("raise_horde", List.of("Tartós feltámasztási rítust indít; az elkészült őr a Társműhely rosterébe kerül.")),
            Map.entry("bone_archers", List.of("Tartós csontíjász-rítust indít; az eredmény a Holtak Udvarában kezelhető.")),
            Map.entry("imp_swarm", List.of("Tartós imp-paktum rítusát indítja el a démoni névsorhoz.")),
            Map.entry("magma_servant", List.of("Tartós Magma-szolga paktumát indítja el a démoni névsorhoz.")),
            Map.entry("legion", List.of("Tartós infernal paktumát indítja el; a rítus eredménye a Társműhelyben jelenik meg.")),
            Map.entry("raise_ghoul", List.of("Ideiglenes ghúlokat idéz a jelenlegi célpont ellen.")),
            Map.entry("army_of_the_dead", List.of("Ideiglenes holtsereget idéz a jelenlegi célpont ellen.")),
            Map.entry("panda_guard", List.of("Két ideiglenes pandaőrt hív a jelenlegi ellenfél ellen.")),
            Map.entry("wild_pack", List.of("Ideiglenes farkasfalkát hív a jelenlegi ellenfél ellen."))
    );

    private SpellDescriptionCatalog() { }

    public static List<String> describe(final String spellId) {
        final String id = spellId == null ? "" : spellId.trim().toLowerCase(Locale.ROOT);
        final List<String> description = DESCRIPTIONS.get(id);
        if (description != null) return description;
        return List.of("Aktiválja a(z) " + displayId(id)
                + " egyedi kaszthatását; a hatás erősségét az élő balanszkonfig adja.");
    }

    private static String displayId(final String id) {
        if (id.isBlank()) return "ismeretlen képesség";
        return id.replace('_', ' ');
    }
}
