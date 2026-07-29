# Natív IceSMP moderáció

Ez a dokumentum a `feature/native-moderation-suite` scope bizonyítható állapotát írja le. Nem állít SModeration- vagy InvSee++-paritást, és nem tartalmaz legacy migrációt.

## Architektúra

A `ModerationManager` a punishment/visibility autoritatív store. A `moderation-data.yml` sémája tartalmazza a punishment ledgert, a SocialSpy- és vanish-beállításokat, valamint az utolsó kijelentkezési helyeket. Az invsee visszaadandó tárgyai külön `invsee-escrow.yml` autoritatív store-ban élnek. Mindkettő a közös `PersistentStoreCoordinator` tagja és `YamlStore.saveAtomic` mentést használ.

Minden mutáció:

1. async schedulerre kerül, hogy a fájl-I/O ne fusson region threaden;
2. lock alatt memóriabeli snapshotot készít;
3. alkalmazza a domainmutációt;
4. atomikusan ment;
5. hiba esetén visszaállítja a snapshotot;
6. kritikus autoritatív írási hibánál a persistence circuit-breaker azonnal lezárja az új mutációk admission gate-jét, majd letiltja a plugint.

A sérült vagy részleges state betöltése `YamlStore.failCorrupt` útvonalon fail-closed. Nincs schema migration: a szerver még nem futott productionben.

## Punishment modell

Típusok: `WARNING`, `KICK`, `MUTE`, `TEMPORARY_MUTE`, `BAN`, `TEMPORARY_BAN`, `UNMUTE`, `UNBAN`.

Minden rekord stabil UUID-t, cél- és adminazonosítót/nevet, okot, létrehozási és opcionális lejárati időt, lifecycle state-et, visszavonási auditot és kapcsolt punishment ID-t tárol. Egy céljátékosnak családonként legfeljebb egy aktív MUTE és egy aktív BAN rekordja lehet. A temp rekord a lejárat pillanatától logikailag inaktív; a karbantartási tick ezt `EXPIRED` state-ként is materializálja.

## Parancsok

| Parancs | Funkció | Permission |
|---|---|---|
| `/warn <játékos> [ok]` | figyelmeztetés | `icesmp.moderation.warn` |
| `/kick <játékos> [ok]` | online játékos kirúgása | `icesmp.moderation.kick` |
| `/mute <játékos> [30m\|2h\|7d\|végleges] [ok]` | eszkalált, időzített vagy permanens némítás | `icesmp.moderation.mute` |
| `/unmute <játékos> [ok]` | aktív némítás visszavonása | `icesmp.moderation.mute` |
| `/ban <játékos> [ok]` | permanens ban | `icesmp.moderation.ban` |
| `/tempban <játékos> <idő> [ok]` | maximum 365 napos tempban | `icesmp.moderation.ban` |
| `/unban <játékos> [ok]` | aktív ban visszavonása | `icesmp.moderation.ban` |
| `/history <játékos> [oldal]` | teljes, lapozható history | `icesmp.moderation.history` |
| `/punishments [játékos]` | aktív büntetések | `icesmp.moderation.history` |
| `/moderation` | permission-szűrt admin GUI | `icesmp.moderation.gui` |
| `/socialspy` | tartós SocialSpy toggle | `icesmp.moderation.socialspy` |
| `/vanish [online játékos]` | tartós vanish | `icesmp.moderation.vanish` |
| `/offlinetp <játékos>` | utolsó kijelentkezési hely | `icesmp.moderation.offlinetp` |
| `/invsee <játékos> [read\|edit] [main\|ender]` | online live inv/ender nézet | read: `icesmp.moderation.inventory.read`; edit: `.edit` |
| `/msg`, `/tell`, `/w`, `/reply` | natív privát üzenetútvonal | `icesmp.message` |

A `icesmp.admin.moderation` parent kiosztja az adminjogokat. A vanished admin megtekintéséhez külön `icesmp.moderation.vanish.see` jog kell.

## Ban enforcement és privát üzenetek

Az async pre-login listener csak a szálbiztos ledger-read modellt olvassa. Aktív ban esetén okot és tempbannál hátralévő időt ad vissza. Lejárt tempban nem blokkol.

A privát üzenetet a címzett entity schedulere kézbesíti. A feladó csak a tényleges címzett-task lefutása után kap sikervisszajelzést. A `/reply` kapcsolat mindkét játékos aktuális join-session generációját tartalmazza: quit/kick lezárja a generációt és atomi módon törli a kétirányú linket, ezért a scheduler-hop után későn befejeződő callback nem írhat stale partnert egy újracsatlakozott sessionbe. A SocialSpy külön jelzi a `DELIVERED`, `BLOCKED_MUTED`, `BLOCKED_FILTER`, `BLOCKED_SPAM`, `TARGET_OFFLINE` és `TARGET_RETIRED` állapotokat; a spy-játékosok feloldása global scheduleren, a kézbesítés a saját entity schedulerükön történik. Nem végez packet interceptiont.

