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
| LuckPerms, GSit, CrazyCrates, FancyHolograms, AuMenus, voicechat, SModeration, minimotd, ImageFrame, Axiom/FAWE/goBrush/VoxelSniper, packetevents/ProtocolLib | Nincs ismert ütközés. |

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
      **Titkos csatorna:** `/suttogas <üzenet>` — csak Suttogók látják; kívülállónak „csak a szél zúg".
      **Gyanú:** rajtakapott testvérgyilkosság (+40) és rajtakapott rítus (+50) növeli; a szemtanúk
      `/suttogas vad <játékos>` váddal (+15, csak IGAZI Suttogóra hat, tokent fogyaszt) nyomozhatnak;
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
- [ ] **Spell-item CMD-k:** a Vadgomba (6101), Rúnakő (6102), Démoni Só (6103) és Kiűzés Botja (6104)
      custom model datát hordoz — resource packkel a textúrájuk cserélhető (RESOURCE_PACK_CMD.md).
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
      saját CustomModelData-juk (6000–6013) és frappáns lore-juk.

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
      működnek tovább.
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
      azonnal ADJA a `warrior_master_trial`-t (❕ üzenet); az a `harcos_proba` pálya lefutásával
      teljesül (a /parkour jutalom mellett quest-jutalom is jár).
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
  - [ ] **MOTD** (MiniMOTD helyett): a szerverlistában gradient-es Ice SMP MOTD, ami ~10 mp-enként
        vált a variánsok közt (frissítsd a listát többször); `motd.yml`-ből szerkeszthető.
  - [ ] **AFK-rendszer** (AxAFKZone helyett): állíts be zónát az `afk.yml`-ben → a zónába lépve
        üzenet + bossbar-visszaszámláló; az intervallum leteltével (default 10 perc) kis
        valuta-jutalom; kilépéskor a haladás NULLÁZÓDIK (nincs bankolás). 3 perc tétlenség
        után (bárhol) a tablistában szürke „⌚ ᴀꜰᴋ" jelenik meg a név után, mozgásra eltűnik.
  - [ ] **Crate-rendszer** (CrazyCrates helyett): admin: `/crate set koznapi` a nézett blokkra,
        `/crate give <név> koznapi 3`; kulccsal jobb-katt → kulcs fogy + súlyozott jutalom
        (hang+részecske+üzenet); kulcs nélkül info az árról; `/crate buy koznapi` levonja a
        25 NEUTRAL-t (sink); `/crate info` esély-táblát mutat; a kulcs NEM craftolható be;
        restart után a crate-blokkok megmaradnak. ⚠️ **Folia:** `/crate give` távoli (másik
        régióban lévő) játékosnak is hibamentes.
  - [ ] **Ülés** (GSit helyett): `/sit` a földön állva leültet (ArmorStand-ülés), újra `/sit`
        vagy sneak = felállás; jobb-katt lépcsőre/fél-lapra ÜRES kézzel leültet; halál/teleport/
        kilépés/blokk-törés alóla → korrekt felállás, nem marad árva ArmorStand.
  - [ ] **Moderáció** (SModeration helyett): `/mute <név> 5 teszt` → a némított nem tud
        chatelni ÉS /msg-t sem küldeni (üzenetet kap a hátralévő idővel); restart után is él;
        `/unmute` felold; `/mute list`; chat-szűrő: tiltott szó CENSOR-módban csillagozva,
        BLOCK-módban elnyelve; spam: 1,5 mp-en belüli két üzenet / ismételt üzenet blokkolva.
  - [ ] **Bejelentés:** `/report <név> <ok min 3 szó>` → az online moderátorok
        (icesmp.admin.moderation) azonnal értesülnek; 60 mp-en belül második report
        rate-limitbe fut; `/reports` lista → `/reports resolve <id>`; restart-álló.
  - [ ] **Inspektor:** `/icesmp inspect <név>` (icesmp.admin.inspect) → teljes riport
        (frakció/kaszt+XP/spec/erőforrás/egyenlegek/statok/bűn/claim/questek/cooldownok);
        offline névre korlátozott riport. ⚠️ **Folia:** távoli (másik régiós) célpontra is
        hibamentes (a riport a célpont szálán épül).
  - [ ] **Invsee** (InvSee++ helyett, csak olvasás): `/invsee <név>` → pillanatkép-GUI a fő
        inventoryról (páncél+offhand sorral) + ender-láda nézet gombbal; SEMMI nem vehető ki.
