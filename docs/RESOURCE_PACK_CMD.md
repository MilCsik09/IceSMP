# Resource Pack — ITEM_MODEL textúra-leírások

Ez a fájl a textúra-készítő (és a képgenerátor) bemenete. A plugin minden custom/egyedi tárgya modern **ITEM_MODEL** komponenst visel (`icesmp:<modell-id>`); régi numerikus modelladatot sehol nem használunk. A pack itemenként az `assets/icesmp/items/<modell-id>.json` modell-fájlt és a hozzá tartozó `assets/icesmp/textures/item/<modell-id>.png` textúrát szállítja — a `<modell-id>` egyben a PNG fájlneve is.

Minden tétel négy fogódzót ad a művésznek: **Alap-item** (a vanilla sziluett-referencia), **Ábrázolás** (mit ábrázoljon), **Színvilág** (paletta + akcent) és **Hangulat / lore** (a világon belüli érzet).

## Technikai tudnivalók

- **Méret:** 16×16 vagy 32×32 px, átlátszó háttérrel (PNG) — a teljes pack egységesen ugyanazt használja, vegyes felbontás tilos.
- **Fájlnév és hely:** a kész PNG az `assets/icesmp/textures/item/<modell-id>.png` útvonalra kerül; a JSON-bekötés kész, csak a PNG-t kell szállítani.
- **Alap-item:** a vanilla tárgy, aminek a helyén az item megjelenik — a vanilla textúrája jó kiindulás a sziluetthez/érzethez.
- **Frakció-színvilág:** RED = Perinfernicitas (láng, vörös-arany), BLUE = Cryghaliris (jég, kék-ezüst), NEUTRAL = Ryanora/Caldestera (kereskedő-arany, zöld-okker), DARK = Kitaszítottak (csont, éjfekete-lila, és a jellegzetes **hideg türkiz derengés** — a lich-szem: a Néma Királyné élőhalott-fénye a szemekben, rúnákban, élek mentén).

## Stílus-szabályok (vanilla-konzisztencia)

1. **Egységes felbontás:** a felbontást a textúra-készítő választja meg (vanilla-hű 16×16 vagy részletesebb 32×32) — de a TELJES pack egységesen ugyanazt használja, vegyes felbontás tilos.
2. **Margó:** a tárgy ne érjen a vászon széléig — a vászon ~80%-át töltse ki, középre igazítva (16-osnál ~1-2 px, 32-esnél ~3-4 px üres perem).
3. **Sziluett-olvashatóság:** az item egy vanilla tárgy helyén jelenik meg — első ránézésre ugyanannak a tárgy-osztálynak tűnjön (bot=bot, sisak=sisak). Kard/szerszám 45°-ban átlósan: markolat balra-le, hegy jobbra-fel.
4. **Kontúr:** 1 px sötét körvonal a külső élen, de NEM tiszta fekete — az anyagszín legmélyebb árnyalata; a belső vonalak még lágyabbak.
5. **Paletta-fegyelem:** anyagonként 4–8 tónus, kemény pixel-átmenetek — semmi blur, anti-aliasing vagy színátmenet; dither csak nagyon indokoltan.
6. **Fényirány:** mindig bal-felső fényforrás (világos él fent/balra, mély tónus lent/jobbra); vetett árnyék a sziluetten kívül nincs.
7. **Alfa igen/nem:** minden pixel vagy teljesen fedő, vagy teljesen átlátszó — félig átlátszó élpixel TILOS (a játékban csúnya szegély lesz belőle).
8. **Telítettség:** a vanilla visszafogott, földes palettája mellett a neon kiabál — izzó akcent (lich-türkiz, láng) csak kevés pixelen (2–6 fénypont).
9. **Kicsiben is működjön:** az inventoryban ~16–32 képernyő-pixel látszik — a 2 px-nél kisebb részlet eltűnik; minden darabot kicsinyítve is ellenőrizz.
10. **Perspektíva:** lapos sprite szemből (vagy 45°-os szerszám) — nem izometrikus/3D nézet.

## Globális paletta

A faction- vagy lore-kötött tárgyak (a nevükben szereplő hely/örökség alapján) ezt az akcenst viseljék:

| Téma | Színek |
|---|---|
| RED / Perinfernicitas (Pyralingrad, Soleil, Főnix, Vérszavanna) | mélyvörös, parázs-narancs izzás, arany |
| BLUE / Cryghaliris (Glatziendorf, Kallan, jég/fagy, sárkány) | jégkék, ezüst, törtfehér, zúzmara-csillám |
| NEUTRAL / Ryanora-Caldestera (Bokic, Creutzér, Smaragdkő, céh) | kereskedő-arany, borostyán, zöld-okker |
| DARK / Kitaszítottak (Thanaopolis, Eleftheria, Néma Királyné) | csont-törtfehér, éjfekete-lila, hideg türkiz lich-fény |
| Mélység / tenger / prizmarin (Mélység Népe, tengeri ereklyék) | prizmarin-türkiz, gyöngyház, tengerkék |

## AI-generálási prompt-sablon

Ha a textúrákat képgenerátorral készíted, ez a sablon jó kiindulás. **Tippek:** egyszerre csak 4–6 itemet kérj egy lapra (úgy tartja a stílust); sima FEHÉR hátteret kérj; NE kérj szöveget a képre; az itemek leírását a lenti blokkok **Ábrázolás + Színvilág** sorából másold be.

```
Pixel art sprite sheet of [N] fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. [első item: Ábrázolás + Színvilág, angolra fordítva]
2. [második item…]
...

Style: chunky 16-bit pixel art, limited palette (4-8 tones per material),
1px dark outline in the material's own darkest shade (not pure black),
light source from the top-left, crisp hard pixels, no anti-aliasing,
no gradients, no drop shadows, flat 2D sprite view (no isometric perspective),
each item centered with a small margin, consistent style across all icons,
cohesive fantasy RPG game asset set, high quality pixel art.
```

A frakció-akcentek angol fordítása a prompthoz: RED = „glowing ember orange-red", BLUE = „icy light blue and silver", NEUTRAL = „merchant gold and amber", DARK = „bone white, pitch black, with cold turquoise lich-glow accents".

## Pénz-érmék

### `currency_blue` — Hópihér-veret
- **Fájl:** `currency_blue.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** kerek, vert érme, rovátkolt peremmel, közepén dombornyomott HÓPEHELY-címer
- **Színvilág:** hűvös ezüstszürke; akcent: jeges világoskék
- **Hangulat / lore:** Cryghaliris valutája, a Hópihér-veret — hideg, tiszta, ezüstös csillogás.

### `currency_dark` — Csontveret
- **Fájl:** `currency_dark.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** kopott, sötét érme, közepén dombornyomott KOPONYA-címer, a koponya szemüregeiben apró TÜRKIZ izzással, szélein csorbulások
- **Színvilág:** sötétebb, kékes acél; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** A Kitaszítottak Csontverete — akit ezzel fizetnek, nem kérdez. A türkiz szempár a Néma Királynő jele.

### `currency_neutral` — Creutzér
- **Fájl:** `currency_neutral.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** kerek, vert érme, közepén dombornyomott KERESKEDŐ-MÉRLEG címer
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: borostyánsárga
- **Hangulat / lore:** Ryanora–Caldestera valutája, a Creutzér — a Bankárszövetség megbízható aranya.

### `currency_red` — Parázsló Parals
- **Fájl:** `currency_red.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** kerek, vert érme, peremén rovátkolt díszítés, közepén dombornyomott LÁNGNYELV-címer
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: izzó narancsvörös
- **Hangulat / lore:** Perinfernicitas valutája, a Parázsló Parals — a láng népének büszke, forró aranya.

## Erszény, pálcák és ostromgép

### `blueprint` — Recept-tervrajz
- **Fájl:** `blueprint.png` &nbsp;|&nbsp; **Alap-item:** `KNOWLEDGE_BOOK`
- **Ábrázolás:** kék tervrajz-lap fehér szerkesztési vonalakkal, egyik sarka felpöndörödik
- **Színvilág:** középkék; akcent: törtfehér
- **Hangulat / lore:** Recept-tervrajz — ebből tanulják a mesterek a ritka recepteket.

### `money_pouch` — Kopott erszény
- **Fájl:** `money_pouch.png` &nbsp;|&nbsp; **Alap-item:** `LEATHER`
- **Ábrázolás:** zsinórral összehúzott, kopott bőrerszény; a nyakánál kikandikáló 2-3 aranyérme
- **Színvilág:** cserzett bőrbarna; akcent: földbarna
- **Hangulat / lore:** Talált pénz: mob-drop és horgász-lelet. Viseltes, útszéli hangulat — valaki elvesztette.

### `selection_wand` — Birtokmérő pálca
- **Fájl:** `selection_wand.png` &nbsp;|&nbsp; **Alap-item:** `STICK`
- **Ábrázolás:** egyenes mérőpálca zöld zsinór-jelöléssel, a végén kis smaragd-csúccsal
- **Színvilág:** világos fa + smaragdzöld akcent
- **Hangulat / lore:** a Bankárszövetség földmérőinek eszköze — vele jelölik ki a birtokok sarkait.

### `selection_wand_territory` — Határkijelölő pálca
- **Fájl:** `selection_wand_territory.png` &nbsp;|&nbsp; **Alap-item:** `BLAZE_ROD`
- **Ábrázolás:** aranyveretes hosszú pálca, tetején kis zászló-motívummal
- **Színvilág:** arany-bronz; akcent: mélyvörös zászló
- **Hangulat / lore:** a koronák földmérő-pálcája — territórium-határok admin-kijelöléséhez.

### `siege_ram` — Ostromgép
- **Fájl:** `siege_ram.png` &nbsp;|&nbsp; **Alap-item:** `TNT_MINECART`
- **Ábrázolás:** nehéz faltörő kos vasalt fejjel, tölgygerenda váz, kis kerekeken
- **Színvilág:** fabarna; akcent: sötét vasalás
- **Hangulat / lore:** Ostromgép — a raidek kapudöntője; a király hadának súlyos érve.

## Relikviák

### `relic_bone_wing` — Csontszárny
- **Fájl:** `relic_bone_wing.png` &nbsp;|&nbsp; **Alap-item:** `ELYTRA`
- **Ábrázolás:** csontokból szőtt, szakadozott szárny sötét hártyával, az ízületeknél hideg türkiz izzás-pontokkal
- **Színvilág:** törtfehér csontszín; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Csontszárny — a Káoszkor élőhalott-relikviája; éjjel viselője árnyékká válik.

### `relic_eleftheria_konnye` — Eleftheria Könnye
- **Fájl:** `relic_eleftheria_konnye.png` &nbsp;|&nbsp; **Alap-item:** `HEART_OF_THE_SEA`
- **Ábrázolás:** éjfekete, megkövült könnycsepp, belsejében halvány TÜRKIZ fénymaggal (lich-fény)
- **Színvilág:** éjkék; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Eleftheria Könnye — a Néma Királynő első suttogása kővé dermedve.

### `relic_frost_wing` — Zúzmara-szárny
- **Fájl:** `relic_frost_wing.png` &nbsp;|&nbsp; **Alap-item:** `ELYTRA`
- **Ábrázolás:** kiterjesztett szárny jégkristály-tollakkal, fagyott csillogással
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Zúzmara-szárny — Cryghaliris elytra-relikviája, jégsárkány-lehelettel átitatva.

### `relic_metelytepo` — Mételytépő
- **Fájl:** `relic_metelytepo.png` &nbsp;|&nbsp; **Alap-item:** `GOLDEN_AXE`
- **Ábrázolás:** arany harci balta, a feje körül halvány lila derengéssel; ősi, idegen mintázatú nyél
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: meleg fabarna
- **Hangulat / lore:** A Mételytépő — a törpék rejtélyes civilizációjának relikviája. Múzeumi kincs, nem szerszám.

### `relic_phoenix_wing` — Főnix-szárny
- **Fájl:** `relic_phoenix_wing.png` &nbsp;|&nbsp; **Alap-item:** `ELYTRA`
- **Ábrázolás:** kiterjesztett, stilizált szárny lángoló tollakkal, a hegyénél izzással
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Főnix-szárny — Perinfernicitas elytra-relikviája, Soleil főnixeinek tollából.

### `relic_sarkany_tojas` — Sárkánytojás-töredék
- **Fájl:** `relic_sarkany_tojas.png` &nbsp;|&nbsp; **Alap-item:** `DRAGON_EGG`
- **Ábrázolás:** repedt sárkánytojás-szilánk pikkelyes héjjal, a repedésben mély lila fénymaggal
- **Színvilág:** sötét lila-fekete; akcent: mély ibolya belső fény
- **Hangulat / lore:** Sárkánytojás-töredék — egy ősi sárkány kihűlt ígérete; a repedésben még dereng a tűz.

### `relic_wander_wind` — Vándorszél
- **Fájl:** `relic_wander_wind.png` &nbsp;|&nbsp; **Alap-item:** `ELYTRA`
- **Ábrázolás:** könnyű, világos szárny, lebegő, áttetsző tollakkal
- **Színvilág:** égszínkék; akcent: törtfehér
- **Hangulat / lore:** Vándorszél — Ryanora & Caldestera szabad szele, Arkynn békés öröksége.

## Kaszt-katalizátorok (Lélekkapocs)

