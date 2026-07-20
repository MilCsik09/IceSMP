# Resource Pack — CustomModelData regiszter

A plugin MINDEN egyedi (custom/unique) tárgya CustomModelData-t (CMD) visel, hogy a
resource pack készítő egyedi textúrát adhasson nekik. **Új custom item = új CMD +
új sor ebben a fájlban** — ez a lista a resource pack egyetlen hiteles forrása.

## Generált resource pack

A `tools/build_resourcepack.py` ebből a regiszterből legenerálja a teljes packot a
`resourcepack/` mappába (16x16 pixel-art textúrák egységes stílusban, icesmp modellek,
`assets/minecraft/items/*.json` CMD-kapcsolók) és becsomagolja `IceSMP-ResourcePack.zip`-be.
Új item felvétele után futtasd újra: `python3 tools/build_resourcepack.py` (Pillow kell).
A generált textúra placeholder — bármelyik felülírható kézzel rajzolttal a
`resourcepack/assets/icesmp/textures/item/` alatt (a fájlnév tartása mellett).

**Nincs kivétel:** minden material-kapcsoló fallbackje a VALÓDI vanilla item-definíció
(`tools/vanilla_items/` cache, forrás: mcmeta tükör `1.21.11-assets`) — az iránytű
tű-animációja, a szigony kézben-3D-je, az elytra törött-állapota és a bőr-itemek
festék-színezése is bitpontosan megmarad a nem-CMD-s példányokon.

## Kiosztott tartományok

| Tartomány | Rendszer | Forrás |
|---|---|---|
| 1001–1004 | Frakció-valuta veretek | `CurrencyType` enum |
| 1010 | Kopott erszény | `MoneyPouchItemFactory` |
| 4101, 4201–4205 | Relikviák (7 db) | `RelicManager` / `relics.yml` |
| 5201–5213 | Kaszt-katalizátorok | `CatalystItemFactory` |
| 5301–5302 | Pet-befogó eszközök | `CaptureItemFactory` |
| 5401 | Ostromágyú | `SiegeWeaponFactory` |
| 6210 | Recept-tervrajz | `BlueprintItemFactory` |
| 6000–6199 | Unique szakma-anyagok | `profession-materials.yml` |
| 6201–6202 | Láda-kulcsok | `crates.yml` |
| 6300–6438 | Nevesített recept-tárgyak | `profession-recipes.yml` (`result.custom-model-data`) |
| 6450–6451 | Bolt-különlegességek (feketepiac) | `economy.yml` (`custom-model-data`) |
| 6460–6463 | Nevesített loot-dropok | `loot.yml` (`custom-model-data`) |

## 1001–1010 — Pénz-tárgyak

| CMD | Item | Material |
|---|---|---|
| 1001 | Parázsló Parals (RED veret) | PAPER |
| 1002 | Hópihér-veret (BLUE veret) | PAPER |
| 1003 | Creutzér (NEUTRAL veret) | PAPER |
| 1004 | Csontveret (DARK veret) | PAPER |
| 1010 | Kopott erszény | LEATHER |

## 4101, 4201–4205 — Relikviák

| CMD | Item | Material |
|---|---|---|
| 4101 | A Mételytépő | GOLDEN_AXE |
| 4201 | Főnix-szárny | ELYTRA |
| 4202 | Zúzmara-szárny | ELYTRA |
| 4203 | Vándorszél | ELYTRA |
| 4204 | Csontszárny | ELYTRA |
| 4205 | Eleftheria Könnye | HEART_OF_THE_SEA |

## 5301–5401, 6210 — Rendszer-itemek

| CMD | Item | Material | Forrás |
|---|---|---|---|
| 5301 | Ősi Kötés Póráza (Vadmester befogó) | LEAD | CaptureItemFactory |
| 5302 | Sötét Paktum-tekercs (Nekromanta befogó) | GHAST_TEAR | CaptureItemFactory |
| 5401 | Ostromágyú | TNT_MINECART | SiegeWeaponFactory |
| 6210 | Recept-tervrajz | KNOWLEDGE_BOOK | BlueprintItemFactory |

## 6450–6463 — Bolt-különlegességek és nevesített loot

