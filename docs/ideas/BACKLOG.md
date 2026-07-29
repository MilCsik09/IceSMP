# Ötlet-backlog (megtisztított)

Ez a fájl **csak azt tartja**, amiért érdemes dolgozni: az elmaradásokat, a refaktor-maradékot, a
tartalomhoz sokat adó tételeket, a review új ötleteit és a kihasználatlan modern API-t.

**Gyomlálás — 2026-07-25 (tulaj-kérés):** a korábbi ~250 nyitott tételből **kb. 190 törölve**. Ami
kikerült: mikro-kozmetika (hang/partikel/címke-finomítások), egymásra épülő láncok, amelyek első
eleme sem indult el, alacsony hozamú admin-eszközök, és a „niche, alacsony prioritás" saját
jelöléssel felvett ötletek. **Semmi nem veszett el:** a teljes korábbi lista a git-történetben él
(`git log -p docs/ideas/BACKLOG.md`). A törlés szempontja: *ad-e ez a tétel érdemben új játékot,
vagy csak több felületet ad ugyanahhoz?*

Jelölés: 🟢/🟡/🔴 = munka (kicsi/közepes/nagy) • ⭐–⭐⭐⭐ = érték • `[TOP]` = ajánlott következő kör

---

## 1. Elmaradások (elkezdett vagy megkövetelt, de nem kész)

- **Világépítés (szerver-csapat, nem plugin-kód)** — ez blokkolja a teljes onboardingot és a
  sztori-gerincet: **18 NPC** kihelyezése `/npcbind` kötésekkel (`hirnok` 9, `erdei_venek` 5
  quest-ponton kötelező!), **4 territory-id** (`dark-capital`, `erdei-szentely`,
  `karhozat-kapuja`, `radicora`), a 4 királyság-spawn (`/territory setspawn`), a `kezdo_parkour`
  pálya, a rituálé-oltár helyszínek, az üres `hidden-spots.spots` feltöltése és a
  **kazamata-belsők + loot** megépítése. Részletek: `PROJEKT-AUDIT.md` világépítő-checklist.
- **A17 HP-rendszer** — az 1.+2. ütem megépítve, de **alapból kikapcsolva** (`health.enabled`).
  Nyitva: pajzs/abszorpció-források közös szabálya + playtest-hangolás (PvP TTK, PvE
  mob-damage). 🟡⭐⭐⭐
- **Hibrid spell-költség finomhangolás** — határeset-spellek „valutájának" pontosítása,
  tier-alapú erőforrás-költségek és regen-ráta hangolása playtest alapján. 🟡⭐⭐
- **Frakció-passzív számok** felülvizsgálata playtest után. 🟢⭐⭐
- **#18 relikvia-források** — a **Mételytépő** és a **Sárkánytojás-töredék** se rituáléból, se
  lootból nem szerezhető meg, ezért a `RelicPvpTransferListener` teljes egészében holt kód
  (a Mételytépő az egyetlen weapon-relic). Tulaj-emlékeztetővel függőben. 🟢⭐⭐
- **Playtest-mérés (nem kód):** tablist/worldEventsTask/petTask időzítés 50-60 fővel; Íjász és
  Orgyilkos valós DPS (a papír-metrika vak a DoT/vanília rétegre).

## 2. Refaktor-maradék

A közös-helper csomag lezárva (MobKillUtil, TabCompleteUtil, DailyBudget, TransientEntities,
PeriodicChanceEvent, RespecService). Ami tudatosan maradt:

- **O21** `AbilityCatalystListener` felelősség-szétbontás — 852 sor, 9 map egy osztályban.
  **Akkor bontsuk, amikor a következő spell/katalizátor-bővítés amúgy is megnyitja** — így a
  bontás a valós használati minta szerint alakul, nem elméleti tengelyek mentén. 🟡⭐⭐
- **O27 maradék** — 10 világesemény-manager átvezetése a `PeriodicChanceEvent`-re. Mind
  bizonyítottan helyes (synchronized spawn-on belüli recheck), ~6 sor/manager a nyereség, ezért
  **csak playtesttel egy körben, managerenként külön committal** — nem vakon, kötegben. 🟢⭐
