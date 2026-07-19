# LORE_REFERENCE — a kódex fejlesztői kísérőfüzete

> A kánon maga a [LORE.md](LORE.md) — az tiszta, világon-belüli szöveg. Ez a fájl a **technikai
> megfeleltetés**: mely lore-elem mely szerver-rendszerre képződik le, mi él már és mi tervezett,
> plusz az elnevezési irányelvek és a unique-item tervkatalógus. Minden új tartalom a kódexhez
> illeszkedjen — a kód-kötéseket pedig itt tartsd naprakészen.

---

## Frakció ↔ kód megfeleltetés

A kód generikus `RED/BLUE/NEUTRAL/DARK` azonosítókat használ; a lore ezekre képződik le. A **passzívok
már a lore szerint működnek** — csak a nevek/valuták placeholderek.

| Kód | Frakció | Passzív (kész) | Főváros | Valuta | Örökség |
|---|---|---|---|---|---|
| `RED` | **Perinfernicitas** | tűz/láva-immunitás | Pyralingrad | **Parázsló Parals** | Soleil / Főnix |
| `BLUE` | **Cryghaliris** | fagy + fulladás-immunitás | Glatziendorf | **Hópihér-veret** | Kallan / Sárkány |
| `NEUTRAL` | **Ryanora & Caldestera** | zuhanás-immunitás, békés mobok, adómentes | Caldestera | **Creutzér / Smaragdkő** | Arkynn / Szarvas |
| `DARK` | **A Kitaszítottak** (lelepleződött Suttogók + bűnösök) | wither-immunitás, élőhalottak békén hagyják | **Mortengrad** (rom) | **Csontveret** | Eleftheria / Néma Királynő |

> A jelenlegi játékos-nevek (`factions.yml`: „Piros/Kék/Semleges/Sötét", `economy.yml`: „… Token")
> **placeholderek** — a K1 reskin cseréli őket a kánon-nevekre (rövid + hosszú alak kell a HUD/tab
> helyszűke miatt).

---

## Lore-elem → mechanika (a kódex fejezetei szerint)

| Kódex-elem | Mechanika / kód-kötés |
|---|---|
| A Felsők halhatatlansága (V.) | respawn a frakció-spawnon (`/territory setspawn`) |
| Szent Zóna (V.) | a Semleges kezdő-spawn a Fa alatt; védett, PvP-tiltott territórium-zóna; a frakcióválasztó hírnök Caldesterában — az út a kettő közt a zarándoklat |
| A Fa fényköre (V.) | táv-alapú mob-skálázás: 1000 blokk = +1 mob-szint (max 10), több XP + lélekkő-esély; spawner-mobok nem skálázódnak |
| Király-választás (IV.) | frakciónkénti szavazás (`KingManager`); király = raid-indítás + kassza |
| Királyok Átka / Felsők (IV.) | a király/raid/szezon/claim rendszerek lore-indoklása |
| Kasztok forrásai (VI.) | 13 kaszt (`JobType`), a kaszt végleges; 31 specializáció = a források „iskolái" |
| Belső erők (VI.) | kasztonkénti erőforrás-sávok (Mana/Düh/Fókusz/Energia/Csi/Lélekerő…); hibrid költségek: HP = vérmágia, XP = rituálé, éhség = nehéz testi spell |
| Lélekkapocs (VI.) | a kaszt-témás Képesség Katalizátor item (`CatalystItemFactory`); jobb katt = cast, sneak+ütés = váltás; **nem dobható el** (`CatalystProtectionListener`: drop-cancel + halálkor megőrzés/respawn-visszaadás), craft/kohó-védett (`CatalystCraftSafetyListener`); elvesztéskor pótolható (Job GUI / `/job givecatalyst`) |
| Az Első Csend (I./VII.) | szándékosan megfejtetlen misztérium — jövőbeli antagonista/quest-horgony; NE magyarázd meg tartalomban |
| Lélekkő (VII.) | DARK-valuta drop magas-szintű mobokból; DARK-játékosnak élőhalottból NEM esik (`currency.soul-drop.dark-undead-drops`); Nekromanta lélek-szilánk csak élő szörnyből (`souls.shards-from-undead`) |
| Csontszámvevő / Csontveret (VII.) | a bank/váltó mechanikailag mind a 4 valutát egyben kezeli — a megkülönböztetés lore-szintű (Mortengrad = DARK bank-kapu; Caldesterában csak a Botera-negyed váltja) |
| Céhek Öröksége (VIII.) | 8 szakma, szakma-szintek, recept-katalógus, tervrajz-tanulás, craft-kapuk |
| Tárgyak Lelke (VIII.) | tárgy-raritás létra (Ócska→Ereklye), affixek, nevesített craft |
| Vér Emlékezete (VIII.) | talentpontok + talentfák (kaszt- és szakma-szintből) |
| Hű Társak (VIII.) | Vadmester befogott állat-társa / Nekromanta élőholt companionja, pet-szintezés |
| Égi Jelek (VIII.) | világesemények (vérhold, Bőség-idő, meteor/hulló csillag, északi fény, köd/szellemek, Vad Hajsza, karaván, kincs/gyűjtő-lázak, invázió/világboss, kollektív szerver-kihívás) — mind config-vezérelt |
| Fa Alatt Testvérek (VIII.) | party-rendszer: frakció-független, osztott XP, personal loot, párton belüli PvP-tiltás |
| Arany Áramlása (VIII.) | bank/piac/aukció, dinamikus árfolyam, adók/illetékek mint pénz-nyelő, „nincs addolt pénz" elv; caldesterai ládák = crate-rendszer (kulcs-ár = valuta-nyelő) |
| Korszakok Könyve (VIII.) | szezonliga: frakció-pontverseny, szezon-végi jutalom |
| Oltárok és Ereklyék (VIII.) | egy-példányos relikviák, PvP-átvétel, inaktivitás-elenyészés; multi-block rituálé-oltárok |
| Hírnökök és Krónikák (VIII.) | quest-rendszer, NPC quest-adók, napi rotáció, frakció-közösségi célok, achievementek/ranglisták |

