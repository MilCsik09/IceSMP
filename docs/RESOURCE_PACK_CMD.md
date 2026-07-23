# Resource Pack — ITEM_MODEL manifest és textúra-spec

Ez a fájl a pack-készítő és a későbbi textúragenerátor bemenete. A plugin modern `ITEM_MODEL` komponenst használ; új itemhez mindig `icesmp:<modell-id>` kell, nem vanília override-lista.

## Kimeneti fájlok

Egy `modell-id` például `currency_red`. A packban ezek a fájlok tartoznak hozzá:

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

## Textúragenerálási szabály

- **Méret:** 16×16 vagy 32×32 PNG. Egy packon belül egy méretet válassz, ne keverd.
- **Háttér:** teljesen átlátszó; félátlátszó perempixel ne legyen.
- **Stílus:** vanilla-hű pixel art, bal-felső fény, 1 px sötét külső kontúr, blur/anti-alias nélkül.
- **Sziluett:** az alap-item családja maradjon felismerhető. Fegyver/szerszám 45°-os ikon, páncél frontális ikon, ital fiola-sziluett.
- **Paletta:** 4–8 fő tónus; erős neon csak kis akcentként.
- **Generátor prompt-sablon:** `vanilla Minecraft item icon, <size>x<size> pixel art, transparent background, <base_item> silhouette, <label>, <prompt_hint>, crisp pixels, no antialiasing`.

## Globális paletta

| Téma | Színek |
|---|---|
| RED / Perinfernicitas | vörös, parázs-narancs, arany |
| BLUE / Cryghaliris | jégkék, ezüst, fehér |
| NEUTRAL / Ryanora-Caldestera | kereskedő-arany, borostyán, zöld-okker |
| DARK / Kitaszítottak | csontfehér, éjfekete-lila, hideg türkiz akcent |
| Mélység / törpe-rúna | sötét acél, bronz, rúna-türkiz |

## Manifest

