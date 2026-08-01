# Kaszt- és specializáció-rework — Profile v2 architektúra

## Hatókör és tulajdonjog

A Profile v2 a kaszt- és specializációállapot új, IceSMP-tulajdonú tartós modellje. Ebben a
fázisban nem valósít meg kasztmagot, harci primitívet, doktrínahatást, mastery contributiont,
második-spec váltást vagy fizikai Lélekkapocs-itemet. A `JobManager` marad a kaszt-XP és a
kasztszint igazságforrása; a profilban levő szint csak ellenőrzött tükör.

A külső pluginok megjeleníthetnek vagy végrehajthatnak egy IceSMP-kérést, de nem birtokolhatnak
profiladatot. CraftEngine-, BetterHud-, ModelEngine-, MythicMobs-, Fancy-, WorldGuard- vagy más
külső API-típus nem kerülhet a domain- vagy persistence-rétegbe. A profilból minden eldobható
runtime állapot újraépíthető.

## Réteghatárok

```text
classspec/domain
    immutable profil, loadoutok, invariánsok, katalógus és értékobjektumok
        │
classspec/application
    sorosított mutation, seal/unseal, respec/reset és lifecycle
        │
classspec/persistence              classspec/migration
    codec, CAS repository, cache   legacy snapshot, migrátor, diagnosztika
        │                                  │
classspec/integration
    Bukkit/Folia és meglévő manager-adapterek; spell/pet/runtime cleanup
```

A domain csak stabil string ID-t, UUID értéket, időbélyeget és saját immutable értékobjektumot
tárolhat. Bukkit `Player`, `Entity`, élő entity UUID, `ItemStack`, scheduler task vagy külső plugin
handle nem lehet tartós profilmező. Az élő companion- és transient runtime-azonosítók külön,
session-határú cache-ben maradnak.

## ClassProfile v2

Minden profil `schemaVersion = 2` értékkel és monoton `revision` számmal rendelkezik. A profilszintű
állapot legalább ezt tartalmazza:

- `ProfileStatus`: `READY`, `MIGRATION_REVIEW` vagy `CORRUPT_QUARANTINE`;
- elsődleges kaszt stabil ID-je és a `JobManager` által birtokolt kasztszint tükre;
- opcionális aktív slot és a `secondSpecUnlocked` jelző;
- pontosan két loadout slot;
- migrációs állapot, az utolsó sikeres migráció azonosítója és korlátozott diagnosztika;
- quarantine- vagy session-block ok úgy, hogy hibás decode után se jöhessen létre részleges runtime.

Minden loadout külön tárolja a spec ID-t, `LoadoutStatus` értéket, opcionális seal okot, doktrína-
döntéseket, mastery rangot és XP-t, a Lélekkapocs logikai állapotát, kedvenc és kiválasztott
spelleket, a zárópróba állapotát, companion rostert, specenkénti tartós mechanikai állapotot és
migrációs megjegyzést.

A loadout státuszai: `EMPTY`, `ACTIVE`, `INACTIVE`, `SEALED`, `MIGRATION_REVIEW`. A mastery rang
0–10 közötti, XP-je nem negatív; a későbbi konfigurált küszöbök és contribution motor nem részei
ennek a fázisnak. A zárópróba állapota `LOCKED`, `AVAILABLE`, `IN_PROGRESS` vagy `COMPLETED`.

A Lélekkapocs fizikai tárgya nem igazságforrás. A profil stabil signature UUID-t, evolúciós fokot,
modulokat, saját revisiont és recovery/rebind információt őriz; a tényleges item későbbi adapter.

## Domaininvariánsok

Minden konstrukció, decode és mutation ugyanazt a központi validációt használja. Érvénytelen profil
nem kerülhet cache-be és nem aktiválhat gameplayt. Kötelező invariánsok:

