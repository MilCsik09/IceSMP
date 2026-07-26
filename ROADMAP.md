# IceSMP — Fejlesztési ütemterv (ROADMAP)

Ez az **egyetlen előre néző terv-dokumentum**. A megvalósult állapotot a
[README.md](README.md) és a [PLAYER_GUIDE.md](PLAYER_GUIDE.md) írja le, az architektúrát a
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), a tesztelést a [PLAYTEST.md](PLAYTEST.md).
A kötetlen ötlet-gyűjtő a [docs/ideas/BACKLOG.md](docs/ideas/BACKLOG.md) (konszolidált; a
2026-07-22-i A-O konszolidáció 319 tétele óta a P1-P8 blokkokkal ~396 felsorolás-tételre nőtt,
ebből 15 ✅ / 2 🔄 — munka/érték becsléssel, a technikai adósság az **O-szekcióban**, 25 nyitott
tétellel) — ami onnan zöld utat kap, ide kerül tervezett tételként.
(A korábbi terv-doksik — ideas.md, todo.md, CONTENT-PLAN, DEPTH-ROADMAP, a fázis-napló —
megvalósultak és törölve lettek; a még nyitott pontjaik itt élnek tovább.)

Jelölés: ⬜ tervezett • 🔨 folyamatban • 💡 ötlet (nincs elköteleződés)

---

## Nyitott fejlesztések

### Ismert hibák / technikai kockázatok
- **NYITOTT KIADÁSBLOKKOLÓK VANNAK.** A legutóbbi mélyaudit több közvetlen Folia
  ownership-hibát, gazdasági exploitot és félbe-lezáródó eseményt talált. Lezárva: a fail-open
  YAML-betöltés (karantén + mentés-tiltás), a szakma- és rituálé-hozzávaló check–consume rése, a
  viselt relikvia relog-vesztése, a visszavont akciók jutalmazása, a gyűjtés-progressz
  visszajátszása, a quest-lánc ciklus, a céh–frakció egyeztetés és több jutalom-faucet.
  **Még nyitott:** piac/wallet/inventory tartós tranzakció, tile-entity block-regen
  write-ahead journal, a `MobKillUtil` teljes UUID/snapshot átterve és a `TransientEntities`-re
  épülő world-event életciklus. Playtesten figyeljétek a konzolt
  `region`/`scheduler`/`IllegalStateException` stacktrace-ekre.
- **Technikai adósság (az átfogó code review nem-blokkoló leletei; működést nem érintenek):**
  - Az esemény-managerek közös mintái (véletlen horgony-játékos választás, perc→millis konverzió,
    enabled-enum sorsolás, mulandó entity biztonságos eltávolítása) 5-8 helyen duplikáltak — egy
    közös `WorldEventUtil`/`TransientEntityHandle` helperbe emelés esedékes (BACKLOG O6/O27).
    Ugyanez a duplikáció-osztály: `prefixAt` 20 fájlban (O4), kill-jutalom előszűrő 19 listenerben
    (O24), napi keret 5+ helyen (O25), hibakulcs→default switch 11+ osztályban (O26). A `utils/`
    csomag már létezik (12 osztály, pl. `SpellTargetingUtil` — O5 így zárult le), tehát ez tisztán
    mechanikus munka, nem architektúra-döntés.
  - ✅ MEGOLDVA — `ClaimManager` már debounce-ol: a 8 mutációs pont mind a `requestSave()`-et hívja
    (2 mp-es async coalescing flush a CurrencyManager mintájára), a szinkron teljes-fájl írás
    csak leállításkor fut. Ugyanez a minta MÉG HIÁNYZIK a `CrateManager.persist()`-ből (O23).
  - Az escort/kincs `getHighestBlockYAt` lombkorona/víz felett kozmetikailag pontatlan
    lehet (a kincs/meteor már védve, az escort-konvoj útpontjai nem). (A LootTable `MIN:MAX`
    elgépelései már betöltéskor log-figyelmeztetést adnak a ConfigValidatoron át — megoldva.)
  - Aukció: a minimum/nagy licit elérheti a buy-out árat és azonnal zár (eBay-szemantika —
    szándékos, de a GUI-tipp csak a shift-kattot említi); a vétel-üzenet ára elvben eltérhet a
    ténylegesen levonttól, ha a frakció-viszony épp a kattintás pillanatában vált.

