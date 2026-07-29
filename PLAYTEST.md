# IceSMP — Playtest Kézikönyv 🧪

Ez a dokumentum a **teszterek** támpontja: mit kell tesztelni, hogyan, és mi van/mi nincs a
pluginban. Végig lehet menni rajta pontról pontra. A jelölőnégyzeteket ( `[ ]` → `[x]` ) ki lehet
pipálni egy másolt példányban.

> **Jelölések:** ✅ = kész, tesztelendő • 🚧 = részben kész • ⏳ = nincs benne (ne teszteld) •
> ⚠️ = Folia-kritikus pont (külön figyelni a konzol-hibákra)

---

## 0. Környezet és build

- **Szerver:** Folia **1.21.11** (NEM sima Paper — a plugin Folia-szálkezelést használ).
- **Java:** 21.
- **Build:** `./gradlew build` → a jar a `build/libs/` alatt. (A plugin forrása fordul: a teljes
  kódbázis lefordul `javac 21`-gyel a Paper 1.21.11 API ellen.)
- **Opcionális függőség:** **LibsDisguises** (soft-depend). Ha telepítve van, a **Druida formák**
  vizuálisan is átalakítják a játékost; nélküle a forma csak stat-szinten vált. Érdemes mindkét
  állapotot tesztelni (telepítve / nélküle).
- **Scoreboard / tablist:** a teljes tablist-réteg (header/footer, nevek, nametag+rendezés,
  ping-oszlop, oldalsáv) **natív** — TAB vagy más scoreboard-plugin NEM kell, sőt ütközik
  (induláskor konzol-figyelmeztetés). Beállítások: `config/tablist.yml` + `general.yml` →
  `hud.sidebar`. Ha mégis külső plugint használnál: `tablist.enabled: false` +
  `hud.sidebar-enabled: false`, és az IceSMP-adatok `%icesmp_...%` placeholderekkel
  érhetők el (PlaceholderAPI-val, lásd docs/SERVER_INTEGRATION.md).
- **Telepítés:** a jar a `plugins/` mappába, indítás, majd a `plugins/IceSMP/config/*.yml`
  szerkeszthető és `/icesmp reload`-dal (vagy újraindítással) frissíthető. Néhány érték a manager
  indulásakor töltődik be — ha egy config-változás nem üt át reload-ra, **indítsd újra** a szervert.
- **Ingame config-vezérlés:** `/icesmp config set <kulcs> <érték>` bármely kulcsot felülbírál
  játékon belülről (a `config.yml`-be íródik, azonnali reload + validátor); `get`/`find` a
  kulcsok felderítéséhez (tab-complete a teljes kulcstérből), `unset`/`list` a felülbírálások
  kezeléséhez. Spell-mana példa: `/icesmp config set spell-balance.hide.resource-cost 25`.
- **FRISSÍTÉS régi jar-ról:** az éles szerveren futó `IceSMP-1.0-SNAPSHOT` (áprilisi, ~200 KiB) óta a
  plugin sokszorosára nőtt — az új jar feltöltése után az **új config/üzenet-fájlok maguktól
  kicsomagolódnak** első indításkor, a régiek megmaradnak (a hiányzó kulcsok biztonságos
  alapértékre esnek, a ConfigValidator a konzolon jelzi az elgépeléseket).

### Kompatibilitás az éles szerver plugin-készletével 🔌
Az IceSMP-t úgy készítettük, hogy az éles plugin-listával együtt fusson. A lényeges pontok:

| Plugin | Mit kell tudni / beállítani |
|---|---|
| **TAB** | ⚠️ **Kiváltva a natív tablist-réteggel** (`config/tablist.yml` + `hud.sidebar`) — header/footer, LP-prefixes tab-nevek, nametag+rendezés, ping-oszlop, animációk mind house-ban. **A TAB.jar törölhető**; ha fent marad, a konzol induláskor figyelmeztet és a két rendszer ütközik. (Visszaút: `tablist.enabled: false` + `hud.sidebar-enabled: false` → `%icesmp_...%` placeholderek, lásd docs/SERVER_INTEGRATION.md.) |
| **WorldGuard** | ✅ Automatikus: a blokkot helyező események (**meteor, kincs**) reflexiós hídon át **kerülik a WG-régiókat** (spawn/városok). Induláskor a konzol jelzi, ha a híd él. WG-régióban a mob-spawn flag blokkolhatja az esemény-mobokat (invázió/hajsza) — ez nem hiba, az esemény kecsesen kezeli. |
| **SimpleClaimSystem** | ⚠️ **Kiváltva a natív `/claim` rendszerrel** — az SCS ezután feleslegessé válik. **Migrálás:** a régi SCS-claimek **nem konvertálódnak automatikusan** — a játékosoknak újra kell claimelniük a területüket (vagy admin kézzel pótolja), utána az SCS jar **törölhető** a szerverről. |
| **LuckPermsChatFormatterFolia** | ⚠️ **Kiváltva a natív chat-formázóval** (`chat.format-enabled` a `general.yml`-ben) — a jar **törölhető**. Amíg mindkettő fent van, kapcsold ki az egyiket, különben dupla formázás történik a chatben. |
| **GrimAC** | 🔎 Playtesten figyelni: a mozgató spellek (Villanás, Árnyéklépés, Hősi Szökellés, Dupla Ugrás, Fázisugrás…), az invázió-bajnok földcsapás-lökése és a frakció-elytrák okozhatnak fals riasztást. Ha igen: Grim-oldali exempt/enyhítés a jelzett check-re. |
| **CoreProtect** | A plugin által lehelyezett/visszaállított blokkok (meteor-kráter, kincsesláda) nem játékos-akciók, a CoreProtect nem naplózza őket — egy nagy területű **rollback a kráter-visszaállítás után** felesleges (magától visszaáll). |
| **VillagerTradeEdit** | A karaván-NPC (WanderingTrader) natív trade-GUI-ját az IceSMP letiltja és a saját boltját nyitja — a VTE a karaván-kereskedőt így nem érinti. Playtesten egyszer ellenőrizd. |
| **ViaVersion/Backwards** | Régi kliens-verziók a HUD unicode-jeleit (👑 ❤ ▮) és a hosszú oldalsáv-sorokat csonkíthatják — kozmetikai, nem hiba. |
| **FarmProtect** | Együttműködik: az IceSMP termés-listenerei `ignoreCancelled`-del futnak, a FarmProtect által tiltott esemény nem ad bónuszt. |
| **economist** | Külön gazdaság: az IceSMP saját frakció-valutát használ (nincs Vault-híd) — a két rendszer nem keveredik. |
| LuckPerms, FancyHolograms, AuMenus, voicechat, ImageFrame, Axiom/FAWE/goBrush/VoxelSniper, packetevents/ProtocolLib | Nincs ismert közvetlen ütközés. |
| GSit, CrazyCrates, SModeration, MiniMOTD, AxAFKZone | Replacement alatt állnak; azonos parancs/event felelősség miatt nem tekinthetők konfliktusmentesnek. Jar nélkül csak a megfelelő scope draft PR-jének buildje és kézi playtestje után tesztelendők. |

### Permissionök tesztelőknek
A legegyszerűbb, ha a tesztelő admin **OP** (minden node megvan), vagy egyetlen sorral:
**`icesmp.admin.all`** — regisztrált szülő-node, az ÖSSZES alábbi admin-node a gyereke
(wildcard-támogatás nélküli permission-pluginnal is működik). Pontosabb jogosztáshoz az
egységes `icesmp.admin.<domain>` séma:

| Node | Mire |
|---|---|
| `icesmp.admin.all` | **super-admin: az összes alábbi egyben** |
| `icesmp.admin.reload` | `/icesmp reload` |
| `icesmp.admin.config` | `/icesmp config get/set/unset/list/find` (ingame config-felülbírálás) |
| `icesmp.admin.events` | világesemény-triggerek |
| `icesmp.admin.job` | kaszt XP / Lélekkapocs / spell-unlock / `/class admin` |
| `icesmp.admin.currency` | valuta-egyenleg beállítás |
| `icesmp.admin.faction` | frakció-kényszerítés, király/kassza admin-műveletek |
| `icesmp.admin.quest` | küldetés force-complete + a `/quest admin` szerkesztő |
| `icesmp.admin.relic` | relikvia adása |
| `icesmp.admin.sinner` | `/sinner` bűn-kezelés |
| `icesmp.admin.territory` / `icesmp.admin.territory.bypass` | területkezelés (+ királyság-spawnok) / zóna- és claim-védelem teljes megkerülése (build+interakció+PvP) |
| `icesmp.territory.builder` | építő-jog: védett zónában is építhet/interaktálhat (PvP-tiltás rá is áll) — szerep-node, NEM admin |
| `icesmp.admin.parkour` / `icesmp.admin.exchangeboard` / `icesmp.admin.profession` / `icesmp.admin.spec` | parkour / tábla / szakma / spec admin |
| `icesmp.admin.npc` | `/npcbind` — NPC kötése küldetéshez/bolthoz/bankárhoz/valutaváltóhoz/frakció-menühöz/parancshoz |

> ♻️ **Visszafelé kompatibilis:** a régi nevek (`icesmp.admin`, `icesmp.job.admin`,
> `icesmp.currency.admin`, `icesmp.faction.admin`, `icesmp.relic.admin`) alias-ként
> regisztrálva maradnak — a meglévő LuckPerms-beállítás átírás nélkül tovább működik,
> de új jogosztásnál már a kanonikus `icesmp.admin.<domain>` nevet használd.

---

## 1. Gyors tesztelő-setup (időkapuk megkerülése)

Egy teszt-karakter beállítása másodpercek alatt (a `<j>` a játékos neve):

```
/faction set <j> RED                 # frakció kényszerítése (RED/BLUE/NEUTRAL/DARK)
/currency set <j> 5000 RED           # valuta a bankhoz/teszthez
/class addxp <j> primary 100000      # gyors szintezés (max szint 50)
/class givecatalyst <j>              # a kaszt Lélekkapcsa (spellbook-tárgy)
/class unlockspell <j> <spell_id>    # konkrét spell azonnali feloldása
/spec choose <id>                    # spec választása (25. szint kell hozzá)
```

### Időzített események azonnali kiváltása ⚠️ (a legfontosabb teszt-parancsok)
```
/events blood-moon start   # vérhold most (stop: /events blood-moon stop)
/events worldboss          # világboss spawn a közeledbe
/events invasion           # invázió-horda indítása köréd
/events caravan arrive     # kereskedő-karaván most (távozás: /events caravan depart)
/events ambient            # véletlen hangulat-esemény kiváltása
/events gathering          # véletlen gyűjtögető buff-ablak megnyitása
/events treasure           # elrejtett kincs a közeledbe
/events wild-hunt          # kóborló elit fenevad (Vad Hajsza) idézése
/events abundance          # Bőség-idő (a vérhold pozitív ellenpárja)
/events challenge          # kollektív szerver-kihívás indítása
/events escort             # karaván-kíséret (konvoj + szörny-hullámok)
/events meteor             # meteor-becsapódás (kráter + kibányászható érc)
/events intro [j]          # bevezető cím-szekvencia újrajátszása
/events season             # szezon-pontállás
```

### Egyéb teszt-triggerek
```
/quest complete <j> <quest_id>   # küldetés azonnali teljesítése
/quest admin create|set|delete   # küldetés-szerkesztő: quest készítése JÁTÉKON BELÜL, kód nélkül
/relic give <j> <relic_id>       # relikvia adása (loot-teszt)  — id-k: /relic list
/sinner set <j>                  # bűnössé tétel (Sötét-paktum teszt); clear/add/status is van
```

### Config-gyorsítás teszthez (`config/world.yml`)
A valós spawn-időközök hosszúak; teszthez érdemes csökkenteni, majd `/icesmp reload` vagy restart:
```yaml
world-events:
  check-interval-seconds: 10          # alap 60
  world-boss: { check-interval-minutes: 2, chance-percent: 100 }
  invasion:   { check-interval-minutes: 2, chance-percent: 100 }
  blood-moon: { chance-percent: 100 }
mob-scaling:
  blocks-per-level: 100               # alap 1000 — így közelebb is erős mobok jönnek
```
(De a force-parancsok — `/events …` — gyorsabbak, mint a config-hangolás.)

---

## 2. Mi VAN a pluginban (rendszer-leltár) ✅

A teljes leírás a [PLAYER_GUIDE.md](PLAYER_GUIDE.md)-ban; röviden, ami tesztelhető:

- **Frakciók** (4): Piros/Kék/Semleges/Sötét, passzív bónuszokkal és valutával.
- **Kasztok** (13) + **specializációk** (31), egy kaszt/játékos (végleges, admin-reset van), 50-es max szint.
- **Képességek** (390+): Lélekkapocs-tárgy, **hibrid költségrendszer** (Erő-csík + HP/XP/éhség),
  cooldown, kombók, spell-mesterség.
- [ ] **Közelharci Lélekkapocs**: melee kaszttal (pl. Harcos) a kézben tartott kard/balta is
      Lélekkapocs — jobb katt cast, SHIFT+jobb katt varázskönyv, SHIFT+ütés spell-váltás; a balta
      jobb-katt fahántása NEM fut le; nem-melee kaszttal (pl. Varázsló) a kard NEM Lélekkapocs.
- [ ] **Dinamikus spell-skálázás**: magasabb kaszt-szinten mérhetően nagyobb spell-sebzés
      (+0,5%/szint), az Arkán Hatalom talent rangonként +2%-ot ad; a bónusz +50%-nál tetőzik.
- **Erő-csík** (osztály-erőforrás): HUD-sáv, regenerálódó költség-pool.
- **Talentek**: kaszt- és szakma-ponttár, általános + kötött talentek.
- **Szakmák** (gyűjtögető/készítő/másodlagos) + szakma-specializációk + craft-korlátok.
- **Gazdaság**: bank, valuta, dinamikus árfolyam, valutaváltás, piactér, állampolgári adó,
  kereslet-sokk, árfolyam-hologramok, lélekkő-drop.
- **Relikviák**: Mételytépő (fegyver, PvP-transzfer) + 4 frakció-elytra; rituálé-oltárok.
- **Pet/minion**: Vadmester & Nekromanta társak (befogás, szint, parancsok), lélekszilánk-bajnok.
- **Küldetések**: 4 kezdő kaszt-próba, Sötét Beavatás, vezeklés-lánc, napi küldetések.
- **Bűn-rendszer**: gyilkosság/árulás/lopás → bűn → 4-nél száműzetés a Sötétbe (örök paktum).
- **Királyság/raid/szezon**: királyválasztás, kassza, adó, raid, hadizsákmány, liga-pontok.
- **Világesemények**: távolság-alapú mob-szintezés, vérhold, világbossok (10 archetípus, 2 fázis),
  inváziók (horda + bajnok), szezonális liga.
- **Sámán-totemek**, **Druida-formák**, **parkour-pályák**, **GUI-k** (profil, menü, spellkönyv,
  piac, ranglista, elérések), **HUD** (oldalsáv + bossbar).

---

## 3. Mi NINCS / részleges (NE teszteld hibaként) ⏳🚧

- ✅ **Bűn-rendszer teljes:** gyilkosság (+1), **árulás** (frakciótárs ölése, +2) és **lopás**
  (idegen territóriumban konténer-fosztás, +1) is bűn — teszteld mindhármat!
- ✅ **Raid teljes:** jelentkezés + létszámkorlát (10v10), területkötés, pont-tartás objektíva,
  terület-átvétel — teszteld a 4.12 szerint!
- 🚧 **Kaszt-questek:** a 4 kezdő próba + az NPC-s mester-láncok (mentor-NPC → próbapálya)
  **készek a pluginban** — de a mester-NPC-k (FancyNpcs) és a próbapályák kihelyezéséig a
  láncok nem haladnak. Teszthez rakj ki egy NPC-t (`/npc create harcos_mester`) és egy pályát.
- 🚧 **Piactér:** fizikai piactábla még nincs (a lapozás, a `/market search`, a
  reputáció-árazás és a **licitálós aukciósház** kész — ezeket teszteld!).
- 🚧 **Intro:** a kamera-utaztatás kész, de alapból kikapcsolt (waypoint-kijelölésig).
- 🚧 **Szezonliga:** pontgyűjtés kész, a győztes **kozmetikai jutalma** még nincs.
- ⏳ **Külön „ultimate" / burst-rendszer** (a korábbi kirobbanás-mechanika kivéve).
- ⏳ **Világépítés** (fővárosok, Sötét romváros, loot-asztalok) — szerver-csapat feladata; a plugin
  csak az eszközt adja (`/territory`).
- ❌ **Másodlagos kaszt: NINCS** — a rendszer tudatosan törölve lett (egy kaszt / játékos, a
  választás végleges; admin-reset: `/class admin resetclass`). Ne jelentsd hibaként, hogy nem
  vehető fel második kaszt!

---

## 4. Tesztelési checklista rendszerenként

### 4.1 Frakciók és passzívok ✅
- [ ] `/faction join <red|blue|neutral|dark>` és `/faction leave` működik; a Sötétbe csak bűnös léphet.
- [ ] **Piros:** állj tűzbe / lávába / magma-blokkra → **nincs sebzés**.
- [ ] **Kék:** powder snow-ban / fagyos bistromban → **nincs fagy-sebzés**; merülj víz alá hosszan →
      **nem fulladsz** (fulladás-immunitás); éhség kb. fele olyan gyorsan fogy.
- [ ] **Semleges:** ess le magasról → **nincs zuhanás-sebzés**; a semleges mobok és **endermanök**
      nem támadnak (ránézésre sem aggrózik az enderman); **nem fizet állampolgári adót**.
- [ ] **Sötét:** wither-rózsa/wither-effekt → **nincs sebzés**; zombi/csontváz/phantom/zoglin
      **nem támad** rád. (A láthatatlanság SZÁNDÉKOSAN megszűnt — ne teszteld hibaként.)
- [ ] `factions.passives.enabled: false` → minden passzív kikapcsol.

### 4.1.1 Királyság-spawnok és váltás-kapu ✅
- [ ] **Spawn-kijelölés:** `/territory setspawn <frakció>` a PONTOS álláspontot menti (magasság +
      nézésirány) — ellenőrizd egy fa alatt állva, hogy NEM a fa tetejére kerülsz.
- [ ] **K2 signature (BLUE):** a 3 Cryghaliris-recept csak Fagy-frakcióval craftolható (más
      frakciónak hibaüzenet, a csempén ⚑ sor); Kallan Szeletelője nyila gyorsabb és többet sebez;
      a Jégvért viselve csökkenti a sebzést; a Kantár jobb kattra gyorsítja a hátast, elfogy, és
      ugyanarra a hátasra másodszor nem fogy el.
- [ ] **K3 signature (RED):** a 3 Perinfernicitas-recept csak Láng-frakcióval craftolható; a
      Tűzköpő Quick Charge-dzsal készül és lövedéke gyorsabb; az Agyar többet sebez (baltával az
      off-handben még többet); a Tollköpeny viselve kioltja a tűz/láva/forró-blokk sebzést.
- [ ] **K4 signature (NEUTRAL, ÚJ):** a 4 Menedék-recept csak Menedék-frakcióval craftolható
      (bányász 45 / horgász 40 / rúnaírnok 35 / füvész 45, tervrajzból). A Csákány érc-töréskor
      ~20% eséllyel extra dropot ad (bányász-láz ALATT szünetel — nincs stack); a Horgászbot
      ~20% eséllyel duplázza a fogást (halászati láz alatt szünetel); a Bankbetét jobb-kattra
      elfogy és +25 Creutzért ír jóvá (dupe-teszt: gyors dupla-katt se duplázzon); a
      Szellemszarvas-Bűbáj jobb-kattra gyors, ideiglenes hátast idéz (~90 mp, ~120 mp cooldown,
      a bűbáj NEM fogy el). Kulcsok: `signature.*` a `crafting.yml`-ben.
- [ ] **Signature-enchantok (ÚJ — bootstrap):** craftolj egy signature itemet → a tooltipben a
      neve alatt VALÓDI enchant-sor jelenik meg magyar lore-névvel (Kallan-íj: „Jégfog", Tűzköpő:
      „Vihartűz", Agyar: „Vérszomj", Jégvért: „Fagypáncél", Tollköpeny: „Főnixtoll", Csákány:
      „Érc-érzék", Horgászbot: „Bokic Kegye") — kliens-mod és resource pack NÉLKÜL (data-driven
      enchant-registry, a kliens szinkronizálja). Kapcsoló: `signature.custom-enchants`.
      ⚠️ Enchant-asztalról EGYÁLTALÁN nem jöhetnek (nincsenek az in_enchanting_table tagben).
      **Rider-effektek (audit — egyik enchant sem dísz):** Jégfog = nyíl-találat 2 mp lassítás;
      Vihartűz = lövedék 2 mp gyújtás; Vérszomj = sebzés 10%-a gyógyít (max 1 szív/ütés);
      Érc-érzék = +2 XP-gömb érc-törésnél; Bokic Kegye = fogáskor 30 mp Szerencse;
      Fagypáncél/Főnixtoll = iskola-ellenállás. Kulcsok: `signature.enchant-riders.*`.
      A resist-nél NINCS action-bar kiírás (spam lenne) — a sebzés-számok mutatják. ⚠️ A bootstrap-regisztráció új
      (unstable) Paper API — az ELSŐ gradle-build és szerverindulás kiemelt teszt: ha a konzol
      bootstrap-hibát dob, a `signature.custom-enchants: false` NEM elég, a Bootstrap-osztály
      regisztrációját kell javítani.
- [ ] **Iskolás mágia damage-type-ok + Rúnavért (ÚJ — bootstrap):** a spellek a saját ISKOLÁJUK
      damage-type-jával sebeznek (icesmp: tuz/fagy/szent/arnyek/termeszet/vihar/kaosz_magia +
      magia=ősmágia). Besorolás: `spells.spell-schools.by-spell.<id>` felülírás → a caster
      kasztjának `by-class` defaultja (pl. paladin=szent, death_knight=fagy, warlock=kaosz) →
      ősmágia — MINDEN spell egyedivé hangolható reload-dal. A kill-attribúció (bűn/raid/
      bounty) változatlan. **Rúnavért** (rúnaírnok 40 recept, üllőn páncélra, max 3):
      szintenként −8% MINDEN mágia ellen; **iskola-counterek**: a Fagypáncél a fagymágia, a
      Főnixtoll a tűzmágia ellen ad szintenként −10%-ot — a kettő összeadódik, 60% plafonig
      (`spells.magic-resist.*`). Vanília sebzésre egyik sem hat. Spell-halálnál magyar,
      iskolás halál-üzenet ("elemésztette a fagymágia").
      ⚠️ Teszt: fagy-spell Fagypáncéllal (számít) és tűz-spell-lel (nem számít rá az
      iskola-counter, csak a Rúnavért); kill-attribúció spell-öléssel; action-bar jelzés;
      per-item enchant-kapcsoló (`signature.custom-enchants.items.*`); első gradle-build.
- [ ] **K6 frakció-ételek (ÚJ):** a 3 séf-recept frakció-kapus (Pisztráng BLUE 25 / Rántotta RED 25 /
      Sütemény NEUTRAL 35). A Pisztráng evése rövid felszívódás-pajzsot, a Rántotta tűz-ellenállást
      ad; a Sütemény „robban" (felfelé lökés + Speed II + tűzijáték-effekt, blokk-kár nélkül).
      **Honvágy-teszt:** BLUE/RED játékos `factions.food-duty.grace-hours` (teszthez állítsd 0.01-re
      → reload) után `check-minutes`-enként rövid Éhséget + action-bar emlékeztetőt kap; bármely hal
      (BLUE) / tojás-étel (RED) evése nullázza. Új/frissen váltó játékos először türelmi időt kap.
      A DARK a **Mortengradi Hamukenyeret** kapja (éjjellátás-buff), de honvágy-kötelezettsége
      NINCS (nincs otthonuk); a NEUTRAL-nak sincs kötelezettsége.
- [ ] **K8 Emlékszilánkok (ÚJ):** az Opálos Emlékszilánk ritkán esik skálázott moboktól (unique-sor,
      3-as súly), boss/event-mobtól biztosabban (8-as súly). `/emlek` mutatja az egyenleget és az
      árakat; `/emlek xp` (3 szilánk → 500 kaszt-XP), `/emlek talent` (5 → +1 kaszt-talentpont, a
      /talent pool beszámítja), `/emlek spec` (8 → a spec-választás SZINT-kapuja elesik — a kaszt/
      frakció/bűnös/quest kapuk maradnak; másodszor nem váltható be), `/emlek lore` (1 → véletlen
      emlék-töredék). A levonás atomi (nincs dupe); kulcsok: `memory-shards.*` (general.yml).
- [ ] **K9 Suttogók (ÚJ):** élőhalottól nagyon ritkán esik a **Suttogás** meghívó (boss-tól eséllyel).
      **Sötét Rítus:** éjjel, SCULK-blokkon állva, EGYEDÜL (16 blokkon belül nincs más játékos),
      SHIFT+jobb katt a meghívóval → vér-áldozat (−6 HP) + Suttogó-státusz (rejtett!). Ha valaki a
      közelben van: a rítus meghiúsul, a szemtanúk Tanú-tokent kapnak, a jelölt nagy gyanút.
      **Titkos csatorna:** `/suttogas <üzenet>` — a rejtett Suttogók **ÉS a Kitaszítottak (DARK)**
      látják; kívülállónak „csak a szél zúg". Teszt: egy DARK-játékos kapja meg a Suttogó üzenetét,
      és ő maga is tudjon írni bele; egy RED/BLUE/NEUTRAL nem-Suttogó semmit ne lásson.
      `factions.whisper.dark-hears-channel: false` + `/icesmp reload` → a DARK kiesik a csatornából.
      **A titok ára:** a csatorna-sor kiírja a feladó nevét, tehát a DARK-játékos MEGTUDJA, ki
      Suttogó — ellenőrizd, hogy ez tudatos döntés marad (nyílt chatbe kiszivárogtathatja).
      **Gyanú:** rajtakapott testvérgyilkosság (+40) és rajtakapott rítus (+50) növeli; a szemtanúk
      `/suttogas vád <játékos>` váddal (+15, csak IGAZI Suttogóra hat, tokent fogyaszt) nyomozhatnak
      (a `vad` és `accuse` alak is működik — ne kelljen ékezetet gépelni);
      10 percenként −5 csillapodás. **Lelepleződés** (100 pont): fény-dráma + broadcast + 4 bűn →
      a bűn-küszöb a meglévő száműzetéssel a Kitaszítottak közé taszít. Kulcsok: `factions.whisper.*`.
- [ ] **K5 Káoszkor-loot:** élőhalott mobból (zombi/csontváz) eshet a Rozsdás Penge / Megrontott
      Elit Páncél (nevesített, rarity-prefixszel + affixekkel); NEM-élőhalottból sosem esik;
      az Eleftheria Könnye rituálé csak DARK-frakcióval aktiválható (síró obszidián mag).
- [ ] **K1 kánon-nevek:** a HUD/tab a rövid frakciónevet mutatja (Láng/Fagy/Menedék/Kitaszított),
      a /menu és a Profil a hosszút (pl. Láng (Perinfernicitas)); a valuta-itemek neve Parázsló
      Parals / Hópihér-veret / Creutzér / Csontveret; a `/faction join piros` (legacy név) is működik.
- [ ] **Első belépés:** vadonatúj fiók a Menedék spawnján jelenik meg (nem a világ-spawnon);
      az intro-kamera utána lejátszódik.
