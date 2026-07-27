# IceSMP audit update — b6db9d2

> **Auditált master:** `b6db9d21d12a2944b67925a5fe9228b4e76b9b04`  
> **Dátum:** 2026-07-27  
> **Hatókör:** a #26, #31, #32, #33, #34 és #35 merge-ek arányossági/overengineering auditja,
> valamint a DEV-item pending reward célzott egyszerűsítése.  
> **Nem állítás:** a korábbi teljes mélyaudit minden findingje nem lett újraellenőrizve.

## Termékdefiníció

Az IceSMP elsődlegesen lore-központú fantasy kingdom SMP: kaszt- és karakterépítés, frakciók,
politika, questek, szakmák, relikviák, raidek, kazamaták, világesemények, felfedezés és közösségi
szezonok együtt adják az élményt. A gazdaság és a perzisztencia ezeket védi; nem önálló banki vagy
általános tranzakciós termék.

## Merge- és rendszerbesorolás

| Rendszer / merge | Besorolás | Valós hiba és arányossági döntés |
|---|---|---|
| #26 persistent-store wiring | `KEEP_AS_IS` | A kihagyott store nem töltődik/mentődik. Kis wiring-javítás, közvetlen progresszióvédelem. |
| #31 `PersistentStoreCoordinator` | `SAFETY_CRITICAL_KEEP` | Corrupt/partial load után ne induljon írható részállapot; autosave és shutdown ne fusson össze. A state machine kicsi és érthető. |
| Globális kritikus write gate | `SIMPLIFY` | Íráshiba után fail-closed kell, de egy DEV-item hiba ne fagyasszon le automatikusan minden más kritikus feature-t. Per-feature health gate javasolt külön scope-ban. |
| #32 DEV-item durable singleton state | `SAFETY_CRITICAL_KEEP` | Megőrzi az egyedi item identitását, tulajdonosát, idejét, pity állapotát és pontos pending jutalmát. |
| #33 DEV grant-ID + recipient + player-PDC receipt | `SIMPLIFIED` | Valós célja a dupe csökkentése volt, de egy ritka DEV-jutalomhoz túl sok tartós tanú és recovery ág került. Egyetlen pending snapshot + retry váltotta. |
| #34 season/community generation marker | `KEEP_AS_IS` | Egy mezős marker megakadályozza, hogy új szezonban régi community progressz éljen tovább. Kis komplexitás, valós szezon-invariáns. |
| #35 treasury és monument grant receipt | `SAFETY_CRITICAL_KEEP` | Tartós kasszajutalom és a Korszakok Könyve sora normál replay során ne duplikálódjon. Lokális, feature-specifikus receipt. |
| #35 member reward PDC receipt/saveData protocol | `SIMPLIFY` | A pending tagjutalom és full-inventory retry kell; a playerdata receipt, effekt/inventory rollback és UUID-halmaz külön scope-ban egyszerűsítendő. |
| Season announcement/story pending flag | `REMOVE_REDUNDANT_LAYER` | Chat és narratív kiírás kozmetikai/best-effort; ne blokkolja a tartós jutalmat, és ne kapjon recovery state-et. |
| `TransactionJournal` + globális currency gate | `NEEDS_RUNTIME_VALIDATION` / `SIMPLIFY` | A market valódi item/pénzvesztést véd, de a globális gate és abszolút balance-repair túl széles. Külön market scope szükséges. |
| `BlockRegenJournal` | `NEEDS_RUNTIME_VALIDATION` | A konténer snapshot-before-clear fontos. Az APPLYING/APPLIED modell valós Folia restart teszt nélkül nem nevezhető pontosan egyszerinek. |
| Forrásszöveg-sorrendet vizsgáló regressziók | `REPLACE_WITH_EXISTING_PROJECT_PATTERN` | Törékeny implementációteszt helyett Bukkit-független állapot-invariáns teszt fut. |

## Ebben a branchben egyszerűsített DEV-item modell

Megtartott invariánsok:

- az autoritatív singleton instance és owner tartós;
- az aktív idő és pity számlálók restart után megmaradnak;
- a pontosan kisorsolt ItemStack a live inventory előtt tartós pending rekordba kerül;
- teljes inventory esetén a jutalom pending marad;
- normál restart/replay ugyanazt a pending jutalmat próbálja újra;
- sikeres átadás után a pending törlése szinkron mentés;
- write hiba esetén az élő inventory-módosítás visszaáll.

Eltávolított rétegek és állapotok:

