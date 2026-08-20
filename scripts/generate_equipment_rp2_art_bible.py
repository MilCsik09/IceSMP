#!/usr/bin/env python3
"""Generate and validate the Equipment RP2 Art Bible from production-derived gear lines."""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
GEAR_LINES = ROOT / "docs/development/equipment-rp2-gear-lines.json"
ARMOR_MATRIX = ROOT / "docs/development/equipment-rp2-armor-matrix.json"
OUTPUT_JSON = ROOT / "docs/development/equipment-rp2-art-bible.json"
OUTPUT_MD = ROOT / "docs/development/equipment-rp2-art-bible.md"
PILOT_MANIFEST = "docs/development/equipment-rp2-pilot-manifest.json"


GLOBAL_POLICY: dict[str, Any] = {
    "style": "Minecraft-native MMORPG",
    "inventory_texture_resolution": [64, 64],
    "inventory_logical_pixel_density": [32, 32],
    "worn_texture_resolution": [64, 32],
    "pixel_density_philosophy": "Author inventory art on a 32x logical grid and nearest-neighbour upscale to 64x; worn art uses the fixed 1.21.11 64x32 equipment UV with two-pixel motif rhythm.",
    "shading_philosophy": "Four-to-six authored value steps, hard pixel clusters, no smooth PBR gradients or single-pixel confetti noise.",
    "value_contrast": "Family construction must remain readable in grayscale; reserve the brightest value for material edges or one focal motif.",
    "saturation": "Most surfaces stay low-to-medium saturation; one controlled line accent may be saturated.",
    "outline_usage": "Use selective dark material boundaries, not a universal black contour around every UV island.",
    "material_readability": "Construction rhythm comes before hue: weave, hide panel, ring/scale repetition and plate framing are mandatory family signals.",
    "ornament_density": "early=low, mid=restrained, high=confident, endgame=specific; never fill every surface.",
    "silhouette_complexity": "Increase by refined layering and focal geometry, not by universal spikes or oversized shoulders.",
    "emissive_usage": {"0": "none", "1": "subtle focal pixels", "2": "visible magical accents", "3": "signature/boss focal use only"},
    "animation_policy": "No armor animation in RP2-B pilot. Future animation needs an explicit client-safe pipeline decision.",
    "palette_discipline": "One dominant material ramp, one secondary ramp and one accent per line; family identity never depends on a fixed family colour.",
    "asymmetry_policy": "One purposeful asymmetry is allowed for straps, patches or trophies; core slot readability remains balanced.",
    "weathering_policy": "Crafted joins are clean; wilderness wear follows exposed edges; boss/prestige finish may be unusual but never uniformly glossy.",
    "magical_effect_policy": "Magic is embedded in seam, ring, inset or etching structure. No generic aura and no all-over neon.",
    "boss_prestige_escalation": "Reserve silhouette exceptions, emissive level 3, rare material treatments and encounter-specific motifs for boss/prestige lines.",
    "colorblind_value_policy": "No line is distinguished by hue alone; silhouette tag, construction rhythm and value structure are independently meaningful.",
}


FAMILY_POLICY: dict[str, dict[str, Any]] = {
    "CLOTH": {
        "silhouette": "light vertical flow, hood/veil framing, layered hems and narrow rigid edges",
        "shape_language": ["vertical taper", "soft stacked strips", "narrow seam edge", "open negative space"],
        "material_language": ["woven textile", "magical fiber", "embroidery", "cord", "resin-stiffened seam"],
        "palette_policy": "textile value blocks dominate; metallic or crystal colour is a small seam/focal accent",
        "forbidden_motifs": ["full metal shell", "broad plate shoulder", "chain field", "solid face plate"],
    },
    "LEATHER": {
        "silhouette": "mobile fitted mass with diagonal panels, straps and organic reinforcement",
        "shape_language": ["diagonal overlap", "segmented organic panel", "strap crossing", "tapered limb guard"],
        "material_language": ["treated hide", "stitch", "tendon", "resin", "chitin or scale insert"],
        "palette_policy": "hide ramps and treatment stains dominate; metal is limited to buckles or small reinforcement",
        "forbidden_motifs": ["robe hem", "complete ring field", "full metal coverage", "plate cuirass mass"],
    },
    "MAIL": {
        "silhouette": "medium mass between leather and plate with visible backing and repeated ring/scale rhythm",
        "shape_language": ["small repeated unit", "medium hard edge", "hybrid yoke", "compact layered skirt"],
        "material_language": ["ring mail", "scale", "light alloy", "wire", "leather or textile backing"],
        "palette_policy": "metal rhythm is broken by visible backing and joins; never read as a solid recoloured plate",
        "forbidden_motifs": ["solid full-body plate", "flowing robe", "oversized pauldron", "unbroken smooth cuirass"],
    },
    "PLATE": {
        "silhouette": "heavy geometric mass with framed forged panels, broad hard edges and structural joints",
        "shape_language": ["large geometric panel", "broad framed edge", "rivet rhythm", "articulated hard joint"],
        "material_language": ["forged plate", "structural reinforcement", "rivet", "tempered edge", "mineral or rune inset"],
        "palette_policy": "large metal value blocks dominate while undersuit and inset separate articulation",
        "forbidden_motifs": ["soft robe taper", "mostly exposed hide", "all-over chain", "unbounded fantasy spike mass"],
    },
}


ACQUISITION_POLICY = {
    "crafted": "deliberate construction, clean joins and profession-specific functional detailing",
    "world": "environment-derived surfaces, practical repairs and exposure-led weathering",
    "boss": "encounter-specific motif and focal treatment; specificity, not automatic glow, carries status",
    "prestige": "rare silhouette/treatment with disciplined status cues and Minecraft-readable massing",
}


PROGRESSION_POLICY = {
    "early": "few materials, direct construction and broad readable blocks",
    "mid": "one secondary material, cleaner fit and a controlled identifying motif",
    "high": "sophisticated joins, confident motif integration and more deliberate contrast",
    "endgame": "specific material finish and refined silhouette; preserve sidegrade parity and avoid universal gold/glow escalation",
}