> **Nem-kánon mechanika:** a kaszt-mester NPC-láncok + parkour-próbapályák a kódban léteznek
> (TALK_TO_NPC + PARKOUR_TRIAL), de a „mesterek" narratíva **egyelőre kikerült a kódexből** — a
> lore-ban a próba-tartalom horgonyozatlan, ne hivatkozz rá kánonként, míg vissza nem kerül.

---

## Tervezett világ-rendszerek (✅ = él • 🔜 = tervezett)

- **✅ Már a lore szerint él:** frakció-passzívák; territórium/claim (fővárosok, védett zónák);
  bűn/vérdíj + `DARK`-száműzetés; valuta-slotok; király/raid/szezon; lélekkő-szabály; Lélekkapocs-védelem.
- **🔜 K7 — Kárhozat Kapuja:** Nether-portál PvPvE-zóna a Senkiföldjén (legális PvP, fokozott
  mob-veszély, exkluzív loot: netherite, boss-drop, Fekete Villám Szilánk).
- **🔜 K8 — Emlékszilánkok:** gyűjthető emlék-progresszió. Jutalom a meglévő rendszerekhez illesztve —
  **NEM kaszt-váltás** (a kaszt állandó): XP/szint, talentpont, specializáció-feloldás, frakció-rang,
  kozmetika, lore-feltárás. Az Opálos Emlékszilánk az Arany Liga / Szabad Városok korának emlékeit őrzi.
- **🔜 K9 — Suttogók (hibrid):** Suttogó = rejtett STÁTUSZ a látható frakció fölött; lelepleződés
  (közterületi sötét mágia, rajtakapott rítus/árulás, tanú-vád) → átváltás a `DARK` Kitaszítottak közé
  (bűn + vérdíj). Részletes terv: [ideas/K-lore.md → K9](ideas/K-lore.md).
- **🔜 K10 — Caldestera feketepiac:** rejtett fegyverek (Bokic-menti Sétapálca, Hamisított Menlevél);
  előfeltétel a fegyver-elkobzó zóna a semleges fővárosban.
- **🔜 Mortengrad megépítése:** a DARK rom-főváros + spawn (világépítés, szerver-csapat); egyben
  magas-veszélyű PvE rom-zóna, K5-loot forrás és a K9 Suttogó-oltár helye.

---

## Unique-item tervtábla (technikai réteg)

A tárgyak **kanonikus lore-szövege a kódexben él** ([LORE.md → A Legendás Tárgyak Lajstroma](LORE.md))
— itt csak a tervezett mechanika (nem végleges balansz) és az implementációs jegyzet áll.

