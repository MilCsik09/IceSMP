# H) PvE, világesemények és végjáték

[← Ötlettár-index](../IDEAS.md)

Jelölés minden tételnél: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) •
`[TOP]` = ajánlott következő kör.

A meglévő világesemény-flotta (vérhold, világboss-archetípusok, invázió, karaván, kíséret,
Vad Hajsza, kincs, meteor, gyűjtögető buff-ablak, bőség-idő, szerver-kihívás, szezonliga —
lásd [10. Világesemények](../player-guide/10-vilagesemenyek.md)) szilárd alap. Ez a kategória
ÚJ esemény-típusokat, boss-mélyítést, mob-ökológiát, ko-op keretrendszereket és végjáték-hurkokat
gyűjt rájuk épülve — a dungeonök (B3), napi vadász-cél (B10), bestiárium (B21), affixek (B27),
szezonzáró esemény (B33), régészet (B42), torony-védelem (B45), expedíciók (B47) és a
boss-telegraph/elit-jelölés polish (A10/A29) NEM ismétlődik itt, csak hivatkozva.

---

### H1. Vándorló világboss (nomád fenevad) `[TOP]`
🔴 • ⭐⭐⭐

**Mi ez:** Egy világboss-változat, amelyik NEM egy helyben él és pusztul el az összecsapásban,
hanem több napig kóborol a vadonban, és fizikailag nyomot hagy maga után.
**Hogyan működne:** Spawnkor a szokásos archetípus-választás fut (WorldBossManager), de a
boss `lifespan-days` (config) ideig él, naponta 1-2 alkalommal új, közeli régióba „vándorol"
(régió-lokális teleport-lánc, nem egyetlen nagy ugrás). Minden állomáshelyen jellegzető nyomot
hagy (pl. lávakohó archetípusnál megpörkölt terep, fagyos királynál jégfolt) — ez a bestiárium-
szerű nyomkövetéshez is jelzés, merre járt. Ha a szezon alatt senki nem öli meg, a lifespan
lejártával eltűnik; ha megölik, a jutalom a túlélt napok számával skálázódik (hosszabb vadászat
= nagyobb zsákmány). Perzisztencia: PDC-alapú world-state, hogy szerver-restart ne törölje.
Folia: minden mozgás-lépés `getRegionScheduler().run(...)`-nal az ÚJ célhelyen fut, a boss
entitás retired-callback-öntudatos (ha a régió kirakodik, a következő tick újratelepíti).
**Miért jó:** A világboss-vadászatból több napos, közösségi „hol jár most" hajsza lesz — a
krónikával (B15) és a Discord-híddal (C5) remekül broadcastolható, a törzsjátékosoknak
visszatérő ok a bejelentkezésre.
**Építőkövek:** WorldBossManager archetípus-készlet, boss-bar infra (HudManager), krónika (B15).
**Buktatók:** Több napos entitás-életciklus = szerver-restart és régió-unload edge case-ek;
gondos retired-callback kezelés nélkül „elveszett" boss marad a memóriában.

### H2. Terjedő korrupt zóna

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### H3. Éjszakai ostrom a claimek ellen (opt-in)
🟡 • ⭐⭐⭐

**Mi ez:** Az invázió-esemény claim-specifikus, ÖNKÉNTES változata: aki bekapcsolja, éjjelente
számíthat rá, hogy a claimjét szörny-hullám ostromolja.
**Hogyan működne:** Claim-tulajdonos egy flaggel (a raid-lootable kapcsoló A13-mintájára)
opt-in-elhet; bekapcsolt claimeknél éjjel esély van rá, hogy a claim határánál InvasionManager-
stílusú hullám spawnol (MobScaling-skálázott, a claim szintjéhez igazítva), amit a védőknek
vissza kell verniük. Sikeres védés jutalma nyersanyag/XP, bukás esetén csak kellemetlenség
(nincs blokk-rombolás — a claim-védelem szabályai érvényben maradnak). Nem opt-in claimek
sosem kapnak ostromot. Folia: a hullám a claim régióján spawnol, `getRegionScheduler`-rel;
a spawn-pontok a claim-határ mentén, kis, région-lokális szkenneléssel választva.
**Miért jó:** A PvE-orientált, magányos vagy kis csapatos claim-tulajdonosoknak ad visszatérő,
opcionális veszélyt és jutalmat — anélkül, hogy a PvP-raid-rendszert (B11/B39) érintené.
**Építőkövek:** InvasionManager hullám-logika, ClaimManager flag-rendszer, TerritoryProtectionService.
**Buktatók:** Az opt-in flag legyen könnyen ki/be kapcsolható (ne büntesse az AFK-t), és a
hullám sose spawnoljon a claim belsejébe — csak a határ külső oldalára.

