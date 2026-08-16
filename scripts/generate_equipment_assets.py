#!/usr/bin/env python3
"""Generate slot-correct worn textures and meaningful item-state sprites.

The detailed 64x64 inventory icons remain the colour and identity authority. Worn textures are
rebuilt on the fixed vanilla equipment UV nets, while state sprites preserve the original icon and
add a deterministic, visible state cue. Every operation uses nearest-neighbour pixels and binary
alpha so the generated files remain Minecraft-safe.
"""

from __future__ import annotations

import base64
import colorsys
import math
import zlib
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
ITEM_TEXTURES = ROOT / "resource-pack/assets/icesmp/textures/item"
EQUIPMENT_TEXTURES = ROOT / "resource-pack/assets/icesmp/textures/entity/equipment"


@dataclass(frozen=True)
class ArmorSpec:
    slot: str
    motif: str


ARMOR: dict[str, ArmorSpec] = {
    "eleftheria_fatyla": ArmorSpec("chest", "veil"),
    "ereklyeszilankos_banyasisak": ArmorSpec("head", "mining"),
    "esszencialt_vasvert": ArmorSpec("chest", "plate"),
    "fonix_tollkopeny": ArmorSpec("chest", "feather"),
    "glatziendorfi_jegvert": ArmorSpec("chest", "ice"),
    "gyemant_mellvert": ArmorSpec("chest", "gem"),
    "gyemant_sisak": ArmorSpec("head", "gem"),
    "halaszkalap": ArmorSpec("head", "hat"),
    "lancing": ArmorSpec("chest", "chain"),
    "lancnadrag": ArmorSpec("legs", "chain"),
    "loot_elit_pancel": ArmorSpec("chest", "corrupt"),
    "melysegi_korona": ArmorSpec("head", "crown"),
    "netherit_sisak": ArmorSpec("head", "dark_helmet"),
    "pancelozott_sisakrostely": ArmorSpec("head", "visor"),
    "rezvertezet_lablemez": ArmorSpec("legs", "copper"),
    "sarkanyvert_recept": ArmorSpec("chest", "dragon"),
    "szornyvert_mellveny": ArmorSpec("chest", "corrupt"),
    "teknos_sisak": ArmorSpec("head", "shell"),
    "vadbor_pancel": ArmorSpec("legs", "leather"),
    "vadolo_csizma": ArmorSpec("feet", "leather"),
    "vas_csizma": ArmorSpec("feet", "plate"),
    "vas_lablemez": ArmorSpec("legs", "plate"),
    "vas_sisak": ArmorSpec("head", "plate"),
    "viharjaro_csizma": ArmorSpec("feet", "storm"),
    "vizallo_csizma": ArmorSpec("feet", "leather"),
}

WINGS = (
    "relic_bone_wing",
    "relic_frost_wing",
    "relic_phoenix_wing",
    "relic_wander_wind",
)