Az async chat-listener csak UUID/név/text snapshotot visz át: a blokkoló visszajelzés mindig a küldő entity schedulerén fut. A ban mentése utáni kick UUID alapján az aktuális sessiont oldja fel, és csak addig fut le, amíg ugyanaz a banrekord aktív; egy közben végrehajtott unban nem okozhat késői kirúgást.

## Vanish

A visibility művelet a néző player schedulerén fut, támogatott `hidePlayer/showPlayer` API-val. Az IceSMP csak a saját maga által elrejtett viewer–subject párokat tartja nyilván, ezért nem old fel más plugin által létrehozott rejtést.

Kezelt lifecycle: relog, world change, config reload, quit/kick transient cleanup és plugin disable. A tartós vanish-beállítás relog után is megmarad; a config kapcsolja az item pickupot, damage-et, interactiont és az online/MOTD countból való kizárást. Mobok nem célozhatják a vanished játékost.

## Online inventory és ender chest

Nincs offline playerdata parser. A cél inventoryjának olvasása/írása kizárólag a cél entity schedulerén, az admin GUI/cursor műveletei kizárólag az admin entity schedulerén történnek. Az edit művelet két scheduler-hopja között `InventoryEscrowGate` biztosítja, hogy:

- a kurzorról kivett tárgynak pontosan egy tulajdonosa legyen;
- a cél slotból kiszorított tárgyat pontosan egy completion/cleanup út adja vissza;
- target disconnect előtt a beillesztett tárgy visszakerüljön;
- admin disconnect után a kiszorított tárgy reconnectkor kerüljön vissza.

Sikeres edit külön `logs/moderation-audit.log` sort ír. A kiszorított/visszaadandó stack előbb a közös lifecycle-ba regisztrált `invsee-escrow.yml` memóriasnapshotjába kerül, és csak ezután válik a target-write `COMPLETE` állapota láthatóvá vagy indul viewer callback. A return queue count-preserving: egy claim pontosan egy rekordot vesz át, a többi azonos stack megmarad; sikertelen inventory-visszaadás a rekordot a queue elejére teszi vissza. Disable előtt az edit admission gate lezár, az összes már befogadott target/viewer callback drainelődik, majd a végső common save rögzíti a fennmaradó escrow-t. A strict decode ismeretlen root kulcsot, hibás schema-verziót, corrupt vagy duplikált UUID-t, üres/null payloadot és count overflowt fail-closed elutasít. Így normál reload/disable/restart alatt a visszaadás nem csak process-memóriára támaszkodik.

Minden érintett nullable entity-scheduler submit közös single-winner kapun fut. Submit-kivétel, `null` handle és retirement ugyanazt az idempotens fallbacket használja; a task és fallback közül legfeljebb egy futhat. Az invsee repeating refresh handle `TaskLease`-be kerül, ezért a handle publikálása előtt lefutó retirement sem hagy stale taskot a sessionben.

**Garanciahatár:** nincs több-store/player-inventory WAL vagy formális exactly-once protokoll. A process azonnali megszakítása a target inventory írása és a következő tartós escrow-save között elvesztést, illetve a reconnect-visszaadás és az azt követő save között ismételt visszaadást okozhat. Ezekhez külön crash-fault-injection és tartós kétfázisú journal kellene; a kód csak a kontrollált lifecycle-t teszi determinisztikussá.

## Config

`config/moderation.yml`:

- `moderation.chat-filter.enabled|mode|words`;
- `moderation.spam.enabled|min-interval-millis|duplicate-window-seconds`;
- `moderation.escalation-minutes`;
- `moderation.chat-log.enabled`;
- `moderation.vanish.exclude-from-online-count`;
- `moderation.vanish.allow-item-pickup|allow-damage|allow-interaction`.

A runtime immutable validált snapshotot használ. Ismeretlen filter mode fail-safe `BLOCK`, hibás spam/escalation érték teljes listás alapérték-visszaesést kap; negatív érték nem kapcsolhatja ki csendben a védelmet.

## Automatizált bizonyíték és kézi korlát

A `moderationRegressionTest` valódi domain-viselkedést tesztel: restart snapshot, temp expiry, ban read model, kétirányúan validált unmute/unban auditlink, warning/kick history, duplikált/ellentmondó/árva revocation/partial state, inventory single-claim + target-write rollback, shutdown admission/drain gate, strict escrow schema és count overflow, scheduler task/retirement single-winner, submit exception/null fallback, repeating-task publish race, valamint `/reply` same-generation és quit–reconnect interleaving. A teljes Gradle build fordítja a Paper-integrációt, a forrásinvariánsok pedig a scheduler-, permission- és common-persistence wiringot őrzik.

A valódi Folia scheduler-ownership, reconnect, reload, permission és fault-injection esetekhez továbbra is runtime playtest kell; enélkül az SModeration és InvSee++ eltávolítása nem minősül véglegesen igazoltnak.
