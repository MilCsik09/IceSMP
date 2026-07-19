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

## Unique-item tervkatalógus (lore + tervezett perk)

A jövőbeli egyedi tárgyak forrása; a zárójeles perk tervezett mechanika (nem végleges balansz).

### ❄️ Cryghaliris
- **Kallan Szeletelője** (Íj/Hosszúíj) — „Kallan első pikkelyeiből és ősi fenyőből faragva; a
  jégsárkányok inaiból font húr a legvastagabb vértet is átvágja." *(+50% nyíl-sebesség, 15% páncéltörés)*
- **Glatziendorfi Jégvért** (Nehéz mellvért) — „Oly nehéz és fagyos, hogy viselőjét sziklaként
  rögzíti; teljes szettben az ellenség fegyverei tompán koppannak." *(Resistance I/II szett-perk)*
- **Fagyasztott Tavi Pisztráng** (Hal/fogyóeszköz) — „A Fagyott Királyság mindennapi eledele;
  nélküle a Jégmezők aurája a legkeményebb harcos vérét is megmérgezi." *(BLUE hal-fogyasztási kötelezettség)*
- **Jégsárkány-Kantár** (Hátas-felszerelés) — „Kemény bőr és sötét mágia; csak ezzel tartható kordában egy vad sárkány."
- **Glatziendorfi Jégtörő** (Balta) — „A Jégmezők sárkányainak pikkelyeivel megerősített fegyver."
- **V. Miinus Haragja** (Fegyver) — a Sárkánykirály-örökös uralkodó nevét viselő, kíméletlen penge.
- **Sárkánycsont Íj** (Íj) — a jégmezők megszelídített sárkányainak csontjából faragva.
- **Sárkány-pörkölt** (Étel) — „A fagyhalál elleni egyetlen menedék."

### 🔥 Perinfernicitas
- **Pyralingradi Tűzköpő** (Számszeríj) — „Soleil papjai áldották meg; lövedékei a sivatagi vihar
  sebességével csapnak le." *(+50% felhúzási/repülési sebesség)*
- **A Vérszavanna Agyara** (Kard/Lándzsa) — „Kovácsolásakor a lángok fekete füstöt okádtak; baltával
  kiegészítve emberfeletti erő." *(fegyver-kombó Strength I/II perk)*
- **Fűszeres Főnixtojás-Rántotta** (Tojásétel) — „Hamuban sült étel; tüze távol tartja a fagyos
  vidék bénító gyengeségét." *(RED tojás-fogyasztási kötelezettség)*
- **Főnix-Tollköpeny** (Kiegészítő) — „I. Zhoris lángmadarainak tollaiból szőve; óv a hőségtől és a
  láva haragjától." *(tűz/láva-ellenállás)*
- **I. Zhoris Lángnyelve** (Kard) — „Ezt a pengét a Hetedik Vérháború napján kovácsolták a Vérszavanna legmélyén."
- **Napfogyatkozás** (Kard) — sötét lángpenge, a Lángoló Birodalom legendás fegyvere.
- **Pyralingradi Ostrom-számszeríj** (Számszeríj) — Soleil papjai áldották meg a Vérszavanna szívében.

### ⚖️ Ryanora & Caldestera
- **A Vasművek Akadémiájának Csákánya** (Szerszám) — „Caldestera mestereinek műve, Asterlayna
  áldásával; a sziklák mélyén gyakran váratlan kincs." *(+20% bányász-loot)*
- **Bokic-menti Horgászbot** (Szerszám) — „A Bokic hordalékából; zsinórja a víz szellemeit is
  felszínre vonzza." *(+20% horgász-loot)*
- **Tiltott Kakaóbabos Sütemény** / **Asterlayna Gyümölcse** (Csokis süti) — „Aetrinita átka; fogyasztása
  tilos — hacsak nem akarsz darabokban távozni a Városból." *(robbanó süti perk)*
- **Smaragdkő Bankbetét** (Értékpapír) — „Caldestera Bankárszövetségének hamisíthatatlan pecsétjével;
  többet ér ezer kardnál."
- **Szellemszarvas-Bűbáj** (Hátas-hívó) — „Tölgyfa talizmán; megfújva a szellemléptű szarvasok segítenek."

### 💀 A Káoszkor lényei / Néma Királynő
- **A Hetedik Vérháború Rozsdás Pengéje** (Mob-drop/fegyver) — „Egykor I. Benedictus vagy I. Lineata
  seregéé; ma csak Eleftheria néma haragja hajtja az élőhalottak kezében."
- **Fekete Villám Szilánk** (Ritka crafting-alapanyag) — „Az a sötét energia pulzál benne, amely
  Hu. 698-ban kettéhasította az eget és felébresztette a Holtak Úrnőjét." *(implementálva:
  `profession-materials.osi_ereklyeszilank` display)*
- **Eleftheria Könnye** (Misztikus relikvia) — „Megkövült, éjfekete csepp; a Néma Királynő első
  suttogása hozta létre, magába zárva a Fa kínjait és a magányt."
- **Megrontott Elit Páncél** (Mob-drop/vért) — „Az eltűnt nemesek dicsőségének maradványa; az acélt
  átrágta az idő, de a sötét mágia védi oszladozó viselőjét."
- **A Néma Királynő suttogása** (Mob-drop) — „Eleftheria első szavai hívták életre a Hu. 698. évben."
- **Megrontott Fekete Csont** (Mob-drop) — az élőhalottak maradványa; ugyanaz a néma harag hajtja.
- **Csontveret** (Érme) — „A Holtak Városának pénze; a Csontszámvevő vereti sírba szállt urak
  koronáiból és megfeketült ezüstből — érintése hideg, akár a kripta." *(DARK valuta)*

> **Törpék** *(kiegészítés — nem az eredeti kódexből):* az ősi romok és a legősibb ereklyék (pl. a
> *Mételytépő* harci fejsze) egy rég letűnt, rejtélyes **törp** civilizáció hagyatéka — a Fa
> gyermekeinek koránál is régebbi nyom a világban. *(A Mételytépő-relikvia lore-horgonyzása;
> törölhető, ha nem kell.)*

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