### `catalyst_archer` — Soleil Vadásztarsolya
- **Fájl:** `catalyst_archer.png` &nbsp;|&nbsp; **Alap-item:** `RABBIT_HIDE`
- **Ábrázolás:** kikészített irha — misztikus, kaszt-színű derengéssel (Soleil Vadásztarsolya)
- **Színvilág:** élénk levélzöld; akcent: borostyánsárga
- **Hangulat / lore:** A(z) Archer kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_assassin` — Homály-szilánk
- **Fájl:** `catalyst_assassin.png` &nbsp;|&nbsp; **Alap-item:** `FLINT`
- **Ábrázolás:** pattintott kovakő — misztikus, kaszt-színű derengéssel (Homály-szilánk)
- **Színvilág:** mély ibolyalila; akcent: sötétebb, kékes acél
- **Hangulat / lore:** A(z) Assassin kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_death_knight` — Néma Rúnakoponya
- **Fájl:** `catalyst_death_knight.png` &nbsp;|&nbsp; **Alap-item:** `WITHER_SKELETON_SKULL`
- **Ábrázolás:** megfeketedett koponya — misztikus, kaszt-színű derengéssel (Néma Rúnakoponya)
- **Színvilág:** éjkék; akcent: mély ibolyalila
- **Hangulat / lore:** A(z) Death Knight kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_demon_hunter` — Hasadék Szeme
- **Fájl:** `catalyst_demon_hunter.png` &nbsp;|&nbsp; **Alap-item:** `ENDER_EYE`
- **Ábrázolás:** végzet-szem — misztikus, kaszt-színű derengéssel (Hasadék Szeme)
- **Színvilág:** sötétebb, kékes acél; akcent: éjkék
- **Hangulat / lore:** A(z) Demon Hunter kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_druid` — Aetrinita Sarja
- **Fájl:** `catalyst_druid.png` &nbsp;|&nbsp; **Alap-item:** `OAK_SAPLING`
- **Ábrázolás:** fiatal csemete — misztikus, kaszt-színű derengéssel (Aetrinita Sarja)
- **Színvilág:** mohazöld; akcent: élénk levélzöld
- **Hangulat / lore:** A(z) Druid kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_evoker` — Sárkányvér-fiola
- **Fájl:** `catalyst_evoker.png` &nbsp;|&nbsp; **Alap-item:** `DRAGON_BREATH`
- **Ábrázolás:** lila köddel teli gömbpalack — misztikus, kaszt-színű derengéssel (Sárkányvér-fiola)
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** A(z) Evoker kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_monk` — Élet Ága
- **Fájl:** `catalyst_monk.png` &nbsp;|&nbsp; **Alap-item:** `BAMBOO`
- **Ábrázolás:** bambusznád — misztikus, kaszt-színű derengéssel (Élet Ága)
- **Színvilág:** izzó narancsvörös; akcent: borostyánsárga
- **Hangulat / lore:** A(z) Monk kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_paladin` — Hajnaltűz Harangja
- **Fájl:** `catalyst_paladin.png` &nbsp;|&nbsp; **Alap-item:** `BELL`
- **Ábrázolás:** öntött harang — misztikus, kaszt-színű derengéssel (Hajnaltűz Harangja)
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: égszínkék
- **Hangulat / lore:** A(z) Paladin kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_priest` — Asterlayna Gyertyája
- **Fájl:** `catalyst_priest.png` &nbsp;|&nbsp; **Alap-item:** `WHITE_CANDLE`
- **Ábrázolás:** fehér gyertya — misztikus, kaszt-színű derengéssel (Asterlayna Gyertyája)
- **Színvilág:** hűvös ezüstszürke; akcent: égszínkék
- **Hangulat / lore:** A(z) Priest kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_shaman` — Ősvihar Totemje
- **Fájl:** `catalyst_shaman.png` &nbsp;|&nbsp; **Alap-item:** `TOTEM_OF_UNDYING`
- **Ábrázolás:** faragott totemfigura — misztikus, kaszt-színű derengéssel (Ősvihar Totemje)
- **Színvilág:** világító cián; akcent: jeges világoskék
- **Hangulat / lore:** A(z) Shaman kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_warlock` — Kárhozat Lámpása
- **Fájl:** `catalyst_warlock.png` &nbsp;|&nbsp; **Alap-item:** `SOUL_LANTERN`
- **Ábrázolás:** kék lángú lélek-lámpás — misztikus, kaszt-színű derengéssel (Kárhozat Lámpása)
- **Színvilág:** bordó; akcent: mély ibolyalila
- **Hangulat / lore:** A(z) Warlock kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_warrior` — Sárkánykirály Kürtje
- **Fájl:** `catalyst_warrior.png` &nbsp;|&nbsp; **Alap-item:** `GOAT_HORN`
- **Ábrázolás:** ívelt kürt — misztikus, kaszt-színű derengéssel (Sárkánykirály Kürtje)
- **Színvilág:** mélyvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A(z) Warrior kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### `catalyst_wizard` — Caldesterai Rúnakódex
- **Fájl:** `catalyst_wizard.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** derengő varázskönyv — misztikus, kaszt-színű derengéssel (Caldesterai Rúnakódex)
- **Színvilág:** királylila; akcent: világító cián
- **Hangulat / lore:** A(z) Wizard kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

## Társ-befogók és pecsétek

### `capture_beast` — Ősi Kötés Póráza
- **Fájl:** `capture_beast.png` &nbsp;|&nbsp; **Alap-item:** `LEAD`
- **Ábrázolás:** feltekert pányva/lasszó, zöld természet-szimbólummal a közepén
- **Színvilág:** cserzett bőrbarna; akcent: élénk levélzöld
- **Hangulat / lore:** Ősi Kötés Póráza — a Vadmester ezzel fogadja társává az állatokat (Aetrinita és Kallan kötése).

### `capture_heart` — Nyughatatlan Szív
- **Fájl:** `capture_heart.png` &nbsp;|&nbsp; **Alap-item:** `ECHO_SHARD`
- **Ábrázolás:** dobbanó, sötét szív izzó türkiz erekkel, éjfekete-lila hártya
- **Színvilág:** éjfekete-lila; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Nyughatatlan Szív — a Szentségtelen éjjel ezzel idézi ghúl-társát; a húsban még ver az élet.

### `capture_necro` — Sötét Paktum-tekercs
- **Fájl:** `capture_necro.png` &nbsp;|&nbsp; **Alap-item:** `GHAST_TEAR`
- **Ábrázolás:** sötét pergamentekercs koponya-pecséttel, a koponya szemeiben türkiz izzással
- **Színvilág:** mély ibolyalila; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Sötét Paktum-tekercs — a Nekromanta ezzel köti szolgájává a szörnyet (Eleftheria mérge).

### `capture_seal` — Démon-pecsét
- **Fájl:** `capture_seal.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** lila ametiszt pecsétszilánk vésett démonrúnával, hideg ibolyafény
- **Színvilág:** sötét ibolya; akcent: hideg lila derengés
- **Hangulat / lore:** Démon-pecsét — a Boszorkánymester paktum-tokenje; egy név a szilánkban, egy démon a hívásra.

## Spell-reagensek

### `spell_demonic_circle` — Démoni Só
- **Fájl:** `spell_demonic_circle.png` &nbsp;|&nbsp; **Alap-item:** `REDSTONE`
- **Ábrázolás:** vörös-lila szórt por, benne halványan izzó idéző-körminta
- **Színvilág:** mélyvörös-lila; akcent: izzó parázsvörös
- **Hangulat / lore:** Démoni Só — a démonológus körének anyaga; egy marék, és a padló felizzik.

### `spell_expel_harm` — Kiűzés Botja
- **Fájl:** `spell_expel_harm.png` &nbsp;|&nbsp; **Alap-item:** `BLAZE_ROD`
- **Ábrázolás:** fényes szent bot arany-fehér rúnaragyogással, gyógyító derengés
- **Színvilág:** arany-fehér; akcent: meleg világító sárga
- **Hangulat / lore:** Kiűzés Botja — a szerzetes ezzel űzi a fájdalmat; ahol suhan, ott gyógyul a seb.

### `spell_rune_strike` — Rúnakő
- **Fájl:** `spell_rune_strike.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** ametiszt rúnakristály, benne kék-fehér villámszikra, éles fénytörés
- **Színvilág:** ametiszt-lila; akcent: kék-fehér szikra
- **Hangulat / lore:** Rúnakő — a rúnavéső csapása kőbe zárva; egy villanás, és a rúna kioldódik.

### `spell_wild_mushroom` — Vadgomba
- **Fájl:** `spell_wild_mushroom.png` &nbsp;|&nbsp; **Alap-item:** `BROWN_MUSHROOM`
- **Ábrázolás:** zömök erdei barna gomba, zöld spóra-akcenttel, nedves természetes fény
- **Színvilág:** erdei barna; akcent: mohazöld
- **Hangulat / lore:** Vadgomba — a druida vad varázsának magja; Aetrinita talajából sarjad.

## Láda-kulcsok

### `cratekey_koznapi` — Caldesterai Kereskedőláda
- **Fájl:** `cratekey_koznapi.png` &nbsp;|&nbsp; **Alap-item:** `TRIPWIRE_HOOK`
- **Ábrázolás:** egyszerű vas ládakulcs sárgaréz fejjel, kopott mindennapi fém, kis fogazat
- **Színvilág:** kopott vasszürke; akcent: sárgaréz
- **Hangulat / lore:** Caldesterai Kereskedőláda kulcsa — a Botera-negyed hétköznapi szerencséje, aprópénzért.

### `cratekey_ritka` — Caldesterai Kincsesláda
- **Fájl:** `cratekey_ritka.png` &nbsp;|&nbsp; **Alap-item:** `TRIPWIRE_HOOK`
- **Ábrázolás:** díszes arany ládakulcs, fejébe ágyazott ékkővel, ragyogó vésett szár
- **Színvilág:** meleg arany; akcent: ékkő-csillanás
- **Hangulat / lore:** Caldesterai Kincsesláda kulcsa — a Bankárszövetség ígérete: ritka, drága, kívánatos.

## Szakma-alapanyagok

### `acskapocs` — Ácskapocs
- **Fájl:** `acskapocs.png` &nbsp;|&nbsp; **Alap-item:** `IRON_NUGGET`
- **Ábrázolás:** szerszámos láda / készlet-doboz
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Kovácsolt kapocs — gerendát fog össze, és tetőt tart, ha kell. Favágó kellék (csak boltból)

### `aranyfust_lemez` — Aranyfüst-lemez
- **Fájl:** `aranyfust_lemez.png` &nbsp;|&nbsp; **Alap-item:** `GOLD_NUGGET`
- **Ábrázolás:** kalapált, fényes fémlemez
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: borostyánsárga
- **Hangulat / lore:** Leheletvékonyra vert arany, a caldesterai ötvösnegyed büszkesége. Kovács köztes alapanyag

### `arnyekpor` — Árnyékpor
- **Fájl:** `arnyekpor.png` &nbsp;|&nbsp; **Alap-item:** `SCULK_VEIN`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** mély ibolyalila; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** A Néma Királynő suttogásának megülepedett, éjfekete pora — elnyeli a fényt és a hangot. Csak szörnyekből esik

### `arnygomba` — Mortengradi Árnygomba
- **Fájl:** `arnygomba.png` &nbsp;|&nbsp; **Alap-item:** `CRIMSON_FUNGUS`
- **Ábrázolás:** kalapos gomba
- **Színvilág:** földbarna; akcent: törtfehér csontszín
- **Hangulat / lore:** Csak romok pincéiben nő, fény sosem érte — a Kitaszítottak kenyérpótlója. Gyógynövényész köztes alapanyag

### `aszalohalo` — Aszalóháló
- **Fájl:** `aszalohalo.png` &nbsp;|&nbsp; **Alap-item:** `COBWEB`
- **Ábrázolás:** csomózott háló
- **Színvilág:** türkizes viharzöld; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Finom szövésű háló a füvek árnyékban szárításához. Gyógynövényész kellék (csak boltból)

### `csalizsir` — Csalizsír
- **Fájl:** `csalizsir.png` &nbsp;|&nbsp; **Alap-item:** `SLIME_BALL`
- **Ábrázolás:** kis hal oldalnézetből
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: borostyánsárga
- **Hangulat / lore:** Illatos zsír a csalira — a titkos összetevőt a halak sem tudják. Halász kellék (csak boltból)

### `csillekenocs` — Csillekenőcs
- **Fájl:** `csillekenocs.png` &nbsp;|&nbsp; **Alap-item:** `SLIME_BALL`
- **Ábrázolás:** fém kanna kiöntőcsőrrel
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Ettől fut a csille hang nélkül — a tárna tisztelete csendet kíván. Bányász kellék (csak boltból)

### `csontenyv` — Csontenyv
- **Fájl:** `csontenyv.png` &nbsp;|&nbsp; **Alap-item:** `BONE_MEAL`
- **Ábrázolás:** egyenes rúd/pálca forma
- **Színvilág:** törtfehér csontszín; akcent: törtfehér
- **Hangulat / lore:** A Mélység Népe régi receptje: lassan főzött enyv, ami követ is köt fához. Szakács köztes alapanyag

### `dermedt_konnycsepp` — Dermedt Könnycsepp
- **Fájl:** `dermedt_konnycsepp.png` &nbsp;|&nbsp; **Alap-item:** `GHAST_TEAR`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Azt mondják, Eleftheria sírt, mielőtt elhallgatott. Ez itt bizonyíték. Csak élőhalottakból esik

### `desztillalt_esoviz` — Desztillált Esővíz
- **Fájl:** `desztillalt_esoviz.png` &nbsp;|&nbsp; **Alap-item:** `GLASS_BOTTLE`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** középkék; akcent: világító cián
- **Hangulat / lore:** Háromszor párolt tavaszi eső — a főzet lelke a tiszta víz. Alkimista kellék (csak boltból)

### `ecet_eszencia` — Ecet-eszencia
- **Fájl:** `ecet_eszencia.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** sötét méregzöld; akcent: világító cián
- **Hangulat / lore:** Hétszer erjesztett eszencia — egy csepp tartósít, kettő büntet. Szakács kellék (csak boltból)

### `edzoolaj` — Edzőolaj
- **Fájl:** `edzoolaj.png` &nbsp;|&nbsp; **Alap-item:** `MAGMA_CREAM`
- **Ábrázolás:** fém kanna kiöntőcsőrrel
- **Színvilág:** sötétebb, kékes acél; akcent: borostyánsárga
- **Hangulat / lore:** Ebbe mártva sziszeg a penge — a Vasművek előírása szerint. Kovács kellék (csak boltból)

### `elso_csend_szilankja` — Az Első Csend Szilánkja
- **Fájl:** `elso_csend_szilankja.png` &nbsp;|&nbsp; **Alap-item:** `ECHO_SHARD`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** Nem tudni, mi ez, sem honnan való. Csak azt, hogy amikor a kezedbe veszed, a világ egy pillanatra elhallgat. A legritkább kincs — csak világbossból

### `emlekszilank` — Opálos Emlékszilánk
- **Fájl:** `emlekszilank.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** Egy Felső elveszett emlékének kristálya — romok mélyén, erős szörnyeknél rejtőzik. Beváltás: /emlek (visszaemlékezés)

### `ercmoso_lug` — Ércmosó-lúg
- **Fájl:** `ercmoso_lug.png` &nbsp;|&nbsp; **Alap-item:** `GLASS_BOTTLE`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** sötét méregzöld; akcent: világító cián
- **Hangulat / lore:** Leoldja a meddőt az ércről, és a kézről a tárnák szagát. Bányász kellék (csak boltból)

### `ezust_toll` — Ezüst-toll
- **Fájl:** `ezust_toll.png` &nbsp;|&nbsp; **Alap-item:** `FEATHER`
- **Ábrázolás:** írótoll tintával
- **Színvilág:** hűvös ezüstszürke; akcent: éjkék
- **Hangulat / lore:** Ezüsthegyű toll — a fontos szavakat nem bízzuk lúdtollra. Bűvölő kellék (csak boltból)

### `favedo_pac` — Favédő pác
- **Fájl:** `favedo_pac.png` &nbsp;|&nbsp; **Alap-item:** `INK_SAC`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** Sötét pác, mit az ácsok kevernek — a fa tőle éli túl a telet. Favágó kellék (csak boltból)

### `fenoko` — Fenőkő
- **Fájl:** `fenoko.png` &nbsp;|&nbsp; **Alap-item:** `SMOOTH_STONE`
- **Ábrázolás:** fém kanna kiöntőcsőrrel
- **Színvilág:** sötétebb, kékes acél; akcent: borostyánsárga
- **Hangulat / lore:** Vízköszörült kő a Bokic partjáról — az él tőle tanul énekelni. Favágó kellék (csak boltból)

### `folyositoszer` — Kovács-folyósítószer
- **Fájl:** `folyositoszer.png` &nbsp;|&nbsp; **Alap-item:** `BLAZE_POWDER`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** sötét méregzöld; akcent: világító cián
- **Hangulat / lore:** A Vasművek Akadémiájának adalékszere: nélküle a netherit nem veszi fel a rúnát. Csak boltból vehető

