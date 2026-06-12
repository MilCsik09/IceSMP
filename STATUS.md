# IceSMP — Aktuális állapot

Ez a dokumentum pontosan leltározza, **mi van jelenleg kész** a pluginban, mik az ismert
korlátok, és mi az, ami még **nincs** implementálva. A lista a forráskód aktuális állapota
alapján készült.

Kapcsolódó dokumentumok: [README.md](README.md) (áttekintés) •
[TECHNICAL.md](TECHNICAL.md) (technikai referencia) • [ideas.md](ideas.md) (tervek)

---

## ✅ KÉSZ — teljesen implementált rendszerek

### 1. Frakció rendszer
- 4 frakció: **RED, BLUE, NEUTRAL, DARK** (`FactionType`), YAML perzisztencia (`factions.yml`)
- `/faction join | leave | set` parancsok tab-complete-tel
- **Passzív bónuszok** (`FactionPassiveListener`, configból kapcsolható):
  - RED: tűz/láva/forró blokk sebzés-immunitás
  - BLUE: fagyás-immunitás + 50% eséllyel lassított éhségvesztés
  - NEUTRAL: lopakodás közben láthatatlanság; nem-ellenséges mobok nem célozzák
  - DARK: wither-sebzés immunitás; élőhalottak (zombi, csontváz, phantom, zoglin) nem támadják
- **Sötét paktum szabályrendszer:**
  - A DARK frakcióba csak **sinner** státuszú játékos léphet be
  - Belépéskor `dark_pact` PDC jelölő — a sinner státusz **soha többé nem törölhető**
    (a `/sinner clear` ilyenkor megtagadja), frakcióelhagyás után is megmarad
  - Admin `/faction set <player> dark` is megpecsételi a paktumot (ha a célpont online)
- **Frakciókassza** (`FactionTreasuryManager`, `treasury.yml`): `/faction treasury` (megtekintés,
  admin: kivét a bankba), `/faction donate <összeg>` (adomány a saját valutából)
- **Állampolgári adó** (todo.md ötlet): időszakonként (alapból óránként) a nem mentesített
  frakciók polgárai a saját valuta-egyenlegük 2%-át a kasszába fizetik — offline polgárok is;
  a global region scheduleren fut (Folia-helyes); money sink a dinamikus árfolyamhoz
- **Bűn-számláló + automatikus száműzetés** (todo.md ötlet): gyilkosság = +1 bűn
  (`SinListener`), a küszöb (alapból 4) elérésekor automatikus száműzetés a Sötét frakcióba
  örök paktummal + szerver-broadcast; admin: `/sinner <player> add | status`
- **Királyok és választás** (`KingManager`, `kings.yml`): a tagok szavaznak
  (`/faction king vote`), min. szavazatszám + listavezetés = koronázás (broadcast);
  ciklus lejártakor a szavazás újraindul; admin `set`/`clear`; a király jogai:
  kassza-kivét és raid-hirdetés
- **Raid-rendszer** (`RaidManager`, ideas.md): a király hirdet (`/faction raid <frakció>`),
  nevezési díj a kasszából (money sink); a raid ideje alatt a hadviselő frakciók közti
  ölés **nem bűn**, hanem pontot ér; a végén a több ölést szerző fél a vesztes
  kasszájának 20%-át zsákmányolja (configolható; NEUTRAL védett); a lezáró időzítő a
  global region scheduleren fut

### 2. Kaszt (class) rendszer
- 4 alap kaszt: **Varázsló, Harcos, Íjász, Orgyilkos** (`JobType`), PDC-ben tárolva
- Elsődleges + másodlagos kaszt; a másodlagos csak az elsődleges **max szintje (50)** után
  választható; kiválasztás a Job GUI-ból (`/profile` → kasztok)
- Szintezés: **progresszív görbe** (az n. szintlépés ára `base-xp + (n-1)×increment`,
  configolható a `classes.leveling` alatt), max 50
- **Kaszt XP mob ölésből** (`ClassXpListener`): alap 5 XP + skálázott mob szintenként +2;
  másodlagos kaszt 50%-ot kap; csak ellenséges mobok (mind configolható)
- **Automatikus spell-feloldás** szint alapján (`classes.<kaszt>.spell-unlocks` config),
  minden XP-úton érvényesül (kill XP, admin parancsok), üzenettel
- Admin parancsok: `/job addxp | setxp | status | unlockspell | givecatalyst | listspells | admin`

### 3. Specializációk
- **Kasztonként 2 spec** (`SpecializationType`): Elementalista/Nekromanta (varázsló),
  Berserker/Védelmező (harcos), Mesterlövész/Vadmester (íjász), Méregkeverő/Fantom (orgyilkos)
