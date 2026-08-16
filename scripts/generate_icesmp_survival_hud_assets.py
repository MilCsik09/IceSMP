#!/usr/bin/env python3
"""Generate HUD v2 player, target and party frames plus vanilla-bar replacements."""

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
PANEL_SIZE = (252, 72)
TARGET_PANEL_SIZE = (240, 88)
HEALTH_SEGMENTS = 20
MINI_SEGMENTS = 10
TEXT_LOGICAL_WIDTH = 6
TEXT_LOGICAL_HEIGHT = 14
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


def generate_panel(file_name: str, air_visible: bool) -> None:
    image = Image.new("RGBA", PANEL_SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((1, 1, 250, 70), radius=9,
                           fill=(4, 8, 13, 218), outline=(91, 174, 190, 210), width=1)
    draw.rounded_rectangle((4, 4, 247, 67), radius=7,
                           outline=(157, 226, 235, 55), width=1)
    draw.line((12, 37, 239, 37), fill=(91, 174, 190, 94))
    draw.line((16, 18, 235, 18), fill=(157, 226, 235, 30))
    dividers = (84, 168) if air_visible else (126,)
    for x in dividers:
        draw.line((x, 40, x, 66), fill=(91, 174, 190, 66))
    save_png(fixed_width(image), TEXTURES / file_name)


def generate_frame_panel(file_name: str, size: tuple[int, int], accent: tuple[int, int, int],
                         style: str) -> None:
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    red, green, blue = accent
    background = (8, 10, 15, 226) if style.startswith("mob") else (4, 8, 13, 220)
    draw.rounded_rectangle((1, 1, size[0] - 2, size[1] - 2), radius=8,
                           fill=background, outline=(red, green, blue, 220), width=1)
    draw.rounded_rectangle((4, 4, size[0] - 5, size[1] - 5), radius=6,
                           outline=(red, green, blue, 65), width=1)
    draw.line((12, 37, size[0] - 13, 37), fill=(red, green, blue, 92))
    draw.line((12, 55, size[0] - 13, 55), fill=(red, green, blue, 46))
    if style == "player":
        draw.line((82, 40, 82, size[1] - 7), fill=(red, green, blue, 58))
        draw.line((166, 40, 166, size[1] - 7), fill=(red, green, blue, 58))
    elif style.startswith("mob"):
        draw.polygon(((5, 18), (13, 5), (21, 18)), outline=(red, green, blue, 180))
        if style in ("mob_elite", "mob_boss"):
            draw.line((30, 5, size[0] - 30, 5), fill=(red, green, blue, 150), width=2)
    save_png(fixed_width(image), TEXTURES / file_name)


def segment(name: str, size: tuple[int, int], base: tuple[int, int, int, int],
            highlight: tuple[int, int, int, int]) -> None:
    image = Image.new("RGBA", size, base)
    draw = ImageDraw.Draw(image)
    draw.line((1, 0, size[0] - 2, 0), fill=highlight)
    draw.point((0, 0), fill=(0, 0, 0, 0))
    save_png(image, TEXTURES / name)


def generate_segments() -> None:
    segment("health_track.png", (10, 10), (12, 17, 23, 244), (62, 77, 89, 255))
    segment("health_fill.png", (10, 10), (29, 173, 104, 255), (170, 255, 205, 255))
    segment("health_warn.png", (10, 10), (202, 119, 34, 255), (255, 215, 120, 255))
    segment("health_critical.png", (10, 10), (190, 43, 48, 255), (255, 131, 112, 255))
    segment("mini_track.png", (5, 6), (11, 16, 22, 244), (62, 77, 89, 255))
    segment("mini_armor.png", (5, 6), (89, 124, 166, 255), (219, 237, 255, 255))
    segment("mini_food.png", (5, 6), (190, 119, 34, 255), (255, 220, 118, 255))
    segment("mini_air.png", (5, 6), (27, 148, 188, 255), (160, 242, 255, 255))
    segment("mini_resource.png", (5, 6), (67, 139, 160, 255), (175, 244, 255, 255))
    segment("mini_health.png", (5, 6), (29, 173, 104, 255), (170, 255, 205, 255))
    segment("mini_health_warn.png", (5, 6), (202, 119, 34, 255), (255, 215, 120, 255))
    segment("mini_health_critical.png", (5, 6), (190, 43, 48, 255), (255, 131, 112, 255))


def icon_canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (14, 14), (0, 0, 0, 0))
    return image, ImageDraw.Draw(image)


