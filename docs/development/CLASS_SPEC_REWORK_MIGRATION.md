# Kaszt- és specializáció-rework — legacy → Profile v2 migráció

## Cél és biztonsági határ

A migráció játékosonként, joinkor fut, kizárólag akkor, ha `class-spec-rework.enabled: true`, nincs
már tartós Profile v2, és a legacy snapshot biztonságosan elkészült. A migrátor nem spawnol petet
vagy miniont, nem ad jutalmat, nem aktivál kétes specializációt, és nem töröl legacy adatot a Profile
v2 sikeres tartós mentése előtt.

A migráció eredménye vagy teljesen commitolt v2 profil, vagy diagnosztizálható review/quarantine.
Részleges v2 runtime nincs.

## Legacy snapshot

A player/PDC/inventory-olvasás a játékos saját Folia schedulerén történik. Az I/O- és codec-rétegbe
csak immutable snapshot jut. A reader legalább ezeket gyűjti:

- elsődleges kaszt és a `JobManager` szerinti kasztszint;
- jelenlegi class specialization;
- kiválasztott és kedvenc spellek;
- megőrzendő, kompatibilis petadat;
- Nekromanta Soulforge-rangok;
- minden ténylegesen létező, korlátozott méretben megőrizhető class/spec mechanikai kulcs;
- a forráskulcs és normalizált alakja az ütközések felismeréséhez.

Companionból megőrizhető a logikai típus, név, szint, XP, stance, trait/mutáció, modulok és
újraidézési idő. Élő entity UUID, Bukkit `Entity`, `Player`, `ItemStack` vagy runtime task nem kerül a
snapshotba. Ismeretlen mező csak explicit byte-/elem-/szöveghatárral őrizhető meg.

## Normalizálás és besorolás

A kaszt- és spec ID-k a közös 13/35 katalógus szerint normalizálódnak. Két külön legacy kulcs azonos
normalizált kulcsa ütközés: egyik érték sem írhatja felül a másikat. Ismeretlen spec, rossz parent
class, többértelmű pet-eredet, orphaned Soulforge vagy ismeretlen mechanikai kulcs review okot hoz
létre.

Egyértelmű migráció alapképe:

- `schemaVersion = 2`;
- `revision = 0`;
- `ProfileStatus = READY`;
- slot 1 = a jelenlegi érvényes spec;
- slot 2 = `EMPTY`;
- `activeSlot = 1`, ha a spec minden invariánst és kaput teljesít; egyébként nincs aktív slot;
- `secondSpecUnlocked = false`;
- kasztszint = ellenőrzött `JobManager`-tükör.

Spec nélküli játékos érvényes, két üres slottal és aktív slot nélkül migrálható. A migrátor nem talál
ki második specet és nem alakít át 36. vagy lore-only specializációt.

## Spell- és companion-mapping

A kiválasztott és kedvenc spellek először korlátozott previewként megmaradnak, majd csak a registry-
és parent-spec validáción átesett értékek kerülnek az aktív loadoutba. Migráció közben nincs grant;
a spell provenance reconcile csak sikeres persist és teljes profilvalidáció után fut.

Companion csak az alábbi névterek egyikébe sorolható:

| Spec | Névterület |
| --- | --- |
| `beast_master` | `beast_master.stable` |
| `necromancer` | `necromancer.court` |
| `unholy` | `unholy.ghoul` |
| `demonologist` | `demonologist.roster` |

Nem bizonyítható eredetű companion megmarad a migrációs snapshotban, de nem aktiválódik. A
Nekromanta melletti Soulforge a `necromancer` loadout mechanikai állapotába kerül; Nekromanta
nélküli Soulforge orphaned adatként megmarad és `MIGRATION_REVIEW` állapotot okoz.

## Review és quarantine

`MIGRATION_REVIEW` szükséges többek között:

- ismeretlen vagy parent classhoz nem illő spec;
- normalizált kulcsütközés;
- többértelmű vagy rossz specbe tartozó companion;
- orphaned Soulforge;
- biztonságosan megőrzött, de nem besorolható mechanikai adat;
- olyan legacy érték, amelynek elvesztése valós játékosprogresszt törölhetne.

A review profil megőrzi az eredeti értékek korlátozott diagnosztikai előnézetét és az okkódot, de nem
aktivál class/spec runtime-ot. Általános admin reset nem törölheti.

`CORRUPT_QUARANTINE` decode-, checksum-, formátum- vagy persistence-sérüléshez tartozik. Az eredeti
payloadot változatlanul félre kell tenni; tilos automatikusan üres profillal felülírni. Quarantine-t
faction-, sinner- vagy questesemény nem oldhat fel.

## Idempotencia és commit-protokoll

A migráció stabil azonosítóval és játékosonként sorosított művelettel fut:

1. v2 repository load; létező profil esetén nincs legacy migráció;
2. immutable legacy snapshot és fingerprint készítése;
3. tiszta, determinisztikus migráció és teljes domainvalidáció;
4. CAS `save(-1, revision=0)`;
5. ugyanannak a payloadnak visszaolvasása/ellenőrzése;
6. csak ezután publikálható a cache és jelölhető sikeresnek a migráció;
7. gate- és spell-reconcile csak a commit után indul.

Ha két login/retry versenyez, csak az egyik `-1 → 0` mentés nyerhet. A vesztes újratölt, és a már
mentett profilt használja; nem duplikál rostert, jutalmat vagy grantet. A fingerprint és a sikeres
migrációazonosító diagnosztikai célú, nem helyettesíti a repository CAS-t.

Persistence-hibánál a legacy forrás változatlan marad, a jelölt profil nem válik autoritatívvá, a
session class/spec része blokkolódik, és az admin `/spec info` diagnosztikája jelzi az okot. A
migráció soha nem jelenthet sikert egy sikertelen persist után.

## Legacy cleanup és rollback

A rollout alatt a legacy mezők recovery-mirrorként megmaradnak. Törlésük külön, későbbi,
verziózott cleanup-migráció lehet csak, miután:

- a Profile v2 tartósan visszaolvasható;
- a rollbackpróba sikeres;
- nincs review/quarantine;
- a retention idő és az üzemeltetői jóváhagyás teljesült.

A feature flag kikapcsolása nem konvertál vissza v2 profilt legacyvá és nem töröl v2 adatot. Rollback
előtt mindig teljes pluginadat-mentés szükséges; az admin lépések a
[runbookban](../admin/CLASS_SPEC_REWORK_RUNBOOK.md) találhatók.
