# IceSMP — Changelog és telepítési útmutató

> **Cél-kiadás:** a jelenleg futó `IceSMP1.0TESTING.jar` (2026-07-14-i állapot, `49cb327`,
> PR #16 merge) leváltása a friss builddel. A futó jar azonosítása bináris-elemzéssel
> történt (osztálykészlet + config-tartalom egyezés). A lemaradás: **176 commit**
> (2026-07-14 → 2026-07-27, `10403f8`) + a 0. stabilitási fázis javításai.

---

## Telepítési útmutató

### 1. Eltávolítandó pluginok (mind natívan kiváltva)

| Régi plugin | Natív kiváltás |
|---|---|
| `ICEsmpadditions.jar` | `world-tweaks.warden-death-xp` (general.yml, élőben hangolható) |
| `FarmProtect.jar` | `world-tweaks.crop-trample-protection` |
| `MiniMOTD` | natív MOTD (`motd.yml`) |
| `TAB` | natív tablist: header/footer, nevek, nametag+rendezés, ping (`tablist.yml`) |
| `CrazyCrates` | natív crate-rendszer (`crates.yml`, `/crate`, rulett-animáció) |
| AFK/ülés plugin | natív `/afk` + `/sit` (klikk-ülés) |
| `InvSee++` / `SModeration` | natív moderáció (`/report`, mute, chat-szűrő) + read-only `/invsee` + inspektor |

A `plugins/` mappából a fentiek jarját el kell távolítani az új IceSMP-jar bemásolásakor.
Megmaradó soft-dependency: PlaceholderAPI, LibsDisguises, FancyNpcs, WorldGuard, LuckPerms
(mind opcionális — nélkülük is fut).

### 2. Resource pack — CSERE KÖTELEZŐ

A teljes item-állomány numerikus CustomModelData-ról **ITEM_MODEL** komponensre állt át
(~268 modell-id az `icesmp:` névtérben). A régi resource pack a új itemekhez nem ad
textúrát; az új pack manifestje: `docs/RESOURCE_PACK_CMD.md`.

### 3. Első indulás

- Az **advancement-fa** mostantól a jarból szállított datapack (7 → 20 csomópont) —
  első indulásnál a log jelzi: „IceSMP advancement-fa: N/N bejegyzés a jar datapackjéből”.
- Az új config-fájlok (pl. `dev-items.yml`, `motd.yml`, `tablist.yml`) a jarból
  csomagolódnak ki; a data-mappás `config.yml` felülbírálások megmaradnak és utolsóként
  merge-ölődnek.
- A **HP-rendszer** megépült, de **alapból kikapcsolt** (`health.enabled: false`) —
  tulaj-döntésig így marad.
- Indulás után ellenőrzés: `python3 scripts/check_consistency.py` a repóban 0 FAIL,
  a szerver-logban nincs `region`/`scheduler`/`IllegalStateException` stacktrace.

---

## Changelog — 2026-07-14 (`49cb327`) → 2026-07-27 (`10403f8`)

### Új rendszerek

- **Natív tablist** (a TAB teljes kiváltása) + **dinamikus HUD**: harc-fókusz, rotáló
  infósor, prioritás-kiszorítás, party-szekció.
- **Natív moderáció**: `/report` rendszer (perzisztens, offline-feedbackkel), mute +
  chat-szűrő + spam-fék, eszkaláció + chat-napló; **admin-inspektor** + read-only `/invsee`.
- **Natív AFK-, crate- és ülés-rendszer** (3 plugin kiváltva); crate rulett-animációval,
  kulcs-források a jutalom-csatornákban.
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
- Hazatérés-rituálé: az áldozat + cooldown csak SIKERES teleport után rögzül (17.).
- GameModeCache cancelelt eventből nem frissül (18.); `/icesmp config set` nem fogad
  NaN/Infinity számot (19.); a config-betöltés csak az allowlist fájljait merge-öli (20.).
- PvP-n gazdát cserélt relikvia megtartja az ITEM_MODEL kinézetét (7.); affix-rollos
  craftolt tárgyak megtartják a rollolt vanília raritás-fokot (8.).
- Kick-rés zárva a relikvia- és katalizátor-stash mapeken (9.); `/faction set`
  tab-complete (10.); `/afk` a parancs-referenciában (11.).

**Gazdasági crash-ablak (H-ECON-001) — szinkron-commit kör:**
- Bank-kifizetés: az egyenleg-levonás tartósan rögzül, MIELŐTT a fizikai veret kézbe
  kerül (írási hibánál automatikus visszatérítés, veret nélkül).
- Bank-befizetés: előbb kerül ki a veret az inventoryból, aztán rögzül tartósan a
  jóváírás — mindkét irányban a veszteség-kerülő sorrend.
- Fizetős claim (létrehozás + Y-bővítés): a wallet-levonás ÉS a claim még a hívásban,
  tartósan mentődik — nincs fizetés nélküli claim / claim nélküli levonás debounce-ablak.
- Adomány-láda: a be- és kivét bejegyzése azonnal tartós (dupe/nyelés-ablak zárva).
- Maradó rés: az inventory (playerdata) és a saját fájl közti ezredmásodperces ablak —
  formális WAL csak akkor, ha a process-kill playtest indokolja.

**Nem része a körnek (tulaj-döntés / külön kör):** a scoreboard-réteg Folia-kérdése
(12., félretéve), a claims.yml fail-closed loader és az alacsony súlyú sáv.
