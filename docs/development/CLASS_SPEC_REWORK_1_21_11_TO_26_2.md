# Kaszt- és specializáció-rework — átállási terv 1.21.11-ről 26.2-re

## Kiinduló szerződés

Az elsődleges cél Minecraft/Paper/Folia 1.21.11 és Java 21; a későbbi cél 26.2 és Java 25. A
Profile v2 már az első kiadásban portolható határokat használ:

- nincs NMS-, obfuszkált szerverosztály- vagy plugin-remapping-függés;
- reflection, ha később elkerülhetetlen, csak külön verziózott adapterben élhet;
- domain, codec, migráció és CAS repository Bukkit- és külsőplugin-független;
- a tartós formátum stabil ID-ket és explicit verziókat tárol, nem Java osztálynevet;
- `Player`, `Entity`, `ItemStack`, élő entity UUID és külső plugin handle nem része a profilnak;
- a fizikai storage változhat anélkül, hogy a profil jelentését újra kellene írni.

A 1.21.11 staging szervert `-Dpaper.disablePluginRemapping=true` kapcsolóval is el kell indítani.
Ez bizonyítja, hogy az IceSMP nem támaszkodik régi, remappelt szerver-JAR kompatibilitási útvonalra.

## Profile v2 kompatibilitási garanciák

26.2 alatt változatlan jelentésű:

- `ICS2` magic, codec-version, schema 2 és checksum-szerződés;
- `-1 → 0`, majd `n → n+1` revision/CAS;
- `ProfileStatus`, `LoadoutStatus`, `SealReason` és capstone állapot;
- a stabil 13 kaszt / 35 spec ID;
- két loadout, aktív slot és second-spec feloldás jelentése;
- mastery, doktrína, signature és companion roster logikai állapota;
- DARK seal/unseal és specenkénti izoláció;
- spell provenance és a class-level tükör tulajdonjoga;
- legacy migráció idempotencia- és quarantine-szabályai.

Az új szerververzió ismeretlen enumot nem találhat ki és nem hagyhat figyelmen kívül: a codec
fail-closed marad. Új séma csak új codec/migrációs verzióval vezethető be, régi fixture-ök
visszaolvasási tesztjével.

## Várhatóan változó részek

- Paper/Folia API és Java toolchain;
- scheduler- és lifecycle-adapterek konkrét implementációi;
- CraftEngine, BetterHud, ModelEngine, MythicMobs és Fancy adapterek;
- resource-pack overlayek, modellek és shaderassetek;
- dependency lock pontos verziói és deployment-hashértékei;
- esetleges verzióspecifikus reflection-adapter;
- a tároló fizikai hordozója, ha ugyanazt a codec- és CAS-szerződést tartja.

## Átállási ellenőrzőlista

1. Készüljön visszaállítható világ-, playerdata-, IceSMP pluginadat- és resource-pack mentés; az
   egyetlen production példányt tilos közvetlenül frissíteni.
2. A 1.21.11-es kiadásból kerüljön archiválásra READY, MIGRATION_REVIEW, quarantine, sealed,
   két-slot, companion- és Soulforge-fixture nyers payloadja és jelentése.
3. A build- és runtime toolchain álljon Java 21-ről Java 25-re, majd frissüljenek a Paper/Folia
   koordináták.
4. Fordítási hibánál először az integration adapter változzon; domain- vagy codec-átírás csak valódi
   adatséma-indokkal engedett.
5. Fusson a teljes dependency-free Profile v2 suite mindkét toolchainen, ugyanazokkal a golden
   codec- és migrációs fixture-ökkel.
6. A verziózárt pluginbundle minden eleme ellenőrzött 26.2 buildre cserélődjön, majd fusson újra a
   dependency preflight.
7. Stagingen induljon a szerver remapping nélkül, üres profillal, meglévő v2 profillal és legacy
   migrációval is.
8. A 1.21.11-es payloadok byte-szintű decode-ja és szemantikai round-tripje maradjon változatlan;
   revision nem ugorhat pusztán verzióváltástól.
9. Minden adapter külön smoke tesztet kapjon: player snapshot, spell reconcile, companion cleanup,
   Lélekkapocs-handle, HUD, modell, encounter és dialog lifecycle.
10. Folia alatt terhelésesen fusson quit/disable flush, cross-region pet/minion cleanup, scheduler
    rejection és task-after-disable ellenőrzés.
11. Production backup-másolaton ellenőrizni kell a class-level tükröt, PDC mirrort, spec grantet,
    review/quarantine payloadot, seal okot, companion rostert és Soulforge-rangot.
12. A rollbackpróba után frissülhet csak a deployment-manifest és a rollout cohort.

## Adatverzió és rollback

A Minecraft világ DataVersionje és a Profile v2 codec verziója külön fogalom. 26.2-re frissített
világot nem szabad régebbi szerverrel megnyitni, de a Profile v2 payloadnak offline, Bukkit nélküli
toolinggal továbbra is olvashatónak kell lennie.

Feature-flag rollback nem töröl és nem konvertál profilt. A legacy recovery-mirror retentionje a
26.2 staging és rollbackpróba végéig kötelező. Ha a 26.2 adapter hibázik, a v2 payloadot változatlanul
félre kell tenni; üres profillal felülírni tilos.

## Kiadási kapu

A port nem attól kész, hogy lefordul. Kiadás csak akkor lehetséges, ha ugyanazon rögzített bundle-lel
zöld a remapping nélküli boot, a golden codec/migrációs fixture, a CAS és fault-injection suite, a
join/quit/disable lifecycle, a Folia cross-region smoke teszt, a dependency preflight, a resource-
pack ellenőrzés és a teljes visszaállítási próba.