PROFESSION_POLICY = {
    "armorer": "structural reinforcement, rivets, rings, framed joins and serviceable geometry",
    "enchanter": "runes integrated into seams, conductive paths and restrained arcane focal points",
    "alchemist": "infused/tinctured surfaces, resin joins and visibly transformed organic material",
}


def art(core: str, primary: list[str], secondary: list[str], silhouette: str, shape: list[str],
        palette: list[str], secondary_palette: list[str], accent: str, contrast: str,
        ornament: str, magic: int, emissive: int, weathering: str, helmet: str, chest: str,
        legs: str, boots: str, icon: str, worn: str, forbidden: list[str], difference: str,
        dominant: str, motifs: list[str]) -> dict[str, Any]:
    return {
        "core_fantasy": core, "primary_materials": primary, "secondary_materials": secondary,
        "silhouette": silhouette, "shape_language": shape, "primary_palette": palette,
        "secondary_palette": secondary_palette, "accent": accent, "contrast_target": contrast,
        "ornament": ornament, "magical_intensity": magic, "emissive_allowance": emissive,
        "weathering": weathering, "helmet_language": helmet, "chest_language": chest,
        "leggings_language": legs, "boots_language": boots, "inventory_icon_direction": icon,
        "worn_armor_direction": worn, "forbidden_motifs": forbidden,
        "differentiation_note": difference, "dominant_silhouette_tag": dominant,
        "motif_tags": motifs,
    }