### `fonixpihe` — Főnixpihe
- **Fájl:** `fonixpihe.png` &nbsp;|&nbsp; **Alap-item:** `FEATHER`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Soleil főnixeinek pihéje — a hamu nem fogja, a láng nem emészti. Csak szörnyekből esik

### `fujtatobor` — Fújtatóbőr
- **Fájl:** `fujtatobor.png` &nbsp;|&nbsp; **Alap-item:** `RABBIT_HIDE`
- **Ábrázolás:** nyereg / lószerszám
- **Színvilág:** cserzett bőrbarna; akcent: földbarna
- **Hangulat / lore:** A kohó tüdeje — szakadt fújtatóval nincs se láng, se legenda. Kovács kellék (csak boltból)

### `fustoloforgacs` — Füstölőforgács
- **Fájl:** `fustoloforgacs.png` &nbsp;|&nbsp; **Alap-item:** `STICK`
- **Ábrázolás:** pálca/bot, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Válogatott keményfa-forgács — a füst a láthatatlan fűszer. Szakács kellék (csak boltból)

### `gyantaoldo` — Gyantaoldó
- **Fájl:** `gyantaoldo.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** Leoldja a gyantát a fűrészről és az alkut a kézfogásról. Favágó kellék (csak boltból)

### `gyogy_kivonat` — Gyógy-kivonat
- **Fájl:** `gyogy_kivonat.png` &nbsp;|&nbsp; **Alap-item:** `GLOW_BERRIES`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** A Bokic partján, hajnalban szedett gyógyfüvek ragyogó párlata. Gyógynövényész köztes alapanyag

### `gyongyhaz_pikkely` — Gyöngyház-pikkely
- **Fájl:** `gyongyhaz_pikkely.png` &nbsp;|&nbsp; **Alap-item:** `PRISMARINE_SHARD`
- **Ábrázolás:** derengő tintazsák / fénylő gyöngy
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: égszínkék
- **Hangulat / lore:** A Bokic vén pontyainak pikkelye — a fény hét színére törik rajta. Halász köztes alapanyag

### `halofonal` — Hálófonal
- **Fájl:** `halofonal.png` &nbsp;|&nbsp; **Alap-item:** `STRING`
- **Ábrázolás:** csomózott háló
- **Színvilág:** türkizes viharzöld; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Viaszolt fonal hálókötéshez — nem rohad, nem szakad, nem felejt. Halász kellék (csak boltból)

### `holdezust_huzal` — Holdezüst Huzal
- **Fájl:** `holdezust_huzal.png` &nbsp;|&nbsp; **Alap-item:** `CHAIN`
- **Ábrázolás:** feltekercselt huzal / drótspirál
- **Színvilág:** hűvös ezüstszürke; akcent: égszínkék
- **Hangulat / lore:** Újhold éjjelén húzott ezüstszál — csak akkor fénylik, ha senki sem nézi. Bűvölő köztes alapanyag

### `horogkeszlet` — Horogkészlet
- **Fájl:** `horogkeszlet.png` &nbsp;|&nbsp; **Alap-item:** `TRIPWIRE_HOOK`
- **Ábrázolás:** fém horgászhorog
- **Színvilág:** világos acélszürke; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Tucatnyi kovácsolt horog — a Bokic pontya válogatós. Halász kellék (csak boltból)

### `irnok_tinta` — Írnok-tinta
- **Fájl:** `irnok_tinta.png` &nbsp;|&nbsp; **Alap-item:** `INK_SAC`
- **Ábrázolás:** derengő tintazsák / fénylő gyöngy
- **Színvilág:** éjkék; akcent: világító cián
- **Hangulat / lore:** A Számvevők receptje szerint kevert tinta — nem fakul, nem hazudik. Bűvölő kellék (csak boltból)

### `jegviragpor` — Jégvirág-por
- **Fájl:** `jegviragpor.png` &nbsp;|&nbsp; **Alap-item:** `SUGAR`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** A Jégmezők peremén nyíló jégvirág szirma, mozsárban holdfénnyel törve. Gyógynövényész köztes alapanyag

### `karhozat_parazs` — A Kapu Parazsa
- **Fájl:** `karhozat_parazs.png` &nbsp;|&nbsp; **Alap-item:** `FIRE_CHARGE`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Kárhozat Kapujából kisodródott parázs — nem ég, nem alszik ki. Csak vár. Csak világbossból / nehéz eventből esik

### `katalizator_so` — Katalizátor-só
- **Fájl:** `katalizator_so.png` &nbsp;|&nbsp; **Alap-item:** `GLOWSTONE_DUST`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** mézarany; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Egy csipet, és a főzet felgyorsul — két csipet, és új laborod lesz. Alkimista kellék (csak boltból)

### `kemenyfa_gerenda` — Keményfa Gerenda
- **Fájl:** `kemenyfa_gerenda.png` &nbsp;|&nbsp; **Alap-item:** `STRIPPED_OAK_WOOD`
- **Ábrázolás:** rönk, évgyűrűkkel
- **Színvilág:** meleg fabarna; akcent: földbarna
- **Hangulat / lore:** A Bokic menti ősvadonban nőtt, évszázados tölgy magja. Favágó köztes alapanyag

### `koso` — Kősó
- **Fájl:** `koso.png` &nbsp;|&nbsp; **Alap-item:** `SUGAR`
- **Ábrázolás:** kristályos só-kupac
- **Színvilág:** törtfehér; akcent: hűvös ezüstszürke
- **Hangulat / lore:** A Menedék sóbányáiból — étel nélküle csak takarmány. Szakács kellék (csak boltból)

### `lampaolaj` — Finomított Lámpaolaj
- **Fájl:** `lampaolaj.png` &nbsp;|&nbsp; **Alap-item:** `GLOW_INK_SAC`
- **Ábrázolás:** fém kanna kiöntőcsőrrel
- **Színvilág:** borostyánsárga; akcent: szénfekete-szürke
- **Hangulat / lore:** A caldesterai lámpagyújtogatók titkos keveréke — tisztán, kormozás nélkül ég. Csak boltból vehető

### `lelekhamu` — Lélekhamu
- **Fájl:** `lelekhamu.png` &nbsp;|&nbsp; **Alap-item:** `GUNPOWDER`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** mély ibolyalila; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Kihunyt lélektűz maradéka. Hideg, szürke — és mégis, néha megmoccan. Alkimista köztes alapanyag

### `lombik_szen` — Lombik-szén
- **Fájl:** `lombik_szen.png` &nbsp;|&nbsp; **Alap-item:** `CHARCOAL`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Egyenletes lángot ad a lombik alá — a türelem tüzelője. Alkimista kellék (csak boltból)

### `melysegi_borostyan` — Mélységi Borostyán
- **Fájl:** `melysegi_borostyan.png` &nbsp;|&nbsp; **Alap-item:** `RAW_GOLD`
- **Ábrázolás:** kerek pite / sütemény
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** A Mélység Népe tárnáinak gyantája, kővé dermedt idő — néha egy-egy elveszett rúna is belefagyott. Régészeti lelet / bányász-finomítás

### `melysegi_iranytu` — Mélységi Iránytű-tű
- **Fájl:** `melysegi_iranytu.png` &nbsp;|&nbsp; **Alap-item:** `COMPASS`
- **Ábrázolás:** iránytű, a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: mélyvörös
- **Hangulat / lore:** A mélyben nem észak felé mutat — hazafelé. Az többet ér. Bányász kellék (csak boltból)

### `merozsinor` — Mérőzsinór
- **Fájl:** `merozsinor.png` &nbsp;|&nbsp; **Alap-item:** `STRING`
- **Ábrázolás:** sodrott kötélköteg
- **Színvilág:** földbarna; akcent: borostyánsárga
- **Hangulat / lore:** Krétázott zsinór — az egyenes vonal a mesterség fele. Favágó kellék (csak boltból)

### `nema_kristaly` — Néma Kristály
- **Fájl:** `nema_kristaly.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** három kristálytüske közös kőalapon
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Sculk-erek közt nőtt kristály. Ha a füledhez tartod, hallod, hogy NEM szól semmit. Csak szörnyekből esik

### `nyelbor` — Nyélbőr
- **Fájl:** `nyelbor.png` &nbsp;|&nbsp; **Alap-item:** `LEATHER`
- **Ábrázolás:** nyereg / lószerszám
- **Színvilág:** cserzett bőrbarna; akcent: földbarna
- **Hangulat / lore:** Cserzett szíj markolat-tekeréshez — a fegyver ott kezdődik, ahol a kéz. Kovács kellék (csak boltból)

### `obszidian_szilank` — Obszidián-szilánk
- **Fájl:** `obszidian_szilank.png` &nbsp;|&nbsp; **Alap-item:** `FLINT`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** A Kapu körüli mezőkről pattintott fekete üveg — a napfényt is elnyeli. Bányász köztes alapanyag

### `olomdugo` — Ólomdugó
- **Fájl:** `olomdugo.png` &nbsp;|&nbsp; **Alap-item:** `IRON_NUGGET`
- **Ábrázolás:** szerszámos láda / készlet-doboz
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Nehéz dugó illékony esszenciákhoz — ami bent van, bent is marad. Alkimista kellék (csak boltból)

### `oltoviasz` — Oltóviasz
- **Fájl:** `oltoviasz.png` &nbsp;|&nbsp; **Alap-item:** `HONEYCOMB`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** A metszés sebét zárja — a kert is gyógyul, nemcsak a beteg. Gyógynövényész kellék (csak boltból)

### `osi_ereklyeszilank` — Fekete Villám Szilánk
- **Fájl:** `osi_ereklyeszilank.png` &nbsp;|&nbsp; **Alap-item:** `NETHER_STAR`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** Az a sötét energia pulzál benne, amely Hu. 698-ban kettéhasította az eget, s felébresztette a Holtak Úrnőjét. Csak világbossból / nehéz eventből esik

### `parafa_uszo` — Parafa-úszó
- **Fájl:** `parafa_uszo.png` &nbsp;|&nbsp; **Alap-item:** `OAK_BUTTON`
- **Ábrázolás:** piros-fehér horgászúszó
- **Színvilág:** meleg fabarna; akcent: törtfehér
- **Hangulat / lore:** Festett úszó — ha megbillen, a víz alatt eldőlt valami. Halász kellék (csak boltból)

### `parazsmag` — Parázsmag
- **Fájl:** `parazsmag.png` &nbsp;|&nbsp; **Alap-item:** `BLAZE_POWDER`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Vérszavanna kihunyt tábortüzeiből kikapart, még mindig lüktető szikra. Alkimista köztes alapanyag

### `pergamen_simito` — Pergamen-simító
- **Fájl:** `pergamen_simito.png` &nbsp;|&nbsp; **Alap-item:** `BONE`
- **Ábrázolás:** félig kigördült pergamentekercs írássorokkal
- **Színvilág:** krémszínű pergamen; akcent: földbarna
- **Hangulat / lore:** Csontsimító a pergamenhez — rúna csak sima lapra ül meg. Bűvölő kellék (csak boltból)

### `permetezo_kanna` — Permetező-kanna
- **Fájl:** `permetezo_kanna.png` &nbsp;|&nbsp; **Alap-item:** `BUCKET`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** szénfekete-szürke; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Rézfejű kanna finom permethez — a harmatot utánozza, nem az esőt. Gyógynövényész kellék (csak boltból)

### `polirpaszta` — Polírpaszta
- **Fájl:** `polirpaszta.png` &nbsp;|&nbsp; **Alap-item:** `SUGAR`
- **Ábrázolás:** fém kanna kiöntőcsőrrel
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Finom csiszolópor — a tükörfény nem hiúság, hanem minőségjelzés. Kovács kellék (csak boltból)

### `rezgo_rez_otvozet` — Rezgő Rézötvözet
- **Fájl:** `rezgo_rez_otvozet.png` &nbsp;|&nbsp; **Alap-item:** `COPPER_INGOT`
- **Ábrázolás:** öntött fémrúd (ingot-forma)
- **Színvilág:** világos acélszürke; akcent: sötétebb, kékes acél
- **Hangulat / lore:** A Mélység Népe tárnáiból tanult ötvözet — halkan zúg, ha rúna simítja. Bányász köztes alapanyag

### `robbantopor` — Robbantópor
- **Fájl:** `robbantopor.png` &nbsp;|&nbsp; **Alap-item:** `GUNPOWDER`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** mélyvörös; akcent: szénfekete-szürke
- **Hangulat / lore:** A Vasművek engedélyezett keveréke — tárnát nyit, nem sírgödröt. Bányász kellék (csak boltból)

### `runa_bastya` — Bástya Rúnája
- **Fájl:** `runa_bastya.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Caldestera falainak emléke. Mellvértre: -kapott sebzés. Kattintsd a rúnát a mellvértre a táskádban!

### `runa_elek` — Él Rúnája
- **Fájl:** `runa_elek.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** A Mélység Népének első kovács-jele. Fegyverre helyezve: +közelharci sebzés. Kattintsd a rúnát a fegyverre a táskádban!

### `runa_fagy` — Fagy Rúnája
- **Fájl:** `runa_fagy.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Kallan jégsárkányainak lehelete. Fegyverre: találatkor eséllyel lelassít. Kattintsd a rúnát a fegyverre a táskádban!

### `runa_lang` — Láng Rúnája
- **Fájl:** `runa_lang.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Soleil főnixeinek szikrája. Fegyverre: találatkor eséllyel meggyújt. Kattintsd a rúnát a fegyverre a táskádban!

### `runa_moho` — Mohóság Rúnája
- **Fájl:** `runa_moho.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** A Bankárszövetség titkos vésete. Fegyverre: a szörnyek gyakrabban ejtenek erszényt. Kattintsd a rúnát a fegyverre a táskádban!

### `runa_visszhang` — Visszhang Rúnája
- **Fájl:** `runa_visszhang.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** Caldestera elveszett rúna-tudása — csak a Varázsló olvassa hangosan. Fegyverre: eséllyel visszhang-csapás. Kattintsd a rúnát a fegyverre a táskádban!

### `runa_zapor` — Zápor Rúnája
- **Fájl:** `runa_zapor.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Nyílzáport suttog a húrba. Íjra/számszeríjra: +lövedék-sebzés. Kattintsd a rúnát az íjra a táskádban!

### `runakreta` — Rúnakréta
- **Fájl:** `runakreta.png` &nbsp;|&nbsp; **Alap-item:** `CLAY_BALL`
- **Ábrázolás:** egyenes rúd/pálca forma
- **Színvilág:** törtfehér; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Előrajzoló kréta rúnákhoz — a hiba itt még letörölhető. Utána nem. Bűvölő kellék (csak boltból)

### `runapor` — Rúnapor
- **Fájl:** `runapor.png` &nbsp;|&nbsp; **Alap-item:** `GLOWSTONE_DUST`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** királylila; akcent: világító cián
- **Hangulat / lore:** Szétmorzsolt rúnakő csilláma — az Akadémiák elveszett rúna-tudásának nyers pora. Bűvölő köztes alapanyag

### `sarkanycsont_szilank` — Sárkánycsont-szilánk
- **Fájl:** `sarkanycsont_szilank.png` &nbsp;|&nbsp; **Alap-item:** `BONE`
- **Ábrázolás:** csontdarab, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Kallan elhullott jégsárkányainak csontja — hidegebb, mint a jég maga. Csak világbossból / nehéz eventből esik

### `sarkfeny_cseppko` — Sarkfény-cseppkő
- **Fájl:** `sarkfeny_cseppko.png` &nbsp;|&nbsp; **Alap-item:** `PRISMARINE_CRYSTALS`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** középkék; akcent: világító cián
- **Hangulat / lore:** Az északi fény egy cseppje, ami barlang mélyén kővé dermedt — még mindig táncol. Bányász köztes alapanyag

### `sozott_csali` — Sózott csali
- **Fájl:** `sozott_csali.png` &nbsp;|&nbsp; **Alap-item:** `DRIED_KELP`
- **Ábrázolás:** kis hal oldalnézetből
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: borostyánsárga
- **Hangulat / lore:** Hordóban érlelt csali — messziről érzik. A halak szerint finom. Halász kellék (csak boltból)

### `sutopergamen` — Sütőpergamen
- **Fájl:** `sutopergamen.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** félig kigördült pergamentekercs írássorokkal
- **Színvilág:** krémszínű pergamen; akcent: földbarna
- **Hangulat / lore:** Viaszolt lap a kemencébe — a lepény alja is megérdemli a tiszteletet. Szakács kellék (csak boltból)

