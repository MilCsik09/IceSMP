# IceSMP kaszt- és specializáció-rework — gap analysis

**Auditált kiinduló állapot:** `master` @ `3867f19640814cc12b2179408f7075cca84877c3`
**Első célplatform:** Minecraft/Paper/Folia 1.21.11, Java 21
**Későbbi célplatform:** Minecraft/Paper/Folia 26.2, Java 25

## Vezetői összefoglaló

A repositoryban már megvan a teljes kanonikus identitásfelület: 13 bejegyzés a `JobType` enumon,
35 stabil bejegyzés a `SpecializationType` enumon, class/spec spell-unlockok a `config/classes.yml`
fájlban, közel négyszáz regisztrált spell, továbbá működő kaszt-XP, spell-provenance, quest-, pet-,
erőforrás- és talentrendszer. A reworknek ezért nem egy második RPG plugint kell felépítenie. A jelenlegi,
egyetlen specializációt spellcsomagként kezelő modellt kell verziózott profillal, kasztmagokkal és
specenként elkülönített állapottal kibővítenie úgy, hogy a bevált rendszerek megmaradjanak.

A legfontosabb hiányok nem a darabszámból, hanem a jelenlegi állapotmodellből következnek:

1. A `SpecializationManager` egyetlen `class_spec` PDC-stringet tárol.
2. Nincs második loadout, doktrínadöntés, spec-mastery vagy Lélekkapocs-profil.
3. A DARK specet a rendszer a kapu elvesztésekor törli; az új design szerint zárolnia kell.
4. A `ResourceManager` ad kaszterőforrást, de nincs kasztmag-state machine.
5. A specek többségének identitása jelenleg unlocklistákban és generikus spellkombókban él.
6. A `PetManager` és a `SoulforgeManager` játékosszintű globális állapotot használ, nem spec-slotot.
7. A `TalentManager` kaszt-/profession-fa, nem háromszintű spec-doktrínarendszer.
8. A natív `HudManager` nem ismer class/spec dirty-field névteret, BetterHud-adapter még nincs.
9. CraftEngine-, ModelEngine-, BetterHud- és MythicMobs-határ még nincs a class/spec domain körül.
10. Nincs profilmigráció, revízió, quarantine vagy loadout-rollback.

## Megtartandó alapok

### Stabil ID-k és választási kapuk

- `src/main/java/hu/taliann/icesmp/data/JobType.java` már mind a 13 kasztot tartalmazza.
- `src/main/java/hu/taliann/icesmp/data/SpecializationType.java` mind a 35 stabil spec-ID-t, valamint
  az öt DARK+sinner kaput tartalmazza.
- `SpecializationManager.canSelectClassSpecialization` már ellenőrzi az elsődleges kasztot, a szintet,
  az Emlékszilánk-feloldást, a frakciót, a sinner állapotot és a beavatási questet.

Ezek migrációs horgonyok. A megjelenített nevek változhatnak, az ID-k nem.

### Spell-provenance

A `JobManager` forrásonként tartja nyilván a grantet, a
`SpecializationManager.resetClassSpecialization` pedig csak a `SPEC:*` eredetű feloldásokat vonja
vissza. Így sem a spec-spellek halmozása, sem a questből vagy talentből is megszerzett képesség
véletlen elvesztése nem történhet meg. A rework ezt a modellt slot-tudatossá teszi, nem lecseréli.

### Cast-pipeline

Az `AbilityCatalystListener.castSelectedSpell` már koherens cast-tranzakciót kezel: kiválasztás,
cooldown, elérhetőség, erőforrásfizetés, végrehajtás, no-op refund, kombó, statisztika és mastery.
Ide kell beilleszteni a class/spec pre- és post-hookokat. Párhuzamos cast engine cooldown-, refund-
és provenance-duplikációt hozna létre.

### Quest- és contentalapok

A `QuestManager` tud kaszt-, frakció-, szint- és előfeltétel-kaput, NPC-interakciót, több objective-et
és jutalmat. Beavatási, zárópróba- és profiljelzésekkel kell kiegészíteni, nem lecserélni.

### Folia-lifecycle

A repository következetesen player/entity/region schedulert használ, és központi session cleanupot
tart fenn. Az új átmeneti mechanikák `PlayerStateCleanup` implementációként csatlakozhatnak; nincs
szükség globális online-player tickre.

### Tartós infrastruktúra

A projektnek van atomi YAML-írása és fail-closed `PersistentStoreCoordinator` rétege. Az online
játékosprofil elsődleges tárolásához a verziózott PDC-codec illeszkedik a jelenlegi modellhez a
legkisebb kockázattal. Későbbi offline/shared index külön repository port mögé tehető anélkül, hogy
a profilséma változna.

## Refaktorálandó komponensek

