# F) Gazdaság és kereskedelem

[← Ötlettár-index](../IDEAS.md)

Jelölés: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) • `[TOP]` = ajánlott
következő kör.

Az IceSMP gazdasága a [03-valuta-gazdasag.md](../player-guide/03-valuta-gazdasag.md)-ben
leírt elvre épül: **négy frakcióvaluta + a Semleges token**, dinamikus árfolyam (kereslet/
kínálat mozgatja), bank csak fővárosban, piac+aukció escrow-val, és a legfontosabb szabály —
**pénz sosem keletkezik a semmiből**. Minden jutalom kasszából, befizetésből vagy meglévő
ELÉG-ből (money sink) jön; ami itt „kifizetés", az mindig egy másik helyről elvont összeg.
Ez a kategória az árfolyam-mechanikát, az adózást, a player-to-player szerződéseket és a
piac/aukció mélyítését dolgozza ki — a már meglévő gazdasági ötletekre (A6, B13, B23, B24,
B25, B40, C3, C4, D2) csak hivatkozik, nem ismétli őket.

---

### F1. Árfolyam-történet grafikon a valutaváltóban
🟢 • ⭐⭐

**Mi ez:** A `/menu` Valutaváltó GUI-ja mellé egy vizuális előzmény-nézet, ami az elmúlt napok
árfolyam-mozgását mutatja valutánként.
**Hogyan működne:** Az `ExchangeRateService` ma csak a pillanatnyi rátát tárolja; egy napi
snapshot-log (kis `YamlStore`-fájl: valuta+dátum+ráta, rotálódó 30 napos ablak) épül mellé. A
GUI-ban a snapshotokból egyszerű oszlop-magasságú üveg-panel „diagram" vagy lore-beli ASCII-
sparkline (▁▂▃▅▇) jelzi a trendet; `/currency rates history <valuta>` a szöveges verzió. Nincs
pénzmozgás, tisztán infó-réteg a meglévő rátaszámítás fölött.
**Miért jó:** Az A6 láthatóság-irányát folytatja vizuálisan; a spekulánsoknak (F2) ez adja a
döntési alapot, a piacot figyelő játékosoknak pedig azonnal látszik, érdemes-e most váltani.
**Építőkövek:** `ExchangeRateService`, `YamlStore` snapshot, `/menu` GUI-minta.
**Buktatók:** A snapshot-lista ne nőjön korlátlanul — kötelező a rotálódó ablak, egyébként
nincs pénz-kockázat.

### F2. Frakció-valuta árfolyam-fogadás (spekulációs jegy)
🟡 • ⭐⭐⭐

