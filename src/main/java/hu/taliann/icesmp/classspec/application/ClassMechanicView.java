package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Complete, immutable 13-class/35-specialization explanation catalogue for first-party UI. */
public record ClassMechanicView(String classId, String classMechanic, String classCycle,
                                String specializationId, String specializationMechanic,
                                String specializationCycle, String interactionHint,
                                boolean companionSpecialization) {

    private record Core(String name, String cycle) { }
    private record Spec(String name, String cycle, String hint) {
        private Spec(final String name, final String cycle) { this(name, cycle, ""); }
    }

    private static final Map<String, Core> CORES = Map.ofEntries(
            Map.entry("wizard", new Core("Rúnaszövés", "Két egymást követő mágiaiskola reakciót készít elő.")),
            Map.entry("warrior", new Core("Düh és Csatatempó", "A Düh fizeti a képességet; a változatos tettek tempófokozatot építenek.")),
            Map.entry("archer", new Core("Szélolvasás", "A teljesen kihúzott, jól ütemezett távlövés készíti elő a következő lövést.")),
            Map.entry("assassin", new Core("Lehetőség", "Pozíció, kitérés, interrupt vagy rejtőzés egyszer elkölthető finisher-ablakot nyit.")),
            Map.entry("druid", new Core("Harmónia és évszak", "A természetmágia Harmóniát épít; az alakváltás évszak-áldásként engedi ki.")),
            Map.entry("paladin", new Core("Eskü és Meggyőződés", "A választott Eskühöz illő tettek a következő szerepazonos castot erősítik.")),
            Map.entry("death_knight", new Core("Rúnakör", "Vér és Fagy rúna újratölt; Halál-rúna csak átalakítással teremthető.")),
            Map.entry("shaman", new Core("Totemkerék", "Egy fő és egy kísérő totem élhet; azonos kategória lecseréli a régit.")),
            Map.entry("monk", new Core("Áramlás", "A változatos technikák építik; ugyanazon mozdulat ismétlése nem.")),
            Map.entry("priest", new Core("Litánia", "A választott ima tettei verseket gyűjtenek, majd egyszeri áldást mondanak ki.")),
            Map.entry("warlock", new Core("Paktum és Lélekadósság", "A paktum adósságot épít; a plafon új paktumot blokkol, amíg vissza nem fizeted.")),
            Map.entry("demon_hunter", new Core("Kárhozat-terhelés", "A terhelés erőt ad, túlterhelve bejövő sebzést növel, majd levezethető.")),
            Map.entry("evoker", new Core("Felerősítés", "Az első cast tölt, a következő I–III. rangon elengedi; találat megszakíthatja."))
    );

    private static final Map<String, Spec> SPECS = Map.ofEntries(
            Map.entry("elementalist", new Spec("Elemi ráhangolódás", "Tűz/Fagy/Vihar hangolás → Konvergencia vagy Elemi Korona → elköltés.")),
            Map.entry("necromancer", new Spec("Holtak Udvara", "Korlátozott tartós udvaroncok és Soulforge-erő → idézés vagy aratás.", "A tartós udvaroncokat a Társműhely mutatja.")),
            Map.entry("berserker", new Spec("Vérőrület", "Vérőrület építése → kontrollált túlpörgés → Kimerülés és levezetés.")),
            Map.entry("guardian", new Spec("Őrség és Eskütárs", "Őrség építése → pajzs vagy intercept az egy kijelölt Eskütárson.", "Lopakodás + jobb katt a Lélekkapoccsal egy csapattárson vagy védelmi célon.")),
            Map.entry("sharpshooter", new Spec("Pontossági lánc", "Egy prédán pontos találatok → gyengepont → távolsági finisher.")),
            Map.entry("beast_master", new Spec("Kötelék", "A társaddal közös célpont → Kötelék → társas finisher.", "A tartós Istállót és a társ parancsait a Társműhely kezeli.")),
            Map.entry("poisoner", new Spec("Toxinkeverés", "Három toxin dózisainak felépítése → célzott katalizálás.")),
            Map.entry("phantom", new Spec("Árnyéknyom", "Véges lopakodás és Észleltség → egy pozicionált visszhang.")),
            Map.entry("plaguebringer", new Spec("Járványtörzs", "Saját találattal ültetett, korlátozott törzs → terjesztés és kitörés.")),
            Map.entry("feral", new Spec("Szagnyom", "Egy prédán Szagnyom és kombópont → alakhoz kötött finisher.")),
            Map.entry("lunar", new Spec("Eclipse", "Nap ↔ Hold mérleg építése → Eclipse-ablak.")),
            Map.entry("ironbark", new Spec("Kéregréteg", "Kéregrétegek és Gyökérháló → tankolás és területvédelem.")),
            Map.entry("restoration", new Spec("Virágzás", "Mag elültetése → érés → időzített Virágzás.")),
            Map.entry("holy", new Spec("Fényjelző", "Gyógyítás egy kijelölt társra is visszhangzik.", "Lopakodás + jobb katt a Haranggal egy csapattárson.")),
            Map.entry("retribution", new Spec("Ítélet", "Három Ítélet-jel → Verdict finisher.")),
            Map.entry("protection", new Spec("Pajzstöltet", "Találatokból Pajzstöltet → Megszentelt Föld vagy védelmi költés.")),
            Map.entry("blood", new Spec("Vér Emlékezete", "Nyolc találatnyi friss sebzés emléke → gyógyítás vagy pajzs.")),
            Map.entry("frost", new Spec("Fagyjel", "Fagyjelek építése → részleges csapás vagy teljes Zúzás.")),
            Map.entry("unholy", new Spec("Dögvész és ghúl", "Dögvész-burst → a tartós ghúl mutációja és formaváltása.", "A ghúl szintjét, mutációját és idézését a Társműhely mutatja.")),
            Map.entry("elemental", new Spec("Elemi Túltöltés", "Élő totempár + elemi egyezés → Túltöltés.")),
            Map.entry("enhancement", new Spec("Maelstrom", "Vihar ↔ Föld ritmus → Maelstrom → költés.")),
            Map.entry("tidal", new Spec("Dagály és Apály", "A két gyógyítási állapot egymást készíti elő.")),
            Map.entry("windwalker", new Spec("Harcművészeti lánc", "Meghatározott, változatos mozdulatsor → finisher.")),
            Map.entry("brewmaster", new Spec("Stagger", "Sebzés elhalasztása a Staggerbe → tisztító főzet.")),
            Map.entry("mistweaver", new Spec("Ködszál", "Legfeljebb három szövetséges kapcsolata → gyógyító tovagyűrűzés.", "Lopakodás + jobb katt az Élet Ágával egy csapattárson.")),
            Map.entry("discipline", new Spec("Engesztelés", "Engesztelés → sebzésből gyógyítás és pajzsháló.")),
            Map.entry("bone_priest", new Spec("Velő és Osszárium", "Nem halálos áldozat → Velő/Osszárium → gyógyítás.")),
            Map.entry("shadow", new Spec("Őrület", "Őrületküszöb → erősebb, életbe kerülő cast → tudatos levezetés.")),
            Map.entry("affliction", new Spec("Átokgrimoár", "Három átokoldal és átköthető Lélekfonal → elszívás.")),
            Map.entry("destruction", new Spec("Parázsbank", "Parázs felhalmozása → teljes bankos burst; maximumon Túlhevülés.")),
            Map.entry("demonologist", new Spec("Démoni névsor", "Legfeljebb három tartós démon → paktumerő és feloldás.", "A tartós démonokat a Társműhely kezeli.")),
            Map.entry("havoc", new Spec("Momentum", "Lélektöredékek → mozgással begyűjtött Momentum → burst.")),
            Map.entry("vengeance", new Spec("Fájdalom és Sigil", "Bejövő sebzésből Fájdalom → legfeljebb két Sigil vagy hasító költés.")),
            Map.entry("devastation", new Spec("Izzás", "Vörös ↔ kék Eszencia-váltás → Izzás → egy burst.")),
            Map.entry("preservation", new Spec("Visszhang és Időlenyomat", "Egyszeri Visszhang és csak életet visszaállító Időlenyomat.", "Lopakodás + jobb katt a Sárkányvér-fiolával egy csapattárson."))
    );

    static {
        if (!CORES.keySet().equals(ClassSpecCatalog.classIds())) {
            throw new IllegalStateException("Class mechanic UI catalogue does not cover all classes");
        }
        if (!SPECS.keySet().equals(ClassSpecCatalog.specializationIds())) {
            throw new IllegalStateException("Class mechanic UI catalogue does not cover all specializations");
        }
    }

    public ClassMechanicView {
        Objects.requireNonNull(classId); Objects.requireNonNull(classMechanic);
        Objects.requireNonNull(classCycle); Objects.requireNonNull(specializationId);
        Objects.requireNonNull(specializationMechanic); Objects.requireNonNull(specializationCycle);
        interactionHint = interactionHint == null ? "" : interactionHint;
    }

    public static Optional<ClassMechanicView> forSpecialization(final String specializationId) {
        final String specId = ClassSpecCatalog.normalize(specializationId);
        final String classId = ClassSpecCatalog.parentOf(specId);
        final Core core = CORES.get(classId);
        final Spec spec = SPECS.get(specId);
        if (core == null || spec == null) return Optional.empty();
        return Optional.of(new ClassMechanicView(classId, core.name(), core.cycle(), specId,
                spec.name(), spec.cycle(), spec.hint(),
                ClassSpecCatalog.companionNamespace(specId) != null));
    }

    public static Set<String> coveredClassIds() { return CORES.keySet(); }
    public static Set<String> coveredSpecializationIds() { return SPECS.keySet(); }
}