### H4. Hullócsillag-mező gyűjtő-verseny
🟡 • ⭐⭐

**Mi ez:** A meteor-esemény kiterjesztése: egy meteor helyett egy egész MEZŐNYI kisebb becsapódás
egy szélesebb területen, amiért a játékosok versenyeznek.
**Hogyan működne:** A MeteorEventManager „raj-módban" több kis krátert generál rövid idő alatt
egy régión belül (kevesebb érc kráterenként, de sok kráter). A begyűjtött ritka ércek darabszáma
egy ideiglenes StatsManager-ranglistába gyűlik; az esemény zárásakor (config `field-duration`)
a top gyűjtők bónusz-jutalmat kapnak, a kráterek pedig — a meteor-esemény szabálya szerint —
maguktól visszaállnak. Sosem claimre/territóriumra hullik. Folia: minden kráter-generálás a
saját régiójában fut, a ranglista-számláló egyszerű atomikus/synchronized növelés (nem
kereszt-régiós zár).
**Miért jó:** A meglévő meteor-esemény egyszeri „ki ér oda előbb" élményét ismételhető,
versenyszerű csoport-eseménnyé bővíti — jó belépő a végjáték-ranglistákhoz (H23).
**Építőkövek:** MeteorEventManager, StatsManager ranglista-infra, boss-bar visszaszámláló.
**Buktatók:** Sok egyidejű kráter = sok blokk-módosítás rövid idő alatt egy régióban — a
kráter-szám és -méret configból szigorúan korlátozandó a régió-lokális teljesítmény miatt.

### H5. Szörny-fészek felszámolás
🟡 • ⭐⭐⭐