**Mi ez:** Játékosok tétet tehetnek arra, hogy egy valuta ára X időn belül emelkedik vagy
csökken egy küszöbnél — a „shortolás" játékos-barát, zéró-összegű változata.
**Hogyan működne:** `/currency bet <valuta> up|down <tét> <óra>` — a tét a bankegyenlegből
azonnal egy közös **fogadási poolba** zárolódik (`MarketManager` escrow-mintája). Lejáratkor
az `ExchangeRateService` aktuális rátáját a fogadáskorihoz hasonlítja; nyerés esetén a tét
1,8×-osa jár vissza, de **kizárólag a pool-ból**, amit a VESZTES tétek töltenek fel — a pool
kezdő tőkéje nulla, kifizetéskor pool-korlátig arányosan csökken, sosem megy negatívba.
**Miért jó:** Izgalmas mini-játék a dinamikus árfolyam köré, közösségi beszélgetést generál
(„emelkedik a Vörös?"), és garantáltan zéró nettó pénzteremtés (zero-sum).
**Építőkövek:** `ExchangeRateService`, `CurrencyManager` atomikus deduct, `MarketManager`
escrow-minta, `YamlStore`.
**Buktatók:** Manipuláció-veszély, ha egy nagy szereplő maga tudja mozgatni az árfolyamot
tömeges eladással — a tét mérete legyen limitált, a payout szigorúan pool-korlátos.

### F3. Territóriumi vám
🟡 • ⭐⭐

**Mi ez:** Idegen frakció területén (`FACTION` zóna) végzett piaci adásvétel után a normálnál
magasabb díj kerül levonásra — vám a birtokos frakció javára.
**Hogyan működne:** A `TerritoryManager` zóna-lookupja megmondja, kinek a földjén áll a
játékos; a `MarketManager` tranzakció-hookjába egy ellenőrzés: ha a vevő/eladó nem a
territórium-birtokos frakció tagja, a díj (pl. alap 10% helyett 15%, `territory.customs.rate`)
különbözete a birtokos **frakció KASSZÁJÁBA** (`FactionTreasuryManager.deposit`) megy, nem
ELÉG-be. A Semleges frakció adómentes passzívája miatt vámmentes marad mind fizetőként, mind
birtokosként.
**Miért jó:** A territórium-birtoklásnak közvetlen gazdasági haszna lesz, nem csak presztízs —
erősíti a hódítási motivációt, és a B12 politikai iránnyal (király állíthatja a vám mértékét)
összekapcsolható.
**Építőkövek:** `TerritoryManager` zóna-lookup, `MarketManager` tranzakció-hook,
`FactionTreasuryManager`.
**Buktatók:** Túl magas vám elnéptelenítheti az idegen piacokon kereskedést; a NEUTRAL-kivétel
logikailag kezelendő a hookban, nehogy duplán vonjon.

### F4. Progresszív piaci díj (nagy tranzakciók extra sinkje)
🟢 • ⭐⭐

**Mi ez:** A jelenlegi fix ~10%-os eladási díj helyett sávos, a tranzakció méretével arányosan
növekvő díjkulcs a piacon és aukción.
**Hogyan működne:** `market.fee.brackets` config (pl. 0–500: 10%, 500–2000: 12%, 2000 fölött:
15%) a `MarketManager` díjszámítási pontjába épül; az extra elszívott rész ugyanúgy ELÉG-be
megy, mint a mai 10% — tehát a nagy tételek arányosan több pénzt vonnak ki a forgalomból, a
kisjátékosokat nem érinti.
**Miért jó:** A gazdag játékosok nagy, egyszeri tranzakciói jelentik a legnagyobb inflációs
kockázatot — a progresszív díj pont ott csippent, ahol a legjobban fáj, finomabb eszköz mint
egy egységes kulcs-emelés minden eladóra.
**Építőkövek:** `MarketManager` díjszámítás, `ConfigManager`.
**Buktatók:** Túl agresszív sáv a nagytételes kereskedést piac-kikerülésbe (kézi csere) tereli
— a küszöböket a C3 faucet/sink monitor adataiból érdemes hangolni, ne érzésre.

### F5. Escrow-s adásvételi szerződés
🟡 • ⭐⭐

**Mi ez:** Két játékos közti, piacon kívüli, egyedi tárgy (relikvia, egyedi enchant-kombináció)
adásvétele csalás-védve, anélkül hogy a feleknek meg kellene bízniuk egymásban.
**Hogyan működne:** `/contract offer <játékos> <ár> [valuta]` — az ajánlattevő tárgya és a
vevő fizetése egyaránt **escrow-ba** kerül (`MarketManager` escrow-mintája, a szerződés-
rekord `YamlStore`-ban); mindkét fél `/contract accept`-tel erősíti meg, ekkor egyidejűleg
cserélnek gazdát. Elfogadás előtt bármelyik fél visszaléphet — ekkor minden visszajár. Kis fix
díj (pl. 2% a vételáron) ELÉG-be megy visszaélés-fékként.
**Miért jó:** A piac csak stackelhető, egyszerű árú tételeket kezel jól — az egyedi tárgyak
(B26 rúna-item, relikvia) cseréjéhez kell egy biztonságos csatorna, ami helyettesíti a
chat-alapú „bízz bennem" ügyleteket.
**Építőkövek:** `MarketManager` escrow, `CurrencyManager` atomikus deduct, `YamlStore`.
**Buktatók:** Az item-oldali escrow bonyolultabb, mint a tiszta pénz-escrow (inventory-hely
kell); az offline fél kezelése (mint a `/market claim`) kötelező elem.

### F6. Részletfizetéses szerződés
🟡 • ⭐⭐

**Mi ez:** Az F5 szerződés-keret bővítése: a vevő nem egy összegben, hanem ütemezett
részletekben fizet, a tárgyat csak az utolsó részlet után kapja meg.
**Hogyan működne:** `/contract installment <játékos> <összeg> <részletek> <napi/heti ütem>` —
az eladó tárgya escrow-ban marad, a vevő bankegyenlegéből minden esedékességkor (global region
scheduler napi tick) automatikusan levonódik a részlet, és az eladóhoz kerül. Ha egy részlet
fedezet hiányában elmarad, a szerződés bukik: az addig befizetett összeg egy kis (pl. 20%)
kötbér-hányada ELÉG-be megy, a többi visszajár a vevőnek, a tárgy visszakerül az eladóhoz.
**Miért jó:** Drága felszerelés elérhetőbbé válik a kevésbé tőkeerős játékosoknak, miközben az
eladó védve van a nemfizetéstől — reális gazdasági dinamika, közösségi bizalom-építés.
**Építőkövek:** F5 szerződés-infra, `CurrencyManager`, globális napi tick-scheduler.
**Buktatók:** Bukott szerződések nyilvántartása külön `YamlStore`-rekordot igényel; a kötbér
mértékét jól kell hangolni, nehogy kifizetődő legyen a szándékos nemfizetés.

### F7. Szállítási szerződés (futár-megbízás)
🟡 • ⭐⭐

**Mi ez:** Egy játékos megbízást ad, hogy egy adott árut A helyről B helyre szállítsanak — a
futár a rablás kockázatát vállalja a díjért.
**Hogyan működne:** `/contract deliver <cél-territórium> <díj> [valuta]` — a megrendelő a
díjat escrow-ba teszi, a tárgyat egy zárolt „csomag" itemként (PDC-jelölt, nem craftolható
alapanyagként) kapja a vállaló futár. Célba éréskor (territórium-belépés detektálás) a csomag
automatikusan kicsomagolódik a megrendelőnél, a díj a futárnak jár. Ha a futár PvP-ben elesik,
a csomag normál lootolható tárgyként hullik ki (valódi kockázat!), a díj visszajár a
megrendelőnek.
**Miért jó:** A B6 karaván-esemény kis, egyéni léptékű testvére — állandó, játékos-generált
kockázat/jutalom tartalom, a B36 útkövekkel a nagy világ bejárhatóságát erősíti, pénz-
semleges (a díj csak gazdát cserél).
**Építőkövek:** `MarketManager` escrow, `TerritoryManager` zóna-belépés, PDC csomag-jelölő.
**Buktatók:** Futár és megrendelő összejátszhat egy „önmagának szállító" trükkre — minimum
távolság/idő-korlát kell a díj kifizetéséhez.

