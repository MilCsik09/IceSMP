# -*- coding: utf-8 -*-
"""IceSMP resource pack generátor.

A docs/RESOURCE_PACK_CMD.md regiszter forrásaiból (configok + kód) legenerálja a
teljes resource packot: 16x16 pixel-art textúrák (egységes stílus: 1px sötét
kontúr, 4 tónusú árnyalás, bal-felső fényforrás), icesmp modellek, és az
assets/minecraft/items/<material>.json CMD-kapcsolók (range_dispatch a
minecraft:custom_model_data property-n — MC 1.21.4+ item-modell rendszer).

Futtatás a repo gyökeréből:  python3 tools/build_resourcepack.py
Kimenet: resourcepack/ (commitolható forrás) + IceSMP-ResourcePack.zip
Kivétel: COMPASS / RECOVERY_COMPASS / TRIDENT — ezek vanilla item-definíciója
speciális (tű-animáció / dobás-állapot), ezeket a pack nem írja felül (a rajtuk
ülő CMD-s tárgyak vanilla kinézetűek maradnak, lásd a regiszter megjegyzését).
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

# Vanilla item-definíciók, amiket NEM írunk felül (speciális belső logika).
SKIP_MATERIALS = {'COMPASS', 'RECOVERY_COMPASS', 'TRIDENT'}

# ---------------------------------------------------------------- palettes ---
PALETTES = {
    'gold':    (238, 190, 66),  'silver': (206, 214, 224), 'copper': (198, 118, 74),
    'iron':    (168, 172, 180), 'ice':    (138, 200, 240), 'fire':   (238, 118, 46),
    'storm':   (86, 190, 190),  'nature': (110, 180, 84),  'poison': (94, 138, 60),
    'shadow':  (112, 82, 158),  'bone':   (226, 220, 200), 'amber':  (226, 160, 62),
    'pearl':   (232, 202, 212), 'blood':  (188, 58, 58),   'royal':  (150, 92, 200),
    'water':   (74, 132, 210),  'earth':  (146, 108, 70),  'leather': (158, 106, 62),
    'paper':   (236, 226, 198), 'crystal': (120, 216, 228), 'night':  (66, 74, 122),
    'coal':    (86, 86, 92),    'honey':  (232, 170, 48),  'wood':   (128, 92, 54),
    'salt':    (238, 238, 232), 'wine':   (142, 58, 82),
}
OUTLINE = (26, 20, 35, 255)


def shades(base):
    b = base
    dark = tuple(max(0, int(c * 0.55)) for c in b)
    mid = tuple(max(0, int(c * 0.8)) for c in b)
    hi = tuple(min(255, int(c * 1.25) + 25) for c in b)
    return {'d': dark + (255,), 'm': mid + (255,), 'b': b + (255,), 'h': hi + (255,)}


class Canvas:
    def __init__(self):
        self.img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        self.px = self.img.load()

    def put(self, x, y, c):
        if 0 <= x < S and 0 <= y < S:
            self.px[x, y] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def disc(self, cx, cy, r, c):
        for y in range(S):
            for x in range(S):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.put(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            self.put(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), c)

    def finish(self):
        """1px kontúr + bal-felső fény / jobb-alsó árny — az egységes stílus."""
        img = self.img
        pix = img.load()
        opaque = {(x, y) for y in range(S) for x in range(S) if pix[x, y][3] > 0}
        # fény/árny
        for (x, y) in opaque:
            c = pix[x, y]
            if (x - 1, y) not in opaque or (x, y - 1) not in opaque:
                pix[x, y] = tuple(min(255, int(v * 1.18) + 12) for v in c[:3]) + (255,)
            elif (x + 1, y) not in opaque or (x, y + 1) not in opaque:
                pix[x, y] = tuple(int(v * 0.72) for v in c[:3]) + (255,)
        # kontúr
        edge = set()
        for (x, y) in opaque:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and (nx, ny) not in opaque:
                    edge.add((nx, ny))
        for (x, y) in edge:
            pix[x, y] = OUTLINE
        return img


# ------------------------------------------------------------------ motifs ---
def m_pouch(c, p):
    c.rect(4, 6, 11, 13, p['b']); c.rect(5, 5, 10, 5, p['m'])
    c.rect(6, 3, 9, 4, p['d']); c.line(5, 8, 10, 8, p['m'])
    c.put(7, 2, p['h']); c.put(8, 2, p['h']); c.rect(6, 10, 9, 12, p['m'])

def m_coin(c, p):
    c.disc(8, 8, 5, p['b']); c.disc(7, 7, 2, p['h']); c.disc(9, 9, 4, p['m'])
    c.disc(8, 8, 3, p['b']); c.put(8, 8, p['h'])

def m_shard(c, p):
    c.line(8, 2, 5, 9, p['b']); c.line(8, 2, 11, 9, p['m'])
    c.rect(5, 9, 11, 10, p['b']); c.line(6, 11, 8, 13, p['m']); c.line(10, 11, 8, 13, p['d'])
    for y in range(4, 12):
        for x in range(6, 11):
            if abs(x - 8) + abs(y - 8) < 5:
                c.put(x, y, p['b'])
    c.line(8, 3, 7, 12, p['h'])

def m_crystals(c, p):
    for (bx, h) in ((4, 5), (8, 7), (12, 4)):
        for y in range(13 - h, 14):
            w = 1 if y < 15 - h else 0
            c.rect(bx - 1, y, bx + 1, y, p['m'] if bx != 8 else p['b'])
        c.put(bx, 13 - h, p['h'])
    c.rect(3, 13, 13, 14, p['d'])

def m_powder(c, p):
    c.disc(8, 11, 4, p['b']); c.rect(4, 11, 12, 13, p['b'])
    c.put(6, 8, p['m']); c.put(9, 7, p['m']); c.put(11, 9, p['h']); c.put(5, 6, p['h'])

def m_vial(c, p):
    c.rect(6, 2, 9, 3, p['d']); c.rect(7, 4, 8, 5, p['m'])
    c.rect(5, 6, 10, 12, p['b']); c.rect(6, 13, 9, 13, p['m'])
    c.rect(6, 8, 9, 12, p['m']); c.put(6, 7, p['h'])

def m_scroll(c, p):
    c.rect(4, 3, 11, 12, p['b']); c.rect(3, 3, 4, 12, p['m']); c.rect(11, 3, 12, 12, p['m'])
    for y in (5, 7, 9):
        c.line(6, y, 10, y, p['d'])

def m_book(c, p):
    c.rect(3, 3, 12, 12, p['m']); c.rect(4, 2, 12, 11, p['b'])
    c.line(4, 2, 4, 11, p['d']); c.rect(7, 4, 9, 6, p['h'])

def m_ingot(c, p):
    c.rect(4, 7, 12, 10, p['b']); c.rect(3, 8, 4, 11, p['m']); c.rect(12, 8, 13, 11, p['m'])
    c.line(4, 7, 12, 7, p['h']); c.rect(4, 11, 12, 11, p['d'])

def m_coil(c, p):
    for r in (5, 3):
        for a in range(0, 360, 8):
            import math
            x = round(8 + r * math.cos(math.radians(a)))
            y = round(8 + r * math.sin(math.radians(a)))
            c.put(x, y, p['b'] if r == 5 else p['m'])
    c.put(4, 4, p['h']); c.put(11, 11, p['d'])

def m_rope(c, p):
    c.line(4, 2, 11, 13, p['b']); c.line(5, 2, 12, 13, p['m'])
    c.line(11, 2, 4, 13, p['b']); c.line(12, 2, 5, 13, p['d'])

def m_feather(c, p):
    c.line(11, 3, 5, 12, p['d'])
    for i in range(5):
        c.line(10 - i, 4 + i * 2, 12 - i, 4 + i * 2, p['b'])
        c.line(9 - i, 5 + i * 2, 11 - i, 5 + i * 2, p['h'] if i % 2 else p['b'])

def m_mushroom(c, p):
    c.disc(8, 7, 4, p['b']); c.rect(4, 7, 12, 8, p['m'])
    c.rect(7, 9, 9, 13, p['h']); c.put(6, 5, p['h']); c.put(10, 6, p['d'])

def m_ember(c, p):
    c.disc(8, 9, 4, p['b']); c.disc(8, 10, 2, p['h'])
    c.line(8, 2, 8, 5, p['m']); c.line(5, 4, 6, 6, p['m']); c.line(11, 3, 10, 6, p['m'])

def m_snow(c, p):
    for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        c.line(8, 8, 8 + dx * 5, 8 + dy * 5, p['b'])
    c.put(8, 8, p['h']); c.disc(8, 8, 1, p['h'])

def m_drop(c, p):
    c.disc(8, 9, 3, p['b']); c.line(8, 3, 6, 8, p['m']); c.line(8, 3, 10, 8, p['b'])
    c.rect(6, 7, 10, 10, p['b']); c.put(7, 8, p['h'])

def m_key(c, p):
    c.disc(6, 5, 3, p['b']); c.disc(6, 5, 1, (0, 0, 0, 0))
    c.line(8, 7, 12, 12, p['b']); c.put(12, 11, p['m']); c.put(11, 12, p['m'])

def m_horn(c, p):
    c.line(4, 12, 11, 4, p['b']); c.line(5, 13, 12, 5, p['b']); c.line(6, 13, 12, 6, p['m'])
    c.rect(11, 3, 12, 6, p['h']); c.rect(3, 11, 5, 13, p['d'])

def m_orb(c, p):
    c.disc(8, 8, 5, p['b']); c.disc(6, 6, 2, p['h']); c.disc(10, 10, 3, p['m'])
    c.disc(8, 8, 5, p['b']) if False else None
    c.put(6, 6, p['h'])

def m_rune(c, p):
    c.rect(4, 3, 11, 13, p['m']); c.rect(5, 2, 10, 12, p['b'])
    c.line(7, 4, 7, 10, p['h']); c.line(7, 4, 9, 6, p['h']); c.line(7, 7, 9, 9, p['h'])

def m_tool(c, p):
    c.line(5, 12, 10, 5, p['d']); c.rect(8, 3, 12, 6, p['b']); c.put(12, 4, p['h'])

def m_amulet(c, p):
    c.line(4, 3, 8, 6, p['m']); c.line(12, 3, 8, 6, p['m'])
    c.disc(8, 9, 3, p['b']); c.put(8, 9, p['h']); c.disc(8, 9, 1, p['h'])

def m_seal(c, p):
    c.disc(8, 9, 4, p['b']); c.rect(5, 3, 7, 6, p['m']); c.rect(9, 3, 11, 6, p['m'])
    c.disc(8, 9, 2, p['m']); c.put(8, 9, p['h'])

def m_kit(c, p):
    c.rect(3, 6, 12, 12, p['b']); c.rect(3, 5, 12, 6, p['m']); c.rect(6, 3, 9, 5, p['d'])
    c.rect(7, 8, 8, 10, p['h']); c.line(6, 9, 9, 9, p['h'])

def m_candle(c, p):
    c.rect(6, 6, 9, 13, p['b']); c.line(8, 4, 8, 5, p['d'])
    c.disc(8, 3, 1, (255, 214, 100, 255)); c.line(6, 8, 9, 8, p['m'])

def m_quill(c, p):
    m_feather(c, p); c.rect(3, 12, 5, 13, p['d'])

def m_can(c, p):
    c.rect(5, 6, 10, 13, p['b']); c.rect(6, 4, 9, 6, p['m']); c.line(10, 5, 12, 3, p['d'])
    c.rect(6, 8, 9, 9, p['m'])

def m_hook(c, p):
    c.line(8, 2, 8, 8, p['b'])
    c.line(8, 8, 6, 11, p['b']); c.line(6, 11, 8, 12, p['m']); c.put(9, 10, p['h'])

def m_net(c, p):
    for i in range(3, 14, 3):
        c.line(i, 3, i, 13, p['m']); c.line(3, i, 13, i, p['b'])

def m_bobber(c, p):
    c.disc(8, 7, 3, (222, 60, 60, 255)); c.disc(8, 10, 3, (238, 238, 238, 255))
    c.rect(5, 8, 11, 8, p['d']); c.line(8, 2, 8, 4, p['m'])

def m_fish(c, p):
    c.disc(7, 8, 3, p['b']); c.rect(4, 7, 10, 9, p['b'])
    c.line(11, 6, 13, 8, p['m']); c.line(11, 10, 13, 8, p['m']); c.put(5, 7, p['d'])

def m_food(c, p):
    c.disc(8, 9, 4, p['b']); c.rect(4, 9, 12, 11, p['m']); c.rect(4, 8, 12, 8, p['h'])
    c.put(6, 10, p['d']); c.put(9, 10, p['d'])

def m_totem(c, p):
    c.rect(6, 4, 9, 11, p['b']); c.rect(4, 5, 11, 7, p['m'])
    c.put(6, 5, p['d']); c.put(9, 5, p['d']); c.rect(6, 12, 9, 13, p['m']); c.put(7, 3, p['h'])

def m_lantern(c, p):
    c.rect(5, 5, 10, 11, p['m']); c.rect(6, 6, 9, 10, (255, 214, 100, 255))
    c.rect(6, 3, 9, 4, p['d']); c.put(8, 2, p['d']); c.rect(5, 12, 10, 12, p['d'])

def m_gear(c, p):
    c.rect(4, 6, 11, 12, p['b']); c.rect(3, 5, 12, 7, p['m'])
    c.rect(6, 8, 9, 10, p['d']); c.put(5, 6, p['h'])

def m_saddle(c, p):
    c.disc(8, 8, 5, p['b']); c.rect(3, 8, 13, 10, p['b'])
    c.rect(3, 11, 13, 11, p['m']); c.rect(6, 4, 9, 6, p['m']); c.put(5, 8, p['h'])

def m_compassrose(c, p):
    c.disc(8, 8, 5, p['m']); c.line(8, 4, 8, 12, p['h']); c.line(4, 8, 12, 8, p['h'])
    c.put(8, 8, p['b'])

def m_cart(c, p):
    c.rect(3, 6, 12, 11, p['b']); c.rect(4, 7, 11, 10, p['d'])
    c.disc(5, 12, 1, p['m']); c.disc(10, 12, 1, p['m'])

def m_map(c, p):
    c.rect(3, 3, 12, 12, p['b']); c.line(5, 5, 8, 8, p['d']); c.line(8, 8, 10, 6, p['d'])
    c.put(10, 6, (200, 60, 60, 255)); c.rect(3, 3, 12, 3, p['m'])

def m_bell(c, p):
    c.rect(6, 4, 9, 9, p['b']); c.rect(5, 9, 10, 10, p['m'])
    c.put(8, 12, p['d']); c.rect(7, 2, 8, 3, p['d']); c.put(6, 5, p['h'])

MOTIFS = {n[2:]: f for n, f in list(globals().items()) if n.startswith('m_') and callable(f)}

# ------------------------------------------------ item -> (motif, palette) ---
KEYWORD_RULES = [
    ('erszeny|pouch', 'pouch', 'leather'),
    ('veret|parals|creutzer|coin', 'coin', 'gold'),
    ('kulcs|key', 'key', 'gold'),
    ('jegvirag|jeg|derm|fagy', 'snow', 'ice'),
    ('parazs|lang|fonix|tuz|karhozat', 'ember', 'fire'),
    ('vihar|szel', 'shard', 'storm'),
    ('borostyan|amber', 'orb', 'amber'),
    ('sarkanycsont|csont_|^csont|bone', 'shard', 'bone'),
    ('holdezust|ezust', 'coil', 'silver'),
    ('konnycsepp|esoviz|csepp', 'drop', 'water'),
    ('kristaly|kvarc|cseppko|szilank', 'shard', 'crystal'),
    ('arny|sotet|lelekhamu', 'powder', 'shadow'),
    ('gomba', 'mushroom', 'poison'),
    ('aranyfust|arany', 'ingot', 'gold'),
    ('gyongyhaz|pikkely', 'orb', 'pearl'),
    ('fuszer|vandor', 'powder', 'amber'),
    ('pecset|viasz$|viaszpecset', 'seal', 'wine'),
    ('lampaolaj|olaj', 'can', 'amber'),
    ('folyosito|lug|oldat', 'vial', 'poison'),
    ('tinta', 'vial', 'night'),
    ('toll', 'quill', 'silver'),
    ('kreta', 'chalk_rune', 'salt'),
    ('gyertya', 'candle', 'honey'),
    ('so$|koso|katalizator_so', 'powder', 'salt'),
    ('pergamen|papir|simito', 'scroll', 'paper'),
    ('horog', 'hook', 'iron'),
    ('csali|zsir', 'fish', 'copper'),
    ('halofonal|halo', 'net', 'storm'),
    ('parafa|uszo', 'bobber', 'wood'),
    ('kotel', 'rope', 'earth'),
    ('huzal', 'coil', 'silver'),
    ('enyv|gyanta|pac', 'drop', 'amber'),
    ('szegecs|kapocs|dugo', 'kit', 'iron'),
    ('robbanto', 'powder', 'blood'),
    ('iranytu|tajolo', 'compassrose', 'gold'),
    ('kenocs|paszta', 'can', 'pearl'),
    ('fiola|lombik|uveg', 'vial', 'crystal'),
    ('szuropapir', 'scroll', 'paper'),
    ('fenoko|edzoolaj', 'can', 'iron'),
    ('nyelbor|fujtato|bor$', 'gear', 'leather'),
    ('merozsinor', 'rope', 'salt'),
    ('tamasz|gerenda|fa$|farag', 'rune', 'wood'),
    ('esszencia|kivonat', 'vial', 'nature'),
    ('mag$|sarj|csemete|virag', 'mushroom', 'nature'),
    ('runapor|runa', 'rune', 'royal'),
    ('emlekszilank|emlek', 'shard', 'royal'),
    ('ereklye', 'seal', 'gold'),
    ('csend', 'orb', 'night'),
    ('meghivo|suttogas', 'scroll', 'shadow'),
    ('szorny', 'orb', 'blood'),
    ('vas', 'ingot', 'iron'),
    ('rez', 'ingot', 'copper'),
    ('lakoma|lepeny|torta|ostya|szarny|porkolt|befott|etel', 'food', 'honey'),
    ('lampas|boja', 'lantern', 'amber'),
    ('kurt', 'horn', 'storm'),
    ('konyv|kodex', 'book', 'royal'),
    ('terkep', 'map', 'paper'),
    ('harang', 'bell', 'gold'),
    ('totem', 'totem', 'gold'),
    ('csille', 'cart', 'iron'),
    ('lopancel', 'saddle', 'iron'),
    ('tavcso|kiemeles', 'tool', 'copper'),
    ('bot$|balta|fejsze|penge|kard|ij$|szigony', 'tool', 'silver'),
    ('pajzs', 'gear', 'iron'),
    ('sisak|vert|csizma|nadrag|lablemez|pancel', 'gear', 'iron'),
    ('tomus', 'book', 'crystal'),
    ('elixir', 'vial', 'royal'),
    ('amulett', 'amulet', 'pearl'),
]


def m_chalk_rune(c, p):
    c.rect(5, 4, 10, 12, p['b']); c.line(7, 6, 7, 10, p['d']); c.line(7, 6, 9, 8, p['d'])
MOTIFS['chalk_rune'] = m_chalk_rune


def pick(idname):
    for pat, motif, pal in KEYWORD_RULES:
        if re.search(pat, idname):
            return motif, pal
    h = sum(ord(ch) for ch in idname)
    return 'rune', list(PALETTES)[h % len(PALETTES)]


def render(texname, motif, palname):
    c = Canvas()
    MOTIFS[motif](c, shades(PALETTES[palname]))
    img = c.finish()
    path = os.path.join(OUT, 'assets/icesmp/textures/item', texname + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ------------------------------------------------------------- collect CMDs --
def collect():
    """(material, cmd, texname) hármasok a regiszter forrásaiból."""
    entries = []
    for cur, cmd in (('red', 1001), ('blue', 1002), ('neutral', 1003), ('dark', 1004)):
        entries.append(('PAPER', cmd, 'coin_' + cur))
    entries.append(('LEATHER', 1010, 'money_pouch'))
    entries.append(('GOLDEN_AXE', 4101, 'relic_metelytepo'))
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
    recipes = yaml.safe_load(open(os.path.join(CFG, 'profession-recipes.yml')))['profession-recipes']
    for rid, v in recipes.items():
        res = v.get('result') or {}
        if isinstance(res, dict) and res.get('custom-model-data'):
            entries.append((res.get('material', 'PAPER'), res['custom-model-data'], 'r_' + rid))
    return entries


# ------------------------------------------------------- special fallbacks ---
def vanilla_model(name):
    return {'type': 'minecraft:model', 'model': 'minecraft:item/' + name}


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
        motif, pal = pick(tex.split('_', 1)[-1] if '_' in tex else tex)
        # kézi finomítások
        if tex.startswith('coin_'):
            motif, pal = 'coin', {'coin_red': 'fire', 'coin_blue': 'ice',
                                  'coin_neutral': 'gold', 'coin_dark': 'shadow'}[tex]
        if tex == 'money_pouch':
            motif, pal = 'pouch', 'leather'
        if tex.startswith('catalyst_'):
            cat_pal = ['royal', 'blood', 'nature', 'shadow', 'poison', 'gold', 'night',
                       'crystal', 'fire', 'silver', 'wine', 'storm', 'ice']
            motif, pal = 'orb', cat_pal[(cmd - 5201) % len(cat_pal)]
        render(tex, motif, pal)
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
                         'fallback': special_fallback(mat),
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
    c = Canvas()
    m_snow(c, shades(PALETTES['ice']))
    c.finish().resize((64, 64), Image.NEAREST).save(os.path.join(OUT, 'pack.png'))

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