def generate_icons() -> None:
    armor, draw = icon_canvas()
    draw.polygon(((7, 1), (12, 3), (11, 9), (7, 13), (3, 9), (2, 3)),
                 fill=(167, 196, 226, 255), outline=(232, 244, 255, 255))
    draw.line((7, 2, 7, 11), fill=(101, 132, 169, 255))
    save_png(fixed_width(armor), TEXTURES / "icon_armor.png")

    food, draw = icon_canvas()
    draw.ellipse((2, 4, 11, 12), fill=(214, 145, 55, 255), outline=(255, 221, 128, 255))
    draw.line((8, 4, 11, 1), fill=(120, 190, 101, 255), width=2)
    save_png(fixed_width(food), TEXTURES / "icon_food.png")

    air, draw = icon_canvas()
    draw.ellipse((1, 5, 7, 11), fill=(57, 170, 207, 96), outline=(164, 239, 250, 255))
    draw.ellipse((7, 1, 12, 6), fill=(57, 170, 207, 96), outline=(164, 239, 250, 255))
    draw.point((3, 6), fill=(240, 255, 255, 255))
    save_png(fixed_width(air), TEXTURES / "icon_air.png")

    icon_specs = {
        "icon_player.png": (105, 207, 230),
        "icon_passive.png": (112, 207, 139),
        "icon_neutral.png": (218, 184, 85),
        "icon_hostile.png": (221, 79, 75),
        "icon_boss.png": (192, 87, 222),
    }
    for file_name, color in icon_specs.items():
        icon, draw = icon_canvas()
        draw.ellipse((3, 1, 10, 8), fill=(*color, 255), outline=(240, 248, 255, 255))
        draw.polygon(((2, 13), (3, 8), (10, 8), (12, 13)),
                     fill=(*color, 220), outline=(240, 248, 255, 255))
        if file_name == "icon_boss.png":
            draw.polygon(((2, 4), (4, 0), (7, 4), (10, 0), (12, 4)),
                         fill=(238, 194, 88, 255))
        save_png(fixed_width(icon), TEXTURES / file_name)


