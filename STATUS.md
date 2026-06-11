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

### 2. Kaszt (class) rendszer
- 4 alap kaszt: **Varázsló, Harcos, Íjász, Orgyilkos** (`JobType`), PDC-ben tárolva
- Elsődleges + másodlagos kaszt; a másodlagos csak az elsődleges **max szintje (50)** után
  választható; kiválasztás a Job GUI-ból (`/profile` → kasztok)
- Szintezés: `szint = XP/100 + 1`, max 50
- **Kaszt XP mob ölésből** (`ClassXpListener`): alap 5 XP + skálázott mob szintenként +2;
  másodlagos kaszt 50%-ot kap; csak ellenséges mobok (mind configolható)
- **Automatikus spell-feloldás** szint alapján (`classes.<kaszt>.spell-unlocks` config),
  minden XP-úton érvényesül (kill XP, admin parancsok), üzenettel
- Admin parancsok: `/job addxp | setxp | status | unlockspell | givespellbook | listspells | admin`

### 3. Specializációk
- **Kasztonként 2 spec** (`SpecializationType`): Elementalista/Nekromanta (varázsló),
  Berserker/Védelmező (harcos), Mesterlövész/Vadmester (íjász), Méregkeverő/Fantom (orgyilkos)
- Csak az **elsődleges** kaszt specializálódhat, a 25. szinttől (configolható); a választás
  végleges (admin `/spec reset` törölheti)
- **Nekromanta = a Varázsló sötét specializációja**: csak DARK frakcióval ÉS sinner
  státusszal választható
- Spec-spellek kaszt-szinthez kötött feloldása (`specializations.<spec>.spell-unlocks`),
  a JobManager XP-hookján keresztül minden XP-változásnál ellenőrizve
- **Szakma-specializációk** is (2/szakma): Fegyverkovács/Páncélkovács, Aranyásó/Vájármester,
  Botanikus/Állattenyésztő, Horgászmester/Kincsvadász — a 25. szakmaszinttől
- `/spec list | choose | info | reset` parancs

### 4. Talent rendszer
- **Két ponttár**: kaszt (5 kasztszintenként 1 pont, elsődleges + másodlagos szintek összege)
  és szakma (10 szintenként 1 pont)
- Config-vezérelt talent definíciók (`talents.*.definitions`), alapból:
  Életerő (+2 max élet/rang), Fürgeség (+mozgás/rang), Erő (+0.5 sebzés/rang),
  Tudásszomj (+5% kaszt XP/rang), Szorgalom (+10% szakma XP/rang), Kitartás (+1 élet/rang)
- Attribútum-effektek valódi attribútum-módosítóként, belépéskor **idempotensen**
  újra-alkalmazva; XP-bónuszokat az XP-listenerek számítják be
- `/talent` (áttekintés + pontok), `/talent spend <class|profession> <talent>`

### 5. Szakma (profession) rendszer
- 4 szakma: **Kovács, Bányász, Földműves, Halász** (`ProfessionType`), PDC-ben, max 50 szint
- **Tevékenység-alapú XP** (`ProfessionXpListener`): érc bányászása (+5), érett termény
  betakarítása (+3), páncél/pajzs craft (+8), smithing (+15), horgászat (+4) — configolható
- `/profession join | info | list` + admin `set | addxp`

### 6. Craftolási korlátozások
- Config-vezérelt szabályok (`crafting-restrictions.rules`): anyaglista + kaszt- és/vagy
  szakma-követelmény szinttel; minden megadott feltételnek teljesülnie kell
- Craftoló asztal **és smithing asztal** eredményét is blokkolja, throttle-olt üzenettel
- Alapszabály: **netherite felszerelés csak 25+ szintű Kovácsnak**

### 7. Varázslat (spell) rendszer
- **21 regisztrált spell** (lista: [TECHNICAL.md 5. fejezet](TECHNICAL.md)) — ebből 6 új
  kaszt-skill: Sasszem, Sortűz, Árnyéklépés, Füstbomba, Életszívás, Csontfagy
- Varázskönyv item (PDC-tagelt): jobb katt = cast, sneak + ütés = spellváltás,
  action bar visszajelzéssel
- Költség rendszer (éhség vagy XP), cooldown rendszer (60 mp felett PDC-perzisztens,
  alatta memória), debounce védelem
- Session-állapot központi takarítása kilépéskor (`PlayerSessionCleanupListener`)

### 8. Relikvia rendszer
- Relic framework: config-vezérelt definíciók (vizuál, custom model data, triggerek,
  cooldown), PDC-tagelt itemek, craft-védelem
- **A Mételytépő** (Metelytepo) harci fejsze teljes mechanikával: sinner bélyegzés,
  Justice és Honor Eye képességek, élőhalott-fagyasztás
- **Tulajdonjog-nyilvántartás** (`relics.yml`): egy relicből csak 1 létezhet aktív
  tulajdonossal; **14 nap inaktivitás** után belépéskor füst effekttel törlődik és újra
  megszerezhető; last-seen belépéskor és kilépéskor is frissül
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
- Job GUI — kasztválasztás állapotjelzéssel (kiválasztott, szint, zárolt másodlagos)

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
| Szakma XP anti-abuse | A játékos által lerakott érc újrabányászása is ad Bányász XP-t (nincs blokk-eredet követés). |
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