- Csak az **elsődleges** kaszt specializálódhat, a 25. szinttől (configolható)
- **Respec frakcióvalutáért** (`/spec respec <class|profession>`, ár: `specializations.respec-cost`,
  money sink): a spec-kötött talentek pontjai automatikusan visszatérülnek; admin `/spec reset` ingyen töröl
- **Nekromanta = a Varázsló sötét specializációja**: csak DARK frakcióval ÉS sinner
  státusszal választható
- Spec-spellek kaszt-szinthez kötött feloldása (`specializations.<spec>.spell-unlocks`),
  a JobManager XP-hookján keresztül minden XP-változásnál ellenőrizve
- **Szakma-specializációk** is (2/szakma): Fegyverkovács/Páncélkovács, Aranyásó/Vájármester,
  Botanikus/Állattenyésztő, Horgászmester/Kincsvadász — a 25. szakmaszinttől
- `/spec list | choose | info | reset` parancs

### 4. Talent rendszer
- **Két ponttár**: kaszt (5 kasztszintenként 1 pont, elsődleges + másodlagos szintek összege)
  és szakma (az összes szakma szintjei összesen, 10 szintenként 1 pont)
- Config-vezérelt talent definíciók (`talents.*.definitions`): általános talentek (Életerő,
  Fürgeség, Erő, Tudásszomj, Szorgalom, Kitartás) + **WoW-szerű kötött talentek**:
  kaszt-kötött (`requires-job`), spec-kötött (`requires-spec`, 1/kaszt-spec, pl. Brutalitás,
  Lélekpaktum, Falkavezér) és szakma-kötött (`requires-profession`) talentek
- A kötött talenteket csak a feltételt teljesítő játékos látja/költheti, hatásuk a feltétel
  megszűntével (pl. respec) inaktiválódik, pontjaik respec-kor visszatérülnek
- Attribútum-effektek valódi attribútum-módosítóként, belépéskor **idempotensen**
  újra-alkalmazva; XP-bónuszokat az XP-listenerek számítják be
- `/talent` (áttekintés + pontok), `/talent spend <class|profession> <talent>`

### 5. Szakma (profession) rendszer — WoW-minta
- **8 szakma 3 kategóriában** (`ProfessionType` + `ProfessionCategory`):
  - Gyűjtögető (1 választható): Bányász, Gyógynövényész, Favágó
  - Készítő (1 választható): Kovács, Alkimista, Bűvölő
  - Másodlagos (mindenkié automatikusan): Halász, Szakács
- **Tevékenység-alapú XP** mind a 8 szakmának: érc, termény/virág/bogyó, rönk, páncélcraft +
  smithing, bűvölőasztal, főzet kivétele a főzőállványból, horgászat, étel sütése —
  mind configolható (`professions.xp.*`)
- **Szakmánkénti XP-tárolás** (`profession_xp_<id>` PDC): szakmaváltás után a régi szint
  megmarad és visszatanulható
- **Progresszív szintgörbe** (`professions.leveling`): minden szint egyre több XP
- `/profession join | info | list` + admin `set | clear | addxp`
- **Szakmánként 2 specializáció** (16 összesen): pl. Fegyverkovács/Páncélkovács,
  Főzetmester/Transzmutátor, Rúnamester/Arkanista, Séf/Hentes

### 6. Craftolási korlátozások
- Config-vezérelt szabályok (`crafting-restrictions.rules`): anyaglista + kaszt- és/vagy
  szakma-követelmény szinttel; minden megadott feltételnek teljesülnie kell
- Craftoló asztal **és smithing asztal** eredményét is blokkolja, throttle-olt üzenettel
- Alapszabály: **netherite felszerelés csak 25+ szintű Kovácsnak**

### 7. Képesség (spell) rendszer + Képesség Katalizátor
- **124 regisztrált spell**: 23 kézzel írt osztály + a `SpellCatalog` deklaratív definíciói
  (újrafelhasználható építőelemekből: `ConfiguredSpell` builder, `ProjectileBurstSpell`,
  `BlinkSpell`, `SummonMinionsSpell`, plusz egyedi osztályok mint a Farkashívás/Méhraj)
- **Minden kaszt és specializáció legalább 10 saját, egyedi képességet tanul** (4 kaszt +
  8 spec = 12 pool, átfedés nélkül); a feloldási szintek configolhatók
- **WoW-stílusú idézések** (`MinionManager` + `MinionProtectionListener`): a Nekromanta
  zombihordát (Holtak Hada) és Csontíjászokat idéz, a Vadmester Pandaőrséget és Vad Falkát —
  a minionok gazda-jelöltek (sosem fordulnak a gazdájuk ellen), automatikusan célpontot
  vesznek fel, és lejáró élettartamuk van (per-entity scheduler, Folia-helyes)
