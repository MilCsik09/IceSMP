# B) Új mechanika

[← Ötlettár-index](../IDEAS.md)

Jelölés minden tételnél: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) • `[TOP]` = ajánlott következő kör.

---

### B1. Heti Királyi Megbízások (battlepass-lite) `[TOP]`
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Heti 5–7 frakció-onkénti feladatlista, pontokért kassza-jutalom + a legaktívabb
tagoknak buff/kozmetika hét végén.
**Hogyan működne:** A quest-keretrendszer ismétlődő/szezonális objektíva-típusait használja
egy `/menu` → Frakció almenü „Heti Megbízás" fülhöz kötve; a feladatlista `config/weekly-missions.yml`-ből
sorsolódik frakciónként (súlyozott pool), a haladás a meglévő `QuestManager` számlálóin
fut. Heti reset a szezon-scheduler mintájára (globális region scheduler tick, vasárnap
éjfél). A pontszám PDC-ben tárolódik játékosonként, a heti záráskor a top N tag kap
buffot/kozmetikát, a kassza a `FactionManager` egyenlegébe kap egy tétel jóváírást.
**Miért jó:** Heti visszatérési ok, ami nem a napi grindet, hanem a közösségi célt jutalmazza;
pénz-semleges (a jutalom forrása a szerver, nem játékos-pénz), így nem inflál.
**Építőkövek:** `QuestManager` ismétlődő questek, közösségi cél-infra, szezon-scheduler minta,
`/menu` Frakció almenü.
**Buktatók:** A feladatlista tartalom-igényes (heti frissítés admin-oldalról), és a
frakció-egyensúlyt figyelni kell, nehogy a nagyobb létszámú frakció mindig nyerjen.

### B2. Duel/párbaj-rendszer téttel
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** `/duel <név> [tét]` — beleegyezéses 1v1, a tét escrow-ba zárva, a győztes viszi.
**Hogyan működne:** Meghívás/elfogadás párbeszéd (party-invite mintájára), mindkét fél tétje
(bank-egyenlegből) a piaci licit-zárolás logikájával escrow-ba kerül a duel indulásakor;
az aréna egy admin-kijelölt `TerritoryManager` zóna raid-területkötés mintájára (belépéskor
teleport, kilépéskor vagy vereségkor auto-kirántás). Győzelemkor a tét 90%-a a győztesé,
10% ELÉG (money sink); teleportálás `teleportAsync`-kal.
**Miért jó:** Kockázatmentes (beleegyezéses) PvP-tartalom komoly téttel, ami versenyszerű
játékosoknak izgalmat ad anélkül, hogy a bűn-rendszert érintené.
**Építőkövek:** Piaci licit-zárolás escrow-mintája, raid-terület-kötés, party invite-elfogadás
mintája, `teleportAsync`.
**Buktatók:** Csalás-védelem (afk-lemondás, disconnect a vereség elől) — timeout + auto-vesztés
szabály kell; a tétet a duel közbeni disconnectnél is le kell zárni.

### B3. Kézzel épített dungeonök kulcs-itemmel

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### B4. Pet-képességek
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Petenként egy aktív képesség (farkas: provokál, macska: gyorsítás, bagoly:
éjjellátás-aura), pet-szint kapuval.
**Hogyan működne:** A `PetManager` pet-adatstruktúrája kap egy `ability` mezőt fajtánként,
a spell-infra mintájára cooldownnal (rövid, in-memory `AbilityCatalystListener`-szerű).
Aktiválás jobb-katt a peten vagy `/pet ability`; a hatás a gazda köré/gazdára hat (a
Folia-szabály szerint mindig a pet SAJÁT régió-szálán fut, a gazda hopolással érintve,
ha eltérő régióban van).
**Miért jó:** A pet-tartás ma kozmetikai — egy aktív képesség célt ad a pet-választásnak és
-szintezésnek, kis befektetésből játékérzet-mélyülés.
**Építőkövek:** `PetManager`, spell-cooldown minta, `AbilityCatalystListener`.
**Buktatók:** Az egyensúly kényes — a pet-képesség ne váljon kötelező DPS/utility elemmé,
inkább QoL-jellegű maradjon.

### B5. Területi erőforrás-pontok (elfoglalható bányák)
**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** Kijelölt „lelőhely"-zónák, amikből a birtokos frakció kasszája óránként nyersanyagot/
szakma-XP buffot termel.
**Hogyan működne:** `TerritoryManager` új `RESOURCE_NODE` altípusa (birtoklás = a zóna
tulajdonos-frakciója, ami a legutóbbi sikeres jelentkezés/elfoglalás alapján dől el — nem
harci mechanika, hanem jelenlét-alapú, ld. B48 őrposzt-mintát); óránkénti globális
scheduler tick a `FactionManager` raktárába (B14) ír faucet helyett nyersanyagot vagy
ideiglenes szakma-XP-szorzót a zóna tagjainak.
**Miért jó:** Állandó frakciós motivációt ad a térkép-jelenlétre anyagi/XP haszonnal, pénz-
addolás nélkül (nyersanyag/buff, nem valuta).
**Építőkövek:** `TerritoryManager` zónatípus, `FactionManager` kassza/raktár, globális
region scheduler tick.
**Buktatók:** A birtoklás-eldöntés mechanikáját (jelenlét vs. raid) élesen definiálni kell,
különben vagy senki nem érdekelt, vagy állandó konfliktus-zóna lesz kezelhetetlenül.

### B6. Játékos-indított karaván (szállítmány-rablás)
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

### B7. Frakció-fejlesztési fa (kassza-sink)
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Tartós frakció-fejlesztések vásárlása kasszapénzből: +X% szakma-XP a tagoknak,
olcsóbb claim-oszlop, gyorsabb erőforrás-regen.
**Hogyan működne:** `config/faction-upgrades.yml` fa-struktúra (előfeltétel-lánc + ár), a
király `/faction upgrade buy <id>`-vel vásárol a kasszából (`FactionManager` egyenleg-
levonás), az aktív upgrade-ek egy `Set<String>` PDC/YAML-mezőben (`FactionManager`
`PersistentStore`) tárolódnak, és a `ResourceManager`/`ClaimManager`/`ProfessionManager`
a vásárlás-listát olvassa szorzóként. GUI: `/menu` Frakció → Fejlesztési fa (RUN:-minta).
**Miért jó:** A kasszapénznek végre tartós célja lesz, a királyi döntéseknek tétje —
a frakció progresszió-görbét kap, nem csak passzívákat.
**Építőkövek:** `FactionManager` kassza, `ConfigManager` YAML-fa, `/menu` GUI-minta.
**Buktatók:** A szorzók sok managert érintenek (regen, XP, claim-ár) — mindegyiket egyesével
be kell kötni, és a balansz könnyen elcsúszhat, ha egy frakció mindent kimaxol.

### B8. Natív crate-rendszer
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** CrazyCrates-kiváltás: frakció-valutás kulccsal nyitható láda, `LootTable`-alapú
tartalommal, látványos nyitás-animációval.
**Hogyan működne:** `CrateItemFactory` a kulcs-itemet PDC-taggel gyártja (égetett ár — sink),
a láda-blokk jobb-kattra egy `CrateManager` `LootTable`-ből húz (a meglévő raritás/loot
infra), a nyitás-animáció `TextDisplay`-ekkel forgó/pörgő tárgy-vizualizáció (a
kincs-esemény hologram-mintája). Eredmény: pénz-semleges tárgyjutalom (sosem valuta).
**Miért jó:** Ismerős, „loot box" jellegű dopamin-hurok szerver-saját tartalommal, külső
plugin-függőség nélkül.
**Építőkövek:** `ItemFactory`-minta, `LootTable`, `TextDisplay` animáció, frakció-valuta sink.
**Buktatók:** Ügyelni kell, hogy a tartalom sose legyen pay-to-win érzetű — csak kozmetika/
QoL/loot-rariátás, amit amúgy is meg lehet szerezni farmolással.

