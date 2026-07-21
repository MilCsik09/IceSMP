# 🏗 Építész-útmutató — mit kell megépíteni az IceSMP világába

> Ez a doksi a szerver-építőknek szól: **mit**, **hova**, **kb. mekkorára** és **milyen
> stílusban** kell megépíteni ahhoz, hogy a plugin összes rendszere élni tudjon.
> A lore-hűség forrása a `docs/LORE.md` (kódex); a technikai kötések (parancsok,
> config-kulcsok) minden szakasz végén ott vannak. A koordináták kitöltése az
> admin-csapat dolga — az építmény elkészülte UTÁN kell a zónákat/pontokat kijelölni.

---

## 0. A világ elrendezése (nagy térkép)

A világ veszélyessége a spawntól **kifelé nő**: minden 1000 blokk = +1 mob-szint (max 10).
Erre épül a földrajz — a béke középen van, a veszély a széleken:

```
                 ❄ JÉGMEZŐK (észak)
              Glatziendorf — BLUE főváros
                       │
                 ~3000-4000 blokk
                       │
     ☠ SENKIFÖLDJE — Kárhozat Kapuja (a RED-BLUE tengely felénél)
                       │
   🌳 KÖZÉP: a Fa + Bokic-folyó — Ryanora vidéke, CALDESTERA (spawn, NEUTRAL)
                       │
                 ~3000-4000 blokk
                       │
              Pyralingrad — RED főváros
                 🔥 VÉRSZAVANNA (dél)

   ⚫ MORTENGRAD (DARK romváros): oldalirányban, ~2000-3000 blokkra a spawntól,
      sötét/mocsaras-sculkos vidéken — szándékosan félreeső.
```

Ökölszabályok:
- **Caldestera (spawn)** a világ közepén, 0. mob-szintű békés zónában.
- **Pyralingrad és Glatziendorf** egymással ÁTELLENBEN (pl. dél/észak), a spawntól
  ~3000-4000 blokkra (3-4. mob-szint) — a lore szerint a Fa a két harcos népet a világ
  két ellentétes sarkába űzte. Biomot igazítsd: szavanna/badlands ↔ havas síkság/hegy.
- **A Kárhozat Kapuja** a két harcos főváros KÖZÖTTI senkiföldjén, kb. félúton —
  bármelyik frakciótól ~1500-2000 blokk.
- **Mortengrad** a spawntól 2000-3000 blokkra, a fő tengelytől ELTOLVA (pl. kelet/nyugat),
  sötét erdő / mocsár / mély sculkos völgy peremén.

---

## 1. Caldestera — a Semleges Főváros (a legfontosabb építés!)

**Lore:** a világ kereskedelmi és tudományos központja a Bokic-folyó partján, a Fa
árnyékában. **Tölgy, tégla és kvarc** gazdag polgári városa; szigorúan **fegyvermentes**
(a plugin be is tartatja: fővárosi fegyvertilalom + körözött-kitiltás). Az utcákon
szarvasok, a falak közt Vámház, a sikátorokban a Botera-negyed feketepiaca.

**Méret:** kb. **200×200 blokk** városmag + falak (a falakon KÍVÜL hagyj 20-30 blokk
szabad sávot — a szezonzáró boss a fal MELLÉ spawnol). Ez a legnagyobb építés, ide fut
be minden új játékos.

**Kötelező elemek (mindnek van plugin-funkciója):**