| CMD | Item | Material | Forrás |
|---|---|---|---|
| 6450 | Bokic-menti Sétapálca | STICK | economy.yml (feketepiac) |
| 6451 | Hamisított Menlevél | PAPER | economy.yml (feketepiac) |
| 6460 | A Hetedik Vérháború Rozsdás Pengéje | IRON_SWORD | loot.yml (named) |
| 6461 | Megrontott Elit Páncél | CHAINMAIL_CHESTPLATE | loot.yml (named) |
| 6462 | Fekete Csont | BONE | loot.yml (named) |
| 6463 | A Néma Királynő Suttogása | NETHERITE_SWORD | loot.yml (named) |

## 5201–5213 — Kaszt-katalizátorok

| CMD | Kaszt | Item | Material |
|---|---|---|---|
| 5201 | WIZARD | Caldesterai Rúnakódex | ENCHANTED_BOOK |
| 5202 | WARRIOR | Sárkánykirály Kürtje | GOAT_HORN |
| 5203 | ARCHER | Soleil Vadásztarsolya | RABBIT_HIDE |
| 5204 | ASSASSIN | Homály-szilánk | FLINT |
| 5205 | DRUID | Aetrinita Sarja | OAK_SAPLING |
| 5206 | PALADIN | Hajnaltűz Harangja | BELL |
| 5207 | DEATH_KNIGHT | Néma Rúnakoponya | WITHER_SKELETON_SKULL |
| 5208 | SHAMAN | Ősvihar Totemje | TOTEM_OF_UNDYING |
| 5209 | MONK | Élet Ága | BAMBOO |
| 5210 | PRIEST | Asterlayna Gyertyája | WHITE_CANDLE |
| 5211 | WARLOCK | Kárhozat Lámpása | SOUL_LANTERN |
| 5212 | DEMON_HUNTER | Hasadék Szeme | ENDER_EYE |
| 5213 | EVOKER | Sárkányvér-fiola | DRAGON_BREATH |

## 6000–6199 — Unique szakma-anyagok (profession-materials.yml)