**Mi ez:** A vadonban ritkán megjelenő „fészek" (jelölt struktúra/blokk), ami amíg áll,
egyre táguló szörny-hullámokat bocsát ki a környékre.
**Hogyan működne:** Egy üzenet jelzi a hozzávetőleges helyet (a kincs-esemény mintájára).
A fészek időnként (config `spawn-interval`) MobScaling-skálázott mob-csoportot bocsát ki a
közvetlen közelébe; minél tovább áll, annál sűrűbben. A felszámoláshoz a fészek-blokkot kell
elpusztítani (utána a kint lévő mobok maradnak, de utánpótlás nincs). Gyors felszámolás =
kevesebb harc, de kisebb jutalom; ha hagyják nőni, több zsákmány, de veszélyesebb. Jutalom
nyersanyag + lélekkő-esély, a WorldBossManager-loot mintájára a leütőnek/csoportnak személyes.
Folia: a fészek és a köré spawnolt mobok egyetlen régióhoz kötöttek, a kibocsátás
`getRegionScheduler`-en fut, nincs kereszt-régiós szkennelés.
**Miért jó:** Világos, cselekvésre ösztönző cél („minél előbb rommá kell tenni"), rövid,
önmagában is teljesíthető PvE-tartalom kis csapatoknak is.
**Építőkövek:** InvasionManager hullám-logika, TreasureEvent koordináta-jelzés, MobScalingManager.
**Buktatók:** Ha egy fészket senki nem talál meg időben, az entitásszám elszabadulhat — kemény
felső korlát a kint lévő mobokon + a H24 takarítás-figyelő automatikus lezárása kötelező.

### H6. Esemény-kaszkád (láncreakció kihasználatlan eseményekből)
🟢 • ⭐⭐

**Mi ez:** Ha egy világesemény kihasználatlanul jár le, ne csak eltűnjön — indítson egy
követő, tematikusan illő eseményt.
**Hogyan működne:** Egyszerű hook-réteg a meglévő event-managerek lezáró (`expire`/`end`)
hívásaira: ha az elrejtett kincset (TreasureEventManager) senki nem találta meg, a helyén
egy őrző elit mob spawnol (Vad Hajsza-stílusú); ha a hullócsillag-mezőt (H4) nem bányászták
ki, korrupt mobok (H2 készletéből) jelennek meg a maradék kráterekben. Configolható
esemény→esemény párok listája (`cascade.<forrás>.target`), egy közös `EventCascadeListener`
figyeli a lezáró eseményeket és dob egy újat a megfelelő manager felé.
**Miért jó:** A „ha lekésed, egyszerűen eltűnik" élmény helyett minden kihasznált lehetőség
tesz valamit a világgal — kevesebb az elpazarolt esemény-broadcast, több a folytonosság.
**Építőkövek:** TreasureEventManager, MeteorEventManager, WildHuntManager lezáró hookjai.
**Buktatók:** Vigyázni, hogy a lánc ne fusson végtelenbe (max 1 lépés kaszkádonként) és ne
halmozzon fel egyszerre túl sok aktív eseményt egy régióban.

### H7. Csali-esemény (csapda-zsákmány)
🟢 • ⭐⭐

**Mi ez:** Egy gyanúsan könnyű zsákmány-jelzés, ami valójában ambush — a kincs-esemény
kockázatosabb testvére.
**Hogyan működne:** A TreasureEventManager mintájára megjelenik egy „láda", de a felnyitás/
kibányászás pillanatában (nem korábban — a telegraph csak utólag derül ki) egy kisebb elit
mob-csapat spawnol a láda köré, és csak ezután hullik a — a rendes kincsnél nagyobb —
zsákmány. Config `bait-chance` határozza meg, hány százalék eséllyel csali egy adott
kincs-esemény (nem külön esemény-típus, hanem a meglévő rásegítő módosítója). Felirat/
üzenet utólag (miután becsapódott) jelzi, hogy csali volt.
**Miért jó:** Kockázat/jutalom feszültséget visz a különben biztonságos „fuss oda, katt"
kincs-loopba, anélkül hogy külön infrát igényelne.
**Építőkövek:** TreasureEventManager, MobScalingManager (az ambush-csapat skálázásához).
**Buktatók:** Az ambush ne legyen kiszámíthatatlanul halálos szóló játékosnak — a mob-erősség
igazodjon a közeli játékosok kaszt-szintjéhez, ne fix nehézségi legyen.

### H8. Világboss add-fázis + interrupt-ellenőrzés `[TOP]`
🟡 • ⭐⭐⭐

**Mi ez:** Egy boss-fázis, ahol a boss egy nagy, telegrafált csapást kántál, közben kisebb
addokat idéz — a csapást vagy meg kell szakítani, vagy az addokat kell gyorsan letakarítani.
**Hogyan működne:** A meglévő SLAM/ZONE/SUMMON speciál-rendszer (WorldBossManager) kap egy új
„csatorna" típusú speciált: hosszabb (pl. 4-5 mp) telegraph alatt kis add-hullám jelenik meg,
és ha a csatornázás lezárul, nagy AoE sebzést oszt a közeli csapatra. Az interrupt egy ÚJ
spell-tag (`interrupts: true`) néhány meglévő CC-spellen (gyökerezés/kábítás-jellegű), ami
a csatornázást megszakítja, ha ráadják. Ha nincs interrupt, az addok gyors letakarítása
csökkenti a végső csapás erejét arányosan. Config: `worldboss.channel.duration`,
`interrupt-damage-reduction`. Folia: az add-spawn a boss régiójában fut, az interrupt-jelzés
(sikeres megszakítás broadcast) a boss entitás schedulerén hop-ol vissza a raid tagjaihoz.
**Miért jó:** Mélységet ad a világboss-harcnak a puszta „álljunk el a jelzett helyről"
mechanikán túl — csapat-koordinációt és spell-választást jutalmaz, nem csak DPS-t.
**Építőkövek:** WorldBossManager speciál-rendszer, meglévő CC-spellek (A1 CC-audit eredménye).
**Buktatók:** Az `interrupts` tag gondos kaszt-egyensúlyt igényel — ha csak 1-2 kaszt tudja
megszakítani, azok kötelezővé válnak a raidhez; szélesen kell elosztani.

### H9. Világboss enrage-timer
🟢 • ⭐⭐

**Mi ez:** Ha a csapat túl lassan harcol, a boss egy idő után feldühödik — állandó,
visszavonhatatlan sebzés/sebesség-bónuszt kap.
**Hogyan működne:** A boss spawnkor kap egy `enrage-timer` (config, pl. 6 perc) visszaszámlálót,
amit a meglévő boss-bar (HudManager megosztott boss-bar) is mutat. Ha a HP a timer lejártáig
nem esik egy küszöb (pl. 60%) alá, a boss enrage-buffot kap (a jelenlegi 50%-os feldühödés-
fázison FELÜL, attól függetlenül) — látványos jelzéssel (partikel-aura + hang), hogy
egyértelmű legyen, mi történt. A timer minden HP-küszöb-áttöréskor (pl. 75/50/25%) egy kicsit
meghosszabbodik, hogy ne legyen tisztán bináris „vesztettünk".
**Miért jó:** Szervezettséget és felkészülést jutalmaz (megfelelő kaszt-mix, spell-rotáció) — a
világbossokat a puszta létszám-brute-force helyett taktikai kihívássá teszi.
**Építőkövek:** WorldBossManager fázis-rendszer, HudManager megosztott boss-bar.
**Buktatók:** Túl szoros timer szólóban/kis csapatban lehetetlenné teszi a bosst — a
timer-hossz igazodjon a boss archetípus alap-nehézségéhez, ne legyen egységes minden bosson.

### H10. Heti mythic világboss-változat lockouttal
🔴 • ⭐⭐⭐

**Mi ez:** Egy heti, felturbózott világboss-változat (meglévő archetípusokból sorsolva), extra
mechanikákkal és jobb loottal — de heti lockouttal, hogy ne lehessen farmolni.
**Hogyan működne:** Hetente egyszer (config-ütemezett, C13 esemény-naptár mintájára) egy
„mythic" jelzésű világboss spawnol: a szokásos archetípus-statok szorozva (`mythic.stat-mult`),
a H8/H9/H12 mechanikák mindegyike bekapcsolva. A leütésben részt vevő játékosok PDC
időbélyeget kapnak (`mythic_lockout_<hét-szám>`) — a lockout alatt már nem kapnak lootot egy
újabb mythic-ölésből (a B3 dungeon-lockout PDC-mintája). A loot garantáltan jobb (H11 token +
relikvia-szilánk esély).
**Miért jó:** Heti visszatérési horgony a törzs-PvE-csapatoknak, kontrollált loot-befolyás
(nincs napi farmolás), és a világboss-rendszernek végre van egy „ez a heti nagy cél" csúcsa.
**Építőkövek:** WorldBossManager, B3 lockout-PDC-minta, C13 esemény-naptár.
**Buktatók:** A lockout legyen per-account, ne per-párt (különben alt-farmolás); a spawn-idő
hirdetése (Discord-híd C5) fontos, hogy a csapatok fel tudjanak rá készülni.

### H11. Boss-loot token-rendszer pity-számlálóval
🟡 • ⭐⭐

**Mi ez:** A világboss-ölések egy boss-specifikus tokent is adnak a nyers lootan felül, amit
egy külön boltban lehet elkölteni — és egy pity-számláló garantálja, hogy nem lehet örökké
peches valaki.
**Hogyan működne:** Minden világboss-archetípus-ölés ad X „boss-tokent" (a leütőnek/csoportnak
személyesen, a jelenlegi loot-elv szerint). Egy token-bolt (GUI, A/B-kategória kozmetika-GUI
mintájára) relikvia-szilánkra (B42/rituálé-alapanyag), kozmetikára (B9) vagy ritka receptre
váltható. Emellett minden boss-ölés, ami NEM adott ritka drop-ot, növel egy rejtett
pity-számlálót (PDC); N sikertelen ölés után a következő ölés garantáltan ad ritka dropot,
majd a számláló nullázódik.
**Miért jó:** A „10 ölés, semmi jó loot" frusztrációt tompítja, és önálló, hosszútávú
elköltési célt ad a token-boltban — a raritás-rendszer fölé egy kiszámíthatóbb progressziós
réteget húz.
**Építőkövek:** WorldBossManager loot-hook, meglévő GUI-minta, relikvia-rendszer.
**Buktatók:** A pity-küszöböt jól kell hangolni — túl alacsony értéktelenné teszi a ritka
dropot, túl magas nem old semmit; balansz-adat (C1-mintájú napi dump) segít utólag hangolni.