### F8. Raid-biztosítás
🟡 • ⭐⭐⭐

**Mi ez:** A frakciótagok kis rendszeres díjért biztosítást köthetnek, ami raid-vereség esetén
részleges kártérítést fizet az elvesztett vagyonért.
**Hogyan működne:** `/bank insure` — havi díj (pl. token-egyenleg 1%-a, a bank-ügyintézés
meglévő fővárosi korlátjával) a **frakció kasszájának** (`FactionTreasuryManager`) egy
elkülönített biztosítási alap-mezőjébe megy. Ha a frakció claimjét sikeres raid éri (meglévő
raid-eredmény hook), a biztosított tagok fix, alacsony térítést kapnak a kasszából (pl. becsült
veszteség 20%-a, kasszakorlátig — ha nincs elég az alapban, a kifizetés arányosan csökken).
**Miért jó:** A raid-vereség frusztrációját tompítja a támadó jutalmának csökkentése nélkül — a
király-politika (B12) új eszköze, és a kassza (B7 versenytársaként) új célt kap.
**Építőkövek:** `FactionTreasuryManager`, raid-eredmény event hook, `commands/bank`-minta.
**Buktatók:** Az alap kimeríthető sorozatos raidelésnél — a kasszakorlátos kifizetés véd az
„addolt pénz" ellen, de emiatt békeidőben felhalmozott tartalék nélkül megbízhatatlan.

