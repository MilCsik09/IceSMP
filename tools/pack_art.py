# -*- coding: utf-8 -*-
"""IceSMP pixel-art motor — production minőségű 16x16 item-textúrák.

Stílus-elvek (a vanilla textúrákkal harmonizálva):
  * 6 tónusú, HUE-ELTOLÁSOS színrámpák: az árnyék hidegebb/telítettebb, a fény
    melegebb/fakóbb — nem sima sötétítés (ettől él a pixel-art).
  * Bal-felső fényforrás: él-világosítás fent/balra, él-sötétítés lent/jobbra.
  * Szelektív kontúr: a paletta legmélyebb tónusából kevert sötét körvonal.
  * Determinisztikus szemcse-zaj (az item nevéből seedelve) — két azonos
    motívumú tárgy is kicsit másképp néz ki, de minden build ugyanazt adja.
  * Anyag-jelleg: fém = kemény tónusugrás + fehér csillanás; kristály/üveg =
    világos fazetta-csík; folyadék = meniszkusz-fény; textil/bőr = varrás.
  * A hős-itemek (érmék, erszény, relikviák, katalizátorok, kulcsok, tervrajz,
    befogók, ostromágyú) egyedileg komponált rajzot kapnak.
"""
import colorsys
import random

S = 16
WHITE = (255, 255, 255, 255)


def ramp(rgb, n=6):
    h, s, v = colorsys.rgb_to_hsv(*[c / 255.0 for c in rgb])
    out = []
    for i in range(n):
        t = i / (n - 1.0)
        hh = (h - 0.055 * (1 - t) + 0.03 * t) % 1.0
        ss = max(0.0, min(1.0, s * (1.25 - 0.6 * t)))
        vv = max(0.0, min(1.0, v * (0.40 + 0.92 * t) + 0.06 * t))
        out.append(tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hh, ss, vv)) + (255,))
    return out


BASE = {
    'gold':   (233, 177, 52),  'silver': (200, 208, 218), 'copper': (196, 110, 62),
    'iron':   (156, 160, 168), 'steel':  (120, 128, 142), 'ice':    (128, 196, 240),
    'fire':   (235, 108, 38),  'storm':  (72, 182, 186),  'nature': (100, 172, 76),
    'poison': (88, 132, 54),   'shadow': (104, 74, 152),  'bone':   (222, 214, 190),
    'amber':  (222, 152, 54),  'pearl':  (228, 196, 208), 'blood':  (176, 50, 50),
    'royal':  (142, 84, 196),  'water':  (66, 124, 202),  'earth':  (138, 100, 62),
    'leather': (150, 98, 56),  'paper':  (232, 220, 188), 'crystal': (108, 208, 222),
    'night':  (58, 66, 114),   'coal':   (74, 74, 80),    'honey':  (226, 162, 42),
    'wood':   (120, 84, 48),   'salt':   (234, 234, 226), 'wine':   (134, 52, 76),
    'moss':   (92, 128, 60),   'sky':    (150, 200, 235), 'blush':  (214, 120, 130),
    'lich':   (72, 216, 214),
}
RAMPS = {k: ramp(v) for k, v in BASE.items()}


