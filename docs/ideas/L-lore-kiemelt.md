# L) Lore-kiemelt ötletek (válogatás)

A kanonikus kódex ([docs/LORE.md](../LORE.md)) alapján **kiválogatott, erősen lore-illeszkedő
tételek** az A/B/D/E/F/G/H/I/J kategóriákból — az eredeti azonosítók megtartva (a forrás-fájlban egysoros
mutató maradt a helyükön). Minden tétel elején a **lore-horgony**: melyik kódex-elem teszi
különösen indokolttá.

Jelölés: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐).

---

## A kódex beépítési terve (K1–K10)

*A LORE.md közvetlen beépítése a meglévő rendszerekbe — nem „illeszkedő ötlet", hanem a
kánon saját megvalósítási terve. Ajánlott sorrend: **K1** (reskin) → **K2/K3/K5**
(signature itemek a kész motorra) → **K7** (Nether-zóna) → a nagy RP-rendszerek (**K8/K9**).*

### K1. Frakció- és valuta-identitás reskin `[TOP]` `[KÉSZ ✅]`
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A jelenlegi placeholder-nevek (`factions.yml`: „Piros/Kék/Semleges/Sötét"; `economy.yml`:
„… Token") lecserélése a lore kanonikus neveire — frakciók, valuták, fővárosok, spawn-hangulat.

**Hogyan működne:**
- `FactionType` display-nevek → **rövid + hosszú alak** (a HUD/tab helyszűke miatt): `RED` = „Láng" /
  *Perinfernicitas*, `BLUE` = „Fagy" / *Cryghaliris*, `NEUTRAL` = „Menedék" / *Ryanora & Caldestera*,
  `DARK` = „Kitaszított" / *A Kitaszítottak*. A HUD/tab a rövidet mutatja, a `/faction info` és a
  menük a hosszút. (A „Suttogók" NEM frakciónév — az a rejtett státusz, lásd K9 / kódex VII.)
- Valuta display-nevek (`economy.yml`): „Piros Token" → **Parázsló Parals** (RED), „Kék Token" →
  **Hópihér-veret** (BLUE), „Semleges Token" → **Creutzér** (NEUTRAL, a `C`/közös valuta = Creutzér),
  „Sötét Token" → **Csontveret** (DARK — a Csontszámvevő veretje, kódex VII.); a `symbol` marad vagy
  frakció-jelet kap.
- Fővárosok: a `/territory setcapital` zónák nevei Glatziendorf (BLUE), Pyralingrad (RED), Caldestera
  (NEUTRAL), **Mortengrad** (DARK — rom-főváros, a világépítés után); a broadcast/hangulat-szövegek
  ehhez igazodnak.

**Miért jó:** Egyetlen, olcsó lépés, ami az egész szervert „a lore világává" teszi — minden HUD-,
chat-, menü- és bolt-szöveg egyszerre kap identitást. Innen minden további tartalom hiteles.

**Építőkövek:** `FactionType`/`factions.yml`, `CurrencyType`/`economy.yml` display-name mezők,
`MessageManager` kulcsok, HUD/tablist rövid-név forrás, `/territory` főváros-nevek.

**Buktatók:** HUD/tab helyszűke → kötelező rövid alak; a `fromString`/`fromId` az ENUM-nevet és a
display-nevet is elfogadja, tehát a parancs-parsolás ne törjön a hosszú névtől; a `messages.yml`
frakció-hivatkozásait végig kell nézni, hogy ne maradjon „Piros".

### K2. Cryghaliris signature unique-itemek `[TOP]` `[KÉSZ ✅]`
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A Fagyott Királyság 3 jellegzetes tárgya a meglévő unique-item + raritás + crafting-gate
motorra: **Kallan Szeletelője** (íj: +nyíl-sebesség, páncéltörés), **Glatziendorfi Jégvért** (nehéz
mellvért: Resistance szett-perk), **Jégsárkány-Kantár** (hátas-felszerelés).

**Hogyan működne:** `BlueprintItemFactory`/`UniqueMaterialFactory` PDC-tagelt itemek + recept a
`profession-recipes.yml`-ben `learn: blueprint`-tel (ritka BLUE-drop/NPC), a craftot a
`crafting-restrictions` BLUE-frakcióhoz és/vagy páncélkovács-szinthez köti. A perkek a meglévő affix/
attribútum-úton (a Jégvért szett-bónusza a `FactionPassiveListener`/attribútum-minta szerint).

**Miért jó:** A frakciónak **saját, vágyott end-game tárgyai** lesznek — a BLUE „nehéz fegyver, vastag
páncél, páncéltörő nyíl" fantázia kézzelfoghatóvá válik, és a blueprint-drop hajtja a világ-felfedezést.

**Építőkövek:** `BlueprintItemFactory`, `UniqueMaterialFactory`, `ItemRarityService`, `MobLootListener`
(blueprint-drop), `CraftingRestrictionManager`, `professionRecipeCatalog`.

**Buktatók:** Balansz — a perkek ne legyenek pay-to-win a frakció-passzív FÖLÖTT; a szett-bónusz
csak teljes szettre; a páncéltörés vanília-limitek közt maradjon.

### K3. Perinfernicitas signature unique-itemek `[KÉSZ ✅]`
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A Lángoló Birodalom 3 tárgya: **Pyralingradi Tűzköpő** (számszeríj: +felhúzási/repülési
sebesség), **A Vérszavanna Agyara** (kard/lándzsa: fegyver-kombó Strength-perk), **Főnix-Tollköpeny**
(kiegészítő: tűz/láva-ellenállás — a RED-passzívra rímel, de tárgyként hordozható).

**Hogyan működne:** ugyanaz a minta, mint K2, RED-gate-tel. A Tűzköpő a meglévő számszeríj-mechanikára
(quick-charge), az Agyar a kard+balta kcombó-perk mintára (ha van; különben attribútum-affix).

**Miért jó:** A RED „gyors, halálos, robbanó" identitása tárgyakban ölt testet; a köpeny lore-hű
(főnix-toll) és hasznos a Nether/láva-tartalomhoz.

**Építőkövek:** mint K2 + a meglévő számszeríj/kombó-perk logika.

**Buktatók:** A Tűzköpő ne legyen strictly jobb a vanília számszeríjnál minden helyzetben (niche:
sebesség vs. tartósság); a köpeny tűz-ellenállása ne duplázza a RED-passzívot végtelenre.

### K4. Ryanora & Caldestera signature itemek (gyűjtő/gazdaság) `[KÉSZ ✅]`
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A Menedék tárgyai a béke/kereskedelem/tudás jegyében: **Vasművek Akadémiájának Csákánya**
(+20% bányász-loot), **Bokic-menti Horgászbot** (+20% horgász-loot), **Smaragdkő Bankbetét**
(értékpapír/bank-item), **Szellemszarvas-Bűbáj** (hátas-hívó).

**Hogyan működne:** a loot-perkek a meglévő **gathering-buff**/loot-szorzó úton (esemény helyett
tárgy-hordozott, kis állandó bónusz); a Bankbetét a bank/valuta-rendszerhez köthető (fix címletű,
átváltható papír); a Bűbáj a pet/hátas-rendszerre.

**Miért jó:** A NEUTRAL nem harci frakció — a tárgyai **gazdaságiak és kényelmiek**, ami a lore-hű
„fegyvermentes, gazdag polgárváros" élményt adja, és a gyűjtögető-gazdaság hurkot erősíti.

**Építőkövek:** gathering-buff/loot-szorzó logika, bank/`CurrencyManager`, pet/hátas-rendszer,
`UniqueMaterialFactory`.

**Buktatók:** A +20% loot ne legyen stackelhető a gathering-buff eseménnyel korlátlanul (sapka);
a Bankbetét ne legyen dupe-olható (PDC-egyediség + atomi beváltás).

> **Megvalósítva:** 4 NEUTRAL-kapus signature recept (bányász/horgász/rúnaírnok/füvész) — Csákány
> (+20% érc-drop, bányász-láz alatt szünetel), Horgászbot (+20% dupla fogás, láz alatt szünetel),
> Bankbetét (jobb-katt: atomi beváltás Creutzérre a játékos saját régió-szálán), Szellemszarvas-
> Bűbáj (cooldownos, ideiglenes gyors hátas). Kulcsok: `signature.*` (crafting.yml).

### K5. Káoszkor / Néma Királynő élőhalott-loot `[KÉSZ ✅]`
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A frakció nélküli portyázó élőhalottak és ősi romok drop-katalógusa: **A Hetedik Vérháború
Rozsdás Pengéje** (fegyver), **Fekete Villám Szilánk** (ritka crafting-alapanyag — ✅ a név már él:
az `osi_ereklyeszilank` display-neve + boss-drop és 4 magas-tier recept használja), **Eleftheria
Könnye** (misztikus relikvia), **Megrontott Elit Páncél** (vért).

**Hogyan működne:** a `MobLootListener` drop-táblájába Káoszkor-mobokra (élőhalott-variánsok, a
Néma Királynő-világesemény); a Fekete Villám Szilánk a magas-tier receptek (K2/K3 unique-itemek)
alapanyaga → köti a világ-tartalmat a craftinghoz; Eleftheria Könnye a relikvia-rendszerbe
(`RelicManager`, egy-példányos).

**Miért jó:** Ad egy **frakció-független, mindenkinek elérhető** loot-réteget, ami a Káoszkor-
hangulatot viszi, és a Fekete Villám Szilánkon át **gazdasági keresletet** teremt a veszélyes zónák
felé (a unique-itemekhez ez kell).

**Építőkövek:** `MobLootListener`, `WorldBossManager`/világesemény (Néma Királynő), `RelicManager`,
`profession-recipes.yml` (a Szilánk mint alapanyag).

**Buktatók:** A relikvia egy-példányosságát a `RelicManager` már kezeli; a Szilánk drop-rátája
gazdaság-érzékeny (túl sok → értéktelen unique-itemek).

### K6. Frakció-ételek és fogyasztási kötelezettség `[KÉSZ ✅]`
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A lore szerint a BLUE-nak halat, a RED-nek tojás-ételt kell ennie a frakció-aurája ellen;
plusz a NEUTRAL **Tiltott Kakaóbabos Süteménye** (robbanó csemege). Tárgyak: **Fagyasztott Tavi
Pisztráng** (BLUE), **Fűszeres Főnixtojás-Rántotta** (RED), **Tiltott Kakaóbabos Sütemény** (NEUTRAL).

**Hogyan működne:** a fogyasztási „kötelezettség" a frakció-passzív rendszer kiegészítése: ha egy
BLUE-játékos régóta nem evett halat (vagy RED tojást), enyhe debuff-aura (lore: a Jégmezők/Vérszavanna
kíméletlensége) — configgal kapcsolható, puha (nem frusztráló). A süti a meglévő „robbanó csemege"
mintára (ha van „Asterlayna Gyümölcse", ez annak lore-neve).

**Miért jó:** Apró, de **erős immerzió** — a frakciódnak „konyhája" van, és a szakma-gazdaság (Séf,
Halász) frakció-specifikus keresletet kap.

**Építőkövek:** `FactionPassiveListener`, a meglévő étel/consumable + robbanó-süti perk, `COOK`/
`FISHERMAN` szakma, `MessageManager`.

**Buktatók:** A kötelezettség-debuff LEGYEN puha és opcionális (config), különben büntető; a
frakcióváltás/új játékos ne kapja azonnal; Folia: az aura-tick a játékos szálán.

> **Megvalósítva:** 3 séf-recept (Pisztráng BLUE / Rántotta RED / Sütemény NEUTRAL, kis tematikus
> buffokkal, a süti „robban"); honvágy-mechanika: `factions.food-duty` (12h grace, 5 percenként
> rövid Éhség + action-bar, türelmi idő újaknak; a vanília hal/tojás-étel is számít). Folia: a
> consume a játékos szálán, a tick játékosonként hopol.

### K7. Kárhozat Kapuja — Nether-portál PvPvE senkiföldje `[TOP]` `[KÉSZ ✅]`
**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** A Hetedik Vérháborút kirobbantó **óriás Nether-portál** mint a szerver legveszélyesebb,
frakció-semleges **high-tier PvPvE-zónája** — „Kárhozat Kapuja" / „Az Őrület Hasadéka". A legjobb
nyersanyagért (netherite, boss-drop, Fekete Villám Szilánk) járnak ide a Felsők, és a két frakció
itt **legálisan** összecsaphat.

**Hogyan működne:** a `TerritoryManager` egy speciális zóna-típusa (PvP mindig engedett, a bűn-
rendszer itt nem büntet), fokozott mob-veszély (a Néma Királynő élőhalottai, K5-drop), és exkluzív
loot/craft-alapanyag. A „szivárgó Nether-fertőzés" hangulatot ambient-particle + a zóna-belépő
üzenet adja.

**Miért jó:** Ad egy **közös, magas kockázatú célt** minden frakciónak — a PvP-t egy legális arénába
tereli, a gazdaságot a veszélyes zóna hajtja, és a lore központi konfliktusát játszhatóvá teszi.

**Építőkövek:** `TerritoryManager`/zóna-típusok, `TerritoryProtectionListener` (PvP-szabály),
`MobScalingManager`/`MobLootListener` (fokozott veszély+loot), `AmbientEventManager` (fertőzés-hangulat).

**Buktatók:** Balansz — a zóna jutalma érje meg a kockázatot, de ne legyen az EGYETLEN netherite-forrás;
Folia: a zóna-szkennelés/PvP-hook régió-lokális; a spawn-kill elleni védelem (belépő grace).

> **Megvalósítva:** `DOOM_GATE` territórium-típus (`/territory ... doom-gate`) — PvP legális
> (rules.doom-gate.pvp=false), ölés nem bűn (sin-exempt), +3 mob-szint (bonus-mob-levels),
> 8 mp belépő-grace (a támadó elveszti), aréna-védelem (build/explosions/fire), belépéskor
> baljós hang + hamu-örvény. Minden kulcs élőben olvasódik (restart nélkül hangolható).

### K8. Emlékszilánkok — a Felsők emlék-progressziója `[KÉSZ ✅]`
**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** A Felsők (játékosok) amnéziával érkeznek; emlékeik fizikai **Emlékszilánkokként** rejtőznek
kazamatákban, romokban, erős szörnyeknél (kánon: kódex V. — „Az elveszett emlékek"). Elég szilánkból a
játékos „visszaemlékszik" — **XP/szint, talentpont, a kasztja egy specializációjának feloldása,
frakció-rang vagy kozmetika/lore-feltárás**. FONTOS: **class-váltás NINCS** — a kaszt állandó (a
korábbi „lovag → sárkánylovas" példa decanonizálva). Item: **Opálos Emlékszilánk**.

**Hogyan működne:** gyűjthető PDC-item (`UniqueMaterialFactory`), a beváltás a spec-feloldó /
talent / rang-rendszerbe köt; a szilánkok forrása strukturált loot (romok, boss, kazamata — a Mélység
Népe romjai kész helyszín-kánon, kódex I.). Lehet lineáris (X szilánk = 1 emlék) vagy set-gyűjtés
(nevesített szilánkok egy „emlék" kirakásához).

**Miért jó:** **Narratív progressziós tengely** a puszta grind mellett — a „ki voltam?" kérdés hajtja
a felfedezést, és a class-váltás/rang lore-ba ágyazottan indokolt lesz.

**Építőkövek:** `UniqueMaterialFactory`, class/spec-feloldás (`JobManager`/`SpecializationManager`),
talent/rang-rendszer, quest/loot-forrás, `MobLootListener`.

**Buktatók:** A class-váltás mély rendszer-érintés — óvatos horgony (ne törje a meglévő progressziót);
a szilánk-gazdaság inflálódhat; egyértelmű UI kell a „hány szilánk kell még" állapothoz.

> **Megvalósítva:** Opálos Emlékszilánk (unique material, mob/boss-loot) + `/emlek` beváltó:
> xp (3→500 kaszt-XP), talent (5→+1 kaszt-talentpont, PDC-additív), spec (8→a spec-választás
> szint-kapuja elesik, egyszeri), lore (1→emlék-töredék). Class-váltás NINCS (kánon szerint).
> Költségek: `memory-shards.*` (general.yml), élőben olvasva.

### K9. A Suttogók — a rejtett hálózat és a Kitaszítottak (hibrid) `[KÉSZ ✅]`
**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** A lore „titkos szekta" koncepciójának és a kód „bűnözői száműzetés" (`DARK`) mechanikájának
**összehangolása egy hibrid modellben**. A **Suttogó** egy REJTETT STÁTUSZ, amely bárki látható
frakciója (RED/BLUE/NEUTRAL) fölé rétegződik — ideológiai „fertőzés", nem frakcióváltás. A `DARK`
frakció marad a **Kitaszítottak fizikai száműzetése**: ide zuhannak a lelepleződött Suttogók ÉS a
közönséges bűnösök. A kettőt a **lelepleződés** köti össze.

**Hogyan működne:**

*1) Rejtett státusz (a fertőzés):* egy `whisperer` PDC-flag + perzisztens állapot (új `WhisperManager`).
A játékos megtartja látható frakcióját, HUD-ját, chat-színét — kívülről semmi sem árul el. Cserébe:
- külön **titkos chat-csatorna** (`/w <üzenet>` — csak Suttogók látják), a Néma Királynő „suttogásaként";
- hozzáférés a **sötét unique-itemekhez** (K5 / K-katalógus: Árnyékpor-vonal, Eleftheria Könnye);
- opcionális titkos **hálózati célok** (heti „szabotázs"-quest: rontsd egy főváros közhangulatát).

*2) Csatlakozás — a Sötét Rítus:* a meglévő **rituálé-oltár rendszert** (relics.yml altar-blokk minta)
újrahasznosítva. Egy rejtett **Suttogó-oltár** (pl. SOUL_FIRE + SCULK + CRYING_OBSIDIAN mag-struktúra),
amit **éjjel, magányosan** (nincs másik játékos X blokkon belül — a titok feltétele) SHIFT+jobb kattal
aktiválsz, kézben a **„Suttogás" meghívóval** (ritka drop / titkos NPC / lore-nyom). Az áldozat a
meggyőződés ára: **saját vér** (HP-áldozat, a vérmágia-tónusban) + egy égetett tárgy/valuta (money
sink). Sikerkor fekete köd lepi el a képernyőt, wither-suttogás szól, s a `whisperer` flag felkerül.
(A rítus helye/receptje maga is felfedezendő — nyomozós RP.)

*3) Lelepleződés — a gyanú-mérő:* rejtett, játékosonkénti **gyanú-pont** (suspicion), amely
dark-akcióktól nő és lassan csökken:
- **közterületen** (más nem-Suttogó játékos látótávján belül) **sötét mágia / Suttogó-tárgy használata**
  → nagy gyanú-ugrás + azonnali kis „árulkodó jel" (fekete SMOKE/SOUL particle + halk suttogás a
  közelállóknak);
- **rajtakapott árulás** (frakciótárs megölése Suttogóként) → nagy ugrás;
- **rajtakapott rítus** (más látja az oltár-aktiválást) → nagy ugrás;
- **tett-alapú tanúzás:** aki LÁTTA a sötét mágiát, kap egy rövid életű **„Tanú" tokent**, amivel
  `/whisper accuse <játékos>`; a vádak halmozódnak, elég tanú → leleplezés (ez a nyomozós mag);
- *(RP-tell: a DARK-passzív miatt az élőhalottak békén hagyják — az éber megfigyelő gyanút foghat.)*

*4) A leleplezés pillanata (vizuális dráma):* amikor a gyanú átüt a küszöbön, a világ „lerántja az
álcát": a játékos körül **kialszik a fény**, fekete SOUL-particle-örvény tör fel, mély wither/raid-kürt
szól, a **neve sötétlilára vált** minden közelállónak, fölé „💀 Kitaszított" jelölés kerül, és
ominózus **szerver-broadcast**: *„A Suttogás lelepleződött: <név> a Néma Királynő szolgája!"*

*5) Suttogóból Kitaszított:* a leleplezés a meglévő **bűn/száműzetés-mechanikára** csatlakozik: a
játékos **bűnössé** válik, a rendszer **átteszi a `DARK` frakcióba** (a Kitaszítottak közé), a
`whisperer` álca lehullik, és **azonnali, emelt vérdíj** kerül a fejére (áruló → magasabb fejpénz).
Megtartja a sötét-item hozzáférést, de már mindenki vadássza. Visszaút: csak a **vezeklés-küldetéslánc**.

