# -*- coding: utf-8 -*-
"""A docs/RESOURCE_PACK_CMD.md művész-bibliává bővítése.

Minden CMD-hez részletes, textúra-készítőnek szóló leírást generál:
mit ábrázoljon, milyen színvilággal, milyen hangulatban (a config-lore-ból),
és melyik vanilla itemet váltja le. A tényadatok forrása ugyanaz, mint a
build_resourcepack.py-é; az ábrázolás/színvilág a pack_art motívum- és
paletta-hozzárendeléséből jön, így a leírás és a (placeholder) textúra
mindig konzisztens. Futtatás a repo gyökeréből:
    python3 tools/build_cmd_artdoc.py
"""
import os
import re
import sys

import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_resourcepack as bp  # noqa: E402
import pack_art as art  # noqa: E402

ROOT = bp.ROOT
CFG = bp.CFG

PALETTE_HU = {
    'gold': 'meleg arany, sárga csillanásokkal', 'silver': 'hűvös ezüstszürke',
    'copper': 'vörösréz, narancsos árnyalatokkal', 'iron': 'világos acélszürke',
    'steel': 'sötétebb, kékes acél', 'ice': 'jeges világoskék', 'fire': 'izzó narancsvörös',
    'storm': 'türkizes viharzöld', 'nature': 'élénk levélzöld', 'poison': 'sötét méregzöld',
    'shadow': 'mély ibolyalila', 'bone': 'törtfehér csontszín', 'amber': 'borostyánsárga',
    'pearl': 'gyöngyházas, rózsás fehér', 'blood': 'mélyvörös', 'royal': 'királylila',
    'water': 'középkék', 'earth': 'földbarna', 'leather': 'cserzett bőrbarna',
    'paper': 'krémszínű pergamen', 'crystal': 'világító cián', 'night': 'éjkék',
    'coal': 'szénfekete-szürke', 'honey': 'mézarany', 'wood': 'meleg fabarna',
    'salt': 'törtfehér', 'wine': 'bordó', 'moss': 'mohazöld', 'sky': 'égszínkék',
    'blush': 'rózsás pír', 'lich': 'hideg türkiz derengés (lich-fény)',
}

MOTIF_HU = {
    'm_shard': 'hosszúkás kristályszilánk, éles törésfelületekkel',
    'm_crystals': 'három kristálytüske közös kőalapon',
    'm_powder': 'kupacba szórt finom por, pár csillanó szemcsével',
    'm_vial': 'dugós üvegfiola, benne folyadékkal',
    'm_bottle': 'gömbölyű palack folyadékkal',
    'm_scroll': 'félig kigördült pergamentekercs írássorokkal',
    'm_book': 'vaskos, veretes könyv',
    'm_tome': 'díszes varázskönyv, a borítóján drágakővel',
    'm_ingot': 'öntött fémrúd (ingot-forma)',
    'm_plate': 'kalapált, fényes fémlemez',
    'm_coil': 'feltekercselt huzal / drótspirál',
    'm_rope': 'sodrott kötélköteg',
    'm_feather': 'nagy madártoll',
    'm_mushroom': 'kalapos gomba',
    'm_ember': 'izzó parázsdarab, felcsapó lángnyelvekkel',
    'm_snow': 'hatágú hópehely / jégkristály',
    'm_drop': 'nagy, nehéz csepp-forma',
    'm_key': 'karikás fejű kulcs',
    'm_horn': 'ívelt kürt',
    'm_orb': 'energiagömb, izzó belső maggal',
    'm_rune_tablet': 'kőtábla vésett rúnajellel',
    'm_kit': 'szerszámos láda / készlet-doboz',
    'm_candle': 'égő gyertya',
    'm_oilcan': 'fém kanna kiöntőcsőrrel',
    'm_hook': 'fém horgászhorog',
    'm_net': 'csomózott háló',
    'm_bobber': 'piros-fehér horgászúszó',
    'm_fish': 'kis hal oldalnézetből',
    'm_stew': 'gőzölgő tál leves/ragu',
    'm_bread': 'frissen sült cipó/vekni',
    'm_pie': 'kerek pite / sütemény',
    'm_meat': 'sült hús, csonttal',
    'm_cake': 'díszített torta',
    'm_lantern': 'fém lámpás, izzó ablakkal',
    'm_bell': 'öntött harang',
    'm_map': 'kiterített térkép útvonallal és jelöléssel',
    'm_cart': 'bányász-csille kerekekkel',
    'm_compass_rose': 'iránytű szélrózsával',
    'm_armor': 'mellvért elölnézetből',
    'm_helmet': 'sisak',
    'm_boots': 'pár csizma',
    'm_sword': 'kard/penge, markolattal',
    'm_axe': 'fejsze/balta',
    'm_pick': 'csákány',
    'm_bow': 'felajzott íj húrral',
    'm_crossbow': 'számszeríj',
    'm_shield': 'címerpajzs',
    'm_saddle': 'nyereg / lószerszám',
    'm_totem': 'faragott totemfigura',
    'm_salt': 'kristályos só-kupac',
    'm_log': 'rönk, évgyűrűkkel',
    'm_sapling': 'fiatal csemete/hajtás',
    'm_flower': 'virág / virágcsokor',
    'm_chalk': 'egyenes rúd/pálca forma',
    'm_brush': 'finomszőrű régész-ecset',
    'm_spyglass': 'kihúzható távcső',
    'm_amulet': 'láncon függő amulett-medál',
    'm_seal': 'pecsétnyomó viaszpecséttel',
    'm_torchset': 'összekötözött fáklyaköteg',
    'm_quill': 'írótoll tintával',
    'm_glowink': 'derengő tintazsák / fénylő gyöngy',
}