PALETTE_OVERRIDES: dict[str, list[tuple[int, int, int, int]]] = {
    "eleftheria_fatyla": [(19, 12, 35, 255), (38, 25, 61, 255), (67, 43, 88, 255),
                           (211, 207, 145, 255), (35, 210, 199, 255)],
    "ereklyeszilankos_banyasisak": [(22, 17, 54, 255), (55, 39, 104, 255),
                                     (94, 72, 155, 255), (176, 246, 235, 255), (31, 210, 225, 255)],
    "esszencialt_vasvert": [(31, 54, 48, 255), (75, 112, 92, 255), (115, 157, 128, 255),
                             (190, 231, 192, 255), (76, 213, 190, 255)],
    "fonix_tollkopeny": [(78, 8, 5, 255), (154, 27, 4, 255), (224, 57, 3, 255),
                          (255, 190, 28, 255), (255, 104, 5, 255)],
    "glatziendorfi_jegvert": [(20, 47, 78, 255), (40, 96, 132, 255), (83, 161, 184, 255),
                               (207, 244, 232, 255), (80, 220, 232, 255)],
    "gyemant_mellvert": [(7, 70, 91, 255), (19, 153, 174, 255), (52, 207, 211, 255),
                          (187, 255, 229, 255), (37, 239, 226, 255)],
    "gyemant_sisak": [(7, 70, 91, 255), (19, 153, 174, 255), (52, 207, 211, 255),
                       (187, 255, 229, 255), (37, 239, 226, 255)],
    "halaszkalap": [(54, 29, 13, 255), (116, 65, 25, 255), (164, 103, 42, 255),
                     (222, 174, 76, 255), (194, 142, 36, 255)],
    "lancing": [(28, 29, 35, 255), (74, 76, 83, 255), (131, 132, 137, 255),
                 (214, 216, 216, 255), (170, 179, 188, 255)],
    "loot_elit_pancel": [(4, 25, 34, 255), (8, 50, 62, 255), (18, 83, 91, 255),
                          (90, 184, 177, 255), (25, 223, 208, 255)],
    "melysegi_korona": [(35, 13, 52, 255), (89, 30, 111, 255), (145, 62, 157, 255),
                         (221, 213, 121, 255), (28, 218, 199, 255)],
    "netherit_sisak": [(16, 14, 20, 255), (49, 43, 53, 255), (87, 76, 85, 255),
                        (205, 161, 63, 255), (232, 190, 60, 255)],
    "pancelozott_sisakrostely": [(9, 62, 72, 255), (42, 129, 137, 255),
                                  (98, 198, 189, 255), (208, 251, 219, 255), (42, 221, 220, 255)],
    "rezvertezet_lablemez": [(75, 34, 17, 255), (143, 66, 25, 255), (205, 103, 40, 255),
                              (245, 172, 73, 255), (77, 141, 89, 255)],
    "sarkanyvert_recept": [(27, 20, 24, 255), (74, 45, 42, 255), (135, 68, 46, 255),
                            (224, 164, 68, 255), (177, 38, 24, 255)],
    "szornyvert_mellveny": [(28, 8, 11, 255), (68, 17, 23, 255), (116, 29, 36, 255),
                             (211, 79, 72, 255), (225, 21, 36, 255)],
    "teknos_sisak": [(25, 48, 12, 255), (49, 99, 23, 255), (102, 157, 36, 255),
                      (180, 211, 66, 255), (135, 190, 41, 255)],
    "vadbor_pancel": [(48, 24, 14, 255), (102, 52, 28, 255), (150, 82, 43, 255),
                       (222, 176, 111, 255), (91, 193, 184, 255)],
    "vadolo_csizma": [(43, 29, 19, 255), (90, 57, 30, 255), (145, 91, 43, 255),
                       (214, 172, 101, 255), (45, 220, 216, 255)],
    "vas_csizma": [(31, 34, 41, 255), (88, 93, 104, 255), (147, 152, 161, 255),
                    (224, 226, 226, 255), (184, 195, 205, 255)],
    "vas_lablemez": [(31, 34, 41, 255), (88, 93, 104, 255), (147, 152, 161, 255),
                      (224, 226, 226, 255), (184, 195, 205, 255)],
    "vas_sisak": [(31, 34, 41, 255), (88, 93, 104, 255), (147, 152, 161, 255),
                   (224, 226, 226, 255), (184, 195, 205, 255)],
    "viharjaro_csizma": [(14, 21, 39, 255), (40, 61, 91, 255), (77, 108, 145, 255),
                          (177, 213, 230, 255), (78, 176, 240, 255)],
    "vizallo_csizma": [(45, 24, 14, 255), (105, 55, 25, 255), (157, 86, 37, 255),
                        (220, 159, 73, 255), (95, 198, 174, 255)],
    "relic_bone_wing": [(17, 27, 24, 255), (45, 65, 52, 255), (104, 127, 95, 255),
                         (211, 221, 180, 255), (84, 201, 177, 255)],
    "relic_frost_wing": [(18, 52, 92, 255), (55, 125, 176, 255), (104, 190, 219, 255),
                          (214, 251, 242, 255), (61, 226, 236, 255)],
    "relic_phoenix_wing": [(103, 8, 4, 255), (189, 28, 4, 255), (238, 67, 3, 255),
                            (255, 193, 36, 255), (255, 109, 5, 255)],
    "relic_wander_wind": [(35, 62, 103, 255), (91, 142, 191, 255), (159, 207, 228, 255),
                           (238, 253, 243, 255), (113, 217, 224, 255)],
    "arany_lopancel": [(91, 43, 4, 255), (172, 91, 10, 255), (229, 144, 18, 255),
                        (255, 231, 101, 255), (255, 181, 28, 255)],
    "gyemant_lopancel": [(8, 67, 83, 255), (20, 139, 158, 255), (65, 201, 199, 255),
                          (205, 255, 225, 255), (46, 235, 220, 255)],
    "vas_lopancel": [(35, 37, 44, 255), (91, 95, 104, 255), (145, 149, 158, 255),
                      (226, 226, 222, 255), (178, 190, 199, 255)],
    "jegsarkany_kantar": [(10, 54, 78, 255), (31, 111, 157, 255), (76, 161, 203, 255),
                           (199, 243, 235, 255), (71, 218, 224, 255)],
}
HORSE_BODY = ("arany_lopancel", "gyemant_lopancel", "vas_lopancel")
HORSE_SADDLE = ("jegsarkany_kantar",)

