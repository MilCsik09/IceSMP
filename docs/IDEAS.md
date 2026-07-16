# Ötlettár (IDEAS)

Fejlesztési ötletek gyűjtője — **nincs elköteleződés**, ez a brainstorm-réteg. Ami innen
zöld utat kap, az a [ROADMAP.md](../ROADMAP.md)-be kerül tervezett tételként; a technikai
adósság külön él a [REFACTOR_CANDIDATES.md](REFACTOR_CANDIDATES.md)-ben.

Jelölés minden tételnél: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) •
`[TOP]` = ajánlott következő kör.

---

## A) Meglévő mechanika átdolgozása / polish

> **Státusz:** A1–A16 ✅ IMPLEMENTÁLVA (3 hullámban; teszt-checklist: PLAYTEST.md).
> A17 új felvétel — külön kör, nincs implementálva.

### A1. CC-audit — „mob-vak" effektek felderítése `[TOP]`
🟢 • ⭐⭐⭐ — A Megzavarásnál derült ki, hogy a látás-zavaró effektek (blindness/darkness/nausea)
mobokra hatástalanok. Ugyanez a hibaosztály máshol is ott lehet: az ÖSSZES CC-spell átnézése
„játékos vs mob" szemmel, és ahol kell, a Megzavarás-recept (mobra aggro-törlés + slowness,
játékosra képernyő-effekt). Megelőzi a „ez nem csinál semmit?" playtest-jelentéseket.

### A2. Party-tudatos AoE spellek `[TOP]`
🟡 • ⭐⭐⭐ — Az AoE spellek (Forgószél, Csi Hullám, Földrengés, splash-ek…) a saját
csapattársat is ütik. Központi `SpellTargetingUtil.isHostile(caster, target)` szűrő
(party-tag + configolhatóan azonos frakció kivéve), és minden AoE/splash azt használja.
Enélkül a csapatos PvE/PvP frusztráló.

### A3. Kaszt-erőforrás identitás (regen-profilok)
🟡 • ⭐⭐⭐ — Most minden kaszt erőforrása egyformán regenerál. WoW-osan: **Düh** harcban
töltődik (ütés ad, tétlenség csökkenti), **Energia** kicsi pool + gyors regen, **Mana** nagy
pool + lassú regen, gyógyítóknál heal-visszatöltés. A ResourceManager központi — egy helyen
megoldható, configból kaszt-onként hangolható. A kasztok *érzésre* különböznének.

### A4. Spell-kedvencek a görgetéshez
🟢 • ⭐⭐ — 20+ feloldott spellnél sok a shift+görgetés. A Spellbookban „kedvencnek jelölés"
(shift-katt), a görgetés csak a kedvenceket lépkedi (üres lista = minden). PDC-lista a
meglévő kiválasztás-index mellé.