| Komponens | Jelenlegi működés | Szükséges változás |
|---|---|---|
| `SpecializationManager` | Egy PDC-spec, destruktív reset | Profile v2 facade; két slot; seal/unseal; atomi slotmutáció |
| `RespecService` | Az egyetlen spec törlése | Egy loadout respecje, csak saját doctrine/spec grantjeinek visszavonása |
| `SpecCommand` / `SpecGUI` | Egy spec választása, info, respec | Aktív slot, second-slot unlock/switch, doktrína- és mastery-státusz |
| `AbilityCatalystListener` | Generikus spell- és kombópipeline | Class/spec pre/post hook, aktív loadout spellnézet |
| `ResourceManager` | Egy kaszterőforrás | Megmarad base resource-ként; a class/spec runtime külön bounded state-et ad |
| `TalentManager` | Kaszt-/profession-fa | Megmarad; külön doctrine service készül |
| `SoulforgeManager` | Globális játékos-PDC ágak | Kizárólag a Nekromanta-loadoutba migrálható |
| `PetManager` | Globális egy-pet PDC | Rosterazonosság loadoutonként, determinisztikus switch-cleanup |
| `HudManager` | Natív sidebar snapshot | Class/spec mezők; BetterHud csak megjelenítési adapter |
| `QuestManager` | Nincs profiljutalom | Beavatás/zárópróba/second-spec jelzés a profile service felé |

## Ellentmondó vagy veszélyes jelenlegi működés

### DARK specializáció törlése

A `SpecializationManager.resetDarkGatedSpecialization` törli a specet és a grantjeit. Ez elveszíti
a fejlődést és ellentmond a zárolt profil követelményének. A csere:

- megtartja a loadout mastery-, doctrine- és Lélekkapocs-állapotát;
- `SEALED` állapotot és konkrét okot rögzít;
- csak az aktív grantet és átmeneti entityket vonja vissza;
- kizárólag minden kapu újbóli teljesülése után aktiválható.

### Globális pet- és Soulforge-állapot

Az egyetlen játékosszintű névtér cross-spec state leakage-et enged. A Vadmester istállója, a
Nekromanta udvara, a Szentségtelen ghúlja és a Demonológus rosterje külön slothoz kötődik; sem
fejlesztést, sem aktív entityt nem oszthatnak meg.

### Generikus kombó mint spec-identitás

A `spells.yml` hasznos pár- és lánckombókat tartalmaz, de önmagában nem modellezi a ráhangolódást,
Staggert, Echót, linket, késleltetett sebzést, pet-hűséget vagy korlátozott Rewindet. A kombó a spell
szintjén megmarad, fölé kerül a mechanikai runtime.

## Hiányzó képességek

- profilséma, verzió, revízió és legacy migráció;
- két, slotonként elkülönített loadout;
- `EMPTY`, `ACTIVE`, `INACTIVE`, `SEALED` és quarantine állapotok;
- három doctrine tier, tierenként két választással;
- tíz mastery rang minden spechez;
- contribution- és anti-farm elszámolás;
- kasztonként egy fizikai, revíziózott Lélekkapocs;
- mind a 13 kasztmag runtime-ja;
- mind a 35 spec mechanikai definíciója;
- determinisztikus cleanup switch/death/quit/world-change/disable esetén;
- presentation portok és indulási dependency lock;
- initiation/capstone completion signal;
- külön PvP clamp a kontroll-, rewind-, execute- és petmechanikákhoz.

## Kerülendő 26.2-portolási blokkolók

- közvetlen NMS vagy obfuszkált szerverosztály;
- Paper legacy plugin-remapperére támaszkodás;
- külső plugin API importja domain/application csomagban;
- gameplay-szabály kizárólag MythicMobs/CraftEngine/BetterHud configban;
- tartós állapot kizárólag itemben vagy entityben;
- szinkron cross-region entityhozzáférés;
- globális, minden tickben futó online-player scan;
- gameplay kódba égetett generált resource-pack útvonal.

## Migrációs irány

1. Ha van profile v2, azt kell dekódolni.
2. Egyébként a jelenlegi elsődleges kasztból és legacy `class_spec` PDC-ből készül profile v2.
3. A staged rollout alatt megmarad a legacy mirror a régi caller és rollback biztonságához.
4. Soulforge/pet állapot csak megfelelő spec-slotba kerülhet; ellenkező esetben orphaned migration
   állapotként quarantine-ba kerül.
5. Migráció nem adhat fizikai Lélekkapocs-példányt profilrevízió-ellenőrzés nélkül.
6. Minden migráció idempotens, a legacy kulcsok a zárt béta végéig megmaradnak.

## Javasolt szállítási sorrend

1. Dependency lock, preflight és integrációs portok.
2. Profile v2, migráció és zárolt DARK állapot.
3. Mechanikai primitívek és 13 kasztmag.
4. Doktrína, mastery, contribution és második slot.
5. Lélekkapocs, HUD, modellek, NPC/dialog és Mythic encounter adapterek.
6. Hat referenciaspec, majd a fennmaradó specek szerepalapú hullámokban.

Ez az elemzés nem helyettesíti a megvalósítást. Azt rögzíti, miért a bevált IceSMP-útvonalak
kiterjesztéseként, nem második RPG motorként készül a rework.
