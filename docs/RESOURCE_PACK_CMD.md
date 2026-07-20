# Resource Pack — CMD-regiszter és textúra-leírások

**A textúra-készítőnek.** Minden egyedi (custom) plugin-item CustomModelData-t (CMD)
visel; ez a fájl itemenként megadja a CMD-t, a fájlnevet, az alap-itemet és a
RÉSZLETES vizuális leírást (mit ábrázoljon, színvilág, hangulat/lore).

## Technikai tudnivalók

- **Méret:** 16×16 px, átlátszó háttérrel (PNG) — vanilla-konzisztens pixel-art.
- **Fájlnév és hely:** a kész PNG a plugin-repo `resourcepack/assets/icesmp/textures/item/<fájlnév>` útvonalára kerül — a JSON-bekötés (modellek, CMD-kapcsolók) már kész, CSAK a PNG-ket kell cserélni. A mostani textúrák generált placeholderek.
- **Alap-item:** a vanilla tárgy, aminek a helyén az item megjelenik, ha a CMD egyezik — a vanilla textúrája jó kiindulási referencia a sziluetthez/érzethez.
- **Jelenlegi állapot:** MINDEN mostani textúra ideiglenes (generált placeholder, illetve az 5 pénz-item egy PRÓBA-lapról bevágva) — a végleges készletet a textúra-készítő adja; semmi sincs kőbe vésve.
- **Frakció-színvilág:** RED=Perinfernicitas (láng, vörös-arany), BLUE=Cryghaliris (jég, kék-ezüst), NEUTRAL=Ryanora/Caldestera (kereskedő-arany, zöld-okker), DARK=Kitaszítottak (csont, éjfekete-lila, és a jellegzetes HIDEG TÜRKIZ derengés — mint a lich-szem: a Néma Királynő élőhalott-fénye a szemekben, rúnákban, élek mentén).
## Stílus-szabályok (vanilla-konzisztencia)

1. **Egységes felbontás:** a felbontást a textúra-készítő választja meg (vanilla-hű 16×16 vagy részletesebb 32×32) — de a TELJES pack egységesen ugyanazt használja, vegyes felbontás tilos. Az import-szkript bármelyikre méretez (`--size`).
2. **Margó:** a tárgy ne érjen a vászon széléig — a vászon ~80%-át töltse ki, középre igazítva (16-osnál ~1-2 px, 32-esnél ~3-4 px üres perem).
3. **Sziluett-olvashatóság:** az item egy vanilla tárgy helyén jelenik meg — első ránézésre ugyanannak a tárgy-osztálynak tűnjön (bot=bot, sisak=sisak). Kard/szerszám 45°-ban átlósan: markolat balra-le, hegy jobbra-fel.
4. **Kontúr:** 1 px sötét körvonal a külső élen, de NEM tiszta fekete — az anyagszín legmélyebb árnyalata; a belső vonalak még lágyabbak.
5. **Paletta-fegyelem:** anyagonként 4-8 tónus, kemény pixel-átmenetek — semmi blur, anti-aliasing vagy színátmenet; dither csak nagyon indokoltan.
6. **Fényirány:** mindig bal-felső fényforrás (világos él fent/balra, mély tónus lent/jobbra); vetett árnyék a sziluetten kívül nincs.
7. **Alfa igen/nem:** minden pixel vagy teljesen fedő, vagy teljesen átlátszó — félig átlátszó élpixel TILOS (a játékban csúnya szegély lesz belőle).
8. **Telítettség:** a vanilla visszafogott, földes paletta mellett a neon kiabál — izzó akcent (lich-türkiz, láng) csak kevés pixelen (2-6 fénypont).
9. **Kicsiben is működjön:** az inventoryban ~16-32 képernyő-pixel látszik — a 2 px-nél kisebb részlet eltűnik; minden darabot kicsinyítve is ellenőrizz.
10. **Perspektíva:** lapos sprite szemből (vagy 45°-os szerszám) — nem izometrikus/3D nézet.

- **Leadás sprite-lapként:** a kész textúrák egyetlen képen is leadhatók (kockás/fehér háttér mehet) — a `python3 tools/import_texture_sheet.py <kép> <nev1,nev2,...>` kivágja, háttértől megtisztítja és beteszi őket a packba (olvasási sorrend: sorok fentről, balról jobbra; a nevek a fenti fájlnevek `.png` nélkül). Az importált textúra végleg felülüti a generált placeholdert.
- Újragenerálás (leírások frissítése configból): `python3 tools/build_cmd_artdoc.py`

## Pénz-tárgyak (1001–1010)

### 1001 — Parázsló Parals
- **Fájl:** `coin_red.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** kerek, vert érme, peremén rovátkolt díszítés, közepén dombornyomott LÁNGNYELV-címer
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: izzó narancsvörös
- **Hangulat / lore:** Perinfernicitas valutája, a Parázsló Parals — a láng népének büszke, forró aranya.

### 1002 — Hópihér-veret
- **Fájl:** `coin_blue.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** kerek, vert érme, rovátkolt peremmel, közepén dombornyomott HÓPEHELY-címer
- **Színvilág:** hűvös ezüstszürke; akcent: jeges világoskék
- **Hangulat / lore:** Cryghaliris valutája, a Hópihér-veret — hideg, tiszta, ezüstös csillogás.

### 1003 — Creutzér
- **Fájl:** `coin_neutral.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** kerek, vert érme, közepén dombornyomott KERESKEDŐ-MÉRLEG címer
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: borostyánsárga
- **Hangulat / lore:** Ryanora–Caldestera valutája, a Creutzér — a Bankárszövetség megbízható aranya.

### 1004 — Csontveret
- **Fájl:** `coin_dark.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** kopott, sötét érme, közepén dombornyomott KOPONYA-címer, a koponya szemüregeiben apró TÜRKIZ izzással, szélein csorbulások
- **Színvilág:** sötétebb, kékes acél; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** A Kitaszítottak Csontverete — akit ezzel fizetnek, nem kérdez. A türkiz szempár a Néma Királynő jele.

### 1010 — Kopott erszény
- **Fájl:** `money_pouch.png` &nbsp;|&nbsp; **Alap-item:** `LEATHER`
- **Ábrázolás:** zsinórral összehúzott, kopott bőrerszény; a nyakánál kikandikáló 2-3 aranyérme
- **Színvilág:** cserzett bőrbarna; akcent: földbarna
- **Hangulat / lore:** Talált pénz: mob-drop és horgász-lelet. Viseltes, útszéli hangulat — valaki elvesztette.

## Relikviák (4101–4205)

### 4101 — A Mételytépő
- **Fájl:** `relic_metelytepo.png` &nbsp;|&nbsp; **Alap-item:** `GOLDEN_AXE`
- **Ábrázolás:** arany harci balta, a feje körül halvány lila derengéssel; ősi, idegen mintázatú nyél
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: meleg fabarna
- **Hangulat / lore:** A Mételytépő — a törpék rejtélyes civilizációjának relikviája. Múzeumi kincs, nem szerszám.

### 4201 — Főnix-szárny
- **Fájl:** `relic_phoenix_wing.png` &nbsp;|&nbsp; **Alap-item:** `ELYTRA`
- **Ábrázolás:** kiterjesztett, stilizált szárny lángoló tollakkal, a hegyénél izzással
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Főnix-szárny — Perinfernicitas elytra-relikviája, Soleil főnixeinek tollából.

### 4202 — Zúzmara-szárny
- **Fájl:** `relic_frost_wing.png` &nbsp;|&nbsp; **Alap-item:** `ELYTRA`
- **Ábrázolás:** kiterjesztett szárny jégkristály-tollakkal, fagyott csillogással
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Zúzmara-szárny — Cryghaliris elytra-relikviája, jégsárkány-lehelettel átitatva.

### 4203 — Vándorszél
- **Fájl:** `relic_wander_wind.png` &nbsp;|&nbsp; **Alap-item:** `ELYTRA`
- **Ábrázolás:** könnyű, világos szárny, lebegő, áttetsző tollakkal
- **Színvilág:** égszínkék; akcent: törtfehér
- **Hangulat / lore:** Vándorszél — Ryanora & Caldestera szabad szele, Arkynn békés öröksége.

### 4204 — Csontszárny
- **Fájl:** `relic_bone_wing.png` &nbsp;|&nbsp; **Alap-item:** `ELYTRA`
- **Ábrázolás:** csontokból szőtt, szakadozott szárny sötét hártyával, az ízületeknél hideg türkiz izzás-pontokkal
- **Színvilág:** törtfehér csontszín; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Csontszárny — a Káoszkor élőhalott-relikviája; éjjel viselője árnyékká válik.

### 4205 — Eleftheria Könnye
- **Fájl:** `relic_eleftheria_konnye.png` &nbsp;|&nbsp; **Alap-item:** `HEART_OF_THE_SEA`
- **Ábrázolás:** éjfekete, megkövült könnycsepp, belsejében halvány TÜRKIZ fénymaggal (lich-fény)
- **Színvilág:** éjkék; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Eleftheria Könnye — a Néma Királynő első suttogása kővé dermedve.

## Kaszt-katalizátorok (5201–5213)