### `suttogas_meghivo` — Suttogás
- **Fájl:** `suttogas_meghivo.png` &nbsp;|&nbsp; **Alap-item:** `ECHO_SHARD`
- **Ábrázolás:** félig kigördült pergamentekercs írássorokkal
- **Színvilág:** mély ibolyalila; akcent: királylila
- **Hangulat / lore:** Egy hang, amit csak te hallasz. Hívás, amit nem lehet visszautasítani — csak követni. Éjjel, a mélység sötétjén (sculk), egyedül…

### `szavannafu_kotel` — Szavannafű-kötél
- **Fájl:** `szavannafu_kotel.png` &nbsp;|&nbsp; **Alap-item:** `VINE`
- **Ábrázolás:** sodrott kötélköteg
- **Színvilág:** földbarna; akcent: borostyánsárga
- **Hangulat / lore:** A Vérszavanna szívós füvéből sodort kötél — a pásztorok szerint sárkányt is tartott már. Favágó köztes alapanyag

### `szorny_mag` — Szörny Mag
- **Fájl:** `szorny_mag.png` &nbsp;|&nbsp; **Alap-item:** `ECHO_SHARD`
- **Ábrázolás:** fiatal csemete/hajtás
- **Színvilág:** élénk levélzöld; akcent: földbarna
- **Hangulat / lore:** Egy káoszkori szörny lüktető, sötét szíve — még kihűlve is hatalmat rejt. Csak erősebb szörnyekből esik

### `szuropapir` — Szűrőpapír
- **Fájl:** `szuropapir.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** félig kigördült pergamentekercs írássorokkal
- **Színvilág:** krémszínű pergamen; akcent: földbarna
- **Hangulat / lore:** A zagyból ez választja ki az esszenciát. És az igazságot. Alkimista kellék (csak boltból)

### `tarnatamasz_szegecs` — Tárnatámasz-szegecs
- **Fájl:** `tarnatamasz_szegecs.png` &nbsp;|&nbsp; **Alap-item:** `IRON_NUGGET`
- **Ábrázolás:** szerszámos láda / készlet-doboz
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Szabvány szegecs a Mélység Népe méretei szerint. Sosem enged. Bányász kellék (csak boltból)

### `tiszta_vasesszencia` — Tiszta Vasesszencia
- **Fájl:** `tiszta_vasesszencia.png` &nbsp;|&nbsp; **Alap-item:** `IRON_NUGGET`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** A Vasművek Akadémiájának titka: ezerszer hajtogatott vas lelke. Kovács köztes alapanyag

### `tozegkocka` — Tőzegkocka
- **Fájl:** `tozegkocka.png` &nbsp;|&nbsp; **Alap-item:** `PACKED_MUD`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** világító cián; akcent: hűvös ezüstszürke
- **Hangulat / lore:** A Bokic-láp fekete aranya — ebben minden mag kicsírázik. Gyógynövényész kellék (csak boltból)

### `uvegfiola_keszlet` — Üvegfiola-készlet
- **Fájl:** `uvegfiola_keszlet.png` &nbsp;|&nbsp; **Alap-item:** `GLASS_BOTTLE`
- **Ábrázolás:** szerszámos láda / készlet-doboz
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Tucatnyi apró fiola — a kivonat annyit ér, amennyit az üveg megőriz. Gyógynövényész kellék (csak boltból)

### `vad_esszencia` — Vad Esszencia
- **Fájl:** `vad_esszencia.png` &nbsp;|&nbsp; **Alap-item:** `PHANTOM_MEMBRANE`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** A Káoszkor elvadult fenevadjának dühe, mely legyőzött testéből száll el. Csak szörnyekből esik

### `vaj` — Friss Vaj
- **Fájl:** `vaj.png` &nbsp;|&nbsp; **Alap-item:** `HONEYCOMB`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** mézarany; akcent: borostyánsárga
- **Hangulat / lore:** A Bokic-parti tanyák köpült vaja — amin ez megolvad, az már ünnep. Szakács kellék (csak boltból)

### `vandorfuszer` — Vándorfűszer
- **Fájl:** `vandorfuszer.png` &nbsp;|&nbsp; **Alap-item:** `COCOA_BEANS`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** mélyvörös; akcent: borostyánsárga
- **Hangulat / lore:** Hét út pora, hét piac illata egy zacskóban — a karavánok kincse. Szakács köztes alapanyag

### `viaszgyertya` — Viasz-gyertya
- **Fájl:** `viaszgyertya.png` &nbsp;|&nbsp; **Alap-item:** `CANDLE`
- **Ábrázolás:** égő gyertya
- **Színvilág:** mézarany; akcent: borostyánsárga
- **Hangulat / lore:** Méhviasz gyertya éjszakai munkához — a rúna fénynél születik, de sötétben ég. Bűvölő kellék (csak boltból)

### `viaszpecset` — Számvevő-pecsétviasz
- **Fájl:** `viaszpecset.png` &nbsp;|&nbsp; **Alap-item:** `HONEYCOMB`
- **Ábrázolás:** pecsétnyomó viaszpecséttel
- **Színvilág:** bordó; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Bankárszövetség hivatalos viasza — amit ez pecsétel, az megmásíthatatlan. Csak boltból vehető (Bankárszövetség)

### `viharkvarc` — Viharkvarc
- **Fájl:** `viharkvarc.png` &nbsp;|&nbsp; **Alap-item:** `QUARTZ`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Villámsújtotta sziklából fejtett kvarc — a belsejében még ott remeg a vihar. Bányász köztes alapanyag

## Recept — Alapanyag (tervrajz)

### `tengeristen_amulettje` — Tengeristen Amulettje
- **Fájl:** `tengeristen_amulettje.png` &nbsp;|&nbsp; **Alap-item:** `CONDUIT`
- **Ábrázolás:** tengeri vezérlőmag (conduit), a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: világító cián
- **Hangulat / lore:** A tenger mélyének szíve dobog benne, egy elfeledett isten hagyatéka. Halász-recept eredménye (Alapanyag (tervrajz) kategória, 48. szint).

## Recept — Bűvölés

### `csali_tomus` — Csali Tomus
- **Fájl:** `csali_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** kis hal oldalnézetből
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: borostyánsárga
- **Hangulat / lore:** Caldestera vízi rúnái, melyek a legmélyebb vizek lakóit is a felszínre csábítják. Bűvölő-recept eredménye (Bűvölés kategória, 20. szint).

### `eles_tomus` — Élesség Tomus
- **Fájl:** `eles_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: gyöngyházas, rózsás fehér
- **Hangulat / lore:** Ősi rúnatudás élesíti a pengét, melyet Caldestera bölcsei jegyeztek le. Bűvölő-recept eredménye (Bűvölés kategória, 15. szint).

### `fagypancel_tekercs` — Fagypáncél Tekercse
- **Fájl:** `fagypancel_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Glatziendorf jégvértjének rúnája könyvbe írva — üllőnél páncélra vihető. Bűvölő-recept eredménye (Bűvölés kategória, 35. szint).

### `fonixtoll_tekercs` — Főnixtoll Tekercse
- **Fájl:** `fonixtoll_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Soleil főnixeinek pihéjével írt rúna — üllőnél páncélra vihető tűz-oltalom. Bűvölő-recept eredménye (Bűvölés kategória, 35. szint).

### `fosztogatas_tomus` — Fosztogatás Tomus
- **Fájl:** `fosztogatas_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: gyöngyházas, rózsás fehér
- **Hangulat / lore:** Az elveszett rúnatudás egy szilánkja, mely bőségesebb zsákmányt ígér. Bűvölő-recept eredménye (Bűvölés kategória, 26. szint).

### `hatekonysag_tomus` — Hatékonyság Tomus
- **Fájl:** `hatekonysag_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: borostyánsárga
- **Hangulat / lore:** Rúnaírás az Akadémia mélyéről — a kéz mozdulatait gyorsítja meg örökre. Bűvölő-recept eredménye (Bűvölés kategória, 12. szint).

### `kemenyfa_ijkeret_tomus` — Keményfa Íjkeret Tomus
- **Fájl:** `kemenyfa_ijkeret_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: mélyvörös
- **Hangulat / lore:** Keményfa Gerenda erezetébe vésett rúna, mely az íjkeretet acélos hajlékonnyá teszi. Bűvölő-recept eredménye (Bűvölés kategória, 27. szint).

### `szerencse_tomus` — Szerencse Tomus
- **Fájl:** `szerencse_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: borostyánsárga
- **Hangulat / lore:** Az Akadémia legritkább rúnája — állítólag Asterlayna csillagfényéből őrződött meg. Bűvölő-recept eredménye (Bűvölés kategória, 30. szint).

### `tartossag_tomus` — Tartósság Tomus
- **Fájl:** `tartossag_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: jeges világoskék
- **Hangulat / lore:** Caldestera Akadémiájának elveszett rúnái, melyek örök tartást kölcsönöznek a fegyvernek. Bűvölő-recept eredménye (Bűvölés kategória, 8. szint).

### `vasesszencias_paloscsapas_tomus` — Vasesszenciás Páncéltörés Tomus
- **Fájl:** `vasesszencias_paloscsapas_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Tiszta Vasesszenciával írt rúna, mely a legkeményebb páncélt is megtöri. Bűvölő-recept eredménye (Bűvölés kategória, 30. szint).

### `vedelem_tomus` — Védelem Tomus
- **Fájl:** `vedelem_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: borostyánsárga
- **Hangulat / lore:** Az Akadémia rúnatára őrizte évszázadokon át, hogy pajzsként vonja be viselőjét. Bűvölő-recept eredménye (Bűvölés kategória, 18. szint).

### `zuhanascsokkentes_tomus` — Zuhanáscsökkentés Tomus
- **Fájl:** `zuhanascsokkentes_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: bordó
- **Hangulat / lore:** Elfeledett rúnák, melyek könnyűvé teszik a legmagasabb zuhanást is. Bűvölő-recept eredménye (Bűvölés kategória, 16. szint).

## Recept — Bűvölés (tervrajz)

### `javitas_tomus` — Javítás Tomus
- **Fájl:** `javitas_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: izzó narancsvörös
- **Hangulat / lore:** Rúnaírás, mely a tapasztalat erejét a legkopottabb pengébe is visszaönti. Bűvölő-recept eredménye (Bűvölés (tervrajz) kategória, 40. szint).

### `orvenylo_pusztitas_tomus` — Örvénylő Pusztítás Tomus
- **Fájl:** `orvenylo_pusztitas_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: jeges világoskék
- **Hangulat / lore:** Netherit-porral kevert ősi rúna, mely pusztító örvényt zár a fegyverbe. Bűvölő-recept eredménye (Bűvölés (tervrajz) kategória, 45. szint).

### `selyemerintes_tomus` — Selyemérintés Tomus
- **Fájl:** `selyemerintes_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: mélyvörös
- **Hangulat / lore:** Az Akadémia legfinomabb rúnaírása, mely a kéz érintését selyemmé szelídíti. Bűvölő-recept eredménye (Bűvölés (tervrajz) kategória, 48. szint).

### `szornymag_talizman` — Szörnymag Talizmán
- **Fájl:** `szornymag_talizman.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** derengő varázskönyv, misztikus derengéssel
- **Színvilág:** mélyvörös; akcent: szénfekete-szürke
- **Hangulat / lore:** Egy Szörny Mag lüktet a talizmán szívében, sötét energiát kölcsönözve viselőjének. Bűvölő-recept eredménye (Bűvölés (tervrajz) kategória, 40. szint).

## Recept — Eszköz

### `celkereszt_szamszerij` — Céhmesteri Számszeríj
- **Fájl:** `celkereszt_szamszerij.png` &nbsp;|&nbsp; **Alap-item:** `CROSSBOW`
- **Ábrázolás:** számszeríj
- **Színvilág:** meleg fabarna; akcent: világos acélszürke
- **Hangulat / lore:** Favágó-recept eredménye (Eszköz kategória, 44. szint).

### `csillekerek` — Megkent Csille
- **Fájl:** `csillekerek.png` &nbsp;|&nbsp; **Alap-item:** `MINECART`
- **Ábrázolás:** bányász-csille, a névhez illő tematikus díszítéssel
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** A tárnák olajozott vasparipája — nyikorgás nélkül fut a sínen. Bányász-recept eredménye (Eszköz kategória, 14. szint).

### `feszitett_szaru_ij` — Feszített Szaruíj
- **Fájl:** `feszitett_szaru_ij.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral
- **Színvilág:** meleg fabarna; akcent: törtfehér
- **Hangulat / lore:** Favágó-recept eredménye (Eszköz kategória, 41. szint).

### `mefonott_pajzs` — Erdőjáró Pajzs
- **Fájl:** `mefonott_pajzs.png` &nbsp;|&nbsp; **Alap-item:** `SHIELD`
- **Ábrázolás:** címerpajzs
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Favágó-recept eredménye (Eszköz kategória, 36. szint).

### `melysegi_tajolo` — Tárnatájoló
- **Fájl:** `melysegi_tajolo.png` &nbsp;|&nbsp; **Alap-item:** `COMPASS`
- **Ábrázolás:** iránytű, a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: mélyvörös
- **Hangulat / lore:** A tű nem északra mutat — a legközelebbi tárnára. Bányász-recept eredménye (Eszköz kategória, 23. szint).

### `melyvizi_horog` — Mélyvízi Horogsor
- **Fájl:** `melyvizi_horog.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** fém horgászhorog
- **Színvilág:** világos acélszürke; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Halász-recept eredménye (Eszköz kategória, 18. szint).

### `mestermuves_bot` — Mesterhorgász Botja
- **Fájl:** `mestermuves_bot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Azt beszélik, ez a bot már akkor ránt, mielőtt a hal harapna. Halász-recept eredménye (Eszköz kategória, 40. szint).

### `pajzsdudor` — Dudoros Hadipajzs
- **Fájl:** `pajzsdudor.png` &nbsp;|&nbsp; **Alap-item:** `SHIELD`
- **Ábrázolás:** címerpajzs
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Kovács-recept eredménye (Eszköz kategória, 31. szint).

