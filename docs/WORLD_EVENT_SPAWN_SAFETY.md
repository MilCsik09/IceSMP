# World-event spawn safety

A world-eventek helyét a `EventSpawnGuard` választja és publikálja. A guard a kezdeti
spawn előtt egységesen ellenőrzi a játékostávolságot, a látóirányt, a védett területeket,
a víz- és partpuffert, a teljes event-footprintet, a lejtést, a biomprofilt, a world
bordert és a friss eseményhelyek memóriáját.

## Alapértelmezett viselkedés

- A minimum játékostávolság legalább 192 blokk, de a tényleges Paper send/view distance
  és a 32 blokkos margó ezt automatikusan megemelheti.
- A 110 fokos, 384 blokkos konzervatív nézési kúp elutasítja a játékos előtt lévő
  helyeket. Ez szándékosan szigorúbb a blokkonkénti ray trace-nál, és Folia alatt nem
  olvas idegen régiót.
- A kereső 32 jelöltet próbál; egyszerre alapból két keresés futhat, egy keresés legfeljebb
  96 egyedi chunkot érinthet és 5 másodperc után watchdog zárja le.
- Csak már legenerált chunk tölthető vissza aszinkron módon. A kereső sosem generál új
  világterületet, és nem végez szinkron chunk-loadot régiószálon.
- A kiválasztott hely alapból 3 másodperces érkezési előjelet kap, majd közvetlenül a
  tényleges spawn előtt újra lefut a teljes validáció.
- Az utolsó eventhelyek 45 percig, 256 blokkos körben nem használhatók újra.

## Speciális profilok

- `stranger`: 64–96 blokkos helyi keresés, 48 blokk minimum, saját nézési kúp. Az Idegen
  így hallótávolságban marad, de nem a játékos előtt materializálódik.
- `escort`: távoli, teljes footprinttel validált indulóhely és négypontos útvonalvizsgálat.
- `escort-route` és `escort-wave`: a már aktív esemény belső mozgását és hullámait nem
  tiltja le a játékosok megérkezése, de a víz-, terep- és protection szabályok megmaradnak.
- `meteor`, `world-boss`, `invasion`, `cultists`, `wild-hunt`, valamint a karavánok saját
  footprint-, lejtés- és biomprofilt használnak.

## Admin diagnosztika

Játékos adminnal:

```text
/events debug spawn <event-kulcs>
```

A parancs valódi, spawn nélküli keresést futtat, majd megmutatja a dinamikus minimumot,
a keresési gyűrűt, a footprintet, az érintett chunkokat, az eltelt időt és az elutasítási
okok darabszámát.

## Config menü

A vízvédelem három kulcsa a `Világesemények` kategóriába kerül. Ha a kategória más
fejlesztések miatt elérné a 45 elemű kapacitást, a további placement-beállítások egy
külön `Event spawn-védelem` kategóriában jelennek meg. Minden itt szereplő beállítás
élőben olvasódik.

## Kötelező staging-próbák

1. Tó, folyó, waterlogged lépcső és 7/8/9 blokkos partszegély.
2. 10, 16 és 32 chunkos send distance, eltérő játékosbeállításokkal.
3. Síkságon előre néző, majd 180 fokkal elforduló játékos.
4. Több játékos különböző irányokból ugyanabban a térségben.
5. Escort teljes útvonala, beragadás-nudge és hullámspawn claim/folyó mellett.
6. Idegen 64–96 blokkra: legyen hallható, de ne jelenjen meg a játékos előtt.
7. Két egyidejű eventkeresés, harmadik keresés budget-elutasítása és timeout.
8. Már generált, de inaktív chunk visszatöltése; nem generált chunk fail-closed viselkedése.
9. Plugin disable érkezési késleltetés és async chunk-future közben.
10. `/events debug spawn` eredményének összevetése a tényleges eventindítással.