### F9. Napszámos piac (fizetett megbízások, órabér-quest)
🟡 • ⭐⭐

**Mi ez:** Player-to-player fizetett munka-hirdetőtábla — nem a király/kassza (mint a B31
zsoldos-tábla), hanem egyik játékos fizet a másiknak konkrét feladatért.
**Hogyan működne:** `/job post <leírás> <díj> [valuta] [max fő]` — a megrendelő a díjat
escrow-ba teszi egy GUI-táblán (`ExchangeBoardManager` GUI-mintája, tranzakciós logikával
bővítve); a vállaló `/job accept`-tel jelentkezik, a megrendelő `/job complete`-tel igazolja a
teljesítést (kézi visszaigazolás), ekkor a díj a vállalóhoz kerül. Vitás esetben a díj zárolva
marad, amíg admin nem dönt.
**Miért jó:** A B31 (király-finanszírozott megbízás) mellett egy alulról jövő munkaerőpiac —
közösségi együttműködést generál, és a friss játékosoknak korai bevételi forrást ad
tapasztaltabb társaktól.
**Építőkövek:** `ExchangeBoardManager` GUI-minta, `MarketManager` escrow, `CurrencyManager`.
**Buktatók:** A kézi teljesítés-igazolás visszaélésre ad módot — időzített auto-felszabadítás
(pl. 48 óra után a vállaló javára, vita-jelzés hiányában) szükséges védőháló.

### F10. Rúna-anyag árupiac (dedikált piaci szegmens)
🟢 • ⭐⭐