### B9. Kozmetikák GUI-ból
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Részecske-nyomok, kalapok, halál-üzenetek valutáért, szezon-jutalmakkal összekötve.
**Hogyan működne:** `/cosmetics` GUI (statikus GUI-minta, dedikált holder), a kiválasztott
kozmetikum PDC-ben (`cosmetic_trail`, `cosmetic_hat`, `cosmetic_deathmsg`); a részecske-
nyom egy alacsony frekvenciájú `entity.getScheduler()` tick a viselő saját régióján. Vásárlás
frakció-valutáért (sink) vagy szezon-győzelemből exkluzívként (PDC-flag, nem eladható).
**Miért jó:** Presztízs-vásárlás pénz-semlegesen (kozmetikum, nem erő) — az „addolt pénz"
elvvel tökéletesen összefér, és a szezon-győzelemnek látható nyoma lesz.
**Építőkövek:** GUI-minta, PDC-tárolás, szezonliga-jutalom infra, frakció-valuta sink.
**Buktatók:** A részecske-effektek ne legyenek zavaróak/lag-forrás nagy csoportos harcban —
sűrűség-limit és config-kapcsoló kell, aki kikapcsolja mások effektjeit.

### B10. Napi vadász-cél (PvE fejvadászat)
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Naponta sorsolt elit mob koordináta-körzettel, első elejtő viszi a lootot.
**Hogyan működne:** A `WildHunt`(`InvasionManager` rokon) infra napi ritmusra általánosítva:
éjfélkor egy globális scheduler tick sorsol egy mob-archetípust + egy koordináta-régiót
(spawn-tól távolság-sávból), broadcast chatben névvel/körzettel. Az első kill-event
jelöli a nyertest, a loot a `LootTable`-ből.
**Miért jó:** Olcsó napi ritmus-tartalom, ami minden bejelentkezéskor ad egy azonnali célt
anélkül, hogy questet kellene felvenni.
**Építőkövek:** `WildHunt`/`InvasionManager` elit-mob logika, globális scheduler tick, `LootTable`.
**Buktatók:** A koordináta-körzet ne legyen túl pontos (célkeresztes odarohanás), inkább egy
nagyobb terület — különben mindig ugyanaz a leggyorsabb játékos viszi.

### B11. Ostromgépek a raidhez
**Munka:** 🔴 • **Érték:** ⭐⭐

**Mi ez:** Telepíthető katapult/ballista raid alatt a támadó oldalnak, ami a védmű-blokkokat
töri.
**Hogyan működne:** A meglévő `SiegeWeaponFactory` kiterjesztése: telepítés csak aktív raid
alatt, a raid-terület-kötésen belül; a lövedék-fizika a meglévő projectile-listener mintáját
követi, a becsapódás a claim-védelem raid-szabályaival egyeztetett blokk-törést okoz (csak
kijelölt „védmű" blokktípusokon, nem terep-griefelés). Remote robbanás `getRegionScheduler()`-en.
**Miért jó:** A raidet vizuálisan és taktikailag epikussá teszi, a védekező oldalnak konkrét,
törhető célpontot ad a puszta „álljunk a spawn-ponton" helyett.
**Építőkövek:** `SiegeWeaponFactory`, raid-terület-kötés, `getRegionScheduler()` remote robbanás.
**Buktatók:** Nagy munka (fizika + blokk-célzás + balansz); a terep-védelem elvét (a
világesemények sosem griefelnek) itt is be kell tartani — csak jelölt védmű-blokk törhető.

### B12. Királyi politika: adó- és kincstár-döntések
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A király sávon belül állíthatja az állampolgári adót, hirdethet kassza-osztalékot,
kiírhat frakció-célt.
**Hogyan működne:** `/faction policy tax <%>` (sávkorlát configból), `/faction policy dividend`
(kassza egyenlő szétosztása online tagok közt), `/faction policy goal <leírás>` a közösségi
cél-infrára kötve. Az adó a meglévő piaci/tranzakciós díj-rétegbe illeszkedik felárként,
`FactionManager`-ben tárolva, minden tranzakciónál olvasva.
**Miért jó:** A királyválasztásnak és a frakció-belpolitikának végre tétje lesz — ma a cím
inkább presztízs, ezzel valódi döntési felelősség.
**Építőkövek:** `FactionManager` kassza, közösségi cél-infra, piaci tranzakciós díj-réteg.
**Buktatók:** Túl magas adó-sáv frusztrálhatja a tagokat — hard cap kell, és az osztalék
kifizetést offline tagokra is méltányosan kezelni (escrow vagy belépéskori jóváírás).

### B13. Piaci vételi megbízások (buy order)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** „Veszek 64 vasat 20-ért" fordított piac: a vevő pénze escrow-ba, bárki teljesítheti.
**Hogyan működne:** `/market buyorder create <item> <db> <ár>` a `MarketManager` listing-
infrájának tükrözött verziója — a vevő egyenlege azonnal escrow-ba zárolódik (mint az
aukció-licit), bárki `/market buyorder fulfill <id>` a tárgyat leadva azonnal megkapja a
zárolt összeget. Lejárat esetén a zárolt pénz visszajár.
**Miért jó:** A gazdaság likviditását növeli — ma csak eladási hirdetés van, a kereslet-oldal
láthatatlan; ez a piac „másik fele".
**Építőkövek:** `MarketManager` listing/escrow-infra, aukció zárolás-minta.
**Buktatók:** Item-ellenőrzés (NBT/enchant-egyezés) a teljesítésnél szigorú kell legyen,
különben silány minőségű tárgy is „teljesíthet" egy pontos leírású megbízást.

### B14. Frakció-raktár (közös láda jogosultságokkal)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Rang-alapú betét/kivét jogú frakció-szintű virtuális raktár naplóval.
**Hogyan működne:** A `DonationChest`-infra általánosítása: `/faction warehouse` GUI
(dedikált holder, slot-jogosultság a frakció-rang alapján), a tartalom `YamlStore.saveAtomic`-
kal frakciónként perzisztálva. Minden be/kivét egy rövid `logs/faction-warehouse.log`
bejegyzést kap (időbélyeg, név, tárgy, mennyiség) — a C7 admin audit-log mintájára.
**Miért jó:** A „ki lopta el a kasszából" drámákat strukturált, visszakövethető mederbe
tereli, és a frakció-tagok közti anyag-megosztást formalizálja.
**Építőkövek:** `DonationChest`-infra, GUI-holder minta, `YamlStore.saveAtomic`, audit-log minta (C7).
**Buktatók:** A jogosultság-szintek (ki vehet ki mennyit naponta) nélkül visszaélhető —
napi kivét-limit rang-onként ajánlott.

### B15. Heti krónika (auto-újság)

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### B16. Mentor-rendszer
**Munka:** 🟡 • **Érték:** ⭐

**Mi ez:** Veterán + új játékos párba áll (`/mentor`), közös questekkel, pénz-semleges
jutalommal mindkettőnek.
**Hogyan működne:** `/mentor offer <név>` / `/mentor accept` a party-invite mintájára; a
párost egy `MentorManager` PDC-flaggel jegyzi, a pár közös teljesítése (pl. 3 közös quest
vagy N közös kill) mindkettőnek XP-t/buffot ad. A mentor-jelöltséghez minimum szint-
küszöb kell (config).
**Miért jó:** Retention + közösség-építés — az új játékos nem egyedül bogarássza ki a
rendszereket, a veteránnak pedig célja/jutalma van a segítségért.
**Építőkövek:** Party invite/elfogadás minta, quest-számláló, PDC-páros-flag.
**Buktatók:** Visszaélés-védelem kell (alt-account mentorálás XP-farmolásra) — pl. csak
első bejelentkezés utáni X napon belüli új játékos jelölhető mentoráltnak.

### B17. Kaszt-próba arénák (hullám-túlélés)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Kijelölt arénában N hullám túlélése — a parkour-próba harci párja, a kaszt-mester
questlánc lépcsője.
**Hogyan működne:** `TerritoryManager` `TRIAL_ARENA` altípus, a `InvasionManager` hullám-
spawn/nehézség-görbe logikáját újrahasznosítva fixen skálázott mobokkal (nem távolság-
alapú). Belépés a kaszt-mester questlánc (`TALK_TO_NPC`) lépéseként nyílik, sikeres
teljesítés a lánc következő objektíváját oldja.
**Miért jó:** Konkrét, mérhető kaszt-mesterség-próba, ami a questlánc absztrakt „ölj 5
szörnyet" objektíváinál sokkal emlékezetesebb végpont.
**Építőkövek:** `InvasionManager` hullám-logika, `TerritoryManager` zónatípus, TALK_TO_NPC questlánc.
**Buktatók:** 13 kaszthoz 13 arénát kell megépíteni (szerver-csapat munka) — a plugin-oldal
egy általános hullám-vezérlő, a tartalom a szűk keresztmetszet.

### B18. Térkép-híd (BlueMap/Dynmap)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Territórium-zónák és fővárosok kirajzolása a webtérképre, claimek opcionálisan.
**Hogyan működne:** Soft-depend reflexiós híd az `integration/` mintájára (a plugin BlueMap/
Dynmap nélkül is fut) — a `TerritoryManager` zóna-listáját (poligon/kör alak, típus-szín)
és a `ClaimManager` claim-határokat egy periodikus globális tick szinkronizálja a
webtérkép API-jába markerekként.
**Miért jó:** A war-szerver identitáshoz a „hol a front" külső, mindig elérhető láthatósága
sokat ad — Discord-ba is linkelhető (C5 híddal együtt még erősebb).
**Építőkövek:** `integration/` reflexiós híd minta, `TerritoryManager`/`ClaimManager` zóna-adat.
**Buktatók:** A poligon-zónák marker-konverziója (pont-lista → webtérkép-formátum) nem
triviális, és a claim-mennyiség nagy szerveren teljesítmény-kockázat a gyakori szinkronnál.

### B19. Évszakos világ-modifikátorok
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

### B20. Relikvia-reforge + presztízs/paragon
**Munka:** 🔴 • **Érték:** ⭐⭐

**Mi ez:** Relikvia-újrakovácsolás ritka anyagból, max szint utáni paragon-pontok apró
additív bónuszokkal.
**Hogyan működne:** `/relic reforge <relikvia>` ritka anyag + valuta árán (sink) újrahúzza
az affix-öket a relikvia raritás-létrájának legfelső sávjából; a paragon-pontok egy
`ProfileManager` PDC-számláló, ami max szint után XP helyett gyűlik, és egy apró,
additív fa-táblázatból (config) válthatók be.
**Miért jó:** Csak akkor releváns, ha a törzs-játékosok már „kimaxoltak" — ekkor viszont
ez az egyetlen dolog, ami tartja őket, önmagában komoly retention-eszköz.
**Építőkövek:** Relikvia-raritás rendszer, talent-fa mintája (paragon-táblázat), sink-elv.
**Buktatók:** Csak késői fázisban van értelme bevezetni — korai bevezetés esetén a
„kimaxolt" játékosréteg még nem létezik, a munka kárba veszne.

### B21. Bestiárium / gyűjtő-album
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

### B22. Címek (title-ök)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Elérésekből, szezon-helyezésből, bestiáriumból nyíló címek, egy választott cím a
chat-prefixbe.
**Hogyan működne:** `/title` GUI a feloldott címek listájával (PDC: `Set<String>`
feloldva + egy aktív cím), a natív chat-formázó (LP-prefix + frakció-szín) elé/mögé
fűzi be az aktív címet; PlaceholderAPI placeholder is exponálja (`%icesmp_title%`).
**Miért jó:** Pénz-semleges presztízs — pont a szerver „nincs addolt pénz" elve mellett a
leglátványosabb, ingyenes motiváció.
**Építőkövek:** Natív chat-formázó, `IceSMPPlaceholders`, achievement/bestiárium/szezonliga
jutalom-forrás, GUI-minta.
**Buktatók:** ROADMAP megjegyzi, hogy „Címek/rangok NEM — ütközne a szerver rang-
pluginjaival" — ezt egyeztetni kell a tulajjal, mielőtt elindul (LuckPerms-rang vs.
kozmetikai cím elhatárolása szükséges).

