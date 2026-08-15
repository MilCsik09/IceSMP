#!/usr/bin/env python3
"""Generate the isolated survival HUD module and its vanilla-sprite replacements."""

import io
import json
import os
import sys
import time
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ModuleNotFoundError:
    print("A survival HUD generálásához a Pillow csomag szükséges.", file=sys.stderr)
    raise SystemExit(2)

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "resource-pack" / "assets" / "icesmp_hud"
TEXTURES = ASSETS / "textures" / "hud" / "survival"
FONTS = ASSETS / "font" / "survival"
VANILLA_HUD = (ROOT / "resource-pack" / "assets" / "minecraft" / "textures"
               / "gui" / "sprites" / "hud")

HUD_BIT = 13
HUD_MAX_BIT = 10
HUD_ADD_HEIGHT = 4095
SURVIVAL_CANVAS_HEIGHT = 120
PANEL_SIZE = (228, 60)
HEALTH_SEGMENTS = 20
MINI_SEGMENTS = 10
TEXT_LOGICAL_WIDTH = 5
TEXT_LOGICAL_HEIGHT = 12
TEXT_OVERSAMPLE = 8
TEXT_FONT_SOURCE = ROOT / "dev-assets" / "icesmp-hud" / "source" / "Inter-SemiBold.ttf"

HEART_SPRITES = (
    "absorbing_full.png", "absorbing_full_blinking.png",
    "absorbing_half.png", "absorbing_half_blinking.png",
    "container.png", "container_blinking.png",
    "frozen_full.png", "frozen_full_blinking.png",
    "frozen_half.png", "frozen_half_blinking.png",
    "full.png", "full_blinking.png", "half.png", "half_blinking.png",
    "poisoned_full.png", "poisoned_full_blinking.png",
    "poisoned_half.png", "poisoned_half_blinking.png",
    "withered_full.png", "withered_full_blinking.png",
    "withered_half.png", "withered_half_blinking.png",
)
ARMOR_SPRITES = ("armor_empty.png", "armor_full.png", "armor_half.png")
FOOD_SPRITES = (
    "food_empty.png", "food_empty_hunger.png",
    "food_full.png", "food_full_hunger.png",
    "food_half.png", "food_half_hunger.png",
)
AIR_SPRITES = ("air.png", "air_bursting.png", "air_empty.png")


def encoded_ascent(shader_id: int, y: int) -> int:
    return -(((shader_id + (1 << HUD_MAX_BIT)) << HUD_BIT) + HUD_ADD_HEIGHT + y)


def save_png(image: Image.Image, path: Path) -> None:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    payload = buffer.getvalue()
    if path.is_file() and path.read_bytes() == payload:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_bytes(payload)
    try:
        for attempt in range(8):
            try:
                os.replace(temporary, path)
                return
            except OSError:
                if attempt == 7:
                    raise
                time.sleep(0.05 * (attempt + 1))
    finally:
        temporary.unlink(missing_ok=True)


def write_font(name: str, providers: list[dict]) -> None:
    path = FONTS / f"{name}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8")


def provider(file_name: str, char: str, shader_id: int, y: int, height: int) -> dict:
    return {
        "type": "bitmap",
        "file": f"icesmp_hud:hud/survival/{file_name}",
        "ascent": encoded_ascent(shader_id, y),
        "height": height,
        "chars": [char],
    }


def fixed_width(image: Image.Image) -> Image.Image:
    image.putpixel((image.width - 1, image.height - 1), (255, 255, 255, 1))
    return image


