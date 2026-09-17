#!/usr/bin/env python3
"""Generate deterministic Minecraft 1.21.11 tooltip-style assets for IceSMP."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
STYLE_ROOT = ROOT / "resource-pack" / "assets" / "icesmp" / "tooltip_styles"
TEXTURE_ROOT = ROOT / "resource-pack" / "assets" / "icesmp" / "textures" / "gui" / "sprites" / "tooltip"

RARITIES = {
    "ocska": (92, 92, 92),
    "kozonseges": (238, 238, 238),
    "nem_mindennapi": (85, 255, 85),
    "ritka": (85, 170, 255),
    "epikus": (190, 85, 255),
    "legendas": (255, 170, 0),
    "mitikus": (255, 85, 85),
    "ereklye": (255, 85, 85),
}


def write_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def background(color: tuple[int, int, int]) -> Image.Image:
    image = Image.new("RGBA", (16, 16))
    pixels = image.load()
    for y in range(16):
        for x in range(16):
            distance = min(x, y, 15 - x, 15 - y)
            shade = 19 + min(11, distance * 2)
            tint = tuple(channel // 32 for channel in color)
            pixels[x, y] = (min(48, shade + tint[0]), min(48, shade + tint[1]),
                            min(48, shade + tint[2]), 238)
    return image


def frame(color: tuple[int, int, int]) -> Image.Image:
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    dark = tuple(max(0, channel // 3) for channel in color)
    draw.rectangle((0, 0, 15, 15), outline=(*dark, 255), width=2)
    draw.rectangle((2, 2, 13, 13), outline=(*color, 255), width=1)
    for point in ((2, 2), (13, 2), (2, 13), (13, 13)):
        draw.point(point, fill=(255, 255, 255, 255))
    return image


def main() -> None:
    for rarity, color in RARITIES.items():
        write_png(background(color), TEXTURE_ROOT / f"{rarity}_background.png")
        write_png(frame(color), TEXTURE_ROOT / f"{rarity}_frame.png")
        (STYLE_ROOT / f"{rarity}.json").parent.mkdir(parents=True, exist_ok=True)
        (STYLE_ROOT / f"{rarity}.json").write_text(
            json.dumps(
                {
                    "background": f"icesmp:tooltip/{rarity}_background",
                    "frame": f"icesmp:tooltip/{rarity}_frame",
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
