#!/usr/bin/env python3
"""Generate the four-family RP2-B pilot assets, runtime index and offline visual evidence."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from collections import deque
from functools import lru_cache
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ART_BIBLE = ROOT / "docs/development/equipment-rp2-art-bible.json"
MANIFEST = ROOT / "docs/development/equipment-rp2-pilot-manifest.json"
EVIDENCE = Path("docs/development/equipment-rp2-render-evidence")
AUTHORED_SOURCES = Path("docs/development/equipment-rp2-authored-sources")
SLOT_ORDER = {"HEAD": 0, "CHEST": 1, "LEGS": 2, "FEET": 3}
# Inventory art is 4x the vanilla 16x16 item grid; worn art must retain the same
# sampling density on the fixed vanilla 64x32 equipment UV coordinate grid.
WORN_TEXTURE_SCALE = 4


def json_bytes(value: Any, pretty: bool = True) -> bytes:
    if pretty:
        return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()


def png_bytes(image: Image.Image) -> bytes:
    stream = io.BytesIO()
    image.save(stream, format="PNG", optimize=True, compress_level=9)
    return stream.getvalue()


def colour(value: str) -> tuple[int, int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[index:index + 2], 16) for index in (0, 2, 4)) + (255,)


def shade(value: tuple[int, int, int, int], factor: float) -> tuple[int, int, int, int]:
    return tuple(max(0, min(255, round(channel * factor))) for channel in value[:3]) + (value[3],)


def line_palette(line: dict[str, Any]) -> list[tuple[int, int, int, int]]:
    primary = [colour(item) for item in line["primary_palette"]]
    secondary = [colour(item) for item in line["secondary_palette"]]
    accent = colour(line["accent"])
    return [shade(primary[0], .75), primary[0], primary[len(primary) // 2], primary[-1], secondary[-1], accent]


@lru_cache(maxsize=8)
def authored_source(line_id: str, kind: str) -> Image.Image:
    path = ROOT / AUTHORED_SOURCES / f"{line_id}-{kind}-source.png"
    if not path.is_file():
        raise SystemExit(f"Missing imagegen-authored RP2 source: {path.relative_to(ROOT)}")
    return Image.open(path).convert("RGBA")


def _background_alpha(crop: Image.Image, minimum_neutral: int = 218) -> Image.Image:
    """Remove only edge-connected generated checkerboard/background pixels."""
    alpha = crop.getchannel("A")
    if alpha.getextrema()[0] == 0:
        return alpha.point(lambda value: 255 if value >= 128 else 0)
    rgb = crop.convert("RGB")
    width, height = crop.size
    candidate = Image.new("1", crop.size, 0)
    candidate_pixels = candidate.load()
    source_pixels = rgb.load()
    for y in range(height):
        for x in range(width):
            red, green, blue = source_pixels[x, y]
            if min(red, green, blue) >= minimum_neutral \
                    and max(red, green, blue) - min(red, green, blue) <= 28:
                candidate_pixels[x, y] = 1
    background = Image.new("1", crop.size, 0)
    background_pixels = background.load()
    queue: deque[tuple[int, int]] = deque()
    for x in range(width):
        queue.extend(((x, 0), (x, height - 1)))
    for y in range(height):
        queue.extend(((0, y), (width - 1, y)))
    while queue:
        x, y = queue.popleft()
        if not (0 <= x < width and 0 <= y < height):
            continue
        if background_pixels[x, y] or not candidate_pixels[x, y]:
            continue
        background_pixels[x, y] = 1
        queue.extend(((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))
    return background.point(lambda value: 0 if value else 255)


@lru_cache(maxsize=4)
def imagegen_material_tones(line_id: str, family: str) -> tuple[tuple[int, int, int, int], ...]:
    source = authored_source(line_id, "inventory")
    mask = _background_alpha(source)
    sampled: list[tuple[int, int, int]] = []
    rgb = source.convert("RGB")
    for y in range(0, source.height, 4):
        for x in range(0, source.width, 4):
            if mask.getpixel((x, y)) < 128:
                continue
            value = rgb.getpixel((x, y))
            luminance = round(.2126 * value[0] + .7152 * value[1] + .0722 * value[2])
            metal_tone = max(value) - min(value) <= max(value) * .28
            if family in {"MAIL", "PLATE"} and not metal_tone:
                continue
            if 16 <= luminance <= 238:
                sampled.append(value)
    if len(sampled) < 64:
        raise SystemExit(f"Imagegen source palette collapsed: {line_id}")
    sampled.sort(key=lambda value: .2126 * value[0] + .7152 * value[1] + .0722 * value[2])
    indices = (.08, .28, .58, .88)
    return tuple(sampled[min(len(sampled) - 1, round((len(sampled) - 1) * index))] + (255,) for index in indices)


def imagegen_worn_palette(line: dict[str, Any]) -> list[tuple[int, int, int, int]]:
    """Derive the material tones from the committed imagegen source itself."""
    tones = imagegen_material_tones(line["canonical_line_id"], line["family"])
    authored = line_palette(line)
    return [*tones, authored[4], authored[5]]


def generate_icon(line: dict[str, Any], slot: str) -> Image.Image:
    """Pixel-clean a committed imagegen source quadrant into one runtime icon."""
    source = authored_source(line["canonical_line_id"], "inventory")
    width, height = source.size
    quadrants = {
        "HEAD": (0, 0, width // 2, height // 2),
        "CHEST": (width // 2, 0, width, height // 2),
        "LEGS": (0, height // 2, width // 2, height),
        "FEET": (width // 2, height // 2, width, height),
    }
    crop = source.crop(quadrants[slot])
    crop.putalpha(_background_alpha(crop))
    bbox = crop.getbbox()
    if bbox is None:
        raise SystemExit(f"Imagegen source quadrant is empty: {line['canonical_line_id']} {slot}")
    crop = crop.crop(bbox)
    ratio = min(27 / crop.width, 27 / crop.height)
    logical_size = (max(1, round(crop.width * ratio)), max(1, round(crop.height * ratio)))
    logical = crop.resize(logical_size, Image.Resampling.NEAREST)
    quantized = logical.convert("RGB").quantize(colors=24, method=Image.Quantize.MEDIANCUT,
                                                        dither=Image.Dither.NONE).convert("RGBA")
    quantized.putalpha(logical.getchannel("A").point(lambda value: 255 if value >= 128 else 0))
    logical_canvas_image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    logical_canvas_image.alpha_composite(quantized, ((32 - logical.width) // 2, (32 - logical.height) // 2))
    return logical_canvas_image.resize((64, 64), Image.Resampling.NEAREST)


def rect(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], fill: tuple[int, ...]) -> None:
    draw.rectangle((box[0], box[1], box[2] - 1, box[3] - 1), fill=fill)


def fill_uv_faces(draw: ImageDraw.ImageDraw, boxes: list[tuple[int, int, int, int]],
                  palette: list[tuple[int, int, int, int]]) -> None:
    dark, base, mid, light, _, _ = palette
    for index, box in enumerate(boxes):
        face_base = (base, mid, base, shade(base, .9))[index % 4]
        rect(draw, box, face_base)
        x1, y1, x2, y2 = box
        if x2 - x1 >= 3 and y2 - y1 >= 3:
            draw.line((x1, y1, x2 - 1, y1), fill=light)
            draw.line((x1, y1, x1, y2 - 1), fill=shade(light, .88))
            draw.line((x1, y2 - 1, x2 - 1, y2 - 1), fill=dark)
            draw.line((x2 - 1, y1, x2 - 1, y2 - 1), fill=shade(dark, .82))
        for y in range(y1 + 2, y2 - 1, 3):
            for x in range(x1 + 2 + ((y - y1) % 2), x2 - 1, 4):
                draw.point((x, y), fill=shade(face_base, 1.08 if (x + y) % 3 else .88))


@lru_cache(maxsize=12)
def authored_worn_view(line_id: str, view_index: int) -> Image.Image:
    source = authored_source(line_id, "worn")
    left = round(source.width * view_index / 3)
    right = round(source.width * (view_index + 1) / 3)
    view = source.crop((left, 0, right, source.height))
    view.putalpha(_background_alpha(view, minimum_neutral=160))
    bbox = view.getbbox()
    if bbox is None:
        raise SystemExit(f"Imagegen worn source view is empty: {line_id} view={view_index}")
    return view.crop(bbox)


def projected_part(source: Image.Image, normalized: tuple[float, float, float, float],
                   size: tuple[int, int], palette: list[tuple[int, int, int, int]]) -> Image.Image:
    x1, y1, x2, y2 = normalized
    crop = source.crop((round(source.width * x1), round(source.height * y1),
                        round(source.width * x2), round(source.height * y2)))
    alpha = crop.getchannel("A").resize(size, Image.Resampling.BOX).point(
        lambda value: 255 if value >= 96 else 0)
    reduced = crop.convert("RGB").resize(size, Image.Resampling.BOX)
    palette_image = Image.new("P", (1, 1))
    raw_palette: list[int] = []
    for value in palette:
        raw_palette.extend(value[:3])
    raw_palette.extend([0] * (768 - len(raw_palette)))
    palette_image.putpalette(raw_palette)
    reduced = reduced.quantize(palette=palette_image, dither=Image.Dither.NONE).convert("RGBA")
    reduced.putalpha(alpha)
    return reduced


def project_imagegen_worn(line: dict[str, Any], main: Image.Image, leggings: Image.Image,
                          palette: list[tuple[int, int, int, int]], uv_scale: int = 1) -> None:
    regions = {
        "front": {"head": (.23, .00, .77, .31), "body": (.24, .27, .76, .60),
                  "arm": (.03, .28, .29, .61), "leg": (.24, .57, .50, .99)},
        "back": {"head": (.23, .00, .77, .31), "body": (.24, .27, .76, .60),
                 "arm": (.03, .28, .29, .61), "leg": (.24, .57, .50, .99)},
        "side": {"head": (.18, .00, .82, .31), "body": (.27, .27, .73, .60),
                 "arm": (.22, .28, .58, .61), "leg": (.28, .57, .65, .99)},
    }
    view_names = ("front", "back", "side")
    for view_index, view_name in enumerate(view_names):
        source = authored_worn_view(line["canonical_line_id"], view_index)
        for part in ("head", "body", "arm", "leg"):
            box = tuple(value * uv_scale for value in FACE_UV[view_name][part])
            size = (box[2] - box[0], box[3] - box[1])
            texture = projected_part(source, regions[view_name][part], size, palette)
            main.alpha_composite(texture, (box[0], box[1]))
            if part in {"body", "leg"}:
                leggings.alpha_composite(texture, (box[0], box[1]))


def paint_face_pattern(image: Image.Image, box: tuple[int, int, int, int], pattern: tuple[str, ...],
                       palette: list[tuple[int, int, int, int]]) -> None:
    """Paint one authored low-resolution motif directly onto a vanilla armor UV face."""
    width = box[2] - box[0]
    height = box[3] - box[1]
    if len(pattern) != height or any(len(row) != width for row in pattern):
        raise SystemExit(f"Invalid worn UV pattern {width}x{height}: {pattern}")
    values = {
        ".": shade(palette[0], .72),
        "d": palette[0], "b": palette[1], "m": palette[2], "l": palette[3],
        "s": palette[4], "a": palette[5],
    }
    pixels = image.load()
    for y, row in enumerate(pattern):
        for x, token in enumerate(row):
            pixels[box[0] + x, box[1] + y] = values[token]


def paint_imagegen_identity(line: dict[str, Any], main: Image.Image, leggings: Image.Image,
                            palette: list[tuple[int, int, int, int]]) -> None:
    """Preserve the bold motifs of each committed ImageGen turnaround at real UV resolution."""
    family = line["family"]
    identity_palette = list(palette)
    if family == "PLATE":
        # The Art Bible secondary is the amber-bearing leather joint, not another focal glow.
        identity_palette[4] = shade(identity_palette[4], .58)
    patterns: dict[str, dict[str, tuple[str, ...]]]
    if family == "CLOTH":
        patterns = {
            "front": {
                "head": ("mbllllbm", "lbbbbbbm", "lb....bm", "lb....bm", "lb....bm", "lb....bm", "lbbbbbbm", "mssssssm"),
                "body": ("lbbbbbbm", "lbbllbbm", "lblbbbmm", "lbllbbmm", "lbllbbmm", "lblbbbmm", "lbbllbbm", "lbbbbbbm", "ssssssss", "lbbaabmm", "lbbssbmm", "mllllllm"),
                "arm": ("mbbm", "lbbm", "lbbm", "lbbm", "lbbm", "lsbm", "sbls", "lbbm", "lbbm", "lbbm", "ssss", "mllm"),
                "leg": ("lbbm", "lbsm", "lbam", "lbsm", "lbbm", "lbbm", "lbbm", "lbbm", "lbsm", "lsbm", "lbbm", "mllm"),
            },
            "back": {
                "head": ("mbllllbm", "lbblllbm", "lbllbbbm", "lbllbbbm", "lbllbbbm", "lbblllbm", "lbbbbllm", "mllllllm"),
                "body": ("lbbbbbbm", "lbblllbm", "lbllbbbm", "lbllbbbm", "lbllbbbm", "lbblllbm", "lbbbbllm", "lbbbbbbm", "ssssssss", "lbbbbbbm", "lbbbbbbm", "mllllllm"),
                "arm": ("mbbm", "lbbm", "lbbm", "lbbm", "lbbm", "lsbm", "sbls", "lbbm", "lbbm", "lbbm", "ssss", "mllm"),
                "leg": ("lbbm", "lbbm", "lbsm", "lbam", "lbsm", "lbbm", "lbbm", "lbbm", "lbbm", "lsbm", "lbbm", "mllm"),
            },
            "side": {
                "head": ("mbllllbm", "lbbbbbbm", "lb....bm", "lb....bm", "lb....bm", "lb....bm", "lbbbbbbm", "mssssssm"),
                "body": ("lbbm", "lsbm", "lbbm", "lbbm", "lbbm", "lbbm", "lbbm", "lbbm", "sbbm", "lbbm", "lbbm", "mllm"),
                "arm": ("mbbm", "lbbm", "lbbm", "lbbm", "lbbm", "lsbm", "sbls", "lbbm", "lbbm", "lbbm", "ssss", "mllm"),
                "leg": ("lbbm", "lbsm", "lbam", "lbsm", "lbbm", "lbbm", "lbbm", "lbbm", "lbsm", "lsbm", "lbbm", "mllm"),
            },
        }
    elif family == "LEATHER":
        patterns = {
            "front": {
                "head": ("bssssssb", "sbbbbbbs", "sb....bs", "sb....bs", "sb....bs", "sb....bs", "sbbbbbbs", "bbssssbb"),
                "body": ("ssbbbbss", "ssmbbbss", "bmmbbbmb", "bmlmbmbb", "bbmmmbbb", "bbbmmmbb", "bbmbmlmb", "bmmbbmmb", "ssssssss", "bbbaabbb", "bbbssbbb", "dbbbbbbd"),
                "arm": ("sssb", "sbbb", "mbbb", "bmbb", "bbmb", "bbbm", "ssss", "bbbs", "bbbs", "bbbs", "ssss", "dbbd"),
                "leg": ("bmsb", "bmmb", "bbmb", "bbbm", "bbbb", "ssss", "baab", "ssss", "baab", "ssss", "bbbb", "dbbd"),
            },
            "back": {
                "head": ("bbssssbb", "bbbbbbbb", "bbbsbbbb", "bbbsbbbb", "bbbsbbbb", "bbbsbbbb", "bbbsbbbb", "bbssssbb"),
                "body": ("ssbbbbss", "smbbbbms", "bmmbbmmb", "bbmssmbb", "bbbssbbb", "bbmssmbb", "bmmbbmmb", "mmbbbbmm", "ssssssss", "bbbbbbbb", "bbbbbbbb", "dbbbbbbd"),
                "arm": ("sssb", "sbbb", "mbbb", "bmbb", "bbmb", "bbbm", "ssss", "bbbs", "bbbs", "bbbs", "ssss", "dbbd"),
                "leg": ("bmsb", "bmmb", "bbmb", "bbbm", "bbbb", "ssss", "bbbb", "bbbb", "ssss", "bbbb", "bbbb", "dbbd"),
            },
            "side": {
                "head": ("bbssssbb", "bbbbbbbs", "bb....bs", "bb....bs", "bb....bs", "bb....bs", "bbbbbbbs", "bbssssbb"),
                "body": ("sssb", "sbbb", "mbbb", "bmbb", "bbmb", "bbbm", "ssss", "bbbs", "bbbs", "bbbs", "ssss", "dbbd"),
                "arm": ("sssb", "sbbb", "mbbb", "bmbb", "bbmb", "bbbm", "ssss", "bbbs", "bbbs", "bbbs", "ssss", "dbbd"),
                "leg": ("bmsb", "bmmb", "bbmb", "bbbm", "bbbb", "ssss", "baab", "ssss", "baab", "ssss", "bbbb", "dbbd"),
            },
        }
    elif family == "MAIL":
        patterns = {
            "front": {
                "head": ("ldldldld", "dldldldl", "ld....ld", "dl....dl", "ld....ld", "dl....dl", "ldssssld", "sasaasas"),
                "body": ("ldldldld", "dldldlds", "ldldldss", "dldldssb", "ldldssbl", "dldssbld", "ldssbldl", "dssbldld", "ssssssss", "bbbabbba", "bbbbbbbb", "dddddddd"),
                "arm": ("sasa", "ldld", "dldl", "ldld", "dldl", "ldld", "sasa", "bbbb", "bbbb", "bbbb", "sasa", "dddd"),
                "leg": ("ldld", "dldl", "ldld", "dldl", "ldld", "dldl", "ldld", "dldl", "sasa", "bbbb", "bbbb", "dddd"),
            },
            "back": {
                "head": ("ldldldld", "dldldldl", "ldldldld", "dldldldl", "ldldldld", "dldldldl", "ldldldld", "sasaasas"),
                "body": ("ldldldld", "sldldldl", "ssldldld", "bssldldl", "lbssldld", "dlbssldl", "ldlbssld", "dldlbssd", "ssssssss", "abbbabba", "bbbbbbbb", "dddddddd"),
                "arm": ("sasa", "ldld", "dldl", "ldld", "dldl", "ldld", "sasa", "bbbb", "bbbb", "bbbb", "sasa", "dddd"),
                "leg": ("ldld", "dldl", "ldld", "dldl", "ldld", "dldl", "ldld", "dldl", "sasa", "bbbb", "bbbb", "dddd"),
            },
            "side": {
                "head": ("ldldldld", "dldldldl", "ld....ld", "dl....dl", "ld....ld", "dl....dl", "ldssssld", "sasaasas"),
                "body": ("ldld", "dlds", "ldss", "dssb", "ssbl", "sbld", "bldl", "ldld", "ssss", "bbba", "bbbb", "dddd"),
                "arm": ("sasa", "ldld", "dldl", "ldld", "dldl", "ldld", "sasa", "bbbb", "bbbb", "bbbb", "sasa", "dddd"),
                "leg": ("ldld", "dldl", "ldld", "dldl", "ldld", "dldl", "ldld", "dldl", "sasa", "bbbb", "bbbb", "dddd"),
            },
        }
    else:
        patterns = {
            "front": {
                "head": ("dllmmlld", "lmbbbblm", "lbaaaabl", "lbddddbl", "lddddddl", "lddddddl", "lbbllbbl", "dllsslld"),
                "body": ("dllssldd", "lssssssl", "lsllllsl", "lsmbbmsl", "lsbaabsl", "lsbaabsl", "lsmbbmsl", "lsllllsl", "lssssssl", "dbssssbd", "dbbbbbbd", "dddddddd"),
                "arm": ("dssd", "slls", "sbbd", "lmml", "lssl", "lssl", "lmml", "sbbd", "lssl", "lmml", "sbbd", "dddd"),
                "leg": ("dssd", "slls", "slas", "slls", "sdds", "slls", "dbbd", "ssss", "slls", "sdds", "slls", "dddd"),
            },
            "back": {
                "head": ("dllmmlld", "lmbbbblm", "lmbbbblm", "lmbbbblm", "lmbbbblm", "lmbbbblm", "lbbllbbl", "dllsslld"),
                "body": ("dllssldd", "lssssssl", "lsllllsl", "lsmbbmsl", "lsmbbmsl", "lsmbbmsl", "lsmbbmsl", "lsllllsl", "lssssssl", "dbssssbd", "dbbbbbbd", "dddddddd"),
                "arm": ("dssd", "slls", "sbbd", "lmml", "lssl", "lssl", "lmml", "sbbd", "lssl", "lmml", "sbbd", "dddd"),
                "leg": ("dssd", "slls", "slls", "sdds", "slls", "slls", "dbbd", "ssss", "slls", "sdds", "slls", "dddd"),
            },
            "side": {
                "head": ("dllmmlld", "lmbbbblm", "lmbbbblm", "lddddddl", "lddddddl", "lddddddl", "lbbllbbl", "dllsslld"),
                "body": ("dssd", "slls", "sbbd", "lmml", "lssl", "lssl", "lmml", "sbbd", "lssl", "lmml", "sbbd", "dddd"),
                "arm": ("dssd", "slls", "sbbd", "lmml", "lssl", "lssl", "lmml", "sbbd", "lssl", "lmml", "sbbd", "dddd"),
                "leg": ("dssd", "slls", "slas", "slls", "sdds", "slls", "dbbd", "ssss", "slls", "sdds", "slls", "dddd"),
            },
        }
    for view, parts in patterns.items():
        for part, pattern in parts.items():
            paint_face_pattern(main, FACE_UV[view][part], pattern, identity_palette)
            if part in {"body", "leg"}:
                paint_face_pattern(leggings, FACE_UV[view][part], pattern, identity_palette)


def worn_textures(line: dict[str, Any]) -> tuple[Image.Image, Image.Image]:
    palette = imagegen_worn_palette(line)
    dark, base, mid, light, secondary, accent = palette
    main = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    legs = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    dm = ImageDraw.Draw(main)
    dl = ImageDraw.Draw(legs)
    head = [(8, 0, 16, 8), (16, 0, 24, 8), (0, 8, 8, 16), (8, 8, 16, 16), (16, 8, 24, 16), (24, 8, 32, 16)]
    body = [(20, 16, 28, 20), (28, 16, 36, 20), (16, 20, 20, 32), (20, 20, 28, 32), (28, 20, 32, 32), (32, 20, 40, 32)]
    arm = [(44, 16, 48, 20), (48, 16, 52, 20), (40, 20, 44, 32), (44, 20, 48, 32), (48, 20, 52, 32), (52, 20, 56, 32)]
    leg = [(4, 16, 8, 20), (8, 16, 12, 20), (0, 20, 4, 32), (4, 20, 8, 32), (8, 20, 12, 32), (12, 20, 16, 32)]
    fill_uv_faces(dm, head, palette)
    fill_uv_faces(dm, body, palette)
    fill_uv_faces(dm, arm, palette)
    fill_uv_faces(dm, leg, palette)
    fill_uv_faces(dl, body, palette)
    fill_uv_faces(dl, leg, palette)
    if WORN_TEXTURE_SCALE == 1:
        project_imagegen_worn(line, main, legs, palette)

    family = line["family"]
    main_boxes = head + body + arm + leg
    leggings_boxes = body + leg
    if family == "CLOTH":
        for target, boxes in ((dm, main_boxes), (dl, leggings_boxes)):
            for x1, y1, x2, y2 in boxes:
                if x2 - x1 >= 4:
                    target.line((x1 + 1, y1 + 1, x1 + 1, y2 - 2), fill=mid)
                    target.line((x2 - 2, y1 + 1, x2 - 2, y2 - 2), fill=secondary)
                if y2 - y1 >= 7:
                    target.line((x1 + 1, y2 - 3, x2 - 2, y2 - 3), fill=light)
    elif family == "LEATHER":
        for target, boxes in ((dm, main_boxes), (dl, leggings_boxes)):
            for x1, y1, x2, y2 in boxes:
                span = min(x2 - x1, y2 - y1)
                if span >= 4:
                    target.line((x1 + 1, y1 + 1, x2 - 2, min(y2 - 2, y1 + span - 1)), fill=secondary)
                    for offset in range(2, span - 1, 3):
                        target.point((x1 + offset, min(y2 - 2, y1 + offset)), fill=light)
                if y2 - y1 >= 6:
                    target.point((x2 - 2, y2 - 2), fill=accent)
    elif family == "MAIL":
        for target, boxes in ((dm, main_boxes), (dl, leggings_boxes)):
            for x1, y1, x2, y2 in boxes:
                rect(target, (x1, y1, x2, y2), dark)
                for y in range(y1 + 1, y2 - 1, 3):
                    start = x1 + 1 + (1 if ((y - y1) // 3) % 2 else 0)
                    for x in range(start, x2 - 1, 3):
                        target.point((x, y), fill=light)
                        if x + 1 < x2:
                            target.point((x + 1, y), fill=mid)
                        if y + 1 < y2:
                            target.point((x, y + 1), fill=base)
                target.line((x1, y2 - 1, x2 - 1, y2 - 1), fill=secondary)
    else:
        for target, boxes in ((dm, main_boxes), (dl, leggings_boxes)):
            for x1, y1, x2, y2 in boxes:
                if x2 - x1 >= 4 and y2 - y1 >= 4:
                    target.rectangle((x1 + 1, y1 + 1, x2 - 2, y2 - 2), outline=light)
                    target.line((x1 + 2, y2 - 2, x2 - 2, y2 - 2), fill=dark)
                    target.point((x1 + 1, y1 + 1), fill=secondary)
                    target.point((x2 - 2, y2 - 2), fill=secondary)
    if family in {"CLOTH", "LEATHER", "MAIL"}:
        # A front-facing hood/coif opening is a dark inset, not missing atlas coverage.
        rect(dm, (10, 10, 14, 15), shade(dark, .52))
    if family == "CLOTH":
        for x in (21, 24, 27):
            dm.line((x, 21, x, 31), fill=light if x == 24 else mid)
            dl.line((x, 21, x, 31), fill=light if x == 24 else mid)
        dm.arc((21, 22, 27, 28), 45, 305, fill=secondary)
        dl.line((4, 21, 4, 31), fill=secondary)
        dl.line((7, 21, 7, 31), fill=accent)
        dm.line((0, 25, 15, 25), fill=light)
    elif family == "LEATHER":
        for target in (dm, dl):
            target.line((20, 21, 27, 29), fill=secondary)
            target.line((27, 21, 20, 29), fill=dark)
            target.point((22, 23), fill=light)
            target.point((24, 25), fill=light)
        dm.line((40, 22, 55, 29), fill=secondary)
        dm.line((0, 24, 15, 28), fill=secondary)
    elif family == "MAIL":
        for target in (dm, dl):
            for y in range(21, 32, 2):
                for x in range(16 + (y % 4), 40, 3):
                    target.point((x, y), fill=light)
                    if x + 1 < 40:
                        target.point((x + 1, y), fill=dark)
            target.rectangle((23, 23, 25, 25), fill=secondary)
        for y in range(21, 32, 2):
            for x in range(40 + (y % 4), 56, 3):
                dm.point((x, y), fill=light)
        dm.line((0, 26, 15, 26), fill=secondary)
    else:
        for target in (dm, dl):
            target.rectangle((20, 20, 27, 31), outline=light)
            target.rectangle((22, 23, 25, 27), fill=secondary, outline=dark)
            target.rectangle((23, 24, 24, 26), fill=accent)
        dm.line((40, 22, 55, 22), fill=light)
        dm.line((40, 29, 55, 29), fill=dark)
        dm.rectangle((9, 11, 14, 13), fill=dark)
        for x, y in ((21, 21), (26, 21), (21, 30), (26, 30), (4, 24), (11, 24)):
            dm.point((x, y), fill=secondary)
        dm.rectangle((4, 23, 7, 29), outline=light)
        dm.rectangle((8, 23, 11, 29), outline=light)
    apply_worn_slot_masks(main, legs, head, body, arm, leg)
    if WORN_TEXTURE_SCALE > 1:
        target_size = (64 * WORN_TEXTURE_SCALE, 32 * WORN_TEXTURE_SCALE)
        main = main.resize(target_size, Image.Resampling.NEAREST)
        legs = legs.resize(target_size, Image.Resampling.NEAREST)
        project_imagegen_worn(line, main, legs, palette, WORN_TEXTURE_SCALE)
        scaled = lambda boxes: [tuple(value * WORN_TEXTURE_SCALE for value in box)
                                for box in boxes]
        apply_worn_slot_masks(main, legs, scaled(head), scaled(body), scaled(arm), scaled(leg))
    return main, legs


def apply_worn_slot_masks(main: Image.Image, leggings: Image.Image,
                          head: list[tuple[int, int, int, int]],
                          body: list[tuple[int, int, int, int]],
                          arm: list[tuple[int, int, int, int]],
                          leg: list[tuple[int, int, int, int]]) -> None:
    """Keep each equipment layer inside the physical region owned by its armor slot."""
    transparent = (0, 0, 0, 0)
    scale = main.width // 64
    main_draw = ImageDraw.Draw(main)
    leggings_draw = ImageDraw.Draw(leggings)

    # Boots use the outer leg model but own only its lower five vertical pixels. Keeping the
    # physical leg-top face would create a floating plate at thigh height.
    rect(main_draw, leg[0], transparent)
    for x1, y1, x2, y2 in leg[2:]:
        rect(main_draw, (x1, y1, x2, y2 - 5 * scale), transparent)

    # Leggings may provide a narrow lower-torso waistband, never a second chest texture.
    rect(leggings_draw, body[0], transparent)
    for x1, y1, x2, y2 in body[2:]:
        rect(leggings_draw, (x1, y1, x2, y2 - 4 * scale), transparent)

    # These atlases are slot-specific. Head/arm pixels in the leggings layer and unrelated
    # pixels outside the canonical UV islands must remain transparent.
    for box in head + arm:
        rect(leggings_draw, box, transparent)


FACE_UV = {
    "front": {"head": (8, 8, 16, 16), "body": (20, 20, 28, 32), "arm": (44, 20, 48, 32), "leg": (4, 20, 8, 32)},
    "back": {"head": (24, 8, 32, 16), "body": (32, 20, 40, 32), "arm": (52, 20, 56, 32), "leg": (12, 20, 16, 32)},
    "side": {"head": (0, 8, 8, 16), "body": (16, 20, 20, 32), "arm": (40, 20, 44, 32), "leg": (0, 20, 4, 32)},
}


def face(texture: Image.Image, box: tuple[int, int, int, int], size: tuple[int, int]) -> Image.Image:
    scale = texture.width // 64
    scaled_box = tuple(value * scale for value in box)
    return texture.crop(scaled_box).resize(size, Image.Resampling.NEAREST)


def mannequin(main: Image.Image, leggings: Image.Image, view: str, skin: tuple[int, int, int, int]) -> Image.Image:
    canvas = Image.new("RGBA", (180, 280), (26, 29, 31, 255))
    uv = FACE_UV[view]
    if view in {"front", "back"}:
        parts = [
            ("head", (66, 18), (48, 48)), ("body", (66, 66), (48, 72)),
            ("arm", (42, 66), (24, 72)), ("arm", (114, 66), (24, 72)),
            ("leg", (66, 138), (24, 108)), ("leg", (90, 138), (24, 108)),
        ]
    else:
        parts = [
            ("head", (72, 18), (48, 48)), ("body", (78, 66), (24, 72)),
            ("arm", (54, 66), (24, 72)), ("arm", (102, 66), (24, 72)),
            ("leg", (78, 138), (24, 108)), ("leg", (102, 138), (24, 108)),
        ]
    draw = ImageDraw.Draw(canvas)
    for kind, position, size in parts:
        draw.rectangle((position[0], position[1], position[0] + size[0] - 1, position[1] + size[1] - 1), fill=skin)
        base_texture = leggings if kind in {"body", "leg"} else main
        canvas.alpha_composite(face(base_texture, uv[kind], size), position)
        if kind in {"body", "leg"}:
            canvas.alpha_composite(face(main, uv[kind], size), position)
    return canvas


def mannequin_slot(main: Image.Image, leggings: Image.Image, slot: str,
                    skin: tuple[int, int, int, int]) -> Image.Image:
    canvas = Image.new("RGBA", (180, 280), (26, 29, 31, 255))
    uv = FACE_UV["front"]
    parts = [
        ("head", (66, 18), (48, 48)), ("body", (66, 66), (48, 72)),
        ("arm", (42, 66), (24, 72)), ("arm", (114, 66), (24, 72)),
        ("leg", (66, 138), (24, 108)), ("leg", (90, 138), (24, 108)),
    ]
    visible = {
        "HEAD": {"head"},
        "CHEST": {"body", "arm"},
        "LEGS": {"body", "leg"},
        "FEET": {"leg"},
    }[slot]
    draw = ImageDraw.Draw(canvas)
    for kind, position, size in parts:
        draw.rectangle((position[0], position[1], position[0] + size[0] - 1,
                        position[1] + size[1] - 1), fill=skin)
        if kind not in visible:
            continue
        texture = leggings if slot == "LEGS" else main
        canvas.alpha_composite(face(texture, uv[kind], size), position)
    return canvas


def inventory_sheet(line: dict[str, Any], icons: dict[str, Image.Image]) -> Image.Image:
    sheet = Image.new("RGBA", (320, 104), (26, 29, 31, 255))
    for index, slot in enumerate(("HEAD", "CHEST", "LEGS", "FEET")):
        x = 16 + index * 76
        sheet.alpha_composite(icons[slot], (x, 12))
        thumb = icons[slot].resize((16, 16), Image.Resampling.NEAREST).resize((32, 32), Image.Resampling.NEAREST)
        sheet.alpha_composite(thumb, (x + 32, 68))
    return sheet


def comparison_sheet(renders: dict[str, Image.Image]) -> Image.Image:
    sheet = Image.new("RGBA", (720, 280), (22, 24, 26, 255))
    for index, family in enumerate(("CLOTH", "LEATHER", "MAIL", "PLATE")):
        sheet.alpha_composite(renders[family], (index * 180, 0))
    return sheet


def worn_fidelity_sheet(references: dict[str, Image.Image], renders: dict[str, Image.Image]) -> Image.Image:
    """Place the ImageGen front source over the actual UV render for direct fidelity review."""
    sheet = Image.new("RGBA", (720, 560), (22, 24, 26, 255))
    for index, family in enumerate(("CLOTH", "LEATHER", "MAIL", "PLATE")):
        source = references[family]
        ratio = min(164 / source.width, 260 / source.height)
        source = source.resize((max(1, round(source.width * ratio)), max(1, round(source.height * ratio))),
                               Image.Resampling.NEAREST)
        sheet.alpha_composite(source, (index * 180 + (180 - source.width) // 2, (280 - source.height) // 2))
        sheet.alpha_composite(renders[family], (index * 180, 280))
    return sheet


def slot_separation_sheet(textures: dict[str, tuple[Image.Image, Image.Image]]) -> Image.Image:
    sheet = Image.new("RGBA", (360, 560), (22, 24, 26, 255))
    for column, family in enumerate(("CLOTH", "LEATHER", "MAIL", "PLATE")):
        main, leggings = textures[family]
        for row, slot in enumerate(("HEAD", "CHEST", "LEGS", "FEET")):
            render = mannequin_slot(main, leggings, slot, (177, 132, 104, 255))
            sheet.alpha_composite(render.resize((90, 140), Image.Resampling.NEAREST),
                                  (column * 90, row * 140))
    return sheet


def build_files(selection: dict[str, Any], art: dict[str, Any]) -> tuple[dict[Path, bytes], dict[str, Any]]:
    by_id = {line["canonical_line_id"]: line for line in art["gear_lines"]}
    selected = selection["selected_lines"]
    if len(selected) != 4 or {entry["family"] for entry in selected} != {"CLOTH", "LEATHER", "MAIL", "PLATE"}:
        raise SystemExit("Pilot manifest must select exactly one line per family")
    files: dict[Path, bytes] = {}
    piece_records: list[dict[str, Any]] = []
    family_front: dict[str, Image.Image] = {}
    family_reference: dict[str, Image.Image] = {}
    family_textures: dict[str, tuple[Image.Image, Image.Image]] = {}
    evidence_index: list[dict[str, Any]] = []
    custom_assets: list[str] = []
    custom_models: list[str] = []
    concept_reference = EVIDENCE / "concept-reference.png"
    if not (ROOT / concept_reference).is_file():
        raise SystemExit("Missing authored AI concept reference: " + str(concept_reference))
    authored_source_records: list[dict[str, str]] = []
    for selected_line in sorted(selected, key=lambda value: value["family"]):
        for kind in ("inventory", "worn"):
            path = AUTHORED_SOURCES / f"{selected_line['line_id']}-{kind}-source.png"
            if not (ROOT / path).is_file():
                raise SystemExit("Missing imagegen-authored pilot source: " + str(path))
            authored_source_records.append({
                "line_id": selected_line["line_id"], "kind": kind, "path": str(path),
                "sha256": hashlib.sha256((ROOT / path).read_bytes()).hexdigest(),
                "authoring": "OPENAI_IMAGEGEN_BUILT_IN",
            })

    for selected_line in sorted(selected, key=lambda value: value["family"]):
        line_id = selected_line["line_id"]
        line = by_id.get(line_id)
        if line is None or line["family"] != selected_line["family"]:
            raise SystemExit(f"Pilot selection is not production-backed: {selected_line}")
        if line["progression_band"] != "mid" or line["acquisition"] != "crafted" or line["set_status"] or line["signature_status"] or line["ascension_status"]:
            raise SystemExit(f"Pilot selection violates central-line constraints: {line_id}")
        equipment_asset = f"icesmp:rp2/{line_id}"
        custom_assets.append(equipment_asset)
        equipment_path = Path(f"resource-pack/assets/icesmp/equipment/rp2/{line_id}.json")
        humanoid_path = Path(f"resource-pack/assets/icesmp/textures/entity/equipment/humanoid/rp2/{line_id}.png")
        leggings_path = Path(f"resource-pack/assets/icesmp/textures/entity/equipment/humanoid_leggings/rp2/{line_id}.png")
        main, leggings = worn_textures(line)
        files[equipment_path] = json_bytes({"layers": {"humanoid": [{"texture": f"icesmp:rp2/{line_id}"}], "humanoid_leggings": [{"texture": f"icesmp:rp2/{line_id}"}]}})
        files[humanoid_path] = png_bytes(main)
        files[leggings_path] = png_bytes(leggings)

        icons: dict[str, Image.Image] = {}
        for slot, template_id in sorted(line["piece_slots"].items(), key=lambda item: SLOT_ORDER[item[0]]):
            icon = generate_icon(line, slot)
            icons[slot] = icon
            item_definition = Path(f"resource-pack/assets/icesmp/items/{template_id}.json")
            item_model = Path(f"resource-pack/assets/icesmp/models/item/{template_id}.json")
            item_texture = Path(f"resource-pack/assets/icesmp/textures/item/{template_id}.png")
            files[item_definition] = json_bytes({"model": {"type": "minecraft:model", "model": f"icesmp:item/{template_id}"}, "hand_animation_on_swap": True, "oversized_in_gui": False, "swap_animation_scale": 1.0})
            files[item_model] = json_bytes({"parent": "minecraft:item/generated", "textures": {"layer0": f"icesmp:item/{template_id}"}})
            files[item_texture] = png_bytes(icon)
            custom_models.append(f"icesmp:{template_id}")
            piece_records.append({
                "template_id": template_id, "family": line["family"], "line_id": line_id, "slot": slot,
                "item_model": f"icesmp:{template_id}", "item_definition": str(item_definition),
                "model": str(item_model), "inventory_texture": str(item_texture),
                "inventory_authored_source": str(AUTHORED_SOURCES / f"{line_id}-inventory-source.png"),
                "worn_authored_source": str(AUTHORED_SOURCES / f"{line_id}-worn-source.png"),
                "equipment_asset": equipment_asset, "equipment_definition": str(equipment_path),
                "worn_textures": [str(humanoid_path), str(leggings_path)],
                "fallback_status": "RP2_CUSTOM", "validation_status": "GENERATOR_STRUCTURAL_PASS",
            })

        views = {view: mannequin(main, leggings, view, (177, 132, 104, 255)) for view in ("front", "back", "side")}
        family_front[line["family"]] = views["front"]
        family_reference[line["family"]] = authored_worn_view(line_id, 0)
        family_textures[line["family"]] = (main, leggings)
        inventory = inventory_sheet(line, icons)
        line_evidence = []
        for name, image in {"inventory": inventory, **views}.items():
            path = EVIDENCE / f"{line_id}-{name}.png"
            files[path] = png_bytes(image)
            line_evidence.append(str(path))
        evidence_index.append({"line_id": line_id, "family": line["family"], "proof_mode": "OFFLINE_DETERMINISTIC_RENDER", "files": line_evidence})

    files[EVIDENCE / "family-comparison.png"] = png_bytes(comparison_sheet(family_front))
    files[EVIDENCE / "worn-reference-comparison.png"] = png_bytes(
        worn_fidelity_sheet(family_reference, family_front))
    files[EVIDENCE / "slot-layer-separation.png"] = png_bytes(
        slot_separation_sheet(family_textures))
    skin_sheet = Image.new("RGBA", (720, 840), (22, 24, 26, 255))
    skin_values = [(78, 52, 43, 255), (177, 132, 104, 255), (224, 184, 151, 255)]
    for row, skin in enumerate(skin_values):
        for column, selected_line in enumerate(sorted(selected, key=lambda value: value["family"])):
            line = by_id[selected_line["line_id"]]
            main, leggings = worn_textures(line)
            skin_sheet.alpha_composite(mannequin(main, leggings, "front", skin), (column * 180, row * 280))
    files[EVIDENCE / "skin-compatibility.png"] = png_bytes(skin_sheet)
    scale_sheet = Image.new("RGBA", (720, 350), (22, 24, 26, 255))
    for column, family in enumerate(("CLOTH", "LEATHER", "MAIL", "PLATE")):
        full = family_front[family]
        scale_sheet.alpha_composite(full, (column * 180, 0))
        for size, xoff, yoff in ((140, 20, 205), (70, 105, 245)):
            ratio = size / full.height
            scaled = full.resize((round(full.width * ratio), size), Image.Resampling.NEAREST)
            scale_sheet.alpha_composite(scaled, (column * 180 + xoff, yoff))
    files[EVIDENCE / "scale-readability.png"] = png_bytes(scale_sheet)

    binding_lines = []
    for index, record in enumerate(sorted(piece_records, key=lambda value: value["template_id"])):
        binding_lines.extend([
            f"binding.{index}.item-model={record['item_model']}",
            f"binding.{index}.equipment-asset={record['equipment_asset']}",
        ])
    properties = "\n".join([
        "# GENERATED from docs/development/equipment-rp2-pilot-manifest.json; do not hand-edit.",
        "schema=1", "minecraft-version=1.21.11",
        f"binding.count={len(piece_records)}",
        *binding_lines,
        "custom-equipment-assets=" + ",".join(sorted(custom_assets)),
        "custom-item-models=" + ",".join(sorted(custom_models)), "",
    ])
    files[Path("src/main/resources/equipment-rp2-pilot.properties")] = properties.encode()
    files[EVIDENCE / "index.json"] = json_bytes({
        "schema": 1, "proof_mode": "OFFLINE_DETERMINISTIC_RENDER", "human_client_staging_required": True,
        "lines": evidence_index,
        "comparison": str(EVIDENCE / "family-comparison.png"),
        "worn_reference_comparison": str(EVIDENCE / "worn-reference-comparison.png"),
        "slot_layer_separation": str(EVIDENCE / "slot-layer-separation.png"),
        "skin_compatibility": str(EVIDENCE / "skin-compatibility.png"),
        "scale_readability": str(EVIDENCE / "scale-readability.png"),
        "concept_reference": str(concept_reference),
        "concept_reference_sha256": hashlib.sha256((ROOT / concept_reference).read_bytes()).hexdigest(),
        "authored_sources": authored_source_records,
    })

    for record in piece_records:
        paths = [record["item_definition"], record["model"], record["inventory_texture"], record["equipment_definition"], *record["worn_textures"]]
        record["checksums"] = {path: hashlib.sha256(files[Path(path)]).hexdigest() for path in paths}
    complete_manifest = {
        "schema": 1, "minecraft_version": "1.21.11", "art_bible": selection["art_bible"],
        "selection_policy": selection["selection_policy"], "selected_lines": selection["selected_lines"],
        "authored_sources": authored_source_records,
        "pieces": sorted(piece_records, key=lambda value: (value["family"], SLOT_ORDER[value["slot"]])),
        "runtime_index": "src/main/resources/equipment-rp2-pilot.properties",
        "render_evidence": str(EVIDENCE / "index.json"),
        "summary": {"pilot_lines": 4, "pilot_pieces": 16, "custom_inventory": 16, "custom_worn": 16, "nonpilot_vanilla_fallback": 144, "validation": "GENERATOR_STRUCTURAL_PASS", "human_client_staging_required": True},
    }
    files[Path("docs/development/equipment-rp2-pilot-manifest.json")] = json_bytes(complete_manifest)
    return files, complete_manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    selection = json.loads(MANIFEST.read_text(encoding="utf-8"))
    art = json.loads(ART_BIBLE.read_text(encoding="utf-8"))
    files, manifest = build_files(selection, art)
    if args.write:
        for relative, content in files.items():
            target = ROOT / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
    if args.check or not args.write:
        drift = []
        for relative, content in files.items():
            target = ROOT / relative
            if not target.is_file() or target.read_bytes() != content:
                drift.append(str(relative))
        if drift:
            raise SystemExit("RP2 pilot generated asset drift: " + ", ".join(drift))
    print(f"Equipment RP2 pilot: lines={manifest['summary']['pilot_lines']} pieces={manifest['summary']['pilot_pieces']} custom-worn={manifest['summary']['custom_worn']} nonpilot-fallback={manifest['summary']['nonpilot_vanilla_fallback']}")


if __name__ == "__main__":
    main()
