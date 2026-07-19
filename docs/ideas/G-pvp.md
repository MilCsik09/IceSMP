# G) PvP, frakció-háború és rivalizálás

[← Ötlettár-index](../IDEAS.md)

Jelölés: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) • `[TOP]` = ajánlott
következő kör.

Az IceSMP alap-PvP-filozófiája **beleegyezéses vagy raid-szentesített**: a `SinManager`
bűn-rendszere mindent büntet, ami nem az egyik kivétel — raidben jelentkezett harcosok közti
ölés (`RaidManager.isSanctionedKill`), duel (B2), aréna (B17/B41) vagy körözött kivégzése
(lásd [02-frakciok.md](../player-guide/02-frakciok.md)). Ez a kategória erre a vázra épít:
mélyíti a raidet, ad kis léptékű, opt-in PvP-tartalmat, és a frakció-rivalizálást teszi
kézzelfoghatóvá — anélkül, hogy a nem-PvP-s, békés játékost bármikor kényszerítené. Duel
(B2), ostromgépek (B11), kaszt-próba aréna (B17), háború-ablakok (B30), védmű-építés (B39),
aréna-liga (B41), diplomácia (B44), zsoldos-tábla (B31), erőforrás-pontok (B5) és
torony-védelem (B45) az IDEAS.md B-kategóriájában él — itt csak hivatkozunk rájuk.

---

### G1. Raid-célpont: zászlólopás mód `[TOP]`
🟡 • ⭐⭐⭐

**Mi ez:** A raid egy variánsa, ahol a pont-tartás helyett a védő fővárosában megjelenő
**frakció-zászlót** kell a támadóknak kilopni és a saját területükre vinni.
**Hogyan működne:** `/faction raid <célfrakció> flag` — a `RaidManager` a szokásos felkészülés
után egy fizikai zászló-itemet (PDC-jelölt, nem droppolható) spawnol a védő fővárosban; a
hordozó neve/pozíciója a bossbar mellett HUD-on látszik mindenkinek (kockázat: üldözhető).
Hazaérve a zászlóval a támadó oldal azonnal +N pontot kap és a raid véget ér. A hordozó
halálakor a zászló visszaesik az eredeti helyére. Bűn-rendszer: a zászló felvétele/vitele nem
lopás (raid-szentesített, mint a konténer-zsákmányolás ma is). Folia: a zászló-entitás
követése a hordozó régió-szálán fut, a HUD-frissítés a meglévő bossbar-broadcast mintáját
követi.
**Miért jó:** Mozgó célpont = dinamikusabb raid, mint a statikus zóna-állás; a gyorsabb,
mozgékonyabb kasztoknak (íjász, orgyilkos) taktikai szerepet ad a tank mellett. Nem-PvP-sek
számára semleges — nem kötelező jelentkezni.
**Építőkövek:** RaidManager (`ActiveRaid`, `isSanctionedKill`), TerritoryManager (főváros-zóna),
HUD bossbar.
**Buktatók:** a zászló-hordozó villámgyors kivégzése (fókusztűz) unfair lehet gyenge
csapatnak — érdemes rövid sebezhetetlenség/lassítás a felvétel utáni 1-2 mp-ben.

### G2. Raid-célpont: kassza-feltörés fázisokkal
🟡 • ⭐⭐⭐

**Mi ez:** A raid másik variánsa: a védő kasszáját nem azonnal, hanem egy 3-fázisú
„feltörési" objektíván keresztül lehet megcsapolni.
**Hogyan működne:** `/faction raid <célfrakció> vault` — a felkészülés után a fővárosi
kassza-pont körül 3 egymást követő mini-objektíva nyílik meg (pl. „tartsd a zónát 30 mp-ig" →
„öngyújts egy jelzőtüzet" → „védd a robbantó-egységet N másodpercig") a `RaidManager`
pont-tartás logikájának bővítéseként; mindegyik fázis sikere a következő zárat nyitja, a
végén a kassza egy konfigurált %-a (`raid.vault-phase.percent-per-phase`) átvándorol. A
fázis-állás a bossbaron (szakasz-jelző) látszik. Bűn-rendszer: a jelen módhoz hasonlóan
raid-szentesített.
**Miért jó:** Több drámai csúcspont egy raidben, nem csak egy hosszú pont-farmolás; a védőnek
esélyt ad résenként visszavágni, nem csak összesített pontszámmal veszíteni.
**Építőkövek:** RaidManager pont-tartás, frakciókassza (`/faction treasury`), bossbar HUD.
**Buktatók:** fázis-logika bugmentessége kritikus (ne lehessen fázist átugrani/duplázni);
hosszabb raid = hosszabb elköteleződés, a háború-ablak (B30) hosszát ehhez kell hangolni.

