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
| `DARK` | **A Kitaszítottak** (lelepleződött Suttogók + bűnösök) | wither-immunitás, élőhalottak békén hagyják | **Thanaopolis** (rom) | **Csontveret** | Eleftheria / Néma Királynő |

> **DARK vizuális azonosító (textúra/art irányelv):** csont-törtfehér + éjfekete-lila alap,
> és a jellegzetes **hideg türkiz derengés** („lich-fény”) a szemekben, rúnákban, pengeélek
> mentén — a Néma Királynő élőhalott-fénye. Minden DARK-kötésű item ezt az akcentet viselje.

> **✅ K1 reskin KÉSZ:** a kánon-nevek élnek a kódban — `FactionType` rövid+hosszú alak
> (HUD/tab = rövid, /faction info + menük = hosszú), `CurrencyType` = Parázsló Parals /
> Hópihér-veret / Creutzér / Csontveret. A parsolás a régi neveket (Piros/Kék/Semleges/Sötét)
> is elfogadja.

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
| A Mélység Népe (I.) | Mételytépő relikvia (`relics.yml`); jövőbeli rom/dungeon/kazamata-horgony + ősi ereklyék forrása |
| Az elveszett emlékek (V.) | 🔜 K8 Emlékszilánkok — a gyűjthető emlék-progresszió narratív alapja |
| Ryanora (III.) | a NEUTRAL vidék/tartomány neve (a Bokic-mente); Caldestera = a fővárosa — territórium-elnevezésnél így használd |
| A fél-álomban alvó Királynő (VII.) | a vérhold + horda/invázió/világboss események narratív tétje; a K9 Suttogók célja (teljes ébresztés); végjáték-horgony — a „harmadik mondat" apokalipszisa NEM implementálandó, narratív tét marad |
| A Királynő kósza hívei (V./VII.) | N25b kultista világesemény (`cultists.*`): portya / rítus (beteljesülve rontás-gócot nyithat — H2-kapocs) / hírvivő a Kitaszítottak földje felé; a Suttogók (K9) rejtett rétegének NYÍLT, világbeli tükre |
| A gyógyuló Fa (V.) | a szezonok/progresszió/közösségi célok narratív tétje („minden rendbe tett darab gyógyítja a Fát"); jövőbeli horgony: a repedés állapota mint szerver-szintű mérce/esemény |
| Az Ünnepek (VIII.) | 🔜 D1 szezonális ünnepek — Hasadás Napja (Hu. 1: gyász/PvP-szünet?), Ultimátum Napja (Hu. 547: caldesterai vásár-esemény), Vérhold-virrasztás (vérhold-kiegészítő), Érkezés Napja (Hu. 978: szezon-évforduló) |
| Lélekkő (VII.) | DARK-valuta drop magas-szintű mobokból; DARK-játékosnak élőhalottból NEM esik (`currency.soul-drop.dark-undead-drops`); Nekromanta lélek-szilánk csak élő szörnyből (`souls.shards-from-undead`) |
| Csontszámvevő / Csontveret (VII.) | a bank/váltó mechanikailag mind a 4 valutát egyben kezeli — a megkülönböztetés lore-szintű (Thanaopolis = DARK bank-kapu; Caldesterában csak a Botera-negyed váltja) |
| Céhek Öröksége (VIII.) | 8 szakma, szakma-szintek, recept-katalógus, tervrajz-tanulás, craft-kapuk |
| Tárgyak Lelke (VIII.) | tárgy-raritás létra (Ócska→Ereklye), affixek, nevesített craft |
| Vér Emlékezete (VIII.) | talentpontok + talentfák (kaszt- és szakma-szintből) |
| Hű Társak (VIII.) | Vadmester befogott állat-társa / Nekromanta élőholt companionja, pet-szintezés |
| Égi Jelek (VIII.) | világesemények (vérhold, Bőség-idő, meteor/hulló csillag, északi fény, köd/szellemek, Vad Hajsza, karaván, kincs/gyűjtő-lázak, invázió/világboss, kollektív szerver-kihívás) — mind config-vezérelt; a spawn-helyeket esemény×védelem mátrix szabályozza (world-events.spawn-rules: territórium/claim/WG-régió/víz), az esemény-mobok nem zombisodnak és nappal sem égnek |
| Fa Alatt Testvérek (VIII.) | party-rendszer: frakció-független, osztott XP, personal loot, párton belüli PvP-tiltás |
| Arany Áramlása (VIII.) | bank/piac/aukció, dinamikus árfolyam, adók/illetékek mint pénz-nyelő, „nincs addolt pénz" elv; caldesterai ládák = crate-rendszer (kulcs-ár = valuta-nyelő); **adó = 2% + fejadó**, a fedezetlen rész hátralék, a tartós nem-fizetést a Számvevők bűnként jelentik fel (bűn-küszöb → száműzetés a Kitaszítottak közé) |
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
- **✅ K7 — Kárhozat Kapuja (KÉSZ):** `DOOM_GATE` territórium-típus — legális PvP (belépő-grace,
  a támadó elveszti), ölés nem bűn, +3 mob-szint (jobb loot-tier), aréna-védelem (build/robbanás/
  tűz tiltva), belépő-hangulat; minden kulcs élőben olvasódik (`territory.doom-gate.*`,
  `territory.protection.rules.doom-gate`).
- **✅ K8 — Emlékszilánkok (KÉSZ):** Opálos Emlékszilánk (mob/boss-loot) + `/emlek` beváltó —
  kaszt-XP / +1 talentpont / spec szint-kapu feloldás / emlék-töredék. Class-váltás NINCS
  (a kaszt állandó — kánon); költségek: `memory-shards.*`.
- **✅ K9 — Suttogók (KÉSZ):** rejtett státusz a látható frakció fölött — Sötét Rítus
  (Suttogás-meghívó, éjjel/sculk/egyedül/vér-ár), /suttogas titkos csatorna + tanú-vád,
  gyanú→leleplezés→bűn→száműzetés a meglévő pipeline-on (`factions.whisper.*`).
- **✅ K10 — Caldestera feketepiac (KÉSZ):** fegyvertilalom + körözött-kapu a NEUTRAL
  fővárosban (CapitalLawListener); Botera-negyed feketepiac-bolt Csontveretért — Bokic-menti
  Sétapálca (rejtett penge) és Hamisított Menlevél (`territory.capital-law.*`).
- **🔜 Thanaopolis megépítése:** a DARK rom-főváros + spawn (világépítés, szerver-csapat); egyben
  magas-veszélyű PvE rom-zóna, K5-loot forrás és a K9 Suttogó-oltár helye.

---

## Unique-item tervtábla (technikai réteg)

A tárgyak **kanonikus lore-szövege a kódexben él** ([LORE.md → A Legendás Tárgyak Lajstroma](LORE.md))
— itt csak a tervezett mechanika (nem végleges balansz) és az implementációs jegyzet áll.

| Tárgy | Frakció | Típus/forrás | Tervezett perk / jegyzet |
|---|---|---|---|
| Kallan Szeletelője | BLUE | íj (K2) | ✅ implementálva: +50% nyíl-sebesség, +15% bónusz-sebzés (`signature.kallan.*`) |
| Glatziendorfi Jégvért | BLUE | mellvért (K2) | ✅ implementálva: viselve −20% sebzés (`signature.jegvert.damage-mult`) |
| Jégsárkány-Kantár | BLUE | hátas-felszerelés (K2) | ✅ implementálva: hátas +sebesség, egyszeri, elfogy (`signature.kantar.speed-add`) |
| Glatziendorfi Jégtörő | BLUE | csatabárd (2. hullám) | ✅ implementálva: lassított célon +25% sebzés (`signature.jegtoro.slowed-bonus`) |
| V. Miinus Haragja | BLUE | kard (2. hullám, loot-only tervrajz) | ✅ implementálva: a viselő alacsony HP-ján +20% sebzés (`signature.miinus.*`) |
| Sárkánycsont Íj | BLUE | íj (2. hullám) | ✅ implementálva: a nyíl pierce-szintje +2 (`signature.sarkanycsont.pierce-add`) |
| Fagyasztott Tavi Pisztráng / Sárkány-pörkölt | BLUE | étel (K6 + 2. hullám) | ✅ mindkettő implementálva: Pisztráng = felszívódás; Pörkölt = rövid Erő; mindkettő hal-kötelezettséget teljesít |
| Pyralingradi Tűzköpő / Ostrom-számszeríj | RED | számszeríj (K3) | ✅ Tűzköpő implementálva: Quick Charge II + lövedék ×1.5 (`signature.tuzkopo.*`) |
| A Vérszavanna Agyara | RED | kard/lándzsa (K3) | ✅ implementálva: +15% sebzés, off-hand baltával +30% (`signature.agyar.*`) |
| Főnix-Tollköpeny | RED | kiegészítő (K3) | ✅ implementálva: viselve tűz/láva/forró-blokk immunitás (`signature.tollkopeny.fire-immunity`) |
| I. Zhoris Lángnyelve | RED | kard (2. hullám, loot-only tervrajz) | ✅ implementálva: gyújtás-esély + égő célon +15% (`signature.langnyelv.*`) |
| Napfogyatkozás | RED | íj (2. hullám) | ✅ implementálva: éjjel gyorsabb lövedék + +25% sebzés (`signature.napfogyatkozas.*`) |
| Fűszeres Főnixtojás-Rántotta | RED | étel (K6) | ✅ implementálva: séf-recept + tűz-ellenállás; tojás-kötelezettség él (`factions.food-duty`) |
| Vasművek Akadémiájának Csákánya | NEUTRAL | szerszám (K4) | ✅ implementálva: érc-töréskor +20% extra drop, bányász-láz alatt szünetel (`signature.csakany.*`) |
| Bokic-menti Horgászbot | NEUTRAL | szerszám (K4) | ✅ implementálva: +20% dupla fogás, halászati láz alatt szünetel (`signature.horgaszbot.*`) |
| Asterlayna Gyümölcse (Tiltott Kakaóbabos Sütemény) | NEUTRAL | süti (K6) | ✅ implementálva: „robbanó csemege" — feldobás + Speed II + effekt |
| Mortengradi Hamukenyér | DARK | étel (K6) | ✅ implementálva: éjjellátás-buff; a DARK-nak NINCS honvágy-kötelezettsége (nincs otthonuk) |
| Smaragdkő Bankbetét | NEUTRAL | értékpapír (K4) | ✅ implementálva: jobb-katt atomi beváltás Creutzérre (`signature.bankbetet.value`) |
| Szellemszarvas-Bűbáj | NEUTRAL | hátas-hívó (K4) | ✅ implementálva: cooldownos ideiglenes gyors hátas (`signature.szarvas.*`) |
| Hetedik Vérháború Rozsdás Pengéje | DARK/közös | mob-drop (K5) | ✅ implementálva: undead-only nevesített drop (loot.yml `named` sor), rarity-affixekkel |
| Fekete Villám Szilánk | DARK/közös | crafting-alapanyag (K5) | ✅ implementálva: `profession-materials.osi_ereklyeszilank` display; magas-tier receptek hozzávalója |
| Jégvirág-por / Parázsmag / Viharkvarc / Mélységi Borostyán | vegyes | köztes anyagok (2. hullám) | ✅ profession-materials (CMD 6005-6008); borostyán régészeti lelet is |
| Sárkánycsont-szilánk / Főnixpihe | BLUE/RED | mob/boss-drop anyagok (2. hullám) | ✅ loot.yml sorok (szilánk boss-only); a 2. hullám fegyver-receptjeinek hozzávalói |
| Eleftheria Könnye | DARK/közös | relikvia (K5) | ✅ implementálva: egy-példányos relikvia + DARK-kapus rituálé-oltár (relics.yml) |
| Megrontott Elit Páncél / Fekete Csont / A Néma Királynő Suttogása | DARK/közös | mob-drop (K5 + 2. hullám) | ✅ mindhárom implementálva: undead-only named drop sorok (a Suttogása boss-only, nagyon ritka) |
| Csontveret | DARK | valuta | a DARK valuta display-neve (K1 reskin) |

> **A Mélység Népe (törpök) — ✅ kanonizálva** (kódex I. fejezet): Asterlayna testének mélybe süllyedt
> szilánkjai körül ébredt nép; a világ első kovácsai (Mételytépő); eltűnésük az Első Csendhez kötött
> misztérium („túl mélyre ástunk, és a csend visszanézett"). Kész horgony jövőbeli rom/dungeon/
> kazamata-tartalomhoz és ősi ereklyékhez.

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

## Bootstrap-enchantok és mágia-iskolák (audit: lore-horgony + funkció)

Szabály: enchantot CSAK lore-horgonnyal és valódi funkcióval használunk — puszta dísz-enchant
nincs. A damage-type iskolák (icesmp:tuz/fagy/szent/arnyek/termeszet/vihar/kaosz/magia) a
kasztok mágia-eredetéhez kötődnek (kódex IV. — a kasztok mágiája); a besorolás configból
hangolható (spells.spell-schools).

| Enchant | Kánon-horgony | Funkció |
|---|---|---|
| Jégfog | Kallan jégsárkányainak harapása (kódex II.) | a Szeletelő nyila rövid lassítást harap |
| Vihartűz | a Vérszavanna vihara + Pyralingrad kohói | a Tűzköpő lövedéke meggyújtja a célt |
| Vérszomj | a Vérszavanna vére (kódex III.) | az Agyar sebzésének kis része gyógyít (életszívás) |
| Fagypáncél | Glatziendorf jégvértje | fagymágia-ellenállás (iskola-counter) |
| Főnixtoll | Soleil főnixei (kódex II.) | tűzmágia-ellenállás (iskola-counter) |
| Érc-érzék | a Vasművek Akadémiájának tanítása | bónusz tapasztalat érc-törésnél |
| Bokic Kegye | a Bokic folyó áldása (kódex III.) | fogáskor rövid Szerencse (jobb zsákmány) |
| Rúnavért | Caldestera rúnaírnokai + a Mélység Népe rúna-öröksége (kódex I.) | generikus mágia-ellenállás (minden iskola) |

Megszerzés: kizárólag signature-craft / tekercs-recept (a saját enchantok nincsenek az
enchant-asztal tagben); rider-számok: `signature.enchant-riders.*` (crafting.yml).

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

## Ó-Caldestera és az új Caldestera (építész-kánon, 2026-07)

| Lore-elem | Mechanika |
|---|---|
| **Ó-Caldestera** (a Fa tövében, spawn) | first-join spawn + onboarding (`world-events.intro.first-join-spawn`); zóna: `protected-city`; kezdő-barát Lvl 0-1 vadon |
| **Új Caldestera** (ÉK, ~+5000/+2000) | a NEUTRAL `capital` zóna: bank/váltó-kapu, frakcióváltás, fővárosi törvény, emlékmű |
| **Komp a szoroson át** | `/komp` + `ferry.routes.*` (economy.yml); révész-NPC: `/npcbind <npc> command "komp <út>"`; viteldíj = money sink |
| **Vérfa (Pyralingrad nemesi negyede)** | építő-anyag kánon: mangrove = nemesség, akácia = köznép, kalcit-diorit-nyír falak |
| **„Egyetlen kapu": nether-portál tilalom** | `nether-portal.allow-creation: false` (world.yml) — új portál sehol; a Kárhozat Kapuját admin gyújtja (zóna-bypass jog); Nether-út = a PvP-senkiföldjén át |

## Névváltás: Thanaopolis (tulaj-döntés, 2026-07-21)

| Név | Mi |
|---|---|
| **Thanaopolis** | a DARK főváros MAI neve (a Holtak Városa) — minden jelen-idejű szöveg/zóna ezt használja; zóna-id: `thanaopolis` |
| **Mortengrad** | a bukás ELŐTTI (történelmi) név — csak a kódex múlt-idejű szövegében és a régi receptek nevében él (Mortengradi Hamukenyér, Mortengradi Árnygomba — az item-id-k és signature-ök változatlanok!) |