- **`IceSMPCore` manager-építés factory-szétbontása** — 1586 sor, 86 manager-mező; a `final`
  mezők miatt nem triviális. Akkor esedékes, ha a konstruktor sorrendje tényleges hibát okoz. 🟡⭐
- **`/icesmp reload` sikerüzenete korrupt confignál is kimegy** — a `ConfigValidator` csak logol,
  a sender „sikeres újratöltés"-t lát. 🟢⭐⭐
- **`save()` szinkronizáltság** — 14 persistentStore-manager `save()`-je nem synchronized
  (konkurens mentésnél a régebbi snapshot nyerhet). 🟢⭐⭐
- **`ProtectionBridge`** flag-ellenőrzés nélkül **bármely WorldGuard-régiót tiltottnak vesz**. 🟢⭐⭐
- **Kalmár-karaván** nem megy át az `EventSpawnGuard`-on + hiányzik a placeholder-UUID re-check
  a hopolt callbackben (a már lezárt szállítmány felülírható). 🟢⭐⭐
- **`/menu` hiányok:** `/tanacs`, `/komp`, `/faction war` nincs csempézve; admin-alparancsok jog
  nélkül is látszanak a tab/help-ben; 1 relikvia-üzenet a MessageManageren kívül. 🟢⭐
- **Tanács-szavazás alt-védelem** (min. játékidő-feltétel) + **ambient-jutalom napi cap**
  (a többi forrás mind kapott keretet) + **parkour-ranglista** (a best-time nem perzisztált). 🟢⭐

## 3. Tartalom — ami sokat ad

### Progresszió és végjáték
- **B28** `[TOP]` **Kaszt-story questlánc** — kasztonként 5-8 lépéses küldetéssor a 25. szintű
  spec-választásig. A 13 mester-lánc már él configban; ez adja a *miért* -et a szintezéshez. 🟡⭐⭐⭐
- **B7** Frakció-fejlesztési fa — kasszapénzből tartós upgrade-ek (XP%, olcsóbb claim). Értelmet
  ad a kasszának a puszta gyűjtögetésen túl. 🟡⭐⭐⭐
- **B58** Relikvia-őrzés territórium-buff — kiállított relikvia passzív buffot ad a
  frakcióterületnek: a relikviák így *védendő* értékké válnak, nem trófeává. 🟡⭐⭐⭐
- **B5** Területi erőforrás-pontok — elfoglalható lelőhely-zónák óránkénti hozammal a birtokos
  frakciónak: állandó, alacsony intenzitású konfliktus-forrás. 🔴⭐⭐⭐
- **B51** Fiók-szintű meta-progresszió — kaszt-független, sosem nullázódó QoL-perkek: ez adja a
  szezon-reset utáni „nem nulláról kezdek" érzést. 🟡⭐⭐
- **B60** Tárgy-szett szinergia — tematikus szett viselése kis passzív bónuszt ad (az affix-szett
  attribútum-összegzés már kész, tehát a fél infra megvan). 🟡⭐⭐

### PvE és világesemények
- **H8** `[TOP]` Világboss add-fázis + interrupt — megszakítható csatorna-speciál addokkal. A
  bossok ma statikus HP-szivacsok; ez az egyetlen tétel, ami *harcot* csinál belőlük. 🟡⭐⭐⭐
- **H18** `[TOP]` Nyilvános esemény-csoportok auto-partyval — súrlódásmentes ideiglenes csoport
  világesemény indulásakor (a party-rendszer és a personal loot már kész). 🟡⭐⭐⭐
- **H22** `[TOP]` Heti kihívás-rotáció pontozással — a MEGLÉVŐ 15 világeseményből heti pontozott
  feladat-készlet: nulla új esemény-kód, mégis heti ritmust ad. 🟡⭐⭐⭐
- **H1** Vándorló világboss — több napig kóborol, nyomot hagy, kockázat-skálázott loot. 🔴⭐⭐⭐
- **H5** Szörny-fészek felszámolás — táguló szörny-forrás, amit minél előbb rommá kell tenni. 🟡⭐⭐⭐
- **H13** Kooperatív boss-végrehajtás — alacsony HP-n több-játékosos „gyenge pont" fázis. 🟡⭐⭐⭐
- **H19** Világ-cél: közös boss-gyengítés — közösségi cél módosítja a következő boss erejét. 🟡⭐⭐⭐
- **H10** Heti mythic világboss-változat per-account lockouttal. 🔴⭐⭐⭐

