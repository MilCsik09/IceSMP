#!/usr/bin/env python3
"""Build the complete custom class UI family and specialization badge font."""

import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "dev-assets" / "icesmp-class-ui" / "source" / "faction-ornaments-v1.png"
SIGIL_SOURCE = ROOT / "dev-assets" / "icesmp-class-ui" / "source" / "specialization-sigils-v1.png"
TEXTURES = ROOT / "resource-pack" / "assets" / "icesmp_hud" / "textures" / "class_ui"
FONT = ROOT / "resource-pack" / "assets" / "icesmp_hud" / "font" / "class_ui.json"
BADGE_FONT = ROOT / "resource-pack" / "assets" / "icesmp_hud" / "font" / "specialization_badge.json"
CLASS_BADGE_FONT = ROOT / "resource-pack" / "assets" / "icesmp_hud" / "font" / "class_badge.json"
REPORT = ROOT / "build" / "reports" / "class-ui" / "contact-sheet.png"
BADGE_REPORT = ROOT / "build" / "reports" / "class-ui" / "specialization-badges.png"

THEMES = {
    "red": ((0, 0), (79, 20, 17), (239, 75, 46)),
    "blue": ((1, 0), (13, 47, 67), (73, 195, 246)),
    "neutral": ((0, 1), (50, 47, 32), (218, 175, 73)),
    "dark": ((1, 1), (42, 25, 58), (169, 85, 232)),
}

SURFACES = (
    "workshop", "profile", "class_select", "spellbook",
    "skill_tree", "talents", "detail", "companion",
)

# The AI source is a strict 7x5 visual atlas. The mapping deliberately assigns the
# closest readable silhouette to every canonical Profile v2 specialization id.
SPEC_BADGES = {
    "berserker": 0, "retribution": 1, "necromancer": 2, "beast_master": 3,
    "phantom": 4, "holy": 5, "elemental": 6, "elementalist": 7,
    "demonologist": 8, "windwalker": 9, "ironbark": 10, "havoc": 11,
    "devastation": 12, "blood": 13, "frost": 14, "plaguebringer": 15,
    "discipline": 16, "protection": 17, "guardian": 18, "feral": 19,
    "sharpshooter": 20, "shadow": 21, "tidal": 22, "enhancement": 23,
    "restoration": 24, "lunar": 25, "affliction": 26, "bone_priest": 27,
    "destruction": 28, "brewmaster": 29, "vengeance": 30,
    "mistweaver": 31, "preservation": 32, "unholy": 33,
    "poisoner": 34,
}

CLASS_BADGES = (
    "warrior", "evoker", "archer", "shaman", "monk", "paladin",
    "demon_hunter", "druid", "priest", "death_knight", "assassin", "warlock", "wizard",
)


def transparent_ornaments(source: Image.Image) -> Image.Image:
    rgba = source.convert("RGBA")
    pixels = rgba.load()
    for y in range(rgba.height):
        for x in range(rgba.width):
            red, green, blue, _ = pixels[x, y]
            chroma = max(red, green, blue) - min(red, green, blue)
            alpha = 0 if min(red, green, blue) >= 225 and chroma <= 10 else 255
            pixels[x, y] = red, green, blue, alpha
    return rgba


def slot_grid(draw: ImageDraw.ImageDraw, accent: tuple[int, int, int]) -> None:
    track = (18, 22, 28, 230)
    edge = (*accent, 105)
    for row in range(6):
        for column in range(9):
            x, y = 7 + column * 18, 17 + row * 18
            draw.rectangle((x, y, x + 17, y + 17), fill=track, outline=edge)
            draw.line((x + 1, y + 1, x + 15, y + 1), fill=(92, 104, 116, 105))
    for row in range(3):
        for column in range(9):
            x, y = 7 + column * 18, 139 + row * 18
            draw.rectangle((x, y, x + 17, y + 17), fill=track, outline=edge)
    for column in range(9):
        x, y = 7 + column * 18, 197
        draw.rectangle((x, y, x + 17, y + 17), fill=track, outline=edge)


def build_background(ornaments: Image.Image, base: tuple[int, int, int],
                     accent: tuple[int, int, int], surface_index: int) -> Image.Image:
    image = Image.new("RGBA", (176, 222), (*base, 255))
    draw = ImageDraw.Draw(image, "RGBA")
    draw.rectangle((1, 1, 174, 220), fill=(9, 12, 17, 242), outline=(*accent, 230), width=2)
    draw.rectangle((4, 4, 171, 217), outline=(135, 151, 166, 115))
    separator_y = (130, 112, 76, 184, 130, 94, 148, 166)[surface_index]
    draw.rectangle((5, separator_y, 170, separator_y + 4), fill=(*accent, 48))
    slot_grid(draw, accent)
    frame = ornaments.resize((176, 176), Image.Resampling.LANCZOS)
    image.alpha_composite(frame, (0, -4))
    draw.rectangle((66, 5, 109, 13), fill=(8, 11, 16, 225), outline=(*accent, 170))
    # Screen-specific corner ticks make each surface recognizable without text.
    for tick in range(surface_index + 1):
        x = 8 + tick * 7
        draw.polygon(((x, 8), (x + 4, 8), (x + 2, 12)), fill=(*accent, 210))
    return image