**Mi ez:** A B26 rúna-kovácsolás ritka, erősen ingadozó kínálatú alapanyagainak külön fül/
szűrő a `/market` böngészőben.
**Hogyan működne:** `MarketManager` kategória-mező (item-típus alapján auto-tagelt
„runematerial" csoport) + GUI-szűrőgomb a böngészőben. Nincs új pénz-logika: a normál piaci
tranzakció (10% ELÉG-díj) érvényes, csak láthatóságot és keresést ad a ritka anyagokra, amik a
tömeges itemek közt elvesznének.
**Miért jó:** A B26 bevezetése esetén a rúna-anyagok kereslete gyorsan a piac egyik motorjává
válhat — kategorizálás nélkül a lapozgatás (mint A24-nél a receptkeresésnél) frusztráló lenne.
**Építőkövek:** `MarketManager`, B26 (előfeltétel), GUI-szűrő minta.
**Buktatók:** Csak B26 után van értelme — önmagában nem áll meg, sorrendben érdemes utána
vinni.

### F11. Ereklye-szilánk börze
🟡 • ⭐⭐

**Mi ez:** A relikvia-rendszer szilánk-alapanyagainak (B42 régészet, B47 expedíciók forrásai)
külön, aukció-jellegű piaci csatornája, mivel ezek egyedi/ritka tételek, nem tömeges stack-áru.
**Hogyan működne:** A meglévő `/market auction` gépezet külön „ereklye" kategóriával
(configolt minimum-kikiáltási ár a spam ellen), a böngésző erre is szűrhető; a licitezés/
buyout logika változatlan (bank-escrow, ELÉG-díj). Az egyetlen új elem a kategória-cimke és a
GUI-szűrő.
**Miért jó:** A relikvia-gazdaság (B20 reforge, B42 régészet) termékeinek végre lesz
kereskedelmi csatornája — end-game gyűjtők közti kereskedelem, ami ma csak kézi
chat-egyeztetéssel megy.
**Építőkövek:** `MarketManager` aukció-infra (változatlan logika), GUI-kategorizálás.
**Buktatók:** Rosszul hangolt minimum-ár elzárhatja a kispénzű vevőket — inkább figyelmeztetés
legyen, ne kemény tiltás.

### F12. Kozmetika-árverés (luxus money sink)
🟢 • ⭐⭐

**Mi ez:** Időszakos, admin-indított aukció exkluzív kozmetikai tételekre (B9 kozmetikák
szezon-limitált változatai), ahol a befolyt összeg TELJES egészében ELÉG.
**Hogyan működne:** `/icesmp auction cosmetic <item> <kikiáltási ár> <óra>` admin-parancs a
meglévő aukció-motort indítja el egy admin-birtokolt „eladóként" — a normál eladói jutalék
logikáját kikapcsolva, mert nincs kinek kifizetni, az egész befolyt összeg eltűnik. A nyertes
a kozmetikát PDC-kötött, tovább nem adható tételként kapja.
**Miért jó:** A B9 kozmetika-rendszer luxus-végét célzottan a leggazdagabb réteg pénzéből
szívja el — hatékony defláció-eszköz, mert önkéntes és presztízs-vezérelt, nem kényszer.
**Építőkövek:** `MarketManager` aukció-infra, B9 kozmetika-rendszer (előfeltétel),
admin-parancs.
**Buktatók:** Túl gyakori aukció fölöslegessé teheti a rendes piacot a gazdagok szemében —
ritka, eseményszerű (pl. szezon-fordulóhoz kötött) időzítés ajánlott.

### F13. Piaci pánik esemény

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### F14. Konjunktúra esemény

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### F15. Szezonzáró árfolyam-sokk

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### F16. Kereskedő-céh hűségszint (F a B29-hez)
🔴 • ⭐⭐

**Mi ez:** A B29 NPC-reputáció gazdasági kiterjesztése: a kereskedelmi NPC-k nemcsak quest/
karaván teljesítésből, hanem a piaci FORGALOM alapján is hűségszintet adnak.
**Hogyan működne:** A B29 reputáció-számlálóhoz új forrás: a `MarketManager` minden sikeres
eladás/vétel után kis reputáció-pontot ír a kereskedő-NPC-hez kötött frakciónál. Magasabb
szinten a frakció-bolt (meglévő NPC-bolt) fix árai kedvezményt kapnak (pl. 5/10/15%) — a
kedvezmény a bolt fix bevételéből (ami ELÉG-be megy) egyszerűen kevesebbet szív el, NEM új
pénzforrás.
**Miért jó:** A piaci aktivitásnak közvetlen, tartós játékos-előnye lesz a puszta pénzen túl —
presztízs+haszon kombó a kereskedő-hajlamú játékosoknak.
**Építőkövek:** B29 NPC-reputáció (előfeltétel), `MarketManager` tranzakció-hook,
frakció-bolt NPC.
**Buktatók:** Csak B29 után értelmes; önálló megvalósítás duplikálná a B29 munkáját, ezért
sorrendben utána érdemes.

### F17. Aukció licit-sniper védelem
🟢 • ⭐⭐

**Mi ez:** Ha valaki az utolsó pillanatokban licitál, az aukció lejárata automatikusan
meghosszabbodik, hogy a többi licitálónak esélye legyen válaszolni.
**Hogyan működne:** A meglévő aukció-lejárat logikába egy ellenőrzés: ha egy új licit a
hátralévő időből kevesebb, mint pl. 2 percet hagy, a lejárat +2 perccel tolódik
(`market.auction.anti-snipe-window`/`-extension` config). Nincs pénzmozgás, tisztán az
aukció-időzítés módosul.
**Miért jó:** Sok piac-jellegű rendszer bevett gyakorlata — véd az utolsó-másodperces
licit-taktikák ellen anélkül, hogy a meglévő bal/jobb-katt licit-UX-hoz nyúlni kellene; kis
munka, azonnal érezhető fair-play javulás.
**Építőkövek:** `MarketManager` aukció-lejárat logika, `ConfigManager`.
**Buktatók:** Elméletileg végtelenített aukciót lehetne generálni folyamatos utolsó-pillanatos
licitekkel — kell egy kemény felső plafon (pl. eredeti lejárat +50%).

### F18. Aukció rezervár-ár
🟢 • ⭐

**Mi ez:** Az eladó megadhat egy rejtett minimumárat: ha a lejáratkor a legmagasabb licit nem
éri el, az aukció licit nélkülinek minősül, és a tárgy visszajár.
**Hogyan működne:** `/market auction <kikiáltási ár> [...] reserve:<ár>` opcionális paraméter
(a `buyout:`-mintát követve); a licitezés a kikiáltási ártól indul normálisan, de lejáratkor,
ha a nyertes licit a rezervár alatt marad, a `MarketManager` a meglévő „licit nélküli" ághoz
nyúl vissza (tárgy + minden zárolt licit visszajár).
**Miért jó:** Az eladók ma vagy alacsony kikiáltási árat kockáztatnak, vagy magasat (kevés
licitáló) — a rezervár biztonságos középutat ad, több eladót csábít aukcióra a fix-áras piac
helyett.
**Építőkövek:** `MarketManager` aukció-infra (buyout-minta mellé).
**Buktatók:** A rezervár-ár NE látsszon a vevőknek a GUI-ban, csak „a rezervár nem teljesült"
üzenet jelenjen meg lejáratkor.

### F19. Zálogház (item-fedezetű gyorshitel)
🟡 • ⭐⭐

**Mi ez:** A fővárosi zálogház-NPC-nél egy értékes tárgy zálogba adható azonnali készpénzért;
ha a játékos nem váltja ki időben, a tárgy a frakció-kasszáé lesz.
**Hogyan működne:** `/pawn <tárgy>` — a raritás-rendszer érték-becslése alapján számolt kölcsön
(pl. az item-érték 50%-a) a bankegyenlegre AZONNAL jóváírva a **frakció kasszájából**, a tárgy
a „zálog-tárba" kerül (PDC-jelölt). Kiváltáskor X nap múlva a kölcsön + kamat (pl. 15%, a
kamat vissza a kasszába) ELÉG-be megy. Ha nem váltja ki, a tárgy a kasszáé marad.
**Miért jó:** Gyors likviditást ad az „item-gazdag, pénz-szegény" játékosnak, miközben a
kassza jól jár — az első olyan mechanika, ahol a kassza aktívan HITELEZ, nem csak gyűjt.
**Építőkövek:** `FactionTreasuryManager`, raritás-rendszer érték-becslés, `CurrencyManager`,
PDC zálog-jelölő.
**Buktatók:** A kassza fedezet nélkül nem hitelezhet többet, mint amennyi benne van —
kasszakorlátos kölcsönfelső-határ kötelező, különben a frakció csődbe hitelezné magát.

