#!/usr/bin/env python3
"""Generate deterministic Minecraft 1.21.11 tooltip chrome for IceSMP."""

from __future__ import annotations
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
GLOBAL_ROOT = ROOT / "resource-pack" / "assets" / "minecraft" / "textures" / "gui" / "sprites" / "tooltip"
RARITY_ROOT = ROOT / "resource-pack" / "assets" / "icesmp" / "textures" / "gui" / "sprites" / "tooltip"
SIZE = 100
RARITIES = {
    "ocska": (92, 92, 92), "kozonseges": (205, 205, 205),
    "nem_mindennapi": (85, 210, 100), "ritka": (75, 145, 255),
    "epikus": (180, 85, 255), "legendas": (255, 170, 0),
    "mitikus": (255, 80, 80), "ereklye": (70, 220, 210),
}
BACKGROUND_SCALING = {"gui":{"scaling":{"type":"nine_slice","width":100,"height":100,"border":9}}}
FRAME_SCALING = {"gui":{"scaling":{"type":"nine_slice","width":100,"height":100,"border":10,"stretch_inner":True}}}

def write_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)

def write_meta(path: Path, data: dict[str, object]) -> None:
    path.with_suffix(path.suffix + ".mcmeta").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

def background() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0,0,0,0)); pixels=image.load()
    for y in range(SIZE):
        for x in range(SIZE):
            d=min(x,y,SIZE-1-x,SIZE-1-y)
            if d == 0: pixels[x,y]=(0,0,0,0)
            elif d <= 2: pixels[x,y]=(28,12,38,235)
            else:
                shade=10+min(8,d//10); pixels[x,y]=(shade+4,shade,shade+8,246)
    return image

def frame(color: tuple[int,int,int]) -> Image.Image:
    image=Image.new("RGBA",(SIZE,SIZE),(0,0,0,0)); pixels=image.load()
    r,g,b=color; dark=(r//3,g//3,b//3); hi=(min(255,round(r*1.12)),min(255,round(g*1.12)),min(255,round(b*1.12)))
    for y in range(SIZE):
        for x in range(SIZE):
            d=min(x,y,SIZE-1-x,SIZE-1-y)
            if d <= 1: pixels[x,y]=(*dark,255)
            elif d == 2: pixels[x,y]=(*color,255)
            elif d == 3: pixels[x,y]=(*hi,180)
            elif d == 4: pixels[x,y]=(*hi,70)
    for x,y in ((2,2),(SIZE-3,2),(2,SIZE-3),(SIZE-3,SIZE-3)): pixels[x,y]=(235,235,235,255)
    return image

def pair(root: Path, prefix: str, color: tuple[int,int,int]) -> None:
    bg=root / (f"{prefix}_background.png" if prefix else "background.png")
    fr=root / (f"{prefix}_frame.png" if prefix else "frame.png")
    write_png(background(), bg); write_png(frame(color), fr)
    write_meta(bg, BACKGROUND_SCALING); write_meta(fr, FRAME_SCALING)

def main() -> None:
    pair(GLOBAL_ROOT, "", (125,50,160))
    for rarity,color in RARITIES.items(): pair(RARITY_ROOT, rarity, color)

if __name__ == "__main__": main()