### 5201 — Caldesterai Rúnakódex
- **Fájl:** `catalyst_wizard.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** derengő varázskönyv — misztikus, kaszt-színű derengéssel (Caldesterai Rúnakódex)
- **Színvilág:** királylila; akcent: világító cián
- **Hangulat / lore:** A(z) Wizard kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5202 — Sárkánykirály Kürtje
- **Fájl:** `catalyst_warrior.png` &nbsp;|&nbsp; **Alap-item:** `GOAT_HORN`
- **Ábrázolás:** ívelt kürt — misztikus, kaszt-színű derengéssel (Sárkánykirály Kürtje)
- **Színvilág:** mélyvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A(z) Warrior kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5203 — Soleil Vadásztarsolya
- **Fájl:** `catalyst_archer.png` &nbsp;|&nbsp; **Alap-item:** `RABBIT_HIDE`
- **Ábrázolás:** kikészített irha — misztikus, kaszt-színű derengéssel (Soleil Vadásztarsolya)
- **Színvilág:** élénk levélzöld; akcent: borostyánsárga
- **Hangulat / lore:** A(z) Archer kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5204 — Homály-szilánk
- **Fájl:** `catalyst_assassin.png` &nbsp;|&nbsp; **Alap-item:** `FLINT`
- **Ábrázolás:** pattintott kovakő — misztikus, kaszt-színű derengéssel (Homály-szilánk)
- **Színvilág:** mély ibolyalila; akcent: sötétebb, kékes acél
- **Hangulat / lore:** A(z) Assassin kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5205 — Aetrinita Sarja
- **Fájl:** `catalyst_druid.png` &nbsp;|&nbsp; **Alap-item:** `OAK_SAPLING`
- **Ábrázolás:** fiatal csemete — misztikus, kaszt-színű derengéssel (Aetrinita Sarja)
- **Színvilág:** mohazöld; akcent: élénk levélzöld
- **Hangulat / lore:** A(z) Druid kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5206 — Hajnaltűz Harangja
- **Fájl:** `catalyst_paladin.png` &nbsp;|&nbsp; **Alap-item:** `BELL`
- **Ábrázolás:** öntött harang — misztikus, kaszt-színű derengéssel (Hajnaltűz Harangja)
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: égszínkék
- **Hangulat / lore:** A(z) Paladin kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5207 — Néma Rúnakoponya
- **Fájl:** `catalyst_death_knight.png` &nbsp;|&nbsp; **Alap-item:** `WITHER_SKELETON_SKULL`
- **Ábrázolás:** megfeketedett koponya — misztikus, kaszt-színű derengéssel (Néma Rúnakoponya)
- **Színvilág:** éjkék; akcent: mély ibolyalila
- **Hangulat / lore:** A(z) Death Knight kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5208 — Ősvihar Totemje
- **Fájl:** `catalyst_shaman.png` &nbsp;|&nbsp; **Alap-item:** `TOTEM_OF_UNDYING`
- **Ábrázolás:** faragott totemfigura — misztikus, kaszt-színű derengéssel (Ősvihar Totemje)
- **Színvilág:** világító cián; akcent: jeges világoskék
- **Hangulat / lore:** A(z) Shaman kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5209 — Élet Ága
- **Fájl:** `catalyst_monk.png` &nbsp;|&nbsp; **Alap-item:** `BAMBOO`
- **Ábrázolás:** bambusznád — misztikus, kaszt-színű derengéssel (Élet Ága)
- **Színvilág:** izzó narancsvörös; akcent: borostyánsárga
- **Hangulat / lore:** A(z) Monk kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5210 — Asterlayna Gyertyája
- **Fájl:** `catalyst_priest.png` &nbsp;|&nbsp; **Alap-item:** `WHITE_CANDLE`
- **Ábrázolás:** fehér gyertya — misztikus, kaszt-színű derengéssel (Asterlayna Gyertyája)
- **Színvilág:** hűvös ezüstszürke; akcent: égszínkék
- **Hangulat / lore:** A(z) Priest kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5211 — Kárhozat Lámpása
- **Fájl:** `catalyst_warlock.png` &nbsp;|&nbsp; **Alap-item:** `SOUL_LANTERN`
- **Ábrázolás:** kék lángú lélek-lámpás — misztikus, kaszt-színű derengéssel (Kárhozat Lámpása)
- **Színvilág:** bordó; akcent: mély ibolyalila
- **Hangulat / lore:** A(z) Warlock kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5212 — Hasadék Szeme
- **Fájl:** `catalyst_demon_hunter.png` &nbsp;|&nbsp; **Alap-item:** `ENDER_EYE`
- **Ábrázolás:** végzet-szem — misztikus, kaszt-színű derengéssel (Hasadék Szeme)
- **Színvilág:** sötétebb, kékes acél; akcent: éjkék
- **Hangulat / lore:** A(z) Demon Hunter kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

### 5213 — Sárkányvér-fiola
- **Fájl:** `catalyst_evoker.png` &nbsp;|&nbsp; **Alap-item:** `DRAGON_BREATH`
- **Ábrázolás:** lila köddel teli gömbpalack — misztikus, kaszt-színű derengéssel (Sárkányvér-fiola)
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** A(z) Evoker kaszt katalizátora — a kaszt-éledés rituálé-tárgya.

## Pet-befogók és ostromgép (5301–5401)

### 5301 — Ősi Kötés Póráza
- **Fájl:** `capture_beast.png` &nbsp;|&nbsp; **Alap-item:** `LEAD`
- **Ábrázolás:** feltekert pányva/lasszó, zöld természet-szimbólummal a közepén
- **Színvilág:** cserzett bőrbarna; akcent: élénk levélzöld
- **Hangulat / lore:** Ősi Kötés Póráza — a Vadmester ezzel fogadja társává az állatokat (Aetrinita és Kallan kötése).

### 5302 — Sötét Paktum-tekercs
- **Fájl:** `capture_necro.png` &nbsp;|&nbsp; **Alap-item:** `GHAST_TEAR`
- **Ábrázolás:** sötét pergamentekercs koponya-pecséttel, a koponya szemeiben türkiz izzással
- **Színvilág:** mély ibolyalila; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Sötét Paktum-tekercs — a Nekromanta ezzel köti szolgájává a szörnyet (Eleftheria mérge).

### 5401 — Ostromágyú
- **Fájl:** `siege_cannon.png` &nbsp;|&nbsp; **Alap-item:** `TNT_MINECART`
- **Ábrázolás:** zömök, fekete ostromágyú-cső kerekes talpon, a csőtorkolatnál szikrával
- **Színvilág:** szénfekete-szürke; akcent: sötétebb, kékes acél
- **Hangulat / lore:** Ostromágyú — a Hét Vérháború öröksége; csak raid alatt szólal meg.

## Unique szakma-anyagok (6000–6138)

### 6000 — Tiszta Vasesszencia
- **Fájl:** `u_tiszta_vasesszencia.png` &nbsp;|&nbsp; **Alap-item:** `IRON_NUGGET`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** A Vasművek Akadémiájának titka: ezerszer hajtogatott vas lelke. Kovács köztes alapanyag

### 6001 — Gyógy-kivonat
- **Fájl:** `u_gyogy_kivonat.png` &nbsp;|&nbsp; **Alap-item:** `GLOW_BERRIES`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** A Bokic partján, hajnalban szedett gyógyfüvek ragyogó párlata. Gyógynövényész köztes alapanyag

### 6002 — Rezgő Rézötvözet
- **Fájl:** `u_rezgo_rez_otvozet.png` &nbsp;|&nbsp; **Alap-item:** `COPPER_INGOT`
- **Ábrázolás:** öntött fémrúd (ingot-forma)
- **Színvilág:** világos acélszürke; akcent: sötétebb, kékes acél
- **Hangulat / lore:** A Mélység Népe tárnáiból tanult ötvözet — halkan zúg, ha rúna simítja. Bányász köztes alapanyag

### 6003 — Keményfa Gerenda
- **Fájl:** `u_kemenyfa_gerenda.png` &nbsp;|&nbsp; **Alap-item:** `STRIPPED_OAK_WOOD`
- **Ábrázolás:** rönk, évgyűrűkkel
- **Színvilág:** meleg fabarna; akcent: földbarna
- **Hangulat / lore:** A Bokic menti ősvadonban nőtt, évszázados tölgy magja. Favágó köztes alapanyag

### 6004 — Rúnapor
- **Fájl:** `u_runapor.png` &nbsp;|&nbsp; **Alap-item:** `GLOWSTONE_DUST`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** királylila; akcent: világító cián
- **Hangulat / lore:** Szétmorzsolt rúnakő csilláma — az Akadémiák elveszett rúna-tudásának nyers pora. Bűvölő köztes alapanyag

### 6005 — Jégvirág-por
- **Fájl:** `u_jegviragpor.png` &nbsp;|&nbsp; **Alap-item:** `SUGAR`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** A Jégmezők peremén nyíló jégvirág szirma, mozsárban holdfénnyel törve. Gyógynövényész köztes alapanyag

### 6006 — Parázsmag
- **Fájl:** `u_parazsmag.png` &nbsp;|&nbsp; **Alap-item:** `BLAZE_POWDER`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Vérszavanna kihunyt tábortüzeiből kikapart, még mindig lüktető szikra. Alkimista köztes alapanyag

### 6007 — Viharkvarc
- **Fájl:** `u_viharkvarc.png` &nbsp;|&nbsp; **Alap-item:** `QUARTZ`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Villámsújtotta sziklából fejtett kvarc — a belsejében még ott remeg a vihar. Bányász köztes alapanyag

### 6008 — Mélységi Borostyán
- **Fájl:** `u_melysegi_borostyan.png` &nbsp;|&nbsp; **Alap-item:** `RAW_GOLD`
- **Ábrázolás:** kerek pite / sütemény
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** A Mélység Népe tárnáinak gyantája, kővé dermedt idő — néha egy-egy elveszett rúna is belefagyott. Régészeti lelet / bányász-finomítás

### 6010 — Vad Esszencia
- **Fájl:** `u_vad_esszencia.png` &nbsp;|&nbsp; **Alap-item:** `PHANTOM_MEMBRANE`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** A Káoszkor elvadult fenevadjának dühe, mely legyőzött testéből száll el. Csak szörnyekből esik

### 6011 — Szörny Mag
- **Fájl:** `u_szorny_mag.png` &nbsp;|&nbsp; **Alap-item:** `ECHO_SHARD`
- **Ábrázolás:** fiatal csemete/hajtás
- **Színvilág:** élénk levélzöld; akcent: földbarna
- **Hangulat / lore:** Egy káoszkori szörny lüktető, sötét szíve — még kihűlve is hatalmat rejt. Csak erősebb szörnyekből esik

### 6012 — Árnyékpor
- **Fájl:** `u_arnyekpor.png` &nbsp;|&nbsp; **Alap-item:** `SCULK_VEIN`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** mély ibolyalila; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** A Néma Királynő suttogásának megülepedett, éjfekete pora — elnyeli a fényt és a hangot. Csak szörnyekből esik

### 6013 — Fekete Villám Szilánk
- **Fájl:** `u_osi_ereklyeszilank.png` &nbsp;|&nbsp; **Alap-item:** `NETHER_STAR`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** Az a sötét energia pulzál benne, amely Hu. 698-ban kettéhasította az eget, s felébresztette a Holtak Úrnőjét. Csak világbossból / nehéz eventből esik

### 6014 — Opálos Emlékszilánk
- **Fájl:** `u_emlekszilank.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** Egy Felső elveszett emlékének kristálya — romok mélyén, erős szörnyeknél rejtőzik. Beváltás: /emlek (visszaemlékezés)

### 6015 — Suttogás
- **Fájl:** `u_suttogas_meghivo.png` &nbsp;|&nbsp; **Alap-item:** `ECHO_SHARD`
- **Ábrázolás:** félig kigördült pergamentekercs írássorokkal
- **Színvilág:** mély ibolyalila; akcent: királylila
- **Hangulat / lore:** Egy hang, amit csak te hallasz. Hívás, amit nem lehet visszautasítani — csak követni. Éjjel, a mélység sötétjén (sculk), egyedül…

### 6016 — Sárkánycsont-szilánk
- **Fájl:** `u_sarkanycsont_szilank.png` &nbsp;|&nbsp; **Alap-item:** `BONE`
- **Ábrázolás:** csontdarab, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Kallan elhullott jégsárkányainak csontja — hidegebb, mint a jég maga. Csak világbossból / nehéz eventből esik

### 6017 — Főnixpihe
- **Fájl:** `u_fonixpihe.png` &nbsp;|&nbsp; **Alap-item:** `FEATHER`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Soleil főnixeinek pihéje — a hamu nem fogja, a láng nem emészti. Csak szörnyekből esik

### 6018 — Holdezüst Huzal
- **Fájl:** `u_holdezust_huzal.png` &nbsp;|&nbsp; **Alap-item:** `CHAIN`
- **Ábrázolás:** feltekercselt huzal / drótspirál
- **Színvilág:** hűvös ezüstszürke; akcent: égszínkék
- **Hangulat / lore:** Újhold éjjelén húzott ezüstszál — csak akkor fénylik, ha senki sem nézi. Bűvölő köztes alapanyag

### 6019 — Csontenyv
- **Fájl:** `u_csontenyv.png` &nbsp;|&nbsp; **Alap-item:** `BONE_MEAL`
- **Ábrázolás:** egyenes rúd/pálca forma
- **Színvilág:** törtfehér csontszín; akcent: törtfehér
- **Hangulat / lore:** A Mélység Népe régi receptje: lassan főzött enyv, ami követ is köt fához. Szakács köztes alapanyag

### 6020 — Számvevő-pecsétviasz
- **Fájl:** `u_viaszpecset.png` &nbsp;|&nbsp; **Alap-item:** `HONEYCOMB`
- **Ábrázolás:** pecsétnyomó viaszpecséttel
- **Színvilág:** bordó; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Bankárszövetség hivatalos viasza — amit ez pecsétel, az megmásíthatatlan. Csak boltból vehető (Bankárszövetség)

### 6021 — Sarkfény-cseppkő
- **Fájl:** `u_sarkfeny_cseppko.png` &nbsp;|&nbsp; **Alap-item:** `PRISMARINE_CRYSTALS`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** középkék; akcent: világító cián
- **Hangulat / lore:** Az északi fény egy cseppje, ami barlang mélyén kővé dermedt — még mindig táncol. Bányász köztes alapanyag

### 6022 — Szavannafű-kötél
- **Fájl:** `u_szavannafu_kotel.png` &nbsp;|&nbsp; **Alap-item:** `VINE`
- **Ábrázolás:** sodrott kötélköteg
- **Színvilág:** földbarna; akcent: borostyánsárga
- **Hangulat / lore:** A Vérszavanna szívós füvéből sodort kötél — a pásztorok szerint sárkányt is tartott már. Favágó köztes alapanyag

### 6023 — Obszidián-szilánk
- **Fájl:** `u_obszidian_szilank.png` &nbsp;|&nbsp; **Alap-item:** `FLINT`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** A Kapu körüli mezőkről pattintott fekete üveg — a napfényt is elnyeli. Bányász köztes alapanyag

### 6024 — Mortengradi Árnygomba
- **Fájl:** `u_arnygomba.png` &nbsp;|&nbsp; **Alap-item:** `CRIMSON_FUNGUS`
- **Ábrázolás:** kalapos gomba
- **Színvilág:** földbarna; akcent: törtfehér csontszín
- **Hangulat / lore:** Csak romok pincéiben nő, fény sosem érte — a Kitaszítottak kenyérpótlója. Gyógynövényész köztes alapanyag

### 6025 — Lélekhamu
- **Fájl:** `u_lelekhamu.png` &nbsp;|&nbsp; **Alap-item:** `GUNPOWDER`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** mély ibolyalila; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Kihunyt lélektűz maradéka. Hideg, szürke — és mégis, néha megmoccan. Alkimista köztes alapanyag

### 6026 — Aranyfüst-lemez
- **Fájl:** `u_aranyfust_lemez.png` &nbsp;|&nbsp; **Alap-item:** `GOLD_NUGGET`
- **Ábrázolás:** kalapált, fényes fémlemez
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: borostyánsárga
- **Hangulat / lore:** Leheletvékonyra vert arany, a caldesterai ötvösnegyed büszkesége. Kovács köztes alapanyag