def _blend(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


class C:
    """Index-rácsos vászon: előbb tónus-indexekkel rajzolunk (A=fő, B=akcent
    rámpa), a finish() végzi a fény/árny/kontúr/zaj feloldást."""

    def __init__(self, main, accent=None, seed=0):
        self.g = {}
        self.direct = {}
        self.A = RAMPS[main] if isinstance(main, str) else main
        self.B = RAMPS[accent] if isinstance(accent, str) else (accent or self.A)
        self.rnd = random.Random(seed)

    # --- rajz-primitívek (i: 0..5 tónus, layer: 'A'/'B') ---
    def p(self, x, y, i, l='A'):
        if 0 <= x < S and 0 <= y < S:
            self.g[(x, y)] = (l, max(0, min(5, i)))

    def px(self, x, y, rgba):
        if 0 <= x < S and 0 <= y < S:
            self.direct[(x, y)] = rgba
            self.g[(x, y)] = ('D', 0)

    def rect(self, x0, y0, x1, y1, i, l='A'):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.p(x, y, i, l)

    def vgrad(self, x0, y0, x1, y1, i0, i1, l='A'):
        for y in range(y0, y1 + 1):
            t = (y - y0) / max(1, (y1 - y0))
            for x in range(x0, x1 + 1):
                self.p(x, y, round(i0 + (i1 - i0) * t), l)

    def disc(self, cx, cy, r, i, l='A'):
        for y in range(S):
            for x in range(S):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.4:
                    self.p(x, y, i, l)

    def ball(self, cx, cy, r, l='A'):
        """Gömb-árnyalás: bal-felső fény, jobb-alsó mély tónus."""
        for y in range(S):
            for x in range(S):
                d2 = (x - cx) ** 2 + (y - cy) ** 2
                if d2 <= r * r + 0.4:
                    lx, ly = x - (cx - r * 0.45), y - (cy - r * 0.45)
                    t = min(1.0, (lx * lx + ly * ly) / (2.2 * r * r))
                    self.p(x, y, round(4.6 - 3.4 * t), l)

    def line(self, x0, y0, x1, y1, i, l='A'):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for k in range(n + 1):
            self.p(round(x0 + (x1 - x0) * k / n), round(y0 + (y1 - y0) * k / n), i, l)

    def spark(self, x, y):
        self.px(x, y, WHITE)

    def noise(self, amount=0.12, lo=-1, hi=1):
        for (x, y), (l, i) in list(self.g.items()):
            if l == 'D':
                continue
            interior = all((x + dx, y + dy) in self.g for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if interior and self.rnd.random() < amount:
                self.p(x, y, i + self.rnd.choice((lo, hi)), l)

    def finish(self, img_cls, outline=True):
        from PIL import Image
        img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        pix = img.load()
        cells = self.g
        for (x, y), (l, i) in cells.items():
            if l == 'D':
                pix[x, y] = self.direct[(x, y)]
                continue
            rr = self.A if l == 'A' else self.B
            j = i
            if (x - 1, y) not in cells or (x, y - 1) not in cells:
                j = min(5, i + 1)
            elif (x + 1, y) not in cells or (x, y + 1) not in cells:
                j = max(0, i - 1)
            pix[x, y] = rr[j]
        if outline:
            edge = {}
            for (x, y) in cells:
                l, _ = cells[(x, y)]
                rr = self.A if l != 'B' else self.B
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < S and 0 <= ny < S and (nx, ny) not in cells:
                        edge[(nx, ny)] = _blend(rr[0][:3], (10, 8, 16), 0.62)
            for (x, y), c in edge.items():
                pix[x, y] = c
        return img


# ================================================================== motifs ===
def m_shard(c):
    c.line(8, 1, 4, 8, 2); c.line(8, 1, 11, 7, 3)
    for y in range(2, 13):
        for x in range(3, 13):
            if abs(x - 7.5) * 2.1 + abs(y - 7) < 7.6:
                c.p(x, y, 3)
    c.line(4, 8, 7, 14, 2); c.line(11, 7, 8, 14, 1)
    c.line(7, 2, 6, 12, 4); c.line(8, 3, 8, 13, 2)
    c.p(6, 4, 5); c.spark(6, 3)

def m_crystals(c):
    for bx, top, w in ((4, 6, 1), (8, 3, 1), (12, 7, 1)):
        for y in range(top, 14):
            c.rect(bx - w, y, bx + w, y, 3 if bx == 8 else 2)
        c.line(bx, top, bx, 13, 4)
        c.p(bx - 1, top + 1, 1); c.spark(bx, top)
    c.rect(2, 13, 13, 14, 1); c.rect(3, 13, 12, 13, 2, 'B')

def m_powder(c):
    for y in range(9, 14):
        half = (y - 5) // 1
        c.rect(8 - half + 1, y, 8 + half - 1, y, 3)
    c.rect(4, 13, 12, 14, 2)
    c.p(5, 11, 2); c.p(10, 12, 2); c.p(7, 9, 4); c.p(9, 10, 4)
    c.p(6, 7, 4); c.p(10, 8, 4); c.spark(8, 6)

def m_vial(c):
    c.rect(6, 1, 9, 2, 1, 'B'); c.rect(7, 3, 8, 4, 2)
    c.vgrad(5, 5, 10, 13, 4, 2)
    c.rect(5, 8, 10, 13, 3, 'B'); c.rect(6, 9, 9, 12, 4, 'B')
    c.line(5, 8, 10, 8, 5, 'B')
    c.line(6, 5, 6, 12, 5); c.spark(6, 6)

def m_bottle(c):
    c.rect(7, 1, 8, 3, 1, 'B'); c.ball(8, 9, 4.4)
    c.disc(8, 10, 3, 3, 'B'); c.p(6, 8, 5); c.spark(6, 7)

def m_scroll(c):
    c.vgrad(4, 2, 11, 13, 4, 3)
    c.rect(3, 2, 3, 13, 2); c.rect(12, 2, 12, 13, 2)
    c.rect(3, 2, 12, 2, 5); c.rect(3, 13, 12, 13, 1)
    for y in (5, 8, 11):
        c.line(5, y, 10, y, 1, 'B')
    c.p(10, 5, 2, 'B')

def m_book(c):
    c.rect(3, 3, 12, 13, 1); c.vgrad(3, 2, 12, 12, 4, 3)
    c.rect(3, 2, 3, 12, 2); c.line(4, 3, 4, 11, 5)
    c.rect(6, 5, 9, 8, 3, 'B'); c.p(7, 6, 5, 'B'); c.spark(7, 5)

def m_tome(c):
    m_book(c)
    c.disc(8, 7, 1, 4, 'B'); c.spark(8, 6)

def m_ingot(c):
    c.vgrad(4, 6, 12, 10, 4, 2)
    c.rect(3, 7, 4, 11, 2); c.rect(12, 7, 13, 11, 1)
    c.rect(3, 11, 13, 11, 1); c.line(4, 6, 12, 6, 5)
    c.line(5, 7, 7, 7, 5); c.spark(6, 7)

def m_plate(c):
    c.vgrad(3, 4, 12, 12, 4, 2)
    c.line(3, 4, 12, 4, 5); c.line(3, 4, 3, 12, 4)
    c.p(5, 6, 5); c.p(10, 10, 1); c.spark(4, 5)

def m_coil(c):
    import math
    for r, i in ((5.2, 3), (3.4, 4), (1.6, 2)):
        for a in range(0, 360, 6):
            x = 8 + r * math.cos(math.radians(a)); y = 8 + r * math.sin(math.radians(a))
            c.p(round(x), round(y), i)
    c.p(4, 4, 5); c.spark(5, 4); c.line(12, 12, 14, 14, 2)

def m_rope(c):
    for k in range(6):
        y = 2 + k * 2
        c.line(5, y, 8, y + 1, 3); c.line(8, y + 1, 11, y, 2)
    c.rect(5, 2, 5, 13, 2); c.rect(11, 2, 11, 13, 1)
    c.p(6, 3, 4); c.p(7, 7, 4)

def m_feather(c):
    c.line(12, 2, 4, 13, 1)
    for k in range(6):
        x, y = 11 - k, 3 + k * 1.7
        c.line(round(x - 2), round(y + 1), round(x), round(y), 3)
        c.line(round(x - 1), round(y + 1.7), round(x + 1), round(y + 0.7), 4)
    c.spark(11, 2); c.p(4, 13, 0)

def m_mushroom(c):
    c.ball(8, 6, 4.3)
    c.rect(4, 7, 12, 7, 1)
    c.vgrad(6, 8, 9, 13, 4, 2, 'B')
    c.p(6, 4, 5); c.p(10, 5, 2); c.spark(6, 3)

def m_ember(c):
    c.line(8, 1, 6, 5, 2); c.line(11, 2, 10, 5, 2); c.line(4, 3, 6, 6, 1)
    c.ball(8, 9, 4.2)
    c.disc(8, 10, 2, 5, 'B'); c.spark(8, 10); c.spark(7, 11)

def m_snow(c):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        c.line(8, 8, 8 + dx * 6, 8 + dy * 6, 3)
        c.p(8 + dx * 4 + dy, 8 + dy * 4 + dx, 4); c.p(8 + dx * 4 - dy, 8 + dy * 4 - dx, 4)
    for dx, dy in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
        c.line(8, 8, 8 + dx * 4, 8 + dy * 4, 2)
    c.disc(8, 8, 1, 5); c.spark(8, 8)

def m_drop(c):
    c.p(8, 2, 3)
    for y in range(3, 12):
        w = min(3, (y - 1) // 2)
        c.rect(8 - w, y, 8 + w, y, 3)
    c.disc(8, 10, 3, 3)
    c.line(6, 7, 6, 11, 5); c.spark(6, 8); c.p(10, 11, 1)

def m_key(c):
    c.disc(6, 5, 3, 3); c.disc(6, 5, 1.2, 0)
    for (x, y) in ((6, 2), (3, 5), (6, 8), (9, 5)):
        c.p(x, y, 4)
    c.line(9, 8, 13, 12, 3); c.line(10, 8, 14, 12, 2)
    c.p(12, 13, 3); c.p(13, 13, 3); c.spark(5, 3)

def m_horn(c):
    for k in range(10):
        t = k / 9.0
        x = 4 + 8 * t; y = 12 - 8 * t * t
        r = 1 + (1 - t) * 1.2
        c.disc(round(x), round(y), r, 3 if k % 2 else 2)
    c.rect(11, 2, 12, 4, 4, 'B'); c.spark(12, 2); c.p(3, 12, 1)

def m_orb(c):
    c.ball(8, 8, 5)
    c.disc(8, 8, 2.2, 4, 'B'); c.p(8, 8, 5, 'B'); c.spark(6, 6)

def m_rune_tablet(c):
    c.vgrad(4, 2, 11, 13, 3, 2)
    c.rect(4, 2, 11, 2, 4); c.rect(4, 13, 11, 13, 1)
    c.line(7, 4, 7, 11, 4, 'B'); c.line(7, 4, 9, 6, 4, 'B'); c.line(7, 8, 9, 10, 4, 'B')
    c.spark(7, 4)

def m_kit(c):
    c.vgrad(3, 6, 12, 12, 3, 2)
    c.rect(3, 5, 12, 6, 4); c.rect(6, 3, 9, 5, 1)
    c.rect(7, 8, 8, 11, 4, 'B'); c.line(5, 9, 10, 9, 4, 'B')
    c.spark(4, 6)

def m_candle(c):
    c.vgrad(6, 6, 9, 13, 4, 2)
    c.line(8, 4, 8, 5, 0)
    c.px(8, 3, (255, 220, 120, 255)); c.px(8, 2, (255, 170, 60, 255))
    c.p(6, 7, 5); c.line(6, 9, 9, 9, 3); c.rect(5, 13, 10, 14, 1, 'B')

def m_oilcan(c):
    c.vgrad(5, 6, 10, 13, 3, 1)
    c.rect(6, 4, 9, 5, 2); c.line(10, 4, 13, 2, 2)
    c.rect(6, 8, 9, 9, 4, 'B'); c.p(6, 7, 5); c.spark(6, 6)

def m_hook(c):
    c.line(9, 1, 9, 8, 3)
    c.line(9, 8, 7, 12, 3); c.line(7, 12, 5, 11, 2); c.p(5, 10, 4)
    c.p(10, 2, 4); c.spark(9, 1); c.p(4, 10, 2)

def m_net(c):
    for i in range(2, 15, 4):
        c.line(i, 2, i, 13, 2); c.line(2, i, 13, i, 3)
    for x in range(2, 15, 4):
        for y in range(2, 15, 4):
            c.p(x, y, 4)
    c.spark(2, 2)

def m_bobber(c):
    c.line(8, 1, 8, 3, 1)
    c.ball(8, 8, 4)
    for y in range(9, 13):
        for x in range(4, 13):
            if (x - 8) ** 2 + (y - 8) ** 2 <= 17:
                c.p(x, y, 4, 'B')
    c.rect(4, 8, 12, 8, 0); c.spark(6, 6)

def m_fish(c):
    c.disc(7, 8, 3.4, 3)
    c.rect(3, 7, 10, 9, 3)
    c.line(11, 5, 13, 8, 2); c.line(11, 11, 13, 8, 2); c.p(12, 8, 2)
    c.p(4, 7, 4, 'B'); c.p(5, 8, 0); c.line(6, 5, 8, 5, 4); c.spark(6, 6)

def m_stew(c):
    c.disc(8, 9, 5, 2)
    c.rect(3, 9, 13, 12, 2); c.rect(4, 13, 12, 13, 1)
    c.disc(8, 9, 4, 4, 'B'); c.rect(4, 9, 12, 9, 5, 'B')
    c.p(6, 8, 3, 'B'); c.p(9, 9, 2, 'B'); c.spark(5, 8)

def m_bread(c):
    for k in range(5):
        c.disc(5 + k * 1.5, 8 + k * 0.6, 2.6, 3)
    c.line(4, 6, 11, 10, 5)
    c.p(5, 7, 1); c.p(7, 8, 1); c.p(9, 9, 1); c.spark(4, 6)

def m_pie(c):
    c.disc(8, 9, 5.2, 3)
    c.rect(3, 9, 13, 11, 3); c.rect(4, 12, 12, 12, 1)
    c.disc(8, 8, 3.6, 4, 'B')
    c.p(6, 7, 2, 'B'); c.p(9, 8, 2, 'B'); c.p(8, 6, 5); c.spark(5, 7)

def m_meat(c):
    c.disc(7, 8, 4, 3)
    c.disc(6, 7, 1.6, 5, 'B')
    c.line(10, 5, 13, 2, 4, 'B'); c.disc(13, 2, 1.2, 5, 'B')
    c.p(8, 10, 1); c.spark(5, 6)

def m_cake(c):
    c.vgrad(3, 7, 12, 12, 4, 3)
    c.rect(3, 6, 12, 7, 5)
    for x in (4, 7, 10):
        c.px(x, 5, (222, 60, 60, 255))
    c.rect(3, 12, 12, 13, 1); c.line(5, 9, 5, 11, 2, 'B'); c.line(9, 9, 9, 11, 2, 'B')

def m_lantern(c):
    c.rect(6, 2, 9, 3, 1); c.p(8, 1, 2)
    c.vgrad(5, 4, 10, 11, 3, 1)
    c.rect(6, 5, 9, 10, 4, 'B'); c.rect(7, 6, 8, 9, 5, 'B')
    c.rect(5, 12, 10, 12, 1); c.spark(7, 6)

def m_bell(c):
    c.rect(7, 1, 8, 2, 1, 'B')
    c.vgrad(6, 3, 9, 8, 5, 3)
    c.rect(5, 9, 10, 10, 2); c.line(5, 9, 10, 9, 4)
    c.p(8, 12, 1, 'B'); c.spark(6, 4)

def m_map(c):
    c.vgrad(3, 2, 12, 13, 4, 3)
    c.rect(3, 2, 3, 13, 2); c.rect(12, 2, 12, 13, 2)
    c.line(5, 5, 7, 8, 1, 'B'); c.line(7, 8, 10, 6, 1, 'B'); c.line(6, 10, 9, 11, 1, 'B')
    c.px(10, 6, (206, 60, 52, 255)); c.p(5, 4, 5)

def m_cart(c):
    c.vgrad(3, 5, 12, 10, 3, 2)
    c.rect(4, 6, 11, 9, 1)
    c.rect(4, 6, 11, 7, 2, 'B')
    c.disc(5, 12, 1.4, 1); c.disc(10, 12, 1.4, 1); c.p(5, 12, 3); c.p(10, 12, 3)
    c.line(3, 5, 12, 5, 4); c.spark(3, 5)

def m_compass_rose(c):
    c.ball(8, 8, 5.4)
    c.disc(8, 8, 4, 2, 'B')
    c.line(8, 4, 8, 12, 4, 'B'); c.line(4, 8, 12, 8, 4, 'B')
    c.px(8, 5, (222, 60, 52, 255)); c.spark(6, 6)

def m_armor(c):
    c.vgrad(4, 3, 11, 11, 4, 2)
    c.rect(4, 3, 5, 6, 3); c.rect(10, 3, 11, 6, 3)
    c.rect(6, 2, 9, 3, 1)
    c.rect(7, 5, 8, 9, 3, 'B'); c.p(7, 5, 5, 'B')
    c.rect(4, 12, 11, 12, 1); c.spark(5, 4)

def m_helmet(c):
    c.ball(8, 8, 5)
    c.rect(3, 9, 12, 10, 2)
    c.rect(5, 9, 10, 11, 0)
    c.rect(6, 5, 9, 5, 5, 'B'); c.spark(5, 5)

def m_boots(c):
    for x0 in (3, 9):
        c.vgrad(x0, 5, x0 + 3, 10, 4, 2)
        c.rect(x0, 11, x0 + 4, 12, 1)
        c.rect(x0, 5, x0 + 3, 5, 5, 'B')
    c.spark(3, 5)

def m_sword(c):
    c.line(11, 2, 5, 8, 4); c.line(12, 3, 6, 9, 3); c.line(11, 2, 12, 3, 5)
    c.line(4, 9, 7, 12, 1, 'B'); c.line(3, 10, 5, 8, 2, 'B')
    c.p(4, 11, 1, 'B'); c.p(3, 12, 2, 'B'); c.spark(11, 2)

def m_axe(c):
    c.line(4, 12, 11, 4, 1, 'B'); c.line(5, 13, 12, 5, 0, 'B')
    c.disc(11, 4, 2.6, 3); c.rect(8, 2, 11, 6, 3)
    c.line(8, 2, 11, 2, 5); c.p(8, 5, 1); c.spark(9, 2)

def m_pick(c):
    c.line(5, 13, 11, 6, 1, 'B'); c.line(6, 13, 12, 7, 0, 'B')
    c.line(4, 6, 13, 5, 3); c.line(5, 4, 12, 4, 4)
    c.p(4, 7, 1); c.p(13, 6, 1); c.spark(6, 4)

def m_bow(c):
    import math
    for a in range(-58, 59, 3):
        x = 7 + 6.0 * math.cos(math.radians(a)); y = 8 + 6.0 * math.sin(math.radians(a))
        c.p(round(x), round(y), 3)
        c.p(round(x) - 1, round(y), 2)
        if abs(a) < 40:
            c.p(round(x) - 2, round(y), 1)
    c.rect(11, 7, 12, 9, 4)  # markolat-tekercs
    c.line(12, 2, 12, 14, 1, 'B')
    c.p(12, 2, 2, 'B'); c.p(12, 14, 2, 'B'); c.spark(12, 3)

def m_crossbow(c):
    c.line(3, 12, 12, 3, 2)
    c.line(4, 4, 12, 12, 1, 'B')
    c.line(7, 3, 3, 7, 3); c.line(8, 4, 4, 8, 2)
    c.p(12, 3, 4); c.spark(11, 3)

def m_shield(c):
    for y in range(2, 14):
        w = 5 - max(0, (y - 8)) * 1 - max(0, (2 - y))
        w = max(1, min(5, round(w)))
        c.rect(8 - w, y, 8 + w, y, 3)
    c.rect(3, 2, 13, 3, 4)
    c.rect(7, 2, 8, 13, 2, 'B'); c.line(4, 7, 12, 7, 2, 'B')
    c.p(4, 3, 5); c.spark(4, 2)

def m_saddle(c):
    c.disc(8, 7, 4.6, 3)
    c.rect(3, 7, 13, 9, 3); c.rect(3, 10, 13, 10, 1)
    c.rect(6, 3, 9, 5, 2, 'B'); c.line(3, 7, 13, 7, 5)
    c.p(4, 8, 1); c.spark(5, 6)

def m_totem(c):
    c.vgrad(6, 3, 9, 11, 4, 3)
    c.rect(4, 4, 11, 6, 4)
    c.p(5, 5, 0); c.p(10, 5, 0)
    c.rect(6, 12, 9, 13, 2)
    c.disc(8, 8, 1, 5, 'B'); c.spark(7, 3); c.spark(8, 8)

def m_salt(c):
    for y in range(8, 14):
        w = (y - 5)
        c.rect(8 - w // 2, y, 8 + w // 2, y, 4)
    c.rect(4, 13, 12, 14, 3)
    c.p(6, 10, 5); c.p(9, 11, 5); c.p(8, 8, 5); c.p(7, 12, 2); c.spark(8, 7)

def m_log(c):
    c.vgrad(3, 5, 12, 12, 3, 1)
    for y in (6, 8, 10):
        c.line(3, y, 12, y, 2)
    c.disc(13, 8, 2, 4, 'B'); c.disc(13, 8, 1, 2, 'B')
    c.line(3, 5, 12, 5, 4); c.spark(4, 5)

def m_sapling(c):
    c.line(8, 8, 8, 13, 1, 'B')
    c.disc(8, 6, 3.4, 3); c.disc(6, 5, 1.6, 4); c.disc(10, 7, 1.6, 2)
    c.rect(5, 13, 11, 14, 0, 'B'); c.spark(6, 4)

def m_flower(c):
    c.line(8, 8, 8, 13, 2, 'B'); c.line(8, 10, 6, 11, 3, 'B')
    for dx, dy in ((0, -2), (2, 0), (-2, 0), (0, 2), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        c.disc(8 + dx, 5 + dy, 1.2, 3)
    c.disc(8, 5, 1.2, 5); c.spark(8, 5)

def m_chalk(c):
    c.vgrad(6, 3, 9, 12, 5, 3)
    c.rect(6, 12, 9, 13, 2)
    c.p(6, 4, 5); c.line(6, 6, 9, 6, 4)

def m_brush(c):
    c.line(9, 2, 12, 5, 2, 'B'); c.line(10, 2, 13, 5, 1, 'B')
    c.vgrad(5, 7, 9, 11, 4, 2)
    for x in range(4, 10):
        c.line(x, 11, x - 1, 14, 3)
    c.spark(10, 2)

def m_spyglass(c):
    c.line(3, 12, 10, 5, 1, 'B'); c.line(4, 13, 11, 6, 2, 'B')
    c.disc(11, 4, 2.4, 3); c.disc(11, 4, 1.2, 5)
    c.rect(6, 8, 8, 10, 3); c.spark(10, 3)

def m_amulet(c):
    c.line(4, 2, 8, 5, 1, 'B'); c.line(12, 2, 8, 5, 1, 'B')
    c.p(4, 2, 2, 'B'); c.p(12, 2, 2, 'B')
    c.ball(8, 9, 3.4)
    c.disc(8, 9, 1.4, 5); c.spark(7, 8)

def m_seal(c):
    c.disc(8, 9, 4.4, 3)
    c.rect(4, 2, 6, 6, 2, 'B'); c.rect(9, 2, 11, 6, 2, 'B')
    c.disc(8, 9, 2.6, 2); c.disc(8, 9, 1.2, 4); c.spark(6, 7)

def m_torchset(c):
    for x0, y0 in ((4, 3), (9, 2)):
        c.rect(x0, y0 + 3, x0 + 1, y0 + 11, 1, 'B')
        c.rect(x0, y0, x0 + 1, y0 + 2, 3)
        c.px(x0, y0 - 1, (255, 214, 100, 255)); c.px(x0 + 1, y0, (255, 170, 60, 255))
    c.rect(3, 13, 12, 14, 0, 'B')

def m_quill(c):
    m_feather(c)
    c.rect(3, 13, 5, 14, 1, 'B')
    c.px(3, 14, (30, 26, 40, 255))

def m_glowink(c):
    c.ball(8, 9, 4)
    c.disc(8, 9, 2.4, 5, 'B')
    c.rect(6, 3, 9, 5, 2); c.spark(7, 9); c.spark(9, 8)


# ============================================================ hero textures ==
def h_coin(c, sym):
    c.ball(8, 8, 6)
    for a in range(16):
        import math
        x = 8 + 5.6 * math.cos(a * 0.3927); y = 8 + 5.6 * math.sin(a * 0.3927)
        c.p(round(x), round(y), 2 if a % 2 else 4)
    c.disc(8, 8, 4, 3)
    if sym == 'flame':
        c.line(8, 5, 7, 8, 5, 'B'); c.line(8, 5, 9, 9, 4, 'B'); c.disc(8, 9, 1.4, 5, 'B')
    elif sym == 'snow':
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            c.line(8, 8, 8 + dx * 2, 8 + dy * 2, 5, 'B')
    elif sym == 'scale':
        c.line(8, 5, 8, 10, 5, 'B'); c.line(5, 6, 11, 6, 5, 'B')
        c.p(5, 7, 4, 'B'); c.p(11, 7, 4, 'B'); c.line(7, 11, 9, 11, 4, 'B')
    elif sym == 'skull':
        c.disc(8, 7, 2, 5, 'B'); c.p(7, 7, 0, 'B'); c.p(9, 7, 0, 'B')
        c.rect(7, 9, 9, 10, 4, 'B'); c.p(8, 10, 0, 'B')
    c.spark(5, 5)

def h_pouch(c):
    for y in range(6, 14):
        w = 4 + min(2, y - 6) - max(0, y - 11)
        c.rect(8 - w, y, 8 + w, y, 3)
    c.rect(6, 4, 10, 5, 2)
    c.rect(5, 5, 11, 5, 1)
    c.line(4, 5, 6, 3, 1, 'B'); c.line(12, 5, 10, 3, 1, 'B')
    c.px(7, 3, (240, 198, 80, 255)); c.px(9, 3, (240, 198, 80, 255)); c.px(8, 2, (255, 226, 130, 255))
    c.line(5, 9, 6, 12, 2); c.line(11, 9, 10, 12, 1)
    c.p(6, 7, 4); c.spark(6, 6)

def h_relic_axe(c):
    c.line(4, 13, 10, 5, 1, 'B'); c.line(5, 14, 11, 6, 0, 'B')
    c.disc(10, 5, 3, 4); c.rect(7, 2, 11, 7, 3)
    c.line(7, 2, 11, 2, 5); c.line(7, 3, 7, 6, 4)
    c.px(12, 3, (196, 120, 255, 255)); c.px(6, 7, (150, 84, 196, 255))
    c.spark(8, 2)

def h_wing(c):
    for k in range(5):
        x0 = 3 + k * 2
        top = 9 - k * 1.6
        c.line(x0, 13 - k, x0 + 1, round(top), 3)
        c.line(x0 + 1, 13 - k, x0 + 2, round(top) + 1, 4)
    c.line(3, 13, 12, 4, 2)
    c.disc(12, 3, 1.4, 5, 'B'); c.spark(12, 3)

def h_tear(c):
    m_drop(c)
    c.disc(8, 10, 2, 1)
    c.line(7, 8, 7, 11, 3, 'B'); c.spark(7, 8)

def h_key(c, fancy):
    c.disc(6, 5, 3.2, 3); c.disc(6, 5, 1.4, 0)
    for (x, y) in ((6, 2), (3, 5), (6, 8), (9, 5)):
        c.p(x, y, 4)
    if fancy:
        c.p(6, 1, 5, 'B'); c.p(2, 5, 4, 'B'); c.p(6, 9, 4, 'B')
    c.line(9, 8, 13, 12, 3); c.line(10, 7, 14, 11, 2)
    c.rect(12, 13, 13, 13, 3); c.p(14, 12, 3)
    c.spark(5, 3)

def h_blueprint(c):
    c.vgrad(3, 3, 12, 12, 3, 2)
    c.rect(3, 3, 12, 3, 4); c.rect(3, 12, 12, 12, 1)
    c.rect(13, 4, 13, 11, 1)
    c.line(5, 5, 10, 5, 5, 'B'); c.line(5, 7, 8, 7, 5, 'B')
    c.rect(5, 9, 7, 10, 4, 'B'); c.p(9, 9, 5, 'B'); c.p(10, 10, 5, 'B')
    c.spark(5, 5)

def h_capture_beast(c):
    import math
    for a in range(0, 360, 5):
        x = 8 + 4.6 * math.cos(math.radians(a)); y = 7 + 4.6 * math.sin(math.radians(a))
        c.p(round(x), round(y), 3)
    for a in range(0, 360, 24):
        x = 8 + 4.6 * math.cos(math.radians(a)); y = 7 + 4.6 * math.sin(math.radians(a))
        c.p(round(x), round(y), 4)
    c.line(11, 10, 13, 14, 2); c.p(12, 12, 1)
    c.disc(8, 7, 1.4, 2, 'B'); c.spark(5, 4)

def h_capture_necro(c):
    m_scroll(c)
    c.disc(8, 8, 2, 1, 'B'); c.p(7, 7, 5, 'B'); c.p(9, 7, 5, 'B'); c.rect(7, 9, 9, 9, 4, 'B')
    c.px(4, 3, (196, 120, 255, 255))

def h_siege(c):
    c.vgrad(3, 9, 12, 12, 2, 1)
    c.disc(5, 13, 1.5, 1); c.disc(10, 13, 1.5, 1)
    c.line(4, 9, 12, 5, 3, 'B'); c.line(4, 10, 12, 6, 2, 'B'); c.line(4, 11, 12, 7, 1, 'B')
    c.disc(13, 5, 1.2, 0, 'B')
    c.px(14, 3, (255, 170, 60, 255)); c.px(15, 2, (255, 220, 120, 255))
    c.spark(5, 9)


# ======================================================= mapping & renderer ==
# Kézi hozzárendelések: (motívum, fő-rámpa, akcent-rámpa)
OVERRIDES = {
    'money_pouch': (h_pouch, 'leather', 'earth'),
    'coin_red': (lambda c: h_coin(c, 'flame'), 'gold', 'fire'),
    'coin_blue': (lambda c: h_coin(c, 'snow'), 'silver', 'ice'),
    'coin_neutral': (lambda c: h_coin(c, 'scale'), 'gold', 'amber'),
    'coin_dark': (lambda c: h_coin(c, 'skull'), 'steel', 'lich'),
    'relic_metelytepo': (h_relic_axe, 'gold', 'wood'),
    'relic_phoenix_wing': (h_wing, 'fire', 'gold'),
    'relic_frost_wing': (h_wing, 'ice', 'crystal'),
    'relic_wander_wind': (h_wing, 'sky', 'salt'),
    'relic_bone_wing': (h_wing, 'bone', 'lich'),
    'relic_eleftheria_konnye': (h_tear, 'night', 'lich'),
    'key_koznapi': (lambda c: h_key(c, False), 'iron', 'silver'),
    'key_ritka': (lambda c: h_key(c, True), 'gold', 'royal'),
    'blueprint': (h_blueprint, 'water', 'salt'),
    'capture_beast': (h_capture_beast, 'leather', 'nature'),
    'capture_necro': (h_capture_necro, 'shadow', 'lich'),
    'siege_cannon': (h_siege, 'coal', 'steel'),
    'shop_6450': (lambda c: (m_chalk(c),), 'wood', 'gold'),        # Sétapálca
    'shop_6451': (m_scroll, 'paper', 'wine'),                      # Menlevél
    'loot_6460': (m_sword, 'steel', 'earth'),                      # Rozsdás Penge
    'loot_6461': (m_armor, 'steel', 'lich'),                       # Megrontott Páncél
    'loot_6462': (m_chalk, 'coal', 'lich'),                        # Fekete Csont
    'loot_6463': (m_sword, 'night', 'lich'),                       # Néma Királynő Suttogása
}

CATALYST_STYLE = {  # CMD 5201.. sorrendben
    5201: ('royal', 'crystal'), 5202: ('blood', 'gold'), 5203: ('nature', 'amber'),
    5204: ('shadow', 'steel'), 5205: ('moss', 'nature'), 5206: ('gold', 'sky'),
    5207: ('night', 'shadow'), 5208: ('crystal', 'ice'), 5209: ('fire', 'amber'),
    5210: ('silver', 'sky'), 5211: ('wine', 'shadow'), 5212: ('steel', 'night'),
    5213: ('storm', 'crystal'),
}

# Kulcsszó → (motívum, fő, akcent). Sorrend számít: az első találat nyer.
RULES = [
    ('lakoma|szarny$|oldalas|hering|csirke|hus|beef', m_meat, 'blood', 'earth'),
    ('porkolt|leves|ragu|stew', m_stew, 'wood', 'amber'),
    ('cipo|kenyer|lepeny', m_bread, 'amber', 'honey'),
    ('torta|sutemeny', m_cake, 'pearl', 'blush'),
    ('pite|befott|ostya|keksz|mezeskalacs|figurak', m_pie, 'amber', 'honey'),
    ('jegvirag|fagy|jeg|zuzmara|derm', m_snow, 'ice', 'crystal'),
    ('parazs|lang|fonix|tuz|karhozat|magma', m_ember, 'fire', 'gold'),
    ('vihar|szel|orkan', m_shard, 'storm', 'crystal'),
    ('borostyan', m_orb, 'amber', 'honey'),
    ('sarkanycsont|csontenyv', m_chalk, 'bone', 'salt'),
    ('holdezust|huzal', m_coil, 'silver', 'sky'),
    ('konnycsepp|esoviz|harmat|csepp', m_drop, 'water', 'crystal'),
    ('kristaly|kvarc|cseppko|katalizator$', m_crystals, 'crystal', 'royal'),
    ('szilank|visszhang', m_shard, 'crystal', 'night'),
    ('arnygomba|gomba', m_mushroom, 'earth', 'bone'),
    ('arny|sotet|lelekhamu|hamu', m_powder, 'shadow', 'lich'),
    ('aranyfust', m_plate, 'gold', 'amber'),
    ('gyongyhaz|pikkely', m_glowink, 'pearl', 'sky'),
    ('fuszer|vandorfuszer', m_powder, 'blood', 'amber'),
    ('pecset', m_seal, 'wine', 'gold'),
    ('lampaolaj', m_oilcan, 'amber', 'coal'),
    ('folyosito|lug|oldat|sav|ecet', m_vial, 'poison', 'crystal'),
    ('tinta', m_glowink, 'night', 'crystal'),
    ('toll', m_quill, 'silver', 'night'),
    ('kreta', m_chalk, 'salt', 'silver'),
    ('gyertya', m_candle, 'honey', 'amber'),
    ('koso|somezo| so$', m_salt, 'salt', 'silver'),
    ('szuropapir|pergamen|simito|papir|menlevel', m_scroll, 'paper', 'earth'),
    ('tekercs', m_scroll, 'paper', 'royal'),
    ('horog', m_hook, 'iron', 'silver'),
    ('csalizsir|csali', m_fish, 'copper', 'amber'),
    ('halofonal|halo', m_net, 'storm', 'silver'),
    ('parafa|uszo|boja', m_bobber, 'wood', 'salt'),
    ('kotel|zsinor', m_rope, 'earth', 'amber'),
    ('enyv|gyanta|pac|viasz', m_drop, 'amber', 'honey'),
    ('szegecs|kapocs|dugo|keszlet|felszereles', m_kit, 'iron', 'wood'),
    ('robbanto|lopor', m_powder, 'blood', 'coal'),
    ('iranytu|tajolo', m_compass_rose, 'gold', 'blood'),
    ('kenocs|paszta|krem', m_oilcan, 'pearl', 'silver'),
    ('fiola|lombik|uveg|palack|elixir|fozet|parlat|esszencia|kivonat|szurlet|ampulla|kolloid', m_vial, 'crystal', 'royal'),
    ('feno|edzoolaj', m_oilcan, 'steel', 'amber'),
    ('nyelbor|fujtato|bor$', m_saddle, 'leather', 'earth'),
    ('gerenda|zsindely|deszka|ronk|fa$|hantolt', m_log, 'wood', 'earth'),
    ('mag$|magja|sarj|csemete|oltvany|vetes|vetomag', m_sapling, 'nature', 'earth'),
    ('virag|koszoru|azalea|rozsa|liliom|peonia|csokra', m_flower, 'blush', 'nature'),
    ('runapor', m_powder, 'royal', 'crystal'),
    ('runa', m_rune_tablet, 'steel', 'royal'),
    ('emlek', m_shard, 'royal', 'pearl'),
    ('ereklye|kiemeles', m_brush, 'copper', 'paper'),
    ('csend', m_orb, 'night', 'lich'),
    ('meghivo|suttogas', m_scroll, 'shadow', 'royal'),
    ('szorny', m_orb, 'blood', 'coal'),
    ('vasesszencia|vas$|vasracs|vaslanc|otvozet|ontveny|tormelek|rud|lemez', m_ingot, 'iron', 'steel'),
    ('arany', m_ingot, 'gold', 'amber'),
    ('rez', m_ingot, 'copper', 'amber'),
    ('lampas', m_lantern, 'iron', 'honey'),
    ('kurt', m_horn, 'bone', 'gold'),
    ('konyv|kodex|tomus', m_tome, 'royal', 'gold'),
    ('terkep', m_map, 'paper', 'water'),
    ('harang', m_bell, 'gold', 'wood'),
    ('totem', m_totem, 'gold', 'nature'),
    ('csille|minecart', m_cart, 'iron', 'wood'),
    ('lopancel|nyereg', m_saddle, 'iron', 'leather'),
    ('tavcso', m_spyglass, 'copper', 'crystal'),
    ('csakany', m_pick, 'steel', 'wood'),
    ('fejsze|balta', m_axe, 'steel', 'wood'),
    ('szamszerij', m_crossbow, 'wood', 'iron'),
    ('ij$|ijja', m_bow, 'wood', 'salt'),
    ('kard|penge|szablya|toro$|nyelv', m_sword, 'silver', 'leather'),
    ('szigony', m_sword, 'crystal', 'water'),
    ('pajzs', m_shield, 'iron', 'wood'),
    ('sisak|kalap|rostely', m_helmet, 'steel', 'gold'),
    ('csizma|talp', m_boots, 'leather', 'iron'),
    ('vert|pancel|lancing|lancnadrag|fatyla', m_armor, 'steel', 'royal'),
    ('amulett|talizman', m_amulet, 'gold', 'crystal'),
    ('faklya|lampasoszlop', m_torchset, 'wood', 'coal'),
    ('bot$|nyel$|vandorbot', m_chalk, 'wood', 'earth'),
    ('horgaszbot', m_hook, 'wood', 'iron'),
    ('hinar|moha|fu$', m_sapling, 'moss', 'nature'),
    ('mez|hab|vaj', m_drop, 'honey', 'amber'),
    ('dinnye|repa|bogyo|alma', m_pie, 'nature', 'blood'),
    ('szen|brikett|koksz', m_powder, 'coal', 'iron'),
    ('ko$|tegla|oszlop|zsindelyko|kapuzat|tomb', m_rune_tablet, 'steel', 'coal'),
]

import re as _re


def _lookup(tex, cmd):
    if tex in OVERRIDES:
        return OVERRIDES[tex]
    if tex.startswith('catalyst_'):
        main, acc = CATALYST_STYLE.get(cmd, ('royal', 'crystal'))
        return (m_orb, main, acc)
    name = tex.split('_', 1)[-1] if '_' in tex else tex
    for pat, motif, main, acc in RULES:
        if _re.search(pat, name):
            if motif is m_tome:  # a tómuszok drágaköve nevenként más
                gems = ['crystal', 'fire', 'nature', 'ice', 'blood', 'gold', 'shadow',
                        'storm', 'pearl', 'amber', 'sky', 'wine']
                acc = gems[sum(map(ord, name)) % len(gems)]
            return (motif, main, acc)
    return (m_rune_tablet, list(BASE)[sum(map(ord, name)) % len(BASE)], 'silver')


def render_texture(tex, cmd, path):
    from PIL import Image
    motif, main, acc = _lookup(tex, cmd)
    c = C(main, acc, seed=sum(map(ord, tex)) * 31 + (cmd or 0))
    motif(c)
    c.noise(0.10)
    c.finish(Image).save(path)