| Tárgy | Frakció | Típus/forrás | Tervezett perk / jegyzet |
|---|---|---|---|
| Kallan Szeletelője | BLUE | íj (K2) | +50% nyíl-sebesség, 15% páncéltörés |
| Glatziendorfi Jégvért | BLUE | mellvért (K2) | Resistance I/II szett-perk |
| Jégsárkány-Kantár | BLUE | hátas-felszerelés (K2) | — |
| Glatziendorfi Jégtörő / V. Miinus Haragja / Sárkánycsont Íj | BLUE | fegyverek | később tervezendő |
| Fagyasztott Tavi Pisztráng / Sárkány-pörkölt | BLUE | étel (K6) | BLUE hal-fogyasztási kötelezettség |
| Pyralingradi Tűzköpő / Ostrom-számszeríj | RED | számszeríj (K3) | +50% felhúzási/repülési sebesség |
| A Vérszavanna Agyara | RED | kard/lándzsa (K3) | fegyver-kombó Strength I/II |
| Főnix-Tollköpeny | RED | kiegészítő (K3) | tűz/láva-ellenállás |
| I. Zhoris Lángnyelve / Napfogyatkozás | RED | fegyverek | később tervezendő |
| Fűszeres Főnixtojás-Rántotta | RED | étel (K6) | RED tojás-fogyasztási kötelezettség |
| Vasművek Akadémiájának Csákánya | NEUTRAL | szerszám (K4) | +20% bányász-loot |
| Bokic-menti Horgászbot | NEUTRAL | szerszám (K4) | +20% horgász-loot |
| Asterlayna Gyümölcse | NEUTRAL | süti (K6) | robbanó csemege perk |
| Smaragdkő Bankbetét | NEUTRAL | értékpapír (K4) | bank-item, atomi beváltás |
| Szellemszarvas-Bűbáj | NEUTRAL | hátas-hívó (K4) | pet/hátas-rendszer |
| Hetedik Vérháború Rozsdás Pengéje | DARK/közös | mob-drop (K5) | fegyver-drop |
| Fekete Villám Szilánk | DARK/közös | crafting-alapanyag (K5) | ✅ implementálva: `profession-materials.osi_ereklyeszilank` display; magas-tier receptek hozzávalója |
| Eleftheria Könnye | DARK/közös | relikvia (K5) | `RelicManager`, egy-példányos |
| Megrontott Elit Páncél / Fekete Csont / A Néma Királynő suttogása | DARK/közös | mob-drop (K5) | drop-tábla tétel |
| Csontveret | DARK | valuta | a DARK valuta display-neve (K1 reskin) |

> **Törpék** *(kiegészítés — nem az eredeti kódexből, a kódexben sem szerepel):* a Mételytépő-relikvia
> lore-ja egy rég letűnt törp civilizációra hivatkozik — a Fa gyermekeinek koránál is régebbi nyom.
> Megtartva relikvia-szintű háttérként; kánon-emelése a tulaj döntése.

---

## A Lélekkapocs formái (kaszt → item)

| Kaszt | Lélekkapocs | Material |
|---|---|---|
| Varázsló | Caldesterai Rúnakódex | ENCHANTED_BOOK |
| Harcos | Sárkánykirály Kürtje | GOAT_HORN |
| Íjász | Soleil Vadásztarsolya | RABBIT_HIDE |
| Orgyilkos | Homály-szilánk | FLINT |
| Druida | Aetrinita Sarja | OAK_SAPLING |
| Paplovag | Hajnaltűz Harangja | BELL |
| Halállovag | Néma Rúnakoponya | WITHER_SKELETON_SKULL |
| Sámán | Ősvihar Totemje | TOTEM_OF_UNDYING |
| Szerzetes | Élet Ága | BAMBOO |
| Pap | Asterlayna Gyertyája | WHITE_CANDLE |
| Boszorkánymester | Kárhozat Lámpása | SOUL_LANTERN |
| Démonvadász | Hasadék Szeme | ENDER_EYE |
| Sárkányidéző | Sárkányvér-fiola | DRAGON_BREATH |

---

## Elnevezési irányelvek

- **Frakció-item** = a frakció hőse/fővárosa/motívuma a névben (Kallan/Glatziendorf/jég; Soleil/
  Pyralingrad/láng/főnix; Arkynn/Caldestera/Bokic/szarvas).
- **Történelmi horgony**: az uralkodók (I. Zhoris, V. Miinus, I. Benedictus, I. Lineata), a letűnt
  hatalmak (Arany Liga, Szabad Városok Szövetsége) és az évszámok (Hu. 698 = Hetedik Vérháború)
  hitelesítik a legendás tárgyakat.
- **Káoszkor-drop** = Eleftheria/Néma Királynő/suttogás/megrontott/fekete motívumok; a legmélyebb
  misztérium-réteg = **az Első Csend** (ne magyarázd meg).
- **Kaszt-fantázia** = a kaszt ereje a forrásához illeszkedjen (kódex VI.): szent = Asterlayna/Soleil,
  természet = Aetrinita gyökerei/Kallan vihara, káosz = Kárhozat Kapuja, halál/árnyék = Eleftheria
  mérge / az Első Csend homálya.
- **Valuta**: RED = Parázsló Parals, BLUE = Hópihér-veret, NEUTRAL = Creutzér/Smaragdkő,
  DARK = Csontveret.