### Játékmenet
- 💡 **Ingame config-vezérlés — kész:** `/icesmp config get|set|unset|list|find` bármely
  config-kulcsot játékon belülről lekér/felülbírál (config.yml-be perzisztálva, azonnali
  reload + validátor, kulcs-tab-complete a teljes kulcstérből). A TELJES spell-balansz
  (`spell-balance.<id>.*`: resource-cost, damage, radius, range, heal/feed, ignite/freeze,
  knockback, cooldown, cost-amount) cast-időben olvasódik — a deklaratív spelleknél sem kell
  restart, és az erőforrás-ár többé nem fixen a cooldown-sávból jön. További ötlet:
  config-diff nézet (mi tér el a defaulttól), többsoros lista-szerkesztő. admin-kijelölt, PONTOS (magasság + nézésirány) spawn-pont
  frakciónként (`/territory setspawn <frakció>`); új játékos a Semleges Királyság spawnján
  jelenik meg, frakcióválasztáskor teleport az új királyság spawnjára, ágy/horgony nélkül a
  saját frakció spawnján éledsz újra. **Frakciót váltani (join ÉS leave) csak a semleges
  fővárosban lehet** (fail-open, amíg nincs kijelölve), a `/faction leave` fizetős váltásnak
  szánt (a leave+join ingyenes kerülőút azonban a frakciórekord törlése miatt MÉG NYITOTT — lásd a
  kiadásblokkolókat), és `/npcbind <npc> faction`
  királyság-választó hírnök-NPC-t köt. További ötlet: váltás-megerősítő GUI a hírnöknél.