**Miért jó:** feloldja a lore↔kód ellentmondást; **belső ellenség + nyomozós RP** (árulás, gyanú,
tanúzás, vérdíj-vadászat); a `DARK` frakció puszta „bűnös-jelölésből" **kétforrású, drámai
száműzetéssé** válik, saját tartalommal.

**Építőkövek:** új `WhisperManager` (PDC `whisperer`-flag + suspicion, `PlayerStateCleanup`),
rituálé-oltár minta (relics.yml altar), bűn/vérdíj-rendszer (átváltás Kitaszítottá), spell/item-használat
hook a gyanú-növeléshez, particle/sound/broadcast a leleplezéshez, titkos chat-csatorna, sötét
unique-itemek (K5).

**Buktatók (Minecraft/Paper + Folia):**
- **Folia:** a közeli-játékos-szkennelés régió-lokális és kicsi; a más játékosnak szóló jel/particle a
  cél `getScheduler()`-én; a broadcast a `getGlobalRegionScheduler()`-en.
- **Balansz:** a leleplezés VALÓS sötét tettet igényeljen (ne véletlen); a gyanú lassan csökkenjen; új
  csatlakozónak grace; a Suttogó-perkek ne legyenek pay-to-win a frakció-passzív fölött.
- **Anti-grief/moderáció:** a „Tanú"-vád ne legyen tömegesen hamisítható (cooldown, valódi látótáv, a
  vád csak gyanút ad, nem azonnali bant); a vérdíj/lebukás féken tartja a titkos előnyt.