# Binary alpha masks extracted from the matching vanilla 1.21.11 equipment UV templates. Keeping
# the masks local makes generation deterministic and prevents a previous custom output from
# becoming the next run's accidental geometry source.
CANONICAL_MASKS: dict[str, tuple[tuple[int, int], str]] = {
    "wing": (
        (64, 32),
        "eNpjYMALGGGAgUxAvk50J1Cqf8AMGDT6GUeMfkYKDRjVT1kKHGT6GQZaPwOFRdAoGAW0AQAjCQDP",
    ),
    "horse_body": (
        (64, 64),
        "eNrtlksOgDAIRDv3v7SJiQoISKGpJnbcuOANduyvtaWlpaKwa/Ej+IxTmoepME9cqKHfs9L/zmtuQd5MJMb3Jq6O8q88/X3BOSd715bNMX+VV1SW9HRelH6HDzrN58HWfv/4B/GyIjzqk4e7nVf7P+VX5xHDMTO/dj25/B7xcf3V/ML90/uPy2d2OvrlrA+8WaHhIpGSQS8uDPpxZpDB31FLFtGT17RhFfYhLi6g4LdSnd8AaokEsQ==",
    ),
    "horse_saddle": (
        (64, 64),
        "eNrtk+0KgCAMRXff/6UjgqUy16aMIu75IZj3bOGHyCNQIpkz1ecjfl9Mh1f8K3cbO/3b3ajx4XzAQImPScqwrELT/ru+uRw9gr/4w6HF76D2gzOt7y9pwfsFLFZLvl9CCCGEEEK+ygFv3QD1",
    ),
}

