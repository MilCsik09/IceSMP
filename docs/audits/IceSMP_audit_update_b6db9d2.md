# IceSMP audit update — b6db9d2

> **Auditált master:** `b6db9d21d12a2944b67925a5fe9228b4e76b9b04`
> **Dátum:** 2026-07-27
> **Hatókör:** a #26, #31, #32, #33, #34 és #35 merge-ek arányossági auditja,
> valamint a DEV-item jutalmazás célzott egyszerűsítése.
> **Nem állítás:** a korábbi teljes mélyaudit minden findingje nem lett újraellenőrizve.

## Termékdefiníció

Az IceSMP elsődlegesen lore-központú fantasy kingdom SMP. A Csodálatos Bingulus ezen belül egy
könnyűsúlyú easter egg DEV-item, nem pénzügyi rendszer és nem általános tranzakciós platform.
Alapértelmezetten 10 perc aktív online birtoklás után ad egy random konfigurált jutalmat.

## Merge- és rendszerbesorolás

| Rendszer / merge | Besorolás | Arányossági döntés |
|---|---|---|
| #26 persistent-store wiring | `KEEP_AS_IS` | A kihagyott store nem töltődik vagy mentődik. Kis wiring-javítás, közvetlen progresszióvédelem. |
| #31 `PersistentStoreCoordinator` | `SAFETY_CRITICAL_KEEP` | Corrupt vagy partial load után ne induljon írható részállapot; autosave és shutdown ne fusson össze. |
| Globális kritikus write gate | `REMOVED_FROM_DEV` | A Bingulus csak a saját state fájljának health állapotát figyeli; más feature hibája nem állítja le, és a DEV write hiba nem állítja le a market/currency rendszert. |
| #32 DEV-item durable singleton state | `SIMPLIFIED_KEEP` | Owner, instance, issued, aktív idő, pity és exact pending item megmarad egy immutable state-ben. |
| #33 többrétegű DEV delivery protocol | `REMOVED` | Nincs receipt, grant ID, recipient, migration, reconciliation vagy exactly-once protocol. |
| #34 season/community generation marker | `KEEP_AS_IS` | Egy mezős marker akadályozza meg, hogy új szezonban régi community progressz éljen tovább. |
| #35 treasury és monument idempotens grant | `SAFETY_CRITICAL_KEEP` | Tartós kasszajutalom és a Korszakok Könyve sora normál replay során ne duplikálódjon. |
| #35 member reward playerdata protocol | `SIMPLIFY` | A pending tagjutalom és full-inventory retry kell; külön, fókuszált scope-ban egyszerűsítendő. |
| Season announcement/story pending flag | `REMOVE_REDUNDANT_LAYER` | Chat és narratív kiírás best-effort; ne blokkolja a tartós jutalmat. |
| `TransactionJournal` + globális currency gate | `NEEDS_RUNTIME_VALIDATION` / `SIMPLIFY` | Valós item- és pénzvesztést véd, de külön market scope szükséges. |
| `BlockRegenJournal` | `NEEDS_RUNTIME_VALIDATION` | A snapshot-before-clear fontos; valós Folia/process-kill ellenőrzés még nem futott. |

## Végleges DEV runtime state

A `DevItemManager` egyetlen immutable state-et tart, egy lockkal:

- aktuális owner UUID;
- singleton instance UUID;
- issued állapot;
- összegyűlt aktív idő;
- opcionális pending reward: rarity, entry és klónozott exact `ItemStack`;
- a gameplayben használt három pity számláló.

A külön `DevItemRewardTransition`, owner fence/generation, state-writer interfész,
preparation/completion/transfer result recordok és párhuzamos atomikus state-mezők megszűntek.
A scheduler átfedést egyetlen minimális `AtomicBoolean` gate zárja ki.

## Lineáris tick flow

1. a globális tick kiolvassa az aktuális ownert;
2. megkeresi az online playert;
3. belép a manager egyetlen gate-jén és az entity schedulerre ütemez;
4. ellenőrzi, hogy a player még mindig az aktuális owner;
5. ellenőrzi és szükség esetén helyreállítja az autentikus singleton itemet;
6. növeli az aktív időt, legfeljebb az intervalig;
7. interval előtt befejezi a ticket;
8. pending hiányában sorsol, exact `ItemStack` snapshotot készít és inventory mutation előtt ment;
9. újra ellenőrzi az owner UUID-t;
10. teljes inventory esetén változatlan pendinggel visszatér;
11. hozzáadja a pending item klónját;
12. pending törlés előtt ismét ellenőrzi az owner UUID-t;
13. siker esetén menti a clear/reset/pity state-et, publikálja és announcementet küld.