# Kézzel írt hős-leírások (ábrázolás, hangulat) — a színvilág a paletta-párból jön.
HERO = {
    'coin_red': ('kerek, vert érme, peremén rovátkolt díszítés, közepén dombornyomott LÁNGNYELV-címer',
                 'Perinfernicitas valutája, a Parázsló Parals — a láng népének büszke, forró aranya.'),
    'coin_blue': ('kerek, vert érme, rovátkolt peremmel, közepén dombornyomott HÓPEHELY-címer',
                  'Cryghaliris valutája, a Hópihér-veret — hideg, tiszta, ezüstös csillogás.'),
    'coin_neutral': ('kerek, vert érme, közepén dombornyomott KERESKEDŐ-MÉRLEG címer',
                     'Ryanora–Caldestera valutája, a Creutzér — a Bankárszövetség megbízható aranya.'),
    'coin_dark': ('kopott, sötét érme, közepén dombornyomott KOPONYA-címer, a koponya szemüregeiben apró TÜRKIZ izzással, szélein csorbulások',
                  'A Kitaszítottak Csontverete — akit ezzel fizetnek, nem kérdez. A türkiz szempár a Néma Királynő jele.'),
    'money_pouch': ('zsinórral összehúzott, kopott bőrerszény; a nyakánál kikandikáló 2-3 aranyérme',
                    'Talált pénz: mob-drop és horgász-lelet. Viseltes, útszéli hangulat — valaki elvesztette.'),
    'relic_metelytepo': ('arany harci balta, a feje körül halvány lila derengéssel; ősi, idegen mintázatú nyél',
                         'A Mételytépő — a törpék rejtélyes civilizációjának relikviája. Múzeumi kincs, nem szerszám.'),
    'relic_phoenix_wing': ('kiterjesztett, stilizált szárny lángoló tollakkal, a hegyénél izzással',
                           'Főnix-szárny — Perinfernicitas elytra-relikviája, Soleil főnixeinek tollából.'),
    'relic_frost_wing': ('kiterjesztett szárny jégkristály-tollakkal, fagyott csillogással',
                         'Zúzmara-szárny — Cryghaliris elytra-relikviája, jégsárkány-lehelettel átitatva.'),
    'relic_wander_wind': ('könnyű, világos szárny, lebegő, áttetsző tollakkal',
                          'Vándorszél — Ryanora & Caldestera szabad szele, Arkynn békés öröksége.'),
    'relic_bone_wing': ('csontokból szőtt, szakadozott szárny sötét hártyával, az ízületeknél hideg türkiz izzás-pontokkal',
                        'Csontszárny — a Káoszkor élőhalott-relikviája; éjjel viselője árnyékká válik.'),
    'relic_eleftheria_konnye': ('éjfekete, megkövült könnycsepp, belsejében halvány TÜRKIZ fénymaggal (lich-fény)',
                                'Eleftheria Könnye — a Néma Királynő első suttogása kővé dermedve.'),
    'key_koznapi': ('egyszerű vas kulcs, karikás fejjel',
                    'Kereskedő Kulcs — a Caldesterai Kereskedőláda nyitja. Hétköznapi, strapabíró darab.'),
    'key_ritka': ('díszes arany kulcs, ékköves, cizellált fejjel',
                  'a Caldesterai Kincsesláda kulcsa — ritka, ünnepélyes, ötvösmunka.'),
    'blueprint': ('kék tervrajz-lap fehér szerkesztési vonalakkal, egyik sarka felpöndörödik',
                  'Recept-tervrajz — ebből tanulják a mesterek a ritka recepteket.'),
    'capture_beast': ('feltekert pányva/lasszó, zöld természet-szimbólummal a közepén',
                      'Ősi Kötés Póráza — a Vadmester ezzel fogadja társává az állatokat (Aetrinita és Kallan kötése).'),
    'capture_necro': ('sötét pergamentekercs koponya-pecséttel, a koponya szemeiben türkiz izzással',
                      'Sötét Paktum-tekercs — a Nekromanta ezzel köti szolgájává a szörnyet (Eleftheria mérge).'),
    'siege_cannon': ('zömök, fekete ostromágyú-cső kerekes talpon, a csőtorkolatnál szikrával',
                     'Ostromágyú — a Hét Vérháború öröksége; csak raid alatt szólal meg.'),
    'shop_6450': ('elegáns sétapálca arany fejjel — ránézésre úri bot, de a fej alatt penge sejlik',
                  'Bokic-menti Sétapálca (feketepiac): az őrség botot lát, a penge nem ért egyet.'),
    'shop_6451': ('hivatalos irat vörös viaszpecséttel — kicsit TÚL tökéletes',
                  'Hamisított Menlevél: a Bankárszövetség pecsétje… majdnem.'),
    'loot_6460': ('rozsdamarta, csorba hosszúkard, régi vér sötét foltjaival',
                  'A Hetedik Vérháború Rozsdás Pengéje — egykor hadsereg-fegyver, ma néma harag.'),
    'loot_6461': ('szakadozott láncvért, a láncszemek közt hideg türkiz derengéssel',
                  'Megrontott Elit Páncél — az eltűnt nemesek dicsőségének maradványa.'),
    'loot_6462': ('koromfekete csontdarab, matt felülettel, hajszálvékony türkiz erezettel',
                  'Fekete Csont — nem ég el, nem törik, nem felejt.'),
    'loot_6463': ('éjsötét pengéjű kard, az él mentén vékony TÜRKIZ suttogás-fénnyel (lich-él)',
                  'A Néma Királynő Suttogása — nem penge: ígéret.'),
}