- [ ] **Választás-teleport:** `/faction join red` után a Piros Királyság spawnjára kerülsz
      („Üdvözöl a(z)…" üzenettel); `factions.spawn.teleport-on-join: false` → nincs teleport.
- [ ] **Respawn:** ágy/horgony nélkül meghalva a SAJÁT frakciód spawnján éledsz; ággyal az ágynál.
- [ ] **Váltás-kapu:** kijelölt semleges főváros mellett frakciót váltani (join ÉS leave) csak a
      semleges fővárosban állva lehet — máshol hibaüzenet; semleges főváros nélkül a kapu nem zár.
- [ ] **Leave = fizetős váltás:** nem-semleges frakcióból `/faction leave` levonja az 500-at és
      indítja a 72 órás cooldownt (a leave+join kerülőút megszűnt).
- [ ] **Hírnök-NPC:** `/npcbind <npc> faction` után az NPC jobb-kattra a frakció-menüt nyitja.

### 4.2 Kasztok, Lélekkapocs, szintezés ✅
- [ ] `/profile` → Kaszt menüből mind a **13 kaszt** választható; **egy kaszt** vehető fel, utána a
      menü „Már van kasztod" jelzést ad; `/class admin resetclass` után újra választható.
- [ ] A Lélekkapocs a kaszthoz illő tárgy (pl. Varázsló = bűvölt könyv); **jobb katt** = cast,
      **lopakodás + ütés** = váltás a feloldott spellek közt (action bar mutatja a kiválasztottat + költséget).
- [ ] A Lélekkapocs craftnál/kemencében **nem használódik el** (védett).
- [ ] Mob-öléssel nő a kaszt-XP; magasabb mob-szintű mob több XP-t ad (alap 10 + 3/mob-szint).
- [ ] ⚠️ **Folia:** ölj mobot egy **régióhatáron / messziről** → az XP/üzenet hibamentesen érkezik
      (figyeld a konzolt „region"/IllegalStateException-re).

### 4.3 Erő-csík + hibrid költség ✅ (FRISS — kiemelt teszt)
- [ ] A HUD oldalsávban látszik az **Erő-csík** (kasztonként más név/szín: Mana/Düh/Energia/Fókusz/Csi…).
- [ ] Egy hétköznapi spell elsütése **csökkenti** a csíkot; idővel **visszatöltődik** (~8/mp).
- [ ] **Üres csíknál** a spell **nem sül el** → action bar: „Nincs elég <erőforrás>!".
- [ ] **Hibrid költségek** (a spellkönyv `/spellbook` minden spellnél kiírja a költség típusát):
  - [ ] **vér-mágia** (pl. Berserker Vérszomj, Nekromanta vér-spelljei) → **életbe (❤)** kerül.
  - [ ] **nagy rituálé/idézés/időjárás/ulti** (XP ≥ 80) → **XP-be** kerül (pl. Esőtánc, Holtak Hada).
  - [ ] **nehéz fizikai** (éhség ≥ 8: állások, Második Lélegzet, Pandaőrség) → **éhségbe** kerül.
  - [ ] minden más → az **Erő-csíkba**.
- [ ] No-op cast (nincs célpont/társ) → a költség **visszatérül**, és nincs cooldown.
- [ ] **Kaszt-profilok (ÚJ):** a tár kasztonként másképp viselkedik (`spells.resource.class.*`):
  - [ ] **Düh-típus** (harcos/halállovag/démonvadász): harcon kívül a csík **lassan ürül** (2/mp),
        ütésenként **+8** töltődik, harcban (5 mp-en belüli ütés) lassú regen (3/mp) fut.
  - [ ] **Energia-típus** (orgyilkos/szerzetes 14/mp, íjász 11/mp): gyors visszatöltődés.
  - [ ] **Mana-típus** (varázsló/sámán/pap/boszorkánymester/evoker/druida 120 max, paplovag 110):
        nagyobb tár, lomhább (7/mp) regen — a HUD-sáv maximuma is a kaszt szerintit mutatja.
  - [ ] Lövedékkel (íj) bevitt találat is számít harci ütésnek (düh-töltés + harc-időbélyeg).
- [ ] `spells.resource.enabled: false` → minden spell a régi éhség/XP/HP költségre vált.
- [ ] *(Ismert finomhangolandó: néhány határeset-spell — pl. Gyökerezés 8 éhségen — a küszöb miatt
      éhséget kér, pedig a Mana is illene rá. Jelezd, ha furcsát látsz.)*

### 4.4 Specializációk ✅
- [ ] 25. szinten `/spec choose <id>` (vagy a Specializáció menü) elérhető; a menü mutatja a feltételt.
- [ ] **Nekromanta** csak Kitaszítottként (Sötét frakció) + bűnösként + a Sötét Beavatás után választható.
- [ ] A spec feloldja a 25–45. szintű spelleket; a szerep illik (tank/heal/dps/caster/ranged).
- [ ] `/spec respec` visszavált valutáért; a spec-kötött talentpontok visszatérülnek.
- [ ] Hibrid kasztok: pl. Holy paplovag gyógyít, Retribution sebez (eltérő spell-pool).

### 4.5 Spellek, kombó, mesterség ✅
- [ ] Több reprezentatív spell elsül és a leírt hatást teszi (sebzés/effekt/teleport/idézés).
- [ ] **Spell-VFX (ÚJ):** a spell-effekt FORMÁZOTT — célzott spell **sugarat** húz a célpontig,
      AoE spell **gyűrűt** rajzol a hatókör mentén, önmagadra ható spell **hélixet** csavar fölfelé;
      a szín a spell jellegéhez illik (tűz narancs-vörös, fagy kék-fehér…). `spell-vfx.enabled: false`
      → visszaáll a régi gömb-puff. `spell-vfx.max-points` csökkentése ritkítja a formát.
- [ ] **Paletta spec szerint:** a szín a kaszt/spec témáját követi — pl. a **Fagy** halállovag-spellek
      (obliterate, frost_strike) kék-fehérek, a **Pusztítás** boszorkánymester (chaos_bolt, incinerate)
      narancs-vörös, az **Árnyék** pap (mind_flay) sötét-lila. Config: `spell-vfx.class-palettes.<spec>`;
      egyedi kivétel: `spell-vfx.overrides.<spell-id>`. Rossz/ismeretlen paletta-név → csendes fallback
      (accent-particle szín), nincs hiba.
- [ ] **Forma a spell jellegéhez:** közelharci csapás (**frost_strike, crusader_strike, blackout_kick,
      keg_smash**) → **becsapódás-csillag** a célponton (nem sugár); lehelet (**fire_breath,
      dream_breath, primal_roar**) → **kúp** a nézési irányban; dobott (**mana_dart, toxin_dart**) →
      **ív**. A többi marad a célzás-alapon (célzott→sugár, körzet→gyűrű, önmagad→hélix). Per-spell
      felülírás: `spell-vfx.shapes.<spell-id>`.
- [ ] **Hero-ultimate szín:** a 31 spec-csúcsspell (lvl 45) kiemelt palettát kap — pl. az
      **ascendance_flame** (elemental sámán) TŰZ-színű a spec kék-fehér helyett, a **serenity**
      (windwalker) ARANY. A színek a `spell-vfx.overrides`-ban hangolhatók.
- [ ] ⚠️ **Folia:** kasztolj **régióhatárra / messzi célpontra** → a sugár/forma hibamentesen
      megjelenik, nincs konzol-hiba.
- [ ] Cooldown működik; a **60 mp feletti** cooldown kilépés után is megmarad.
- [ ] **Shift+görgetés spell-váltás:** Lélekkapocsral a kézben lopakodva görgetve a spell vált
      (előre/hátra), a hotbar-slot NEM vált el, és a Lélekkapocs neve az épp kiválasztott
      képességet mutatja; Lélekkapocs nélkül a görgetés normál slot-váltás marad.
- [ ] **Spell-kedvencek (ÚJ):** a spellkönyvben **shift-katt** egy feloldott spellen → ★ jelölés
      (újra shift-katt: le); ha van legalább egy kedvenc, a shift+görgetés **csak a kedvenceket**
      lépkedi (action bar: „★1/3"); üres kedvenc-lista = a teljes feloldott lista. A spellkönyv
      **tölcsér-gombja** a „csak feloldottak" szűrőt kapcsolja.
- [ ] **Kombó:** egy konfigurált spell-pár (pl. Fagyérintés → Arkán Lökés) rövid időn belül →
      „⚡ Kombó!" + gyorsabb felépülés.
- [ ] **Kombó-láncok (ÚJ, 3 lépés):** egy konfigurált lánc (pl. varázsló: Fagyérintés → Arkán
      Lökés → Tűzgolyó; `spells.combos.chains`) mindhárom lépése az időablakon belül → a finisher
      **+25% erővel** sül el („⚡ Kombó-lánc befejező!") + cooldown-visszatérítés; sima cast után
      az action bar mutatja a **nyíló kombó-ablakot** („⏳ Kombó-ablak: <köv. spell>"), kombó után
      a lánc következő lépését.
- [ ] `/spell upgrade <id>` valutáért növeli a mesterség-rangot (max 5 rang): a cooldown csökken
      (-8%/rang) ÉS az erő nő (+5%/rang) — magasabb rangon egy sebző spell nagyobbat üt, egy
      buff/debuff spell effektje tovább tart, a self-heal többet gyógyít; a költség/self-damage nem nő.
- [ ] Idézett társak (Nekromanta/Vadmester) **nem fordulnak ellened**, a célpontodra támadnak, idővel eltűnnek.
- [ ] **Resource-cost override:** `/icesmp config set spell-balance.<id>.resource-cost <érték>` után a
      Spellbook AZONNAL az új árat mutatja és a cast annyit von le (cooldown-módosítás nélkül);
      `unset` után visszaáll a cooldown-sávos alapár.
- [ ] **Élő spell-balansz:** deklaratív spellnél (pl. egy sima sebző spell)
      `/icesmp config set spell-balance.<id>.damage <érték>` → a következő cast MÁR az új sebzést
      viszi és a Spellbook-lore is az új számot mutatja, restart nélkül (range/radius/heal/ignite/
      freeze/knockback ugyanígy).
- [ ] **Üres AOE-cast visszatérítés:** Lökéshullám/Gyökerezés/Megzavarás/Csontfagy célpont nélkül
      „Nincs célpont" üzenetet ad, a költség visszajár és NEM indul cooldown.
- [ ] **Újracast-védelem:** Belső Fókusz lefagyás alatt, Elrejtőzés aktív invis alatt nem castolható
      újra (a sebesség/páncél nem veszhet el).

### 4.5.1 Playtest-balansz reworkök (Ice SMP 5 TEST doksi) ✅
- [ ] **Dobható spellek:** Vadgomba (gomba 3mp után robban: slowness+poison), Rúnacsapás (kő —
      hozzáérő ellenfél 7 sebzés), Démoni Kör (só-csapda poison), Átok Nyúl (nyúl-akna 3 szív splash) —
      az itemek nem vehetők fel, a kaszterre nem hatnak.
- [ ] **Lövedékek:** Szent Harag (meglévő élet 30%-a; világbosson/Wardenen HATÁSTALAN), Lélek Csere
      (25% átszáll rád), Sarlóvetés (bumeráng oda-vissza sebez), Tűzgolyó (splash a cél körül),
      Elmerobbantás (3mp múlva robban a jelölt cél).
- [ ] **Channelek:** Mély Lélegzet (3.5mp lángszórás a nézésirányba, mozgás közben követi), Forgószél
      (5mp pörgés, újracast tiltva közben), Szellemlátás (2mp-enként váltakozó invis 8mp-ig) — kilépésre
      mind leáll (nincs beragadt állapot).
- [ ] **Kiűzés Botja:** 5mp-re kapott Knockback 3 bot — nem dobható el, nem mozgatható, lejáratkor és
      kilépéskor eltűnik.
- [ ] **Áhítat Aurája:** 10mp-ig a támadók 1 visszavert sebzést kapnak; két aurás játékos egymást ütve
      NEM kerül végtelen pingpongba (max 2 visszaverés/mp).
- [ ] **Jéglánc:** pontosan max 2 célt fagyaszt (fő cél + legközelebbi 3 blokkon belül).
- [ ] **Fagysugár (volt Fagyláz):** 2 blokk széles sugár, minden útba esőt fagyaszt. (A név-ütközések
      feloldva: Halállovag = Fagysugár, Sárkányidéző = Lánggolyó — a spec-spellek neve változatlan.)
- [ ] **Spell-item modellek:** a Vadgomba, Rúnakő, Démoni Só és Kiűzés Botja ITEM_MODEL-t hordoz — resource packkel a textúrájuk cserélhető (`docs/RESOURCE_PACK_CMD.md`).
- [ ] **Megzavarás (rework):** mobokra is LÁTHATÓAN hat — a támadó mob leáll rólad (célpont-vesztés,
      10 mp-ig ismételve), lassabb és gyengébb; játékos-ellenfél megvakul (vakság+sötétség).
      A méreg-komponens kikerült (tiszta CC).
- [ ] **Druida-formák:** Párducformában −4 szív max-élet (visszaváltáskor/relogkor visszaáll);
      Medve: éhség-debuff; Hold: mining fatigue; Utazó: weakness.

### 4.6 Talentek ✅
- [ ] `/profile` → Talentek: kaszt-ponttár (5 szintenként 1) és szakma-ponttár (10 szintenként 1).
- [ ] Általános talent (Életerő/Erő/Fürgeség/…) azonnal hat; kötött talent csak a feltételt teljesítőnél jelenik meg.
- [ ] Respec után a pontok visszatérülnek.

### 4.7 Szakmák ✅
- [ ] `/profession join` 1 gyűjtögető + 1 készítő; Halász/Szakács alapból megvan.
- [ ] A megfelelő tevékenység ad XP-t (bányászat/aratás/horgászat/sütés/craft).
- [ ] **Creative-guard:** creative módú blokktörés NEM ad szakma-XP-t, és nem viszi a napi
      küldetés / quest / közösségi cél számlálóit sem.
- [ ] 25. szinten szakma-spec választható.
- [ ] **Craft-korlát:** netherite felszerelést csak 25+ Kovács craftol (különben nem jön létre + üzenet).
- [ ] **Craft-korlát bővítés (ÚJ):** netherite-rúd → Bányász 20; számszeríj/pajzs → Favágó 8;
      főzőállvány → Alkimista 5; bűvölő-asztal → Enchanter 5; torta/sütőtökös pite/nyúlpörkölt →
      Séf 6. Megfelelő szinttel elkészül, alatta NEM jön létre + kap üzenetet. A nyers alapok
      (íj, sült húsok, kőszerszám) bárkinek szabadok. Kikapcsolható: `crafting-restrictions.enabled`.
- [ ] **Recept-könyv** (`/profession recipes` vagy `/menu` → Recept-könyv): a szakmáid receptjei
      tanult (zöld) / zárolt (szürke) állapottal, hozzávaló megvan/hiányzik jelzéssel, lapozva;
      kattintásra craft (hozzávalók fogynak), `affix-tier`-es recept egyedi rolled tárgyat ad.
- [ ] **Tervrajz:** blueprint-recept zárolva marad, míg meg nem szerzed a tervrajzot (mob-drop
      ritkán / `/profession blueprint <j> <id>` admin). Jobb katt a tervrajzon → megtanulod
      (már ismert tervrajz nem fogy el); utána a recept-könyvből craftolható (szint is kell).
- [ ] **Rolled-affix mestermunka:** mestermunkát craftolva a tárgy VÉLETLEN minőséget ([Közönséges]…
      [Legendás]) + random attribútum-affixeket kap (a leírásban); két craft nem ugyanaz. Shift-click
      bulk-craft alap-statú marad.
- [ ] **WoW-mob loot (loot-tábla):** sima szörnyek `loot.mob-drop.chance` eséllyel dobnak egy
      táblasort: rolled `drop`-tier felszerelést (random névvel, akár Ócska/negatív affix), sokféle
      nyersanyagot/értéket, vagy **csak-mobból-eső egyedi alapanyagot** (Vad Esszencia / Szörny Mag
      / Árnyékpor). Boss/event-mob garantáltan `boss`-tier tárgyat + boss-only Fekete Villám Szilánkot.
      Szakma-craftolt nevesített tárgy SOHA nem esik mobból.
- [ ] **Mob-only alapanyag receptekben:** a mob-only egyedi alapanyagokat igénylő receptek
      (pl. Vadbőr Vért, Villámszilánk Pengéje) csak akkor craftolhatók, ha begyűjtötted a mobból-eső anyagot;
      a recept-könyv kékkel jelzi a hiányt.
- [ ] **Egyedi alapanyag védelem:** az egyedi szakma-alapanyagok (pl. Vasesszencia = IRON_NUGGET,
      Gyógy-kivonat = ehető GLOW_BERRIES) NEM használhatók normál módon: nem craftolhatók be,
      nem kovácsolhatók, nem tüzelők, nem ehetők, nem rakhatók le — csak a recept-könyvben. Van
      saját ITEM_MODEL-jük és frappáns lore-juk.

### 4.8 Gazdaság ✅
- [ ] `/bank deposit|withdraw|balance`, `/currency balance|pay|exchange|rates`, `/currency set` (admin).
- [ ] **Adó: fejadó + hátralék (ÚJ):** üresre ürített számlával is jár a beszedésenkénti minimum
      (`factions.tax.minimum-amount`, default 2) — 0 egyenlegnél a teljes összeg **hátralékként**
      gyűlik (üzenet jelzi), plafonig (`max-arrears`, default 50); pénz érkezése után a következő
      beszedés a hátralékot is levonja. Restart után a hátralék megmarad (treasury.yml: tax-arrears).
      A Semlegesek továbbra is mentesek.
- [ ] **Adócsalás (ÚJ):** ha a hátralék a plafonon ragad és a beszedés semmit sem tud levonni,
      strike jár (`factions.tax.evasion-strikes`, default 3); a küszöbnél a Számvevők feljelentik
      az adócsalót → **+1 bűn** (üzenettel, online beszedéskor) — a bűn-küszöb elérése a meglévő
      száműzetést indítja (Kitaszítottak). A strike törlődik, amint a tartozás a plafon alá esik;
      restart-álló (treasury.yml: tax-evasion-strikes). 0 = kikapcsolva.
- [ ] **Relikvia halál-viselkedés (ÚJ — reclaim):** passzív relikviával (pl. szárny) halj meg:
      a tárgy KÖDDÉ VÁLIK (nem esik le, senki sem veheti fel), üzenet jelzi; a tulajdon marad —
      CSAK te idézheted újra az oltárnál (áldozattal); más játékosnak az oltár elutasít. Ha
      `relics.inactivity.lost-expiry-days` (default 3 nap) alatt nem idézed újra, a relikvia
      MINDENKINEK felszabadul. Módok: `relics.passive-death.mode: reclaim|keep|drop`.
- [ ] **Relikvia-dup javítások (ÚJ):** aktív tulajdonos NEM idézheti újra a meglévő relikviáját
      (az oltár elutasít — kivéve, ha "elveszett"); admin pótlás: `/relic give` (force). Belépéskor
      a VISELT szárny is része az inaktivitás-sweepnek és a dedupnak; halott másolat belépése nem
      veszi el az aktív tulajdonos jogát.
- [ ] **Ingame config-menü (ÚJ):** `/icesmp config menu` (jog: `icesmp.admin.config`) — kategóriák
      (Adó / Világesemények / Kárhozat / Suttogók / Ételek / Signature / Relikviák / Emlék);
      boolean: katt váltás; szám: bal +, jobb −, SHIFT ×5; mód-kulcs (pl. passive-death.mode):
      katt = következő opció. Minden módosítás a config.yml override-ba íródik és AZONNAL él
      (reload + validátor fut); a teljes kulcskészlet továbbra is `/icesmp config set|find`.
- [ ] **Dinamikus árfolyam:** több valuta a szerveren → kevesebbet ér (`/currency rates`).
- [ ] **Valutaváltó GUI** (`/menu` → Bank & Pénz → Valutaváltó): forrás-választó fent, cél-választó
      lent (a forrással azonos valuta szürke, nem választható), középen élő árfolyam + 64-es előnézet;
      a 16/32/64/mind gombok a `/currency exchange` parancsot futtatják, és a váltás után a kiválasztott
      pár megmarad; fővároson kívül a váltás elutasítva (capital-only).
- [ ] **Piac:** `/market sell <ár>` a kézben tartott tárgyra (max 5 tétel); `/market` vétel a bankból;
      `/market cancel` visszavon. Eladásnál ~10% „elég" (money sink).
- [ ] **Frakció-bolt NPC:** rakj ki egy FancyNpcs NPC-t `altalanos_bolt` néven → jobb-katt megnyitja
      a vásárló GUI-t; kattintás vesz (bankból fizet, a pénz ELÉG — money sink), tele táska a földre
      dob. Elég fedezet híján hibaüzenet; `faction`-korlátozott boltban más frakciós tag nem vehet.
- [ ] **Parancs-NPC:** `/npcbind <npc> command spellbook` után az NPC-re kattintva a JÁTÉKOS
      futtatja a parancsot (a saját jogaival — pl. `command icesmp reload` sima játékosnak
      jog-hibát ad, nem fut le); `/npcbind <npc> clear` visszaállítja.
- [ ] **Kereskedő-karaván:** `/events caravan arrive` → broadcast + megjelenik a vándorkereskedő
      (WanderingTrader) a közeledben; jobb-katt megnyitja a ritka-portéka boltját (nem a natív
      trade-et!), vétel a bankból ELÉG. `/events caravan depart` → broadcast + eltűnik; utána a
      korábbi NPC-re kattintva a bolt már nem nyílik. Az entity **sebezhetetlen** és nem tolható.
- [ ] **Aukció:** `/market auction <ár> [óra]` indít; a GUI-ban **bal-katt** = min. licit,
      **jobb-katt** = nagyobb ugrás (+25%) — mindkettő bankból zárol; másik játékos túllicitál →
      az első **visszakapja** a zárolt licitet + üzenetet kap.
- [ ] **Buy-out:** `/market auction <ár> [óra] buyout:<ár>` (a buy-out ≥ kikiáltási ár, különben
      hibaüzenet); a GUI-ban **shift-katt** → azonnal megnyered a buy-out áron, a tárgy `/market
      claim`-mel átvehető, az eladó megkapja a bevételt (−díj).
- [ ] **Aukció-lejárat:** rövid (pl. 0.05 óra = 3 perc) aukció lejár → nyertesnél a tárgy, eladónál
      a licit (−10% díj); licit nélkül a tárgy visszajár. Offline nyertes **belépéskor** vagy
      `/market claim`-mel kapja meg.
- [ ] **Aukció-védelem:** saját aukcióra nem licitálhatsz; élő licites aukció `/market cancel`-lel
      nem vonható vissza; legmagasabb licitálóként nem licitálhatsz rá még egyszer — de a
      **buy-outot a vezető licitáló is használhatja** (a saját zárolt licitje visszajár).
- [ ] **Reputáció-árazás:** ellenséges/raidelő frakciótól drágább (+25%), szövetségestől olcsóbb (−10%).
- [ ] **Adó:** óránként a frakciótagok a valuta-egyenlegük 2%-át a kasszába fizetik (Semleges mentes).
- [ ] ⚠️ **Folia:** vásárolj olyan eladótól, aki **másik régióban/máshol van** → az eladó értesítése
      hibamentes (cross-entity).
- [ ] **Árfolyamtábla:** `/exchangeboard place` hologram lerak; magától frissül; `/exchangeboard remove`.
- [ ] **Adomány-láda:** `/adomany add` a kézben tartott tárgyat (teljes stack) a közös ládába teszi
      — a tárgy csak SIKERES adományozás UTÁN tűnik el a kezedből (üres kézzel / kapacitás
      betelt / saját-limit elérve esetén hibaüzenet, a tárgy marad nálad). `/adomany` megnyitja a
      lapozható böngészőt (45 tétel/oldal); kattintás egy tárgyra **azonnal elviszi** (nincs ár).
      Már elvitt tételre kattintva "már elvitte valaki más" hibaüzenet, nincs dupe. Az
      "Adományozás" csempe a GUI-ban ugyanazt csinálja, mint `/adomany add` (a kezedben lévő
      tárgyat adományozza). A Főmenü gomb visszaviszi a `/menu`-be.
- [ ] ⚠️ **Adomány-láda dupe-teszt:** két játékos (vagy gyors dupla katt) egyszerre próbálja
      elvinni ugyanazt a tételt → csak az egyik kapja meg a tárgyat, a másik hibaüzenetet kap.

### 4.8.1 Frakcióterületek (zónák) ✅
- [ ] `/territory circle|setcapital|remove|list|info` admin parancsok működnek.
- [ ] **Poligon-zóna:** `/territory pos` több ponton (pl. egy fal mentén), `/territory points`
      listáz, `/territory undo` visszavon, `/territory show` kirajzolja, `/territory create
      protected-city <frakció> <id>` lezárja (≥3 pont kell). Belül vagy-e a poligonon: `/territory info`.
- [ ] Területhatár átlépésekor típusfüggő action bar üzenet jön (főváros / védett város / védett
      frakcióterület / frakcióterület).
- [ ] **Védett zóna** (capital / protected-city / protected-faction) — alapból teljes védelem:
  - [ ] **build**: senki nem tör/rak blokkot, nem használ vödröt, nem szed le képkeretet/armor standot.
  - [ ] **interact**: konténer/ajtó/gomb/kar/műhely jobbklikk tiltott.
  - [ ] **pvp**: játékos↔játékos sebzés (közelharc ÉS nyíl/lövedék) blokkolva, a támadó action-bar üzenetet kap.
  - [ ] **explosions**: creeper/TNT nem tör blokkot a zónában.
  - [ ] **fire**: tűzcsiholó nem gyújt, a tűz nem terjed/nem éget a zónában.
- [ ] **Kárhozat-zóna (doom-gate, ÚJ — K7):** jelölj ki egy zónát — frakció NEM kell:
      `/territory circle doom-gate <id> <sugár> Kárhozat Kapuja` (a régi, frakciós forma is él), majd:
  - [ ] Belépéskor sötétvörös action bar („senkiföldje: itt nincs törvény"), baljós Nether-hang +
        hamu/lélek particle-örvény.
  - [ ] **PvP legális** a zónában (`rules.doom-gate.allow-pvp: true`), de a frissen belépő ~8 mp
        **belépő-védelmet** kap (a támadó action-bar üzenetet lát); aki maga támad, azonnal
        elveszti a védelmét. Kilépéskor / kilépő játékosnál a grace törlődik.
  - [ ] **Ölés itt nem bűn**: PvP-kill után a killer „A Kárhozat Kapujánál nincs törvény" action
        bart kap, a bűn-számláló NEM nő (`territory.doom-gate.sin-exempt`). Raid-szentesített és
        bounty-ölés szabályai előrébb valók (azok változatlanok).
  - [ ] **Mob-boost + keményítés**: a zónában spawnoló mobok +3 szintet kapnak, nappal sem
        égnek, nem zombisodnak (`territory.mob-rules.doom-gate` — bonus-levels /
        no-daylight-burn / no-zombification; BÁRMELY zóna-típusra vagy tulajdonos-frakcióra
        — pl. `dark:` — is megadható).
  - [ ] Építés/robbanás/tűz tiltott az arénában, ajtó/oltár interakció szabad; claim nem rakható.
  - [ ] Minden kulcs élőben olvasódik → `/icesmp reload` után restart nélkül él.
- [ ] **K10 Feketepiac + Caldestera törvényei (ÚJ):** rakj ki `feketepiac` nevű bolt-NPC-t a
      Botera-negyedbe → Csontveretért árulja a két csempész-árut. **Fegyvertilalom:** a NEUTRAL
      fővárosban nyílt fegyverrel a kézben az őrség elrakatja (inventoryba kerül, action-bar);
      a **Bokic-menti Sétapálca** (bot!) átcsúszik — közelharcban +5 flat sebzés, de CSAK a
      főváros zónáján belül (`signature.setapalca.bonus-damage` + `capital-only: true` —
      a városon kívül a bot csak bot). **Körözött-kapu:** vérdíjas játékost a határon
      visszafordít; **Hamisított Menlevéllel** a zsebében beengedi. Kulcsok:
      `territory.capital-law.*`; a bolt-áru name/lore/signature mezőit a ShopManager stampeli.
- [ ] **I14 „Készítette: X" (ÚJ — Tier S):** craftolj NEVES/gear receptet → a lore alján halvány
      dőlt sor „Készítette: <név>" + PDC (crafted_by/crafted_at); a piacra téve is megmarad
      (márkajelzés). Bulk (stackelhető) eredményen NINCS (stack-törés ellen). Kapcsoló:
      `crafted-by.enabled` (profession-recipes.yml). Régi, címke nélküli itemek hibátlanul
      működnek tovább. **Bélyeg-kivétel:** a STACKELHETŐ unique-alkatrészre (affix nélkül, pl.
      `ures_kupa`) NEM kerül bélyeg — különben a craftolt és a visszakapott példány két külön
      kupacban állna. Teszt: craftolj kupát, majd igyál meg egy italt → a visszakapott üres kupa
      a craftolttal EGY stackbe álljon.
- [ ] **Új recept-eredmény kulcsok (ÚJ):** a katalógus három új `result` mezőt ismer —
      ellenőrizd mindhármat:
      - **`rarity`** → a tárgy vanília raritás-színt kap a tooltipben (a saját raritás-létránk
        `ItemDataFactory.vanillaRarityOf` szerint képezve le a 4 vanília fokra);
      - **`use-remainder`** (`unique:<id>` alakot is elfogad) → a tárgy elfogyasztása után a
        megadott tárgy marad a kézben. Kocsma-kör: `ures_kupa` → ital (pl. `jeghegyi_sor`) →
        megivás → **ugyanaz az üres kupa** kerül vissza, tehát a kupa körbe-körbe járhat;
      - **`use-cooldown`** (`{group, seconds}`) → a használat után a megadott csoport minden
        tárgya visszatöltés alatt van (a vanília cooldown-animáció látszik).
- [ ] **Hozzávaló-fedezet = hozzávaló-fogyás (ÚJ, visszaesés-teszt):** a készlet-ellenőrzés és a
      levonás UGYANAZT a szűrőt használja, ezért **identitással bíró** tárgy (unique, signature,
      „Készítette"-bélyeg, saját név vagy lore) hozzávalóként NEM számít és NEM is fogy el;
      a sérült/kopott szerszám viszont igen (számít ÉS fogy).
      **A hiba, amit keresünk:** ha egy craftolt tárgy fedezi a saját receptje plain hozzávalóját,
      de nem fogy el, akkor a hozzávaló INGYEN van (65 recept eredménye osztozik anyagon a saját
      plain hozzávalójával — pl. minden kocsma-ital `HONEY_BOTTLE`, a signature-fegyverek `BOW`/
      `CROSSBOW`/`NETHERITE_SWORD`). Teszt: legyen a hátizsákban CSAK egy craftolt Jéghegyi Sör
      (plain mézes üveg nélkül) → a Jéghegyi Sör receptje NE legyen craftolható. Ugyanez egy
      elkészült Kallan Szeletelőjével a `kallan_szeletelo` receptre.
- [ ] **D18 `/lore` kódex-parancs (ÚJ — Tier S):** `/lore` felsorolja a témákat;
      `/lore lang|fagy|menedek|kitaszitottak|fa|kapu|suttogok` (aliasok: red/blue/neutral/dark
      stb.) 5 sor kánon-szöveget ír chatbe; a szövegek messages-kulcsból felülírhatók
      (`faction-lore.<téma>.<sor>`). A fővárosi lore-pontok (tábla/NPC) admin-elhelyezésűek —
      ugyanezek a szövegek használhatók hozzájuk.
- [ ] **B15 Heti Krónika (ÚJ — Tier S):** `chronicle.interval-days` (default 7) ütemben a
      Bankárszövetség krónikája broadcastol: liga-állás + top3 szint/vagyon/raid-ölés, változatos
      nyitó/záró sablonokkal; `/kronika` (alias /chronicle) bármikor visszaolvassa az utolsó
      számot; restart-álló (chronicle.yml). Teszthez: `chronicle.interval-days: 0.001` NEM megy
      (napokban egész) — állítsd 1-re és várd ki, vagy ellenőrizd reload után az első kiadást.
- [ ] **H2 Rontás-góc (ÚJ — Tier S):** ritkán (`corruption.*`) a vadonban SCULK_CATALYST mag
      nyílik broadcast-tal; a zóna ÉJSZAKÁNKÉNT nő (+4 blokk, 64-ig), korrupt, glowing mobokat
      szül (cap: 12 egyszerre — entitás-fék tesztje!); terepet NEM ír át (csak az 1 mag-blokk).
      **Tisztítás:** ölj 15 korrupt fajzatot (számláló-üzenet a magnál), majd SHIFT+jobb katt a
      magra → loot + 5 perc regeneráció + „a Fa fellélegzik" broadcast; a mag kézzel NEM
      bányászható. Restart-teszt: a zóna corruption.yml-ből visszaáll. Spawn-hely: a
      spawn-rules.corruption mátrix-sor szerint (városba nem nyílik).
  - [ ] **P4e mag-aura (icesmp:rontas):** a magtól `corruption.aura.radius` (alap 5) blokkon
        belül állva ~2 mp-enként fél szív sebzés; belépéskor kezdődik, kilépéskor megáll.
        HALÁL ott → magyar üzenet: „… testét felemésztette a rontás." (NEM vanília halál-szöveg).
        Creative/spectator MENTES. `corruption.aura.enabled=false` → azonnal (reload) megszűnik.
        Élő-config: `damage`/`radius` állítása `/icesmp reload` után restart nélkül hat.
        Bootstrap-log: a damage-type regisztrációja az első induláskor hibátlan (nincs error-sor).
- [ ] **B42 Régészet (ÚJ — Tier S):** időnként gyanús homok/kavics lelőhely bukkan fel
      (broadcast + koordináta, `archeology.*`); ECSETTEL kiásva SZERVER-SAJÁT lelet esik
      (Emlékszilánk / Ősi Ereklyeszilánk / anyagok — sosem vanília cserép); 20 perc után a
      terep nyomtalanul visszaáll (ki nem ásott lelőhelynél is). Spawn-hely: a
      spawn-rules.archeology mátrix-sor szerint. Restartnál a lelőhely-blokk visszaáll.
- [ ] **B3 Kazamaták (ÚJ — Tier S, plugin-oldal):** jelölj ki DUNGEON zónát
      (`/territory circle dungeon <frakció> <id> <sugár> [név]`) → belépni csak kulccsal lehet
      (signature: `dungeonkulcs_<zóna-id>` — bolt/recept adja, minta a factions.yml-ben).
      Belépéskor a kulcs ELFOGY: 2 órás futam-passz (ki-be járás szabad) + 7 napos pecsét
      (új futam tiltva, action-bar a hátralévő napokkal). Admin-bypass: territory bypass jog.
      A bent spawnoló mobok +5 szintet kapnak (mob-rules.dungeon), nappal sem égnek;
      zóna-védelem: allow-séma dungeon-sor (építés/robbanás tiltva, interakció/PvP szabad).
      A pálya megépítése + boss-telepítés admin-munka. Kulcsok: `territory.dungeon.*`.
- [ ] **Frakcióterület** (`faction`): `build` csak a NEM-tagot tiltja (tag épít), `interact/pvp/
      explosions/fire` alapból szabad — a `rules.faction.*` kapcsolókkal külön állítható.
- [ ] **Bypass:** `icesmp.admin.territory.bypass` mindent megkerül (PvP is);
      `icesmp.territory.builder` védett zónában is építhet/interaktálhat, de PvP-zni NEM.
- [ ] **Kill-switch:** `protect-zones: false` → a védett zónák minden szabálya kikapcsol.
- [ ] **Grief-rések (védett zóna):** enderman nem visz el blokkot; kívülről víz/láva nem folyik be;
      dugattyú nem tol be blokkot; TNT nem pusztít képkeretet/armor standot.
- [ ] **PvP-rések (biztonságos zóna):** nyíl/lövedék, farkas (háziállat), TNT-sebzés és ártó
      splash/lingering bájital sem hat a játékosra; a támadó action-bar üzenetet kap.
- [ ] **`/territory tp <id>`** a zóna középpontjához teleportál (a legfelső blokkra; Y-korlátnál a sávba).
- [ ] **`/territory show <id>`** tetszőleges (nem alattad lévő) zóna határát is kirajzolja.
- [ ] **messages/territory.yml** felülírja az alapszövegeket (pl. `territory-pvp-denied`).
- [ ] **Claim tiltás:** védett zónában a `/claim` és `/claim area` elutasítva
      (`claim-in-protected-zone`); **normál frakcióterületen viszont ENGEDETT** (alapból
      `claims.block-in-territory: false`). Kis poligon-zóna szélén is véd (sarok+közép próbák).
- [ ] **Zóna-módosítás:** `/territory rename|resize|settype|sety <id> ...` a meglévő zónát módosítja
      (a `resize` poligonra elutasít); `settype ... capital` a régi fővárost lefokozza.
- [ ] **Magassági sáv:** `/territory sety <id> 60 ~` után a zóna csak Y=60 felett véd; az `info`
      a „Magasság" mezőben mutatja; a `~`/`*` = korlátlan.
- [ ] **Poligon-validáció:** önmetsző határvonalnál (a fal átvágja saját magát) a `create` elutasít.
- [ ] **Teljesítmény:** sok zónával is gyors a mozgás/építés (chunk-index; a lookup nem lassít).
- [ ] Régi `territories.yml` (csak `capital: true/false`) betöltése: capital→CAPITAL, egyébként FACTION.

### 4.9 Relikviák + rituálé-oltárok ✅
- [ ] `/relic give <j> <id>` → a relikvia megjelenik; `/relic list` az id-khez.
- [ ] **Mételytépő** megjelöli/bünteti a bűnösöket; **PvP-ben** ölésnél az új gazdája a gyilkos lehet.
- [ ] **4 frakció-elytra** csak a tulajdonos + a megfelelő frakció tagja használja; a passzív szárnyak
      PvP-ben **nem** cserélnek gazdát.
- [ ] **Szárny frakció-kapuk (ÚJ):** a 4 szárny-rituálé csak a SAJÁT frakcióval végezhető el
      (requires-faction — RED-ként a Csontszárny oltára elutasít); idegen frakció tagja a szárnyat
      a **földről sem veheti fel** (`relics.wings.faction-locked-pickup`, action-bar üzenettel).
- [ ] **Csontszárny árnyék-forma (JAVÍTVA):** DARK-ként ÉJJEL siklás közben az árnyék-forma
      (láthatatlanság + gyorsaság + lélek-particle) FOLYAMATOSAN fennmarad a repülés alatt (2 mp-es
      frissítéssel), és leáll leszálláskor/napkeltekor. A teljes éjszaka számít (hajnal előtt is);
      Netherben/Endben mindig él (örök félhomály). Nappali felszállásnál action-bar jelzi, miért
      nincs effekt.
- [ ] **Rituálé-oltár:** a megfelelő oltár-blokk + áldozati tárgyak + **SHIFT+jobb katt** → megidézi a szárnyat.
- [ ] **Egy-példány szabály:** ha él a tulajdonos, nem idézhető/adható újra.
- [ ] **Multi-block szentélyek (5×5):** hiányos szerkezettel (csak a mag-blokk áll) az oltár hibát
      ír és nem aktiválódik; a teljes szentély (5×5 alapzat + 4 két-magas saroktorony) megépítve működik.
- [ ] **Típusos oltárok** (config `type`): **Feloldozás** (Lélek-lámpás) leveszi a bűnös-jelet és
      nullázza a bűnöket — de sötét paktumossal elutasítja; **Hazatérés-kő** (Lodestone) a frakció
      fővárosába teleportál (főváros híján hibaüzenet). Sikertelen kimenetnél az áldozat NEM fogy;
      cooldown alatt ismétléskor hiba.
- [ ] **Kaszt-szentélyek (13):** mindegyik `*_szentely` csak a saját kasztjának aktiválódik
      (`requires-class`), más kaszt hibaüzenetet kap; a buff a táblázat szerint felkerül.
- [ ] ⚠️ **Folia:** a Mételytépő ölés-büntetése a gyilkost **másik régióból** is hibamentesen jelöli.

### 4.10 Pet / minion ✅
- [ ] Vadmester/Nekromanta: `/pet item` befogó eszköz → jobb katt a célon → társ; `/pet name|summon|dismiss|info`.
- [ ] A társ szintet lép a gazda öléseiből; sunyítás+jobb katt rajta → állásváltás (Támadás/Passzív/Maradj).
- [ ] Nekromanta: minden ölés után lélekszilánk (`/souls`); `/souls champion` bajnokot idéz.

### 4.11 Küldetések + bűn-rendszer + Sötét ✅
- [ ] `/quest list|accept|info|abandon`; a haladás az action barban; teljesítéskor jutalom.
- [ ] **Onboarding-lánc (ÚJ, első belépés):** vadonatúj játékos joinkor automatikusan megkapja a
      „Beszélj a hírnökkel" questet (`onboarding_herald`, üdvözlő üzenettel); teljesítéskor
      **auto-indul** a következő („Első csata": ölj 5 szörnyet → „Első gyűjtögetés": 10 rönk,
      jutalom: valuta + csákány + kenyér). Előfeltétel a szerveren: `hirnok` nevű NPC a semleges
      fővárosban (`/npcbind hirnok faction` ajánlott). Meglévő játékosnál NEM indul újra.
      Kikapcsolás: `quests.yml` → `onboarding.enabled: false`. Quest-láncolás: a quest `next`
      mezője (adminból is állítható: `/quest admin set <id> next <köv-id>`).
- [ ] `/quest complete <j> <id>` (admin) azonnal teljesít.
- [ ] **Bűn:** ölj meg egy másik játékost → +1 bűn; **4 bűnnél** automatikus száműzetés a Sötétbe (örök paktum).
      (Raid alatt a hadakozók közti ölés **nem** bűn.)
- [ ] **Árulás:** öld meg a SAJÁT frakciótársadat → **+2 bűn** külön üzenettel. (Semleges–Semleges
      ölés sima gyilkosság, +1.)
- [ ] **Lopás:** végy ki tárgyat egy **másik frakció territóriumában** álló ládából → +1 bűn üzenettel;
      ugyanabban a területen 1 percen belül több kivét **nem** ad újabb bűnt. Saját területen és
      claimeletlen vadonban nincs bűn; virtuális GUI-k (piac, menük) nem érintettek.
- [ ] **Lopás-kivételek:** raid-háború alatt a hadviselő fél területén a zsákmányolás nem bűn;
      a `icesmp.admin.territory.bypass` joggal szintén nem.
- [ ] **Fejvadászat:** vigyél fel egy játékost 3+ bűnre (`/sinner <j> add`) → megjelenik a `/bounty`
      listán fejpénzzel. Öld meg → a gyilkos MEGKAPJA a fejpénzt (bank), NEM kap érte bűnt, a
      célpont bűnszámlálója 0-ra áll (de bűnös marad); broadcast jelzi. 3 bűn alatt sima gyilkosság.
- [ ] **Mester-lánc (NPC):** rakj ki egy FancyNpcs NPC-t `harcos_mester` néven; a `warrior_trial`
      után vedd fel a `warrior_mentor` questet, katt az NPC-re → a mentor-quest teljesül ÉS az NPC
      azonnal ADJA a `warrior_master_trial`-t (❕ üzenet); az 20 megerősített (Lvl 2+) szörny
      leölésével teljesül. Mind a 13 kasztnak van mentor+mester-próba lánca; a Boszorkánymesteré
      a `pakt_mester` NPC-hez kötött (nincs külön `boszorkany_mester`).
- [ ] **NPC-marker (per-player):** akinek felvehető questje van az NPC-nél → ARANY aura az NPC
      felett; akinek aktív TALK_TO_NPC questje szól hozzá → ZÖLD aura; egy harmadik játékos
      (feltétel nélkül) SEMMIT nem lát. A marker ~2 mp-enként pulzál, ~48 blokkos körzetben.
- [ ] **Sötét Beavatás** küldetés feloldja a Nekromantát.
- [ ] **Admin quest-szerkesztő:** `/quest admin create proba_quest KILL_MOBS 5 Próba Quest` →
      `/quest admin set proba_quest rewards.class-xp 100` → játékosként `/quest accept proba_quest`,
      5 mob után teljesül. `set`-tel giver-npc is adható (NPC adja + arany aura); `delete` törli;
      a configbeli questek NEM szerkeszthetők/törölhetők innen. Restart után is megmarad
      (custom-quests.yml).
- [ ] **Új objektívák:** próbálj ki párat — PLACE_BLOCKS (blokk-lerakás), COLLECT_ITEMS
      (felvett tárgyak, stack-nyi haladás), KILL_PLAYERS (PvP), BREED_ANIMALS, ENCHANT_ITEMS,
      CONSUME_ITEMS (evés); DELIVER_ITEMS: vidd a tárgyakat a megadott NPC-hez → kattintásra
      ÁTVESZI őket (kevesebbnél action bar mutatja, mennyi van nálad). Bővebb készlet:
      olvasztás (kohó), állat-szelídítés (taming), falusi kereskedés, bióm-felfedezés,
      raid-győzelem, világboss-ölés.
- [ ] **Több-objektívás quest (ALL):** `/quest admin create multi KILL_MOBS 5 Több Feladat` →
      `/quest admin addobjective multi COLLECT_ITEMS 16 Kenyér` → `/quest admin set multi objectives-mode ALL`
      → felvéve a `/quest info`/HUD MINDKÉT feladatot külön mutatja (pl. Szörnyek 0/5 • Kenyér 0/16),
      bármely sorrendben halad, csak MINDKETTŐ kész után teljesül.
- [ ] **Több-objektívás quest (SEQUENCE):** ugyanez `objectives-mode SEQUENCE`-szel → csak az
      AKTUÁLIS lépés halad, a következő csak az előző után nyílik (story-lánc).
- [ ] **Küldetésnapló GUI:** `/quest log` (`gui`, `naplo`) → három fül: **Aktív** (haladással;
      shift-katt = feladás), **Felvehető** (katt = felvétel), **Teljesített**; sok questnél lapozható.
- [ ] **Ismétlődő (repeatable) quest:** teljesítés után NEM vehető fel újra azonnal; a config-beli
      cooldown (pl. 24 óra) letelte után ismét felvehető.
- [ ] **Szezonális quest:** szezononként CSAK EGYSZER teljesíthető; új szezon indulása után újra elérhető.
- [ ] **Választós párbeszéd:** olyan quest-NPC-nél (`dialogue.choices`), ahol a párbeszéd után
      kattintható válaszopciók jelennek meg a chatben → különböző opció KÜLÖNBÖZŐ következő questet indít.
- [ ] **NPC napi rotáció:** `rotation-group`-os NPC egy poolból naponta csak a beállított számú questet
      kínálja; a kínálat naponta (nap váltásakor) frissül — más questek jelennek meg.
- [ ] **Frakció-közösségi cél:** a `community-goals`-hoz tartozó tevékenységet többen végezve a MEGOSZTOTT
      számláló nő (minden frakciótag beleszámít, nem egyéni felvétel); a cél elérésekor az egész frakció
      **kassza-jutalmat + rövid buffot** kap, majd a számláló újraindul.
- [ ] **Saját-frakció valuta jutalom:** `/quest admin set proba_quest rewards.currency.type OWN`
      + amount → teljesítéskor a játékos a SAJÁT frakciója valutáját kapja (Piros → piros token).
- [ ] **NPC-párbeszéd:** `/quest admin set proba_quest dialogue.speaker Aldric mester`,
      `... dialogue.give Üdv vándor!|Van egy feladatom.` → quest-átvételkor az NPC „mondja" a
      sorokat ~1,5 mp-enként; `dialogue.complete` a teljesítéskor szól (parkour-célnál is).
      A mester-láncok gyárilag kaptak példa-dialógust.
- [ ] **Vezeklés-lánc** (3 rész) az EGYETLEN mód a paktum megtörésére.
- [ ] ⚠️ **Folia:** a bűn-jelölés a gyilkost másik régióból is hibamentesen jelöli.

### 4.12 Király, raid, kassza, szezon ✅
- [ ] `/faction king` (szavazás/koronázás); a király kivehet a kasszából, adót állít, raidet hirdet.
- [ ] `/faction treasury`, `/faction donate`; az adó és adományok töltik.
- [ ] `/faction raid <cél> [terület]` (csak király); alapból a védő fővárosáért folyik; a hirdető
      király automatikusan harcos. `/faction raid status` mutatja a fázist/pontokat/létszámot.
- [ ] **Jelentkezés:** a felkészülés alatt `/faction raid join` (max 10/oldal — a 11. jelentkezőt
      elutasítja); a bossbar a felkészülés alatt erre hív, harc alatt a pontállást mutatja.
- [ ] **Résztvevő-szabály:** csak a JELENTKEZETT harcosok közti ölés szentesített és pontozó;
      nem-jelentkezett hadviselő-frakciós ölése raid alatt is bűn (gyilkosság/árulás).
- [ ] **Zóna-szabály:** területkötött raidnél a zónán KÍVÜLI ölés szentesített, de nem ér pontot
      (külön üzenet); a zóna középpontján állva ~5 mp-enként pont jár (action bar jelzi).
- [ ] **Terület-átvétel:** ha a támadó nyer, a terület átkerül hozzá (broadcast; fővárosi státusz
      elvész); védő győzelemnél / döntetlennél marad. A végén hadizsákmány + győztes-buff.
- [ ] **Ostromágyú** (craftolható) csak aktív raid alatt sül el; terep-barát robbanás.
      ⚠️ **Folia:** célozz **távoli** pontra (másik régió) → a robbanás ott történik, konzol-hiba nélkül.
- [ ] `/events season` mutatja a liga-pontokat; a szezon végén jutalom + reset.
- [ ] **Szezon-győztes tagi jutalom:** a szezon lezárultakor a győztes frakció KASSZÁJA kapja a
      treasury-reward-ot, az ONLINE TAGJAI pedig győzelmi buffot (Erő/Regen/Falu Hőse) + tárgy-jutalmat
      (config: champion-reward-items) + ünneplő tűzijátékot; a más frakciós tagok semmit. Döntetlennél/
      pont nélkül nincs bajnok, nincs tagi jutalom.

### 4.13 Világesemények ✅
- [ ] **Mob-szintezés:** a spawntól távolodva erősebb, `[Lvl X]` nevű mobok; a névtábla csak ránézésre
      jelenik meg; spawner/parancs-mob nem skálázódik.
- [ ] **Vérhold** (`/events blood-moon start`): erősebb mobok + dupla lélekkő-esély; broadcast.
- [ ] **Lélekkő élőhalott-kivétel:** DARK játékos zombi/csontváz-ölése NEM ad lélekkövet (élő
      szörny — pl. creeper — igen); Nekromanta élőhalott-ölése NEM ad lélek-szilánkot, élő igen
      (`currency.soul-drop.dark-undead-drops` / `souls.shards-from-undead`).
- [ ] **Világboss** (`/events worldboss`): véletlen archetípus, név, aura-debuff a közelben; ~8 mp-enként
      telegrafált képesség; **50% HP alatt feldühödik**; legyőzve kassza+pont+buff.
      ⚠️ A SUMMON-special által idézett add-ok egy idő után **eltűnnek** (nem maradnak ott örökre).
- [ ] **Boss-telegraph (ÚJ):** a SLAM/ZONE special előtt **részecske-gyűrű** rajzolja ki a veszélyzónát
      (5, ill. 3 blokk sugár) + Warden-hang; a SUMMON előtt Evoker-idéző hang szól — kivédhetőbb a special.
- [ ] **Boss-telegraph padló-lap (DisplayFx, ÚJ):** a gyűrű mellett egy lapos, piros, IZZÓ padló-lap
      NŐ kicsiről a teljes zónáig a ~1,5 mp figyelmeztetés alatt, és a becsapódáskor eltűnik — sokkal
      olvashatóbb „lépj ki innen". Nem marad blokk-szemét (a special után, relog/restart után sem).
      Kapcsoló: `display-fx.boss-telegraph.enabled: false`; a lap anyaga configos. ⚠️ Folia: régióhatáron
      spawnolt bossnál is hibamentes.
- [ ] **`/events status` (ÚJ, mindenkinek):** „Mi történik most?" — kilistázza az összes épp aktív
      világeseményt (vérhold/boss/invázió/karaván/gyűjtögető/kincs/Vad Hajsza/bőség/kihívás/kíséret/meteor,
      hátralévő perccel) + a szezon-állást; üresen „nyugalom van" üzenet. A `/menu` → Események almenü
      tetején ugyanez **óra-ikonként** (kattintásra lefuttatja a parancsot).
- [ ] **Invázió** (`/events invasion`): horda + megnevezett bajnok (telegrafált földcsapás); extra XP/lélekkő.
- [ ] **Esemény-spawn szabályok (ÚJ):** világboss / invázió / Vad Hajsza **NEM spawnol** claimelt
      frakció-territóriumba, játékos-claimbe, WG-régióba (városok), sem víz tetejére — állj be egy
      városba és `/events worldboss|invasion|wild-hunt`: nem jelenik meg semmi (a következő
      intervallumban máshol próbálkozik). Config: `world-events.spawn-rules` esemény×védelem
      mátrix (world-boss/invasion/wild-hunt/treasure/meteor × territory/claim/region/water,
      minden cella külön kapcsolható) + `world-events.spawn-rules-enabled` mester-kapcsoló (a régi avoid-territory fallbackként él); a
      kulcsok élőben olvasódnak → `/icesmp reload` után restart nélkül él. ⚠️ A régi
      `meteor.avoid-territory` kulcs megszűnt (a mátrix meteor-sora váltja).
- [ ] **Esemény-mob keményítés (ÚJ):** a Pokoli Hadúr boss / Alvilági Roham piglinjei / Pokoli Behemót
      az overworldben **NEM zombisodnak át** (várj mellettük ~15 mp-et); az invázió csontváz/zombi
      mobjai és a boss SUMMON-addjai **nappal nem gyulladnak meg**. ⚠️ Zombisodás esetén korábban a
      kill tévesen bűnnek számított (új entity, elveszett tracking) — ennek is tesztje ez.
- [ ] ⚠️ **Folia — invázió-bajnok földcsapás régióhatáron:** a bajnok slamje másik régióban álló
      játékost is hibamentesen sebez/lök (scheduler-hop, nincs IllegalStateException a konzolban).
- [ ] **Hangulat-események** (`/events ambient`): broadcast + kozmetikai effekt; az északi fény rövid
      éjjellátást ad, az állat-vándorlás passzív csordát idéz a közeledbe (balanszot nem érint).
- [ ] **Gyűjtögető buff** (`/events gathering`): broadcast a kezdetről; bányász-láznál érctöréskor
      bónusz drop, XP-óránál szorzott XP, halászati láznál esély dupla fogásra; a végén záró broadcast.
- [ ] **Kincs** (`/events treasure`): megjelölt láda (részecske-jelző) + broadcast koordinátákkal; a
      rákattintás VAGY törés **egyszer** kiosztja a loot-ot a megtalálónak, aztán eltűnik; lejáratkor
      feltáratlanul eltűnik. A vanilla láda-GUI **nem** nyílik meg.
- [ ] **Vad Hajsza** (`/events wild-hunt`): megnevezett, glowing elit fenevad; leölve ritka loot hullik
      + broadcast a vadászról; ha `expire-minutes`-en belül nem ölik meg, elszökik (broadcast).
- [ ] **Bőség-idő** (`/events abundance`): broadcast; a termés gyorsabban nő, szaporodáskor néha iker,
      kevesebb természetes szörny-spawn, gyengéd regeneráció; a végén záró broadcast. Terephez nem nyúl.
- [ ] **Szerver-kihívás** (`/events challenge`): broadcast + **boss-bar** a közös haladással; szörny-ölés
      / érc-bányászás / termés-betakarítás növeli; célnál MINDEN online játékos jutalmat kap (XP + loot +
      Sietség); lejáratkor bukás-broadcast. Belépő játékos is látja a boss-bart.
- [ ] **Karaván-kíséret** (`/events escort`): ládás láma-konvoj a cél felé halad (boss-bar = haladás + HP);
      ~45 mp-enként szörny-hullám támadja a konvojt; célba érve loot hullik + a karaván-bolt bónusz-készlete
      (`bonus-items`) egy időre elérhető; a konvoj halálakor/lejáratkor bukás. **Terep-teszt:** a kíséret-mob
      robbanása (ha van) **nem tör blokkot**; a konvoj/mobok reload után nem maradnak ott (nem perzisztens).
      **Egyszerre-egy teszt:** aktív kíséret (vagy épp induló spawn-lánc) mellett az újabb
      `/events escort` **false**-szal elhasal — sosem áll két konvoj (a második korábban
      orphanná tette volna az elsőt).
- [ ] **Meteor** (`/events meteor`): kráter jelenik meg érc-blokkokkal + broadcast a koordinátákkal; az érc
      kibányászható (valódi drop). **Terep-teszt (fontos):** `expire-minutes` után VAGY `/reload`/leállítás
      után a kráter **teljesen visszaáll** az eredeti terepre; `avoid-territory: true` mellett **nem** csapódik
      claimelt területre (állj egy `/territory`-be és nézd, hogy máshova kerül).
- [ ] **Party:** `/party invite` két fiókkal → accept után közös csapat; közeli mob-ölés XP-je
      **fejenként oszlik** (floor(xp/n), a maradék a megölőé; ha xp < létszám, csak a megölő kap);
      párttag **nem sebezhető** (kard + nyíl); Vad Hajsza/kincs esemény párttal →
      **mindenki saját (personal) lootot kap**; kilépő játékos kikerül; 2 fő alatt a csapat feloszlik;
      `/p szia` csapat-chat.
- [ ] **Party-HUD:** csapatban a HUD-oldalsávon megjelenik a „— Csapat —" szekció (👑 vezető, tagnév +
      élet-sáv); a társ sebződésekor a sávja **sárgára/pirosra vált** (~1 mp-en belül frissül); a csapat
      feloszlása után a szekció **eltűnik és nem hagy üres sorokat** az oldalsávon.
- [ ] **Megbízott-GUI (ÚJ):** `/menu` → Birtok → „Megbízottak kezelése" (vagy `/claim trustgui`):
      felül a megbízottak fejei (katt = visszavonás), alul a 15 blokkon belüli játékosok (katt =
      megbízás); a kattintás a `/claim trust|untrust` parancsot futtatja és a GUI frissül.
- [ ] **Claim:** `/claim` lefoglal (részecske-határ); másik fiók NEM tud törni/rakni/ládát nyitni benne
      (action-bar üzenet); `trust` után igen; robbanás nem bont claimelt blokkot;
      frakció-territóriumban/WG-régióban a claim elutasítva; a 4. chunktól ár (bankból, ELÉG); `unclaim`
      nem ad vissza pénzt; admin: `/claim admin unclaim`.
- [ ] **Claim terep-védelem:** kívülről gyújtott tűz nem terjed be claimre és claimelt blokk nem ég el;
      claim-határon kívüli dugattyú nem tol be / nem húz ki blokkot (claimen BELÜLI saját gép működik);
      kívülről víz/láva nem folyik be; idegen flint-and-steel gyújtás a claimen tiltva (action-bar).
- [ ] **Chat-formázó:** a chatben `[LP-prefix] Név: üzenet` formátum, a név frakció-színnel; LuckPerms
      nélkül is működik (prefix nélkül). ⚠️ Ha a régi LuckPermsChatFormatterFolia plugin még fent van,
      kapcsold ki az egyiket (dupla formázás)!

### 4.14 GUI-k és HUD ✅
- [ ] `/menu`, `/profile`, `/spellbook`, `/market`, `/leaderboard`, `/achievements`, `/daily` megnyílik,
      a gombok működnek, a kattintások nem visznek ki tárgyat a menüből.
- [ ] **Elérések configból (`achievements.definitions`):** a 21 mérföldkő a `quests.yml`-ben él.
      Teszt: vegyél fel egy ÚJ blokkot (pl. `metric: CLASS_LEVEL`, `threshold: 5`, `reward: 25`)
      → `/icesmp reload` → az `/achievements` listában **azonnal megjelenik**, szerver-újraindítás
      nélkül, és a küszöböt elérve a veret jár. Hibás sor (ismeretlen `metric` vagy nem pozitív
      `threshold`) **kimarad és a konzol figyelmeztet** — a többi elérés ilyenkor is működik.
      FIGYELEM: meglévő elérés **id-jét ne nevezd át** (a teljesítést a játékos PDC-je az id-n
      tartja, átnevezés után újra kiosztódna).
- [ ] **`/stats [név]` (ÚJ):** kiírja a statisztika-profilt (játékos-ölések, halálok, K/D,
      mob-ölések, elsütött spellek, teljesített questek); névvel másik (online vagy már látott)
      játékosé; a számlálók ölés/halál/cast/quest-teljesítés után nőnek és restart után megmaradnak.
- [ ] **Quest builder** (`/quest admin builder <id>`, admin): új id-vel a típus-választó nyílik →
      darabszám + név a chatben → szerkesztő; létező custom questtel rögtön a szerkesztő. Mező-csempe
      kattintás után a chatbe írt érték mentődik ('mégse' megszakít), a kapcsolók (ismételhető,
      szezonális, bűn-tisztítás, objektíva-mód) helyben váltanak; törléshez két kattintás kell;
      a chatbe írt érték NEM jelenik meg a publikus chatben.
- [ ] **Admin panel** (`/menu` → Admin, csak admin-joggal): minden világesemény indítható gombbal
      (vérhold start/stop, világboss, invázió, karaván érkezés/távozás, Vad Hajsza, meteor, kincs,
      gyűjtögető buff, bőség, kihívás, kíséret, hangulat-esemény); a kezelősor (admin unclaim,
      NPC-kötések, quest admin lista) csak a megfelelő jogosultsággal látszik.
- [ ] **P4d Natív dialógus (ÚJ):** ÚJ játékos első belépésekor (az intro-cím UTÁN,
      `onboarding.welcome-dialog-delay-ticks`≈4 mp) natív **üdvözlő-ablak** ugrik fel
      (cím + első-lépés sorok + „rendben" gomb) — resource pack NÉLKÜL. ESC/gomb bezárja.
      `onboarding.welcome-dialog: false` → nem jön. Tartalom élő-configból
      (`onboarding.welcome-dialog-title/-lines`, MiniMessage). Régi játékosnál NEM jelenik meg.
- [ ] **P7 Data-component ételek (ÚJ, CONSUMABLE-migráció):** frissen craftolt signature-étel
      (Fagyasztott pisztráng, Főnixtojás-rántotta, Sárkány-pörkölt, Vadlakoma, Vándorünnep lepénye,
      Hamvak lakomája, Mortengrádi hamukenyér) evésekor a buff MOST a data-komponensből jön —
      a hatás ugyanaz, de **nincs dupla buff** (a listener a food_v2 jelölő miatt kihagyja).
      A Tiltott kakaóbabos sütemény (lökés+partikel) VÁLTOZATLAN (listener-ág). A honvágy-
      kötelezettség (BLUE hal / RED tojás) változatlanul működik. `/icesmp reload` után az
      ÚJ craftok tükrözik a buff-időt (a komponens craft-időben olvassa a configot).
- [ ] **P7 Szakács fogyaszthatók (ÚJ, recept-vezérelt CONSUMABLE + ITEM_MODEL):** a Szakács
      craftolja: **Jéghegyi Sör / Parázs Pálinka / Caldesterai Gyógytea / Mortengrádi Keserű**
      (ITALOK — ivás-animáció + hang!), **Vándor Pogácsája / Halász Fogása** (ÉTELEK). Ivás/evés
      után a megfelelő rövid buff (pl. Sör→Regeneráció+Felszívódás, Pálinka→Tűzállóság+Erő,
      Pogácsa→táplálék+Sietség, Fogás→Vízlégzés). **ITEM_MODEL**: pack nélkül alap-item
      textúrát mutat, a `icesmp:<id>` modellt a külső pack adja. A buff-idők a receptből (config).
- [ ] **Kupa-hurok (USE_REMAINDER, ÚJ):** craftolj **Üres Kupát** (Szakács 5, `GLASS:2` → 4 db),
      abból bármelyik kocsma-italt (mind a 12 kér 1 kupát) → **megivás után az Üres Kupa a
      kezedben marad** (nem sima üvegpalack, hanem a bélyegzett kupa!), és azonnal újra
      felhasználható italhoz. Ellenőrizd, hogy tele hotbarnál sem vész el (a vanília a földre
      dobja), és hogy a kupa a recept-könyvben hozzávalóként elfogy.
- [ ] **P7 USE_COOLDOWN — katalizátor cooldown-bleed javítás (ÚJ):** tarts a hotbarban egy
      pálca-katalizátort ÉS a vele AZONOS Materialú vanília itemet (pl. Homály-szilánk=FLINT →
      sima kovakő; Sárkánykirály Kürtje=GOAT_HORN → sima kürt). Castolás után CSAK a katalizátor
      sötétül el (cooldown-overlay), a vanília item NEM. (Melee-kard katalizátornál a kard
      overlay-e változatlan.)
- [ ] **P7 TOOLTIP_DISPLAY — affix-gear tiszta tooltip (ÚJ):** egy affix-rollos felszerelésen
      NINCS többé a vanília „When in Main Hand: +X Attack Damage" blokk — a stat CSAK a saját
      affix-lore-sorban (pl. „+ 12 Élesség") látszik, egyszer.
- [ ] **P8e Alacsony-HP piros vignetta (ÚJ):** HP a küszöb (alap 30%) alá esve a képernyő
      szélén vörös vészköd jelenik meg; gyógyulva/küszöb fölé eltűnik. `hud.low-hp-vignette.enabled:
      false` → nem jön. A per-player világperem NEM okoz tényleges perem-hatást (nem lök vissza,
      nem sebez). Respawn/reconnect után tiszta állapot. Threshold élő-configból.
- [ ] HUD oldalsáv: frakció, kasztok+szintek, szakmák, talentpontok, egyenleg, **Erő-csík**.
- [ ] Bossbar (világboss/raid) megjelenik — és **nem** ütközik az Erő-csíkkal (az a sidebar-on van).
- [ ] **Natív tablist (ÚJ — TAB-kiváltás, a TAB.jar NÉLKÜL tesztelendő):**
  - [ ] **Header/footer** megjelenik (glyph + animált ping/online sor, twitch/discord marquee);
        az animáció halad, de a lista NEM villog (diff-elt küldés).
  - [ ] **Tab-nevek:** LP-prefix + frakció-színes név + suffix; frakció-váltásnál a szín ~0,5
        mp-en belül átvált, közben nincs villogás.
  - [ ] **Rendezés:** a tablistában elöl a magasabb LP-rang (owner→admin→…→default), azonos
        rangon ABC; a fej fölött is látszik a prefix + frakció-szín (nametag).
  - [ ] **Ping-oszlop** a tablistában él és frissül.
  - [ ] **Oldalsáv új dizájn:** animált elválasztó-vonalak, small-caps címkék (ꜰʀᴀᴋᴄɪó/ᴋᴀꜱᴢᴛ/
        ᴇꜱᴇᴍéɴʏ/⭐ᴠᴀʟᴜᴛᴀ), cím a configból (`hud.sidebar.title`); `/hud` szekció-toggle továbbra
        is működik, és a sidebar kikapcsolása NEM töri el a nametageket/pinget (a board marad).
  - [ ] **Harc-fókusz (ÚJ):** találat adásakor VAGY kapásakor az oldalsáv „kitisztul" — csak az
        Erő-csík (+ party-frame-ek) maradnak; az utolsó találat után ~8 mp-cel a teljes HUD
        visszatér. Kikapcsolás: `hud.dynamic.combat-focus: false`; a látható szekciók a
        `combat-visible-sections` listával hangolhatók.
  - [ ] **Rotáló infósor (ÚJ):** az esemény-sor ~4 mp-enként váltakozik: aktív események ↔
        „ꜱᴢᴇᴢᴏɴ: még ~N nap" ↔ „ɴᴀᴘɪ: x/y" (kész napi kihívásnál zöld ✔); nyugalomban az
        esemény-frame kimarad a rotációból. Kikapcsolás: `hud.dynamic.rotating-line: false`
        (fix esemény-sor).
  - [ ] ⚠️ **Folia:** két játékos KÜLÖN régióban (messze egymástól) — mindkettő látja a másik
        nevét/rangját/pingjét a tablistában, konzol-hiba nélkül.
  - [ ] Ha a TAB.jar mégis fent van: induláskor konzol-figyelmeztetés jön az ütközésről.
- [ ] **Plugin-kiváltások (ÚJ — a jarok NÉLKÜL tesztelendő):**
  - [ ] **Warden-XP** (ICEsmpadditions helyett): Warden leölése 80–125 XP-t dob
        (`world-tweaks.warden-death-xp`, configolható).
  - [ ] **Termés-taposás** (FarmProtect helyett): szántóföldre ugrás (játékos ÉS mob) nem töri
        fel a földet (`world-tweaks.crop-trample-protection`).
  - [ ] **MOTD** (MiniMOTD helyett): a completion branch buildelt; eltávolítás csak az alábbi valódi Folia ping/reload tesztek után;
        idő/random rotáció, eseményprioritás, 64×64 ikonok, vanish-count és reload külön ellenőrzendő.
  - [ ] **AFK-rendszer** (AxAFKZone helyett): csak a `feature/native-afk-zones` scope után;
        közös 3D selectionből létrehozott több zóna, közvetlen zónaváltás, reward-rollok,
        full-inventory és reload/quit/kick lifecycle külön ellenőrzendő. A globális AFK-jelzés
        ettől függetlenül továbbra is tesztelendő.
  - [ ] **Crate-rendszer** (CrazyCrates helyett): csak a `feature/native-crates-completion`
        scope után; lista/preview, permission/world, többkulcsos fogyasztás, cooldown, statisztika,
        strict reward-validáció és full-inventory útvonal is tesztelendő a meglévő fizikai nyitás mellett.
  - [ ] **Ülés** (GSit helyett): csak a `feature/native-sit-poses` scope után; `/sit`, click-to-sit,
        anyag- és világpolicy, unsafe location, tiltott parancs, minden cleanup-esemény, lay/crawl
        és árva seat-entitás külön ellenőrzendő.
  - [ ] **Moderáció** (SModeration helyett): `/mute <név> 5m teszt` → chat és natív `/msg`
        blokkolva; restart után is él; `/unmute`, `/history <név>`, `/punishments <név>` ugyanazt
        az autoritatív ledgert olvassa. Külön tempban/login, SocialSpy és corrupt-state teszt kell.
  - [ ] **Bejelentés:** `/report <név> <ok min 3 szó>` → az online moderátorok
        (icesmp.admin.moderation) azonnal értesülnek; 60 mp-en belül második report
        rate-limitbe fut; `/reports` lista → `/reports resolve <id>`; restart-álló.
  - [ ] **Inspektor:** `/icesmp inspect <név>` (icesmp.admin.inspect) → teljes riport
        (frakció/kaszt+XP/spec/erőforrás/egyenlegek/statok/bűn/claim/questek/cooldownok);
        offline névre korlátozott riport. ⚠️ **Folia:** távoli (másik régiós) célpontra is
        hibamentes (a riport a célpont szálán épül).
  - [ ] **Invsee** (InvSee++ helyett): `/invsee <név> read main|ender` élő read-only nézet;
        `/invsee <név> edit main|ender` csak külön edit permissionnel. Target/admin quit/kick,
        reload és reconnect közbeni escrow-visszaadás két eltérő Folia-régióban is ellenőrzendő.
- [ ] **QoL-kör (A43–A52, ÚJ):**
  - [ ] **Relációs háború-szín:** raid alatt a szemben álló fél tagjai PIROSAK a tablistádban
        és a fejük fölött — a raidben nem érintett harmadik frakciónak viszont NEM; a raid vége
        után a frakció-színek visszaállnak; a tablist-sorrend eközben NEM ugrál.
  - [ ] **AFK-jelzés/hátrasorolás:** ~3 perc tétlenség után „⌚ ᴀꜰᴋ" suffix + a játékos a rangján
        belül a tablist aljára kerül; mozgásra azonnal vissza.
  - [ ] **AFK-jutalomkapu:** AFK-jelölt játékos mob-ölése NEM ad kaszt-XP-t/lélekkövet (auto-farm
        teszt: állj mob-farm mellé 3+ percig); az AFK-zóna időzített jutalma viszont jár.
  - [ ] **Közös kill-jutalom előszűrő (`kill-rewards.*`):** MINDEN ölés-alapú jutalom ugyanazokon a
        szűrőkön megy át, tier szerint. Ellenőrizd:
        - **AFK-jelölten** (3+ perc) a mob-ölés nem ad kaszt-XP-t, lélekkövet, **pénz-erszényt ÉS
          mob-lootot** sem (korábban az erszény/loot még jött);
        - **spawner-mob** (rakj le spawnert) leölve nem dob erszényt/lootot, de **kaszt-XP-t igen**
          (a farmos szintezés szándékos);
        - **kreatív módban** ölve nincs FAUCET/PROGRESSION jutalom, de a **quest-haladás megy**
          (admin-teszt maradjon lehetséges);
        - **saját idézett minion** leölése semmit nem ad — sem lootot, sem XP-t, sem **quest-haladást,
          ranglista-pontot, bestiárium-bejegyzést vagy közösségi cél-számlálót** (ez utóbbi négy
          korábban pumpálható volt).
        Mind a négy szűrő kikapcsolható: `/icesmp config menu` → „Kill-jutalom szűrők".
  - [ ] **A korona átka (ÚJ, `factions.kings.crown-curse`):** ültess trónra egy királyt, majd
      állítsd `hours-per-level: 0`-ra… nem lehet (min. 1) — teszthez vedd `hours-per-level: 1`-re
      és `/icesmp reload`. Egy óra trónon = 1 szint. Ellenőrizd:
      - a szint-lépés EGYSZER szól („A korona hidegebb lett", szint/max),
      - minden szinten lich-türkiz derengés a korona (fej) körül,
      - `whisper-percent` szerint a Királynő suttog: **szintenként SAJÁT 8-soros készlet**
        (össz. 40 sor), és a szintek fokozatosan tárnak fel többet a történetből —
        1: puszta nyugtalanság → 2: a Káoszkor és a 698-as év → 3: az Elveszett Uralkodók
        NEVEI (Zhoris, Miinus, Benedictus, Lineata) → 4: Eleftheria mibenléte (a Fa negyedik
        gyermeke, az első suttogás, a Könny) → 5: nyílt ítélet (a lajstrom, a harmadik mondat).
        Ellenőrizd, hogy magasabb szinten NEM jönnek vissza az alacsonyabb szint sorai,
      - `stakes-from-level` (alap 3) ALATT semmi tét — csak hangulat,
      - fölötte: HUNGER (leáll a természetes regeneráció) + a közeli CÉLTALAN élőhalottak a
        királyt kezdik célozni (aktív célpontot NEM vesz el tőlük),
      - trónfosztás/újraválasztás után az átok **nullázódik** (nincs külön állapota: a
        trónon töltött időből számol),
      - a nem-király játékosokra semmi nem hat, és `enabled: false` mindent kikapcsol.
- [ ] **Célpont-sor:** harc-fókuszban az oldalsáv tetején „🎯 <célpont>" (játékosnál élet-sávval),
        az utolsó találat után ~10 mp-cel eltűnik.
  - [ ] **Crate-rulett:** kulcs-nyitáskor pörgő GUI (~3,5 mp, lassul), a végén a tényleges
        nyeremény áll meg; a GUI IDŐ ELŐTTI bezárása esetén is jár a nyeremény; kapcsoló:
        crates-settings.spin-animation. Quest-jutalomból kulcs: az onboarding_gather ad 1 köznapi kulcsot.
  - [ ] **3D crate-feltárás (DisplayFx, ÚJ):** a láda-blokk FÖLÖTT egy lebegő tárgy-ikon **pörög**
        a lehetséges nyeremények között (~1,5 mp), majd a tényleges nyereményen **megáll, felnagyul
        és aranyszínnel felizzik**, aztán eltűnik — mások is látják a láda mellett. Nem marad
        entitás-szemét (relog/restart után sem). Kapcsoló: `display-fx.crate-reveal.enabled: false`.
        ⚠️ Folia: a pörgetés az ikon saját szálán fut, régióhatár-közeli ládánál is hibamentes.
  - [ ] **Kombó-kiemelés:** kombó/lánc-finisher utáni 3 mp-ben a sebzés-szám nagyobb, arany, "!"-lel.
  - [ ] **Fekvés:** `/sit fekves` (LibsDisguises-szel) fekvő póz; mozgásra/újra kiadva felállás;
        LD nélkül udvarias hibaüzenet. ⚠️ GrimAC false-positive playtest kötelező!
  - [ ] **Mute-eszkaláció:** `/mute <név>` perc NÉLKÜL → 1. alkalom 5 perc, majd 30/180/1440
        (moderation.escalation-minutes); a blokkolt/cenzúrázott üzenetek a
        logs/chat-moderation.log-ba kerülnek (5 MB-nál forgatás).
  - [ ] **Esemény-MOTD:** vérhold indítása után a szerverlista MOTD-ja a vérhold-variánsra vált
        (frissítsd a listát), utána vissza a normál rotációra.
- [ ] **Natív MOTD completion — `feature/native-motd-completion`:**
  - [x] **AUTOMATED:** `motdRegressionTest` — TIME floor-mod, időablakon belül stabil RANDOM, seed-érzékenység és pool-lefedés.
  - [x] **AUTOMATED:** teljes signed-`long` parser (`2^53` fölött, MIN/MAX, kétirányú overflow, string, tört/NaN/Infinity) és minden MOTD boolean típushű validációja.
  - [x] **AUTOMATED:** kizárólag `{online}`/`{max}` placeholder; MiniMessage-only és vegyes MiniMessage/token szöveg változatlanul érvényes.
  - [x] **AUTOMATED:** vérhold > világboss > szezonzárás prioritás és milliszekundum-pontos szezonzáró küszöb.
  - [x] **AUTOMATED:** secure icon-root/köztes/fájl no-follow, traversal tiltás, helyes/hibás méret, sérült/túl nagy PNG és a hét bundled 64×64 ikon.
  - [x] **AUTOMATED:** két reload, scheduler rejection/null handle, task–fallback single-winner és disable utáni késői callback nem publikál cache-t.
  - [ ] **REAL FOLIA REQUIRED / MANUAL:** párhuzamos server-list pingek TIME és RANDOM módban, hiba és region-thread stacktrace nélkül.
  - [ ] **REAL FOLIA REQUIRED:** vérhold, világboss és szezonzárás egymásra fedése; a prioritás pontosan ebben a sorrendben érvényes.
  - [ ] **PERMISSION TEST / MANUAL:** vanished admin megjelenik vagy kimarad az online countból az `exclude-vanished-from-online-count` kapcsoló szerint; nem készül második vanish-state.
  - [ ] **RELOAD TEST:** ikonfájl csere közben két gyors `/icesmp reload`; a régi async generáció nem írja vissza a régi ikonkészletet.
  - [ ] **RELOAD TEST:** `/icesmp config set motd.selection-mode random`, `rotation-seconds`, vanish toggle és `icons.mode` azonnal új snapshotot ad.
  - [ ] **NEGATIVE TEST:** sérült, nem PNG, 32×32, 65×64, root/köztes/fájl symlink és 1 MiB feletti fájl kihagyása; a plugin többi része működik.
  - [ ] **NEGATIVE TEST:** ismeretlen enum, hibás típusú boolean, lebegőpontos/overflow integer, nulla max-player override, üres/65 elemű pool, ismeretlen placeholder, duplikált normalizált ID és hibás strict MiniMessage a MOTD-t fail-safe letiltja.
  - [ ] **MANUAL:** `{online}`/`{max}`, max-player override és NONE/DEFAULT/VARIANT/RANDOM ikonmód a kliens szerverlistájában.
  - [ ] **MANUAL / NEGATIVE:** MiniMOTD jar nélkül teljes átvételi teszt; proxy/vhost és upstream configkompatibilitás nincs és nem cél.

- [ ] **QoL-kör 2 (A53–A62, ÚJ):**
  - [ ] **`/afk`:** azonnali ⌚-jelölés + hátrasorolás + jutalomkapu; bármilyen mozgás/üzenet törli
        (a parancs kiadása maga NEM törli — a toggle megmarad).
  - [ ] **Sidebar-számok:** az oldalsáv jobb szélén NINCSENEK piros sor-számok (blank numberFormat).
  - [ ] **{event}/{ping} tokenek:** a tablist headerben/footerben használható az {event} (aktív
        események) és a {ping} mostantól színkódolt (zöld <80 / sárga <150 / piros).
  - [ ] **Invsee-frissítés:** a 🔄 gombbal új pillanatkép (2 mp-es fék); a cím mutatja a kép korát.
  - [ ] **Kulcs-lore:** a crate-kulcs lore-jában a láda neve + top-3 jutalom esély-%-kal.
  - [ ] **Mute-lejárat:** a némítás lejártakor a játékos üzenetet kap (csak EGYSZER, akkor is, ha
        chat-spammel próbálkozott közben).
  - [ ] **Report-visszajelzés:** resolve után a bejelentő üzenetet kap — online azonnal, offline a
        következő belépéskor (restart-álló).
  - [ ] **Ikon-rotáció:** icons/*.png betöltés a konzol-logban; variánshoz kötött ikon váltáskor
        a szerverlista-ikon is cserél (RP nélkül is működik — ez szerver-oldali).
  - [ ] **Kombó-csík:** cast után az action baron fogyó ▰▰▰▱▱ csík mutatja a kombó-ablakot; új
        cast megszakítja; lejáratkor csendben eltűnik (nem ír „lejárt"-at).
- [ ] **QoL-kör 3 (A69–A70 + particle-fixek, ÚJ):**
  - [ ] **Egységes menü-hangnyelv:** a /menu hubban almenü-váltás lapozás-hangot, akció
        kattintás-hangot ad; az invsee nézet-váltása is; a hangok a gui.sounds.* kulcsokkal
        felülírhatók (rossz hang-név = csend, nem hiba).
  - [ ] **Quest-toast:** küldetés teljesítésekor a jobb felső sarokban vanília toast ugrik fel
        a quest nevével (a chat-üzenet mellett); az advancement-képernyőn NEM jelenik meg
        maradvány; kikapcsolás: quest-toast.enabled: false. Ha a szerver-implementáció nem
        támogatja, egyszeri konzol-warn + a toast kimarad (a chat-üzenet marad).
  - [ ] **Terep-követő határok:** a /claim show pereme dombon-völgyön át a TALAJT követi
        („földszinten, a blokkok felett"), NEM a játékos derékmagasságában lebeg; a sarok-oszlopok
        a claim TELJES magasságát mutatják; barlangban (felszín alatt) a perem a játékos szintjén
        IS kirajzolódik. Ugyanez a /territory show ringre/körre. ⚠️ Folia: távoli (másik régiós)
        határpontnál nincs hiba (fallback a néző szintjére).
  - [ ] **Claim-fényfal (DisplayFx, ÚJ):** a /claim show a particle-perem MELLÉ élenként egy
        megnyújtott, IZZÓ üvegfalat állít (saját/trusted=zöld, idegen=piros), ami csak NEKED
        látszik (más játékos nem látja), és a `border.show-seconds` letelte után magától eltűnik —
        NEM marad blokk-szemét a világban (kilépés/relog/szerver-restart után sem). Kapcsoló:
        `display-fx.claim-wall.enabled: false`; a fal anyaga/magassága configos. ⚠️ Folia: több
        régiót átfedő nagy claimnél is hibamentesen spawnol.
  - [ ] **Kincs-fényoszlop:** a kincs-láda fölött magas, messziről látható END_ROD-oszlop áll
        (dombok mögül is kivehető), a láda-szintű csillogás mellett.
- [ ] **Particle-polish (ÚJ):**
  - [ ] **Hangulat-effektek:** a szentjánosbogár/köd/lelkek/aurora ~24 mp-ig PULZÁL (2 mp-enként),
        a flavour-höz illő helyen (bogarak bokor-magasságban, köd a talajon, aurora magasan az
        égen) — NEM egyetlen villanás a fej fölött; kilépéskor a lánc leáll.
  - [ ] **Aurora fény-fátyol (DisplayFx, ÚJ):** az északi fénynél a particle mellé **két magasan
        lebegő, áttetsző, lassan oldalra sodródó fény-lap** jelenik meg az égen (teal + lila),
        CSAK neked látszik, és az effekt végén eltűnik — nem marad blokk-szemét (relog/restart után
        sem). Kapcsoló: `display-fx.aurora.enabled: false`; anyag configos.
  - [ ] **FLASH-diéta:** boss-spawn/enrage/SLAM, invázió-bajnok, rituálé és Szent Harag villanása
        egyszeri, nem halmozott — közelről sem vakít el hosszan.
  - [ ] **Konfetti mérséklés:** escort/vad hajsza/crate/kombó ünneplő-partikelei visszafogottak
        (≤16-18 darab), a hatás megmaradt, a "particle-hányás" eltűnt.
- [ ] **Sebzés-számok (ÚJ):** játékos által (kézzel vagy lövedékkel) megütött entitás fölött lebegő
      szám mutatja a bevitt sebzést (~1 mp-ig; játékos-áldozatnál piros, mobnál sárga); gyors
      sorozat-ütésnél nem spammel (250 ms limit/célpont).
  - [ ] **Láthatóság:** alapból (`visibility: attacker-only`) CSAK a sebző látja — egy közelben
        álló harmadik játékosnak NEM jelenik meg; `spells.damage-indicators.visibility: everyone`
        után mindenki látja; **lövedékes** (íj) találatnál is a lövő kapja meg a feliratot.
  - [ ] **Teljes kikapcsolás:** `spells.damage-indicators.enabled: false` → semmilyen felirat.
- [ ] **Halál-összegző (ÚJ):** halálkor a chatben az utolsó 10 mp sebzései (max 5 sor: „-2.5❤ Zombi
      (3.2 mp-e)", lövedéknél a lövő zárójelben) + összesített sebzés. Kikapcsolás:
      `spells.death-recap.enabled: false`. Ütés NEM számítódik duplán (entitás- vs. környezeti sebzés).

### 4.15 Druida formák + parkour ✅
- [ ] Druida forma-spellek: LibsDisguises-szel vizuális átalakulás; nélküle stat-váltás (mindkettő teszt).
- [ ] `/parkour start <id>` futás; admin: `/parkour setstart|setfinish|remove`.

### 4.19 Teljes-review javítások (ÚJ) + /iceitem
- [ ] **Kapu-bypass zárva (KRITIKUS):** enderpearl/chorus/portál/plugin-teleporttal SEM lehet
      kulcs nélkül DUNGEON-zónába jutni, körözöttként Caldesterába lépni, vagy a Kárhozat-zóna
      belépési grace-ét kihagyni (mindhárom listener PlayerTeleportEvent-et is figyel).
      Csónakban/csillében ülve átkelve a kapu kiszállít és visszatesz a határ elé.
- [ ] **Fegyvertilalom-rések:** fővárosban bejelentkezve fegyverrel a kézben, ill. ládából
      shift-kattal az aktív slotba vett fegyvernél is elrakat az őrség (join + láda-zárás check).
- [ ] **Invázió Folia-biztos:** a horda-gyűrű tagjai régióhatár közelében is hibátlanul
      spawnolnak (pontonkénti régió-hop); `/events` státusz nem mutat "tomboló inváziót"
      azután, hogy minden mob elhullott (halál-esemény takarít); dupla `/events invasion`
      hívás nem indít torlódó hullámokat (15 mp indítási türelem).
- [ ] **Rontás-zóna:** a mob-utánpótlás sem spawnol claim/város/WG-régió belsejébe (a
      spawn-rules mátrix a terjedésre is érvényes); `corruption.enabled: false` reload után az
      aktív góc magja eltűnik, fajzatai despawnolnak; tömeges korrupt-farmolás nem okoz
      tick-lassulást (a purge-kill számláló mentése a globális tickre gyűjtve).
- [ ] **Config-írás verseny:** két admin egyszerre kattint a config-menüben / ad ki
      `/icesmp config set`-et → mindkét módosítás megmarad (szerializált override-út).
- [ ] **Frakció-étel váltásnál:** frakcióváltás után NEM jön azonnali honvágy-debuff — új
      türelmi idő indul (az időbélyeg frakcióhoz kötött).
- [ ] **Adó:** a levonás friss egyenleggel számol (a beszedés pillanatában kapott pénz is
      adózik); az adócsalás-strike a küszöbnél plafonoz; a nyilvántartásból törölt játékosok
      hátralék-sorai kitakarodnak a treasury.yml-ből.
- [ ] **Vérszomj sorrend:** a lifesteal a VÉGSŐ sebzés után számol (Jégvértes áldozaton
      kevesebbet gyógyít, mint páncélozatlanon).
- [ ] **Crafted-by stackelés:** két külön craftban készült, lore-os stackelhető étel
      összestackel (crafted_at időbélyeg csak nem-stackelhető gearre kerül).
- [ ] **B33 Végítélet-hét (ÚJ — Tier A):** állítsd a szezont úgy, hogy 7 napon belül járjon le
      (pl. `/icesmp config set world-events.season.length-days <kicsi>` — a season.yml `start`
      bélyegéhez képest számol) → napváltáskor lore-hangú broadcast (nap + pont-szorzó);
      a vérhold/világboss/invázió esély-kulcsa naponta nő (`chance-mult-per-day`), az invázió
      mob-szintje bónuszt kap; liga-pont jóváírás emelt (utolsó nap ×2 — `/events season`).
      **Utolsó nap:** EGYSZERI Szezonboss („A Lapforduló Őre") a NEUTRAL főváros pereme mellett
      (wall-offset; guard-bypass szándékos), emelt élettel; halálakor a `season-finale.boss.loot`
      tábla gurul (unique-sorok is: `unique:<id>[:db]` — LootTable-bővítés) + extra liga-pont +
      saját broadcast. Restart után NEM duplázódik (season-finale.yml jelző); főváros-zóna
      nélkül sima világboss-útra esik vissza. Kulcsok: `world-events.season-finale.*`.
- [ ] **D17 Korszakváltás-narratíva (ÚJ — Tier A):** szezonzáráskor a bajnok-broadcast után
      title („Lapforduló") + 4-6 késleltetett, krónikás-hangú chat-sor (nyitány-variáns, az
      előző szezon top-1 hősei szint/vagyon/raid szerint, bajnok-sor VAGY „üres korona",
      zárás-variáns; `line-delay-seconds` ütem). Kikapcsolás:
      `world-events.season.transition-story.enabled: false`. Variánsok messages-kulcsokból
      (`season-story-*`) felülírhatók.
- [ ] **D19 A Rejtélyes Idegen (ÚJ — Tier A):** `/events stranger` → néma, sérthetetlen,
      csuklyás alak („Az Idegen") spawnol 16-40 blokkra; a 24 blokkon belül állók egy talányos
      sort kapnak; jobb-katt NEM nyit kereskedést (elnyelve); 45 mp után lélek-köddel eltűnik.
      Természetes felbukkanás ritka (`stranger-npc.chance-percent` 6%/90 perc); spawn-rules
      mátrix-sor: `stranger`. NINCS semmilyen jutalom — ez szándékos.
      **Egyszerre-egy teszt:** amíg áll egy Idegen, az újabb `/events stranger` **false**-szal
      elhasal (nem lesz két alak), és a 45 mp-es köddé válás után megint indítható.
- [ ] **D9 Énekmondó (ÚJ — Tier A):** nevezz el egy FancyNpcs-NPC-t `enekmondo`-nak (vagy írd
      át a `bard.npc-name`-et) → jobb-kattra a HETI ballada (top-1 szint/vagyon/raid sablon-
      variánsokkal; egész héten ugyanaz a dal, hétfőnként fordul). FancyNpcs nélkül nem elérhető.
- [ ] **D15 Tábortűz-mesélés (ÚJ — Tier A):** tábortűznél SNEAK+jobb-katt → „Leülsz a tűzhöz…"
      action bar; 6 mp helyben maradás után sztori-sor + 8 XP + lélekláng-particle (a közelben
      ülők „X mesél a tűznél…" action bart látnak); elsétálva megszakad, nincs jutalom;
      cooldown 60 perc (PDC `cd_campfire_story` — csak SIKERES mesélés indítja). Kulcsok:
      `campfire-story.*` (general.yml), saját sztori-lista a `stories` kulccsal.
      **Sztori-átadás (bővítve):** a készlet **62 sor**, és végigmeséli a kódexet — Teremtés
      (Asterlayna, Aetrinita, a négy gyermek), Hasadás (Hu. 1.), a három birodalom alapítása
      (14 / 117 / 547), a Hetedik Vérháború és a 698-as Káoszkor, a Felsők megérkezése (978).
      Cél: a játékosnak NE kelljen kódexet olvasnia. Ülj le sokszor (cooldownt vedd 0-ra
      teszthez) és ellenőrizd, hogy tényleg váltakoznak, nem 5-6 sor forog.
      **Frakciós nézőpont (ÚJ):** a közös 62 sor MELLETT minden frakciónak van saját, 22 soros
      készlete — UGYANAZOK az események a saját népük hangján és elfogultságával, frakció-színnel
      (Láng=piros, Fagy=aqua, Menedék=arany, Kitaszítottak=lila). A
      `campfire-story.faction-chance-percent` (alap: 50) dönti el, milyen eséllyel jön a frakciós
      változat. Teszt: ülj le sokszor UGYANAZZAL a játékossal, majd frakciót váltva — a mesék
      hangja és színe váltson; 0-ra állítva CSAK a közös készlet jöjjön. Minden sor külön
      átírható a `campfire-story-<frakció>-<n>` kulccsal.
- [ ] **Sztori-csatornák összesítve (ÚJ):** a világ történetét négy ingame felület tanítja,
      összesen **~255 sorban** — ellenőrizd, hogy mind él:
      - **tábortűz** (62 közös sor + 4×22 frakciós = 150, ismételhető, ingyen — a fő csatorna),
      - **a Rejtélyes Idegen** (45 talányos sor, `/events stranger`),
      - **a bárd heti krónika-versszaka** (20 fejezet: MINDEN héten más szelet az idővonalból,
        a hősök dicsérete ELŐTT hangzik el — `/events`-független, az `enekmondo` NPC-nél),
      - **a korona átka** (40 suttogás, szintenként más — csak királynak).
- [ ] **Haladás-fül (ÚJ, jarból szállított datapack):** a szerver indulásakor a logban jelenjen
      meg, hogy a fa a **jar datapackjéből** él (`IceSMP advancement-fa: 22/22 bejegyzés a jar
      datapackjéből él`). Ha WARNING jön „DEPRECATED tartalék úton" szöveggel, a
      datapack-felderítés hibázott — a fa akkor is működik, de nézd meg a bootstrap-logot.
      Ellenőrizd `/datapack list`-tel, hogy az `icesmp` pack **engedélyezve** van. A **Haladás**
      képernyőn (L) legyen **IceSMP** fül `beacon` ikonnal. `advancements.enabled: false`
      (general.yml) + restart → a grantek no-opok.
- [ ] **A fa-bejegyzések NEM toastolnak és NEM írnak chatbe** (`show_toast:false`,
      `announce_to_chat:false` mindegyik JSON-ban): a visszajelzést az adott rendszer saját
      chat-üzenete adja, az ünneplő toast pedig a külön, három fix toast-bejegyzés (lásd lentebb).
      Ellenőrizd, hogy egy fa-grant (pl. kaszt-választás) NEM ugrik fel a sarokban.
- [ ] **Hovatartozás vs. Kitaszítva (ÚJ, a világ tényleges szerkezete):** a világban NÉGY
      hatalom van, de a játékos csak HÁROM közül választhat — a Menedékben KEZD (a NEUTRAL az
      alapértelmezett frakció), onnan a Lánghoz vagy a Fagyhoz állhat. A Kitaszítottak közé nem
      lépni lehet, hanem KERÜLNI. Ezt a fa is így mondja el: `faction_join` („Kikötöttél az
      egyik hatalom mellett") → alatta a REJTETT `exiled` („Bűnök vezettek a Néma Királynő népe
      közé") → alatta a `redeemed` („Megtörted az örök paktumot"). Ellenőrizd, hogy a
      `faction_join` szövege SEHOL nem állítja, hogy négyből választhatsz.
- [ ] **A Kitaszítottakhoz HÁROM út vezet, és MIND a `exiled` bejegyzést adja** (e nélkül a
      `redeemed` szülő nélküli csomópontra érkezne):
      1. **bűn-küszöb:** vigyél fel egy játékost 4 bűnre (`/sinner <j> add`) → automatikus
         száműzetés + **Kitaszítva**; majd a vezeklés-lánccal → **Vezeklés**.
      2. **Suttogó-lelepleződés (K9):** a gyanú a küszöbig → `expose()` a bűn-pipeline-on át
         száműz → **Kitaszítva** (a Suttogó-út a bűn-küszöbön keresztül fut, nem külön ágon).
      3. **önkéntes paktum:** bűnösként `/faction join dark` (kétlépcsős megerősítés) →
         **Kitaszítva** akkor is, ha a bűn-küszöböt még nem érte el.
- [ ] **„Akit a csend befogadott" (ÚJ, REJTETT — a Suttogó-státusz titka):** a Sötét Rítus
      sikere (éjjel + sculkon + magányosan + vér-áldozat) adja a `whisperer` bejegyzést.
      **Titok-ellenőrzés:** a granthez NEM tartozik chat-broadcast és NEM ugrik fel toast,
      tehát a közelben lévő játékosok semmit nem látnak belőle; a bejegyzés a saját
      haladás-fülön is rejtett keretben van. Lelepleződés után a státusz elveszik, de a
      megszerzett bejegyzés (jogosan) megmarad — az emlék nem törlődik.
- [ ] **A 22 csomópont grant-pontja:** mindegyik a saját eseményén jár, egyik sem holt.
      Sorra: kaszt-választás (`root`+Elhivatás) • spec-választás • max kaszt-szint •
      capstone-talent vásárlása • frakció-belépés • Suttogó-rítus (rejtett) • Kitaszítottak
      (három út, lásd feljebb) • királlyá választás • a korona-átok max szintje • megnyert raid
      (CSAK a győztes oldal jelentkezett harcosainak!) • vezeklés (DARK-paktum megtörése) •
      szakma-választás • szakma max szint • mestermű-craft (Legendás/Ereklye roll) • rontás-góc
      megtörése • rejtett hely • világboss • első relikvia • rituálé • pet max szint •
      parkour-futam. Gépi ellenőrzés: `python3 scripts/check_consistency.py` FAIL-el holt
      bejegyzésre, árva JSON-ra ÉS tartalom-driftre (a JSON-ok generátora:
      `python3 scripts/gen_advancements.py`).
- [ ] **Toast (ÚJ, fix bejegyzések):** quest-teljesítéskor felugrik a „Küldetés teljesítve"
      toast, és **ismételten is** (nem csak egyszer — a bejegyzés visszavonódik). A quest NEVE
      a chat-üzenetben van, nem a toaston. A haladás-fülön a toast-bejegyzések NEM látszanak
      (rejtettek). **Szaporodás-ellenőrzés:** teljesíts 20-30 questet, majd nézd meg a
      `<world>/datapacks/bukkit/` fájlszámát — MOST már nem nőhet, mert nincs futásidejű
      advancement-betöltés (a régi megoldás véletlen kulcsokat használt).
- [ ] **A38 Spawn-élmény (ÚJ — Tier A):** állíts be `world-events.intro.first-join-spawn`-t
      ("world,x,y,z[,yaw,pitch]") → az ELSŐ belépő oda teleportál, és csak utána indul az
      intro; visszatérő belépésnél rövid, halk üdvözlő title + csengő hang
      (`intro.join-welcome.*` — nem torlódik az intróval, mert első belépéskor az intro fut).
- [ ] **B19 Évszakos modifikátorok (ÚJ — Tier A):** a VALÓS évszak szorzói
      (`world-events.season-modifiers.<tel|tavasz|nyar|osz>.<esemény>`) a vérhold/világboss/
      invázió/hajsza/bőség/gyűjtögető-láz sorsolási esélyén (télen vérhold ×1.4, nyáron
      bőség ×1.4 stb.); `enabled: false` mindet kikapcsolja; a B33-finálé szorzójával összeszorzódik.
- [ ] **D3 Szezon-emlékmű (ÚJ — Tier A):** állíts be `season-monument.location`-t
      ("world,x,y,z") → szezonzáráskor a blokk a bajnok banner-színére vált, fölötte hologram
      („A Korszakok Könyve" + sorszám + bajnok + top-3 hős); a lista korszakonként bővül
      (max-lines), restart-álló (monument.yml + perzisztens TextDisplay). Bajnok nélküli
      szezon nem kerül kőbe.
- [ ] **Eldobható minion halála nem állítja le az élő társat (ÚJ):** a permanens társ és a rövid
      életű spell-minion ugyanazt a tulajdonos-jelölést viseli, és a halál-kezelő ELŐBB bontotta a
      gazda-kulcsos állapotot (aktív társ, harci cél), csak utána ellenőrizte az azonosságot.
      **Teszt:** idézd meg a permanens társat, majd idézz egy eldobható minion-hordát és hagyd
      meghalni → a társ **maradjon aktív és vezérelhető** (harci cél, állásmód); ezután a TÁRS
      haláljakor jöjjön a „társad elesett" üzenet és a respawn-cooldown.
- [ ] **Pet-rítus nem fut kétszer (ÚJ):** egy fizikai jobb kattintás main- ÉS off-hand eventet is
      ad, a rítus viszont mindkettőben a main-handet olvasta → 2+ darabos stacknél KÉT ritka
      kelléket fogyasztott. **Teszt:** tarts 2 Nyughatatlan Szívet (vagy Démon-pecsétet), futtasd a
      rítust → pontosan EGY fogyjon el.
- [ ] **Emlék-XP nem viszi el a szilánkot kaszt nélkül (ÚJ):** a `/emlek xp` előbb elvette a
      szilánkokat, és az XP-jóváírás boolean-je elveszett — kaszt nélkül a játékos elveszítette a
      ritka szilánkokat, mégis sikert olvasott. **Teszt:** kaszt NÉLKÜL `/emlek xp` → érthető
      elutasítás, és a szilánkok **maradjanak meg**; kaszttal a beváltás menjen és adjon XP-t.
- [ ] **Elérés-azonosító kanonizálás (ÚJ):** a megszerzett elérések listája PDC-ből visszaolvasva
      kisbetűsít, a tárolás viszont a config eredeti casingjét használta — egy `RichOne` azonosítójú
      elérés így MINDEN periodikus tickben újra kifizette a jutalmát.
      **Teszt:** vegyél fel a `quests.yml` `achievements.definitions` alá egy nagybetűs azonosítót
      (pl. `TesztElereny`) → indításnál a konzol figyelmeztessen, és a sor maradjon KI (különben
      ismételt jutalmat adna); a 21 shippelt elérés (mind kanonikus) változatlanul működjön, és egy
      már megszerzett elérés NE fizessen újra a stats-tick körökben.
- [ ] **Főzet-XP csak VALÓDI kivételre (ÚJ):** a főzőállvány eredmény-slotjaiban a puszta
      „van benne főzet" korábban XP-t adott, ezért a bent maradó főzetre ismételt kattintás
      korlátlan Alkimista-XP-t és heti szakma-cél haladást termelt. Most csak a tényleges kivétel
      számít. **Teszt:** kattints a kész főzetre inkompatibilis kurzorral (no-op) → **ne** kapj
      XP-t; kattints rá üres kurzorral (kivétel) → kapj; a most üres slotra kattintva ne kapj újra;
      shift-kattintás és hotbar-csere is fizessen (azok is kivételek).
- [ ] **Heti szakma-cél csak a SAJÁT szakmádtól tölthető (ÚJ):** korábban egy nem-bányász
      ércbontása is töltötte a Bányász-céh heti közös célját, mert a listener eldobta a
      XP-jóváírás eredményét. **Teszt:** olyan játékossal, akinek NINCS bányász szakmája, törj
      ércet → se személyes XP, se heti cél-haladás; bányászként ugyanez mindkettőt adja.
- [ ] **Quest-lánc ciklus nem futhat végtelen jutalom-hurokba (ÚJ):** egy önmagára (vagy körben)
      mutató, repeatable, nulla cooldownos, már teljesített `REACH_LEVEL` quest az
      `accept → complete → reward → advanceChain → accept` láncot végtelenszer futtatta volna.
      Két védelem: a `check_consistency.py` FAIL-el a `next`-gráf bármely ciklusára (push előtt
      kiderül), futásidőben pedig 16-os mélység-korlát áll, SEVERE naplóval.
      **Teszt:** `/quest admin set <id> next <ugyanaz az id>` → a checker akadjon meg; ha mégis
      élesbe kerül, a konzolban „Quest-lánc mélység-korlát" sor legyen, és a szerver ne álljon meg.
- [ ] **Céhtagság nem éli túl a frakcióváltást (ÚJ):** a céh egy frakción belüli szervezet, mégis
      az ellenséges oldalra váltó játékos tovább vezethette a régi frakció céhét, az ott teljesített
      questjei a régi céh XP-jét növelték, és a kasszát a RÉGI frakció valutájában kezelhette.
      Az egyeztetés KÖZPONTILAG a `setFaction`-ban fut, tehát minden út érvényes.
      **Teszt mind a négy úton** (`/faction join`, `/faction leave`, admin `/faction set`,
      bűn-száműzetés, vezeklés):
      1. tagként válts frakciót → lépj ki a céhből, és kapj róla üzenetet;
      2. **vezetőként** válts → az irányítás a legrégebbi másik tagra szálljon;
      3. **egyedüli vezetőként** válts → a céh szűnjön meg (ne maradjon üres, gazdátlan céh);
      4. váltás után a `/ceh info` ne mutasson tagságot, a quest-teljesítés ne töltse a régi céhet,
         és a `/ceh befizet` ne menjen.
- [ ] **Gyűjtés nem játszható vissza (ÚJ — kiadásblokkoló volt):** a `COLLECT_ITEMS` progressz a
      felvett stack méretét könyvelte, és mivel a DOBÁS mindig új item-entitást hoz létre (új UUID),
      ugyanazzal a fizikai stackkel korlátlanul növelhető volt a gyűjtő-quest ÉS az ismételhető
      közösségi cél — utóbbi minden körben kifizette a teljes treasury-, liga- és buff-jutalmat.
      **Teszt:**
      1. vegyél fel egy vasrudat gyűjtő questet (vagy legyen aktív ilyen közösségi cél);
      2. tarts magadnál egy stacket, dobd le, vedd fel — **a haladás NE nőjön**, akárhányszor
         ismételed;
      3. **pingpong-teszt:** dobd le, és MÁSIK játékos vegye fel → nála se nőjön;
      4. **halál-teszt:** halj meg (ne legyen keep-inventory), majd vedd fel a saját dropodat →
         ne nőjön (a halál-dropokat egy tickkel később, a halál helye körül jelöljük meg);
      5. **legitim út:** bányássz/ölj újat, és úgy vedd fel → **nőjön** a haladás;
      6. **tele hátizsák:** hagyj egy szabad helyet, és vegyél fel egy 64-es stacket → csak a
         TÉNYLEGESEN átkerült darab számítson (nem a teljes 64);
      7. `keep-inventory` bekapcsolva halálnál ne jelöljön semmit (nincs is drop).
- [ ] **Sérült állapotfájl NEM írható felül (ÚJ — kiadásblokkoló volt):** a
      `YamlConfiguration.loadConfiguration(File)` FAIL-OPEN — hibás YAML-nál nem dob kivételt,
      csak naplóz és ÜRES konfigurációt ad. Így a manager üres állapottal indult, a következő
      autosave pedig szabályos, de üres fájlt írt a kézzel még javítható adat FÖLÉ.
      Mind a 33 állapotfájl-betöltés a védett `YamlStore.loadTracked` úton megy.
      **Teszt (pl. `currency.yml`, `market.yml`, `claims.yml`):**
      1. állítsd le a szervert, tegyél a fájlba szintaktikailag hibás YAML-t (pl. tabulátor);
      2. indíts → a konzolban `SÉRÜLT állapotfájl: <név>` + `Karantén-másolat: <név>.corrupt-<ms>`
         + `MENTÉSE LETILTVA` SEVERE sorok;
      3. várd meg az autosave-et, majd állítsd le szabályosan → a **sérült fájl VÁLTOZATLAN**
         (nem íródott felül), és ott van mellette a `.corrupt-` másolat;
      4. javítsd a fájlt, indíts újra → az adat betöltődik, a mentés újra működik (a jelölés
         sikeres parse-ra feloldódik).
      **FIGYELEM:** a `config/` és `messages/` fájlok SZÁNDÉKOSAN maradtak fail-openen — egy
      hibás config ne akadályozza meg a szerver indulását, és azokat sosem írjuk vissza.
- [ ] **Rituálé-áldozat NEM kerülhető meg (ÚJ — kiadásblokkoló volt):** az áldozat-ellenőrzés és
      a fogyasztás MOST ugyanazt a predikátumot használja (`PlainIngredients`) — korábban az
      ellenőrzés típus szerint számolt, a levonás viszont meta-egyezést kért, ezért egy
      **átnevezett** (üllőn elnevezett) áldozat fedezte a követelményt, de nem fogyott el:
      a relikvia-idézés, tisztítás, paktum, buff és hazateleport INGYEN, korlátlanul ismételhető volt.
      **Teszt minden rituálé-típusra** (`relic`, `cleanse`, `buff`, `home`, `uncurse`, `pakt`):
      1. nevezd át üllőn a konfigurált áldozatot → a rituálé **NE induljon el**
         („Hiányoznak az áldozati tárgyak");
      2. sima, névtelen áldozattal induljon el, és az áldozat **fogyjon is el**;
      3. **kopott/sérült** áldozat (ha szerszám) számítson ÉS fogyjon;
      4. unique-material NE fedezze a sima anyag-követelményt;
      5. a konzolban NE legyen „Rituálé-áldozat nem fogyott el" SEVERE sor — az invariáns-sértést jelez.
      Ugyanez a szerződés védi a szakma-craftot; gépi őr: a `check_consistency.py` FAIL-el minden
      `removeItem(new ItemStack(...))` visszaesésre.
- [ ] **Viselt relikvia NEM tűnhet el relognál (ÚJ — kiadásblokkoló volt):** a belépési
      relikvia-sweep MOST egyetlen bejárás. A `PlayerInventory#getContents()` a teljes slotteret
      adja (0-35 tár, 36-39 páncél, 40 offhand), ezért a korábbi külön páncél-/offhand-kör
      ugyanazt a tárgyat látta másodszor, és a közös duplikátum-szűrő a VISELT relikviát törölte.
      **Teszt (mind a 6 relikviára, de főleg a 4 elytrára):**
      1. vedd FEL a relikvia-elytrát (mellvért-slot), lépj ki, lépj vissza → **maradjon meg**;
      2. ugyanez offhandbe fogott relikviával;
      3. ugyanez páncél-slotos relikviával;
      4. **valódi duplikátum**-teszt: adj magadnak KÉT példányt ugyanabból a relikviából (egyet
         viselve, egyet a tárban) → relog után pontosan EGY maradjon;
      5. **lejárat**: állítsd a relikvia inaktivitás-elenyészését azonnalira → a VISELT példány is
         évüljön el (a sweep a páncél-slotot is eléri, nem csak a tárat);
      6. a kozmetikai frissítés (név/lore újraírás) a viselt darabon is fusson le.
- [ ] **Visszavont akció NEM jutalmaz (ÚJ — kiadásblokkoló volt):** a progressz-listenerek
      (quest, napi, szakma-XP, szerver-kihívás, gyűjtő-buff) `MONITOR` prioritáson könyvelnek, a
      védelem (claim/territórium/unique) `HIGH`/`HIGHEST`-en cancel-el. Korábban a `NORMAL`
      prioritás miatt a progressz a visszavonás ELŐTT megtörtént.
      **Teszt:** állj MÁS játékos claimjébe (nincs törési jogod), és törj ismételten ércet/termést:
      1. a blokk NE törjön el (a védelem cancel-el);
      2. szakma-XP, quest-, napi-, közösségi és szerver-kihívás haladás **NE** nőjön;
      3. aktív Bányász-láz / Termés-óra alatt **NE** essen bónusz-drop a védett blokkból
         (ez korábban valódi tárgy-duplikációt adott);
      4. ugyanez tiltott blokk-lerakással és védett unique fogyóeszközzel.
      **Kontroll:** a SAJÁT claimedben ugyanezek az akciók továbbra is adjanak XP-t, haladást és
      bónusz-dropot — a MONITOR nem szüntetheti meg a legitim jutalmat.
      Gépi őr: `scripts/check_consistency.py` FAIL-el, ha egy progressz-handler visszakerül
      alacsonyabb prioritásra.
- [ ] **B54 Átkozott felszerelés (ÚJ — Tier A):** világboss/event-boss loot ~8%-a Átkozott
      (sötétvörös lore-sor): viselve darabonként +10% kimenő sebzés (cap +40%), de a
      páncél-slotból NEM vehető ki („Az átok nem ereszt…"). Felvételkor az első kísérlet
      figyelmeztet, csak a `confirm-seconds`-on (alap: 5) belüli második erősít meg. Levétel:
      építs Átok-törés oltárt (CRYING_OBSIDIAN mag + 3×3 obszidián talp, áldozat: 8 ametiszt +
      1 ghast-könny) → SHIFT+jobb-katt → minden viselt/kézben tartott átok megtörik, a tárgy megmarad.
      Kulcsok: `item-rarity.cursed.*`, rituálé: `rituals.atok_tores`.
- [ ] **B54 — a felvétel MINDHÁROM útja külön tesztelendő** (két megerősítés-kapu együtt
      dolgozik: az `InventoryClickEvent` és az `EntityEquipmentChangedEvent`, és a kettő
      korábban kioltotta egymást — ez a visszaesés-teszt):
      1. **jobb-katt** kézből (a legtermészetesebb út) → 1. kísérlet: a darab lekerül és
         visszakerül a hátizsákba + figyelmeztetés; 2. kísérlet 5 mp-en belül → **FELMARAD**.
      2. **shift-katt** a hátizsákban → 1. kísérlet: cancel + figyelmeztetés; 2. kísérlet
         5 mp-en belül → **FELMARAD** (nem szabad, hogy azonnal visszakerüljön a hátizsákba!).
      3. **kurzorral a páncél-slotba tétel** → ugyanaz, mint a 2.
      **A hurok-tünet, amit keresünk:** ha a darab a megerősítés UTÁN is visszakerül a
      hátizsákba és újra jön a figyelmeztetés, akkor a két kapu ismét ugyanazt a jelzést
      fogyasztja — a felvétel ilyenkor SEMELYIK úton nem sikerül.
      **Tele hátizsákkal:** a visszavett darab a földre esik — ellenőrizd, hogy ez CSAK a
      megerősítés előtti (elutasított) kísérletnél történhet meg, mert viselt átkozott darab
      földre kerülése a levétel-zárat is megtörné.
      **Nem átkozott páncél** fel-le vétele semmilyen üzenetet ne adjon, és NE fogyassza el egy
      folyamatban lévő átkozott-megerősítést sem.
- [ ] **F13/F14/F15 Gazdasági események (ÚJ — Tier A):** a kereslet-sokk mellett most
      **pánik** is jöhet (~35% eséllyel a sorsolt esemény lefelé üt: x0.6-0.8, piros
      broadcast, a lecsengése „A pánik elült…"); **konjunktúra**: ritkán fél órára egy
      valutában 10%→5% a piaci eladási díj (broadcast nyit/zár; a díj-kedvezmény azonnal él
      az eladás-jóváírásnál); **finálé-tőzsdeláz**: a Végítélet-hét alatt a sokkok esélye ×3,
      kilengése ×1.5, hossza ÷4. Kulcsok: `currency.economy-event.panic-*` / `finale-*`,
      `currency.market-boom.*`. A szezonzárás továbbra sem wipe — csak a liga-pontok nullázódnak.
- [ ] **I7 Évszakos termés (ÚJ — Tier A):** Bőség-idő alatt (`/events abundance` kényszerítheti)
      a Gyógynövényész betakarítás-XP-je ×1.5 (`professions.seasonal.abundance-multiplier`);
      CSAK virág/érett termény/harvest — érc/rönk-törés XP-je változatlan.
- [ ] **I22 Loot-only receptek (ÚJ — Tier A):** a három csúcs-netherit tervrajz (Mélybányász
      Netherit Csákány, Erdőirtó Netherit Fejsze, Sárkányvért) tervrajza CSAK világboss/
      event-boss lootból eshet (sima mob-drop poolból kikerült); a recept-könyvben lila
      „Csak legendás ellenfelektől szerezhető tervrajz" zárolt sor. Új recept-mező:
      `loot-only: true` (profession-recipes.yml).
- [ ] **D8 Titkos helyek (ÚJ — Tier A):** vegyél fel egy spotot a configba
      (`hidden-spots.spots.<id>`: name/location/radius/xp/rewards) → aki elsőként odaér,
      jutalmat kap + „🧭 X ELSŐKÉNT fedezte fel…" broadcast; a többiek egyszeri, fél-értékű
      jutalmat (repeat-reward-ratio) és a felfedező nevét látják; `first-finder-only: true`
      esetén csak az első kap bármit. A jelölés PDC-s (relog után sem duplázható); a check
      30 mp-enként, a játékos saját szálán fut. Kulcsok: `hidden-spots.*` (world.yml).
- [ ] **Tartalombővítés 2. hullám (ÚJ):**
  - [ ] **6 új unique anyag** (`/iceitem unique …`): jegviragpor, parazsmag, viharkvarc,
        melysegi_borostyan (régészeti leletként is!), sarkanycsont_szilank (boss-only drop),
        fonixpihe (mob-drop). Craft-védelem (nem rakható le/ehető/olvasztható) él rájuk.
  - [ ] **16 új recept**, köztük az 5 új signature fegyver: Glatziendorfi Jégtörő (lassított
        célon +25%), V. Miinus Haragja (LOOT-ONLY tervrajz; <35% HP-n +20%), Sárkánycsont Íj
        (pierce +2), I. Zhoris Lángnyelve (LOOT-ONLY; gyújtás + égő célon +15%), Napfogyatkozás
        (éjjel gyorsabb + erősebb nyíl) — kulcsok: `signature.jegtoro/miinus/sarkanycsont/
        langnyelv/napfogyatkozas.*`. Plusz: Sárkány-pörkölt (BLUE étel: hal-kötelezettség +
        rövid Erő), Sárkánycsont Pajzs, Viharüveg Lámpás, Vándor Úti Kenyere, Bokic-parti
        Gyógytea, Fagypáncél/Főnixtoll Tekercse (üllőre vihető iskola-counter könyvek).
  - [ ] **Új loot-sorok:** Fekete Csont (undead named), A Néma Királynő Suttogása (undead
        BOSS-only named, nagyon ritka), Főnixpihe (mob), Sárkánycsont-szilánk (boss).
  - [ ] **+3 /lore téma:** `melyseg` (Mélység Népe), `korszakok` (a Könyv), `bokic` (a folyó).
  - [ ] **Storytelling-poolok nőttek:** tábortűz 6→15, Idegen 6→12, bárd 15→25, krónika 8→12,
        szezon-átvezető 6→10 variáns; +2 MOTD-variáns (konyv, suttogas).
  - [ ] **+10 quest** (jegvirag_szuret, parazs_gyujtes, borostyan_kutatas, fonixpihe_vadaszat,
        sarkanycsont_kutato, porkolt_lakoma, uti_kenyer, viharkvarc_fejto, korrupt_irtas,
        karhozat_zarandoklat — az utóbbi territory-id-ját igazítsd a szerveredhez!).
- [ ] **Tartalombővítés 3. hullám (ÚJ):**
  - [ ] **34 unique anyag** (17 új): 10 szakma-gyártott (Holdezüst Huzal, Csontenyv,
        Sarkfény-cseppkő, Szavannafű-kötél, Obszidián-szilánk, Árnygomba, Lélekhamu,
        Aranyfüst-lemez, Gyöngyház-pikkely, Vándorfűszer), 3 VENDOR-ONLY (Számvevő-pecsétviasz,
        Finomított Lámpaolaj, Kovács-folyósítószer — CSAK az Általános Boltban kaphatók, a
        bolt-item `unique:` mezője a valódi PDC-s anyagot adja), 4 mob/boss-drop (Dermedt
        Könnycsepp undead, A Kapu Parazsa boss, Néma Kristály mob, Az Első Csend Szilánkja —
        a legritkább, weight 2 boss-only).
  - [ ] **176 recept** (+21): 10 anyag-recept, ünnepi étel MIND A 4 frakciónak (RED
        Vadlakoma: Gyorsaság+Tűzáll; NEUTRAL Vándorünnep Lepénye: Szerencse+Gyorsaság;
        DARK Hamvak Lakomája: Felszívódás+Éjjellátás; a BLUE pörkölt mellé), 3 új LEGENDÁS
        loot-only darab (A Mélység Népe Koronája, Viharjáró Csizma, Eleftheria Fátyla — mind
        Első Csend Szilánkját + vendor-anyagot + netheritet kér), és a meglévő legendások
        MEGDRÁGÍTVA (Miinus/Lángnyelv: +Szilánk/Parázs + folyósítószer; Jégtörő/Napfogyatkozás/
        Lámpás: vendor-anyag igény).
  - [ ] **Storytelling ~kétszerezve:** tábortűz 15→29, Idegen 12→23, bárd 25→40, krónika
        12→19, szezon-átvezető 10→16 variáns.
- [ ] **Tartalombővítés 4. hullám — vendor-gazdaság + jövedelem-csapok (ÚJ):**
  - [ ] **39 új vendor-only szakma-kellék** (szakmánként 5 — össz. 73 unique anyag): a
        **Szakmai Kellékbolt** (`/npc create kellekbolt`) mind a 42 vendor-árut árulja;
        a boltok kínálata TELJESEN configból jön (economy.yml faction-shops — sorok
        törölhetők/árazhatók/másolhatók bármely NPC-boltba). 16 recept mostantól kelléket
        (is) kér (Kősó, Írnok-tinta, Edzőolaj, Sózott csali…).
  - [ ] **Rotálódó karaván:** a karaván áru-poolja 12 tételes (4 régi + 8 RITKA alapanyag:
        Emlékszilánk, Sárkánycsont-szilánk, Főnixpihe, Néma Kristály, Borostyán, Viharkvarc,
        Könnycsepp, Kapu Parazsa — drágán); érkezésenként `stock-size` (5) darab sorsolódik
        ("ma épp ezt hozta"), a látogatás alatt stabil. Kulcsok: `caravan.rotation.*`.
  - [ ] **Felvásárló NPC** (`/npc create felvasarlo`): a kézben tartott nyersanyag jobb-kattra
        eladva a `buyer.prices` fix árain, NAPI kerettel (`daily-cap` 250, PDC-ben követve,
        éjfélkor nullázódik); PDC-s (egyedi) tárgyat nem vesz; a fizetség FIZIKAI veret a
        kézbe, egészre lefelé kerekítve (1 veret alatt nem vesz). Üzenet mutatja a maradék keretet.
  - [ ] **Kopott erszény (fizikai pénz-tárgy, WoW-stílus):** a talált pénz TÁRGYKÉNT
        érkezik — bőr-item, PDC-ben darabszám + VÉLETLEN frakció-valuta; jobb-katt FIZIKAI
        veretekre (token-item) bontja a kézbe (üzenet + hang). Admin-adás:
        `/iceitem erszeny <összeg> [darab] [játékos]`.
  - [ ] **„Számlára csak a bankból” szabály:** MINDEN jutalom-kifizetés (quest, napi/heti
        kihívás, mérföldkő, parkour, ambient-esemény, AFK-jutalom, vérdíj, Felvásárló,
        Bankbetét-jegy) fizikai veretet ad a kézbe — addToBalance jutalom-úton NINCS;
        a számlára kizárólag `/bank deposit` tesz pénzt. A piac/aukció/kincstár bankon
        belüli átvezetés marad.
  - [ ] **Mob pénz-drop:** ellenséges mob játékos-ölésekor ~20% eséllyel Kopott erszény
        esik (1-4 + mob-szintenként +0,5); spawner-mob SOSEM dob (entitás-PDC jelölés,
        restartot is túléli). Kulcsok: `mob-money-drop.*`.
  - [ ] **Horgász-szerencse:** ~4% eséllyel a fogás mellé iszapba veszett Kopott erszény
        akad (5-15 összeggel, véletlen valutával); AFK-jelöltnek nem jár. Kulcsok:
        `fishing-windfall.*`.
- [ ] **D1 — Szezonális ünnepek (ÚJ):**
  - [ ] HolidayService: valós naptári MM-DD ablakok (év-átfordulással), 4 kánon-ünnep
        előre configolva (Rém-éj 10-25→11-02, Érkezés Napja 12-20→12-27, Hasadás Napja,
        Ultimátum Napja); ablak-nyitás/zárás broadcast; `override(kulcs)` API-n át a
        managerek futásidőben kaphatnak ünnep-skint (a config sosem íródik át).
        Kulcsok: world.yml `seasonal-events.*`.
- [ ] **D11 — Járőröző városi őrség (ÚJ, v1):**
  - [ ] Config-útvonalú (waypoint-listás) őr-NPC-k: plugin-spawnolta, sebezhetetlen,
        nevesített villager-őrök; a léptetés MINDIG az őr saját entity-schedulerén fut
        (kis teleportAsync-lépések, több-régiós útvonalon is); éjjel gyorsabb tempó.
  - [ ] Restart-újraspawn, shutdown-takarítás; példa-route kikommentelve. Kulcsok:
        world.yml `city-guards.*` (guards.<id>.name/world/route).
- [ ] **J7 — Rejtvény-küldetések (ÚJ; tulaj-döntés: SOSINCS súgás):**
  - [ ] `riddle: true` quest-mező: a napló/haladás-sor MINDIG "??? — a nyomot a leírás
        rejti"-t mutat — a cél sosem tárul fel, a megfejtés a játékosé/közösségé
        (az időzített súgás-fokozat kivezetve, nincs hint-minutes kulcs).
  - [ ] 26 rejtvény-quest él (rejtveny_* — gyűjtés, vadászat,
        olvasztás, biom, horgászat, NPC-keresés versbe rejtve); admin-szerkesztés:
        `/quest admin set <id> riddle true`.
  - [ ] Ellenőrzés: rejtvény-quest felvéve → a /quest info és a napló SOHA nem írja ki
        a konkrét célt, de a teljesítés a megfejtett cselekvésre magától bekövetkezik.
- [ ] **F11 — Ereklye-börze (ÚJ; tulaj-döntés: valódi relikvia NEM listázható):**
  - [ ] `/market ereklye`: a böngésző a PDC-tages tételekre (unique anyag) szűrve
        nyílik (`@ereklye` belső szűrő a MarketGUI-ban); a kereső-infra változatlan.
  - [ ] VALÓDI relikvia (relic_id PDC) listázása/aukcióba adása TILOS (hibaüzenettel) —
        a relikvia több-lépcsős kihívással szerzett, egyedi-tulajdonú tárgy, a börze a
        szilánkoké. Kapcsoló: economy.yml `market.allow-relic-listing` (default false).
  - [ ] Aukció-indításnál unique-tételre ajánlott minimum-kikiáltás figyelmeztetés
        (NEM tiltás): economy.yml `market.relic-auction.recommended-min-bid`.
- [ ] **G16 — Nagydöntő (Tier B TOP, ÚJ):**
  - [ ] A szezon utolsó 48 órájában (`season-finale.top2-window-hours`) a liga-tábla top2
        frakciójának MINDEN pont-jóváírása ×2 (`top2-point-multiplier`, a B33-szorzó UTÁN);
        az ablak nyíltakor egyszeri „NAGYDÖNTŐ!” broadcast a párosítással; szezonváltáskor
        a flag újraáll. Kulcsok: world.yml `world-events.season-finale.top2-*`.
- [ ] **H14 — Ritka spawn-variánsok (ÚJ):**
  - [ ] Spawn-kor ~1,5% eséllyel „✦ Albínó” (fehér, GLOWING) vagy „☽ Árnyék-” (lila,
        SPEED) variáns — PDC-tag + látható névtábla (MobScalingManager.maybeMakeRareVariant).
  - [ ] Kill-bónuszok: dupla kaszt-XP (ClassXpListener), emelt lélekkő-esély (Soulstone),
        és ÖNÁLLÓ bestiárium-bejegyzés (albino_/arnyek_ prefix). Kulcsok: world.yml
        `rare-variant.*`.
- [ ] **DARK undead-népesség (ÚJ, lore-ambiencia):**
  - [ ] A DARK territóriumokban folyamatos, magas szintű undead-populáció — hatókör
        configból: `scope: capital` (csak a főváros) vagy `all` (minden DARK territórium),
        `territory-id` felülbírálással; alap: 24 fő, 30 mp-enként 4-es rajokban pótolva,
        szint 4-7 (MobScaling.forceLevel); a mobok a napon SEM égnek el
        (EventSpawnGuard.prepare) és nem zombisodnak.
  - [ ] Élettartam-korlát (600 mp, a mob saját schedulerén) + halál-könyvelés — a populáció
        sosem nő a plafon fölé; spawn a helyszín régió-schedulerén. A DARK játékosokat a
        frakció-passzíva védi. MINDEN kulcs élő: world.yml `dark-undead.*`.
        (Osztály-név generikus: DarkUndeadAmbienceManager — a lore-név csak szövegben él.)
- [ ] **Rontás-góc DARK-perem sorsolás (ÚJ — elfogadott irány):**
  - [ ] A természetes góc-nyílás `corruption.dark-bias.chance-percent` (65) eséllyel egy
        véletlen DARK territórium PEREMÉN TÚL történik (a zóna szélétől
        `min-edge-distance`..`max-edge-distance` [24..96] blokkra — a territórium
        BELSEJÉT a spawn-rules amúgy is védi); ilyenkor külön broadcast szól
        („a Kitaszítottak földjének pereméről szivárgott ki”).
  - [ ] Ha nincs DARK territórium / a sorsolás nem talál / admin `forceSpawn`: marad a
        régi horgony-játékos út a régi üzenettel. Élő kulcsok: world.yml
        `corruption.dark-bias.*`. Ellenőrzés: definiálj DARK territóriumot, állítsd a
        chance-t 100-ra, várd ki (vagy admin-indítsd) a gócot → a mag a zóna szélén túl,
        de annak közelében nyílik.
- [ ] **Aszimmetrikus szezon-liga (ÚJ — tulaj-döntés a 2+1+1 felállás miatt):**
  - [ ] Forrás-súlymátrix: minden liga-pont-jóváírás forrás-címkével megy
        (`SeasonManager.addPoints(faction, amount, source)`), és a
        `world-events.season.source-weights.<forrás>.<frakció>` súly skálázza a NYERS
        pontot (a B33/G16 idő-szorzók UTÁNA); hiányzó kulcs = 1.0, 0 = nem ér pontot.
  - [ ] Meglévő források címkézve: raid-győzelem → `raid` (NEUTRAL 0.5), világboss +
        szezonzáró boss → `world-boss` (mind 1.0).
  - [ ] ÚJ pontforrások: közösségi cél teljesítése → `community`
        (quests.yml `community-goals.season-points` 8; NEUTRAL 1.5, DARK 0.75;
        szerver-célnál minden frakció kap); rontás-tisztítás → `cleanse`
        (world.yml `corruption.season-points` 6; NEUTRAL 1.5, DARK 0.5);
        becsület-párbaj győzelem → `duel` (factions.yml `honor-duel.season-points` 2;
        DARK 1.5); sikeres kém-küldetés (lebukás nélküli lejárat) → `spy`
        (factions.yml `spy.season-points` 2; DARK 1.5, mások 0.75).
  - [ ] Ellenőrzés: NEUTRAL játékossal rontás-tisztítás → 9 pont (6×1.5); DARK
        párbaj-győzelem → 3 pont (2×1.5); raid-győzelem NEUTRAL-ként → 5 pont (10×0.5);
        súly 0-ra állítva a forrás semmit nem ír jóvá. `/events season` mutatja az állást.
- [ ] **Review-kör 2 (Tier A/B utólagos átvizsgálás) — javítás-ellenőrzések:**
  - [ ] KRITIKUS-javítás: a plugin egyáltalán ELINDUL (korábban a konstruktor-sorrend
        miatt onEnable-crash lett volna: soulforge/resource/ritual huzalozás a mezők
        felépülte ELŐTT futott); `/parbaj` győzelem után a bűnpont TÉNYLEG csökken
        (SinManager.reduceSin elírt PDC-kulcsa javítva).
  - [ ] economy.yml: a `market:` blokk EGYszer szerepel (a dupla kulcs a piac/aukció
        beállításokat némán elnyelte volna) — `/icesmp config get market.fee-percent`
        a fájlbeli értéket mutatja.
  - [ ] `/ceh kirug <ismeretlen név>` nem fagyaszt (nincs blokkoló Mojang-lookup),
        ismeretlen névre hibaüzenet. `/market <TAB>` felajánlja az `ereklye`-t.
  - [ ] Rúna-felrakásnál a rúna a kurzorról TÉNYLEG elfogy (explicit setCursor);
        erszény levegőbe-kattintva továbbra is nyílik.
  - [ ] Kilépéskor takarul a párbaj- és kém-állapot (PlayerSessionCleanup-regisztráció);
        projectile-PvP két régió közt nem olvas idegen PDC-t (Sárkánytojás-bónusz
        cache-ből, isOwnedByCurrentRegion-kapuval).
  - [ ] Bestiárium-mérföldkő hibás config-sora (nem szám) csak kimarad, nem dob;
        ritka variáns csak TÉNYLEGESEN szintezett mobra kerül; játékos-karaván
        kifizetése nem duplázódhat (tick szinkronizálva); szezon-hossz élő átírása
        NEM ismétli meg a nagydöntő-boss spawnját (szezon-azonosító = kezdő-bélyeg).
- [ ] **Teljes kódbázis-audit (430 fájl, 7 mélyvizsgálat) — 1. javítás-hullám:**
  - [ ] FORDÍTÁS-BLOKKOLÓ: 4 monk/harcos spell (Whirlwind/DeepBreath/SpinningCraneKick/
        FlyingSerpentKick) statikus helperje instance-metódust hívott — javítva.
  - [ ] Relikvia-cooldown NEM nullázódik ki-be lépéssel (relog-exploit zárva); világboss-
        jutalom csak a KÖVETETT bossért jár (crash-árva példány nem fizet duplán).
  - [ ] Pakt-oltár (E25) ÉLETRE KELT: az üres sacrifice-lista eddig némán letiltotta a
        rituálét ("ritual-missing-sacrifice") — üres lista = nincs áldozat-követelmény.
  - [ ] Karaván-kíséret (escort) spawn-guard: konvoj + hullám-mobok nem spawnolnak
        territórium/claim/WG-régió belsejébe, vízre (új `escort` mátrix-sor).
  - [ ] Vagyon-elérés az ÖSSZES valuta összegét nézi (RED/BLUE/DARK játékos is elérheti);
        király-koronázás csak aktuális frakciótagnak (átállt szavazó nem koronázható);
        claim-méret long-számítás (int-túlcsordulás plafon-kerülése zárva).
  - [ ] Folia: párbaj-elfogadás a kihívó PDC-jét a SAJÁT szálán írja; céh-taglista COW
        (CME-védelem); szerver-kihívás bossbar-frissítés synchronized; kém-álca
        levétele plugin-leálláskor (spyManager.shutdown).
  - [ ] Baráti tűz: Dühös Csirke + Árnyégés nem sebez párttagot/frakciótársat; EGYETLEN
        élőhalott-definíció (csontváz-/zombiló is — UndeadUtil a közös forrás).
  - [ ] Konjunktúra (F14) a sokk kikapcsolásakor is él; adomány-láda mentése debounce-olt;
        hangulat-esemény pénze AFK-nak nem jár; 8 halott metódus + holt ctor-paraméter törölve.
- [ ] **Gameplay-review kör (teljes diff, 4 mélyvizsgálat) — balansz-javítások:**
  - [ ] BANK-ONLY zárás: a király kassza-kivéte FIZIKAI veretben érkezik + napi keret
        (`factions.treasury.withdraw-daily-cap` 1000); a VAGYON-elérések kaszt-XP-t
        fizetnek veret helyett (a kölcsön-token exploit halott).
  - [ ] Vérdíj-pénznyomda fék: ugyanarra az áldozatra csak
        `factions.sins.bounty.per-victim-cooldown-hours` (12) óránként jár kifizetés
        (a bűn-törlés jár, a broadcast elmarad, a vadász csendes üzenetet kap).
  - [ ] Kém-küldetés: liga-pont CSAK ha az álca IDEGEN frakció territóriumában jár le
        (saját bázison AFK = semmi), és naponta max `spy.points-daily-limit` (2) siker
        pontoz. Párbaj: liga-pont csak KÜLÖNBÖZŐ frakciójú felek közt; a felkérés
        60 mp után lejár és élő felkérés nem írható felül némán.
  - [ ] Liga-hangolás: raid `season-points` 5→10; B33×G16 NEM szorzódik össze (a
        nagyobbik él, max ×2 idő-szorzó); szerver-szintű közösségi cél súlyozatlanul
        fizet (community-server forrás — a NEUTRAL 1.5 csak a saját céljaira jár).
  - [ ] Pénz-csapok sapkája: mob-pénz `mob-money-drop.daily-cap` (300/nap/játékos —
        a natural-spawn darkroom-farm fékje), horgász-lelet `fishing-windfall.daily-cap`
        (150/nap); valutaváltási díj default 3% (oda-vissza spekuláció fékje).
  - [ ] H14 ÚJRA ÉL: a maybeMakeRareVariant hívás visszakötve az applyScaling-be (a
        2. review-kör sorrend-javítása kivette — a variáns-rendszer halott kód volt);
        mob-szint kemény plafon: `mob-scaling.hard-cap-level` (15) a vérhold/zóna-bónusz
        UTÁN is fog. DARK undead-spawn mostantól spawn-rules mátrix-soron megy
        (`dark-undead`: territórium engedett, claim/WG/víz tiltott).
  - [ ] Rontás dark-bias 65%→50%; rejtvény-quest pénz-jutalmak ~40%-kal vágva (a
        fejezet-questek kaszt-XP-t kaptak — a nehezebb story ne fizessen rosszabbul);
        rejtvenyi_elso_nyom → rejtveny_elso_nyom átnevezve.
  - [ ] Raid lánc-fék: `factions.raid.cooldown-minutes` (60) két hirdetés közt.
- [ ] **Menü/config/admin integráció (teljessé tétel):**
  - [ ] Főmenü: új „Bestiárium” (GUI) és „Szakma-céh heti cél” csempe; Frakció-almenü:
        „Céh”, „Kém-álca”, „Karaván-indítás: 100” (király-parancsra fut); Körözési
        lista: „Becsület-párbaj” csempe; Lélekszilánk-almenü: „Lélek-kovács” (csak
        Nekromanta); Bank-almenü: „Ereklye-börze”.
  - [ ] Admin-menü: „Config-menü” gomb (icesmp.admin.config node-ra kapuzva),
        „Item-adás” (icesmp.admin.item), „Rontás-góc nyitása” és „Régészeti lelőhely”
        esemény-trigger; a hasAnyAdminAccess a két új node-ot is nézi.
  - [ ] `/events corruption` + `/events archeology` (rontas/regeszet alias, admin-node,
        tab-complete) — a CorruptionManager.forceSpawn eddig sehonnan nem volt hívható.
  - [ ] Config-GUI (`/icesmp config menu`): 6 ÚJ kategória — Szezon-liga (pontok,
        nagydöntő), Rontás-zóna (dark-bias-szal), DARK-népesség + ritka variánsok,
        Céhek + szakma-hét, Párbaj + kém, Börze + városi őrség — mind élő kulcsokra.
- [ ] **G6 — Becsület-párbaj (ÚJ):**
  - [ ] `/parbaj kihiv <név>` (CSAK bűnös ajánlhat) → `/parbaj elfogad|elutasit`; elfogadáskor
        3 perces ablak; a párbaj-kill NEM termel bűnt és NEM fizet vérdíjat (SinListener-kizárás
        a bounty ELŐTT); ha a bűnös nyer → -1 bűnpont (SinManager.reduceSin).
  - [ ] Heti limit (2, PDC-ben, ELFOGADÁSKOR fogy — alt-exploit fék); kilépéskor állapot-takarítás.
        Kulcsok: factions.yml `honor-duel.*`.
- [ ] **G14 — Kém-álca (ÚJ, LibsDisguises soft-depend):**
  - [ ] `/kem <célfrakció>`: 60 mp-es hamis nevű játékos-álca (SpyDisguise reflexiós híd,
        PlayerDisguise); frakciónkénti álnevek (`spy.fake-names.*`); 15 perc cooldown (PDC).
  - [ ] Korlátok: aktív raid alatt nem indítható; BÁRMILYEN PvP-találat (adott VAGY kapott)
        azonnal lebuktat (SpyRevealListener, támadó-oldalon régió-hoppal); a bűn-szabályok
        az álca alatt is élnek. LibsDisguises nélkül tiszta hibaüzenet.
- [ ] **I16 — Szakma-céh heti közös cél (ÚJ):**
  - [ ] Globális, frakció-független heti számláló szakmánként (egység = termelt szakma-XP;
        AtomicLong + concurrent mapek); `/szakmacel` mutatja az állást; perzisztens
        (profession-weekly.yml).
  - [ ] Hét fordulásakor: elért cél → broadcast + a küszöb (100) feletti hozzájárulók +300
        szakma-XP-t kapnak — online azonnal (saját régió-szálon), offline belépéskor
        (perzisztált függő jutalom). Kulcsok: professions.yml `profession-weekly.*`.
- [ ] **E25 — Pakt-oltár (Boszorkánymester, ÚJ):**
  - [ ] Új rituálé-típus: `pakt` (relics.yml `pakt_oltar` — SOUL_LANTERN mag, obszidián-alap);
        csak WARLOCK kaszt, NEM halmozható; ára 1× Első Csend Szilánkja a táskából.
  - [ ] Hatás: tartós +20% max Lélekerő (player-PDC + join-kor töltött concurrent cache —
        a ResourceManager.max() szál-biztos szorzó-lookupon át olvassa). Kulcsok: `pakt.*`.
- [ ] **E32 — Sárkánytojás-töredék (Sárkányidéző, ÚJ):**
  - [ ] Új relikvia: `sarkany_tojas` (DRAGON_EGG, ITEM_MODEL `relic_sarkany_tojas`); amíg a tulajdonosa Sárkányidéző
        (EVOKER), az Eszencia-poolja +10% (`pakt.dragon-essence-bonus-percent`) — másnak
        csak presztízs-tárgy. A relikvia-szabályok (tulajdon, rablás) változatlanok.
- [ ] **E1 — Lélek-kovács (Nekromanta, ÚJ):**
  - [ ] `/soulforge` (alias /lelekkovacs): 3 ág (Élet/Sebzés/Létszám), áganként 5 rang,
        növekvő szilánk-ár (5/8/12/18/25 — classes.yml `soulforge.rank-costs`); rangok
        player-PDC-ben.
  - [ ] Cast-kor a SummonMinionsSpell a rangokból olvas (statikus híd): minion-HP ×(1+8%/rang),
        sebzés ×(1+6%/rang), Létszám-ág extra idézés-slot ([0,0,1,1,2,3] — max +3).
- [ ] **E7 — Varázsló rúnaíró affinitás (ÚJ):**
  - [ ] A Varázsló minden rúna-hatást DUPLÁN olvas (`runes.wizard-affinity-multiplier` 2.0):
        él/bástya-százalék és láng/fagy-esély szorzódik.
  - [ ] Visszhang Rúnája (ITEM_MODEL `runa_visszhang`, Varázsló-ZÁRT recept — új `job:` recept-mező +
        JobManager-ellenőrzés a craftban): találatkor eséllyel visszhang-csapás
        (+30% bónusz-sebzés, ENCHANT-partikel). Kulcsok: crafting.yml `runes.runa_visszhang.*`.
- [ ] **B21 — Bestiárium / gyűjtő-album (ÚJ):**
  - [ ] `/bestiarium` (alias /bestiary, /lajstrom): csak olvasható áttekintő GUI — 4 kategória
        (Szörnyek/Receptek/Territóriumok/Világbossok) számlálóval; a haladás player-PDC-ben
        (CSV-halmazok), nincs külön store.
  - [ ] Hookok: mob-faj első elejtése (MONITOR), boss-archetípus (WorldBossManager.isWorldBoss),
        recept első craftja (ProfessionRecipeBookListener setter-hook), territórium első
        belépés (TerritoryListener setter-hook).
  - [ ] Mérföldkövek: world.yml `bestiary.milestones.*` ("darab:veret[:broadcast]") — a jutalom
        FIZIKAI veretben érkezik; a nagy mérföldkő broadcastol.
- [ ] **B6 — Játékos-indított karaván (ÚJ):**
  - [ ] `/faction caravan send <összeg>` (csak KIRÁLY): a kasszából rakomány indul; sorsolt
        őrzőpont a vadonban (EventSpawnGuard `player-caravan` mátrix-sor: territórium/claim/
        régió/víz tiltva), broadcast a koordinátákkal — 5 percig védhető VAGY rabolható.
  - [ ] Túléli az ablakot → a kassza a rakomány ×1,25-ét kapja (profit); ellenséges játékos
        leöli a konvojt → a rakomány a RABLÓ frakció kasszájáé; saját frakciós ölés → elvész.
  - [ ] Frakciónként cooldown (90 perc); restart/leállás közben aktív szállítmány
        visszatérítődik. Élő kulcsok: world.yml `player-caravan.*`.
- [ ] **B35 — Céhek (ÚJ):**
  - [ ] `/ceh letrehoz <név>` (alapítás 250 saját valutáért a számláról — sink); csak azonos
        frakcióból hívható tag (`/ceh meghiv` → `/ceh elfogad`); vezető-átadás kilépéskor,
        egyedül maradva feloszlik. Perzisztencia: guilds.yml (YamlStore).
  - [ ] Céh-szint: quest-teljesítésenként +10 céh-XP (`guilds.xp-per-quest`); szintlépéskor
        tag-broadcast; a taglétszám-plafon 10-ről 3 szintenként +1-gyel nő (max 15).
  - [ ] `/ceh befizet <összeg>` — a tag számlájáról a céh-kasszába (bankon belüli átvezetés);
        `/ceh info` (szint/XP/tagok/kassza), `/ceh lista` (top 10 XP szerint).
  - [ ] Függő meghívás kilépéskor takarítva (PlayerStateCleanup). Élő kulcsok: factions.yml `guilds.*`.
- [ ] **B26 — Rúna-kovácsolás (ÚJ):**
  - [ ] 6 rúna unique-materialként (ITEM_MODEL `runa_*`), Kovács/Bűvölő 26-42. szintű receptek
        (runapor + tematikus unique + Rúnakréta kellék): Él (+közelharci sebzés%), Zápor
        (+lövedék-sebzés%, a nyíl örökli az íj rúnáját), Bástya (mellvért, -kapott sebzés%),
        Láng (gyújtás-esély), Fagy (lassítás-esély), Mohóság (erszény-drop esély bónusz).
  - [ ] Felhelyezés: a KURZORON tartott rúnát kattintsd a cél-tárgyra a táskában — PDC
        `rune_effect` + lore-sor; tárgyanként EGY rúna, nem cserélhető; cél-típus a
        `runes.<id>.applies` szerint (weapon/bow/chest). Élő kulcsok: crafting.yml `runes.*`.
- [ ] **J9 — Fejezet-rendszer (szezon story-ív, ÚJ):**
  - [ ] A szezonnak perzisztens **fejezet-sorszáma** van (`season.number` a season.yml-ben,
        `SeasonManager.getSeasonNumber()`); szezonváltáskor nő + „Új fejezet nyílik” broadcast.
  - [ ] `chapter: N` mező a questeken: csak az N. fejezet alatt vehető fel — lezárt fejezetnél
        „a krónika továbblapozott”, jövőbelinél „még nem nyílt meg” üzenet; a MÁR FELVETT
        fejezet-quest kegyelemből befejezhető, de a next-lánc új tagja már nem nyílik.
  - [ ] 1. fejezet demo-lánc: „A Kapu Árnyéka” (suttogások → bizonyíték → krónikás, 3 quest,
        next-láncolva); admin-szerkesztés: `/quest admin set <id> chapter <N>`.
- [ ] **Profession-mélyítés (ÚJ):**
  - [ ] **~50 recept/szakma (össz. 407 — armorer 53, enchanter 54):** minden szakma szintlétrája az 1. szinttől az 50.-ig
        kitöltve; a magas szintű receptek kelléket + ritka unique anyagot kérnek; az 50-es
        céh-mesterművek (A Mélység Szíve / A Világfa Magja / Az Erdő Szíve / A Céhmester
        Üllője / A Bölcsek Köve / A Végtelen Kódex / A Bokic Áldása / A Kapu Lakomája)
        Első Csend Szilánkját is.
  - [ ] **Craft-XP (skill-up):** recept-könyvi craft szakma-XP-t ad (`professions.xp.recipe-craft-*`;
        base 8 + szint×2); a szinted felett 10+ szinttel járó recept fél, 20+ felett 0 XP
        („szürke recept”). Szintlépéskor üzenet + hang, fokozatváltásnál külön üzenet.
  - [ ] **Fokozatok:** Inas (1–9) → Segéd → Legény → Mester → Nagymester → Legendás Mester (50);
        a szakma-GUI a szint mellett a fokozatot is mutatja.
  - [ ] **ITEM_MODEL manifest:** minden nevesített (lore-os) recept-eredmény item-modelt visel; teljes lista: `docs/RESOURCE_PACK_CMD.md` (resource pack készítéshez). Új custom item = új `icesmp:<modell-id>` + új manifest sor.
- [ ] **`/iceitem` admin item-adó (ÚJ):** `icesmp.admin.item` joggal
      `/iceitem <unique|recept|relikvia|tervrajz|erszeny> <id> [darab] [játékos]` — tab-complete
      mind az öt típus id-listájával. A `recept` út a teljes stamp-lánccal ad (signature-PDC,
      custom enchant, „Készítette", affix-roll — bitre azonos a craftolttal); a `relikvia`
      force-móddal ír tulajdont; másik játékosnak adva a cél régió-szálán landol a tárgy.

---

## 5. ⚠️ Folia-specifikus tesztpontok (kiemelt)

A plugin Folia-régió-szálkezelést használ. A leggyakoribb hiba a **cross-region/cross-entity**
hozzáférés — ezt a konzol `IllegalStateException` / „Thread ... cannot access region ..." üzenettel
jelzi. Célzottan próbáld ki ezeket (a hibajavítások ezeket fedik):

- [ ] **Kill-jutalmak régióhatáron:** ölj mobot úgy, hogy te és a mob épp más-más régió közelében
      vagytok → kaszt-XP, pet-XP, lélekszilánk, quest- és napi-haladás mind hibamentes.
- [ ] **Piac más régióban lévő eladóval:** vásárolj, miközben az eladó messze/másik régióban van.
- [ ] **Ostromágyú távoli célra:** lőj egy messzi pontra (másik régió) raid alatt.
- [ ] **Admin parancs távoli célpontra:** `/class addxp`, `/currency set`, `/faction set`,
      `/quest complete` egy **másik régióban tartózkodó** játékosra.
- [ ] **Mételytépő PvP-ölés** másik régióban lévő áldozatra/gyilkosra.
- [ ] **Totem/idézés/forma** régióhatár közelében.
- [ ] **Általános:** futtass egy hosszabb session-t több játékossal a világ különböző pontjain, és
      figyeld a konzolt bármilyen `region`/`scheduler`/`IllegalStateException` stacktrace-re.

> Ha bármelyiknél stacktrace jön a konzolon, az **bug** — jegyezd fel a teljes stacket.

---

## 6. Hibabejelentő sablon

```
Cím: <rövid leírás>
Rendszer: <pl. Erő-csík / Frakció passzív / Világboss / Folia-cross-region>
Lépések:
  1. ...
  2. ...
Elvárt eredmény: ...
Tényleges eredmény: ...
Konzol-log (ha van): <teljes stacktrace>
Folia-gyanú? (régióhatár/cross-entity volt?): igen / nem
Reprodukálható?: mindig / néha / egyszer
Build/verzió: <jar verzió>
```

---

## 7. Prioritási javaslat a teszteléshez

1. **Erő-csík + hibrid költség** (4.3) — ez a legfrissebb rendszer, itt a legvalószínűbb a finomhangolás.
2. **Folia cross-region pontok** (5.) — a stabilitás kulcsa; ezek frissen javítva, célzott teszt kell.
3. **Frakció-passzívok** (4.1) — szintén frissen módosítva (Kék fulladás, Semleges zuhanás, invis kivéve).
4. Utána a többi rendszer (kasztok, specek, gazdaság, események) végig a 4. szekció szerint.

Jó tesztelést! ❄️

## Teszter-visszajelzés kör (2026-07-20)
- [ ] **Konvoj-halál átláthatóság:** a kíséret-láma CSAK szörny-sebzéstől halhat
      (támadás/lövedék/robbanás) — esés/fulladás/kaktusz/tűz nem morzsolja némán.
      Ellenőrzés: told vízbe/magasból — nem sérül; szörny üti — sérül, a bossbar
      HP-száma frissül.
- [ ] **Kíséret lazítva:** wave-interval 45→60 mp, wave-size 4→3 (world.yml escort).
- [ ] **Horgony-rotáció:** world boss és escort nem spawnol kétszer egymás után
      ugyanarra a játékosra (ha 2+ online). Teszt: két játékossal két egymás utáni
      esemény → különböző horgony.
- [ ] **Piglin-zombisodás:** az EventSpawnGuard.prepare a bossra ÉS a SUMMON-addokra
      is fut (setImmuneToZombification) — a teszter által látott zombisodás a korábbi
      buildben volt; friss builden ellenőrizendő, hogy a Pokoli Hadúr + kísérete a
      felvilágon sem alakul át.
- [ ] **HP-sáv:** a világboss-sáv sebzésre azonnal (WorldBossListener) és 2 mp-enként
      (fázis-tick) is frissül — friss builden ellenőrizendő; az ESCORT sávja
      TÁVOLSÁG-alapú (a % az út megtett része, a HP csak szöveg) — ha ez zavaró,
      külön HP-sáv tétel nyitható.
- [ ] Backlog (ideas N24-N27): claim-segéd varázsló, hely-kötött eventek + kultista
      esemény, ismételt-spawn kegyelem (gyengébb boss VAGY ideiglenes buff),
      kereskedő-karaván szabad spawn.

## Kijelölő-pálcák (N24 — teszter-kérés)
- [ ] **Birtokmérő pálca** (`/claim wand`, ITEM_MODEL `selection_wand`, STICK): bal katt blokkra = 1. sarok,
      jobb katt = 2. sarok (méret+ár-előnézet chatben, átfedés-figyelmeztetéssel),
      SNEAK+jobb = foglalás (a /claim area teljes ár/limit-logikáján át). A kattintott
      BLOKK koordinátája megy a kijelölésbe (nem a játékos helye). A pálca-katt nem
      üt/nem nyit semmit (cancel).
- [ ] **Határkijelölő pálca** (`/territory wand`, ITEM_MODEL `selection_wand_territory`, BLAZE_ROD, admin-node):
      bal katt = poligon-pont a kattintott blokkon, jobb katt = utolsó pont visszavonása,
      SNEAK+jobb = határ-előnézet (/territory show); létrehozás továbbra is
      /territory create-tel. Jog nélkül a pálca nem csinál semmit (hibaüzenet).
- [ ] Tab-complete: `claim wand`, `territory wand`; ITEM_MODEL manifest felvéve.

## N25/N27 — Esemény-spawnpontok és hely-horgony (teszter-kérés)
- [ ] `/events spawnpoint add <world-boss|escort|caravan|any> [id]` az admin helyén
      pontot vesz fel (restart-álló: event-spawnpoints.yml); `remove <id>` / `list`.
- [ ] `world-events.anchors.<esemény>.mode`: player (default, régi viselkedés) |
      points (admin-pontok közül sorsol) | random (véletlen koordináta a fő világ
      spawnja körül, `random-radius` 1500) | mixed (pont, ha van; különben random).
      Élő kulcsok — /icesmp reload nélkül is állíthatók.
- [ ] Világboss: points-módban az admin-aréna környékére (±8 blokk) spawnol, a
      spawn-rules mátrix ott is él; escort: a konvoj-útvonal a ponttól indul;
      kereskedő-karaván: a configolt megállók (stops) elsőbbsége marad, utána
      jön a horgony-mód, végül a játékos-fallback.
- [ ] N26 kegyelem-mechanika TULAJ-DÖNTÉSSEL ELVETVE (se gyengébb boss, se buff) —
      ismétlődés ellen a horgony-rotáció + hely-horgony véd.

## N25b — Kultista esemény (ÚJ, teszter-ötlet + lore)
- [ ] Három változat súlyozott sorsolással (cultists.variant-weights): TÁMADÁS (4 fős
      portya — akolitus + pengék; az utolsó leölése broadcast-tal zár), RÍTUS (3 hív
      kört áll, SOUL-particle + kántálás-hang; rite-minutes [6] alatt MINDET le kell
      ölni → rite-loot hullik; különben a rítus beteljesül és rite-corruption-chance
      [60%] eséllyel RONTÁS-GÓC nyílik a helyszínen!), HÍRVIVŐ (magányos csuklyás a
      legközelebbi DARK territórium felé lépked — leölve zár, elérve/lejárva köddé
      válik "az üzenete célba ért" broadcast-tal).
- [ ] Spawn: spawn-rules.cultists mátrix-sor + N25 horgony-mód (anchors.cultists);
      minden mob prepare-elt (nem ég, nem zombisodik), szintezett (mob-level 5),
      PDC-jelölt; despawn plugin-leálláskor. Admin: /events cultists (+ tab).
- [ ] Ellenőrzés: rítus kivárva → góc nyílik a rítus helyén; rítus megszakítva →
      loot a kör közepén; hírvivő követése elvezet a DARK föld felé.

## Kultista × Suttogó crossover (tulaj-kérés)
- [ ] BETELJESÜLT kultista esemény (rítus lefutott VAGY a hírvivő célba ért) →
      minden online, felesküdött Suttogó gyanúja −15 (cultists.whisper-suspicion-relief,
      PRIVÁT üzenettel — nem leplez le), és a DARK frakció +3 liga-pont ("cult" forrás).
- [ ] A hírvivő tétje így: leölve loot + a hálózat vesztesége; célba érve a Suttogók
      álcája mélyül és a DARK pontot kap — a védelme valódi cél a rejtett/DARK oldalnak.
- [ ] Nem farmolható: az esemény csak természetes sorsolással (vagy admin-triggerrel)
      indul, játékos nem tudja kiváltani. Ellenőrzés: Suttogó-státuszú játékossal
      várd ki egy rítus beteljesülését → gyanú-érték csökken (/suttogas), liga-pont nő.

## P-audit javítások, 1. kör (gameplay-audit — [GYORS] tételek)
- [ ] Szerver-kihívás népesség-skálázása: `server-challenge.per-player-targets` (true)
      esetén a cél = `targets-per-player.<típus>` (slay 40 / mine 60 / harvest 80)
      × online létszám — 3 fősen és 30 fősen is elérhető. `false` → régi fix cél.
- [ ] Közösségi quest-célok újraskálázva (quests.yml community: vas 600, vadászat 1500,
      hal 500, szén 1000, sötét-zombi 800, raid-győzelem 4, világboss 3) és a heti
      szakma-célok is (professions.yml: armorer 2500, cook 1500, enchanter 1500,
      alchemist 1800) — kis szerveren is teljesíthetők.
- [ ] Onboarding-lánc folytatása: az `onboarding_gather` után új `onboarding_utmutatas`
      quest (Hírnök NPC — elirányít a /class, /profession join, /quest list felé).
- [ ] D1 ünnep-hook ÉLESÍTVE (halott kód volt): Rém-éj alatt a vérhold esélye ×2,
      az invázióé ×1.5 (`seasonal-events.remej.blood-moon-chance-mult` /
      `invasion-chance-mult` — bármely ünnepre megadható, élő kulcs).
- [ ] Világboss: a leütő SZEMÉLYES bónusz-loot-ot kap a kasszajutalom mellett
      (`world-events.world-boss.killer-loot`, 2 guríts — tárgy, sosem pénz).
- [ ] Rontás-góc: loot-tábla feljavítva (DIAMOND 2:5 + ENCHANTED_GOLDEN_APPLE) — a
      megtisztítás egyénileg is megéri; dark-undead ambiencia scope: "all" (minden
      DARK territórium, nem csak a főváros).

## P-audit javítások, 2. kör (gameplay-audit — [GYORS] tételek)
- [ ] Szezon-bajnok offline-kifizetés: a záráskor offline bajnok-tagok tárgy-jutalma
      függőbe kerül (season.yml: pending-champion-spoils, restart-álló) és a
      következő belépéskor jár, külön üzenettel. A buff/tűzijáték csak az élő
      ünneplés pillanatáé (online tagok).
- [ ] DARK-belépés kétlépcsős: az első `/faction join dark` figyelmeztet (örök paktum,
      nincs visszaút), és csak `factions.dark.join-confirm-seconds` (60) másodpercen
      belül megismételve lép életbe (0 = kikapcsolva). Ellenőrzés: első hívás nem
      vált frakciót, második (ablakon belül) igen.
- [ ] Kaszt-választó szerep-címkék: a /profile → Kaszt menü minden kaszt-ikonja
      "Szerep: ..." sort mutat (messages.job-gui-role-<id> kulccsal felülírható) —
      a visszafordíthatatlan döntés előtt látszik a tank/gyógyító/sebző irány.
- [ ] Napi NPC-quest rotáció ÉLESÍTVE: hero_trial, borostyan_kutatas, uti_kenyer,
      viharkvarc_fejto, korrupt_irtas a "napi-npc" rotation-groupban, naponta 3
      elérhető (nap-determinisztikus). A frakció-kötött anyag-questek nem rotálnak.
- [ ] Karaván unique-slot-garancia: a rotált készletben mindig van legalább egy
      `unique:` anyag (caravan.rotation.guarantee-unique) — a ritka szakma-alapanyag
      forrás sosem tűnik el egy-egy érkezésre.
- [ ] factions.yml komment-ellentmondás javítva: a Sötétbe lépés a KÓD szerint mindig
      ingyenes (sinner-feltétel + örök paktum az ára) — a config-komment már ezt mondja.

## P-audit javítások, 3. kör — esemény-orchestráció
- [ ] Egyszerre csak EGY nagy PvE-esemény indul természetes sorsolásból: a
      world-events.orchestration.major-events listán szereplő események
      (world-boss, invasion, wild-hunt, escort, cultists) sorsolása kimarad,
      amíg egy másik listás esemény aktív. Élő kulcsok; kapcsoló a config-menüben.
- [ ] Az admin /events parancsok (worldboss, invasion, wildhunt, escort, cultists)
      a kaput MEGKERÜLIK — kézi indítás mindig lehetséges.
- [ ] Ellenőrzés: futó világboss mellett az invázió/kíséret/kultista sorsolás nem
      indít újat (az intervallum ettől még pörög); a boss halála után a következő
      sorsolás már indíthat.

## P-audit javítások, 4. kör — hadi-ablak + Suttogó-erősítés + broadcast-diéta
- [ ] **Hadi-ablak:** menetrend szerint (factions.war-window.schedule, alap: szombat-
      vasárnap 18:00-20:00, szerver-helyi idő) vagy admin-nyitásra (/faction war
      start [perc], jog: icesmp.admin.war) a RED↔BLUE ölés NEM bűn és NEM
      vérdíj-eset — liga-pontot ér ("war" forrás, red/blue 1.0 súllyal).
- [ ] Farm-fék: ölőnként napi 5 pont plafon (daily-point-cap) + ugyanarra az
      áldozatra 30 perc cooldown (per-victim-cooldown-minutes) — a plafonon túli
      hadi-ölés bűn-mentes, de pontot nem ér (külön üzenet). Nyitás/zárás broadcast;
      státusz: /faction war (a következő nyitásig hátralévő idővel).
- [ ] Ellenőrzés: ablakon KÍVÜL a RED↔BLUE ölés +1 bűn (mint eddig); NEUTRAL áldozat
      az ablak alatt is a normál bűn-úton megy (DARK áldozat ölése SOSEM bűn — lásd
      lent); raid/párbaj elsőbbsége megmarad. Config-menü: új "Hadi-ablak" kategória
      (a gyökér-rács 36 slotos lett).
- [ ] **DARK-áldozat kivétel EXPLICIT (tulaj-szabály):** Kitaszított ölése sosem bűn
      (factions.sins.dark-victim-exempt, action-bar jelzéssel). Korábban ezt a
      vérdíj-ág fedte implicit módon, de a vérdíj-kifizetés nullázza a számlálót —
      a másodszor megölt Kitaszítottért bűn járt volna (rés bezárva). A 3+ bűnű
      DARK-áldozatért a vérdíj továbbra is jár.
- [ ] **Suttogó-erősítés:** beteljesült kultista eseménykor a felesküdöttek a
      gyanú-csillapítás MELLÉ privát tárgy-részesedést kapnak (cultists.
      whisper-loot-rolls [1] guríts a rite-loot táblából, "a hálózat osztozik"
      üzenettel); éjjel (13000-23000 tick) az élőhalottak a Suttogót sem támadják
      (factions.whisper.night-undead-truce) — árulkodó jel a szemtanúnak!
- [ ] **Broadcast-diéta:** a régészet-lelőhely (spawn + lejárat, archeology.
      announce-radius 160), a hullócsillag és az állat-csorda (ambient-events.
      local-announce-radius 192) csak a környéken állóknak szól (LocalAnnounce,
      Folia-safe távolság-ellenőrzés a címzett saját szálán). A nagy események
      (vérhold, boss, invázió, karaván, kihívás…) globális hírek maradnak.

## Tartalom-kör 1: mester-láncok mind a 13 kasztnak + fejezet 2-3 + capstone + kazamata-starter
- [ ] **9 új mentor+mester lánc** (druida, paplovag, halállovag, sámán, szerzetes, pap,
      boszorkánymester, démonvadász, sárkányidéző): kezdő próba → mentor (TALK_TO_NPC,
      100 XP) → mester-próba (kaszt-ízű feladat, 400 XP). Új mester-NPC-k: druida_mester,
      paplovag_mester, halallovag_mester, saman_mester, szerzetes_mester, pap_mester,
      pakt_mester, demonvadasz_mester, sarkany_mester (kihelyezés: EPITESZ_UTMUTATO).
- [ ] **Parkour kivéve a kötelező útból (tulaj-döntés):** a 4 régi mester-próba is
      kaszt-ízű feladat lett (harcos: 20 Lvl2+ mob; íjász: 12 csontváz; varázsló:
      3 bűvölés; orgyilkos: 15 Lvl2+ mob) — a parkour opcionális szabadidős tartalom
      (acrobat_challenge megmaradt). Egyetlen pálya megépítése sem kötelező többé.
- [ ] **2. fejezet (2. szezon, 5 quest):** repedesek (bióm) → szilankok (16 ametiszt) →
      ujjaepites (24 vas-olvasztás) → orzok (15 Lvl4+ mob) → pecset (hírnök, záró-jutalom).
- [ ] **3. fejezet (3. szezon, 5 quest):** sohajok (20 zombi) → lelekfeny (8 lélekfáklya
      craft) → visszhang (2 echo shard) → ostrom (20 Lvl5+ mob) → harmadik_mondat
      (hírnök; ritka láda-kulcs jutalom).
- [ ] **Level-50 capstone:** ver_emlekezete (requires-level 50; 30 Lvl6+ elit mob) →
      kaszt_orokseg (hírnök; NETHERITE_INGOT + ENCHANTED_GOLDEN_APPLE + 2 ritka kulcs) —
      a 46-50-es sávnak van célja.
- [ ] **Kazamata-starter:** 2 kulcs-recept (bűvölő 30/40: A Mélység Kulcsa
      [dungeonkulcs_melyseg, ITEM_MODEL `melyseg_kulcsa`], A Csontkripta Kulcsa [dungeonkulcs_csontkripta, ITEM_MODEL `csontkripta_kulcsa`] — manifestbe felvéve); új player-guide oldal (16-kazamatak.md).
      A zónákat az építészek hozzák létre (melyseg, csontkripta id-vel).
- [ ] **Suttogó sötét-anyag:** a rítus-loot (és így a Suttogó-részesedés) mostantól
      árnyékport is adhat (unique:arnyekpor:2) — a guide ígérte sötét-mágiájú anyag él.

## Tartalom-kör 2: szezon-közép + fejezet1-bővítés + rejtvények + mellék-questek + sötét penge
- [ ] **Szezon-közepi ablak (ÚJ mechanizmus):** min-season-day / max-season-day quest-kulcs
      (QuestManager-kapu, admin-szerkeszthető, saját elutasítás-üzenetekkel) — a
      "A Fa üzenete" 3 questes lánc CSAK a szezon 20-40. napján vehető fel (ültetés →
      liget-védelem → vének, köznapi kulcs jutalommal). Ellenőrzés: a 20. nap előtt
      "még nem jött el az ideje", a 40. nap után "az ablak bezárult"; felvett quest az
      ablak zárta után is befejezhető.
- [ ] **1. fejezet 3 → 5 quest:** suttogasok → bizonyitek → ÚJ orjarat (12 Lvl2+ mob) →
      ÚJ jelentes (6 papír craft) → kronikas (a next-lánc átfűzve).
- [ ] **+6 rejtvény (16 → 22):** edes_ho (cukor), siro_ko (síró obszidián), lampas_nep
      (tengeri lámpás), kilenc_elet (macska-szelídítés), fold_vere (lávás vödör),
      hangtalan_dal (echo-szilánk) — a cél egyikben sem derül ki a leírásból.
- [ ] **4 mellék-quest a napi-npc rotációban** (pool: 5 → 9, naponta 3): kovács
      acél-rendelés (16 vas-olvasztás) + szén-szállítmány (32 szén deliver), vének
      gyógyfű-szüret (24 fű/virág) + méz-áldozat (3 mézesüveg deliver) — mind
      giver-npc-s, dialógussal, napi cooldownnal.
- [ ] **Az Éjszaka Pengéje** (sötét szignatúra-recept, bűvölő 35): netherit kard +
      4 árnyékpor + 2 echo-szilánk + 8 obszidián → crafted-affixes penge (ITEM_MODEL `ejszaka_pengeje`,
      regiszterben) — az árnyékpor sötét útról jön (rítus-loot / Suttogó-részesedés).

## Építész-kör: valós térkép + komp + portál-szabály (tulaj-döntések)
- [ ] **Komp (/komp, alias /ferry):** ferry.routes.<id> (a/b végpont "world,x,y,z",
      fee) — a játékos a KÖZELEBBI kikötőtől (max-board-distance 24) a túlpartra
      teleportál; viteldíj a banki egyenlegből ég el (money sink); 10 mp cooldown;
      révész-NPC kötés: /npcbind <npc> command "komp <útvonal>". Útvonal nélkül lista.
      Ellenőrzés: kikötőtől távol "menj közelebb"; kevés bank-egyenleggel elutasít.
- [ ] **Nether-portál világszabály:** új portál gyújtása MINDENHOL tilos
      (nether-portal.allow-creation: false, élő kulcs + config-menü kapcsoló) —
      action-bar üzenettel; a zóna-bypass jogú admin gyújthat (a Kárhozat Kapuja
      felélesztése). A NETHER_PAIR (túloldali pár) szabad → a Kapu működik.
      A Netherbe az EGYETLEN út a PvP-senkiföldjén át vezet.
- [ ] **EPITESZ_UTMUTATO a valós térképre:** T=Pyralingrad DNy (~-10k,+10k),
      K=Glatziendorf DK (~+5.5k,+12.5k), S=új Caldestera ÉK (~+5k,+2k, óceánnal),
      spawn=Ó-Caldestera a Fa tövében (1/b szakasz); Pyralingrad-anyagok javítva
      (mangrove vérfa + akácia + kalcit-diorit-nyír); hazatérés-kő opcionális;
      kazamata-terv 10+; mob-szintezés NYITOTT tulaj-döntésként jelölve.
- [ ] **Lore-kánon bővítés:** a Menedék kettészakadása (Ó-Caldestera a Fa hű
      követőié, az új Caldestera a csalódottaké) + a pyralingradi vérfa a kódexben;
      mechanika-kötések a LORE_REFERENCE-ben.

## Névváltás: Thanaopolis (tulaj-döntés)
- [ ] A DARK főváros mai neve **Thanaopolis** (a Holtak Városa); a **Mortengrad** a
      bukás előtti történelmi név (a kódexben így él tovább). Minden jelen-idejű
      szöveg (guide-ok, questek, kandalló-mese, /lore, építész-útmutató) Thanaopolist
      mond; a régi receptek (Mortengradi Hamukenyér / Árnygomba) nevei és id-i
      VÁLTOZATLANOK — a lore szerint az ősi név a receptekben maradt fenn.
- [ ] /lore: a "thanaopolis" alias is a Kitaszítottak-oldalt nyitja (a "mortengrad"
      legacy-aliasként megmaradt). Zóna-id javaslat az építészeknek: thanaopolis.

## Névadás: Radicora (a spawn-település lore-neve)
- [ ] A Fa tövében álló régi főváros kanonikus neve **Radicora** („a gyökerek
      városa" — radix + a Ryanora/Caldestera névcsalád -ora végződése); a nép
      ajkán Ó-Caldestera. Zóna-id javaslat: radicora. A korábbi PLAYTEST-sorok
      "Ó-Caldestera" említései erre a városra értendők.

## Névadás: Olethropyla (a Kárhozat Kapuja ősi neve)
- [ ] A Kapu ősi/krónikás-neve **Olethropyla** (görög: olethros "pusztulás" + pylé
      "kapu" — Thermopülai-áthallással); a játékos-szövegek a népi "Kárhozat Kapuja"
      nevet használják továbbra is. Zóna-id KÖTÖTT: karhozat-kapuja (quest hivatkozik rá).

## 50-60 fős kör: zóna-rámpa + personal-loot + Suttogó-kedvezmény + tartalom-hullám 3
- [ ] **Zóna-rámpás mob-szint (tulaj-kérés):** a biztonságos territórium-zónák
      (faction/protected/capital — doom-gate/dungeon NEM) peremétől kifelé a szint
      0-ról nő (mob-scaling.zone-ramp.blocks-per-level [250] blokkonként +1), amíg el
      nem éri a spawn-táv szerinti normál szintet. Zóna belsejében 0. Élő kulcsok +
      config-menü. Ellenőrzés: a 12k-s főváros fala mellett Lvl 0-1, ~2500 blokkra Lvl 10.
- [ ] **Personal-loot kiterjesztés (tulaj-jóváhagyás):**
      • Kincs: az első megtaláló teljes zsákmánya után a láda runner-up-seconds [45] mp-ig
        nyitva — fejenként EGYSZER, fél-értékű gurítás, max-claimants [12] plafon.
      • Vad Hajsza: minden sebző résztvevő fél-értékű saját gurítást kap (a leütő és
        partyja kimarad a 2. körből — ők a teljeset vitték).
      • Rontás: aki 3+ korrupt fajzatot irtott, a tisztításkor fél-értékű privát gurítást kap.
- [ ] **Suttogó feketepiac-kedvezmény:** a felesküdöttek a feketepiacon CSENDBEN 25%
      kedvezményt kapnak (blackmarket-discount-percent — a kijelzett ár marad, semmi
      látható nem leplez le). Config-menü kulcs a Suttogók kategóriában.
- [ ] **Közösségi célok 50-60 főre:** vas 1500, szerver-vadászat 4000, hal 1200,
      érc 2500, sötét-zombi 1500, raid 6, világboss 5.
- [ ] **Tartalom-hullám 3 (123 → 136 quest):** +4 rejtvény (26 összesen); révész-lánc
      (ÚJ revesz NPC a kikötőben: ismerkedés → 12 hal → 24 deszka); 4 frakció-heti
      (kohók/tisztogatás/vásár/lélek-aratás, 168h); +2 napi a rotációban (hírvitel,
      fegyvermustra — pool: 11, naponta 3).
- [ ] **Ingame lore-bővítés:** +8 tábortűz-mese és +6 Idegen-sor az új nevekkel
      (Radicora, vérfa, Olethropyla, révész, Thanaopolis); /lore aliasok: radicora,
      olethropyla. TULAJ-DÖNTÉS RÖGZÍTVE: kaszt-váltás CSAK adminnal (self-service nem lesz).

## Tanács + DARK mob-rules + invázió-élettartam (tulaj-jóváhagyások)
- [ ] **Vének Tanácsa (NEUTRAL):** /tanacs szavaz <játékos> (heti 1 szavazat, csak
      Menedék-polgárra, átszavazható; hét-fordulón nullázódik + broadcast); a tanács =
      a hét élő top-3 szavazottja (factions.council.seats). Jogok: kassza-kivét
      (tanácsi napi keret: 400), karaván-indítás, /tanacs vasarhet (Creutzér piaci
      díj-kedvezmény ablak, market-week-minutes [60] — a meglévő konjunktúra-mechanikán).
      Raidet a tanács NEM hirdethet. Perzisztens: council.yml (csak a futó hét szavazatai).
      Config-menü kulcsok az Adó-kategóriában.
- [ ] **DARK mob-rules élesítve (1-es opció):** a Kitaszítottak földjén minden spawnoló
      mob +2 szint (a zóna-rámpázott alapra) és az élőhalott nappal sem ég — Thanaopolis
      környéke nem szelídített vidék. (A 4-7-es élőhalott-népesség ettől független.)
- [ ] **Invázió-mob élettartam (teszter-panasz: kóbor pókok a városban):** a le nem ölt
      horda-mob mob-lifespan-seconds [600] után köddé válik (saját entity-schedulerén,
      POOF-effekttel) — a nappal megszelídült pók nem vándorol be a semlegesbe.

## Frakció-váltás szezon-szabályok + tartalom-hullám 4 (tulaj-kérés, 2026-07-21)
- [ ] **Szezon-plafon:** ugyanabban a szezonban a 3. frakció-váltási kísérlet elutasul
      (factions.switch.max-per-season [2]); az első, ingyenes választás NEM számít bele;
      a Semlegesből ingyen váltás és a DARK-ba lépés VISZONT igen (PDC-számláló,
      szezon-bélyeggel — új szezonban nullázódik).
- [ ] **Hajrá-zár:** a szezon utolsó 7 napjában (factions.switch.lockout-final-days)
      SEMMILYEN váltás nem megy: fizetős, Semlegesből ingyenes, DARK-belépés, /faction
      leave — mind elutasul a hajrá-üzenettel. A bűn-száműzetés (kényszer-út) továbbra
      is működik.
- [ ] **Napi pool 15-re nőtt:** napi_ospatak (horgász), napi_erclelet (érc),
      napi_csontszuret (csontváz), napi_fuvesasszony (virág) — a rotációban felbukkannak.
- [ ] **4 új rejtvény (30 össz):** soharcos (állvány-craft), feher_csend (havas síkság),
      melysegek_szava (5 hal), hamu_kenyere (12 sültkrumpli) — a leírásból kitalálható,
      teljesítéskor jár a jutalom.
- [ ] **Frakció-heti 2. kör:** red_heti_hatartisztitas (45 kill), blue_heti_jegszuret
      (96 jég), neutral_heti_vasarjaras (10 villager-trade), dark_heti_csonttized
      (60 zombi) — csak a saját frakció veheti fel, 168h cooldown.
- [ ] **Gyökerek-lánc (3):** mangrove-gyökér → hajtás-ültetés → Radicora-látogatás
      (VISIT_TERRITORY "radicora" — a zóna kijelölése UTÁN tesztelhető végig).
- [ ] **Hamu és zúzmara lánc (3):** 25 kill → pajzs-craft → 3 bűvölés; a záró ritka
      kulcsot ad.
- [ ] **Beszállító-hetik (4):** fa/kő/étel/bőr leadás a vandor_kereskedo NPC-nek
      (DELIVER_ITEMS — az NPC átveszi a tárgyakat).
- [ ] **Nagy heti kihívások:** heti_nagyvadaszat (120 kill → ritka kulcs),
      heti_nagyhalaszat (40 hal → 2 köznapi kulcs).

## Fable-javítási kör (P2-leletek első hulláma, 2026-07-22)
- [ ] **Reload-race zárva:** /icesmp reload közbeni craftolás és relikvia-használat nem
      dob hibát (RelicRegistry synchronized, CraftingRestrictionManager COW-lista);
      a reload után a relic/mob-scaling/craft-restrikció configok AZONNAL élnek
      (reload-hook, restart nélkül).
- [ ] **Kultista rítus:** az utolsó hívő levágása a határidő pillanatában sem adhat
      dupla jutalmat/kettős broadcastot (claimClose csere).
- [ ] **Load/save kaszkád:** egy kézzel elrontott YAML-store mellett a többi manager
      betöltődik (severe log a hibásról), leállásnál a többi mentése lefut.
- [ ] **Autosave:** settings.autosave-minutes [10] percenként async mentés — kill -9
      után legfeljebb ennyi percnyi adat hiányzik (pl. friss stats/ranglista).
- [ ] **Raid restartnál:** folyamatban lévő ostrom alatt leállítva a szerver broadcast
      jelzi az eredmény nélküli zárást (nem némán tűnik el).
- [ ] **Párbaj-invariáns:** egy kihívó második függő kihívása "duel-pending" hibát ad;
      elfogadás csak akkor megy át, ha egyik fél sem áll már aktív párbajban.
- [ ] **Szezonális quest-id:** world-events.season.length-days átírása a futó szezonban
      NEM nyitja újra a már teljesített seasonal questeket.
- [ ] **Spell-erő plafon:** spells.total-power-cap [1.75] — max mastery + max szint +
      lánc-finisher együtt sem üt nagyobbat, mint alap-sebzés × 1.75 (pl. obliterate
      max ~14, deathfrost-kombó ~22.5 össz a korábbi 36 helyett).
- [ ] **Fővárosi raid él:** meghirdetett raid alatt a célzóna-fővárosban a REGISZTRÁLT
      harcosok tudnak PvP-zni (kill-pont születik), nem-résztvevőt továbbra is véd a
      zóna máshol; a raid végén a tiltás visszaáll.

## "A világ visszagyógyul" — robbanás-regen + törmelék (tulaj-kérés, 2026-07-22)
- [ ] **Robbanás védett zónában/claimben:** a creeper/TNT LÁTVÁNYOSAN robban — a blokkok
      törmelékként repülnek ki a középpontból (pattognak, csúsznak), landolva/pár mp után
      porfelhővel eltűnnek; SEMMI nem droppol; a kráter valódi.
- [ ] **Visszaépülés:** territory.protection.regen.delay-seconds [180] után a blokkok
      PONTOSAN az eredeti állapotukba épülnek vissza, alulról felfelé (menetenként max
      40 blokk); a közben odaépített idegen blokk drop nélkül tűnik el.
- [ ] **Láda-teszt:** robbanás láda/kemence mellett — a tile-entity blokk NEM robban ki,
      tartalma érintetlen.
- [ ] **Restart-teszt:** robbanás után, a visszaépülés ELŐTT restart — a lyuk a restart
      után is visszaépül (block-regen.yml perzisztencia).
- [ ] **Kikapcsolva** (regen.enabled: false): a korábbi teljes robbanás-tiltás él.

## Ostrom-rombolás + teljes rombolás-lefedettség (tulaj-kérés, 2026-07-22)
- [ ] **Ostrom-bontás:** élő raid alatt a CÉLZÓNÁBAN a regisztrált harcos kézzel bont
      falat — nincs drop/XP, a blokk siege-delay-seconds [300] után visszaépül;
      nem-résztvevő és zónán kívüli harcos továbbra sem bonthat.
- [ ] **Ostromon kívüli bontás** (player-break.always-enabled, ALAPBÓL KI): bekapcsolva
      bárki bonthat védett zónában (always-delay-seconds [120] regen); kikapcsolva a
      régi tiltás él. Láda/kemence SOSEM bontható így.
- [ ] **Wither-teszt:** Wither robbanás + test-bontás védett zónában/claimben —
      törmelék repül, drop nincs, minden visszaépül; a Wither többé NEM rombol véglegesen
      (a territory-oldalon ez rés volt: eddig semmi nem fogta).
- [ ] **Ravager/enderman claimben:** blokk-evés/rombolás → regen-út vagy tiltás,
      maradandó kár nincs.
- [ ] **Tempó-hangolás:** restore-interval-ticks [10] + blocks-per-pass [3] — a fal
      láthatóan, fokozatosan épül vissza (kb. 6 blokk/mp); a kulcsok átírásával a
      sebesség változik (interval-hoz restart kell).

## Regen-finomítások: gyógyulás-effekt + törmelék-% (tulaj-kérés, 2026-07-22)
- [ ] **Gyógyulás-effekt:** visszaépüléskor blokkonként az anyag saját lerakás-hangja
      szól + porfelhő (restore-effects-enabled [true]) — kikapcsolva néma.
- [ ] **Törmelék-%:** debris-percent [100] csökkentésével (pl. 40) a kirobbant blokkoknak
      csak ennyi %-a repül ki törmelékként, a többi csendben tűnik el.
- [ ] **Metadata-teszt:** lépcsők/félblokkok/forgatott blokkok orientációja a
      visszaépülés után PONTOSAN az eredeti; tábla/zászló/láda (tile-entity) eleve nem
      robban ki, adata nem veszhet el.
- [ ] **Perem-eset (ismert):** támaszték-vesztő rátett blokk (fáklya, falitábla) a
      vanília fizikával lepattanhat és droppolhat — playtesten mérd, mennyire zavaró.

## Regen 3. kör: rúnák, NBT-kapcsoló, TNT-kizárás, támasz-tudatos visszaépítés (2026-07-22)
- [ ] **Rúna-effekt:** robbanást túlélő láda/kemence/tábla körül rúna-részecskék +
      enchant-hang — nem tűnik bugnak, hogy állva maradt.
- [ ] **tile-entity-explode [ki] bekapcsolva:** láda robbanáskor kirobban, tartalma NEM
      szóródik ki, visszaépüléskor a TELJES tartalom + tábla-szöveg visszatér (restart
      közben is — block-regen.yml extra mező). Fej/zászló/spawner ilyenkor is rúna-védett.
- [ ] **TNT-lánc:** több egymás melletti TNT robbanása végigfut (lánc él), de a TNT
      blokkok NEM épülnek vissza — nincs robbanás-hurok, nincs ingyen-TNT.
- [ ] **Törmelék-%:** debris-percent 100-on MINDEN kirobbant blokk repül (nincs darabszám-
      plafon); 40-en kb. minden második-harmadik.
- [ ] **Fizika-lepattanás:** a kirobbant falról fáklya/falitábla NEM droppol — drop nélkül
      tűnik el és a fallal együtt visszaépül (BlockDestroyEvent-út).
- [ ] **Támasz-teszt:** homok/kavics fal + fáklyás fal robbantása → visszaépüléskor SEMMI
      nem esik le és nem pattan le: a gravitációs blokk megvárja az alapját, a fáklya a
      falát (support-grace-seconds [120] után kényszer-visszaépítés).

## Regen 4. kör: teljes tile-entity NBT + rendszer-átvizsgálás (2026-07-22)
- [ ] **Teljes NBT-kör** (tile-entity-explode: true): fej (textúrás!), zászló (mintás),
      spawner, lektorna+könyv, shulker-tartalom, díszcserép robbanása után MINDEN adat
      pontosan visszatér; robbanáskor semmi tartalom nem szóródik ki.
- [ ] **Dupla láda:** a robbanás csak az egyik felet éri → a TÚLÉLŐ fél tartalma
      érintetlen marad; a robbant fél visszaépülve a saját tartalmával tér vissza.
- [ ] **Dedupe:** robbanás + fizika-esemény ugyanarra a blokkra → a blokk EGYSZER áll
      vissza (nincs dupla sor-bejegyzés).

## Regen 5. kör: zóna-mátrix + hurok-fék (tulaj-kérés, 2026-07-22)
- [ ] **Zóna-mátrix:** regen.zones.wilderness true-ra állítva a vadonban is gyógyul a
      creeper-kráter/Wither-rombolás (drop nélkül); false-on vanília. Frakcióföld
      (faction) külön kulcson. A kézi bányászás a vadonban MINDIG vanília marad.
- [ ] **Regen nélküli védett zóna** (pl. zones.capital: false): a régi teljes
      robbanás-tiltás él ott — a mátrix zónánként vált a három mód közt
      (vanília / tiltás / regen).
- [ ] **Hurok-fék:** fáklya vízfolyásba regen-elve max-recaptures [3] után nem épül
      újra (a rendszer elengedi) — nincs végtelen capture-restore kör;
      recapture-window-seconds [600] után a számláló nullázódik.

## Regen 6. kör: fizika-pajzs a visszaépített blokkokon (tulaj-kérés, 2026-07-22)
- [ ] **Pajzs-teszt:** vízfolyásba visszaépült fáklya physics-shield-seconds [300] ideig
      NEM mosódik el (a víz be sem folyik a blokkjába), homok nem esik le, semmi nem
      pattan le; a pajzs lejárta után a vanília fizika visszaáll.
- [ ] **Hurok-fék mint védőháló:** pajzzsal a víz-elmosás hurok el sem indul; 0-ra
      állított pajzsnál (physics-shield-seconds: 0) a max-recaptures fék fogja meg.
- [ ] **Terhelés:** pajzsolt pozíció nélkül a BlockPhysicsEvent-handler gyors-úton
      azonnal visszatér — üresjáratban nem mérhető többlet.

## Regen 7. kör: kráter-pajzs + pajzs fő-kapcsoló (tulaj-kérés, 2026-07-22)
- [ ] **Kráter-pajzs:** robbanás után a lyukba NEM folyik be a szomszédos víz, a
      perem-homok nem omlik be — a kráter a visszaépülésig érintetlen marad; a
      visszaépült blokkok utána physics-shield-seconds-ig pajzsoltak.
- [ ] **Fő-kapcsoló:** physics-shield-enabled: false → a régi megoldás él (fizika
      normál, csak a max-recaptures hurok-fék véd); true → teljes pajzs.
- [ ] **Restart-teszt:** kráteres állapotban restart → a kráter-pozíciók a betöltés
      után is pajzsoltak (a sorból épül vissza a pajzs-lista).

## Regen 8. kör: irány-szelektív víz-szabály (tulaj-döntés, 2026-07-22)
- [ ] **Befolyás él:** vízparti fal kirobbantva → a víz LÁTVÁNYOSAN beömlik a résen és
      megül a kráterben (nem lebeg a peremen).
- [ ] **Tovább-terjedés tiltva:** a kráterben álló víz NEM folyik tovább a fal mögötti
      belső terekbe — az épület belseje szárazon marad.
- [ ] **Kiszorítás:** a visszaépülő fal blokkonként kiszorítja a beállt vizet; záródás
      után a külső víz a falnak feszül, a világ állapota a robbanás előtti.
- [ ] **Fáklya-védelem:** vizes kráterbe visszaépült fáklya az utó-pajzs alatt nem
      mosódik el, mire a pajzs lejár, a fal már körbeérte.
- [ ] **Perem-homok:** a kráterbe hulló homok/kavics ITEMKÉNT esik le (felvehető),
      nem tűnik el némán a visszaépítéskor.

## Regen 9. kör: review-javítások (2 agent leletei, 2026-07-22)
- [ ] **Befalazás-védelem:** a kráterben álló játékosra NEM épül rá a fal (a blokk
      várakozik, amíg el nem mozdul) — nincs fulladás-csapda.
- [ ] **Duplikált handler javítva:** mob-grief + regen egyetlen EntityChangeBlock
      handlerben (enderman blokk-rakás továbbra is tiltva védett zónában).
- [ ] **Zóna-mátrix az ostrom-bontásra is hat** (zones.<típus>: false → ott a bontás
      a régi tiltásra esik vissza).
- [ ] **Hurok-fék csak fizikára:** a szándékos ismételt ostrom-bontás nem tesz
      blokkot "sérthetetlenné"; dupla-törmelék nincs claim+territórium átfedésnél.
- [ ] **Terhelés-javítások:** O(1) dedupe + periodikus pajzs/history-seprés — 100 TNT-s
      lánc alatt mérendő a TPS (playtest-pont).

## Szentségtelen (Unholy) DK-spec + DARK-kaszt kapcsoló (tulaj-kérés, 2026-07-22)
- [ ] **Unholy spec:** csak Kitaszított + bűnös veheti fel (Nekromanta-minta);
      spelljei 25-45: Gennyes Csapás, Járvány, Ghúl-szolga (2 husk-minion),
      Halálörvény (vér-áras), A Holtak Szorítása, Ragály, A Holtak Serege (6 minion
      ulti) + Szentségtelen Kötelék tier3 talent.
- [ ] **Vezeklés-reset:** a vezeklés-lánc záró jutalma a DARK-kapus specet
      (Nekromanta/Szentségtelen) automatikusan elengedi (a kaszt marad, üzenettel);
      nem-DARK-spec játékosnál nem történik semmi.
- [ ] **classes.death-knight.dark-only [false]:** true-ra állítva ÚJ Halállovagot
      csak Kitaszított választhat (magyarázó üzenet); meglévő nem-DARK DK-t nem érint;
      false-on a régi működés.

## DARK-spec csomag: állandó petek + 3 új spec (tulaj-kérés, 2026-07-22)
- [ ] **Állandó ghúl (Unholy):** a Sötét Paktum-tekerccsel élőhalott (zombi/csontváz/
      phantom) fogható be tartós társnak — szintez, átnevezhető, stance-váltós, mint a
      Vadmester/Nekromanta petje; nem-élőhalott NEM fogható.
- [ ] **Warlock démon-familiáris:** kaszt-szintű (mindkét spec): vex/boszorka/blaze/
      magma-kocka/zombified piglin fogható tartós társnak a tekerccsel.
- [ ] **Csontpap (PRIEST, DARK+bűnös):** Csontforrasztás (csoport-regen aura),
      Szívó Sugár (drain), Csont-oltalom (ellenállás-áldás), Királynő Siráma
      (AoE+gyengítés), Vér-tized (SAJÁT HP-ból csoport-regen!), Sír Csendje,
      Utolsó Kenet (nagy Absorption-aura ulti) + Sírfátyol talent.
- [ ] **Pestishozó (ASSASSIN, DARK+bűnös):** Pestis-vágás→Fekete Halál DoT-íve
      (méreg/wither halmozás, Miazma lassítás) + Pestis-mesterség talent.
- [ ] **Demonológus (WARLOCK, DARK+bűnös):** Démontűz, Imp-raj (3 vex), Magma-szolga,
      Démonbőr, Káosz-láng, Áldozati Paktum (vér-áras burst), A Légió (5 vex ulti)
      + Légió-paktum talent.
- [ ] **Kapuk:** mindhárom spec csak Kitaszított+bűnösként vehető fel; vezeklésnél
      automatikusan elvész (spec-reset üzenettel).

## Pet-review P0 javítások (2026-07-22)
- [ ] **/pet item routing:** Szentségtelen és Boszorkánymester a Sötét Paktum-tekercset
      kapja (nem a beast-pórázt) — a befogás mindkét új szerepnek működik.
- [ ] **Tiltólista:** Warden/Ravager/Vasgólem/Elder Guardian/Wither/Sárkány egyik
      szereppel sem fogható be (pets.capture.blocklist, élő kulcs).
- [ ] **Lopás-védelem:** más játékos vanília úton szelídített állata (farkas, ló, macska)
      NEM fogható be idegen befogóval; a sajátod igen.

## Rituálé-idézés: lore-hű állandó társak (tulaj-döntés, 2026-07-22)
- [ ] **Modell-váltás:** a Szentségtelen és a Boszorkánymester NEM befog, hanem IDÉZ —
      a tekercs nekik nem működik, a /pet item útmutatót ad; Vadmester/Nekromanta
      befogása változatlan.
- [ ] **Kellék-beszerzés (kihívás):** Nyughatatlan Szív (ITEM_MODEL `capture_heart`) élőhalott-killből
      3% (csak Szentségtelennek esik), Démon-pecsét (ITEM_MODEL `capture_seal`) boszorka/illager-killből
      6% (csak Boszorkánymesternek).
- [ ] **Rituálé:** kellékkel a kézben jobb-klikk, CSAK ÉJJEL (night-only) — lélek-
      részecskék + hang; a kellék elfogy; nappal hibaüzenet.
- [ ] **Forma-progresszió:** pet-szint 1-14: Ghúl (husk) / Imp (vex); 15+: Csontszolga
      (wither skeleton) / Tűz-démon (blaze); 25+: Förtelem (zoglin) / Magma-behemót —
      a magasabb forma ÚJ rituálét (új kelléket) kér.
- [ ] **Idézett-prémium:** az idézett társ +5 bónusz-szintnyi statot kap (summon.
      bonus-levels) a befogott társakhoz képest; befogásra váltásnál a prémium elvész.
- [ ] **DARK-kilépés spec-reset:** kifizetős DARK→más váltásnál és /faction leave-nél
      a sötét spec automatikusan elveszik (üzenettel) — az "örök paktum" specet nem
      lehet kivinni; MagmaCube-minion halálakor NEM hasad gazdátlan kockákra;
      a dark-only DK-kapu a JobManagerben is él (minden hívási út védett).

## Pet-rework: GUI, állásmódok, Társvért, halál-cooldown (tulaj-kérés, 2026-07-22)
- [ ] **/pet GUI:** üres /pet (vagy /menu → Társ) vezérlőpultot nyit — infó-csempe
      (név/szint/XP/állapot), Idézés, Elbocsátás, Átnevezés-súgó, 3 állásmód-gomb
      (az aktuális világít), Társvért-státusz, Bezárás; minden kattintás a /pet
      alparancsokra delegál és frissíti a GUI-t.
- [ ] **Állásmód (gazda-PDC):** /pet stance aktiv|passziv|marad + sunyítás+jobb katt
      a társon (ciklikus váltás) + GUI-gombok — mindhárom út ugyanazt állítja;
      Passzívban sosem harcol, Maradj-ban helyben vár (világváltásnál utánad jön);
      a spell-idézett minionok állásmódja ettől független (entitás-PDC).
- [ ] **Halál-cooldown:** a társ halála után 120 mp-ig (pets.companion.death-respawn-
      seconds) nem idézhető újra — a GUI mutatja a hátralévő időt; 0 = kikapcsolva.
- [ ] **Társvért (ITEM_MODEL `capture_heart`/társ-felszerelés ikon):** 1% eséllyel esik szörny-killből társ-tartó kasztnak
      (csak amíg nincs felszerelve); jobb katt a SAJÁT kint lévő társon → +4 páncél
      +4 max-HP (pets.equipment.*), elfogy; újraidézés után is a társon marad;
      idegen mobra/másik játékos társára nem tehető fel.
- [ ] **Talent-átszállás:** a gazda max-health talentjeinek fele (pets.talent-health-
      share) a companionra is rászáll idézéskor (a minionokkal azonos arány).
- [ ] **Kijelentkezés-takarítás:** logoutkor a kint lévő társ/minionok despawnolnak
      (PDC-ből újraidézhető) — nem marad árva persistent entitás; belépés után
      /pet summon visszahozza.
- [ ] **Aggro-szűrő:** a társ/minion célzása játékosra és falusira csak parancsból
      (assist/defend) mehet — magától sosem támad rájuk; chunk-betöltéskor a
      gazdátlan (offline tulaj) minionok despawnolnak.

## DARK-spec hangolás + iskolák + katalógus-rend (2026-07-22)
- [ ] **Fekete Halál újrahangolva:** 150 XP (100 helyett) és 6 blokk sugár (8 helyett)
      — spells-balance.yml black_death entry a kóddal egyezik.
- [ ] **Spell-id ütközés javítva:** a Pestishozó Fertőzése plague_contagion, a
      Szentségtelen Halálörvénye unholy_coil id-t kapott — a Méregkeverő contagion
      és a DK-alap death_coil spellje változatlanul működik (regresszió-teszt!).
- [ ] **Iskola-felülírások:** Szentségtelen/Csontpap sebző spelljei árny-iskola,
      a pestis/méreg spellek természet-iskola (spells.yml by-spell) — az iskola-
      bónuszok ennek megfelelően számolnak.
- [ ] **Balansz-entryk:** mind a 28 új DARK-spell (4 spec + 5 idézés) explicit
      spells-balance.yml entryvel — /icesmp config set spell-balance.<id>.<kulcs>
      élőben hangol.

## P2 1. csomag: kis kód-patchek (tulaj-kérés, 2026-07-22)
- [ ] **Örök paktum kapu:** paktumos DARK-játékos se /faction join <más>, se /faction
      leave úton nem tud kilépni (üzenet: vezeklés az egyetlen út); a vezeklés-lánc
      után (breakDarkPact) a kilépés újra működik, és a sötét spec ilyenkor is
      elveszik a már élő reset-úton.
- [ ] **Vérdíj bűn-mosás fék:** ugyanarra az áldozatra 12 órán belül a második
      kivégzés se veretet, se bűn-nullázást nem ad (üzenet jelzi); az első
      kivégzés változatlanul fizet ÉS nulláz.
- [ ] **admin.item jog:** az icesmp.admin.all mostantól a /iceitem-et is megadja;
      LuckPerms-ben az icesmp.admin.item külön is kiadható.
- [ ] **Quest-dialógusok:** a merchant_choice a vándor kereskedőnél, a
      forest_cleansing az erdei véneknél vehető fel NPC-re kattintva — a
      dialógus-sorok ténylegesen megjelennek.
- [ ] **Vásárjárás átdolgozva:** a NEUTRAL heti Vásárjárás mostantól szállítás
      (25 kenyér/smaragd a vándor kereskedőnek), nem a Vásár hete duplikátuma.
- [ ] **Ascendant talent:** 10 elköltött pont után (requires-spent 9 + maga a
      talent) tiszta szintezésből is megvehető az 50. szinten.
- [ ] **Unique-smelt:** unique anyag (pl. mélységi borostyán) kemencében NEM olvad
      át vanília itemmé — a smelt elakad rajta.
- [ ] **Claim entitás-védelem:** idegen claimben állat/villager/armor-stand nem
      ölhető (közvetlen ütés, nyíl, szelídített állat útján sem), item-frame nem
      forgatható/fosztható, mob nem vödrözhető; szörny-ölés és PvP változatlan;
      megbízott (trust) mindent tehet.
- [ ] **Lélekkő napi keret:** 50 lélekkő/nap/játékos (élő kulcs:
      currency.soul-drop.daily-cap; 0 = korlátlan); spawner-mob eleve nem ejt
      (skálázatlan → min-mob-level alatt).
- [ ] **Parkour-fék:** pályánként napi 3 jutalmazott futam (parkour.daily-reward-limit,
      élő kulcs) — fölötte a futam és a quest-haladás számít, veret nem jár (üzenet);
      gyöngy/refő-teleport, elytra-nyitás és riptide futás közben MEGSZAKÍTJA a futamot.
- [ ] **Doksi-szinkron:** README (DARK-belépés bűn-alapú, 6 zónatípus, 400+ recept),
      01-kezdes (veret-átadás a kikapcsolt /currency pay helyett), 02-frakciok
      (paktum-zár), 03-valuta (lélekkő-keret), 14-parancsok (territory-típusok).

## A-csomag: tulaj-döntések implementálása (2026-07-22)
- [ ] **End-zár:** stronghold-keretbe nem tehető szem (üzenet), égő End-portálon nem
      lehet átlépni; zóna-bypass joggal igen; end-portal.allow=true visszaadja a vaníliát.
- [ ] **Combat-tag:** PvP-találatra 12 mp jelölés (actionbar); jelölt játékos a védett
      zónában IS sebezhető (üldözés végigvihető), a komp elutasítja; logout törli;
      12 mp harc nélkül lejár és a zóna-védelem visszaáll.
- [ ] **Min-harc-idő:** hadi-ablak pont és párbaj bűn-tisztulás csak akkor jár, ha a
      pár első összecsapása óta ≥20 mp telt el — egy-ütéses ölés üzenetet ad, pontot nem.
- [ ] **CC-DR:** ugyanazon játékosra 15 mp-en belül a 2. fagyasztás/erős lassítás fele,
      a 3. negyed ideig tart, a 4.-től immun; mobokra nem vonatkozik; spells.cc-dr élő.
- [ ] **Szezon-lépcső:** szezonzáráskor teljes végeredmény-broadcast; 2. hely fél,
      3. hely negyed kassza-jutalom (runner-up-ratios, élő kulcs).
- [ ] **Árfolyam:** reference-supply 2500 (50 fős kalibráció); napi váltási keret 200
      forrás-veret (daily-limit, 0=ki) — túllépésnél üzenet, a keret másnap nullázódik.
- [ ] **Kultista-loot:** portya/hírvivő/rítus-hívő leölve 35% eséllyel dob a
      cultists.loot táblából (árnyékpor/emlékszilánk/ritkán Suttogás-meghívó).
- [ ] **Kazamata-loot:** /territory dungeonchest regisztrált láda fejenként hetente
      fosztható (vanília nyitás cancel, virtuális loot, actionbar-visszajelzés);
      /territory dungeonboss kijelölt mini-boss belépéskor ébred (24h respawn),
      halálakor boss-tábla loot; kazamata-mob 8% bónusz-drop; minden kulcs élő.

## B-csomag: balansz-hangolások (tulaj-jóváhagyás, 2026-07-22)
- [ ] **Vér-spellek:** garrote/bloodbath/phantom_grip/soul_rot +1 sebzés (spells-balance).
- [ ] **Energia-regen:** orgyilkos/szerzetes 7/mp, íjász 6/mp — a tár most tényleg kifogyhat.
- [ ] **Tier2 talent-párok:** kasztonként eltérő effekt/szám (pl. Varázsló: spell-power vs
      HP; Íjász: sebzés vs mozgás; Boszorkánymester: erős spell-power vs kis HP) — a
      választás már nem álválasztás; respec után újraoszthatók.
- [ ] **Recept-szintek:** 6 recept a hozzávalója forrás-szintjére emelve (tutaj_keszlet 24,
      vandor_lepenye/fuszeres_nyulragu 22, kivonatos_csontliszt_bomba/kemenyfa_nyelkoteg 24,
      runapor_finomitas 26); a checker 7. szabálya mostantól FAIL-t ad ilyen driftre.
- [ ] **Régi mesterművek KI:** a 8 kódba égetett recept (Tárnász Csákány…) nem craftolható
      (legacy-masterworks=false); a meglévő példányok működnek; a katalógus-párok élnek.
- [ ] **DARK szárny-rituálé:** csontblokk 16 + lélekhomok 16 + gyémántblokk 1 — a
      többi szárnnyal azonos árszint.
- [ ] **Kaszt-megerősítés:** első kaszt-kattintás figyelmeztet, 30 mp-en belül megismételve
      választ; a DK dark-only kapu és a setPrimaryJob-elutasítás változatlan.
- [ ] **Király-lejárat:** term-days után a trón automatikusan megüresedik (broadcast),
      új szavazás indul; dethrone-on-expiry=false visszaadja a régi viselkedést.
- [ ] **Tanácsi keret:** 3 tanácstag EGYÜTT max 400/nap (közös számláló) — a 2. tanácstag
      kivétje már az elsőét is terheli.
- [ ] **Régész-megosztás:** kiásáskor a 24 blokkon belül állók 50% eséllyel (max 3 fő)
      kisebb leletet kapnak; a lelőhely-spawn 250 blokk szórású.
- [ ] **Kazamata csoport-kulcs:** egy kulcs a 16 blokkon belüli párttagoknak is nyit;
      friss pecsétes tag nem kap új passzt.
- [ ] **Suttogó tanú-gyanú:** éjszakai élőhalott-békesség kívülálló szemtanú előtt 2%
      eséllyel +1 gyanú.
- [ ] **Közösségi célok:** szezonváltáskor nullázódnak (broadcast a szezonzáró részeként).
- [ ] **Honvágy:** a debuff 45 mp Éhség (10 helyett) — érezhető, de nem büntető.
- [ ] **Rontás-góc:** 75 percenként 35% — kb. kétszer olyan gyakori, mint eddig.

## Üzembiztonsági kör: NPC-tartalék + HUD/tablist perf + tick-szétterítés (2026-07-22)
- [ ] **NPC-tartalék-út:** FancyNpcs NÉLKÜL a /quest talk <npc-név> teljesíti a
      TALK_TO_NPC/DELIVER_ITEMS célokat és felveszi a giver-npc questeket (dialógussal);
      híddal a parancs udvarias elutasítást ad (quest-npc-fallback.always=true felülírja);
      tab-complete a quest-NPC neveket ajánlja.
- [ ] **Dialógus parancsos felvételkor:** /quest accept után a give-dialógus lejátszódik
      (merchant_choice elágazó szövege parancsból is látszik).
- [ ] **Induláskori NPC-őr:** FancyNpcs hiányában WARN a logban a quest-NPC-számmal;
      híddal 60 mp után létezés-ellenőrzés — a hiányzó NPC-k név szerint a logban.
- [ ] **Tablist-perf:** a kilépő játékos team/score-bejegyzése legkésőbb ~5 mp-en belül
      (sweep-every-refresh) tűnik el; a nevek/ping/színek frissítése azonnali marad;
      60 fős terhelésnél a tablist-tick ideje mérendő (cél: nincs tüske).
- [ ] **HUD diff-cache:** változatlan oldalsáv-sor nem generál csomagot (packet-sniff
      vagy timings); sor-változás (HP, valuta) azonnal átmegy; /hud szekció-váltásnál
      és sidebar ki/be-nél nincs beragadt sor.
- [ ] **Világesemény-szétterítés:** a ~33 rendszer-tick 4-es csokrokban, az intervallum
      első felére terítve fut (timings: nincs 60 mp-enkénti globál-tüske); egy hibázó
      manager WARN-t ír, de a többi csokor lefut.
- [ ] **Pet-tick üresjárat-fék:** társ nélküli játékosokra nem megy scheduler-hop
      (timings); idézésnél azonnal indul a követés, elbocsátás/halál/logout után leáll.

## HP/sebzés-skála (A17 1. ütem, tulaj-kérés, 2026-07-22)
> ⚠️ ALAPBÓL KIKAPCSOLVA (tulaj-döntés): a teszthez előbb health.enabled=true +
> scale-heals=true (config-GUI "HP-rendszer" kategória vagy /icesmp config set).
- [ ] **Kaszt-HP-profil:** kaszt-választás után a max HP a szinttel nő (pl. Harcos 25.
      szint: 20+15=35; Varázsló 25. szint: 26.25); szint-lépés után legkésőbb 2 mp-en
      belül frissül; kaszt admin-resetnél visszaáll 20-ra; talent-HP ezen felül adódik.
- [ ] **Szívsor-normalizálás:** a kijelzés minden max HP-nál 10 szív (a szív "értéke"
      nő); health.display.normalize=false visszaadja a vanília szív-duzzadást;
      abszorpció-szívek továbbra is látszanak.
- [ ] **Harcon kívüli regen:** sebzés-kontaktus (adott VAGY kapott, PvE is) után 8 mp-ig
      nincs regen; utána 2 mp-enként max-HP 5%; 3 comb (6 food) alatt nem fut; harc
      közben a vanília étel-regen változatlan; a regen a sebzés-számokat nem triggereli.
- [ ] **Gyógyítás-skálázás:** 40 HP-s tanknál a 4-es heal 8-at gyógyít (maxHP/20 = 2.0
      szorzó, plafon 2.5); 20 HP-s frissnél változatlan; scale-heals=false kikapcsolja.
- [ ] **PvP TTK mérés:** 50-es szinteken a kill-idő érezhetően hosszabb (cél: nem
      2-3 spell); Fagylovag-kombó a CC-DR + nagyobb tavak mellett mérendő.
- [ ] **PvE mérés:** a nagyobb játékos-HP mellett a magas szintű zónák nehézsége
      (mob damage-per-level 1.0) újraértékelendő — szükség esetén emelés.
- [ ] **Admin-GUI:** /icesmp config menü új "HP-rendszer" kategória — kapcsolók élőben.

## WoW-mód 2. ütem: sebzés-profilok + Varázserő gear-affix (tulaj-kérés, 2026-07-22)
> ⚠️ ALAPBÓL KIKAPCSOLVA (health.enabled=false) — a teszthez kapcsold be.
- [ ] **Kézi sebzés-skála:** 50-es Harcos gyémántkarddal +5 sebzést üt a szint-bónuszból
      (attribútum, F3-mal ellenőrizhető); kikapcsolásnál élőben lekerül; casternél +1.5.
- [ ] **Lövedék-skála:** a nyíl/számszeríj/szigony találata +szint-bónusz sebzést kap
      (Íjász 50: +4); a bónusz a lövéskori cache-ből jön (kaszt-váltás után max 2 mp).
- [ ] **Varázserő-affix:** WoW-módban a loot/craft gearre sorsolódhat (páncél ÉS fegyver;
      "+N% Varázserő" lore); a spell-sebzés ennyivel nő (a dynamic-scaling 50%-plafonja
      alatt); Ócska-rollon negatív lehet; KIKAPCSOLT módban NEM sorsolódik, de a már
      létező példány visszakapcsoláskor újra hat.
- [ ] **Halmozódás-ellenőrzés:** szint% + talent + Varázserő együtt sem lépi át a
      spells.dynamic-scaling.max-bonus-percent plafont; a teljes szorzat a
      total-power-cap (1.75) alatt marad.

## Bootstrap-kiaknázás: 5 új iskola-counter + védőháló (tulaj-kérés, 2026-07-22)
- [ ] **Védőháló:** a bootstrap handler-hibái logolódnak, de a szerver elindul
      (szimulálható: ideiglenesen rossz kulccsal); a spellek vanília sebzésre esnek
      vissza, a signature-stamp kihagyja a hiányzó enchantot.
- [ ] **5 új counter-enchant:** Éj-fátyol (szent), Árnyűző (árnyék), Méregfojtó
      (természet), Viharfogó (vihar), Káosz-zabla (káosz) — enchanter 38-as
      tervrajz-receptek (ITEM_MODEL manifestben), enchantelt könyv, üllőn mellvértre; a
      megfelelő iskola spell-sebzését szintenként 10%-kal csökkenti (magic-resist.
      school-per-level), a Rúnavérttel a 60% plafonig összeadódik.
- [ ] **Counter-exkluzivitás:** két különböző iskola-counter NEM kerülhet egy
      páncélra üllőn (az eredmény eltűnik); Rúnavért + egy counter együtt mehet;
      a meglévő Fagypáncél/Főnixtoll párra is érvényes.
- [ ] **DARK-specek ellen:** Árnyűző mellvértben a Szentségtelen/Csontpap árny-spelljei
      ~10%-kal kisebbet ütnek; Méregfojtó a Pestishozó természet-spelljei ellen.
- [ ] **Enchant-asztal tiszta:** az új enchantok sem jönnek enchant-asztalról.

## Mélyaudit-kör: lore-kánon, /lore Radicora, escort-útvonal (2026-07-26)
- [ ] **`/lore radicora` VALÓDI szócikk:** `/lore radicora` (aliasok: `o-caldestera`,
      `ocaldestera`, `gyokerek`) Radicora/Ó-Caldestera 6 sorát adja — NEM a `menedek`
      általános frakció-összefoglalóját. `/lore` usage-sora és a tab-complete is
      felsorolja. Ismeretlen téma (`/lore izelabda`) továbbra is a usage-sort adja,
      nem csendben más szócikket.
- [ ] **`/lore fa` eredet-javítás:** a Fa „Asterlayna lelkének utolsó
      csillagszilánkjából kelt ki" — nem „a teste fölött nőtt" (a kódex szerint a
      teste semmivé foszlott).
- [ ] **`/lore menedek` földrajz:** Ryanora ősi szíve a Fa tövében (Radicora), a mai
      főváros Caldestera a szoroson túl, komppal — a két hely nem keverhető össze.
- [ ] **Tábortűz kánon-hűség:** az Asterlayna-leszállás sora már szóbeszédként szól
      („Van, aki azt tartja…") és nem írja át az Első Háború okát; a DARK Királyok
      Átka-sor sem azt mondja, hogy a Felsőkre nem hat, hanem hogy a sírban nem
      tarthatja őket, viszont a korona szorít, míg viselik.
- [ ] **Escort-útvonal spawn-guard:** `/events escort` — a konvoj nem CSAK védelem-mentes
      pontról indul, hanem az útvonala mintavett pontjai (25/50/75/100%) is védelem-
      mentesek. Teszt: vegyél fel egy claimet/territóriumot az anchor mellé úgy, hogy a
      lehetséges célok többsége bele essen → a konvoj vagy más irányba indul, vagy
      (ha mind a 8 próbált irány védett) ebben az intervallumban nem indul el egy sem.
      A `world-events.spawn-rules.escort.*` mátrix-sor kikapcsolásával a korlát megszűnik.
- [ ] **Ereklye-flavor:** a legendás flavor-sorok között NEM szerepel egyediség-állítás
      („Egyetlen darab létezik belőle") — a ritkaság ismételhetően generálódik, egyediséget
      csak a nevesített, ID-zett relikvia állít.
- [ ] **Gépi őrök (nem ingame, de a körhöz tartozik):** `python3 scripts/check_consistency.py`
      FAIL-el, ha (a) egy doksi-szám elszakad a mért értéktől (Java-fájl/manager/store/
      tábortűz-mese/Idegen-sor), (b) az ARCHITECTURE.md csomagtérkép fájlszáma driftel,
      (c) egy `/lore` téma tab-complete/szócikk/usage-sor hármasa szétcsúszik.

## Mélyaudit-kör: állapotgépek (spell-provenancia, frakció, király, raid) 2026-07-26
- [ ] **Spec-reset nem halmoz (HIGH-19):** válassz kaszt-specet, szintezz addig, hogy a spec
      spelljei feloldódjanak (`/spell lista`), majd `/spec respec class` → a spec spelljei
      ELTŰNNEK a listából. Válassz másik specet: csak AZ ő spelljei jönnek. A kaszt-szintből
      járó spellek MINDIG megmaradnak.
- [ ] **Talent-visszavonás forrásérzékeny (MED-07):** ha egy talent olyan spellt ad, amit a
      kaszt-szint IS ad, a talent elvesztésekor (spec-váltás → `refundUnavailableTalents`)
      a spell MEGMARAD. Ha csak a talent adta, eltűnik.
- [ ] **Admin-feloldás sérthetetlen:** `/job admin unlockallskills <játékos>` után egy
      spec-respec NEM veszi el a spelleket (ADMIN forrás).
- [ ] **Régi (provenancia előtti) mentés:** olyan játékossal, aki a frissítés ELŐTT halmozott
      össze két spec spellkészletét, az első `/spec respec class` letakarítja a régi spec
      spelljeit is (a backfill visszamenőleg forráshoz kötötte őket).
- [ ] **Frakció-kilépés nem kerülőút (HIGH-17):** RED-ként `/faction leave` (fizetős, kapus)
      → utána `/faction join blue` a világ közepén: az **elutasítás** a semleges-főváros kapura
      hivatkozik. Szezon-hajrában (`factions.switch.lockout-final-days` ablakban) a leave utáni
      join is TILOS. Új játékos első `/faction join`-ja továbbra is ingyenes és kapu nélküli.
- [ ] **Király-mandátum a koronázástól (HIGH-18):** `factions.kings.term-days: 1` +
      szavazás a ciklus vége felé → a friss király mandátuma a koronázástól számol (nem esik
      le azonnal), a korona-átok szintje 0-ról indul (`/faction king info`), a szavazólap
      kiürül, és a `crowned` haladás-bejegyzés MEGJÖN (eddig csak admin-koronázásnál jött).
      Restart után a mandátum nem indul újra (reign-start a kings.yml-ben).
- [ ] **Raid-nevezés csak felkészülésben (HIGH-16):** raid hirdetés → várd meg a harci szakasz
      kezdetét → `/faction raid join` → „a harci szakasz már megkezdődött" hibát ad.
- [ ] **Raid-díj visszatérítés restartnál (HIGH-16):** hirdess raidet (kassza csökken a
      nevezési díjjal), majd állítsd le a szervert → a broadcast a visszatérítést is említi,
      és a frakciókassza visszakapja a díjat (`/faction treasury`).
- [ ] **Raid-cooldown restart-álló (HIGH-16):** raid vége után azonnal restart → a
      `/faction raid start` továbbra is cooldown-hibát ad (raids.yml).
- [ ] **Fegyvertilalom offhandben is (DEEP-MED-10):** offhandbe vett karddal állj be
      Caldesterába → az őrség azt is elrakja. Töltsd tele a hátizsákot (csak az aktív slot
      legyen szabad) → a fegyver NEM kerül vissza a kezedbe; helyette „tele a hátizsákod"
      figyelmeztetést kapsz.
- [ ] **Párbaj heti számláló (DEEP-MED-11):** `honor-duel.weekly-limit: 2` — használd el a
      heti kettőt, várd meg a hét-váltást (vagy állítsd át a rendszeridőt) → az új hét első
      párbaja elfogadható. Visszautasítás (`/parbaj nem`) után a kihívó AZONNAL kihívhatja
      újra ugyanazt a játékost (nincs „duel-pending" a lejáratig).
- [ ] **Közösségi cél maradéka átvisz (DEEP-LOW-02):** állíts célt 10-re, adj be 8-at, majd
      egy 64-es stacket → a cél teljesül, és a következő ciklus NEM 0-ról, hanem a maradékkal
      indul (`/celok`); egy hozzájárulás legfeljebb 3 ciklust zár le.
- [ ] **Függőleges biome-progressz (DEEP-LOW-01):** barlang-biome quest (`EXPLORE_BIOME`,
      pl. lush_caves) — ásd le magad EGY oszlopban a barlang-biome-ba: a progressz megjön
      (eddig csak X/Z elmozdulásra futott az ellenőrzés).
- [ ] **AFK-kikapcsolás takarít (DEEP-LOW-03):** állj AFK-zónába (bossbar látszik), majd
      `/icesmp config set afk.enabled false` → a bossbar eltűnik, a zóna-időzítő nullázódik,
      és az AFK-státusz megszűnik (nem ragad bent).
- [ ] **Relikvia keep-mód recovery (DEEP-HIGH-07):** `relics.passive-death.mode: keep` —
      halj meg passzív relikviával, majd respawn ELŐTT lépj ki. Visszajelentkezés után a
      relikvia a rituálé-oltárnál ÚJRAIDÉZHETŐ (a tulajdon él, nem ragadt be). Ha viszont
      normálisan respawnolsz, a tárgy visszakerül ÉS az oltár NEM ad második példányt.
- [ ] **Gépi őrök:** `python3 scripts/check_consistency.py` FAIL-el, ha (a) egy
      `unlockSpell(...)` forrás nélkül hív, (b) valahol `removeFaction(...)` törli a
      frakció-hozzárendelést, (c) kéz-kiürítést közvetlenül `addItem` követ.

## Mélyaudit-kör: territórium-átadás, advancement-fa, vagyon, WG-híd (2026-07-26)
- [ ] **Raid-foglalás megőrzi a függőleges sávot (DEEP-MED-07):** adj egy frakcióterületnek
      Y-sávot (`/territory sety <id> 60 90`), raideld el → a győztes frakcióé lesz, DE a
      terület Y-sávja MEGMARAD (`/territory info <id>` — eddig teljes világmagasságúvá vált),
      és a poligon-alak/rádiusz/középpont is változatlan; a főváros-státusz nem száll át.
- [ ] **Hiányos advancement-fa KIKAPCSOL (MED-10):** töröld ki EGY advancement-JSON-t a világ
      datapack-könyvtárából, indítsd újra → a log SEVERE sorban NEVESÍTI a hiányzó
      bejegyzés(eke)t, és az advancement-rendszer kikapcsol (nem oszt némán semmit).
      A teljes pack visszaállítása után újraindításnál minden bejegyzés él.
- [ ] **Advancement élő kikapcsolás:** `/icesmp config set advancements.enabled false` →
      innentől nem jön toast/bejegyzés (eddig a betöltött fa tovább osztott);
      visszakapcsolás után `/icesmp reload` kell (a fa regisztrációja indulási).
- [ ] **Egységes vagyon (MED-08):** adj a játékosnak EGYSZERRE többféle valutát (pl. 500
      Parals + 300 Creutzér). A `/toplista vagyon`, a heti krónika/bárdi ének és a
      vagyon-elérés UGYANAZT az összeget mutatja (az összes valuta összegét) — eddig a
      ranglista csak a default valutát nézte.
- [ ] **Claim-átfedés valódi metszés (HIGH-20):** hozz létre WorldGuardban egy KICSI régiót
      (pl. 5×5) úgy, hogy ne essen chunk-középre, és egy másikat eltérő Y-magasságban →
      a `/claim` a környező területre ELUTASÍT (eddig a mintapontok közt átcsúszott).
- [ ] **WG-híd megszakító (HIGH-20):** ha a WG-lekérdezés hibázik (pl. WG-reload közben), a
      log figyelmeztet, a claim-ellenőrzés 60 másodpercig ELUTASÍT (fail-closed), utána a
      híd magától újra próbálkozik — NEM marad végleg kikapcsolva. WorldGuard NÉLKÜL a
      claim továbbra is engedélyezett (nincs régió, amit védeni kell).

## P0 kiadásblokkolók: block-regen napló + piaci tranzakció-napló + kill-kontextus (2026-07-26)
> Ezek a blokk-visszaépítés, a piac és a mob-jutalom TARTÓSSÁGI/szál-helyességi javításai.
> A tesztek nyers leállítást (`kill -9`) is kérnek — külön teszt-világon futtasd.

### Block-regen write-ahead napló (CRIT-05)
- [ ] **Tartalom-vesztés:** `territory.protection.regen.tile-entity-explode: true`, védett zónában
      tegyél egy ládába 3 jól felismerhető tárgyat, robbants rá TNT-vel, majd 2 percen belül
      `kill -9` (NEM `/stop`). Újraindítás után a láda visszaépül, PONTOSAN a három tárggyal
      (a duplikáció is hiba).
- [ ] **Örök lyuk:** `regen.delay-seconds: 20`, robbants ki ~30 blokkos falszakaszt, majd 25 s
      múlva — MIKÖZBEN épül vissza — `/stop`. Újraindulás után a fal maradéka is visszaépül.
- [ ] **Napló-hiba fail-safe (NINCS dupe-farm):** `chmod 500 plugins/IceSMP`, majd robbants rá
      egy TELI ládára. A láda sértetlenül él túl (óvó rúna-effekt), a logban napló-hiba.
      **KRITIKUS:** ha a láda MÉGIS visszaépül, egyszer épül vissza — NEM töltődik újra
      másodpercenként (az ismételt világ-mutáció korlátlan tárgy-duplikáció lenne).
- [ ] **Hiányzó világ:** robbants külön világban, `/stop`, nevezd át a világ mappáját, indíts
      újra → világonként EGY warning, a rekordok a block-regen.yml-ben MEGVANNAK. Visszanevezés
      + újraindítás után a blokkok visszaépülnek. `/icesmp reload` után a warning újra jöhet.
- [ ] **Napló-tisztulás:** ~10 blokk kirobbantása, teljes visszaépülés, `/stop` → a
      `block-regen.yml` `pending` szakasza ÜRES, a `block-regen.wal.rotated` NEM létezik.
- [ ] **Sérült checkpoint:** rontsd el a `block-regen.yml`-t, indíts újra → karantén-másolat +
      mentés-tiltás a logban, a robbanások tovább működnek, a `.wal` nő (rekord nem vész el).

### Piaci tranzakció-napló (CRIT-06)
- [ ] **Tiszta út:** `/market sell 100` → a tárgy eltűnik a kézből, a tétel a GUI-ban van, és a
      `market-journal.yml` a művelet UTÁN ÜRES.
- [ ] **Vásárlás:** A listáz 100-ért, B megveszi → B bankjából a kiírt összeg, A-nak a 10% díj
      után 90, a tárgy B-nél, a napló üres.
- [ ] **Crash vásárlás közben:** `kill -9` közvetlenül egy vásárlás után. Újraindítás: VAGY a
      tárgy B-nél van ÉS A pénzt kapott, VAGY a tétel visszaáll és B pénze megvan — köztes
      állapot nincs. **Sosem fordulhat elő, hogy B megkapja a tárgyat és a pénze is visszajön.**
- [ ] **Crash listázás közben:** listázás után azonnal `kill -9`. Belépéskor EGY üzenet, és a
      tárgy PONTOSAN egy helyen van (inventory VAGY piaci tétel).
- [ ] **Két nyitott listázás (tranzakciónkénti jelző):** írásvédett napló mellett próbálj két
      különböző tárgyat listázni, majd oldd fel és indíts újra → MINDKÉT tárgy előkerül
      (egy közös jelző esetén a második felülírta volna az elsőt, és az elveszett volna).
- [ ] **Rejtett tétel nem foglal helyet:** helyreállításra váró (még meg nem erősített) tétel
      NEM számít bele a `market.max-listings-per-player` limitbe, és nem okoz hamis
      „élő licites aukció nem vonható vissza" hibát.
- [ ] **Sérült napló:** szemetet a `market-journal.yml`-be, újraindítás → `/market sell`
      ELUTASÍT érthető magyar üzenettel („a tranzakció-napló most nem írható"), a GUI-vásárlás
      is, az aukció-lezárás halasztódik; pénz és tárgy érintetlen.
- [ ] **Aukció escrow:** A aukciót indít, B licitál, C túllicitál → B visszakapja a zárolt
      licitet, C-től a nagyobb összeg levonva. Nyers leállítás + újraindítás: az egyenlegek
      ugyanezek.

### Kill-kontextus és jutalom-utak (CRIT-07)
- [ ] **Kereszt-régiós ölés:** íjjal/spellel ölj mobot távoli régióban → az erszény/lélekkő/
      kazamata-drop a MOB helyén, a kaszt-XP/lélekszilánk/pet-XP nálad, konzol-hiba NINCS.
- [ ] **Nincs dupla erszény:** rúnás fegyver, `mob-money-drop.chance-percent: 100` + bónusz 50
      → ölésenként PONTOSAN egy erszény.
- [ ] **Kill-szintű retesz:** ugyanaz az ölés két jutalom-csatornán sem fizet kétszer (a retesz
      az áldozat UUID-jéhez kötött, nem a kontextus-példányhoz).
- [ ] **Kijelentkezés a hop előtt:** ölj mobot és AZONNAL lépj ki → nincs konzol-hiba.
- [ ] **Előszűrők:** kreatívban / AFK-ban / spawner-mobra / saját minionra a FAUCET-jutalmak
      nem fizetnek; spawner-mobnál a kaszt-XP és pet-XP IGEN.
- [ ] **Ismert korlát (nyitott):** kereszt-régiós ölésnél a PÁRT-XP megosztás és a Vad Hajsza
      személyes lootja némán elmaradhat (pozíció-olvasás az áldozat szálán, fail-open) — ezt
      külön kör zárja, nem regresszió.

## P0-A…F csomagok: fail-closed perzisztencia és Folia-életciklus (2026-07-26)
> Ezek a csomagok az adatvesztés és a szál-helyesség ellen dolgoznak. Több teszt NYERS
> leállítást (`kill -9`) kér — külön teszt-világon futtasd, és előtte mentsd a `plugins/IceSMP`-t.

### P0-A — sérült állapotfájl fail-closed
- [ ] **Indulás megszakad:** állítsd le a szervert, írj szemetet a `plugins/IceSMP/currency-balances.yml`-be,
      indíts újra → a log SEVERE sorban nevezi a fájlt, karantén-másolat készül, és az IceSMP
      **NEM indul el** (nem fut tovább üres egyenlegekkel). A karantén visszaállítása után indul.
- [ ] **Szemantikai sérülés is fogja:** ép YAML, de negatív egyenleg / nem-UUID kulcs →
      ugyanaz a fail-closed viselkedés (nem csak a parse-hiba számít).
- [ ] **A közösségi cél számlálója is:** negatív `progress` érték a `community-goals.yml`-ben →
      indulás megszakad, nem nullázza némán a célt.

### P0-B — piac/wallet fail-stop
- [ ] **Napló nem írható → a művelet elmarad:** `chmod 500 plugins/IceSMP`, majd `/market sell 100`
      → érthető magyar hibaüzenet („a tranzakció-napló most nem írható"), a tárgy a kézben MARAD.
      Ugyanez a GUI-vásárlásnál. Jogosultság visszaadása után minden működik.
- [ ] **Wallet-commit a napló törlése ELŐTT:** vásárlás közbeni `kill -9` után az egyenleg és a
      tárgy MINDIG egy irányba zárul — sosem fordulhat elő, hogy a vevőnél a tárgy ÉS a pénze is.

### P0-C — block-regen tokenes recovery
- [ ] **Teli láda + kill -9:** védett zónában robbants rá egy 3 felismerhető tárgyat tartalmazó
      ládára, 2 percen belül `kill -9` → újraindítás után a láda PONTOSAN a három tárggyal épül
      vissza (se több — a duplikáció is hiba —, se kevesebb).
- [ ] **Nincs dupe-farm:** írásvédett data-könyvtár mellett a láda **egyszer** épül vissza, NEM
      töltődik újra másodpercenként.

### P0-D — forrás-eseményes gyűjtés + contribution receipt
- [ ] **Ledob–felvesz nem duplázza:** `COLLECT_ITEMS` közösségi célnál dobd el és vedd fel
      ugyanazt a stacket többször → a számláló CSAK egyszer nő.
- [ ] **Olvasztás/horgászat egyszer számít:** kemencéből kivett és kifogott tétel is pontosan
      egyszer könyvelődik, `kill -9` + újraindítás után sem ismétlődik (tartós receipt).

### P0-E — Folia kill-pillanatkép + scheduleres párt-jutalom
- [ ] **Kereszt-régiós párt-XP:** két párttag távoli régiókban; öld meg a mobot úgy, hogy a
      másik tag a megosztási sugáron BELÜL, de MÁSIK régióban legyen → mindkettő megkapja az
      osztott XP-t (korábban a megosztás némán elmaradt), és a konzolon nincs szál-hiba.
- [ ] **Sugáron kívüli tag nem kap:** a sugáron kívüli párttag nem kap részt; a teljes XP a
      gyilkosnál marad (nincs XP-nyomtatás és nincs veszteség).
- [ ] **Offline/kilépő tag:** ölés után azonnal lépjen ki az egyik tag → nincs konzol-hiba, a
      többiek megkapják a részüket (a 4 tickes határidő lezárja az aggregációt).

### P0-F — mulandó entitás életciklus + esemény-watchdog
- [ ] **Karaván-kíséret VÉGIGFUT:** `/events escort` → a konvoj elindul és célba ér (NEM zárul
      le azonnal „a konvoj elesett" üzenettel). Ez a regisztráció-teszt: a liveness fail-closed,
      így regisztráció nélkül az esemény az első tickben elhalna.
- [ ] **Vad Hajsza VÉGIGFUT:** `/events wildhunt` → a fenevad életben marad a lejáratig vagy a
      leöléséig (nem „megszökik" azonnal).
- [ ] **Idegen NPC marad:** `/events stranger` → az NPC a beállított ideig áll, nem tűnik el
      azonnal és nem spawnol újra ismételten.
- [ ] **Halál azonnal felszabadít:** öld meg a Vad Hajsza fenevadát → az esemény azonnal zárul,
      és rögtön indítható új nagy esemény (nem kell megvárni a watchdogot).
- [ ] **Minion-cap azonnal ürül:** idézd meg a maximális minion-számot, öld meg az egyiket →
      1-2 másodpercen belül idézhető új (nem GC-függő).
- [ ] **Watchdog (beragadt esemény):** ha egy esemény `isActive()`-ja beragadna, a
      `world-events.orchestration.max-active-minutes` (alap 60) lejárta után a többi nagy
      esemény ismét indulhat magától — nem kell szerver-újraindítás.

## PR-review javítások: tartósság-bizonyíték és megszakító-gyógyulás (2026-07-26)
- [ ] **Sikertelen playerdata-mentés nem zárja a naplót (P0):** a `MarketManager.persistPlayer`
      hibája után a `market-journal.yml` bejegyzés NYITVA marad (logban figyelmeztetés).
      Teszt: listázz, majd nézd meg a naplót — ha a mentés bukott, a bejegyzés ott van, és a
      következő indulás a jelzőből dönt. **Sosem fordulhat elő, hogy a tétel a lemezen van ÉS a
      tárgy a kézben (dupe), vagy hogy a piac már nem tartozik a tárggyal, de a játékos sem
      mentette el (vesztés).**
- [ ] **Sikertelen pénz-helyreállítás újrapróbálható (P1):** crash után olyan helyreállítással,
      ahol a levonás nem fedezett → a log SEVERE sort ad, a napló-bejegyzés MEGMARAD, és a
      KÖVETKEZŐ indulás újra megpróbálja (eddig véglegesen legitimálódott az eltérő egyenleg).
- [ ] **Sérült market.yml nem dob el tételt (P1):** rontsd el egy tétel valutáját/tárgyát, egy
      eladó-UUID-t, vagy hagyj zárolt licitet licitáló nélkül → az indulás MEGSZAKAD karantén-
      másolattal (eddig némán átugrotta, és a tárgy/escrow elveszett).
- [ ] **WorldGuard-reload után a híd magához tér (P1):** `/wg reload` (vagy a WG plugin
      újratöltése) közben futtass claim-ellenőrzést → a híd hibát logol és 60 mp-re kikapcsol,
      **utána viszont ÚJRA feloldja a WG-hivatkozásokat**, és ismét helyesen válaszol. Nem marad
      hibás a szerver-újraindításig. Amíg hibás, a `/claim` elutasít (fail-closed).
- [ ] **Spell-provenancia migráció szint-helyes (P2):** olyan játékossal, aki egy MAGASABB
      szintű kaszt-spellt talentből/specből kapott meg, a backfill NEM ír rá `BASE` forrást →
      a talent/spec elvesztésekor a spell is elmegy (eddig véglegesen nála maradt). Ellenőrizd
      azt is, hogy idegen kaszt specének táblája nem attribútál (Varázslónak nincs Harcos-spece).
- [ ] **Közösségi jutalom crash-biztos (P1):** állítsd a célt 1-re, teljesítsd, és a kifizetés
      pillanatában `kill -9`. Újraindításkor a log „N függő kifizetés — újrajátszás" sort ad,
      és a kincstár/szezon/buff jutalom MEGÉRKEZIK (eddig a nyugta miatt az esemény nem
      játszódott újra, a jutalom pedig elmaradt). A jutalom nem duplázódik, ha nem volt crash.

## PR-re-review javítások: tanú-megtartás, szigorú betöltés, idempotens kifizetés (2026-07-26)
- [ ] **Commitolt helyreállítás tanúja megmarad:** ha egy commitolt BUY/BID/SETTLE javítása
      nem sikerül (fedezetlen levonás), a bejegyzés nyitva marad, ÉS a tanúja bent marad a
      `market.yml`-ben. Teszt: a következő indulás **előre** (a commitolt célértékre) próbálja
      újra, nem visszaforgat. Ellenőrzés: a `committed-txn` lista tartalmazza a nyitott
      bejegyzés azonosítóját.
- [ ] **Várólistás tárgylista szigorú:** rontsd el egy `pending-deliveries` lista EGY elemét
      (pl. írj bele stringet), indíts újra → az indulás MEGSZAKAD karantén-másolattal.
      Eddig az az egy elem nyomtalanul eltűnt, a lista többi tárgya betöltődött.
- [ ] **Kétirányú licit-invariáns:** licitáló ZÁROLT ÖSSZEG NÉLKÜL, illetve nem véges/negatív
      ár, licit vagy buy-out érték szintén indulás-megszakítást ad.
- [ ] **WG-híd nincs fail-open ablak újra-feloldás közben:** WorldGuard-reload alatt futtass
      párhuzamosan claim-ellenőrzést → **egyetlen** `/claim` sem mehet át azon, hogy a híd
      félig-kész állapotot mutat („nincs régió"). Amíg nem tud válaszolni, elutasít.
- [ ] **Bukott újra-feloldás után is próbálkozik:** ha a WG épp reload közben van és az újra-
      feloldás elbukik, a log újabb 60 mp-es ablakot jelez, és a híd a következő ablakban
      ISMÉT próbál — nem marad hallgatásban a szerver-újraindításig.
- [ ] **Közösségi kifizetés PONTOSAN egyszer (idempotens):** teljesítsd a célt, majd
      `kill -9` a kifizetés közben. Újraindításkor a log „függő kifizetés — újrajátszás" sort
      ad, és a kincstár/szezon-pont **pontosan egyszer** növekszik (se elmaradás, se duplázás).
      Ellenőrzés: `faction-treasury.yml` → `applied-grants`, `season.yml` → `season.applied-grants`
      tartalmazza a `community:<id>:treasury:<FRAKCIÓ>` / `:season:` azonosítót; ismételt
      indítás után az összeg NEM nő tovább.
- [ ] **Részleges kifizetés pótlódik:** ha a kincstár-írás sikerül, a szezon-írás nem (pl.
      írásvédett `season.yml`), a log RÉSZBEN-sikert jelez, a bejegyzés MARAD, és a jogosultság
      visszaadása után az újrajátszás CSAK a szezon-pontot pótolja — a kincstár nem kap újra.
- [ ] **Config-változás nem téríti el a replayt:** hagyj függő kifizetést, majd írd át (vagy
      töröld) a cél `reward-treasury` értékét a configban, és indíts újra → a kifizetés a
      MENTETT pillanatkép szerint történik (a törölt cél jutalma sem veszik el).

## Csodálatos Bingulus — örökös DEV item

Teszt-tulajdonos UUID: `eb80c20f-092a-4d76-bd44-d168c91ea9e2`.

Gyorsítás teszthez:

```text
/icesmp config set dev-items.csodalatos_bingulus.reward-interval-seconds 10
/icesmp reload
/iceitem dev csodalatos_bingulus 1 <tulajdonos>
```

- [ ] A tárgy csak a konfigurált UUID-jű játékosnak adható.
- [ ] Saját inventoryn belül mozgatható, de nem dobható el és nem rakható ládába/ender chestbe.
- [ ] Halál után visszatér; más játékos nem tudja megtartani vagy felvenni.
- [ ] Offline állapotban és a tárgy hiányakor nem halad a jutalomóra.
- [ ] Telt inventorynál a kisorsolt jutalom várakozik, nem esik a földre és nem sorsolódik újra.
- [ ] A vanilla, `unique:`, `recipe:` és `blueprint:` jutalmak működnek.
- [ ] Két másolt Bingulus sem gyorsítja a jutalmazást; a manager egy hiteles példányt hagy meg.
- [ ] Restart után megmarad a rész-progressz, a pending jutalom és a pity-számláló.

## 0. stabilitási fázis — két-régiós Folia ellenőrzés (2026-07-28)

Két teszter álljon egymástól távol (külön régió, ~200+ blokk), egy pedig a jelenség
helyszínén. A konzolt végig figyeld `region`/`scheduler`/`IllegalStateException` stacktrace-re.

- [ ] **Totem régióhatáron:** sámán rakjon totemet régióhatár közelébe úgy, hogy a
      hatósugárba eső mob/játékos a szomszéd régióban áll — a buff/sebzés megérkezik,
      konzol-hiba nélkül. Crash-teszt: aktív totemnél öld meg a szervert (kill -9),
      restart után a totem-állvány NEM marad a világban.
- [ ] **Pet távoli célponton:** a gazdi lövedékkel sebezzen távoli (másik régiós) mobot —
      a pet nem dob hibát; ha a cél átfut a határon, a következő tickben újra felveszi.
- [ ] **Világboss ZONE-special:** a bossnál a zóna-telegráf régióhatáron álló játékossal
      is hibamentes.
- [ ] **Párt-jutalom régióhatáron:** két párttag KÜLÖN régióban, sugáron belül — kincs
      nyitásakor / mob-ölésnél MINDKETTEN kapnak (a korábbi néma kimaradás megszűnt).
- [ ] **Hazatérés-rituálé kudarca:** érvénytelen fővárosnál / tiltott teleportnál a
      2 ender pearl és a cooldown MEGMARAD, hibaüzenettel; sikeres útnál egyszer fogy.
- [ ] **Reload-teljesség:** recept-ár módosítás + `/icesmp reload` → a recept-könyv az
      új árat mutatja restart nélkül; `spell-vfx.enabled: false` + reload → a spell-VFX
      azonnal eltűnik.
- [ ] **Config-őrök:** `/icesmp config set` NaN-ra hibatűrő (nem lesz ingyen bolt-item);
      egy bemásolt `zz-backup.yml` reload után warningot ad és NEM ír felül kulcsot.
- [ ] **Sérült állapotfájl:** egy kézzel elrontott UUID a factions.yml-ben → az indulás
      MEGÁLL, a fájl karanténba kerül, semmi nem íródik felül.
- [ ] **Gazdasági crash-teszt (H-ECON-001 — a WAL-kör átvételi tesztje, addig VÁRHATÓAN BUKIK):** `/bank kivet 100` után AZONNAL öld meg a
      szervert (kill -9) — restart után az egyenleg a levont érték, dupla pénz nincs
      (a veret a crash miatt veszhet el, de nem duplázódik). Ugyanez befizetésre:
      a beadott veret vagy a számlán van, vagy visszakerült — sosem tűnik el nyomtalanul.
- [ ] **Fizetős claim crash-teszt:** claim-vásárlás után azonnali kill — restart után a
      levonás ÉS a claim együtt van meg (vagy együtt hiányzik), félkész állapot nincs.

## Natív moderáció — replacement scope (2026-07-28)

- [x] **AUTOMATED:** `./gradlew moderationRegressionTest` — ledger restart/expiry/revocation/history/invariánsok; invsee restart snapshot, count-preserving single-claim, claim utáni törlés, corrupt/partial/schema/count state; scheduler submit exception/null/retirement single-winner; repeating-task publish race; `/reply` quit–reconnect generációvédelem.
- [x] **AUTOMATED:** `./gradlew clean build --no-daemon --stacktrace` — teljes fordítás és regressziós lifecycle.
- [ ] **REAL FOLIA REQUIRED / RESTART TEST:** tempmute és tempban mentése, restart előtti/utáni enforcement, lejárat.
- [ ] **NEGATIVE TEST / RESTART TEST:** hibás, részleges, duplikált és ellentmondó `moderation-data.yml` fail-closed karantén.
- [ ] **NEGATIVE TEST:** írásvédett data könyvtár / lemezhiba — mutáció rollback, nincs hamis sikerüzenet, plugin fail-closed.
- [ ] **REAL FOLIA REQUIRED:** `/msg` két eltérő régióban; sender/recipient quit–reconnect a kézbesítési hop közben; a régi callback nem épít `/reply` linket az új sessionhez; SocialSpy státuszok.
- [ ] **PERMISSION TEST:** minden akció, GUI-gomb és tab completion külön permissionnel; invsee read/edit szétválasztás.
- [ ] **REAL FOLIA REQUIRED / RELOAD TEST:** vanish relog, world change, reload, tablist viewer-filter, nametag, online/MOTD count.
- [ ] **NEGATIVE TEST:** vanished admin mob target, item pickup, incoming/outgoing damage és interaction kapcsolók.
- [ ] **REAL FOLIA REQUIRED:** invsee fő/ender read és edit eltérő régiókban; target és admin quit/kick minden scheduler-hopnál; azonnal retired/null submit fault-injectionnél nincs elvesző vagy duplikált stack és nincs stale refresh task.
- [ ] **RELOAD TEST:** aktív invsee session reload/disable alatt bezár; admission lezárás után minden befogadott transfer drainelődik; final save után restart/reconnect restore; sikeres return utáni kontrollált újraindítás nem adja vissza ismét a rekordot.
- [ ] **NEGATIVE TEST:** `/offlinetp` hiányzó adat, nem betöltött vagy UUID-ben eltérő világ; nincs szinkron world/chunk load.

A fenti kézi pontok teljesítése előtt az SModeration/InvSee++ eltávolíthatósága **nem végleges**.
