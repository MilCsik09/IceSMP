# IceSMP — Teljes funkció-leltár (belső bemutató)

> Minden rendszer és mechanika egy helyen, belső "mutogatásra". Technikai mélységű,
> de teljes. Állapot: 2026-07-22 — kód-oldalon launch-kész, élő playtest előtt.
> Alap: Folia (régió-szálas Paper), MC 1.21.11, Java 21, ~300 Java-fájl / 60+ manager.

## ⚔ Kasztok és képességek

- **13 frakció-független kaszt** (Varázsló, Harcos, Íjász, Orgyilkos, Druida, Paplovag,
  Halállovag, Sámán, Szerzetes, Pap, Boszorkánymester, Démonvadász, Sárkányidéző) +
  **32 specializáció** (25. szinttől) + **~390 spell** (config-vezérelt katalógus).
- **Lélekkapocs**: a cast-eszköz — jobb-klikk cast, sneak-klikk spell-váltás, kedvencek,
  spellkönyv-GUI. A kaszt-választás VÉGLEGES (admin-only reset: /class admin resetclass).
- **Hibrid erőforrás-modell**: kasztonként saját Erő-csík (Mana/Düh/Energia…, lazy-regen,
  HUD-on), de a vér-mágia HP-t, a rituálék XP-t, a nehéz fizikai spellek éhséget kérnek.
- **Spell-mesterség**: használattal rangot lép a spell — cooldown csökken, sebzés/heal/
  effekt-időtartam nő; **közös erő-plafon** (spells.total-power-cap 1.75) fékezi a
  mastery × szint-bónusz × kombó-finisher szorzatot.
- **Kombók és láncok**: konfigurált spell-párok/hármasok gyors egymásutánja cooldown-
  visszatérítést + finisher-bónuszt ad; a HUD jelzi a nyíló kombó-ablakot.
- **8 varázs-iskola** (Tűz/Fagy/Szent/Árnyék/Természet/Vihar/Káosz/Ősmágia) saját
  sebzés-típusokkal + iskola-ellenes enchantok (Fagypáncél, Főnixtoll) + Rúnavért
  páncél-enchant (spell-rezisztencia, plafon 60%).
- **Talent-fa**: 5 szintenként pont; stat-talentek (HP/sebzés/sebesség/XP), tier-kapuk,
  kasztonkénti tier2 build-pár + mind a 32 spec saját tier3 talentje; szakma-talentek.
  Bónusz-pont Emlékszilánkból (/emlek).
- **Állapotos spellek** külön osztályban (druida-forma álcával, dupla-ugrás,
  rejtőzés…), kötelező állapot-takarítással kijelentkezéskor.

## 🏰 Frakciók és politika

- **4 frakció**: RED=Perinfernicitas (Láng), BLUE=Cryghaliris (Fagy), NEUTRAL=Menedék
  (Ryanora/Caldestera), DARK=Kitaszítottak. Frakció-spawnok, fővárosi respawn.
- **Passzívák**: RED tűz/láva-immunitás; BLUE fagy+fulladás-immunitás, lassabb éhség;
  NEUTRAL zuhanás-immunitás, mobok békén hagyják, adómentes; DARK wither-immunitás,
  élőhalottak nem támadják.
- **Király-rendszer**: szavazás (min-votes), term, adókulcs-állítás (max 10%),
  kassza-kivét (napi 1000, fizikai veretben), raid-hirdetés.
- **Vének Tanácsa (NEUTRAL)**: heti szavazás (/tanacs szavaz), élő top-3, gazdasági
  jogok — kassza-kivét (400/nap), karaván, Vásár-hét (piaci díj-kedvezmény ablak);
  raid-jog szándékosan nincs.
- **Adó**: óránkénti %-os + fejadó, hátralék-nyilvántartás, adócsalás-strike → bűn.
- **Bűn-rendszer**: gyilkosság/árulás/lopás → bűnpont; 4 bűn = száműzetés a DARK-ba
  (örök paktum, kétlépcsős megerősítés önkéntes belépésnél); vérdíj-rendszer (bounty,
  25/bűn, 12h per-áldozat cooldown); kivétel-lánc: raid → párbaj → hadi-ablak →
  vérdíj → Kapu-zóna → DARK-áldozat (sosem bűn).