MATERIAL_SHAPE = {
    'TRIDENT': 'háromágú szigony', 'BOW': 'felajzott íj húrral', 'CROSSBOW': 'számszeríj',
    'SHIELD': 'címerpajzs', 'FISHING_ROD': 'horgászbot orsóval és zsinórral',
    'IRON_SWORD': 'egyenes hosszúkard', 'NETHERITE_SWORD': 'súlyos, sötét pengéjű kard',
    'DIAMOND_SWORD': 'kristálypengéjű kard', 'GOLDEN_AXE': 'arany harci balta',
    'DIAMOND_HELMET': 'díszes harci sisak', 'IRON_HELMET': 'vas sisak',
    'TURTLE_HELMET': 'teknőspáncél-sisak', 'LEATHER_HELMET': 'bőr kalap/sapka',
    'LEATHER_BOOTS': 'pár bőrcsizma', 'IRON_BOOTS': 'pár vasalt csizma',
    'CHAINMAIL_CHESTPLATE': 'láncszemekből font ing', 'CHAINMAIL_LEGGINGS': 'láncszem-nadrág',
    'IRON_CHESTPLATE': 'vas mellvért', 'DIAMOND_CHESTPLATE': 'kristályveretes mellvért',
    'NETHERITE_CHESTPLATE': 'sötét, súlyos mellvért', 'NETHERITE_HELMET': 'sötét, súlyos sisak',
    'NETHERITE_BOOTS': 'pár sötét, súlyos csizma', 'IRON_HORSE_ARMOR': 'lópáncél (vas)',
    'GOLDEN_HORSE_ARMOR': 'lópáncél (arany)', 'DIAMOND_HORSE_ARMOR': 'lópáncél (gyémánt)',
    'ANVIL': 'kovácsüllő', 'LANTERN': 'fém lámpás izzó ablakkal', 'SOUL_LANTERN': 'kék lángú lélek-lámpás',
    'TOTEM_OF_UNDYING': 'faragott totemfigura', 'CONDUIT': 'tengeri vezérlőmag (conduit)',
    'END_CRYSTAL': 'lebegő kristály keretben', 'GOAT_HORN': 'ívelt kürt',
    'MINECART': 'bányász-csille', 'COMPASS': 'iránytű', 'RECOVERY_COMPASS': 'sötét iránytű derengő tűvel',
    'SPYGLASS': 'kihúzható távcső', 'BRUSH': 'finomszőrű ecset', 'MAP': 'kiterített térkép',
    'BELL': 'öntött harang', 'CAKE': 'díszített torta', 'ENCHANTED_GOLDEN_APPLE': 'ragyogó aranyalma',
    'WRITTEN_BOOK': 'megírt, veretes kódex', 'HEART_OF_THE_SEA': 'sötétkék, erezett szív-drágakő',
    'EXPERIENCE_BOTTLE': 'zöld derengésű palack', 'WIND_CHARGE': 'palackozott szélörvény',
    'DRAGON_BREATH': 'lila köddel teli gömbpalack', 'STICK': 'pálca/bot', 'BONE': 'csontdarab',
    'OAK_SAPLING': 'fiatal csemete', 'ENCHANTED_BOOK': 'derengő varázskönyv',
    'ENDER_EYE': 'végzet-szem', 'FLINT': 'pattintott kovakő', 'BAMBOO': 'bambusznád',
    'RABBIT_HIDE': 'kikészített irha', 'WHITE_CANDLE': 'fehér gyertya',
    'WITHER_SKELETON_SKULL': 'megfeketedett koponya',
}


