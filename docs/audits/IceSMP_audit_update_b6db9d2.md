# IceSMP audit update — b6db9d2

> **Auditált master:** `b6db9d21d12a2944b67925a5fe9228b4e76b9b04`
> **Dátum:** 2026-07-27
> **Hatókör:** a #26, #31, #32, #33, #34 és #35 merge-ek arányossági auditja,
> valamint a DEV-item pending reward célzott egyszerűsítése és owner-transfer javítása.
> **Nem állítás:** a korábbi teljes mélyaudit minden findingje nem lett újraellenőrizve.

## Termékdefiníció

Az IceSMP elsődlegesen lore-központú fantasy kingdom SMP: kaszt- és karakterépítés, frakciók,
politika, questek, szakmák, relikviák, raidek, kazamaták, világesemények, felfedezés és közösségi
szezonok együtt adják az élményt. A gazdaság és a perzisztencia ezeket védi; nem önálló banki vagy
általános tranzakciós termék.

## Merge- és rendszerbesorolás

| Rendszer / merge | Besorolás | Arányossági döntés |
|---|---|---|
| #26 persistent-store wiring | `KEEP_AS_IS` | A kihagyott store nem töltődik vagy mentődik. Kis wiring-javítás, közvetlen progresszióvédelem. |
| #31 `PersistentStoreCoordinator` | `SAFETY_CRITICAL_KEEP` | Corrupt vagy partial load után ne induljon írható részállapot; autosave és shutdown ne fusson össze. |
| Globális kritikus write gate | `SIMPLIFIED` a DEV scope-ban | A DEV manager már a saját state fájljának write-health állapotát figyeli; más feature hibája nem állítja le. |
| #32 DEV-item durable singleton state | `SAFETY_CRITICAL_KEEP` | Megőrzi az egyedi item identitását, tulajdonosát, idejét, pity állapotát és pontos pending jutalmát. |
| #33 többrétegű DEV delivery protocol | `SIMPLIFIED` | Egyetlen exact pending snapshot és lokális owner fence váltotta; nincs production compatibility igény. |
| #34 season/community generation marker | `KEEP_AS_IS` | Egy mezős marker akadályozza meg, hogy új szezonban régi community progressz éljen tovább. |
| #35 treasury és monument idempotens grant | `SAFETY_CRITICAL_KEEP` | Tartós kasszajutalom és a Korszakok Könyve sora normál replay során ne duplikálódjon. |
| #35 member reward playerdata protocol | `SIMPLIFY` | A pending tagjutalom és full-inventory retry kell; külön, fókuszált scope-ban egyszerűsítendő. |
| Season announcement/story pending flag | `REMOVE_REDUNDANT_LAYER` | Chat és narratív kiírás best-effort; ne blokkolja a tartós jutalmat. |
| `TransactionJournal` + globális currency gate | `NEEDS_RUNTIME_VALIDATION` / `SIMPLIFY` | Valós item- és pénzvesztést véd, de külön market scope szükséges. |
| `BlockRegenJournal` | `NEEDS_RUNTIME_VALIDATION` | A snapshot-before-clear fontos; valós Folia/process-kill ellenőrzés még nem futott. |
| Forrásszöveg-sorrendet vizsgáló regressziók | `REPLACED_WITH_BEHAVIOURAL_TESTS` | Megfigyelhető transition viselkedést és determinisztikus race-eket ellenőrző tesztek futnak. |

## Végleges DEV reward modell

Megtartott invariánsok:

- az autoritatív singleton instance és owner tartós;
- az aktív idő és pity számlálók restart után megmaradnak;
- a pontosan kisorsolt `ItemStack` a live inventory előtt tartós pending rekordba kerül;
- teljes inventory esetén a jutalom és minden progressz változatlanul pending marad;
- normál restart ugyanazt az exact itemet próbálja újra, újrasorsolás nélkül;
- sikeres átadás után a pending törlődik, a progress resetelődik és a pity frissül;
- completion write hiba esetén az in-process inventory mutation rollbackel;
- owner UUID és monoton runtime generation együtt fence-eli a tickeket;
- stale tick nem törölhet pendinget, nem resetelhet progresszt és nem módosíthat pityt;
- az új owner csak sikeres owner snapshot write után válik live autoritássá;
- owner transfer alatt a pending exact item, progress és pity megmarad;
- két átfedő ticket egy lokális coalescing gate akadályoz meg;
- corrupt vagy részleges DEV state továbbra is fail-closed.

Eltávolított rétegek:

- külön delivery azonosító és címzettállapot;
- játékosadatba írt delivery nyugta;
- explicit játékosadat-mentési commit protocol;
- többlépcsős delivery decision state machine;
- címzett-átruházási recovery;
- nem létező production állapothoz készült compatibility és migration ágak;
- alternatív async save queue;
- implementációs metódussorrendet vizsgáló tesztek.