- **Becsület-párbaj** (/parbaj): bűnösök heti 2× tisztulhatnak győzelemmel;
  pár-invariáns védett (egy kihívó = egy függő kihívás).
- **Hadi-ablak**: menetrend szerint (szo-va 18-20h) a RED↔BLUE ölés bűn-mentes,
  liga-pontot ér ("war" forrás) — napi pont-plafon + per-áldozat cooldown farm-fék;
  admin: /faction war start|stop.
- **Raid**: király hirdeti, jelentkezés, 10v10, célzóna (alapból a védekező fővárosa),
  kill-pontok, kassza-tét, győztes liga-pont; **élő ostrom alatt a célzónában a
  regisztrált harcosok PvP-je a védett zónában is engedett**; ostrom-bontás a
  regen-rendszerrel (lásd lent); restartnál broadcast-tal zár.
- **Frakció-váltás**: 500 + 72h cooldown, csak a semleges fővárosban; **szezononként
  max 2 váltás** (az ingyenes utak is számítanak), **a szezon utolsó 7 napjában teljes
  váltás-zár**; Menedékből ingyen, DARK-ba bűnösként ingyen (örök).
- **A Suttogók**: rejtett státusz a látható frakció fölött — Sötét Rítus (éjjel,
  sculkon, vér-áras), titkos /suttogas csatorna, gyanú-rendszer (tettek növelik,
  idő csökkenti, küszöbnél lelepleződés → bűnök + kitaszítás); előnyök: kultista
  loot-részesedés, éjszakai élőhalott-békesség, csendes 25% feketepiac-kedvezmény,
  exkluzív recept (Az Éjszaka Pengéje), tanú-vád mechanika (/suttogas vad).
- **Kém** (/kem): rövid álca-akció LibsDisguises-zel, spy-jutalom, raid alatt tiltva.

## 💰 Gazdaság

- **4 frakció-valuta** (Vörös Talentum, Kék Talentum, Creutzér, Csontveret) — CMD-s
  fizikai veretek több címletben.
- **SZENT SZABÁLY: a bank-számlára csak fizikai veret-befizetéssel kerülhet pénz** —
  minden jutalom fizikai tokenben érkezik (payOutTokens), a rendszer sosem "nyomtat"
  számlára. Gépi audittal igazoltan sértetlen.
- **Dinamikus árfolyam**: banki kínálat-alapú árfolyam-görbe (reference-supply),
  3% váltási díj, árfolyam-tábla NPC (exchange board), gazdasági események
  (konjunktúra/dekonjunktúra ablakok, tanácsi Vásár-hét).
- **Pénz-források napi plafonnal**: mob-drop erszények (300/nap), horgász-lelet
  (150/nap), felvásárló NPC (250/nap), lélekkő-drop (DARK-út); questek, AFK-jutalom.
- **Nyelők**: boltok (általános/kellék/feketepiac/karaván), láda-kulcsok, komp-díj,
  piac/aukció-díj (10%, boom alatt 5%), váltási díj, frakció-váltás, spec-respec,
  claim-vétel, céh-alapítás, adó.
- **Piac** (/market): listázás, keresés, díj; **aukció**: licit + buy-out, túllicitált
  visszatérítés; relikvia-szekció.
- **Boltok**: fix NPC-boltok (npcbind), rotációs karaván-készlet unique-slot
  garanciával, feketepiac (Suttogó-kedvezménnyel).
- **Komp** (/komp): fizetős útvonalak a nagy vizeken át (money sink), NPC-köthető.
- **Bank** (/bank): befizetés/kivét fizikai veretben, egyenleg; /currency pay
  alapból kikapcsolva (díj-mentes P2P ellen).

## 🗺 Territóriumok, claimek, védelem

