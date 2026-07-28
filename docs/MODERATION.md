# Natív IceSMP moderáció

Ez a dokumentum a `feature/native-moderation-suite` scope bizonyítható állapotát írja le. Nem állít SModeration- vagy InvSee++-paritást, és nem tartalmaz legacy migrációt.

## Architektúra

A `ModerationManager` az egyetlen autoritatív moderációs store. A `moderation-data.yml` sémája tartalmazza a punishment ledgert, a SocialSpy- és vanish-beállításokat, valamint az utolsó kijelentkezési helyeket. A manager a közös `PersistentStoreCoordinator` tagja; mentéshez `YamlStore.saveAtomic` útvonalat használ.

Minden mutáció:

1. async schedulerre kerül, hogy a fájl-I/O ne fusson region threaden;
2. lock alatt memóriabeli snapshotot készít;
3. alkalmazza a domainmutációt;
4. atomikusan ment;
5. hiba esetén visszaállítja a snapshotot;
6. kritikus autoritatív írási hibánál a meglévő persistence circuit-breaker letiltja a plugint.

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

A privát üzenetet a címzett entity schedulere kézbesíti. A feladó csak a tényleges címzett-task lefutása után kap sikervisszajelzést és csak ekkor épül fel a `/reply` kapcsolat. A SocialSpy külön jelzi a `DELIVERED`, `BLOCKED_MUTED`, `BLOCKED_FILTER`, `BLOCKED_SPAM`, `TARGET_OFFLINE` és `TARGET_RETIRED` állapotokat; nem végez packet interceptiont.

## Vanish

A visibility művelet a néző player schedulerén fut, támogatott `hidePlayer/showPlayer` API-val. Az IceSMP csak a saját maga által elrejtett viewer–subject párokat tartja nyilván, ezért nem old fel más plugin által létrehozott rejtést.

Kezelt lifecycle: relog, world change, config reload, quit/kick transient cleanup és plugin disable. A tartós vanish-beállítás relog után is megmarad; a config kapcsolja az item pickupot, damage-et, interactiont és az online/MOTD countból való kizárást. Mobok nem célozhatják a vanished játékost.

## Online inventory és ender chest

Nincs offline playerdata parser. A cél inventoryjának olvasása/írása kizárólag a cél entity schedulerén, az admin GUI/cursor műveletei kizárólag az admin entity schedulerén történnek. Az edit művelet két scheduler-hopja között `InventoryEscrowGate` biztosítja, hogy:

- a kurzorról kivett tárgynak pontosan egy tulajdonosa legyen;
- a cél slotból kiszorított tárgyat pontosan egy completion/cleanup út adja vissza;
- target disconnect előtt a beillesztett tárgy visszakerüljön;
- admin disconnect után a kiszorított tárgy reconnectkor kerüljön vissza.

Sikeres edit külön `logs/moderation-audit.log` sort ír. A runtime reconnect-escrow memóriabeli; process crash közbeni exactly-once garanciát a kód nem állít.

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

A `moderationRegressionTest` valódi domain-viselkedést tesztel: restart snapshot, temp expiry, ban read model, kétirányúan validált unmute/unban auditlink, warning/kick history, duplikált/ellentmondó/árva revocation/partial state, kétirányú inventory single-claim és non-finite location. A teljes Gradle build fordítja a Paper-integrációt.

A valódi Folia scheduler-ownership, reconnect, reload, permission és fault-injection esetekhez továbbra is runtime playtest kell; enélkül az SModeration és InvSee++ eltávolítása nem minősül véglegesen igazoltnak.