## Owner-transfer race

A korábbi live reload útvonalon a régi owner entity schedulerén futó tick durable prepare után is
folytathatta az inventory mutationt, miközben a reload már új ownert publikált. A completion csak a
pending metadata azonosságát ellenőrizte, ezért stale tick törölhette az új ownerhez megőrzendő
pendinget, resetelhette a progresszt és módosíthatta a pityt.

A javított sorrend:

1. a tick rögzíti az aktuális owner UUID + generation fence-et;
2. a pending prepare csak ezzel a fence-szel commitolható;
3. owner transfer immutable candidate snapshotot készít az új ownerrel;
4. a candidate előbb tartósan kiíródik;
5. csak sikeres write után publikálódik a live owner és az új generation;
6. completion csak azonos fence, actor UUID és exact pending item mellett commitol;
7. mismatch esetén az inventory rollbackel, a pending/progress/pity változatlan marad.

## Tesztek

A dependency-free regression suite két részből áll:

- strict state metadata validáció;
- viselkedési pending/retry és owner-fence regressziók.

A viselkedési suite ellenőrzi:

- durable prepare megelőzi az inventory mutationt;
- prepare write failure nem hagy ghost pendinget;
- full inventory nem okoz részleges deliveryt;
- restart az exact amount/meta/affix/stamp adatokat őrzi;
- completion write failure rollbackel és retryzható;
- owner transfer durable prepare után fence-eli a stale tick-et;
- owner transfer inventory mutation után rollbackeli a stale deliveryt;
- sikertelen owner snapshot write nem publikál új live ownert;
- átfedő tickek nem lépnek be egyszerre;
- AIR, nulla amount és ismeretlen rarity elutasított.

A concurrency fixture-ek `CountDownLatch`-ot használnak; nincs időzítésfüggő `sleep`.
A `devItemRewardRegressionTest` Gradle `JavaExec` task a `check` lifecycle része, ezért a
`./gradlew clean build` ténylegesen futtatja a suite-ot. A Python driver kiegészítő célzott futtatási
út és obsolete-path guard.

## CI és consistency

A tartós `.github/workflows/ci.yml` workflow:

- pull requesten és master pushon automatikusan fut;
- kézzel is indítható;
- `contents: read` jogosultságot használ;
- validálja a Gradle wrappert;
- Java 21 környezetben teljes clean buildet futtat;
- log-markerrel ellenőrzi, hogy a Gradle `check` elindította a DEV suite-ot;
- külön futtatja a Python DEV regressziót;
- `git diff --check`-et futtat;
- base/head consistency eredményt hasonlít össze;
- új FAIL, WARN, diagnosztika vagy kategória esetén megbukik.

A végleges headhez tartozó konkrét run ID és eredmények a PR leírásában szerepelnek.

## Finding-státuszok

- `SIMPLIFIED`: DEV-item pending reward delivery és owner-transfer kezelés.
- `FIXED`: stale owner tick completion, owner publication-before-write, más store hibájára reagáló DEV global gate.
- `SAFETY_CRITICAL_KEEP`: strict store load, coordinator, DEV singleton state, season generation marker,
  treasury/monument idempotens grant.
- `OVERENGINEERED_SIMPLIFICATION_NEEDED`: season member reward protocol, market currency gate/recovery.
- `NEEDS_RUNTIME_VALIDATION`: BlockRegenJournal replay, TransactionJournal recovery,
  season playerdata delivery és valódi Folia ownership útvonalak.
- `NOT_REVALIDATED`: a történeti audit minden más findingje.

## Célzottan újraellenőrzött régi findingek

- `CRIT-08 / TransientEntities`: `PARTIALLY_FIXED` — saját EntityScheduler és heartbeat van;
  a teljes world-event call graph valódi Folia runtime-ja nem futott.
- `HIGH-35 / party proximity`: `STILL_OPEN` — távoli member world/location olvasásnál jogos XP vagy
  loot maradhat el.
- `CRIT-03 / ritual outcome-before-consume`: `STILL_OPEN` — a vezérlési sorrend az aktuális masteren
  továbbra is fennáll.
- A történeti audit többi findingje: `NOT_REVALIDATED`.

## Aktuális gameplay-prioritások

1. Party XP/personal-loot proximity valódi Folia owner-scheduler snapshotokkal.
2. Rituálé outcome és áldozatfogyasztás commit-sorrendje.
3. Block-regen tile-NBT replay valódi Folia/process-kill ellenőrzése.
4. Season member reward protocol külön egyszerűsítési scope-ban.
5. Market journal és globális currency gate külön gazdasági scope-ban.

## Nem futtatott ellenőrzések

- valódi Folia 1.21.11 több-régiós szerver;
- process-kill az inventory mutation és YAML completion között;
- ENOSPC vagy permission-denied fault-injection;
- teljes production plugin-stackkel integrációs playtest.