- **6 zónatípus**: FACTION (tagok építhetnek, claimelhető), PROTECTED_FACTION /
  PROTECTED_CITY / CAPITAL (senki nem épít; a főváros a bank/váltó kapuja),
  DOOM_GATE (törvényen kívüli PvP-zóna a Kárhozat Kapujánál), DUNGEON (kulcs-kapus).
  Kör- és poligon-alak, Y-sáv, chunk-indexelt O(1) lookup.
- **Zóna-szabály mátrix**: build/interact/pvp/explosions/fire zónatípusonként
  configból; admin- és builder-bypass node-ok; PvP-tiltás kiterjed potion/TNT/pet
  forrásokra is.
- **Claim-rendszer** (/claim): chunk-alapú birtok, trust-lista, konténer-védelem,
  piston/tűz/folyadék/robbanás-védelem, raid-fosztogatás kapu (csak konténer,
  csak regisztrált támadónak), belépés-jelzés action-baron.
- **Fővárosi törvény**: fegyver-tilalom a semleges fővárosban (Sétapálca rejtett
  penge kivétel), körözöttek kitiltása; városi őrség (kozmetikai).
- **Globális nether-portál tiltás**: kaput gyújtani csak bypass-joggal lehet — a
  világ egyetlen aktív átjárója a Kárhozat Kapuja (Olethropyla).

## 💥 "A világ visszagyógyul" — rombolás/robbanás-rendszer

- **Minden rombolás-forrás lefedve**: robbanások (creeper, TNT+lánc, Wither-spawn/
  koponya, kristály, ghast, ágy/horgony), Wither test-bontása, ravager/enderman,
  fizika-lepattanás (fáklya a kirobbant falról), ostrom-bontás, (kapcsolhatóan)
  szabad bontás.
- **Zónánkénti mátrix** (regen.zones.<típus>): gyógyulás / régi teljes tiltás /
  vanília — a vadon is bekapcsolható (ott a kézi bányászás MINDIG vanília marad).
- **Látvány**: a kirobbant blokkok törmelék-entitásként repülnek ki a robbanás
  középpontjából (debris-percent szabályozza az arányt), pattognak-csúsznak, majd
  porfelhővel szétporladnak — sosem raknak le blokkot, sosem droppolnak.
- **No-drop garancia**: yield 0, XP 0, TNT sosem épül vissza (lánc él, hurok nincs),
  a kráterbe hulló perem-homok itemként esik le (nem vész el némán).
- **Visszaépülés**: pontos BlockData-pillanatkép (orientáció, waterlogged, minden),
  alulról felfelé, állítható tempóval (blocks-per-pass × restore-interval-ticks) és
  indulási késleltetéssel (robbanás/ostrom/szabad bontás külön kulcs); blokkonként
  anyag-hű lerakás-hang + porfelhő; támasz-tudatos sorrend (gravitációs blokk csak
  alapra, fáklya csak falra — grace után kényszer); élőlényre SOSEM épül rá
  (befalazás-védelem); restart-biztos perzisztens várólista (block-regen.yml).
- **Tile-entity-k**: alapból "óvó rúnák" effekttel sérthetetlenek; kapcsolható
  teljes NBT-út (struktúra-pillanatkép): láda/shulker-tartalom, tábla-szöveg,
  fej-textúra, zászló-minta, spawner — robbanáskor semmi nem szóródik ki, minden
  pontosan visszatér; dupla-láda-biztos.
- **Fizika-pajzs** (kapcsolható): irány-szelektív víz-szabály — a kráterbe a víz
  látványosan beömlik, de onnan tovább nem terjed (a fal mögötti terek nem áznak el),
  a visszaépülő fal kiszorítja; a friss blokk időzített utó-pajzsot kap; kikapcsolva
  a hurok-fék (max-recaptures) véd a végtelen körök ellen.
- **Ostrom-bontás**: élő raid alatt a célzónában a regisztrált harcosok drop/XP
  nélkül bonthatnak, a fal a raid után visszaépül; ostromon kívüli szabad bontás
  külön kapcsolóval (alapból ki).