- **Kaszt-tematikus Képesség Katalizátor** (`CatalystItemFactory`, `is_ability_catalyst` PDC):
  Mágikus Kódex (varázsló), Harci Kürt (harcos), Vadásztarsoly (íjász), Árnyékamulett (orgyilkos)
- Interakciók (`AbilityCatalystListener`): jobb katt = cast; sneak + ütés = spellváltás
  **kaszt-specifikus hanggal** és a spell nevét + költségét mutató action barral
- Megszerzés: Job GUI katalizátor-gomb (saját igénylés, duplikáció-védelemmel) vagy admin
  `/job givecatalyst`; craft/kemence-védelem (`CatalystCraftSafetyListener`)
- Költség rendszer (éhség vagy XP), cooldown rendszer (60 mp felett PDC-perzisztens,
  alatta memória), debounce védelem
- Session-állapot központi takarítása kilépéskor (`PlayerSessionCleanupListener`)

### 7.5 Quest-keretrendszer
- **Config-vezérelt küldetések** (`quests.<id>`, `QuestManager`): 6 objective-típus
  (KILL_MOBS szinttel/típussal, BREAK_BLOCKS, CRAFT_ITEMS, CATCH_FISH, VISIT_TERRITORY,
  REACH_LEVEL), lánc-feltételek (requires-quest) + kaszt/frakció/szint kapuk
- Haladás PDC-ben, action bar visszajelzéssel; jutalmak: kaszt XP, valuta, spell-feloldás,
  **cleanse-sins** (a sötét paktum megtörése)
- `/quest list | info | accept | abandon` + admin `complete`
- **Beépített questek**: 4 kaszt-próba, Sötét Beavatás (a Nekromanta spec quest-kapuja:
  `specializations.necromancer.required-quest`), és a 3 részes **vezeklés-lánc** — a
  teljesítése az egyetlen visszaút a sötét paktumból (todo.md/ideas.md ötlet)

### 8. Relikvia rendszer
- Relic framework: config-vezérelt definíciók (vizuál, custom model data, triggerek,
  cooldown), PDC-tagelt itemek, craft-védelem
- **A Mételytépő** (Metelytepo) harci fejsze teljes mechanikával: sinner bélyegzés,
  Justice és Honor Eye képességek, élőhalott-fagyasztás
- **Tulajdonjog-nyilvántartás** (`relics.yml`): egy relicből csak 1 létezhet aktív
  tulajdonossal; **14 nap inaktivitás** után belépéskor füst effekttel törlődik és újra
  megszerezhető; last-seen belépéskor és kilépéskor is frissül
- **4 frakció-elytra relikvia** (ideas.md/todo.md): Főnix-szárny (RED: tűz/láva-immunitás,
  zuhanás = lángvihar), Zúzmara-szárny (BLUE: fagyimmunitás, felszálláskor fagyaura),
  Vándorszél (NEUTRAL: nincs zuhanósebzés, széllökés-boost), Csontszárny (DARK: éjjeli
  repüléskor árnyforma) — csak a tulajdonos ÉS a megfelelő frakció tagja használhatja
- **Fegyver-relikvia PvP gazdacsere** (todo.md szabály): a megölt játékos droppolt
  fegyver-relikviái (`relics.weapon-relics`) a gyilkosé lesznek (item PDC + relics.yml
  átírva); a passzív relikviák védettek
- `/relic list | give` parancs

### 9. Gazdaság — valuták és dinamikus árfolyam
- 4 valuta (RED/BLUE/NEUTRAL/DARK token), fizikai itemként (PDC + custom model data) és
  banki egyenlegként (`currency-balances.yml`)
- `/bank balance | deposit | withdraw` — item ↔ bank konverzió
- `/currency balance | pay | set | exchange | rates`
- **Dinamikus, kínálat-alapú árfolyam** (`ExchangeRateService`):
  `érték = alapérték × clamp((referencia-kínálat / kínálat)^rugalmasság, min, max)` —
  a túltermelt valuta leértékelődik; váltási díj configból; `/currency rates` élő kijelzés;
  fix árfolyam fallback, ha a dinamikus ki van kapcsolva

### 10. Távolság-alapú mob skálázás
- `CreatureSpawnEvent`-re: szint = vízszintes távolság a world spawntól / 1000 blokk
  (configolható), max 10
- Szintenként +2 max élet, +1 sebzés (attribútum-alapú), `[Lvl X] <mobnév>` névcímke
  (kliens-oldalon lokalizált mobnévvel), `mob_level` PDC a továbbfelhasználáshoz
- Csak ellenséges mobok; spawner/parancs/plugin spawnok kihagyva (farm-védelem)

### 11. Frakcióterületek
- Kör alakú claimek + frakciónként 1 főváros, `territories.yml` perzisztenciával
- Admin parancs: `/territory setcapital | claim | remove | list | info`
- Határátlépéskor action bar kijelzés (terület / főváros / vadon), blokk-váltásra szűrve
- Opcionális **építésvédelem** (`territory.protection.enabled`, alapból ki): idegen frakció
  területén break/place tiltás, bypass joggal

