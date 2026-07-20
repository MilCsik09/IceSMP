# -*- coding: utf-8 -*-
"""IceSMP resource pack generátor.

A docs/RESOURCE_PACK_CMD.md regiszter forrásaiból (configok + kód) legenerálja a
teljes resource packot: 16x16 pixel-art textúrák (egységes stílus: 1px sötét
kontúr, 4 tónusú árnyalás, bal-felső fényforrás), icesmp modellek, és az
assets/minecraft/items/<material>.json CMD-kapcsolók (range_dispatch a
minecraft:custom_model_data property-n — MC 1.21.4+ item-modell rendszer).

Futtatás a repo gyökeréből:  python3 tools/build_resourcepack.py
Kimenet: resourcepack/ (commitolható forrás) + IceSMP-ResourcePack.zip
A fallback minden materialnál a VALÓDI vanilla item-definíció (tools/vanilla_items/
cache, forrás: a mcmeta tükör <mcverzió>-assets tagje) — így az iránytű tű-animációja,
a szigony kézben-3D-je, az íj/pajzs állapotai és a bőr-itemek festék-színezése is
bitpontosan megmarad a nem-CMD-s példányokon. Hiányzó cache-fájlnál a beépített
közelítő fallback él (special_fallback).
"""
import json
import os
import re
import shutil
import zipfile

import yaml
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CFG = os.path.join(ROOT, 'src/main/resources/config')
OUT = os.path.join(ROOT, 'resourcepack')
S = 16

# A vanilla item-definíciók helyi cache-e (fetch: mcmeta tükör, lásd fejkomment).
VANILLA_CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'vanilla_items')
SKIP_MATERIALS = set()

# A rajz-réteg a pack_art modulban él (production pixel-art motor).
import sys as _sys
_sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pack_art import render_texture  # noqa: E402


# ------------------------------------------------------------- collect CMDs --
def collect():
    """(material, cmd, texname) hármasok a regiszter forrásaiból."""
    entries = []
    for cur, cmd in (('red', 1001), ('blue', 1002), ('neutral', 1003), ('dark', 1004)):
        entries.append(('PAPER', cmd, 'coin_' + cur))
    entries.append(('LEATHER', 1010, 'money_pouch'))
    # relikviák: a RelicManager hardcoded regisztrációiból (id, material, cmd)
    rsrc = open(os.path.join(ROOT, 'src/main/java/hu/taliann/icesmp/managers/RelicManager.java')).read()
    for rid, mat, cmd in re.findall(
            r'registerRelic\(\s*"(\w+)",\s*Material\.(\w+),\s*(?://[^\n]*\n\s*)*(\d{4}),', rsrc):
        entries.append((mat, int(cmd), 'relic_' + rid))
    entries.append(('KNOWLEDGE_BOOK', 6210, 'blueprint'))          # tervrajz
    entries.append(('LEAD', 5301, 'capture_beast'))                # Ősi Kötés Póráza
    entries.append(('GHAST_TEAR', 5302, 'capture_necro'))          # Sötét Paktum-tekercs
    entries.append(('TNT_MINECART', 5401, 'siege_cannon'))         # Ostromágyú
    src = open(os.path.join(ROOT, 'src/main/java/hu/taliann/icesmp/items/CatalystItemFactory.java')).read()
    for job, mat, _n, cmd in re.findall(r'JobType\.(\w+), new CatalystTheme\(\s*Material\.(\w+), "(.*?)",\s*(\d{4})', src, re.S):
        entries.append((mat, int(cmd), 'catalyst_' + job.lower()))
    mats = yaml.safe_load(open(os.path.join(CFG, 'profession-materials.yml')))['profession-materials']
    for mid, v in mats.items():
        cmd = v.get('custom-model-data', 0)
        if cmd:
            entries.append((v.get('material', 'PAPER'), cmd, 'u_' + mid))
    crates = yaml.safe_load(open(os.path.join(CFG, 'crates.yml')))
    for cid, cv in (crates.get('crates') or crates).items():
        if isinstance(cv, dict) and cv.get('key-custom-model-data'):
            entries.append((cv.get('key-material', 'TRIPWIRE_HOOK'), cv['key-custom-model-data'], 'key_' + cid))
    # bolt-különlegességek (faction-shops items custom-model-data) + nevesített loot-dropok
    eco = yaml.safe_load(open(os.path.join(CFG, 'economy.yml')))
    def walk_shop(node):
        if isinstance(node, dict):
            if node.get('custom-model-data') and node.get('material'):
                yield node
            for v in node.values():
                yield from walk_shop(v)
    seen_shop = set()
    for it in walk_shop(eco.get('faction-shops', {})):
        cmd = it['custom-model-data']
        if cmd not in seen_shop:
            seen_shop.add(cmd)
            entries.append((it['material'], cmd, 'shop_%d' % cmd))
    loot = yaml.safe_load(open(os.path.join(CFG, 'loot.yml')))
    def walk_loot(node):
        if isinstance(node, dict):
            if node.get('type') == 'named' and node.get('custom-model-data'):
                yield node
            for v in node.values():
                yield from walk_loot(v)
        elif isinstance(node, list):
            for v in node:
                yield from walk_loot(v)
    seen_loot = set()
    for it in walk_loot(loot):
        cmd = it['custom-model-data']
        if cmd not in seen_loot:
            seen_loot.add(cmd)
            entries.append((str(it['item']), cmd, 'loot_%d' % cmd))
    recipes = yaml.safe_load(open(os.path.join(CFG, 'profession-recipes.yml')))['profession-recipes']
    for rid, v in recipes.items():
        res = v.get('result') or {}
        if isinstance(res, dict) and res.get('custom-model-data'):
            entries.append((res.get('material', 'PAPER'), res['custom-model-data'], 'r_' + rid))
    return entries