### G3. Ostromlétra/kötél mechanika
🟢 • ⭐⭐

**Mi ez:** Raid alatt telepíthető létra/kötél-item, amivel a támadók gyorsan bejutnak a
védő claim-falán, ha az természetes akadály (szikla, magas fal).
**Hogyan működne:** Craftolható „ostromkötél" (`items/SiegeWeaponFactory`-mintára új recept,
kötél+horog), csak aktív raidben, csak a támadó oldalnak használható (a `RaidManager.
isSanctionedKill`/`isParticipant` ellenőrzésével). Jobb katt egy falra: ideiglenes,
X másodperc után eltűnő létra-blokk-sor (`scaffolding` vagy léptető-elixír terület, nem
world-edit léptékű blokk-rakás). Bűn-rendszer: nem érinti (nem block-break, nem lopás).
**Miért jó:** Vertikális/terep-adta védelem ellensúlyozása anélkül, hogy a claim-védelem
blokk-törését engedné — a hegyi/vízi fővárosok is támadhatók maradnak.
**Építőkövek:** SiegeWeaponFactory item-minta, RaidManager részvétel-ellenőrzés, claim-védelem
(nem rombol).
**Buktatók:** ha a létra túl erős, minden claim-tervezés értelmetlenné válik — rövid
élettartam és korlátozott darabszám/raid szükséges.

### G4. 3v3 skirmish-pont a territórium-határon
🟡 • ⭐⭐⭐

**Mi ez:** Kis léptékű, beleegyezéses csapat-PvP: két szomszédos frakció-territórium
határán kijelölt kis aréna, ahová 3v3 csapatok jelentkezhetnek egy gyors meccsre.
**Hogyan működne:** `/skirmish queue` — a `TerritoryManager`-ben admin-kijelölt
határ-zónákhoz (új típus vagy meglévő PLOT-mintára) kötött várólista; 3 fő összegyűlik mindkét
oldalon → automatikus teleport, rövid (pl. 5 perces) meccs első-3-kill vagy zászló-elvig. Az
`isAlly`/isHostile (A2 `SpellTargetingUtil`) itt kikapcsolva a zónán belül — mindenki
ellenfél a csapaton kívül. Bűn-rendszer: kizárólag beleegyezéses (a queue = beleegyezés), a
zónán kívüli ölésre a normál szabály él. Eredmény PDC-számlálóba (A15 stats-infra).
**Miért jó:** A raid (nagy, ritka esemény) és a duel (1v1) közötti rést tölti ki — gyors,
ismételhető csapat-PvP anélkül, hogy egész frakciót mozgósítana.
**Építőkövek:** TerritoryManager zóna, SpellTargetingUtil (A2), StatsManager (A15).
**Buktatók:** kis várólista-létszámnál (alacsony népesség) hosszú várakozás — méret-arányos
config (2v2 fallback) hasznos lehet.

### G5. Határvillongás-esemény (border skirmish)
🟡 • ⭐⭐

**Mi ez:** Időszakos, admin/scheduler-indított világesemény: két szomszédos frakció
territórium-határán egy semleges „vitatott zóna" nyílik meg pár órára, ahol a PvP
automatikusan szentesített.
**Hogyan működne:** A meglévő világesemény-managerek mintájára (pl. `WorldEventManager`
scheduler) egy `BorderSkirmishEvent`: kiválaszt egy határ-szakaszt két territórium között
(`TerritoryManager` szomszédossági ellenőrzéssel), broadcast + bossbar-visszaszámlálás, a
zónában PvP mindig szentesített (nincs bűn), a zónában elesettek nem bűnt, hanem
esemény-pontot generálnak a saját frakciójuknak. Az esemény végén a több pontot szerző
frakció kis kasszabónuszt kap.
**Miért jó:** Alacsony belépési küszöbű, spontán PvP-alkalom raid-hirdetés (király-döntés)
nélkül — a frakció bármely tagja részt vehet, ha akar; aki nem akar, egyszerűen elkerüli a
zónát.
**Építőkövek:** világesemény-infra (D-kategória mintái), TerritoryManager szomszédosság,
frakciókassza.
**Buktatók:** ha túl gyakori, a „nem-PvP" ígéret sérül — ritka, jól belőtt gyakoriság (heti 1-2)
és világos zóna-határ (pl. partikel-fal) kell.