### PvP és frakció-háború
- **G1** `[TOP]` Raid zászlólopás-mód — mozgó célpont, üldözhető zászlóhordozó. Ez oldja a
  „fővárosi raid-cél védett zónában" alapproblémát is. 🟡⭐⭐⭐
- **G2** Kassza-feltörés fázisokkal — 3-fázisú mini-objektíva-lánc a kasszáért. 🟡⭐⭐⭐
- **G4** 3v3 skirmish-pont a határon — kis, beleegyezéses csapat-PvP várólistával. 🟡⭐⭐⭐
- **G10** Heti rivális-frakció — liga-állás alapján automatikus rivális-pár extra ponttal. 🟡⭐⭐⭐
- **B30** Háború-ablakok — raid csak megadott idősávban + védett hétvége (időzóna-védelem). 🟡⭐⭐⭐
- **G11+G12** Anti-snowball — sorozatvereség után védő-buff, leszakadó frakciónak PvE-bónusz. 🟡⭐⭐

### Gazdaság
- **B23** Játékos-boltok (chest shop) — tábla+láda bolt claimen, offline is működő adás-vétel.
  A leghiányzóbb gazdasági felület: ma minden kereskedés online jelenlétet kíván. 🔴⭐⭐⭐
- **B13** Piaci vételi megbízások (buy order) — fordított piac, vevő escrow-ba zár. 🟡⭐⭐
- **F8** Raid-biztosítás — havi díjas frakció-biztosítás, vereségnél részleges kártérítés
  a kasszából (pénz-semleges, mert kasszából fedezett). 🟡⭐⭐⭐
- **B1** `[TOP]` Heti Királyi Megbízások (battlepass-lite) — frakciónkénti heti feladatlista
  pontokért, kassza-jutalom + top-tag buff. 🟡⭐⭐⭐
- **F19** Zálogház — tárgy zálogba kasszapénzért, kamattal kiváltható (item-fedezetű sink). 🟡⭐⭐

### Szakmák
- **I1** `[TOP]` Mestermű-esély — kritikus craft, raritás-létrán egy fokkal feljebb. A
  szakma-vég ma jutalmatlan; ez adja a „megérte 50-re menni" pillanatot. 🟡⭐⭐⭐
- **I18** Recept-lánc (Kovács nyers penge → Alkimista finomítás) — szakmák közti *együttműködés*
  az egymás mellett élés helyett. 🟡⭐⭐⭐
- **I6** Ritka nagy érc-ér esemény jelzéssel — véletlen nagy lelőhely, közelítő broadcast. 🟡⭐⭐⭐
- **I3** Napi szakma-megrendelés NPC-től — napi 1-2 HAVE/CRAFT cél extra XP-ért. 🟡⭐⭐⭐
- **I12** Műhely-blokkok — claimre építhető szakma-állomás közelségi bónusszal. 🟡⭐⭐⭐
- **J15** Mestermunka quest-sor — szakma 50. szintjén egyedi, egyszeri záró küldetés. 🟡⭐⭐⭐

### Questek és story
- **J6** `[TOP]` Sürgős küldetések — időkorlátos EXTRA jutalom, ami sosem büntet. 🟢⭐⭐⭐
- **J10+J12** Döntés-következmény flagek + visszatérő NPC-k emlékezettel — a `merchant_choice`
  ma a játék EGYETLEN elágazó választása, és nyoma sem marad. Ez adja a story-nak a súlyt. 🟡⭐⭐⭐
- **J8** Csoportos quest (party-közös progressz). 🟡⭐⭐⭐
- **J1** Kíséret-küldetés objektíva (ESCORT_NPC) — élő NPC-kísérés, elbukhat. 🟡⭐⭐⭐
- **J18** Quest-statisztika — accept/complete/abandon számláló balansz-döntésekhez. 🟢⭐⭐⭐
- **~13 napos sztori-lyuk a szezon 41-53. napján** + mind a 31 rejtvény kapu nélkül, 1. naptól
  kimeríthető (elő-terheltség). 🟢⭐⭐