### 6027 — Gyöngyház-pikkely
- **Fájl:** `u_gyongyhaz_pikkely.png` &nbsp;|&nbsp; **Alap-item:** `PRISMARINE_SHARD`
- **Ábrázolás:** derengő tintazsák / fénylő gyöngy
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: égszínkék
- **Hangulat / lore:** A Bokic vén pontyainak pikkelye — a fény hét színére törik rajta. Halász köztes alapanyag

### 6028 — Vándorfűszer
- **Fájl:** `u_vandorfuszer.png` &nbsp;|&nbsp; **Alap-item:** `COCOA_BEANS`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** mélyvörös; akcent: borostyánsárga
- **Hangulat / lore:** Hét út pora, hét piac illata egy zacskóban — a karavánok kincse. Szakács köztes alapanyag

### 6029 — Dermedt Könnycsepp
- **Fájl:** `u_dermedt_konnycsepp.png` &nbsp;|&nbsp; **Alap-item:** `GHAST_TEAR`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Azt mondják, Eleftheria sírt, mielőtt elhallgatott. Ez itt bizonyíték. Csak élőhalottakból esik

### 6030 — A Kapu Parazsa
- **Fájl:** `u_karhozat_parazs.png` &nbsp;|&nbsp; **Alap-item:** `FIRE_CHARGE`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Kárhozat Kapujából kisodródott parázs — nem ég, nem alszik ki. Csak vár. Csak világbossból / nehéz eventből esik

### 6031 — Néma Kristály
- **Fájl:** `u_nema_kristaly.png` &nbsp;|&nbsp; **Alap-item:** `AMETHYST_SHARD`
- **Ábrázolás:** három kristálytüske közös kőalapon
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Sculk-erek közt nőtt kristály. Ha a füledhez tartod, hallod, hogy NEM szól semmit. Csak szörnyekből esik

### 6032 — Az Első Csend Szilánkja
- **Fájl:** `u_elso_csend_szilankja.png` &nbsp;|&nbsp; **Alap-item:** `ECHO_SHARD`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** Nem tudni, mi ez, sem honnan való. Csak azt, hogy amikor a kezedbe veszed, a világ egy pillanatra elhallgat. A legritkább kincs — csak világbossból

### 6033 — Finomított Lámpaolaj
- **Fájl:** `u_lampaolaj.png` &nbsp;|&nbsp; **Alap-item:** `GLOW_INK_SAC`
- **Ábrázolás:** fém kanna kiöntőcsőrrel
- **Színvilág:** borostyánsárga; akcent: szénfekete-szürke
- **Hangulat / lore:** A caldesterai lámpagyújtogatók titkos keveréke — tisztán, kormozás nélkül ég. Csak boltból vehető

### 6034 — Kovács-folyósítószer
- **Fájl:** `u_folyositoszer.png` &nbsp;|&nbsp; **Alap-item:** `BLAZE_POWDER`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** sötét méregzöld; akcent: világító cián
- **Hangulat / lore:** A Vasművek Akadémiájának adalékszere: nélküle a netherit nem veszi fel a rúnát. Csak boltból vehető

### 6100 — Robbantópor
- **Fájl:** `u_robbantopor.png` &nbsp;|&nbsp; **Alap-item:** `GUNPOWDER`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** mélyvörös; akcent: szénfekete-szürke
- **Hangulat / lore:** A Vasművek engedélyezett keveréke — tárnát nyit, nem sírgödröt. Bányász kellék (csak boltból)

### 6101 — Tárnatámasz-szegecs
- **Fájl:** `u_tarnatamasz_szegecs.png` &nbsp;|&nbsp; **Alap-item:** `IRON_NUGGET`
- **Ábrázolás:** szerszámos láda / készlet-doboz
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Szabvány szegecs a Mélység Népe méretei szerint. Sosem enged. Bányász kellék (csak boltból)

### 6102 — Csillekenőcs
- **Fájl:** `u_csillekenocs.png` &nbsp;|&nbsp; **Alap-item:** `SLIME_BALL`
- **Ábrázolás:** fém kanna kiöntőcsőrrel
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Ettől fut a csille hang nélkül — a tárna tisztelete csendet kíván. Bányász kellék (csak boltból)

### 6103 — Ércmosó-lúg
- **Fájl:** `u_ercmoso_lug.png` &nbsp;|&nbsp; **Alap-item:** `GLASS_BOTTLE`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** sötét méregzöld; akcent: világító cián
- **Hangulat / lore:** Leoldja a meddőt az ércről, és a kézről a tárnák szagát. Bányász kellék (csak boltból)

### 6104 — Mélységi Iránytű-tű
- **Fájl:** `u_melysegi_iranytu.png` &nbsp;|&nbsp; **Alap-item:** `COMPASS`
- **Ábrázolás:** iránytű, a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: mélyvörös
- **Hangulat / lore:** A mélyben nem észak felé mutat — hazafelé. Az többet ér. Bányász kellék (csak boltból)

### 6105 — Üvegfiola-készlet
- **Fájl:** `u_uvegfiola_keszlet.png` &nbsp;|&nbsp; **Alap-item:** `GLASS_BOTTLE`
- **Ábrázolás:** szerszámos láda / készlet-doboz
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Tucatnyi apró fiola — a kivonat annyit ér, amennyit az üveg megőriz. Gyógynövényész kellék (csak boltból)

### 6106 — Aszalóháló
- **Fájl:** `u_aszalohalo.png` &nbsp;|&nbsp; **Alap-item:** `COBWEB`
- **Ábrázolás:** csomózott háló
- **Színvilág:** türkizes viharzöld; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Finom szövésű háló a füvek árnyékban szárításához. Gyógynövényész kellék (csak boltból)

### 6107 — Oltóviasz
- **Fájl:** `u_oltoviasz.png` &nbsp;|&nbsp; **Alap-item:** `HONEYCOMB`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** A metszés sebét zárja — a kert is gyógyul, nemcsak a beteg. Gyógynövényész kellék (csak boltból)

### 6108 — Tőzegkocka
- **Fájl:** `u_tozegkocka.png` &nbsp;|&nbsp; **Alap-item:** `PACKED_MUD`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** világító cián; akcent: hűvös ezüstszürke
- **Hangulat / lore:** A Bokic-láp fekete aranya — ebben minden mag kicsírázik. Gyógynövényész kellék (csak boltból)

### 6109 — Permetező-kanna
- **Fájl:** `u_permetezo_kanna.png` &nbsp;|&nbsp; **Alap-item:** `BUCKET`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** szénfekete-szürke; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Rézfejű kanna finom permethez — a harmatot utánozza, nem az esőt. Gyógynövényész kellék (csak boltból)

### 6110 — Fenőkő
- **Fájl:** `u_fenoko.png` &nbsp;|&nbsp; **Alap-item:** `SMOOTH_STONE`
- **Ábrázolás:** fém kanna kiöntőcsőrrel
- **Színvilág:** sötétebb, kékes acél; akcent: borostyánsárga
- **Hangulat / lore:** Vízköszörült kő a Bokic partjáról — az él tőle tanul énekelni. Favágó kellék (csak boltból)

### 6111 — Gyantaoldó
- **Fájl:** `u_gyantaoldo.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** Leoldja a gyantát a fűrészről és az alkut a kézfogásról. Favágó kellék (csak boltból)

### 6112 — Ácskapocs
- **Fájl:** `u_acskapocs.png` &nbsp;|&nbsp; **Alap-item:** `IRON_NUGGET`
- **Ábrázolás:** szerszámos láda / készlet-doboz
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Kovácsolt kapocs — gerendát fog össze, és tetőt tart, ha kell. Favágó kellék (csak boltból)

### 6113 — Mérőzsinór
- **Fájl:** `u_merozsinor.png` &nbsp;|&nbsp; **Alap-item:** `STRING`
- **Ábrázolás:** sodrott kötélköteg
- **Színvilág:** földbarna; akcent: borostyánsárga
- **Hangulat / lore:** Krétázott zsinór — az egyenes vonal a mesterség fele. Favágó kellék (csak boltból)

### 6114 — Favédő pác
- **Fájl:** `u_favedo_pac.png` &nbsp;|&nbsp; **Alap-item:** `INK_SAC`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** Sötét pác, mit az ácsok kevernek — a fa tőle éli túl a telet. Favágó kellék (csak boltból)

### 6115 — Edzőolaj
- **Fájl:** `u_edzoolaj.png` &nbsp;|&nbsp; **Alap-item:** `MAGMA_CREAM`
- **Ábrázolás:** fém kanna kiöntőcsőrrel
- **Színvilág:** sötétebb, kékes acél; akcent: borostyánsárga
- **Hangulat / lore:** Ebbe mártva sziszeg a penge — a Vasművek előírása szerint. Kovács kellék (csak boltból)

### 6116 — Polírpaszta
- **Fájl:** `u_polirpaszta.png` &nbsp;|&nbsp; **Alap-item:** `SUGAR`
- **Ábrázolás:** fém kanna kiöntőcsőrrel
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Finom csiszolópor — a tükörfény nem hiúság, hanem minőségjelzés. Kovács kellék (csak boltból)

### 6117 — Nyélbőr
- **Fájl:** `u_nyelbor.png` &nbsp;|&nbsp; **Alap-item:** `LEATHER`
- **Ábrázolás:** nyereg / lószerszám
- **Színvilág:** cserzett bőrbarna; akcent: földbarna
- **Hangulat / lore:** Cserzett szíj markolat-tekeréshez — a fegyver ott kezdődik, ahol a kéz. Kovács kellék (csak boltból)

### 6118 — Fújtatóbőr
- **Fájl:** `u_fujtatobor.png` &nbsp;|&nbsp; **Alap-item:** `RABBIT_HIDE`
- **Ábrázolás:** nyereg / lószerszám
- **Színvilág:** cserzett bőrbarna; akcent: földbarna
- **Hangulat / lore:** A kohó tüdeje — szakadt fújtatóval nincs se láng, se legenda. Kovács kellék (csak boltból)

### 6119 — Desztillált Esővíz
- **Fájl:** `u_desztillalt_esoviz.png` &nbsp;|&nbsp; **Alap-item:** `GLASS_BOTTLE`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** középkék; akcent: világító cián
- **Hangulat / lore:** Háromszor párolt tavaszi eső — a főzet lelke a tiszta víz. Alkimista kellék (csak boltból)

### 6120 — Szűrőpapír
- **Fájl:** `u_szuropapir.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** félig kigördült pergamentekercs írássorokkal
- **Színvilág:** krémszínű pergamen; akcent: földbarna
- **Hangulat / lore:** A zagyból ez választja ki az esszenciát. És az igazságot. Alkimista kellék (csak boltból)

### 6121 — Katalizátor-só
- **Fájl:** `u_katalizator_so.png` &nbsp;|&nbsp; **Alap-item:** `GLOWSTONE_DUST`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** mézarany; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Egy csipet, és a főzet felgyorsul — két csipet, és új laborod lesz. Alkimista kellék (csak boltból)

### 6122 — Ólomdugó
- **Fájl:** `u_olomdugo.png` &nbsp;|&nbsp; **Alap-item:** `IRON_NUGGET`
- **Ábrázolás:** szerszámos láda / készlet-doboz
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Nehéz dugó illékony esszenciákhoz — ami bent van, bent is marad. Alkimista kellék (csak boltból)

### 6123 — Lombik-szén
- **Fájl:** `u_lombik_szen.png` &nbsp;|&nbsp; **Alap-item:** `CHARCOAL`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Egyenletes lángot ad a lombik alá — a türelem tüzelője. Alkimista kellék (csak boltból)

### 6124 — Írnok-tinta
- **Fájl:** `u_irnok_tinta.png` &nbsp;|&nbsp; **Alap-item:** `INK_SAC`
- **Ábrázolás:** derengő tintazsák / fénylő gyöngy
- **Színvilág:** éjkék; akcent: világító cián
- **Hangulat / lore:** A Számvevők receptje szerint kevert tinta — nem fakul, nem hazudik. Bűvölő kellék (csak boltból)

### 6125 — Pergamen-simító
- **Fájl:** `u_pergamen_simito.png` &nbsp;|&nbsp; **Alap-item:** `BONE`
- **Ábrázolás:** félig kigördült pergamentekercs írássorokkal
- **Színvilág:** krémszínű pergamen; akcent: földbarna
- **Hangulat / lore:** Csontsimító a pergamenhez — rúna csak sima lapra ül meg. Bűvölő kellék (csak boltból)