ART: dict[str, dict[str, Any]] = {
    "alkimista_fatyol": art("healing tincture veil", ["woven cloth", "infused bandage"], ["amber resin", "herbal stitch"], "narrow hood and layered apron strips", ["vertical wrap", "sealed seam"], ["#3B3034", "#806B64", "#D4C5A8"], ["#694B3C", "#C58B54"], "#D9A46A", "warm light seam on muted cloth", "measured vial-knot embroidery", 1, 0, "clean treated edges", "open bandage hood", "cross-wrapped veil coat", "layered wrap trousers", "soft resin-toe saru", "slot silhouette plus one amber knot", "thin textile layers and open face", ["metal cuirass", "poison green wash", "large vial rack"], "Only CLOTH line with sealed medicinal wraps and amber resin knots.", "wrapped-apron", ["bandage", "resin-knot"]),
    "aranyfust": art("gold-leaf smoke regalia", ["smoked silk", "gold leaf"], ["black gauze"], "fan hem with drifting side panels", ["soft fan", "broken leaf edge"], ["#171518", "#3C3130", "#756052"], ["#9F7B39", "#D6B966"], "#E6D28A", "gold focal edge over near-black cloth", "sparse dissolving leaf flecks", 1, 1, "prestige-clean with intentional broken leaf", "low smoke cowl", "asymmetric fan-front mantle", "wide taper smoke trousers", "flat black-gold slippers", "gold leaf confined to outer contour", "floating-looking hem through negative pixels", ["solid gold armor", "crown", "full glow"], "Only CLOTH line with smoke-dark negative space and fragmented gold-leaf perimeter.", "smoke-fan", ["gold-leaf", "smoke"]),
    "csillagfatyol": art("astral encounter veil", ["star-woven textile"], ["silver thread", "night crystal"], "split comet-tail robe", ["long split", "star seam"], ["#10182F", "#27365B", "#53628A"], ["#9BA9C8", "#DCE4EA"], "#B7A4E8", "cold star points on deep navy", "constellation seam clusters", 3, 2, "pristine boss finish", "pointed astral hood", "comet split chest drape", "paired taper panels", "silver-edged soft boots", "clear star cluster per slot", "long star seam with limited focal glow", ["wings", "giant star spikes", "all-over galaxy noise"], "Only CLOTH line with a two-tail comet silhouette and constellation clusters.", "comet-split", ["constellation", "comet"]),
    "csontvarro": art("field-stitched ossuary cloth", ["rough linen"], ["bone toggles", "sinew"], "ragged short layers with bone closures", ["uneven strip", "visible stitch"], ["#2B2925", "#625B4D", "#A89B7D"], ["#C9C0A5", "#E2D9BE"], "#8F6B4B", "pale bone closures on coarse mid-values", "simple bone toggle rows", 0, 0, "heavy wilderness fray", "stitched sack hood", "short bone-closed coat", "patched straight trousers", "wrapped rough shoes", "large bone toggle readable at 16x", "coarse weave, torn but family-light", ["skull mask", "horns", "bone plate shell"], "Only CLOTH line with coarse rag edges and functional bone-toggle construction.", "rag-toggle", ["bone-toggle", "coarse-stitch"]),
    "fonixszovet": art("fire-tempered feather weave", ["runewoven cloth", "phoenix fiber"], ["charred seam"], "upward feathered hems", ["rising taper", "feather overlap"], ["#2A1214", "#69231F", "#A63C25"], ["#D66A2C", "#F2B653"], "#FFD17A", "hot inner seam against charred outer cloth", "feather chevrons, not literal wings", 2, 1, "heat-darkened outer edges", "back-swept feather hood", "rising chevron robe", "layered ember trousers", "charred feather shoes", "one upward feather cluster per icon", "heat gradient follows weave structure", ["wings", "flame aura", "solid orange field"], "Only CLOTH line whose cloth construction rises in feather-like chevrons from charred edges.", "rising-feather", ["feather-weave", "ember-seam"]),
    "holdlen": art("moon-linen ritual vestment", ["moon linen", "woven textile"], ["silver embroidery", "conductive thread"], "long vertical hooded layers with crescent hem", ["vertical taper", "circular seam"], ["#17192A", "#2D3350", "#555B7B"], ["#9AA1B8", "#D5D7DE"], "#9A84C7", "silver crescent on dark indigo", "restrained crescent and orbit stitches", 1, 0, "clean enchanter finish", "deep open moon hood", "long ritual coat with central orbit seam", "split vertical robe panels", "narrow silver-bound saru", "slot form plus one crescent stitch", "negative face, slim arms and long textile flow", ["plate shoulder", "star field", "neon rune carpet"], "Only CLOTH line with a clean crescent/orbit grammar and uninterrupted vertical moon-linen flow.", "crescent-column", ["crescent", "orbit-seam"]),
    "kodszovo": art("fog-woven wanderer cloth", ["mist-dyed cloth"], ["wet cord"], "staggered translucent-looking bands", ["offset band", "soft broken edge"], ["#273037", "#4C5960", "#74838A"], ["#9CA9AA", "#C7D0CC"], "#7899A3", "pale fog band over medium slate", "almost unornamented drifting seams", 1, 0, "dew-darkened wilderness edges", "loose low hood", "offset fog-band tunic", "uneven drifting panels", "soft gray wraps", "wide pale band survives downscale", "alpha-like breaks without unsafe partial alpha", ["bright cyan glow", "hard crystal edge", "symmetrical royal trim"], "Only CLOTH line built from horizontally offset fog bands and low-contrast broken edges.", "fog-bands", ["mist-band", "dew-edge"]),
    "melyseg_ritual": art("abyssal weighted ritual robe", ["ink cloth"], ["weighted cord", "teal ritual tablet"], "low weighted hem and triangular prayer tabs", ["downward triangle", "cord vertical"], ["#0D1D25", "#17343D", "#2A5059"], ["#5C7779", "#8AA3A0"], "#3E9A94", "teal tablet focus in deep ink values", "small prayer-tab sequence", 2, 1, "salt-stained lower hem", "tight drowned cowl", "weighted cord mantle", "triangular prayer-tab panels", "corded dark shoes", "single teal tablet and downward triangle", "weight concentrated at hem, shoulders remain light", ["tentacles", "fish scales", "full cyan glow"], "Only CLOTH line with weighted cords and downward abyss-prayer tabs.", "weighted-tabs", ["prayer-tab", "weighted-cord"]),
    "nemakristaly": art("silent crystal-seamed cloth", ["sound-dampened textile"], ["mute crystal", "black thread"], "angular collar over narrow silent drape", ["sharp collar", "interrupted seam"], ["#191622", "#332A45", "#554568"], ["#827393", "#B5ACC0"], "#79A9A5", "pale crystal chips isolated in dark fabric", "few disconnected crystal stitches", 2, 1, "laboratory-clean", "angular mute hood", "broken conductive seam coat", "quiet straight panels", "crystal-tab slippers", "one disconnected crystal chip per slot", "angular collar differentiates while mass remains textile", ["large crystal spikes", "continuous glowing rune", "purple plate"], "Only CLOTH line with deliberately disconnected seams and sparse mute-crystal chips.", "silent-collar", ["mute-crystal", "broken-seam"]),
    "szenthamvak": art("sanctified ash vestment", ["ash-white textile"], ["charcoal cord", "ember-gold censer metal"], "ceremonial ash mantle and narrow censer sash", ["clean mantle", "falling ash blocks"], ["#34302D", "#6A625A", "#B8AEA0"], ["#D9D0C2", "#EEE6D7"], "#D79A4B", "ivory mantle with ember-gold focal clasp", "censer-chain sash and ash speck clusters", 2, 1, "ritual ash deposit, not random grime", "ash-priest hood", "short mantle over long sash", "formal ash panels", "ivory-gold saru", "gold clasp plus ash-white slot mass", "boss specificity through mantle/sash, not bulk", ["angel wings", "halo", "solid gold robe"], "Only CLOTH line with an ash-white mantle and single censer-chain sash.", "censer-mantle", ["censer", "ash-cluster"]),

    "demonbor": art("boss-forged infernal hide", ["demon hide"], ["black sinew", "heat-dark resin"], "tight ridged hide with heat-crack panels", ["organic ridge", "tension seam"], ["#1B1113", "#4A2024", "#782E2E"], ["#A64835", "#D16A45"], "#E58A52", "hot crack focal lines on black hide", "restrained heat fissures", 2, 1, "scorched boss surface", "ridged closed mask", "heat-cracked hide cuirass", "sinew-bound thigh plates", "cloven-shaped but human boots", "one heat fissure per icon", "fitted hide stays mobile despite boss specificity", ["horns", "wings", "flame aura", "metal plate shell"], "Only LEATHER line with black sinew-bound heat fissures in infernal hide.", "heat-ridge", ["heat-fissure", "black-sinew"]),
    "holdarnyek": art("prestige lunar shadow leather", ["moon-cured hide"], ["silvered buckle", "shadow resin"], "crescent-cut overlapping panels", ["crescent cut", "diagonal overlap"], ["#11151D", "#252C3A", "#404A5A"], ["#788397", "#B6BFCC"], "#7187B5", "silver crescent edge on matte black", "minimal prestige crescent edging", 2, 1, "pristine matte finish", "half-moon mask", "crescent-cut diagonal vest", "alternating lunar panels", "silent silver-edge boots", "crescent negative cut is primary cue", "matte fitted silhouette with one lunar cut", ["robe", "full silver armor", "star field"], "Only LEATHER line with crescent-shaped negative cuts and matte moon-cured hide.", "crescent-cut", ["lunar-cut", "matte-hide"]),
    "kitinbor": art("chitin-reinforced stalker hide", ["treated hide", "chitin plate"], ["tendon lacing"], "angular insect carapace inserts on a fitted base", ["carapace segment", "joint notch"], ["#1E2118", "#3B452A", "#5C6938"], ["#7F8450", "#A9A36D"], "#B08B4F", "dry shell edge over dark hide", "small joint-node rows", 0, 0, "clean crafted shell with worn tips", "mandible-free chitin mask", "segmented carapace vest", "notched thigh shells", "jointed chitin boots", "large shell segment, not tiny scales", "organic plates never form a metal cuirass", ["antennae", "mandibles", "full beetle shell", "crystal spike"], "Only LEATHER line with notched chitin carapace segments and tendon-laced joints.", "carapace-notch", ["chitin", "joint-notch"]),
    "predator_karma": art("apex encounter trophy harness", ["scarred hide"], ["bone claw", "red cord"], "purposeful one-shoulder trophy asymmetry", ["claw diagonal", "asymmetric guard"], ["#241B15", "#5A3A24", "#8A5630"], ["#B8905B", "#D7BE8C"], "#9E3F35", "bone claw and red cord on dark hide", "single trophy-claw sequence", 1, 0, "battle-scarred boss wear", "low predator brow mask", "one-shoulder claw harness", "slash-panel leggings", "reinforced stalking boots", "single claw diagonal remains legible", "trophy asymmetry without oversized shoulder", ["skull", "horn crown", "multiple trophy walls"], "Only LEATHER line with a single bone-claw shoulder harness and red karma cord.", "claw-shoulder", ["claw-trophy", "red-cord"]),
    "sotetmoha": art("moss-reclaimed early hide", ["old hide"], ["dark moss", "root stitch"], "soft lichen fringe on compact panels", ["rounded patch", "root seam"], ["#24271D", "#414B2F", "#616C3D"], ["#766249", "#9B835D"], "#718B46", "moss edge against brown hide", "natural root stitches", 0, 0, "heavy damp wilderness wear", "moss-edged cap mask", "patched lichen vest", "rounded knee patches", "mud-dark boots", "broad moss patch, no fine noise", "soft organic edges and practical compact mass", ["poison neon", "vines extending off body", "metal panels"], "Only LEATHER line with rounded moss patches and root-like repair stitches.", "lichen-fringe", ["moss-patch", "root-stitch"]),
    "utjaro": art("road-worn passage leathers", ["travel hide"], ["canvas strap", "iron buckle"], "cross-body travel straps and shortened coat tail", ["route diagonal", "utility fold"], ["#3A3027", "#695440", "#92765B"], ["#626A67", "#9AA09A"], "#B08A55", "pale route strap on weathered brown", "map-grid stitch at one panel", 0, 0, "edge-polished and road-dusted", "open traveler mask", "cross-body route vest", "practical patched trousers", "high road boots", "cross strap distinguishes every slot family", "movement-first short silhouette", ["large backpack", "robe tail", "plate greave"], "Only LEATHER line with route-like cross straps and road-polished edges.", "route-cross", ["travel-strap", "map-stitch"]),
    "vadbor": art("alchemically treated wild-hide armor", ["reinforced wild hide"], ["binding tendon", "pine resin"], "mobile diagonal hide panels with small organic shoulder reinforcement", ["diagonal panel", "strap crossing", "tapered guard"], ["#2B2118", "#59402A", "#85613A"], ["#6B7341", "#9A9B62"], "#C2A36B", "bone-tan stitch and moss resin on umber", "functional double-stitch and resin seals", 0, 0, "controlled alchemical wear, clean joins", "open stitched hunter hood", "diagonal panel vest with resin shoulder", "segmented hide trousers", "layered mobile boots", "slot silhouette plus bold diagonal seam", "hide panels, visible skin breaks, no metal mass", ["fur mantle", "full metal plates", "poison green field"], "Only LEATHER line with pine-resin seals and a double-stitched diagonal wild-hide panel grammar.", "resin-diagonal", ["resin-seal", "double-stitch"]),
    "vadorzo": art("crafted predator-warden leather", ["reinforced hide"], ["tendon cord", "forest resin"], "compact bracers and guarded collar", ["forward chevron", "guard strap"], ["#252019", "#50422C", "#76623C"], ["#425C3C", "#69805A"], "#B89B5E", "light guard chevron on forest brown", "small ward chevrons", 0, 0, "clean craft with field-scuffed guard edges", "guarded low mask", "forward-chevron leather vest", "corded guard panels", "reinforced ward boots", "forward chevron marks crafted protection", "more guarded than Vadbor, still light and fitted", ["claw trophies", "plate chest", "long robe"], "Only LEATHER line with compact ward chevrons and reinforced predator bracers.", "guard-chevron", ["ward-chevron", "reinforced-bracer"]),
    "verszavanna": art("sun-scorched blood-savanna hunt hide", ["sun-cured hide"], ["black tendon", "ochre pigment"], "long slash diagonals and high knee wraps", ["long diagonal", "dry layered edge"], ["#2B1815", "#6A3025", "#9B4B31"], ["#B5793B", "#D1A45C"], "#481E1E", "ochre edge over red-brown field", "painted hunt slashes", 1, 0, "sun-cracked wilderness surface", "narrow hunt mask", "long diagonal savanna vest", "high ochre knee wraps", "dust-dark hunt boots", "one long ochre slash", "lean hot-climate silhouette with high wraps", ["wet gloss", "bone trophy wall", "flame motif"], "Only LEATHER line with long ochre hunt slashes over sun-cracked red savanna hide.", "savanna-slash", ["ochre-slash", "sun-crack"]),
    "vizbor": art("water-cured demonhide sidegrade", ["water-treated hide"], ["shell stud", "fish oil finish"], "smooth overlapping panels with rounded drip ends", ["rounded overlap", "waterline seam"], ["#17272B", "#28484E", "#416A6E"], ["#799391", "#B3C2B9"], "#4C9A94", "pearl stud against deep teal hide", "waterline stitch and shell studs", 1, 0, "wet sheen represented by hard highlights", "sealed diver mask", "overlapping water-hide vest", "rounded drip panels", "sealed slick boots", "shell stud and rounded overlap", "slick but organic fitted panels", ["fins", "scales covering all surfaces", "cyan glow"], "Only LEATHER line with rounded waterline overlaps and sparse shell-stud closures.", "water-overlap", ["waterline", "shell-stud"]),

    "csontenyv": art("bone-glued warden mail", ["iron ring mail"], ["bone splint", "glued leather backing"], "dense ring yoke broken by vertical bone splints", ["ring rhythm", "vertical splint"], ["#24272A", "#555B5D", "#858B88"], ["#B7AE91", "#DED4B5"], "#7A5A3B", "pale bone bars interrupt dark rings", "functional bone splint row", 0, 0, "clean crafted rings, matte glue stains", "bone-bar coif", "ring yoke with central splint", "mail skirt and bone knee ties", "ring boot with bone tab", "rings plus one vertical ivory bar", "medium mass with backing visible", ["skull", "solid bone plate", "full iron block"], "Only MAIL line whose ring field is interrupted by straight pale bone-glue splints.", "bone-splint-mail", ["ring", "bone-splint"]),
    "gyongyhaz_warden": art("shore-found pearl warden mail", ["weathered rings"], ["pearl shell scale", "sea-cloth backing"], "shell-scale shoulder yoke over sparse mail", ["shell fan", "ring gap"], ["#24363B", "#4D6669", "#738989"], ["#A7B8AE", "#D7D8C5"], "#78A7A4", "pearl shell fan on sea-gray backing", "natural shell fan rows", 0, 0, "salt-bleached early wear", "shell-rim coif", "pearl yoke with sparse rings", "simple ring skirt", "salt-dark boots", "large shell fan survives downscale", "light early mail with visible cloth", ["full pearl plate", "trident motif", "glow"], "Only MAIL line with a pearlescent shell-fan yoke and intentionally sparse ring coverage.", "pearl-yoke", ["shell-fan", "sparse-ring"]),
    "konnyu_otvozet": art("precision light-alloy field mail", ["light-alloy rings", "small alloy scale"], ["dark textile backing", "copper join"], "compact segmented mail panels with visible backing", ["small repeated ring", "medium frame", "segmented yoke"], ["#22282D", "#4A555C", "#7B878B"], ["#8A5938", "#BC7A48"], "#D5B079", "pewter rings and copper joins over charcoal", "regular serviceable join nodes", 0, 0, "clean armorer finish", "compact open coif", "ring-and-scale segmented vest", "short mail skirt over fitted legs", "light segmented boots", "ring dots plus copper joint at slot center", "visibly between leather and plate; backing must remain visible", ["solid breastplate", "all-over chain cage", "oversized shoulder"], "Only MAIL line with precise pewter ring blocks, compact alloy scales and desaturated copper service joins.", "alloy-segments", ["light-ring", "copper-join"]),
    "melyvizi_vadasz": art("prestige abyss hunter mail", ["blackened marine mail"], ["pearl hook", "pressure-treated backing"], "net-like diagonals and narrow deep-sea collar", ["net diagonal", "hook curve"], ["#0F242B", "#1C4650", "#326873"], ["#8BAAA5", "#C7D3C4"], "#B38B55", "pearl and brass hooks on abyss teal", "disciplined net-knot sequence", 2, 1, "salt-dark prestige patina", "narrow pressure coif", "diagonal net-mail harness", "weighted mail skirt", "hook-clasped deep boots", "hook curve plus net diagonal", "prestige through controlled net structure, not fins", ["fins", "tentacles", "full aqua glow"], "Only MAIL line with diagonal net-mail and pearl/brass hook closures.", "net-hook", ["net-knot", "pearl-hook"]),
    "runalanc": art("conductive runic chain", ["blackened silver rings"], ["moon thread backing", "rune link"], "ring field with a single continuous rune-link border", ["ring rhythm", "rune border"], ["#171A21", "#353B46", "#606875"], ["#70618D", "#A18DBE"], "#B9A7D6", "pale violet rune-links on black silver", "one border circuit and no rune carpet", 2, 1, "precise enchanter finish", "open rune-edged coif", "black ring vest with circuit border", "rune-linked mail skirt", "conductive chain boots", "one rune-link glyph at slot edge", "ring rhythm dominates; magic follows joins", ["full glowing runes", "plate panels", "purple recolour only"], "Only MAIL line with a continuous violet rune-link border integrated into blackened chain.", "rune-border-mail", ["rune-link", "blackened-ring"]),
    "runapajzs": art("boss shield-lamella mail", ["tempered ring mail"], ["square rune lamella", "blue backing"], "squared shield tiles nested in chain fields", ["square lamella", "ring border"], ["#182432", "#34485A", "#617283"], ["#849CB0", "#BFCBD2"], "#5E91C5", "blue rune tile focus on steel rings", "shield-square rune sequence", 3, 2, "boss-clean tempered finish", "square rune brow coif", "shield-tile yoke in mail", "lamella-edged ring skirt", "square-guard boots", "single shield-square per icon", "boss-specific squares but still hybrid mail", ["solid tower-shield chest", "giant shoulder", "all-over blue glow"], "Only MAIL line with square rune-shield lamella nested inside visible ring fields.", "shield-lamella", ["shield-square", "ring-border"]),
    "vadvadasz": art("early wilderness hunting mail", ["recovered iron rings"], ["dark hide backing", "rawhide tie"], "sparse chest rings and leather-heavy limbs", ["sparse ring", "hide gap"], ["#2A2A25", "#515347", "#76776B"], ["#5B432C", "#8A6841"], "#A89362", "iron ring clusters on dark hide", "simple tie knots", 0, 0, "wilderness rust and repairs", "partial hunting coif", "sparse ring bib", "hide-backed ring strips", "mostly-hide mail boots", "clustered rings, not continuous field", "earliest MAIL deliberately shows much backing", ["solid plate", "full polished chain", "magic rune"], "Only MAIL line with deliberately sparse recovered-ring clusters over dominant hunting hide.", "sparse-ring-bib", ["recovered-ring", "rawhide-tie"]),
    "viharjaro": art("storm encounter conductor mail", ["storm steel rings"], ["charged blue backing", "lightning clasp"], "angled storm yoke with converging ring currents", ["current diagonal", "ring convergence"], ["#101A2A", "#263B55", "#456581"], ["#7F9CB2", "#BFD6DB"], "#55B5D9", "cyan lightning clasp on navy steel", "few current-line bolts", 3, 2, "rain-polished boss metal", "storm-crown coif without spikes", "converging current mail vest", "angled current skirt", "charged ring boots", "one cyan current fork", "energy moves through rings, never a full aura", ["cloud shoulders", "full lightning body", "giant crystal"], "Only MAIL line whose ring rows visibly converge into a controlled cyan storm clasp.", "storm-convergence", ["current-line", "lightning-clasp"]),
    "viharkvarc_runas": art("quartz-conduit tempest mail", ["conductive alloy mail"], ["storm quartz", "rune cord"], "braided chain columns around quartz nodes", ["braided column", "quartz node"], ["#182229", "#384A52", "#62727A"], ["#6D9AAA", "#A9CFD4"], "#79D2D5", "quartz nodes against gray braided chain", "spaced conduit nodes", 2, 1, "precise crafted polish", "quartz-node coif", "braided conductor vest", "vertical conduit mail", "node-clasp boots", "one bright node on braided line", "high complexity through connection logic", ["crystal spikes", "solid cyan plate", "random runes"], "Only MAIL line with braided conductor columns and evenly spaced storm-quartz nodes.", "quartz-conduit", ["braided-chain", "quartz-node"]),
    "viharszel": art("wind-scoured highland mail", ["pale weathered rings"], ["blue-gray cloth backing"], "wind-swept diagonal bands and open lower edge", ["swept diagonal", "open ring edge"], ["#26323B", "#4C606C", "#728794"], ["#9DAEB4", "#CCD5D4"], "#739EB0", "pale wind edge on steel blue", "subtle wind-line spacing", 1, 0, "wind-polished world wear", "swept open coif", "diagonal wind-mail sash", "open-edge ring skirt", "windbound boots", "wide diagonal pale band", "asymmetry suggests motion without cloth robe", ["lightning bolt", "cape", "full cyan glow"], "Only MAIL line with pale wind-swept diagonal ring bands and an intentionally open lower edge.", "wind-swept-mail", ["wind-band", "open-ring-edge"]),

    "borostyan_tarna": art("amber mine bulwark plate", ["forged dark iron plate"], ["amber mineral inset", "soot leather undersuit"], "broad rectangular framed panels and heavy squared greaves", ["large rectangle", "framed hard edge", "rivet grid"], ["#191B1C", "#34383A", "#575C5E"], ["#6F4B25", "#A66B28"], "#D39536", "amber inset against soot-dark iron", "functional mine-rivet grids", 0, 0, "soot-darkened with clean forged edges", "squared miner helm", "framed breastplate with central amber window", "articulated rectangular thigh plates", "heavy amber-inset greaves", "large panel and amber window per slot", "max family mass without fantasy oversizing", ["crystal spikes", "gold plate", "chain-dominant surface"], "Only PLATE line with soot-dark rectangular mine panels and recessed amber windows.", "amber-frame", ["amber-window", "mine-rivet"]),
    "csillagacel": art("prestige star-steel harness", ["star steel plate"], ["night enamel", "white edge"], "sleek faceted plate with restrained upward points", ["clean facet", "star notch"], ["#1B2637", "#40536B", "#71839B"], ["#AAB8C7", "#E2E8EC"], "#A6B7E4", "white star edge over blue steel", "few five-notch star facets", 2, 1, "mirror-clean prestige finish", "faceted star helm", "sleek star-notched cuirass", "long clean facets", "white-edge star greaves", "star notch, not star field", "prestige refinement through clean facets", ["giant star spikes", "gold trim", "galaxy texture"], "Only PLATE line with clean pale star-steel facets and small five-notch edges.", "star-facet", ["star-notch", "white-edge"]),
    "glatziendorfi": art("frost-ward runeforged plate", ["tempered blue iron"], ["frost crystal inset", "ice rune"], "layered glacier-like plate shelves", ["stepped plate", "frost bevel"], ["#16283A", "#2C5068", "#4C7890"], ["#91B6BE", "#D4E3DE"], "#65C8D0", "frost bevel against deep blue iron", "sparse ward-rune bevels", 2, 1, "cold clean forged finish", "stepped frost helm", "glacier-shelf breastplate", "frost-beveled leg plates", "iceward greaves", "one stepped frost bevel", "existing ice identity expanded coherently across four slots", ["ice spikes", "full cyan glow", "smooth diamond recolour"], "Only PLATE line with stepped glacier shelves and frost-rune bevels.", "glacier-step", ["frost-bevel", "ward-rune"]),
    "hataror": art("practical border guard plate", ["service iron plate"], ["green wool tabard", "brown strap"], "simple open helmet and compact service panels", ["practical rectangle", "tabard break"], ["#303436", "#5C6262", "#888E8B"], ["#3F5439", "#65745A"], "#A08A60", "green tabard breaks gray plate mass", "minimal border chevron", 0, 0, "scratched early service wear", "open border helm", "compact plate over short tabard", "simple knee guards", "service boots", "green tabard block plus iron edge", "low-complexity early plate remains unmistakably structural", ["crown", "runes", "oversized shoulder"], "Only PLATE line with a plain green border tabard breaking practical service-iron panels.", "guard-tabard", ["border-chevron", "service-iron"]),
    "melyseg_orseg": art("abyss pressure-guard plate", ["pressure-black plate"], ["teal glass inset", "brass seal"], "rounded pressure panels within a massive framed shell", ["pressure dome", "seal ring"], ["#0F1A1F", "#23353C", "#3E5359"], ["#3D7776", "#70A6A0"], "#B18A50", "teal pressure window and brass seal", "encounter-specific seal rings", 2, 1, "salted boss patina", "pressure-seal helm", "deep framed pressure cuirass", "sealed articulated legs", "weighted abyss greaves", "pressure window is unmistakable focal cue", "rounded inner domes stay within plate frames", ["tentacles", "diving fins", "full teal glow"], "Only PLATE line with sealed teal pressure windows inside black framed plates.", "pressure-window", ["pressure-seal", "teal-window"]),
    "osicsarnok": art("ancestral hall sentinel plate", ["aged bronze plate"], ["stone-gray inset", "dark cloth"], "architectural arch frames and column-like greaves", ["arch frame", "column vertical"], ["#332A22", "#67513A", "#95744E"], ["#77736A", "#AAA59A"], "#C0A16A", "bronze arch edge over stone inset", "small hall-key patterns", 1, 0, "honest age and edge polish", "arched sentinel helm", "hall-arch breast frame", "column-panel legs", "plinth-like greaves", "large arch reads at inventory scale", "architectural mass without statue cosplay", ["crown", "giant columns", "gold flood"], "Only PLATE line with ancestral hall arches and column-like vertical articulation.", "hall-arch", ["arch-frame", "column-panel"]),
    "ostromtoro": art("siege-engine boss plate", ["black siege iron"], ["red shock leather", "tempered bolt"], "huge squared chest reinforcement and ram-like horizontal bands", ["horizontal ram", "bolt block"], ["#151617", "#303133", "#515154"], ["#5C2725", "#913A34"], "#C45E43", "red shock strip between black iron blocks", "heavy bolt lines", 1, 0, "impact dents and scorched edges", "block visor helm", "ram-banded siege cuirass", "impact-braced legs", "anchor-heavy greaves", "horizontal red shock band", "most massive boss plate while staying player-scale", ["actual ram horn", "spikes", "flame exhaust"], "Only PLATE line with horizontal battering bands and red shock-leather compression strips.", "siege-bands", ["ram-band", "shock-strip"]),
    "runaforged": art("endgame enchanted forge plate", ["charcoal forged plate"], ["violet conductive channel", "rune steel"], "nested frame geometry around recessed rune channels", ["nested frame", "recessed channel"], ["#17171D", "#33333E", "#555565"], ["#66547F", "#9277AD"], "#B596D1", "violet channel inside charcoal frames", "continuous but sparse forge circuit", 2, 1, "precision forge finish", "channel-framed helm", "nested rune-frame cuirass", "vertical channel plates", "circuit greaves", "one recessed violet channel", "enchantment lives inside structural plate frames", ["rune carpet", "purple metal recolour", "crystal spikes"], "Only PLATE line with recessed violet forge channels nested inside charcoal structural frames.", "rune-channel-frame", ["recessed-channel", "nested-frame"]),
    "salakfal": art("improvised slag-wall plate", ["slag-dark scrap plate"], ["furnace scale", "burnt leather"], "crude overlapping blocks and uneven furnace seams", ["uneven overlap", "slag edge"], ["#1E1B19", "#40372F", "#625044"], ["#7D4228", "#A75C31"], "#C77B3D", "orange slag seam in dark scrap", "minimal furnace clamp marks", 0, 0, "heavy early soot and chipped edges", "riveted scrap helm", "overlap-wall chest", "uneven scrap leggings", "furnace-dark boots", "orange seam between two large blocks", "early plate is crude but still broad and structural", ["lava glow", "spike scrap", "chain field"], "Only PLATE line with uneven slag-block overlaps and narrow furnace-orange seams.", "slag-overlap", ["slag-seam", "scrap-block"]),
    "sarkfeny": art("aurora-tempered crusader plate", ["clean tempered steel"], ["aurora enamel", "pale leather undersuit"], "clean curved-front geometric panels with slim enamel sweep", ["clean frame", "swept inset"], ["#26323B", "#52636E", "#82919A"], ["#497A68", "#765D8D"], "#9BB79E", "muted green-violet enamel on steel", "single aurora sweep", 1, 0, "clean armorer finish", "open aurora-rim helm", "swept-inset crusader breastplate", "clean articulated plates", "enamel-rim greaves", "one two-tone sweep survives grayscale via value edge", "mid-tier refinement with boss headroom intact", ["rainbow field", "full glow", "wing shoulders"], "Only PLATE line with one restrained two-tone aurora-enamel sweep across clean crusader frames.", "aurora-sweep", ["aurora-enamel", "clean-frame"]),
}


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def dump_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"


