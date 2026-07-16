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

### A18. Spell-loadoutok (kedvenc-készletek)
🟢 • ⭐⭐ — Az A4 kedvencekre építve: 2-3 elmenthető kedvenc-készlet (pl. „PvP" / „farm" /
„boss"), váltás a spellkönyvből vagy `/spellbook loadout <n>`-nel. PDC-ben több csv-lista,
a görgetés mindig az aktív készletet lépkedi. Kaszt-játékérzet nagy dobása kis munkából.

### A19. Kombó-jelzések a spellkönyvben
🟢 • ⭐ — A spell-csempe lore-jába kerüljön be, ha a spell egy kombó-pár vagy lánc tagja
(„⚡ Kombó: Fagyérintés → EZ → Tűzgolyó"). A configból (pairs/chains) generálható, a
játékos a GUI-ból tanulja a láncokat, nem a wikiről.

### A20. Quest-tracker a HUD-on
🟡 • ⭐⭐ — Egy kiválasztott („követett") quest objektíva-állása az oldalsávon (a party-szekció
mintájára, A12 toggle-lal kapuzva). `/quest track <id>` + a küldetésnapló GUI-ból kattintva.
A haladás most csak action-barban villan — a tracker állandó jelenlétet ad.

### A21. Halál-pont visszajelzés
🟢 • ⭐⭐ — Halál után a chatben a halál koordinátái + világ (a death recap A9 mellé), és egy
rövid ideig élő irány-jelző (action bar iránytű: „⚰ 214 blokk ÉK felé"). A cucc-visszaszerzés
frusztrációját csökkenti; a lebomló sír (grave) a B-kategóriás nagyobb testvére.

### A22. Ranglisták bővítése az új statokból
🟢 • ⭐ — Az A15 számlálói (K/D, mob-ölés, spell-cast, quest) kerüljenek fel a
`/leaderboard`-ra új kategóriákként (a StatsManager Category enum + top() bővítése).
Olcsó, és a statisztika-profil így versennyé válik.

### A23. NPC-dialógus polish
🟢 • ⭐ — A quest-dialógusok kapjanak beszélőnkénti hangot (falusi hümmögés, kürt a
hírnöknél), írógép-effektet (soronkénti késleltetett kiírás a global schedulerrel) és
`<gomb>`-stílusú folytatás-jelzést. Tisztán prezentációs réteg a meglévő sendDialogue-ra.

### A24. Szakma-recept kedvencek + keresés
🟢 • ⭐ — A recept-katalógus GUI-ba keresőmező (anvil-input vagy chat-prompt minta a quest
builderből) és kedvenc-csillagozás (A4 PDC-mintája). 50+ receptnél a lapozgatás fájdalmas.

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

### B21. Bestiárium / gyűjtő-album
🟡 • ⭐⭐⭐ — Perzisztens „album": megölt mob-típusok, elkészített receptek, felfedezett
territóriumok, legyőzött boss-archetípusok pipálódnak (StatsManager/PDC számlálók).
Mérföldkő-jutalmak (10/50/100 faj) az achievement-infrán. Gyűjtögető-hajlamú játékosnak
hónapokra ad célt, és minden meglévő rendszert összefűz.

### B22. Címek (title-ök)
🟡 • ⭐⭐ — Elérésekből, szezon-helyezésből, bestiáriumból nyíló **címek** („Sárkányölő",
„A Fagy Ura"), egy választott cím a chat-prefixbe (a chat-formázó már saját) és a
PlaceholderAPI-ba. GUI a kiválasztáshoz, PDC-tárolás. Pénz-semleges presztízs — pont
amit a szerver-elv szeret.

### B23. Játékos-boltok (chest shop)
🔴 • ⭐⭐⭐ — Tábla+láda bolt a saját claimen (a claim-jogosultság a védelem): fix áras
adás-vétel offline is. A piac dinamikus árfolyama mellé a „falusi kisbolt" réteg;
tranzakció-díj ELÉG (sink). A MarketManager escrow-logikája újrahasznosítható.

### B24. Bank-lekötés (betét kamattal, sink-semlegesen)
🟡 • ⭐ — `/bank lockup <összeg> <7|14|30 nap>`: a lekötött pénz nem költhető, lejáratkor
a **kincstárból** (nem a semmiből!) fizetett prémium — a király dönthet a kamatlábról
(B12 politika-irány). Pénzt ültet ki a forgalomból = deflációs eszköz.

### B25. Heti lottó
🟢 • ⭐⭐ — Jegy frakció-valutáért (sink), heti sorsolás a befizetések X%-ából (a többi ELÉG).
Broadcast + krónika-hír a nyertesről. Kis munka (YamlStore + heti tick a szezon-scheduler
mintájára), nagy közösségi zaj.

### B26. Rúna-kovácsolás (enchant-kiegészítő szakma-ág)
🔴 • ⭐⭐⭐ — A Kovács/Varázsló közös végjátéka: rúna-itemek (PDC-tag, ItemFactory-minta)
craftolása ritka anyagokból, felhelyezés fegyverre/páncélra kis, TEMATIKUS bónuszokkal
(pl. +2% spell-erő, lassítás-esély). A talent/mastery mellé harmadik, ITEM-oldali
progresszió — loot-izgalmat ad a raritás-rendszer fölé.

### B27. Dungeon-affixek (kihívás-módosítók)
🟡 • ⭐⭐ — A B3 dungeonökhöz (vagy már a világboss/invázióhoz): heti rotálódó módosítók
(„Vérszomjas hét: +25% mob-sebzés, +50% loot"). Egy config-szekció + a MobScaling
szorzóira kötve; a heti krónika (B15) hirdeti. Ismételhető tartalom frissen tartása.

### B28. Kaszt-story questlánc
🟡 • ⭐⭐⭐ — Kasztonként 5-8 lépéses, `next`-láncolt (A5 infra!) story-küldetéssor a kaszt
szentélyéhez/identitásához kötve, a 25. szintű spec-választásig vezetve. A quest-rendszer
minden eleme (dialógus, elágazás, TALK_TO_NPC) készen áll — tartalom-írás a munka zöme.

### B29. NPC-reputáció
🔴 • ⭐⭐ — Nevezetes NPC-k (hírnök, kereskedők, céh-mesterek) felé külön hírnév-skála
(quest/karaván/eszkort teljesítésből), szintenként kedvezmény a boltjukban vagy exkluzív
recept/quest. A frakció-rendszertől független, „város-RPG" réteg.

### B30. Háború-ablakok (war window)
🟡 • ⭐⭐⭐ — Raid csak megadott idősávban indítható (pl. este 7-10, configból; a raid
indítás-ellenőrzésébe egy idő-kapu). Kiegészítés: „védett hétvége" a szezonzáró előtt.
A védő fél életminőségét óriásit javítja — off-time raidelés a #1 SMP-panasz.

### B31. Zsoldos-tábla
🟡 • ⭐⭐ — A király a kasszából vérdíj-szerű **megbízásokat** tűzhet ki (raid-védelem,
eszkort-kíséret, ellenséges relikvia-hordozó levadászása) az ExchangeBoard mintájára;
teljesítés-ellenőrzés a meglévő event-hookokból. A kassza értelmes elköltési iránya.

### B32. Építőverseny-esemény
🟢 • ⭐ — Admin kijelöl telkeket (`/territory` PLOT), a játékosok határidőre építenek, a
szavazás GUI-ból (fejek + katt). Jutalom kozmetika/cím (B22). Szinte csak GUI-munka a
territórium-infra fölött.

### B33. Szezonzáró világesemény („végítélet-hét")
🟡 • ⭐⭐⭐ — A szezon utolsó hetében eszkalálódó modifikátorok (sűrűbb vérhold, erősebb
invázió, dupla liga-pont), az utolsó napon szerver-boss a fővárosnál. A meglévő esemény-
managerek ütemezett kombinálása + broadcast-dramaturgia — a szezonoknak íve lesz.

### B34. Lebomló sír (grave) halálkor
🟡 • ⭐⭐ — Halálkor a cucc egy védett „sír"-blokkba kerül (csak a halott nyithatja, X perc
után mindenkinek szabad — a kincs-láda TreasureEvent kódja szinte egy az egyben jó erre).
A keep-inventory és a full-loot közti egészséges középút; A21 irány-jelzővel párban.

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

### C5. Discord-webhook híd
🟢 • ⭐⭐⭐ — A nagy broadcastok (világboss, raid-indítás, szezonzárás, király-választás,
lottó/krónika) egy configolható webhook-URL-re is kimennek (sima HTTP POST az async
schedulerről, plugin-függőség nélkül). A szerver élete kilátszik a Discordra = retention.

### C6. YAML-store integritás-őr + mentés
🟡 • ⭐⭐ — Induláskor minden PersistentStore-fájl parse-próbája; sérült fájlnál automatikus
`.bak`-ból helyreállítás (a saveAtomic mellé rotálódó 3 példányos backup). Egy rossz
kézi szerkesztés ma adatvesztés lehet — ez a biztosítás.

### C7. Admin audit-log
🟢 • ⭐⭐ — Minden admin-parancs (config set, item-adás, event-indítás, quest admin) egy
külön `logs/admin-audit.log`-ba (időbélyeg + név + parancs). Több adminos szerveren a
„ki állította ezt át?" kérdés megválaszolója. Egy közös helyre (Permissions/parancs-router)
beköthető.

### C8. Edzőbábu (training dummy)
🟢 • ⭐⭐⭐ — `/icesmp dummy spawn`: sebezhetetlen, visszagyógyuló ArmorStand/mob, ami
action-barban DPS-t és összesített sebzést jelez vissza (a DamageIndicator infra méri).
A spell-balansz teszteléshez (és a játékosoknak a rotáció-gyakorláshoz) alapeszköz.

### C9. Folia régió-teljesítmény riport
🟡 • ⭐ — `/icesmp perf`: régiónkénti TPS/MSPT top-lista (Folia API-ból), a legterheltebb
chunk-koordinátákkal. Lag-vadászathoz Folián a globális TPS semmitmondó — ez a valódi
diagnosztika.

### C10. Config-diff parancs
🟢 • ⭐ — `/icesmp config diff`: a jar-beli defaultoktól eltérő élő kulcsok listája (a
ConfigManager mindkét réteget látja). Frissítéskor/hibakereséskor azonnal látszik, mi
van felülbírálva — az ingame config-rendszer természetes párja.

## D) Világ, hangulat, közösség

### D1. Szezonális ünnepek
🟡 • ⭐⭐ — Naptár-vezérelt skin az eseményekre (október: tök-fejes invázió + „rém-éj" a
vérhold helyén; december: ajándék-ládás kincs-esemény, hó-hangulat). A meglévo esemény-
managerek paraméterezése + pár item/üzenet — kis munka, nagy „él a világ" érzet.

### D2. Városi hirdetőtábla
🟢 • ⭐⭐ — Játékos-hirdetések (kereslek/eladó/toborzás) egy GUI-táblán a fővárosban
(ExchangeBoard GUI-minta, X nap után lejár, feladás kis díjért — sink). A kereskedelmi
és közösségi élet organikus találkozóhelye.

### D3. Szezon-emlékművek
🟢 • ⭐⭐ — Szezonzáráskor a győztes frakció zászlaja/szobra (admin-épített helyszínen
tábla + fej + hologram-TextDisplay) a fővárosban, az MVP-k neveivel (StatsManager top).
A dicsőség fizikai nyoma a világban — a következő szezon motivációja.

### D4. Hangulat-rétegek bővítése
🟢 • ⭐ — Az ambient-eseményekhez (aurora, köd, vándorlás) finom hang-réteg (vanília
hangokból komponált „zene"), és ritka mikro-események: hullócsillag-eső éjjel, szentjános-
bogár raj a mocsárban. Tisztán atmoszféra, a meglévő AmbientEventManager bővítése.

### D5. Kocsma (social hub) ital-buffokkal
🟡 • ⭐⭐ — A Szakács/Sörfőző (Szerzetes-spec tematika!) készíthet **italokat** (recept-
katalógus itemek), amik a fővárosi kocsmában fogyasztva rövid, NEM harci buffokat adnak
(szakma-XP bónusz, szerencse). Találkozóhely + szakma-sink + tematikus flair egyben.

---

## Ajánlott következő kör (értéк/munka arány szerint)

*(Az A1–A16 kör ✅ kész — az ajánlás a maradékra frissítve.)*

1. **B1 Heti Királyi Megbízások** — továbbra is a legjobb érték/munka arányú új tartalom.
2. **C1 Spell-statisztika + C8 edzőbábu** — a 2. balansz-kör adatból és mérésből menjen.
3. **B30 Háború-ablakok** — kis munka, a védők életminőségének legnagyobb dobása.
4. **A18 Spell-loadoutok + A22 ranglista-bővítés** — két gyors győzelem a friss A-infrán.
5. **C5 Discord-híd + B25 heti lottó** — közösségi zaj, retention.
6. **A17 HP-rendszer átdolgozás** — a nagy kör, külön ágon, teljes playtesttel (B26 rúnák
   és B3 dungeonök UTÁNA érdemesek, az új HP-skálára hangolva).