### H12. Kevert fázis-mechanika (archetípus-kombó bossok)
🟡 • ⭐⭐

**Mi ez:** Egy világboss több fázisban KÜLÖNBÖZŐ archetípusok szignatúra-speciáljait
kombinálja, hogy ismerős építőkövekből új harc-élményt adjon.
**Hogyan működne:** A H10 mythic-változatnál (vagy ritkán a sima spawnnál is) a boss 2-3
fázisra bontva más-más archetípus speciálját „kölcsönzi" — pl. 1. fázis Lávakohó Behemót
SLAM-ja, 2. fázis (50% alatt) Fagyott Trón Királya ZONE fagy-mezője. A fázisváltás egy
egyértelmű vizuális/hang-jelzéssel jár (A10 telegraph-polish mintája), és a szignatúra-aura
is vált a fázissal együtt. Tisztán configból (`worldboss.phase-pool` lista) összeállítható,
új asset nem kell — a meglévő speciál-implementációk újrahasznosítva.
**Miért jó:** Kevés munkából sok variációt ad — minden mythic-hét (H10) más „összeállítású"
bosst kaphat anélkül, hogy új kódot kellene írni minden alkalommal.
**Építőkövek:** WorldBossManager speciál-implementációk, meglévő szignatúra-aura rendszer.
**Buktatók:** A fázisváltás-jelzés legyen félreérthetetlen — két speciál összemosódása
(pl. SLAM közben induló ZONE) zavaró, nem izgalmas, ha nincs egyértelmű vágás közöttük.

### H13. Kooperatív boss-végrehajtás
🟡 • ⭐⭐

