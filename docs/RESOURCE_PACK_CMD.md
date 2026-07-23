# Resource Pack — ITEM_MODEL manifest és textúra-spec

Ez a fájl a pack-készítő és a textúragenerátor egyetlen bemenete. A plugin minden custom/egyedi tárgya modern `ITEM_MODEL` komponenst visel (`icesmp:<modell-id>`); régi numerikus modelladatot sehol nem használunk. A manifest sorai a configból/kódból származnak — a `Modell-id`, `Alap-item` és `Név` oszlopokat onnan generáljuk, a `Prompt-hint` a generátornak írt, tárgyra szabott vizuális leírás.

## Kimeneti fájlok

Egy `modell-id` (pl. `currency_red`) három pack-fájlt kap:

```text
assets/icesmp/items/<modell-id>.json
assets/icesmp/models/item/<modell-id>.json
assets/icesmp/textures/item/<modell-id>.png
```

Javasolt item-definition JSON:

```json
{ "model": { "type": "minecraft:model", "model": "icesmp:item/<modell-id>" } }
```

Javasolt modell JSON:

```json
{ "parent": "minecraft:item/generated", "textures": { "layer0": "icesmp:item/<modell-id>" } }
```

## Textúra-stílus

- **Méret:** 16×16 vagy 32×32 PNG. Egy packon belül egyetlen méretet válassz, ne keverd.
- **Háttér:** teljesen átlátszó; félátlátszó perempixel ne legyen.
- **Stílus:** vanilla-hű pixel art, bal-felső fényforrás, 1 px sötét külső kontúr, blur/anti-alias nélkül.
- **Sziluett:** az alap-item családja maradjon felismerhető — fegyver/szerszám 45°-os átlós póz, páncél frontális nézet, ital/fiola palack-sziluett.
- **Paletta:** tárgyanként 4–8 fő tónus; erős neon csak apró akcentként.
- **Konzisztencia:** egy kategória tagjai (pl. rúna-tomusok, valuták, láncpáncél-darabok) osszanak közös vázat — csak a megkülönböztető akcens térjen el, hogy a pack egységesnek hasson.
- **DARK-kötésű tárgy:** csont-törtfehér + éjfekete-lila alap, és a jellegzetes hideg türkiz „lich-fény" derengés a szemekben, rúnákban, pengeél mentén (a Néma Királyné élőhalott-fénye).

## Generátor-prompt

A manifest utolsó oszlopa (`Prompt-hint`) már tárgyra szabott, konkrét vizuális leírás — fő motívum + anyag + 1–2 szín/akcens —, nem sablon. A generátorba ezt fűzd a következő sablonba:

```text
vanilla Minecraft item icon, <size>x<size> pixel art, transparent background, <base_item> silhouette, <label>, <prompt_hint>, crisp pixels, no antialiasing
```

Ahol `<base_item>` az `Alap-item`, `<label>` a `Név`, `<prompt_hint>` pedig a `Prompt-hint` oszlop.

## Globális paletta

A faction-kötött tárgyak (a nevükben szereplő hely/örökség alapján) ezt az akcenst viseljék:

| Téma | Színek |
|---|---|
| RED / Perinfernicitas (Pyralingrad, Soleil, Főnix, Vérszavanna) | mélyvörös, parázs-narancs izzás, arany |
| BLUE / Cryghaliris (Glatziendorf, Kallan, jég/fagy, sárkány) | jégkék, ezüst, törtfehér, zúzmara-csillám |
| NEUTRAL / Ryanora-Caldestera (Bokic, Creutzér, Smaragdkő, céh) | kereskedő-arany, borostyán, zöld-okker |
| DARK / Kitaszítottak (Thanaopolis, Eleftheria, Néma Királyné) | csont-törtfehér, éjfekete-lila, hideg türkiz lich-fény |
| Mélység / tenger / prizmarin (Mélység Népe, tengeri ereklyék) | prizmarin-türkiz, gyöngyház, tengerkék |

## Manifest

