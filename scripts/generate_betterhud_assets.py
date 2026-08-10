#!/usr/bin/env python3
"""Deterministic IceSMP BetterHud assets with controlled antialiasing."""

from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "deploy" / "betterhud" / "assets" / "icesmp"
PREVIEW = ROOT / "deploy" / "betterhud" / "previews" / "icesmp-hud-contact-sheet.png"
CONCEPT = ROOT / "deploy" / "betterhud" / "previews" / "icesmp-hud-concept.png"
FRAME_SOURCE = ROOT / "deploy" / "betterhud" / "previews" / "icesmp-hud-runtime-source.png"
ICON_SOURCE = ROOT / "deploy" / "betterhud" / "previews" / "icesmp-hud-icons-source-v2.png"

THEMES = {
    "guest":   ("#111820", "#263443", "#77DDF2", "#A9B7C6"),
    "red":     ("#1B1212", "#46201B", "#E7683F", "#F0A05B"),
    "blue":    ("#0C1824", "#173850", "#8BE9FD", "#C8F4FF"),
    "neutral": ("#17150F", "#44351D", "#D6A74B", "#9CAB59"),
    "dark":    ("#111016", "#31283F", "#62D7CE", "#A08CC8"),
}

CLASS_GLYPHS = {
    "warrior": "sword", "evoker": "eye", "archer": "bow", "shaman": "totem",
    "monk": "fist", "paladin": "shield", "demon_hunter": "horns", "druid": "leaf",
    "priest": "cross", "death_knight": "skull", "assassin": "daggers",
    "warlock": "runes", "wizard": "hat",
}


def rgba(hex_color, alpha=255):
    value = hex_color.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def canvas(width, height):
    return Image.new("RGBA", (width, height), (0, 0, 0, 0))


def pixels(image):
    getter = getattr(image, "get_flattened_data", None)
    return getter() if getter is not None else image.getdata()


def save(image, name):
    OUT.mkdir(parents=True, exist_ok=True)
    image.save(OUT / name, optimize=True)


def panel(theme):
    base, raised, accent, light = map(rgba, THEMES[theme])
    image = canvas(320, 88)
    d = ImageDraw.Draw(image)
    # Shared compact hierarchy: identity header, resource channel, two mechanics, footer.
    d.rectangle((5, 3, 315, 84), fill=base)
    d.rectangle((7, 5, 313, 82), outline=raised, width=2)
    d.rectangle((52, 9, 306, 27), fill=raised)
    d.rectangle((52, 31, 306, 41), fill=(7, 10, 14, 255))
    d.rectangle((52, 45, 306, 58), fill=(8, 12, 17, 255))
    d.rectangle((52, 61, 306, 74), fill=(8, 12, 17, 255))
    d.rectangle((7, 77, 313, 82), fill=raised)
    d.rectangle((10, 8, 46, 44), fill=(6, 9, 13, 255), outline=accent, width=2)
    d.rectangle((10, 47, 46, 73), fill=raised, outline=light)
    d.rectangle((55, 34, 303, 38), fill=accent)
    # Different silhouettes and motifs, never just recolours.
    if theme == "red":
        for x, flip in ((0, 1), (319, -1)):
            points = [(x, 15), (x + 7 * flip, 8), (x + 3 * flip, 25),
                      (x + 10 * flip, 34), (x + 4 * flip, 48), (x, 42)]
            d.polygon(points, fill=accent)
        d.polygon([(145, 87), (160, 76), (175, 87)], fill=accent)
    elif theme == "blue":
        d.polygon([(0, 18), (12, 3), (10, 22), (22, 15), (15, 32)], fill=accent)
        d.polygon([(319, 10), (306, 0), (309, 19), (296, 15), (305, 31)], fill=light)
        d.polygon([(296, 87), (307, 72), (308, 84)], fill=accent)
    elif theme == "neutral":
        for x in (2, 307):
            d.rectangle((x, 13, x + 10, 72), fill=raised, outline=accent)
            d.rectangle((x + 3, 7, x + 7, 78), fill=accent)
        d.rectangle((137, 0, 183, 5), fill=raised, outline=accent)
    elif theme == "dark":
        for x, flip in ((0, 1), (319, -1)):
            d.polygon([(x, 9), (x + 10 * flip, 3), (x + 7 * flip, 18),
                       (x + 15 * flip, 27), (x + 5 * flip, 25)], fill=light)
            d.polygon([(x, 55), (x + 12 * flip, 62), (x + 5 * flip, 68)], fill=accent)
        for x in range(126, 196, 14):
            d.rectangle((x, 80, x + 5, 87), fill=accent)
    else:
        d.rectangle((1, 20, 6, 68), fill=accent)
        d.rectangle((314, 20, 319, 68), fill=accent)
    return image


