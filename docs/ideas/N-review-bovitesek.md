# N — A teljes kód-review bővítési ötletei (backlog)

> Forrás: a 2026-07-20-i teljes review (3 párhuzamos mélyvizsgálat: világesemények+zónák,
> frakció-rendszerek, itemek+enchantok+spellek). A review HIBA- és OPTIMALIZÁLÁS-találatai
> már javítva; ez a fájl a felmerült BŐVÍTÉS-ötleteket gyűjti, olcsóbbtól a nagyobb felé.

## Világesemények / zónák

- [ ] **N1 — `/events spawncheck` debug-parancs:** adott koordinátára meghívja az
      EventSpawnGuard `isBlocked`/`isUnsafeSurface` metódusait eventenként — az admin azonnal
      látja, MIÉRT nem spawnol ott egy esemény. (A meglévő API-t közvetlenül kiszolgálja.)
- [ ] **N2 — Archeológia: több egyidejű lelőhely** (`archeology.max-sites`), a jelenlegi
      egyetlen site-mező listává generalizálva.
- [ ] **N3 — Archeológia "pity timer":** ha N kiásásból nem esett unique lelet, a következő
      garantált (a `finds` tábla meglévő `unique:` szintaxisára építve).
- [ ] **N4 — Purge-kill toplista:** a rontás-irtás StatsManager-kategória, amit a Heti Krónika
      is felhasznál ("A rontás legnagyobb ellenségei").
- [ ] **N5 — Krónika-archívum:** az utolsó N (pl. 4) szám tárolása, `/kronika <szám>`
      paraméterrel; a heti world-boss/invázió/hajsza-számok felvétele a krónikába.

## Frakció-rendszerek

- [ ] **N6 — `/faction tax arrears` + `/faction tax payoff`:** saját hátralék megtekintése és
      önkéntes azonnali törlesztés (a meglévő taxArrears/currencyManager API-ra épül).
- [ ] **N7 — Signature-étel buff-cooldown:** az abszorpció/tűzállóság újraevéssel jelenleg
      korlátlanul frissíthető — rövid per-étel cooldown configból.
- [ ] **N8 — `/suttogas allapot`:** a Suttogó lássa a saját gyanú-szintjét (mennyire közel a
      lelepleződés); admin-oldalon "hány Suttogó aktív" statisztika a cache-ből.
- [ ] **N9 — Zóna-belépési üzenet cooldown:** gyors oda-vissza határmozgásnál ne villogjon az
      action-bar (rövid per-játékos cooldown a TerritoryListenerben).
- [ ] **N10 — `territory.mob-rules.<szelektor>.max-level`** zónánkénti felülbírálás; boss/
      esemény-mobok kizárása a zóna-bónuszból (ne kapjanak dupla szintet).
- [ ] **N11 — Memória-szilánk: profession-pool bónuszpont** beváltás (a talent-út mintájára);
      napi/heti limit a bónuszpont-grind ellen.

## Itemek / enchantok / spellek

- [ ] **N12 — Iskola-tekercsek** (M1 [TOP] az M-bootstrap.md-ben): fogyasztható tekercs, ami
      rövid időre iskola-ellenállást ad — a Rúnavért/counter-enchant rendszer kiterjesztése.
- [ ] **N13 — Páncél-enchant riderek:** a Rúnavért mellé 1-2 páncél-enchant aktív mellékhatással
      (pl. lassulás-tisztítás fagy-iskola találatnál, kis visszacsapás káosz ellen).
- [ ] **N14 — Rúnavért VFX:** finom rúna-particle a sikeres iskola-ellenálláskor (az eltávolított
      action-bar helyett vizuális, nem tolakodó visszajelzés).

## Mozgás-esemény infrastruktúra (nagyobb)