### G6. Becsület-párbaj a bűn tisztítására

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### G7. PvP-jelzős zóna extra loottal
🟡 • ⭐⭐

**Mi ez:** Admin-kijelölt vadon-zónák, ahol a PvP mindig szabad (nincs bűn), cserébe a
mob-loot és a ritka spawn-esély megemelt.
**Hogyan működne:** `TerritoryManager`-ben új zóna-flag (`pvp-always-sanctioned: true`,
`territory.protection.rules` mintájára), belépéskor action-bar figyelmeztetés
(„⚔ PvP-zóna — az itteni ölés nem számít bűnnek”). A zónában a `MobScaling`/loot-tábla egy
konfigurált szorzót kap (`territory.pvp-zone.loot-multiplier`). Bűn-rendszer: explicit
kivétel — a zóna maga a beleegyezés.
**Miért jó:** A kockázatkedvelő játékosoknak önkéntes, jól megjelölt „high-risk high-reward”
terep; mindenki más egyszerűen elkerülheti — nem érinti a PvE-only játékost.
**Építőkövek:** TerritoryManager zóna-típus/flag, loot-tábla szorzó, HUD/action-bar jelzés.
**Buktatók:** a zóna gyorsan "farm-spot campelővé" válhat erősebb csoportoknak — méret és
elhelyezés (távol a spawntól) számít.

### G8. Hadizsákmány-token rendszer
🟡 • ⭐⭐

**Mi ez:** Legyőzött ellenfél (raid, szentesített PvP) egy nem-item „zsákmány-tokent" dob,
ami egy külön, PvP-specifikus boltnál váltható be — a vesztes tényleges felszerelése
érintetlen marad.
**Hogyan működne:** Szentesített kill esetén (`RaidManager.isSanctionedKill` vagy duel/aréna
győzelem) a győztes PDC-be/inventoryba kap X „hadizsákmány-token" itemet (drop nem az
ellenfél saját tárgyaiból). A frakció fővárosban egy „hadizsákmány-kereskedő" NPC-nél
(FancyNpcs-híd) tokenért kozmetika/B26-rúna-alapanyag/címjelölt (B22) vásárolható.
**Miért jó:** PvP-jutalom anélkül, hogy a vesztes felszerelését veszélyeztetné (nem
full-loot) — a versengést jutalmazza toxicitás nélkül, és a pénz-semleges elvet is tartja
(sink-célú token-bolt).
**Építőkövek:** RaidManager sanctioned-kill check, B22 címek, B26 rúna-anyagok, NPC-bolt.
**Buktatók:** ha a token túl könnyen farmolható alt-öléssel, inflálódik — csak valódi
(raid/duel/aréna) szentesített kill adjon tokent, sima gyilkosság soha.

### G9. Kill-streak jelzés szerénység-fékkel
🟢 • ⭐⭐

**Mi ez:** Sorozatos szentesített ölés esetén a győztesnek nyilvános, de önszabályozó
kill-streak-jelzés (broadcast + HUD-cím), a streak-bónusz csökkenő hozammal.
**Hogyan működne:** `StatsManager`-ben egy session-szintű (nem perzisztens) streak-számláló,
csak szentesített ölésekre nő (raid/duel/aréna); 3-nál chat-broadcast, 5-nél HUD-cím
(„⚔ Vérszomjas — 5 ölés”), a streak-hez járó apró bónusz (pl. +5% liga-pont a G aréna
módoknál) minden újabb ölésnél csökkenő szorzóval (`pvp.streak.diminish-factor`), hogy ne
legyen snowball. Halálkor/logout-kor nullázódik.
**Miért jó:** Elismerés a jó formában lévő PvP-seknek anélkül, hogy a gyengébb oldal
esélytelenné válna — a csökkenő hozam direkt a snowball-effektus ellen dolgozik.
**Építőkövek:** StatsManager, RaidManager/B2 duel sanctioned-kill jelzés, HUD cím-API.
**Buktatók:** a nyilvános broadcast célponttá teheti a streakelőt (ami jó, de frusztráló is
lehet) — kikapcsolható legyen configból, ha a közösség toxikusnak érzi.