| CMD | Id | Név | Material |
|---|---|---|---|
| 6000 | tiszta_vasesszencia | Tiszta Vasesszencia | IRON_NUGGET |
| 6001 | gyogy_kivonat | Gyógy-kivonat | GLOW_BERRIES |
| 6002 | rezgo_rez_otvozet | Rezgő Rézötvözet | COPPER_INGOT |
| 6003 | kemenyfa_gerenda | Keményfa Gerenda | STRIPPED_OAK_WOOD |
| 6004 | runapor | Rúnapor | GLOWSTONE_DUST |
| 6005 | jegviragpor | Jégvirág-por | SUGAR |
| 6006 | parazsmag | Parázsmag | BLAZE_POWDER |
| 6007 | viharkvarc | Viharkvarc | QUARTZ |
| 6008 | melysegi_borostyan | Mélységi Borostyán | RAW_GOLD |
| 6010 | vad_esszencia | Vad Esszencia | PHANTOM_MEMBRANE |
| 6011 | szorny_mag | Szörny Mag | ECHO_SHARD |
| 6012 | arnyekpor | Árnyékpor | SCULK_VEIN |
| 6013 | osi_ereklyeszilank | Fekete Villám Szilánk | NETHER_STAR |
| 6014 | emlekszilank | Opálos Emlékszilánk | AMETHYST_SHARD |
| 6015 | suttogas_meghivo | Suttogás | ECHO_SHARD |
| 6016 | sarkanycsont_szilank | Sárkánycsont-szilánk | BONE |
| 6017 | fonixpihe | Főnixpihe | FEATHER |
| 6018 | holdezust_huzal | Holdezüst Huzal | CHAIN |
| 6019 | csontenyv | Csontenyv | BONE_MEAL |
| 6020 | viaszpecset | Számvevő-pecsétviasz | HONEYCOMB |
| 6021 | sarkfeny_cseppko | Sarkfény-cseppkő | PRISMARINE_CRYSTALS |
| 6022 | szavannafu_kotel | Szavannafű-kötél | VINE |
| 6023 | obszidian_szilank | Obszidián-szilánk | FLINT |
| 6024 | arnygomba | Mortengradi Árnygomba | CRIMSON_FUNGUS |
| 6025 | lelekhamu | Lélekhamu | GUNPOWDER |
| 6026 | aranyfust_lemez | Aranyfüst-lemez | GOLD_NUGGET |
| 6027 | gyongyhaz_pikkely | Gyöngyház-pikkely | PRISMARINE_SHARD |
| 6028 | vandorfuszer | Vándorfűszer | COCOA_BEANS |
| 6029 | dermedt_konnycsepp | Dermedt Könnycsepp | GHAST_TEAR |
| 6030 | karhozat_parazs | A Kapu Parazsa | FIRE_CHARGE |
| 6031 | nema_kristaly | Néma Kristály | AMETHYST_SHARD |
| 6032 | elso_csend_szilankja | Az Első Csend Szilánkja | ECHO_SHARD |
| 6033 | lampaolaj | Finomított Lámpaolaj | GLOW_INK_SAC |
| 6034 | folyositoszer | Kovács-folyósítószer | BLAZE_POWDER |
| 6100 | robbantopor | Robbantópor | GUNPOWDER |
| 6101 | tarnatamasz_szegecs | Tárnatámasz-szegecs | IRON_NUGGET |
| 6102 | csillekenocs | Csillekenőcs | SLIME_BALL |
| 6103 | ercmoso_lug | Ércmosó-lúg | GLASS_BOTTLE |
| 6104 | melysegi_iranytu | Mélységi Iránytű-tű | COMPASS |
| 6105 | uvegfiola_keszlet | Üvegfiola-készlet | GLASS_BOTTLE |
| 6106 | aszalohalo | Aszalóháló | COBWEB |
| 6107 | oltoviasz | Oltóviasz | HONEYCOMB |
| 6108 | tozegkocka | Tőzegkocka | PACKED_MUD |
| 6109 | permetezo_kanna | Permetező-kanna | BUCKET |
| 6110 | fenoko | Fenőkő | SMOOTH_STONE |
| 6111 | gyantaoldo | Gyantaoldó | HONEY_BOTTLE |
| 6112 | acskapocs | Ácskapocs | IRON_NUGGET |
| 6113 | merozsinor | Mérőzsinór | STRING |
| 6114 | favedo_pac | Favédő pác | INK_SAC |
| 6115 | edzoolaj | Edzőolaj | MAGMA_CREAM |
| 6116 | polirpaszta | Polírpaszta | SUGAR |
| 6117 | nyelbor | Nyélbőr | LEATHER |
| 6118 | fujtatobor | Fújtatóbőr | RABBIT_HIDE |
| 6119 | desztillalt_esoviz | Desztillált Esővíz | GLASS_BOTTLE |
| 6120 | szuropapir | Szűrőpapír | PAPER |
| 6121 | katalizator_so | Katalizátor-só | GLOWSTONE_DUST |
| 6122 | olomdugo | Ólomdugó | IRON_NUGGET |
| 6123 | lombik_szen | Lombik-szén | CHARCOAL |
| 6124 | irnok_tinta | Írnok-tinta | INK_SAC |
| 6125 | pergamen_simito | Pergamen-simító | BONE |
| 6126 | ezust_toll | Ezüst-toll | FEATHER |
| 6127 | runakreta | Rúnakréta | CLAY_BALL |
| 6128 | viaszgyertya | Viasz-gyertya | CANDLE |
| 6129 | horogkeszlet | Horogkészlet | TRIPWIRE_HOOK |
| 6130 | csalizsir | Csalizsír | SLIME_BALL |
| 6131 | halofonal | Hálófonal | STRING |
| 6132 | parafa_uszo | Parafa-úszó | OAK_BUTTON |
| 6133 | sozott_csali | Sózott csali | DRIED_KELP |
| 6134 | koso | Kősó | SUGAR |
| 6135 | sutopergamen | Sütőpergamen | PAPER |
| 6136 | ecet_eszencia | Ecet-eszencia | HONEY_BOTTLE |
| 6137 | fustoloforgacs | Füstölőforgács | STICK |
| 6138 | vaj | Friss Vaj | HONEYCOMB |