**Mi ez:** A világboss alacsony HP-n egy rövid, sebezhetetlen „végrehajtás" fázisba lép, amit
csak TÖBB játékos egyidejű közreműködésével lehet lezárni.
**Hogyan működne:** Ha a HP egy küszöb (pl. 8%) alá esik, a boss ideiglenesen sebezhetetlenné
válik, és N (config, párt/csoport-mérethez skálázva) gyenge pontot jelöl ki maga körül
részecske-jelzéssel. Minden gyenge pontot egy-egy külön játékosnak kell jobb-katt-tal
„aktiválnia" egy rövid ablakon belül (pl. 6 mp) — ha mindet aktiválták, a boss visszaesik
sebezhető állapotba és a végső csapás extra sebzést kap; ha lejár az ablak, újra próbálható,
de a boss enyhén regenerál. Folia: a gyenge pontok saját (kis) entitások/marker-lokációk a
boss régiójában, az aktiválás a boss entitás schedulerén hop-ol vissza az állapot-frissítéshez.
**Miért jó:** A finish nem csak DPS-kérdés — kényszeríti, hogy a raidben ténylegesen több
aktív játékos legyen jelen és figyeljen, nem csak a legerősebb DPS-esek viszik a harcot.
**Építőkövek:** WorldBossManager fázis-rendszer, party/csoport-létszám infó.
**Buktatók:** Szóló/kis csapatra a küszöb-N legyen skálázva (ne zárjon ki 1-2 fős csoportokat
a bossölésből teljesen).

### H14. Ritka spawn-variánsok (albínó/árnyék mobok)
🟢 • ⭐⭐

**Mi ez:** Kis eséllyel megjelenő, vizuálisan megkülönböztetett mob-variánsok, amik dupla
XP-t adnak és külön bestiárium-bejegyzést nyitnak.
**Hogyan működne:** MobScalingListener spawn-hookjában kis eséllyel (`rare-variant.chance`,
config) egy normál mob „albínó" (fehér/csillogó) vagy „árnyék" (sötét partikel-aura)
variánssá alakul — PDC-tag jelzi, a névtábla és a részecske-effekt megkülönbözteti (az A29
elit-jelölés mintájára, de saját szín-kóddal). Ölésük dupla kaszt-XP-t és megnövelt
lélekkő-esélyt ad, és a bestiárium (B21) a variánst ÖNÁLLÓ bejegyzésként számolja, nem a
bázis-mob alatt.
**Miért jó:** Minden hétköznapi farmolásba becsempész egy kis „mi volt ez a csillogás?"
izgalmat, és a gyűjtögető-hajlamú játékosoknak (B21 célközönség) extra mérföldkövet ad.
**Építőkövek:** MobScalingManager/Listener spawn-hook, B21 bestiárium, A29 vizuális jelölés.
**Buktatók:** A variáns-eséllyel vigyázni kell farm-spawnereknél — azok alapból NEM
skálázódnak (10. oldal), a rare-variant logika kövesse ugyanezt a kizárást, különben a
farmok automata dupla-XP gépezetté válnának.

### H15. Éjszakai elit-járőrök a vadonban
🟡 • ⭐⭐

**Mi ez:** Éjjelente a vadonban (spawntól távol) önállóan kóborló, megerősített mob-csapatok
jelennek meg — invázió-eseménytől függetlenül, folyamatos háttér-veszélyként.
**Hogyan működne:** Éjszaka (napszak-ellenőrzés, az ambient-esemény napszak-kötésének
mintájára) kis eséllyel egy 2-4 fős, egy vezető-mob által irányított járőr-csoport spawnol
a MobScalingManager szint-táblázata szerint (a helyi távolság-szint felturbózva
`patrol.level-bonus`-szal). A járőr bejárja a közeli területet, majd ha nem találkozik
játékossal, egy idő után eltűnik (nincs végtelen felhalmozódás). Legyőzése normál (nem
esemény-) jutalmat ad, de a vezető-mob nagyobb eséllyel dob lélekkövet.
**Miért jó:** A vadon éjjel ne csak „több zombi" legyen, hanem érezhetően veszélyesebb —
folyamatos, esemény-broadcast nélküli feszültséget ad az éjszakai felfedezőknek.
**Építőkövek:** MobScalingManager szint-táblázat, napszak-ellenőrzés (AmbientEventManager mintája).
**Buktatók:** Entity-szám kordában tartása kritikus — max egyidejű járőr régiónként/világonként,
és kötelező az időzített eltűnés, nehogy felhalmozódjanak alvó régiókban.

### H16. Bióm-specifikus veszély-szintek
🟢 • ⭐⭐