### 6126 — Ezüst-toll
- **Fájl:** `u_ezust_toll.png` &nbsp;|&nbsp; **Alap-item:** `FEATHER`
- **Ábrázolás:** írótoll tintával
- **Színvilág:** hűvös ezüstszürke; akcent: éjkék
- **Hangulat / lore:** Ezüsthegyű toll — a fontos szavakat nem bízzuk lúdtollra. Bűvölő kellék (csak boltból)

### 6127 — Rúnakréta
- **Fájl:** `u_runakreta.png` &nbsp;|&nbsp; **Alap-item:** `CLAY_BALL`
- **Ábrázolás:** egyenes rúd/pálca forma
- **Színvilág:** törtfehér; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Előrajzoló kréta rúnákhoz — a hiba itt még letörölhető. Utána nem. Bűvölő kellék (csak boltból)

### 6128 — Viasz-gyertya
- **Fájl:** `u_viaszgyertya.png` &nbsp;|&nbsp; **Alap-item:** `CANDLE`
- **Ábrázolás:** égő gyertya
- **Színvilág:** mézarany; akcent: borostyánsárga
- **Hangulat / lore:** Méhviasz gyertya éjszakai munkához — a rúna fénynél születik, de sötétben ég. Bűvölő kellék (csak boltból)

### 6129 — Horogkészlet
- **Fájl:** `u_horogkeszlet.png` &nbsp;|&nbsp; **Alap-item:** `TRIPWIRE_HOOK`
- **Ábrázolás:** fém horgászhorog
- **Színvilág:** világos acélszürke; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Tucatnyi kovácsolt horog — a Bokic pontya válogatós. Halász kellék (csak boltból)

### 6130 — Csalizsír
- **Fájl:** `u_csalizsir.png` &nbsp;|&nbsp; **Alap-item:** `SLIME_BALL`
- **Ábrázolás:** kis hal oldalnézetből
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: borostyánsárga
- **Hangulat / lore:** Illatos zsír a csalira — a titkos összetevőt a halak sem tudják. Halász kellék (csak boltból)

### 6131 — Hálófonal
- **Fájl:** `u_halofonal.png` &nbsp;|&nbsp; **Alap-item:** `STRING`
- **Ábrázolás:** csomózott háló
- **Színvilág:** türkizes viharzöld; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Viaszolt fonal hálókötéshez — nem rohad, nem szakad, nem felejt. Halász kellék (csak boltból)

### 6132 — Parafa-úszó
- **Fájl:** `u_parafa_uszo.png` &nbsp;|&nbsp; **Alap-item:** `OAK_BUTTON`
- **Ábrázolás:** piros-fehér horgászúszó
- **Színvilág:** meleg fabarna; akcent: törtfehér
- **Hangulat / lore:** Festett úszó — ha megbillen, a víz alatt eldőlt valami. Halász kellék (csak boltból)

### 6133 — Sózott csali
- **Fájl:** `u_sozott_csali.png` &nbsp;|&nbsp; **Alap-item:** `DRIED_KELP`
- **Ábrázolás:** kis hal oldalnézetből
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: borostyánsárga
- **Hangulat / lore:** Hordóban érlelt csali — messziről érzik. A halak szerint finom. Halász kellék (csak boltból)

### 6134 — Kősó
- **Fájl:** `u_koso.png` &nbsp;|&nbsp; **Alap-item:** `SUGAR`
- **Ábrázolás:** kristályos só-kupac
- **Színvilág:** törtfehér; akcent: hűvös ezüstszürke
- **Hangulat / lore:** A Menedék sóbányáiból — étel nélküle csak takarmány. Szakács kellék (csak boltból)

### 6135 — Sütőpergamen
- **Fájl:** `u_sutopergamen.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** félig kigördült pergamentekercs írássorokkal
- **Színvilág:** krémszínű pergamen; akcent: földbarna
- **Hangulat / lore:** Viaszolt lap a kemencébe — a lepény alja is megérdemli a tiszteletet. Szakács kellék (csak boltból)

### 6136 — Ecet-eszencia
- **Fájl:** `u_ecet_eszencia.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** sötét méregzöld; akcent: világító cián
- **Hangulat / lore:** Hétszer erjesztett eszencia — egy csepp tartósít, kettő büntet. Szakács kellék (csak boltból)

### 6137 — Füstölőforgács
- **Fájl:** `u_fustoloforgacs.png` &nbsp;|&nbsp; **Alap-item:** `STICK`
- **Ábrázolás:** pálca/bot, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Válogatott keményfa-forgács — a füst a láthatatlan fűszer. Szakács kellék (csak boltból)

### 6138 — Friss Vaj
- **Fájl:** `u_vaj.png` &nbsp;|&nbsp; **Alap-item:** `HONEYCOMB`
- **Ábrázolás:** nagy, nehéz csepp-forma
- **Színvilág:** mézarany; akcent: borostyánsárga
- **Hangulat / lore:** A Bokic-parti tanyák köpült vaja — amin ez megolvad, az már ünnep. Szakács kellék (csak boltból)

## Kulcsok és tervrajz (6201–6210)

### 6201 — Kereskedő Kulcs
- **Fájl:** `key_koznapi.png` &nbsp;|&nbsp; **Alap-item:** `TRIPWIRE_HOOK`
- **Ábrázolás:** egyszerű vas kulcs, karikás fejjel
- **Színvilág:** világos acélszürke; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Kereskedő Kulcs — a Caldesterai Kereskedőláda nyitja. Hétköznapi, strapabíró darab.

### 6202 — Kincsesláda-kulcs
- **Fájl:** `key_ritka.png` &nbsp;|&nbsp; **Alap-item:** `TRIPWIRE_HOOK`
- **Ábrázolás:** díszes arany kulcs, ékköves, cizellált fejjel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: királylila
- **Hangulat / lore:** a Caldesterai Kincsesláda kulcsa — ritka, ünnepélyes, ötvösmunka.

### 6210 — Recept-tervrajz
- **Fájl:** `blueprint.png` &nbsp;|&nbsp; **Alap-item:** `KNOWLEDGE_BOOK`
- **Ábrázolás:** kék tervrajz-lap fehér szerkesztési vonalakkal, egyik sarka felpöndörödik
- **Színvilág:** középkék; akcent: törtfehér
- **Hangulat / lore:** Recept-tervrajz — ebből tanulják a mesterek a ritka recepteket.

## Recept-tárgyak (6300–6438)

### 6300 — Borostyánfényű Lámpás
- **Fájl:** `r_borostyan_lampa.png` &nbsp;|&nbsp; **Alap-item:** `LANTERN`
- **Ábrázolás:** kerek pite / sütemény
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** A mélység gyantája ég benne — fénye nyugtatja a tárnák szellemeit. Bányász-recept eredménye (Ritkaság kategória, 39. szint).

### 6301 — A Mélység Szíve
- **Fájl:** `r_melyseg_szive.png` &nbsp;|&nbsp; **Alap-item:** `HEART_OF_THE_SEA`
- **Ábrázolás:** sötétkék, erezett szív-drágakő, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** A legmélyebb tárna alján dobog valami. A bányászcéh legendás mesterműve. Bányász-recept eredménye (Ritkaság kategória, 50. szint).

### 6302 — Jégvirág-koszorú
- **Fájl:** `r_jegvirag_koszoru.png` &nbsp;|&nbsp; **Alap-item:** `BLUE_ORCHID`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Cryghaliris kertjeinek dísze — nem hervad el soha. Gyógynövényész-recept eredménye (Ritkaság kategória, 34. szint).

### 6303 — Fonnyadt Rózsa-oltvány
- **Fájl:** `r_wither_rozsa_oltvany.png` &nbsp;|&nbsp; **Alap-item:** `WITHER_ROSE`
- **Ábrázolás:** fiatal csemete/hajtás
- **Színvilág:** élénk levélzöld; akcent: földbarna
- **Hangulat / lore:** A Kitaszítottak kertjének virága. Aki megszagolja, megborzong. Gyógynövényész-recept eredménye (Ritkaság kategória, 44. szint).

### 6304 — Örök Virágzás Csokra
- **Fájl:** `r_orok_viragzas.png` &nbsp;|&nbsp; **Alap-item:** `PEONY`
- **Ábrázolás:** virág / virágcsokor
- **Színvilág:** rózsás pír; akcent: élénk levélzöld
- **Hangulat / lore:** Ryanora mezőinek emléke — a csokor sosem hullajtja szirmát. Gyógynövényész-recept eredménye (Ritkaság kategória, 48. szint).

### 6305 — A Világfa Magja
- **Fájl:** `r_vilagfa_magja.png` &nbsp;|&nbsp; **Alap-item:** `OAK_SAPLING`
- **Ábrázolás:** fiatal csemete/hajtás
- **Színvilág:** élénk levélzöld; akcent: földbarna
- **Hangulat / lore:** Azt mondják, az Első Fa magja. Ültesd el, és figyeld, mi nő belőle. Gyógynövényész-recept eredménye (Ritkaság kategória, 50. szint).

### 6306 — Vándorbot
- **Fájl:** `r_vandorbot.png` &nbsp;|&nbsp; **Alap-item:** `STICK`
- **Ábrázolás:** pálca/bot, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Egyszerű bot, ezer mérföld emléke. A vándor sosem hagyja el. Favágó-recept eredménye (Ritkaság kategória, 33. szint).

### 6307 — Erdők Kürtje
- **Fájl:** `r_erdok_kurtje.png` &nbsp;|&nbsp; **Alap-item:** `GOAT_HORN`
- **Ábrázolás:** ívelt kürt, a névhez illő tematikus díszítéssel
- **Színvilág:** törtfehér csontszín; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Mélyen zengő hang: a fák válaszolnak rá. Aki hallja, hazatalál. Favágó-recept eredménye (Ritkaság kategória, 47. szint).

### 6308 — Vasfa Íj
- **Fájl:** `r_vasfa_ij.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral
- **Színvilág:** meleg fabarna; akcent: törtfehér
- **Hangulat / lore:** A vasfa nem hajlik — a mester keze alatt mégis. Favágó-recept eredménye (Ritkaság kategória, 49. szint).

### 6309 — Az Erdő Szíve
- **Fájl:** `r_erdo_szive_totem.png` &nbsp;|&nbsp; **Alap-item:** `TOTEM_OF_UNDYING`
- **Ábrázolás:** faragott totemfigura, a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: élénk levélzöld
- **Hangulat / lore:** A favágócéhek legendája: az erdő egyszer visszaadja, amit elvettél tőle. Favágó-recept eredménye (Ritkaság kategória, 50. szint).

### 6310 — A Céhmester Üllője
- **Fájl:** `r_cehmester_ulloje.png` &nbsp;|&nbsp; **Alap-item:** `ANVIL`
- **Ábrázolás:** kovácsüllő, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Ezen az üllőn nem görbül el semmi. A kovácscéhek büszkesége. Kovács-recept eredménye (Ritkaság kategória, 50. szint).

### 6311 — Palackozott Vihar
- **Fájl:** `r_vihar_palack.png` &nbsp;|&nbsp; **Alap-item:** `WIND_CHARGE`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Egy marék szél, üvegbe zárva. Óvatosan a dugóval! Alkimista-recept eredménye (Ritkaság kategória, 46. szint).

### 6312 — A Bölcsek Köve
- **Fájl:** `r_bolcsek_kove.png` &nbsp;|&nbsp; **Alap-item:** `EXPERIENCE_BOTTLE`
- **Ábrázolás:** zöld derengésű palack, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Nem arannyá változtat — bölccsé. Az alkimista-céh végső titka. Alkimista-recept eredménye (Ritkaság kategória, 50. szint).

### 6313 — Csendülő Harang
- **Fájl:** `r_csendulo_harang.png` &nbsp;|&nbsp; **Alap-item:** `BELL`
- **Ábrázolás:** öntött harang, misztikus derengéssel
- **Színvilág:** éjkék; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Hangja messzire száll a fagyban. A Suttogók megállnak, ha megkondul. Bűvölő-recept eredménye (Ritkaság kategória, 27. szint).

### 6314 — Emlékek Könyve
- **Fájl:** `r_emlekek_konyve.png` &nbsp;|&nbsp; **Alap-item:** `WRITTEN_BOOK`
- **Ábrázolás:** megírt, veretes kódex, a névhez illő tematikus díszítéssel
- **Színvilág:** királylila; akcent: gyöngyházas, rózsás fehér
- **Hangulat / lore:** Üres lapjain néha idegen sorok jelennek meg — más korokból. Bűvölő-recept eredménye (Ritkaság kategória, 37. szint).

### 6315 — A Végtelen Kódex
- **Fájl:** `r_vegtelen_kodex.png` &nbsp;|&nbsp; **Alap-item:** `WRITTEN_BOOK`
- **Ábrázolás:** megírt, veretes kódex, a névhez illő tematikus díszítéssel
- **Színvilág:** királylila; akcent: mélyvörös
- **Hangulat / lore:** Minden lapja egy-egy elfeledett név. A bűvölőcéhek legféltettebb kincse. Bűvölő-recept eredménye (Ritkaság kategória, 50. szint).

### 6316 — Viharjelző Bója
- **Fájl:** `r_viharjelzo_boja.png` &nbsp;|&nbsp; **Alap-item:** `LANTERN`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** A kikötők őrszeme: ha pislog, vihar közeleg a Bokic felől. Halász-recept eredménye (Eszköz kategória, 32. szint).

### 6317 — Mesterhorgász Botja
- **Fájl:** `r_mestermuves_bot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Azt beszélik, ez a bot már akkor ránt, mielőtt a hal harapna. Halász-recept eredménye (Eszköz kategória, 40. szint).