### `tavcso` — Bányamérnöki Távcső
- **Fájl:** `tavcso.png` &nbsp;|&nbsp; **Alap-item:** `SPYGLASS`
- **Ábrázolás:** kihúzható távcső, a névhez illő tematikus díszítéssel
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: világító cián
- **Hangulat / lore:** A bányamérnök szeme: meglátja a repedést, mielőtt omlana. Bányász-recept eredménye (Eszköz kategória, 27. szint).

### `uszokeszlet` — Úszókészlet
- **Fájl:** `uszokeszlet.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** piros-fehér horgászúszó
- **Színvilág:** meleg fabarna; akcent: törtfehér
- **Hangulat / lore:** Halász-recept eredménye (Eszköz kategória, 8. szint).

### `vadaszij` — Vadászíj
- **Fájl:** `vadaszij.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral
- **Színvilág:** meleg fabarna; akcent: törtfehér
- **Hangulat / lore:** Favágó-recept eredménye (Eszköz kategória, 14. szint).

### `viharjelzo_boja` — Viharjelző Bója
- **Fájl:** `viharjelzo_boja.png` &nbsp;|&nbsp; **Alap-item:** `LANTERN`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** A kikötők őrszeme: ha pislog, vihar közeleg a Bokic felől. Halász-recept eredménye (Eszköz kategória, 32. szint).

## Recept — Fagyott királyság (konyha)

### `fagyasztott_pisztrang` — Fagyasztott Tavi Pisztráng
- **Fájl:** `fagyasztott_pisztrang.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_SALMON`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** A glatziendorfi tavak jégbe zárt kincse — a Fagy népe ezen él. Fogyasztva rövid felszívódás-pajzsot ad. Szakács-recept eredménye (Fagyott Királyság (konyha) kategória, 25. szint).

## Recept — Fagyott királyság (tervrajz)

### `glatziendorfi_jegtoro` — Glatziendorfi Jégtörő
- **Fájl:** `glatziendorfi_jegtoro.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_AXE`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** A kikötő jégtörőinek mintájára kovácsolt csatabárd — ami egyszer megdermedt, azt ez a fejsze megtöri. Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 46. szint).

### `glatziendorfi_jegvert` — Glatziendorfi Jégvért
- **Fájl:** `glatziendorfi_jegvert.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_CHESTPLATE`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Oly nehéz és fagyos, hogy viselőjét sziklaként rögzíti; az ellenség fegyverei tompán koppannak rajta. Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 50. szint).

### `jegsarkany_kantar` — Jégsárkány-Kantár
- **Fájl:** `jegsarkany_kantar.png` &nbsp;|&nbsp; **Alap-item:** `SADDLE`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Kemény bőr és sötét mágia; csak ezzel tartható kordában egy vad sárkány. Jobb katt egy hátason: tartós gyorsítás. Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 40. szint).

### `kallan_szeletelo` — Kallan Szeletelője
- **Fájl:** `kallan_szeletelo.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Kallan első pikkelyeiből és ősi fenyőből faragva; a jégsárkányok inaiból font húr a legvastagabb vértet is átvágja. Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 45. szint).

### `miinus_haragja` — V. Miinus Haragja
- **Fájl:** `miinus_haragja.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** súlyos, sötét pengéjű kard, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** A megfagyott király pengéje: minél mélyebb a viselő sebe, annál hidegebben üt vissza. „A tél nem kegyelmez — emlékezik.” Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 50. szint).

### `sarkanycsont_ij` — Sárkánycsont Íj
- **Fájl:** `sarkanycsont_ij.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Kallan elhullott sárkányainak csontjából hajlított íj — nyila átüti a sorfalat. Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 44. szint).

## Recept — Fegyver

### `csontenyves_ijkar` — Csontenyves Íjkar
- **Fájl:** `csontenyves_ijkar.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Csontenyvvel rétegelt, kötéllel erősített íjkar — a Mélység Népe így készítette. Favágó-recept eredménye (Fegyver kategória, 34. szint).

### `gyemant_kard` — Gyémántkard
- **Fájl:** `gyemant_kard.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_SWORD`
- **Ábrázolás:** kard/penge, markolattal
- **Színvilág:** hűvös ezüstszürke; akcent: cserzett bőrbarna
- **Hangulat / lore:** A legkeményebb kő élesíti, a kovácsmesterek keze formálja tökéletes egyensúlyúvá. Kovács-recept eredménye (Fegyver kategória, 22. szint).

### `haromagu_szigony` — Háromágú Szigony
- **Fájl:** `haromagu_szigony.png` &nbsp;|&nbsp; **Alap-item:** `TRIDENT`
- **Ábrázolás:** háromágú szigony, a névhez illő tematikus díszítéssel
- **Színvilág:** világító cián; akcent: középkék
- **Hangulat / lore:** A Bokic mélyéről merített erő, mit csak a víz szellemei adnak kölcsön. Kovács-recept eredménye (Fegyver kategória, 30. szint).

### `vaskard` — Vaskard
- **Fájl:** `vaskard.png` &nbsp;|&nbsp; **Alap-item:** `IRON_SWORD`
- **Ábrázolás:** kard/penge, markolattal
- **Színvilág:** hűvös ezüstszürke; akcent: cserzett bőrbarna
- **Hangulat / lore:** Egyszerű, de megbízható penge — a caldesterai kovácsműhelyek alapja. Kovács-recept eredménye (Fegyver kategória, 8. szint).

## Recept — Fegyver (tervrajz)

### `ereklye_penge` — Villámszilánk Pengéje
- **Fájl:** `ereklye_penge.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** finomszőrű régész-ecset
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: krémszínű pergamen
- **Hangulat / lore:** Fekete Villám Szilánkkal kovácsolva — a Hu. 698-as ég hasadékának ereje él benne. Kovács-recept eredménye (Fegyver (tervrajz) kategória, 50. szint).

### `melytengeri_ereklyeszigony` — Mélytengeri Villámszigony
- **Fájl:** `melytengeri_ereklyeszigony.png` &nbsp;|&nbsp; **Alap-item:** `TRIDENT`
- **Ábrázolás:** háromágú szigony, a névhez illő tematikus díszítéssel
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: krémszínű pergamen
- **Hangulat / lore:** Fekete Villám Szilánk pulzál a szigony hegyén, mit a mélytenger sötétjéből hoztak fel. Halász-recept eredménye (Fegyver (tervrajz) kategória, 47. szint).

### `netherit_kard` — Netherit Pallos
- **Fájl:** `netherit_kard.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** kard/penge, markolattal
- **Színvilág:** hűvös ezüstszürke; akcent: cserzett bőrbarna
- **Hangulat / lore:** A Kárhozat Kapuja körül gyűjtött netherit adja e pallos törhetetlen élét. Kovács-recept eredménye (Fegyver (tervrajz) kategória, 42. szint).

### `runakovacsolt_penge` — Rúnakovácsolt Penge
- **Fájl:** `runakovacsolt_penge.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** súlyos, sötét pengéjű kard, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Rúnaporral kovácsolt penge, mely az Akadémia elveszett tudását őrzi. Kovács-recept eredménye (Fegyver (tervrajz) kategória, 44. szint).

## Recept — Ital

### `aranyfeny_mezsor` — Aranyfényű Mézsör
- **Fájl:** `aranyfeny_mezsor.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** habos, borostyánszín mézsör fakupában, aranyló buborékokkal, sűrű krémhab-koronával
- **Színvilág:** meleg borostyán; akcent: aranyló hab
- **Hangulat / lore:** Aranyfényű Mézsör — ünnepi kupa; a méz édessége és a nap melege egy kortyban.

### `arnyeklikor` — Árnyéklikőr
- **Fájl:** `arnyeklikor.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** sötét, ibolyafekete likőr karcsú üvegben, halvány türkiz derengéssel
- **Színvilág:** ibolyafekete; akcent: hideg türkiz derengés
- **Hangulat / lore:** Árnyéklikőr — a Kitaszítottak itala; aki issza, egy pillanatra beleolvad az árnyékba.

### `bokic_gyogytea` — Bokic-parti Gyógytea
- **Fájl:** `bokic_gyogytea.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Hajnali szedésű füvek forrázata a Bokic partjáról — átmelegít és tisztán tart. Gyógynövényész-recept eredménye (Ital kategória, 24. szint).

### `caldesterai_gyogytea` — Caldesterai Gyógytea
- **Fájl:** `caldesterai_gyogytea.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** gőzölgő, zöldes gyógytea cserépcsészében, halvány párával, csillanó mézcseppel
- **Színvilág:** gyógyzöld; akcent: borostyán mézcsepp
- **Hangulat / lore:** Caldesterai Gyógytea — a Bokic-mente békés főzete; a vándor legjobb barátja.

### `hamvasztott_kave` — Hamvasztott Kávé
- **Fájl:** `hamvasztott_kave.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** barnásfekete kávé csontszín csészében, sűrű gőzzel, hamvas szürke tajtékkal
- **Színvilág:** barnásfekete; akcent: hamvas szürke
- **Hangulat / lore:** Hamvasztott Kávé — Mortengrad keserű ébresztője; korom az ízében, éjszaka a lelkében.

### `jeghegyi_sor` — Jéghegyi Sör
- **Fájl:** `jeghegyi_sor.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** jégkék, habos sör dérlepte korsóban, ezüst buborékokkal, hideg párával
- **Színvilág:** jégkék; akcent: dérfehér
- **Hangulat / lore:** Jéghegyi Sör — Glatziendorf kocsmáinak jeges kortya; Cryghaliris büszkesége.

### `jegkiraly_parlat` — Jégkirály Párlata
- **Fájl:** `jegkiraly_parlat.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** kristálytiszta párlat karcsú fiolában, jégkék csillanással, ezüst dérkoszorúval
- **Színvilág:** kristálykék; akcent: ezüst dér
- **Hangulat / lore:** Jégkirály Párlata — a fagy uralkodójának pohara; tiszta, hideg, metsző.

### `kofejto_sore` — Kőfejtő Söre
- **Fájl:** `kofejto_sore.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** sötét borostyán sör kőkorsóban, vastag krémhabbal, meleg barna árnyalattal
- **Színvilág:** sötét borostyán; akcent: krémfehér hab
- **Hangulat / lore:** Kőfejtő Söre — a tárnák pora ellen; a bányász estéjének súlyos jutalma.

### `mortengradi_keseru` — Mortengrádi Keserű
- **Fájl:** `mortengradi_keseru.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** sötét gesztenyebarna keserűlikőr csontpohárban, hamvas felszínnel, halvány türkiz derengéssel
- **Színvilág:** gesztenyebarna; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Mortengrádi Keserű — a Kitaszítottak itala; keserű, mint az otthontalanság.

### `parazs_palinka` — Parázs Pálinka
- **Fájl:** `parazs_palinka.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** tüzes, narancsvörös pálinka kis üvegben, izzó parázs-csillanással, forró aranyfénnyel
- **Színvilág:** izzó narancsvörös; akcent: parázs-arany
- **Hangulat / lore:** Parázs Pálinka — Pyralingrad tűzitala; egy korty, és a torkon lefolyik a láng.

### `szentelt_bor` — Szentelt Bor
- **Fájl:** `szentelt_bor.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** mélyvörös, szentelt bor kehelyben, aranyló szent fénnyel, tiszta rubinragyogással
- **Színvilág:** mélyvörös rubin; akcent: aranyló szent fény
- **Hangulat / lore:** Szentelt Bor — a pap áldott kelyhe; Asterlayna fénye csillan a mélyén.

### `tengeresz_rum` — Tengerész Rum
- **Fájl:** `tengeresz_rum.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** sötét borostyánbarna rum zömök palackban, aranyló csillanással, pecséttel
- **Színvilág:** borostyánbarna; akcent: aranyló csillanás
- **Hangulat / lore:** Tengerész Rum — a Bokic-parti kikötők itala; sós szél és hosszú utak íze.

### `viharfi_almabor` — Viharfi Almabor
- **Fájl:** `viharfi_almabor.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** aranysárga almabor buborékos pohárban, halvány zöldes csillanással, viharkék peremfénnyel
- **Színvilág:** aranysárga; akcent: viharkék
- **Hangulat / lore:** Viharfi Almabor — a viharfiak fanyar itala; almáskert és égzengés egy pohárban.

## Recept — Ital (tervrajz)

### `arnyekmereg` — Árnyékméreg
- **Fájl:** `arnyekmereg.png` &nbsp;|&nbsp; **Alap-item:** `SPLASH_POTION`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** mély ibolyalila; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** A Suttogók receptje: Árnyékporból lepárolt méreg, mely elnyeli a fényt és az életet. Alkimista-recept eredménye (Ital (tervrajz) kategória, 38. szint).

### `bajnok_elixir` — Bajnok Elixírje
- **Fájl:** `bajnok_elixir.png` &nbsp;|&nbsp; **Alap-item:** `POTION`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Aetrinita áldása száll benne, hogy a bajnokok sose hátráljanak. Alkimista-recept eredménye (Ital (tervrajz) kategória, 48. szint).

### `ereklye_elixir` — Villámszilánk Elixírje
- **Fájl:** `ereklye_elixir.png` &nbsp;|&nbsp; **Alap-item:** `POTION`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Fekete Villám Szilánk cseppjei oldódtak ebbe az elixírbe, a Hu. 698 emlékeként. Alkimista-recept eredménye (Ital (tervrajz) kategória, 49. szint).

## Recept — Kazamata kulcs

### `csontkripta_kulcsa` — A Csontkripta Kulcsa
- **Fájl:** `csontkripta_kulcsa.png` &nbsp;|&nbsp; **Alap-item:** `TRIAL_KEY`
- **Ábrázolás:** csontból faragott kulcs, koponya-fejjel, megfeketedett ezüst pánttal
- **Színvilág:** csontfehér és hamuszürke; akcent: fakó lélek-kék
- **Hangulat / lore:** Thanaopolis kriptamélyének pecsétje — a holtak csak a hordozóját engedik le.

### `melyseg_kulcsa` — A Mélység Kulcsa
- **Fájl:** `melyseg_kulcsa.png` &nbsp;|&nbsp; **Alap-item:** `TRIAL_KEY`
- **Ábrázolás:** nehéz, ipari vaskulcs fogaskerék-mintás fejjel, rúnavésettel
- **Színvilág:** sötét vasszürke, rozsda-nyomokkal; akcent: halvány rúna-türkiz
- **Hangulat / lore:** a Vasművek Akadémiája elhagyott tárnáinak zárja — kazamata-belépő, belépéskor elfogy.

## Recept — Kitaszítottak (konyha)

### `mortengradi_hamukenyer` — Mortengradi Hamukenyér
- **Fájl:** `mortengradi_hamukenyer.png` &nbsp;|&nbsp; **Alap-item:** `BREAD`
- **Ábrázolás:** frissen sült cipó/vekni
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** A Kitaszítottak kenyere: hamuval sütik, hogy a Királynő szolgái ne érezzék az élet szagát. Fogyasztva rövid éjjellátást ad. A Kitaszítottaknak nincs honvágyuk — nincs otthon. Szakács-recept eredménye (Kitaszítottak (konyha) kategória, 30. szint).

## Recept — Különleges

