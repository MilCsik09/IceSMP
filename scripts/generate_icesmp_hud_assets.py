#!/usr/bin/env python3
"""Generate the first-party IceSMP HUD font and texture layer.

The runtime uses fixed-position, zero-net-width draw commands. Source artwork that is
not derivable from the live resource pack lives under ``dev-assets/icesmp-hud``; no
external HUD plugin is involved in generation or delivery.
"""

import io
import json
import os
import time
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
HUD_SOURCE = ROOT / "dev-assets" / "icesmp-hud" / "source"
FRAME_ATLAS_SOURCE = HUD_SOURCE / "icesmp-hud-runtime-source.png"
ITEM_SOURCE = ROOT / "resource-pack" / "assets" / "icesmp" / "textures" / "item"
GUEST_FRAME_SOURCE = HUD_SOURCE / "frame-guest-v2.png"
MECHANIC_CORE_SOURCE = HUD_SOURCE / "mechanics-core-v1.png"
MECHANIC_SPEC_SOURCE = HUD_SOURCE / "mechanics-spec-v1.png"
TEXT_FONT_SOURCE = HUD_SOURCE / "DejaVuSans.ttf"
ASSETS = ROOT / "resource-pack" / "assets" / "icesmp_hud"
TEXTURES = ASSETS / "textures" / "hud"
FONTS = ASSETS / "font"

SPACE_FIRST = 0xE400
SPACE_MIN = -512
SPACE_MAX = 512
HUD_BIT = 13
HUD_MAX_BIT = 10
HUD_ADD_HEIGHT = 4095
HUD_FRAME_WIDTH = 240
HUD_FRAME_HEIGHT = 160
TEXT_LOGICAL_WIDTH = 6
TEXT_LOGICAL_HEIGHT = 12
TEXT_OVERSAMPLE = 4

THEMES = ("guest", "red", "blue", "neutral", "dark")
CLASSES = ("warrior", "evoker", "archer", "shaman", "monk", "paladin",
           "demon_hunter", "druid", "priest", "death_knight", "assassin",
           "warlock", "wizard")
CLASS_GLYPHS = CLASSES + ("none",)
RUNE_KINDS = ("blood", "frost", "death")
RUNE_STATES = ("ready", "spent", "regenerating", "locked")
MECHANIC_VARIANTS = ("active", "ready", "alert", "spent")

# Stable row-major order of the two reviewed IceSMP source sheets. A mechanic is keyed
# by class as well as id because e.g. Evoker and Shaman resonance are not the same
# gameplay signal and must never silently share an icon.
CORE_MECHANICS = (
    ("warrior", "battle_tempo"), ("evoker", "empower"),
    ("archer", "wind_read"), ("shaman", "totem_wheel"),
    ("monk", "flow"), ("paladin", "conviction"),
    ("demon_hunter", "load"), ("druid", "harmony"),
    ("priest", "litany"), ("death_knight", "rune_wheel"),
    ("assassin", "opening"), ("warlock", "soul_debt"),
    ("wizard", "runewaving"),
)
SPEC_MECHANICS = (
    ("warrior", "blood_frenzy"), ("warrior", "guard"),
    ("evoker", "resonance"), ("evoker", "imprint"),
    ("archer", "precision_chain"), ("archer", "bond"),
    ("shaman", "resonance"), ("shaman", "maelstrom"), ("shaman", "tide"),
    ("monk", "combo_chain"), ("monk", "stagger"), ("monk", "mist_threads"),
    ("paladin", "beacon"), ("paladin", "judgement_marks"),
    ("paladin", "shield_charge"), ("demon_hunter", "fragments"),
    ("demon_hunter", "pain"), ("demon_hunter", "sigil"),
    ("druid", "combo"), ("druid", "balance"), ("druid", "bark"),
    ("druid", "seeds"), ("priest", "shield_web"), ("priest", "marrow"),
    ("priest", "madness"), ("death_knight", "blood_memory"),
    ("death_knight", "frost_marks"), ("death_knight", "plague"),
    ("assassin", "toxin"), ("assassin", "detection"),
    ("assassin", "infection"), ("warlock", "curses"),
    ("warlock", "embers"), ("warlock", "demons"),
    ("wizard", "attunement"), ("wizard", "court"),
)
MECHANICS = CORE_MECHANICS + SPEC_MECHANICS


