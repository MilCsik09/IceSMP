# IceSMP content authoring

Az IceSMP packaged gameplay-tartalmának egyetlen forrása a Gitben követett,
kézzel olvasható `src/main/resources/content/**` fa. A Python/Java tooling ezt a
tartalmat ellenőrizheti, reportot vagy resource-pack artifactot készíthet belőle,
de YAML/JSON gameplay-definíciót nem írhat és nem egészíthet ki overlayjel.

## Authority térkép

| Terület | Kanonikus forrás | Betöltés |
|---|---|---|
| felszerelés, ritkaság, relikvia | `content/equipment/*.yml` | restart |
| szakmaanyag és recept | `content/professions/*.yml` | restart |
| ellenfél, technika, loot | `content/pve/*.yml` | restart |
| kaszt, quest, spell | `content/progression/*.yml` | restart |
| authored eseménytartalom | `content/events/*.yml` | restart |
| üzemeltetői hangolás | `config/*.yml`, schema-bounded `config.yml` | `/icesmp reload operator` policy szerint |
| szöveg/lokalizáció | `messages/*.yml` | `/icesmp reload messages` |
| szerver-extension quest | versioned `custom-quests.yml` | manager-controlled |

Az authority nem a YAML gyökérkulcs neve, hanem a fájlhely és a
`ConfigManager` explicit allowlistje. Ugyanazt a leaf pathot két forrásban ne
add meg. Az `itemization.stats.*`, `itemization.loot.*`, `health.*` és a
dokumentált relikvia-üzemeltetési kulcsok szándékos operator seam-ek: definíciójuk
handcrafted contentben él, de az üzemeltető schema-bounded override-ot adhat.

Az egyetlen szándékos szerveroldali `EXTENSIBLE_CONTENT` authority a
`custom-quests.yml`: schema-version 1, `QuestManager`-validált és atomikusan
mentett. A bounded `/quest admin` eszköz nem írja át a packaged questeket és
nem teljes content-editor. Éles szerveren ezt is forráskontrollált deployment
artifactként kezeld; hibás vagy ismeretlen séma fail-closed.

## Szerzői munkafolyamat

1. Azonosítsd a fenti táblából az egyetlen kanonikus fájlt.
2. Közvetlenül szerkeszd a YAML/JSON rekordot; tartsd meg a stable ID-t, hacsak
   nincs explicit persistence-migráció.
3. Frissíts minden hivatkozást és az embernek szánt dokumentációt ugyanabban a
   commitban.
4. Futtasd a területi validátort, majd a központi kapukat.
5. Gameplay-teszthez indíts új szervert/JAR-t; content hot reload nincs.

Kötelező központi ellenőrzés:

```bash
python3 scripts/audit_config_content_command_surface.py --check
python3 scripts/check_consistency.py
./gradlew build --console=plain --no-daemon
```

### Új ellenfél vagy technika

Az ability és a rá hivatkozó template együtt a
`content/pve/enemies.yml` fájlba kerül. A template stable ID-je az esemény-,
bestiary- és reward-hivatkozások szerződése.

```yaml
mob-abilities:
  pelda_roham:
    kind: DASH
    cooldown-ticks: 160
    telegraph-ticks: 20
    recovery-ticks: 20
    target-rule: CURRENT_TARGET
    interruptible: true
    radius: 8.0
    power: 3.0
    max-summons: 0

mob-templates:
  pelda_ellenfel:
    schema-version: 2
    display-name: Példa Ellenfél
    entity-type: HUSK
    level: {minimum: 10, maximum: 20}
    rank: NORMAL
    archetype: BRUISER
    stats:
      health-multiplier: 1.0
      damage-multiplier: 1.0
      movement-multiplier: 1.0
      cc-resistance: 0.0
    abilities: [pelda_roham]
    rank-abilities: {}
    resistances: []
    weaknesses: []
    loot-profile: hostile_common
    source-tags: [region:wilderness]
    spawn-policy: authored_only
    bestiary-id: pelda_ellenfel
    affix-pool: []
```

Futtasd az `audit_enemy_worldboss_rework.py --check`,
`audit_authored_pve_consolidation.py --check` és a combat audit kapuit. A
telegráfot, counterplayt és Folia owner-thread viselkedést stagingen is próbáld.

### Új szakmarecept

A recept közvetlenül a `content/professions/recipes.yml` fájl
`profession-recipes` mapjába kerül. Custom input előtt a
`content/professions/materials.yml` stable material ID-jét authorold.

```yaml
profession-recipes:
  pelda_recept:
    profession: miner
    kind: gyakorlo
    level: 10
    learn: level
    display-name: Példa Recept
    category: Alapanyag
    result: {material: IRON_INGOT, amount: 1}
    ingredients: [RAW_IRON:1, COAL:1]
```

Futtasd a két `audit_professions_2_*_closure.py` auditot és a
`test_professions_2_economy.py` tesztet. Ellenőrizd a faucet/sink és a craft
profit hurkokat; audit ne módosítsa a receptet.

### Kaszthangolás vagy spell-unlock

A kaszt progressziója és unlock listája a
`content/progression/classes.yml` fájlban él; spell-definíció a
`content/progression/spells.yml` fájlban. A `config/spells-balance.yml` csak a
dokumentált, operator-tunable numerikus cast-time seam. Új ID esetén minden
registry- és dokumentációs hivatkozást frissíts, majd futtasd a class/spell
regressziókat és a consistency gate-et.

### Eseménytartalom

Az authored roster, stage és reward-definíció a megfelelő
`content/events/*.yml` fájlba kerül. Spawn-időzítés, engedélyezés, helyezés és
üzemeltetési limitek maradjanak a megfelelő `config/*.yml` fájlban. Ne másold
ugyanazt a leafet mindkét rétegbe.

### Jövőbeli fegyverkatalógus

Ez a változás nem reworköli a fegyvereket. Egy későbbi kézzel authorolt
fegyverkatalógus természetes helye `content/equipment/weapons.yml`; a fájlt az
explicit content allowlisthez, typed loaderhez, validátorhoz és regresszióhoz
együtt kell hozzáadni. A katalógust ne generátor és ne `config/*.yml` overlay
hozza létre.

## Validációs hibák kezelése

- YAML parse/schema/reference hiba: javítsd a canonical rekordot; ne lazítsd a
  validátort érvényes indok nélkül.
- Duplicate authority: töröld a másodlagos definíciót, és tartsd meg az egyetlen
  owner fájlt.
- Ismeretlen operator override: az override figyelmen kívül marad; az
  `/icesmp inspect config <kulcs>` megmutatja az authorityt és reload policyt.
- Sikertelen operator reload: a korábbi immutable snapshot marad aktív.
- Régi, deployolt gameplay YAML: startup előtt SHA-jelölt migration backupba
  kerül; a packaged handcrafted content csak sikeres archiválás után aktiválódik.

Az auditok kimenete review evidence. A gameplay forrása mindig a fent felsorolt
handcrafted fájl, nem a report, manifest vagy audit script.