### `gyongyhaz_talizman` — Gyöngyház Talizmán
- **Fájl:** `gyongyhaz_talizman.png` &nbsp;|&nbsp; **Alap-item:** `NAUTILUS_SHELL`
- **Ábrázolás:** derengő tintazsák / fénylő gyöngy
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: égszínkék
- **Hangulat / lore:** Hét pikkely, hét szín, egy kagylóházban — a Bokic halászainak szerencsehozója. Halász-recept eredménye (Különleges kategória, 32. szint).

### `pecsetes_szerzodes` — Pecsétes Szerződés
- **Fájl:** `pecsetes_szerzodes.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** pecsétnyomó viaszpecséttel
- **Színvilág:** bordó; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Bankárszövetség viaszával hitelesített üres szerződés — két fél, egy pecsét, és nincs többé vita. Bűvölő-recept eredménye (Különleges kategória, 30. szint).

### `sarkfeny_prizma` — Sarkfény-prizma
- **Fájl:** `sarkfeny_prizma.png` &nbsp;|&nbsp; **Alap-item:** `SEA_LANTERN`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** sötét méregzöld; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Cseppkőbe zárt északi fény — a szoba, ahol áll, sosem lesz igazán sötét. Bűvölő-recept eredménye (Különleges kategória, 38. szint).

### `viharuveg_lampas` — Viharüveg Lámpás
- **Fájl:** `viharuveg_lampas.png` &nbsp;|&nbsp; **Alap-item:** `LANTERN`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Viharkvarc-szilánk ég az üveg mögött — a fénye nem alszik ki, mert a vihar sosem fárad el. Bűvölő-recept eredménye (Különleges kategória, 28. szint).

## Recept — Legendás (tervrajz)

### `eleftheria_fatyla` — Eleftheria Fátyla
- **Fájl:** `eleftheria_fatyla.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_CHESTPLATE`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Lélekhamuból szőtt, könnyekkel hímzett vért — a Néma Királynő gyásza, páncéllá kovácsolva. Viselni kegy. És teher. Kovács-recept eredménye (Legendás (tervrajz) kategória, 50. szint).

### `melysegi_korona` — A Mélység Népe Koronája
- **Fájl:** `melysegi_korona.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_HELMET`
- **Ábrázolás:** sötét, súlyos sisak, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Borostyánba dermedt idő és néma kristály egy koronában — az utolsó kovácsmű, amit a Mélység Népe hátrahagyott. Kovács-recept eredménye (Legendás (tervrajz) kategória, 50. szint).

### `viharjaro_csizma` — Viharjáró Csizma
- **Fájl:** `viharjaro_csizma.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_BOOTS`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Aki viseli, a vihar előtt jár egy lépéssel — és a vihar ezt tiszteli. Kovács-recept eredménye (Legendás (tervrajz) kategória, 48. szint).

## Recept — Lángoló birodalom (konyha)

### `fonixtojas_rantotta` — Fűszeres Főnixtojás-Rántotta
- **Fájl:** `fonixtojas_rantotta.png` &nbsp;|&nbsp; **Alap-item:** `PUMPKIN_PIE`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Vérszavanna reggelije: tojás, parázs és bátorság. Fogyasztva rövid tűz- ellenállást ad. Szakács-recept eredménye (Lángoló Birodalom (konyha) kategória, 25. szint).

## Recept — Lángoló birodalom (tervrajz)

### `fonix_tollkopeny` — Főnix-Tollköpeny
- **Fájl:** `fonix_tollkopeny.png` &nbsp;|&nbsp; **Alap-item:** `LEATHER_CHESTPLATE`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** I. Zhoris lángmadarainak tollaiból szőve; óv a hőségtől és a láva haragjától. Kovács-recept eredménye (Lángoló Birodalom (tervrajz) kategória, 40. szint).

### `pyralingradi_tuzkopo` — Pyralingradi Tűzköpő
- **Fájl:** `pyralingradi_tuzkopo.png` &nbsp;|&nbsp; **Alap-item:** `CROSSBOW`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Soleil papjai áldották meg; lövedékei a sivatagi vihar sebességével csapnak le. Kovács-recept eredménye (Lángoló Birodalom (tervrajz) kategória, 45. szint).

### `verszavanna_agyara` — A Vérszavanna Agyara
- **Fájl:** `verszavanna_agyara.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** súlyos, sötét pengéjű kard, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Kovácsolásakor a lángok fekete füstöt okádtak; baltával a másik kézben emberfeletti erőt ád. Kovács-recept eredménye (Lángoló Birodalom (tervrajz) kategória, 50. szint).

## Recept — Menedék (konyha)

### `kakaobabos_sutemeny` — Tiltott Kakaóbabos Sütemény
- **Fájl:** `kakaobabos_sutemeny.png` &nbsp;|&nbsp; **Alap-item:** `COOKIE`
- **Ábrázolás:** díszített torta
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: rózsás pír
- **Hangulat / lore:** Caldestera cukrászai Asterlayna Gyümölcsének hívják — a Bankárszövetség hivatalosan tiltja. Fogyasztva… robban egy kicsit. Szakács-recept eredménye (Menedék (konyha) kategória, 35. szint).

## Recept — Menedék (tervrajz)

### `bokic_horgaszbot` — Bokic-menti Horgászbot
- **Fájl:** `bokic_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** A Bokic halászai szerint a folyó annak ad kétszer, aki ismeri a vize énekét. Halász-recept eredménye (Menedék (tervrajz) kategória, 40. szint).

### `smaragdko_bankbetet` — Smaragdkő Bankbetét
- **Fájl:** `smaragdko_bankbetet.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** élénk levélzöld; akcent: hűvös ezüstszürke
- **Hangulat / lore:** A Bankárszövetség pecsétes betétjegye — jobb-kattal beváltható Creutzérre. Bűvölő-recept eredménye (Menedék (tervrajz) kategória, 35. szint).

### `szellemszarvas_bubaj` — Szellemszarvas-Bűbáj
- **Fájl:** `szellemszarvas_bubaj.png` &nbsp;|&nbsp; **Alap-item:** `RABBIT_FOOT`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Arkynn ligeteinek ajándéka: hívó bűbáj, amely a ködből szólítja elő a Szellemszarvast. Jobb-katt: hátas-hívás (nem fogy el). Gyógynövényész-recept eredménye (Menedék (tervrajz) kategória, 45. szint).

### `vasmuvek_csakanya` — Vasművek Akadémiájának Csákánya
- **Fájl:** `vasmuvek_csakanya.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_PICKAXE`
- **Ábrázolás:** csákány
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** A ryanorai Vasművek mestervizsga-darabja; éle megérzi az érc rejtett teléreit. Bányász-recept eredménye (Menedék (tervrajz) kategória, 45. szint).

## Recept — Páncél

### `arany_lopancel` — Arany Lópáncél
- **Fájl:** `arany_lopancel.png` &nbsp;|&nbsp; **Alap-item:** `GOLDEN_HORSE_ARMOR`
- **Ábrázolás:** öntött fémrúd (ingot-forma)
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: borostyánsárga
- **Hangulat / lore:** Aranyfüst-lemezzel futtatva — a parádék dísze. Kovács-recept eredménye (Páncél kategória, 27. szint).

### `bastya_pajzs_recept` — Bástya Pajzs
- **Fájl:** `bastya_pajzs_recept.png` &nbsp;|&nbsp; **Alap-item:** `SHIELD`
- **Ábrázolás:** címerpajzs
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Vasborítás és smaragd-veret — a kovácscéhek bástyaként állítják a viselő elé. Kovács-recept eredménye (Páncél kategória, 15. szint).

### `esszencialt_vasvert` — Esszenciált Vasvért
- **Fájl:** `esszencialt_vasvert.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_CHESTPLATE`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Tiszta Vasesszenciával itatott lemezek, melyek szinte élővé teszik a páncélt. Kovács-recept eredménye (Páncél kategória, 35. szint).

### `gyemant_lopancel` — Gyémánt Lópáncél
- **Fájl:** `gyemant_lopancel.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_HORSE_ARMOR`
- **Ábrázolás:** nyereg / lószerszám
- **Színvilág:** világos acélszürke; akcent: cserzett bőrbarna
- **Hangulat / lore:** Ritkább, mint a jó lovas. A céh büszkén veri rá a pecsétjét. Kovács-recept eredménye (Páncél kategória, 37. szint).

### `gyemant_mellvert` — Gyémánt Mellvért
- **Fájl:** `gyemant_mellvert.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_CHESTPLATE`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** A kovácscéhek legdrágább rendelése: tömör gyémánt, mely visszaveri a pengét. Kovács-recept eredménye (Páncél kategória, 26. szint).

### `gyemant_sisak` — Gyémántsisak
- **Fájl:** `gyemant_sisak.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Csiszolt gyémántlemezek, melyeket csak a Vasművek Akadémiájának mesterei értenek. Kovács-recept eredménye (Páncél kategória, 24. szint).

### `halaszkalap` — Halászkalap
- **Fájl:** `halaszkalap.png` &nbsp;|&nbsp; **Alap-item:** `LEATHER_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Halász-recept eredménye (Páncél kategória, 15. szint).

### `lancing` — Kovácsolt Láncing
- **Fájl:** `lancing.png` &nbsp;|&nbsp; **Alap-item:** `CHAINMAIL_CHESTPLATE`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Kovács-recept eredménye (Páncél kategória, 11. szint).

### `lancnadrag` — Kovácsolt Láncnadrág
- **Fájl:** `lancnadrag.png` &nbsp;|&nbsp; **Alap-item:** `CHAINMAIL_LEGGINGS`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Kovács-recept eredménye (Páncél kategória, 13. szint).

### `pancelozott_sisakrostely` — Rostélyos Csatasisak
- **Fájl:** `pancelozott_sisakrostely.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Kovács-recept eredménye (Páncél kategória, 43. szint).

### `rezvertezet_lablemez` — Rézvértezet Lábvért
- **Fájl:** `rezvertezet_lablemez.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_LEGGINGS`
- **Ábrázolás:** öntött fémrúd (ingot-forma)
- **Színvilág:** világos acélszürke; akcent: sötétebb, kékes acél
- **Hangulat / lore:** Rezgő Rézötvözettel erősített lábvért, mely sosem veszti fényét a csatában. Kovács-recept eredménye (Páncél kategória, 33. szint).

### `sarkanycsont_pajzs` — Sárkánycsont Pajzs
- **Fájl:** `sarkanycsont_pajzs.png` &nbsp;|&nbsp; **Alap-item:** `SHIELD`
- **Ábrázolás:** címerpajzs, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Sárkánycsont-lemezekkel erősített pajzs — ami nekicsapódik, megdermed egy szívverésre. Kovács-recept eredménye (Páncél kategória, 40. szint).

### `teknos_sisak` — Teknőspáncél-sisak
- **Fájl:** `teknos_sisak.png` &nbsp;|&nbsp; **Alap-item:** `TURTLE_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Halász-recept eredménye (Páncél kategória, 30. szint).

### `vadbor_pancel` — Vadbőr Vért
- **Fájl:** `vadbor_pancel.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_LEGGINGS`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Egy káoszkori fenevad esszenciája ivódott bele, vadságot kölcsönözve. Kovács-recept eredménye (Páncél kategória, 28. szint).

### `vadolo_csizma` — Vadölő Csizma
- **Fájl:** `vadolo_csizma.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_BOOTS`
- **Ábrázolás:** pár csizma
- **Színvilág:** cserzett bőrbarna; akcent: világos acélszürke
- **Hangulat / lore:** Vad Esszenciával itatott talp, mely néma léptekkel követi a fenevadak nyomát. Kovács-recept eredménye (Páncél kategória, 32. szint).

### `vas_csizma` — Vascsizma
- **Fájl:** `vas_csizma.png` &nbsp;|&nbsp; **Alap-item:** `IRON_BOOTS`
- **Ábrázolás:** pár csizma
- **Színvilág:** cserzett bőrbarna; akcent: világos acélszürke
- **Hangulat / lore:** Szilárd talp és pántok, hogy a viselője bármely tárnában biztosan álljon lábán. Kovács-recept eredménye (Páncél kategória, 10. szint).

### `vas_lablemez` — Vas Lábvért
- **Fájl:** `vas_lablemez.png` &nbsp;|&nbsp; **Alap-item:** `IRON_LEGGINGS`
- **Ábrázolás:** öntött fémrúd (ingot-forma)
- **Színvilág:** világos acélszürke; akcent: sötétebb, kékes acél
- **Hangulat / lore:** A Vasművek Akadémiájának szabott mintája, mely minden csatában szolgálatot tesz. Kovács-recept eredménye (Páncél kategória, 12. szint).

### `vas_lopancel` — Vas Lópáncél
- **Fájl:** `vas_lopancel.png` &nbsp;|&nbsp; **Alap-item:** `IRON_HORSE_ARMOR`
- **Ábrázolás:** nyereg / lószerszám
- **Színvilág:** világos acélszürke; akcent: cserzett bőrbarna
- **Hangulat / lore:** A céh kovácsainak munkája: a ló is katonának érzi magát benne. Kovács-recept eredménye (Páncél kategória, 17. szint).

### `vas_sisak` — Vassisak
- **Fájl:** `vas_sisak.png` &nbsp;|&nbsp; **Alap-item:** `IRON_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Vastag vaslemez, mely a kovácscéhek műhelyeiben nyeri el végső formáját. Kovács-recept eredménye (Páncél kategória, 10. szint).

### `vasesszencias_pajzs` — Vasesszenciás Pajzs
- **Fájl:** `vasesszencias_pajzs.png` &nbsp;|&nbsp; **Alap-item:** `SHIELD`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Tiszta Vasesszenciával átitatott vaslemez, mely szinte magától állja a csapásokat. Kovács-recept eredménye (Páncél kategória, 30. szint).

### `vizallo_csizma` — Halászcsizma
- **Fájl:** `vizallo_csizma.png` &nbsp;|&nbsp; **Alap-item:** `LEATHER_BOOTS`
- **Ábrázolás:** pár csizma
- **Színvilág:** cserzett bőrbarna; akcent: világos acélszürke
- **Hangulat / lore:** Halász-recept eredménye (Páncél kategória, 16. szint).

## Recept — Páncél (tervrajz)

### `ereklyeszilankos_banyasisak` — Villámszilánkos Bányászsisak
- **Fájl:** `ereklyeszilankos_banyasisak.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_HELMET`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** Fekete Villám Szilánk izzik a sisak mélyén, megvilágítva a legsötétebb járatokat. Bányász-recept eredménye (Páncél (tervrajz) kategória, 50. szint).

### `netherit_sisak` — Netherit Csatasisak
- **Fájl:** `netherit_sisak.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Nether-portál sötét fémjéből kovácsolva, hogy viselője a csata hevében is álljon. Kovács-recept eredménye (Páncél (tervrajz) kategória, 45. szint).

### `sarkanyvert_recept` — Sárkányvért
- **Fájl:** `sarkanyvert_recept.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_CHESTPLATE`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Kallan jégsárkányainak pikkelye ihlette hadi vért, a kovácscéhek büszkesége. Kovács-recept eredménye (Páncél (tervrajz) kategória, 40. szint).