### 12. Profil és GUI-k
- `/profile` — profil könyv (név, frakció, mind a 4 valuta-egyenleg, bank gombok) + GUI
- Job GUI — kasztválasztás állapotjelzéssel (kiválasztott, szint, zárolt másodlagos) +
  **katalizátor-igénylő gomb** (tematikus item, duplikáció-védelemmel)

### 13. Infrastruktúra
- Folia-kompatibilis (`folia-supported: true`, szinkron taskok, `teleportAsync`)
- Lokalizáció: minden játékos-szöveg a `messages.yml`-ből (legacy `&` + MiniMessage)
- Build: Gradle + verziókatalógus (`gradle/libs.versions.toml`), Java 21
- Dokumentáció: README, TECHNICAL.md, AGENTS.md, ideas.md, ez a STATUS.md

---

## ⚠️ ISMERT KORLÁTOK / részben kész

| Terület | Korlát |
|---|---|
| **Build verifikáció** | A felhő-környezetben a PaperMC repo nem elérhető, így a legutóbbi változások **fordítása helyben ellenőrizendő** (`gradlew build`). Automatizált teszt nincs a projektben. |
| `/icesmp reload` | Csak a `config.yml` + `messages.yml` töltődik újra; a betöltéskor cache-elő managerek (mob scaling, craft szabályok, relic definíciók, territory) értékeihez teljes újraindítás kell. |
| Relic ability registry | A `RelicAbilityRegistry` üres — config `ability-id`-ra hivatkozó trigger warninggal no-opol. A Mételytépő képességei saját listeneren át működnek (ez szándékos), de új relic ability-khez regisztráció kell. |
| `config.yml` `messages:` szekció | Örökölt duplikátum, a kód **nem használja** (a forrás a `messages.yml`) — törölhető lenne. |
| Specializáció GUI | A specializációk csak paranccsal (`/spec`) érhetők el, a Job GUI nem mutatja őket. |
| Hide + Semleges passzív | A NEUTRAL lopakodás-láthatatlanság és a Hide spell ugyanazt a potion effektet használja — lopakodás abbahagyása lebonthatja a Hide láthatatlanságát. |
| Szakma XP anti-abuse | A játékos által lerakott érc/rönk újrabányászása is ad XP-t (nincs blokk-eredet követés). Az Alkimista XP a főzőállvány eredmény-slotjának kattintásához kötött heurisztika — ki-be pakolással duplán is jóváírható. |
| Területvédelem hatóköre | Csak blokk törés/lerakás ellen véd; láda-hozzáférést, robbantást, vödröt, pisztont nem kezel. |
| Dinamikus árfolyam kínálata | Csak a **banki** egyenlegeket számolja; a játékosoknál lévő fizikai tokenek nincsenek a kínálatban. |
| Dark pact offline célpontra | Admin `/faction set <offline játékos> dark` esetén a paktum csak a játékos következő online állapotában pecsételhető (PDC-hez online játékos kell) — jelenleg ilyenkor nem pecsételődik automatikusan. |
| Sinner = bináris flag | Nincs bűn-számláló (a todo.md "4–5 bűn után automatikus száműzetés" ötlete nincs implementálva). |
| Audit jelzések | Az `ICE_SMP_AUDIT_REPORT.md`-ben jelzett örökölt problémák részben nyitottak: `RainDance`/`SunDance` nagy szinkron blokk-iteráció, `HideSpell` páncél-backup csak memóriában, `ArmamentSpell` tele inventory esete, `MetelytepoManager` kisebb entitás-állapot szivárgás. |

---

## ❌ MÉG NINCS implementálva (tervek — ideas.md / todo.md)

- **Raid eventek** (10v10, király hirdeti, sinner tag felfüggesztés raid alatt)
- **Király-választás** és frakció-vezetési mechanika
- **Frakció-kassza, adórendszer, money sinkek**
- **4 frakció-elytra relikvia** (speciális képességekkel)
- **Fegyver-relikvia gazdacsere PvP-ben** (a passzív relikviák védettsége mellett)
- **Quest-keretrendszer** (kaszt-questek, nekromanta beavatás-quest, vezeklés-quest)
- **Ultimate képességek** és skill-fa GUI
- **Első belépéses intro** (title-szekvencia / kamera-út)
- **Világesemények** (világ-bossok, vérhold, szezonális liga)
- **Piactér / aukciósház**, árfolyam-kijelző táblák a fővárosokban
- **Bank kamat**
- Fővárosok közti távolság / világépítés — *nem plugin feladat* (a kijelölő eszköz, a
  `/territory`, kész)
