# K) Lore-integráció / világ-tartalom

A [docs/LORE.md](../LORE.md) kanonikus történetének **beépítése a meglévő rendszerekbe** — identitás
(nevek, valuták), lore-hű unique-itemek a kész unique-item/raritás/crafting-gate motorra, és a nagy
világ-rendszerek (Nether-portál zóna, Emlékszilánkok, Suttogók-szekta, feketepiac).

Jelölés: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) • `[TOP]` = ajánlott.

> **Kulcs-előny:** a 4 frakció-passzív (RED=tűz, BLUE=fagy, NEUTRAL=békés/adómentes, DARK=wither/
> élőhalott-béke) **már a lore szerint működik** — ezek a tételek nagyrészt „felöltöztetés" és
> tartalom, nem rendszer-átépítés.

---

## K1. Frakció- és valuta-identitás reskin `[TOP]`
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

## K2. Cryghaliris signature unique-itemek `[TOP]`
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

## K3. Perinfernicitas signature unique-itemek
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

## K4. Ryanora & Caldestera signature itemek (gyűjtő/gazdaság)
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

## K5. Káoszkor / Néma Királynő élőhalott-loot
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

## K6. Frakció-ételek és fogyasztási kötelezettség
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

## K7. Kárhozat Kapuja — Nether-portál PvPvE senkiföldje `[TOP]`
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

## K8. Emlékszilánkok — a Felsők emlék-progressziója
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

## K9. A Suttogók — a rejtett hálózat és a Kitaszítottak (hibrid)
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

## K10. Caldestera feketepiac — csempészet és rejtett fegyverek
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

---

**Összesen: 10 tétel (K1–K10).** Ajánlott kezdés: **K1** (reskin, olcsó identitás) → **K2/K3/K5**
(signature itemek a kész motorra) → **K7** (Nether-zóna) → a nagy RP-rendszerek (K8/K9).