BOWS = (
    "csontenyves_ijkar",
    "feszitett_szaru_ij",
    "kallan_szeletelo",
    "napfogyatkozas",
    "sarkanycsont_ij",
    "vadaszij",
    "vasfa_ij",
)
CROSSBOWS = (
    "celkereszt_szamszerij",
    "pyralingradi_ostrom_szamszerij",
    "pyralingradi_tuzkopo",
)
FISHING_RODS = (
    "bokic_horgaszbot",
    "egyszeru_horgaszbot",
    "legendas_horgaszbot",
    "melyvizi_horog",
    "mesteri_horgaszbot",
    "mestermuves_bot",
    "rezhorgany_horgaszbot",
    "tartos_horgaszbot",
    "uszokeszlet",
)
HANDHELD_ITEMS = (
    "bokic_aldasa",
    "ejszaka_pengeje",
    "ereklye_penge",
    "glatziendorfi_jegtoro",
    "gyemant_fejsze",
    "gyemant_kard",
    "haromagu_szigony",
    "kovilta_fejsze",
    "loot_nema_kiralyno",
    "loot_rozsdas_penge",
    "melytengeri_ereklyeszigony",
    "miinus_haragja",
    "netherit_csakany",
    "netherit_fejsze",
    "netherit_kard",
    "osi_ereklye_kiemeles",
    "relic_metelytepo",
    "runafenyes_csakany",
    "runakovacsolt_penge",
    "tarnasz_csakany_recept",
    "vasfejsze",
    "vaskard",
    "vasmuvek_csakanya",
    "verszavanna_agyara",
    "zhoris_langnyelve",
)
SHIELDS = (
    "bastya_pajzs_recept",
    "mefonott_pajzs",
    "pajzsdudor",
    "sarkanycsont_pajzs",
    "vasesszencias_pajzs",
)
TRIDENTS = ("bokic_aldasa", "haromagu_szigony", "melytengeri_ereklyeszigony")


