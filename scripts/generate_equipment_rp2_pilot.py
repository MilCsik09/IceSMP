#!/usr/bin/env python3
"""Generate the four-family RP2-B pilot assets, runtime index and offline visual evidence."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
from typing import Any, Callable

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ART_BIBLE = ROOT / "docs/development/equipment-rp2-art-bible.json"
MANIFEST = ROOT / "docs/development/equipment-rp2-pilot-manifest.json"
EVIDENCE = Path("docs/development/equipment-rp2-render-evidence")
SLOT_ORDER = {"HEAD": 0, "CHEST": 1, "LEGS": 2, "FEET": 3}


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


def logical_canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    return image, ImageDraw.Draw(image)


def polygon(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], fill: tuple[int, ...],
            outline: tuple[int, ...] | None = None) -> None:
    draw.polygon(points, fill=fill)
    if outline:
        draw.line(points + [points[0]], fill=outline, width=1)


def add_icon_material(draw: ImageDraw.ImageDraw, family: str, slot: str,
                      palette: list[tuple[int, int, int, int]]) -> None:
    dark, base, mid, light, secondary, accent = palette
    if family == "CLOTH":
        if slot == "HEAD":
            polygon(draw, [(8, 23), (7, 10), (11, 4), (16, 2), (21, 4), (25, 10), (24, 23), (20, 27), (12, 27)], base, dark)
            polygon(draw, [(11, 20), (11, 11), (14, 7), (18, 7), (21, 11), (21, 20), (18, 22), (14, 22)], (0, 0, 0, 0))
            draw.line((9, 23, 16, 28, 23, 23), fill=secondary)
        elif slot == "CHEST":
            polygon(draw, [(10, 4), (22, 4), (27, 10), (24, 15), (22, 12), (23, 29), (17, 27), (16, 30), (15, 27), (9, 29), (10, 12), (8, 15), (5, 10)], base, dark)
            draw.line((15, 5, 15, 27), fill=mid)
            draw.line((18, 5, 18, 27), fill=light)
        elif slot == "LEGS":
            polygon(draw, [(9, 3), (23, 3), (24, 11), (21, 29), (16, 26), (11, 29), (8, 11)], base, dark)
            draw.line((16, 4, 16, 27), fill=light)
            draw.line((11, 9, 21, 9), fill=secondary)
        else:
            polygon(draw, [(7, 11), (15, 9), (16, 18), (13, 25), (5, 25), (4, 21)], base, dark)
            polygon(draw, [(17, 9), (25, 11), (28, 21), (27, 25), (19, 25), (16, 18)], base, dark)
            draw.line((6, 20, 14, 20), fill=light)
            draw.line((18, 20, 26, 20), fill=light)
    elif family == "LEATHER":
        if slot == "HEAD":
            polygon(draw, [(7, 22), (8, 9), (12, 4), (20, 4), (24, 9), (25, 22), (20, 26), (12, 26)], base, dark)
            polygon(draw, [(11, 18), (11, 10), (14, 7), (20, 9), (21, 18), (18, 21), (14, 21)], (0, 0, 0, 0))
            draw.line((8, 10, 24, 20), fill=secondary)
        elif slot == "CHEST":
            polygon(draw, [(10, 5), (22, 5), (27, 10), (24, 14), (22, 12), (22, 27), (17, 29), (10, 26), (10, 12), (8, 14), (5, 10)], base, dark)
            polygon(draw, [(10, 7), (14, 5), (22, 16), (22, 21), (19, 21)], mid, dark)
            draw.line((8, 21, 23, 9), fill=secondary)
        elif slot == "LEGS":
            polygon(draw, [(9, 4), (23, 4), (24, 10), (21, 29), (16, 27), (11, 29), (8, 10)], base, dark)
            draw.line((9, 9, 22, 18), fill=secondary)
            draw.line((11, 20, 20, 13), fill=light)
        else:
            polygon(draw, [(6, 10), (15, 8), (16, 18), (13, 27), (5, 27), (3, 22)], base, dark)
            polygon(draw, [(17, 8), (26, 10), (29, 22), (27, 27), (19, 27), (16, 18)], base, dark)
            draw.line((5, 17, 14, 13), fill=secondary)
            draw.line((18, 13, 27, 17), fill=secondary)
    elif family == "MAIL":
        if slot == "HEAD":
            polygon(draw, [(7, 23), (7, 8), (11, 3), (21, 3), (25, 8), (25, 23), (21, 27), (11, 27)], base, dark)
            polygon(draw, [(11, 19), (11, 9), (14, 7), (20, 7), (21, 19), (18, 22), (14, 22)], (0, 0, 0, 0))
        elif slot == "CHEST":
            polygon(draw, [(9, 4), (23, 4), (28, 9), (25, 14), (23, 12), (23, 27), (19, 29), (13, 29), (9, 27), (9, 12), (7, 14), (4, 9)], base, dark)
            draw.rectangle((11, 5, 21, 10), fill=mid)
        elif slot == "LEGS":
            polygon(draw, [(8, 3), (24, 3), (25, 11), (22, 29), (17, 28), (15, 28), (10, 29), (7, 11)], base, dark)
            draw.rectangle((9, 4, 23, 9), fill=mid)
        else:
            polygon(draw, [(5, 10), (15, 8), (16, 18), (13, 27), (4, 27), (3, 20)], base, dark)
            polygon(draw, [(17, 8), (27, 10), (29, 20), (28, 27), (19, 27), (16, 18)], base, dark)
        for y in range(6, 27, 3):
            for x in range(6 + (y // 3) % 2, 28, 4):
                if draw._image.getpixel((x, y))[3]:
                    draw.point((x, y), fill=light)
                    if x + 1 < 32 and draw._image.getpixel((x + 1, y))[3]:
                        draw.point((x + 1, y), fill=dark)
        draw.rectangle((15, 11, 16, 12), fill=secondary)
    else:
        if slot == "HEAD":
            polygon(draw, [(6, 23), (7, 6), (11, 2), (21, 2), (25, 6), (26, 23), (21, 28), (11, 28)], base, dark)
            draw.rectangle((9, 11, 23, 15), fill=dark)
            draw.line((10, 12, 22, 12), fill=light)
        elif slot == "CHEST":
            polygon(draw, [(8, 3), (24, 3), (30, 9), (27, 15), (24, 13), (25, 28), (18, 30), (14, 30), (7, 28), (8, 13), (5, 15), (2, 9)], base, dark)
            draw.rectangle((9, 6, 23, 23), outline=light, width=1)
            draw.rectangle((12, 9, 20, 19), fill=mid, outline=dark)
        elif slot == "LEGS":
            polygon(draw, [(7, 3), (25, 3), (27, 11), (23, 30), (17, 28), (15, 28), (9, 30), (5, 11)], base, dark)
            draw.rectangle((8, 5, 24, 11), outline=light)
            draw.line((16, 11, 16, 28), fill=dark)
        else:
            polygon(draw, [(4, 9), (15, 7), (16, 17), (14, 29), (3, 29), (1, 21)], base, dark)
            polygon(draw, [(17, 7), (28, 9), (31, 21), (29, 29), (18, 29), (16, 17)], base, dark)
            draw.rectangle((4, 18, 14, 26), outline=light)
            draw.rectangle((18, 18, 28, 26), outline=light)
        for x, y in ((9, 7), (22, 7), (8, 23), (23, 23)):
            draw.point((x, y), fill=secondary)
        draw.rectangle((15, 15, 17, 18), fill=accent)


def generate_icon(line: dict[str, Any], slot: str) -> Image.Image:
    image, draw = logical_canvas()
    palette = line_palette(line)
    add_icon_material(draw, line["family"], slot, palette)
    _, _, _, light, secondary, accent = palette
    if line["canonical_line_id"] == "holdlen":
        draw.arc((12, 12, 20, 20), 45, 305, fill=light)
        draw.point((18, 15), fill=accent)
    elif line["canonical_line_id"] == "vadbor":
        draw.line((9, 22, 22, 9), fill=secondary, width=1)
        for offset in (0, 4, 8):
            draw.point((11 + offset, 20 - offset), fill=light)
    elif line["canonical_line_id"] == "konnyu_otvozet":
        draw.rectangle((15, 14, 17, 16), fill=secondary)
        draw.point((16, 15), fill=accent)
    elif line["canonical_line_id"] == "borostyan_tarna":
        draw.rectangle((13, 12, 19, 19), fill=secondary, outline=light)
        draw.rectangle((15, 14, 17, 17), fill=accent)
    opaque = [(x, y) for y in range(32) for x in range(32) if image.getpixel((x, y))[3]]
    for index, value in enumerate(palette):
        if index < len(opaque):
            draw.point(opaque[index * max(1, len(opaque) // len(palette))], fill=value)
    return image.resize((64, 64), Image.Resampling.NEAREST)


def rect(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], fill: tuple[int, ...]) -> None:
    draw.rectangle((box[0], box[1], box[2] - 1, box[3] - 1), fill=fill)


def fill_uv_faces(draw: ImageDraw.ImageDraw, boxes: list[tuple[int, int, int, int]],
                  palette: list[tuple[int, int, int, int]]) -> None:
    dark, base, mid, light, _, _ = palette
    for index, box in enumerate(boxes):
        rect(draw, box, (light, dark, base, mid)[index % 4])


def worn_textures(line: dict[str, Any]) -> tuple[Image.Image, Image.Image]:
    palette = line_palette(line)
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

    family = line["family"]
    if family in {"CLOTH", "LEATHER", "MAIL"}:
        rect(dm, (10, 10, 14, 15), (0, 0, 0, 0))
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
    return main, legs


FACE_UV = {
    "front": {"head": (8, 8, 16, 16), "body": (20, 20, 28, 32), "arm": (44, 20, 48, 32), "leg": (4, 20, 8, 32)},
    "back": {"head": (24, 8, 32, 16), "body": (32, 20, 40, 32), "arm": (52, 20, 56, 32), "leg": (12, 20, 16, 32)},
    "side": {"head": (0, 8, 8, 16), "body": (16, 20, 20, 32), "arm": (40, 20, 44, 32), "leg": (0, 20, 4, 32)},
}


def face(texture: Image.Image, box: tuple[int, int, int, int], size: tuple[int, int]) -> Image.Image:
    return texture.crop(box).resize(size, Image.Resampling.NEAREST)


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


def build_files(selection: dict[str, Any], art: dict[str, Any]) -> tuple[dict[Path, bytes], dict[str, Any]]:
    by_id = {line["canonical_line_id"]: line for line in art["gear_lines"]}
    selected = selection["selected_lines"]
    if len(selected) != 4 or {entry["family"] for entry in selected} != {"CLOTH", "LEATHER", "MAIL", "PLATE"}:
        raise SystemExit("Pilot manifest must select exactly one line per family")
    files: dict[Path, bytes] = {}
    piece_records: list[dict[str, Any]] = []
    family_front: dict[str, Image.Image] = {}
    evidence_index: list[dict[str, Any]] = []
    custom_assets: list[str] = []
    custom_models: list[str] = []
    concept_reference = EVIDENCE / "concept-reference.png"
    if not (ROOT / concept_reference).is_file():
        raise SystemExit("Missing authored AI concept reference: " + str(concept_reference))

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
                "equipment_asset": equipment_asset, "equipment_definition": str(equipment_path),
                "worn_textures": [str(humanoid_path), str(leggings_path)],
                "fallback_status": "RP2_CUSTOM", "validation_status": "GENERATOR_STRUCTURAL_PASS",
            })

        views = {view: mannequin(main, leggings, view, (177, 132, 104, 255)) for view in ("front", "back", "side")}
        family_front[line["family"]] = views["front"]
        inventory = inventory_sheet(line, icons)
        line_evidence = []
        for name, image in {"inventory": inventory, **views}.items():
            path = EVIDENCE / f"{line_id}-{name}.png"
            files[path] = png_bytes(image)
            line_evidence.append(str(path))
        evidence_index.append({"line_id": line_id, "family": line["family"], "proof_mode": "OFFLINE_DETERMINISTIC_RENDER", "files": line_evidence})

    files[EVIDENCE / "family-comparison.png"] = png_bytes(comparison_sheet(family_front))
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
        "skin_compatibility": str(EVIDENCE / "skin-compatibility.png"),
        "scale_readability": str(EVIDENCE / "scale-readability.png"),
        "concept_reference": str(concept_reference),
        "concept_reference_sha256": hashlib.sha256((ROOT / concept_reference).read_bytes()).hexdigest(),
    })

    for record in piece_records:
        paths = [record["item_definition"], record["model"], record["inventory_texture"], record["equipment_definition"], *record["worn_textures"]]
        record["checksums"] = {path: hashlib.sha256(files[Path(path)]).hexdigest() for path in paths}
    complete_manifest = {
        "schema": 1, "minecraft_version": "1.21.11", "art_bible": selection["art_bible"],
        "selection_policy": selection["selection_policy"], "selected_lines": selection["selected_lines"],
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
