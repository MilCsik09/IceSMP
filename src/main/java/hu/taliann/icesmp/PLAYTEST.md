
## PR-re-review javítások: tanú-megtartás, szigorú betöltés, idempotens kifizetés (2026-07-26)
- [ ] **Commitolt helyreállítás tanúja megmarad:** ha egy commitolt BUY/BID/SETTLE javítása
      nem sikerül (fedezetlen levonás), a bejegyzés nyitva marad, ÉS a tanúja bent marad a
      `market.yml`-ben. Teszt: a következő indulás **előre** (a commitolt célértékre) próbálja
      újra, nem visszaforgat. Ellenőrzés: a `committed-txn` lista tartalmazza a nyitott
      bejegyzés azonosítóját.
- [ ] **Várólistás tárgylista szigorú:** rontsd el egy `pending-deliveries` lista EGY elemét
      (pl. írj bele stringet), indíts újra → az indulás MEGSZAKAD karantén-másolattal.
      Eddig az az egy elem nyomtalanul eltűnt, a lista többi tárgya betöltődött.
- [ ] **Kétirányú licit-invariáns:** licitáló ZÁROLT ÖSSZEG NÉLKÜL, illetve nem véges/negatív
      ár, licit vagy buy-out érték szintén indulás-megszakítást ad.
- [ ] **WG-híd nincs fail-open ablak újra-feloldás közben:** WorldGuard-reload alatt futtass
      párhuzamosan claim-ellenőrzést → **egyetlen** `/claim` sem mehet át azon, hogy a híd
      félig-kész állapotot mutat („nincs régió"). Amíg nem tud válaszolni, elutasít.
- [ ] **Bukott újra-feloldás után is próbálkozik:** ha a WG épp reload közben van és az újra-
      feloldás elbukik, a log újabb 60 mp-es ablakot jelez, és a híd a következő ablakban
      ISMÉT próbál — nem marad hallgatásban a szerver-újraindításig.
- [ ] **Közösségi kifizetés PONTOSAN egyszer (idempotens):** teljesítsd a célt, majd
      `kill -9` a kifizetés közben. Újraindításkor a log „függő kifizetés — újrajátszás" sort
      ad, és a kincstár/szezon-pont **pontosan egyszer** növekszik (se elmaradás, se duplázás).
      Ellenőrzés: `faction-treasury.yml` → `applied-grants`, `season.yml` → `season.applied-grants`
      tartalmazza a `community:<id>:treasury:<FRAKCIÓ>` / `:season:` azonosítót; ismételt
      indítás után az összeg NEM nő tovább.
- [ ] **Részleges kifizetés pótlódik:** ha a kincstár-írás sikerül, a szezon-írás nem (pl.
      írásvédett `season.yml`), a log RÉSZBEN-sikert jelez, a bejegyzés MARAD, és a jogosultság
      visszaadása után az újrajátszás CSAK a szezon-pontot pótolja — a kincstár nem kap újra.
- [ ] **Config-változás nem téríti el a replayt:** hagyj függő kifizetést, majd írd át (vagy
      töröld) a cél `reward-treasury` értékét a configban, és indíts újra → a kifizetés a
      MENTETT pillanatkép szerint történik (a törölt cél jutalma sem veszik el).