### A5. First-join onboarding quest-lánc
🟡 • ⭐⭐⭐ — A semleges spawn + hírnök-NPC + quest-rendszer kész, csak összefűzés kell:
auto-induló kezdő lánc („Beszélj a hírnökkel" → „Válassz királyságot" → „Igényeld a
katalizátort" → „Ölj 5 szörnyet" → „Nyisd meg a /profile-t"). TALK_TO_NPC objektíva már van.
Az új játékos első 10 perce vezetett.

### A6. Aukció/piac polish + /market stats
🟢 • ⭐⭐ — A REFACTOR_CANDIDATES 16-os tétele (buy-out tooltip, tényleges levont ár az
üzenetben) + `/market stats`: legkeresettebb itemek, átlagárak, forgalom. A dinamikus
árfolyam-gazdaságnak jót tesz a láthatóság.

### A7. Katalizátor-cooldown vizuálisan a hotbaron
🟢 • ⭐⭐⭐ — `Player#setCooldown(material, ticks)` a katalizátor anyagára cast után: a
vanília szürke „töltődés-overlay" jelenik meg az itemen — azonnal látszik, mikor
castolhatsz újra, action-bar számolgatás nélkül. Pár soros, látványos polish.

### A8. Sebzés-számok / hit-visszajelzés
🟡 • ⭐⭐ — Spell-találatkor lebegő sebzés-szám (TextDisplay-infra már van a
hologramokhoz), kritikus/kombó színnel. Config-kapcsolóval, hogy aki utálja, kikapcsolja.

### A9. Halál-összegző (death recap)
🟡 • ⭐⭐ — Halál után kattintható chat-összegző: ki/mi ölt meg, az utolsó 5 mp sebzés-forrásai
(spell-nevekkel). PvP-szerveren a „mi ölt meg??" örök kérdés; a combat-eventek már átfolynak
a plugin listenerein, csak egy rövid ring-buffer kell per player.

### A10. Világboss-telegraph polish
🟢 • ⭐⭐ — A SLAM/ZONE speciálok jelzése hangosabb/olvashatóbb: boss-bar villanás a speciál
előtt, kör-alakú partikel-ring a becsapódás helyén (a meglévő telegraph-ra rá lehet építeni),
külön hang a SLAM/ZONE/SUMMON-hoz.

### A11. Spellbook rendezés/szűrés
🟢 • ⭐ — Feloldott spellek elöl, szint szerint rendezve; „csak feloldottak" kapcsoló.
Tisztán GUI-munka.

### A12. HUD-testreszabás
🟡 • ⭐⭐ — `/hud toggle <szekció>`: a HUD-oldalsáv szekciói (párt, erőforrás, esemény-sáv)
egyenként ki-bekapcsolhatók, PDC-ben tárolva. A HudManager soronként épít, könnyen kapuzható.

### A13. Claim-GUI
🟡 • ⭐⭐ — (ROADMAP-örökség.) A claim-műveletek (trust/untrust, y-bővítés, raid-lootable
kapcsoló, határ-mutatás) egy GUI-ban a `/menu` CLAIM almenüjéből — a parancsok maradnak a
logika, a GUI csak delegál (RUN:-minta).

### A14. „Mi történik most?" esemény-oldal
🟢 • ⭐⭐ — `/events status` (mindenkinek): épp aktív világesemények listája hátralévő idővel +
a `/menu` EVENTS almenü élő adatokkal. Az esemény-managerek állapota megvan, csak ki kell
olvasni.

### A15. Statisztika-profil
🟡 • ⭐ — `/stats [név]`: killek, halálok, K/D, castolt spellek száma, ledolgozott questek. A
StatsManager ranglista-infrája bővíthető; vigyázat: minden új számláló hot-path írás.

### A16. Kombó-rendszer mélyítés
🟡 • ⭐⭐ — A spell-kombó (pár + gyorsabb felépülés) létezik. Bővítés: 3 lépcsős kombó-láncok
kaszt-onként, kombó-finisher bónusz (a 3. spell +X% erő), HUD-jelzés a kombó-ablakról.

### A17. Teljes HP-rendszer átdolgozása `[TULAJ KÉRÉSE — kidolgozott javaslat, külön körben]`
🔴 • ⭐⭐⭐ — A vanília 20 HP + étel-regen nem illik egy MMO-jellegű szerverhez: minden kaszt
ugyanannyit bír, a harc kimenetelét a golden apple / étel-spam dönti el, a spell-sebzésszámok
pedig a 10 szíves skálán túl durva lépcsőkben hangolhatók. Javasolt átdolgozás (fázisokban):

1. **Kasztonkénti alap-HP profilok** — `GENERIC_MAX_HEALTH` attribútum-módosítóval (a
   talent-rendszer max-health effektje már pontosan így dolgozik, ugyanaz a plumbing):
   tank/melee (harcos, paplovag, halállovag) ~26-30 HP, hibrid (szerzetes, démonvadász,
   sámán, druida) ~22-24, íjász/orgyilkos ~20, caster (varázsló, pap, boszorkánymester,
   evoker) ~16-18. Config: `classes.yml` → `<kaszt>.base-health`, szint-skálázással
   (`health-per-level`, pl. +0,2/szint, cap).
2. **Szív-kijelzés normalizálás** — `player.setHealthScale(20)` mindenkinél, hogy a több
   HP ne csúfítsa el a hotbart (a tényleges értéket a HUD/`/stats` mutassa számmal).
3. **Regen-átdolgozás** — a vanília saturation-regen kikapcsolása (gamerule vagy
   `naturalRegeneration` off + saját tick): étel = éhség-költség fedezet, a gyógyulás
   forrása harcon kívüli lassú regen (pl. 1 HP/2 mp, 8 mp-cel az utolsó sebződés után —
   a ResourceManager lastCombat mintája újrahasznosítható) + gyógyító spellek/szakma-ételek.
   Így a healer-spec és a Szakács tényleges értéket kap.
4. **Sebzés-újrahangolás** — a spell-balance értékek átnézése az új HP-skálán (a
   spell-balance.yml live-read rendszere miatt ez restart nélkül iterálható); a
   sebzés-számok (A8) és a halál-összegző (A9) adja hozzá a visszajelzést, a C1
   spell-statisztika az adatot.
5. **Pajzs/absorption egységesítés** — a meglévő pajzs-jellegű effektek (Devotion Aura
   reflect, Bulwark, absorption-adó spellek) közös „shield" rétegbe terelése, hogy a
   HUD-on és a death recapben is egyértelmű legyen.
6. **Kapcsolódások** — talent max-health effekt az ÚJ alapra épüljön (százalékos, ne fix);
   faction-passzívák és relikviák HP-bónuszai auditálandók; PLAYTEST külön fejezetet kap.

Kockázat: minden harci rendszert érint (spell-balansz, mob-skálázás, világboss, raid) —
külön ágon, teljes playtest-körrel érdemes, NEM a mostani A-hullám része.

## B) Új mechanika

### B1. Heti Királyi Megbízások (battlepass-lite) `[TOP]`
🟡 • ⭐⭐⭐ — Heti 5–7 feladat frakció-onként (quest-keretrendszer + közösségi célok infráján),
pontokért → hét végén kassza-jutalom + a legaktívabb tagoknak buff/kozmetika. Pénz-semleges
(szerver-elv-kompatibilis), heti visszatérési ok — SMP-retention királya.

### B2. Duel/párbaj-rendszer téttel
🟡 • ⭐⭐⭐ — `/duel <név> [tét]`: mindkét fél tétje escrow-ba (piaci licit-zárolás mintája),
győztes viszi, a díj egy része ELÉG (money sink). Beleegyezéses → bűn-rendszert nem érinti.
A raid területkötése újrahasznosítható aréna-zónának.

### B3. Kézzel épített dungeonök kulcs-itemmel
🔴 • ⭐⭐⭐ — (ROADMAP-irány konkretizálva.) Szerver-csapat épít, admin kijelöli (`/territory`
DUNGEON típus), belépés kulcs-itemért (frakció-valuta — sink), bent MobScaling-skálázott
mobok + boss (világboss-archetípus infra) + heti lockout PDC-ben. Szinte minden alkatrész
megvan, „csak" összeszerelés.

### B4. Pet-képességek
🟡 • ⭐⭐ — Petenként egy aktív képesség (farkas: provokál, macska: gyorsítás, bagoly:
éjjellátás-aura) pet-szint kapuval, a spell-infra mintájára. A pet-tartásnak célt ad.

### B5. Területi erőforrás-pontok (elfoglalható bányák)
🔴 • ⭐⭐⭐ — Kijelölt „lelőhely"-zónák (territórium-infra): a birtokos frakció kasszája
óránként termel belőlük — ÚJ pénz helyett inkább nyersanyagot/szakma-XP buffot (faucet-elv
miatt). Raid-célpontként működnek → állandó territoriális konfliktus-ok. A war-szerver
identitás fő motorja lehetne.

### B6. Játékos-indított karaván (szállítmány-rablás)
🔴 • ⭐⭐ — A karaván-esemény player-verziója: frakció indít szállítmányt A→B (kasszából
finanszírozva), célba érve bónusz; útközben a többi frakció rabolhatja (raid-jelentkezés
mintájára). Kockázat/jutalom gazdasági minijáték + PvP-tartalom.

### B7. Frakció-fejlesztési fa (kassza-sink)
🟡 • ⭐⭐⭐ — A kasszapénz most főleg áll. Tartós frakció-fejlesztések vásárlása: +X% szakma-XP
a tagoknak, olcsóbb claim-oszlop, gyorsabb erőforrás-regen — mind égetett áron (sink), a
király dönt. A kasszának cél, a királyságnak progresszió.

### B8. Natív crate-rendszer
🟡 • ⭐⭐ — (ROADMAP-örökség, CrazyCrates-kiváltás.) Frakció-valutás kulcs (égetett ár — sink),
LootTable-alapú tartalom, látványos nyitás-animáció (TextDisplay). Pénz-semleges jutalmakkal.

### B9. Kozmetikák GUI-ból
🟡 • ⭐⭐ — (ROADMAP-örökség.) Részecske-nyomok, kalapok, halál-üzenetek — valutáért (sink), a
szezon-jutalmakkal összekötve (a győztes szezon exkluzív kozmetikát kap).

### B10. Napi vadász-cél (PvE fejvadászat)
🟢 • ⭐⭐ — Naponta sorsolt elit mob („A Sebhelyes Medve a fagyott tónál") koordináta-körzettel;
első elejtő viszi a lootot. A WildHunt-infra kis általánosítása napi ritmusra.

### B11. Ostromgépek a raidhez
🔴 • ⭐⭐ — A SiegeWeaponFactory már létezik — kiterjesztés: telepíthető katapult/ballista
(raid alatt, támadó oldalnak), ami a védmű-blokkokat töri (a claim-védelem raid-szabályaival
egyeztetve). Nagy munka, de a raidet epikussá teszi.

### B12. Királyi politika: adó- és kincstár-döntések
🟡 • ⭐⭐ — A király állíthassa az állampolgári adót egy sávon belül, hirdethessen
kassza-osztalékot, írjon ki frakció-célt (közösségi cél infra). A királyválasztásnak tétje
lesz — most főleg cím.

### B13. Piaci vételi megbízások (buy order)
🟡 • ⭐⭐ — „Veszek 64 vasat 20-ért" — fordított piac: a vevő pénze escrow-ba, bárki
teljesítheti. A MarketManager listing-infrája tükrözhető. A gazdaság likviditását növeli.

### B14. Frakció-raktár (közös láda jogosultságokkal)
🟡 • ⭐⭐ — A DonationChest-infra általánosítása: frakció-szintű virtuális raktár
(rang-alapú betét/kivét jog, napló). A „ki lopta el a kasszából" drámák strukturált mederbe
terelése.

### B15. Heti krónika (auto-újság)
🟢 • ⭐ — Hét végén auto-generált könyv-item / chat-összefoglaló: raid-eredmények,
szezon-állás, legnagyobb piaci üzlet, új körözöttek. A meglévő statokból összerakható —
a szerver „élő világ" érzését erősíti.

### B16. Mentor-rendszer
🟡 • ⭐ — Veterán + új játékos párba áll (`/mentor`), közös questek, mindkettő pénz-semleges
jutalmat kap (XP/buff). Retention + közösség-építés; a party-infra újrahasznosítható.

### B17. Kaszt-próba arénák (hullám-túlélés)
🟡 • ⭐⭐ — A parkour-próba harci párja: kijelölt arénában túlélj N hullámot (InvasionManager
hullám-logikája újrahasznosítható) — a kaszt-mester questlánc következő lépcsője lehet.

### B18. Térkép-híd (BlueMap/Dynmap)
🟡 • ⭐⭐ — Soft-depend reflexiós híd (integration/-minta): territórium-zónák és fővárosok
kirajzolása a webtérképre, claimek opcionálisan. A war-szervernek a „hol a front" láthatóság
sokat ad.

### B19. Évszakos világ-modifikátorok
🟢 • ⭐ — A szezonhoz kötött finom világ-hangolás configból: télen gyakoribb fagy-események,
nyáron bőség-idő — a meglévi esemény-súlyok szezon-szorzói. Olcsó „élő világ" réteg.

### B20. Relikvia-reforge + presztízs/paragon
🔴 • ⭐⭐ — (ROADMAP end-game tételek.) Relikvia-újrakovácsolás ritka anyagból (sink), max
szint utáni paragon-pontok apró, additív bónuszokkal. Csak akkor, ha a törzs-játékosok már
„kimaxoltak".

## C) Admin / infra ötletek

### C1. Spell-használati statisztika (balansz-adat) `[TOP]`
🟢 • ⭐⭐⭐ — Számláló: melyik spellt hányszor castolják (memóriában + napi YAML-dump),
`/icesmp balance report` táblázat. A következő balansz-kör így ADATból dolgozik, nem csak
érzésre — pont ehhez a playtest-kultúrához való.

### C2. Playtest-mód kapcsoló
🟢 • ⭐⭐ — `/icesmp playtest on|off`: egy kapcsolóval cooldown/idő-kapuk 90%-os csökkentése
(a PLAYTEST.md most kézzel állíttatja át ezeket). Overlay-jellegű config-felülbírálás a
meglévő ingame config-rendszerre építve.

### C3. Gazdasági faucet/sink monitor
🟢 • ⭐⭐ — Heti összesítő a logba/fájlba: mennyi pénz keletkezett (kill-reward, quest…) és
égett el (díjak, boltok, claimek) forrásonként. A „nincs addolt pénz" elv őre — infláció-gyanú
esetén azonnal látszik, melyik csap folyik.

### C4. Kill-reward / gazdaság szimulátor-parancs
🟡 • ⭐ — `/icesmp simulate <óra>`: durva becslés, mennyi pénz/XP termelődne N óra átlagos
játékkal a jelenlegi configon. Balansz-döntésekhez gyors szanity-check.

---

## Ajánlott következő kör (értéк/munka arány szerint)

1. **A2 Party-tudatos AoE** — enélkül a friss spell-munka PvP-értéke csorbul.
2. **A1 CC-audit** — olcsó, és a Megzavarás-minta kéznél van.
3. **B1 Heti Királyi Megbízások** — a legjobb érték/munka arányú új tartalom.
4. **A7 Katalizátor-cooldown a hotbaron** + **A5 onboarding-lánc** — két gyors, látványos győzelem.
5. **C1 Spell-statisztika** — hogy a 2. balansz-kör már adatból menjen.