### B23. Játékos-boltok (chest shop)
**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** Tábla+láda bolt a saját claimen, fix áras adás-vétel offline is.
**Hogyan működne:** Tábla-elhelyezés a claim tulajdonosának ládája fölé egy `/shop create
<item> <ár>` paranccsal írja be a PDC-taget a táblára/ládára; jobb-katt vásárlóként a
`MarketManager` escrow-logikáját újrahasznosítva azonnal levon a vevő egyenlegéből és
tölt a láda tartalmából (offline eladó is működik, mert nincs élő jóváhagyás). Csak
claim-en belül helyezhető el (a claim-jogosultság adja a védelmet lopás ellen).
**Miért jó:** A piac dinamikus árfolyama mellé egy „falusi kisbolt" fix-áras réteget ad —
kisebb tranzakciókhoz egyszerűbb, mint az aukció; tranzakció-díj ELÉG (sink).
**Építőkövek:** `ClaimManager` jogosultság, `MarketManager` escrow-logika, tábla-parsing.
**Buktatók:** Nagy munka (tábla-szintaxis, láda-szinkron, dupla-eladás race condition
elkerülése egyidejű vásárlásnál — szinkronizált hozzáférés kell a láda-tartalomhoz).

### B24. Bank-lekötés (betét kamattal, sink-semlegesen)
**Munka:** 🟡 • **Érték:** ⭐

**Mi ez:** `/bank lockup <összeg> <7|14|30 nap>` — a lekötött pénz nem költhető, lejáratkor
kincstárból fizetett prémium.
**Hogyan működne:** A lekötés egy `BankManager` PDC/YAML-bejegyzés (összeg, lejárat,
kamatláb-snapshot); a prémiumot KIZÁRÓLAG a frakció kincstárából fizeti ki lejáratkor
(nem a semmiből), a kamatlábat a király állíthatja (B12 politika-irány sávon belül).
Lejáratkor egy globális tick jóváírja az egyenleget + prémiumot.
**Miért jó:** Pénzt ültet ki a forgalomból = deflációs eszköz, miközben nem generál új
valutát — a ROADMAP explicit elvetette a „semmiből" kamatot, ez a pénz-semleges verzió.
**Építőkövek:** `BankManager`, `FactionManager` kincstár, B12 politika-irány.
**Buktatók:** Ha a kincstár üres lejáratkor, a kifizetés elmarad vagy csúszik — ezt
kezelni kell (pl. sorban állás vagy részleges kifizetés), különben játékos-panasz forrás.

### B25. Heti lottó
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Jegy frakció-valutáért, heti sorsolás a befizetések egy részéből.
**Hogyan működne:** `/lottery buy <db>` jegyenként fix ár (sink), a befizetés `YamlStore`-ban
gyűlik; heti scheduler tick (szezon-scheduler mintájára) egyenletes eloszlású sorsolást
végez, a nyeremény-pool X%-a (config) a nyertesé, a maradék ELÉG. Broadcast + krónika-
hír (B15) a nyertesről.
**Miért jó:** Kis munka (egy YAML-számláló + egy heti tick), nagy közösségi zaj — mindenki
beszél róla hétfőn.
**Építőkövek:** `YamlStore`, szezon-scheduler minta, heti krónika (B15), sink-elv.
**Buktatók:** A nyeremény-pool méretét korlátozni kell (max jegy/fő vagy csökkenő
esély sok jeggyel), különben a gazdagabb frakció egyszerűen kivásárolja a sorsolást.