def strip_codes(s):
    return re.sub(r'&.', '', str(s)).strip()


def lore_of(lines):
    txt = ' '.join(strip_codes(l) for l in (lines or []) if l)
    return re.sub(r'\s+', ' ', txt).strip()


def main():
    entries = bp.collect()  # (material, cmd, tex)
    mats = yaml.safe_load(open(os.path.join(CFG, 'profession-materials.yml')))['profession-materials']
    recipes = yaml.safe_load(open(os.path.join(CFG, 'profession-recipes.yml')))['profession-recipes']
    loot_cfg = open(os.path.join(CFG, 'loot.yml'), encoding='utf-8').read()
    eco = yaml.safe_load(open(os.path.join(CFG, 'economy.yml')))
    relsrc = open(os.path.join(ROOT, 'src/main/java/hu/taliann/icesmp/managers/RelicManager.java')).read()
    relic_names = {}
    for rid, disp in re.findall(
            r'registerRelic\(\s*"(\w+)",\s*Material\.\w+,\s*(?://[^\r\n]*\s*)*\d{4},\s*"([^"]+)"', relsrc):
        relic_names['relic_' + rid] = disp
    catsrc = open(os.path.join(ROOT, 'src/main/java/hu/taliann/icesmp/items/CatalystItemFactory.java')).read()
    cat_names = {}
    for job, mat, name, cmd in re.findall(
            r'JobType\.(\w+), new CatalystTheme\(\s*Material\.(\w+), "(.*?)",\s*(\d{4})', catsrc, re.S):
        cat_names[int(cmd)] = (job, strip_codes(re.sub(r'<[^>]+>', '', name)))

    mat_by_cmd = {v.get('custom-model-data'): (k, v) for k, v in mats.items() if v.get('custom-model-data')}
    rec_by_cmd = {}
    for k, v in recipes.items():
        res = v.get('result') or {}
        if isinstance(res, dict) and res.get('custom-model-data'):
            rec_by_cmd[res['custom-model-data']] = (k, v)

    prof_hu = {'miner': 'Bányász', 'herbalist': 'Gyógynövényész', 'lumberjack': 'Favágó',
               'armorer': 'Kovács', 'alchemist': 'Alkimista', 'enchanter': 'Bűvölő',
               'fisherman': 'Halász', 'cook': 'Szakács'}

    out = []
    out.append('# Resource Pack — CMD-regiszter és textúra-leírások')
    out.append('')
    out.append('**A textúra-készítőnek.** Minden egyedi (custom) plugin-item CustomModelData-t (CMD)')
    out.append('visel; ez a fájl itemenként megadja a CMD-t, a fájlnevet, az alap-itemet és a')
    out.append('RÉSZLETES vizuális leírást (mit ábrázoljon, színvilág, hangulat/lore).')
    out.append('')
    out.append('## Technikai tudnivalók')
    out.append('')
    out.append('- **Méret:** 16×16 px, átlátszó háttérrel (PNG) — vanilla-konzisztens pixel-art.')
    out.append('- **Fájlnév és hely:** a kész PNG a plugin-repo `resourcepack/assets/icesmp/textures/item/<fájlnév>` útvonalára kerül — a JSON-bekötés (modellek, CMD-kapcsolók) már kész, CSAK a PNG-ket kell cserélni. A mostani textúrák generált placeholderek.')
    out.append('- **Alap-item:** a vanilla tárgy, aminek a helyén az item megjelenik, ha a CMD egyezik — a vanilla textúrája jó kiindulási referencia a sziluetthez/érzethez.')
    out.append('- **Frakció-színvilág:** RED=Perinfernicitas (láng, vörös-arany), BLUE=Cryghaliris (jég, kék-ezüst), NEUTRAL=Ryanora/Caldestera (kereskedő-arany, zöld-okker), DARK=Kitaszítottak (csont, éjfekete-lila, és a jellegzetes HIDEG TÜRKIZ derengés — mint a lich-szem: a Néma Királynő élőhalott-fénye a szemekben, rúnákban, élek mentén).')
    out.append('- Újragenerálás (leírások frissítése configból): `python3 tools/build_cmd_artdoc.py`')
    out.append('')

    bands = [
        (u'Pénz-tárgyak', lambda c: 1001 <= c <= 1999),
        (u'Relikviák', lambda c: 4000 <= c <= 4999),
        (u'Kaszt-katalizátorok', lambda c: 5201 <= c <= 5299),
        (u'Pet-befogók és ostromgép', lambda c: 5300 <= c <= 5999),
        (u'Unique szakma-anyagok', lambda c: 6000 <= c <= 6199),
        (u'Kulcsok és tervrajz', lambda c: 6200 <= c <= 6299),
        (u'Recept-tárgyak', lambda c: 6300 <= c <= 6449),
        (u'Bolt-különlegességek és nevesített loot', lambda c: 6450 <= c <= 6499),
    ]

    def describe(tex, cmd, material):
        motif, main, acc = art._lookup(tex, cmd)
        mname = getattr(motif, '__name__', '')
        shape = MOTIF_HU.get(mname, 'stilizált tárgy-ikon')
        colors = '%s; akcent: %s' % (PALETTE_HU.get(main, main), PALETTE_HU.get(acc, acc))
        # Ha a kulcsszó-szabály nem talált (generikus tabletta) VAGY az alap-item
        # önmagában beszédes tárgy, az anyagból származtatjuk az ábrázolást.
        generic = mname in ('m_rune_tablet', 'm_chalk', '')
        if material in MATERIAL_SHAPE and (generic or material in (
                'TRIDENT', 'ANVIL', 'CONDUIT', 'TOTEM_OF_UNDYING', 'END_CRYSTAL',
                'ENCHANTED_GOLDEN_APPLE', 'HEART_OF_THE_SEA', 'MINECART', 'COMPASS',
                'RECOVERY_COMPASS', 'SPYGLASS', 'BRUSH', 'GOAT_HORN', 'WRITTEN_BOOK')):
            shape = MATERIAL_SHAPE[material] + ', a névhez illő tematikus díszítéssel'
            if generic:
                colors = 'a hangulat-sorhoz/frakcióhoz illő színvilág — művészi döntés'
        elif mname == 'm_orb' and material in MATERIAL_SHAPE:
            shape = MATERIAL_SHAPE[material] + ', misztikus derengéssel'

        name, mood = None, None
        if tex in HERO:
            shape, mood = HERO[tex]
        if cmd in cat_names:
            job, disp = cat_names[cmd]
            name = disp
            shape = (MATERIAL_SHAPE.get(material, 'kaszt-ereklye ikon')
                     + ' — misztikus, kaszt-színű derengéssel (%s)' % disp)
            mood = 'A(z) %s kaszt katalizátora — a kaszt-éledés rituálé-tárgya.' % job.title().replace('_', ' ')
        if cmd in mat_by_cmd:
            mid, mv = mat_by_cmd[cmd]
            name = strip_codes(mv.get('display-name', mid))
            mood = lore_of(mv.get('lore'))
        if cmd in rec_by_cmd:
            rid, rv = rec_by_cmd[cmd]
            name = strip_codes(rv.get('display-name', rid))
            ctx = '%s-recept eredménye (%s kategória, %d. szint).' % (
                prof_hu.get(rv.get('profession', ''), rv.get('profession', '')),
                rv.get('category', '?'), rv.get('level', 1))
            mood = (lore_of(rv.get('lore')) + ' ' + ctx).strip()
        if name is None:
            name = {'coin_red': 'Parázsló Parals', 'coin_blue': 'Hópihér-veret',
                    'coin_neutral': 'Creutzér', 'coin_dark': 'Csontveret',
                    'money_pouch': 'Kopott erszény', 'blueprint': 'Recept-tervrajz',
                    'capture_beast': 'Ősi Kötés Póráza', 'capture_necro': 'Sötét Paktum-tekercs',
                    'siege_cannon': 'Ostromágyú', 'key_koznapi': 'Kereskedő Kulcs',
                    'key_ritka': 'Kincsesláda-kulcs', 'shop_6450': 'Bokic-menti Sétapálca',
                    'shop_6451': 'Hamisított Menlevél',
                    'loot_6460': 'A Hetedik Vérháború Rozsdás Pengéje',
                    'loot_6461': 'Megrontott Elit Páncél', 'loot_6462': 'Fekete Csont',
                    'loot_6463': 'A Néma Királynő Suttogása'}.get(tex)
        if name is None and tex.startswith('relic_'):
            name = relic_names.get(tex, tex[6:].replace('_', ' ').title())
        return name or tex, shape, colors, mood or ''

    for band_name, pred in bands:
        rows = sorted([e for e in entries if pred(e[1])], key=lambda e: e[1])
        if not rows:
            continue
        out.append('## %s (%d–%d)' % (band_name, rows[0][1], rows[-1][1]))
        out.append('')
        for material, cmd, tex in rows:
            name, shape, colors, mood = describe(tex, cmd, material)
            out.append('### %d — %s' % (cmd, name))
            out.append('- **Fájl:** `%s.png` &nbsp;|&nbsp; **Alap-item:** `%s`' % (tex, material))
            out.append('- **Ábrázolás:** %s' % shape)
            out.append('- **Színvilág:** %s' % colors)
            if mood:
                out.append('- **Hangulat / lore:** %s' % mood)
            out.append('')

    out.append('## Szándékosan CMD NÉLKÜL (nem kell textúra)')
    out.append('')
    out.append('- A ~215 köteg-recept (deszka, rúd, liszt, sült hús…) vanilla árucikk — stackelnie')
    out.append('  kell a gyűjtött itemekkel, recept-hozzávaló és piaci áru.')
    out.append('- A lánc-köztes egydarabosok (netherit-sor, nautilus→vezérkürt, aranyalma→Kapu')
    out.append('  Lakomája, visszhang-szilánk, főzet-alapok, kezdő horgászbot, sablon-másolat).')
    out.append('- Lerakható munkablokkok (üllő, köszörű, pulpitus, mágneskő, térképasztal, kaptár,')
    out.append('  méztömb) — lerakva úgyis a vanilla blokk-modell él.')
    out.append('- Névtábla (a nevesített névtábla a saját nevét adná a mobokra) és a napi')
    out.append('  frakció-ételek (material alapján számítanak, bármely forrásból).')
    out.append('')
    open(os.path.join(ROOT, 'docs/RESOURCE_PACK_CMD.md'), 'w').write('\n'.join(out) + '\n')
    print('entries:', len(entries), '-> docs/RESOURCE_PACK_CMD.md')


if __name__ == '__main__':
    main()