**Mi ez:** A táv-alapú mob-szintezés mellé bióm-alapú módosító, hogy egyes helyek (nether,
mély sötét, óceáni monument) érezhetően veszélyesebbek legyenek a puszta távolságtól függetlenül.
**Hogyan működne:** A MobScalingManager táv/1000-blokk szint-számítása mellé egy
`biome-danger-modifier` config-tábla (bióm → szint-bónusz és/vagy mob-készlet felülbírálás)
kerül — pl. a nether alap +2 szint, a mély sötét +3 és kizárólag ottani mob-készlet.
A módosító a meglévő szint-számítás UTÁN alkalmazódik, így a cap (max 10) és a farm-spawner
kizárás változatlan marad.
**Miért jó:** A világ ne csak „középpont-e távolság" logikával legyen veszélyes — egyes
biómok tematikusan is fenyegetőbbek lesznek, ami felfedezésre és óvatosságra ösztönöz.
**Építőkövek:** MobScalingManager szint-számítás, meglévő cap/farm-kizárás logika.
**Buktatók:** A bióm-lookup blokk-pozíciónként drága lehet sok mobnál — cache-elni kell
chunk-szinten, ne minden spawn-eseménynél kérdezzük le újra a világ bióm-adatát.

### H17. Vadon-veszélytérkép
🟢 • ⭐

**Mi ez:** Egy GUI/parancs, ami megmutatja, mennyire veszélyes éppen a közeled — a
táv-alapú szint, bióm-módosító (H16) és az ismert korrupt zónák (H2) / fészkek (H5)
együttes kiértékelését.
**Hogyan működne:** `/dangermap` (vagy a `/menu` Események almenü egy csempéje): a játékos
körüli néhány gyűrűben (koncentrikus távolság-sávok) kiírja a várható mob-szintet, és
jelöli, ha ismert korrupt zóna vagy fészek van a közelben (csak a MÁR felfedezettek —
nincs spoiler). Tisztán olvasó lekérdezés a meglévő MobScalingManager/H2/H5 getterekre,
esetleg PAPI-placeholderként is (`%icesmp_danger_here%`) kiajánlva.
**Miért jó:** Az A14 „mi történik most" esemény-oldal PvE-veszély párja — segít eldönteni,
hova induljon a játékos, ahelyett hogy vakon belesétálna egy fészekbe.
**Építőkövek:** MobScalingManager, H2/H5 zóna-regiszterek, HudManager/PAPI-bridge.
**Buktatók:** Csak a MÁR felfedezett/ismert veszélyeket mutassa — a teljes térkép-tudás
(minden korrupt zóna élőben) elvenné a felfedezés izgalmát.

### H18. Nyilvános esemény-csoportok auto-partyval `[TOP]`
🟡 • ⭐⭐⭐