### Kaszt-identitás (a legerősebbek a 13-ból)
- **E10+E11** Orgyilkos: kombó-pont felhalmozás + execute-forma. 🟡⭐⭐⭐
- **E13** Paplovag: eskü-töltés — blokkolt sebzés Szent Erővé alakul. 🟡⭐⭐⭐
- **E18+E20** Sámán: totem-hálózat + elemi egyesülés (4 totem ulti). 🟡⭐⭐⭐
- **E27+E28** Démonvadász: bosszú-fúria (elszenvedett sebzésből is töltődik) + démon-forma ulti. 🔴⭐⭐⭐
- **E22** Pap: ima-visszhang — kritikus gyógyítás + Mana-visszatérítés. 🟡⭐⭐⭐
- **E30+E31** Sárkányidéző: Eszencia két-pólusú skála + sárkánylehelet csatorna-ulti. 🔴⭐⭐⭐
- **E15** Halállovag: rúna-pecsét kombó (Vér/Fagy/Halál egyensúly). 🔴⭐⭐⭐

### UX, ami tényleg számít
- **A27** Okos gyógyítás — sneak-cast healer a legsebzettebb párttagra. Gyógyító-kasztot ma
  szinte játszhatatlanul körülményes irányítani. 🟡⭐⭐⭐
- **A20** Quest-tracker a HUD-on — a követett quest objektíva-állása állandó oldalsáv-szekció. 🟡⭐⭐
- **A18** Spell-loadoutok — 2-3 elmenthető spell-készlet (PvP/farm/boss) gyors váltással. 🟢⭐⭐
- **A28** Menü-badge-ek — jelzés a `/menu` csempéken, ha van teendő (talentpont, napi quest). 🟡⭐⭐
- **B34** Lebomló sír halálkor — a cucc védett sír-blokkba kerül X percre. 🟡⭐⭐
- **B36** Gyorsutazás-hálózat (útkövek) — táv-arányos díjjal (money sink + a nagy világ
  bejárhatósága). 🟡⭐⭐⭐

### Hangulat és közösség (a legjobb 4)
- **D5+D14** Kocsma ital-buffokkal + közös vacsora-bónusz — a 4 kocsma-ital már kész
  (CONSUMABLE), ez adja hozzá a *helyet*. 🟡⭐⭐
- **D20** Szobor-oszlop saját statokkal — mérföldkövet elérő játékosok a fővárosban. 🟡⭐⭐
- **D22** Közösségi híd/út-építő projekt — blokk-adományozásos közös cél látható eredménnyel. 🟡⭐⭐
- **D26** Időkapszula-esemény — szezononkénti beásható láda, következő szezonzáráskor nyílik. 🟡⭐⭐

### Infra, ami valódi döntéseket támogat
- **C1** `[TOP]` Spell-használati statisztika — cast-számláló + `/icesmp balance report`. 390 spell
  van; balansz-döntést ma tiszta megérzésből hozunk. 🟢⭐⭐⭐
- **C3** Gazdasági faucet/sink monitor — heti forrásonkénti pénz-keletkezés/megsemmisülés. 🟢⭐⭐
- **C8** Edzőbábu — sebezhetetlen DPS-mérő céltábla (a fenti két méréshez a harc-oldali pár). 🟢⭐⭐⭐
- **C5** Discord-webhook híd — nagy események (boss/raid/szezon/király) Discordra. 🟢⭐⭐⭐
- **C6** YAML-store integritás-őr — induláskori parse-próba, sérülésnél auto-visszaállítás. 🟡⭐⭐
- **M5** Loader: adatbázis-réteg előkészítés — HikariCP+SQLite/H2, **csak ha a YAML szűkös lesz**
  (60 fő fölött vagy ha a mentés-idő látható lag lesz). 🔴⭐⭐

### Bootstrap-réteg
- **M2** Kárhozat-lehelet damage-type — DoT a Kárhozat-zóna közepén, amit a Rúnavért nem blokkol
  (a bootstrap damage-type-regisztráció már működik, ez csak egy új sor + listener). 🟡⭐⭐

---

## 4. Új ötletek (2026-07-25 review — a kódexből és a rendszer-leltárból)