## 🌍 Világesemények (16+ rendszer)

- **Nagy események** (MajorEventGate: egyszerre csak EGY fut): világboss (10 archetípus,
  fázisok, speciál-támadások, killer-loot + skálázott jutalom), invázió (horda +
  bajnok, mob-élettartam fékkel), Vad Hajsza (menekülő préda, sebző-részesedés),
  karaván-kísérés (escort), kultisták (rítus/portya/hírvivő variánsok, Suttogó-szál).
- **További események**: vérhold (drop-szorzók, ünnep-skálázás), kincsláda
  (runner-up ablakkal), meteor (kráter + ritka érc, auto-visszaállítás), rontás-góc
  (sculk-terjedés, irtó-részesedés), régészet (brush-lelőhelyek), játékos-karaván
  (szállítmány-kockázat), kalmár-karaván, bőség-ablak, gyűjtő-buff, szerver-kihívás
  (per-player skálázott), rejtett helyek (D8), ambient-események (kültéri jutalom),
  Idegen NPC (28 kóbor mondat), tábortűz-mesék (37 lore-történet).
- **Broadcast-diéta**: a nagy események globális hírek, a személyes léptékűek csak
  a helyszín környékén hallatszanak (LocalAnnounce, Folia-biztos).
- **EventSpawnGuard**: minden esemény-spawn közös kapun — territory/claim/WG mátrix
  eseményenként, felszín-biztonság, mob-előkészítés (zombisodás/égés ellen).
- **Mob-skálázás**: távolság-alapú szint + **zóna-rámpa** (a legközelebbi biztonságos
  zóna szélétől 250 blokk/szint — a 13k-ra lévő fővárosok környéke is kezdőbarát);
  DARK-földön +2 szint és az élőhalott nappal sem ég; Thanaopolis élőhalott-populáció
  (max 24/territórium, batch-spawn, élettartam).
- **Szezon-liga**: 60 napos szezon, forrás-súlyozott pontok frakciónként (war/raid/
  community/duel/spy/cult…), utolsó 7 nap finálé (top-2 dupla pont, nagydöntő-
  broadcast), bajnok-jutalom (kassza + tagi csomag + offline-pending kifizetés),
  megbízható rollover (offline-átfordulás pótolva).
- **Ünnepek**: HolidayService — vérhold/invázió esély-szorzók ünnepnapokon.

## 📜 Questek és tartalom (160 quest)

- **Onboarding-lánc**: első belépéskor auto-induló Hírnök-lánc (beszélgetés → első
  csata → gyűjtés → útmutatás), intro-címszekvencia, first-join a semleges spawnon.
- **Kaszt-tartalom**: mind a 13 kaszt mentor+mester lánca; level-50 capstone-lánc.
- **Sztori**: 3 fejezet (szezononként nyílnak, 5-5 quest), szezon-közepi "A Fa
  üzenete" lánc (20-40. nap dátum-kapuval), Gyökerek-lánc (Radicora), Hamu és
  zúzmara lánc (hadi-ablak felvezető), révész-lánc, vezeklés-lánc (az EGYETLEN út
  a DARK-paktum alól — bűn-tisztító záró jutalommal), nekromanta-beavatás.
- **30 rejtvény-quest**: a leírás versbe rejti a célt, a megfejtés a játékos dolga.
- **Ismétlődő réteg**: napi NPC-rotáció (15-ös poolból naponta 3), heti frakció-
  questek (2 kör), heti beszállítók (vándor kereskedő), nagy heti kihívások
  (láda-kulcs jutalommal), napi quest (/daily), elágazó dialógus (merchant_choice).
- **17 objective-típus** (kill/gyűjt/craft/halász/szállít/territórium-látogatás/
  bűvölés/tenyésztés/biome-felfedezés/NPC-dialógus…), többcélú questek (ALL/ANY),
  min/max-season-day kapuk, chapter-kapuk, requires-level/faction/quest.