| Épület/hely | Mit szolgál | Technikai kötés |
|---|---|---|
| **Főtér a Hírnökkel** | onboarding-lánc + útmutatás-quest + frakcióválasztás | NPC: `/npc create hirnok` + `/npcbind hirnok quest ...` |
| **Frakció-követségek / választó-terem** | itt lehet frakciót VÁLTANI (a váltás csak a semleges fővárosban működik!) | 3-4 frakció-NPC: `/npcbind <npc> faction <red/blue/dark>` |
| **Bank / Bankárszövetség székház** | bank + valutaváltás CSAK fővárosban használható | banker-NPC: `/npcbind <npc> bank`, váltó: `/npcbind <npc> exchange` |
| **Árfolyam-csarnok** | élő árfolyam-táblák | `/exchangeboard place` a falakra |
| **Piac- / aukciós csarnok** | flavor a /market, /auction köré | nem kötelező kötés, de ide való |
| **Vámház + Botera-negyed (sikátorok)** | lore: feketepiac, csempészek; a fegyvertilalom „színpada" | kapukhoz őr-NPC dekoráció |
| **A Korszakok Könyve emlékmű** | szezonzáráskor ide kerül a bajnok bannerje + hologram-krónika | talapzat + `season-monument.location` |
| **Mester-céhházak / kiképzőterek** | mind a 13 kaszt mentor-questje | 13 mester-NPC (lásd a 10. szakasz NPC-táblázatát) |
| **Parkour-pálya (OPCIONÁLIS)** | szabadidős kihívás (a kaszt-fejlődés NEM függ tőle) | `/parkour setstart <pálya> ...` — pl. az akrobata-kihíváshoz |
| **Kovács-negyed** | `kovacs_mester` NPC (beszállító-questek) | `/npc create kovacs_mester` |
| **Karaván-megálló / fogadó-udvar** | a vándorkereskedő karaván fix megállója | `caravan.stops` koordináta |
| **Őrjárat-útvonalak** | járőröző városi őrség (éjjel gyorsabb) | `city-guards.guards.<id>.route` waypointok |
| **Láda-terem / jutalomházak** | crate-ládák (kulcsos nyitás) | `/crate set <láda-id>` a fizikai ládára |
| **Kaszt-szentélyek csarnoka(i)** | 13 kaszt-oltár (buff-rituálék) — lehet egy Szentély-negyed | lásd 6. szakasz (multiblock) |
| **Hazatérés-kő** | `hazateres` rituálé (fővárosba teleport) — a TÖBBI városba is kell egy | LODESTONE mag-oltár |

**Parkour (opcionális):** ha építetek pályát, 30-60 blokk hosszú, 1-2 perc alatt
teljesíthető legyen — de egyetlen kaszt-lánc sem függ tőle, tehát ráér bármikor.

**Falak:** a városmag köré. A szezonzáró boss a NEUTRAL főváros zóna-pereme MELLÉ
spawnol (`wall-offset` blokkal kijjebb) — hagyd szabadon a fal külső előterét, hogy
legyen hol megvívni.

**Zónázás (az építés után, admin):**
- városmag: `/territory create capital neutral caldestera Caldestera`
- külső védett műemlékek: `protected-city` típus
- Ryanora vidéke (tanyák, gázlók a Bokic mentén): `faction NEUTRAL` zóna — ITT
  claimelhetnek a játékosok
- spawn-pont: `/territory setspawn neutral` + `world-events.intro.first-join-spawn`

---

## 2. Pyralingrad — a Láng fővárosa (RED)

**Lore:** a Vérszavanna szíve, Soleil főnix-örökségének városa. **Akácia, homokkő és
mélypala** — tikkasztó, egzotikus, agresszív építészet: lángoló tornyok, főnix-motívumok,
lándzsás kapuk. Már 14 évvel a Hasadás után állt: a legrégebbi főváros, lehet nyers,
katonás.

**Méret:** kb. **120×120 - 150×150 blokk** + falak. Biom: szavanna/badlands.

**Kötelező elemek:**
- **Trónterem** — a király-rendszer színtere (koronázás, raid-hirdetés).
- **Frakció-spawn tér** (`/territory setspawn red`) — ide érkezik, aki a Lánghoz csatlakozik,
  és ágy nélkül itt éled újra.
- **Bank-fiók + váltó** (banker/exchange NPC — a bank fővároshoz kötött!).
- **Frakció-bolt** (shop-NPC: `/npcbind <npc> shop <bolt-id>`) — a RED tematikus árukészlete.
- **Kassza-terem / kincstár** (flavor a /faction treasury köré, adó-hirdetőtábla).
- **Hazatérés-kő szentély** (LODESTONE oltár).
- **Kaszt-szentély(ek)** — ide illők: `harcos_szentely` (ANVIL), `demonvadasz_szentely`
  (CHISELED_NETHER_BRICKS), `saman_szentely` (LIGHTNING_ROD).
- **Karaván-megálló** (ha a karaván ide is jár: `caravan.stops`).
- **Városfalak + kapu** — raid-célpont hangulat.
- Opcionális: **városi őrség útvonal** (city-guards), **kazamata-lejárat** (lásd 7.).

**Zónázás:** `capital red` a mag + `faction RED` a környező birtokok (claimelhető) +
`protected-faction` a falakra/műemlékekre.

---

## 3. Glatziendorf — a Fagy fővárosa (BLUE)

**Lore:** az örök tél városa a Jégmezőkön, Kallan sárkány-örökösei. **Fenyő, kő és
gyapjú** — fagyos, sötét, büszke: vastag falak, sárkány-motívumok, jégbe vágott
tornyok. Hu. 117-ben épült.