def pixels(image: Image.Image):
    getter = getattr(image, "get_flattened_data", None)
    return getter() if getter is not None else image.getdata()


def encoded_ascent(shader_id: int, y: int) -> int:
    """Match the 1.21.11 HUD shader's signed bitmap-ascent transport."""
    return -(((shader_id + (1 << HUD_MAX_BIT)) << HUD_BIT) + HUD_ADD_HEIGHT + y)


def provider(file_name: str, char: str, shader_id: int, y: int, height: int) -> dict:
    return {
        "type": "bitmap",
        "file": f"icesmp_hud:hud/{file_name}",
        "ascent": encoded_ascent(shader_id, y),
        "height": height,
        "chars": [char],
    }


def write_font(name: str, providers: list[dict]) -> None:
    FONTS.mkdir(parents=True, exist_ok=True)
    (FONTS / f"{name}.json").write_text(
        json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def save_png(image: Image.Image, path: Path) -> None:
    """Write generated PNGs only when bytes changed, using a retryable atomic replace."""
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


def largest_component_bbox(image: Image.Image) -> tuple[int, int, int, int] | None:
    alpha = image.getchannel("A")
    width, height = image.size
    visited: set[tuple[int, int]] = set()
    components: list[list[tuple[int, int]]] = []
    for y in range(height):
        for x in range(width):
            if (x, y) in visited or alpha.getpixel((x, y)) < 16:
                continue
            pending = [(x, y)]
            visited.add((x, y))
            points = []
            while pending:
                px, py = pending.pop()
                points.append((px, py))
                for nx in range(max(0, px - 1), min(width, px + 2)):
                    for ny in range(max(0, py - 1), min(height, py + 2)):
                        if (nx, ny) not in visited and alpha.getpixel((nx, ny)) >= 16:
                            visited.add((nx, ny))
                            pending.append((nx, ny))
            components.append(points)
    if not components:
        return None
    points = max(components, key=len)
    return (min(x for x, _ in points), min(y for _, y in points),
            max(x for x, _ in points) + 1, max(y for _, y in points) + 1)


def centered_sprite(image: Image.Image, maximum: int = 54,
                    largest_component: bool = False) -> Image.Image:
    source = image.convert("RGBA")
    bbox = largest_component_bbox(source) if largest_component else source.getchannel("A").getbbox()
    sprite = source.crop(bbox) if bbox else source
    sprite.thumbnail((maximum, maximum), Image.Resampling.LANCZOS)
    output = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    output.alpha_composite(sprite, ((64 - sprite.width) // 2, (64 - sprite.height) // 2))
    # Minecraft derives bitmap-glyph advance from the last non-transparent column.
    # Keep every dynamic icon on the same 64px logical cell.
    output.putpixel((63, 63), (255, 255, 255, 1))
    return output


def validate_static_icon_sources() -> None:
    """Validate reviewed first-party icons that are versioned directly in the resource pack."""
    expected = [f"class-{class_id}.png" for class_id in CLASSES]
    expected += [f"icon-{name}.png" for name in ("money", "event", "level")]
    expected += [f"rune-{kind}-{state}.png" for kind in RUNE_KINDS for state in RUNE_STATES]
    expected += ["charge-ready.png", "charge-spent.png"]
    for name in expected:
        path = TEXTURES / name
        if not path.is_file():
            raise FileNotFoundError(f"Missing first-party HUD icon: {path}")
        image = Image.open(path).convert("RGBA")
        if image.size != (64, 64):
            raise RuntimeError(f"First-party HUD icon must stay 64x64: {path} ({image.size})")
        if image.getpixel((63, 63))[3] != 1:
            raise RuntimeError(f"First-party HUD icon lost its fixed-width alpha marker: {path}")


def remove_magenta(image: Image.Image) -> Image.Image:
    source = image.convert("RGBA")
    cleaned = []
    for red, green, blue, alpha in pixels(source):
        if alpha == 0:
            cleaned.append((0, 0, 0, 0))
            continue
        if min(red, blue) > 115 and min(red, blue) - green > 65 and abs(red - blue) < 90:
            cleaned.append((0, 0, 0, 0))
        else:
            cleaned.append((red, green, blue, alpha))
    source.putdata(cleaned)
    return source


def strip_magenta_resample_spill(image: Image.Image) -> Image.Image:
    source = image.convert("RGBA")
    cleaned = []
    for red, green, blue, alpha in pixels(source):
        spill = (16 <= alpha < 128 and red > 200 and blue > 200
                 and green <= 8 and abs(red - blue) < 30)
        cleaned.append((0, 0, 0, 0) if spill else (red, green, blue, alpha))
    source.putdata(cleaned)
    return source


def canonical_frames() -> dict[str, Image.Image]:
    if not FRAME_ATLAS_SOURCE.is_file():
        raise FileNotFoundError(f"Missing high-resolution HUD frame atlas: {FRAME_ATLAS_SOURCE}")
    atlas = Image.open(FRAME_ATLAS_SOURCE).convert("RGBA")
    boxes = {
        "red": (60, 40, 730, 475), "blue": (790, 40, 1490, 475),
        "neutral": (60, 525, 730, 950), "dark": (790, 520, 1500, 960),
    }
    frames: dict[str, Image.Image] = {}
    for theme, box in boxes.items():
        keyed = remove_magenta(atlas.crop(box))
        bbox = keyed.getchannel("A").getbbox()
        sprite = keyed.crop(bbox) if bbox else keyed
        sprite.thumbnail((670, 410), Image.Resampling.LANCZOS)
        frame = Image.new("RGBA", (680, 420), (0, 0, 0, 0))
        frame.alpha_composite(sprite, ((680 - sprite.width) // 2, (420 - sprite.height) // 2))
        frames[theme] = frame
    return frames


def mechanic_variant(base: Image.Image, variant: str) -> Image.Image:
    """Create deterministic runtime states from one reviewed high-resolution source."""
    source = base.convert("RGBA")
    alpha = source.getchannel("A")
    if variant == "ready":
        colored = ImageEnhance.Color(source).enhance(1.22)
        colored = ImageEnhance.Brightness(colored).enhance(1.12)
        glow = alpha.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(1.0))
        halo = Image.new("RGBA", source.size, (112, 231, 255, 0))
        halo.putalpha(glow.point(lambda value: value // 3))
        output = Image.alpha_composite(halo, colored)
    elif variant == "alert":
        red = Image.new("RGBA", source.size, (231, 76, 63, 0))
        red.putalpha(alpha.point(lambda value: value * 2 // 5))
        output = Image.alpha_composite(source, red)
        output = ImageEnhance.Contrast(output).enhance(1.08)
    elif variant == "spent":
        gray = source.convert("LA").convert("RGBA")
        gray.putalpha(alpha.point(lambda value: value * 3 // 4))
        output = ImageEnhance.Brightness(gray).enhance(0.48)
    else:
        output = source
    output.putpixel((63, 63), (255, 255, 255, 1))
    return output


def generate_mechanic_icons() -> None:
    """Crop the two reviewed source sheets into fixed, class-specific glyph cells."""
    sheets = (
        (MECHANIC_CORE_SOURCE, 4, 4, CORE_MECHANICS),
        (MECHANIC_SPEC_SOURCE, 6, 6, SPEC_MECHANICS),
    )
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for source_path, columns, rows, keys in sheets:
        if not source_path.is_file():
            raise FileNotFoundError(f"Missing mechanic icon source sheet: {source_path}")
        sheet = Image.open(source_path).convert("RGBA")
        for index, (class_id, mechanic_id) in enumerate(keys):
            column, row = index % columns, index // columns
            box = (
                round(column * sheet.width / columns),
                round(row * sheet.height / rows),
                round((column + 1) * sheet.width / columns),
                round((row + 1) * sheet.height / rows),
            )
            base = centered_sprite(sheet.crop(box), 52, True)
            for variant in MECHANIC_VARIANTS:
                file_name = f"mechanic-{class_id}-{mechanic_id}-{variant}.png"
                save_png(mechanic_variant(base, variant), TEXTURES / file_name)


def guest_frame_with_canonical_layout(canonical: Image.Image) -> Image.Image:
    """Apply guest art without allowing generated panel geometry to drift."""
    donor = Image.open(GUEST_FRAME_SOURCE).convert("RGBA").resize(
        canonical.size, Image.Resampling.LANCZOS)
    donor_pixels = donor.load()
    for y in range(donor.height):
        for x in range(donor.width):
            red, green, blue, alpha = donor_pixels[x, y]
            if red > 95 and blue > 95 and green * 3 < red * 2 and green * 3 < blue * 2:
                donor_pixels[x, y] = (red, green, blue, 0)

    styled = Image.new("RGBA", canonical.size)
    source_pixels = canonical.load()
    styled_pixels = styled.load()
    for y in range(canonical.height):
        for x in range(canonical.width):
            red, green, blue, alpha = source_pixels[x, y]
            luminance = (red * 30 + green * 59 + blue * 11) // 100
            styled_pixels[x, y] = (
                min(255, int(luminance * 0.78 + 25)),
                min(255, int(luminance * 0.86 + 34)),
                min(255, int(luminance * 0.90 + 39)),
                alpha,
            )
    canonical_styled = styled.copy()

    mask = Image.new("L", canonical.size, 0)
    draw = ImageDraw.Draw(mask)
    width, height = canonical.size
    rim = max(12, width // 34)
    draw.rectangle((0, 0, width - 1, rim), fill=255)
    draw.rectangle((0, height - rim - 1, width - 1, height - 1), fill=255)
    draw.rectangle((0, 0, rim, height - 1), fill=255)
    draw.rectangle((width - rim - 1, 0, width - 1, height - 1), fill=255)
    draw.rectangle((width // 2 - 40, 0, width // 2 + 40, 39), fill=255)
    draw.rectangle((width // 2 - 34, height - 40, width // 2 + 34, height - 1), fill=255)
    mask = Image.composite(mask, Image.new("L", canonical.size, 0), donor.getchannel("A"))
    ornament = Image.composite(donor, Image.new("RGBA", canonical.size), mask)
    styled.alpha_composite(ornament)

    protected = (rim + 1, 40, width - rim - 1, height - 40)
    styled.paste(canonical_styled.crop(protected), protected)
    if (styled.getchannel("A").crop(protected).tobytes()
            != canonical.getchannel("A").crop(protected).tobytes()):
        raise RuntimeError("Guest HUD changed the canonical content-grid geometry")
    return styled


def generate_frames() -> None:
    TEXTURES.mkdir(parents=True, exist_ok=True)
    frames = canonical_frames()
    for theme in THEMES:
        if theme == "guest":
            if not GUEST_FRAME_SOURCE.is_file():
                raise FileNotFoundError(f"Missing original Menedék HUD source: {GUEST_FRAME_SOURCE}")
            source = guest_frame_with_canonical_layout(frames["neutral"])
            bbox = frames["neutral"].getchannel("A").getbbox()
        else:
            source = frames[theme]
            bbox = source.getchannel("A").getbbox()
        sprite = source.crop(bbox) if bbox else source
        sprite.thumbnail((HUD_FRAME_WIDTH, HUD_FRAME_HEIGHT), Image.Resampling.LANCZOS)
        output = Image.new("RGBA", (HUD_FRAME_WIDTH, HUD_FRAME_HEIGHT), (0, 0, 0, 0))
        output.alpha_composite(sprite, ((HUD_FRAME_WIDTH - sprite.width) // 2,
                                        (HUD_FRAME_HEIGHT - sprite.height) // 2))
        output = strip_magenta_resample_spill(output)
        output.putpixel((HUD_FRAME_WIDTH - 1, HUD_FRAME_HEIGHT - 1), (255, 255, 255, 1))
        save_png(output,
                 TEXTURES / f"frame-hud-{theme}.png")


def generate_currency_icons() -> None:
    for currency in ("red", "blue", "neutral", "dark"):
        source = ITEM_SOURCE / f"currency_{currency}.png"
        if not source.is_file():
            raise FileNotFoundError(f"Missing canonical currency icon: {source}")
        save_png(centered_sprite(Image.open(source), 52),
                 TEXTURES / f"currency-{currency}.png")


def generate_none_class_icon() -> None:
    """Neutral sanctuary compass for players who have not chosen a class yet."""
    scale = 4
    image = Image.new("RGBA", (64 * scale, 64 * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    center = 32 * scale
    draw.ellipse((8 * scale, 8 * scale, 56 * scale, 56 * scale),
                 fill=(15, 22, 29, 245), outline=(151, 171, 178, 255), width=3 * scale)
    draw.ellipse((14 * scale, 14 * scale, 50 * scale, 50 * scale),
                 outline=(48, 113, 117, 255), width=2 * scale)
    draw.polygon(((center, 11 * scale), (38 * scale, center),
                  (center, 53 * scale), (26 * scale, center)),
                 fill=(71, 143, 145, 255), outline=(218, 231, 225, 255))
    draw.polygon(((center, 18 * scale), (35 * scale, center),
                  (center, 46 * scale), (29 * scale, center)),
                 fill=(200, 211, 202, 255))
    draw.ellipse((28 * scale, 28 * scale, 36 * scale, 36 * scale),
                 fill=(11, 20, 27, 255), outline=(230, 239, 232, 255), width=scale)
    image = image.resize((64, 64), Image.Resampling.LANCZOS)
    image.putpixel((63, 63), (255, 255, 255, 1))
    save_png(image, TEXTURES / "class-none.png")


def generate_text_atlas() -> tuple[list[str], int, int]:
    characters = (
        " !\"#$%&'()*+,-./0123456789:;<=>?@"
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`"
        "abcdefghijklmnopqrstuvwxyz{|}~"
        "ÁÉÍÓÖŐÚÜŰáéíóöőúüű•—…"
    )
    unique = "".join(dict.fromkeys(characters))
    columns = 16
    rows = (len(unique) + columns - 1) // columns
    padding_length = rows * columns - len(unique)
    padding = "".join(chr(0xE900 + index) for index in range(padding_length))
    padded = unique + padding
    cell_width = TEXT_LOGICAL_WIDTH * TEXT_OVERSAMPLE
    cell_height = TEXT_LOGICAL_HEIGHT * TEXT_OVERSAMPLE
    atlas = Image.new("RGBA", (columns * cell_width, rows * cell_height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(atlas)
    if not TEXT_FONT_SOURCE.is_file():
        raise FileNotFoundError(f"Missing reproducible IceSMP HUD text font: {TEXT_FONT_SOURCE}")
    font = ImageFont.truetype(TEXT_FONT_SOURCE, size=10 * TEXT_OVERSAMPLE)
    for index, char in enumerate(padded):
        if index < len(unique):
            x = (index % columns) * cell_width
            y = (index // columns) * cell_height
            box = font.getbbox(char)
            width = max(0, box[2] - box[0])
            height = max(0, box[3] - box[1])
            if width > 0 and height > 0:
                glyph = Image.new("L", (width, height), 0)
                glyph_draw = ImageDraw.Draw(glyph)
                glyph_draw.text((-box[0], -box[1]), char, font=font, fill=255)
                maximum_width = cell_width - 2
                maximum_height = cell_height - 4
                scale = min(1.0, maximum_width / width, maximum_height / height)
                if scale < 1.0:
                    glyph = glyph.resize((max(1, round(width * scale)),
                                          max(1, round(height * scale))),
                                         Image.Resampling.LANCZOS)
                colored = Image.new("RGBA", glyph.size, (235, 247, 255, 255))
                colored.putalpha(glyph)
                atlas.alpha_composite(colored,
                                      (x + (cell_width - glyph.width) // 2,
                                       y + (cell_height - glyph.height) // 2))
            atlas.putpixel((x + cell_width - 1, y + cell_height - 1), (255, 255, 255, 1))
    TEXTURES.mkdir(parents=True, exist_ok=True)
    save_png(atlas, TEXTURES / "text-atlas.png")
    return ([padded[row * columns:(row + 1) * columns] for row in range(rows)],
            TEXT_LOGICAL_WIDTH, TEXT_LOGICAL_HEIGHT)


def generate_segments() -> None:
    TEXTURES.mkdir(parents=True, exist_ok=True)
    colors = {
        "segment-track.png": ((8, 12, 17, 255), (53, 67, 85, 255)),
        "segment-fill.png": ((70, 190, 213, 255), (234, 247, 255, 255)),
        "segment-fill-warm.png": ((183, 58, 50, 255), (240, 160, 91, 255)),
        "segment-fill-gold.png": ((113, 89, 35, 255), (240, 216, 141, 255)),
    }
    for name, (base, highlight) in colors.items():
        image = Image.new("RGBA", (12, 5), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        draw.rounded_rectangle((0, 0, 11, 4), radius=1, fill=base)
        draw.line((1, 0, 10, 0), fill=highlight)
        save_png(image, TEXTURES / name)


def generate_wallet_strip() -> None:
    scale = 4
    image = Image.new("RGBA", (HUD_FRAME_WIDTH * scale, 22 * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((1 * scale, 1 * scale, (HUD_FRAME_WIDTH - 2) * scale, 20 * scale), radius=4 * scale,
                           fill=(8, 12, 17, 238), outline=(91, 109, 123, 255), width=1 * scale)
    for index in range(1, 4):
        x = (1 + index * 59) * scale
        draw.line((x, 4 * scale, x, 18 * scale), fill=(53, 67, 85, 220), width=1 * scale)
    draw.line((6 * scale, 2 * scale, (HUD_FRAME_WIDTH - 8) * scale, 2 * scale),
              fill=(102, 181, 163, 150), width=1 * scale)
    image = image.resize((HUD_FRAME_WIDTH, 22), Image.Resampling.LANCZOS)
    image.putpixel((HUD_FRAME_WIDTH - 1, 21), (255, 255, 255, 1))
    save_png(image, TEXTURES / "wallet-strip.png")

    details = Image.new("RGBA", (HUD_FRAME_WIDTH * scale, 22 * scale), (0, 0, 0, 0))
    detail_draw = ImageDraw.Draw(details)
    detail_draw.rounded_rectangle((1 * scale, 1 * scale, (HUD_FRAME_WIDTH - 2) * scale, 20 * scale), radius=4 * scale,
                                  fill=(8, 12, 17, 232), outline=(53, 67, 85, 255), width=1 * scale)
    for index in range(1, 3):
        x = round((1 + index * 79.3) * scale)
        detail_draw.line((x, 4 * scale, x, 18 * scale),
                         fill=(53, 67, 85, 210), width=1 * scale)
    details = details.resize((HUD_FRAME_WIDTH, 22), Image.Resampling.LANCZOS)
    details.putpixel((HUD_FRAME_WIDTH - 1, 21), (255, 255, 255, 1))
    save_png(details, TEXTURES / "detail-strip.png")


def generate_transparent_white_bossbar() -> None:
    target = ROOT / "resource-pack" / "assets" / "minecraft" / "textures" / "gui" / "sprites" / "boss_bar"
    target.mkdir(parents=True, exist_ok=True)
    transparent = Image.new("RGBA", (1, 1), (0, 0, 0, 0))
    save_png(transparent, target / "white_background.png")
    save_png(transparent, target / "white_progress.png")


def generate_hud_shader() -> None:
    """Install the first-party IceSMP positioning shader."""
    target = ROOT / "resource-pack" / "assets" / "minecraft" / "shaders" / "core"
    target.mkdir(parents=True, exist_ok=True)
    vertex = """#version 330
#define HEIGHT_BIT 13
#define MAX_BIT 10
#define ADD_OFFSET 4095
#define DEFAULT_OFFSET 10
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
void main() {
    vec3 pos = Position;
    vec2 ui = ceil(2 / vec2(ProjMat[0][0], -ProjMat[1][1]));
    bool hudGlyph = false;
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    if (pos.y >= ui.y && ProjMat[3].x == -1) {
        int bit = int(pos.y) >> HEIGHT_BIT;
        if (((bit >> MAX_BIT) & 1) == 1) {
            int id = bit - (1 << MAX_BIT);
            hudGlyph = true;
            pos.y -= (bit << HEIGHT_BIT) + ADD_OFFSET + DEFAULT_OFFSET;
            float layer = 0;
            bool outline = false;
            if (id == 4) layer = 1;
            else if (id == 5) layer = 2;
            else if (id == 6) layer = 3;
            else if (id == 7) layer = 4;
            else if (id == 8) layer = 5;
            else if (id == 9) layer = 6;
            else if (id == 10) { layer = 7; outline = true; }
            pos.z += layer;
            if (!outline && (pos.z == 0 || pos.z == 1000 || pos.z == -90 || pos.z == 2800)) {
                vertexColor = vec4(0);
            }
        }
    }
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    texCoord0 = UV0;
    vec4 clipPosition = ProjMat * ModelViewMat * vec4(pos, 1.0);
    if (hudGlyph) clipPosition.x += clipPosition.w;
    gl_Position = clipPosition;
}
"""
    fragment = """#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
"""
    (target / "rendertype_text.vsh").write_text(vertex, encoding="utf-8")
    (target / "rendertype_text.fsh").write_text(fragment, encoding="utf-8")


def generate_contact_sheet() -> None:
    target = ROOT / "build" / "reports" / "icesmp-hud" / "contact-sheet.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGBA", (960, 1260), (12, 16, 22, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=18)
    draw.text((24, 14), "IceSMP HUD asset QA", font=font, fill=(235, 247, 255, 255))
    for index, theme in enumerate(THEMES):
        frame = Image.open(TEXTURES / f"frame-hud-{theme}.png").convert("RGBA")
        preview = frame.resize((208, 128), Image.Resampling.LANCZOS)
        x = 24 + (index % 3) * 308
        y = 50 + (index // 3) * 174
        sheet.alpha_composite(preview, (x, y))
        draw.text((x, y + 134), theme, font=font, fill=(169, 183, 198, 255))
    icon_names = [f"class-{name}.png" for name in CLASSES]
    icon_names += [f"currency-{name}.png" for name in ("red", "blue", "neutral", "dark")]
    icon_names += [f"rune-{kind}-ready.png" for kind in RUNE_KINDS]
    for index, name in enumerate(icon_names):
        icon = Image.open(TEXTURES / name).convert("RGBA")
        x = 24 + (index % 10) * 88
        y = 410 + (index // 10) * 104
        sheet.alpha_composite(icon, (x + 12, y))
        draw.text((x, y + 68), name.replace("class-", "").replace(".png", "")[:12],
                  font=ImageFont.load_default(size=10), fill=(199, 212, 234, 255))
    draw.text((24, 626), "49 class-qualified mechanic families (active source)",
              font=font, fill=(235, 247, 255, 255))
    label_font = ImageFont.load_default(size=9)
    for index, (class_id, mechanic_id) in enumerate(MECHANICS):
        icon = Image.open(TEXTURES / f"mechanic-{class_id}-{mechanic_id}-active.png").convert("RGBA")
        x = 18 + (index % 8) * 118
        y = 660 + (index // 8) * 82
        sheet.alpha_composite(icon, (x + 25, y))
        label = f"{class_id[:8]}:{mechanic_id[:10]}"
        draw.text((x, y + 64), label, font=label_font, fill=(199, 212, 234, 255))
    save_png(sheet, target)


def main() -> None:
    validate_static_icon_sources()
    generate_frames()
    generate_none_class_icon()
    generate_mechanic_icons()
    generate_currency_icons()
    generate_segments()
    generate_wallet_strip()
    generate_transparent_white_bossbar()
    generate_hud_shader()
    text_rows, _, text_height = generate_text_atlas()

    spaces = {
        chr(SPACE_FIRST + offset - SPACE_MIN): offset
        for offset in range(SPACE_MIN, SPACE_MAX + 1)
    }
    write_font("space", [{"type": "space", "advances": spaces}])

    write_font("panel", [
        provider(f"frame-hud-{theme}.png", chr(0xE100 + index), 4, 18, HUD_FRAME_HEIGHT)
        for index, theme in enumerate(THEMES)
    ])
    write_font("wallet_panel", [provider("wallet-strip.png", chr(0xE105), 4, 201, 22)])
    write_font("detail_panel", [provider("detail-strip.png", chr(0xE106), 4, 178, 22)])
    write_font("class_icon", [
        provider(f"class-{class_id}.png", chr(0xE110 + index), 8, 38, 36)
        for index, class_id in enumerate(CLASS_GLYPHS)
    ])
    write_font("utility", [
        provider("icon-money.png", chr(0xE130), 8, 55, 15),
        provider("icon-event.png", chr(0xE131), 8, 155, 15),
        provider("icon-level.png", chr(0xE132), 8, 42, 15),
    ])
    write_font("runes", [
        provider(f"rune-{kind}-{state}.png", chr(0xE140 + kind_index * 4 + state_index),
                 8, 112, 18)
        for kind_index, kind in enumerate(RUNE_KINDS)
        for state_index, state in enumerate(RUNE_STATES)
    ])
    write_font("currency", [
        provider(f"currency-{currency}.png", chr(0xE160 + index), 8, 210, 15)
        for index, currency in enumerate(("red", "blue", "neutral", "dark"))
    ])
    write_font("charges", [
        provider("charge-ready.png", chr(0xE170), 8, 134, 10),
        provider("charge-spent.png", chr(0xE171), 8, 134, 10),
    ])
    mechanic_providers = [
        provider(
            f"mechanic-{class_id}-{mechanic_id}-{variant}.png",
            chr(0xE200 + mechanic_index * len(MECHANIC_VARIANTS) + variant_index),
            8, 96, 14,
        )
        for mechanic_index, (class_id, mechanic_id) in enumerate(MECHANICS)
        for variant_index, variant in enumerate(MECHANIC_VARIANTS)
    ]
    write_font("mechanic_icons", mechanic_providers)
    write_font("mechanic_slots", [
        {**entry, "ascent": encoded_ascent(8, 134), "height": 10}
        for entry in mechanic_providers
    ])
    write_font("resource_segments", [
        provider("segment-track.png", chr(0xE180), 5, 92, 5),
        provider("segment-fill.png", chr(0xE181), 6, 92, 5),
        provider("segment-fill-warm.png", chr(0xE182), 6, 92, 5),
        provider("segment-fill-gold.png", chr(0xE183), 6, 92, 5),
    ])
    write_font("metric_segments", [
        provider("segment-track.png", chr(0xE180), 5, 123, 5),
        provider("segment-fill.png", chr(0xE181), 6, 123, 5),
        provider("segment-fill-warm.png", chr(0xE182), 6, 123, 5),
        provider("segment-fill-gold.png", chr(0xE183), 6, 123, 5),
    ])

    for name, y in {
        "text_header": 42,
        "text_subheader": 59,
        "text_resource": 84,
        "text_mechanic": 108,
        "text_state": 134,
        "text_event": 155,
        "text_detail": 190,
        "text_wallet": 213,
    }.items():
        write_font(name, [{
            "type": "bitmap",
            "file": "icesmp_hud:hud/text-atlas.png",
            "ascent": encoded_ascent(10, y - 9),
            "height": text_height,
            "chars": text_rows,
        }])

    manifest = {
        "version": 1,
        "space_first": f"U+{SPACE_FIRST:04X}",
        "space_min": SPACE_MIN,
        "space_max": SPACE_MAX,
        "text_advance": TEXT_LOGICAL_WIDTH + 1,
        "text_oversample": TEXT_OVERSAMPLE,
        "maximum_bitmap_glyph_width": 256,
        "themes": list(THEMES),
        "classes": list(CLASSES),
        "mechanics": [f"{class_id}:{mechanic_id}" for class_id, mechanic_id in MECHANICS],
        "mechanic_variants": list(MECHANIC_VARIANTS),
        "fixed_segment_count": 12,
        "wallet_slots": 4,
        "vanilla_health_hidden": False,
        "vanilla_armor_hidden": False,
    }
    (ASSETS / "hud-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    generate_contact_sheet()


if __name__ == "__main__":
    main()
