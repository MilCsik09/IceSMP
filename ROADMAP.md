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
- 💡 **Raid-variánsok:** a raid-mélyítés alapjai (jelentkezés + 10v10 korlát, területkötés,
  pont-tartás objektíva, terület-átvétel) **készek**; további ötlet: zászlófoglalás-mód,
  több egyidejű raid, védő-oldali erődítés-mechanika.
- 💡 **Bűn-rendszer finomítás:** a lopás/árulás detektálás **kész** (idegen territóriumban
  konténer-fosztás +1, frakciótárs ölése +2); további ötlet: bűn-alapú fejvadász-jutalom.
- 🔨 **Kaszt-questek felturbózása:** a plugin-oldal **kész** (TALK_TO_NPC + PARKOUR_TRIAL
  objektívák, FancyNpcs-bridge, 4 kaszt mester-lánca configban) — a mester-NPC-k és
  próbapályák kihelyezése a szerver-csapatra vár; utána jöhet a többi 9 kaszt lánca (config).
- 💡 **Quest-keretrendszer — kész bővítések:** 21 objektíva-típus, **több-objektívás questek**
  (ALL/SEQUENCE), ismétlődő + szezonális questek, NPC quest-adók napi rotációval, választós
  párbeszéd, tárgy/saját-frakció-valuta jutalom, **quest-napló GUI** (`/quest log`), teljes
  játékon-belüli admin-szerkesztő (`/quest admin`), és **frakció-közösségi célok** (közös
  számláló → kassza-jutalom + buff). További ötlet: quest-lánc-térkép GUI, heti/eseményhez
  kötött rotáció.
- ⬜ **Szezonliga jutalom:** a győztes frakció kozmetikai/relikvia-jutalma a szezon végén.
- 💡 **Frakció-diplomácia:** szövetség/békekötés parancsok a királyoknak — 3 királysággal a
  szövetség-tér minimális (egyetlen 2v1 kapcsoló), ezért csak akkor éri meg, ha valaha
  több frakció / al-klán rendszer lesz. (A statikus viszonyok + raid-ellenségesség kész.)
- 💡 **Külön ulti-töltő sáv:** a kivett „kirobbanás" helyett egy második, lassan töltődő ulti-mérő,
  hogy az erőforrás-költség MELLETT látványos burst-jutalom is legyen.
- 💡 **Cosmetics:** részecske-effektek, kalapok (a szezon-jutalom kiterjesztése), GUI-ból.
  (Címek/rangok NEM — ütközne a szerver rang-pluginjaival.)

### Gazdaság
- 💡 **Bank-kamat / kölcsön**, **frakció-bolt NPC-k** (money sink), **kereskedő-karaván esemény**.
- 💡 **Aukció-finomítás:** kézi licit-összeg megadása (most a GUI mindig a minimum következő
  licitet teszi), buy-out ár. (A licit + lejárat + túllicit-visszafizetés alap **kész**.)

### Balansz (élő playtest visszajelzés alapján)
- 🔨 **Hibrid spell-költség finomhangolás:** határeset-spellek „valutájának" pontosítása
  (pl. a 8-éhséges Gyökerezés), tier-alapú erőforrás-költségek és regen-ráta hangolása.
- ⬜ **Frakció-passzív számok** felülvizsgálata playtest után (a Semleges invis már kivéve).
- ⬜ **Spell-mesterség kiterjesztés:** a rang sebzés/hatás-skálázása (most csak cooldown-ra hat).

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