### G10. Heti rivális-frakció kijelölés
🟡 • ⭐⭐⭐

**Mi ez:** A `SeasonManager` heti ciklusában automatikusan kijelölt „rivális" frakció-pár
(pl. a liga-táblázat két szomszédos helyezettje), akik közti raid/skirmish extra
liga-pontot ér.
**Hogyan működne:** Heti tick (a szezon-scheduler mintájára) kiszámolja a liga-állás alapján
a legközelebbi rangú frakció-párt, broadcast + `/faction rival` infó-parancs. A rivális ellen
nyert raid/G4 skirmish/G5 határvillongás liga-pontja szorzót kap
(`season.rivalry.point-multiplier`, pl. ×1,5). A kijelölés csak jelzés — nem kötelezi
senkit PvP-re.
**Miért jó:** Narratívát ad a heti liga-versenynek (nem csak számok), és extra ösztönzést a
kiegyensúlyozott, közeli versenyre — a sereghajtó frakciók nem lesznek rivális-célponttá
tolva.
**Építőkövek:** SeasonManager liga-tábla, RaidManager, faction-broadcast.
**Buktatók:** ha a rivális mindig ugyanaz a domináns frakció-pár, unalmassá válik — a
szomszédos-rangú számítás ezt eleve tompítja, de kis frakció-számnál (4) korlátozott a hatás.

### G11. Frakció-morál: sorozatvereség védő-buff
🟡 • ⭐⭐

**Mi ez:** Ha egy frakció egymás után több raidet veszít, a fővárosánál ideiglenes
védő-buffot kap — anti-snowball mechanika a gyengülő oldalnak.
**Hogyan működne:** `RaidManager.endRaid()` mellé egy vereség-számláló frakciónként
(memóriában, heti reset); N egymást követő vereség után (`raid.morale.losses-for-buff`,
pl. 3) a frakció fővárosi territóriumán belül tartózkodó tagjai gyenge, ideiglenes buffot
kapnak (pl. +10% páncél, rövid regeneráció) a következő raid alatt, amíg nyernek vagy a
számláló lejár. HUD/chat jelzi („A frakciód elszántan védekezik — morál-buff aktív”).
**Miért jó:** Megakadályozza, hogy egy domináns frakció végtelenítve söpörje a gyengébbeket —
a vesztes oldalnak esélyt ad visszavágni, ahelyett hogy a spirál miatt teljesen kiürülne.
**Építőkövek:** RaidManager (endRaid, ActiveRaid), TerritoryManager főváros-zóna, HUD.
**Buktatók:** túl erős buff "farmolható vereség-taktikát" szülhet (szándékos vesztés a
buffért) — a szorzót érdemes szerénynek tartani és csak valódi győzelemig aktívnak.

### G12. Gyengébb frakció felzárkóztató mechanika
🟢 • ⭐⭐

**Mi ez:** Az online-létszámban vagy liga-pontban leszakadó frakció tagjai kis, ideiglenes
kill-reward/XP-bónuszt kapnak, hogy a részvétel ne váljon értelmetlenné.
**Hogyan működne:** Heti/napi tick kiszámolja a frakciók online-létszám-arányát és liga-állását
(`SeasonManager`); az utolsó helyezett (vagy létszámban legkisebb) frakció tagjai
`faction.catchup.xp-bonus-percent` szorzót kapnak PvE XP-re/kill-jutalomra (nem PvP-re, hogy
ne torzítsa a harcot). A HUD egy diszkrét jelzést mutat („felzárkóztató bónusz aktív”).
**Miért jó:** A 4 frakciós metában egy tartósan gyenge oldal frusztrálóan érzi magát —
ez a mechanika nem PvP-erőt ad (ami snowballt okozna), hanem PvE-progressziót gyorsít, hogy a
lemaradó frakció tagjai ne érezzék feleslegesnek a maradást.
**Építőkövek:** SeasonManager liga-adatok, online-létszám lekérdezés, XP/kill-reward réteg.
**Buktatók:** ha PvP-bónuszra terjed ki, öngerjesztő igazságtalanságot szül — szigorúan
PvE-only bónusz maradjon.

