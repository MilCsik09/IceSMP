# Natív IceSMP ládarendszer

> Branch-scope: `feature/native-crates-completion`, base: `claude/projekt-audit-u0hkcz` @
> `f4e82263d35c645a053eccac09a3ad254a36ed90`. A rendszer az IceSMP által ténylegesen
> használt crate-funkciókat biztosítja; nem CrazyCrates-kompatibilitási réteg.

## Megtartott termékfunkciók

- fizikai, tartósan regisztrált crate-helyek;
- PDC-azonosított kulcsok, kulcsvásárlás és admin key give;
- súlyozott roll, bounded mass-open, cooldown és statisztika;
- read-only lista/preview GUI, spin GUI és 3D reveal;
- vanilla item, command, currency, unique item, profession recipe, blueprint és crate-key reward;
- per-crate permission- és world-policy.

Nem cél legacy CrazyCrates config/key/location migráció, upstream commandparitás vagy korlátlan
mass-open.

## Központi policy

A `CrateManager.accessDecision` ugyanazt a `icesmp.crate.use`, opcionális per-crate permission és
world policyt alkalmazza a fizikai opening, `/crate info`, `/crate preview`, browser GUI és player
completion útvonalakon. Jelen lévő, hibás típusú `worlds` érték fail-closed confighiba; csak a
hiányzó vagy szabályos üres lista jelent minden világot.

A config egész értékei és a tartós count/total/cooldown mezők pontos integer parseren mennek át.
Nincs `double` köztes reprezentáció vagy saturáció: tört, `Float`/`Double`, `NaN`, `Infinity`,
overflow, boolean és lista elutasításra kerül. A boolean mezők csak valódi YAML booleanok lehetnek.

## Opening állapotgép

Egy opening életciklusa:

`RESERVED → PERSISTED → GRANTING → COMPLETED`

Hiba esetén a terminális állapot `ROLLED_BACK` vagy `FAILED_PARTIAL`. A `PERSISTED → GRANTING`
átmenet CAS-alapú, ezért a player scheduler task csak egyszer claimelheti a grantet. A finalize és
a kompenzációs rollback kölcsönösen kizárja egymást.

A stat és cooldown `CrateLedger.prepare` alatt csak mutation tokenként készül el; autoritatív
ledger-state-be kizárólag sikeres reward-settlement után kerül. Scheduler rejection, quit, reload
vagy pre-reward hiba ezért nem hozhat létre phantom statot vagy cooldownot.

### Tartós recovery fence

A schema 2 `crates-data.yml` az openinghez recovery rekordot is tárol:

- `ROLLBACK_ONLY`: sem kulcs, sem reward, sem ledger-side effect nincs;
- `REFUND_KEYS`: kulcs elfogyott, automatikus egyszeri visszaadás engedélyezett;
- `REFUND_CLAIMED`: a visszaadás tartósan claimelve; restart után kézi audit kell;
- `MANUAL_REVIEW`: nem bizonyíthatóan kompenzálható külső side effect történhetett.

A kulcsfogyasztás előtt `REFUND_KEYS`, bármely reward side effect előtt `MANUAL_REVIEW` marker
kerül atomikusan mentésre. Így orderly reload/disable esetén a rendszer vagy side effect nélkül
eldobja a preparationt, vagy visszaadja a kulcsot, vagy konzervatívan admin-auditot kér. Egy recovery
játékosonként és opening-ID-nként egyszer claimelhető.

## Reward-settlement és kompenzáció

1. a teljes reward batch előre feloldódik a captured config snapshotból;
2. a szükséges kulcs pontosan, több inventory stackből fogy;
3. a currency batch `CurrencyManager.addBalancesDurably` útvonalon azonnal mentődik;
4. a command batch a global scheduleren fut, és minden `dispatchCommand` eredménye ellenőrzött;
5. az itemek a player owner schedulerén kerülnek inventoryba vagy full inventorynál a játékos
   helyére esnek;