### `szornyvert_mellveny` — Szörnyvért Mellvény
- **Fájl:** `szornyvert_mellveny.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_CHESTPLATE`
- **Ábrázolás:** sötét, súlyos mellvért, misztikus derengéssel
- **Színvilág:** mélyvörös; akcent: szénfekete-szürke
- **Hangulat / lore:** Egy Szörny Mag sötét ereje dobog e mellvény netherit-lemezei alatt. Kovács-recept eredménye (Páncél (tervrajz) kategória, 46. szint).

## Recept — Ritkaság

### `bokic_aldasa` — A Bokic Áldása
- **Fájl:** `bokic_aldasa.png` &nbsp;|&nbsp; **Alap-item:** `TRIDENT`
- **Ábrázolás:** háromágú szigony, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** A folyó egyszer mindent visszaad. A halászcéhek legendás szigonya. Halász-recept eredménye (Ritkaság kategória, 50. szint).

### `bolcsek_kove` — A Bölcsek Köve
- **Fájl:** `bolcsek_kove.png` &nbsp;|&nbsp; **Alap-item:** `EXPERIENCE_BOTTLE`
- **Ábrázolás:** zöld derengésű palack, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Nem arannyá változtat — bölccsé. Az alkimista-céh végső titka. Alkimista-recept eredménye (Ritkaság kategória, 50. szint).

### `borostyan_lampa` — Borostyánfényű Lámpás
- **Fájl:** `borostyan_lampa.png` &nbsp;|&nbsp; **Alap-item:** `LANTERN`
- **Ábrázolás:** kerek pite / sütemény
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** A mélység gyantája ég benne — fénye nyugtatja a tárnák szellemeit. Bányász-recept eredménye (Ritkaság kategória, 39. szint).

### `cehmester_ulloje` — A Céhmester Üllője
- **Fájl:** `cehmester_ulloje.png` &nbsp;|&nbsp; **Alap-item:** `ANVIL`
- **Ábrázolás:** kovácsüllő, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Ezen az üllőn nem görbül el semmi. A kovácscéhek büszkesége. Kovács-recept eredménye (Ritkaság kategória, 50. szint).

### `csendulo_harang` — Csendülő Harang
- **Fájl:** `csendulo_harang.png` &nbsp;|&nbsp; **Alap-item:** `BELL`
- **Ábrázolás:** öntött harang, misztikus derengéssel
- **Színvilág:** éjkék; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Hangja messzire száll a fagyban. A Suttogók megállnak, ha megkondul. Bűvölő-recept eredménye (Ritkaság kategória, 27. szint).

### `emlekek_konyve` — Emlékek Könyve
- **Fájl:** `emlekek_konyve.png` &nbsp;|&nbsp; **Alap-item:** `WRITTEN_BOOK`
- **Ábrázolás:** megírt, veretes kódex, a névhez illő tematikus díszítéssel
- **Színvilág:** királylila; akcent: gyöngyházas, rózsás fehér
- **Hangulat / lore:** Üres lapjain néha idegen sorok jelennek meg — más korokból. Bűvölő-recept eredménye (Ritkaság kategória, 37. szint).

### `erdo_szive_totem` — Az Erdő Szíve
- **Fájl:** `erdo_szive_totem.png` &nbsp;|&nbsp; **Alap-item:** `TOTEM_OF_UNDYING`
- **Ábrázolás:** faragott totemfigura, a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: élénk levélzöld
- **Hangulat / lore:** A favágócéhek legendája: az erdő egyszer visszaadja, amit elvettél tőle. Favágó-recept eredménye (Ritkaság kategória, 50. szint).

### `erdok_kurtje` — Erdők Kürtje
- **Fájl:** `erdok_kurtje.png` &nbsp;|&nbsp; **Alap-item:** `GOAT_HORN`
- **Ábrázolás:** ívelt kürt, a névhez illő tematikus díszítéssel
- **Színvilág:** törtfehér csontszín; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Mélyen zengő hang: a fák válaszolnak rá. Aki hallja, hazatalál. Favágó-recept eredménye (Ritkaság kategória, 47. szint).

### `felepules_iranytuje` — Felépülés Iránytűje
- **Fájl:** `felepules_iranytuje.png` &nbsp;|&nbsp; **Alap-item:** `RECOVERY_COMPASS`
- **Ábrázolás:** sötét iránytű derengő tűvel, a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: mélyvörös
- **Hangulat / lore:** Oda vezet vissza, ahol utoljára minden elveszett. Bűvölő-recept eredménye (Ritkaság kategória, 39. szint).

### `jegvirag_koszoru` — Jégvirág-koszorú
- **Fájl:** `jegvirag_koszoru.png` &nbsp;|&nbsp; **Alap-item:** `BLUE_ORCHID`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Cryghaliris kertjeinek dísze — nem hervad el soha. Gyógynövényész-recept eredménye (Ritkaság kategória, 34. szint).

### `kristaly_katalizator` — Kristály-katalizátor
- **Fájl:** `kristaly_katalizator.png` &nbsp;|&nbsp; **Alap-item:** `END_CRYSTAL`
- **Ábrázolás:** lebegő kristály keretben, a névhez illő tematikus díszítéssel
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Néma kristály szívvel ver — ne nézz bele túl sokáig. Alkimista-recept eredménye (Ritkaság kategória, 49. szint).

### `melyseg_szive` — A Mélység Szíve
- **Fájl:** `melyseg_szive.png` &nbsp;|&nbsp; **Alap-item:** `HEART_OF_THE_SEA`
- **Ábrázolás:** sötétkék, erezett szív-drágakő, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** A legmélyebb tárna alján dobog valami. A bányászcéh legendás mesterműve. Bányász-recept eredménye (Ritkaság kategória, 50. szint).

### `oceanjaro_terkep` — Óceánjáró Térképe
- **Fájl:** `oceanjaro_terkep.png` &nbsp;|&nbsp; **Alap-item:** `MAP`
- **Ábrázolás:** kiterített térkép útvonallal és jelöléssel
- **Színvilág:** krémszínű pergamen; akcent: középkék
- **Hangulat / lore:** Olyan zátonyokat is jelöl, amiket még senki sem látott — még. Halász-recept eredménye (Ritkaság kategória, 48. szint).

### `orok_viragzas` — Örök Virágzás Csokra
- **Fájl:** `orok_viragzas.png` &nbsp;|&nbsp; **Alap-item:** `PEONY`
- **Ábrázolás:** virág / virágcsokor
- **Színvilág:** rózsás pír; akcent: élénk levélzöld
- **Hangulat / lore:** Ryanora mezőinek emléke — a csokor sosem hullajtja szirmát. Gyógynövényész-recept eredménye (Ritkaság kategória, 48. szint).

### `osi_ereklye_kiemeles` — Ereklye-kiemelő Készlet
- **Fájl:** `osi_ereklye_kiemeles.png` &nbsp;|&nbsp; **Alap-item:** `BRUSH`
- **Ábrázolás:** finomszőrű ecset, a névhez illő tematikus díszítéssel
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: krémszínű pergamen
- **Hangulat / lore:** Puha ecset, acél türelem — a múlt nem szereti a sietséget. Bányász-recept eredménye (Ritkaság kategória, 48. szint).

### `totem_ujraelesztes` — Újraélesztett Totem
- **Fájl:** `totem_ujraelesztes.png` &nbsp;|&nbsp; **Alap-item:** `TOTEM_OF_UNDYING`
- **Ábrázolás:** faragott totemfigura, a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: élénk levélzöld
- **Hangulat / lore:** Lélekhamuval újratöltve. Egyszer még visszaránt a peremről. Alkimista-recept eredménye (Ritkaság kategória, 48. szint).

### `vandorbot` — Vándorbot
- **Fájl:** `vandorbot.png` &nbsp;|&nbsp; **Alap-item:** `STICK`
- **Ábrázolás:** pálca/bot, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Egyszerű bot, ezer mérföld emléke. A vándor sosem hagyja el. Favágó-recept eredménye (Ritkaság kategória, 33. szint).

### `vasfa_ij` — Vasfa Íj
- **Fájl:** `vasfa_ij.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral
- **Színvilág:** meleg fabarna; akcent: törtfehér
- **Hangulat / lore:** A vasfa nem hajlik — a mester keze alatt mégis. Favágó-recept eredménye (Ritkaság kategória, 49. szint).

### `vegtelen_kodex` — A Végtelen Kódex
- **Fájl:** `vegtelen_kodex.png` &nbsp;|&nbsp; **Alap-item:** `WRITTEN_BOOK`
- **Ábrázolás:** megírt, veretes kódex, a névhez illő tematikus díszítéssel
- **Színvilág:** királylila; akcent: mélyvörös
- **Hangulat / lore:** Minden lapja egy-egy elfeledett név. A bűvölőcéhek legféltettebb kincse. Bűvölő-recept eredménye (Ritkaság kategória, 50. szint).

### `vezetokurt` — Mélység Vezérkürtje
- **Fájl:** `vezetokurt.png` &nbsp;|&nbsp; **Alap-item:** `CONDUIT`
- **Ábrázolás:** tengeri vezérlőmag (conduit), a névhez illő tematikus díszítéssel
- **Színvilág:** törtfehér csontszín; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A mélység válaszol, ha megszólal. A halászcéhek őrzik a titkát. Halász-recept eredménye (Ritkaság kategória, 46. szint).

### `vihar_palack` — Palackozott Vihar
- **Fájl:** `vihar_palack.png` &nbsp;|&nbsp; **Alap-item:** `WIND_CHARGE`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Egy marék szél, üvegbe zárva. Óvatosan a dugóval! Alkimista-recept eredménye (Ritkaság kategória, 46. szint).

### `vilagfa_magja` — A Világfa Magja
- **Fájl:** `vilagfa_magja.png` &nbsp;|&nbsp; **Alap-item:** `OAK_SAPLING`
- **Ábrázolás:** fiatal csemete/hajtás
- **Színvilág:** élénk levélzöld; akcent: földbarna
- **Hangulat / lore:** Azt mondják, az Első Fa magja. Ültesd el, és figyeld, mi nő belőle. Gyógynövényész-recept eredménye (Ritkaság kategória, 50. szint).

### `wither_rozsa_oltvany` — Fonnyadt Rózsa-oltvány
- **Fájl:** `wither_rozsa_oltvany.png` &nbsp;|&nbsp; **Alap-item:** `WITHER_ROSE`
- **Ábrázolás:** fiatal csemete/hajtás
- **Színvilág:** élénk levélzöld; akcent: földbarna
- **Hangulat / lore:** A Kitaszítottak kertjének virága. Aki megszagolja, megborzong. Gyógynövényész-recept eredménye (Ritkaság kategória, 44. szint).

## Recept — Rúnaírnok (tervrajz)

### `arnyuzo_tekercs` — Árnyűző tekercs
- **Fájl:** `arnyuzo_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** feltekert pergamen türkiz lich-fény rúnákkal, elűzött árnyalak-glyphfel
- **Színvilág:** pergamen-fehér; akcent: hideg türkiz lich-fény
- **Hangulat / lore:** Árnyűző tekercs — rúnaírnok műve; a lapon a rúna elűzi, ami az árnyból bújik elő.

### `ej_fatyol_tekercs` — Éj-fátyol tekercs
- **Fájl:** `ej_fatyol_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** feltekert pergamen éjfekete-lila fátyol-rúnákkal, csillagköd-glyphfel
- **Színvilág:** éjfekete-lila; akcent: hideg türkiz peremfény
- **Hangulat / lore:** Éj-fátyol tekercs — a Kitaszítottak rúnaírnokának fátyla; aki hordja, beleolvad az éjbe.

### `kaosz_zabla_tekercs` — Káosz-zabla tekercs
- **Fájl:** `kaosz_zabla_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** feltekert pergamen kaotikus lila-türkiz zabla-rúnával, örvénylő káosz-glyphfel
- **Színvilág:** kaotikus lila-türkiz; akcent: szikrázó peremfény
- **Hangulat / lore:** Káosz-zabla tekercs — a megzabolázott káosz rúnája; a rend és az őrület határán ír.

### `meregfojto_tekercs` — Méregfojtó tekercs
- **Fájl:** `meregfojto_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** feltekert pergamen mérgeszöld méregcsepp-rúnákkal, fojtó indafonat-glyphfel
- **Színvilág:** mérgeszöld; akcent: savas ragyogás
- **Hangulat / lore:** Méregfojtó tekercs — a rúnaírnok ellenszere; a lapra vésett rúna elfojtja a mérget.

### `runavert_tekercs` — Rúnavért-tekercs
- **Fájl:** `runavert_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** félig kigördült pergamentekercs írássorokkal
- **Színvilág:** krémszínű pergamen; akcent: királylila
- **Hangulat / lore:** Caldestera rúnaírnokainak védőírása — a bőrbe rótt rúna elissza a mágiát. Üllőn vihető páncélra. Bűvölő-recept eredménye (Rúnaírnok (tervrajz) kategória, 40. szint).

### `viharfogo_tekercs` — Viharfogó tekercs
- **Fájl:** `viharfogo_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** feltekert pergamen viharkék villám-rúnákkal, cikázó égi glyphfel
- **Színvilág:** viharkék; akcent: elektromos peremragyogás
- **Hangulat / lore:** Viharfogó tekercs — a vihart papírra szelídítő rúna; a villám a lap fölött cikázik.

## Recept — Szerszám

### `egyszeru_horgaszbot` — Egyszerű Horgászbot
- **Fájl:** `egyszeru_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Egyszerű bot a Bokic partjáról, de a víz szellemei így is figyelnek rá. Halász-recept eredménye (Szerszám kategória, 5. szint).

### `gyemant_fejsze` — Gyémántfejsze
- **Fájl:** `gyemant_fejsze.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_AXE`
- **Ábrázolás:** fejsze/balta
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** Csiszolt gyémántél, mely a Vasművek Akadémiájának büszkesége az erdőkben. Favágó-recept eredménye (Szerszám kategória, 36. szint).

### `kovilta_fejsze` — Kővésett Fejsze
- **Fájl:** `kovilta_fejsze.png` &nbsp;|&nbsp; **Alap-item:** `STONE_AXE`
- **Ábrázolás:** fejsze/balta
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** Caldestera kovácsinasainak első próbája: durva kő, de biztos kézzel faragva. Favágó-recept eredménye (Szerszám kategória, 10. szint).

### `mesteri_horgaszbot` — Mesteri Horgászbot
- **Fájl:** `mesteri_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Arannyal és smaragddal díszített mesterbot — a Bokic vizének szellemei is elismerik. Halász-recept eredménye (Szerszám kategória, 35. szint).

### `rezhorgany_horgaszbot` — Rézhorgony Horgászbot
- **Fájl:** `rezhorgany_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** öntött fémrúd (ingot-forma)
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: borostyánsárga
- **Hangulat / lore:** Rezgő Rézötvözet horgonyzza le e botot a Bokic legerősebb sodrásában is. Halász-recept eredménye (Szerszám kategória, 26. szint).

### `runafenyes_csakany` — Rúnafényes Bányászcsákány
- **Fájl:** `runafenyes_csakany.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_PICKAXE`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Rúnapor fénye vezeti a csákány élét a legmélyebb tárnák sötétjében is. Bányász-recept eredménye (Szerszám kategória, 32. szint).

