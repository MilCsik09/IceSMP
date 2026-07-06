# IceSMP — Fejlesztési ütemterv (ROADMAP)

Ez az **egyetlen előre néző terv-dokumentum**. A megvalósult állapotot a
[README.md](README.md) és a [PLAYER_GUIDE.md](PLAYER_GUIDE.md) írja le, az architektúrát a
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), a tesztelést a [PLAYTEST.md](PLAYTEST.md).
(A korábbi terv-doksik — ideas.md, todo.md, CONTENT-PLAN, DEPTH-ROADMAP, a fázis-napló —
megvalósultak és törölve lettek; a még nyitott pontjaik itt élnek tovább.)

Jelölés: ⬜ tervezett • 🔨 folyamatban • 💡 ötlet (nincs elköteleződés)

---

## Nyitott fejlesztések

### Ismert hibák / technikai kockázatok
- Jelenleg nincs nyitott, reprodukált technikai blocker. A legutóbbi auditban talált Folia
  célpont-scheduler hibák, az Angry Chicken cross-region damager kockázata és az orb Java 21
  build-környezete javítva lett; playtesten továbbra is figyeljétek a konzolt
  `region`/`scheduler`/`IllegalStateException` stacktrace-ekre.

### Játékmenet
- 💡 **Világesemények — bővítve („élőbb világ"):** a vérhold / világboss / invázió / szezon mellé
  bekerült a **hangulat-események** rendszere (északi fény, hulló csillag, köd, szellemek,
  szentjánosbogarak, állat-vándorlás), a **gyűjtögető buff-ablakok** (bányász-láz / termés-óra /
  halászati láz / XP-óra), a **felfedező kincs-esemény** (megjelölt loot-láda, első megtaláló viszi)
  és a **Vad Hajsza** (kóborló elit fenevad ritka loottal). Mind config-vezérelt és pénz-semleges
  (tárgy/effekt/XP, sosem valuta). További ötlet: heti/eseményhez kötött rotáció, karaván-kíséret.
- 💡 **Raid-variánsok:** a raid-mélyítés alapjai (jelentkezés + 10v10 korlát, területkötés,
  pont-tartás objektíva, terület-átvétel) **készek**; további ötlet: zászlófoglalás-mód,
  több egyidejű raid, védő-oldali erődítés-mechanika.
- 💡 **Bűn-rendszer finomítás:** a lopás/árulás detektálás **kész** (idegen territóriumban
  konténer-fosztás +1, frakciótárs ölése +2). A **bűn-alapú fejvadász-rendszer** (fejpénz a
  körözöttekre, `/bounty` lista, igazságos kivégzés bűn nélkül) is **kész**.
- 🔨 **Kaszt-questek felturbózása:** a plugin-oldal **kész** (TALK_TO_NPC + PARKOUR_TRIAL
  objektívák, FancyNpcs-bridge, 4 kaszt mester-lánca configban) — a mester-NPC-k és
  próbapályák kihelyezése a szerver-csapatra vár; utána jöhet a többi 9 kaszt lánca (config).
- 💡 **Quest-keretrendszer — kész bővítések:** 21 objektíva-típus, **több-objektívás questek**
  (ALL/SEQUENCE), ismétlődő + szezonális questek, NPC quest-adók napi rotációval, választós
  párbeszéd, tárgy/saját-frakció-valuta jutalom, **quest-napló GUI** (`/quest log`), teljes
  játékon-belüli admin-szerkesztő (`/quest admin`), és **frakció-közösségi célok** (közös
  számláló → kassza-jutalom + buff). További ötlet: quest-lánc-térkép GUI, heti/eseményhez
  kötött rotáció.
- 💡 **Szezonliga jutalom-bővítés:** a szezon-végi győztes-jutalom **kész** (kassza + tagoknak
  buff/tárgy/tűzijáték); további ötlet: egyedi kozmetikai relikvia vagy szezon-emléktárgy.
- 💡 **Frakció-diplomácia:** szövetség/békekötés parancsok a királyoknak — 3 királysággal a
  szövetség-tér minimális (egyetlen 2v1 kapcsoló), ezért csak akkor éri meg, ha valaha
  több frakció / al-klán rendszer lesz. (A statikus viszonyok + raid-ellenségesség kész.)
- 💡 **Külön ulti-töltő sáv:** a kivett „kirobbanás" helyett egy második, lassan töltődő ulti-mérő,
  hogy az erőforrás-költség MELLETT látványos burst-jutalom is legyen.
- 💡 **Cosmetics:** részecske-effektek, kalapok (a szezon-jutalom kiterjesztése), GUI-ból.
  (Címek/rangok NEM — ütközne a szerver rang-pluginjaival.)

### Gazdaság
- ❌ **Bank-kamat — elvetve:** a kamat a semmiből teremtene valutát (addolt pénz → infláció), ez
  ellentétes a szerver „nincs addolt pénz" elvével. Csak akkor jöhet szóba, ha szigorúan
  pénz-semleges / kizárólag a frakciókasszából fedezett formában tervezzük. (A **frakció-bolt
  NPC-k** money sink **kész**: FancyNpcs-hez kötött, config-vezérelt boltok, jobb-katt vásárló GUI,
  égetett ár. Minden gazdasági bővítés nyelő vagy pénz-semleges legyen, sosem faucet.)
- 💡 **Kereskedő-karaván esemény — kész:** időszakos vándorkereskedő (config-vezérelt megállók vagy
  véletlen játékos-közeli felbukkanás), időkorlátos ottmaradás, jobb-katt = ritka portékák boltja
  (égetett ár = money sink). Admin: `/events caravan arrive|depart`. További ötlet: véletlenszerű
  napi készlet-rotáció, karaván-kíséret védelmi mini-esemény.
- 💡 **Aukció-finomítás — kész:** a GUI-ban kattintás-típus szerinti licit (bal = minimum, jobb =
  nagyobb ugrás +25%), **buy-out ár** (`/market auction ... buyout:<ár>`, shift-katt = azonnali
  megvétel). További ötlet: teljesen szabad összegű licit chat-parancsból (listing-ID targeting).

### Balansz (élő playtest visszajelzés alapján)
- 🔨 **Hibrid spell-költség finomhangolás:** határeset-spellek „valutájának" pontosítása
  (pl. a 8-éhséges Gyökerezés), tier-alapú erőforrás-költségek és regen-ráta hangolása.
- ⬜ **Frakció-passzív számok** felülvizsgálata playtest után (a Semleges invis már kivéve).
- 💡 **Spell-mesterség — kész:** a rang a cooldown mellett a **sebzést, self-heal-t és az
  effekt-időtartamot** is skálázza (config: power-per-rank / max-power-multiplier). További
  ötlet: rang-alapú extra effektek (pl. egy plusz státusz a max rangon).

### Világépítés (szerver-csapat, nem plugin-kód)
- ⬜ Fővárosok, az Élet Fája (spawn), a Sötét romváros megépítése; `/territory` kijelölések.
- ⬜ Parkour-pályák, rituálé-oltár helyszínek és intro-kamera waypointok kihelyezése.
- ⬜ Kaszt-mester NPC-k (FancyNpcs: `harcos_mester`, `ijasz_mester`, `varazslo_mester`,
  `orgyilkos_mester`) és a mester-próbapályák (`harcos_proba`, `ijasz_proba`,
  `varazslo_proba`, `orgyilkos_proba`) kihelyezése a fővárosokban.

---

## Karbantartási elvek

- Minden változás **Folia-szabálykövető** (ARCHITECTURE 4. fejezet): entitást csak a saját
  régió-szálán mutálunk; cross-entity művelet a cél `getScheduler()`-én fut.
- Új spell a `ConfiguredSpell` builderrel + `classes.yml` unlock-bejegyzéssel; új világtartalom
  config-vezérelt. Fordítás-ellenőrzés minden változás után.
- Player-facing szöveg magyarul; a guide-ok (PLAYER_GUIDE + docs/player-guide + PLAYTEST)
  minden játékmenet-változással együtt frissülnek.
- Betöltéskor a `ConfigValidator` konvenció-alapon ellenőrzi a configot (material/currency-nevek,
  százalék-tartomány, nem-negatív időtartamok) — az admin-elgépelések tiszta log-figyelmeztetésként
  jelennek meg, nem némán az alapértékre esve vissza.