### B26. Rúna-kovácsolás (enchant-kiegészítő szakma-ág)

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### B27. Dungeon-affixek (kihívás-módosítók)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Heti rotálódó módosítók a B3 dungeonökhöz/világbosshoz/invázióhoz („Vérszomjas
hét: +25% mob-sebzés, +50% loot").
**Hogyan működne:** `config/dungeon-affixes.yml` heti sorsolt (vagy menetrend-alapú, C13
mintájára) affix a `MobScaling` szorzóira és a `LootTable` drop-esélyére kötve; a heti
krónika (B15) hirdeti, a HUD/boss-bar a dungeon/esemény kezdetén megjeleníti az aktív
affix nevét.
**Miért jó:** Ismételhető tartalmat tart frissen minimális munkával — ugyanaz a dungeon
hetente máshogy játszik.
**Építőkövek:** `MobScaling`, `LootTable`, heti krónika (B15), esemény-naptár (C13).
**Buktatók:** Az affix-kombinációk balansz-tesztelése idővel exponenciálisan nő — érdemes
kis, egymástól független szorzó-készlettel indulni, nem kombinált affixekkel.

### B28. Kaszt-story questlánc
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Kasztonként 5-8 lépéses, láncolt story-küldetéssor a kaszt identitásához kötve,
a 25. szintű spec-választásig vezetve.
**Hogyan működne:** A meglévő `next`-láncolt quest-mechanika (A5 infra) + `TALK_TO_NPC` +
elágazó dialógus felhasználásával `config/quests/<kaszt>-story.yml` sorozat, a kaszt
szentélyéhez (rituálé-oltár, README) kötött NPC-kkel. A lánc vége a spec-választás
pillanatában zár le egy kis narratív jelenettel.
**Miért jó:** A kaszt-választás ma pusztán mechanikai — ez ad neki történetet, ami miatt a
játékos érzelmileg is a kasztjához kötődik, nem csak a statokhoz.
**Építőkövek:** Quest-lánc infra (`next`), TALK_TO_NPC, elágazó dialógus, rituálé-oltár
szentélyek.
**Buktatók:** Tartalom-írás a munka zöme (13 kaszt × 5-8 lépés = 65-100 quest-lépés
dialógussal) — fokozatosan, kaszt-onként érdemes kiadni, nem egy nagy dobással.

### B29. NPC-reputáció
**Munka:** 🔴 • **Érték:** ⭐⭐

**Mi ez:** Nevezetes NPC-k felé külön hírnév-skála, szintenként kedvezmény/exkluzív recept.
**Hogyan működne:** `ReputationManager` PDC-számláló NPC-id + player-uuid párra, quest/
karaván/eszkort teljesítésből növekszik (config: `reputation.gain.<forrás>`); a
frakció-bolt NPC-k és a recept-katalógus a reputáció-szintet olvasva ad kedvezményt
(ár-szorzó) vagy old fel exkluzív receptet/questet.
**Miért jó:** A frakció-rendszertől független, „város-RPG" réteget ad — a játékos nem
csak a frakciójához, hanem egyes NPC-khez/városokhoz is köthet hosszú távú viszonyt.
**Építőkövek:** Frakció-bolt NPC-k, recept-katalógus, quest/karaván/eszkort teljesítés-hookok.
**Buktatók:** Sok NPC × sok forrás kombinációnál a számláló-hookok szétszórtan élnek a
kódban — egy közös `ReputationManager.grant(npcId, player, amount)` API kell, amit
minden releváns listener hív, ne duplikált logika.

### B30. Háború-ablakok (war window)
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Raid csak megadott idősávban indítható (pl. este 7-10), + „védett hétvége" a
szezonzáró előtt.
**Hogyan működne:** `config/raid.yml` → `war-window.start`/`end` (napi órasáv) és
`war-window.protected-days` (dátum-lista/szezon-relatív); a raid indítás-ellenőrzésébe
(`RaidManager.canStart`) egy idő-kapu kerül, ami a szerver aktuális idejét veti össze a
sávval és emberi olvasható üzenettel utasítja el az ablakon kívüli próbálkozást.
**Miért jó:** A védő fél életminőségét óriásit javítja — az off-time (alvás közbeni)
raidelés a #1 SMP-panasz, ez a legkisebb munkával a legnagyobb frusztráció-csökkentés.
**Építőkövek:** `RaidManager` indítás-ellenőrzés, config-vezérelt időablak.
**Buktatók:** Időzóna-eltérésekkel rendelkező, nemzetközi közösségnél nehéz mindenkinek
jó ablakot találni — a szerver-idő (nem a játékosé) legyen az irányadó, és ezt
kommunikálni kell.