### Lore-vezérelt (a kódex kimondja, a kód nem tudja)
- **N3** **A Vasművek Akadémiájának Csákánya** — a lajstrom NEUTRAL legendás csákánya. Ellenőrizve:
  a „Vasművek Akadémiája" hat helyen szerepel, de MIND raritás-**flavor sor** az `item-rarity.yml`-ben
  — nevesített csákány-item nincs. Kézenfekvő cél: a Bányász 50. szintű mestermunka-questjének
  (J15) jutalma. 🟢⭐⭐
- **N4** `[TOP]` **A Néma Királynő átka a koronán** — a kódex szerint Eleftheria átka „a világ
  minden koronás főjét sírba vitte", és négy Elveszett Uralkodó nevét a legendás tárgyak őrzik.
  A királyi rendszer ma ezt nem tudja. Javaslat: a trónon töltött idővel gyűlő **Átok-szint** —
  eleinte csak hangulat (suttogás-üzenetek, lich-türkiz partikel a koronán), magasabb szinten
  valódi tét (élőhalott-vonzás, gyógyulás-csökkenés), és a lemondás/trónfosztás tisztít. Ez egy
  csapásra lore-ba kötné a király-rendszert, és beépített anti-örökös-király fék. 🟡⭐⭐⭐
- **N5** **Lélekkapocs-visszhang** — a kódex szerint a Lélekkapocs „a Fa ajándéka", el nem dobható.
  Ötlet: az Élet Fája alatt (spawn) a Lélekkapocs rövid ideig **fénylik és többet ad** (kis
  cooldown-csökkentés vagy erőforrás-regen) — a spawn így nem csak tér, hanem *szent hely*. 🟢⭐⭐
- **N6** **Asterlayna-szilánk meteor** — a meteor-esemény ma névtelen kőzuhanás, pedig a kódex
  első lapja arról szól, hogy **Asterlayna lezuhant**. Ötlet: a kráter közepén ritkán
  „Asterlayna-szilánk" (unique material) — rituálé-hozzávaló, a lore-hoz kötve. Nulla új
  rendszer, csak egy loot-sor + név. 🟢⭐⭐

### Mechanika-ötletek (a meglévő rendszerekre építve)
- **N7** `[TOP]` **Fegyver-hatótáv kaszt-identitásként** — az 1.21.11 `AttackRange` komponens
  itemenként állítja a `minReach`/`maxReach`-et. Ez a legolcsóbb módja, hogy a 13 kaszt fegyvere
  *érezhetően* más legyen: lándzsa/alabárd hosszú, tőr rövid de gyors. Ma minden fegyver
  egyforma távolságból üt. 🟢⭐⭐⭐
- **N8** **Lovas roham (KineticWeapon)** — az 1.21.11 `KineticWeapon` komponens
  (`damageMultiplier`, `forwardMovement`, `dismountConditions`) mozgás-alapú sebzést ad: a
  Paplovag lándzsája lóháton rohamban üt igazán. Kész vanília-mechanika, nulla saját tick-kód. 🟡⭐⭐⭐
- **N9** **Néma léptek (UseEffects)** — az `interactVibrations` komponenssel egy item
  **nem gerjeszt sculk-vibrációt**. Ez az Orgyilkos lopakodás-identitásának kézenfekvő,
  vanília-alapú kiterjesztése (warden/sculk-környezetben taktikai előny). 🟢⭐⭐
- **N10** **Ritkaság-szín natívan (RARITY)** — a pluginnak saját affix/raritás-rendszere van, de
  a vanília `RARITY` komponenst nem használja: a név-szín és a tooltip-kezelés ingyen jönne,
  konzisztensen a vanília itemekkel. 🟢⭐⭐
- **N11** **Ital-maradék (USE_REMAINDER)** — a 4 kocsma-ital megivása üres kupát/palackot adjon
  vissza, amit vissza lehet vinni a kocsmárosnak pár veretért. Apró hurok, de a kocsmát
  *helyből* rendszerré teszi. 🟢⭐⭐
- **N12** **Recept-tanító tárgy (RECIPES komponens)** — a tervrajz-rendszer ma saját kódú; a
  vanília `RECIPES` komponens felvételkor tanít receptet. Érdemes megvizsgálni, hogy a
  tervrajz-út egyszerűsíthető-e rá. 🟢⭐

---