def remove_magenta(image):
    """Chroma-key #FF00FF with edge decontamination while retaining controlled AA."""
    source = image.convert("RGBA")
    output_pixels = []
    for red, green, blue, _ in pixels(source):
        # The generator may preblend edge pixels with darker magenta; remove that spill too.
        if min(red, blue) > 115 and min(red, blue) - green > 65 and abs(red - blue) < 90:
            output_pixels.append((0, 0, 0, 0))
            continue
        distance = max(abs(red - 255), green, abs(blue - 255))
        if distance <= 10:
            output_pixels.append((0, 0, 0, 0))
            continue
        alpha = 255 if distance >= 80 else round((distance - 10) * 255 / 70)
        fraction = alpha / 255.0
        if fraction < 1.0:
            red = round((red - 255 * (1.0 - fraction)) / fraction)
            green = round(green / fraction)
            blue = round((blue - 255 * (1.0 - fraction)) / fraction)
        output_pixels.append((max(0, min(255, red)), max(0, min(255, green)),
                              max(0, min(255, blue)), alpha))
    source.putdata(output_pixels)
    return source


def extracted_frames():
    source = Image.open(FRAME_SOURCE).convert("RGBA")
    quadrants = {
        "red": (60, 40, 730, 475), "blue": (790, 40, 1490, 475),
        "neutral": (60, 525, 730, 950), "dark": (790, 520, 1500, 960),
    }
    result = {}
    for theme, box in quadrants.items():
        keyed = remove_magenta(source.crop(box))
        bbox = keyed.getchannel("A").getbbox()
        sprite = keyed.crop(bbox) if bbox else keyed
        sprite.thumbnail((670, 410), Image.Resampling.LANCZOS)
        framed = canvas(680, 420)
        framed.alpha_composite(sprite, ((680 - sprite.width) // 2, (420 - sprite.height) // 2))
        result[theme] = framed
    # Guest is a quiet, desaturated froststeel derivative, not a fifth faction identity.
    guest = result["blue"].copy()
    data = []
    for red, green, blue, alpha in pixels(guest):
        gray = round((red + green + blue) / 3)
        data.append((round(red * .35 + gray * .65), round(green * .55 + gray * .45),
                     round(blue * .65 + gray * .35), alpha))
    guest.putdata(data)
    result["guest"] = guest
    return result


def atlas_icons():
    """Extracts padded, transparent 64px sprites from the regular 6x5 chroma atlas."""
    source = remove_magenta(Image.open(ICON_SOURCE).convert("RGBA"))
    class_cells = {
        "warrior": (0, 0), "evoker": (1, 0), "archer": (2, 0),
        "shaman": (3, 0), "monk": (4, 0), "paladin": (5, 0),
        "demon_hunter": (0, 1), "druid": (1, 1), "priest": (2, 1),
        "death_knight": (3, 1), "assassin": (4, 1), "warlock": (5, 1),
        "wizard": (0, 2),
    }
    rune_cells = {
        ("blood", "ready"): (1, 2), ("blood", "regenerating"): (2, 2),
        ("blood", "spent"): (3, 2), ("blood", "locked"): (4, 2),
        ("frost", "ready"): (5, 2), ("frost", "regenerating"): (0, 3),
        ("frost", "spent"): (1, 3), ("frost", "locked"): (2, 3),
        ("death", "ready"): (3, 3), ("death", "regenerating"): (4, 3),
        ("death", "spent"): (5, 3), ("death", "locked"): (0, 4),
    }
    utility_cells = {"money": (1, 4), "level": (2, 4), "event": (3, 4)}

    def extract(cell, maximum):
        column, row = cell
        left = round(column * source.width / 6)
        right = round((column + 1) * source.width / 6)
        top = round(row * source.height / 5)
        bottom = round((row + 1) * source.height / 5)
        tile = source.crop((left, top, right, bottom))
        bbox = tile.getchannel("A").getbbox()
        sprite = tile.crop(bbox) if bbox else tile
        sprite.thumbnail((maximum, maximum), Image.Resampling.LANCZOS)
        output = canvas(64, 64)
        output.alpha_composite(sprite, ((64 - sprite.width) // 2, (64 - sprite.height) // 2))
        return output

    return ({name: extract(cell, 54) for name, cell in class_cells.items()},
            {key: extract(cell, 52) for key, cell in rune_cells.items()},
            {name: extract(cell, 50) for name, cell in utility_cells.items()})


def emblem(theme):
    _, raised, accent, light = map(rgba, THEMES[theme])
    image = canvas(24, 24)
    d = ImageDraw.Draw(image)
    d.polygon([(12, 1), (22, 6), (19, 19), (12, 23), (5, 19), (2, 6)],
              fill=raised, outline=accent)
    if theme == "red":
        d.polygon([(12, 4), (17, 12), (14, 11), (16, 18), (8, 17), (10, 12), (7, 13)], fill=light)
    elif theme == "blue":
        d.line((12, 4, 12, 20), fill=light, width=2); d.line((5, 12, 19, 12), fill=light, width=2)
        d.line((7, 7, 17, 17), fill=accent); d.line((17, 7, 7, 17), fill=accent)
    elif theme == "neutral":
        d.rectangle((10, 5, 13, 18), fill=light); d.line((6, 9, 17, 16), fill=accent, width=2)
        d.line((18, 8, 7, 17), fill=accent, width=2)
    elif theme == "dark":
        d.ellipse((6, 5, 17, 16), fill=light); d.rectangle((8, 14, 15, 20), fill=accent)
        d.rectangle((8, 9, 10, 11), fill=raised); d.rectangle((14, 9, 16, 11), fill=raised)
    else:
        d.polygon([(12, 4), (15, 10), (20, 12), (15, 14), (12, 20), (9, 14), (4, 12), (9, 10)], fill=light)
    return image


def class_icon(name, glyph):
    image = canvas(24, 24)
    d = ImageDraw.Draw(image)
    ink, shade, accent = rgba("#EAF7FF"), rgba("#71809A"), rgba("#77DDF2")
    d.rectangle((1, 1, 22, 22), fill=(9, 13, 19, 255), outline=shade)
    if glyph == "sword": d.line((6, 18, 17, 5), fill=ink, width=3); d.line((6, 14, 10, 18), fill=accent, width=2)
    elif glyph == "eye": d.polygon([(4, 12), (9, 7), (15, 7), (20, 12), (15, 17), (9, 17)], outline=accent); d.rectangle((11, 10, 13, 14), fill=ink)
    elif glyph == "bow": d.arc((5, 3, 18, 20), 270, 90, fill=accent, width=2); d.line((15, 4, 15, 20), fill=ink)
    elif glyph == "totem": d.rectangle((9, 4, 15, 19), fill=accent); d.line((5, 8, 19, 8), fill=ink, width=2)
    elif glyph == "fist": d.rectangle((6, 9, 17, 17), fill=ink); d.rectangle((8, 5, 10, 11), fill=accent); d.rectangle((12, 4, 14, 11), fill=accent)
    elif glyph == "shield": d.polygon([(12, 3), (19, 6), (17, 17), (12, 21), (7, 17), (5, 6)], fill=accent); d.line((12, 6, 12, 17), fill=ink, width=2)
    elif glyph == "horns": d.arc((3, 3, 12, 15), 90, 250, fill=accent, width=3); d.arc((12, 3, 21, 15), 290, 90, fill=accent, width=3)
    elif glyph == "leaf": d.polygon([(5, 17), (8, 7), (19, 4), (16, 15)], fill=accent); d.line((6, 19, 17, 7), fill=ink)
    elif glyph == "cross": d.rectangle((10, 4, 14, 20), fill=ink); d.rectangle((5, 9, 19, 13), fill=ink)
    elif glyph == "skull": d.rectangle((6, 5, 18, 16), fill=accent); d.rectangle((9, 16, 15, 20), fill=shade); d.rectangle((8, 9, 10, 11), fill=ink); d.rectangle((14, 9, 16, 11), fill=ink)
    elif glyph == "daggers": d.line((5, 5, 18, 18), fill=ink, width=2); d.line((18, 5, 5, 18), fill=accent, width=2)
    elif glyph == "runes": d.polygon([(12, 4), (19, 12), (12, 20), (5, 12)], outline=accent, width=2); d.rectangle((10, 10, 14, 14), fill=ink)
    else: d.polygon([(5, 16), (9, 7), (14, 4), (18, 16)], fill=accent); d.rectangle((4, 16, 19, 19), fill=ink)
    return image


def rune(kind, state, progress=50):
    colors = {"blood": "#E7683F", "frost": "#8BE9FD", "death": "#9CAB59"}
    accent = rgba(colors[kind]); muted = rgba("#3A404B"); ink = accent if state == "ready" else muted
    image = canvas(16, 16); d = ImageDraw.Draw(image)
    d.rectangle((1, 1, 14, 14), fill=(8, 11, 15, 255), outline=ink)
    if kind == "blood": d.polygon([(8, 3), (12, 9), (10, 13), (6, 13), (4, 9)], fill=ink)
    elif kind == "frost":
        d.line((8, 3, 8, 13), fill=ink, width=2); d.line((3, 8, 13, 8), fill=ink, width=2)
        d.line((4, 4, 12, 12), fill=ink); d.line((12, 4, 4, 12), fill=ink)
    else:
        d.rectangle((5, 5, 11, 11), fill=ink); d.rectangle((7, 11, 9, 13), fill=ink)
        d.point((6, 7), fill=(8, 11, 15, 255)); d.point((10, 7), fill=(8, 11, 15, 255))
    if state == "regenerating":
        width = max(1, min(12, round(progress * 12 / 100)))
        d.rectangle((2, 13, 1 + width, 14), fill=accent)
    elif state == "locked":
        d.rectangle((5, 7, 11, 12), fill=muted); d.rectangle((6, 4, 10, 8), outline=muted)
    return image


def bar(name, color, width=328):
    image = canvas(width, 10); d = ImageDraw.Draw(image)
    if name == "track": d.rectangle((0, 0, width - 1, 9), fill=(7, 10, 14, 255), outline=rgba("#354355"), width=2)
    else: d.rectangle((0, 0, width - 1, 9), fill=rgba(color)); d.line((0, 0, width - 1, 0), fill=rgba("#EAF7FF"), width=2)
    return image


def rune_progress():
    image = canvas(48, 6)
    ImageDraw.Draw(image).rectangle((0, 0, 47, 5), fill=rgba("#EAF7FF"))
    return image


def charge_pip(ready):
    """Tintable 32px mechanic pip; faction colour is applied by BetterHud at render time."""
    scale = 4
    image = canvas(32 * scale, 32 * scale)
    d = ImageDraw.Draw(image)
    frame = rgba("#71809A")
    fill = rgba("#EAF7FF") if ready else rgba("#273241")
    d.polygon([(64, 6), (121, 64), (64, 121), (7, 64)],
              fill=rgba("#0A0F16"), outline=frame, width=7)
    d.polygon([(64, 27), (101, 64), (64, 101), (27, 64)], fill=fill)
    if ready:
        d.polygon([(64, 38), (90, 64), (64, 90), (38, 64)], fill=rgba("#FFFFFF"))
    else:
        d.line((42, 64, 86, 64), fill=rgba("#111820"), width=7)
    return image.resize((32, 32), Image.Resampling.LANCZOS)


def utility_icon(kind):
    """64px IceSMP utility mark, drawn large and downsampled for controlled HUD AA."""
    scale = 4
    image = canvas(64 * scale, 64 * scale)
    d = ImageDraw.Draw(image)
    gold, ice, pale, shadow = map(rgba, ("#D6A74B", "#77DDF2", "#EAF7FF", "#111820"))
    if kind == "money":
        d.ellipse((32, 32, 224, 224), fill=rgba("#3A2A12"), outline=gold, width=14)
        d.ellipse((52, 52, 204, 204), outline=rgba("#F0D88D"), width=8)
        d.polygon([(128, 66), (165, 108), (145, 178), (111, 178), (91, 108)],
                  fill=gold, outline=pale)
        d.line((128, 78, 128, 169), fill=shadow, width=8)
    elif kind == "event":
        d.polygon([(128, 18), (208, 94), (180, 218), (76, 218), (48, 94)],
                  fill=rgba("#173850"), outline=ice, width=12)
        d.polygon([(128, 42), (174, 104), (151, 193), (105, 193), (82, 104)], fill=ice)
        d.line((128, 50, 128, 187), fill=pale, width=8)
        d.line((94, 112, 162, 112), fill=pale, width=7)
    else:
        d.polygon([(128, 20), (213, 70), (191, 178), (128, 232), (65, 178), (43, 70)],
                  fill=rgba("#263443"), outline=ice, width=12)
        d.polygon([(128, 55), (154, 109), (211, 117), (168, 157), (178, 213),
                   (128, 185), (78, 213), (88, 157), (45, 117), (102, 109)], fill=pale)
    return image.resize((64, 64), Image.Resampling.LANCZOS)


def popup_frame(theme):
    """Faction-specific proc toast geometry; motifs differ as well as palette."""
    scale = 3
    width, height = 300 * scale, 72 * scale
    base, raised, accent, light = map(rgba, THEMES[theme])
    image = canvas(width, height)
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((9, 12, width - 10, height - 13), radius=24,
                        fill=base, outline=raised, width=8)
    d.line((55, 25, width - 55, 25), fill=accent, width=5)
    d.line((55, height - 26, width - 55, height - 26), fill=raised, width=4)
    if theme == "red":
        d.polygon([(8, 48), (47, 7), (37, 64), (75, 108), (38, 167), (48, 209), (8, 172)], fill=accent)
        d.polygon([(width - 8, 48), (width - 47, 7), (width - 37, 64),
                   (width - 75, 108), (width - 38, 167), (width - 48, 209),
                   (width - 8, 172)], fill=accent)
    elif theme == "blue":
        d.polygon([(5, 107), (47, 23), (42, 91), (86, 62), (61, 130), (91, 174), (37, 155)], fill=light)
        d.polygon([(width - 5, 107), (width - 47, 23), (width - 42, 91),
                   (width - 86, 62), (width - 61, 130), (width - 91, 174),
                   (width - 37, 155)], fill=accent)
    elif theme == "neutral":
        d.rectangle((5, 51, 35, 165), fill=raised, outline=accent, width=6)
        d.rectangle((width - 35, 51, width - 5, 165), fill=raised, outline=accent, width=6)
        for x in (18, width - 18):
            d.ellipse((x - 9, 96, x + 9, 114), fill=light)
    elif theme == "dark":
        d.polygon([(7, 63), (50, 15), (40, 79), (79, 108), (36, 126), (61, 192), (8, 156)], fill=light)
        d.polygon([(width - 7, 63), (width - 50, 15), (width - 40, 79),
                   (width - 79, 108), (width - 36, 126), (width - 61, 192),
                   (width - 8, 156)], fill=accent)
    else:
        d.polygon([(8, 108), (42, 63), (42, 153)], fill=accent)
        d.polygon([(width - 8, 108), (width - 42, 63), (width - 42, 153)], fill=accent)
    return image.resize((300, 72), Image.Resampling.LANCZOS)


def contact_sheet(assets):
    sheet = Image.new("RGBA", (700, 660), (18, 20, 24, 255))
    positions = {"red": (8, 8), "blue": (356, 8), "neutral": (8, 220), "dark": (356, 220)}
    for theme, position in positions.items():
        thumb = assets[f"frame-{theme}"].copy(); thumb.thumbnail((336, 204), Image.Resampling.LANCZOS)
        sheet.alpha_composite(thumb, position)
    x, y = 14, 438
    for name in CLASS_GLYPHS:
        icon = assets[f"class-{name}"].resize((40, 40), Image.Resampling.LANCZOS)
        sheet.alpha_composite(icon, (x, y)); x += 50
    x, y = 14, 482
    for state in ("ready", "spent", "regenerating", "locked"):
        for kind in ("blood", "frost", "death"):
            icon = assets[f"rune-{kind}-{state}"].resize((28, 28), Image.Resampling.LANCZOS)
            sheet.alpha_composite(icon, (x, y)); x += 32
        x += 10
    for kind in ("money", "event", "level"):
        sheet.alpha_composite(assets[f"icon-{kind}"].resize((32, 32), Image.Resampling.LANCZOS), (x, y))
        x += 40
    popup = assets["popup-dark"].copy(); popup.thumbnail((300, 72), Image.Resampling.LANCZOS)
    sheet.alpha_composite(popup, (14, 530))
    sheet.alpha_composite(assets["class-death_knight"].resize((48, 48), Image.Resampling.LANCZOS), (34, 542))
    sheet.alpha_composite(assets["metric-track"].resize((156, 10), Image.Resampling.LANCZOS), (330, 550))
    sheet.alpha_composite(assets["metric-fill"].resize((110, 10), Image.Resampling.LANCZOS), (330, 570))
    sheet.alpha_composite(assets["charge-ready"], (510, 546))
    sheet.alpha_composite(assets["charge-spent"], (550, 546))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True); sheet.save(PREVIEW, optimize=True)


def main():
    assets = {}
    frames = extracted_frames() if FRAME_SOURCE.is_file() else {theme: panel(theme) for theme in THEMES}
    if ICON_SOURCE.is_file():
        class_icons, rune_icons, utility_icons = atlas_icons()
    else:
        class_icons = {name: class_icon(name, glyph) for name, glyph in CLASS_GLYPHS.items()}
        rune_icons = {(kind, state): rune(kind, state) for kind in ("blood", "frost", "death")
                      for state in ("ready", "spent", "regenerating", "locked")}
        utility_icons = {kind: utility_icon(kind) for kind in ("money", "event", "level")}
    for theme in THEMES:
        assets[f"frame-{theme}"] = frames[theme]; save(assets[f"frame-{theme}"], f"frame-{theme}.png")
        # Minecraft bitmap-font providers are more reliable with a render-sized texture below
        # 256 px on each axis. Keep the high-resolution source above for regeneration/contact
        # sheets, but feed BetterHud this antialiased 204x126 runtime copy at scale 1.0.
        assets[f"frame-hud-{theme}"] = frames[theme].resize((204, 126), Image.Resampling.LANCZOS)
        save(assets[f"frame-hud-{theme}"], f"frame-hud-{theme}.png")
        assets[f"emblem-{theme}"] = emblem(theme); save(assets[f"emblem-{theme}"], f"emblem-{theme}.png")
    for name, glyph in CLASS_GLYPHS.items():
        assets[f"class-{name}"] = class_icons[name]; save(assets[f"class-{name}"], f"class-{name}.png")
    for kind in ("blood", "frost", "death"):
        for state in ("ready", "spent", "regenerating", "locked"):
            key = f"rune-{kind}-{state}"
            assets[key] = rune_icons[(kind, state)]; save(assets[key], key + ".png")
    for kind in ("money", "event", "level"):
        assets[f"icon-{kind}"] = utility_icons[kind]
        save(assets[f"icon-{kind}"], f"icon-{kind}.png")
    for theme in THEMES:
        assets[f"popup-{theme}"] = popup_frame(theme)
        save(assets[f"popup-{theme}"], f"popup-{theme}.png")
    save(bar("track", "#354355"), "resource-track.png")
    save(bar("fill", "#77DDF2"), "resource-fill.png")
    assets["metric-track"] = bar("track", "#354355", 156)
    assets["metric-fill"] = bar("fill", "#EAF7FF", 156)
    assets["metric-mini-track"] = bar("track", "#354355", 100)
    assets["metric-mini-fill"] = bar("fill", "#EAF7FF", 100)
    assets["charge-ready"] = charge_pip(True)
    assets["charge-spent"] = charge_pip(False)
    for key in ("metric-track", "metric-fill", "metric-mini-track", "metric-mini-fill",
                "charge-ready", "charge-spent"):
        save(assets[key], key + ".png")
    save(rune_progress(), "rune-progress.png")
    contact_sheet(assets)


if __name__ == "__main__":
    main()