### 6318 — Óceánjáró Térképe
- **Fájl:** `r_oceanjaro_terkep.png` &nbsp;|&nbsp; **Alap-item:** `MAP`
- **Ábrázolás:** kiterített térkép útvonallal és jelöléssel
- **Színvilág:** krémszínű pergamen; akcent: középkék
- **Hangulat / lore:** Olyan zátonyokat is jelöl, amiket még senki sem látott — még. Halász-recept eredménye (Ritkaság kategória, 48. szint).

### 6319 — A Bokic Áldása
- **Fájl:** `r_bokic_aldasa.png` &nbsp;|&nbsp; **Alap-item:** `TRIDENT`
- **Ábrázolás:** háromágú szigony, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** A folyó egyszer mindent visszaad. A halászcéhek legendás szigonya. Halász-recept eredménye (Ritkaság kategória, 50. szint).

### 6320 — Főnixfűszeres Szárny
- **Fájl:** `r_fonix_fuszeres_szarny.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_CHICKEN`
- **Ábrázolás:** sült hús, csonttal
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Csípős! A Perinfernicitas konyhájának kedvence — óvatosan vele. Szakács-recept eredménye (Étel kategória, 41. szint).

### 6321 — Lakodalmas Emeletes Torta
- **Fájl:** `r_lakodalmas_torta.png` &nbsp;|&nbsp; **Alap-item:** `CAKE`
- **Ábrázolás:** díszített torta
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: rózsás pír
- **Hangulat / lore:** Három emelet, három frakció békéje — legalább amíg a torta el nem fogy. Szakács-recept eredménye (Étel kategória, 45. szint).

### 6322 — A Kapu Lakomája
- **Fájl:** `r_kapu_lakomaja.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_GOLDEN_APPLE`
- **Ábrázolás:** ragyogó aranyalma, a névhez illő tematikus díszítéssel
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Egyetlen falat, és megérted, mit őrzött a Kapu túlsó oldala. Szakács-recept eredménye (Étel kategória, 50. szint).

### 6323 — Tárnász Csákány
- **Fájl:** `r_tarnasz_csakany_recept.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_PICKAXE`
- **Ábrázolás:** csákány
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** A Vasművek Akadémiájának mesterei szerint még a legmélyebb tárna is megnyílik előtte. Bányász-recept eredménye (Szerszám kategória, 15. szint).

### 6324 — Mélybányász Netherit Csákány
- **Fájl:** `r_netherit_csakany.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_PICKAXE`
- **Ábrázolás:** csákány
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** A Kárhozat Kapujából szüremlő netherit fejezi be ezt a mélybányász szerszámot. Bányász-recept eredménye (Szerszám (tervrajz) kategória, 48. szint).

### 6325 — Kővésett Fejsze
- **Fájl:** `r_kovilta_fejsze.png` &nbsp;|&nbsp; **Alap-item:** `STONE_AXE`
- **Ábrázolás:** fejsze/balta
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** Caldestera kovácsinasainak első próbája: durva kő, de biztos kézzel faragva. Favágó-recept eredménye (Szerszám kategória, 10. szint).

### 6326 — Vasfejsze
- **Fájl:** `r_vasfejsze.png` &nbsp;|&nbsp; **Alap-item:** `IRON_AXE`
- **Ábrázolás:** fejsze/balta
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** A kovácscéhek szabvány szerint edzik, hogy egyetlen favágó se álljon meg tőle. Favágó-recept eredménye (Szerszám kategória, 20. szint).

### 6327 — Gyémántfejsze
- **Fájl:** `r_gyemant_fejsze.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_AXE`
- **Ábrázolás:** fejsze/balta
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** Csiszolt gyémántél, mely a Vasművek Akadémiájának büszkesége az erdőkben. Favágó-recept eredménye (Szerszám kategória, 36. szint).

### 6328 — Erdőirtó Netherit Fejsze
- **Fájl:** `r_netherit_fejsze.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_AXE`
- **Ábrázolás:** fejsze/balta
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** A Kárhozat Kapujának netheritjével edzve, egyetlen fatörzs sem áll ellen neki. Favágó-recept eredménye (Szerszám (tervrajz) kategória, 48. szint).

### 6329 — Vaskard
- **Fájl:** `r_vaskard.png` &nbsp;|&nbsp; **Alap-item:** `IRON_SWORD`
- **Ábrázolás:** kard/penge, markolattal
- **Színvilág:** hűvös ezüstszürke; akcent: cserzett bőrbarna
- **Hangulat / lore:** Egyszerű, de megbízható penge — a caldesterai kovácsműhelyek alapja. Kovács-recept eredménye (Fegyver kategória, 8. szint).

### 6330 — Vassisak
- **Fájl:** `r_vas_sisak.png` &nbsp;|&nbsp; **Alap-item:** `IRON_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Vastag vaslemez, mely a kovácscéhek műhelyeiben nyeri el végső formáját. Kovács-recept eredménye (Páncél kategória, 10. szint).

### 6331 — Vascsizma
- **Fájl:** `r_vas_csizma.png` &nbsp;|&nbsp; **Alap-item:** `IRON_BOOTS`
- **Ábrázolás:** pár csizma
- **Színvilág:** cserzett bőrbarna; akcent: világos acélszürke
- **Hangulat / lore:** Szilárd talp és pántok, hogy a viselője bármely tárnában biztosan álljon lábán. Kovács-recept eredménye (Páncél kategória, 10. szint).

### 6332 — Vas Lábvért
- **Fájl:** `r_vas_lablemez.png` &nbsp;|&nbsp; **Alap-item:** `IRON_LEGGINGS`
- **Ábrázolás:** öntött fémrúd (ingot-forma)
- **Színvilág:** világos acélszürke; akcent: sötétebb, kékes acél
- **Hangulat / lore:** A Vasművek Akadémiájának szabott mintája, mely minden csatában szolgálatot tesz. Kovács-recept eredménye (Páncél kategória, 12. szint).

### 6333 — Bástya Pajzs
- **Fájl:** `r_bastya_pajzs_recept.png` &nbsp;|&nbsp; **Alap-item:** `SHIELD`
- **Ábrázolás:** címerpajzs
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Vasborítás és smaragd-veret — a kovácscéhek bástyaként állítják a viselő elé. Kovács-recept eredménye (Páncél kategória, 15. szint).

### 6334 — Gyémántkard
- **Fájl:** `r_gyemant_kard.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_SWORD`
- **Ábrázolás:** kard/penge, markolattal
- **Színvilág:** hűvös ezüstszürke; akcent: cserzett bőrbarna
- **Hangulat / lore:** A legkeményebb kő élesíti, a kovácsmesterek keze formálja tökéletes egyensúlyúvá. Kovács-recept eredménye (Fegyver kategória, 22. szint).

### 6335 — Gyémántsisak
- **Fájl:** `r_gyemant_sisak.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Csiszolt gyémántlemezek, melyeket csak a Vasművek Akadémiájának mesterei értenek. Kovács-recept eredménye (Páncél kategória, 24. szint).

### 6336 — Gyémánt Mellvért
- **Fájl:** `r_gyemant_mellvert.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_CHESTPLATE`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** A kovácscéhek legdrágább rendelése: tömör gyémánt, mely visszaveri a pengét. Kovács-recept eredménye (Páncél kategória, 26. szint).

### 6337 — Háromágú Szigony
- **Fájl:** `r_haromagu_szigony.png` &nbsp;|&nbsp; **Alap-item:** `TRIDENT`
- **Ábrázolás:** háromágú szigony, a névhez illő tematikus díszítéssel
- **Színvilág:** világító cián; akcent: középkék
- **Hangulat / lore:** A Bokic mélyéről merített erő, mit csak a víz szellemei adnak kölcsön. Kovács-recept eredménye (Fegyver kategória, 30. szint).

### 6338 — Sárkányvért
- **Fájl:** `r_sarkanyvert_recept.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_CHESTPLATE`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Kallan jégsárkányainak pikkelye ihlette hadi vért, a kovácscéhek büszkesége. Kovács-recept eredménye (Páncél (tervrajz) kategória, 40. szint).

### 6339 — Netherit Pallos
- **Fájl:** `r_netherit_kard.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** kard/penge, markolattal
- **Színvilág:** hűvös ezüstszürke; akcent: cserzett bőrbarna
- **Hangulat / lore:** A Kárhozat Kapuja körül gyűjtött netherit adja e pallos törhetetlen élét. Kovács-recept eredménye (Fegyver (tervrajz) kategória, 42. szint).

### 6340 — Netherit Csatasisak
- **Fájl:** `r_netherit_sisak.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Nether-portál sötét fémjéből kovácsolva, hogy viselője a csata hevében is álljon. Kovács-recept eredménye (Páncél (tervrajz) kategória, 45. szint).

### 6341 — Bajnok Elixírje
- **Fájl:** `r_bajnok_elixir.png` &nbsp;|&nbsp; **Alap-item:** `POTION`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Aetrinita áldása száll benne, hogy a bajnokok sose hátráljanak. Alkimista-recept eredménye (Ital (tervrajz) kategória, 48. szint).

### 6342 — Tartósság Tomus
- **Fájl:** `r_tartossag_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: jeges világoskék
- **Hangulat / lore:** Caldestera Akadémiájának elveszett rúnái, melyek örök tartást kölcsönöznek a fegyvernek. Bűvölő-recept eredménye (Bűvölés kategória, 8. szint).

### 6343 — Hatékonyság Tomus
- **Fájl:** `r_hatekonysag_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: borostyánsárga
- **Hangulat / lore:** Rúnaírás az Akadémia mélyéről — a kéz mozdulatait gyorsítja meg örökre. Bűvölő-recept eredménye (Bűvölés kategória, 12. szint).

### 6344 — Élesség Tomus
- **Fájl:** `r_eles_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: gyöngyházas, rózsás fehér
- **Hangulat / lore:** Ősi rúnatudás élesíti a pengét, melyet Caldestera bölcsei jegyeztek le. Bűvölő-recept eredménye (Bűvölés kategória, 15. szint).

### 6345 — Zuhanáscsökkentés Tomus
- **Fájl:** `r_zuhanascsokkentes_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: bordó
- **Hangulat / lore:** Elfeledett rúnák, melyek könnyűvé teszik a legmagasabb zuhanást is. Bűvölő-recept eredménye (Bűvölés kategória, 16. szint).

### 6346 — Védelem Tomus
- **Fájl:** `r_vedelem_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: borostyánsárga
- **Hangulat / lore:** Az Akadémia rúnatára őrizte évszázadokon át, hogy pajzsként vonja be viselőjét. Bűvölő-recept eredménye (Bűvölés kategória, 18. szint).

### 6347 — Csali Tomus
- **Fájl:** `r_csali_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** kis hal oldalnézetből
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: borostyánsárga
- **Hangulat / lore:** Caldestera vízi rúnái, melyek a legmélyebb vizek lakóit is a felszínre csábítják. Bűvölő-recept eredménye (Bűvölés kategória, 20. szint).

### 6348 — Fosztogatás Tomus
- **Fájl:** `r_fosztogatas_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: gyöngyházas, rózsás fehér
- **Hangulat / lore:** Az elveszett rúnatudás egy szilánkja, mely bőségesebb zsákmányt ígér. Bűvölő-recept eredménye (Bűvölés kategória, 26. szint).

### 6349 — Szerencse Tomus
- **Fájl:** `r_szerencse_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: borostyánsárga
- **Hangulat / lore:** Az Akadémia legritkább rúnája — állítólag Asterlayna csillagfényéből őrződött meg. Bűvölő-recept eredménye (Bűvölés kategória, 30. szint).

