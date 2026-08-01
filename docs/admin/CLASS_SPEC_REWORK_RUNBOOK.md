# Kaszt- és specializáció-rework — Profile v2 üzemeltetői runbook

Ez a kiegészítő runbook nem helyettesíti a `docs/ADMIN_GUIDE.md` kézikönyvet. A Profile v2
alapértelmezetten kikapcsolt, és az első rollout mindig staging/cohort alapú.

## Állapotok röviden

| Állapot | Class/spec runtime | Üzemeltetői jelentés |
| --- | --- | --- |
| `READY` | gate- és loadoutfüggően használható | Érvényes, tartós profil. |
| `MIGRATION_REVIEW` | blokkolt | Bizonytalan legacy adat megmaradt; explicit recovery kell. |
| `CORRUPT_QUARANTINE` | blokkolt | Sérült payload; az eredetit változatlanul meg kell őrizni. |
| session persistence block | blokkolt | A tartós profil lehet ép, de az aktuális session mentése/loadja nem biztonságos. |
| `SEALED` loadout | az adott spec blokkolt | A progressz megmaradt, valamely kapu hiányzik vagy admin/recovery seal aktív. |

A blokk csak a class/spec rendszert érinti; a játékos más szerverfunkciókat tovább használhat.

## Engedélyezés előtti kapu

1. Készüljön visszaállítható mentés a világról, playerdatáról, teljes IceSMP pluginadatról és
   resource packről.
2. Pontosan a `class-spec-dependencies.lock.yml` fájlban rögzített pluginverziók legyenek telepítve.
3. A staging szerver induljon `-Dpaper.disablePluginRemapping=true` kapcsolóval.
4. A `class-spec-rework.enabled` maradjon `false`, amíg a dependency preflight és az összes Profile
   v2 regresszió nem zöld.
5. Ellenőrizd a repository-inventory findingokat is; a workflow zöld színe önmagában nem bizonyít
   nulla review-required komponenst.
6. Készíts cohortot: új játékos, spec nélküli legacy játékos, normál spec, mind az öt DARK spec,
   companionos spec, Soulforge, valamint szándékosan review/quarantine fixture.
7. Csak sikeres rollbackpróba után kapcsold be a flaget belső cohorton; globális rollout külön lépés.

Ha a flag `false`, a Profile v2 nem olvas, nem ír és nem migrál. Ha `true`, egy játékos sessionjében
nem futhat egyszerre legacy és v2 spec runtime.

## Join, quit és leállítás ellenőrzése

Joinkor a class/spec funkció addig blokkolt, amíg a profil load vagy legacy migráció, a validáció és
a gate/spell reconcile sikeresen be nem fejeződik. A játékosnak nem kell az egész szerverről
kiesnie. Ellenőrizd, hogy ugyanarra a játékosra nincs ismételt migráció vagy dupla grant.

Quitkor előbb transient spell/pet/minion/form cleanup, majd `flush(player)` és cache cleanup történik.
Plugin disablekor új mutation nem indulhat; minden runtime cleanup és `flushAll` befejeződik, majd a
cache ürül. Leállítás előtt figyeld a logban a flush hibákat; sikertelen flush mellett ne tekintsd a
deploymentet tisztán leállítottnak.

## `/spec info` diagnosztika

A diagnosztika legalább ezt mutatja:

- profile status, schema version és revision;
- primary class és ellenőrzött class-level tükör;
- active slot és second-spec unlock;
- slotonként spec ID, loadout status és seal ok;
- mastery rang és XP;
- migration-review vagy quarantine ok;
- session persistence block oka.

A parancs diagnosztika, nem recovery-művelet. Nem old fel sealt, nem ír felül payloadot és nem töröl
legacy snapshotot. Kimenetét a játékos UUID-jével, időponttal, szerverbuilddel és a kapcsolódó log-
korrelációs azonosítóval együtt rögzítsd; nyers payloadot ne másolj publikus csatornára.

## Decode-hiba és quarantine

1. Ne futtass általános class/spec resetet.
2. Állítsd le az érintett játékos class/spec módosításait; más rendszerek maradhatnak elérhetők.
3. Mentsd ki változatlanul az eredeti payloadot, checksumot, fájlt és vonatkozó logot.
4. Ellenőrizd, hogy nem keletkezett üres helyettesítő profil és a cache sem aktivált részállapotot.
5. Hasonlítsd össze backupból és stagingen a payloadot; recovery csak verziózott, explicit admin
   folyamattal történhet.