**Mi ez:** Amikor egy világesemény elindul, a közelben lévő, párt nélküli játékosok egy
könnyű, ideiglenes „esemény-csoportba" kerülhetnek — koordinációhoz és bónusz-XP-hez.
**Hogyan működne:** Világboss/invázió/torony-védelem-jellegű esemény indulásakor a közeli
(nem párt-tag) játékosok egy elfogadható értesítést kapnak („Csatlakozol az esemény-
csoporthoz?"); igen esetén egy könnyűsúlyú, csak az esemény idejére élő csoport jön létre
(NEM a teljes PartyManager-kötelezettség — nincs XP-megosztás bonyodalom, csak közös
jelölők a HUD-on + kis részvételi XP-bónusz). Az esemény végével (siker/timeout) a csoport
automatikusan feloszlik.
**Miért jó:** A világeseményeknél ma esetleges, ki kivel száll be — ez a súrlódás nélküli
csapatosodás jelentősen javítja a spontán ko-op élményt, különösen új/szóló játékosoknak.
**Építőkövek:** PartyManager könnyített al-módja, meglévő esemény-managerek indító hookjai.
**Buktatók:** Ne kényszerítse rá senkire (opt-in marad), és a bónusz-XP legyen szerény, nehogy
a teljes párt-rendszert feleslegessé tegye eseményeknél.

### H19. Világ-cél: közös boss-gyengítés fázis
🟡 • ⭐⭐⭐

**Mi ez:** Egy világboss/mythic-spawn ELŐTT futó közösségi előkészítő cél, ami — ha
teljesül — gyengíti a bosst vagy javítja a lootot, amikor végül megjelenik.
**Hogyan működne:** A CommunityGoalManager mintájára egy „előkészítő" cél fut a mythic-hét
(H10) előtti napokban (pl. „gyűjts 500 lepecsételő kristályt" vagy „ölj 200 korrupt mobot" —
a H2/H5 mellékterméke is lehet forrás), boss-bar-ral mérve mindenkinek. Ha a cél időben
teljesül, a hét mythic-bossa csökkentett enrage-timerrel VAGY garantált extra token-droppal
(H11) indul; ha nem teljesül, a boss a normál, nehezebb formájában spawnol.
**Miért jó:** Összeköti a hétköznapi PvE-grindelést a heti nagy raid-payoff-fal — a napi
farmolásnak közvetlen hatása lesz a hétvégi közös célra, nem csak személyes haszna.
**Építőkövek:** CommunityGoalManager, H2/H5/H10 eseményláncok, HudManager boss-bar.
**Buktatók:** A célt reális szinten kell tartani — ha rendszeresen elbukik, demoralizáló;
ha mindig automatikusan teljesül, értelmetlen dísz csak.

### H20. Mentőakció-esemény (NPC kimentése)
🟡 • ⭐⭐

**Mi ez:** Egy fogságba esett, névvel ellátott NPC-t kell kiszabadítani egy őrzött
helyszínen, mielőtt a rá kimért idő lejár.
**Hogyan működne:** Ritkán megjelenik egy esemény-jelzés (koordinátával, a kincs-esemény
mintájára), ahol egy FancyNpcs-hidas NPC mob-őrség fogságában van. A közeli játékosoknak
le kell győzniük az őrséget és „ki kell szabadítaniuk" (interakció) az NPC-t egy időkorláton
belül (boss-bar mutatja) — a kimentett NPC ezután egy egyszeri, tematikus jutalmat ad
(nyersanyag/recept-lap), majd eltűnik/hazatér. Sikertelen mentésnél az NPC „elvész", az
esemény jutalom nélkül zárul.
**Miért jó:** Elbeszélő ízt ad a puszta „ölj mobot" eseményeknek — kis történet-morzsa,
ami a világot élőbbnek érezteti, és jól kombinálható a H18 auto-partyval.
**Építőkövek:** EscortManager időzítő/boss-bar mintája, FancyNpcs-bridge, TreasureEvent
koordináta-jelzés.
**Buktatók:** Az őrség-erősség legyen a közeli játékos-létszámhoz skálázva, különben szólóban
lehetetlen, csapatban meg trivializálódik.

### H21. SOS-jelzőrakéta
🟢 • ⭐⭐

**Mi ez:** Egy craftolható/vásárolható item, amivel a bajba jutott játékos gyors segély-
jelzést küldhet a közeli játékosoknak — külön párt nélkül is.
**Hogyan működne:** Az item használatkor egy látványos, égi jelzőfényt lő fel (part­ikel +
hang) és a közeli (config-sugarú) játékosoknak HUD-üzenetet/iránytű-jelzést küld a bajba
jutott irányába (az A21 halál-irányjelző mintája, de élő játékosra). Cooldownnal védett,
hogy ne legyen spam-eszköz. Nem old meg semmit automatikusan — pusztán figyelemfelhívás,
hogy közeli szövetségesek dönthessenek, mennek-e segíteni (fészek/elit-járőr/korrupt zóna
ellen).
**Miért jó:** Könnyűsúlyú, párt-mentes segítség-kérés — a szóló/kis csapatos PvE-seknek ad
esélyt, hogy szükség esetén bevonjanak másokat anélkül, hogy előre meg kellett volna
szervezni bármit.
**Építőkövek:** ItemFactory-minta, A21 irány-jelző infra, HUD-üzenet csatorna.
**Buktatók:** A hatótávolság és cooldown jó hangolása kulcs — túl nagy sugár mindenkit
zaklat, túl kicsi haszontalanná teszi.

### H22. Heti kihívás-rotáció pontozással `[TOP]`
🟡 • ⭐⭐⭐

**Mi ez:** A meglévő világesemények heti, változó „hívás-listája" — konkrét célok, amikért
pont jár, a végjáték-hurok gerince.
**Hogyan működne:** Hetente (C13 esemény-naptár mintájára) egy rotáló feladat-készlet aktív,
pl. „Ölj meg 3 világbosst", „Számolj fel 5 fészket (H5)", „Tisztíts meg 1 korrupt zónát (H2)",
„Élj túl egy éjszakai ostromot (H3)" — mindegyik a MEGLÉVŐ event-managerek eredményéből
számolt (nincs új gameplay, csak számlálás-hook). A teljesítés pontot ad egy heti
számlálóba; a hét zárásakor a pontok krónikába (B15) kerülnek, és a legjobbak jutalmat
kapnak (kassza-semleges: nyersanyag/kozmetika/cím).
**Miért jó:** A sok különálló világesemény egy közös, heti ívbe rendeződik — mindig van
konkrét, mérhető cél, ami visszatérésre ösztönöz, a meglévő tartalom újrafelhasználásával.
**Építőkövek:** ServerChallengeManager/CommunityGoalManager mintája, összes event-manager
eredmény-hookja, StatsManager.
**Buktatók:** A célok legyenek reálisan elérhetők szóló és csoportos játékosnak is —
csak csapat-eseményekre (világboss) építő lista kizárja a szóló PvE-seket.

### H23. PvE ranglista-szezon
🟡 • ⭐⭐

**Mi ez:** A frakciós szezonligától (SeasonManager) FÜGGETLEN, személyes PvE-pontszám-ranglista,
saját szezon-ritmussal.
**Hogyan működne:** A H22 heti pontok (plusz boss-ölés/fészek-felszámolás/bestiárium-
haladás) egy külön, egyéni PvE-szezon-számlálóba gyűlnek. A szezon zárásakor (a frakciós
szezontól eltérő, vagy azzal egybefutó ciklus — configolható) a top-lista tagjai címet
(B22) és kozmetikát kapnak, majd a számláló nullázódik. A `/leaderboard` egy új
kategóriaként mutatja (A22 bővítés mintájára).
**Miért jó:** A frakciós/PvP-orientált szezonliga mellett önálló progressziós utat ad a
PvE-re fókuszáló, esetleg frakció-semleges játékosoknak is — mindenki kap saját versenyt.
**Építőkövek:** StatsManager, SeasonManager ciklus-minta, B22 címek.
**Buktatók:** Egyértelműen kommunikálni kell, hogy ez NEM helyettesíti a frakciós szezont —
két párhuzamos ranglista könnyen összezavarodhat, ha a UI nem választja külön őket.

### H24. Elhagyott esemény automatikus takarítása
🟢 • ⭐⭐

**Mi ez:** Egy háttér-őrjárat, ami észreveszi, ha egy világesemény „elakadt" (senki a
közelben, régió kirakodott, lejárt de nem zárult le) és kényszerrel lezárja.
**Hogyan működne:** Egy periodikus (globális scheduler-ütemezésű, de a tényleges takarítás
mindig a saját régió-schedulerén hajtva végre) ellenőrzés végigmegy az aktív esemény-
managereken (H1 nomád boss, H2 korrupt zóna, H5 fészek, invázió, stb.), és ha egy esemény
X perce nem látott közeli játékost VAGY a régiója kirakodott, force-despawn-olja a hozzá
tartozó entitásokat és törli az állapotot. Log-bejegyzés (C7 audit-log mintája) minden
kényszerített lezárásról, hogy látszódjon, mennyire gyakori.
**Miért jó:** A H1/H2/H5/H10 típusú, hosszabb életű események nélküle lassan felhalmozott
„zombi" entitásokat és állapotot hagynának a világban — ez a rendszer biztosítéka, hogy a
régió-teljesítmény ne romoljon a tartalom-gazdagodással.
**Építőkövek:** Minden esemény-manager `end()`/`expire()` hookja, C9 régió-teljesítmény riport.
**Buktatók:** A küszöb-idő legyen elég nagy, hogy ne zárjon le éppen csak átmenetileg
üresen hagyott (pl. párt épp úton lévő) eseményeket — inkább konzervatív timeout, mint túl agresszív.