**Méret és elemek:** ugyanaz a lista, mint Pyralingradnál (trónterem, frakció-spawn,
bank-fiók + váltó, frakció-bolt, kincstár, hazatérés-kő, falak, karaván-megálló),
BLUE-ra hangolva. Biom: havas síkság/hegység.
- Ide illő kaszt-szentélyek: `ijasz_szentely` (FLETCHING_TABLE), `sarkanyidezo_szentely`
  (END_STONE_BRICKS), `pap_szentely` (SEA_LANTERN), `szerzetes_szentely` (BELL).

**Zónázás:** `capital blue` + `faction BLUE` + `protected-faction`.

---

## 4. Mortengrad — a Holtak Városa (DARK)

**Lore:** a Káoszkor által elpusztított egykori büszke főváros — **düledező tornyok,
benőtt sikátorok, kővázak**. A Kitaszítottak egyetlen menedéke; az élőhalottak úgy
róják az utcákat, mint egykor a polgárok. A plugin ezt ÉLŐVÉ teszi: a DARK
territóriumokban magától járkáló, szintezett élőhalott-lakosság spawnol
(`dark-undead` ambiencia) — a DARK-tagokat és az éjjeli Suttogókat nem bántják,
mindenki másnak halálos csapda.

**Méret:** kb. **100×150 blokk romváros**. NE legyen szép: beomlott háztetők, hiányos
falak, benőtt utcák, üres piacterek. Fény alig (a mob-spawnhoz sötét kell!), mohás
kő, sculk-erek a romok közt.

**Kötelező elemek:**
- **Frakció-spawn** a romok közt (`/territory setspawn dark`) — ide száműzik a bűnösöket.
- **A Csontszámvevő kincstára** — a DARK bank/váltó NPC-je (lore: ő vereti a
  Csontveretet). Romos bank-épület, csontszámvevő-NPC `bank` + `exchange` kötéssel.
- **A Suttogók legszentebb oltára** — a romok MÉLYÉN, nehezen megtalálható rejtett
  szentély: `eleftheria_konnye` rituálé-oltár (CRYING_OBSIDIAN mag, DARK-kapus) +
  körülötte **SCULK-padló** (a Sötét Rítushoz — Suttogóvá válni éjjel, egyedül,
  SCULK-blokkon állva lehet; több ilyen rejtett sculk-folt is legyen a világban!).
- **A `sotet_beavatas` quest célpontja** — a Nekromanta-spec zarándok-célja; a
  quest a városba érkezést figyeli, tehát a zóna maga a cél (`territory` DARK).
- **`feloldozas` oltár** (SOUL_LANTERN mag) — a vezeklés-lánc végi bűn-tisztító
  szentély: lehet a város szélén álló megtört kápolna.
- **Hazatérés-kő** (LODESTONE) romos változata.
- Ide illő szentélyek: `halallovag_szentely` (CRYING_OBSIDIAN), `boszorkany_szentely`
  (RESPAWN_ANCHOR), `pakt_oltar` (SOUL_LANTERN — Boszorkánymester paktum).

