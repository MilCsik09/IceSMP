# Natív IceSMP ládarendszer

> Branch-scope: `feature/native-crates-completion`, base: `claude/projekt-audit-u0hkcz` @
> `f4e82263d35c645a053eccac09a3ad254a36ed90`. A rendszer az IceSMP által ténylegesen
> használt crate-funkciókat biztosítja; nem CrazyCrates-kompatibilitási réteg.

## Reuse-audit

| Szükséges képesség | Meglévő IceSMP-komponens | Újrahasználás módja | Szükséges bővítés | Új komponens? |
|---|---|---|---|---|
| Crate domain és fizikai hely | `CrateManager` | egyetlen autoritatív owner maradt | strict snapshot, tranzakció, stat/cooldown | új manager nem |
| Parancs és interakció | `CrateCommand`, `CrateListener` | meglévő belépési pontok | list/preview/stats/reset/mass-open és gate-ek | nem |
| Kulcsazonosítás | `CrateKeyFactory`, PDC | meglévő `crate_key` PDC | validált snapshotból név/modell/lore | új séma nem |
| Animáció | `CrateSpinGUI`, 3D reveal | kozmetikai réteg maradt | cleanup és új rewardikonok | új animációs framework nem |
| GUI | `GuiUtil`, holder/listener minta | read-only lista és preview | `CrateBrowserHolder`/listener | általános GUI-framework nem |
| Valuta | `CurrencyManager` | központi wallet API | egy batchben alkalmazott currency rewardok | új valuta API nem |
| Item/reward | `UniqueMaterialFactory`, `ProfessionRecipeCatalog`, recipe builder, `BlueprintItemFactory` | közvetlen resolver-hívások | crate-key reward adapter | reward framework nem |
| Persistence | `PersistentStore`, coordinator, `YamlStore.saveAtomic` | közös load/autosave/shutdown | schema-1 crate state, rollback | új persistence nem |
| Config/üzenet/jog | `ConfigManager`, `MessageManager`, `Permissions` | központi reload és registry | strict crate snapshot, dinamikus per-crate jog | párhuzamos registry nem |
| Cleanup/Folia | `PlayerStateCleanup`, player/global/async scheduler minták | központi lifecycle | pending open rollback és spin cleanup | wrapper/cleanup registry nem |

## Játékosfolyamat

- `/crates` vagy `/crate`: read-only ládalista-GUI.
- `/crate preview <id>`: súlyozott jutalmak és esélyek.
- `/crate info <id>`: ár, kulcsszám, cooldown, mass-open és odds.
- `/crate buy <id> [darab]`: PDC-azonosított kulcsvásárlás az IceSMP walletből.
- Főkézből jobb katt a regisztrált fizikai crate-blokkra: egy nyitás.
- Lopakodva jobb katt: a konfigurált maximumig többszörös nyitás; kizárólag teljesen
  finanszírozható nyitások kerülnek a tranzakcióba.
- Kulcs nélkül a fizikai láda info- és preview-útvonalat mutat.

Az off-hand `PlayerInteractEvent` mindig kiesik, ezért egy fizikai kattintás nem indulhat el kétszer.

## Adminparancsok

| Parancs | Funkció |
|---|---|
| `/crate set <id>` | A nézett blokk regisztrálása, tartós mentéssel. |
| `/crate remove` | A nézett crate-hely törlése, tartós mentéssel. |
| `/crate give <játékos> <id> [darab]` | Kulcs átadása a céljátékos entity schedulerén. |
| `/crate list` | Regisztrált fizikai helyek. |
| `/crate stats <játékos|uuid> [id]` | Játékosonkénti és crate-enkénti nyitási statisztika. |
| `/crate resetstats <játékos|uuid> [id|all]` | Stat és az érintett cooldown törlése atomikus crate-state mentéssel. |
| `/crate status` | Érvényes crate-ek és izolált confighibák. |

A stats-reset nem futhat ugyanazon játékos folyamatban lévő nyitási tranzakciója közben.

## Permissionök

- `icesmp.crate.use` — lista, preview, vásárlás és nyitás; alapból `true`.
- `icesmp.admin.crate` — set/remove/give/list/stats/reset/status; az admin-szülő része.
- `crates.<id>.permission` — opcionális, kizárólag `icesmp.*` node; a validált configból
  dinamikusan regisztrálódik, alapból `false`.

A command, GUI és fizikai interakció ugyanazt a központi permission-state-et ellenőrzi.

## Config és validáció

Forrás: `src/main/resources/config/crates.yml`.

Crate-enként: enabled, display/key vizuál, key price, permission, worldlista,
`required-key-count`, cooldown, mass-open, hang, broadcast és rewardlista.