Nincs többfázisú commit, receipt, playerdata-nyugta vagy formális inventory+YAML tranzakció.

## Live owner reload

`/icesmp reload` után az új konfigurált owner state-candidate-je megőrzi az instance ID-t, az aktív
időt, a pityt és az exact pending itemet. A candidate előbb a DEV state fájlba kerül, és csak sikeres
write után lesz a runtime state. Az online játékosok frissítése eltávolítja a régi owner példányát és
helyreállíthatja az új owner autentikus itemét.

A régi tick owner UUID-t ellenőriz a tick elején, inventoryba adás előtt és pending törlés előtt.
Mismatch esetén egyszerűen visszatér. Nincs generation counter és nincs tranzakciós owner-transfer
state machine; a nanoszekundumos szélsőséges versenyekre nem ígérünk tökéletes rollbacket.

## Normál garanciák

- csak az aktuális owner tarthat autentikus Bingulust;
- az ownernél egy autentikus példány marad;
- cursorban és ender chestben lévő tiltott/dupla példányok kezelhetők;
- respawn és normál restore után az issued singleton visszaadható;
- aktív idő csak online autentikus birtoklás közben gyűlik;
- alapértelmezetten 10 perc után történik sorsolás;
- a kisorsolt exact item inventory mutation előtt pendingként mentődik;
- full inventory után ugyanaz próbálható újra;
- normál restart nem kényszerít rerollt, amount/meta/affix/PDC/craft stamp megmarad;
- sikeres completion törli a pendinget, nullázza a progresszt és frissíti a pityt;
- malformed vagy részleges state elutasított;
- scheduler normal completion, exception, retired callback, rejection és null task után kinyitja a gate-et;
- DEV write failure után további DEV progress/sorsolás/delivery nem történik;
- a DEV store hibája nem kapcsolja le a marketet, walletet, currencyt vagy season rendszert.

## Tudatosan elfogadott edge case-ek

Nincs exactly-once garancia. Elfogadható ritka reward-vesztés vagy duplikáció:

- process kill az inventory mutation és a completion save között;
- hardverhiba vagy bizonytalan post-rename filesystem outcome;
- completion write failure az inventory mutation után;
- extrém owner-transfer race két UUID-check közötti nagyon szűk ablakban;
- plugin listener side effect, amely nem rollbackelhető általánosan.

Ezek tízpercnyi easter egg rewardhoz nem indokolnak journalt, receiptet, grant ID-t vagy
reconciliation frameworköt.

## Regressziók és Gradle

A rövid regression suite közvetlenül az immutable production state-et és a production gate-et hívja.
Lefedett:

- reward interval előtt/után;
- exact pending snapshot és caller-mutation elleni klónozás;
- normál restart/replay;
- full inventory retry;
- egyszerű owner reload és stale owner clear tiltása;
- write-failure publication boundary és független feature izoláció;
- strict state validáció;
- gate normal/exception/retired/rejection/null útjai.

Nincs `CountDownLatch` owner-transfer fixture, fake production manager vagy második control-flow
implementáció. A `devItemRewardRegressionTest` a Gradle `check` lifecycle része. A
`scripts/test_dev_item_state.py` statikus obsolete-token guard után ugyanezt a Gradle taskot hívja;
nem fordítja és futtatja újra saját tesztrendszerként.

## CI és consistency

A `.github/workflows/ci.yml` jogosultsága változatlanul csak:

```yaml
permissions:
  contents: read
```

A workflow Java 21 clean buildet, Gradle DEV-suite markert, célzott Python drivert,
`git diff --check`-et és base/head consistency-deltát futtat. A konkrét végleges run és a
base/head számlálók a PR leírásában szerepelnek.

## Finding-státuszok

- `SIMPLIFIED`: Bingulus runtime state, tick flow, pending/retry és owner reload.
- `REMOVED`: DEV transition framework, generation fence, result recordok, receipt/grant/recipient/migration ágak.
- `FIXED`: DEV write-health cross-feature coupling és beragadható scheduler gate útvonalak.
- `SAFETY_CRITICAL_KEEP`: strict store load, coordinator, singleton identity, season generation marker,
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