- `pending.grant-id`;
- `pending.recipient`;
- `dev_reward_receipt` játékos-PDC;
- explicit `Player.saveData()` commit;
- `DELIVER / ACKNOWLEDGE / WAIT_FOR_RECORDED_RECIPIENT` döntési ágak;
- címzett-átruházási recovery;
- forrásszöveg-metódussorrendet ellenőrző Python-teszt.

Feladott elméleti garancia:

- nincs formális exactly-once bizonyítás erőszakos process-killre az inventory item hozzáadása és
  a pending YAML törlésének befejezése közötti szűk ablakban;
- kézzel sérült playerdata és minden lehetséges storage kombinációja nem kap külön állapotgépet.

Ez egy ritka DEV-item időalapú jutalma. A gyakorlati szerverüzemhez a tartós pending, a teljes
inventory retry és a normál restart recovery arányos védelmet ad lényegesen kisebb mentális és
tartós állapotkomplexitással.

## Metrikák

| Mérőszám | Master | Branch |
|---|---:|---:|
| `DevItemManager.java` sor | `1465` | `1270` |
| `DevItemStateData.java` sor | `123` | `77` |
| DEV tartós pending mezők | 5 | 3 |
| Player-PDC receipt kulcs | 1 | 0 |
| Delivery decision ág | 3 | 0 |
| Tartós transition helper | 4 | 2 |
| Bukkit-független regressziós teszteset | 10 | 7 |
| Érintett Java-fájl | — | 3 |
| Hozzáadott / törölt sor összesen | — | `117 / 469` |

## Futtatott ellenőrzések

Baseline master:

- `./gradlew clean build --no-daemon --stacktrace` — `PASS`
- `python3 scripts/check_consistency.py` — `6 FAIL / 0 WARN (baseline, exit 1)`
- `python3 scripts/test_dev_item_state.py` — `PASS`

Branch:

- `./gradlew clean build --no-daemon --stacktrace` — `PASS`
- `python3 scripts/check_consistency.py` — `6 FAIL / 0 WARN (no new drift, exit 1)`
- `python3 scripts/test_dev_item_state.py` — `PASS`

## Finding-státuszok

- `SIMPLIFIED`: DEV-item pending reward receipt/outbox réteg.
- `SAFETY_CRITICAL_KEEP`: strict store load, coordinator, DEV singleton state, season generation marker,
  treasury/monument idempotens grant.
- `OVERENGINEERED_SIMPLIFICATION_NEEDED`: season member PDC receipt és kozmetikai batch flag-ek;
  globális kritikus write gate; market currency gate/journal recovery.
- `NEEDS_RUNTIME_VALIDATION`: BlockRegenJournal konténer-replay, TransactionJournal market recovery,
  season playerdata delivery és valódi Folia ownership útvonalak.
- `NOT_REVALIDATED`: a történeti audit minden más findingje.

## Célzottan újraellenőrzött régi findingek

- `CRIT-08 / TransientEntities`: `PARTIALLY_FIXED` — a registry saját EntitySchedulert és
  heartbeatet használ; a teljes world-event call graph valódi Folia runtime-ja nem futott.
- `HIGH-35 / party proximity`: `STILL_OPEN` — a távoli member world/location olvasása
  továbbra is cross-region try/catch mintára épül, ezért jogos XP/loot maradhat el.
- `CRIT-03 / ritual outcome-before-consume`: `STILL_OPEN` — a konkrét vezérlési sorrend az
  aktuális masteren is fennáll.
- A történeti audit többi findingje: `NOT_REVALIDATED`.

## Aktuális gameplay-prioritások

1. Party XP/personal-loot proximity: a jelenlegi `getNearbyMembers` idegen régiós
   `Player` location/world olvasást próbál és kivételnél jogos tagot hagy ki (`STILL_OPEN`).
2. Rituálék: a tartós/teleport/buff hatás jelenleg az általános áldozatfogyasztás előtt fut;
   a `home` future eredménye sincs a commit feltételéhez kötve (`STILL_OPEN`).
3. Block-regen tile-NBT replay valódi Folia/process-kill ellenőrzése (`NEEDS_RUNTIME_VALIDATION`).
4. Season member reward PDC/saveData protokoll egyszerűsítése külön branchben.
5. Market journal és globális currency gate külön, szűk gazdasági scope-ban.

## Nem futtatott ellenőrzések

- valódi Folia 1.21.11 több-régiós szerver;
- process-kill/fault-injection az inventory és YAML írás közti pontokon;
- ENOSPC, permission-denied és fizikailag sérült playerdata;
- teljes production plugin-stackkel integrációs teszt.
