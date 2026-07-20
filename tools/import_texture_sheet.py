# -*- coding: utf-8 -*-
"""Sprite-sheet importáló: kész (kézi/AI) textúra-lapból éles pack-textúrákat vág.

A textúrás a docs/RESOURCE_PACK_CMD.md leírásai alapján készít sprite-lapot
(akár bakelt kockás háttérrel); ez a szkript kiszedi a hátteret, megkeresi az
egyes sprite-okat (olvasási sorrend: sorok fentről, soron belül balról), négyzetre
igazítja és a pack felbontására skálázza őket, majd a tools/textures_override/
mappába menti — onnan a build_resourcepack.py a generált placeholder HELYETT
ezeket csomagolja.

Használat:
    python3 tools/import_texture_sheet.py <kep.png> <nev1,nev2,...> [--size 32]
A nevek a pack textúra-fájlnevei (pl. coin_red,coin_blue,coin_neutral,coin_dark,money_pouch).
"""
import os
import sys
from collections import deque

from PIL import Image

OVERRIDE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'textures_override')


def is_backgroundish(rgb):
    r, g, b = rgb[:3]
    return r > 222 and g > 222 and b > 222 and max(r, g, b) - min(r, g, b) < 14


def key_background(im):
    """A képszélről elérhető világos (kockás-háttér) pixelek átlátszóvá tétele —
    a sprite-on BELÜLI fehérek (pl. hópehely) zárt területként megmaradnak."""
    im = im.convert('RGBA')
    w, h = im.size
    px = im.load()
    seen = [[False] * h for _ in range(w)]
    dq = deque()
    for x in range(w):
        for y in (0, h - 1):
            if is_backgroundish(px[x, y]):
                dq.append((x, y)); seen[x][y] = True
    for y in range(h):
        for x in (0, w - 1):
            if is_backgroundish(px[x, y]) and not seen[x][y]:
                dq.append((x, y)); seen[x][y] = True
    while dq:
        x, y = dq.popleft()
        px[x, y] = (0, 0, 0, 0)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not seen[nx][ny] and is_backgroundish(px[nx, ny]):
                seen[nx][ny] = True
                dq.append((nx, ny))
    return im


def components(im, min_area=400):
    """Átlátszatlan összefüggő komponensek bounding-boxai, olvasási sorrendben."""
    w, h = im.size
    px = im.load()
    seen = [[False] * h for _ in range(w)]
    boxes = []
    for y0 in range(h):
        for x0 in range(w):
            if seen[x0][y0] or px[x0, y0][3] == 0:
                continue
            dq = deque([(x0, y0)])
            seen[x0][y0] = True
            minx = maxx = x0; miny = maxy = y0; area = 0
            while dq:
                x, y = dq.popleft()
                area += 1
                minx = min(minx, x); maxx = max(maxx, x)
                miny = min(miny, y); maxy = max(maxy, y)
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < w and 0 <= ny < h and not seen[nx][ny] and px[nx, ny][3] > 0:
                            seen[nx][ny] = True
                            dq.append((nx, ny))
            if area >= min_area:
                boxes.append((minx, miny, maxx, maxy))
    # olvasási sorrend: sor-sávok (átfedő y-tartomány = egy sor), soron belül x szerint
    boxes.sort(key=lambda b: b[1])
    rows = []
    for b in boxes:
        for row in rows:
            if b[1] <= row[0][3]:  # átfed az első elem y-sávjával
                row.append(b)
                break
        else:
            rows.append([b])
    ordered = []
    for row in rows:
        ordered.extend(sorted(row, key=lambda b: b[0]))
    return ordered


def cut(im, box, size):
    x0, y0, x1, y1 = box
    sprite = im.crop((x0, y0, x1 + 1, y1 + 1))
    side = max(sprite.width, sprite.height)
    sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    sq.paste(sprite, ((side - sprite.width) // 2, (side - sprite.height) // 2), sprite)
    return sq.resize((size, size), Image.LANCZOS)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    path, names = sys.argv[1], sys.argv[2].split(',')
    size = 32
    if '--size' in sys.argv:
        size = int(sys.argv[sys.argv.index('--size') + 1])
    im = key_background(Image.open(path))
    boxes = components(im)
    if len(boxes) != len(names):
        print('FIGYELEM: %d sprite-ot találtam, de %d nevet kaptam!' % (len(boxes), len(names)))
        print('boxok:', boxes)
        sys.exit(2)
    os.makedirs(OVERRIDE_DIR, exist_ok=True)
    for name, box in zip(names, boxes):
        out = os.path.join(OVERRIDE_DIR, name + '.png')
        cut(im, box, size).save(out)
        print('%-24s <- %s  (%dx%d)' % (name + '.png', box, box[2] - box[0] + 1, box[3] - box[1] + 1))
    print('Kész — a build_resourcepack.py mostantól ezeket csomagolja a generált helyett.')


if __name__ == '__main__':
    main()