**Zónázás:** `capital dark mortengrad` (ezzel él a `dark-undead` ambiencia „minden
DARK territórium" beállítása) + a környék `faction DARK`.

---

## 5. A Kárhozat Kapuja — a Senkiföldje (DOOM_GATE)

**Lore:** a Hetedik Vérháborút kirobbantó **óriás Nether-portál** a Jégmezők és a
Vérszavanna közti gazdátlan vadonban. Instabil, a Nether energiája szivárog belőle,
megfertőzve a földet. Itt nincs törvény: **a PvP legális, az ölés nem bűn**.

**Építés:**
- **Egy monumentális, TÖRÖTT Nether-portál** — javasolt 15-25 blokk magas obszidián
  kapu-keret, repedezve, körülötte kristályosodott/korrupt táj (netherrack-erek,
  soul sand foltok, halott fák, láva-hasadékok).
- Körülötte **aréna-jellegű romtáj** ~60-100 blokk sugárban: fedezékek, romok,
  csontok — a két elpusztult sereg maradványai (törött ostromgépek, zászlók).
- A plugin ad rá: bónusz-szintű mobokat, belépési PvP-türelmet, robbanás/tűz-védett
  terepet — a zóna maga védett (nem építhető/bontható), tehát amit megépítesz, megmarad.

**Zónázás:** `/territory circle doom-gate <id> <sugár>` (frakció nélkül) — a sugár
fedje az egész arénát.

---

## 6. Rituálé-oltárok (multiblock szentélyek) — pontos építési recept

Minden oltár ugyanarra a mintára épül (5×5 lábnyom, 3 magas):
- **Alapzat:** 5×5 padló a mag-blokk alatt (y-1) a megadott anyagból.
- **4 saroktorony:** a 4 sarkon 2 magas oszlop (y0 + y1).
- **Középen a MAG-BLOKK** — ezt kell SHIFT+jobb kattal aktiválni.
- A pontos blokk-lista a `config/relics.yml` `rituals.<id>.structure` kulcsában van —
  építés után teszteld aktiválással!

A `pakt_oltar` kisebb: 3×3 obszidián alap, közepén CRYING_OBSIDIAN, rajta SOUL_LANTERN.

**Oltár-lista (mag-blokk → hova való):**

| Rituálé | Mag-blokk | Javasolt hely |
|---|---|---|
| `eleftheria_konnye` (DARK relikvia) | CRYING_OBSIDIAN | Mortengrad mélye (rejtett) |
| `phoenix_wing` | MAGMA_BLOCK | Vérszavanna / Pyralingrad környéke |
| `frost_wing` | BLUE_ICE | Jégmezők / Glatziendorf környéke |
| `wander_wind` | AMETHYST_BLOCK | vadon, magaslat |
| `bone_wing` | SOUL_SOIL | Mortengrad / temető-rom |
| `feloldozas` (bűn-tisztítás) | SOUL_LANTERN | Mortengrad széli kápolna |
| `atok_tores` | CRYING_OBSIDIAN | vadon, romhely |
| `hazateres` (haza-teleport) | LODESTONE | MINDEN fővárosba egy! |
| 13 kaszt-szentély (`*_szentely`) | lásd relics.yml | fővárosok szentély-negyedei + vadon-szentélyek |

A kaszt-szentélyeket oszd el tematikusan: pl. `druida_szentely` (FLOWERING_AZALEA) egy
erdei ligetbe, `varazslo_szentely` (ENCHANTING_TABLE) a caldesterai akadémiára,
`elementalista`/`saman` egy viharvert hegycsúcsra stb. Nem baj, ha némelyik a vadonban,
felfedezésre vár.

---

## 7. Kazamaták (DUNGEON zónák)

Kulcs-kapus, heti pecsétes instancia-dungeonök. Építs **1-2 kazamatát kezdésnek**:
- **Méret:** 40-80 blokk hosszú, kanyargó belső tér (folyosók + 2-3 terem + boss-terem).
- **Stílus-ötlet:** a Vasművek Akadémiájának elhagyott tárnái; egy elsüllyedt
  Bokic-parti céh-pince; jégbarlang-labirintus.
- Bejárat: jól látható kapu-építmény a vadonban — a belépéshez kulcs-tárgy kell
  (`dungeonkulcs_<zóna-id>` signature; boltba/receptbe köthető).
- A benti mobok erejét a plugin adja (`mob-rules.dungeon`), a boss admin-eszközzel
  telepíthető.

**Zónázás:** `/territory create|circle dungeon <frakció> <id> ...` — a zóna védett,
nem bontható.

---

## 8. Esemény-infrastruktúra (fix helyszínek a világeseményekhez)

- **Világboss-aréna:** 1-2 állandó aréna a vadonban (~40-60 blokk átmérőjű nyílt
  küzdőtér, romokkal/fedezékkel; hagyj 8 blokk szabad spawn-teret középen). Kijelölés:
  állj középre → `/events spawnpoint add world-boss [id]`, majd
  `world-events.anchors.world-boss.mode: points` (vagy `mixed`).
- **Kíséret-útvonal indulópont:** karaván-út menti állomás (őrtorony + út) —
  `/events spawnpoint add escort`.
- **Kereskedő-karaván megállók:** fogadó-udvarok a fővárosokban/utak mentén —
  `caravan.stops` koordináta-lista.
- **Kultista-helyszínek** (opcionális): baljós kőkörök, romos kápolnák a vadonban —
  `/events spawnpoint add cultists`.
- **Utak!** A fővárosokat összekötő úthálózat (a Bokic-gázlókkal) sokat ad a
  világnak — a kíséret, a hírvivő és a karaván is „úton járó" események.

---

## 9. Titkos helyek (felfedező-tartalom)

Szórj szét a világban **8-15 kis nevezetességet** — mindegyik kap felfedező-jutalmat
(első megtaláló: broadcast + teljes jutalom). Építs 10-30 blokkos mini-helyszíneket:
- elveszett kilátó, ledőlt óriás-szobor, remete-kunyhó, elhagyott halász-tanya a
  Bokicnál, jég-barlangi szentély, homokba temetett karaván, Asterlayna
  csillag-kráter, Káoszkor-emlékmű…
- Bekötés: `hidden-spots.spots.<id>` (name + location + radius + rewards).
- A lore-ból meríts neveket (Elveszett Uralkodók, céhek, Vérháborúk emlékei).

---

## 10. NPC-összesítő (kit kell kihelyezni és bekötni)

| NPC belső név | Hely | Kötés |
|---|---|---|
| `hirnok` | Caldestera főtér | onboarding + útmutatás questek (quests.yml már hivatkozza) |
| `harcos_mester` | Caldestera céhház | mentor+mester-questek (giver-npc kész) |
| `ijasz_mester` | Caldestera céhház | ugyanígy |
| `varazslo_mester` | Caldestera akadémia | ugyanígy |
| `orgyilkos_mester` | Botera-negyed | ugyanígy |
| `druida_mester` | erdei liget (Ryanora) | ugyanígy |
| `paplovag_mester` | Caldestera szentély-negyed | ugyanígy |
| `halallovag_mester` | Mortengrad kapuja / kripta | ugyanígy |
| `saman_mester` | viharvert magaslat v. Glatziendorf | ugyanígy |
| `szerzetes_mester` | hegyi kolostor v. Caldestera | ugyanígy |
| `pap_mester` | Caldestera szentély-negyed | ugyanígy |
| `pakt_mester` | Botera-negyed mélye / rejtett zug | ugyanígy |
| `demonvadasz_mester` | a Kárhozat Kapuja pereme | ugyanígy |
| `sarkany_mester` | Glatziendorf (sárkány-lore) | ugyanígy |
| `kovacs_mester` | Caldestera kovács-negyed | beszállító-questek |
| `erdei_venek` | erdei liget (Ryanora) | quest-célpont |
| `vandor_kereskedo` | (a karaván hozza magával) | quest-célpont |
| frakció-választó NPC-k (3-4 db) | Caldestera követségek | `/npcbind <npc> faction <frakció>` |
| banker + váltó NPC-k | minden főváros bankja | `/npcbind <npc> bank` / `exchange` |
| bolt-NPC-k | fővárosi boltok | `/npcbind <npc> shop <bolt-id>` |
| Csontszámvevő | Mortengrad kincstár | bank + exchange (DARK) |

(NPC-létrehozás: FancyNpcs `/npc create <név>`; a skin/megjelenés szabad, a BELSŐ név
számít a kötéshez.)

---

## 11. Gyors méret-puska

| Építés | Méret (kb.) |
|---|---|
| Caldestera városmag | 200×200 + falak |
| Pyralingrad / Glatziendorf | 120-150 oldalhosszú + falak |
| Mortengrad romváros | 100×150 |
| Kárhozat Kapuja portál | 15-25 magas kapu, 60-100 sugarú zóna |
| Rituálé-oltár | 5×5×3 (pakt: 3×3) |
| Parkour-pálya | 30-60 hosszú, 1-2 perces |
| Kazamata | 40-80 belső hossz |
| Titkos hely | 10-30 |
| Világboss-aréna | 40-60 átmérő |

## 12. Admin-parancs puska (az építés utáni bekötéshez)

```
/territory pos|wand ... create <capital|faction|protected-faction|protected-city> <frakció> <id> [név]
/territory circle <típus> <frakció> <id> <sugár>      # kör-zóna (doom-gate: frakció nélkül)
/territory setcapital <frakció> <id>                   # főváros-kijelölés
/territory setspawn <frakció>                          # frakció-spawn (a pontos pozíciód)
/npc create <név>  +  /npcbind <név> <quest|shop|bank|exchange|faction|command> ...
/parkour setstart|setfinish <pálya-id>
/events spawnpoint add <world-boss|escort|caravan|cultists|any> [id]
/exchangeboard place
/crate set <láda-id>
/icesmp config set season-monument.location "world,x,y,z"
/icesmp config set world-events.intro.first-join-spawn "world,x,y,z"
# + config-fájlok: caravan.stops, city-guards.guards, hidden-spots.spots
```

> **Sorrend-javaslat:** 1. Caldestera (spawn + onboarding működjön) → 2. Mortengrad
> (a bűn-rendszer célállomása) → 3. a két harcos főváros → 4. Kárhozat Kapuja →
> 5. oltárok/pályák/titkos helyek/arénák folyamatosan.