def binary_alpha(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A").point(lambda value: 255 if value >= 128 else 0)
    rgba.putalpha(alpha)
    return rgba


def canonical_mask(name: str) -> Image.Image:
    size, encoded = CANONICAL_MASKS[name]
    raw = zlib.decompress(base64.b64decode(encoded))
    expected = size[0] * size[1]
    if len(raw) != expected:
        raise ValueError(f"Corrupt canonical equipment mask {name}: {len(raw)} != {expected}")
    return Image.frombytes("L", size, bytes(value * 255 for value in raw))


def icon(name: str) -> Image.Image:
    return binary_alpha(Image.open(ITEM_TEXTURES / f"{name}.png"))


def palette(name: str, size: int = 8) -> list[tuple[int, int, int, int]]:
    override = PALETTE_OVERRIDES.get(name)
    if override is not None:
        return override
    source = icon(name)
    opaque = [pixel[:3] for pixel in source.get_flattened_data() if pixel[3]]
    strip = Image.new("RGB", (len(opaque), 1))
    strip.putdata(opaque)
    quantized = strip.quantize(colors=size, method=Image.Quantize.MEDIANCUT).convert("RGB")
    counts = quantized.getcolors(maxcolors=len(opaque)) or []
    colours = [colour for _, colour in sorted(counts, reverse=True)]
    colours = list(dict.fromkeys(colours))
    while len(colours) < 4:
        colours.append(colours[-1] if colours else (127, 127, 127))

    def luminance(colour: tuple[int, int, int]) -> float:
        return 0.2126 * colour[0] + 0.7152 * colour[1] + 0.0722 * colour[2]

    ordered = sorted(colours, key=luminance)
    darkest = ordered[0]
    lightest = ordered[-1]
    base = ordered[len(ordered) // 2]
    mid = ordered[min(len(ordered) - 1, len(ordered) * 2 // 3)]
    accent = max(
        colours,
        key=lambda colour: colorsys.rgb_to_hsv(*(channel / 255 for channel in colour))[1]
        * (0.4 + colorsys.rgb_to_hsv(*(channel / 255 for channel in colour))[2]),
    )
    return [(*value, 255) for value in (darkest, base, mid, lightest, accent)]


def rect(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], colour: tuple[int, ...]) -> None:
    draw.rectangle((box[0], box[1], box[2] - 1, box[3] - 1), fill=colour)


def fill_head(draw: ImageDraw.ImageDraw, colours: list[tuple[int, ...]]) -> None:
    dark, base, mid, light, _ = colours
    rect(draw, (8, 0, 16, 8), light)
    rect(draw, (16, 0, 24, 8), dark)
    rect(draw, (0, 8, 8, 16), base)
    rect(draw, (8, 8, 16, 16), mid)
    rect(draw, (16, 8, 24, 16), base)
    rect(draw, (24, 8, 32, 16), dark)


def fill_leg(draw: ImageDraw.ImageDraw, colours: list[tuple[int, ...]], boots_only: bool) -> None:
    dark, base, mid, light, _ = colours
    rect(draw, (4, 16, 8, 20), light)
    rect(draw, (8, 16, 12, 20), dark)
    top = 23 if boots_only else 20
    for box, colour in (
        ((0, top, 4, 32), base),
        ((4, top, 8, 32), mid),
        ((8, top, 12, 32), base),
        ((12, top, 16, 32), dark),
    ):
        rect(draw, box, colour)


def fill_body(draw: ImageDraw.ImageDraw, colours: list[tuple[int, ...]]) -> None:
    dark, base, mid, light, _ = colours
    rect(draw, (20, 16, 28, 20), light)
    rect(draw, (28, 16, 36, 20), dark)
    for box, colour in (
        ((16, 20, 20, 32), base),
        ((20, 20, 28, 32), mid),
        ((28, 20, 32, 32), base),
        ((32, 20, 40, 32), dark),
    ):
        rect(draw, box, colour)


def fill_arm(draw: ImageDraw.ImageDraw, colours: list[tuple[int, ...]]) -> None:
    dark, base, mid, light, _ = colours
    rect(draw, (44, 16, 48, 20), light)
    rect(draw, (48, 16, 52, 20), dark)
    for box, colour in (
        ((40, 20, 44, 32), base),
        ((44, 20, 48, 32), mid),
        ((48, 20, 52, 32), base),
        ((52, 20, 56, 32), dark),
    ):
        rect(draw, box, colour)


def add_head_motif(draw: ImageDraw.ImageDraw, motif: str, colours: list[tuple[int, ...]]) -> None:
    dark, _, mid, light, accent = colours
    if motif == "crown":
        draw.rectangle((0, 8, 31, 11), fill=(0, 0, 0, 0))
        draw.rectangle((0, 12, 31, 15), fill=mid)
        for x in (1, 6, 10, 14, 18, 22, 27):
            draw.point((x, 11), fill=light)
        draw.rectangle((11, 13, 12, 14), fill=accent)
    elif motif == "hat":
        draw.rectangle((0, 8, 31, 12), fill=(0, 0, 0, 0))
        draw.line((0, 13, 31, 13), fill=light)
        draw.line((0, 15, 31, 15), fill=dark)
    elif motif == "mining":
        draw.rectangle((10, 9, 13, 12), fill=accent)
        draw.point((11, 8), fill=light)
        draw.line((8, 13, 15, 13), fill=dark)
    elif motif == "visor":
        draw.rectangle((9, 10, 14, 14), fill=dark)
        for x in (9, 11, 13):
            draw.line((x, 10, x, 14), fill=light)
    elif motif == "shell":
        draw.line((9, 9, 14, 14), fill=dark)
        draw.line((14, 9, 9, 14), fill=light)
        draw.rectangle((10, 10, 13, 13), outline=accent)
    elif motif == "gem":
        draw.polygon(((11, 9), (14, 12), (11, 15), (8, 12)), fill=accent)
        draw.point((11, 10), fill=light)
    elif motif == "dark_helmet":
        draw.rectangle((9, 11, 14, 14), fill=dark)
        draw.point((10, 12), fill=accent)
        draw.point((13, 12), fill=accent)
    else:
        draw.line((8, 14, 15, 14), fill=dark)
        draw.point((11, 12), fill=light)


def add_body_motif(draw: ImageDraw.ImageDraw, motif: str, colours: list[tuple[int, ...]]) -> None:
    dark, base, _, light, accent = colours
    if motif == "chain":
        for y in range(21, 32, 2):
            for x in range(16 + (y % 4), 56, 4):
                draw.point((x, y), fill=light)
                if x + 1 < 56:
                    draw.point((x + 1, y + 1 if y + 1 < 32 else y), fill=dark)
    elif motif == "feather":
        for y in (21, 24, 27, 30):
            draw.line((20, y, 23, min(31, y + 2)), fill=light)
            draw.line((27, y, 24, min(31, y + 2)), fill=accent)
            draw.line((44, y, 47, min(31, y + 2)), fill=light)
    elif motif == "veil":
        draw.line((20, 21, 23, 31), fill=accent)
        draw.line((27, 21, 24, 31), fill=accent)
        draw.line((20, 21, 27, 21), fill=light)
        draw.point((23, 24), fill=light)
        draw.point((24, 24), fill=light)
    elif motif in {"ice", "gem"}:
        draw.polygon(((24, 21), (27, 24), (24, 28), (21, 24)), fill=accent)
        draw.point((24, 22), fill=light)
        draw.line((44, 21, 47, 24), fill=light)
        draw.line((44, 26, 47, 29), fill=accent)
    elif motif in {"corrupt", "dragon"}:
        draw.line((20, 21, 27, 30), fill=accent)
        draw.line((27, 21, 20, 30), fill=dark)
        draw.rectangle((23, 24, 24, 26), fill=light)
        draw.line((40, 22, 55, 29), fill=accent)
    else:
        draw.line((20, 21, 27, 21), fill=light)
        draw.line((20, 22, 23, 27), fill=dark)
        draw.line((27, 22, 24, 27), fill=dark)
        draw.rectangle((23, 24, 24, 26), fill=accent)


def add_legs_motif(draw: ImageDraw.ImageDraw, motif: str, colours: list[tuple[int, ...]]) -> None:
    dark, _, _, light, accent = colours
    draw.line((16, 20, 39, 20), fill=dark)
    draw.line((20, 21, 27, 21), fill=light)
    if motif == "chain":
        for y in range(22, 32, 2):
            for x in range(0, 40, 3):
                draw.point((x, y), fill=light if (x + y) % 2 else dark)
    else:
        draw.line((5, 21, 5, 31), fill=accent)
        draw.line((10, 21, 10, 31), fill=dark)
        draw.line((23, 22, 24, 30), fill=accent)


def add_boot_motif(draw: ImageDraw.ImageDraw, motif: str, colours: list[tuple[int, ...]]) -> None:
    dark, _, _, light, accent = colours
    draw.line((0, 23, 15, 23), fill=light)
    draw.line((0, 30, 15, 30), fill=dark)
    if motif == "storm":
        draw.line((4, 24, 8, 27), fill=accent)
        draw.line((8, 27, 5, 30), fill=light)
    elif motif == "leather":
        draw.rectangle((5, 25, 6, 26), fill=accent)
        draw.rectangle((9, 27, 10, 28), fill=accent)


def generate_armor(name: str, spec: ArmorSpec) -> None:
    colours = palette(name)
    output = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(output)
    if spec.slot == "head":
        fill_head(draw, colours)
        add_head_motif(draw, spec.motif, colours)
        layer = "humanoid"
    elif spec.slot == "chest":
        fill_body(draw, colours)
        fill_arm(draw, colours)
        add_body_motif(draw, spec.motif, colours)
        layer = "humanoid"
    elif spec.slot == "legs":
        fill_leg(draw, colours, boots_only=False)
        fill_body(draw, colours)
        add_legs_motif(draw, spec.motif, colours)
        layer = "humanoid_leggings"
    elif spec.slot == "feet":
        fill_leg(draw, colours, boots_only=True)
        add_boot_motif(draw, spec.motif, colours)
        layer = "humanoid"
    else:
        raise ValueError(f"Unsupported armor slot: {spec.slot}")
    destination = EQUIPMENT_TEXTURES / layer / f"{name}.png"
    binary_alpha(output).save(destination, optimize=True)


def edge_mask(mask: Image.Image) -> Image.Image:
    source = mask.convert("L")
    result = Image.new("L", source.size, 0)
    pixels = result.load()
    for y in range(source.height):
        for x in range(source.width):
            if not source.getpixel((x, y)):
                continue
            if any(
                nx < 0
                or ny < 0
                or nx >= source.width
                or ny >= source.height
                or not source.getpixel((nx, ny))
                for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
            ):
                pixels[x, y] = 255
    return result


def generate_masked_equipment(name: str, layer: str, mask_name: str) -> None:
    destination = EQUIPMENT_TEXTURES / layer / f"{name}.png"
    mask = canonical_mask(mask_name)
    colours = palette(name)
    dark, base, mid, light, accent = colours
    output = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    pixels = output.load()
    for y in range(output.height):
        for x in range(output.width):
            if not mask.getpixel((x, y)):
                continue
            stripe = (x // 4 + y // 5) % 4
            colour = (base, mid, base, dark)[stripe]
            if (x + 2 * y) % 19 == 0:
                colour = accent
            pixels[x, y] = colour
    output.paste(light, mask=edge_mask(mask))
    binary_alpha(output).save(destination, optimize=True)


def generate_wing(name: str) -> None:
    colours = palette(name)
    dark, base, mid, light, accent = colours
    mask = canonical_mask("wing")
    output = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    pixels = output.load()
    for y in range(output.height):
        for x in range(output.width):
            if not mask.getpixel((x, y)):
                continue
            feather = ((x - 22) // 3 + y // 4) % 4
            colour = (base, mid, base, dark)[feather]
            if (x + 2 * y) % 13 == 0:
                colour = accent
            pixels[x, y] = colour
    output.paste(light, mask=edge_mask(mask))
    binary_alpha(output).save(EQUIPMENT_TEXTURES / "wings" / f"{name}.png", optimize=True)


def transformed(source: Image.Image, *, angle: float = 0, scale: float = 1.0,
                offset: tuple[int, int] = (0, 0)) -> Image.Image:
    image = binary_alpha(source)
    if angle:
        image = image.rotate(angle, resample=Image.Resampling.NEAREST, expand=False)
    if scale != 1.0:
        size = max(1, round(64 * scale))
        resized = image.resize((size, size), Image.Resampling.NEAREST)
        canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        canvas.alpha_composite(resized, ((64 - size) // 2, (64 - size) // 2))
        image = canvas
    if offset != (0, 0):
        canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        canvas.alpha_composite(image, offset)
        image = canvas
    return binary_alpha(image)


def state_path(name: str, suffix: str) -> Path:
    return ITEM_TEXTURES / "states" / f"{name}_{suffix}.png"


def pca_axis(image: Image.Image) -> tuple[float, float, float, float]:
    alpha = image.getchannel("A")
    points = [(x, y) for y in range(64) for x in range(64) if alpha.getpixel((x, y))]
    cx = sum(x for x, _ in points) / len(points)
    cy = sum(y for _, y in points) / len(points)
    xx = sum((x - cx) ** 2 for x, _ in points)
    yy = sum((y - cy) ** 2 for _, y in points)
    xy = sum((x - cx) * (y - cy) for x, y in points)
    angle = 0.5 * math.atan2(2 * xy, xx - yy)
    return cx, cy, math.cos(angle), math.sin(angle)


def bow_state(name: str, stage: int) -> Image.Image:
    source = icon(name)
    output = source.copy()
    colours = palette(name)
    _, _, _, light, accent = colours
    cx, cy, ux, uy = pca_axis(source)
    vx, vy = -uy, ux
    projections = [
        ((x - cx) * ux + (y - cy) * uy, x, y)
        for y in range(64)
        for x in range(64)
        if source.getchannel("A").getpixel((x, y))
    ]
    low = min(projections)[0]
    high = max(projections)[0]
    first = (round(cx + ux * low), round(cy + uy * low))
    second = (round(cx + ux * high), round(cy + uy * high))
    pull = (3, 8, 14)[stage]
    grip = (round(cx - vx * pull), round(cy - vy * pull))
    draw = ImageDraw.Draw(output)
    draw.line((first, grip, second), fill=light, width=3)
    arrow_end = (round(cx + ux * high * 0.75), round(cy + uy * high * 0.75))
    draw.line((grip, arrow_end), fill=accent, width=3)
    return binary_alpha(output)


def crossbow_state(name: str, suffix: str) -> Image.Image:
    source = icon(name)
    output = source.copy()
    dark, _, _, light, accent = palette(name)
    draw = ImageDraw.Draw(output)
    if suffix.startswith("pull_"):
        stage = int(suffix[-1])
        y = 39 - stage * 5
        draw.line((15, y, 49, y - 11), fill=light, width=3)
        draw.rectangle((27, 28 - stage * 2, 35, 32 - stage * 2), outline=accent, width=2)
    else:
        rocket = suffix.endswith("rocket")
        colour = accent if rocket else light
        draw.line((19, 44, 47, 18), fill=colour, width=3)
        tip = ((47, 18), (42, 19), (46, 23)) if not rocket else ((47, 18), (40, 20), (45, 25))
        draw.polygon(tip, fill=colour)
        if rocket:
            draw.point((18, 45), fill=accent)
            draw.point((17, 46), fill=light)
    draw.point((0, 0), fill=dark if output.getpixel((0, 0))[3] else (0, 0, 0, 0))
    return binary_alpha(output)


def transformed_state(name: str, *, angle: float = 0, scale: float = 1.0) -> Image.Image:
    return transformed(icon(name), angle=angle, scale=scale)


def shield_state(name: str) -> Image.Image:
    output = transformed(icon(name), scale=1.04)
    mask = output.getchannel("A")
    _, _, _, light, accent = palette(name)
    outer_edge = edge_mask(mask)
    active_band = ImageChops.multiply(outer_edge.filter(ImageFilter.MaxFilter(5)), mask)
    output.paste(accent, mask=active_band)
    output.paste(light, mask=outer_edge)
    return binary_alpha(output)


def generate_states() -> None:
    for name in BOWS:
        for stage in range(3):
            bow_state(name, stage).save(state_path(name, f"pull_{stage}"), optimize=True)
    for name in CROSSBOWS:
        for suffix in ("pull_0", "pull_1", "pull_2", "charged", "charged_rocket"):
            crossbow_state(name, suffix).save(state_path(name, suffix), optimize=True)
    for name in FISHING_RODS:
        transformed_state(name, angle=-8, scale=0.96).save(state_path(name, "cast"), optimize=True)
    for name in SHIELDS:
        shield_state(name).save(state_path(name, "blocking"), optimize=True)
    for name in TRIDENTS:
        transformed_state(name, angle=2).save(state_path(name, "in_hand"), optimize=True)
        transformed_state(name, angle=-14, scale=0.94).save(state_path(name, "throwing"), optimize=True)


def main() -> None:
    for name, spec in ARMOR.items():
        generate_armor(name, spec)
    for name in WINGS:
        generate_wing(name)
    for name in HORSE_BODY:
        generate_masked_equipment(name, "horse_body", "horse_body")
    for name in HORSE_SADDLE:
        generate_masked_equipment(name, "horse_saddle", "horse_saddle")
    generate_states()
    state_count = (
        len(BOWS) * 3
        + len(CROSSBOWS) * 5
        + len(FISHING_RODS)
        + len(SHIELDS)
        + len(TRIDENTS) * 2
    )
    print(
        f"Generated {len(ARMOR)} armor, {len(WINGS)} wing, "
        f"{len(HORSE_BODY) + len(HORSE_SADDLE)} horse/saddle and {state_count} state textures"
    )


if __name__ == "__main__":
    main()