### G13. Füstjelzők/zászlók raid-jelölőnek
🟢 • ⭐⭐

**Mi ez:** Raid alatt lerakható, csapat-színű jelölő-blokk/item, amivel a résztvevők
taktikai pontokat (gyülekező, célpont, veszély) jelölhetnek a csapattársaiknak.
**Hogyan működne:** Craftolható „jelzőfüst" item, csak aktív raid alatt, csak a saját oldal
(`RaidManager.isParticipant`) számára használható: lerakva rövid ideig élő, frakció-színű
partikel-oszlopot + a raid-résztvevők action-barján egy iránytű-jelzést ad
(A21 iránytű-mintára). Bűn-rendszert nem érinti (nem block-break/place a valós világban,
vagy ha igen, X mp után automatikusan eltűnik).
**Miért jó:** Kommunikációs eszköz koordinált csapatoknak, taktikai mélységet ad anélkül,
hogy bármilyen erőt adna — tisztán információs előny, a gyengébb szervezettségű oldal is
használhatja.
**Építőkövek:** RaidManager részvétel-ellenőrzés, A21 iránytű-minta, partikel-effekt.
**Buktatók:** vizuális zaj-veszély nagy csatában — a partikel-sűrűséget és élettartamot
alacsonyan kell tartani.

### G14. Kém-mechanika: álca a LibsDisguises-hídon

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### G15. Ellátmány-vonal raid alatt
🟡 • ⭐⭐

**Mi ez:** Hosszabb raidekben egy „ellátmány-pont”, ahonnan a résztvevők gyógyszer/nyílvessző/
étel-utánpótlást vehetnek fel — ha a védő elvágja, a támadó ereje csökken.
**Hogyan működne:** A raid-terület szélén (vagy a támadó gyülekezőpontján) egy „ellátmány-láda”
spawnol a felkészülési szakaszban, amit bármelyik szentesített résztvevő X percenként
kimeríthet (kis mennyiségű étel/nyíl/potion, konfigurálva `raid.supply.refill-interval`).
Ha a védő oldal sikeresen a láda köré áll és X másodpercig kontrollálja (mini pont-tartás),
a láda ideiglenesen „elvágottá” válik (nem tölt) — HUD-jelzés mindkét oldalnak.
**Miért jó:** Másodlagos taktikai célpont a fő objektíva mellett — a védőnek van proaktív,
nem csak reaktív lehetősége, és a hosszú raidek nem válnak pusztán állóháborúvá.
**Építőkövek:** RaidManager fázis/pont-tartás logika, HUD bossbar.
**Buktatók:** ha az ellátmány túl erős, a raid az ellátmány-harcról szól a fő célpont
helyett — kis, kiegészítő hatásúnak kell maradnia.

### G16. Nagydöntő: top2 frakció szezon-hétvégéje `[TOP]`
🟡 • ⭐⭐⭐

**Mi ez:** A szezon utolsó hétvégéjén a liga-táblázat 1. és 2. helyezett frakciója egy
kiemelt, dupla liga-pontos, extra jutalmú „nagydöntő" raid-sorozatot vív egymás ellen.
**Hogyan működne:** `SeasonManager` szezonzáró-logikájának bővítése (a B33 „végítélet-hét"
világesemény testvér-mechanikája): a záró hétvégén a top2 frakció között a `RaidManager`
automatikusan feloldja a raid-hirdetést (a királyoknak csak el kell indítaniuk), a
liga-pont-szorzó és a hadizsákmány dupla (`season.finale.point-multiplier`), broadcast +
Discord-webhook (C5) jelzi szerver-szinten. A győztes kap egy exkluzív szezon-kozmetikát
(B9/B22 cím) és bekerül a szezon-emlékműbe (D3).
**Miért jó:** A szezonnak látványos, mindenki által követhető csúcspontot ad — még a
nem résztvevő játékosok is nézőként/broadcast-fogyasztóként élik meg az „élő világ” érzést.
**Építőkövek:** SeasonManager liga-tábla, RaidManager, B33 végítélet-hét, D3 szezon-emlékmű,
C5 Discord-híd.
**Buktatók:** ha a top2 rendszeresen ugyanaz a két frakció, ismétlődővé válhat — a G12
felzárkóztató mechanika közvetve ezt is tompítja.

