#!/usr/bin/env python3
"""Generate the first-party IceSMP HUD font and texture layer.

The runtime uses fixed-position, zero-net-width draw commands. Source artwork that is
not derivable from the live resource pack lives under ``dev-assets/icesmp-hud``; no
external HUD plugin is involved in generation or delivery.
"""

import io
import json
import os
import sys
import time
from pathlib import Path

try:
    from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageFont
except ModuleNotFoundError:
    print("A generateIceSmpHudAssets futtatásához a Pillow csomag szükséges; "
          "telepítés: python3 -m pip install Pillow", file=sys.stderr)
    raise SystemExit(2)

ROOT = Path(__file__).resolve().parents[1]
HUD_SOURCE = ROOT / "dev-assets" / "icesmp-hud" / "source"
FRAME_ATLAS_SOURCE = HUD_SOURCE / "frames-v3.png"
ITEM_SOURCE = ROOT / "resource-pack" / "assets" / "icesmp" / "textures" / "item"
MECHANIC_CORE_SOURCE = HUD_SOURCE / "mechanics-core-v3.png"
MECHANIC_SPEC_SOURCE = HUD_SOURCE / "mechanics-spec-v3.png"
TEXT_FONT_SOURCE = HUD_SOURCE / "Inter-SemiBold.ttf"
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
TEXT_LOGICAL_WIDTH = 5
TEXT_LOGICAL_HEIGHT = 12
# Keep the compact six-pixel layout advance, but retain a real high-density glyph
# source. Minecraft scales the 8x atlas into the configured 5x12 logical cell; we
# never collapse the outline to a five-pixel binary mask during generation.
TEXT_OVERSAMPLE = 8
COMPACT_WALLET_ANCHOR_Y = 178
COMPACT_WALLET_ANCHOR_DELTA = COMPACT_WALLET_ANCHOR_Y - 201
HUD_LAYOUT_SCALES = (0.75, 0.90, 1.00, 1.15, 1.25, 1.40, 1.60, 1.80,
                     2.00, 2.20, 2.40, 2.60, 2.80, 3.00, 3.25, 3.50)