def build() -> dict[str, Any]:
    lines = load_json(GEAR_LINES)["gear_lines"]
    armor = load_json(ARMOR_MATRIX)["armor"]
    armor_by_line: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for piece in armor:
        armor_by_line[piece["gear_line"]].append(piece)
    production_ids = {line["line_id"] for line in lines}
    if production_ids != set(ART):
        raise SystemExit(f"Art spec drift: missing={sorted(production_ids-set(ART))} extra={sorted(set(ART)-production_ids)}")

    records = []
    for line in sorted(lines, key=lambda value: (value["family"], value["line_id"])):
        pieces = sorted(armor_by_line[line["line_id"]], key=lambda value: value["slot"])
        rarity = sorted({piece["rarity"] for piece in pieces})
        record = {
            "canonical_line_id": line["line_id"],
            "family": line["family"],
            "four_piece_ids": [piece["template_id"] for piece in pieces],
            "piece_slots": {piece["slot"]: piece["template_id"] for piece in pieces},
            "acquisition": line["acquisition"],
            "profession": line["profession"] or None,
            "progression_band": line["progression"],
            "rarity_context": rarity,
            "gameplay_stat_archetype": line["archetype"],
            "set_status": line["set"] or None,
            "signature_status": any(piece["signature"] for piece in pieces),
            "ascension_status": any(piece["ascension"] for piece in pieces),
            **ART[line["line_id"]],
        }
        records.append(record)

    return {
        "schema": 1,
        "minecraft_version": "1.21.11",
        "authority": {
            "production_inputs": [str(GEAR_LINES.relative_to(ROOT)), str(ARMOR_MATRIX.relative_to(ROOT))],
            "generated_by": str(Path(__file__).relative_to(ROOT)),
            "runtime_assets_are_committed_authority": True,
        },
        "global_policy": GLOBAL_POLICY,
        "family_policy": FAMILY_POLICY,
        "acquisition_policy": ACQUISITION_POLICY,
        "progression_policy": PROGRESSION_POLICY,
        "profession_policy": PROFESSION_POLICY,
        "gear_lines": records,
        "pilot_selection": {
            "canonical_manifest": PILOT_MANIFEST,
            "criteria": ["one per family", "mid progression", "crafted and production-reachable", "not prestige", "not Signature", "not Ascension-exclusive", "not a mechanical set"],
        },
        "production_readiness": {
            "specified_lines": len(records),
            "remaining_after_pilot": 36,
            "boss_prestige_headroom_reserved": True,
            "human_client_staging_required": True,
        },
    }