| Kategória | Modell-id | Alap-item | Név | Prompt-hint |
|---|---|---|---|---|
| crate-key | `cratekey_koznapi` | `TRIPWIRE_HOOK` | Caldesterai Kereskedőláda | egyszerű vas ládakulcs, sárgaréz fej, kopott mindennapi fém |
| crate-key | `cratekey_ritka` | `TRIPWIRE_HOOK` | Caldesterai Kincsesláda | díszes arany ládakulcs, beágyazott ékkő, ragyogó vésett szár |
| factory | `blueprint` | `KNOWLEDGE_BOOK` | Recept-tervrajz | kék tervrajzlap fehér szerkesztővonalakkal, hajtott sarok, technikai rajz |
| factory | `money_pouch` | `LEATHER` | Kopott erszény | kopott bőrerszény kikandikáló arany érmékkel, zsinóros száj, gyűrött varrás |
| factory | `selection_wand` | `STICK` | Birtokmérő pálca | egyszerű mérőpálca zöld jelölőzsinórral, faragott markolat, mérnöki jelek |
| factory | `selection_wand_territory` | `BLAZE_ROD` | Határkijelölő pálca | arany admin-pálca kis lobogó zászlóval, izzó jelölőhegy, hivatali veret |
| factory | `siege_ram` | `TNT_MINECART` | Ostromgép | nehéz faltörő kos vasalt fejjel, tölgygerenda váz, kerekes talp |
| factory:capture | `capture_beast` | `LEAD` | Ősi Kötés Póráza | fonott bőrpányva hurokkal, zöld természet-jel, cserzett barna szíj |
| factory:capture | `capture_heart` | `ECHO_SHARD` | Nyughatatlan Szív | dobbanó sötét szív, izzó türkiz erek, éjfekete-lila hártya |
| factory:capture | `capture_necro` | `GHAST_TEAR` | Sötét Paktum-tekercs | sötét összetekert paktum-tekercs, viasz-koponyapecsét, fakó pergamen |
| factory:capture | `capture_seal` | `AMETHYST_SHARD` | Démon-pecsét | lila ametiszt pecsétszilánk, vésett démonrúna, hideg ibolyafény |
| factory:catalyst | `catalyst_archer` | `RABBIT_HIDE` | Soleil Vadásztarsolya | varrott bőr vadásztarsoly kilógó nyílvesszőkkel, Soleil arany-narancs napcímer, parázs derengés |
| factory:catalyst | `catalyst_assassin` | `FLINT` | Homály-szilánk | obszidiánfekete törött kőszilánk, éles penge-él, hideg türkiz árnyék-derengés |
| factory:catalyst | `catalyst_death_knight` | `WITHER_SKELETON_SKULL` | Néma Rúnakoponya | csont-törtfehér koponya, homlokán izzó türkiz rúnák, éjfekete-lila lich-derengés |
| factory:catalyst | `catalyst_demon_hunter` | `ENDER_EYE` | Hasadék Szeme | hasadó zöld-lila démonszem, örvénylő pupilla, ibolyaszínű repedésfény |
| factory:catalyst | `catalyst_druid` | `OAK_SAPLING` | Aetrinita Sarja | fiatal zöld csemete rügyekkel, borostyán-arany életfény, lágy természet-derengés |
| factory:catalyst | `catalyst_evoker` | `DRAGON_BREATH` | Sárkányvér-fiola | fiola örvénylő sárkánylehelettel, mélyvörös-lila füst, izzó belső mag |
| factory:catalyst | `catalyst_monk` | `BAMBOO` | Élet Ága | zöld bambuszszál csí-energiával, jádezöld derengés, tiszta harmatcsepp |
| factory:catalyst | `catalyst_paladin` | `BELL` | Hajnaltűz Harangja | arany harang hajnalfény-sugárzással, szent parázs-narancs ragyogás, vésett perem |
| factory:catalyst | `catalyst_priest` | `WHITE_CANDLE` | Asterlayna Gyertyája | fehér viaszgyertya szent lánggal, arany-fehér glória, meleg lágy fény |
| factory:catalyst | `catalyst_shaman` | `TOTEM_OF_UNDYING` | Ősvihar Totemje | faragott fatotem viharkék energiával, villám-erek, türkiz-arany derengés |
| factory:catalyst | `catalyst_warlock` | `SOUL_LANTERN` | Kárhozat Lámpása | vaskeretes lélek-lámpás kék-zöld lélektűzzel, sötét kárhozat-derengés |
| factory:catalyst | `catalyst_warrior` | `GOAT_HORN` | Sárkánykirály Kürtje | faragott harci kürt sárkánymotívummal, arany-vörös veret, mély réz-csillanás |
| factory:catalyst | `catalyst_wizard` | `ENCHANTED_BOOK` | Caldesterai Rúnakódex | bőrkötésű rúnakódex, izzó kék-arany glyphek, lebegő varázslapok |
| factory:currency | `currency_blue` | `PAPER` | Hópihér-veret | kék-ezüst érme, vésett hópehely-címer, hideg fémcsillanás |
| factory:currency | `currency_dark` | `PAPER` | Csontveret | csont-törtfehér érme, koponya-címer, hideg türkiz lich-derengés |
| factory:currency | `currency_neutral` | `PAPER` | Creutzér | arany-borostyán érme, kereskedő-mérleg címer, meleg fémcsillanás |
| factory:currency | `currency_red` | `PAPER` | Parázsló Parals | vörös-arany érme, lángnyelv-címer, parázs-narancs izzás |
| factory:spell | `spell_demonic_circle` | `REDSTONE` | Démoni Só | vörös-lila szórt por, izzó idéző-körminta, sötét mágia-derengés |
| factory:spell | `spell_expel_harm` | `BLAZE_ROD` | Kiűzés Botja | fényes szent bot, arany-fehér rúnaragyogás, gyógyító derengés |
| factory:spell | `spell_rune_strike` | `AMETHYST_SHARD` | Rúnakő | ametiszt rúnakristály, kék-fehér villámszikra, éles fénytörés |
| factory:spell | `spell_wild_mushroom` | `BROWN_MUSHROOM` | Vadgomba | erdei barna gomba, zöld spóra-akcent, nedves természetes fény |
| named-loot | `loot_elit_pancel` | `CHAINMAIL_CHESTPLATE` | Megrontott Elit Páncél | rozsdás-korom láncmellvért, megrontott sötét foltok, hideg türkiz szivárgás |
| named-loot | `loot_fekete_csont` | `BONE` | Fekete Csont | szurokfekete csont, füstös felület, halvány türkiz rontásfény |
| named-loot | `loot_nema_kiralyno` | `NETHERITE_SWORD` | A Néma Királynő Suttogása | sötét netherit penge, éle mentén hideg türkiz lich-fény, éjfekete-lila derengés |
| named-loot | `loot_rozsdas_penge` | `IRON_SWORD` | A Hetedik Vérháború Rozsdás Pengéje | ősi rozsdamarta vaskard, vérfoltos csorba él, kopott bőrmarkolat |
| shop | `shop_menlevel` | `PAPER` | Hamisított Menlevél | pecsétes menlevél-tekercs, hamis bankárszövetségi viaszjegy, sárgult pergamen |
| shop | `shop_setapalca` | `STICK` | Bokic-menti Sétapálca | elegáns úri sétapálca, rejtett penge sejtetése, fényes fanyél ezüstfejjel |
| profession-material | `acskapocs` | `IRON_NUGGET` | Ácskapocs | hajlított U-alakú kovácsolt vaskapocs, szürke fém rozsdafoltokkal, tompa csillanás |
| profession-material | `aranyfust_lemez` | `GOLD_NUGGET` | Aranyfüst-lemez | papírvékony arany fóliaréteg, tükröző sárgaarany felület, gyűrődő szélek |
| profession-material | `arnyekpor` | `SCULK_VEIN` | Árnyékpor | sötét kúszó erezet, fekete-lila szórt por, hideg türkiz derengés |
| profession-material | `arnygomba` | `CRIMSON_FUNGUS` | Mortengradi Árnygomba | kalapos gomba csont-törtfehér tönkkel, éjfekete-lila kalap, türkiz spórafény |
| profession-material | `aszalohalo` | `COBWEB` | Aszalóháló | sűrű szálú szárítóháló, poros törtfehér pókfonál, csomózott rács |
| profession-material | `csalizsir` | `SLIME_BALL` | Csalizsír | kocsonyás zsírgolyó, zavaros sárgásbarna massza, fénylő nyúlós felület |
| profession-material | `csillekenocs` | `SLIME_BALL` | Csillekenőcs | sűrű kenőzsír-gombóc, koromszürke olajos massza, fémes csillanás |
| profession-material | `csontenyv` | `BONE_MEAL` | Csontenyv | őrölt csontpor ragacsos csomókkal, sárgásfehér szemcsés massza, matt felület |
| profession-material | `dermedt_konnycsepp` | `GHAST_TEAR` | Dermedt Könnycsepp | megfagyott áttetsző könnycsepp, jégkék belső csillám, sima üvegszerű felület |
| profession-material | `desztillalt_esoviz` | `GLASS_BOTTLE` | Desztillált Esővíz | üvegfiola tiszta átlátszó vízzel, halvány kékes csillanás, apró buborékok |
| profession-material | `ecet_eszencia` | `HONEY_BOTTLE` | Ecet-eszencia | palack halványsárga áttetsző folyadékkal, savas borostyán árnyalat, könnyű pára |
| profession-material | `edzoolaj` | `MAGMA_CREAM` | Edzőolaj | sötét olajos gömb izzó belső maggal, mélybarna massza, meleg parázsfény |
| profession-material | `elso_csend_szilankja` | `ECHO_SHARD` | Az Első Csend Szilánkja | sötét kristályszilánk türkiz erekkel, éjfekete-lila test, hideg lich-derengés |
| profession-material | `emlekszilank` | `AMETHYST_SHARD` | Opálos Emlékszilánk | opálos áttetsző kristályszilánk, szivárványos gyöngyházfény, lágy lila csillogás |
| profession-material | `ercmoso_lug` | `GLASS_BOTTLE` | Ércmosó-lúg | üvegfiola maró zöldessárga lúggal, homályos vegyszer, halvány mérgező derengés |
| profession-material | `ezust_toll` | `FEATHER` | Ezüst-toll | finom pihés tollszál, csillogó ezüstös szálak, hűvös fémes fény |
| profession-material | `favedo_pac` | `INK_SAC` | Favédő pác | sötétbarna pácfolt-zsák, telített dióbarna festék, olajos fénylő csepp |
| profession-material | `fenoko` | `SMOOTH_STONE` | Fenőkő | lapos csiszolókő, sima szürke kőfelület, kopott élező barázdák |
| profession-material | `folyositoszer` | `BLAZE_POWDER` | Kovács-folyósítószer | izzó narancssárga por, parázsló arany szemcsék, meleg fémes csillám |
| profession-material | `fonixpihe` | `FEATHER` | Főnixpihe | lángoló pihetoll, parázs-narancs izzó szálak, mélyvörös arany csillogás |
| profession-material | `fujtatobor` | `RABBIT_HIDE` | Fújtatóbőr | varrott barna bőrdarab, gyűrött cserzett felület, kopott perem |
| profession-material | `fustoloforgacs` | `STICK` | Füstölőforgács | vékony fahasáb füstölő forgáccsal, világosbarna faerezet, kunkorodó forgács |
| profession-material | `gyantaoldo` | `HONEY_BOTTLE` | Gyantaoldó | palack borostyánszínű ragacsos oldószerrel, áttetsző gyantás folyadék, olajos fény |
| profession-material | `gyogy_kivonat` | `GLOW_BERRIES` | Gyógy-kivonat | fürtnyi világító bogyó, gyógyzöld belső ragyogás, borostyán csillogás |
| profession-material | `gyongyhaz_pikkely` | `PRISMARINE_SHARD` | Gyöngyház-pikkely | gyöngyházfényű halpikkely-szilánk, türkiz-zöld irizálás, sima fénylő él |
| profession-material | `halofonal` | `STRING` | Hálófonal | sodrott vékony fonál, nyersfehér len szálak, laza tekercs |
| profession-material | `holdezust_huzal` | `CHAIN` | Holdezüst Huzal | vékony ezüstös láncszemek, holdfényes fémes csillogás, hűvös kék árnyalat |
| profession-material | `horogkeszlet` | `TRIPWIRE_HOOK` | Horogkészlet | hajlított fém horgok fanyélen, csillanó acélhegyek, kopott szürke fém |
| profession-material | `irnok_tinta` | `INK_SAC` | Írnok-tinta | sötét tintazsák, mély fekete-kék festék, fénylő nedves csepp |
| profession-material | `jegviragpor` | `SUGAR` | Jégvirág-por | csillogó fehér kristálypor, jégkék szikrázó szemcsék, zúzmara-csillám |
| profession-material | `karhozat_parazs` | `FIRE_CHARGE` | A Kapu Parazsa | izzó tűzgömb, parázs-narancs magból lángnyelvek, mélyvörös füst |
| profession-material | `katalizator_so` | `GLOWSTONE_DUST` | Katalizátor-só | világító sárga sószemcsék, foszforeszkáló arany por, meleg derengés |
| profession-material | `kemenyfa_gerenda` | `STRIPPED_OAK_WOOD` | Keményfa Gerenda | csupasz tölgygerenda, világosbarna faerezet, egyenes gyalult felület |
| profession-material | `koso` | `SUGAR` | Kősó | durva szemcsés kristálysó, szürkésfehér ásványi szemek, matt csillanás |
| profession-material | `lampaolaj` | `GLOW_INK_SAC` | Finomított Lámpaolaj | világító olajzsák, borostyánsárga fénylő folyadék, meleg derengés |
| profession-material | `lelekhamu` | `GUNPOWDER` | Lélekhamu | finom szürke hamupor, halvány türkiz szikrák, kihűlt korom |
| profession-material | `lombik_szen` | `CHARCOAL` | Lombik-szén | sötét faszéndarab, matt fekete felület, apró parázsló repedések |
| profession-material | `melysegi_borostyan` | `RAW_GOLD` | Mélységi Borostyán | nyers borostyán-arany rög, meleg mézszínű áttetsző test, érdes felület |
| profession-material | `melysegi_iranytu` | `COMPASS` | Mélységi Iránytű-tű | kör alakú iránytű vörös mágnestűvel, sötét fémes tok, halvány derengés |
| profession-material | `merozsinor` | `STRING` | Mérőzsinór | csomózott mérőzsinór egyenletes jelölésekkel, viaszolt barna szál, feszes tekercs |
| profession-material | `nema_kristaly` | `AMETHYST_SHARD` | Néma Kristály | halványan derengő kristályszilánk, éjfekete-lila erezet, hideg türkiz lich-fény, hangtalan |
| profession-material | `nyelbor` | `LEATHER` | Nyélbőr | cserzett barna bőrcsík, tekercselt markolatszalag, kopott varrás, meleg földbarna |
| profession-material | `obszidian_szilank` | `FLINT` | Obszidián-szilánk | éles fekete vulkáni üvegszilánk, tükröző törésfelület, lila-fekete csillogás |
| profession-material | `olomdugo` | `IRON_NUGGET` | Ólomdugó | tömzsi szürke ólomrög, matt fémfelület, apró horpadások, hideg ónszürke |
| profession-material | `oltoviasz` | `HONEYCOMB` | Oltóviasz | sárgás méhviasz-lép, hatszöges mintázat, lágy fényes felület, borostyán-arany |
| profession-material | `osi_ereklyeszilank` | `NETHER_STAR` | Fekete Villám Szilánk | szilánkos sötét csillagmag, szétágazó fekete villámerek, ibolya energiaszikra |
| profession-material | `parafa_uszo` | `OAK_BUTTON` | Parafa-úszó | kerek parafadugó úszó, barnás szemcsés kéreg, piros-fehér festéscsík |
| profession-material | `parazsmag` | `BLAZE_POWDER` | Parázsmag | izzó parázsszemcse, narancsvörös magból szálló szikrák, arany fény |
| profession-material | `pergamen_simito` | `BONE` | Pergamen-simító | csiszolt fehér csont-simítóél, elefántcsont árnyalat, kopott fényes felület |
| profession-material | `permetezo_kanna` | `BUCKET` | Permetező-kanna | zöldes fém permetezőkanna, hosszú fúvóka, vízcseppek, foltos réz-zöld |
| profession-material | `polirpaszta` | `SUGAR` | Polírpaszta | krémes fehér csiszolópaszta halom, finom szemcsék, gyöngyházfényű csillanás |
| profession-material | `rezgo_rez_otvozet` | `COPPER_INGOT` | Rezgő Rézötvözet | vöröses réztömb, finom rezgéshullám-vésetek, patinás zöld foltok, fémes csillogás |
| profession-material | `robbantopor` | `GUNPOWDER` | Robbantópor | szürkésfekete lőporhalom, durva szemcsék, apró szikrák, kormos sötétszürke |
| profession-material | `runa_bastya` | `AMETHYST_SHARD` | Bástya Rúnája | kristályszilánk vésett pajzs-rúnával, acélkék glóbusz-fény, védő geometrikus jel |
| profession-material | `runa_elek` | `AMETHYST_SHARD` | Él Rúnája | kristályszilánk éles penge-glifával, ezüstszürke vágóél-vonalak, hideg fémfény |
| profession-material | `runa_fagy` | `AMETHYST_SHARD` | Fagy Rúnája | kristályszilánk jégkristály-rúnával, dermedt jégkék ragyogás, zúzmarás repedések |
| profession-material | `runa_lang` | `AMETHYST_SHARD` | Láng Rúnája | kristályszilánk lángnyelv-glifával, izzó tűzvörös parázsfény, narancs szikrázás |
| profession-material | `runa_moho` | `AMETHYST_SHARD` | Mohóság Rúnája | kristályszilánk kapzsi érme-rúnával, arany-smaragd csillogás, mohó pénzsóvár fény |
| profession-material | `runa_visszhang` | `AMETHYST_SHARD` | Visszhang Rúnája | kristályszilánk koncentrikus hanghullám-glifával, türkiz derengés, visszaverődő rezgésgyűrűk |
| profession-material | `runa_zapor` | `AMETHYST_SHARD` | Zápor Rúnája | kristályszilánk esőcsepp-rúnával, hűvös esőkék csíkok, csillogó vízcsepp-fény |
| profession-material | `runakreta` | `CLAY_BALL` | Rúnakréta | gömbölyű krétagolyó, poros fehér felület, halvány rúnakarcolatok, törtfehér |
| profession-material | `runapor` | `GLOWSTONE_DUST` | Rúnapor | izzó aranyszínű varázspor, szikrázó fényszemcsék, halvány rúnaglifák derengése |
| profession-material | `sarkanycsont_szilank` | `BONE` | Sárkánycsont-szilánk | hasított ősi sárkánycsont-szilánk, repedezett törtfehér, füstös szürke erezet |
| profession-material | `sarkfeny_cseppko` | `PRISMARINE_CRYSTALS` | Sarkfény-cseppkő | jégkék prizmakristály-csepp, sarki fény villódzása, ezüstös törtfehér ragyogás |
| profession-material | `sozott_csali` | `DRIED_KELP` | Sózott csali | aszalt sötétzöld hínárcsali, sókristály-bevonat, ráncos textúra, tengeri barnászöld |
| profession-material | `sutopergamen` | `PAPER` | Sütőpergamen | gyűrött krémfehér sütőpapír-lap, zsírfoltos felület, enyhén áttetsző szélek |
| profession-material | `suttogas_meghivo` | `ECHO_SHARD` | Suttogás | sötét sculk-szilánk, suttogó türkiz erezet, éjfekete-lila mélység, lich-derengés |
| profession-material | `szavannafu_kotel` | `VINE` | Szavannafű-kötél | sodrott száraz fűkötél, aranybarna szálak, kirojtosodott végek, szavannás okker |
| profession-material | `szorny_mag` | `ECHO_SHARD` | Szörny Mag | sötét sculk-mag szilánk, lüktető hideg türkiz erezet, éjfekete-lila burok |
| profession-material | `szuropapir` | `PAPER` | Szűrőpapír | kerekített fehér szűrőpapír-korong, finom rostos textúra, halvány nedvességfolt |
| profession-material | `tarnatamasz_szegecs` | `IRON_NUGGET` | Tárnatámasz-szegecs | zömök vas szegecsfej, kopott fémfelület, rozsdafoltok, iparos szürkésbarna |
| profession-material | `tiszta_vasesszencia` | `IRON_NUGGET` | Tiszta Vasesszencia | ragyogó ezüstös vasrög, tiszta tükörfényes felület, halvány kékes fémderengés |
| profession-material | `tozegkocka` | `PACKED_MUD` | Tőzegkocka | tömör sötétbarna tőzegkocka, rostos növényi textúra, nedves földbarna |
| profession-material | `uvegfiola_keszlet` | `GLASS_BOTTLE` | Üvegfiola-készlet | több áttetsző üvegfiola, parafadugók, halvány kékes csillanás, tiszta üveg |
| profession-material | `vad_esszencia` | `PHANTOM_MEMBRANE` | Vad Esszencia | áttetsző szürkészöld hártyaszárny-foszlány, foszforeszkáló erezet, vadállati zöldes derengés |
| profession-material | `vaj` | `HONEYCOMB` | Friss Vaj | halványsárga vajtömb, sima krémes felület, lágy fényes csillanás, tejsárga |
| profession-material | `vandorfuszer` | `COCOA_BEANS` | Vándorfűszer | marék barnás fűszerbab, borostyánsárga porszemcsék, meleg földbarna, illatos szárított |
| profession-material | `viaszgyertya` | `CANDLE` | Viasz-gyertya | krémfehér gyertyatest, apró aranyló láng, olvadt viaszcseppek, meleg gyertyafény |
| profession-material | `viaszpecset` | `HONEYCOMB` | Számvevő-pecsétviasz | borostyán-arany pecsétviasz-korong, dombornyomott számvevő-jel, meleg fényes felület |
| profession-material | `viharkvarc` | `QUARTZ` | Viharkvarc | fehér kvarckristály, belső villámszikra derengés, kékesfehér vihar-energia csillogás |
| profession-recipe:alapanyag-(tervrajz) | `tengeristen_amulettje` | `CONDUIT` | Tengeristen Amulettje | prizmarin-türkiz kagylószív, gyöngyház csillám, örvénylő tengerkék energia, apró korall-berakás |
| profession-recipe:bűvölés | `csali_tomus` | `ENCHANTED_BOOK` | Csali Tomus | kopott bőrkötésű tomus, halványzöld horog-rúna, csillogó csalilégy dísz |
| profession-recipe:bűvölés | `eles_tomus` | `ENCHANTED_BOOK` | Élesség Tomus | nyitott tomus, penge-fehér rúnák izzanak, éles kardél-motívum lapján |
| profession-recipe:bűvölés | `fagypancel_tekercs` | `ENCHANTED_BOOK` | Fagypáncél Tekercse | feltekert pergamen, jégkék fagykristály-rúnák, dermedt páncélpikkely minta |
| profession-recipe:bűvölés | `fonixtoll_tekercs` | `ENCHANTED_BOOK` | Főnixtoll Tekercse | tekercs izzó narancs főnixtollal, parázsló arany rúnaszalag, tűzcsóva |
| profession-recipe:bűvölés | `fosztogatas_tomus` | `ENCHANTED_BOOK` | Fosztogatás Tomus | kopott tomus, aranyérme-rúnák szórnak fényt, zsákmány-glyph a lapon |
| profession-recipe:bűvölés | `hatekonysag_tomus` | `ENCHANTED_BOOK` | Hatékonyság Tomus | tomus, sárga energiaszikra-rúnák, gyors csákány-motívum, lendületvonalak izzanak |
| profession-recipe:bűvölés | `kemenyfa_ijkeret_tomus` | `ENCHANTED_BOOK` | Keményfa Íjkeret Tomus | barnás fatextúrás tomus, íj-sziluett rúna, keményfa erezet, borostyán fény |
| profession-recipe:bűvölés | `szerencse_tomus` | `ENCHANTED_BOOK` | Szerencse Tomus | tomus smaragdzöld rúnákkal, négylevelű lóhere-glyph, szerencse-csillámok ragyognak |
| profession-recipe:bűvölés | `tartossag_tomus` | `ENCHANTED_BOOK` | Tartósság Tomus | acélszürke tomus, kovácsolt üllő-rúna, tartós vaspánt-keret, hideg fém csillanás |
| profession-recipe:bűvölés | `vasesszencias_paloscsapas_tomus` | `ENCHANTED_BOOK` | Vasesszenciás Páncéltörés Tomus | tomus, ezüst páncéltörő ék-rúna, vasesszencia-köd, repedt pikkely-glyph izzik |
| profession-recipe:bűvölés | `vedelem_tomus` | `ENCHANTED_BOOK` | Védelem Tomus | tomus arany pajzs-glyph-fel, védő aranyszín rúnakör, meleg fénykontúr |
| profession-recipe:bűvölés | `zuhanascsokkentes_tomus` | `ENCHANTED_BOOK` | Zuhanáscsökkentés Tomus | tomus lágy fehér toll-rúnával, világoskék lebegő pihe-glyph, könnyed fény |
| profession-recipe:bűvölés-(tervrajz) | `javitas_tomus` | `ENCHANTED_BOOK` | Javítás Tomus | tomus, türkiz tapasztalat-gömb rúnák, összeforró repedés-glyph, gyógyító fény |
| profession-recipe:bűvölés-(tervrajz) | `orvenylo_pusztitas_tomus` | `ENCHANTED_BOOK` | Örvénylő Pusztítás Tomus | tomus, örvénylő fehér pengeív-rúna, kavargó pusztítás-glyph, ezüst suhanás |
| profession-recipe:bűvölés-(tervrajz) | `selyemerintes_tomus` | `ENCHANTED_BOOK` | Selyemérintés Tomus | tomus, gyöngyházfehér selyemfonál-rúnák, lágy csillámló kézlenyomat-glyph, finom fény |
| profession-recipe:bűvölés-(tervrajz) | `szornymag_talizman` | `ENCHANTED_BOOK` | Szörnymag Talizmán | sötét talizmán, húsos szörnymag-rúna, mérgeszöld izzás, csontos amulett-keret |
| profession-recipe:eszköz | `celkereszt_szamszerij` | `CROSSBOW` | Céhmesteri Számszeríj | átlós réz-veretes tusa, borostyán-arany célkereszt, faragott céhpecsét a barna száron |
| profession-recipe:eszköz | `csillekerek` | `MINECART` | Megkent Csille | olajtól csillogó vasperemű csille, zsírozott tengely, réz-szegecses meleg barna láda |
| profession-recipe:eszköz | `feszitett_szaru_ij` | `BOW` | Feszített Szaruíj | átlós ívkar sárgás-borostyán csontlemez borítással, feszülő inhúros ideg |
| profession-recipe:eszköz | `mefonott_pajzs` | `SHIELD` | Erdőjáró Pajzs | fonott vesszőkeret zöld-okker levélmintával, faragott kéreg felület, réz-szegecses perem |
| profession-recipe:eszköz | `melysegi_tajolo` | `COMPASS` | Tárnatájoló | réz tok, borostyán számlap, izzó zöld-okker mágnestű, tárna-koromfoltok |
| profession-recipe:eszköz | `melyvizi_horog` | `FISHING_ROD` | Mélyvízi Horogsor | átlós sötét bambusznyél, mélykék zsinór, súlyozott ezüst horgok, moszatzöld cérnabevonat |
| profession-recipe:eszköz | `mestermuves_bot` | `FISHING_ROD` | Mesterhorgász Botja | átlós lakkozott mahagóni nyél, arany menetes zsinórvezető, díszes réz orsó, borostyán markolat |
| profession-recipe:eszköz | `pajzsdudor` | `SHIELD` | Dudoros Hadipajzs | domború vas-boglár középen, szürke acél korong, szegecses perem, csatakarcolt felület |
| profession-recipe:eszköz | `tavcso` | `SPYGLASS` | Bányamérnöki Távcső | réz tubus mérőskálával, borostyán lencse, gravírozott bányász-jelek, meleg barna bőrtekercs |
| profession-recipe:eszköz | `uszokeszlet` | `FISHING_ROD` | Úszókészlet | átlós vékony nádnyél, piros-fehér parafa úszó, könnyű zsinór, apró ólomsúlyok |
| profession-recipe:eszköz | `vadaszij` | `BOW` | Vadászíj | átlós tiszafa ívkar, barna bőrmarkolat, feszes ínhúr, egyszerű vadász-faragás |
| profession-recipe:eszköz | `viharjelzo_boja` | `LANTERN` | Viharjelző Bója | rozsdás vas kalitka, viharkék izzó gömb, sós tengeri patina, kötélgyűrű tetején |
| profession-recipe:fagyott-királyság-(konyha) | `fagyasztott_pisztrang` | `COOKED_SALMON` | Fagyasztott Tavi Pisztráng | dérlepte sült pisztráng jégkék bevonattal, ezüstös pikkelyek, hideg kristálycsillanás |
| profession-recipe:fagyott-királyság-(tervrajz) | `glatziendorfi_jegtoro` | `NETHERITE_AXE` | Glatziendorfi Jégtörő | jégkék pengeél zúzmarás ezüst fejjel, kék sárkánypikkely-gravírozás a nyélen, fagyos derengés |
| profession-recipe:fagyott-királyság-(tervrajz) | `glatziendorfi_jegvert` | `NETHERITE_CHESTPLATE` | Glatziendorfi Jégvért | ezüstkék mellvért zúzmara-mintás lemezekkel, jégszilánk-vállvédő, halvány kék sárkány-címer, dérrel bevont felület |
| profession-recipe:fagyott-királyság-(tervrajz) | `jegsarkany_kantar` | `SADDLE` | Jégsárkány-Kantár | jégkék bőrszíj ezüst csatokkal, kék sárkánypikkely-díszítés, zúzmara-csillám, apró fagyott tüskék |
| profession-recipe:fagyott-királyság-(tervrajz) | `kallan_szeletelo` | `BOW` | Kallan Szeletelője | ívelt jégkék íj csontfehér markolattal, ezüst húr, zúzmarás faragás, hideg kék derengés |
| profession-recipe:fagyott-királyság-(tervrajz) | `miinus_haragja` | `NETHERITE_SWORD` | V. Miinus Haragja | jégkék penge fagyott élekkel, ezüst keresztvas kék zafírral, zúzmara-erek, királyi hideg ragyogás |
| profession-recipe:fagyott-királyság-(tervrajz) | `sarkanycsont_ij` | `BOW` | Sárkánycsont Íj | jégsárkány-csontból faragott ív, kék zúzmara-berakás, ezüst húr, halvány kék pikkely-motívum a végeken |
| profession-recipe:fegyver | `csontenyves_ijkar` | `BOW` | Csontenyves Íjkar | átlós rétegelt íjtest, sárgás csontenyv-fény, sötét fahátlap, feszülő ínideg |
| profession-recipe:fegyver | `gyemant_kard` | `DIAMOND_SWORD` | Gyémántkard | átlós penge cián-fehér kristályéllel, ragyogó gyémántbetétek, ezüst keresztvas, tiszta kék csillanás |
| profession-recipe:fegyver | `haromagu_szigony` | `TRIDENT` | Háromágú Szigony | átlós hármas villafej, szürke acél szakák, kék-zöld tengeri csillanás, csavart nyél |
| profession-recipe:fegyver | `vaskard` | `IRON_SWORD` | Vaskard | átlós szürke acélpenge, csiszolt él, egyszerű vas keresztvas, bőrtekercs markolat |
| profession-recipe:fegyver-(tervrajz) | `ereklye_penge` | `NETHERITE_SWORD` | Villámszilánk Pengéje | átlós sötét netherit penge, fekete villám-erek, ibolya-fehér szikrák, arany-perem él |
| profession-recipe:fegyver-(tervrajz) | `melytengeri_ereklyeszigony` | `TRIDENT` | Mélytengeri Villámszigony | átlós háromágú fej, fekete villám-erek, ibolya-fehér szikra, mélykék tengeri fém |
| profession-recipe:fegyver-(tervrajz) | `netherit_kard` | `NETHERITE_SWORD` | Netherit Pallos | átlós sötét szemcsés netherit penge, arany-perem él, széles pallos, tompa fém csillanás |
| profession-recipe:fegyver-(tervrajz) | `runakovacsolt_penge` | `NETHERITE_SWORD` | Rúnakovácsolt Penge | átlós sötét netherit penge, izzó türkiz-kék rúnaglyphek az élen, arany-perem |
| profession-recipe:ital | `aranyfeny_mezsor` | `HONEY_BOTTLE` | Aranyfényű Mézsör | habos borostyánszín mézsör fakupában, aranyló buborékok, sűrű krémhab korona |
| profession-recipe:ital | `arnyeklikor` | `HONEY_BOTTLE` | Árnyéklikőr | sötét ibolyafekete likőr karcsú üvegben, türkiz derengés, árnyékos folyadék |
| profession-recipe:ital | `bokic_gyogytea` | `HONEY_BOTTLE` | Bokic-parti Gyógytea | gőzölgő borostyánszín gyógytea agyagbögrében, meleg aranyló pára, gyógyfű-levél |
| profession-recipe:ital | `caldesterai_gyogytea` | `HONEY_BOTTLE` | Caldesterai Gyógytea | gőzölgő zöldes gyógytea cserépcsészében, halvány pára, borostyán mézcsepp csillan |
| profession-recipe:ital | `hamvasztott_kave` | `HONEY_BOTTLE` | Hamvasztott Kávé | barnásfekete kávé csontszín csészében, sűrű gőz, hamvas szürke tajték |
| profession-recipe:ital | `jeghegyi_sor` | `HONEY_BOTTLE` | Jéghegyi Sör | jégkék habos sör dérlepte korsóban, ezüst buborékok, hideg pára |
| profession-recipe:ital | `jegkiraly_parlat` | `HONEY_BOTTLE` | Jégkirály Párlata | kristálytiszta párlat karcsú fiolában, jégkék csillanás, ezüst dérkoszorú |
| profession-recipe:ital | `kofejto_sore` | `HONEY_BOTTLE` | Kőfejtő Söre | sötét borostyán sör kőkorsóban, vastag krémhab, meleg barna árnyalat |
| profession-recipe:ital | `mortengradi_keseru` | `HONEY_BOTTLE` | Mortengrádi Keserű | sötét gesztenyebarna keserűlikőr csontpohárban, hamvas felszín, hideg türkiz derengés |
| profession-recipe:ital | `parazs_palinka` | `HONEY_BOTTLE` | Parázs Pálinka | tüzes narancsvörös pálinka kis üvegben, izzó parázs-csillanás, forró aranyfény |
| profession-recipe:ital | `szentelt_bor` | `HONEY_BOTTLE` | Szentelt Bor | mélyvörös szentelt bor kehelyben, aranyló szent fény, tiszta rubinragyogás |
| profession-recipe:ital | `tengeresz_rum` | `HONEY_BOTTLE` | Tengerész Rum | sötét borostyánbarna rum zömök palackban, aranyló csillanás, tengerész pecsét |
| profession-recipe:ital | `viharfi_almabor` | `HONEY_BOTTLE` | Viharfi Almabor | aranysárga almabor buborékos pohárban, halvány zöldes csillanás, viharkék peremfény |
| profession-recipe:ital-(tervrajz) | `arnyekmereg` | `SPLASH_POTION` | Árnyékméreg | dobóüveg sötét ibolyaszín méreggel, türkiz köd gomolyog, csontszín parafa |
| profession-recipe:ital-(tervrajz) | `bajnok_elixir` | `POTION` | Bajnok Elixírje | arany-vörös bajnoki elixír gömbfiolában, ragyogó fénypöttyök, hősi aranyglória |
| profession-recipe:ital-(tervrajz) | `ereklye_elixir` | `POTION` | Villámszilánk Elixírje | elektromos kék elixír fiolában, cikázó villámszilánk belül, szikrázó ezüst fény |
| profession-recipe:kazamata-kulcs | `csontkripta_kulcsa` | `TRIAL_KEY` | A Csontkripta Kulcsa | csont-törtfehér kulcs éjfekete fogazattal, türkiz lich-fény derengés, apró koponya-fejrész, ibolya árnyék |
| profession-recipe:kazamata-kulcs | `melyseg_kulcsa` | `TRIAL_KEY` | A Mélység Kulcsa | éjfekete-lila kulcs csontfogazattal, hideg türkiz derengés, mélységi korall-berakás, lich-fény szikrák |
| profession-recipe:kitaszítottak-(konyha) | `mortengradi_hamukenyer` | `BREAD` | Mortengradi Hamukenyér | hamuszürke cipó ropogós sötét héjjal, szénporos felszín, törtfehér bél |
| profession-recipe:különleges | `gyongyhaz_talizman` | `NAUTILUS_SHELL` | Gyöngyház Talizmán | spirális gyöngyház-kagyló szivárványos csillámmal, tengerkék perem, prizmarin fénylő mag, ezüst foglalat |
| profession-recipe:különleges | `pecsetes_szerzodes` | `PAPER` | Pecsétes Szerződés | sárguló pergamen kézírásos sorokkal, vörös viaszpecsét, arany zsinór, céh-címeres fejléc |
| profession-recipe:különleges | `sarkfeny_prizma` | `SEA_LANTERN` | Sarkfény-prizma | prizmarin kristálykocka szivárványos sarkfény-derengéssel, türkiz belső ragyogás, gyöngyház élek, hideg fénytörés |
| profession-recipe:különleges | `viharuveg_lampas` | `LANTERN` | Viharüveg Lámpás | üveglámpás örvénylő viharfelhővel belül, ezüst keret, kékesszürke villám-szikrák, ködös derengés |
| profession-recipe:legendás-(tervrajz) | `eleftheria_fatyla` | `NETHERITE_CHESTPLATE` | Eleftheria Fátyla | csont-törtfehér páncél fátyolszerű fekete-lila szövettel, türkiz lich-fény szegély, néma királynéi elegancia |
| profession-recipe:legendás-(tervrajz) | `melysegi_korona` | `NETHERITE_HELMET` | A Mélység Népe Koronája | csontfehér korona éjfekete tüskékkel, türkiz lich-fény ékkövek, mélységi korall-ágak, ibolya derengés |
| profession-recipe:legendás-(tervrajz) | `viharjaro_csizma` | `NETHERITE_BOOTS` | Viharjáró Csizma | sötétszürke páncélcsizma villám-erezettel, ezüst csatok, kékesfehér szikrák a talpon, viharfelhő-motívum |
| profession-recipe:lángoló-birodalom-(konyha) | `fonixtojas_rantotta` | `PUMPKIN_PIE` | Fűszeres Főnixtojás-Rántotta | aranysárga rántotta tányéron, izzó narancs fűszerpaprika, parázsló tűzcsóva-dísz |
| profession-recipe:lángoló-birodalom-(tervrajz) | `fonix_tollkopeny` | `LEATHER_CHESTPLATE` | Főnix-Tollköpeny | izzó narancs-vörös tollköpeny arany szegéllyel, parázsló toll-réteg, főnix-láng derengés, mélyvörös kapucni |
| profession-recipe:lángoló-birodalom-(tervrajz) | `pyralingradi_tuzkopo` | `CROSSBOW` | Pyralingradi Tűzköpő | sötét fém számszeríj parázs-narancs izzó csővel, arany berakás, mélyvörös láng-gravírozás, füstölgő perem |
| profession-recipe:lángoló-birodalom-(tervrajz) | `verszavanna_agyara` | `NETHERITE_SWORD` | A Vérszavanna Agyara | görbe agyar-penge mélyvörös erekkel, parázs-narancs izzó él, arany keresztvas, szavanna-csontmarkolat |
| profession-recipe:menedék-(konyha) | `kakaobabos_sutemeny` | `COOKIE` | Tiltott Kakaóbabos Sütemény | sötét csokoládébarna keksz kakaóbab-darabkákkal, repedezett felszín, gazdag kakaófény |
| profession-recipe:menedék-(tervrajz) | `bokic_horgaszbot` | `FISHING_ROD` | Bokic-menti Horgászbot | meleg barna fabot smaragdzöld zsinórral, borostyán úszó, apró smaragd-berakás, réz orsó |
| profession-recipe:menedék-(tervrajz) | `smaragdko_bankbetet` | `PAPER` | Smaragdkő Bankbetét | díszes bankjegy smaragdzöld nyomattal, arany peremkeret, viaszpecsét, ryanora-céh címere, borostyán vízjel |
| profession-recipe:menedék-(tervrajz) | `szellemszarvas_bubaj` | `RABBIT_FOOT` | Szellemszarvas-Bűbáj | áttetsző zöldes szellem-szarvasagancs talizmán, borostyán-arany zsinór, halvány smaragd derengés, erdei fűcsomó |
| profession-recipe:menedék-(tervrajz) | `vasmuvek_csakanya` | `DIAMOND_PICKAXE` | Vasművek Akadémiájának Csákánya | acélkék csákányfej gyémánt-berakással, meleg barna nyél, réz gyűrűk, vésett akadémia-címer, szikrázó él |
| profession-recipe:páncél | `arany_lopancel` | `GOLDEN_HORSE_ARMOR` | Arany Lópáncél | csillogó arany lemezvért, vésett lószügy-minta, meleg sárga fény, díszes szegély |
| profession-recipe:páncél | `bastya_pajzs_recept` | `SHIELD` | Bástya Pajzs | magas szögletes vaspajzs, szürke acél lemezek, bástya-oromzat minta, szegecses perem |
| profession-recipe:páncél | `esszencialt_vasvert` | `DIAMOND_CHESTPLATE` | Esszenciált Vasvért | frontális acélmellvért esszencia-erekkel, halvány cián-fehér izzás, csiszolt lemezek, díszes gerinc |
| profession-recipe:páncél | `gyemant_lopancel` | `DIAMOND_HORSE_ARMOR` | Gyémánt Lópáncél | cián-fehér kristálylemez lóvért, ragyogó gyémántbetétek, ezüst szegély, hideg kék csillanás |
| profession-recipe:páncél | `gyemant_mellvert` | `DIAMOND_CHESTPLATE` | Gyémánt Mellvért | frontális cián-fehér kristálymellvért, ragyogó gyémánt lemezek, ezüst gerinc, kék csillanás |
| profession-recipe:páncél | `gyemant_sisak` | `DIAMOND_HELMET` | Gyémántsisak | frontális cián-fehér kristálysisak, ragyogó gyémánt homloklemez, ezüst él, hideg kék fény |
| profession-recipe:páncél | `halaszkalap` | `LEATHER_HELMET` | Halászkalap | puha barna bőrkalap, karimás perem, viharvert varrás, sárgás halász-zsinór szalag |
| profession-recipe:páncél | `lancing` | `CHAINMAIL_CHESTPLATE` | Kovácsolt Láncing | fonott acélgyűrűk mellvértje, szürke fém szövet, kovácsolt gyűrűsorok, tompa csillanás |
| profession-recipe:páncél | `lancnadrag` | `CHAINMAIL_LEGGINGS` | Kovácsolt Láncnadrág | fonott acélgyűrű lábvért, szürke fém szövet, sűrű gyűrűsorok, csípő-lemez kapcsok |
| profession-recipe:páncél | `pancelozott_sisakrostely` | `DIAMOND_HELMET` | Rostélyos Csatasisak | frontális cián-fehér kristálysisak, leengedett acélrostély, függőleges rések, ezüst szegecsek |
| profession-recipe:páncél | `rezvertezet_lablemez` | `DIAMOND_LEGGINGS` | Rézvértezet Lábvért | frontális réz lemezű lábvért, borostyán fém csillanás, kalapált lemezek, zöldes patina-foltok |
| profession-recipe:páncél | `sarkanycsont_pajzs` | `SHIELD` | Sárkánycsont Pajzs | csontfehér sárkánybordás pajzskorong, jégkék erezet, zúzmara-csillám, faragott felület |
| profession-recipe:páncél | `teknos_sisak` | `TURTLE_HELMET` | Teknőspáncél-sisak | frontális zöld-barna páncélteknős-kupak, kagylóbordás mintázat, sárgás perem, nedves csillanás |
| profession-recipe:páncél | `vadbor_pancel` | `DIAMOND_LEGGINGS` | Vadbőr Vért | frontális barna vadbőr lábvért, prémes szegély, varrott bőrlemezek, okker foltok |
| profession-recipe:páncél | `vadolo_csizma` | `DIAMOND_BOOTS` | Vadölő Csizma | frontális megerősített vadászcsizma, cián-fehér kristálylemez orr, barna bőr, ezüst kapcsok |
| profession-recipe:páncél | `vas_csizma` | `IRON_BOOTS` | Vascsizma | frontális szürke acélcsizma, csiszolt lábfej-lemez, egyszerű szegecsek, tompa fém csillanás |
| profession-recipe:páncél | `vas_lablemez` | `IRON_LEGGINGS` | Vas Lábvért | frontális szürke acél lábvért, csiszolt combelemezek, egyszerű szegecssorok, tompa csillanás |
| profession-recipe:páncél | `vas_lopancel` | `IRON_HORSE_ARMOR` | Vas Lópáncél | szürke acél lemezű lóvért, egyszerű lószügy-lemez, kalapált felület, tompa fém csillanás |
| profession-recipe:páncél | `vas_sisak` | `IRON_HELMET` | Vassisak | frontális szürke acélsisak, csiszolt homloklemez, egyszerű orrvédő, tompa fém csillanás |
| profession-recipe:páncél | `vasesszencias_pajzs` | `SHIELD` | Vasesszenciás Pajzs | kerek acélpajzs esszencia-erekkel, halvány cián-fehér izzás, szürke fém, szegecses perem |
| profession-recipe:páncél | `vizallo_csizma` | `LEATHER_BOOTS` | Halászcsizma | magas barna gumírozott bőrcsizma, viaszos vízálló felület, sárgás varrás, sáros perem |
| profession-recipe:páncél-(tervrajz) | `ereklyeszilankos_banyasisak` | `DIAMOND_HELMET` | Villámszilánkos Bányászsisak | frontális bányászsisak fejlámpával, fekete villám-erek, ibolya-fehér szikra, cián-fehér kristályperem |
| profession-recipe:páncél-(tervrajz) | `netherit_sisak` | `NETHERITE_HELMET` | Netherit Csatasisak | frontális sötét szemcsés netherit sisak, arany-perem szegély, tompa fém, csatakarcolt homloklemez |
| profession-recipe:páncél-(tervrajz) | `sarkanyvert_recept` | `NETHERITE_CHESTPLATE` | Sárkányvért | frontális pikkelyes netherit mellvért, sötét fém pikkelyek, arany-perem gerinc, vöröses izzás |
| profession-recipe:páncél-(tervrajz) | `szornyvert_mellveny` | `NETHERITE_CHESTPLATE` | Szörnyvért Mellvény | frontális csontos netherit mellvért, sötét szemcsés lemezek, tüskés szegély, vöröses-arany izzás |
| profession-recipe:ritkaság | `bokic_aldasa` | `TRIDENT` | A Bokic Áldása | smaragdzöld szigony borostyán-arany ággal, gyöngyöző vízcseppek, meleg barna nyél, áldó zöld derengés |
| profession-recipe:ritkaság | `bolcsek_kove` | `EXPERIENCE_BOTTLE` | A Bölcsek Köve | kis fiola izzó vörös-arany kővel, ősi ereklye-fény, ibolya-fehér szikrák, örvénylő aranypor |
| profession-recipe:ritkaság | `borostyan_lampa` | `LANTERN` | Borostyánfényű Lámpás | réz lámpás meleg borostyán izzással, arany fénysugarak, apró rovar-zárvány az üvegben, patinás keret |
| profession-recipe:ritkaság | `cehmester_ulloje` | `ANVIL` | A Céhmester Üllője | kovácsolt sötét vasüllő arany céh-címerrel, parázsló szikrák, réz peremdísz, kalapács-nyom kopás |
| profession-recipe:ritkaság | `csendulo_harang` | `BELL` | Csendülő Harang | patinás bronzharang arany peremmel, vésett hullám-minta, halvány arany hangrezgés-derengés, csüngő kötél |
| profession-recipe:ritkaság | `emlekek_konyve` | `WRITTEN_BOOK` | Emlékek Könyve | kopott barna bőrkötésű könyv arany veretekkel, halvány derengő lapok, préselt virág, ezüst csat |
| profession-recipe:ritkaság | `erdo_szive_totem` | `TOTEM_OF_UNDYING` | Az Erdő Szíve | smaragdzöld faragott fa-totem lüktető zöld magmával, borostyán-arany erek, moha-borítás, élet-derengés |
| profession-recipe:ritkaság | `erdok_kurtje` | `GOAT_HORN` | Erdők Kürtje | csavart borostyán-barna szarvkürt smaragd berakással, zöld-okker faragás, arany szájperem, erdei inda-motívum |
| profession-recipe:ritkaság | `felepules_iranytuje` | `RECOVERY_COMPASS` | Felépülés Iránytűje | réz iránytű pulzáló türkiz-zöld tűvel, borostyán számlap, halvány gyógyító derengés, kopott perem |
| profession-recipe:ritkaság | `jegvirag_koszoru` | `BLUE_ORCHID` | Jégvirág-koszorú | jégkék kristályos virágkoszorú zúzmara-szirmokkal, ezüst szár, dérrel bevont levelek, hideg kék csillám |
| profession-recipe:ritkaság | `kristaly_katalizator` | `END_CRYSTAL` | Kristály-katalizátor | lebegő ibolya-fehér kristály belső energiamaggal, forgó fény-gyűrűk, szikrázó élek, mágikus derengés |
| profession-recipe:ritkaság | `melyseg_szive` | `HEART_OF_THE_SEA` | A Mélység Szíve | türkiz-prizmarin szívkagyló örvénylő tengerkék maggal, gyöngyház erezet, halvány kék derengés, ezüst csillám |
| profession-recipe:ritkaság | `oceanjaro_terkep` | `MAP` | Óceánjáró Térképe | kopott pergamen tengeri térkép türkiz vizekkel, arany iránytűrózsa, gyöngyház partvonal, tekercs-perem |
| profession-recipe:ritkaság | `orok_viragzas` | `PEONY` | Örök Virágzás Csokra | dús rózsaszín-arany bazsarózsa-csokor örökfényű szirmokkal, smaragdzöld levelek, halvány arany derengés, selyemszalag |
| profession-recipe:ritkaság | `osi_ereklye_kiemeles` | `BRUSH` | Ereklye-kiemelő Készlet | finom réz-nyelű régész-ecset arany sörtékkel, ősi ereklye-por, vésett minta, borostyán foglalat |
| profession-recipe:ritkaság | `totem_ujraelesztes` | `TOTEM_OF_UNDYING` | Újraélesztett Totem | arany-smaragd totem ragyogó élet-maggal, felfelé áramló fénykristályok, smaragdzöld szemek, feltámadás-derengés |
| profession-recipe:ritkaság | `vandorbot` | `STICK` | Vándorbot | görcsös meleg barna vándorbot kopott bőrfonással, apró borostyán-kő a tetején, útpor, zöld inda |
| profession-recipe:ritkaság | `vasfa_ij` | `BOW` | Vasfa Íj | sötét vasfából faragott masszív íj, réz veretek, zöld-okker inda-gravírozás, feszes ínhúr |
| profession-recipe:ritkaság | `vegtelen_kodex` | `WRITTEN_BOOK` | A Végtelen Kódex | sötétkék bőrkötésű kódex arany csillag-mintával, izzó rúnák a lapokon, ezüst kapocs, mágikus derengés |
| profession-recipe:ritkaság | `vezetokurt` | `CONDUIT` | Mélység Vezérkürtje | csavart mélységi kagyló-kürt türkiz belső izzással, gyöngyház erezet, korall-tüskék, prizmarin derengés |
| profession-recipe:ritkaság | `vihar_palack` | `WIND_CHARGE` | Palackozott Vihar | átlátszó gömbpalack örvénylő szürkéskék viharral, kavargó szél-örvény, kékesfehér villám-szikrák, ezüst dugó |
| profession-recipe:ritkaság | `vilagfa_magja` | `OAK_SAPLING` | A Világfa Magja | izzó arany-zöld facsemete-mag lüktető életfénnyel, smaragd levélkék, borostyán gyökér-erek, mágikus derengés |
| profession-recipe:ritkaság | `wither_rozsa_oltvany` | `WITHER_ROSE` | Fonnyadt Rózsa-oltvány | fonnyadt éjfekete rózsa hervadó szirmokkal, ibolya-türkiz lich-derengés, csontszürke szár, tüskés inda |
| profession-recipe:rúnaírnok-(tervrajz) | `arnyuzo_tekercs` | `ENCHANTED_BOOK` | Árnyűző tekercs | tekercs, türkiz lich-fény rúnák, elűzött árnyalak-glyph, hideg spektrális ragyogás |
| profession-recipe:rúnaírnok-(tervrajz) | `ej_fatyol_tekercs` | `ENCHANTED_BOOK` | Éj-fátyol tekercs | tekercs, éjfekete-lila fátyol-rúnák, csillagköd-glyph, hideg türkiz peremfény |
| profession-recipe:rúnaírnok-(tervrajz) | `kaosz_zabla_tekercs` | `ENCHANTED_BOOK` | Káosz-zabla tekercs | tekercs, kaotikus lila-türkiz zabla-rúna, örvénylő káosz-glyph, szikrázó peremfény |
| profession-recipe:rúnaírnok-(tervrajz) | `meregfojto_tekercs` | `ENCHANTED_BOOK` | Méregfojtó tekercs | tekercs, mérgeszöld méregcsepp-rúnák, fojtó indafonat-glyph, savas ragyogás |
| profession-recipe:rúnaírnok-(tervrajz) | `runavert_tekercs` | `ENCHANTED_BOOK` | Rúnavért-tekercs | tekercs, ezüst vért-rúnakör, védő pikkely-glyph izzik, acélkék fénykontúr |
| profession-recipe:rúnaírnok-(tervrajz) | `viharfogo_tekercs` | `ENCHANTED_BOOK` | Viharfogó tekercs | tekercs, viharkék villám-rúnák, cikázó égi glyph, elektromos peremragyogás |
| profession-recipe:szerszám | `egyszeru_horgaszbot` | `FISHING_ROD` | Egyszerű Horgászbot | átlós vékony faág nyél, egyszerű fehér zsinór, apró vas horog, natúr fakéreg |
| profession-recipe:szerszám | `gyemant_fejsze` | `DIAMOND_AXE` | Gyémántfejsze | átlós cián-fehér kristályél bárdfej, ragyogó gyémánt penge, ezüst nyak, kék csillanás |
| profession-recipe:szerszám | `kovilta_fejsze` | `STONE_AXE` | Kővésett Fejsze | átlós szürke kőbárd fej, durva pattintott él, barna faág nyél, kőszemcsés felület |
| profession-recipe:szerszám | `mesteri_horgaszbot` | `FISHING_ROD` | Mesteri Horgászbot | átlós lakkozott sötét nyél, rézgyűrűs zsinórvezetők, precíz orsó, feszes ezüst zsinór |
| profession-recipe:szerszám | `rezhorgany_horgaszbot` | `FISHING_ROD` | Rézhorgony Horgászbot | átlós barna nyél, réz horgony-alakú súly, borostyán fém csillanás, zöldes patina |
| profession-recipe:szerszám | `runafenyes_csakany` | `DIAMOND_PICKAXE` | Rúnafényes Bányászcsákány | átlós cián-fehér kristályfej csákány, izzó türkiz-kék rúnaglyphek, ezüst nyak, sötét nyél |
| profession-recipe:szerszám | `tarnasz_csakany_recept` | `DIAMOND_PICKAXE` | Tárnász Csákány | átlós cián-fehér kristályfej csákány, borostyán-arany bányász-vésetek, kopott barna nyél, réz gyűrű |
| profession-recipe:szerszám | `tartos_horgaszbot` | `FISHING_ROD` | Tartós Horgászbot | átlós vastag megerősített nyél, dupla zsinórvezetők, robusztus vas orsó, sötétbarna lakk |
| profession-recipe:szerszám | `vasfejsze` | `IRON_AXE` | Vasfejsze | átlós szürke acél bárdfej, csiszolt él, egyszerű vas nyakrész, barna faág nyél |
| profession-recipe:szerszám-(tervrajz) | `legendas_horgaszbot` | `FISHING_ROD` | Legendás Horgászbot | átlós aranyveretes díszes nyél, ragyogó ékkő-berakás, arany orsó, izzó fény-zsinór |
| profession-recipe:szerszám-(tervrajz) | `netherit_csakany` | `NETHERITE_PICKAXE` | Mélybányász Netherit Csákány | átlós sötét szemcsés netherit fej, arany-perem él, tompa fém, robusztus sötét nyél |
| profession-recipe:szerszám-(tervrajz) | `netherit_fejsze` | `NETHERITE_AXE` | Erdőirtó Netherit Fejsze | átlós sötét szemcsés netherit bárdfej, arany-perem él, széles penge, sötét robusztus nyél |
| profession-recipe:sötét-mágia | `ejszaka_pengeje` | `NETHERITE_SWORD` | Az Éjszaka Pengéje | éjfekete penge ibolya árnyék-erezettel, hideg türkiz él-derengés, csontmarkolat, lich-fény szikrák a keresztvason |
| profession-recipe:vérszavanna-(tervrajz) | `napfogyatkozas` | `BOW` | Napfogyatkozás | sötét ív izzó parázs-narancs korona-gyűrűvel, mélyvörös test, arany napkorong-motívum, fekete-arany húr |
| profession-recipe:vérszavanna-(tervrajz) | `zhoris_langnyelve` | `NETHERITE_SWORD` | I. Zhoris Lángnyelve | hullámos láng-penge parázs-narancs izzással, arany keresztvas mélyvörös rubinttal, füstölgő él, tűz-gravírozás |
| profession-recipe:étel | `banyasz_szalonna` | `COOKED_PORKCHOP` | Bányász Szalonnája | ropogós sült szalonna tányéron, aranybarna zsírosan sült szélek, füstös pirulás |
| profession-recipe:étel | `erdei_gombapite` | `PUMPKIN_PIE` | Erdei Gomba Pite | aranybarna gombás pite szeletben, erdei gombakalapok tetején, meleg barna töltelék |
| profession-recipe:étel | `fonix_fuszeres_szarny` | `COOKED_CHICKEN` | Főnixfűszeres Szárny | ropogós csirkeszárny izzó narancs fűszerbevonattal, parázsló chili-máz, arany pirulás |
| profession-recipe:étel | `fuszeres_vandorhus` | `COOKED_MUTTON` | Fűszeres Vándorhús | fűszerezett sült ürühús szeletek tányéron, aranybarna kéreg, meleg fűszerpír |
| profession-recipe:étel | `halasz_fogasa` | `COOKED_SALMON` | Halász Fogása | ropogósra sült lazacfilé tányéron, rózsaszínes hús, aranybarna héj, citromkarika |
| profession-recipe:étel | `hamvak_lakomaja` | `BEETROOT_SOUP` | Hamvak Lakomája | mélyvörös céklaleves csontszín tálban, hamvas szürke tajték, sötét gőz |
| profession-recipe:étel | `harcos_husos_tal` | `COOKED_BEEF` | Harcos Húsos Tála | gőzölgő marhahús-szeletek fatálcán, aranybarna sült kéreg, bőséges húsadag |
| profession-recipe:étel | `kapu_lakomaja` | `ENCHANTED_GOLDEN_APPLE` | A Kapu Lakomája | ragyogó aranyalma parázsló narancs glóriával, izzó rúnapára, tüzes főnixfény |
| profession-recipe:étel | `lakodalmas_torta` | `CAKE` | Lakodalmas Emeletes Torta | fehér emeletes esküvői torta cukormázzal, rózsaszín díszek, aranyló csillámok |
| profession-recipe:étel | `mezes_puszedli` | `COOKIE` | Mézes Puszedli | aranybarna mézes puszedli fehér cukormázzal, fényes mézcsepp, puha keksz |
| profession-recipe:étel | `pasztor_urucomb` | `COOKED_MUTTON` | Pásztor Ürücombja | sült ürücomb csonton, aranybarna ropogós bőr, rozmaringág, meleg pecsenyefény |
| profession-recipe:étel | `sarkany_porkolt` | `RABBIT_STEW` | Sárkány-pörkölt | gőzölgő vörös pörkölt cseréptálban, izzó paprikás szaft, tüzes húskockák |
| profession-recipe:étel | `tengerek_gyongye` | `COOKED_COD` | Tengerek Gyöngye | ezüstös sült tőkehal tányéron, gyöngyházfényű hús, tengerkék máz, citromszelet |
| profession-recipe:étel | `tuzes_chili_tal` | `COOKED_BEEF` | Tüzes Chilis Tál | gőzölgő vörös chilis marhahús tálban, izzó paprikaszemek, tüzes narancs szaft |
| profession-recipe:étel | `vadlakoma` | `COOKED_BEEF` | Vérszavannai Vadlakoma | bőséges sült vadhús fatálon, parázsló narancs fűszermáz, füstös pirosas kéreg |
| profession-recipe:étel | `vandor_pogacsaja` | `BREAD` | Vándor Pogácsája | aranybarna pogácsa rácsos tetővel, ropogós héj, meleg barna morzsás bél |
| profession-recipe:étel | `vandor_uti_kenyer` | `BREAD` | Vándor Úti Kenyere | rusztikus kerek cipó ropogós aranybarna héjjal, magvas felszín, meleg bél |
| profession-recipe:étel | `vandorunnep_lepenye` | `PUMPKIN_PIE` | Vándorünnep Lepénye | aranybarna sült lepény szeletben, borostyán töltelék, fahéjas máz, meleg pára |
| profession-recipe:étel-(tervrajz) | `aranyalma_lakoma` | `GOLDEN_APPLE` | Aranyalma Lakoma | csillogó aranyalma fényes arany héjjal, meleg sárga ragyogás, halvány glória |
| profession-recipe:étel-(tervrajz) | `legendas_lakoma` | `ENCHANTED_GOLDEN_APPLE` | Legendás Lakoma | ragyogó aranyalma lila varázsglóriával, örvénylő bűbáj-pára, tündöklő prizmafény |
| relic | `relic_bone_wing` | `ELYTRA` | Csontszárny | csontból szőtt szárny, sötét hártya, hideg türkiz ízületek |
| relic | `relic_eleftheria_konnye` | `HEART_OF_THE_SEA` | Eleftheria Könnye | fekete könnycsepp, belső türkiz fénymag |
| relic | `relic_frost_wing` | `ELYTRA` | Zúzmara-szárny | jégkristály tollak, kék-ezüst fagyfény |
| relic | `relic_metelytepo` | `GOLDEN_AXE` | Mételytépő | arany csatabárd ősi türkiz rúnákkal, mélységi zöld-arany penge, lich-fény él |
| relic | `relic_phoenix_wing` | `ELYTRA` | Főnix-szárny | lángoló tollú szárny, vörös-arany izzás |
| relic | `relic_sarkany_tojas` | `DRAGON_EGG` | Sárkánytojás-töredék | repedt sárkánytojás-szilánk, lila mélyfény |
| relic | `relic_wander_wind` | `ELYTRA` | Vándorszél | könnyű áttetsző tollak, égszínkék szélmotívum |

## Karbantartási szabály

- Új itemnél a config/kód `item-model` értéke legyen `icesmp:<modell-id>`.
- Ugyanez a `<modell-id>` szerepeljen a manifestben és a három pack-fájl útvonalában.
- A manifest törzsét (kategória/modell-id/alap-item/név) a configból generáljuk újra; kézzel csak a `Prompt-hint` mezőt finomítjuk.
- A `Prompt-hint` legyen tárgyra szabott (fő motívum + anyag + 1–2 szín/akcens), ne sablon — kerüld a puszta „ikon"/„vanilla-hű" jellegű kitöltést.
- Faction- vagy lore-kötött tárgy a globális paletta szerinti akcenst viselje; DARK-nál kötelező a hideg türkiz lich-fény.
