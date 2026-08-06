# Frakcióhoz kötött játékosnév-színek

A játékosnév-színek egyetlen központi palettát használnak minden támogatott felületen.

| Frakció | Natív szín | Legacy/TAB kód | Vizuális szerep |
|---|---|---|---|
| RED / Láng | RED | `§c` | támadó, tüzes identitás |
| BLUE / Fagy | BLUE | `§9` | hideg, kék identitás |
| NEUTRAL / Menedék | GREEN | `§a` | a Smaragdkő/Ryanora lore-szín, a DARK-tól jól elkülönülő identitás |
| DARK / Kitaszított | DARK_GRAY | `§8` | sötét, komor identitás |
| nincs vagy ismeretlen tagság | WHITE | `§f` | fail-safe alapállapot |

A NEUTRAL korábbi szürke (`GRAY` / `§7`) színe megszűnt. A DARK nem kap lich-kék árnyalatot, mert az a vanilla névszín-készletben túl közel kerülne a BLUE frakcióhoz.

## Érintett felületek

- natív tablist játékosnév;
- fej fölötti scoreboard-team nametag;
- HUD tablist-fallback;
- HUD frakcióérték;
- natív async chat formázás;
- `%icesmp_faction_color%` PlaceholderAPI-kimenet külső TAB/scoreboard pluginokhoz.

## Manuális staging ellenőrzés

1. Legyen egyszerre RED, BLUE, NEUTRAL és DARK játékos online.
2. Ellenőrizd a tablistát és a fej fölötti nametaget világos és sötét háttér előtt.
3. Ellenőrizd a natív chatet mind a négy frakcióval.
4. Kapcsold ki a natív tablistát, és ellenőrizd a HUD fallbacket.
5. Külső TAB mellett ellenőrizd a `%icesmp_faction_color%` kimenetet: NEUTRAL `§a`, DARK `§8`.
6. Aktív raidben az ellenség piros felülírása továbbra is előzze meg a frakció alapszínét.

A paletta élő-configgal felülbírálható: `tablist.faction-colors.<frakció|guest>` (NamedTextColor nevek); a defaultot a `FactionDisplayColorPolicy` adja, minden név-felület (tab, nametag, HUD, chat, `%icesmp_faction_color%`) ugyanazon a feloldón megy át.
