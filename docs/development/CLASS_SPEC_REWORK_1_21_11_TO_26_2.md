# Kaszt- és specializáció-rework — átállási terv 1.21.11-ről 26.2-re

## Kiinduló állapot

Az első implementáció célplatformja Minecraft/Paper/Folia 1.21.11 és Java 21. A staging szervert
`-Dpaper.disablePluginRemapping=true` kapcsolóval is el kell indítani. Ez bizonyítja először, hogy
az IceSMP nem támaszkodik a régi, obfuszkált szerver-JAR-okat remappelő kompatibilitási útvonalra.

## Átállási ellenőrzőlista

1. Készüljön visszaállítható világ-, játékosadat-, pluginadat- és resource-pack mentés; az egyetlen
   production példányt tilos közvetlenül frissíteni.
2. A build- és runtime toolchain álljon át Java 21-ről Java 25-re.
3. Frissüljenek a Paper/Folia API-koordináták, majd fusson le minden dependency-free regressziós teszt.
4. A verziózárt pluginbundle minden eleme külön ellenőrzött 26.2-kompatibilis buildre cserélődjön.
5. Fusson újra a class/spec dependency preflight; a tényleges JAR-ok SHA-256 értéke kerüljön a
   deployment-manifestbe.
6. Épüljön újra a CraftEngine, BetterHud és ModelEngine közös resource packje, majd hasonlítsuk össze
   a generált manifestet az előző kiadással.
7. A profilcodec és a migrációs fixture-ök bizonyítsák, hogy az érvényes 1.21.11-es profilok jelentése
   változatlan marad.
8. Minden adapter külön smoke tesztet kapjon: Lélekkapocs, HUD, modelléletciklus, encounter-életciklus,
   mentor- és párbeszédfolyamat.
9. Folia alatt terhelésesen is fusson le a régióhatáron átnyúló link, pet, minion, zóna és loadout-cleanup.
10. Production backup-másolaton ellenőrizni kell a kaszt, inventory, PDC-profil, quest és világadat
    folytonosságát.

## Várhatóan változó részek

- Paper/Folia függőség és Java toolchain;
- a class/spec adapterek konkrét implementációi;
- a generált resource-pack overlayek és shaderassetek;
- a dependency lock pontos verziói és hashértékei;
- az esetleges verzióspecifikus reflection, amely kizárólag adapterben élhet.

## Várhatóan változatlan részek

- a stabil class/spec/doctrine/content ID-k;
- a profil v2 jelentése és revíziós szabályai;
- a contribution- és mastery-szabályok;
- a spell-provenance;
- a beavatási és zárópróba-teljesítések;
- a Lélekkapocs logikai azonossága;
- a 13/35 kanonikus katalógus.

## Kiadási kapu

A port nem attól kész, hogy lefordul. Csak akkor tekinthető kiadhatónak, ha a remapping nélküli
indítás, a profil round-trip, a resource-pack letöltés, a 13 kasztmag smoke tesztje, a 35 spec
teljes lifecycle-tesztje, a Folia cross-region forgatókönyvek és a rollbackpróba ugyanazon,
pontosan rögzített production bundle-lel is zöld.