### B31. Zsoldos-tábla
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A király a kasszából vérdíj-szerű megbízásokat tűzhet ki (raid-védelem, eszkort,
ellenséges relikvia-hordozó levadászása).
**Hogyan működne:** `/faction bounty post <típus> <cél> <díj>` az `ExchangeBoard` GUI-
mintájára egy táblán jelenik meg; teljesítés-ellenőrzés a meglévő event-hookokból
(kill-event, escort-completion-event) automatikusan pipálja a megbízást, a díj a
kasszából a teljesítő egyenlegébe kerül.
**Miért jó:** A kassza értelmes elköltési iránya olyan tevékenységekre, amiket a
rendszer nehezen kényszerít ki (pl. „védd a karavánt") — a király célzottan motiválhat.
**Építőkövek:** `ExchangeBoard` GUI-minta, event-hookok (kill/escort), `FactionManager` kassza.
**Buktatók:** A teljesítés-ellenőrzés hamisítás-védelme fontos (pl. ne lehessen saját
alt-tal „levadászni" a célt) — a cél-uuid egyezés szigorú ellenőrzése kell.

### B32. Építőverseny-esemény
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Admin kijelöl telkeket, a játékosok határidőre építenek, szavazás GUI-ból.
**Hogyan működne:** `/territory` `PLOT` altípus telkekhez, `/build-contest start` admin-
parancs nyitja a beadási ablakot; szavazás egy GUI-ban (fejek + katt telkenként, egy
szavazat/játékos, PDC-flag ismétlés ellen). Jutalom kozmetika (B9) vagy cím (B22).
**Miért jó:** Szinte csak GUI-munka a meglévő territórium-infra fölött, mégis erős
közösségi/kreatív esemény, ami a nem-harcias játékosokat is bevonja.
**Építőkövek:** `TerritoryManager` zónatípus, GUI-minta, kozmetika (B9)/cím (B22) jutalom.
**Buktatók:** A szavazás manipulálható (alt-account szavazatok) — egy szavazat/IP vagy
minimum-playtime küszöb ajánlott.

### B33. Szezonzáró világesemény („végítélet-hét")

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### B34. Lebomló sír (grave) halálkor
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Halálkor a cucc egy védett „sír"-blokkba kerül, csak a halott nyithatja, X perc
után mindenkinek szabad.
**Hogyan működne:** A `TreasureEvent` láda-logikája (jelölt, védett, első-megnyitó-jogosult
blokk) szinte változtatás nélkül újrahasznosítható halál-eseményre: a `PlayerDeathEvent`-
ben a drop helyett egy sír-blokk épül (fej + TextDisplay felirat), PDC-ben tárolja a
halott UUID-jét + a lejárati bélyeget; X perc után bárki nyithatja.
**Miért jó:** A keep-inventory és a full-loot közti egészséges középutat ad — a cucc nem
vész el azonnal mások előtt, de a halál mégis kockázattal jár. A21 irány-jelzővel párban
a legjobb élmény.
**Építőkövek:** `TreasureEvent` láda-logika, `PlayerDeathEvent`, `TextDisplay` felirat.
**Buktatók:** PvP-zónában a sír ne váljon célponttá, ahol a gyilkos egyszerűen kivárja a
lejáratot — érdemes a halott számára hosszabb kizárólagos ablakot adni, mint a
gyilkosnak.

### B35. Céhek (frakción belüli kisközösségek)

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### B36. Gyorsutazás-hálózat (útkövek)
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Admin-lehelyezett útkő-pontok, aktiválás odautazással, használat frakció-
valutáért (táv-arányos díj).
**Hogyan működne:** `/waystone` admin-parancs jelöl ki blokkot; jobb-katt aktiválja
(PDC-ben a játékos „felfedezett" listájába kerül az útkő-id), utána `/waystone travel
<id>` (vagy GUI-lista) a felfedezett pontok közül `teleportAsync`-kal utazik, a díj a
két pont közti blokk-távolsággal arányos (frakció-valuta sink).
**Miért jó:** A nagy világ bejárhatóvá válik anélkül, hogy az elytra/ló befektetett
értékét nullázná — a díj + a „csak felfedezett pontok" szabály megőrzi az utazás
mint erőforrás érzését.
**Építőkövek:** `teleportAsync`, kingdom-spawn infra, PDC felfedezés-lista, frakció-valuta sink.
**Buktatók:** A díj-formula hangolása kényes — túl olcsó lenullázza a lovaglás/elytra
értékét, túl drága pedig feleslegessé teszi a rendszert.

### B37. Nyíl-műhely (íjász item-ág)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Craftolható speciális nyilak: horgony-nyíl, jelző-nyíl, robbanó-nyíl.
**Hogyan működne:** `ArrowItemFactory` PDC-taggel gyártott nyíl-variánsok a recept-
katalógusban; a `SpellProjectileListener` a becsapódáskor a nyíl PDC-tagje alapján
ismeri fel a variánst és alkalmazza a hatást (horgony: rövid gyökér-effekt, jelző:
glowing a célon, robbanó: kis AoE — drága alapanyagból, nem grief-elő robbanás).
**Miért jó:** Az íjász kaszt item-oldali mélységet kap a spell-rotáció mellé, és a
fogyóeszköz-jelleg (a nyíl elhasználódik) folyamatos keresletet termel a piacon.
**Építőkövek:** `ItemFactory`-minta, `SpellProjectileListener`, recept-katalógus.
**Buktatók:** A robbanó-nyíl terep-védelmi szabályt igényel (ne törjön blokkot védett
zónában) — a `TerritoryProtectionListener` `explosions` szabályát kell rákötni.

### B38. Mythic spell-variánsok (mastery 5 fölé)
**Munka:** 🔴 • **Érték:** ⭐⭐

**Mi ez:** 5-ös mastery után egy spell nagy valuta+anyag árán „átlényegülhet" módosult
viselkedéssel és külön CMD-vizuállal.
**Hogyan működne:** `config/spells-mythic.yml`: spellenkénti mythic-variáns leírás
(módosult paraméterek + viselkedés-flag, pl. `splits_on_impact: true`), a `ConfiguredSpell`
builder egy `mythicVariant` mezőt olvas cast-időben, ha a játékos PDC-je jelöli a
feloldást (`/spellbook ascend <spell>` — sink). A CMD-vizuál a RESOURCE_PACK_CMD.md
folyamatot követi.
**Miért jó:** Végjáték-cél a fő rotációnak azoknak, akik már mindent kimaxoltak a
kaszt-fán — a spell nem csak erősebb, hanem MÁSKÉNT viselkedik, ami friss döntéseket kényszerít ki.
**Építőkövek:** `ConfiguredSpell` builder, mastery-rendszer, CMD resource-pack folyamat.
**Buktatók:** A viselkedés-módosítás (nem csak számhangolás) minden érintett spellnél
egyedi kódot igényelhet — csak néhány, gondosan kiválasztott „ikonikus" spellel érdemes indulni.

### B39. Védmű-építés raid-védelemhez
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A védő oldal a raid-ablak alatt lerakható védműveket kap: barikád-blokk, lassító
mező, riasztó-harang.
**Hogyan működne:** `DefenseItemFactory` (a `SiegeWeaponFactory`-minta párja) kassza-
pénzért (sink) craftolható itemek; barikád-blokk extra törés-idő PDC-taggel, lassító
mező egy időzített `PotionEffect`-terület a régión, riasztó-harang a védő frakció
online tagjainak broadcastol (Folia: `getRegionScheduler()` a terület-effekthez, majd
hop minden érintett játékos schedulerére az üzenethez).
**Miért jó:** A raidet taktikusabbá teszi — a védő nem csak elszenvedi az ostromot,
hanem aktívan felkészülhet, ami az ostromgépek (B11) természetes ellenpárja.
**Építőkövek:** `SiegeWeaponFactory`-minta, raid-terület-kötés, kassza-sink.
**Buktatók:** A barikád-elhelyezés ne legyen kihasználható spawn-blokkolásra normál
(nem raid) időben — csak aktív raid-ablakban legyen lerakható/aktív.

### B40. NPC-kereskedőhajó / vándorkereskedő ritka árukkal
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Hetente egyszer különleges kereskedő a fővárosban, limitált készletű rotálódó
ritka árukkal, kizárólag frakció-valutáért.
**Hogyan működne:** A karaván-infra „skin"-je: `FancyNpcs`-hídon (soft-depend) heti
scheduler tick szól be az NPC-t a fővárosba, a készlet `config/wanderer-stock.yml`-ből
véletlen rotál (recept-lapok, rúna-anyagok B26-hoz, kozmetika B9-hez); jobb-katt bolt-GUI,
a készlet fogyóban (elsőnek jön, annak van).
**Miért jó:** A „gyere be szerdán" ritmus + nagy valuta-nyelő egyben — ritka, exkluzív
tartalmat ad anélkül, hogy a normál piaci kínálatot felhígítaná.
**Építőkövek:** Karaván-esemény infra, `FancyNpcs` bridge, heti scheduler, frakció-valuta sink.
**Buktatók:** A limitált készlet miatt csak az online/gyors játékosok férnek hozzá —
fontolandó egy tisztességes elosztási szabály (pl. sorban állás vagy egy/fő limit).

### B41. Aréna-liga (heti bajnokság, ELO)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A B2 duel + B17 aréna fölé heti 1v1/2v2 liga ELO-val, szezon-végi cím és
liga-pont jutalom.
**Hogyan működne:** `/arena queue [1v1|2v2]` egy egyszerű párosító sorban (online
játékosok közül hasonló ELO-jú ellenfél), a mérkőzés a B2 duel-arénát/mechanikát
használja téttelenül. Az ELO a `StatsManager` bővítéseként PDC/YAML-ban tárolt szám
(K-faktoros frissítés győzelem/vereség után). Szezon-végén a top N cím-et (B22) kap.
**Miért jó:** A beleegyezéses PvP versenyszintjét adja azoknak, akiknek a duel/aréna nem
elég strukturált — a bűn-rendszert nem érinti, tisztán sportszerű.
**Építőkövek:** B2 duel-mechanika, B17 aréna, `StatsManager` ELO-bővítés, cím-rendszer (B22).
**Buktatók:** A párosító sor kis játékosszámnál (kevés online) hosszú várakozást
eredményezhet — kis szerveren érdemes tágabb ELO-sávval indulni.

### B42. Régészet szakma-ág

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### B43. Pet-tenyésztés
**Munka:** 🔴 • **Érték:** ⭐

**Mi ez:** Két max-szintű, azonos fajú pet párosítható, az utód a szülők statjaiból örököl
+ kis mutáció-eséllyel.
**Hogyan működne:** `/pet breed <pet1> <pet2>` a `PetManager` stat-modelljét bővíti: az
utód statjai a szülők átlaga + egy véletlen mutáció-sáv (szín/név-prefix, config-
súlyozott). Az örökítés PDC-be íródik az új pet-entitás létrehozásakor.
**Miért jó:** Niche, de a pet-gyűjtőknek végjátékot ad — a pet-tartás presztízse a
„tökéletes statú" utód üldözésévé válik.
**Építőkövek:** `PetManager` stat-modell.
**Buktatók:** Alacsony prioritású (⭐), mert szűk játékosréteget szolgál — csak akkor
éri meg, ha a pet-rendszer már amúgy is aktívan használt.

### B44. Diplomácia: szövetség és fegyverszünet
**Munka:** 🔴 • **Érték:** ⭐⭐

**Mi ez:** Király-szintű döntés két frakció formális szövetségéről (közös raid-védelem,
ff-védelem) vagy fizetett fegyverszünetről.
**Hogyan működne:** `/faction diplomacy propose <frakció> <alliance|truce>` mindkét király
jóváhagyásával aktiválódik (`FactionManager` reláció-mátrix bővítése); szövetség esetén
az A2 `SpellTargetingUtil.isHostile` az `isAlly`-ba a szövetséges frakciót is beleveszi
(AoE-spellek nem ütik), fegyverszünet kassza→kassza fizetéssel (ELÉG-hányaddal) jár, és
időzített lejárattal.
**Miért jó:** A 4 frakciós meta mélységét adja — a statikus ellenségesség helyett
dinamikus, játékos-vezérelt viszonyokat, ami újra és újra témát ad a frakció-politikának.
**Építőkövek:** `FactionManager` reláció-mátrix, A2 `isHostile`/`isAlly` targeting-infra, kassza.
**Buktatók:** A ROADMAP megjegyzi, hogy 3-4 királysággal a szövetség-tér minimális (max
egy 2v1 kapcsoló) — csak akkor éri meg igazán, ha több frakció/al-klán rendszer is
bekerül, addig inkább alacsony prioritású.

### B45. Torony-védelem invázió-mód
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Az invázió-esemény változata a fővárosnál: hullámok a kapu felé, a játékosok
védművekkel + spellekkel védenek.
**Hogyan működne:** Az `InvasionManager` hullám-logikája fix célponttal (a főváros
kapuja) és egy közösségi cél-számlálóval (a betörési szint mérésére — minden áttört
hullám csökkenti a „védelem" számlálót); a védmű-itemek (B39) itt is használhatók.
Vereség esetén (számláló nulláig csökken) enyhe, nem-grief büntetés (pl. rövid debuff
a fővárosban), győzelem esetén frakció-szintű jutalom.
**Miért jó:** Ko-op PvE csúcsélmény a meglévő invázió+közösségi cél infrából — mindenki
egy célért harcol, ami a frakció-kohéziót erősíti anélkül, hogy PvP kellene hozzá.
**Építőkövek:** `InvasionManager` hullám-logika, közösségi cél-infra, védmű-itemek (B39).
**Buktatók:** A főváros védett zóna (README szerint senki nem építhet/pvp-zhet ott) —
a mob-spawn és a védmű-elhelyezés kivételszabályait a `TerritoryProtectionListener`
rule-jaiba pontosan be kell illeszteni, nehogy megsértse a zóna-védelmet.

### B46. Trófea-halak és horgász-dicsőség
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Ritka, nevesített halak méret-görgetéssel, rekord a ranglistán, trófea-fal item
a claimre.
**Hogyan működne:** A Halász szakma horgászat-eseményébe egy méret-görgetés (véletlen
súlyozott, config-sávos) és egy ritka „nevesített" halfaj-pool kerül; a legnagyobb
fogás `StatsManager` ranglistára kerül (A22-mintára), a hal PDC-ben tárolja a méretet/
dátumot, kihelyezhető egy `ItemDisplay`-keretben trófea-fal itemként a claimen.
**Miért jó:** A Halász szakma csendes mélyítése a bestiárium (B21) irányába — kis
munka, de a horgász-játékosoknak saját presztízs-ága lesz.
**Építőkövek:** Halász szakma horgászat-esemény, `StatsManager` ranglista (A22), `ItemDisplay` keret.
**Buktatók:** A méret-eloszlást gondosan kell hangolni, hogy a „rekord" tényleg ritka
maradjon — túl gyakori nagy hal leértékeli a presztízst.

### B47. Ereklye-expedíciók (heti PvE cél-pont)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Hetente egy „expedíciós hely" a térképen, többfázisú esemény (mini-boss →
ásatás → menekítés), a végén rituálé-alapanyag/relikvia-szilánk.
**Hogyan működne:** Heti scheduler tick választ egy koordinátát (kincs-esemény
elhelyezés-mintájára) és `ExpeditionManager` fázis-gépet indít: 1) mini-boss spawn
(világboss-archetípus kicsinyített verziója), 2) ásatás (kis blokk-szkennelés, régió-
lokális), 3) menekítés (időzített extrakciós pont, Vad Hajsza-szerű üldözés). Sikeres
zárás rituálé-alapanyagot/relikvia-szilánkot ad a résztvevőknek (personal loot, party-infra).
**Miért jó:** A meglévő világesemény-építőkövekből (kincs + Vad Hajsza + világboss)
összefűzött „mini-raid" PvE-seknek, ami heti ritmust ad anélkül, hogy PvP kellene hozzá.
**Építőkövek:** Kincs-esemény elhelyezés, világboss-archetípus, Vad Hajsza üldözés-logika,
party personal loot.
**Buktatók:** A 3 fázis állapotgépe (mini-boss/ásatás/menekítés) törékeny lehet, ha
a résztvevők menet közben lecsatlakoznak — minden fázis-váltásnál explicit timeout/
fallback kell.

---

## Új ötletek (B48–B62)

### B48. Territórium-őrposztok (felderítő jelzőrendszer)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Frakciótagok által építhető őrtorony-struktúra a frakcióterület szélén, ami
jelzi, ha idegen lép be a zóna közelébe.
**Hogyan működne:** `/territory outpost build` egy jelölt blokk-mintát (admin-definiált
séma) ellenőriz a claim/territórium határán belül; sikeres építés után egy `OutpostManager`
periodikus, régió-lokális ellenőrzést futtat (`getRegionScheduler()` a torony chunkján) —
ha nem-tag lép a zóna X blokkos körzetébe, action bar/HUD-jelzés megy a frakció online
tagjainak (Folia: hop minden érintett játékos schedulerére). Tisztán információs, nem
harci hatás.
**Miért jó:** A territórium-birtoklás passzív, folyamatos befektetést igényel (építés,
karbantartás), és a frakciótagoknak valós okuk van a zóna szélén tevékenykedni — mélyíti
a territórium-rendszert anélkül, hogy új PvP-mechanikát vezetne be.
**Építőkövek:** `TerritoryManager` zóna-határ lekérdezés, `getRegionScheduler()` periodikus
ellenőrzés, HUD/action bar jelzés-infra.
**Buktatók:** A gyakori régió-lokális szkennelés teljesítmény-költséges lehet sok toronynál —
ritka tick-intervallum (pl. 5-10 mp) és chunk-index alapú gyors elutasítás szükséges.

### B49. Ideiglenes claim-vendégjog
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A claim tulajdonosa időkorlátos, korlátozott build/interact jogot adhat nem-
trust játékosnak — anélkül, hogy állandó trust-listára tenné.
**Hogyan működne:** `/claim guest <név> <perc>` a `ClaimManager` trust-listájának egy
külön, lejárati bélyeggel ellátott almezőjét írja (PDC vagy a claim YAML-bejegyzés
`guests: {uuid: expiry}` térképe); a védelem-ellenőrzés (`TerritoryProtectionListener`
mintájára) a lejárt vendégjogot automatikusan figyelmen kívül hagyja anélkül, hogy külön
takarítási tick kellene (lusta lejárat-ellenőrzés olvasáskor).
**Miért jó:** Sok gyakorlati helyzetre (látogató segít építeni, ideiglenes közös projekt)
a jelenlegi bináris trust/no-trust túl merev — ez finomabb, alacsony kockázatú megosztást tesz lehetővé.
**Építőkövek:** `ClaimManager` trust-lista, `TerritoryProtectionListener` jogosultság-ellenőrzés.
**Buktatók:** A lejárat-ellenőrzést minden build/interact eseménynél kell futtatni — ha
drága lekérdezés, cache-elni kell játékosonként rövid TTL-lel.

### B50. Claim-fejlődési szintek (aktivitás-alapú evolúció)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A claim nem statikus — rendszeres használat (bejelentkezés, építkezés,
karbantartás) fokozatosan „szintet lép", ami kis QoL-bónuszokat old fel (nagyobb y-tartomány,
extra trust-slot).
**Hogyan működne:** `ClaimManager` egy `activity`-számlálót vezet claimenként (bejelentkezés
+ blokk-elhelyezés claimen belül inkrementálja, config-súlyozva); küszöbök átlépésekor
(pl. 100/500/2000 pont) automatikus szint-emelés, a bónuszokat a claim-GUI (A13) mutatja.
Inaktivitás (X nap nincs belépés a claim tulajdonosától) lassú visszalépést okozhat —
opcionálisan kikapcsolható configból.
**Miért jó:** A claim-birtoklás ma „megveszed és kész" — ez folyamatos, alacsony
súrlódású progressziót ad neki, jutalmazva azokat, akik tényleg élnek a helyükön.
**Építőkövek:** `ClaimManager` perzisztencia, claim-GUI (A13), config-küszöbök.
**Buktatók:** Az aktivitás-számlálás könnyen visszaélhető (automatizált blokk-lerakás
farmolásra) — a build-eseményeket cooldown-nal/napi felső korláttal érdemes védeni.

### B51. Fiók-szintű meta-progresszió (veterán-fa)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A kaszt-szinttől független, fiók-egész progressziós fa: össz-játékidő, összesített
kill/quest/craft-számokból nyíló apró, kaszt-független QoL-perkek.
**Hogyan működne:** `ProfileManager` (vagy a meglévő `StatsManager` bővítése) egy
`veteran_points` mezőt gyűjt a különféle rendszerek (kill, quest, craft, territórium-
felfedezés) meglévő számlálóiból súlyozott összegzéssel; `/veteran` GUI-ban válthatók be
apró perkekre (extra hotbar-szlot spell-kedvencnek — A4/A18, gyorsabb pet-hívás, +1
party-invite/nap). Perzisztencia PDC-ben.
**Miért jó:** A kaszt-váltás (admin-reset) vagy új szezon esetén a játékos ne veszítse
el az „idő itt töltve" érzetét — ez egy sosem nullázódó, kizárólag QoL-jellegű réteg,
ami sosem ad harci erőt (nem sérti a balanszot).
**Építőkövek:** `StatsManager` számlálók, GUI-minta, A4/A18 kedvenc-infra, PDC-tárolás.
**Buktatók:** Szigorúan QoL-re kell szorítkozni — ha bármi harci erőt ad, a régóta
játszók ellenállhatatlan előnyt kapnak az újakkal szemben, ami rontja az új-játékos élményt.

### B52. Fegyver-mesterség (item-szintű XP)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Az egyes fegyver-itemek saját, a tárgyhoz kötött XP-t gyűjtenek használat
(kill/találat) közben, és kis, a tárgyra rögzített bónuszokat oldanak fel.
**Hogyan működne:** A fegyver PDC-je egy `weapon_xp` számlálót kap, ami kill-eventeknél
(a kill-reward listenerek mintájára, cross-entity hop nélkül, mert a fegyver a saját
gazdájánál van) nő; küszöbönként (pl. 100/500/2000) a fegyver lore-ja egy apró,
FIX bónuszt ír be (pl. +1% sebzés, rövidebb cooldown egy hozzá kötött spellre). A
bónusz a TÁRGYHOZ kötött, nem a játékoshoz — ha eladod/elveszíted, a haladás is megy vele.
**Miért jó:** Az item-progresszió (raritás + affix) mellé egy „bejáratott fegyver"
érzetet ad — a régóta hordott tárgy ténylegesen egyre jobb lesz, ami érzelmi kötődést épít.
**Építőkövek:** `ItemFactory` PDC-minta, kill-reward listener minta, spell-balansz olvasás.
**Buktatók:** Kockázatos, ha a bónusz túl erős — egy elveszett/ellopott „bejáratott"
fegyver komoly presztízs-veszteség; a bónuszsávot alacsonyan (néhány %) kell tartani.

### B53. Tárgy-fúzió (kockázatos item-összeolvasztás)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Két azonos típusú, magas raritású tárgy összeolvasztható eggyé, ami a jobbik
affixeket örökli — de sikertelenség esetén mindkét tárgy elvész.
**Hogyan működne:** `/fusion` GUI (anvil-szerű, két bemeneti slot), a siker-esély a
raritás-különbségtől függ (config-táblázat); sikeres fúzió a magasabb raritású tárgy
affix-készletét bővíti a másik tárgy egy véletlen affixével, sikertelen fúzió mindkét
input-itemet elégeti (nincs pénz-jutalom, tiszta item-sink).
**Miért jó:** Kockázat/jutalom döntést ad a felesleges, duplikált ritka lootnak — a
játékos választhat: eladja a piacon (biztos, kis érték) vagy megkockáztatja a fúziót
(nagyobb tét, nagyobb nyereség).
**Építőkövek:** Raritás/affix-rendszer, GUI-minta (anvil-input), item-sink elv.
**Buktatók:** A sikerarányt gondosan kell hangolni — ha túl kedvező, mindenki mindig
fuzionál (a raritás-létra inflálódik); ha túl kegyetlen, senki nem használja.

### B54. Elátkozott felszerelés (kockázatos erő)

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### B55. Önkéntes nehézség-fogadalom (kockázat-mód)
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Bármikor bekapcsolható önkéntes „fogadalom": ideig-óráig (vagy adott
tevékenységre) a játékos több sebzést kap ÉS ad, cserébe megnövelt XP-t/loot-esélyt.
**Hogyan működne:** `/pact berserker <perc>` PDC-flaget állít, ami a sebzés-számítás
hook-pontjában (bejövő/kimenő módosító) és a `LootTable`/XP-jóváírás hook-pontjában
szorzót alkalmaz a fogadalom aktív ideje alatt; a HUD egy jól látható jelzést mutat,
amíg fut. Önkéntes, bármikor kikapcsolható (kis cooldown a visszaélés ellen).
**Miért jó:** Azoknak ad izgalmat, akik a vanília nehézséget unják, anélkül hogy
mindenkire ráerőltetné — tisztán opt-in kockázat/jutalom réteg, ami a farm-rutint
frissen tartja.
**Építőkövek:** Sebzés-számítás hook, `LootTable`/XP-jóváírás hook, HUD-jelzés infra.
**Buktatók:** PvP-ben a szorzó kihasználható lehet (bekapcsolás csak győztes pozícióból) —
érdemes csak PvE-sebzésre/lootra korlátozni, vagy PvP-ben teljesen kizárni.

### B56. Napi bejelentkezési sorozat (login streak)
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Egymást követő napi belépésekért fokozódó, pénz-semleges jutalom-sorozat,
ami kihagyott nap után nullázódik (vagy lassan csökken).
**Hogyan működne:** `LoginStreakManager` PDC-ben tárolja az utolsó belépés dátumát és a
jelenlegi streak-számot; `PlayerJoinEvent`-en (napi határ, szerver-idő) inkrementál vagy
nulláz, és `config/login-streak.yml` küszöbönként (3/7/14/30 nap) jutalmat ad (XP,
szakma-anyag, kis kozmetika — sosem valuta). A HUD/action bar megjeleníti az aktuális
sorozatot belépéskor.
**Miért jó:** A leg olcsóbb, legbizonyítottabb retention-mechanika — napi visszatérési
okot ad minimális munkából, és jól kombinálható a heti krónikával (B15) vagy a heti
megbízásokkal (B1).
**Építőkövek:** `PlayerJoinEvent`, PDC-dátum-tárolás, HUD/action bar jelzés.
**Buktatók:** A nullázódás túl büntető érzetű lehet — érdemes 1 „kihagyható nap" toleranciát
adni (streak-freeze), hogy egy elfoglalt nap ne törje derékba a motivációt.

### B57. Heti kaszt-kihívás (kaszt-specifikus időablak)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A B1 frakció-szintű heti megbízásoktól függetlenül, kaszt-onként egy heti
kihívás-objektíva (pl. „Varázslóként végezz 3 comboból végzett kill-t"), spec-specifikus jutalommal.
**Hogyan működne:** `config/weekly-class-challenge.yml` kasztonként egy sablon-objektívát
ír le (a meglévő quest-objektíva típusokból építve, a spell-kombó A16 vagy a kaszt-
próba B17 infrájára kötve); heti reset a B1-gyel közös scheduler tick-en, a haladás a
quest-napló GUI-ban (`/quest log`) egy külön szekcióban jelenik meg. Jutalom kaszt-
specifikus kozmetika vagy XP-boost (sosem harci erő).
**Miért jó:** A B1 frakció-szintű célok mellé egyéni, a kaszt-identitáshoz kötött heti
ritmust ad — a Varázsló és a Harcos más kihívást kap, ami a kaszt-játékérzetet erősíti
(A3 iránnyal rokon, de heti tartalom-oldalról).
**Építőkövek:** Quest-objektíva típusok, B1 heti scheduler, kaszt-próba (B17), quest-napló GUI.
**Buktatók:** 13 kaszthoz 13 kihívás-sablon karbantartása heti tartalom-terhet jelent —
érdemes egy közös, paraméterezhető sablon-készletből (config) generálni, nem kézzel írt egyedi questet mindenkinek.

### B58. Relikvia-őrzés territórium-buff
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Ha egy frakció aktív relikviát tart a saját fővárosában (rituálé-oltáron
kiállítva, nem eltéve), a frakcióterület egy passzív buffot sugároz a tagoknak.
**Hogyan működne:** A rituálé-oltár szentélyben egy kijelölt „kiállító" blokk fogadja a
relikviát; amíg ott van (a relikvia-rendszer 14 napos inaktivitás-szabálya érintetlen
marad, ez csak egy extra állapot), a `TerritoryManager` a frakcióterület tagjainak
periodikusan (régió-lokális `getRegionScheduler()` tick) egy kis, relikvia-specifikus
buffot ad (pl. a Mételytépő birtoklása enyhe bűn-tisztulás gyorsítást ad a fővárosban).
Elvesztéskor (ellopás/elenyészés) a buff azonnal megszűnik.
**Miért jó:** A relikvia és a territórium-rendszer ma egymástól függetlenül élnek — ez
egy konkrét, mindkét rendszert erősítő interakció: a relikvia birtoklása nemcsak
presztízs, hanem tényleges, közösségi haszon, ami motiválja a megőrzését.
**Építőkövek:** Rituálé-oltár szentély, relikvia-rendszer (14 napos inaktivitás-szabály),
`TerritoryManager`/`FactionPassiveListener` buff-adás mintája.
**Buktatók:** A relikvia-birtoklás már most is erős (egyedi, csak egy létezik) — a
territórium-buff ne legyen túl nagy, nehogy a relikvia-birtokló frakció végleg
lehagyhatatlan előnybe kerüljön.

### B59. Gyűjtögető pet-segéd (pet × szakma interakció)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Bizonyos petek (pl. borz, róka) gyűjtögető szakma közben (bányászat, favágás,
gyógynövény-szedés) kis eséllyel extra alapanyagot hoznak a gazdának.
**Hogyan működne:** A `PetManager` pet-szint és a `ProfessionManager` szakma-szint
közösen olvasva egy szorzót ad a gyűjtögetés-eventekre (blokk-törés XP/drop-hook), ha
a megfelelő pet-faj aktívan a gazda mellett van (közelség-ellenőrzés a pet saját
régió-szálán); az extra alapanyag ugyanabból a `LootTable`-ből jön, amit a szakma amúgy
is ad — nincs új anyagtípus, csak megnövelt mennyiség/esély.
**Miért jó:** A pet-rendszer ma kizárólag kozmetikai/harci (B4) — ez egy második,
gyűjtögetős használati célt ad neki, ami a nem-harcias, szakma-fókuszú játékosoknak is
okot ad pet-tartásra és -szintezésre.
**Építőkövek:** `PetManager`, `ProfessionManager` gyűjtögetés-hookok, `LootTable`.
**Buktatók:** A bónusz-esély ne legyen olyan nagy, hogy a szakma-XP-görbét/piaci
kínálatot érdemben eltorzítsa — kis, egyszámjegyű százalékos sávban érdemes tartani.

### B60. Tárgy-szett szinergia (rarity-set bónusz)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Ha egy játékos egyszerre visel több, azonos tematikus „szett"-hez tartozó
(szakma-craftolt vagy mob-loot eredetű) tárgyat, kis passzív szinergia-bónuszt kap.
**Hogyan működne:** Az `ItemFactory`-tárgyak egy opcionális `set_id` PDC-taget kapnak
(csak a tematikusan összetartozó receptek/loot-tételek); egy `EquipmentSetListener` az
`PlayerArmorChangeEvent`/`PlayerItemHeldEvent` mintán újraszámolja, hány darab aktív a
készletből, és 2/4 db küszöbnél egy kis, additív bónuszt ad (pl. +2% szakma-XP egy
tematikus szakma-szetthez). A számítás a viselő SAJÁT régió-szálán fut, nincs cross-entity hop.
**Miért jó:** A raritás/affix-rendszer mellé egy második, „gyűjtögesd össze a szettet"
horgot ad — nem helyettesíti az egyedi tárgyak erejét, csak apró extra motivációt teremt
a tematikus felszerelés összeállítására.
**Építőkövek:** `ItemFactory` PDC-minta, raritás/loot-rendszer, equipment-change listener minta.
**Buktatók:** Csak néhány, gondosan válogatott szettel érdemes indulni — ha minden tárgy
szett-tag lesz, a rendszer elveszti a „ritka, célzott gyűjtés" jellegét.

### B61. Közös frakció-munkaprojekt (közmunka)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A király által kiírt, tagok által anyaggal/idővel közösen finanszírozott
infrastruktúra-fejlesztés (pl. egy új őrposzt vagy a frakció-fejlesztési fa egy tétele),
ahol a HOZZÁJÁRULÁS (nem csak a király pénze) számít.
**Hogyan működne:** `/faction project start <id>` a B7 fejlesztési fa egy tételét
„közmunka" módba teszi: a szükséges anyagmennyiséget (config, nem valuta) bárki
befizetheti (`/faction project contribute`), egy `WorkProjectManager` YAML-ban vezeti a
haladást; a küszöb elérésekor a fejlesztés aktiválódik, és a legtöbbet hozzájáruló
tagok egy kis, egyszeri megnevezés-jutalmat kapnak (pl. a krónikában B15 megemlítve).
**Miért jó:** A frakció-fejlesztést (B7) ma pusztán a király kasszája fizeti — ez egy
kooperatív réteget ad, ahol a rangon aluli tagok is érdemben hozzájárulhatnak, ami
erősíti a „közös projekt" érzést a puszta „a király vásárol" helyett.
**Építőkövek:** B7 frakció-fejlesztési fa, `YamlStore` haladás-számláló, heti krónika (B15).
**Buktatók:** Az anyag-alapú hozzájárulás könnyen a leggazdagabb/legaktívabb tagok
projektjévé válhat — érdemes egy fő/nap felső korlátot beépíteni a hozzájárulásra,
hogy a részvétel szélesebb körben oszoljon el.

### B62. Kölcsönös felszerelés-kölcsönzés (party/céh gear lending)
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Party- vagy céhtagok között ideiglenesen kölcsönadható, PDC-vel nyomon
követett tárgy — a kölcsönadó bármikor visszakérheti, a rendszer emlékezteti a kölcsönbe adottakra.
**Hogyan működne:** `/lend <játékos>` a kézben tartott tárgyra egy `loaned_from`/
`loaned_at` PDC-párt ír; a kölcsönadó `/lend recall` paranccsal bármikor jelezheti az
igényt (a kölcsönvevő HUD-on/action barban kap emlékeztetőt), a tárgy visszaadásakor a
PDC-tag törlődik. Nincs kényszerített visszavétel — bizalmi mechanika, csak nyomon követés
és emlékeztetés.
**Miért jó:** Kis munka, de konkrét, mindennapi közösségi súrlódást (ki adott kölcsön
mit kinek, ki felejtette el visszaadni) old fel strukturáltan a party/céh (B35) belső
életében — a bizalom infrastruktúrája, nem a végrehajtása.
**Építőkövek:** `PartyManager`/céh (B35) tagság, PDC-tag, HUD/action bar emlékeztető.
**Buktatók:** Nincs kikényszerítő mechanizmus (a kölcsönvevő elméletileg megtarthatja/
eladhatja a tárgyat) — ezt a lore-szöveg és a parancs-üzenet is egyértelműsítse: ez
bizalmi eszköz, nem tulajdon-védelem.