## 5. Modernizáció — kihasználatlan 1.21.11 API

*A valódi `folia-api-1.21.11` bájtkód-átvizsgálásából (2026-07-25). A data-komponens API-t MÁR
használjuk (6 komponens: `ATTRIBUTE_MODIFIERS`, `CONSUMABLE`, `FOOD`, `ITEM_MODEL`,
`TOOLTIP_DISPLAY`, `USE_COOLDOWN`) — az alábbiak a **még nem használt** felületek.*

### Data-komponensek (89-ből 6-ot használunk)
- **Fegyver-viselkedés (1.21.11-es újdonságok):** `KineticWeapon` (N8), `PiercingWeapon`
  (átszúró csapás, `dealsKnockback`), `AttackRange` (N7), `MINIMUM_ATTACK_CHARGE`,
  `SwingAnimation` (itemenkénti csapás-animáció), `WEAPON`, `TOOL`. 🟡⭐⭐⭐
- **Item-viselkedés:** `RARITY` (N10), `USE_REMAINDER` (N11), `USE_EFFECTS` (N9),
  `RECIPES` (N12), `BREAK_SOUND`, `POTION_DURATION_SCALE`, `DAMAGE_TYPE`. 🟢⭐⭐
- **Tartósság deklaratívan:** `MAX_DAMAGE`, `DAMAGE`, `REPAIRABLE`, `ENCHANTABLE`,
  `REPAIR_COST`, `MAX_STACK_SIZE` — a custom gear kopás/javítás-szabályai ma kódban élnek. 🟢⭐⭐
- **Korábban felvéve, továbbra is nyitva:** `EQUIPPABLE` (kozmetikai kalap/korona — a
  frakció-kozmetikák aranybányája), `DEATH_PROTECTION`, `GLIDER`, `BLOCKS_ATTACKS`, `PROFILE`,
  `TOOLTIP_STYLE`, `CONTAINER`/`CONTAINER_LOOT`, `LODESTONE_TRACKER`. 🟡⭐⭐⭐

### Nem használt eventek (a leghasznosabbak)
- **`PrePlayerAttackEntityEvent`** — a csapás MEGELŐZÉSE (nem a sebzés cancelje). Tisztább
  megoldás a zóna/raid PvP-kapura és a combat-tagre, mint a damage-event visszavonása. 🟢⭐⭐⭐
- **`EntityEquipmentChangedEvent` + `PlayerInventorySlotChangeEvent`** — pontos horog a
  felszerelés-váltásra: az affix-stat/attribútum-alkalmazás (friss rendszer!) ma nem esemény-
  vezérelt. 🟢⭐⭐⭐
- **`PlayerCustomClickEvent`** — a **Dialog API** callback-eventje; a P6 dialog-konverziók
  (menük natív dialógusra) ezen keresztül működnek. A Dialog API teljesen jelen van (45 osztály). 🟡⭐⭐
- **`EntityToggleSitEvent`** — közvetlen illeszkedés a meglévő `SitManager`-hez. 🟢⭐
- **`PlayerNameEntityEvent`** — pet átnevezése névtáblával (a pet-rendszerhez). 🟢⭐
- **`PlayerStopUsingItemEvent`** — csatornázott/felhúzott spellek elengedésének pontos horga. 🟢⭐⭐
- **`EntityKnockbackEvent`** — a spell-knockback skálázása/tiltása (tank-talent horog). 🟢⭐⭐
- **`PlayerItemCooldownEvent` / `PlayerItemGroupCooldownEvent`** — a katalizátor
  `USE_COOLDOWN`-csoportjához HUD-visszajelzés. 🟢⭐
- **`EntityDamageItemEvent`** — unique/relikvia itemek kopás-védelme deklaratívan. 🟢⭐
- **`PlayerShieldDisableEvent`** — a Bástya-pajzs baltával kiütésének kezelése. 🟢⭐
- **`EntityMoveEvent`** — mob-mozgás esemény (konvoj-vezetés, pet-követés) a tick-polling helyett;
  Folia-barát, az entitás régió-szálán fut. 🟡⭐⭐