def collision_report(data: dict[str, Any]) -> dict[str, Any]:
    collisions: dict[str, list[dict[str, Any]]] = {"exact_palette": [], "material_combination": [], "motif": [], "silhouette_tag": []}
    for family in FAMILY_POLICY:
        records = [line for line in data["gear_lines"] if line["family"] == family]
        fields = {
            "exact_palette": lambda line: tuple(line["primary_palette"] + line["secondary_palette"] + [line["accent"]]),
            "material_combination": lambda line: tuple(line["primary_materials"] + line["secondary_materials"]),
            "motif": lambda line: tuple(line["motif_tags"]),
            "silhouette_tag": lambda line: line["dominant_silhouette_tag"],
        }
        for kind, getter in fields.items():
            groups: dict[Any, list[str]] = defaultdict(list)
            for record in records:
                groups[getter(record)].append(record["canonical_line_id"])
            for signature, ids in groups.items():
                if len(ids) > 1:
                    collisions[kind].append({"family": family, "lines": ids, "signature": signature})
    return {"collisions": collisions, "pass": not any(collisions.values())}


def render_markdown(data: dict[str, Any]) -> str:
    rows = []
    for line in data["gear_lines"]:
        rows.append(f"| `{line['canonical_line_id']}` | {line['family']} | {line['acquisition']} | {line['progression_band']} | {line['core_fantasy']} | {line['differentiation_note']} |")
    family_sections = []
    for family, policy in FAMILY_POLICY.items():
        family_sections.append(
            f"### {family}\n\n- Silhouette: {policy['silhouette']}\n- Shape grammar: {', '.join(policy['shape_language'])}\n- Materials: {', '.join(policy['material_language'])}\n- Palette: {policy['palette_policy']}\n- Forbidden: {', '.join(policy['forbidden_motifs'])}\n"
        )
    return """# Equipment Resource Pack 2.0 Art Bible

Generated from production-derived gear-line and armor-matrix authority. The JSON sibling is the machine-readable contract; committed PNG/JSON assets are runtime authority.

## Global rules

- Style: Minecraft-native MMORPG.
- Inventory: 64×64 physical texture, authored on a 32×32 logical pixel grid.
- Worn: Minecraft 1.21.11 fixed 64×32 humanoid equipment UV.
- Material/construction and silhouette must communicate family before palette.
- Emissive levels are 0–3 and are reserved; the pilot introduces no emissive/shader dependency.
- Higher progression means more sophisticated construction and motif integration, not automatic gold, glow or visual noise.
- Boss/prestige silhouette exceptions remain reserved headroom.

## Family authority

""" + "\n".join(family_sections) + """
## Acquisition and progression

Profession-crafted pieces use clean deliberate joins; wilderness lines expose environmental wear; boss lines carry encounter-specific motifs; prestige lines use rare treatments without breaking Minecraft readability. Early→endgame increases finish and specificity while preserving sidegrade parity.

## Forty canonical gear lines

| Line | Family | Acquisition | Progression | Core fantasy | Mandatory differentiation |
|---|---|---|---|---|---|
""" + "\n".join(rows) + "\n"


