# Kaszt- és specializáció-rework — architektúra

## Tulajdonosi alapszabály

Minden játékszabályt és tartós játékosértéket az IceSMP birtokol. A külső plugin megjeleníthet vagy
végrehajthat egy IceSMP-kérést, de a runtime állapota eldobható: az IceSMP-profilból maradéktalanul
újraépíthetőnek kell lennie.

## Réteghatárok

```text
class/spec domain és profil
        │
        ├── cast / damage / heal / contribution application service-ek
        │
        ├── közös mechanikai primitívek és 13 kasztmag-runtime
        │
        └── stabil megjelenítési és encounter portok
                 ├── CraftEngine adapter
                 ├── BetterHud adapter
                 ├── ModelEngine adapter
                 ├── MythicMobs adapter
                 └── FancyNpcs/FancyDialogs adapter
```

Külső API-típus csak adaptercsomagban jelenhet meg. A határon stabil string ID, UUID, immutable
snapshot és saját handle haladhat át. A domain nem tarthat meg Bukkit `Player`-t, élő entityreferenciát
vagy prémium plugin saját handle-jét tartós állapotban.

## Az alapozó fázis portjai

- `ClassSpecHudPort`: HUD-layoutot aktivál és a domain által már kiszámított dirty mezőket publikálja.
- `ClassSpecContentPort`: a játékos egyetlen fizikai Lélekkapcsát egy profilrevízióhoz köti.
- `ClassSpecModelPort`: stabil handle-lel csatol és választ le modelleket.
- `ClassSpecEncounterPort`: beavatási és zárópróba-encountert indít, illetve jelez.
- `ClassSpecDialogPort`: rövid életű, szerveroldalon újraellenőrzött tokennel mentorfolyamatot nyit.

Az első fázis szándékosan csak szerződést ad, külső API-import nélkül. A konkrét adapterek a
megjelenítési fázisban érkeznek, és a 26.2-port során cserélhetők maradnak.

## Dependency-policy

A `class-spec-dependencies.lock.yml` az átvizsgált 1.21.11-es deployment-szerződés. A rework alapból
ki van kapcsolva. Ha `class-spec-rework.enabled: true` és a dependency enforcement aktív, az indulás
még a tartós gameplay betöltése előtt leáll, ha bármely kötelező plugin hiányzik vagy a verziója nincs
az engedélyezett készletben.

Így a jelenlegi production a fokozatos fejlesztés alatt változatlanul fut, de félaktív rework nem válhat
észrevétlenül mérvadó állapottá.

## Folia-szabályok

- a profilmódosítás a játékos saját schedulerén fut;
- entity- és modell-cleanup az adott entity schedulerén fut;
- régióhatáron át csak immutable snapshot utazhat, élő entityreferencia nem;
- globális ütemezés csak ritka koordinációra használható, online játékosonkénti tickes scanre nem;
- minden átmeneti állapottulajdonos bekerül a központi player-session cleanupba.