### 6350 — Javítás Tomus
- **Fájl:** `r_javitas_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: izzó narancsvörös
- **Hangulat / lore:** Rúnaírás, mely a tapasztalat erejét a legkopottabb pengébe is visszaönti. Bűvölő-recept eredménye (Bűvölés (tervrajz) kategória, 40. szint).

### 6351 — Örvénylő Pusztítás Tomus
- **Fájl:** `r_orvenylo_pusztitas_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: jeges világoskék
- **Hangulat / lore:** Netherit-porral kevert ősi rúna, mely pusztító örvényt zár a fegyverbe. Bűvölő-recept eredménye (Bűvölés (tervrajz) kategória, 45. szint).

### 6352 — Selyemérintés Tomus
- **Fájl:** `r_selyemerintes_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: mélyvörös
- **Hangulat / lore:** Az Akadémia legfinomabb rúnaírása, mely a kéz érintését selyemmé szelídíti. Bűvölő-recept eredménye (Bűvölés (tervrajz) kategória, 48. szint).

### 6353 — Egyszerű Horgászbot
- **Fájl:** `r_egyszeru_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Egyszerű bot a Bokic partjáról, de a víz szellemei így is figyelnek rá. Halász-recept eredménye (Szerszám kategória, 5. szint).

### 6354 — Tartós Horgászbot
- **Fájl:** `r_tartos_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Gyémánttal erősített nyél, mely a Bokic legmélyebb sodrásában sem törik el. Halász-recept eredménye (Szerszám kategória, 20. szint).

### 6355 — Mesteri Horgászbot
- **Fájl:** `r_mesteri_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Arannyal és smaragddal díszített mesterbot — a Bokic vizének szellemei is elismerik. Halász-recept eredménye (Szerszám kategória, 35. szint).

### 6356 — Legendás Horgászbot
- **Fájl:** `r_legendas_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Netherittel edzett zsinór, mely a Bokic legrégebbi, legvadabb lakóit is kifogja. Halász-recept eredménye (Szerszám (tervrajz) kategória, 45. szint).

### 6357 — Tengeristen Amulettje
- **Fájl:** `r_tengeristen_amulettje.png` &nbsp;|&nbsp; **Alap-item:** `CONDUIT`
- **Ábrázolás:** tengeri vezérlőmag (conduit), a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: világító cián
- **Hangulat / lore:** A tenger mélyének szíve dobog benne, egy elfeledett isten hagyatéka. Halász-recept eredménye (Alapanyag (tervrajz) kategória, 48. szint).

### 6358 — Aranyalma Lakoma
- **Fájl:** `r_aranyalma_lakoma.png` &nbsp;|&nbsp; **Alap-item:** `GOLDEN_APPLE`
- **Ábrázolás:** sült hús, csonttal
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Ünnepi lakoma, melyben a Fa áldása és az arany fénye egyaránt megcsillan. Szakács-recept eredménye (Étel (tervrajz) kategória, 45. szint).

### 6359 — Legendás Lakoma
- **Fájl:** `r_legendas_lakoma.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_GOLDEN_APPLE`
- **Ábrázolás:** ragyogó aranyalma, a névhez illő tematikus díszítéssel
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Aetrinita áldásával sütött lakoma, méltó egy legenda asztalára. Szakács-recept eredménye (Étel (tervrajz) kategória, 48. szint).

### 6360 — Esszenciált Vasvért
- **Fájl:** `r_esszencialt_vasvert.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_CHESTPLATE`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Tiszta Vasesszenciával itatott lemezek, melyek szinte élővé teszik a páncélt. Kovács-recept eredménye (Páncél kategória, 35. szint).

### 6361 — Rúnakovácsolt Penge
- **Fájl:** `r_runakovacsolt_penge.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** súlyos, sötét pengéjű kard, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Rúnaporral kovácsolt penge, mely az Akadémia elveszett tudását őrzi. Kovács-recept eredménye (Fegyver (tervrajz) kategória, 44. szint).

### 6362 — Vadbőr Vért
- **Fájl:** `r_vadbor_pancel.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_LEGGINGS`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Egy káoszkori fenevad esszenciája ivódott bele, vadságot kölcsönözve. Kovács-recept eredménye (Páncél kategória, 28. szint).

### 6363 — Árnyékméreg
- **Fájl:** `r_arnyekmereg.png` &nbsp;|&nbsp; **Alap-item:** `SPLASH_POTION`
- **Ábrázolás:** kupacba szórt finom por, pár csillanó szemcsével
- **Színvilág:** mély ibolyalila; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** A Suttogók receptje: Árnyékporból lepárolt méreg, mely elnyeli a fényt és az életet. Alkimista-recept eredménye (Ital (tervrajz) kategória, 38. szint).

### 6364 — Szörnymag Talizmán
- **Fájl:** `r_szornymag_talizman.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** derengő varázskönyv, misztikus derengéssel
- **Színvilág:** mélyvörös; akcent: szénfekete-szürke
- **Hangulat / lore:** Egy Szörny Mag lüktet a talizmán szívében, sötét energiát kölcsönözve viselőjének. Bűvölő-recept eredménye (Bűvölés (tervrajz) kategória, 40. szint).

### 6365 — Villámszilánk Pengéje
- **Fájl:** `r_ereklye_penge.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** finomszőrű régész-ecset
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: krémszínű pergamen
- **Hangulat / lore:** Fekete Villám Szilánkkal kovácsolva — a Hu. 698-as ég hasadékának ereje él benne. Kovács-recept eredménye (Fegyver (tervrajz) kategória, 50. szint).

### 6366 — Rúnafényes Bányászcsákány
- **Fájl:** `r_runafenyes_csakany.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_PICKAXE`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Rúnapor fénye vezeti a csákány élét a legmélyebb tárnák sötétjében is. Bányász-recept eredménye (Szerszám kategória, 32. szint).

### 6367 — Villámszilánkos Bányászsisak
- **Fájl:** `r_ereklyeszilankos_banyasisak.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_HELMET`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** világító cián; akcent: éjkék
- **Hangulat / lore:** Fekete Villám Szilánk izzik a sisak mélyén, megvilágítva a legsötétebb járatokat. Bányász-recept eredménye (Páncél (tervrajz) kategória, 50. szint).

### 6368 — Vasesszenciás Pajzs
- **Fájl:** `r_vasesszencias_pajzs.png` &nbsp;|&nbsp; **Alap-item:** `SHIELD`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Tiszta Vasesszenciával átitatott vaslemez, mely szinte magától állja a csapásokat. Kovács-recept eredménye (Páncél kategória, 30. szint).

### 6369 — Rézvértezet Lábvért
- **Fájl:** `r_rezvertezet_lablemez.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_LEGGINGS`
- **Ábrázolás:** öntött fémrúd (ingot-forma)
- **Színvilág:** világos acélszürke; akcent: sötétebb, kékes acél
- **Hangulat / lore:** Rezgő Rézötvözettel erősített lábvért, mely sosem veszti fényét a csatában. Kovács-recept eredménye (Páncél kategória, 33. szint).

### 6370 — Vadölő Csizma
- **Fájl:** `r_vadolo_csizma.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_BOOTS`
- **Ábrázolás:** pár csizma
- **Színvilág:** cserzett bőrbarna; akcent: világos acélszürke
- **Hangulat / lore:** Vad Esszenciával itatott talp, mely néma léptekkel követi a fenevadak nyomát. Kovács-recept eredménye (Páncél kategória, 32. szint).

### 6371 — Szörnyvért Mellvény
- **Fájl:** `r_szornyvert_mellveny.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_CHESTPLATE`
- **Ábrázolás:** sötét, súlyos mellvért, misztikus derengéssel
- **Színvilág:** mélyvörös; akcent: szénfekete-szürke
- **Hangulat / lore:** Egy Szörny Mag sötét ereje dobog e mellvény netherit-lemezei alatt. Kovács-recept eredménye (Páncél (tervrajz) kategória, 46. szint).

### 6372 — Villámszilánk Elixírje
- **Fájl:** `r_ereklye_elixir.png` &nbsp;|&nbsp; **Alap-item:** `POTION`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Fekete Villám Szilánk cseppjei oldódtak ebbe az elixírbe, a Hu. 698 emlékeként. Alkimista-recept eredménye (Ital (tervrajz) kategória, 49. szint).

### 6373 — Vasesszenciás Páncéltörés Tomus
- **Fájl:** `r_vasesszencias_paloscsapas_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** dugós üvegfiola, benne folyadékkal
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Tiszta Vasesszenciával írt rúna, mely a legkeményebb páncélt is megtöri. Bűvölő-recept eredménye (Bűvölés kategória, 30. szint).

### 6374 — Keményfa Íjkeret Tomus
- **Fájl:** `r_kemenyfa_ijkeret_tomus.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** díszes varázskönyv, a borítóján drágakővel
- **Színvilág:** királylila; akcent: mélyvörös
- **Hangulat / lore:** Keményfa Gerenda erezetébe vésett rúna, mely az íjkeretet acélos hajlékonnyá teszi. Bűvölő-recept eredménye (Bűvölés kategória, 27. szint).

### 6375 — Rézhorgony Horgászbot
- **Fájl:** `r_rezhorgany_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** öntött fémrúd (ingot-forma)
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: borostyánsárga
- **Hangulat / lore:** Rezgő Rézötvözet horgonyzza le e botot a Bokic legerősebb sodrásában is. Halász-recept eredménye (Szerszám kategória, 26. szint).

### 6376 — Mélytengeri Villámszigony
- **Fájl:** `r_melytengeri_ereklyeszigony.png` &nbsp;|&nbsp; **Alap-item:** `TRIDENT`
- **Ábrázolás:** háromágú szigony, a névhez illő tematikus díszítéssel
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: krémszínű pergamen
- **Hangulat / lore:** Fekete Villám Szilánk pulzál a szigony hegyén, mit a mélytenger sötétjéből hoztak fel. Halász-recept eredménye (Fegyver (tervrajz) kategória, 47. szint).

### 6377 — Kallan Szeletelője
- **Fájl:** `r_kallan_szeletelo.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Kallan első pikkelyeiből és ősi fenyőből faragva; a jégsárkányok inaiból font húr a legvastagabb vértet is átvágja. Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 45. szint).

### 6378 — Glatziendorfi Jégvért
- **Fájl:** `r_glatziendorfi_jegvert.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_CHESTPLATE`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Oly nehéz és fagyos, hogy viselőjét sziklaként rögzíti; az ellenség fegyverei tompán koppannak rajta. Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 50. szint).

### 6379 — Jégsárkány-Kantár
- **Fájl:** `r_jegsarkany_kantar.png` &nbsp;|&nbsp; **Alap-item:** `SADDLE`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Kemény bőr és sötét mágia; csak ezzel tartható kordában egy vad sárkány. Jobb katt egy hátason: tartós gyorsítás. Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 40. szint).

### 6380 — Pyralingradi Tűzköpő
- **Fájl:** `r_pyralingradi_tuzkopo.png` &nbsp;|&nbsp; **Alap-item:** `CROSSBOW`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Soleil papjai áldották meg; lövedékei a sivatagi vihar sebességével csapnak le. Kovács-recept eredménye (Lángoló Birodalom (tervrajz) kategória, 45. szint).

### 6381 — A Vérszavanna Agyara
- **Fájl:** `r_verszavanna_agyara.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** súlyos, sötét pengéjű kard, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Kovácsolásakor a lángok fekete füstöt okádtak; baltával a másik kézben emberfeletti erőt ád. Kovács-recept eredménye (Lángoló Birodalom (tervrajz) kategória, 50. szint).

### 6382 — Főnix-Tollköpeny
- **Fájl:** `r_fonix_tollkopeny.png` &nbsp;|&nbsp; **Alap-item:** `LEATHER_CHESTPLATE`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** I. Zhoris lángmadarainak tollaiból szőve; óv a hőségtől és a láva haragjától. Kovács-recept eredménye (Lángoló Birodalom (tervrajz) kategória, 40. szint).

### 6383 — Rúnavért-tekercs
- **Fájl:** `r_runavert_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** félig kigördült pergamentekercs írássorokkal
- **Színvilág:** krémszínű pergamen; akcent: királylila
- **Hangulat / lore:** Caldestera rúnaírnokainak védőírása — a bőrbe rótt rúna elissza a mágiát. Üllőn vihető páncélra. Bűvölő-recept eredménye (Rúnaírnok (tervrajz) kategória, 40. szint).

### 6384 — Fagyasztott Tavi Pisztráng
- **Fájl:** `r_fagyasztott_pisztrang.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_SALMON`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** A glatziendorfi tavak jégbe zárt kincse — a Fagy népe ezen él. Fogyasztva rövid felszívódás-pajzsot ad. Szakács-recept eredménye (Fagyott Királyság (konyha) kategória, 25. szint).