### G17. Raid-előrejelzés (barátkozás/riasztás)
🟢 • ⭐⭐

**Mi ez:** A király raid-hirdetése előtt egy rövid „mozgósítási" időablak, ami alatt a
védő frakció figyelmeztetést kap és felkészülhet, mielőtt a hivatalos felkészülési
szakasz elindul.
**Hogyan működne:** `/faction raid <cél>` meghirdetéskor (a `RaidManager.startRaid` elé
építve) a védő frakció összes online tagja azonnali broadcast + HUD-cím üzenetet kap
(„⚠ <Frakció> hadat üzent!”), és egy konfigurált késleltetés (`raid.announce-delay-seconds`,
pl. 60 mp) után indul csak a tényleges felkészülési szakasz (join-ablak). Ez alatt a védők
összegyűlhetnek, felszerelkezhetnek, de a `joinRaid` még nem nyílik meg.
**Miért jó:** Csökkenti a „lecsaptak, mire beléptem, már vége volt” frusztrációt — igazságosabb
esélyt ad a védőnek anélkül, hogy a raidet meghosszabbítaná.
**Építőkövek:** RaidManager.startRaid, HUD cím-API, faction broadcast.
**Buktatók:** a hosszú előrejelzés túl kiszámíthatóvá teheti a raidet (a védő túlerősítheti
magát) — a B30 háború-ablakokkal együtt kell hangolni, hogy ne legyen túl bőkezű.

### G18. Kereszttűz-bónusz: 3+ frakciós összecsapás
🟡 • ⭐⭐

**Mi ez:** Ha egy raid-zónában (vagy G5 határvillongásban) egy harmadik frakció tagjai is
megjelennek szemlélőként vagy opportunista támadóként, egy külön szabálykészlet aktiválódik.
**Hogyan működne:** A `RaidManager.isSanctionedKill` bővítése: ha egy harmadik frakció tagja
belép a raid-zónába és a raid-résztvevőkre támad, ez a HARMADIK fél számára is
szentesített (nem bűn), de a raid pontszámába nem számít bele (nem tud nyerni) — csak
„zavaró" szerepet játszhat (pl. csökkenti az egyik oldal létszámát). A zónában lévők
figyelmeztetést kapnak, ha harmadik fél lép be.
**Miért jó:** Extra taktikai réteg a 4 frakciós metában — szövetségek (B44) és rivalizálás
(G10) itt válik ténylegesen kézzelfoghatóvá, anélkül hogy a fő raid-szabályokat felborítaná.
**Építőkövek:** RaidManager, TerritoryManager zóna-belépés esemény, B44 diplomácia.
**Buktatók:** komplexitás-robbanás a sanctioned-kill logikában — gondos tesztelés kell, nehogy
a harmadik fél véletlenül bűnt kapjon vagy pontot lopjon.

### G19. PvP-tanonc mód (első 7 nap védelme)
🟢 • ⭐⭐

**Mi ez:** Új, nemrég regisztrált játékosok rövid, csökkenő védelmet kapnak a nem-szentesített
PvP-vel szemben, hogy az első heti tanulási görbét ne PvP-halál törje meg.
**Hogyan működne:** Első csatlakozástól számított `pvp.newbie-protection-days` (pl. 7) napig
a játékos nem célozható békeidős (nem raid/duel/aréna) PvP-vel — a `SpellTargetingUtil`
(A2) egy extra ellenőrzést kap. A védelem az első saját kezdeményezésű PvP-akcióval
(pl. `/duel` elfogadás, raid `join`) azonnal megszűnik önként. HUD-on visszaszámláló
jelzi a hátralévő napokat.
**Miért jó:** Az új játékosok első benyomása ne a „levadászás” legyen egy PvP-fókuszú
szerveren — közvetve az A5 onboarding-lánc (első 10 perc) természetes párja hosszabb távon.
**Építőkövek:** SpellTargetingUtil (A2), PDC join-időbélyeg, HUD.
**Buktatók:** exploit-veszély, ha valaki szándékosan "véd magát" a védelemmel farmolás
közben (pl. körözöttet nem lehet levadászni rajta) — a körözött-kivégzés kivétel maradjon
a védelem alól is.