- **Config:** minden küszöb (gyanú-súlyok, HP-áldozat, grace, broadcast on/off) configból.

> **Megvalósítva:** WhisperManager (PDC-flag + suspicion + Tanú-tokenek, PlayerStateCleanup) —
> Sötét Rítus (meghívó-item, éjjel/sculk/egyedül/vér-ár, rajtakapva meghiúsul), /suttogas titkos
> csatorna + tanú-vád, gyanú-források (árulás/rítus/vád) + decay, leleplezés-dráma → bűnök → a
> meglévő száműzetés-pipeline a Kitaszítottak közé visz. Minden kulcs: factions.whisper.*.

### K10. Caldestera feketepiac — csempészet és rejtett fegyverek `[KÉSZ ✅]`
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Caldesterában **tilos fegyvert viselni** (a semleges főváros szabálya) → a **Botera-negyed**
feketepiaca virágzik: ártalmatlannak tűnő, rejtett fegyverek, amikkel kicselezhető a városi őrség.
Tárgyak: **Bokic-menti Sétapálca** (rejtett penge — a kapu-plugin nem koboz el), **Hamisított Menlevél**
(belépő bűnözőknek).

**Hogyan működne:** ha van/lesz „fegyvermentes zóna"-elkobzás a NEUTRAL fővárosban, a Sétapálca egy
PDC-taggel **kivételt** kap (a kobzás-listener átengedi); a Menlevél a belépés-kapu ellenőrzését
csalja ki. Beszerzés: feketepiac-NPC/ritka bolt a Botera-negyedben.

**Miért jó:** **Emergent gameplay** — a szabály (fegyvertilalom) automatikusan szül egy alvilágot;
csaló-tárgyak, tolvaj-fantázia, kockázat/jutalom a semleges városban is.

**Építőkövek:** a NEUTRAL-főváros fegyver-elkobzó szabály (ha implementált; K7/territory-védelem
mintára), PDC-kivétel-tag, feketepiac-NPC/bolt (`ShopManager`), `CraftingRestrictionManager`/item-factory.

**Buktatók:** Előfeltétel a fegyver-elkobzó zóna léte (különben a „rejtett" nincs mihez képest);
a kivétel-tag ne legyen általánosan másolható; a Menlevél ne kerülje meg a valódi bant, csak a
lore-kaput.

> **Megvalósítva:** CapitalLawListener — fegyvertilalom a NEUTRAL fővárosban (az őrség
> elrakatja a nyílt fegyvert; a Sétapálca bot, átcsúszik, de +5 sebzést rejt) + körözött-kapu
> (vérdíjast visszafordít, Menlevéllel nem); feketepiac-bolt (Csontveretért), a ShopManager
> name/lore/signature-áru támogatással bővült. Kulcsok: territory.capital-law.*.

---

**Összesen: 10 tétel (K1–K10).** Ajánlott kezdés: **K1** (reskin, olcsó identitás) → **K2/K3/K5**
(signature itemek a kész motorra) → **K7** (Nether-zóna) → a nagy RP-rendszerek (K8/K9).

## Tier S — a kódex szinte megköveteli
### H2. Terjedő korrupt zóna `[KÉSZ ✅]`

> **Lore-horgony:** a Kárhozat Kapujából szivárgó rontás ↔ a gyógyuló Fa — a tisztítás lore-ból a Fát gyógyítja (kódex V./VII.)

🔴 • ⭐⭐⭐

**Mi ez:** Egy sötét energiával fertőzött terület, ami — ha hagyják — éjszakánként nő, és
egyre veszélyesebb, egyre erősebb mobokat szül.
**Hogyan működne:** Ritkán, a vadonban kijelölt magpontból induló zóna (kis sugár), ami
`corruption.spread-rate` config szerint tick-enként (éjjelente) terjed, amíg el nem éri a
`radius-cap`-et vagy meg nem tisztítják. A zónán belül a MobScalingManager egy „korrupt"
mob-készletet szór (erősebb, sötét-token esély feljebb), a megtisztításhoz a korrupt mobok
egy hányadát meg kell ölni ÉS egy központi „mag"-blokkot elpusztítani (TreasureEvent-szerű
egyszeri interakció). Sikeres tisztítás jutalma nyersanyag + rövid buff a közeli játékosoknak.
Folia: a terjedés csak a magpont régiójában fut (`getRegionScheduler`), chunk-határon átnyúló
terjedésnél szomszéd-régióba hop, nem globális szkennelés.
**Miért jó:** Passzív fenyegetés, ami cselekvésre kényszerít — ha senki nem törődik vele, a
világ ténylegesen rosszabbodik, ami erős „élő világ" érzetet ad és csapatmunkára ösztönöz.
**Építőkövek:** MobScalingManager mob-készlet felülbírálás, TreasureEvent-mintájú interakció,
WorldGuard-bridge (ne terjedjen védett zónába).
**Buktatók:** Kontroll nélkül a mob-entitásszám elszabadulhat egy régióban — kemény cap
(max egyidejű korrupt mob) és a takarítás-figyelő (H24) kötelező párja.
### B42. Régészet szakma-ág `[KÉSZ ✅]`