### 6385 — Fűszeres Főnixtojás-Rántotta
- **Fájl:** `r_fonixtojas_rantotta.png` &nbsp;|&nbsp; **Alap-item:** `PUMPKIN_PIE`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Vérszavanna reggelije: tojás, parázs és bátorság. Fogyasztva rövid tűz- ellenállást ad. Szakács-recept eredménye (Lángoló Birodalom (konyha) kategória, 25. szint).

### 6386 — Tiltott Kakaóbabos Sütemény
- **Fájl:** `r_kakaobabos_sutemeny.png` &nbsp;|&nbsp; **Alap-item:** `COOKIE`
- **Ábrázolás:** díszített torta
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: rózsás pír
- **Hangulat / lore:** Caldestera cukrászai Asterlayna Gyümölcsének hívják — a Bankárszövetség hivatalosan tiltja. Fogyasztva… robban egy kicsit. Szakács-recept eredménye (Menedék (konyha) kategória, 35. szint).

### 6387 — Mortengradi Hamukenyér
- **Fájl:** `r_mortengradi_hamukenyer.png` &nbsp;|&nbsp; **Alap-item:** `BREAD`
- **Ábrázolás:** frissen sült cipó/vekni
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** A Kitaszítottak kenyere: hamuval sütik, hogy a Királynő szolgái ne érezzék az élet szagát. Fogyasztva rövid éjjellátást ad. A Kitaszítottaknak nincs honvágyuk — nincs otthon. Szakács-recept eredménye (Kitaszítottak (konyha) kategória, 30. szint).

### 6388 — Vasművek Akadémiájának Csákánya
- **Fájl:** `r_vasmuvek_csakanya.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_PICKAXE`
- **Ábrázolás:** csákány
- **Színvilág:** sötétebb, kékes acél; akcent: meleg fabarna
- **Hangulat / lore:** A ryanorai Vasművek mestervizsga-darabja; éle megérzi az érc rejtett teléreit. Bányász-recept eredménye (Menedék (tervrajz) kategória, 45. szint).

### 6389 — Bokic-menti Horgászbot
- **Fájl:** `r_bokic_horgaszbot.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** horgászbot orsóval és zsinórral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** A Bokic halászai szerint a folyó annak ad kétszer, aki ismeri a vize énekét. Halász-recept eredménye (Menedék (tervrajz) kategória, 40. szint).

### 6390 — Smaragdkő Bankbetét
- **Fájl:** `r_smaragdko_bankbetet.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** élénk levélzöld; akcent: hűvös ezüstszürke
- **Hangulat / lore:** A Bankárszövetség pecsétes betétjegye — jobb-kattal beváltható Creutzérre. Bűvölő-recept eredménye (Menedék (tervrajz) kategória, 35. szint).

### 6391 — Szellemszarvas-Bűbáj
- **Fájl:** `r_szellemszarvas_bubaj.png` &nbsp;|&nbsp; **Alap-item:** `RABBIT_FOOT`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Arkynn ligeteinek ajándéka: hívó bűbáj, amely a ködből szólítja elő a Szellemszarvast. Jobb-katt: hátas-hívás (nem fogy el). Gyógynövényész-recept eredménye (Menedék (tervrajz) kategória, 45. szint).

### 6392 — Glatziendorfi Jégtörő
- **Fájl:** `r_glatziendorfi_jegtoro.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_AXE`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** A kikötő jégtörőinek mintájára kovácsolt csatabárd — ami egyszer megdermedt, azt ez a fejsze megtöri. Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 46. szint).

### 6393 — V. Miinus Haragja
- **Fájl:** `r_miinus_haragja.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** súlyos, sötét pengéjű kard, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** A megfagyott király pengéje: minél mélyebb a viselő sebe, annál hidegebben üt vissza. „A tél nem kegyelmez — emlékezik.” Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 50. szint).

### 6394 — Sárkánycsont Íj
- **Fájl:** `r_sarkanycsont_ij.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Kallan elhullott sárkányainak csontjából hajlított íj — nyila átüti a sorfalat. Kovács-recept eredménye (Fagyott Királyság (tervrajz) kategória, 44. szint).

### 6395 — I. Zhoris Lángnyelve
- **Fájl:** `r_zhoris_langnyelve.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Az első lángkirály kardja — a penge éle sosem hűl ki, s a sebek, amiket ejt, parázslanak, mint az emléke. Kovács-recept eredménye (Vérszavanna (tervrajz) kategória, 50. szint).

### 6396 — Napfogyatkozás
- **Fájl:** `r_napfogyatkozas.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Fekete obszidián-íj, amely akkor a legerősebb, amikor a nap lehunyja szemét — éjjel a nyila árnyékból csap le. Kovács-recept eredménye (Vérszavanna (tervrajz) kategória, 46. szint).

### 6397 — Sárkány-pörkölt
- **Fájl:** `r_sarkany_porkolt.png` &nbsp;|&nbsp; **Alap-item:** `RABBIT_STEW`
- **Ábrázolás:** gőzölgő tál leves/ragu
- **Színvilág:** meleg fabarna; akcent: borostyánsárga
- **Hangulat / lore:** Glatziendorf ünnepi étke: lassan fő, gyorsan melegít — a tenger íze és a tűzhely ereje egy tálban. Szakács-recept eredménye (Étel kategória, 30. szint).

### 6398 — Vándor Úti Kenyere
- **Fájl:** `r_vandor_uti_kenyer.png` &nbsp;|&nbsp; **Alap-item:** `BREAD`
- **Ábrázolás:** frissen sült cipó/vekni
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** Kétszer sütött, mézzel kent úti kenyér — a karavánok esküsznek rá. Szakács-recept eredménye (Étel kategória, 12. szint).

### 6399 — Bokic-parti Gyógytea
- **Fájl:** `r_bokic_gyogytea.png` &nbsp;|&nbsp; **Alap-item:** `HONEY_BOTTLE`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Hajnali szedésű füvek forrázata a Bokic partjáról — átmelegít és tisztán tart. Gyógynövényész-recept eredménye (Ital kategória, 24. szint).

### 6400 — Sárkánycsont Pajzs
- **Fájl:** `r_sarkanycsont_pajzs.png` &nbsp;|&nbsp; **Alap-item:** `SHIELD`
- **Ábrázolás:** címerpajzs, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Sárkánycsont-lemezekkel erősített pajzs — ami nekicsapódik, megdermed egy szívverésre. Kovács-recept eredménye (Páncél kategória, 40. szint).

### 6401 — Viharüveg Lámpás
- **Fájl:** `r_viharuveg_lampas.png` &nbsp;|&nbsp; **Alap-item:** `LANTERN`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Viharkvarc-szilánk ég az üveg mögött — a fénye nem alszik ki, mert a vihar sosem fárad el. Bűvölő-recept eredménye (Különleges kategória, 28. szint).

### 6402 — Fagypáncél Tekercse
- **Fájl:** `r_fagypancel_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** hatágú hópehely / jégkristály
- **Színvilág:** jeges világoskék; akcent: világító cián
- **Hangulat / lore:** Glatziendorf jégvértjének rúnája könyvbe írva — üllőnél páncélra vihető. Bűvölő-recept eredménye (Bűvölés kategória, 35. szint).

### 6403 — Főnixtoll Tekercse
- **Fájl:** `r_fonixtoll_tekercs.png` &nbsp;|&nbsp; **Alap-item:** `ENCHANTED_BOOK`
- **Ábrázolás:** izzó parázsdarab, felcsapó lángnyelvekkel
- **Színvilág:** izzó narancsvörös; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Soleil főnixeinek pihéjével írt rúna — üllőnél páncélra vihető tűz-oltalom. Bűvölő-recept eredménye (Bűvölés kategória, 35. szint).

### 6404 — Vérszavannai Vadlakoma
- **Fájl:** `r_vadlakoma.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_BEEF`
- **Ábrázolás:** sült hús, csonttal
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Nyílt tűzön, egészben sült vad, ahogy I. Zhoris vadászai ették győzelem után. Szakács-recept eredménye (Étel kategória, 30. szint).

### 6405 — Vándorünnep Lepénye
- **Fájl:** `r_vandorunnep_lepenye.png` &nbsp;|&nbsp; **Alap-item:** `PUMPKIN_PIE`
- **Ábrázolás:** frissen sült cipó/vekni
- **Színvilág:** borostyánsárga; akcent: mézarany
- **Hangulat / lore:** A Bokic-parti aratóünnep édes lepénye — egy szelet szerencse minden vándornak. Szakács-recept eredménye (Étel kategória, 30. szint).

### 6406 — Hamvak Lakomája
- **Fájl:** `r_hamvak_lakomaja.png` &nbsp;|&nbsp; **Alap-item:** `BEETROOT_SOUP`
- **Ábrázolás:** sült hús, csonttal
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** A Kitaszítottak ünnepi tála: kevés, keserű, de aki megosztja, az már nem idegen. Szakács-recept eredménye (Étel kategória, 30. szint).

### 6407 — A Mélység Népe Koronája
- **Fájl:** `r_melysegi_korona.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_HELMET`
- **Ábrázolás:** sötét, súlyos sisak, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Borostyánba dermedt idő és néma kristály egy koronában — az utolsó kovácsmű, amit a Mélység Népe hátrahagyott. Kovács-recept eredménye (Legendás (tervrajz) kategória, 50. szint).

### 6408 — Viharjáró Csizma
- **Fájl:** `r_viharjaro_csizma.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_BOOTS`
- **Ábrázolás:** hosszúkás kristályszilánk, éles törésfelületekkel
- **Színvilág:** türkizes viharzöld; akcent: világító cián
- **Hangulat / lore:** Aki viseli, a vihar előtt jár egy lépéssel — és a vihar ezt tiszteli. Kovács-recept eredménye (Legendás (tervrajz) kategória, 48. szint).

### 6409 — Eleftheria Fátyla
- **Fájl:** `r_eleftheria_fatyla.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_CHESTPLATE`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Lélekhamuból szőtt, könnyekkel hímzett vért — a Néma Királynő gyásza, páncéllá kovácsolva. Viselni kegy. És teher. Kovács-recept eredménye (Legendás (tervrajz) kategória, 50. szint).

### 6410 — Pecsétes Szerződés
- **Fájl:** `r_pecsetes_szerzodes.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** pecsétnyomó viaszpecséttel
- **Színvilág:** bordó; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A Bankárszövetség viaszával hitelesített üres szerződés — két fél, egy pecsét, és nincs többé vita. Bűvölő-recept eredménye (Különleges kategória, 30. szint).

### 6411 — Sarkfény-prizma
- **Fájl:** `r_sarkfeny_prizma.png` &nbsp;|&nbsp; **Alap-item:** `SEA_LANTERN`
- **Ábrázolás:** kőtábla vésett rúnajellel
- **Színvilág:** sötét méregzöld; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Cseppkőbe zárt északi fény — a szoba, ahol áll, sosem lesz igazán sötét. Bűvölő-recept eredménye (Különleges kategória, 38. szint).

