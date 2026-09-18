#!/usr/bin/env python3
"""Generate deterministic global Minecraft 1.21.11 tooltip chrome for IceSMP."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEXTURE_ROOT = (
    ROOT / "resource-pack" / "assets" / "minecraft" / "textures"
    / "gui" / "sprites" / "tooltip"
)

SIZE = 100
BACKGROUND_BORDER = 9
FRAME_BORDER = 10

BACKGROUND_SCALING = {
    "gui": {
        "scaling": {
            "type": "nine_slice",
            "width": SIZE,
            "height": SIZE,
            "border": BACKGROUND_BORDER,
        }
    }
}
FRAME_SCALING = {
    "gui": {
        "scaling": {
            "type": "nine_slice",
            "width": SIZE,
            "height": SIZE,
            "border": FRAME_BORDER,
            "stretch_inner": True,
        }
    }
}


def write_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def write_scaling_metadata(path: Path, definition: dict[str, object]) -> None:
    path.with_suffix(path.suffix + ".mcmeta").write_text(
        json.dumps(definition, indent=2) + "\n",
        encoding="utf-8",
    )


def background() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()
    for y in range(SIZE):
        for x in range(SIZE):
            distance = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            if distance == 0:
                pixels[x, y] = (0, 0, 0, 0)
            elif distance <= 2:
                pixels[x, y] = (28, 12, 38, 235)
            else:
                shade = 10 + min(8, distance // 10)
                pixels[x, y] = (shade + 4, shade, shade + 8, 246)
    return image


def frame() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()
    for y in range(SIZE):
        for x in range(SIZE):
            distance = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            if distance <= 1:
                pixels[x, y] = (55, 20, 75, 255)
            elif distance == 2:
                pixels[x, y] = (125, 50, 160, 255)
            elif distance == 3:
                pixels[x, y] = (85, 35, 115, 180)
            elif distance == 4:
                pixels[x, y] = (65, 30, 90, 80)
    for x, y in ((2, 2), (SIZE - 3, 2), (2, SIZE - 3), (SIZE - 3, SIZE - 3)):
        pixels[x, y] = (185, 95, 225, 255)
    return image


def main() -> None:
    background_path = TEXTURE_ROOT / "background.png"
    frame_path = TEXTURE_ROOT / "frame.png"
    write_png(background(), background_path)
    write_png(frame(), frame_path)
    write_scaling_metadata(background_path, BACKGROUND_SCALING)
    write_scaling_metadata(frame_path, FRAME_SCALING)


if __name__ == "__main__":
    main()