def generate_text_atlas() -> list[str]:
    characters = (" 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
                  "ÁÉÍÓÖŐÚÜŰáéíóöőúüű_'./+%:,-!?HP…•—◆")
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
    font = ImageFont.truetype(TEXT_FONT_SOURCE, size=10 * TEXT_OVERSAMPLE)
    _, font_descent = font.getmetrics()
    baseline_y = cell_height - font_descent - TEXT_OVERSAMPLE // 2
    stroke_width = TEXT_OVERSAMPLE // 2
    for index, char in enumerate(padded):
        x = (index % columns) * cell_width
        y = (index // columns) * cell_height
        if index < len(unique):
            box = font.getbbox(char, anchor="ls", stroke_width=stroke_width)
            width = max(0, box[2] - box[0])
            height = max(0, box[3] - box[1])
            if width > 0 and height > 0:
                glyph = Image.new("RGBA", (width, height), (0, 0, 0, 0))
                ImageDraw.Draw(glyph).text(
                    (-box[0], -box[1]), char, font=font,
                    fill=(239, 247, 252, 255), stroke_width=stroke_width,
                    stroke_fill=(2, 5, 8, 224), anchor="ls")
                maximum_width = cell_width - TEXT_OVERSAMPLE // 2
                if width > maximum_width:
                    glyph = glyph.resize((maximum_width, height), Image.Resampling.LANCZOS)
                atlas.alpha_composite(
                    glyph,
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
    themes = ("ice", "ember", "frost", "guild", "lich")
    panel_providers = []
    for index, theme in enumerate(themes):
        panel_providers.append(provider(f"player_{theme}.png", chr(0xEB00 + index),
                                        11, 15, PANEL_SIZE[1]))
        panel_providers.append(provider(f"target_player_{theme}.png", chr(0xEB05 + index),
                                        11, 15, TARGET_PANEL_SIZE[1]))
        panel_providers.append(provider(f"party_{theme}.png", chr(0xEB40 + index),
                                        11, 15, PANEL_SIZE[1]))
    for index, name in enumerate(("passive", "neutral", "hostile", "elite", "boss")):
        panel_providers.append(provider(f"target_mob_{name}.png", chr(0xEB0A + index),
                                        11, 15, TARGET_PANEL_SIZE[1]))
    write_font("panel", panel_providers)
    write_font("health_segments", [
        provider("health_track.png", chr(0xEB10), 12, 36, 10),
        provider("health_fill.png", chr(0xEB11), 13, 36, 10),
        provider("health_warn.png", chr(0xEB12), 13, 36, 10),
        provider("health_critical.png", chr(0xEB13), 13, 36, 10),
    ])
    write_font("mini_segments", [
        provider("mini_track.png", chr(0xEB20), 12, 61, 6),
        provider("mini_armor.png", chr(0xEB21), 13, 61, 6),
        provider("mini_food.png", chr(0xEB22), 13, 61, 6),
        provider("mini_air.png", chr(0xEB23), 13, 61, 6),
        provider("mini_resource.png", chr(0xEB24), 13, 61, 6),
        provider("mini_health.png", chr(0xEB25), 13, 61, 6),
        provider("mini_health_warn.png", chr(0xEB26), 13, 61, 6),
        provider("mini_health_critical.png", chr(0xEB27), 13, 61, 6),
    ])
    write_font("icons", [
        provider("icon_armor.png", chr(0xEB30), 14, 56, 14),
        provider("icon_food.png", chr(0xEB31), 14, 56, 14),
        provider("icon_air.png", chr(0xEB32), 14, 56, 14),
        provider("icon_player.png", chr(0xEB33), 14, 14, 14),
        provider("icon_passive.png", chr(0xEB34), 14, 14, 14),
        provider("icon_neutral.png", chr(0xEB35), 14, 14, 14),
        provider("icon_hostile.png", chr(0xEB36), 14, 14, 14),
        provider("icon_boss.png", chr(0xEB37), 14, 14, 14),
    ])
    write_font("player_name", [text_provider(17, text_rows)])
    write_font("text_header", [text_provider(30, text_rows)])
    write_font("text_percent", [text_provider(46, text_rows)])
    write_font("text_stats", [text_provider(81, text_rows)])
    write_font("target_header", [text_provider(17, text_rows)])
    write_font("target_status", [text_provider(30, text_rows)])
    write_font("target_health", [text_provider(57, text_rows)])
    write_font("target_stats", [text_provider(82, text_rows)])
    write_font("target_health_segments", [
        provider("health_track.png", chr(0xEB10), 12, 43, 10),
        provider("health_fill.png", chr(0xEB11), 13, 43, 10),
        provider("health_warn.png", chr(0xEB12), 13, 43, 10),
        provider("health_critical.png", chr(0xEB13), 13, 43, 10),
    ])
    write_font("target_resource_segments", [
        provider("mini_track.png", chr(0xEB20), 12, 69, 6),
        provider("mini_resource.png", chr(0xEB24), 13, 69, 6),
    ])
    write_font("party_header", [text_provider(17, text_rows)])
    write_font("party_health_text", [text_provider(39, text_rows)])
    write_font("party_status", [text_provider(59, text_rows)])
    write_font("party_health_segments", [
        provider("mini_track.png", chr(0xEB20), 12, 31, 6),
        provider("mini_health.png", chr(0xEB25), 13, 31, 6),
        provider("mini_health_warn.png", chr(0xEB26), 13, 31, 6),
        provider("mini_health_critical.png", chr(0xEB27), 13, 31, 6),
    ])
    write_font("party_resource_segments", [
        provider("mini_track.png", chr(0xEB20), 12, 51, 6),
        provider("mini_resource.png", chr(0xEB24), 13, 51, 6),
    ])


def generate_vanilla_replacements() -> None:
    transparent = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for name in HEART_SPRITES:
        save_png(transparent, VANILLA_HUD / "heart" / name)
    for name in ARMOR_SPRITES + FOOD_SPRITES + AIR_SPRITES:
        save_png(transparent, VANILLA_HUD / name)


def generate_preview(air_visible: bool, target_name: str) -> None:
    scale = 3
    panel_name = "panel_air.png" if air_visible else "panel.png"
    panel = Image.open(TEXTURES / panel_name).convert("RGBA")
    canvas = Image.new("RGBA", (PANEL_SIZE[0] * scale, PANEL_SIZE[1] * scale),
                       (20, 27, 35, 255))
    canvas.alpha_composite(panel.resize(canvas.size, Image.Resampling.NEAREST), (0, 0))
    for index in range(HEALTH_SEGMENTS):
        name = "health_fill.png" if index < 15 else "health_track.png"
        segment_image = Image.open(TEXTURES / name).convert("RGBA").resize(
            (10 * scale, 10 * scale), Image.Resampling.NEAREST)
        canvas.alpha_composite(segment_image, ((16 + index * 11) * scale, 21 * scale))
    groups = [(None, 0, "icon_armor.png", "18"),
              ("mini_food.png", 7, "icon_food.png", "14/20")]
    centers = [63, 189]
    if air_visible:
        groups.append(("mini_air.png", 5, "icon_air.png", "150/300"))
        centers = [42, 126, 210]
    for center, (fill, active, icon_name, value) in zip(centers, groups):
        icon = Image.open(TEXTURES / icon_name).convert("RGBA").resize(
            (14 * scale, 14 * scale), Image.Resampling.NEAREST)
        canvas.alpha_composite(icon, ((center - 38) * scale, 41 * scale))
        if fill is not None:
            for index in range(MINI_SEGMENTS):
                name = fill if index < active else "mini_track.png"
                segment_image = Image.open(TEXTURES / name).convert("RGBA").resize(
                    (5 * scale, 6 * scale), Image.Resampling.NEAREST)
                canvas.alpha_composite(segment_image,
                                       ((center - 22 + index * 6) * scale, 44 * scale))
    font_path = ROOT / "dev-assets" / "icesmp-hud" / "source" / "Inter-SemiBold.ttf"
    font = ImageFont.truetype(font_path, 10 * scale)
    small = ImageFont.truetype(font_path, 8 * scale)
    draw = ImageDraw.Draw(canvas)
    draw.text((PANEL_SIZE[0] * scale // 2, 3 * scale), "87.5 / 120 HP (+4)", font=font,
              fill=(154, 242, 194, 255), stroke_width=scale,
              stroke_fill=(2, 5, 8, 224), anchor="ma")
    draw.text((PANEL_SIZE[0] * scale // 2, 21 * scale), "73%", font=font,
              fill=(247, 251, 255, 255), stroke_width=scale,
              stroke_fill=(2, 5, 8, 224), anchor="ma")
    for center, (_, _, _, value) in zip(centers, groups):
        draw.text(((center + 8) * scale, 56 * scale), value, font=small,
                  fill=(218, 232, 242, 255), stroke_width=scale,
                  stroke_fill=(2, 5, 8, 224), anchor="ma")
    target = ROOT / "build" / "reports" / "icesmp-hud" / target_name
    save_png(canvas, target)


def main() -> None:
    palette = {
        "ice": (91, 174, 190), "ember": (207, 86, 59), "frost": (98, 206, 231),
        "guild": (203, 164, 69), "lich": (79, 198, 190),
    }
    for theme, accent in palette.items():
        generate_frame_panel(f"player_{theme}.png", PANEL_SIZE, accent, "player")
        generate_frame_panel(f"target_player_{theme}.png", TARGET_PANEL_SIZE, accent, "target")
        generate_frame_panel(f"party_{theme}.png", PANEL_SIZE, accent, "party")
    mob_palette = {
        "passive": (94, 185, 118), "neutral": (203, 166, 67),
        "hostile": (201, 67, 61), "elite": (220, 158, 55), "boss": (178, 70, 207),
    }
    for kind, accent in mob_palette.items():
        generate_frame_panel(f"target_mob_{kind}.png", TARGET_PANEL_SIZE, accent,
                             f"mob_{kind}")
    generate_segments()
    generate_icons()
    generate_fonts(generate_text_atlas())
    generate_vanilla_replacements()
    manifest = {
        "version": 2,
        "module": "frames",
        "anchor": "top_left",
        "canvas_height": SURVIVAL_CANVAS_HEIGHT,
        "panel_size": list(PANEL_SIZE),
        "target_panel_size": list(TARGET_PANEL_SIZE),
        "player_frame_themes": list(palette),
        "target_player_themes": list(palette),
        "target_mob_styles": list(mob_palette),
        "party_frame_themes": list(palette),
        "party_max_rows": 4,
        "health_segments": HEALTH_SEGMENTS,
        "mini_segments": MINI_SEGMENTS,
        "armor_display": "flat_value",
        "air_display": "only_when_depleted",
        "default_scale": 1.0,
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
    generate_preview(False, "survival-preview.png")
    generate_preview(True, "survival-air-preview.png")


if __name__ == "__main__":
    main()