- [ ] **QoL-kör (A43–A52, ÚJ):**
  - [ ] **Relációs háború-szín:** raid alatt a szemben álló fél tagjai PIROSAK a tablistádban
        és a fejük fölött — a raidben nem érintett harmadik frakciónak viszont NEM; a raid vége
        után a frakció-színek visszaállnak; a tablist-sorrend eközben NEM ugrál.
  - [ ] **AFK-jelzés/hátrasorolás:** ~3 perc tétlenség után „⌚ ᴀꜰᴋ" suffix + a játékos a rangján
        belül a tablist aljára kerül; mozgásra azonnal vissza.
  - [ ] **AFK-jutalomkapu:** AFK-jelölt játékos mob-ölése NEM ad kaszt-XP-t/lélekkövet (auto-farm
        teszt: állj mob-farm mellé 3+ percig); az AFK-zóna időzített jutalma viszont jár.
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
- [ ] **D9 Énekmondó (ÚJ — Tier A):** nevezz el egy FancyNpcs-NPC-t `enekmondo`-nak (vagy írd
      át a `bard.npc-name`-et) → jobb-kattra a HETI ballada (top-1 szint/vagyon/raid sablon-
      variánsokkal; egész héten ugyanaz a dal, hétfőnként fordul). FancyNpcs nélkül nem elérhető.
- [ ] **D15 Tábortűz-mesélés (ÚJ — Tier A):** tábortűznél SNEAK+jobb-katt → „Leülsz a tűzhöz…"
      action bar; 6 mp helyben maradás után sztori-sor + 8 XP + lélekláng-particle (a közelben
      ülők „X mesél a tűznél…" action bart látnak); elsétálva megszakad, nincs jutalom;
      cooldown 60 perc (PDC `cd_campfire_story` — csak SIKERES mesélés indítja). Kulcsok:
      `campfire-story.*` (general.yml), saját sztori-lista a `stories` kulccsal.
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
- [ ] **B54 Átkozott felszerelés (ÚJ — Tier A):** világboss/event-boss loot ~8%-a Átkozott
      (sötétvörös lore-sor): viselve darabonként +10% kimenő sebzés (cap +40%), de a
      páncél-slotból NEM vehető ki („Az átok nem ereszt…"). Felvételkor az első kattintás
      figyelmeztet, csak az 5 mp-en belüli második erősít meg. Levétel: építs Átok-törés
      oltárt (CRYING_OBSIDIAN mag + 3×3 obszidián talp, áldozat: 8 ametiszt + 1 ghast-könny)
      → SHIFT+jobb-katt → minden viselt/kézben tartott átok megtörik, a tárgy megmarad.
      Kulcsok: `item-rarity.cursed.*`, rituálé: `rituals.atok_tores`.
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
  - [ ] 22 rejtvény-quest él (rejtveny_* — gyűjtés, vadászat,
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
  - [ ] Új relikvia: `sarkany_tojas` (DRAGON_EGG, CMD 4206); amíg a tulajdonosa Sárkányidéző
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
  - [ ] Visszhang Rúnája (CMD 6146, Varázsló-ZÁRT recept — új `job:` recept-mező +
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
  - [ ] 6 rúna unique-materialként (CMD 6140-6145), Kovács/Bűvölő 26-42. szintű receptek
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
  - [ ] **CMD-regiszter:** MINDEN nevesített (lore-os) recept-eredmény CustomModelData-t visel
        (6300–6438, 139 tárgy — a végtermék-eszközök is), a Kopott erszény 1010 — teljes lista: `docs/RESOURCE_PACK_CMD.md`
        (resource pack készítéshez). Új custom item = új CMD + új sor a regiszterben!
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
- [ ] **Birtokmérő pálca** (`/claim wand`, CMD 5410, STICK): bal katt blokkra = 1. sarok,
      jobb katt = 2. sarok (méret+ár-előnézet chatben, átfedés-figyelmeztetéssel),
      SNEAK+jobb = foglalás (a /claim area teljes ár/limit-logikáján át). A kattintott
      BLOKK koordinátája megy a kijelölésbe (nem a játékos helye). A pálca-katt nem
      üt/nem nyit semmit (cancel).
- [ ] **Határkijelölő pálca** (`/territory wand`, CMD 5411, BLAZE_ROD, admin-node):
      bal katt = poligon-pont a kattintott blokkon, jobb katt = utolsó pont visszavonása,
      SNEAK+jobb = határ-előnézet (/territory show); létrehozás továbbra is
      /territory create-tel. Jog nélkül a pálca nem csinál semmit (hibaüzenet).
- [ ] Tab-complete: `claim wand`, `territory wand`; CMD-regiszter: 5410-5411 felvéve.

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
      [dungeonkulcs_melyseg, CMD 6203], A Csontkripta Kulcsa [dungeonkulcs_csontkripta,
      CMD 6204] — CMD-regiszterbe felvéve); új player-guide oldal (16-kazamatak.md).
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
      4 árnyékpor + 2 echo-szilánk + 8 obszidián → crafted-affixes penge (CMD 6439,
      regiszterben) — az árnyékpor sötét útról jön (rítus-loot / Suttogó-részesedés).