### F20. Frakció-kötvény (befektetési jegy a kasszának)
🟡 • ⭐⭐

**Mi ez:** A B24 személyes bank-lekötéssel szemben ez FRAKCIÓ-szintű: a tagok pénzt
kölcsönöznek a kasszának egy konkrét projektre (pl. B7 fejlesztés, B3 dungeon-építés), cserébe
kamatos visszafizetést kapnak.
**Hogyan működne:** A király `/faction bond issue <cél-összeg> <kamat%> <lejárat>`-tal
kötvényt nyit; tagok `/faction bond buy <összeg>`-gel jegyeznek (bankegyenlegükből a
**kasszába** megy azonnal, felhasználható a célra). Lejáratkor a tőke+kamat a **kasszából**
fizetődik vissza a jegyzőknek — ha a kassza időközben nem termelt elég bevételt (adó, F3 vám,
boltok), a kifizetés arányosan csökken (nincs deficit-fedezet).
**Miért jó:** A B7 kasszasink-célokat közösségi finanszírozássá alakítja — a tagok aktívan
befektetnek a saját frakciójuk fejlődésébe, ami erősebb politikai dimenziót ad, mint a B24.
**Építőkövek:** `FactionTreasuryManager`, B12 politika-irány, B24 lekötés-minta (kamat-elv
frakciós szinten).
**Buktatók:** Ha a király rosszul gazdálkodik és nem tud visszafizetni, valós
bizalom-vesztés — kell egy „kötvény nem fizethető ki" állapot, ne omoljon össze csendben.