### 6412 — Csontenyves Íjkar
- **Fájl:** `r_csontenyves_ijkar.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral, a névhez illő tematikus díszítéssel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Csontenyvvel rétegelt, kötéllel erősített íjkar — a Mélység Népe így készítette. Favágó-recept eredménye (Fegyver kategória, 34. szint).

### 6413 — Gyöngyház Talizmán
- **Fájl:** `r_gyongyhaz_talizman.png` &nbsp;|&nbsp; **Alap-item:** `NAUTILUS_SHELL`
- **Ábrázolás:** derengő tintazsák / fénylő gyöngy
- **Színvilág:** gyöngyházas, rózsás fehér; akcent: égszínkék
- **Hangulat / lore:** Hét pikkely, hét szín, egy kagylóházban — a Bokic halászainak szerencsehozója. Halász-recept eredménye (Különleges kategória, 32. szint).

### 6414 — Fűszeres Vándorhús
- **Fájl:** `r_fuszeres_vandorhus.png` &nbsp;|&nbsp; **Alap-item:** `COOKED_MUTTON`
- **Ábrázolás:** sült hús, csonttal
- **Színvilág:** mélyvörös; akcent: földbarna
- **Hangulat / lore:** Vándorfűszerrel érlelt, füstölt hús — hetekig eláll a nyeregtáskában. Szakács-recept eredménye (Étel kategória, 28. szint).

### 6415 — Megkent Csille
- **Fájl:** `r_csillekerek.png` &nbsp;|&nbsp; **Alap-item:** `MINECART`
- **Ábrázolás:** bányász-csille, a névhez illő tematikus díszítéssel
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** A tárnák olajozott vasparipája — nyikorgás nélkül fut a sínen. Bányász-recept eredménye (Eszköz kategória, 14. szint).

### 6416 — Tárnatájoló
- **Fájl:** `r_melysegi_tajolo.png` &nbsp;|&nbsp; **Alap-item:** `COMPASS`
- **Ábrázolás:** iránytű, a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: mélyvörös
- **Hangulat / lore:** A tű nem északra mutat — a legközelebbi tárnára. Bányász-recept eredménye (Eszköz kategória, 23. szint).

### 6417 — Bányamérnöki Távcső
- **Fájl:** `r_tavcso.png` &nbsp;|&nbsp; **Alap-item:** `SPYGLASS`
- **Ábrázolás:** kihúzható távcső, a névhez illő tematikus díszítéssel
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: világító cián
- **Hangulat / lore:** A bányamérnök szeme: meglátja a repedést, mielőtt omlana. Bányász-recept eredménye (Eszköz kategória, 27. szint).

### 6418 — Ereklye-kiemelő Készlet
- **Fájl:** `r_osi_ereklye_kiemeles.png` &nbsp;|&nbsp; **Alap-item:** `BRUSH`
- **Ábrázolás:** finomszőrű ecset, a névhez illő tematikus díszítéssel
- **Színvilág:** vörösréz, narancsos árnyalatokkal; akcent: krémszínű pergamen
- **Hangulat / lore:** Puha ecset, acél türelem — a múlt nem szereti a sietséget. Bányász-recept eredménye (Ritkaság kategória, 48. szint).

### 6419 — Vas Lópáncél
- **Fájl:** `r_vas_lopancel.png` &nbsp;|&nbsp; **Alap-item:** `IRON_HORSE_ARMOR`
- **Ábrázolás:** nyereg / lószerszám
- **Színvilág:** világos acélszürke; akcent: cserzett bőrbarna
- **Hangulat / lore:** A céh kovácsainak munkája: a ló is katonának érzi magát benne. Kovács-recept eredménye (Páncél kategória, 17. szint).

### 6420 — Arany Lópáncél
- **Fájl:** `r_arany_lopancel.png` &nbsp;|&nbsp; **Alap-item:** `GOLDEN_HORSE_ARMOR`
- **Ábrázolás:** öntött fémrúd (ingot-forma)
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: borostyánsárga
- **Hangulat / lore:** Aranyfüst-lemezzel futtatva — a parádék dísze. Kovács-recept eredménye (Páncél kategória, 27. szint).

### 6421 — Gyémánt Lópáncél
- **Fájl:** `r_gyemant_lopancel.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_HORSE_ARMOR`
- **Ábrázolás:** nyereg / lószerszám
- **Színvilág:** világos acélszürke; akcent: cserzett bőrbarna
- **Hangulat / lore:** Ritkább, mint a jó lovas. A céh büszkén veri rá a pecsétjét. Kovács-recept eredménye (Páncél kategória, 37. szint).

### 6422 — Újraélesztett Totem
- **Fájl:** `r_totem_ujraelesztes.png` &nbsp;|&nbsp; **Alap-item:** `TOTEM_OF_UNDYING`
- **Ábrázolás:** faragott totemfigura, a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: élénk levélzöld
- **Hangulat / lore:** Lélekhamuval újratöltve. Egyszer még visszaránt a peremről. Alkimista-recept eredménye (Ritkaság kategória, 48. szint).

### 6423 — Kristály-katalizátor
- **Fájl:** `r_kristaly_katalizator.png` &nbsp;|&nbsp; **Alap-item:** `END_CRYSTAL`
- **Ábrázolás:** lebegő kristály keretben, a névhez illő tematikus díszítéssel
- **Színvilág:** világító cián; akcent: királylila
- **Hangulat / lore:** Néma kristály szívvel ver — ne nézz bele túl sokáig. Alkimista-recept eredménye (Ritkaság kategória, 49. szint).

### 6424 — Felépülés Iránytűje
- **Fájl:** `r_felepules_iranytuje.png` &nbsp;|&nbsp; **Alap-item:** `RECOVERY_COMPASS`
- **Ábrázolás:** sötét iránytű derengő tűvel, a névhez illő tematikus díszítéssel
- **Színvilág:** meleg arany, sárga csillanásokkal; akcent: mélyvörös
- **Hangulat / lore:** Oda vezet vissza, ahol utoljára minden elveszett. Bűvölő-recept eredménye (Ritkaság kategória, 39. szint).

### 6425 — Mélység Vezérkürtje
- **Fájl:** `r_vezetokurt.png` &nbsp;|&nbsp; **Alap-item:** `CONDUIT`
- **Ábrázolás:** tengeri vezérlőmag (conduit), a névhez illő tematikus díszítéssel
- **Színvilág:** törtfehér csontszín; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** A mélység válaszol, ha megszólal. A halászcéhek őrzik a titkát. Halász-recept eredménye (Ritkaság kategória, 46. szint).

### 6426 — Vadászíj
- **Fájl:** `r_vadaszij.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral
- **Színvilág:** meleg fabarna; akcent: törtfehér
- **Hangulat / lore:** Favágó-recept eredménye (Eszköz kategória, 14. szint).

### 6427 — Erdőjáró Pajzs
- **Fájl:** `r_mefonott_pajzs.png` &nbsp;|&nbsp; **Alap-item:** `SHIELD`
- **Ábrázolás:** címerpajzs
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Favágó-recept eredménye (Eszköz kategória, 36. szint).

### 6428 — Feszített Szaruíj
- **Fájl:** `r_feszitett_szaru_ij.png` &nbsp;|&nbsp; **Alap-item:** `BOW`
- **Ábrázolás:** felajzott íj húrral
- **Színvilág:** meleg fabarna; akcent: törtfehér
- **Hangulat / lore:** Favágó-recept eredménye (Eszköz kategória, 41. szint).

### 6429 — Céhmesteri Számszeríj
- **Fájl:** `r_celkereszt_szamszerij.png` &nbsp;|&nbsp; **Alap-item:** `CROSSBOW`
- **Ábrázolás:** számszeríj
- **Színvilág:** meleg fabarna; akcent: világos acélszürke
- **Hangulat / lore:** Favágó-recept eredménye (Eszköz kategória, 44. szint).

### 6430 — Kovácsolt Láncing
- **Fájl:** `r_lancing.png` &nbsp;|&nbsp; **Alap-item:** `CHAINMAIL_CHESTPLATE`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Kovács-recept eredménye (Páncél kategória, 11. szint).

### 6431 — Kovácsolt Láncnadrág
- **Fájl:** `r_lancnadrag.png` &nbsp;|&nbsp; **Alap-item:** `CHAINMAIL_LEGGINGS`
- **Ábrázolás:** mellvért elölnézetből
- **Színvilág:** sötétebb, kékes acél; akcent: királylila
- **Hangulat / lore:** Kovács-recept eredménye (Páncél kategória, 13. szint).

### 6432 — Dudoros Hadipajzs
- **Fájl:** `r_pajzsdudor.png` &nbsp;|&nbsp; **Alap-item:** `SHIELD`
- **Ábrázolás:** címerpajzs
- **Színvilág:** világos acélszürke; akcent: meleg fabarna
- **Hangulat / lore:** Kovács-recept eredménye (Eszköz kategória, 31. szint).

### 6433 — Rostélyos Csatasisak
- **Fájl:** `r_pancelozott_sisakrostely.png` &nbsp;|&nbsp; **Alap-item:** `DIAMOND_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Kovács-recept eredménye (Páncél kategória, 43. szint).

### 6434 — Úszókészlet
- **Fájl:** `r_uszokeszlet.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** piros-fehér horgászúszó
- **Színvilág:** meleg fabarna; akcent: törtfehér
- **Hangulat / lore:** Halász-recept eredménye (Eszköz kategória, 8. szint).

### 6435 — Halászcsizma
- **Fájl:** `r_vizallo_csizma.png` &nbsp;|&nbsp; **Alap-item:** `LEATHER_BOOTS`
- **Ábrázolás:** pár csizma
- **Színvilág:** cserzett bőrbarna; akcent: világos acélszürke
- **Hangulat / lore:** Halász-recept eredménye (Páncél kategória, 16. szint).

### 6436 — Mélyvízi Horogsor
- **Fájl:** `r_melyvizi_horog.png` &nbsp;|&nbsp; **Alap-item:** `FISHING_ROD`
- **Ábrázolás:** fém horgászhorog
- **Színvilág:** világos acélszürke; akcent: hűvös ezüstszürke
- **Hangulat / lore:** Halász-recept eredménye (Eszköz kategória, 18. szint).

### 6437 — Teknőspáncél-sisak
- **Fájl:** `r_teknos_sisak.png` &nbsp;|&nbsp; **Alap-item:** `TURTLE_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Halász-recept eredménye (Páncél kategória, 30. szint).

### 6438 — Halászkalap
- **Fájl:** `r_halaszkalap.png` &nbsp;|&nbsp; **Alap-item:** `LEATHER_HELMET`
- **Ábrázolás:** sisak
- **Színvilág:** sötétebb, kékes acél; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Halász-recept eredménye (Páncél kategória, 15. szint).

## Bolt-különlegességek és nevesített loot (6450–6463)

### 6450 — Bokic-menti Sétapálca
- **Fájl:** `shop_6450.png` &nbsp;|&nbsp; **Alap-item:** `STICK`
- **Ábrázolás:** elegáns sétapálca arany fejjel — ránézésre úri bot, de a fej alatt penge sejlik
- **Színvilág:** meleg fabarna; akcent: meleg arany, sárga csillanásokkal
- **Hangulat / lore:** Bokic-menti Sétapálca (feketepiac): az őrség botot lát, a penge nem ért egyet.

### 6451 — Hamisított Menlevél
- **Fájl:** `shop_6451.png` &nbsp;|&nbsp; **Alap-item:** `PAPER`
- **Ábrázolás:** hivatalos irat vörös viaszpecséttel — kicsit TÚL tökéletes
- **Színvilág:** krémszínű pergamen; akcent: bordó
- **Hangulat / lore:** Hamisított Menlevél: a Bankárszövetség pecsétje… majdnem.

### 6460 — A Hetedik Vérháború Rozsdás Pengéje
- **Fájl:** `loot_6460.png` &nbsp;|&nbsp; **Alap-item:** `IRON_SWORD`
- **Ábrázolás:** rozsdamarta, csorba hosszúkard, régi vér sötét foltjaival
- **Színvilág:** sötétebb, kékes acél; akcent: földbarna
- **Hangulat / lore:** A Hetedik Vérháború Rozsdás Pengéje — egykor hadsereg-fegyver, ma néma harag.

### 6461 — Megrontott Elit Páncél
- **Fájl:** `loot_6461.png` &nbsp;|&nbsp; **Alap-item:** `CHAINMAIL_CHESTPLATE`
- **Ábrázolás:** szakadozott láncvért, a láncszemek közt hideg türkiz derengéssel
- **Színvilág:** sötétebb, kékes acél; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** Megrontott Elit Páncél — az eltűnt nemesek dicsőségének maradványa.

### 6462 — Fekete Csont
- **Fájl:** `loot_6462.png` &nbsp;|&nbsp; **Alap-item:** `BONE`
- **Ábrázolás:** koromfekete csontdarab, matt felülettel, hajszálvékony türkiz erezettel
- **Színvilág:** a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés
- **Hangulat / lore:** Fekete Csont — nem ég el, nem törik, nem felejt.

### 6463 — A Néma Királynő Suttogása
- **Fájl:** `loot_6463.png` &nbsp;|&nbsp; **Alap-item:** `NETHERITE_SWORD`
- **Ábrázolás:** éjsötét pengéjű kard, az él mentén vékony TÜRKIZ suttogás-fénnyel (lich-él)
- **Színvilág:** éjkék; akcent: hideg türkiz derengés (lich-fény)
- **Hangulat / lore:** A Néma Királynő Suttogása — nem penge: ígéret.

## Szándékosan CMD NÉLKÜL (nem kell textúra)

- A ~215 köteg-recept (deszka, rúd, liszt, sült hús…) vanilla árucikk — stackelnie
  kell a gyűjtött itemekkel, recept-hozzávaló és piaci áru.
- A lánc-köztes egydarabosok (netherit-sor, nautilus→vezérkürt, aranyalma→Kapu
  Lakomája, visszhang-szilánk, főzet-alapok, kezdő horgászbot, sablon-másolat).
- Lerakható munkablokkok (üllő, köszörű, pulpitus, mágneskő, térképasztal, kaptár,
  méztömb) — lerakva úgyis a vanilla blokk-modell él.
- Névtábla (a nevesített névtábla a saját nevét adná a mobokra) és a napi
  frakció-ételek (material alapján számítanak, bármely forrásból).