- **Jutalmak**: kaszt-XP, fizikai valuta, itemek, láda-kulcsok, spell-unlock,
  bűn-tisztítás; quest-toast + NPC-aura jelzések (arany=felvehető, zöld=leadható).
- **Közösségi célok**: frakció- és szerver-szintű megosztott számlálók (vas-gyűjtés,
  vadászat, raid-győzelem…), 50-60 fős szerverre hangolva; **elérések** (achievements)
  mérföldkő-jutalmakkal; **bestiárium** (kill-lajstrom fokozatokkal, /bestiarium).

## ⛏ Szakmák, itemek, craft

- **8 szakma** (bányász, füvész, favágó, halász, szakács, kovács, alkimista,
  bűvölő) — 1 gyűjtő + 1 készítő választható, halász+szakács alap; szintek 50-ig,
  fokozat-címek, szakma-XP minden releváns cselekvésből.
- **410 recept** a katalógusban (recept-könyv GUI, kereső), szint-kapukkal;
  recept-tárgyak (megtanulható), tervrajzok, unique köztes-anyagok (72 db,
  loot/bolt/craft forrásokkal).
- **Szignatúra-rendszer**: a mestermunka a készítő nevét viseli; craft-affixok
  (tier-alapú tulajdonság-roll), tárgy-raritás rendszer, mestermű-esély.
- **Craft-safety**: unique anyagok védve vanília crafttól, kohótól (bemenet is!),
  amboszttól, fogyasztástól, lerakástól.
- **Szakma-céh heti célok** (/szakmacel): közös termelési célok jutalommal;
  offline-pending kifizetés.
- **Relikviák**: unique erő-tárgyak PDC-lánccal — rituálé-oltárok (frakció-szárnyak:
  főnix/fagy/vándor/csont-elytra, áldozat-recepttel), kaszt-szentély buffok,
  loot-források; passzív relikvia halálnál "elveszett" státuszba kerül (reclaim-
  rituáléval visszaidézhető), fegyver-relikvia PvP-ben gazdát cserél; inaktivitás-
  lejárat; relikvia-aukció.
- **Láda-rendszer (natív crate)**: fizikai láda-pontok + kulcs-itemek (köznapi/
  ritka), /crate buy (valuta-sink), pörgős reveal-animáció, súlyozott loot-táblák
  (sosem pénz); kulcs quest-jutalomból is.
- **Pénz-itemek**: erszények (mob-drop, súlyozott), Opálos Emlékszilánk (talent/
  respec-valuta), lélekkövek (soulforge-fejlesztés).

## 🐾 Kísérők és extra rendszerek

- **Pet-rendszer**: befogó-eszközök (CMD 5301+), társ-idézés, pet-szint és harc-
  asszisztencia, Vadmester/Nekromanta társak; **Soulforge** (/soulforge): nekromanta
  minion-fejlesztés lélekkövekből.
- **Party** (/party): meghívás, XP-megosztás, party-HP a HUD-on, personal-loot
  kizárások.
- **Céh** (/ceh): frakción belüli kisközösség — alapítás (250), tagság, kassza,
  aktivitás-szint (taglétszám-plafon nő); quest-céh-XP. (Tartalmi mélyítés backlogon.)
- **Parkour** (/parkour): pályák, idő-mérés, jutalom (limit-kérdés backlogon) —
  opcionális szabadidős tartalom, nem kötelező kaszt-út.
- **Kazamaták**: kulcs-kapus DUNGEON zónák (melyseg/csontkripta kulcs-receptek,
  CMD 6203-04), futam-passz, heti pecsét, +5 mob-szint; belső tartalom kézi építés.
- **Krónika**: heti szerver-összefoglaló broadcast + /kronika visszaolvasás.
- **AFK-rendszer**: AFK-jelölés, (kis) AFK-jutalom nappal, esemény-kizárások.
- **Moderáció**: /report rendszer (30 napos retenció), mute, spam-szűrő, chat-log.
- **Frakció-ételek (honvágy)**: hazai étel-kötelezettség puha debuff-fal, signature
  ételek buffokkal (pisztráng, rántotta, hamukenyér, ünnepi fogások).