Elutasított értékek:

- hibás vagy duplikált normalizált crate-ID;
- ismeretlen material, currency, világ, sound vagy rewardtípus;
- nulla, negatív, `NaN`, `Infinity` vagy túl nagy súly/valuta/cooldown/item amount;
- üres vagy 128 elemnél nagyobb rewardlista;
- nem létező unique item, recipe, blueprint vagy crate-key cél;
- nem `icesmp.*` permission;
- sortöréses, kezdő `/`-os, túl hosszú vagy ismeretlen placeholderes command reward.

Command rewardban csak `{player}`, `{uuid}`, `{crate}`, `{amount}` engedélyezett.
Egy hibás crate letiltható úgy, hogy a többi valid crate tovább működik; az admin `/crate status`
megkapja a hibák listáját. Reloadkor új immutable snapshot és generation jön létre.

## Rewardtípusok

- `item`: vanilla material;
- `command`: whitelistelt placeholderes konzolparancs;
- `currency`: központi IceSMP wallet;
- `unique-item`: `UniqueMaterialFactory`;
- `recipe-item`: a meglévő profession recipe builder;
- `blueprint`: `BlueprintItemFactory`;
- `crate-key`: a meglévő `CrateKeyFactory`.

Item overflow a játékos helyén kerül ledobásra; nincs csendes tárgyvesztés. A recipe reward
mennyisége külön, 64-es plafont kapott a költséges builder-hívások miatt.

## Tranzakció és persistence

A végleges `crates-data.yml` schema 1 tartalma:

- fizikai crate-helyek: világ UUID + utolsó ismert név + blokkkoordináta + crate-ID;
- játékos utolsó ismert neve;
- crate-enkénti nyitásszám;
- crate-enkénti cooldown-végidő.

Nincs legacy decoder vagy migráció. Loadkor a részleges, duplikált, ismeretlen crate-es,
hibás világú, negatív vagy túlcsorduló state fail-closed hibának számít.

Nyitási sorrend:

1. permission/world/cooldown/key/config ellenőrzés és egyetlen pending reservation;
2. minden reward sorsolása a validált snapshotból;
3. stat és cooldown memóriabeli mutation tokennel;
4. `YamlStore.saveAtomic`;
5. player-scheduleres újraellenőrzés;
6. pontos, több stackes kulcsfogyasztás;
7. currency batch, majd item/command side effect;
8. audit, broadcast és kozmetikai reveal.

Mentési hiba, config-generation váltás, quit/kick vagy retired scheduler esetén a még nem teljesült
tranzakció ledger-tokenje rollbackel és újra atomikusan mentődik. Currency reward hibája előtt
készült exact inventory snapshot visszaállítja a kulcsokat. Ez **nem** process-crash exactly-once
garancia: a külső command és több state-fájl közötti crash-atomitás nincs állítva.

## Folia ownership

- player inventory, kulcs, GUI és jutalom: player entity scheduler;
- state fájl és auditlog: async scheduler;
- command dispatch és broadcast-fanout indítása: global region scheduler;
- nézőnkénti broadcast: az adott player scheduler;
- display/reveal: a meglévő region-safe display helper;
- quit/kick/disable: központi `PlayerStateCleanup` + manager shutdown.

Tiltott scheduler, raw thread, timer vagy unmanaged executor nincs a crate-scope-ban.

## Automatizált ellenőrzés

`crateRegressionTest` valódi dependency-free domain teszteket futtat:

- súly, amount, cooldown és command placeholder validáció;
- determinisztikus súlyozott kiválasztás;
- exact több-stackes kulcsfogyasztási terv;
- required key és részleges mass-open;
- stat/cooldown, restart snapshot, exact rollback és reset rollback;
- overflow és stale token elutasítás;
- persistence-, PDC-, GUI-, scheduler- és lifecycle source-invariánsok.

Kötelező build: `./gradlew clean build --no-daemon --stacktrace`,
`python3 scripts/check_consistency.py`, `git diff --check`.

## Runtime playtest és eltávolíthatóság

A teljes kategorizált lista a `PLAYTEST.md` natív crate fejezetében található. Kiemelten:
off-hand duplaevent, két-stackes required key, mass-open, full inventory, permission/world/cooldown,
összes rewardtípus, save failure, reload-generation, quit/kick/disable, restart location és valódi
Folia cross-region command/broadcast.

A CrazyCrates jar csak zöld remote CI **és** a dokumentált valódi Folia átvételi teszt után
jelölhető biztonságosan eltávolíthatónak. A rendszer nem állít upstream feature-paritást.