6. csak ezután alkalmazódik és mentődik a stat/cooldown token, majd lesz `COMPLETED`.

Currency write failure előtt nincs completed opening. A durable currency mutation exact snapshotos
rollback tokent ad; stale vagy sikertelen rollback kézi auditot igényel. Command submit exception,
null/rejected handle, `dispatchCommand == false` vagy exception esetén nulla sikeres commandnál a
currency és kulcs kompenzálható. Már lefutott command vagy átadott item után nincs automatikus
kulcsrefund: az opening `FAILED_PARTIAL`/`MANUAL_REVIEW`, így nem duplikál jutalmat.

Ez nem több-store distributed transaction. Process-kill esetére nincs exactly-once állítás; a
recovery rekord a bizonyítható orderly lifecycle-t és a kézi audit határát teszi explicitte.

## Reload, location és kulcsvásárlás

A key purchase az árat és a kulcsot ugyanabból a `ConfigSnapshot` generationből készíti. Generation
váltáskor a durable currency deduction rollbackel, és nem ad új definícióból kulcsot.

Opening finalize előtt újraellenőrződik:

- ugyanaz a world UUID és név;
- ugyanaz a blokkkoordináta;
- ugyanaz a crate-ID;
- ugyanaz a captured definition és config generation;
- az aktuális permission/world policy.

Loadkor ismeretlen vagy törölt crate-ID, kicserélt világ, duplikált hely, hibás schema, count vagy
recovery token fail-closed corrupt state.

## Concurrency és cleanup

- off-hand interaction elutasított;
- játékosonként legfeljebb egy opening vagy recovery aktív;
- stats reset aktív opening/recovery alatt tiltott;
- entity/global/async scheduler submit exception és null handle single-winner fallbacken fut;
- spin/reveal task handle race-biztos lease-t használ;
- quit/kick/reload/disable pending openinget rollback/refund/manual-review állapotba sorol;
- shutdown rövid, korlátos drain után tartósan osztályozza a fennmaradó grantet;
- audit append és rotáció egyetlen sorosított writeren fut;
- GUI/command region threadek nem osztanak meg mutable `DecimalFormat` példányt.

## Automatizált bizonyíték

`crateRegressionTest` viselkedési tesztjei lefedik:

- strict integer, boolean, world-list, weight, amount és command placeholder validáció;
- exact több-stackes kulcsfogyasztás és részleges, de csak teljesen finanszírozott mass-open;
- CAS grant claim és finalize–rollback kizárás;
- scheduler task/rejection single-winner, exception/null/retirement és task-handle publish race;
- recovery single-claim, restart snapshot és duplikált player recovery tiltás;
- phantom stat/cooldown hiánya, settlement, reset, rollback, overflow és stale token;
- command batch és item/currency side-effect kompenzációs policy;
- konkurens audit append/rotáció és thread-safe számformázás;
- production wiring, central access policy és Folia source guardok.

Kötelező ellenőrzések:

```text
./gradlew crateRegressionTest --no-daemon --stacktrace
./gradlew clean build --no-daemon --stacktrace
python3 scripts/check_consistency.py
git diff --check
```

A review publikálási ellenőrzése ugyanazt a teljes parancssort futtatja a remote feature HEAD-en;
a normál, read-only pull-request CI ennek külön post-fix bizonyítéka. Ez nem helyettesíti a valódi
Folia/fault-injection átvételi teszteket.

## Runtime playtest és eltávolíthatóság

Valódi Folia/fault-injection teszt kell legalább: off-hand/dupla katt, több stackes key, partial
mass-open, full inventory, minden rewardtípus, command false/exception/rejection, currency write és
rollback failure, két gyors reload, világ/crate cseréje, quit/kick/disable minden lifecycle ponton,
spin/reveal cleanup, auditrotáció, restart recovery és manual-review adminfolyamat.

A CrazyCrates jar csak zöld remote CI **és** ezek sikeres runtime átvételi tesztje után távolítható
el. CI alapján production-safe vagy process-crash exactly-once garancia nincs.