### H25. PvE mérföldkő-jutalmak a heti rotációhoz
🟢 • ⭐⭐

**Mi ez:** A H22 heti kihívás-rotáció egymást követő teljesítéséért járó, fokozatosan növekvő
streak-jutalom.
**Hogyan működne:** A H22 heti számláló mellé egy `streak-weeks` PDC-számláló kerül: ha a
játékos egymás után N héten teljesíti a heti kihívást (akár csak részlegesen — a küszöb
configolható), fokozatosan jobb jutalmat kap (pl. 2. hét: bónusz nyersanyag-csomag, 4. hét:
kozmetika-token, 8. hét: exkluzív cím B22-ből). Egyetlen kihagyott hét nullázza a streaket
(vagy egy „fagyasztó" item enyhítheti — B25 lottó-jegy-szerű sink-elem).
**Miért jó:** A H22 egyszeri heti célja mellett hosszútávú, kumulatív okot ad a rendszeres
visszatérésre — a streak-mechanika a legjobb retention-eszközök egyike élő szervereken.
**Építőkövek:** H22 heti számláló, B22 címek, PDC-alapú streak-tárolás.
**Buktatók:** A streak-nullázás legyen megbocsátó (pl. 1 kihagyott hét ne törölje azonnal az
egészet) — a túl büntető streak-rendszer inkább frusztrál, mint motivál.

---

Összesen **25 ötlet** (H1–H25) ebben a kategóriában.