### `tarnasz_csakany_recept` — Tárnász Csákány
- **Fájl:** `tarnasz_csakany_recept.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_PICKAXE`
- **Ábrázolás:** csákány
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** A Vasművek Akadémiájának mesterei szerint még a legmélyebb tárna is megnyílik előtte. Bányász-recept eredménye (Szerszám kategória, 15. szint).

### `tartos_horgaszbot` — Tartós Horgászbot
- **Fájl:** `tartos_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Gyémánttal erősített nyél, mely a Bokic legmélyebb sodrásában sem törik el. Halász-recept eredménye (Szerszám kategória, 20. szint).

### `vasfejsze` — Vasfejsze
- **Fájl:** `vasfejsze.png` &nbsp;|&nbsp; **Alap-item:** `IRON_AXE`
- **Ábrázolás:** fejsze/balta
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** A kovácscéhek szabvány szerint edzik, hogy egyetlen favágó se álljon meg tőle. Favágó-recept eredménye (Szerszám kategória, 20. szint).

## Recept — Szerszám (tervrajz)

### `legendas_horgaszbot` — Legendás Horgászbot
- **Fájl:** `legendas_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Netherittel edzett zsinór, mely a Bokic legrégebbi, legvadabb lakóit is kifogja. Halász-recept eredménye (Szerszám (tervrajz) kategória, 45. szint).

### `netherit_csakany` — Mélybányász Netherit Csákány
- **Fájl:** `netherit_csakany.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_PICKAXE`
- **Ábrázolás:** csákány
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** A Kárhozat Kapujából szüremlő netherit fejezi be ezt a mélybányász szerszámot. Bányász-recept eredménye (Szerszám (tervrajz) kategória, 48. szint).

### `netherit_fejsze` — Erdőirtó Netherit Fejsze
- **Fájl:** `netherit_fejsze.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_AXE`
- **Ábrázolás:** fejsze/balta
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** A Kárhozat Kapujának netheritjével edzve, egyetlen fatörzs sem áll ellen neki. Favágó-recept eredménye (Szerszám (tervrajz) kategória, 48. szint).

## Recept — Sötét mágia

### `ejszaka_pengeje` — Az Éjszaka Pengéje
- **Fájl:** `ejszaka_pengeje.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** netherit kard, amelynek élén füstszerű árnyék-fátyol fut végig
- **Színvilág:** mélyfekete és sötét ibolya; akcent: fakó lélek-kék derengés
- **Hangulat / lore:** a Suttogók árnyékporával edzett penge — a fém emlékszik a suttogásra. Sötét mágia, bűvölő-munka.

## Recept — Vérszavanna (tervrajz)

### `napfogyatkozas` — Napfogyatkozás
- **Fájl:** `napfogyatkozas.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Fekete obszidián-íj, amely akkor a legerősebb, amikor a nap lehunyja szemét — éjjel a nyila árnyékból csap le. Kovács-recept eredménye (Vérszavanna (tervrajz) kategória, 46. szint).

### `zhoris_langnyelve` — I. Zhoris Lángnyelve
- **Fájl:** `zhoris_langnyelve.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Az első lángkirály kardja — a penge éle sosem hűl ki, s a sebek, amiket ejt, parázslanak, mint az emléke. Kovács-recept eredménye (Vérszavanna (tervrajz) kategória, 50. szint).

## Recept — Étel

### `banyasz_szalonna` — Bányász Szalonnája
- **Fájl:** `banyasz_szalonna.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_PORKCHOP`
- **Ábrázolás:** ropogósra sült szalonna tányéron, aranybarna zsíros szélekkel, füstös pirulással
- **Színvilág:** aranybarna; akcent: füstös vörösesbarna
- **Hangulat / lore:** Bányász Szalonnája — a tárnamunka zsíros ereje; egy falat, és bírod a mélységet.

### `erdei_gombapite` — Erdei Gomba Pite
- **Fájl:** `erdei_gombapite.png` &nbsp;|&nbsp; **Alap-item:** `PUMPKIN_PIE`
- **Ábrázolás:** aranybarna gombás pite szeletben, erdei gombakalapokkal a tetején, meleg töltelékkel
- **Színvilág:** aranybarna; akcent: erdei gombabarna
- **Hangulat / lore:** Erdei Gomba Pite — a Bokic-mente erdejének melege; egyszerű, laktató, otthonos.

### `fonix_fuszeres_szarny` — Főnixfűszeres Szárny
- **Fájl:** `fonix_fuszeres_szarny.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_CHICKEN`
- **Ábrázolás:** sült hús, csonttal
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Csípős! A Perinfernicitas konyhájának kedvence — óvatosan vele. Szakács-recept eredménye (Étel kategória, 41. szint).

### `fuszeres_vandorhus` — Fűszeres Vándorhús
- **Fájl:** `fuszeres_vandorhus.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_MUTTON`
- **Ábrázolás:** sült hús, csonttal
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Vándorfűszerrel érlelt, füstölt hús — hetekig eláll a nyeregtáskában. Szakács-recept eredménye (Étel kategória, 28. szint).

### `halasz_fogasa` — Halász Fogása
- **Fájl:** `halasz_fogasa.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_SALMON`
- **Ábrázolás:** ropogósra sült lazacfilé tányéron, rózsaszínes hússal, aranybarna héjjal, citromkarikával
- **Színvilág:** lazacrózsaszín; akcent: aranybarna héj
- **Hangulat / lore:** Halász Fogása — a napi zsákmány a tűzről; a Bokic-parti horgász büszkesége.

### `hamvak_lakomaja` — Hamvak Lakomája
- **Fájl:** `hamvak_lakomaja.png` &nbsp;|&nbsp; **Alap-item:** `BEETROOT_SOUP`
- **Ábrázolás:** sült hús, csonttal
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** A Kitaszítottak ünnepi tála: kevés, keserű, de aki megosztja, az már nem idegen. Szakács-recept eredménye (Étel kategória, 30. szint).

### `harcos_husos_tal` — Harcos Húsos Tála
- **Fájl:** `harcos_husos_tal.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_BEEF`
- **Ábrázolás:** gőzölgő marhahús-szeletek fatálcán, aranybarna sült kéreggel, bőséges adaggal
- **Színvilág:** sült húsbarna; akcent: aranybarna kéreg
- **Hangulat / lore:** Harcos Húsos Tála — a csata előtti lakoma; nehéz gyomor, kemény ököl.

### `kapu_lakomaja` — A Kapu Lakomája
- **Fájl:** `kapu_lakomaja.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_GOLDEN_APPLE`
- **Ábrázolás:** ragyogó aranyalma, a névhez illő tematikus díszítéssel
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Egyetlen falat, és megérted, mit őrzött a Kapu túlsó oldala. Szakács-recept eredménye (Étel kategória, 50. szint).

### `lakodalmas_torta` — Lakodalmas Emeletes Torta
- **Fájl:** `lakodalmas_torta.png` &nbsp;|&nbsp; **Alap-item:** `CAKE`
- **Ábrázolás:** díszített torta
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: rózsás pír
- **Hangulat / lore:** Három emelet, három frakció békéje — legalább amíg a torta el nem fogy. Szakács-recept eredménye (Étel kategória, 45. szint).

### `mezes_puszedli` — Mézes Puszedli
- **Fájl:** `mezes_puszedli.png` &nbsp;|&nbsp; **Alap-item:** `COOKIE`
- **Ábrázolás:** aranybarna mézes puszedli fehér cukormázzal, fényes mézcseppel, puha keksz-testtel
- **Színvilág:** aranybarna; akcent: fehér cukormáz
- **Hangulat / lore:** Mézes Puszedli — a caldesterai vásár csemegéje; a gyerekek kedvence.

### `pasztor_urucomb` — Pásztor Ürücombja
- **Fájl:** `pasztor_urucomb.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_MUTTON`
- **Ábrázolás:** sült ürücomb csonton, aranybarna ropogós bőrrel, rozmaringággal, meleg pecsenyefénnyel
- **Színvilág:** pecsenyebarna; akcent: rozmaringzöld
- **Hangulat / lore:** Pásztor Ürücombja — a szavannai pásztor tűzhelyének illata; egyszerű, bőséges.

### `sarkany_porkolt` — Sárkány-pörkölt
- **Fájl:** `sarkany_porkolt.png` &nbsp;|&nbsp; **Alap-item:** `RABBIT_STEW`
- **Ábrázolás:** gőzölgő tál leves/ragu
- **Színvilág:** meleg fabarna; akcent: borostyánsárga
- **Hangulat / lore:** Glatziendorf ünnepi étke: lassan fő, gyorsan melegít — a tenger íze és a tűzhely ereje egy tálban. Szakács-recept eredménye (Étel kategória, 30. szint).

### `tengerek_gyongye` — Tengerek Gyöngye
- **Fájl:** `tengerek_gyongye.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_COD`
- **Ábrázolás:** ezüstösen sült tőkehal tányéron, gyöngyházfényű hússal, tengerkék mázzal, citromszelettel
- **Színvilág:** gyöngyházfehér; akcent: tengerkék
- **Hangulat / lore:** Tengerek Gyöngye — a mélytengeri fogás ünnepi fogása; a hullámok ízét őrzi.

### `tuzes_chili_tal` — Tüzes Chilis Tál
- **Fájl:** `tuzes_chili_tal.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_BEEF`
- **Ábrázolás:** gőzölgő, vörös chilis marhahús tálban, izzó paprikaszemekkel, tüzes narancs szafttal
- **Színvilág:** tüzes vörös; akcent: izzó narancs
- **Hangulat / lore:** Tüzes Chilis Tál — Pyralingrad asztalának lángja; a gyáváknak nem való.

### `vadlakoma` — Vérszavannai Vadlakoma
- **Fájl:** `vadlakoma.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_BEEF`
- **Ábrázolás:** sült hús, csonttal
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Nyílt tűzön, egészben sült vad, ahogy I. Zhoris vadászai ették győzelem után. Szakács-recept eredménye (Étel kategória, 30. szint).

### `vandor_pogacsaja` — Vándor Pogácsája
- **Fájl:** `vandor_pogacsaja.png` &nbsp;|&nbsp; **Alap-item:** `BREAD`
- **Ábrázolás:** aranybarna pogácsa rácsos tetővel, ropogós héjjal, meleg morzsás béllel
- **Színvilág:** aranybarna; akcent: meleg morzsabarna
- **Hangulat / lore:** Vándor Pogácsája — az út elemózsiája; egy zsebnyi otthon a hosszú úton.

### `vandor_uti_kenyer` — Vándor Úti Kenyere
- **Fájl:** `vandor_uti_kenyer.png` &nbsp;|&nbsp; **Alap-item:** `BREAD`
- **Ábrázolás:** frissen sült cipó/vekni
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** Kétszer sütött, mézzel kent úti kenyér — a karavánok esküsznek rá. Szakács-recept eredménye (Étel kategória, 12. szint).

### `vandorunnep_lepenye` — Vándorünnep Lepénye
- **Fájl:** `vandorunnep_lepenye.png` &nbsp;|&nbsp; **Alap-item:** `PUMPKIN_PIE`
- **Ábrázolás:** frissen sült cipó/vekni
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** A Bokic-parti aratóünnep édes lepénye — egy szelet szerencse minden vándornak. Szakács-recept eredménye (Étel kategória, 30. szint).

## Recept — Étel (tervrajz)

### `aranyalma_lakoma` — Aranyalma Lakoma
- **Fájl:** `aranyalma_lakoma.png` &nbsp;|&nbsp; **Alap-item:** `GOLDEN_APPLE`
- **Ábrázolás:** sült hús, csonttal
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Ünnepi lakoma, melyben a Fa áldása és az arany fénye egyaránt megcsillan. Szakács-recept eredménye (Étel (tervrajz) kategória, 45. szint).

### `legendas_lakoma` — Legendás Lakoma
- **Fájl:** `legendas_lakoma.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_GOLDEN_APPLE`
- **Ábrázolás:** ragyogó aranyalma, a névhez illő tematikus díszítéssel
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Aetrinita áldásával sütött lakoma, méltó egy legenda asztalára. Szakács-recept eredménye (Étel (tervrajz) kategória, 48. szint).

## Bolt-különlegességek

### `shop_menlevel` — Hamisított Menlevél
- **Fájl:** `shop_menlevel.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** hivatalos irat vörös viaszpecséttel — kicsit TÚL tökéletes
- **Színvilág:** krémszínű pergamen; akcent: bordó
- **Hangulat / lore:** Hamisított Menlevél: a Bankárszövetség pecsétje… majdnem.

### `shop_setapalca` — Bokic-menti Sétapálca
- **Fájl:** `shop_setapalca.png` &nbsp;|&nbsp; **Alap-item:** `STICK`
- **Ábrázolás:** elegáns sétapálca arany fejjel — ránézésre úri bot, de a fej alatt penge sejlik
- **Színvilág:** meleg fabarna; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Bokic-menti Sétapálca (feketepiac): az őrség botot lát, a penge nem ért egyet.

## Nevesített loot

### `loot_elit_pancel` — Megrontott Elit Páncél
- **Fájl:** `loot_elit_pancel.png` &nbsp;|&nbsp; **Alap-item:** `CHAINMAIL_CHESTPLATE`
- **Ábrázolás:** szakadozott láncvért, a láncszemek közt hideg türkiz derengéssel
- **Színvilág:** sötétebb, kékes acél; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Megrontott Elit Páncél — az eltűnt nemesek dicsőségének maradványa.

### `loot_fekete_csont` — Fekete Csont
- **Fájl:** `loot_fekete_csont.png` &nbsp;|&nbsp; **Alap-item:** `BONE`
- **Ábrázolás:** koromfekete csontdarab, matt felülettel, hajszálvékony türkiz erezettel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Fekete Csont — nem ég el, nem törik, nem felejt.

### `loot_nema_kiralyno` — A Néma Királynő Suttogása
- **Fájl:** `loot_nema_kiralyno.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** éjsötét pengéjű kard, az él mentén vékony TÜRKIZ suttogás-fénnyel (lich-él)
- **Színvilág:** éjkék; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** A Néma Királynő Suttogása — nem penge: ígéret.

### `loot_rozsdas_penge` — A Hetedik Vérháború Rozsdás Pengéje
- **Fájl:** `loot_rozsdas_penge.png` &nbsp;|&nbsp; **Alap-item:** `IRON_SWORD`
- **Ábrázolás:** rozsdamarta, csorba hosszúkard, régi vér sötét foltjaival
- **Színvilág:** sötétebb, kékes acél; akcent: földbarna
- **Hangulat / lore:** A Hetedik Vérháború Rozsdás Pengéje — egykor hadsereg-fegyver, ma néma harag.

## Karbantartási szabály

- Új itemnél a config/kód `item-model` értéke legyen `icesmp:<modell-id>`; ugyanez a `<modell-id>` a blokk-fejlécben és a PNG fájlnevében.
- Minden élő modell-id kapjon egy `### \`<modell-id>\` — Név` blokkot (a `scripts/check_consistency.py` ezt ellenőrzi); a blokk adja meg az Alap-itemet, Ábrázolást, Színvilágot és a Hangulat/lore sort.
- A leírás legyen tárgyra szabott; faction/lore-kötött tárgy a Globális paletta akcensét viselje, DARK-nál a hideg türkiz lich-fényt.
