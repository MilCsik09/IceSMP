# IceSMP — Changelog és telepítési útmutató

> **Cél-kiadás:** a jelenleg futó `IceSMP1.0TESTING.jar` (2026-07-14-i állapot, `49cb327`,
> PR #16 merge) leváltása a friss builddel. A futó jar azonosítása bináris-elemzéssel
> történt (osztálykészlet + config-tartalom egyezés). A lemaradás: **176 commit**
> (2026-07-14 → 2026-07-27, `10403f8`) + a 0. stabilitási fázis javításai.

---

## Unreleased — globális AFK scope-helyreállítás (2026-07-30)

- A jutalmazó AFK-zóna teljes runtime-, scheduler-, config-, üzenet-, bossbar- és kifizetési útja törölve; a globális tétlenségészlelés és a `/afk` megmaradt.
- A `/afk` most kézi és automatikus AFK-ból is valóban aktív állapotba kapcsol vissza, friss tétlenségi időablakkal.
- A natív tablista HUD nélkül is elindul, az AFK-játékost garantáltan a saját rangja végére rendezi, kikapcsoláskor pedig eltakarítja a natív kimenetet.
- A boss/event és kazamata-miniboss loot ugyanazt az AFK-jutalomkaput használja; a miniboss lifecycle jutalomtiltás mellett is lezárul.
- Bizonyíték: `afkRegressionTest`, AFK product-boundary consistency guard és teljes Java 21 build. Valódi Folia átvételi teszt továbbra is szükséges.

## Unreleased — natív crate code-review hardening (2026-07-29)

- Atomi opening lifecycle: `RESERVED → PERSISTED → GRANTING → COMPLETED`, kizárólagos rollback/finalize és single-claim grant.
- A stat és cooldown csak sikeres reward-settlement után válik autoritatívvá; scheduler rejection nem hagy phantom állapotot.
- Tartós recovery fence: `ROLLBACK_ONLY`, `REFUND_KEYS`, `REFUND_CLAIMED`, `MANUAL_REVIEW`; automatikus refund csak bizonyíthatóan kompenzálható állapotban.
- A currency reward durable mutationt és exact-snapshot kompenzációt használ; command reward csak elfogadott scheduler submit, tényleges futás és `dispatchCommand == true` után sikeres.
- Strict config: hibás `worlds` típus fail-closed, pontos egész parser `double` köztes reprezentáció nélkül, egységes permission/world policy.
- Generation-konzisztens kulcsvásárlás, finalize előtti world/location/crate-definition revalidation, ismeretlen tartós crate-ID fail-closed.
- Sorosított audit writer, thread-safe formázás, spin/reveal rejection cleanup, stats-reset és opening race-védelem.
- Nem állítunk distributed transaction, process-crash exactly-once vagy automatikus CrazyCrates-paritási garanciát.
- Bizonyíték: `crateRegressionTest`, teljes Java 21 build, consistency és whitespace check; a CrazyCrates eltávolításához valódi Folia/fault-injection átvételi teszt kell.

## Telepítési útmutató

### 1. Külső pluginok eltávolíthatósági állapota

> A buildelt natív alap önmagában nem runtime-garancia. A MiniMOTD, GSit, CrazyCrates,
> SModeration és InvSee++ csak a replacement-mátrix kézi Folia-kapui után törölhető.
> Az AxAFKZone/AxAPI külön eset: jutalmazó AFK-zóna nincs a termékscope-ban, ezért nem
> deployment-függőség; az éles jar/adat és remap-cache eltávolítása operátori lépés.

| Régi plugin | Natív kiváltás |
|---|---|
| `ICEsmpadditions.jar` | `world-tweaks.warden-death-xp` (general.yml, élőben hangolható) |
| `FarmProtect.jar` | `world-tweaks.crop-trample-protection` |
| `MiniMOTD` | **FELTÉTELES** — a natív completion buildelt és regressziózott; valódi Folia ping/ikon/reload és jar nélküli átvételi playtest még kell |
| `TAB` | natív tablist: header/footer, nevek, nametag+rendezés, ping (`tablist.yml`) |
| `CrazyCrates` | **FELTÉTELES** — a natív crate lifecycle code-review-zott és regressziózott; valódi Folia/fault-injection átvételi teszt még kell |
| `AxAFKZone` / `AxAPI` | **NEM KELL** — a jutalmazó AFK-zóna törölve; a natív globális AFK külön regresszióval védett. Éles jar/adat/remap-cache eltávolítandó, migráció nincs |
| `GSit` | **FELTÉTELES** — a natív sit-only lifecycle buildelt és regressziózott; valódi Folia seat/cleanup átvételi playtest még kell |
| `InvSee++` / `SModeration` | **FELTÉTELES** — a natív suite buildelt; valódi Folia/restart/fault-injection playtest még kell |

A `plugins/` mappából csak a mátrixban **READY** és kézzel is igazolt tételek jarját szabad eltávolítani.
Megmaradó soft-dependency: PlaceholderAPI, LibsDisguises, FancyNpcs, WorldGuard, LuckPerms
(mind opcionális — nélkülük is fut).

### 2. Resource pack — CSERE KÖTELEZŐ

A teljes item-állomány numerikus CustomModelData-ról **ITEM_MODEL** komponensre állt át
(~268 modell-id az `icesmp:` névtérben). A régi resource pack a új itemekhez nem ad
textúrát; az új pack manifestje: `docs/RESOURCE_PACK_CMD.md`.

### 3. Első indulás

- Az **advancement-fa** mostantól a jarból szállított datapack (7 → 20 csomópont) —
  első indulásnál a log jelzi: „IceSMP advancement-fa: N/N bejegyzés a jar datapackjéből”.
- Az új config-fájlok (pl. `crates.yml`, `dev-items.yml`, `motd.yml`, `sit.yml`, `tablist.yml`) a jarból
  csomagolódnak ki; a data-mappás `config.yml` felülbírálások megmaradnak és utolsóként
  merge-ölődnek.
- A **HP-rendszer** megépült, de **alapból kikapcsolt** (`health.enabled: false`) —
  tulaj-döntésig így marad.
- Indulás után ellenőrzés: `python3 scripts/check_consistency.py` a repóban 0 FAIL,
  a szerver-logban nincs `region`/`scheduler`/`IllegalStateException` stacktrace.

---


## Unreleased — natív MOTD completion (2026-07-29)

- Immutable, strict config snapshot időalapú és seedelt, időablakon belül stabil random rotációval; exact signed-`long` parserrel és típushű boolean validációval.
- Eseményprioritás: vérhold, világboss, szezonzárás, majd normál variáns; kizárólag `{online}`/`{max}` tokenek és max-player override.
- A vanished játékosok számlálása a moderációs `VanishManager` meglévő thread-safe cache-ét használja, párhuzamos state nélkül.
- Variáns/default/random ikonmód; az ikonok async, secure-directory handle-en, root/köztes/fájl symlink követése nélkül töltődnek, majd egyetlen immutable cache-ként kerülnek a global-region schedulerre.
- `/icesmp reload`, `motd.*` config set/unset és a központi config GUI ugyanazt a célzott reloadot használja.
- A reload/disable generáció és a moderációból újrahasznált single-winner scheduler gate megakadályozza a késői vagy visszautasított async callback cache-publikálását.
- Bizonyíték: `motdRegressionTest`, teljes Gradle build, consistency `0 FAIL / 0 WARN` és whitespace check. A MiniMOTD eltávolításához továbbra is valódi Folia server-list és reload playtest kell; upstream paritást nem állítunk.

## Unreleased — natív moderációs suite (2026-07-28)

- Egységes, strict punishment ledger: warning/kick/mute/tempmute/ban/tempban/unmute/unban, teljes history és async login ban gate.
- Közös persistence lifecycle, atomikus mentés, mutációs rollback és fail-closed corrupt-state kezelés.
- Natív `/msg`/`tell`/`w`/`reply`, tartós SocialSpy, tartós vanish viewer-specifikus tablist-integrációval.
- Online live inventory és ender chest read/edit külön permissionnel, két entity-scheduler közötti tesztelt single-claim escrow-val és auditloggal.
- Review-hardening: a tartós invsee return queue strict restart/corrupt-state validációt és claimenkénti törlést kapott; a nullable entity-scheduler submitok single-winner fallbacken futnak; a `/reply` link join-session generációval védett a quit–reconnect race ellen.
- Moderációs GUI, offline teleport, központi permissionök és konfigurálható magyar üzenetek.
- Bizonyíték: `moderationRegressionTest` és teljes Gradle build. Valódi Folia playtest nélkül nincs végleges plugin-eltávolítási állítás.

## Changelog — 2026-07-14 (`49cb327`) → 2026-07-27 (`10403f8`)

### Új rendszerek

- **Natív tablist** (a TAB teljes kiváltása) + **dinamikus HUD**: harc-fókusz, rotáló
  infósor, prioritás-kiszorítás, party-szekció.
- **Natív moderáció**: `/report` rendszer (perzisztens, offline-feedbackkel), mute +
  chat-szűrő + spam-fék, eszkaláció + chat-napló; **admin-inspektor** + read-only `/invsee`.
- **Natív globális AFK, crate- és sit-only alapok**. Az AFK-zóna jutalmazó scope-ja elvetett;
  a crate és ülés külső pluginjának leváltása továbbra is valódi Folia átvételi kapuhoz kötött.
- **DisplayFx + SpellVfx réteg**: display-entity effektek, formázott spell-VFX kaszt/spec-
  palettákkal és per-spell override-dal, 3D crate-feltárás, boss-AoE padló-telegraph,
  aurora fény-fátyol; particle-diéta (FLASH-korlát, konfetti-mérséklés).
- **P7 data-komponens réteg** (`ItemDataFactory`): CONSUMABLE-ételek/italok (+~30 Szakács-
  fogyasztható két adagban), USE_COOLDOWN (katalizátor-bleed fix), TOOLTIP_DISPLAY;
  **bootstrap-réteg**: saját `icesmp:rontas` damage-type + 5 iskola-counter enchant + üllő-őr.
- **Vanília-egyenértékű gear**: egyedi nevek + determinisztikus statok/enchantok minden
  nem-signature felszerelésen; explicit attribútum-modifier támogatás.
- **Advancement-réteg**: natív IceSMP haladás-fül, jarból szállított datapack, 20 csomópont
  valódi grant-pontokkal.
- **Lore-kör**: kanonikus kódex (`docs/LORE.md`), ~170 sor ingame sztori-átadás négy
  csatornán, frakciós tábortűz-mesék, **korona-átok** (CrownCurseManager), a Suttogás-
  csatornát a Kitaszítottak is hallják; natív párbeszéd-ablakok (DialogService) +
  üdvözlő-dialóg.
- **Csodálatos Bingulus** — tulajdonhoz kötött, restart-biztos DEV-item (pity-védelemmel,
  regressziós tesztekkel; a #39-es körben egyszerűsített, karbantartható állapotgéppel).
- **QoL-körök (A43–A70)**: relációs színek, AFK-kapuk, célpont-sor, esemény-MOTD,
  kulcs-lore, értesítések, ikonok, tiszta sidebar, kombó-csík, fekvő póz.

### Stabilitás, perzisztencia, exploit-zárások (2026-07-22–27-i körök)

- **Write-ahead journalok**: piac/wallet tranzakció (`TransactionJournal`) + tile-entity
  block-regen (`BlockRegenJournal`); szezon-jutalom tartós helyreállítás; kifizetés-outbox.
- **Fail-closed állapot-betöltés**: sérült YAML → karantén + mentés-tiltás (nem íródik felül).
- Időszakos **autosave** + raid-zárás broadcast restartnál.
- **Exploit-zárások**: örök paktum kapu (DARK), vérdíj-fék, leave+join kerülőút zárva,
  jutalom-faucet plafonok (lélekkő napi cap), rituálé-hozzávaló közös szerződés,
  gyűjtés-progressz visszajátszás-védelem, visszavont akciók MONITOR-prioritásra,
  FurnaceSmelt-tiltás unique anyagokra, End-portál-zár.
- **PvP/balansz**: combat-tag (12 mp, zóna-védelem + komp-tiltás), hard-CC diminishing
  return, B-csomag (12 hangolási tétel: energia-regen, vér-spellek, tier2 talentek,
  recept-szintek, DARK szárny-ár), spell power-cap (1.75).
- **Teljesítmény-kör**: tablist-söprés O(n), HUD diff-cache, világesemény-köteg
  szétterítés, pet-tick üresjárat-fék.
- **Folia-javítások**: kill-snapshot + scheduleres party-jutalom, transient lifecycle
  watchdog, viselt relikvia relog-fix, kultista rítus dupla-lezárás zárva.
- **CI**: állandó GitHub Actions (build + PersistentStore- és DEV-item regressziós suite +
  konzisztencia-delta kapu).

### Dokumentáció és tartalom-integritás

- Élő audit-doksi (`docs/ideas/PROJEKT-AUDIT.md`) + konszolidált BACKLOG + kivitelezési
  terv a ROADMAP-ban; gépi drift-ellenőrző (`scripts/check_consistency.py`) bővített
  tükör-lefedettséggel; szöveg-audit (ékezetek, terminológia, angol azonosítók).

---

## 0. stabilitási fázis javításai (2026-07-28 — az új build része)

> A kivitelezési terv 0. fázisa (ROADMAP). A tételszámok a PROJEKT-AUDIT 2026-07-28-as
> szekciójára hivatkoznak. Valódi build + mindkét regressziós suite zöld.

**Folia szál-biztonság:**
- Sámán-totem pulzus, pet cél-feloldás/aggro-keresés, világboss ZONE-telegráf:
  régió-tulajdon kapu + scheduler-hop (1–3.); kazamata-kapu párttag-szűrés védőhálóval (4.).
- Párt-közelség (XP-megosztás, personal loot) az új, játékos-szálon töltött
  **PositionCache** tükörből számol — a régiófelosztás-függő jutalom-kimaradás megszűnt.
- Totem crash-árva sweep: betöltéskor a nem követett, taggelt totem-állvány eltávolítása.

**Perzisztencia és életciklus:**
- 26 store mentési hibája a log után tovább is dobódik — a leállítás-koordinátor
  hibagyűjtése így már látja (14.); a 14 nem-szinkronizált store-save synchronized.
- Királyválasztás: az összetett szavazás→koronázás tranzakció lock alá került (16.).
- factions.yml + kings.yml betöltés fail-closed karanténnal — hibás rekordot a mentés
  többé nem tüntet el némán (15., részleges: a claims.yml külön kört vár).
- Leállítás hibaszigetelt: `onDisable` try/finally + lépésenkénti hibagyűjtés — egy hibás
  manager-shutdown nem viszi el a mentést és a takarítást (13.).

**Élő-config és gameplay:**
- `/icesmp reload` mostantól a recept-katalógust és a spell-VFX beállításokat is frissíti (5–6.).
- Hazatérés-rituálé: az áldozat induláskor fogy el, sikertelen teleportnál refund +
  nincs cooldown — se ingyen-teleport, se igazságtalan veszteség (17.).
- GameModeCache cancelelt eventből nem frissül (18.); `/icesmp config set` nem fogad
  NaN/Infinity számot (19.); a config-betöltés csak az allowlist fájljait merge-öli (20.).
- PvP-n gazdát cserélt relikvia megtartja az ITEM_MODEL kinézetét (7.); affix-rollos
  craftolt tárgyak megtartják a rollolt vanília raritás-fokot (8.).
- Kick-rés zárva a relikvia- és katalizátor-stash mapeken (9.); `/faction set`
  tab-complete (10.); `/afk` a parancs-referenciában (11.).

**Gazdasági crash-ablak (H-ECON-001):** a szinkron-commit kísérlet PR-review alapján
visszavonva (teljes wallet/claim snapshot írása régió-szálon nem elfogadható, és a
két-fájlos írást nem is tette atomivá) — a blokkoló NYITVA marad, az elfogadott irány
a szűk WAL/pending-record a market-journal mintájára (külön kör). A hazatérés-rituálé
viszont végleges formát kapott: az áldozat induláskor fogy el, sikertelen teleportnál
visszajár (refund) és a cooldown sem indul — se ingyen-teleport, se ingyen-veszteség.

**Nem része a körnek (tulaj-döntés / külön kör):** a scoreboard-réteg Folia-kérdése
(12., félretéve), a H-ECON-001 WAL, a claims.yml fail-closed loader és az alacsony
súlyú sáv.