1. legfeljebb egy loadout lehet `ACTIVE`, és az `activeSlot` csak erre mutathat;
2. `EMPTY`, `SEALED` vagy `MIGRATION_REVIEW` slot nem lehet aktív;
3. ugyanaz a spec nem szerepelhet mindkét slotban, és minden specnek a primary classhoz kell tartoznia;
4. a második slot feloldás nélkül üres, az `EMPTY` slot pedig semmilyen rejtett állapotot nem hordoz;
5. review vagy quarantine profil normál mutationt és class/spec runtime-ot nem aktiválhat;
6. sealed loadoutból nincs aktív grant, companion, forma vagy transient state;
7. általános reset nem törölhet review/quarantine snapshotot vagy diagnosztikát;
8. Soulforge csak a `necromancer` loadout mechanikai névterében fejthet ki hatást;
9. companion roster csak a hozzá tartozó spec saját loadoutjából olvasható és módosítható;
10. decode- vagy mentési hiba után a profil nem aktiválódhat részlegesen.

A stabil 13/35 katalógus és az öt DARK spec (`necromancer`, `plaguebringer`, `unholy`,
`bone_priest`, `demonologist`) közös domainkatalógusból validálódik. A régi lore-dokumentumok nem
spec-lista igazságforrások.

## Codec és persistence

A teljes profil egyetlen verziózott codec-borítékon halad át:

- magic: `ICS2`;
- explicit codec- és schema-verzió;
- determinisztikus, UTF-8-alapú payload;
- CRC32 vagy erősebb checksum;
- teljes payload-, string-, lista-, halmaz- és mapméret-korlát.

A writer stabil sorrendben írja a mapeket és halmazokat. A reader elutasítja a hibás checksumot,
csonkolást, trailing adatot, ismeretlen enumot, ismételt vagy normalizálás után ütköző kulcsot,
hibás UTF-8-at, negatív vagy túlméretes hosszmezőt. Java natív object serialization nem használható.

A repository a `load`, `save(expectedRevision, nextProfile)`, `quarantine`, `flush`, `flushAll` és
cache-invalidation műveletek egyetlen tulajdonosa. A hiányzó profil elvárt revíziója `-1`, az első
sikeres mentésé `0`; utána kizárólag `n → n+1` engedélyezett. Játékosonként egy lock vagy sorosított
mutation-lánc védi a kritikus módosításokat. Stale vagy kihagyott revision nem írhat, régebbi async
completion nem publikálhat újabb cache fölé.

A mentés az IceSMP meglévő atomikus persistent-store/YAML infrastruktúráját használja. A teljes
profil nem szóródik PDC-kulcsokra; PDC-ben csak kis identity, migration és rollback mirror maradhat.
Sikertelen íráskor a korábbi tartós profil marad autoritatív, a jelölt új profil nem válik cache-
igazsággá, a session class/spec része pedig fail-closed blokkot kap.

## Feature flag és lifecycle

Az egyetlen rollout-kapu a már létező `class-spec-rework.enabled`, alapértéke `false`.

- `false`: nincs Profile v2 read, write vagy migráció; a legacy runtime változatlan;
- `true`: joinkor kizárólag Profile v2 load vagy legacy migráció indul, ugyanazon játékosnál nincs
  kettős legacy/v2 spec runtime;
- a cache-ben már betöltött profil is activation-pending marad a gate-reconcile és runtime rebuild
  végéig; ezalatt csak diagnosztika olvasható, gameplay/spec/pet/mechanika nem;
- decode- vagy migrációs bizonytalanság csak az érintett class/spec gameplayt blokkolja;
- sikeres load/migrate után gate-revalidáció és provenance-alapú spell reconcile fut;
- quitkor transient cleanup és determinisztikus `flush(player)`;
- disablekor új mutation nem fogadható, minden runtime cleanup és `flushAll` lezárul, majd a cache ürül.

Fájl-I/O nem futhat player/entity region threaden. Player/PDC/inventory snapshot a játékos saját
schedulerén készül, az aszinkron rétegbe csak immutable adat kerül. Entity- és modell-cleanup az
adott entity schedulerén fut; disable után callback nem indíthat új taskot.