def validate(data: dict[str, Any]) -> None:
    if len(data["gear_lines"]) != 40:
        raise SystemExit("Art Bible must specify exactly 40 gear lines")
    counts = Counter(line["family"] for line in data["gear_lines"])
    if counts != Counter({family: 10 for family in FAMILY_POLICY}):
        raise SystemExit(f"Family line count drift: {counts}")
    required = {"core_fantasy", "primary_materials", "secondary_materials", "silhouette", "shape_language", "primary_palette", "secondary_palette", "accent", "contrast_target", "ornament", "magical_intensity", "emissive_allowance", "weathering", "helmet_language", "chest_language", "leggings_language", "boots_language", "inventory_icon_direction", "worn_armor_direction", "forbidden_motifs", "differentiation_note"}
    for line in data["gear_lines"]:
        missing = required - line.keys()
        if missing or len(line["four_piece_ids"]) != 4 or len(line["piece_slots"]) != 4:
            raise SystemExit(f"Incomplete line art spec {line['canonical_line_id']}: {sorted(missing)}")
    collision = collision_report(data)
    if not collision["pass"]:
        raise SystemExit("Structural art metadata collision: " + json.dumps(collision, ensure_ascii=False))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--collision-output", type=Path)
    args = parser.parse_args()
    data = build()
    validate(data)
    expected_json = dump_json(data)
    expected_md = render_markdown(data)
    if args.write:
        OUTPUT_JSON.write_text(expected_json, encoding="utf-8")
        OUTPUT_MD.write_text(expected_md, encoding="utf-8")
    if args.check or not args.write:
        if not OUTPUT_JSON.is_file() or OUTPUT_JSON.read_text(encoding="utf-8") != expected_json:
            raise SystemExit("equipment-rp2-art-bible.json drift")
        if not OUTPUT_MD.is_file() or OUTPUT_MD.read_text(encoding="utf-8") != expected_md:
            raise SystemExit("equipment-rp2-art-bible.md drift")
    if args.collision_output:
        args.collision_output.parent.mkdir(parents=True, exist_ok=True)
        args.collision_output.write_text(dump_json(collision_report(data)), encoding="utf-8")
    print("Equipment RP2 Art Bible: 40 lines / 10 per family / structural collisions=0")


if __name__ == "__main__":
    main()