- [ ] **N15 — Közös PlayerMoveEvent-diszpécser:** 9 listener figyeli külön a move-eventet, és
      lépésenként többször számolja ugyanazt a zóna-lookupot — egy megosztott, lépésenként
      egyszer számított "aktuális zóna" átadása csökkentené a redundanciát. (A lookup olcsó,
      ez tisztán szépészeti/skálázási tétel — csak nagy játékosszámnál éri meg.)

## Világesemények

- [x] **N16 — Rontás-góc DARK-perem sorsolás `[KÉSZ ✅]`:** a tulaj által elfogadott irány
      („a korrupt zóna dark territory felőli terjedése tetszik!") — a természetes góc-nyílás
      configolt eséllyel (`corruption.dark-bias.chance-percent`, 65) egy véletlen DARK
      territórium pereméN TÚL történik (`min/max-edge-distance`, 24..96 blokk), külön
      „a Kitaszítottak földjének pereméről szivárgott ki" broadcast-tal; a territórium
      belsejét a spawn-rules továbbra is védi, fallback a horgony-játékos út.
- [x] **N18 — Gameplay-balansz kör `[KÉSZ ✅]`:** teljes-diff gameplay-review (4 mélyvizsgálat)
      javításai — bank-only zárás (kassza-kivét veretben + napi keret; vagyon-elérés XP-t
      fizet), vérdíj per-victim cooldown, kém-pont csak ellenséges földön + napi limit,
      párbaj-pont csak frakciók közt + felkérés-lejárat, B33×G16 nem szorzódik, raid-pont
      10 + raid-cooldown, mob-pénz/horgász napi sapka, váltási díj 3%, H14 variáns-hívás
      visszakötve, mob-szint hard-cap 15, dark-undead spawn-rules sor, rontás dark-bias 50%,
      rejtvény-jutalmak vágva + fejezet-XP.
- [x] **N17 — Aszimmetrikus szezon-liga `[KÉSZ ✅]`:** a tulaj felvetésére (2+1+1 frakció —
      tiszta hadi-ligaként sántít) minden liga-pont-jóváírás forrás-címkés, és a
      `world-events.season.source-weights` mátrix frakciónként súlyozza: RED/BLUE/DARK a
      háborúból (raid), a NEUTRAL a közösségi célból + rontás-tisztításból (1.5×), a DARK a
      párbajból + kém-küldetésből (1.5×) pontoz erősebben. Új források: community (8),
      cleanse (6), duel (2), spy (2) — mindegyik élő configból.

## Refaktor-backlog

> A refaktor/technikai-adósság tételek EGY helyen élnek: [O-refaktor.md](O-refaktor.md)
> (oda költözött a korábbi docs/REFACTOR_CANDIDATES.md teljes tartalma ÉS az itteni
> N19-N23 is, utóbbiak #24-28 sorszámon).

## Teszter-visszajelzések (2026-07-20)

- [ ] **N24 — Claim-segéd:** a claimelés "macerás" — kijelölés-varázsló (wand-item vagy
      lépésenkénti GUI-asszisztens, élő előnézettel a DisplayFx-falakkal); a pénz-oldala
      és a magasság/irány-bővítés a teszterek szerint jó, marad.
- [ ] **N25 — Hely-kötött világesemények:** a world boss / escort ne (csak) játékos-horgonyra
      spawnoljon, hanem fix/megjelölt helyszínekre is (admin-kijelölt esemény-pontok listája,
      sorsolás közülük); + ÚJ esemény-ötlet a lore-ból: kultista támadás/keresés/követés.
- [ ] **N26 — Ismételt-spawn kegyelem:** ha ugyanarra a játékosra rövid időn belül többször
      spawnol boss/esemény, a boss legyen gyengébb (kevesebb HP) VAGY a játékos kapjon
      ideiglenes harci buffot — a horgony-rotáció (KÉSZ) az első lépés, ez a második.
- [ ] **N27 — Vándor kereskedő-karaván szabad spawnja:** a kereskedő-karaván bárhol
      megjelenhet (ne fix pont / játékos-közel legyen kötelező) — a N25 esemény-pont
      rendszerrel együtt kezelendő.