- **Lopás-mechanika**: tetten ért lopás bűn-következménnyel, raid-fosztogatás
  szankcionált kivételével.

## 🖥 UI, HUD, kényelem

- **/menu hub**: kattintós főmenü minden rendszerhez (MENU/RUN/OPEN/CLOSE akciók),
  admin-gombok jog-kapuzva; **/profile karakterlap** (kaszt/spec/szakma/talent/
  képesség-fa almenük); 18 dedikált GUI (piac, aukció, talent, recept, config…),
  dupe-biztos holder+listener mintával.
- **HUD**: oldalsáv (frakció, kaszt+szint, erőforrás-sáv, forgó infósor, valuta,
  party-HP), harc-fókusz mód, célpont-sor; **natív tablist** (fejléc/lábléc,
  rang-rendezés, háború-színek, színkódolt ping); **lebegő sebzés-számok**;
  **halál-összegző** (utolsó 10 mp találatai); intro-címszekvencia új játékosnak;
  quest-toast értesítések; boss-bárok.
- **Parancsok**: ~60 parancs magyar tab-complete-tel és hibaüzenetekkel;
  ranglisták (/leaderboard), /lore kódex-lapozó (frakciók/helyek történetei,
  alias-okkal), /kronika, /adomany közösségi láda.
- **NPC-integráció**: /npcbind — NPC-hez köthető bolt, bank, váltó, frakció-választó,
  quest-adó, tetszőleges parancs (komp!); quest-NPC aura-markerek.

## 🔧 Admin és üzemeltetés

- **/icesmp config**: teljes élő-config felülbírálás ingame (get/set/unset/find) +
  kattintható ConfigMenuGUI; /icesmp reload restart nélkül (reload-hook a cache-elő
  managerekre).
- **/iceitem**: bármely plugin-item kiadása (unique/recept/relikvia/tervrajz/erszény)
  a valódi factory-láncon át.
- **/events**: minden világesemény kézi indítása/leállítása teszteléshez.
- **/territory**: zóna-kijelölés (kör/poligon), spawn-beállítás, főváros-kijelölés.
- **Perzisztencia**: 30+ YAML-store atomikus mentéssel (temp+rename), 10 percenkénti
  async autosave, hibatűrő load/save (egy sérült fájl nem dönt kaszkádot),
  player-adatok PDC-ben; session-takarítás (nincs UUID-szivárgás).
- **Gépi drift-ellenőrző** (scripts/check_consistency.py): YAML-épség, quest-
  hivatkozások, CMD-regiszter lefedettség, jog-node regisztráció, /menu célok,
  duplikált metódusok, tükör-repo drift — push előtt kötelező.
- **Resource pack**: teljes CMD-regiszter (docs/RESOURCE_PACK_CMD.md, 233 CMD
  sávozva) + textúra-generátor és pack-összeállító scriptek.
- **Integrációk** (mind opcionális, reflexiós híd): PlaceholderAPI (%icesmp_%
  placeholderek), LibsDisguises (druida-forma, kém, fekvés), FancyNpcs (quest-NPC-k),
  WorldGuard (spawn-guard), LuckPerms (chat-prefix, tablist-rang).
- **Folia-natív**: minden feladat régió/entitás-schedulereken, cross-entity
  scheduler-hop minta, concurrent állapot-kezelés — 50-60 fős célra méretezve.

## 📚 Dokumentáció-ökoszisztéma

16 oldalas player-guide (magyar, kezdő-barát) • PLAYTEST.md tesztelői kézikönyv •
építész-útmutató valós térképpel és MVP-checklisttel • LORE.md kódex + LORE_REFERENCE
technikai megfeleltetés • PITCH (közérthető) + TEASER (hangulati) • élő P2-audit +
konszolidált BACKLOG (319 ötlet) • tükör-repo (IceSMPGuides) gépi drift-őrzéssel.