### F21. Napi váltási limit (anti-arbitrázs fék)
🟢 • ⭐

**Mi ez:** A `/currency exchange` napi felhasználható kerettel bír (pl. összesen 5000
egységnyi érték/nap/fő), hogy egyetlen szereplő ne tudja tömeges váltással durván elmozdítani
az árfolyamot.
**Hogyan működne:** `CurrencyManager`/`ExchangeRateService` egy napi, memóriában/PDC-ben
tárolt, éjfélkor nullázódó számlálót vezet a váltott összegekről; a limit felett a
`/currency exchange` elutasít, üzenetben jelezve a visszaállás idejét. Nincs pénzmozgás-
változás, csak egy plafon a meglévő váltási mechanikára.
**Miért jó:** A dinamikus árfolyam-rendszer sebezhető egy nagy tőkéjű játékos tömeges,
egyszeri váltásával szemben — a plafon megvédi a kisjátékosokat a mesterséges volatilitástól.
**Építőkövek:** `CurrencyManager`/`ExchangeRateService`, napi számláló.
**Buktatók:** Túl szoros limit bosszantja az aktív kereskedőket — a C3 faucet/sink adatokból
érdemes reálisan hangolni, ne érzésre.

### F22. Kereskedelmi szövetség / vámmentesség
🟡 • ⭐⭐

**Mi ez:** A B44 diplomácia-rendszer gazdasági kiterjesztése: szövetséges frakciók tagjai
vámmentesen (F3 nélkül) kereskedhetnek egymás területén, kedvezményes piaci díjjal.
**Hogyan működne:** A B44 szövetség-állapotra (`isAlly`) az F3 vám-hook rákérdez: ha a vevő és
a territórium-birtokos frakció szövetséges, a vám nulla, a piaci díj a normál helyett
kedvezményes (pl. 8% a 10% helyett — a különbség NEM új pénz, egyszerűen kevesebb ég el). Az
adatot a B44 tárolja, itt csak felhasználás történik.
**Miért jó:** A diplomáciai döntéseknek (B44) kézzelfogható gazdasági tétje lesz — a szövetség
nemcsak katonai védelem, hanem valódi kereskedelmi előny.
**Építőkövek:** B44 diplomácia (előfeltétel), F3 vám-hook, `MarketManager` díjszámítás.
**Buktatók:** Csak B44 után értelmes; addig legfeljebb egy egyszerű `YamlStore`-flag
placeholder-ként vezethető be előre.

### F23. Gazdasági index-tábla a fővárosban
🟢 • ⭐⭐