# Reviewed baseline anchors in the same HUD shader coordinate system as bitmap ascent.
# Keeping them together prevents independent providers from drifting into adjacent panels.
HUD_Y = {
    "frame": 18,
    "class_icon": 38,
    "header": 42,
    "subheader": 55,
    "resource_text": 67,
    "resource_bar": 70,
    "mechanic_icon": 86,
    "mechanic_text": 94,
    "metric_bar": 108,
    "runes": 130,
    "charge": 137,
    "state": 143,
    "event_icon": 155,
    "event_text": 165,
    "detail_text": 190,
    "wallet_icon": 210,
    "wallet_text": 217,
    "wallet_lower_icon": 230,
    "wallet_lower_text": 237,
}
HUD_X = {
    "resource_text": 68,
    "resource_bar": 60,
    "primary_metric_bar": 12,
    "secondary_metric_bar": 125,
    "event_center": 120,
    "level_center": 218,
}

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
    sprite.thumbnail((maximum, maximum), Image.Resampling.NEAREST)
    scaled_bbox = sprite.getchannel("A").getbbox()
    if scaled_bbox:
        sprite = sprite.crop(scaled_bbox)
    output = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    output.alpha_composite(sprite, ((64 - sprite.width) // 2, (64 - sprite.height) // 2))
    # Minecraft derives bitmap-glyph advance from the last non-transparent column.
    # Keep every dynamic icon on the same 64px logical cell.
    output.putpixel((63, 63), (255, 255, 255, 1))
    return output


def validate_static_icon_sources() -> None:
    """Validate reviewed first-party icons that are versioned directly in the resource pack."""
    expected = [f"class-{class_id}.png" for class_id in CLASS_GLYPHS]
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


def mechanic_variant(base: Image.Image, variant: str) -> Image.Image:
    """Create deterministic runtime states from one reviewed high-resolution source."""
    source = base.convert("RGBA")
    alpha = source.getchannel("A")
    if variant == "active":
        output = source.copy()
    elif variant == "ready":
        colored = ImageEnhance.Brightness(ImageEnhance.Color(source).enhance(1.18)).enhance(1.10)
        glow = alpha.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(1.0))
        halo = Image.new("RGBA", source.size, (100, 225, 255, 0))
        halo.putalpha(glow.point(lambda value: value // 4))
        output = Image.alpha_composite(halo, colored)
    elif variant == "alert":
        red = Image.new("RGBA", source.size, (235, 55, 45, 0))
        red.putalpha(alpha.point(lambda value: value * 2 // 5))
        output = Image.alpha_composite(source, red)
    elif variant == "spent":
        gray = ImageEnhance.Color(source).enhance(0.10)
        output = ImageEnhance.Brightness(gray).enhance(0.48)
        output.putalpha(alpha.point(lambda value: value * 3 // 4))
    else:
        raise ValueError(variant)
    safe_mask = Image.new("L", (64, 64), 0)
    ImageDraw.Draw(safe_mask).rectangle((8, 8, 55, 55), fill=255)
    output.putalpha(ImageChops.multiply(output.getchannel("A"), safe_mask))
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
            base = sheet.crop(box).convert("RGBA")
            if base.size != (64, 64):
                raise RuntimeError(f"Mechanic source cell must stay 64x64: {source_path}")
            for variant in MECHANIC_VARIANTS:
                file_name = f"mechanic-{class_id}-{mechanic_id}-{variant}.png"
                save_png(mechanic_variant(base, variant), TEXTURES / file_name)


def generate_frames() -> None:
    TEXTURES.mkdir(parents=True, exist_ok=True)
    if not FRAME_ATLAS_SOURCE.is_file():
        raise FileNotFoundError(f"Missing normalized HUD frame atlas: {FRAME_ATLAS_SOURCE}")
    atlas = Image.open(FRAME_ATLAS_SOURCE).convert("RGBA")
    if atlas.size != (HUD_FRAME_WIDTH * len(THEMES), HUD_FRAME_HEIGHT):
        raise RuntimeError(f"Unexpected normalized HUD frame atlas size: {atlas.size}")
    for index, theme in enumerate(THEMES):
        output = atlas.crop((index * HUD_FRAME_WIDTH, 0,
                             (index + 1) * HUD_FRAME_WIDTH, HUD_FRAME_HEIGHT))
        output.putpixel((HUD_FRAME_WIDTH - 1, HUD_FRAME_HEIGHT - 1), (255, 255, 255, 1))
        save_png(output, TEXTURES / f"frame-hud-{theme}.png")


def generate_currency_icons() -> None:
    for currency in ("red", "blue", "neutral", "dark"):
        source = ITEM_SOURCE / f"currency_{currency}.png"
        if not source.is_file():
            raise FileNotFoundError(f"Missing canonical currency icon: {source}")
        save_png(centered_sprite(Image.open(source), 46),
                 TEXTURES / f"currency-{currency}.png")


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
    atlas = Image.new(
        "RGBA", (columns * cell_width, rows * cell_height), (0, 0, 0, 0))
    if not TEXT_FONT_SOURCE.is_file():
        raise FileNotFoundError(f"Missing reproducible IceSMP HUD text font: {TEXT_FONT_SOURCE}")
    # Inter SemiBold is rasterized directly into the high-density atlas. Preserving
    # subpixel alpha is essential here: shrinking first to a 5px binary glyph is what
    # made the previous typeface look broken after Minecraft's GUI scaling.
    font = ImageFont.truetype(TEXT_FONT_SOURCE, size=9 * TEXT_OVERSAMPLE)
    _, font_descent = font.getmetrics()
    baseline_y = cell_height - font_descent - TEXT_OVERSAMPLE // 2
    for index, char in enumerate(padded):
        if index < len(unique):
            x = (index % columns) * cell_width
            y = (index // columns) * cell_height
            box = font.getbbox(char, anchor="ls")
            width = max(0, box[2] - box[0])
            height = max(0, box[3] - box[1])
            if width > 0 and height > 0:
                glyph = Image.new("L", (width, height), 0)
                glyph_draw = ImageDraw.Draw(glyph)
                glyph_draw.text((-box[0], -box[1]), char, font=font, fill=255, anchor="ls")
                maximum_width = cell_width - TEXT_OVERSAMPLE // 2
                if width > maximum_width:
                    # Wide glyphs (M/W/Ő) may be condensed horizontally, but their
                    # vertical metrics must never shrink relative to E/N/H.
                    glyph = glyph.resize((maximum_width, height), Image.Resampling.LANCZOS)
                glyph = glyph.point(lambda alpha: min(255, round(alpha * 1.18)))
                colored = Image.new("RGBA", glyph.size, (239, 247, 252, 255))
                colored.putalpha(glyph)
                atlas.alpha_composite(
                    colored,
                    (x + (cell_width - glyph.width) // 2,
                     y + baseline_y + box[1]))
            atlas.putpixel(
                (x + cell_width - 1, y + cell_height - 1),
                (255, 255, 255, 1))
    TEXTURES.mkdir(parents=True, exist_ok=True)
    save_png(atlas, TEXTURES / "text-atlas.png")
    return ([padded[row * columns:(row + 1) * columns] for row in range(rows)],
            TEXT_LOGICAL_WIDTH, TEXT_LOGICAL_HEIGHT)


def generate_segments() -> None:
    TEXTURES.mkdir(parents=True, exist_ok=True)
    colors = {
        "segment-track.png": ((8, 12, 17, 255), (52, 65, 80, 255)),
        "segment-fill.png": ((45, 162, 190, 255), (210, 244, 255, 255)),
        "segment-fill-warm.png": ((176, 48, 36, 255), (255, 168, 76, 255)),
        "segment-fill-gold.png": ((126, 88, 28, 255), (250, 219, 119, 255)),
    }
    for name, (base, highlight) in colors.items():
        image = Image.new("RGBA", (12, 3), base)
        draw = ImageDraw.Draw(image)
        draw.line((1, 0, 10, 0), fill=highlight)
        draw.point((0, 0), fill=(0, 0, 0, 0))
        save_png(image, TEXTURES / name)

        metric_name = name.replace("segment-", "metric-")
        metric = Image.new("RGBA", (7, 5), base)
        metric_draw = ImageDraw.Draw(metric)
        metric_draw.line((1, 0, 5, 0), fill=highlight)
        metric_draw.point((0, 0), fill=(0, 0, 0, 0))
        save_png(metric, TEXTURES / metric_name)


def generate_wallet_strip() -> None:
    for name, outline, height, fill_alpha in (
            ("wallet-strip.png", (95, 201, 180, 155), 42, 158),
            ("detail-strip.png", (115, 142, 178, 150), 22, 148)):
        image = Image.new("RGBA", (HUD_FRAME_WIDTH, height), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        draw.rounded_rectangle((2, 2, 237, height - 3), radius=5,
                               fill=(6, 10, 15, fill_alpha), outline=outline, width=1)
        draw.line((8, 3, 231, 3),
                  fill=(outline[0], outline[1], outline[2], 70))
        if name == "wallet-strip.png":
            draw.line((119, 3, 119, height - 4), fill=(outline[0], outline[1], outline[2], 80))
            draw.line((3, 21, 236, 21), fill=(outline[0], outline[1], outline[2], 80))
        else:
            middle = height // 2
            draw.polygon(((4, middle), (7, middle - 3), (10, middle), (7, middle + 3)),
                         outline=(outline[0], outline[1], outline[2], 120))
            draw.polygon(((235, middle), (232, middle - 3), (229, middle), (232, middle + 3)),
                         outline=(outline[0], outline[1], outline[2], 120))
        image.putpixel((HUD_FRAME_WIDTH - 1, height - 1), (255, 255, 255, 1))
        save_png(image, TEXTURES / name)


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
const float HUD_LAYOUT_SCALES[16] = float[16](0.75, 0.90, 1.00, 1.15, 1.25, 1.40, 1.60, 1.80,
        2.00, 2.20, 2.40, 2.60, 2.80, 3.00, 3.25, 3.50);
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
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
    float responsiveScale = clamp(min(ScreenSize.x / 2560.0, ScreenSize.y / 1440.0), 0.65, 1.5);
    vec2 hudScale = vec2(responsiveScale) * ui / ScreenSize;
    bool hudGlyph = false;
    bool topLeft = false;
    float layoutScale = 1.0;
    float layoutYOffset = 0.0;
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    if (pos.y >= ui.y && ProjMat[3].x == -1) {
        int bit = int(pos.y) >> HEIGHT_BIT;
        if (((bit >> MAX_BIT) & 1) == 1) {
            int id = bit - (1 << MAX_BIT);
            hudGlyph = true;
            topLeft = id >= 11 && id <= 15;
            ivec3 packedColor = ivec3(round(Color.rgb * 255.0));
            int layoutCode = (packedColor.r & 15) | ((packedColor.g & 15) << 4)
                    | ((packedColor.b & 15) << 8) | ((packedColor.b & 16) << 8)
                    | ((packedColor.r & 16) << 9);
            layoutYOffset = float((layoutCode & 1023) - 512);
            layoutScale = HUD_LAYOUT_SCALES[(layoutCode >> 10) & 15];
            vec3 visualColor = vec3((packedColor & ivec3(224, 240, 224))
                    + ivec3(16, 8, 16)) / 255.0;
            vertexColor = vec4(min(visualColor, vec3(1.0)), Color.a)
                    * texelFetch(Sampler2, UV2 / 16, 0);
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
            else if (id == 11) layer = 1;
            else if (id == 12) layer = 2;
            else if (id == 13) layer = 3;
            else if (id == 14) layer = 4;
            else if (id == 15) { layer = 5; outline = true; }
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
    if (hudGlyph) {
        vec2 selectedHudScale = hudScale * layoutScale;
        if (topLeft) {
            clipPosition.x = -clipPosition.w + clipPosition.x * selectedHudScale.x;
            clipPosition.y = clipPosition.w
                    + (clipPosition.y - clipPosition.w) * selectedHudScale.y
                    - layoutYOffset * responsiveScale * layoutScale
                    * 2.0 * clipPosition.w / ScreenSize.y;
        } else {
            clipPosition.x = clipPosition.w + clipPosition.x * selectedHudScale.x;
            clipPosition.y = clipPosition.w
                    + (clipPosition.y - clipPosition.w) * selectedHudScale.y
                    - layoutYOffset * 2.0 * clipPosition.w / ScreenSize.y;
        }
    }
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


def generate_layout_preview(text_rows: list[str]) -> None:
    """Render the reviewed logical grid without requiring a Minecraft client."""
    frame_y = HUD_Y["frame"]
    preview_scale = 3
    canvas = Image.new(
        "RGBA", (HUD_FRAME_WIDTH * preview_scale, 225 * preview_scale), (8, 11, 16, 255))

    def scaled(image: Image.Image, width: int, height: int,
               resampling: Image.Resampling = Image.Resampling.NEAREST) -> Image.Image:
        return image.resize((width * preview_scale, height * preview_scale), resampling)

    canvas.alpha_composite(
        scaled(Image.open(TEXTURES / "frame-hud-red.png").convert("RGBA"), 240, 160), (0, 0))
    canvas.alpha_composite(
        scaled(Image.open(TEXTURES / "detail-strip.png").convert("RGBA"), 240, 22),
        (0, (178 - frame_y) * preview_scale))
    canvas.alpha_composite(
        scaled(Image.open(TEXTURES / "wallet-strip.png").convert("RGBA"), 240, 42),
        (0, (201 - frame_y) * preview_scale))

    atlas = Image.open(TEXTURES / "text-atlas.png").convert("RGBA")
    glyphs: dict[str, Image.Image] = {}
    for row, characters in enumerate(text_rows):
        for column, char in enumerate(characters):
            glyphs[char] = atlas.crop((
                column * TEXT_LOGICAL_WIDTH * TEXT_OVERSAMPLE,
                row * TEXT_LOGICAL_HEIGHT * TEXT_OVERSAMPLE,
                (column + 1) * TEXT_LOGICAL_WIDTH * TEXT_OVERSAMPLE,
                (row + 1) * TEXT_LOGICAL_HEIGHT * TEXT_OVERSAMPLE,
            )).resize((TEXT_LOGICAL_WIDTH * preview_scale,
                       TEXT_LOGICAL_HEIGHT * preview_scale), Image.Resampling.LANCZOS)

    def paste_text(value: str, x: int, anchor_y: int, color: tuple[int, int, int]) -> None:
        for index, char in enumerate(value):
            glyph = glyphs.get(char, glyphs.get("?"))
            if glyph is None:
                continue
            colored = Image.new("RGBA", glyph.size, (*color, 255))
            colored.putalpha(glyph.getchannel("A"))
            canvas.alpha_composite(
                colored,
                ((x + index * (TEXT_LOGICAL_WIDTH + 1)) * preview_scale,
                 (anchor_y - 9 - frame_y) * preview_scale))

    def paste_sprite(name: str, x: int, anchor_y: int, size: int) -> None:
        sprite = scaled(Image.open(TEXTURES / name).convert("RGBA"), size, size,
                        Image.Resampling.LANCZOS)
        canvas.alpha_composite(sprite, (x * preview_scale, (anchor_y - frame_y) * preview_scale))

    def paste_bar(prefix: str, x: int, anchor_y: int, advance: int,
                  active: int, fill_suffix: str = "fill") -> None:
        track_source = Image.open(TEXTURES / f"{prefix}-track.png").convert("RGBA")
        fill_source = Image.open(TEXTURES / f"{prefix}-{fill_suffix}.png").convert("RGBA")
        track = scaled(track_source, track_source.width, track_source.height)
        fill = scaled(fill_source, fill_source.width, fill_source.height)
        for index in range(12):
            position = ((x + index * advance) * preview_scale,
                        (anchor_y - frame_y) * preview_scale)
            canvas.alpha_composite(track, position)
            if index < active:
                canvas.alpha_composite(fill, position)

    paste_sprite("class-warrior.png", 18, HUD_Y["class_icon"], 36)
    paste_sprite("mechanic-warrior-battle_tempo-active.png", 20, HUD_Y["mechanic_icon"], 14)
    paste_sprite("mechanic-warrior-guard-active.png", 141, HUD_Y["mechanic_icon"], 14)
    for index in range(5):
        paste_sprite("charge-ready.png", 20 + index * 12, HUD_Y["charge"], 10)
    paste_text("Harcos", 64, HUD_Y["header"], (119, 221, 242))
    paste_text("Berserker • Vörös Rend", 64, HUD_Y["subheader"], (199, 212, 234))
    level = "48"
    paste_text(level, HUD_X["level_center"] - len(level) * (TEXT_LOGICAL_WIDTH + 1) // 2,
               HUD_Y["header"], (234, 247, 255))
    paste_text("Düh 82/100", HUD_X["resource_text"], HUD_Y["resource_text"], (199, 212, 234))
    paste_text("Fő 72", 37, HUD_Y["mechanic_text"], (119, 221, 242))
    paste_text("Spec 43", 158, HUD_Y["mechanic_text"], (199, 212, 234))
    paste_text("Harc • Aktív", 141, HUD_Y["state"], (199, 212, 234))
    paste_text("Tűz 72", 20, HUD_Y["detail_text"], (199, 212, 234))
    paste_text("Fagy 48", 86, HUD_Y["detail_text"], (199, 212, 234))
    paste_text("Arkán 31", 152, HUD_Y["detail_text"], (199, 212, 234))
    event = "Vérhold • RAID • Világboss"
    paste_text(event, 120 - len(event) * (TEXT_LOGICAL_WIDTH + 1) // 2,
               HUD_Y["event_text"], (240, 216, 141))
    paste_bar("segment", HUD_X["resource_bar"], HUD_Y["resource_bar"], 13, 10)
    paste_bar("metric", HUD_X["primary_metric_bar"], HUD_Y["metric_bar"], 8, 9)
    paste_bar("metric", HUD_X["secondary_metric_bar"], HUD_Y["metric_bar"],
              8, 5, "fill-gold")

    wallet = (("currency-neutral.png", "Creutzér 12.8k", 8, HUD_Y["wallet_icon"],
               HUD_Y["wallet_text"], (240, 216, 141)),
              ("currency-red.png", "Parals 840", 128, HUD_Y["wallet_icon"],
               HUD_Y["wallet_text"], (199, 212, 234)),
              ("currency-blue.png", "Hópihér 319", 8, HUD_Y["wallet_lower_icon"],
               HUD_Y["wallet_lower_text"], (199, 212, 234)),
              ("currency-dark.png", "Csontveret 64", 128, HUD_Y["wallet_lower_icon"],
               HUD_Y["wallet_lower_text"], (199, 212, 234)))
    for icon, label, x, icon_y, text_y, color in wallet:
        paste_sprite(icon, x, icon_y, 15)
        paste_text(label, x + 17, text_y, color)

    target = ROOT / "build" / "reports" / "icesmp-hud" / "layout-preview.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    save_png(canvas, target)

    compact_wallet_top = (COMPACT_WALLET_ANCHOR_Y - frame_y) * preview_scale
    detailed_wallet_top = (201 - frame_y) * preview_scale
    compact = Image.new("RGBA", (HUD_FRAME_WIDTH * preview_scale,
                                  (160 + 42 + 1) * preview_scale), (8, 11, 16, 255))
    compact.alpha_composite(canvas.crop((0, 0, canvas.width, 160 * preview_scale)), (0, 0))
    compact.alpha_composite(canvas.crop((0, detailed_wallet_top, canvas.width,
                                         detailed_wallet_top + 42 * preview_scale)),
                            (0, compact_wallet_top))
    save_png(compact, target.with_name("layout-preview-compact.png"))


def main() -> None:
    validate_static_icon_sources()
    generate_frames()
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
        provider(f"frame-hud-{theme}.png", chr(0xE100 + index), 4, HUD_Y["frame"], HUD_FRAME_HEIGHT)
        for index, theme in enumerate(THEMES)
    ])
    write_font("wallet_panel", [provider("wallet-strip.png", chr(0xE105), 4, 201, 42)])
    write_font("wallet_panel_compact", [
        provider("wallet-strip.png", chr(0xE105), 4, COMPACT_WALLET_ANCHOR_Y, 42)
    ])
    write_font("detail_panel", [provider("detail-strip.png", chr(0xE106), 4, 178, 22)])
    write_font("class_icon", [
        provider(f"class-{class_id}.png", chr(0xE110 + index), 8, HUD_Y["class_icon"], 36)
        for index, class_id in enumerate(CLASS_GLYPHS)
    ])
    write_font("utility", [
        provider("icon-money.png", chr(0xE130), 8, 55, 15),
        provider("icon-event.png", chr(0xE131), 8, HUD_Y["event_icon"], 15),
        provider("icon-level.png", chr(0xE132), 8, HUD_Y["header"], 15),
    ])
    write_font("runes", [
        provider(f"rune-{kind}-{state}.png", chr(0xE140 + kind_index * 4 + state_index),
                 8, HUD_Y["runes"], 18)
        for kind_index, kind in enumerate(RUNE_KINDS)
        for state_index, state in enumerate(RUNE_STATES)
    ])
    write_font("runes_compact", [
        provider(f"rune-{kind}-{state}.png", chr(0xE140 + kind_index * 4 + state_index),
                 8, HUD_Y["metric_bar"], 12)
        for kind_index, kind in enumerate(RUNE_KINDS)
        for state_index, state in enumerate(RUNE_STATES)
    ])
    write_font("runes_panel", [
        provider(f"rune-{kind}-{state}.png", chr(0xE140 + kind_index * 4 + state_index),
                 8, 91, 18)
        for kind_index, kind in enumerate(RUNE_KINDS)
        for state_index, state in enumerate(RUNE_STATES)
    ])
    write_font("currency", [
        provider(f"currency-{currency}.png", chr(0xE160 + index), 8, HUD_Y["wallet_icon"], 15)
        for index, currency in enumerate(("red", "blue", "neutral", "dark"))
    ])
    write_font("currency_lower", [
        provider(f"currency-{currency}.png", chr(0xE160 + index), 8, HUD_Y["wallet_lower_icon"], 15)
        for index, currency in enumerate(("red", "blue", "neutral", "dark"))
    ])
    write_font("currency_compact", [
        provider(f"currency-{currency}.png", chr(0xE160 + index), 8,
                 HUD_Y["wallet_icon"] + COMPACT_WALLET_ANCHOR_DELTA, 15)
        for index, currency in enumerate(("red", "blue", "neutral", "dark"))
    ])
    write_font("currency_compact_lower", [
        provider(f"currency-{currency}.png", chr(0xE160 + index), 8,
                 HUD_Y["wallet_lower_icon"] + COMPACT_WALLET_ANCHOR_DELTA, 15)
        for index, currency in enumerate(("red", "blue", "neutral", "dark"))
    ])
    write_font("charges", [
        provider("charge-ready.png", chr(0xE170), 8, HUD_Y["charge"], 10),
        provider("charge-spent.png", chr(0xE171), 8, HUD_Y["charge"], 10),
    ])
    mechanic_providers = [
        provider(
            f"mechanic-{class_id}-{mechanic_id}-{variant}.png",
            chr(0xE200 + mechanic_index * len(MECHANIC_VARIANTS) + variant_index),
            8, HUD_Y["mechanic_icon"], 14,
        )
        for mechanic_index, (class_id, mechanic_id) in enumerate(MECHANICS)
        for variant_index, variant in enumerate(MECHANIC_VARIANTS)
    ]
    write_font("mechanic_icons", mechanic_providers)
    write_font("mechanic_slots", [
        {**entry, "ascent": encoded_ascent(8, HUD_Y["charge"]), "height": 10}
        for entry in mechanic_providers
    ])
    write_font("resource_segments", [
        provider("segment-track.png", chr(0xE180), 5, HUD_Y["resource_bar"], 3),
        provider("segment-fill.png", chr(0xE181), 6, HUD_Y["resource_bar"], 3),
        provider("segment-fill-warm.png", chr(0xE182), 6, HUD_Y["resource_bar"], 3),
        provider("segment-fill-gold.png", chr(0xE183), 6, HUD_Y["resource_bar"], 3),
    ])
    write_font("metric_segments", [
        provider("metric-track.png", chr(0xE180), 5, HUD_Y["metric_bar"], 5),
        provider("metric-fill.png", chr(0xE181), 6, HUD_Y["metric_bar"], 5),
        provider("metric-fill-warm.png", chr(0xE182), 6, HUD_Y["metric_bar"], 5),
        provider("metric-fill-gold.png", chr(0xE183), 6, HUD_Y["metric_bar"], 5),
    ])
    for name, y in {
        "text_header": HUD_Y["header"],
        "text_subheader": HUD_Y["subheader"],
        "text_resource": HUD_Y["resource_text"],
        "text_mechanic": HUD_Y["mechanic_text"],
        "text_state": HUD_Y["state"],
        "text_event": HUD_Y["event_text"],
        "text_detail": HUD_Y["detail_text"],
        "text_wallet": HUD_Y["wallet_text"],
        "text_wallet_lower": HUD_Y["wallet_lower_text"],
        "text_wallet_compact": HUD_Y["wallet_text"] + COMPACT_WALLET_ANCHOR_DELTA,
        "text_wallet_compact_lower": HUD_Y["wallet_lower_text"] + COMPACT_WALLET_ANCHOR_DELTA,
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
        "text_font": "Inter SemiBold",
        "text_source_resolution": [TEXT_LOGICAL_WIDTH * TEXT_OVERSAMPLE,
                                   TEXT_LOGICAL_HEIGHT * TEXT_OVERSAMPLE],
        "layout_y": HUD_Y,
        "layout_x": HUD_X,
        "maximum_bitmap_glyph_width": 256,
        "themes": list(THEMES),
        "classes": list(CLASSES),
        "mechanics": [f"{class_id}:{mechanic_id}" for class_id, mechanic_id in MECHANICS],
        "mechanic_variants": list(MECHANIC_VARIANTS),
        "fixed_segment_count": 12,
        "resource_segment_advance": 13,
        "metric_segment_advance": 8,
        "wallet_slots": 4,
        "wallet_columns": 2,
        "wallet_rows": 2,
        "detail_metrics_conditional": True,
        "compact_wallet_anchor_y": COMPACT_WALLET_ANCHOR_Y,
        "compact_wallet_anchor_delta": COMPACT_WALLET_ANCHOR_DELTA,
        "rune_panel_size": 18,
        "layout_color_payload_bits": 14,
        "layout_y_offset_range": [-512, 511],
        "layout_scale_variants": list(HUD_LAYOUT_SCALES),
        "vanilla_health_hidden": True,
        "vanilla_armor_hidden": True,
        "vanilla_food_hidden": True,
        "vanilla_oxygen_hidden": True,
        "hardcore_hearts_overridden": False,
    }
    (ASSETS / "hud-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    generate_contact_sheet()
    generate_layout_preview(text_rows)


if __name__ == "__main__":
    main()