6. Faction-, sinner- és questeseménytől quarantine vagy persistence seal nem oldódhat fel.

## `MIGRATION_REVIEW`

Review esetén az ismeretlen spec, spell-preview, companionadat, orphaned Soulforge, mechanikai
snapshot és okkód megmarad. Általános reset nem használható. Az operátor előbb exportálja és
ellenőrzi a snapshotot, majd csak olyan explicit resolve/recovery eljárást futtat, amely a választott
adatvesztést naplózza és új CAS-revisionnel ment. Ha ilyen recovery még nincs implementálva, a profil
maradjon blokkolt; kézi YAML/PDC törlés nem elfogadott megoldás.

## Mentési hiba

- a korábbi tartós profil marad autoritatív;
- a sikertelen mutation nem jelenhet meg sikeresnek a játékosnak;
- respec- vagy Soulforge-költség teljesen visszajár;
- spell/pet/transient runtime biztonságos állapotba kerül;
- a session persistence block csak explicit recovery vagy sikeres teljes reload után oldható;
- ugyanazt a gazdasági műveletet csak az eredeti idempotency tokennel szabad újrapróbálni.

Ha a diagnosztika `RUNTIME_FAILED` respecet jelez, a profilcommit már tartós: ne térítsd vissza
automatikusan a költséget és ne ismételd új tokennel a műveletet. Ellenőrizd a `/spec info`
revisiont és session blockot, majd explicit runtime/profile recoveryt végezz.
Ugyanez érvényes `soulforge-runtime-failed` esetén: a rang és a szilánkköltség már commitolt.

ENOSPC, permission denied vagy fájlrendszerhiba után előbb az infrastruktúrát javítsd és a tartós
payloadot ellenőrizd; a flag ki-be kapcsolása nem recovery.

## DARK seal/unseal ellenőrzés

A DARK specek: `necromancer`, `plaguebringer`, `unholy`, `bone_priest`, `demonologist`.

Gate-vesztéskor a loadout `SEALED`, az aktív slot üres, a spec-grant és aktív companion/transient
runtime eltűnik, miközben mastery, doctrine, signature, capstone és roster megmarad. Második spec nem
aktiválódik automatikusan.

Automatikus unseal csak az eredeti faction-, sinner- vagy questok helyreállásakor engedett, és a
loadout `INACTIVE` állapotba tér vissza. Admin-, persistence-, recovery- és quarantine-sealt gate
event nem oldhat fel. Ha seal-persist hiba történt, a session blokkot ne oldd fel pusztán a kapu
helyreállításával.

## Companion és Soulforge incidens

Élő entity UUID nem tartós adat. Gazdátlan runtime entitynél a profil rosterét ne töröld; a scheduler-
cleanupot és az újraidézési állapotot javítsd. Másik slotból látható roster adatvédelmi/invariáns-
incidens: az érintett profilt azonnal blokkolni és kivizsgálni kell.

Soulforge csak aktív, használható `necromancer` loadoutból adhat hatást. Nekromanta nélküli adat
orphaned review, nem automatikusan törlendő. Párhuzamos upgrade vagy mentési hiba után ellenőrizd a
revisiont, a rangot és a költség-visszatérítést; kézi rangnöveléssel ne kompenzálj.

## Rollback

1. Állítsd le az új belépéseket és várd meg a determinisztikus `flushAll` eredményét.
2. Készíts incidensmentést a jelenlegi v2 payloadokról és logokról akkor is, ha korábbi backupra
   állsz vissza.
3. Kapcsold ki a rework flaget és állítsd vissza az előző IceSMP buildet, pluginbundle-t és resource
   packet a jóváhagyott mentési pontról.
4. A v2 és legacy recovery-mirrort ne töröld és ne konvertáld kézzel.
5. Production forgalom előtt staging backup-másolaton ellenőrizd a class/spec, inventory, quest,
   spell provenance és companion folytonosságot.

Újabb Minecraft DataVersionre frissített világot tilos régebbi szerverre visszaengedni. A Profile v2
flag rollback nem egyenlő világ-downgrade-del.