### G20. Frakció-hírszerzés: ellenség online-térkép
🟢 • ⭐

**Mi ez:** A király (vagy egy dedikált „felderítő" rang) egy korlátozott GUI-ban láthatja,
mely ellenséges frakció-tagok vannak online és nagyjából hol (territórium-szinten, nem
pontos koordináta).
**Hogyan működne:** `/faction intel` — a király/felderítő rangnak elérhető GUI, ami a
`TerritoryManager` zónáira bontva ("N ellenséges játékos a <zóna> környékén") mutatja az
online ellenfelek eloszlását, koordináta nélkül. Adatforrás: online-lista + utolsó ismert
territórium-belépés eseményből (nem élő tracking, néhány perces késleltetéssel, hogy ne
legyen valós idejű ESP).
**Miért jó:** Stratégiai döntéstámogatás a királynak raid-hirdetéshez, anélkül hogy pontos
koordináta-tracking-gel (ami visszaélésre ad módot) sértené a fair playt.
**Építőkövek:** TerritoryManager zóna-belépés, online-játékos lista, /menu GUI-minta.
**Buktatók:** ha túl pontos, ESP-szerű nyomkövetéssé silányul — szándékosan durva
granularitás (zóna-szint, késleltetett adat) kell.

### G21. Frakció-becsület-pontok (nem pénz-alapú presztízs)
🟢 • ⭐⭐

**Mi ez:** Szentesített PvP-győzelmekért (raid, duel, aréna) járó pénz-semleges
becsület-pont, ami frakció-szintű ranglistát és egyéni presztízst épít.
**Hogyan működne:** A `StatsManager` egy új, PvP-specifikus számlálót kap
(`honor-points`), ami kizárólag szentesített ölésekért nő (a `RaidManager.
isSanctionedKill` / duel/aréna eredmény hookjaiból), sima gyilkosságért soha. A
`/leaderboard honor` új kategória (A22-mintára), a frakció-összesített becsület a
`/faction info`-ban jelenik meg. A pont önmagában nem váltható pénzre — csak
címekhez (B22) és kozmetikákhoz (B9) nyit utat.
**Miért jó:** Presztízs-célt ad a PvP-seknek a pénz-gazdaság megkerülésével (nincs
inflációs kockázat), és a frakció-összesített szám közösségi büszkeséget épít.
**Építőkövek:** StatsManager, A22 leaderboard, B22 címek, RaidManager sanctioned-kill.
**Buktatók:** ha csak egyéni, a gyengébb PvP-sek lemaradnak — a frakció-összesített nézet
mindenkinek ad részvételi értéket, még ha keveset is termel egyénileg.

### G22. Menekülő-bónusz: utolsó állás
🟢 • ⭐

**Mi ez:** Szentesített PvP-ben (raid/duel), ha valaki kritikusan alacsony HP-n túléli a
harcot és elmenekül, kis, egyszeri regeneráció-löketet kap — a "hajszál híján megúsztam"
érzésnek mechanikai visszajelzést ad.
**Hogyan működne:** A meglévő combat-tracking (A9 death recap ring-buffer mintája) figyeli,
ha egy szentesített PvP-harcban a HP 15% alá esik, majd a harcos X másodpercig nem kap
újabb találatot (sikeres menekülés) — ekkor egyszeri, kis (`pvp.close-call.heal-percent`)
azonnali gyógyítás + rövid regeneráció-effekt, cooldownnal (naponta egyszer/kaszt).
**Miért jó:** A menekülés is legyen "eredmény", ne csak a kill — csökkenti a "vagy nyersz
vagy meghalsz" feszültséget, anélkül hogy PvP-erőt adna (csak a már megnyert menekülést
jutalmazza).
**Építőkövek:** A9 death recap ring-buffer, RaidManager/B2 duel sanctioned kontextus.
**Buktatók:** kombózható exploittal (szándékos "majdnem meghalás" cooldown-résre) — napi
limit és csak valódi kritikus HP-küszöb véd ellene.