> **Lore-horgony:** a Mélység Népe romjainak és az Emlékszilánkoknak a kiásása (kódex I./V.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Gyanús homok/kavics blokkok ecsettel áshatók: cserép-szilánkok → relikvia-
szilánk/bestiárium-oldal/recept-lap.
**Hogyan működne:** A vanília 1.20 archeológia-mechanika (gyanús blokk + ecset) rátölthető
egy `ArcheologyManager`-rel: a blokk-spawn ritka, világ-szórt (a kincs-esemény
elhelyezés-mintáját követve), a kiásott cserép-szilánk PDC-taggel jelöli, mi állítható
össze belőle (relikvia-szilánk, bestiárium-oldal — B21, recept-lap).
**Miért jó:** A vanília rendszerre épít (kevesebb saját munka), és felfedező-játékstílust
szolgáló, csendes tartalmat ad, ami más rendszerekbe (bestiárium, relikvia) táplál be.
**Építőkövek:** Vanília archeológia API, kincs-esemény elhelyezés-minta, bestiárium (B21).
**Buktatók:** A vanília gyanús blokk loot-táblája felülírandó, hogy csak a szerver-saját
tartalom (nem vanília pottery sherd) essen belőle — gondos config-munka kell.
### B3. Kézzel épített dungeonök kulcs-itemmel `[KÉSZ ✅ — plugin-oldal]`

> **Lore-horgony:** törp-kazamaták és Káoszkor-romok — a Mélység Népe eltűnése kész dungeon-narratíva (kódex I.)

**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** Szerver-csapat által épített helyszínek, admin által kijelölve, kulcs-itemért
belépve, bent skálázott mobokkal + boss-szal, heti lockouttal.
**Hogyan működne:** `/territory` új `DUNGEON` típusa (nem claimelhető, belépés-ellenőrzéssel);
belépéskor a `ClaimManager`/`TerritoryProtectionListener` mintájára egy `DungeonManager`
ellenőrzi a kulcs-item PDC-tagjét (frakció-valutáért craftolható/vásárolható — sink) és a
`PersistentDataContainer`-ben tárolt heti lockout-bélyeget. Bent a `MobScaling`
nehézség-skálázás fixen a dungeon szintjére állítva (nem távolság-alapú), a boss a
világboss-archetípus infrát (fázisok, telegraph, SLAM/ZONE speciál) újrahasznosítja.
**Miért jó:** Kézzel tervezett, ismételhető PvE-tartalom heti ritmussal — a procedurálisan
generált világesemények mellett „igazi" dungeon-élményt ad.
**Építőkövek:** `TerritoryManager` zónatípus, `MobScaling`, világboss fázis/telegraph-infra,
frakció-valuta sink.
**Buktatók:** Nagy világépítési munka (nem plugin-kód); a heti lockout PDC-logikát csoportra
(party) kell számolni, nem csak egyénre, különben a csapatból egy tag zárva marad.
### B15. Heti krónika (auto-újság) `[KÉSZ ✅]`

> **Lore-horgony:** a kódexet a Bankárszövetség krónikásai írják — a heti újság ugyanez a hang, élőben (kódex VIII.)

**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Hét végén auto-generált könyv-item/chat-összefoglaló a hét eseményeiről.
**Hogyan működne:** Vasárnap éjfélkor egy globális scheduler tick összeszedi a
`StatsManager` heti top-adatait (raid-eredmény, szezon-állás, legnagyobb piaci üzlet,
körözöttek) egy sablon-szövegbe, ami `written_book` itemként kerül a fővárosi hirdetőtáblára
(D2) és broadcast chat-üzenetként is kimegy.
**Miért jó:** A meglévő statokból szinte ingyen összerakható, és a szerver „élő világ"
érzését erősíti — a játékosok visszaolvashatják, mi történt, míg ők offline voltak.
**Építőkövek:** `StatsManager`, globális scheduler tick, `written_book` item, hirdetőtábla (D2).
**Buktatók:** A sablon-szöveg gépi íze könnyen szárazra sikerülhet — pár változatos
mondat-sablon kell a monotónia elkerülésére.
### D18. A 4 frakció lore-jának ingame felszíne `[KÉSZ ✅]`

> **Lore-horgony:** a kódex ingame-esítése: lore-könyvek, NPC-szövegek, feliratok

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A frakciók (RED/BLUE/NEUTRAL/DARK) háttértörténete ne csak a guide-ban létezzen, hanem apró, world-based nyomokban is felbukkanjon.
**Hogyan működne:** Minden frakció fővárosában 2-3 admin-elhelyezett „lore-pont” (tábla-blokk, könyv-item egy polcon, vagy NPC dialógus-ág a FancyNpcs-hídon át) meséli a frakció eredetét/etikáját — a szöveg `messages.yml` dedikált `faction-lore.<faction>.<n>` kulcs-készletéből, amit az A31 frakció-infó oldal linkelhet. Kiegészítésként `/lore <frakció>` parancs ugyanezt chatbe írja azoknak, akik nem tudnak odautazni.
**Miért jó:** A frakció-választás (ma inkább mechanikai: passzívák + szín) érzelmi/narratív súlyt kap.
**Építőkövek:** A31 frakció-infó oldal, `MessageManager` kulcs-minta, `FancyNpcsQuestBridge` dialógus-ág.
**Buktatók:** A fő munka tartalom-írás, nem kód; a 4 frakció lore-mennyisége maradjon kiegyensúlyozott.

### I14. Szakma-címke az itemen — „Készítette: X" `[KÉSZ ✅]`

> **Lore-horgony:** szó szerinti kánon-mondat: „a mester keze alól kikerülő mű a nevét is viseli" (kódex VIII. — A Tárgyak Lelke)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Minden szakma-craftolt tárgy PDC-ben és a lore-ban megkapja a készítő nevét (és opcionálisan a craftolás idejét).
**Hogyan működne:** A `ProfessionRecipeManager` craft-végrehajtás végén egy `crafted_by`/`crafted_at` PDC-pár kerül az itemre, a lore-ba egy halvány szürke sor („Készítette: Anna"). A piacon (`MarketGUI`) ez a sor megmarad, tehát a hírneves craftolók neve „márkajelzésként" utazik a tárggyal.
**Miért jó:** Presztízs-mechanika pénz-semlegesen: a jó hírű szakma-mesterek tárgyai felismerhetők és keresettebbek lesznek a piacon — szociális dimenziót ad a craftolásnak, ami eddig névtelen volt.
**Építőkövek:** `ProfessionRecipeManager`, ItemFactory-minta (PDC+lore), `MarketManager` (megjelenítés).
**Buktatók:** A régi (címke nélküli) tárgyakkal való visszamenőleges kompatibilitás — null-biztos lore-építés, ne dobjon hibát a hiányzó PDC-mezőn.

## Tier A — erős illeszkedés
### B33. Szezonzáró világesemény („végítélet-hét") `[KÉSZ ✅]`

> **Lore-horgony:** a Korszakok Könyve lapfordulása + a fél-álom Királynő nyugtalansága (kódex VII./VIII.)

**Munka:** 🟡 • **Érték:** ⭐⭐⭐

> **Megvalósítva:** `SeasonFinaleManager` (PersistentStore, season-finale.yml) — a szezon
> utolsó `days` (7) napjában napi eszkaláció: esély-szorzó a vérhold/világboss/invázió
> chance-kulcsára, invázió mob-szint bónusz, liga-pont szorzó lineárisan ×2-ig; napváltás
> lore-broadcast. Utolsó nap: egyszeri Szezonboss („A Lapforduló Őre") a NEUTRAL főváros
> pereménél (WorldBossManager.forceFinaleSpawn — guard-bypass, emelt élet, finálé-PDC),
> halálakor egyedi loot-tábla (LootTable `unique:` sor-támogatással) + extra liga-pont.
> Config: `world-events.season-finale.*` (élőben olvasva).

**Mi ez:** A szezon utolsó hetében eszkalálódó modifikátorok, az utolsó napon szerver-boss
a fővárosnál.
**Hogyan működne:** A szezon-scheduler utolsó 7 napjában egy `SeasonFinaleManager` a
meglévő esemény-managerek súlyait/gyakoriságát fokozatosan emeli (sűrűbb vérhold,
erősebb invázió, dupla liga-pont — config-táblázat naponkénti eszkalációval); az utolsó
napon egy egyszeri, globálisan broadcastolt szerver-boss spawn a fővárosnál (világboss-
archetípus infra, egyedi loot-tábla).
**Miért jó:** A szezonoknak drámai ívet ad — nem csak „lejár a liga", hanem érezhetően
felfut a világ a záráshoz, ami a résztvevők számára emlékezetes pillanatot teremt.
**Építőkövek:** Szezon-scheduler, esemény-managerek súly-rendszere, világboss-archetípus infra.
**Buktatók:** Az eszkaláció ne váljon já(rha)tatlanná a gyengébb frakciónak — a boss-nehézség
és a modifikátorok ne büntessék túl azt, aki lemaradt a szezonban.
### J9. Fejezet-rendszer (szezononkénti story-ív)

> **Lore-horgony:** a korszakok = fejezetek — a szezon-story-ív kánonja készen áll (kódex VIII.)

🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** A `next`-láncolást egy magasabb szintű "fejezet" fogalom fogja össze: minden
szezon egy story-ívet nyit (a `SeasonManager` szezonváltásához kötve), amiben csak az adott
szezon alatt elérhető quest-láncok futnak.
**Hogyan működne:** `chapter: "szezon3"` mező a questeken; a `QuestManager` elfogadás-
ellenőrzésébe egy `requires-chapter-active` szűrő kerül, ami a `SeasonManager.getCurrentSeasonId()`-
del veti össze. Be nem fejezett fejezet-questek szezonváltáskor archiválódnak (a lezárt
állapot PDC-ben marad, de új fejezet-questek nem nyílnak rájuk).
**Miért jó:** A szezon-rendszernek (király, raid, liga) most nincs NARRATÍV íve — ez adja
meg a "évad-történet" érzést, a heti krónikával (B15) és a szezonzáró eseménnyel (B33)
összefűzve.
**Építőkövek:** `SeasonManager`, `next`-láncolás, `QuestManager` requires-* keret.
**Buktatók:** Nagy tartalom-munka (írás), és a szezonváltáskor futó láncoknál explicit
kell dönteni: törlődik vagy átmenetileg befejezhető marad-e a félbehagyott quest.
### D17. Szezon-átvezető broadcast-történetek `[KÉSZ ✅]`

> **Lore-horgony:** a korszak-átmenetek krónikás-hangú narrálása (kódex VIII.)

**Munka:** 🟢 • **Érték:** ⭐⭐

> **Megvalósítva:** `SeasonStoryTeller` — a SeasonManager szezonzárás-hookja title
> (Lapforduló) + 4-6 késleltetett, variánsos krónikás-sort hirdet (StatsManager top-1
> hősök, bajnok-sor); config: `world-events.season.transition-story.*`, messages-kulcsok:
> `season-story-*`.

**Mi ez:** Szezonváltáskor száraz statisztika helyett rövid, narratív hangvételű broadcast-sorozat vezeti fel az új szezont.
**Hogyan működne:** A szezonzárás hookjához 3-5 üzenetből álló, késleltetett (`runDelayed` a globális scheduleren) szöveg-sorozat kötődik — az előző szezon eseményeiből (StatsManager top, B15 krónika, győztes frakció) sablon-szöveggel generált „korszakváltás” narratíva (`messages.yml` season-intro sablonok), a bemutató (intro) cím-szekvencia vizuális stílusával (title/subtitle + chat).
**Miért jó:** A szezon-ciklus (ma csak pontszám-reset) valódi „fejezetváltásnak” érződik — történet-ívet ad egy visszaszámláló számnak.
**Építőkövek:** Szezonzárás-hook, B15 heti krónika adatforrás, meglévő intro cím-szekvencia.
**Buktatók:** Ne legyen blokkoló/hosszú (title-sorozat, nem kényszerített várakozás); több sablon-variáns kell az ismétlődés ellen.
### D19. Rejtélyes idegen NPC ritka felbukkanása `[KÉSZ ✅]`

> **Lore-horgony:** kész K9-horgony: a Suttogók toborzója vagy az Első Csend hírnöke (kódex VII.)

**Munka:** 🟢 • **Érték:** ⭐⭐

> **Megvalósítva:** `StrangerNpcManager` + `StrangerListener` — néma/sérthetetlen
> WANDERING_TRADER („Az Idegen”), talányos sor a közelieknek, lélek-köddel despawn;
> jutalom nélkül (szándékosan). Admin: `/events stranger`; config:
> `world-events.stranger-npc.*`; spawn-rules mátrix-sor: `stranger`.

**Mi ez:** Egy név nélküli, kapucnis „Idegen” NPC, aki ritkán, véletlen helyen/időben feltűnik, mond egy talányos sort, majd eltűnik.
**Hogyan működne:** Alacsony esély (`stranger-npc.chance-percent`, az `AmbientEventManager` esély-mintájával) triggerkor egy FancyNpcs-NPC spawnol véletlen, spawntól távoli, játékos-közeli koordinátán (a világboss spawn-választás mintájára), rövid, több-variánsos, talányos sort mond, majd `despawn-seconds` múlva particle-lel eltűnik. Nincs mechanikai jutalom — se kereskedő (B40), se questadó, tisztán atmoszférikus.
**Miért jó:** A világ rejtélyt hordoz — a közösség „láttátok az Idegent?” beszélgetései admin-kontroll nélküli, szabad közösségi tartalmat generálnak.
**Építőkövek:** `AmbientEventManager` esély/trigger-minta, `FancyNpcsQuestBridge` NPC-spawn, világboss spawn-választás.
**Buktatók:** Mechanikai jutalom TILOS hozzá (elveszti a rejtély-jelleget); túl gyakori feltűnés lerontja a ritkaság-érzetet.
### D9. Énekmondó NPC (generált balladák) `[KÉSZ ✅]`

> **Lore-horgony:** balladák az Elveszett Uralkodókról és a Vérháborúkról (kódex-függelékek)

**Munka:** 🟢 • **Érték:** ⭐

> **Megvalósítva:** `BardManager` — a FancyNpcs interact-hook a `bard.npc-name` NPC-re
> jobb-kattkor heti balladát énekel (StatsManager top-1 szint/vagyon/raid, epoch-hét
> alapján determinisztikus variánsok); messages-kulcsok: `bard-*`.

**Mi ez:** A fővárosi bárd hetente a top-játékosokról „énekel” a statisztikák alapján.
**Hogyan működne:** Heti tick (a szezon-scheduler mintájára) lekérdezi a `StatsManager` top-3 kategóriáját (K/D, mob-ölés, quest — A15/A22), és sablon-szöveg-készletből (`messages.yml`, `{player}`/`{stat}` placeholder) összeállít 2-3 sort, amit a bárd NPC dialógusból (jobb-katt) vagy periodikus chat-buborékból megjelenít. A heti krónikával (B15) közös adatforrás, külön prezentáció.
**Miért jó:** A száraz ranglista-számokból narratívát csinál — vicces, megosztható pillanat, gyakorlatilag ingyen alapanyagból.
**Építőkövek:** `StatsManager` top-lekérdezés, `FancyNpcsQuestBridge` dialógus, B15 heti krónika.
**Buktatók:** A sablon-szöveg ne ismétlődjön gépiesen — kategóriánként több variáns kell.
### D15. Tábortűz-mesélés XP-vel `[KÉSZ ✅]`

> **Lore-horgony:** a kódex szájhagyomány-felülete — tábortűz melletti mesélés

**Munka:** 🟢 • **Érték:** ⭐

> **Megvalósítva:** `CampfireStoryListener` — SNEAK+jobb-katt, hold-seconds kitartás a
> radius-on belül → sztori-sor (6 beépített variáns / `stories` lista) + xp-reward + FX;
> PDC-cooldown csak sikernél. Config: `campfire-story.*` (general.yml).

**Mi ez:** Tábortűz mellé ülve (sneak+jobb-katt) rövid, ismétlődő „mesélés” mikro-esemény kevés XP-vel.
**Hogyan működne:** A listener figyeli a tábortűz sneak+jobb-kattot; ha a játékos `campfire-story.hold-seconds` ideig `campfire-story.radius`-on belül marad (mozgás megszakítja), rövid partikel/hang-jelenet (`SOUL_FIRE_FLAME` + halk `AMBIENT_CAVE`) után kis XP-jutalom (`campfire-story.xp-reward`) és véletlen sztori-sor (config-lista, D18 frakció-lore-ból is meríthet). PDC cooldown (`cd_campfire_story`) az AFK-farm ellen.
**Miért jó:** A dekoratív tábortűz köré harc nélküli, közösségi ritmust ad (több játékos üljön egy tűz köré) — alacsony tétű „élj a világban” tartalom.
**Építőkövek:** `PlayerInteractListener` minta, PDC-cooldown minta (spell-cooldownok), D18 lore-szövegkészlet.
**Buktatók:** Cooldown/hold-seconds nélkül AFK-bot-farmolható XP-forrás lenne — alacsony összeg, hosszú cooldown kötelező.
### B26. Rúna-kovácsolás (enchant-kiegészítő szakma-ág)

> **Lore-horgony:** Caldestera elveszett rúna-tudása + a Mélység Népe, a világ első kovácsai (kódex I./VI.)

**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** Rúna-itemek craftolása ritka anyagokból, felhelyezés fegyverre/páncélra kis
tematikus bónuszokkal.
**Hogyan működne:** A rúna egy `ItemFactory`-mintás PDC-item (`RuneItemFactory`), a
recept-katalógusba a Kovács/Varázsló ág 25+ szintű recepteként kerül; a „felhelyezés"
egy anvil-szerű GUI-lépés, ami a célfegyver PDC-jébe ír egy `rune_effect` mezőt (a
spell-effekt logika ezt olvassa cast/találat idején kis, additív szorzóként).
**Miért jó:** A talent/mastery mellé egy harmadik, ITEM-oldali progressziós tengelyt ad —
loot-izgalmat a raritás-rendszer fölé, mert a rúna FÜGGETLEN a fegyver raritásától.
**Építőkövek:** `ItemFactory`-minta, recept-katalógus, spell-effekt hook-pontok.
**Buktatók:** A rúna-hatásokat spellenként kell integrálni (nagy felületű változtatás) —
érdemes egy szűk, közös hook-ponton (pl. `SpellCastContext` módosító lista) átvezetni,
ne szórtan minden spell-osztályban.
### B35. Céhek (frakción belüli kisközösségek)

> **Lore-horgony:** a VIII. fejezet Céh-öröksége — a Felsők újraélesztik a letűnt céheket

**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** 5-15 fős céh saját névvel, kasszával, céh-questekkel és céh-szintlépéssel — a
frakció (50+) és a party (5) közti köztes réteg.
**Hogyan működne:** `GuildManager` a `PartyManager` perzisztens, kibővített testvére:
`/guild create <név>` (frakción belül, tagok csak azonos frakcióból), saját `YamlStore`-
perzisztált kassza (bank-infra), céh-szintű közösségi cél-questek (a meglévő közösségi
cél-minta céh-scope-ra szűkítve), céh-szintlépés (tag-aktivitás összesítéséből XP).
**Miért jó:** A közösségi kohézió legerősebb eszköze — az 50+ fős frakció túl nagy az
igazi közösségérzethez, az 5 fős party túl kicsi a tartós szervezethez; a céh a kettő közt hidal.
**Építőkövek:** `PartyManager` minta, közösségi cél-infra, bank/kassza-infra, `YamlStore`.
**Buktatók:** Nagy munka (új manager + GUI + perzisztencia + rang-rendszer réteg) — érdemes
a party-infra közvetlen kiterjesztéseként indulni, ne nulláról.
### B54. Elátkozott felszerelés (kockázatos erő)

> **Lore-horgony:** az Ócska-átok és az Első Csend-érintette tárgyak motívuma (kódex I. + item-rarity)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Ritka, erős tárgyak egy csoportja, amik felvétel után nem vehetők le szabadon
(egy rituálé/quest kell a „megtöréshez"), cserébe komoly bónuszt adnak.
**Hogyan működne:** A raritás-létra egy „Átkozott" al-kategóriája (a loot-tábla ritka
ágán, mob-loot vagy világboss-forrásból); az item PDC-je egy `cursed: true` flaget hordoz,
amit egy `PlayerArmorChangeEvent`/inventory-listener figyel — levételi kísérletnél
visszahelyezi, hacsak a játékos nincs a rituálé-oltárnál (a meglévő Feloldozás-szolgáltatás
mintájára egy „Átok-törés" opcióval).
**Miért jó:** Tematikus, önkéntes kockázat-vállalás — a nagy erő ára az elköteleződés,
ami izgalmas döntési helyzetet teremt anélkül, hogy PvP-t vagy gazdaságot érintene.
**Építőkövek:** Raritás/loot-rendszer, rituálé-oltár Feloldozás-szolgáltatás mintája,
inventory-listener.
**Buktatók:** A kényszerített viselet UX-szempontból frusztráló lehet, ha a játékos nem
érti előre a szabályt — egyértelmű lore-szöveg és felvétel előtti megerősítő GUI-prompt kötelező.

### A38. Első belépés spawn-élmény polish

> **Lore-horgony:** a Szent Zóna-ébredés + a zarándoklat élménye (kódex V.)

🟢 • ⭐⭐

**Mi ez:** Az onboarding-lánc (A5) mellé a tényleges spawn-pillanat vizuális/hangi csiszolása.
**Hogyan működne:** Join-kor rövid title/subtitle üdvözlés + halk hangjel (nem broadcast-
szintű), a semleges főváros látványos pontján spawnoltatva (`teleportAsync`); az üdvözlő
üzenet configolható (`messages.yml` → `join-welcome-title`).
**Miért jó:** Az MMO-szerverek első benyomása sokat számít — egy 2 másodperces title-élmény
olcsó, de emlékezetes belépő.
**Építőkövek:** join-listener, `teleportAsync`, `MessageManager`.
**Buktatók:** ne ütközzön az onboarding-quest üzenetével — az időzítést egyeztetni kell, hogy
ne torlódjon két üdvözlés egyszerre.
### B6. Játékos-indított karaván (szállítmány-rablás)

> **Lore-horgony:** a vándorkaraván kánonja — „védelmük a Felsők becsülete" — játékos-karavánra kiterjesztve (kódex VIII.)

**Munka:** 🔴 • **Érték:** ⭐⭐

**Mi ez:** A karaván-esemény player-verziója: frakció indít szállítmányt A→B kasszából
finanszírozva, célba érve bónusz, útközben rabolható.
**Hogyan működne:** `/faction caravan send <cél>` (király/megbízott jog) a meglévő karaván-
esemény entitás-mozgás logikáját (escort-konvoj útpontjai) indítja frakció-kasszából fizetett
áruval; más frakció tagjai a raid-jelentkezés mintájára „lesből" jelentkezhetnek egy
rablás-ablakra. Sikeres védés = célba ér + kassza-bónusz, sikeres rablás = a rakomány a
rabló frakció kasszájába megy.
**Miért jó:** Kockázat/jutalom gazdasági minijáték, ami PvP-tartalmat termel frakciók között
anélkül, hogy formális hadüzenet kellene hozzá.
**Építőkövek:** Karaván-esemény konvoj-mozgás, raid-jelentkezés infra, `FactionManager` kassza.
**Buktatók:** Az útvonal-számítás (pathfinding konvojnak) régió-lokálisan kis lépésekben
menjen (Folia blokk-szkennelés szabály), és a rablás-ablak ne legyen kihasználható
off-time időzítéssel (ld. B30 háború-ablak elve alkalmazható ide is).
### B19. Évszakos világ-modifikátorok

> **Lore-horgony:** az Égi Jelek + a gyógyuló Fa / a Királynő álmának ciklusai évszak-modifikátorként (kódex V./VII./VIII.)

**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A szezonhoz kötött finom világ-hangolás: télen gyakoribb fagy-események, nyáron
Bőség-idő gyakoribb.
**Hogyan működne:** `config/season-modifiers.yml`: szezononként szorzó-táblázat az esemény-
managerek véletlen-súlyaira (a meglévő esemény-súly rendszer olvassa ki egy plusz
szezon-szorzót). Nincs új esemény, csak a meglévők gyakoriság-hangolása.
**Miért jó:** Olcsó „élő világ" réteg — a szezon nemcsak liga-pontban, hanem a világ
hangulatában is érezhető.
**Építőkövek:** Esemény-managerek súlyozott sorsolása, szezonliga-scheduler.
**Buktatók:** Csak finomhangolás, nem új tartalom — a hatás könnyen észrevehetetlen marad,
ha a szorzók túl kicsik.
### B21. Bestiárium / gyűjtő-album

> **Lore-horgony:** a krónikás-lajstrom műfaj (kódex-függelékek) bestiárium-formában

**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Perzisztens album: megölt mob-típusok, elkészített receptek, felfedezett
territóriumok, legyőzött boss-archetípusok pipálódnak.
**Hogyan működne:** `/bestiary` GUI (statikus GUI-minta), a haladás `StatsManager`
bővítéseként PDC-számlálók (mob-típusonként első kill dátuma, recept-katalógus első
craft flag, territórium first-entry flag). Mérföldkő-jutalmak (10/50/100 faj) az
achievement-infrán, broadcast + krónika-bejegyzés (B15) nagy mérföldköveknél.
**Miért jó:** Gyűjtögető-hajlamú játékosnak hónapokra ad célt, és szinte az ÖSSZES
meglévő rendszert (mob, szakma, territórium, boss) egyetlen progressziós UI-ba fűzi össze.
**Építőkövek:** `StatsManager` PDC-számlálók, GUI-minta, achievement-infra, heti krónika (B15).
**Buktatók:** Sok apró hook-pontot igényel (minden mob-típus, minden recept, minden
territórium bejárása) — inkrementálisan, kategóriánként érdemes bevezetni.
### D3. Szezon-emlékművek

> **Lore-horgony:** a Korszakok Könyve fizikai emlékművei — „neve örökre bekerül" (kódex VIII.)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Szezonzáráskor a győztes frakció fizikai nyoma a fővárosban (zászló/szobor + névtábla).
**Hogyan működne:** A szezonváltás-hookban (liga-lezárás) egy admin-előkészített koordinátán (`season-monument.location`) automatikusan cserélődik egy fej-blokk/banner a győztes frakció színére, mellé `TextDisplay`-hologram íródik a szezon számával és a `StatsManager` MVP-top 3 nevével. A korábbi emlékmű nem törlődik, a lista alul bővül (vagy a D10 vitrinbe kerül).
**Miért jó:** A dicsőség fizikai, maradandó nyoma a világban — a következő szezon kezdetén mindenki látja, mit kell legyőznie.
**Építőkövek:** `StatsManager` top-lekérdezés, `TextDisplay`-minta, szezon-lezárás hook.
**Buktatók:** Az admin-előkészítés (helyszín + build) kézi munka; kódból csak a tábla-csere/hologram-frissítés megy.
### D8. Felfedezhető titkos helyek

> **Lore-horgony:** a Mélység Népe romjai + a világ rejtett zugai (kódex I.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Admin-kijelölt rejtett pontok, amiket először megtaláló játékos felfedezés-jutalmat kap.
**Hogyan működne:** Config-listában koordináta + sugár + jutalom (`hidden-spots.<id>.location/radius/reward`); alacsony gyakoriságú, throttle-olt proximity-check (player move eventen vagy periodikus, kis-sugarú scan a territórium chunk-index mintájára) észleli az első belépőt, PDC/YAML `discovered-by` zárja le (`hidden-spots.first-finder-only` configolható). Jutalom: token/XP + bestiárium-bejegyzés (B21), ritkán titkos kereskedő nyit (B40).
**Miért jó:** A kézzel épített world-building végre játékmechanikai súlyt kap — a felfedezés önálló játékstílussá válik.
**Építőkövek:** Territórium chunk-index minta, B21 bestiárium-infra, B40 titkos kereskedő.
**Buktatók:** A proximity-check ne fusson minden tick minden játékosra — throttle és kis sugár kötelező a teljesítmény miatt.
### E1. Nekromanta: lélek-kovácsolás

> **Lore-horgony:** a lélek-kánon: a holtidéző az élők lelkét aratja (kódex VII.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A lélekszilánk-gyűjtés (`SoulShardManager`) kapjon tartós, presztízs-jellegű befektetési célt a Nekromanta minion-seregéhez.

**Hogyan működne:** `/soulforge` GUI-ban 3 fejleszthető ág (Élet/Sebzés/Létszám), áganként 5 rang, rangonként növekvő lélekszilánk-ár (pl. 5→8→12→18→25), minden 3. Létszám-rang +1 minion-slot (max +3 az alap fölé). A rangok egy player-store-ban (a `MinionManager` mellett) tárolódnak; cast-kor a `SummonMinionsSpell` ebből olvassa a stat-szorzókat, a spawnolt minion attribútum-módosítóit a saját entity-scheduleren állítja be (Folia). Max rang elérésekor egyedi minion-CMD-skin (A33/B9 iránnyal összekötve).

**Miért jó:** a Nekromanta végjáték-identitása lesz, hogy a serege fizikailag látszik nőni a szezonnal — a niche, unlock-feltételes (Sötét frakció + bűnös) úthoz így súlyos jutalom társul.

**Építőkövek:** `SoulShardManager`, `MinionManager`, `SummonMinionsSpell` (minion-minta).

**Buktatók:** a végtelenül skálázódó sereg PvP-ben balansztörő lehet — rang-cap és PvP-zónákban (arénák, B41 liga) minion-létszám-limit kell.
### E7. Varázsló: rúnaíró affinitás

> **Lore-horgony:** Caldestera elveszett rúna-tudása — a varázsló-forrás (kódex VI.)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A B26 rúnakovácsolás Varázsló-kaszt-exkluzív bónusza — a kaszt „olvassa" a rúnákat.

**Hogyan működne:** Ha egy Varázsló rúnázott fegyvert/páncélt visel, a rúna tematikus bónusza (pl. +2% spell-erő) rá nézve megduplázódik (equip-eventkor PDC-olvasás, ItemFactory rúna-tag alapján), más kaszt csak alap-értéken kapja. Cserébe a Varázsló recept-listájában (`professions.yml`) 1-2 kaszt-exkluzív rúna-recept nyílik (pl. „Mana-visszhang rúna": ritka esély ingyen újracastolásra), amit csak Varázsló olvashat fel sikeresen (recipe-gate a JobType-ra).

**Miért jó:** a rúna-rendszer nem homogenizálja a kasztokat, hanem a Varázsló „mágia mestere" identitását item-oldalon is erősíti.

**Építőkövek:** B26 rúnakovácsolás, ItemFactory PDC-minta, `professions.yml` recept-gate.

**Buktatók:** kaszt-exkluzív item-bónusz könnyen „kötelező pick"-ké válhat — a hatás maradjon kis, additív jellegű.
### E25. Boszorkánymester: rituálé-oltár pakt

> **Lore-horgony:** a rituálé-oltárok törvénye + a Kárhozat-forrás pakt-fantáziája (kódex VI./VIII.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A Boszorkánymester Lélekerő-erőforrása közvetlen kapcsolatba kerül a relikvia-rendszer rituálé-oltárával.

**Hogyan működne:** A rituálé-oltárnál (`relics.yml`) a Boszorkánymester egyszeri, kaszt-exkluzív „pakt"-ceremóniát végezhet: a maximális Lélekerő-poolja tartósan +20%-kal nő (talent max-attribútum mintájú PDC-módosító), cserébe az oltár egy ritka rituálé-alapanyagot fogyaszt, ami kizárólag B47 ereklye-expedíciókból szerezhető.

**Miért jó:** a Lélekerő-rendszer (E5 kockázat/jutalom identitása) konkrét, ritka végjáték-célt kap, összekötve a Boszorkánymestert a rituálé-oltárral és a B47 expedíciós tartalommal.

**Építőkövek:** relics.yml rituálé-oltár, B47 ereklye-expedíciók (alapanyag-forrás), talent max-attribútum minta.

**Buktatók:** tartós, végleges pool-növelés balansz-kockázat — nem halmozható cap kell, és a ritka alapanyag miatt csak keveseknél lesz elérhető (tudatosan vállalt szűkösség végjáték-tartalomként).
### E32. Sárkányidéző: sárkánytojás-relikvia

> **Lore-horgony:** sárkány-eszencia + az Ereklyék Törvénye (kódex VI./VIII.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Kaszt-exkluzív relikvia-típus, ami az Eszencia-rendszerhez kötődik.

**Hogyan működne:** Egy ritka relikvia („Sárkánytojás-töredék", új `relics.yml` bejegyzés) csak Sárkányidéző birtokában aktiválva az Eszencia-skála (E30) mindkét szélső pólusát 10%-kal kiterjeszti (-110..+110), hosszabb ideig fenntartható extrém polaritás-bónuszt engedve. A relikvia birtoklása/aktiválása a meglévő relikvia-szabályokat (cooldown, esetleges PvP-rablás) követi, csak a hatás-branch kaszt-gate-elt (JobType-ellenőrzés a hatás-alkalmazásnál).

**Miért jó:** a relikvia-rendszer konkrét, kaszt-tematikus ágat kap, összekötve az E30/E31 eszencia-identitást a szerver nagy presztízs-tárgyaival — kaszt-specifikus izgalmat ad a relikvia-vadászatnak.

**Építőkövek:** relics.yml relikvia-rendszer, E30 Eszencia-polaritás, JobType-gate a relikvia-effekt-alkalmazásban.

**Buktatók:** a kaszt-exkluzív hatás miatt más kaszt „értéktelennek" találhatja a relikviát, ha megszerzi — érdemes egy kaszt-független alap-hatást is adni neki, a Sárkányidéző-bónusz csak plusz réteg legyen.

---

[← Ötlettár-index](../IDEAS.md)
### F13. Piaci pánik esemény

> **Lore-horgony:** „a pénz értéke a világ állapotát követi" — a pánik mint kánon-esemény (kódex VIII.)

🟡 • ⭐⭐

**Mi ez:** Ritka világesemény, ami egy véletlen valuta árfolyamát átmenetileg leveri — a
meglévő kereslet-sokk (felfelé mozgás) tükörpárja.
**Hogyan működne:** A kereslet-sokk (`ExchangeRateService`, x1,2–1,6 emelés) mellé egy
szimmetrikus „pánik" ág (x0,6–0,8 szorzó) ugyanabból az esemény-ütemezőből, broadcast-
üzenettel („Pánik tört ki a Kék piacon!"). Nincs közvetlen pénzmozgás — csak az árfolyam-
számítás bemenete változik időlegesen, ugyanúgy, mint a meglévő sokknál.
**Miért jó:** A jelenlegi rendszer csak felfelé lő ki — a lefelé mozgás hiánya hosszú távon
csak inflál. A pánik szimmetrikus kockázatot ad, és a spekulánsoknak (F2) is izgalmasabb
terepet teremt.
**Építőkövek:** `ExchangeRateService` kereslet-sokk logikája (tükrözve).
**Buktatók:** Túl gyakori/durva pánik random-frusztrálónak érződhet — kis valószínűség és jól
látható broadcast kell, nehogy „láthatatlan büntetésként" éljék meg.
### F14. Konjunktúra esemény

> **Lore-horgony:** ugyanaz a kánon-mondat — a konjunktúra a jó idők lenyomata (kódex VIII.)

🟢 • ⭐

**Mi ez:** A piaci pánik (F13) pozitív párja: időszakos „fellendülés", amikor egy valutában
ideiglenesen csökken a piaci eladási díj (nem az árfolyam).
**Hogyan működne:** Broadcast-vezérelt időablak (pl. 30 perc), amíg a `MarketManager` adott
valutában kötött eladásainak díja a szokásos 10% helyett 5% — kevesebb pénz ég el, de a
kereskedési kedv nő, mert olcsóbb kereskedni. Az esemény-ütemező (mint a többi világesemény)
sorsolja, admin-beavatkozás nem kell.
**Miért jó:** Aktív kereskedésre ösztönöz konkrét időablakban (mint a kereslet-sokk),
közösségi zajt gerjeszt („most van a fellendülés, adj-vegyél!") — olcsó, tisztán pozitív
hangulati elem.
**Építőkövek:** `MarketManager` díjszámítás, esemény-ütemező minta.
**Buktatók:** Az alacsonyabb díj rövid távon kevesebb sinket jelent — gyakoriságban szigorúan
korlátozva kell tartani, hogy ne törje meg az inflációs egyensúlyt.
### F15. Szezonzáró árfolyam-sokk

> **Lore-horgony:** a Korszakok lapfordulása az árfolyamban (kódex VIII.)

🟢 • ⭐⭐

**Mi ez:** A szezon utolsó napjaiban (a B33 „végítélet-hét" gazdasági rétege) minden valuta
árfolyama felgyorsult ütemben, láthatóan ingadozik.
**Hogyan működne:** `season.closing-days` config alatt az `ExchangeRateService` kereslet-sokk
gyakorisága/amplitúdója megszorzódik (pl. 3×), a fővárosi árfolyamtáblák (hologram) frissítési
gyakorisága is nő. Nincs új pénzforrás — csak a meglévő sokk-mechanika idő-alapú felturbózása.
**Miért jó:** A B33 szezonzáró világeseményhez illő gazdasági dráma-réteg — az utolsó napok
kereskedése emlékezetesebb, aki figyeli a piacot, nagyot nyerhet/veszíthet, ami sztorikat
generál.
**Építőkövek:** `ExchangeRateService`, B33 szezonzáró esemény-ütemezés (előfeltétel).
**Buktatók:** Csak B33 mellett teljes az élménye — önállóan is működik, de a hatás kisebb.
### G6. Becsület-párbaj a bűn tisztítására

> **Lore-horgony:** a bűn/vezeklés-rendszer becsület-útja (kódex VII. — Kitaszítottak)

🟡 • ⭐⭐

**Mi ez:** A bűnös játékos felajánlhat egy „becsület-párbajt" — ha nyer az áldozat ellen (vagy
annak frakciótársa ellen), egy bűnpontja letörlődik.
**Hogyan működne:** `/duel <név> honor` — a B2 duel-infrára épülő új mód: tét helyett a
győztes bűnszámlálója (`SinManager`) -1-et kap, ha a kihívó bűnös és az ellenfél a sértett fél
(vagy annak nevezett képviselője). A kihívás csak akkor ajánlható fel, ha a kihívó bűnszáma
> 0. A meccs maga beleegyezéses (bűnt nem termel, ahogy minden duel).
**Miért jó:** Szimbolikus, RP-s út a bűn csökkentésére a vezeklés-küldetéslánc mellett — a
sértett fél dönthet, elfogadja-e az „elégtételt", ahelyett hogy csak várna a fejvadászokra.
**Építőkövek:** SinManager, B2 duel-infra (escrow nélkül).
**Buktatók:** exploit-veszély, ha a bűnös a saját alt/haverja ellen "veszít" szándékosan
bűntisztázásért — érdemes napi/heti limitet szabni rá.
### G14. Kém-mechanika: álca a LibsDisguises-hídon

> **Lore-horgony:** a Suttogók álca-motívuma nyílt kém-mechanikaként (kódex VII.)

🔴 • ⭐⭐

**Mi ez:** Raid előtt egy speciális itemmel/spellel rövid időre másik frakció tagjának
álcázhatod magad, hogy felderíts (korlátozott, kockázatos taktikai eszköz).
**Hogyan működne:** `/spy disguise <célfrakció>` — a `DruidDisguise`-híd (integration/,
LibsDisguises soft-depend) mintájára egy időkorlátos (pl. 60 mp) álca, ami az álcázott
frakció-nevét/skin-jelzését mutatja MÁS játékosoknak, de a saját HUD-on/tablistán a valódi
adat marad admin/rendszer-oldalon. Korlátok: nem használható aktív raid harci szakaszban
(csak felkészülés/felderítés közben), és PvP-interakció (találat adása/kapása) azonnal
lebuktatja (leveszi az álcát). Bűn-rendszer: az álca alatti kém-tevékenység (pl. területre
belépés) a normál lopás/behatolás szabályai szerint minősül — az álca nem ment fel bűn alól.
**Miért jó:** Roleplay-mély taktikai réteg a szervezett frakcióknak; a kemény korlátok
(nincs harci előny, kill lebuktat) miatt nem lesz PvP-erőforrás, csak információs játék.
**Építőkövek:** integration/DruidDisguise (LibsDisguises-híd), RaidManager fázis-ellenőrzés,
SinManager.
**Buktatók:** komoly félreértés-/panasz-potenciál („becsapott!”) — világos szabály és
UI-jelzés kell, hogy ne érezzék tisztességtelennek; Folia-oldalon a disguise-frissítés
mindig a céljátékos saját régió-szálán fusson.
### I7. Évszakos termények a Bőség-időhöz kötve

> **Lore-horgony:** a Bőség ideje — a Fa gyógyuló lehelete a termésben (kódex VIII.)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A Bőség-idő (a szezon-esemény infra hangulati ablaka) alatt a Gyógynövényész termény-betakarítása extra XP-t/hozamot ad, azon kívül visszaáll az alap ütem.
**Hogyan működne:** A `ProfessionXpListener.onPlayerHarvestBlock`-ban egy config-szorzó (`professions.seasonal.abundance-multiplier`) az aktív világesemény-getterből olvasva (mintázat: E2 druida-hangolódás ugyanezt az eseményt olvassa spell-oldalról). Opcionálisan egy „téli szűkösség" ellentétes irányú szorzó is bevezethető, de a minimum verzió csak a bőség-bónusz.
**Miért jó:** Olcsó „élő világ" réteg, ami a Gyógynövényész-t is bekapcsolja a szezon-ritmusba (eddig csak a druida spellek reagáltak rá) — a gyűjtögető ritmusa illeszkedik a világ ritmusához.
**Építőkövek:** Világesemény-getterek (Bőség-idő állapot), `ProfessionXpListener`, `ConfigManager`.
**Buktatók:** A szorzó csak a HARVEST eseményre hasson, ne a sima blokk-törésre (különben Favágó/Bányász is véletlenül bónuszt kapna).
### I16. Szakma-céh heti közös cél

> **Lore-horgony:** a Céhek Öröksége — céh-közösségi célok (kódex VIII.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Az azonos szakmát űzők (frakciótól függetlenül) egy heti, szakma-szintű közösségi célban vesznek részt („Bányászok együtt: 5000 érc egy hét alatt").
**Hogyan működne:** A meglévő közösségi cél-infra (frakció-szintű mintára) egy GLOBÁLIS, szakma-szűrt számláló-változata: `ProfessionXpListener` minden releváns eseménynél hozzáad egy megosztott heti számlálóhoz, ha a játékos az adott szakmát űzi. Cél elérésekor mindenki, aki hozzájárult (min. küszöb felett), közös jutalmat kap (XP-bónusz-hét vagy kozmetika). Heti reset a szezon-scheduler mintájára.
**Miért jó:** Frakció-semleges közösségi réteget ad a szakma-rendszerhez — a Bányászok mint „szakma-közösség" először kapnak közös identitást és célt a frakciós struktúra felett, ami a heti visszatérést erősíti (B1 párja szakma-oldalon).
**Építőkövek:** Közösségi cél-infra, `ProfessionXpListener`, `ProfessionManager` (aktív szakma-szűrés).
**Buktatók:** A globális számláló több régió-szálról is íródik — konkurrens/atomic számláló kell (a `QuestManager.customQuests` copy-on-write mintája vagy synchronized long).
### I22. Ritka recept-lapok loot-forrásból

> **Lore-horgony:** „tervrajzokat ment ki a romok közül" — a kánon-mondat kiterjesztése recept-lapokra (kódex VIII.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A meglévő tervrajz (Knowledge Book) rendszer bővítése: a legritkább recepteket KIZÁRÓLAG világboss/nehéz esemény loot adja, sosem NPC-bolt.
**Hogyan működne:** A `ProfessionRecipeCatalog`-ban egy új `loot-only: true` mező a csúcs-recepteknél (pl. Ereklye-szintű mestermunkák), amit a világboss/esemény loot-tábla bővítése ad ki (a guide már említi az „Ősi Ereklyeszilánk" boss-only alapanyagot — ugyanez az elv, de magára a RECEPTRE). A recept-könyvben zárolt sorként látszik, lore: „Csak legendás ellenfelektől szerezhető".
**Miért jó:** A világbossokat/nehéz eseményeket a szakma-progresszió szempontjából is tétre teszi (eddig csak felszerelés-loot forrás volt), és a legritkább recepteknek presztízs-értéket ad — nem lehet pénzért megvenni.
**Építőkövek:** `ProfessionRecipeCatalog`, világboss/esemény loot-tábla, `ItemRarityService` (boss tier).
**Buktatók:** Legyen elég recept ÉS elég gyakori a boss-esemény, hogy ne érezzék elérhetetlennek — a C1 spell-statisztika mintájára érdemes loot-drop számlálót is vezetni (mennyi tervrajz esett/hét).


## Tier B — határesetek

*Gyengébb, de valós kánon-kötés — akkor érdemesek, ha a kapcsolódó kánon-elem (ünnepek,
Vámház-őrség, Korszak-finálé) tartalmat kap.*

### D1. Szezonális ünnepek

> **Lore-horgony:** az Időrend évfordulói kész ünnep-naptár — kánon: kódex VIII. „Az Ünnepek" (Hasadás Napja, Ultimátum Napja, Vérhold-virrasztás, Érkezés Napja)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Naptárhoz kötött vizuális/tartalmi skin a meglévő világeseményekre (október: tök-fejes invázió, december: ajándék-esemény).
**Hogyan működne:** Config dátumtartomány (`seasonal-events.halloween.start: "10-25"` / `.end: "11-02"`), éjféli globális tick ellenőrzi az aktuális naptári dátumot, és ha aktív egy ablak, felülírja futásidőben az Invázió/Vérhold/Kincs-esemény paramétereit (mob-skin CMD-vel, vérhold broadcast-szöveg „rém-éj”-re, decemberi ajándék-láda loot-tábla) — ugyanaz az `AmbientEventManager`/`InvasionManager` hívási lánc, csak paraméterezve, nem új rendszer.
**Miért jó:** A már ismert eseményeket (vérhold, invázió) új köntösben adja vissza — a valós naptárhoz igazodás azonnali „most van október!” él-a-világ érzetet kelt kis munkából.
**Építőkövek:** `AmbientEventManager`, `InvasionManager`, CMD-skin infra (A33).
**Buktatók:** Az időzóna-függő dátum-logika legyen konzisztens; a felülírás csak runtime-paraméter maradjon, ne írja át tartósan a configot.
### D11. Járőröző városi őrség

> **Lore-horgony:** a Vámház őrei kánon (kódex VII.) — a járőröző őrség csak megtestesíti

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** FancyNpcs-alapú őr-NPC-k, amik előre megadott útvonalon körbejárnak a fővárosban.
**Hogyan működne:** Config-listában waypoint-koordináták NPC-nként (`city-guards.<id>.route`); periodikus (pl. 2 mp-enkénti) léptetés a következő ponthoz mindig az adott NPC-entitás SAJÁT régió-szálán (`entity.getScheduler().run(...)`), mert az útvonal több chunk-régiót is átszelhet. Éjjel/vérhold alatt sűrűbb léptetés + fáklya-tartás, nappal lassabb „őrjárat” tempó; közeli PvP-riasztás/raid esetén rövid figyelmeztető sor (D7 infra kiterjesztése).
**Miért jó:** A fővárosban nem csak kereskedő- és quest-NPC-k állnak — az őrjárat mozgása azt sugallja, hogy a város él és véd, erősítve a frakciós hovatartozás-érzést.
**Építőkövek:** `FancyNpcsQuestBridge`, D7 napszak-üzenet infra, entity-scheduler minta.
**Buktatók:** A waypoint-lépés MINDIG az NPC saját régió-szálán fusson; sok NPC * sűrű tick teljesítmény-költség, throttle kell.
### G16. Nagydöntő: top2 frakció szezon-hétvégéje `[TOP]`

> **Lore-horgony:** „a korszak végén a krónikák ítélnek" — a Korszakok Könyve versenyfináléja (kódex VIII.)

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
### J7. Rejtvény-küldetések (koordináta-nyomok / jelkép-keresés)

> **Lore-horgony:** a lore-nyomok természetes hordozója — Mélység Népe-feliratok, kódex-utalások (kódex I.)

🟡 • **Érték:** ⭐⭐

**Mi ez:** SEQUENCE-láncú küldetés, ahol az egyes lépések nem nyíltan adott koordinátát,
hanem egy rejtvényt/nyomot mutatnak (dialógus-szöveg, tábla, jelkép-item), és a játékosnak
kell kitalálnia a következő helyszínt.
**Hogyan működne:** Nem igényel új objektíva-típust — a meglévő VISIT_TERRITORY/TALK_TO_NPC
láncot SEQUENCE módban, `dialogue.give` szövegben rejtett nyommal ("a törött torony
árnyékában, ahol a nap delelőn áll…") és opcionális `objective.description` felülírással,
ami NEM árulja el a pontos célt (csak a HUD-on "???" jelenik meg, amíg oda nem érsz). Az
admin quest-builder GUI-ba egy "rejtett leírás" checkbox kerülhet.
**Miért jó:** A story-orientált játékosoknak felfedezés-élményt ad a világ tényleges
bejárásával, nem csak a HUD-nyíl követésével — jól illik a titkos helyekhez (D8).
**Építőkövek:** meglévő SEQUENCE-lánc + dialógus-mező, `QuestBuilderGUI`.
**Buktatók:** Túl nehéz rejtvénynél a játékos elakad és feladja a láncot — legyen egy
opcionális "súgás" fokozat (pl. 10 perc után a HUD mégis megmutatja a célt).
### F11. Ereklye-szilánk börze

> **Lore-horgony:** a Fekete Villám Szilánk kereskedelme — kánon-áru a lélekkő mellett (kódex VII.)

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
### H14. Ritka spawn-variánsok (albínó/árnyék mobok)

> **Lore-horgony:** az Első Csend-érintette lények — a misztérium ritka, néma hírnökei (kódex I.)

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

---

**Összesen: 49 tétel** (K: 10, S: 6, A: 27, B: 6). Ajánlott kezdés: **H2 + B42** (a gyógyuló Fa és a Mélység Népe azonnal játszhatóvá válik), majd **B15** (a krónikás-hang életben tartása).