def generate_panel() -> None:
    image = Image.new("RGBA", PANEL_SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((1, 1, 226, 58), radius=8,
                           fill=(4, 8, 13, 196), outline=(91, 174, 190, 178), width=1)
    draw.rounded_rectangle((4, 4, 223, 55), radius=6,
                           outline=(157, 226, 235, 38), width=1)
    draw.line((18, 35, 209, 35), fill=(91, 174, 190, 70))
    draw.line((76, 39, 76, 55), fill=(91, 174, 190, 52))
    draw.line((151, 39, 151, 55), fill=(91, 174, 190, 52))
    save_png(fixed_width(image), TEXTURES / "panel.png")


def segment(name: str, size: tuple[int, int], base: tuple[int, int, int, int],
            highlight: tuple[int, int, int, int]) -> None:
    image = Image.new("RGBA", size, base)
    draw = ImageDraw.Draw(image)
    draw.line((1, 0, size[0] - 2, 0), fill=highlight)
    draw.point((0, 0), fill=(0, 0, 0, 0))
    save_png(image, TEXTURES / name)


def generate_segments() -> None:
    segment("health_track.png", (8, 8), (12, 17, 23, 238), (55, 68, 79, 255))
    segment("health_fill.png", (8, 8), (36, 170, 107, 255), (157, 248, 198, 255))
    segment("health_warn.png", (8, 8), (193, 116, 38, 255), (255, 205, 113, 255))
    segment("health_critical.png", (8, 8), (184, 48, 48, 255), (255, 125, 106, 255))
    segment("mini_track.png", (4, 5), (11, 16, 22, 238), (55, 68, 79, 255))
    segment("mini_armor.png", (4, 5), (93, 122, 158, 255), (211, 229, 246, 255))
    segment("mini_food.png", (4, 5), (184, 119, 42, 255), (250, 213, 116, 255))
    segment("mini_air.png", (4, 5), (34, 145, 181, 255), (151, 239, 250, 255))


def icon_canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
    return image, ImageDraw.Draw(image)


def generate_icons() -> None:
    armor, draw = icon_canvas()
    draw.polygon(((6, 1), (10, 3), (9, 8), (6, 11), (3, 8), (2, 3)),
                 fill=(167, 196, 226, 255), outline=(232, 244, 255, 255))
    draw.line((6, 2, 6, 9), fill=(101, 132, 169, 255))
    save_png(fixed_width(armor), TEXTURES / "icon_armor.png")

    food, draw = icon_canvas()
    draw.ellipse((2, 3, 9, 10), fill=(214, 145, 55, 255), outline=(255, 221, 128, 255))
    draw.line((7, 3, 9, 1), fill=(120, 190, 101, 255), width=2)
    save_png(fixed_width(food), TEXTURES / "icon_food.png")

    air, draw = icon_canvas()
    draw.ellipse((1, 4, 6, 9), fill=(57, 170, 207, 96), outline=(164, 239, 250, 255))
    draw.ellipse((6, 1, 10, 5), fill=(57, 170, 207, 96), outline=(164, 239, 250, 255))
    draw.point((3, 5), fill=(240, 255, 255, 255))
    save_png(fixed_width(air), TEXTURES / "icon_air.png")


def generate_text_atlas() -> list[str]:
    characters = " 0123456789./()+%HP…"
    unique = "".join(dict.fromkeys(characters))
    columns = 16
    rows = (len(unique) + columns - 1) // columns
    padded = unique + "".join(
        chr(0xEC00 + index) for index in range(rows * columns - len(unique)))
    cell_width = TEXT_LOGICAL_WIDTH * TEXT_OVERSAMPLE
    cell_height = TEXT_LOGICAL_HEIGHT * TEXT_OVERSAMPLE
    atlas = Image.new("RGBA", (columns * cell_width, rows * cell_height), (0, 0, 0, 0))
    if not TEXT_FONT_SOURCE.is_file():
        raise FileNotFoundError(f"Missing reproducible survival HUD font: {TEXT_FONT_SOURCE}")
    font = ImageFont.truetype(TEXT_FONT_SOURCE, size=9 * TEXT_OVERSAMPLE)
    _, font_descent = font.getmetrics()
    baseline_y = cell_height - font_descent - TEXT_OVERSAMPLE // 2
    for index, char in enumerate(padded):
        x = (index % columns) * cell_width
        y = (index // columns) * cell_height
        if index < len(unique):
            box = font.getbbox(char, anchor="ls")
            width = max(0, box[2] - box[0])
            height = max(0, box[3] - box[1])
            if width > 0 and height > 0:
                glyph = Image.new("L", (width, height), 0)
                ImageDraw.Draw(glyph).text(
                    (-box[0], -box[1]), char, font=font, fill=255, anchor="ls")
                maximum_width = cell_width - TEXT_OVERSAMPLE // 2
                if width > maximum_width:
                    glyph = glyph.resize((maximum_width, height), Image.Resampling.LANCZOS)
                glyph = glyph.point(lambda alpha: min(255, round(alpha * 1.18)))
                colored = Image.new("RGBA", glyph.size, (239, 247, 252, 255))
                colored.putalpha(glyph)
                atlas.alpha_composite(
                    colored,
                    (x + (cell_width - glyph.width) // 2, y + baseline_y + box[1]))
        atlas.putpixel((x + cell_width - 1, y + cell_height - 1), (255, 255, 255, 1))
    save_png(atlas, TEXTURES / "text-atlas.png")
    return [padded[row * columns:(row + 1) * columns] for row in range(rows)]


def text_provider(y: int, rows: list[str]) -> dict:
    return {
        "type": "bitmap",
        "file": "icesmp_hud:hud/survival/text-atlas.png",
        "ascent": encoded_ascent(15, y - 9),
        "height": TEXT_LOGICAL_HEIGHT,
        "chars": rows,
    }


def generate_fonts(text_rows: list[str]) -> None:
    write_font("panel", [provider("panel.png", chr(0xEB00), 11, 15, PANEL_SIZE[1])])
    write_font("health_segments", [
        provider("health_track.png", chr(0xEB10), 12, 32, 8),
        provider("health_fill.png", chr(0xEB11), 13, 32, 8),
        provider("health_warn.png", chr(0xEB12), 13, 32, 8),
        provider("health_critical.png", chr(0xEB13), 13, 32, 8),
    ])
    write_font("mini_segments", [
        provider("mini_track.png", chr(0xEB20), 12, 52, 5),
        provider("mini_armor.png", chr(0xEB21), 13, 52, 5),
        provider("mini_food.png", chr(0xEB22), 13, 52, 5),
        provider("mini_air.png", chr(0xEB23), 13, 52, 5),
    ])
    write_font("icons", [
        provider("icon_armor.png", chr(0xEB30), 14, 48, 12),
        provider("icon_food.png", chr(0xEB31), 14, 48, 12),
        provider("icon_air.png", chr(0xEB32), 14, 48, 12),
    ])
    write_font("text_header", [text_provider(27, text_rows)])
    write_font("text_percent", [text_provider(41, text_rows)])
    write_font("text_stats", [text_provider(69, text_rows)])


def generate_vanilla_replacements() -> None:
    transparent = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for name in HEART_SPRITES:
        save_png(transparent, VANILLA_HUD / "heart" / name)
    for name in ARMOR_SPRITES + FOOD_SPRITES + AIR_SPRITES:
        save_png(transparent, VANILLA_HUD / name)


def generate_preview() -> None:
    scale = 3
    panel = Image.open(TEXTURES / "panel.png").convert("RGBA")
    canvas = Image.new("RGBA", (PANEL_SIZE[0] * scale, PANEL_SIZE[1] * scale),
                       (20, 27, 35, 255))
    canvas.alpha_composite(panel.resize(canvas.size, Image.Resampling.NEAREST), (0, 0))
    for index in range(HEALTH_SEGMENTS):
        name = "health_fill.png" if index < 15 else "health_track.png"
        segment_image = Image.open(TEXTURES / name).convert("RGBA").resize(
            (8 * scale, 8 * scale), Image.Resampling.NEAREST)
        canvas.alpha_composite(segment_image, ((24 + index * 9) * scale, 17 * scale))
    for group, (fill, active) in enumerate((("mini_armor.png", 8), ("mini_food.png", 7),
                                            ("mini_air.png", 10))):
        icon_name = ("icon_armor.png", "icon_food.png", "icon_air.png")[group]
        icon = Image.open(TEXTURES / icon_name).convert("RGBA").resize(
            (12 * scale, 12 * scale), Image.Resampling.NEAREST)
        canvas.alpha_composite(icon, ((7 + group * 63) * scale, 35 * scale))
        for index in range(MINI_SEGMENTS):
            name = fill if index < active else "mini_track.png"
            segment_image = Image.open(TEXTURES / name).convert("RGBA").resize(
                (4 * scale, 5 * scale), Image.Resampling.NEAREST)
            canvas.alpha_composite(segment_image,
                                   ((23 + group * 63 + index * 5) * scale, 37 * scale))
    font_path = ROOT / "dev-assets" / "icesmp-hud" / "source" / "Inter-SemiBold.ttf"
    font = ImageFont.truetype(font_path, 8 * scale)
    small = ImageFont.truetype(font_path, 6 * scale)
    draw = ImageDraw.Draw(canvas)
    draw.text((PANEL_SIZE[0] * scale // 2, 2 * scale), "87.5 / 120 HP (+4)", font=font,
              fill=(154, 242, 194, 255), anchor="ma")
    draw.text((PANEL_SIZE[0] * scale // 2, 18 * scale), "73%", font=font,
              fill=(247, 251, 255, 255), anchor="ma")
    for x, value in ((48, "18/20"), (111, "14/20"), (174, "300/300")):
        draw.text((x * scale, 45 * scale), value, font=small,
                  fill=(218, 232, 242, 255), anchor="ma")
    target = ROOT / "build" / "reports" / "icesmp-hud" / "survival-preview.png"
    save_png(canvas, target)


def main() -> None:
    generate_panel()
    generate_segments()
    generate_icons()
    generate_fonts(generate_text_atlas())
    generate_vanilla_replacements()
    manifest = {
        "version": 1,
        "module": "survival",
        "anchor": "bottom_center",
        "canvas_height": SURVIVAL_CANVAS_HEIGHT,
        "panel_size": list(PANEL_SIZE),
        "health_segments": HEALTH_SEGMENTS,
        "mini_segments": MINI_SEGMENTS,
        "text_font": "Inter SemiBold",
        "text_oversample": TEXT_OVERSAMPLE,
        "text_atlas": "icesmp_hud:hud/survival/text-atlas.png",
        "vanilla_health_hidden": True,
        "vanilla_armor_hidden": True,
        "vanilla_food_hidden": True,
        "vanilla_oxygen_hidden": True,
        "hardcore_hearts_overridden": False,
        "heart_sprites": list(HEART_SPRITES),
        "armor_sprites": list(ARMOR_SPRITES),
        "food_sprites": list(FOOD_SPRITES),
        "air_sprites": list(AIR_SPRITES),
    }
    (ASSETS / "survival-hud-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    generate_preview()


if __name__ == "__main__":
    main()