**Mi ez:** A meglévő árfolyamtáblák (hologramok) mellé egy összesített „gazdaság-egészség"
kijelző mindenkinek — nem admin-log (az C3 dolga), hanem játékos-facing mutató.
**Hogyan működne:** `TextDisplay` hologram a fővárosban, ami néhány percenként frissül a C3
faucet/sink heti adataiból számolt egyszerű trend-jelzővel (pl. „Piaci forgalom: ↑ élénkülő" /
„↓ visszaeső" szöveges trend, NEM konkrét admin-szám). Csak olvasás, a C3 adatait aggregálja
játékos-barát formává.
**Miért jó:** A C3 admin-only adatait a közösség elé tárja admin-parancs nélkül — a
spekulánsoknak (F2) és a kereskedőknek pillantás-alapú piaci hangulatjelzőt ad, erősíti az
„élő gazdaság" érzetet.
**Építőkövek:** C3 faucet/sink monitor (adatforrás, előfeltétel), `TextDisplay`
hologram-infra (árfolyamtábla-minta).
**Buktatók:** Csak C3 után van értelme; a mutató legyen durva trend, ne konkrét szám, nehogy
exploit-célra ossza meg a szerver teljes gazdasági adatát.

### F24. Fuvarozói piac (player szállító szolgáltatás)
🟡 • ⭐

**Mi ez:** Az F7 szállítási szerződés kiterjesztése reputáció-réteggel: aki sokat futárkodik,
„fuvarozói" profilt épít (értékelés, teljesített szállítások száma), amit a megrendelők
választáskor látnak.
**Hogyan működne:** Az F7 szerződés-teljesítéskor a megrendelő 1-5 csillagos értékelést adhat
(`/contract rate`); a `StatsManager` egy új számlálót vezet (teljesített szállítás, átlag-
értékelés), ami az F7 GUI-listában a futár neve mellett látszik. Önálló pénzmozgás nincs,
tisztán reputáció-réteg az F7 fölött.
**Miért jó:** A megbízható futárok kitűnnek, a megrendelők bizalommal választanak — kis
munkából közösségi minőség-szűrőt ad az F7 fölé.
**Építőkövek:** F7 szállítási szerződés (előfeltétel), `StatsManager`.
**Buktatók:** Csak F7 után van értelme; hamis pozitív értékelés (haverok pumpálják egymást)
ellen nincs védelem — inkább jelzés, mint kemény szűrő.

### F25. Kereskedelmi szezon-verseny (forgalom-ranglista)
🟢 • ⭐⭐

**Mi ez:** Szezon-végén (a B33 párja) a legtöbb piaci forgalmat lebonyolító játékosok/
frakciók külön ranglistán, jutalommal.
**Hogyan működne:** A `MarketManager` minden lezárt eladás/aukció összegét egy `StatsManager`-
számlálóhoz adja (érték-alapú, nem darabszám, hogy a spam-eladás ne érje meg); szezonzáráskor
a TOP N kereskedő egy külön „verseny-kasszából" (amit a piaci díjak X%-a táplál a szezon
alatt — tehát MÁR elszívott, sinkelt pénz oszlik újra, nem új) kap pénz-semleges jutalmat:
cím (B22), kozmetika (B9), esetleg kis token-jutalom a versenyalapból.
**Miért jó:** A kereskedő-hajlamú játékosoknak (akik ma csak halkan gazdagodnak) versenycélt
és elismerést ad, a B33 szezon-narratívát gazdasági dimenzióval bővíti.
**Építőkövek:** `MarketManager`, `StatsManager` ranglista-infra, B33 szezonzáró (időzítés),
B22 címek.
**Buktatók:** Az érték-alapú számlálás önkereskedéssel (haverok közt körbe-adott tétel)
manipulálható — minimum egyedi vevő-szám vagy díj-arányos súlyozás szükséges védelemnek.

### F26. Hadiadó (király vész-adó eszköze)
🟢 • ⭐⭐

**Mi ez:** A B12 politikai adó-sáv mellé egy külön, időkorlátos eszköz: a király háború/raid
idejére átmenetileg a szokásos sáv fölé emelheti az állampolgári adót, hogy gyorsan
finanszírozza a védekezést (F8 biztosítás, B39 védmű, B11 ostromgép).
**Hogyan működne:** `/faction warTax <emelt kulcs> <óra>` (csak király, cooldown-nal a
visszaélés ellen) — az óránkénti 2%-os állampolgári adó (meglévő mechanika) a megadott időre a
B12 normál sávjánál magasabb kulcsra vált, majd automatikusan visszaáll. A befolyt extra a
**frakció kasszájába** megy, ahonnan az F8/B39/B11 sink-célok fizethetők — a tagok pénze
mozog, nem keletkezik új.
**Miért jó:** A B12-nél élesebb, eseményhez kötött politikai eszköz — a király döntése
azonnali, dramatikus közösségi hatással jár, erősíti a frakció-politika tétjét raid idején.
**Építőkövek:** B12 politika-irány (állampolgári adó mechanika), `FactionTreasuryManager`,
raid/hadiállapot detektálás.
**Buktatók:** A király visszaélhet vele (folyamatosan magas adót tarthat) — szigorú
időkorlát + cooldown kell, és a NEUTRAL adómentesség érintetlen kell maradjon.

---

[← Ötlettár-index](../IDEAS.md)