# ------------------------------------------------------- special fallbacks ---
def vanilla_model(name):
    return {'type': 'minecraft:model', 'model': 'minecraft:item/' + name}


def vanilla_fallback(mat):
    """A cache-elt VALÓDI vanilla definíció model-ága; ha nincs, közelítő fallback."""
    path = os.path.join(VANILLA_CACHE, mat.lower() + '.json')
    if os.path.isfile(path):
        return json.load(open(path))['model']
    return special_fallback(mat)


def special_fallback(mat):
    m = mat.lower()
    if mat == 'BOW':
        return {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                'on_false': vanilla_model('bow'),
                'on_true': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:use_duration',
                            'scale': 0.05, 'fallback': vanilla_model('bow_pulling_0'),
                            'entries': [{'threshold': 0.65, 'model': vanilla_model('bow_pulling_1')},
                                        {'threshold': 0.9, 'model': vanilla_model('bow_pulling_2')}]}}
    if mat == 'CROSSBOW':
        return {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                'on_false': {'type': 'minecraft:select', 'property': 'minecraft:charge_type',
                             'fallback': vanilla_model('crossbow'),
                             'cases': [{'when': 'arrow', 'model': vanilla_model('crossbow_arrow')},
                                       {'when': 'rocket', 'model': vanilla_model('crossbow_firework')}]},
                'on_true': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:crossbow/pull',
                            'fallback': vanilla_model('crossbow_pulling_0'),
                            'entries': [{'threshold': 0.58, 'model': vanilla_model('crossbow_pulling_1')},
                                        {'threshold': 1.0, 'model': vanilla_model('crossbow_pulling_2')}]}}
    if mat == 'FISHING_ROD':
        return {'type': 'minecraft:condition', 'property': 'minecraft:fishing_rod/cast',
                'on_true': vanilla_model('fishing_rod_cast'), 'on_false': vanilla_model('fishing_rod')}
    if mat == 'SHIELD':
        return {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                'on_true': {'type': 'minecraft:special', 'model': {'type': 'minecraft:shield'},
                            'base': 'minecraft:item/shield_blocking'},
                'on_false': {'type': 'minecraft:special', 'model': {'type': 'minecraft:shield'},
                             'base': 'minecraft:item/shield'}}
    if mat == 'GOAT_HORN':
        return {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                'on_true': vanilla_model('tooting_goat_horn'), 'on_false': vanilla_model('goat_horn')}
    return vanilla_model(m)


# -------------------------------------------------------------------- main ---
def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    entries = collect()
    seen = {}
    for mat, cmd, tex in entries:
        assert cmd not in seen or seen[cmd] == tex, ('CMD ütközés', cmd, tex, seen[cmd])
        seen[cmd] = tex

    by_material = {}
    skipped = []
    for mat, cmd, tex in entries:
        tex_path = os.path.join(OUT, 'assets/icesmp/textures/item', tex + '.png')
        os.makedirs(os.path.dirname(tex_path), exist_ok=True)
        # Kézi/importált textúra (tools/textures_override) MINDIG felülüti a generáltat —
        # a textúrás kész munkáit az import_texture_sheet.py teszi ide.
        override = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'textures_override', tex + '.png')
        if os.path.isfile(override):
            shutil.copyfile(override, tex_path)
        else:
            render_texture(tex, cmd, tex_path)
        model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'icesmp:item/' + tex}}
        mp = os.path.join(OUT, 'assets/icesmp/models/item', tex + '.json')
        os.makedirs(os.path.dirname(mp), exist_ok=True)
        json.dump(model, open(mp, 'w'), indent=1)
        if mat in SKIP_MATERIALS:
            skipped.append((mat, cmd, tex))
            continue
        by_material.setdefault(mat, []).append((cmd, tex))

    for mat, lst in by_material.items():
        lst.sort()
        sel = {'model': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:custom_model_data',
                         'fallback': vanilla_fallback(mat),
                         'entries': [{'threshold': cmd,
                                      'model': {'type': 'minecraft:model', 'model': 'icesmp:item/' + tex}}
                                     for cmd, tex in lst]}}
        ip = os.path.join(OUT, 'assets/minecraft/items', mat.lower() + '.json')
        os.makedirs(os.path.dirname(ip), exist_ok=True)
        json.dump(sel, open(ip, 'w'), indent=1)

    json.dump({'pack': {'pack_format': 64,
                        'supported_formats': {'min_inclusive': 46, 'max_inclusive': 128},
                        'description': 'IceSMP — egyedi tárgy-textúrák (CMD-regiszter)'}},
              open(os.path.join(OUT, 'pack.mcmeta'), 'w'), ensure_ascii=False, indent=1)
    from pack_art import C, m_snow
    logo = C('ice', 'crystal', seed=1)
    m_snow(logo)
    logo.finish(Image).resize((64, 64), Image.NEAREST).save(os.path.join(OUT, 'pack.png'))

    zpath = os.path.join(ROOT, 'IceSMP-ResourcePack.zip')
    with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
        for dirpath, _dirs, files in os.walk(OUT):
            for f in files:
                full = os.path.join(dirpath, f)
                z.write(full, os.path.relpath(full, OUT))
    print('textures:', len(entries), '| materials:', len(by_material),
          '| skipped (vanilla marad):', skipped)
    print('zip:', zpath)


if __name__ == '__main__':
    main()