### G23. Frakció-zászló kihelyezhető trófeaként
🟢 • ⭐

**Mi ez:** Sikeres területvédés vagy raid-győzelem után a győztes frakció egy
kihelyezhető, dekoratív zászló-trófeát kap a fővárosba — vizuális emlékeztető a
diadalokra.
**Hogyan működne:** `RaidManager.endRaid()` győzelmi ághoz kötve: a győztes frakció
kap egy nem-eladható trófea-itemet (dátum + ellenfél neve PDC-ben), amit a fővárosi
territóriumban (ItemDisplay/banner-blokk) kihelyezhet. A `/faction trophies` parancs
listázza az összes eddigi győzelmet.
**Miért jó:** Alacsony munkával, tartós fizikai nyoma marad a háborúknak a világban — a D3
szezon-emlékmű kisebb, gyakoribb testvére, közösségi büszkeség-építő.
**Építőkövek:** RaidManager endRaid hook, ItemDisplay (D3-mintára), PDC.
**Buktatók:** sok győzelemnél a fővárosban zászló-zsúfoltság — méret/darabszám-limit vagy
lecserélhető "legutóbbi N" logika kell.

### G24. Fegyvernyugvás utáni cooldown (raid spam elleni fék)
🟢 • ⭐⭐

**Mi ez:** Ugyanaz a két frakció nem hirdethet raidet egymás ellen közvetlenül egymás
után — rövid, konfigurált szünetet kell tartani, hogy a vesztes ne legyen azonnal
újra lecsapva.
**Hogyan működne:** `RaidManager.startRaid` elé egy pár-szintű cooldown-ellenőrzés
(`raid.same-pair-cooldown-hours`, pl. 6 óra) az adott attacker↔defender párra — a
király hirdethet más frakció ellen közben, csak ugyanazt nem azonnal. `/faction raid
status` mutatja a hátralévő időt, ha blokkolva van.
**Miért jó:** Véd a "vesztes azonnal újra lecsapva, esélye sincs regenerálódni" spirál
ellen — közvetlenül kiegészíti a G11 morál-buffot egy egyszerűbb, időalapú fékkel.
**Építőkövek:** RaidManager.startRaid, konfig-kulcs.
**Buktatók:** ha túl hosszú, a rivalizálás (G10) lendületét törheti — a B30 háború-ablak
gyakoriságával összhangban kell belőni.

### G25. Csapat-egyensúlyozó raid-jelentkezéskor
🟢 • ⭐⭐

**Mi ez:** Ha az egyik oldal jelentkezési létszáma jelentősen meghaladja a másikét a
felkészülési szakasz vége felé, egy figyelmeztetés/ajánlás jelenik meg, hogy a
túlerőben lévő oldal önkéntesen visszalépjen a fair meccsért.
**Hogyan működne:** `RaidManager.joinRaid` mellé egy létszám-arány figyelő
(`countParticipants` mindkét oldalra); ha az arány meghalad egy küszöböt
(`raid.balance-warning-ratio`, pl. 1,5×) a felkészülési szakasz utolsó harmadában,
mindkét oldal HUD-üzenetet kap a létszám-különbségről (nem kényszerít, csak informál).
Opcionálisan a gyengébb oldalnak a G12 XP-bónusz aktiválódik a raid idejére is.
**Miért jó:** Átláthatóságot ad a jelentkezési egyenlőtlenségről — a közösség saját
fair play-normái tudnak rá reagálni, admin-kényszer nélkül.
**Építőkövek:** RaidManager (joinRaid, countParticipants), HUD, G12 felzárkóztató.
**Buktatók:** ha kötelezővé tennénk a limitet, az szervezési szabadságot venne el —
marad tájékoztató jellegű, nem blokkoló.

---

[← Ötlettár-index](../IDEAS.md)