## DARK seal/unseal

A kapu elvesztése nem törli a loadoutot. A sikeres CAS-mutation `SEALED` állapotot és tartós
`SealReason` értéket rögzít; ha az aktív slot sealelődik, az `activeSlot` kiürül, második spec nem
aktiválódik automatikusan. Ezután a provenance-rendszer csak a sealed spec grantjeit vonja vissza,
és a scheduler-adapter takarítja a pet/minion/form/transient runtime-ot. Mastery, doktrína,
signature, zárópróba és roster megmarad.

Megkülönböztetett okok: frakcióhiány, sinner-jel hiánya, questfeltétel hiánya, adminisztratív seal,
persistence/recovery blokk és quarantine. Automatikus unseal csak ugyanannak a frakció-, sinner-
vagy questkapunak a helyreállásakor engedett. Admin-, persistence- és quarantine-sealt esemény nem
oldhat fel. Unseal után a loadout `INACTIVE`; korábbi aktivitás nem áll vissza automatikusan.

Ha a seal mentése hibázik, a régi tartós profil marad autoritatív, de a session blokkolódik és az
aktív class/spec runtime biztonságosan kitakarításra kerül. Így persistence-hiba sem hagy használható
DARK grantet vagy társat.

## Companion- és Soulforge-izoláció

Tartós companion roster csak ezekben a loadoutnévterekben létezhet:

- `beast_master.stable`;
- `necromancer.court`;
- `unholy.ghoul`;
- `demonologist.roster`.

A companion rekord logikai ID-t, típust, nevet, szintet/XP-t, traitet vagy mutációt, stance-et,
modul-/páncélazonosítókat, újraidézési időt és tartós státuszt őrizhet, élő entity UUID-t nem.
Rituális `unholy` és `demonologist` társ nem érhető el az általános `/pet summon` útvonalon.

A Soulforge kizárólag a Nekromanta mechanikai állapotában él. Fejlesztése revision/CAS-védett
mutation; mentési hiba visszatéríti a lefoglalt költséget, ugyanaz az idempotency token nem számolható
el kétszer. Tartós rangcommit utáni runtime-hiba nem refundol szilánkot, hanem blokkolt sessiont és
admin recoveryt eredményez. Nekromanta nélküli legacy Soulforge adat nem vész el: orphaned migrációs snapshotként
`MIGRATION_REVIEW` állapotot okoz.

## Respec, admin reset és spell provenance

A respec és admin reset a profil commitját tekinti döntési határnak. Gazdasági levonás vagy más
kompenzálható költség reservation/token alapján történik; mentési hiba refundot és sikertelen
parancseredményt ad. A class PDC és legacy adatok csak sikeres profile commit után módosíthatók.
Retry ugyanazzal a tokennel nem vonhat le kétszer. Ha a CAS commit már tartós, de az utólagos
scheduler/runtime reconcile hibázik, a költség nem jár vissza: a session blokkolódik, az eredmény
`RUNTIME_FAILED`, és admin recovery szükséges. Ez megakadályozza a tartós respec ingyenessé válását.

Spell reconcile forrásonként dolgozik: csak az érintett `SPEC:*` provenance szűnik meg; questből,
talentből, kasztszintből vagy adminforrásból kapott azonos spell megmarad. Review/quarantine profil
általános resetje elutasított, így a megőrzött bizonytalan legacy snapshot nem veszhet el.

## Kapcsolódó dokumentumok

- [Legacy migráció](CLASS_SPEC_REWORK_MIGRATION.md)
- [Regressziós tesztterv](CLASS_SPEC_REWORK_TEST_PLAN.md)
- [1.21.11 → 26.2 portterv](CLASS_SPEC_REWORK_1_21_11_TO_26_2.md)
- [Üzemeltetői runbook](../admin/CLASS_SPEC_REWORK_RUNBOOK.md)