### Egyéb modern felületek
- **P5 Datapack-réteg — PONTOSÍTVA (2026-07-25).** A P5a/P5b **leszállt**: az `AdvancementService`
  7 csomópontos natív haladás-fája (+ a `ToastUtil` quest-toastjai) data-driven advancement
  JSON-ként él, és a Bukkit ezeket a VILÁG automatikusan generált datapackjébe teszi
  (`<world>/datapacks/bukkit/`, „Data pack for resources provided by Bukkit plugins").
  Tehát datapack-formában és -mechanizmussal működik — csak nem a jar szállítja.
  **Ami NYITOTT:** a betöltés a `Bukkit.getUnsafe()` úton megy, ami az API-ban `@Deprecated`
  (MC/Paper-bumpnál ez az első törési pont). A modern, támogatott alternatíva a
  `io.papermc.paper.datapack.DatapackRegistrar`: a jar szállítana saját datapacket
  (advancement + recept + loot + struktúra egy helyen). 🟡⭐⭐
  **Playteszten ellenőrizendő:** a `ToastUtil` MINDEN toasthoz véletlen kulcsú advancementet
  tölt be; ha a `removeAdvancement` a lemezről nem törli, a fájlok quest-teljesítésenként
  szaporodnak a bukkit-datapackben. Ha igen, a javítás egy FIX kulcsú, újrahasznosított
  toast-advancement. 🟢⭐⭐
- **P8b Resource-pack PUSH** (`sendResourcePacks`) — **a legjobb érték/munka arány a listán:**
  a 265 ITEM_MODEL kész és manifestelt, de a szerver soha nem tolja ki a packet, ezért az
  **egész vizuális réteg alszik**, amíg valaki kézzel nem telepíti. 🟡⭐⭐⭐
- **P8c Egyedi fontok + negatív térköz + `shadowColor`** — pixel-pontos HUD/GUI-grafika. 🟡⭐⭐
- **P8f Block/BlockState PDC (TileState)** — adat közvetlenül a blokkon (rituálé-oltár,
  claim-jelölő, kazamata-láda) külön UUID-map helyett. 🟢⭐
- **P8h Structure API** — programozott struktúra-lerakás (kazamata-belsők generálásához). 🟡⭐⭐
- **`io.papermc.paper.math.Position`/`BlockPosition`** — allokáció-mentes blokk-matematika a
  `Location`-klónozás helyett a forró utakon (zóna-ellenőrzés, spawn-keresés). 🟢⭐

### Plugin-beolvasztás (kevesebb külső függőség)
- 🟢 gyors: **FarmProtect** (termés-taposás, ~30 sor), **minimotd**, **ICEsmpadditions**
  (a forrása kell hozzá).
- 🟡 közepes: **economist + service-io** (ha semmi nem függ tőlük → törölhetők),
  **FancyHolograms** (általános `/hologram` a meglévő TextDisplay-infrára), **AuMenus**,
  **VillagerTradeEdit**.
- 🟠 nagy: **TAB** (header/footer + LP-prefix a saját HUD-ba), **WorldGuard** (admin-zóna flagek
  a TerritoryManagerbe) — csak alapos playtest után.

---

## 6. Tulaj-döntéssel lezárt tételek (ne nyissuk újra)

- **Céh-rendszer bővítése** — NEM bővítjük: tovább szabdalná a 4 felé húzó playerbase-t. A váz
  marad, tartalom-invest nélkül.
- **Ostrom-rombolás külön rendszerként** — nem kell: a claim/territórium-robbantás a
  regen-rendszerrel már lefedett.
- **Bank-kamat** — elvetve: a kamat a semmiből teremtene valutát. Csak szigorúan pénz-semleges,
  kizárólag kasszából fedezett formában jöhet szóba (lásd F19/F20).
- **Címek/rangok** — nem: ütközne a szerver rang-pluginjaival.
- **Frakció-diplomácia (szövetség/béke)** — 3 királysággal a szövetség-tér egyetlen 2v1
  kapcsoló; csak akkor éri meg, ha valaha több frakció / al-klán rendszer lesz.
- **Mikro-optimalizációk** (O3/O7/O8/O10/O12/O13/O22/O23) — méréssel mind mérhetetlen; és az
  **O11** GUI close-cleanup kifejezetten REGRESSZIÓT okozna (a QuestBuilder-prompt szándékosan
  chat-alapú, túl kell élnie a GUI bezárását).