## 6201–6202 — Láda-kulcsok (crates.yml)

| CMD | Item | Material |
|---|---|---|
| 6201 | Kereskedő Kulcs | TRIPWIRE_HOOK |
| 6202 | Kincses Kulcs | TRIPWIRE_HOOK |

## 6300–6438 — Recept-tárgyak CMD-vel (profession-recipes.yml)

Minden nevesített (lore-os) recept-eredmény + a végtermék-eszközök + a 13 affix-roll
felszerelés (íjak/pajzsok/sisakok — a név a rollból, a textúra a receptből).

Szándékosan CMD NÉLKÜL marad: a 215 vanilla köteg-output (árucikk, stackelnie kell),
a 22 lánc-köztes/lerakható egydarabos (netherit-sor, nautilus→conduit, üllő/pulpitus,
névtábla, főzet-alap), és a 24 unique-anyag recept (a factory 6000+ CMD-jét viseli).

| CMD | Recept-id | Név | Material | Szakma |
|---|---|---|---|---|
| 6300 | borostyan_lampa | Borostyánfényű Lámpás | LANTERN | miner |
| 6301 | melyseg_szive | A Mélység Szíve | HEART_OF_THE_SEA | miner |
| 6302 | jegvirag_koszoru | Jégvirág-koszorú | BLUE_ORCHID | herbalist |
| 6303 | wither_rozsa_oltvany | Fonnyadt Rózsa-oltvány | WITHER_ROSE | herbalist |
| 6304 | orok_viragzas | Örök Virágzás Csokra | PEONY | herbalist |
| 6305 | vilagfa_magja | A Világfa Magja | OAK_SAPLING | herbalist |
| 6306 | vandorbot | Vándorbot | STICK | lumberjack |
| 6307 | erdok_kurtje | Erdők Kürtje | GOAT_HORN | lumberjack |
| 6308 | vasfa_ij | Vasfa Íj | BOW | lumberjack |
| 6309 | erdo_szive_totem | Az Erdő Szíve | TOTEM_OF_UNDYING | lumberjack |
| 6310 | cehmester_ulloje | A Céhmester Üllője | ANVIL | armorer |
| 6311 | vihar_palack | Palackozott Vihar | WIND_CHARGE | alchemist |
| 6312 | bolcsek_kove | A Bölcsek Köve | EXPERIENCE_BOTTLE | alchemist |
| 6313 | csendulo_harang | Csendülő Harang | BELL | enchanter |
| 6314 | emlekek_konyve | Emlékek Könyve | WRITTEN_BOOK | enchanter |
| 6315 | vegtelen_kodex | A Végtelen Kódex | WRITTEN_BOOK | enchanter |
| 6316 | viharjelzo_boja | Viharjelző Bója | LANTERN | fisherman |
| 6317 | mestermuves_bot | Mesterhorgász Botja | FISHING_ROD | fisherman |
| 6318 | oceanjaro_terkep | Óceánjáró Térképe | MAP | fisherman |
| 6319 | bokic_aldasa | A Bokic Áldása | TRIDENT | fisherman |
| 6320 | fonix_fuszeres_szarny | Főnixfűszeres Szárny | COOKED_CHICKEN | cook |
| 6321 | lakodalmas_torta | Lakodalmas Emeletes Torta | CAKE | cook |
| 6322 | kapu_lakomaja | A Kapu Lakomája | ENCHANTED_GOLDEN_APPLE | cook |
| 6323 | tarnasz_csakany_recept | Tárnász Csákány | DIAMOND_PICKAXE | miner |
| 6324 | netherit_csakany | Mélybányász Netherit Csákány | NETHERITE_PICKAXE | miner |
| 6325 | kovilta_fejsze | Kővésett Fejsze | STONE_AXE | lumberjack |
| 6326 | vasfejsze | Vasfejsze | IRON_AXE | lumberjack |
| 6327 | gyemant_fejsze | Gyémántfejsze | DIAMOND_AXE | lumberjack |
| 6328 | netherit_fejsze | Erdőirtó Netherit Fejsze | NETHERITE_AXE | lumberjack |
| 6329 | vaskard | Vaskard | IRON_SWORD | armorer |
| 6330 | vas_sisak | Vassisak | IRON_HELMET | armorer |
| 6331 | vas_csizma | Vascsizma | IRON_BOOTS | armorer |
| 6332 | vas_lablemez | Vas Lábvért | IRON_LEGGINGS | armorer |
| 6333 | bastya_pajzs_recept | Bástya Pajzs | SHIELD | armorer |
| 6334 | gyemant_kard | Gyémántkard | DIAMOND_SWORD | armorer |
| 6335 | gyemant_sisak | Gyémántsisak | DIAMOND_HELMET | armorer |
| 6336 | gyemant_mellvert | Gyémánt Mellvért | DIAMOND_CHESTPLATE | armorer |
| 6337 | haromagu_szigony | Háromágú Szigony | TRIDENT | armorer |
| 6338 | sarkanyvert_recept | Sárkányvért | NETHERITE_CHESTPLATE | armorer |
| 6339 | netherit_kard | Netherit Pallos | NETHERITE_SWORD | armorer |
| 6340 | netherit_sisak | Netherit Csatasisak | NETHERITE_HELMET | armorer |
| 6341 | bajnok_elixir | Bajnok Elixírje | POTION | alchemist |
| 6342 | tartossag_tomus | Tartósság Tomus | ENCHANTED_BOOK | enchanter |
| 6343 | hatekonysag_tomus | Hatékonyság Tomus | ENCHANTED_BOOK | enchanter |
| 6344 | eles_tomus | Élesség Tomus | ENCHANTED_BOOK | enchanter |
| 6345 | zuhanascsokkentes_tomus | Zuhanáscsökkentés Tomus | ENCHANTED_BOOK | enchanter |
| 6346 | vedelem_tomus | Védelem Tomus | ENCHANTED_BOOK | enchanter |
| 6347 | csali_tomus | Csali Tomus | ENCHANTED_BOOK | enchanter |
| 6348 | fosztogatas_tomus | Fosztogatás Tomus | ENCHANTED_BOOK | enchanter |
| 6349 | szerencse_tomus | Szerencse Tomus | ENCHANTED_BOOK | enchanter |
| 6350 | javitas_tomus | Javítás Tomus | ENCHANTED_BOOK | enchanter |
| 6351 | orvenylo_pusztitas_tomus | Örvénylő Pusztítás Tomus | ENCHANTED_BOOK | enchanter |
| 6352 | selyemerintes_tomus | Selyemérintés Tomus | ENCHANTED_BOOK | enchanter |
| 6353 | egyszeru_horgaszbot | Egyszerű Horgászbot | FISHING_ROD | fisherman |
| 6354 | tartos_horgaszbot | Tartós Horgászbot | FISHING_ROD | fisherman |
| 6355 | mesteri_horgaszbot | Mesteri Horgászbot | FISHING_ROD | fisherman |
| 6356 | legendas_horgaszbot | Legendás Horgászbot | FISHING_ROD | fisherman |
| 6357 | tengeristen_amulettje | Tengeristen Amulettje | CONDUIT | fisherman |
| 6358 | aranyalma_lakoma | Aranyalma Lakoma | GOLDEN_APPLE | cook |
| 6359 | legendas_lakoma | Legendás Lakoma | ENCHANTED_GOLDEN_APPLE | cook |
| 6360 | esszencialt_vasvert | Esszenciált Vasvért | DIAMOND_CHESTPLATE | armorer |
| 6361 | runakovacsolt_penge | Rúnakovácsolt Penge | NETHERITE_SWORD | armorer |
| 6362 | vadbor_pancel | Vadbőr Vért | DIAMOND_LEGGINGS | armorer |
| 6363 | arnyekmereg | Árnyékméreg | SPLASH_POTION | alchemist |
| 6364 | szornymag_talizman | Szörnymag Talizmán | ENCHANTED_BOOK | enchanter |
| 6365 | ereklye_penge | Villámszilánk Pengéje | NETHERITE_SWORD | armorer |
| 6366 | runafenyes_csakany | Rúnafényes Bányászcsákány | DIAMOND_PICKAXE | miner |
| 6367 | ereklyeszilankos_banyasisak | Villámszilánkos Bányászsisak | DIAMOND_HELMET | miner |
| 6368 | vasesszencias_pajzs | Vasesszenciás Pajzs | SHIELD | armorer |
| 6369 | rezvertezet_lablemez | Rézvértezet Lábvért | DIAMOND_LEGGINGS | armorer |
| 6370 | vadolo_csizma | Vadölő Csizma | DIAMOND_BOOTS | armorer |
| 6371 | szornyvert_mellveny | Szörnyvért Mellvény | NETHERITE_CHESTPLATE | armorer |
| 6372 | ereklye_elixir | Villámszilánk Elixírje | POTION | alchemist |
| 6373 | vasesszencias_paloscsapas_tomus | Vasesszenciás Páncéltörés Tomus | ENCHANTED_BOOK | enchanter |
| 6374 | kemenyfa_ijkeret_tomus | Keményfa Íjkeret Tomus | ENCHANTED_BOOK | enchanter |
| 6375 | rezhorgany_horgaszbot | Rézhorgony Horgászbot | FISHING_ROD | fisherman |
| 6376 | melytengeri_ereklyeszigony | Mélytengeri Villámszigony | TRIDENT | fisherman |
| 6377 | kallan_szeletelo | Kallan Szeletelője | BOW | armorer |
| 6378 | glatziendorfi_jegvert | Glatziendorfi Jégvért | NETHERITE_CHESTPLATE | armorer |
| 6379 | jegsarkany_kantar | Jégsárkány-Kantár | SADDLE | armorer |
| 6380 | pyralingradi_tuzkopo | Pyralingradi Tűzköpő | CROSSBOW | armorer |
| 6381 | verszavanna_agyara | A Vérszavanna Agyara | NETHERITE_SWORD | armorer |
| 6382 | fonix_tollkopeny | Főnix-Tollköpeny | LEATHER_CHESTPLATE | armorer |
| 6383 | runavert_tekercs | Rúnavért-tekercs | ENCHANTED_BOOK | enchanter |
| 6384 | fagyasztott_pisztrang | Fagyasztott Tavi Pisztráng | COOKED_SALMON | cook |
| 6385 | fonixtojas_rantotta | Fűszeres Főnixtojás-Rántotta | PUMPKIN_PIE | cook |
| 6386 | kakaobabos_sutemeny | Tiltott Kakaóbabos Sütemény | COOKIE | cook |
| 6387 | mortengradi_hamukenyer | Mortengradi Hamukenyér | BREAD | cook |
| 6388 | vasmuvek_csakanya | Vasművek Akadémiájának Csákánya | DIAMOND_PICKAXE | miner |
| 6389 | bokic_horgaszbot | Bokic-menti Horgászbot | FISHING_ROD | fisherman |
| 6390 | smaragdko_bankbetet | Smaragdkő Bankbetét | PAPER | enchanter |
| 6391 | szellemszarvas_bubaj | Szellemszarvas-Bűbáj | RABBIT_FOOT | herbalist |
| 6392 | glatziendorfi_jegtoro | Glatziendorfi Jégtörő | NETHERITE_AXE | armorer |
| 6393 | miinus_haragja | V. Miinus Haragja | NETHERITE_SWORD | armorer |
| 6394 | sarkanycsont_ij | Sárkánycsont Íj | BOW | armorer |
| 6395 | zhoris_langnyelve | I. Zhoris Lángnyelve | NETHERITE_SWORD | armorer |
| 6396 | napfogyatkozas | Napfogyatkozás | BOW | armorer |
| 6397 | sarkany_porkolt | Sárkány-pörkölt | RABBIT_STEW | cook |
| 6398 | vandor_uti_kenyer | Vándor Úti Kenyere | BREAD | cook |
| 6399 | bokic_gyogytea | Bokic-parti Gyógytea | HONEY_BOTTLE | herbalist |
| 6400 | sarkanycsont_pajzs | Sárkánycsont Pajzs | SHIELD | armorer |
| 6401 | viharuveg_lampas | Viharüveg Lámpás | LANTERN | enchanter |
| 6402 | fagypancel_tekercs | Fagypáncél Tekercse | ENCHANTED_BOOK | enchanter |
| 6403 | fonixtoll_tekercs | Főnixtoll Tekercse | ENCHANTED_BOOK | enchanter |
| 6404 | vadlakoma | Vérszavannai Vadlakoma | COOKED_BEEF | cook |
| 6405 | vandorunnep_lepenye | Vándorünnep Lepénye | PUMPKIN_PIE | cook |
| 6406 | hamvak_lakomaja | Hamvak Lakomája | BEETROOT_SOUP | cook |
| 6407 | melysegi_korona | A Mélység Népe Koronája | NETHERITE_HELMET | armorer |
| 6408 | viharjaro_csizma | Viharjáró Csizma | NETHERITE_BOOTS | armorer |
| 6409 | eleftheria_fatyla | Eleftheria Fátyla | NETHERITE_CHESTPLATE | armorer |
| 6410 | pecsetes_szerzodes | Pecsétes Szerződés | PAPER | enchanter |
| 6411 | sarkfeny_prizma | Sarkfény-prizma | SEA_LANTERN | enchanter |
| 6412 | csontenyves_ijkar | Csontenyves Íjkar | BOW | lumberjack |
| 6413 | gyongyhaz_talizman | Gyöngyház Talizmán | NAUTILUS_SHELL | fisherman |
| 6414 | fuszeres_vandorhus | Fűszeres Vándorhús | COOKED_MUTTON | cook |
| 6415 | csillekerek | Megkent Csille | MINECART | miner |
| 6416 | melysegi_tajolo | Tárnatájoló | COMPASS | miner |
| 6417 | tavcso | Bányamérnöki Távcső | SPYGLASS | miner |
| 6418 | osi_ereklye_kiemeles | Ereklye-kiemelő Készlet | BRUSH | miner |
| 6419 | vas_lopancel | Vas Lópáncél | IRON_HORSE_ARMOR | armorer |
| 6420 | arany_lopancel | Arany Lópáncél | GOLDEN_HORSE_ARMOR | armorer |
| 6421 | gyemant_lopancel | Gyémánt Lópáncél | DIAMOND_HORSE_ARMOR | armorer |
| 6422 | totem_ujraelesztes | Újraélesztett Totem | TOTEM_OF_UNDYING | alchemist |
| 6423 | kristaly_katalizator | Kristály-katalizátor | END_CRYSTAL | alchemist |
| 6424 | felepules_iranytuje | Felépülés Iránytűje | RECOVERY_COMPASS | enchanter |
| 6425 | vezetokurt | Mélység Vezérkürtje | CONDUIT | fisherman |
| 6426 | vadaszij | Vadászíj | BOW | lumberjack |
| 6427 | mefonott_pajzs | Erdőjáró Pajzs | SHIELD | lumberjack |
| 6428 | feszitett_szaru_ij | Feszített Szaruíj | BOW | lumberjack |
| 6429 | celkereszt_szamszerij | Céhmesteri Számszeríj | CROSSBOW | lumberjack |
| 6430 | lancing | Kovácsolt Láncing | CHAINMAIL_CHESTPLATE | armorer |
| 6431 | lancnadrag | Kovácsolt Láncnadrág | CHAINMAIL_LEGGINGS | armorer |
| 6432 | pajzsdudor | Dudoros Hadipajzs | SHIELD | armorer |
| 6433 | pancelozott_sisakrostely | Rostélyos Csatasisak | DIAMOND_HELMET | armorer |
| 6434 | uszokeszlet | Úszókészlet | FISHING_ROD | fisherman |
| 6435 | vizallo_csizma | Halászcsizma | LEATHER_BOOTS | fisherman |
| 6436 | melyvizi_horog | Mélyvízi Horogsor | FISHING_ROD | fisherman |
| 6437 | teknos_sisak | Teknőspáncél-sisak | TURTLE_HELMET | fisherman |
| 6438 | halaszkalap | Halászkalap | LEATHER_HELMET | fisherman |