def build_badges() -> list[Image.Image]:
    if not SIGIL_SOURCE.is_file():
        raise FileNotFoundError(f"Missing specialization sigil source: {SIGIL_SOURCE}")
    source = Image.open(SIGIL_SOURCE).convert("RGBA")
    badge_dir = TEXTURES / "spec_badges"
    badge_dir.mkdir(parents=True, exist_ok=True)
    cell_width = source.width / 7.0
    cell_height = source.height / 5.0
    providers = []
    previews = []
    for glyph_index, (spec_id, atlas_index) in enumerate(SPEC_BADGES.items()):
        column, row = atlas_index % 7, atlas_index // 7
        crop = source.crop((round(column * cell_width), round(row * cell_height),
                            round((column + 1) * cell_width), round((row + 1) * cell_height)))
        bounds = crop.getbbox()
        if bounds:
            crop = crop.crop(bounds)
        crop.thumbnail((60, 60), Image.Resampling.LANCZOS)
        badge = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        badge.alpha_composite(crop, ((64 - crop.width) // 2, (64 - crop.height) // 2))
        badge.save(badge_dir / f"{spec_id}.png", optimize=True)
        previews.append(badge)
        providers.append({
            "type": "bitmap",
            "file": f"icesmp_hud:class_ui/spec_badges/{spec_id}.png",
            "ascent": 14,
            "height": 16,
            "chars": [chr(0xE400 + glyph_index)],
        })
    BADGE_FONT.parent.mkdir(parents=True, exist_ok=True)
    BADGE_FONT.write_text(json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n",
                          encoding="utf-8")
    BADGE_REPORT.parent.mkdir(parents=True, exist_ok=True)
    contact = Image.new("RGBA", (64 * 7, 64 * 5), (8, 10, 14, 255))
    for index, badge in enumerate(previews):
        contact.alpha_composite(badge, ((index % 7) * 64, (index // 7) * 64))
    contact.save(BADGE_REPORT, optimize=True)
    return previews


def build_class_badge_font() -> None:
    providers = []
    for index, class_id in enumerate(CLASS_BADGES):
        providers.append({
            "type": "bitmap",
            "file": f"icesmp_hud:hud/class-{class_id}.png",
            "ascent": 14,
            "height": 16,
            "chars": [chr(0xE430 + index)],
        })
    CLASS_BADGE_FONT.write_text(
        json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    if not SOURCE.is_file():
        raise FileNotFoundError(f"Missing class UI ornament source: {SOURCE}")
    source = transparent_ornaments(Image.open(SOURCE))
    quadrant_width = source.width // 2
    quadrant_height = source.height // 2
    TEXTURES.mkdir(parents=True, exist_ok=True)
    providers = []
    previews = []
    for surface_index, surface in enumerate(SURFACES):
        for theme_index, (theme, (quadrant, base, accent)) in enumerate(THEMES.items()):
            qx, qy = quadrant
            crop = source.crop((qx * quadrant_width, qy * quadrant_height,
                                (qx + 1) * quadrant_width, (qy + 1) * quadrant_height))
            background = build_background(crop, base, accent, surface_index)
            legacy_name = f"class-ui-{theme}.png" if surface_index == 0 else None
            file_name = legacy_name or f"class-ui-{surface}-{theme}.png"
            background.save(TEXTURES / file_name, optimize=True)
            previews.append(background)
            providers.append({
                "type": "bitmap",
                "file": f"icesmp_hud:class_ui/{file_name}",
                "ascent": 13,
                "height": 222,
                "chars": [chr(0xE390 + surface_index * len(THEMES) + theme_index)],
            })
    FONT.parent.mkdir(parents=True, exist_ok=True)
    FONT.write_text(json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8")
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    contact = Image.new("RGBA", (176 * len(THEMES), 222 * len(SURFACES)), (0, 0, 0, 0))
    for index, preview in enumerate(previews):
        contact.alpha_composite(preview, ((index % len(THEMES)) * 176,
                                          (index // len(THEMES)) * 222))
    contact.save(REPORT, optimize=True)
    build_badges()
    build_class_badge_font()


if __name__ == "__main__":
    main()