| Kategória | Modell-id | Alap-item | Név | Prompt-hint |
|---|---|---|---|---|
| crate-key | `cratekey_koznapi` | `TRIPWIRE_HOOK` | Caldesterai Kereskedőláda | láda-kulcs, ritkaság szerinti fém/ékkő |
| crate-key | `cratekey_ritka` | `TRIPWIRE_HOOK` | Caldesterai Kincsesláda | láda-kulcs, ritkaság szerinti fém/ékkő |
| factory | `blueprint` | `KNOWLEDGE_BOOK` | Recept-tervrajz | kék tervrajzlap, fehér vonalak |
| factory | `money_pouch` | `LEATHER` | Kopott erszény | bőrerszény, kikandikáló érmék |
| factory | `selection_wand` | `STICK` | Birtokmérő pálca | mérőpálca, zöld jelölőzsinór |
| factory | `selection_wand_territory` | `BLAZE_ROD` | Határkijelölő pálca | arany admin pálca, kis zászló |
| factory | `siege_ram` | `TNT_MINECART` | Ostromgép | fakos/ostromgép ikon, vasalat |
| factory:capture | `capture_beast` | `LEAD` | Ősi Kötés Póráza | pányva/lasszó, zöld természetjel |
| factory:capture | `capture_heart` | `ECHO_SHARD` | Nyughatatlan Szív | dobbanó sötét szív, türkiz erek |
| factory:capture | `capture_necro` | `GHAST_TEAR` | Sötét Paktum-tekercs | sötét tekercs, koponya pecsét |
| factory:capture | `capture_seal` | `AMETHYST_SHARD` | Démon-pecsét | lila pecsétszilánk, démonruna |
| factory:catalyst | `catalyst_archer` | `RABBIT_HIDE` | Soleil Vadásztarsolya | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_assassin` | `FLINT` | Homály-szilánk | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_death_knight` | `WITHER_SKELETON_SKULL` | Néma Rúnakoponya | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_demon_hunter` | `ENDER_EYE` | Hasadék Szeme | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_druid` | `OAK_SAPLING` | Aetrinita Sarja | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_evoker` | `DRAGON_BREATH` | Sárkányvér-fiola | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_monk` | `BAMBOO` | Élet Ága | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_paladin` | `BELL` | Hajnaltűz Harangja | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_priest` | `WHITE_CANDLE` | Asterlayna Gyertyája | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_shaman` | `TOTEM_OF_UNDYING` | Ősvihar Totemje | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_warlock` | `SOUL_LANTERN` | Kárhozat Lámpása | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_warrior` | `GOAT_HORN` | Sárkánykirály Kürtje | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:catalyst | `catalyst_wizard` | `ENCHANTED_BOOK` | Caldesterai Rúnakódex | kaszt-katalizátor, base item sziluett + kaszt aura |
| factory:currency | `currency_blue` | `PAPER` | Hópihér-veret | érme, hópehely címer, kék-ezüst |
| factory:currency | `currency_dark` | `PAPER` | Csontveret | érme, koponya címer, csont + hideg türkiz |
| factory:currency | `currency_neutral` | `PAPER` | Creutzér | érme, mérleg címer, arany-borostyán |
| factory:currency | `currency_red` | `PAPER` | Parázsló Parals | érme, láng címer, vörös-arany |
| factory:spell | `spell_demonic_circle` | `REDSTONE` | Démoni Só | vörös/lila por, körminta |
| factory:spell | `spell_expel_harm` | `BLAZE_ROD` | Kiűzés Botja | szent bot, világos rúnafény |
| factory:spell | `spell_rune_strike` | `AMETHYST_SHARD` | Rúnakő | rúnakristály, villámfény |
| factory:spell | `spell_wild_mushroom` | `BROWN_MUSHROOM` | Vadgomba | erdei gomba, zöld akcent |
| named-loot | `loot_elit_pancel` | `CHAINMAIL_CHESTPLATE` | Megrontott Elit Páncél | páncél ikon, vanilla forma |
| named-loot | `loot_fekete_csont` | `BONE` | Fekete Csont | vanilla-hű ikon, név szerinti fő motívum |
| named-loot | `loot_nema_kiralyno` | `NETHERITE_SWORD` | A Néma Királynő Suttogása | 45 fokos eszköz/fegyver sziluett |
| named-loot | `loot_rozsdas_penge` | `IRON_SWORD` | A Hetedik Vérháború Rozsdás Pengéje | 45 fokos eszköz/fegyver sziluett |
| shop | `shop_menlevel` | `PAPER` | Hamisított Menlevél | pecsétes menlevél, hamis bankárszövetségi jel |
| shop | `shop_setapalca` | `STICK` | Bokic-menti Sétapálca | úri sétapálca, rejtett penge sejtetése |
| profession-material | `acskapocs` | `IRON_NUGGET` | Ácskapocs | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `aranyfust_lemez` | `GOLD_NUGGET` | Aranyfüst-lemez | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `arnyekpor` | `SCULK_VEIN` | Árnyékpor | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `arnygomba` | `CRIMSON_FUNGUS` | Mortengradi Árnygomba | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `aszalohalo` | `COBWEB` | Aszalóháló | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `csalizsir` | `SLIME_BALL` | Csalizsír | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `csillekenocs` | `SLIME_BALL` | Csillekenőcs | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `csontenyv` | `BONE_MEAL` | Csontenyv | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `dermedt_konnycsepp` | `GHAST_TEAR` | Dermedt Könnycsepp | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `desztillalt_esoviz` | `GLASS_BOTTLE` | Desztillált Esővíz | fiola/ital, folyadékszín kiemelés |
| profession-material | `ecet_eszencia` | `HONEY_BOTTLE` | Ecet-eszencia | fiola/ital, folyadékszín kiemelés |
| profession-material | `edzoolaj` | `MAGMA_CREAM` | Edzőolaj | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `elso_csend_szilankja` | `ECHO_SHARD` | Az Első Csend Szilánkja | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `emlekszilank` | `AMETHYST_SHARD` | Opálos Emlékszilánk | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `ercmoso_lug` | `GLASS_BOTTLE` | Ércmosó-lúg | fiola/ital, folyadékszín kiemelés |
| profession-material | `ezust_toll` | `FEATHER` | Ezüst-toll | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `favedo_pac` | `INK_SAC` | Favédő pác | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `fenoko` | `SMOOTH_STONE` | Fenőkő | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `folyositoszer` | `BLAZE_POWDER` | Kovács-folyósítószer | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `fonixpihe` | `FEATHER` | Főnixpihe | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `fujtatobor` | `RABBIT_HIDE` | Fújtatóbőr | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `fustoloforgacs` | `STICK` | Füstölőforgács | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `gyantaoldo` | `HONEY_BOTTLE` | Gyantaoldó | fiola/ital, folyadékszín kiemelés |
| profession-material | `gyogy_kivonat` | `GLOW_BERRIES` | Gyógy-kivonat | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `gyongyhaz_pikkely` | `PRISMARINE_SHARD` | Gyöngyház-pikkely | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `halofonal` | `STRING` | Hálófonal | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `holdezust_huzal` | `CHAIN` | Holdezüst Huzal | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `horogkeszlet` | `TRIPWIRE_HOOK` | Horogkészlet | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `irnok_tinta` | `INK_SAC` | Írnok-tinta | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `jegviragpor` | `SUGAR` | Jégvirág-por | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `karhozat_parazs` | `FIRE_CHARGE` | A Kapu Parazsa | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `katalizator_so` | `GLOWSTONE_DUST` | Katalizátor-só | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `kemenyfa_gerenda` | `STRIPPED_OAK_WOOD` | Keményfa Gerenda | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `koso` | `SUGAR` | Kősó | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `lampaolaj` | `GLOW_INK_SAC` | Finomított Lámpaolaj | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `lelekhamu` | `GUNPOWDER` | Lélekhamu | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `lombik_szen` | `CHARCOAL` | Lombik-szén | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `melysegi_borostyan` | `RAW_GOLD` | Mélységi Borostyán | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `melysegi_iranytu` | `COMPASS` | Mélységi Iránytű-tű | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `merozsinor` | `STRING` | Mérőzsinór | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `nema_kristaly` | `AMETHYST_SHARD` | Néma Kristály | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `nyelbor` | `LEATHER` | Nyélbőr | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `obszidian_szilank` | `FLINT` | Obszidián-szilánk | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `olomdugo` | `IRON_NUGGET` | Ólomdugó | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `oltoviasz` | `HONEYCOMB` | Oltóviasz | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `osi_ereklyeszilank` | `NETHER_STAR` | Fekete Villám Szilánk | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `parafa_uszo` | `OAK_BUTTON` | Parafa-úszó | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `parazsmag` | `BLAZE_POWDER` | Parázsmag | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `pergamen_simito` | `BONE` | Pergamen-simító | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `permetezo_kanna` | `BUCKET` | Permetező-kanna | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `polirpaszta` | `SUGAR` | Polírpaszta | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `rezgo_rez_otvozet` | `COPPER_INGOT` | Rezgő Rézötvözet | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `robbantopor` | `GUNPOWDER` | Robbantópor | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `runa_bastya` | `AMETHYST_SHARD` | Bástya Rúnája | rúna/könyv/tekercs, olvasható fő jel |
| profession-material | `runa_elek` | `AMETHYST_SHARD` | Él Rúnája | rúna/könyv/tekercs, olvasható fő jel |
| profession-material | `runa_fagy` | `AMETHYST_SHARD` | Fagy Rúnája | rúna/könyv/tekercs, olvasható fő jel |
| profession-material | `runa_lang` | `AMETHYST_SHARD` | Láng Rúnája | rúna/könyv/tekercs, olvasható fő jel |
| profession-material | `runa_moho` | `AMETHYST_SHARD` | Mohóság Rúnája | rúna/könyv/tekercs, olvasható fő jel |
| profession-material | `runa_visszhang` | `AMETHYST_SHARD` | Visszhang Rúnája | rúna/könyv/tekercs, olvasható fő jel |
| profession-material | `runa_zapor` | `AMETHYST_SHARD` | Zápor Rúnája | rúna/könyv/tekercs, olvasható fő jel |
| profession-material | `runakreta` | `CLAY_BALL` | Rúnakréta | rúna/könyv/tekercs, olvasható fő jel |
| profession-material | `runapor` | `GLOWSTONE_DUST` | Rúnapor | rúna/könyv/tekercs, olvasható fő jel |
| profession-material | `sarkanycsont_szilank` | `BONE` | Sárkánycsont-szilánk | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `sarkfeny_cseppko` | `PRISMARINE_CRYSTALS` | Sarkfény-cseppkő | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `sozott_csali` | `DRIED_KELP` | Sózott csali | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `sutopergamen` | `PAPER` | Sütőpergamen | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `suttogas_meghivo` | `ECHO_SHARD` | Suttogás | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `szavannafu_kotel` | `VINE` | Szavannafű-kötél | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `szorny_mag` | `ECHO_SHARD` | Szörny Mag | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `szuropapir` | `PAPER` | Szűrőpapír | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `tarnatamasz_szegecs` | `IRON_NUGGET` | Tárnatámasz-szegecs | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `tiszta_vasesszencia` | `IRON_NUGGET` | Tiszta Vasesszencia | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `tozegkocka` | `PACKED_MUD` | Tőzegkocka | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `uvegfiola_keszlet` | `GLASS_BOTTLE` | Üvegfiola-készlet | fiola/ital, folyadékszín kiemelés |
| profession-material | `vad_esszencia` | `PHANTOM_MEMBRANE` | Vad Esszencia | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `vaj` | `HONEYCOMB` | Friss Vaj | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `vandorfuszer` | `COCOA_BEANS` | Vándorfűszer | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `viaszgyertya` | `CANDLE` | Viasz-gyertya | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `viaszpecset` | `HONEYCOMB` | Számvevő-pecsétviasz | vanilla-hű ikon, név szerinti fő motívum |
| profession-material | `viharkvarc` | `QUARTZ` | Viharkvarc | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:alapanyag-(tervrajz) | `tengeristen_amulettje` | `CONDUIT` | Tengeristen Amulettje | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:bűvölés | `csali_tomus` | `ENCHANTED_BOOK` | Csali Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `eles_tomus` | `ENCHANTED_BOOK` | Élesség Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `fagypancel_tekercs` | `ENCHANTED_BOOK` | Fagypáncél Tekercse | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `fonixtoll_tekercs` | `ENCHANTED_BOOK` | Főnixtoll Tekercse | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `fosztogatas_tomus` | `ENCHANTED_BOOK` | Fosztogatás Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `hatekonysag_tomus` | `ENCHANTED_BOOK` | Hatékonyság Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `kemenyfa_ijkeret_tomus` | `ENCHANTED_BOOK` | Keményfa Íjkeret Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `szerencse_tomus` | `ENCHANTED_BOOK` | Szerencse Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `tartossag_tomus` | `ENCHANTED_BOOK` | Tartósság Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `vasesszencias_paloscsapas_tomus` | `ENCHANTED_BOOK` | Vasesszenciás Páncéltörés Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `vedelem_tomus` | `ENCHANTED_BOOK` | Védelem Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés | `zuhanascsokkentes_tomus` | `ENCHANTED_BOOK` | Zuhanáscsökkentés Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés-(tervrajz) | `javitas_tomus` | `ENCHANTED_BOOK` | Javítás Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés-(tervrajz) | `orvenylo_pusztitas_tomus` | `ENCHANTED_BOOK` | Örvénylő Pusztítás Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés-(tervrajz) | `selyemerintes_tomus` | `ENCHANTED_BOOK` | Selyemérintés Tomus | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:bűvölés-(tervrajz) | `szornymag_talizman` | `ENCHANTED_BOOK` | Szörnymag Talizmán | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:eszköz | `celkereszt_szamszerij` | `CROSSBOW` | Céhmesteri Számszeríj | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:eszköz | `csillekerek` | `MINECART` | Megkent Csille | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:eszköz | `feszitett_szaru_ij` | `BOW` | Feszített Szaruíj | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:eszköz | `mefonott_pajzs` | `SHIELD` | Erdőjáró Pajzs | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:eszköz | `melysegi_tajolo` | `COMPASS` | Tárnatájoló | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:eszköz | `melyvizi_horog` | `FISHING_ROD` | Mélyvízi Horogsor | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:eszköz | `mestermuves_bot` | `FISHING_ROD` | Mesterhorgász Botja | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:eszköz | `pajzsdudor` | `SHIELD` | Dudoros Hadipajzs | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:eszköz | `tavcso` | `SPYGLASS` | Bányamérnöki Távcső | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:eszköz | `uszokeszlet` | `FISHING_ROD` | Úszókészlet | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:eszköz | `vadaszij` | `BOW` | Vadászíj | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:eszköz | `viharjelzo_boja` | `LANTERN` | Viharjelző Bója | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:fagyott-királyság-(konyha) | `fagyasztott_pisztrang` | `COOKED_SALMON` | Fagyasztott Tavi Pisztráng | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:fagyott-királyság-(tervrajz) | `glatziendorfi_jegtoro` | `NETHERITE_AXE` | Glatziendorfi Jégtörő | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fagyott-királyság-(tervrajz) | `glatziendorfi_jegvert` | `NETHERITE_CHESTPLATE` | Glatziendorfi Jégvért | páncél ikon, vanilla forma |
| profession-recipe:fagyott-királyság-(tervrajz) | `jegsarkany_kantar` | `SADDLE` | Jégsárkány-Kantár | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:fagyott-királyság-(tervrajz) | `kallan_szeletelo` | `BOW` | Kallan Szeletelője | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fagyott-királyság-(tervrajz) | `miinus_haragja` | `NETHERITE_SWORD` | V. Miinus Haragja | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fagyott-királyság-(tervrajz) | `sarkanycsont_ij` | `BOW` | Sárkánycsont Íj | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fegyver | `csontenyves_ijkar` | `BOW` | Csontenyves Íjkar | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fegyver | `gyemant_kard` | `DIAMOND_SWORD` | Gyémántkard | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fegyver | `haromagu_szigony` | `TRIDENT` | Háromágú Szigony | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fegyver | `vaskard` | `IRON_SWORD` | Vaskard | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fegyver-(tervrajz) | `ereklye_penge` | `NETHERITE_SWORD` | Villámszilánk Pengéje | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fegyver-(tervrajz) | `melytengeri_ereklyeszigony` | `TRIDENT` | Mélytengeri Villámszigony | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fegyver-(tervrajz) | `netherit_kard` | `NETHERITE_SWORD` | Netherit Pallos | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:fegyver-(tervrajz) | `runakovacsolt_penge` | `NETHERITE_SWORD` | Rúnakovácsolt Penge | rúna/könyv/tekercs, olvasható fő jel; 45 fokos eszköz/fegyver sziluett |
| profession-recipe:ital | `aranyfeny_mezsor` | `HONEY_BOTTLE` | Aranyfényű Mézsör | fiola/ital, folyadékszín kiemelés; étel/ital, jól olvasható alapforma |
| profession-recipe:ital | `arnyeklikor` | `HONEY_BOTTLE` | Árnyéklikőr | fiola/ital, folyadékszín kiemelés |
| profession-recipe:ital | `bokic_gyogytea` | `HONEY_BOTTLE` | Bokic-parti Gyógytea | fiola/ital, folyadékszín kiemelés; étel/ital, jól olvasható alapforma |
| profession-recipe:ital | `caldesterai_gyogytea` | `HONEY_BOTTLE` | Caldesterai Gyógytea | fiola/ital, folyadékszín kiemelés; étel/ital, jól olvasható alapforma |
| profession-recipe:ital | `hamvasztott_kave` | `HONEY_BOTTLE` | Hamvasztott Kávé | fiola/ital, folyadékszín kiemelés; étel/ital, jól olvasható alapforma |
| profession-recipe:ital | `jeghegyi_sor` | `HONEY_BOTTLE` | Jéghegyi Sör | fiola/ital, folyadékszín kiemelés; étel/ital, jól olvasható alapforma |
| profession-recipe:ital | `jegkiraly_parlat` | `HONEY_BOTTLE` | Jégkirály Párlata | fiola/ital, folyadékszín kiemelés |
| profession-recipe:ital | `kofejto_sore` | `HONEY_BOTTLE` | Kőfejtő Söre | fiola/ital, folyadékszín kiemelés; étel/ital, jól olvasható alapforma |
| profession-recipe:ital | `mortengradi_keseru` | `HONEY_BOTTLE` | Mortengrádi Keserű | fiola/ital, folyadékszín kiemelés |
| profession-recipe:ital | `parazs_palinka` | `HONEY_BOTTLE` | Parázs Pálinka | fiola/ital, folyadékszín kiemelés; étel/ital, jól olvasható alapforma |
| profession-recipe:ital | `szentelt_bor` | `HONEY_BOTTLE` | Szentelt Bor | fiola/ital, folyadékszín kiemelés |
| profession-recipe:ital | `tengeresz_rum` | `HONEY_BOTTLE` | Tengerész Rum | fiola/ital, folyadékszín kiemelés; étel/ital, jól olvasható alapforma |
| profession-recipe:ital | `viharfi_almabor` | `HONEY_BOTTLE` | Viharfi Almabor | fiola/ital, folyadékszín kiemelés |
| profession-recipe:ital-(tervrajz) | `arnyekmereg` | `SPLASH_POTION` | Árnyékméreg | fiola/ital, folyadékszín kiemelés |
| profession-recipe:ital-(tervrajz) | `bajnok_elixir` | `POTION` | Bajnok Elixírje | fiola/ital, folyadékszín kiemelés |
| profession-recipe:ital-(tervrajz) | `ereklye_elixir` | `POTION` | Villámszilánk Elixírje | fiola/ital, folyadékszín kiemelés |
| profession-recipe:kazamata-kulcs | `csontkripta_kulcsa` | `TRIAL_KEY` | A Csontkripta Kulcsa | kulcs sziluett, egyedi fej |
| profession-recipe:kazamata-kulcs | `melyseg_kulcsa` | `TRIAL_KEY` | A Mélység Kulcsa | kulcs sziluett, egyedi fej |
| profession-recipe:kitaszítottak-(konyha) | `mortengradi_hamukenyer` | `BREAD` | Mortengradi Hamukenyér | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:különleges | `gyongyhaz_talizman` | `NAUTILUS_SHELL` | Gyöngyház Talizmán | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:különleges | `pecsetes_szerzodes` | `PAPER` | Pecsétes Szerződés | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:különleges | `sarkfeny_prizma` | `SEA_LANTERN` | Sarkfény-prizma | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:különleges | `viharuveg_lampas` | `LANTERN` | Viharüveg Lámpás | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:legendás-(tervrajz) | `eleftheria_fatyla` | `NETHERITE_CHESTPLATE` | Eleftheria Fátyla | páncél ikon, vanilla forma |
| profession-recipe:legendás-(tervrajz) | `melysegi_korona` | `NETHERITE_HELMET` | A Mélység Népe Koronája | páncél ikon, vanilla forma |
| profession-recipe:legendás-(tervrajz) | `viharjaro_csizma` | `NETHERITE_BOOTS` | Viharjáró Csizma | páncél ikon, vanilla forma |
| profession-recipe:lángoló-birodalom-(konyha) | `fonixtojas_rantotta` | `PUMPKIN_PIE` | Fűszeres Főnixtojás-Rántotta | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:lángoló-birodalom-(tervrajz) | `fonix_tollkopeny` | `LEATHER_CHESTPLATE` | Főnix-Tollköpeny | páncél ikon, vanilla forma |
| profession-recipe:lángoló-birodalom-(tervrajz) | `pyralingradi_tuzkopo` | `CROSSBOW` | Pyralingradi Tűzköpő | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:lángoló-birodalom-(tervrajz) | `verszavanna_agyara` | `NETHERITE_SWORD` | A Vérszavanna Agyara | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:menedék-(konyha) | `kakaobabos_sutemeny` | `COOKIE` | Tiltott Kakaóbabos Sütemény | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:menedék-(tervrajz) | `bokic_horgaszbot` | `FISHING_ROD` | Bokic-menti Horgászbot | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:menedék-(tervrajz) | `smaragdko_bankbetet` | `PAPER` | Smaragdkő Bankbetét | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:menedék-(tervrajz) | `szellemszarvas_bubaj` | `RABBIT_FOOT` | Szellemszarvas-Bűbáj | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:menedék-(tervrajz) | `vasmuvek_csakanya` | `DIAMOND_PICKAXE` | Vasművek Akadémiájának Csákánya | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:páncél | `arany_lopancel` | `GOLDEN_HORSE_ARMOR` | Arany Lópáncél | páncél ikon, vanilla forma |
| profession-recipe:páncél | `bastya_pajzs_recept` | `SHIELD` | Bástya Pajzs | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:páncél | `esszencialt_vasvert` | `DIAMOND_CHESTPLATE` | Esszenciált Vasvért | páncél ikon, vanilla forma |
| profession-recipe:páncél | `gyemant_lopancel` | `DIAMOND_HORSE_ARMOR` | Gyémánt Lópáncél | páncél ikon, vanilla forma |
| profession-recipe:páncél | `gyemant_mellvert` | `DIAMOND_CHESTPLATE` | Gyémánt Mellvért | páncél ikon, vanilla forma |
| profession-recipe:páncél | `gyemant_sisak` | `DIAMOND_HELMET` | Gyémántsisak | páncél ikon, vanilla forma |
| profession-recipe:páncél | `halaszkalap` | `LEATHER_HELMET` | Halászkalap | páncél ikon, vanilla forma |
| profession-recipe:páncél | `lancing` | `CHAINMAIL_CHESTPLATE` | Kovácsolt Láncing | páncél ikon, vanilla forma |
| profession-recipe:páncél | `lancnadrag` | `CHAINMAIL_LEGGINGS` | Kovácsolt Láncnadrág | páncél ikon, vanilla forma |
| profession-recipe:páncél | `pancelozott_sisakrostely` | `DIAMOND_HELMET` | Rostélyos Csatasisak | páncél ikon, vanilla forma |
| profession-recipe:páncél | `rezvertezet_lablemez` | `DIAMOND_LEGGINGS` | Rézvértezet Lábvért | páncél ikon, vanilla forma |
| profession-recipe:páncél | `sarkanycsont_pajzs` | `SHIELD` | Sárkánycsont Pajzs | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:páncél | `teknos_sisak` | `TURTLE_HELMET` | Teknőspáncél-sisak | páncél ikon, vanilla forma |
| profession-recipe:páncél | `vadbor_pancel` | `DIAMOND_LEGGINGS` | Vadbőr Vért | páncél ikon, vanilla forma |
| profession-recipe:páncél | `vadolo_csizma` | `DIAMOND_BOOTS` | Vadölő Csizma | páncél ikon, vanilla forma |
| profession-recipe:páncél | `vas_csizma` | `IRON_BOOTS` | Vascsizma | páncél ikon, vanilla forma |
| profession-recipe:páncél | `vas_lablemez` | `IRON_LEGGINGS` | Vas Lábvért | páncél ikon, vanilla forma |
| profession-recipe:páncél | `vas_lopancel` | `IRON_HORSE_ARMOR` | Vas Lópáncél | páncél ikon, vanilla forma |
| profession-recipe:páncél | `vas_sisak` | `IRON_HELMET` | Vassisak | páncél ikon, vanilla forma |
| profession-recipe:páncél | `vasesszencias_pajzs` | `SHIELD` | Vasesszenciás Pajzs | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:páncél | `vizallo_csizma` | `LEATHER_BOOTS` | Halászcsizma | páncél ikon, vanilla forma |
| profession-recipe:páncél-(tervrajz) | `ereklyeszilankos_banyasisak` | `DIAMOND_HELMET` | Villámszilánkos Bányászsisak | páncél ikon, vanilla forma |
| profession-recipe:páncél-(tervrajz) | `netherit_sisak` | `NETHERITE_HELMET` | Netherit Csatasisak | páncél ikon, vanilla forma |
| profession-recipe:páncél-(tervrajz) | `sarkanyvert_recept` | `NETHERITE_CHESTPLATE` | Sárkányvért | páncél ikon, vanilla forma |
| profession-recipe:páncél-(tervrajz) | `szornyvert_mellveny` | `NETHERITE_CHESTPLATE` | Szörnyvért Mellvény | páncél ikon, vanilla forma |
| profession-recipe:ritkaság | `bokic_aldasa` | `TRIDENT` | A Bokic Áldása | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:ritkaság | `bolcsek_kove` | `EXPERIENCE_BOTTLE` | A Bölcsek Köve | fiola/ital, folyadékszín kiemelés |
| profession-recipe:ritkaság | `borostyan_lampa` | `LANTERN` | Borostyánfényű Lámpás | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `cehmester_ulloje` | `ANVIL` | A Céhmester Üllője | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `csendulo_harang` | `BELL` | Csendülő Harang | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `emlekek_konyve` | `WRITTEN_BOOK` | Emlékek Könyve | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:ritkaság | `erdo_szive_totem` | `TOTEM_OF_UNDYING` | Az Erdő Szíve | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `erdok_kurtje` | `GOAT_HORN` | Erdők Kürtje | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `felepules_iranytuje` | `RECOVERY_COMPASS` | Felépülés Iránytűje | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `jegvirag_koszoru` | `BLUE_ORCHID` | Jégvirág-koszorú | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `kristaly_katalizator` | `END_CRYSTAL` | Kristály-katalizátor | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `melyseg_szive` | `HEART_OF_THE_SEA` | A Mélység Szíve | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `oceanjaro_terkep` | `MAP` | Óceánjáró Térképe | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `orok_viragzas` | `PEONY` | Örök Virágzás Csokra | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `osi_ereklye_kiemeles` | `BRUSH` | Ereklye-kiemelő Készlet | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `totem_ujraelesztes` | `TOTEM_OF_UNDYING` | Újraélesztett Totem | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `vandorbot` | `STICK` | Vándorbot | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `vasfa_ij` | `BOW` | Vasfa Íj | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:ritkaság | `vegtelen_kodex` | `WRITTEN_BOOK` | A Végtelen Kódex | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:ritkaság | `vezetokurt` | `CONDUIT` | Mélység Vezérkürtje | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `vihar_palack` | `WIND_CHARGE` | Palackozott Vihar | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `vilagfa_magja` | `OAK_SAPLING` | A Világfa Magja | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:ritkaság | `wither_rozsa_oltvany` | `WITHER_ROSE` | Fonnyadt Rózsa-oltvány | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:rúnaírnok-(tervrajz) | `arnyuzo_tekercs` | `ENCHANTED_BOOK` | Árnyűző tekercs | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:rúnaírnok-(tervrajz) | `ej_fatyol_tekercs` | `ENCHANTED_BOOK` | Éj-fátyol tekercs | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:rúnaírnok-(tervrajz) | `kaosz_zabla_tekercs` | `ENCHANTED_BOOK` | Káosz-zabla tekercs | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:rúnaírnok-(tervrajz) | `meregfojto_tekercs` | `ENCHANTED_BOOK` | Méregfojtó tekercs | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:rúnaírnok-(tervrajz) | `runavert_tekercs` | `ENCHANTED_BOOK` | Rúnavért-tekercs | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:rúnaírnok-(tervrajz) | `viharfogo_tekercs` | `ENCHANTED_BOOK` | Viharfogó tekercs | rúna/könyv/tekercs, olvasható fő jel |
| profession-recipe:szerszám | `egyszeru_horgaszbot` | `FISHING_ROD` | Egyszerű Horgászbot | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:szerszám | `gyemant_fejsze` | `DIAMOND_AXE` | Gyémántfejsze | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:szerszám | `kovilta_fejsze` | `STONE_AXE` | Kővésett Fejsze | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:szerszám | `mesteri_horgaszbot` | `FISHING_ROD` | Mesteri Horgászbot | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:szerszám | `rezhorgany_horgaszbot` | `FISHING_ROD` | Rézhorgony Horgászbot | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:szerszám | `runafenyes_csakany` | `DIAMOND_PICKAXE` | Rúnafényes Bányászcsákány | rúna/könyv/tekercs, olvasható fő jel; 45 fokos eszköz/fegyver sziluett |
| profession-recipe:szerszám | `tarnasz_csakany_recept` | `DIAMOND_PICKAXE` | Tárnász Csákány | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:szerszám | `tartos_horgaszbot` | `FISHING_ROD` | Tartós Horgászbot | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:szerszám | `vasfejsze` | `IRON_AXE` | Vasfejsze | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:szerszám-(tervrajz) | `legendas_horgaszbot` | `FISHING_ROD` | Legendás Horgászbot | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:szerszám-(tervrajz) | `netherit_csakany` | `NETHERITE_PICKAXE` | Mélybányász Netherit Csákány | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:szerszám-(tervrajz) | `netherit_fejsze` | `NETHERITE_AXE` | Erdőirtó Netherit Fejsze | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:sötét-mágia | `ejszaka_pengeje` | `NETHERITE_SWORD` | Az Éjszaka Pengéje | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:vérszavanna-(tervrajz) | `napfogyatkozas` | `BOW` | Napfogyatkozás | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:vérszavanna-(tervrajz) | `zhoris_langnyelve` | `NETHERITE_SWORD` | I. Zhoris Lángnyelve | 45 fokos eszköz/fegyver sziluett |
| profession-recipe:étel | `banyasz_szalonna` | `COOKED_PORKCHOP` | Bányász Szalonnája | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:étel | `erdei_gombapite` | `PUMPKIN_PIE` | Erdei Gomba Pite | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:étel | `fonix_fuszeres_szarny` | `COOKED_CHICKEN` | Főnixfűszeres Szárny | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:étel | `fuszeres_vandorhus` | `COOKED_MUTTON` | Fűszeres Vándorhús | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:étel | `halasz_fogasa` | `COOKED_SALMON` | Halász Fogása | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:étel | `hamvak_lakomaja` | `BEETROOT_SOUP` | Hamvak Lakomája | étel/ital, jól olvasható alapforma |
| profession-recipe:étel | `harcos_husos_tal` | `COOKED_BEEF` | Harcos Húsos Tála | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:étel | `kapu_lakomaja` | `ENCHANTED_GOLDEN_APPLE` | A Kapu Lakomája | étel/ital, jól olvasható alapforma |
| profession-recipe:étel | `lakodalmas_torta` | `CAKE` | Lakodalmas Emeletes Torta | étel/ital, jól olvasható alapforma |
| profession-recipe:étel | `mezes_puszedli` | `COOKIE` | Mézes Puszedli | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:étel | `pasztor_urucomb` | `COOKED_MUTTON` | Pásztor Ürücombja | étel/ital, jól olvasható alapforma |
| profession-recipe:étel | `sarkany_porkolt` | `RABBIT_STEW` | Sárkány-pörkölt | étel/ital, jól olvasható alapforma |
| profession-recipe:étel | `tengerek_gyongye` | `COOKED_COD` | Tengerek Gyöngye | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:étel | `tuzes_chili_tal` | `COOKED_BEEF` | Tüzes Chilis Tál | étel/ital, jól olvasható alapforma |
| profession-recipe:étel | `vadlakoma` | `COOKED_BEEF` | Vérszavannai Vadlakoma | étel/ital, jól olvasható alapforma |
| profession-recipe:étel | `vandor_pogacsaja` | `BREAD` | Vándor Pogácsája | étel/ital, jól olvasható alapforma |
| profession-recipe:étel | `vandor_uti_kenyer` | `BREAD` | Vándor Úti Kenyere | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:étel | `vandorunnep_lepenye` | `PUMPKIN_PIE` | Vándorünnep Lepénye | vanilla-hű ikon, név szerinti fő motívum |
| profession-recipe:étel-(tervrajz) | `aranyalma_lakoma` | `GOLDEN_APPLE` | Aranyalma Lakoma | étel/ital, jól olvasható alapforma |
| profession-recipe:étel-(tervrajz) | `legendas_lakoma` | `ENCHANTED_GOLDEN_APPLE` | Legendás Lakoma | étel/ital, jól olvasható alapforma |
| relic | `relic_bone_wing` | `ELYTRA` | Csontszárny | csontból szőtt szárny, sötét hártya, hideg türkiz ízületek |
| relic | `relic_eleftheria_konnye` | `HEART_OF_THE_SEA` | Eleftheria Könnye | fekete könnycsepp, belső türkiz fénymag |
| relic | `relic_frost_wing` | `ELYTRA` | Zúzmara-szárny | jégkristály tollak, kék-ezüst fagyfény |
| relic | `relic_metelytepo` | `GOLDEN_AXE` | Mételytépő | relikvia, erős egyedi sziluett, lore-akcent |
| relic | `relic_phoenix_wing` | `ELYTRA` | Főnix-szárny | lángoló tollú szárny, vörös-arany izzás |
| relic | `relic_sarkany_tojas` | `DRAGON_EGG` | Sárkánytojás-töredék | repedt sárkánytojás-szilánk, lila mélyfény |
| relic | `relic_wander_wind` | `ELYTRA` | Vándorszél | könnyű áttetsző tollak, égszínkék szélmotívum |

## Karbantartási szabály

- Új itemnél a config/kód `item-model` értéke legyen `icesmp:<modell-id>`.
- Ugyanez a `<modell-id>` szerepeljen a manifestben és a három pack-fájl útvonalában.
- A manifestet a configból érdemes újragenerálni, majd kézzel csak a `Prompt-hint` mezőt finomítani.