- 💡 **Világesemények — bővítve („élőbb világ"):** a vérhold / világboss / invázió / szezon mellé
  bekerült **11 új esemény**, mind config-vezérelt és **pénz-semleges** (tárgy/effekt/XP, sosem valuta),
  és mind a `/events <típus>` admin-triggerrel is kiváltható:
  - **hangulat-események** (északi fény, hulló csillag, köd, szellemek, szentjánosbogarak, állat-vándorlás),
  - **gyűjtögető buff-ablakok** (bányász-láz / termés-óra / halászati láz / XP-óra),
  - **felfedező kincs-esemény** (megjelölt loot-láda, első megtaláló viszi),
  - **Vad Hajsza** (kóborló elit fenevad ritka loottal),
  - **Bőség-idő** (a vérhold pozitív ellenpárja: gyorsabb termés, iker-állatok, csendesebb éj, regen),
  - **kollektív szerver-kihívás** (közös cél boss-baron → mindenki jutalma),
  - **karaván-kíséret** (kooperatív escort: konvoj védése hullámok ellen → loot + bónusz-bolt),
  - **meteor-becsapódás** (kráter kibányászható érccel).
  **Terep-szabály:** a világpusztító események sosem grief-elnek — nincs blokk-romboló robbanás, a meteor
  frakció-területen kívülre irányít és lejáratkor/leálláskor visszaállítja az eredeti terepet.
  További ötlet: heti/eseményhez kötött rotáció, vihar / aranyláz-zóna / napfogyatkozás.
- 💡 **Party-rendszer — kész:** WoW-stílusú csapat (max 5 fő, frakciótól független): meghívó/elfogadás,
  vezetői jogok (kick/promote/disband), csapat-chat (`/p`), **fejenként osztott XP** a közeli tagok közt,
  **personal loot** a plugin-eseményekből (Vad Hajsza, kincs), párton belüli PvP tiltva, és **party-HUD**
  (a HUD-oldalsávon „— Csapat —" szekció: tagnév + színkódolt élet-sáv + 👑 vezető-jelölés, csak csapatban
  látszik). További ötlet: party-célpont jelölés, party-waypoint.
- 💡 **Natív claim + chat-formázó — kész:** chunk-alapú terület-claim (első 3 ingyen, utána égetett
  frakció-valuta ár — money sink; trust; robbanás-védelem; raid-lootable kapcsoló; a meteor/kincs
  események kerülik) a SimpleClaimSystem kiváltására, és natív chat-formázó (LP-prefix + frakció-színes
  név) a LuckPermsChatFormatterFolia kiváltására. A piston/tűz/folyadék edge-case védelem **kész**
  (a TerritoryProtectionListener mintáját követi). További ötlet: claim-GUI, claim-bérlés frakciótársnak.
- 💡 **Raid-variánsok:** a raid-mélyítés alapjai (jelentkezés + 10v10 korlát, területkötés,
  pont-tartás objektíva, terület-átvétel) **készek**; további ötlet: zászlófoglalás-mód,
  több egyidejű raid, védő-oldali erődítés-mechanika.
- 💡 **Bűn-rendszer finomítás:** a lopás/árulás detektálás **kész** (idegen territóriumban
  konténer-fosztás +1, frakciótárs ölése +2). A **bűn-alapú fejvadász-rendszer** (fejpénz a
  körözöttekre, `/bounty` lista, igazságos kivégzés bűn nélkül) is **kész**.
- ⬜ **Kaszt-questek felturbózása — a plugin- ÉS config-oldal KÉSZ:** TALK_TO_NPC +
  PARKOUR_TRIAL objektívák, FancyNpcs-bridge, és **mind a 13 kaszt** kezdő próbája +
  kétlépcsős mester-lánca (mentor + mester-próba) a configban él. Ami hátra van: a
  mester-NPC-k kihelyezése (szerver-csapat, lásd Világépítés).
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

### Lehetséges további irányok (döntésre vár — egyik sincs elkezdve)
A szétszórt „További ötlet" sorok konszolidálva, nagyjából érték/erőfeszítés sorrendben:

1. **Plugin-beolvasztás folytatása** (kevesebb külső függőség):
   - 🟢 gyors: **FarmProtect** (termés-taposás, ~30 sor), **minimotd** (MOTD), **ICEsmpadditions**
     (saját 2 KB-os mini-plugin — a forrása kell hozzá);
   - 🟡 közepes: **economist + service-io** (ha semmi nem függ tőlük → törölhetők; vagy IceSMP
     gazdaság-szolgáltató híd), **FancyHolograms** (általános `/hologram` admin-parancs a meglévő
     TextDisplay-infrára), **AuMenus** (config-vezérelt hub-menü), **VillagerTradeEdit** (statikus
     trade-módosítások configból);
   - 🟠 nagy: **TAB** (header/footer + LP-prefix sorrend a saját HUD-ba), **WorldGuard**
     (admin-zóna flagek a TerritoryManagerbe) — csak alapos playtest után.
2. **Végjáték-progresszió:** presztízs/paragon szintek a max kaszt után, relikvia-fejlesztés
   (reforge), szezon-emléktárgyak.
3. **Egyedi dungeonök (PvE):** kézzel készített helyszínek megnevezett bossokkal, mechanikákkal,
   loot-táblákkal (a LootTable + world-event infra újrahasznosítható).
4. **Kozmetikumok GUI-ból, valutáért** (money sink): részecske-nyomok, kalapok, halál-üzenetek.
   A **natív crate-rendszer KÉSZ** (crates.yml, /crate set|buy, pörgős reveal-GUI, quest-kulcs jutalmak — a CrazyCrates kiváltva); a kozmetikum-bolt maga még nyitott.
5. **Világesemény-bővítések:** vihar / aranyláz-zóna / napfogyatkozás, heti/eseményhez kötött
   esemény-rotáció, karaván-készlet napi rotáció.
6. **Party-extrák:** party-célpont jelölés, party-waypoint.
7. **Claim-extrák:** claim-GUI, claim-bérlés frakciótársnak. (A piston/tűz/folyadék védelem kész.)
8. **Raid-variánsok:** zászlófoglalás-mód, több egyidejű raid, védő-oldali erődítés.
9. **Quest-extrák:** quest-lánc-térkép GUI, a maradék 9 kaszt mester-lánca (csak config).
10. **Külön ulti-töltő sáv** (második, lassan töltődő mérő a burst-jutalomhoz).

### Világépítés (szerver-csapat, nem plugin-kód)
- ⬜ Fővárosok, az Élet Fája (spawn), a Sötét romváros — **Thanaopolis, a Holtak Városa** (történelmi nevén Mortengrad; a
  Kitaszítottak/DARK fővárosa) — megépítése; `/territory` kijelölések,
  majd `/territory setspawn <frakció>` mind a 4 királyság-spawnra + a királyság-választó
  hírnök-NPC kihelyezése a semleges fővárosban (`/npcbind <npc> faction`).
- ⬜ Parkour-pályák (a questek egyetlen hivatkozott pályája a `kezdo_parkour` — az
  akrobata-kihívás; a kaszt-fejlődés NEM függ parkourtól), rituálé-oltár helyszínek és
  intro-kamera waypointok kihelyezése.
- ⬜ A questek által megkövetelt **18 NPC** kihelyezése + `/npcbind` kötések: `hirnok`,
  `vandor_kereskedo`, `erdei_venek`, `kovacs_mester`, `revesz`, `pakt_mester` (a
  Boszorkánymester mester-láncát is ő adja) + 12 kaszt-mester (`harcos_mester`,
  `ijasz_mester`, `varazslo_mester`, `orgyilkos_mester`, `druida_mester`, `paplovag_mester`,
  `halallovag_mester`, `saman_mester`, `szerzetes_mester`, `pap_mester`, `demonvadasz_mester`,
  `sarkany_mester`). A **13 mentor+mester-próba lánc configban KÉSZ** — csak a kihelyezés vár.

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
