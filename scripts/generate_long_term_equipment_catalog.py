#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/config/equipment-catalog-expansion.yml"
PILOT_MANIFEST = ROOT / "docs/development/equipment-rp2-pilot-manifest.json"
PRODUCTION_MANIFEST = ROOT / "docs/development/equipment-rp2-production-manifest.json"
SLOTS = ("head", "chest", "legs", "feet")
SUFFIX = {"head": "sisak", "chest": "mellvert", "legs": "labvert", "feet": "csizma"}

LINES: dict[str, list[dict[str, Any]]] = {
    "CLOTH": [
        {"id":"fonixszovet","name":"Főnixszövet","acq":"crafted","profession":"enchanter","archetype":"Arcane","band":"endgame","anchors":{"chest":"fonixpihe_kopeny"}},
        {"id":"holdlen","name":"Holdlen","acq":"crafted","profession":"enchanter","archetype":"Ritual","band":"mid"},
        {"id":"nemakristaly","name":"Némakristály","acq":"crafted","profession":"enchanter","archetype":"Arcane","band":"high"},
        {"id":"alkimista_fatyol","name":"Alkimista Fátyol","acq":"crafted","profession":"alchemist","archetype":"Sanctified","band":"mid"},
        {"id":"csontvarro","name":"Csontvarró","acq":"world","archetype":"Sanctified","band":"early","anchors":{"chest":"csontvarro_kopeny"}},
        {"id":"kodszovo","name":"Ködszövő","acq":"world","archetype":"Veil","band":"early"},
        {"id":"melyseg_ritual","name":"Mélységi Rítus","acq":"world","archetype":"Ritual","band":"high"},
        {"id":"csillagfatyol","name":"Csillagfátyol","acq":"boss","archetype":"Arcane","band":"endgame","set":"csillagfatyol"},
        {"id":"szenthamvak","name":"Szent Hamvak","acq":"boss","archetype":"Sanctified","band":"endgame","set":"szenthamvak"},
        {"id":"aranyfust","name":"Aranyfüst","acq":"prestige","archetype":"Veil","band":"endgame","anchors":{"chest":"aranyfust_kopeny"}},
    ],
    "LEATHER": [
        {"id":"vadorzo","name":"Vadőrző","acq":"crafted","profession":"alchemist","archetype":"Predator","band":"mid","anchors":{"feet":"vadorzo_csizma"}},
        {"id":"vadbor","name":"Vadbőr","acq":"crafted","profession":"alchemist","archetype":"Wildheart","band":"mid"},
        {"id":"vizbor","name":"Vízbőr","acq":"crafted","profession":"alchemist","archetype":"Demonhide","band":"high"},
        {"id":"kitinbor","name":"Kitinbőr","acq":"crafted","profession":"armorer","archetype":"Shadow","band":"endgame"},
        {"id":"utjaro","name":"Útjáró","acq":"world","archetype":"Shadow","band":"early","anchors":{"feet":"utjaro_csizma"}},
        {"id":"sotetmoha","name":"Sötétmoha","acq":"world","archetype":"Wildheart","band":"early"},
        {"id":"verszavanna","name":"Vérszavanna","acq":"world","archetype":"Predator","band":"high"},
        {"id":"predator_karma","name":"Predátor Karma","acq":"boss","archetype":"Predator","band":"endgame","set":"predator_karma"},
        {"id":"demonbor","name":"Démonbőr","acq":"boss","archetype":"Demonhide","band":"endgame","set":"demonbor"},
        {"id":"holdarnyek","name":"Holdárnyék","acq":"prestige","archetype":"Shadow","band":"endgame"},
    ],
    "MAIL": [
        {"id":"csontenyv","name":"Csontenyv","acq":"crafted","profession":"armorer","archetype":"Warden","band":"mid","anchors":{"chest":"csontenyv_pancel"}},
        {"id":"konnyu_otvozet","name":"Könnyű Ötvözet","acq":"crafted","profession":"armorer","archetype":"Hunter","band":"mid"},
        {"id":"runalanc","name":"Rúnalánc","acq":"crafted","profession":"enchanter","archetype":"Runic","band":"high"},
        {"id":"viharkvarc_runas","name":"Viharkvarc Rúnás","acq":"crafted","profession":"enchanter","archetype":"Tempest","band":"endgame"},
        {"id":"vadvadasz","name":"Vadvadász","acq":"world","archetype":"Hunter","band":"early"},
        {"id":"gyongyhaz_warden","name":"Gyöngyház Őr","acq":"world","archetype":"Warden","band":"early"},
        {"id":"viharszel","name":"Viharszél","acq":"world","archetype":"Tempest","band":"high"},
        {"id":"viharjaro","name":"Viharjáró","acq":"boss","archetype":"Tempest","band":"endgame","set":"viharjaro","anchors":{"head":"viharjaro_sisak","chest":"viharjaro_mellvert","legs":"viharjaro_labvert","feet":"viharjaro_bakancs"}},
        {"id":"runapajzs","name":"Rúnapajzs","acq":"boss","archetype":"Runic","band":"endgame","set":"runapajzs"},
        {"id":"melyvizi_vadasz","name":"Mélyvízi Vadász","acq":"prestige","archetype":"Hunter","band":"endgame"},
    ],
    "PLATE": [
        {"id":"glatziendorfi","name":"Glatziendorfi","acq":"crafted","profession":"armorer","archetype":"Runeforged","band":"high","anchors":{"chest":"glatziendorfi_jegvert"}},
        {"id":"borostyan_tarna","name":"Borostyán Tárna","acq":"crafted","profession":"armorer","archetype":"Bulwark","band":"mid","anchors":{"chest":"melysegi_borostyan_mellvert"}},
        {"id":"sarkfeny","name":"Sarkfény","acq":"crafted","profession":"armorer","archetype":"Crusader","band":"mid","anchors":{"head":"sarkfeny_sisak"}},
        {"id":"runaforged","name":"Rúnakovácsolt","acq":"crafted","profession":"enchanter","archetype":"Runeforged","band":"endgame"},
        {"id":"hataror","name":"Határőr","acq":"world","archetype":"Crusader","band":"early"},
        {"id":"salakfal","name":"Salakfal","acq":"world","archetype":"Dread","band":"early"},
        {"id":"osicsarnok","name":"Ősi Csarnok","acq":"world","archetype":"Bulwark","band":"high"},
        {"id":"melyseg_orseg","name":"Mélység Őrsége","acq":"boss","archetype":"Bulwark","band":"endgame","set":"melyseg_orseg","anchors":{"head":"melyseg_orseg_sisak","chest":"melyseg_orseg_mellvert","legs":"melyseg_orseg_labvert","feet":"melyseg_orseg_bakancs"}},
        {"id":"ostromtoro","name":"Ostromtörő","acq":"boss","archetype":"Dread","band":"endgame","set":"ostromtoro_ostromfal","anchors":{"chest":"ostromtoro_mellvert"}},
        {"id":"csillagacel","name":"Csillagacél","acq":"prestige","archetype":"Crusader","band":"endgame"},
    ],
}

MATERIAL = {
    "CLOTH": {"head":"LEATHER_HELMET","chest":"LEATHER_CHESTPLATE","legs":"LEATHER_LEGGINGS","feet":"LEATHER_BOOTS"},
    "LEATHER": {"head":"LEATHER_HELMET","chest":"LEATHER_CHESTPLATE","legs":"LEATHER_LEGGINGS","feet":"LEATHER_BOOTS"},
    "MAIL": {"head":"CHAINMAIL_HELMET","chest":"CHAINMAIL_CHESTPLATE","legs":"CHAINMAIL_LEGGINGS","feet":"CHAINMAIL_BOOTS"},
    "PLATE": {"head":"IRON_HELMET","chest":"IRON_CHESTPLATE","legs":"IRON_LEGGINGS","feet":"IRON_BOOTS"},
}
# Combat-budget authority. The slot shares sum to one, so a complete set is directly comparable
# across families and progression bands. Armor remains the defensive identity; the remaining
# weighted budget is allocated to the line's authored sidegrade pattern.
SLOT_SHARE = {"head": .19, "chest": .34, "legs": .28, "feet": .19}
# Full-set combat value tracks actual Paper survival checkpoints: early is an iron-sidegrade,
# mid clears the diamond transition, and high/endgame add bounded vertical identity above
# netherite without treating any backing Material as the canonical source of stats.
SET_BUDGET = {"early": 32.0, "mid": 60.0, "high": 76.0, "endgame": 92.0}
SET_ARMOR = {
    "CLOTH": {"early": 6.0, "mid": 7.5, "high": 9.0, "endgame": 10.5},
    "LEATHER": {"early": 8.0, "mid": 10.0, "high": 11.5, "endgame": 13.0},
    "MAIL": {"early": 11.0, "mid": 13.0, "high": 15.0, "endgame": 17.0},
    "PLATE": {"early": 15.0, "mid": 17.0, "high": 19.0, "endgame": 20.0},
}
SET_TOUGHNESS = {
    "CLOTH": {"early": 0.0, "mid": 0.0, "high": 0.0, "endgame": 0.0},
    "LEATHER": {"early": 0.0, "mid": 0.0, "high": 1.0, "endgame": 2.0},
    "MAIL": {"early": 0.0, "mid": 2.0, "high": 4.0, "endgame": 6.0},
    "PLATE": {"early": 2.0, "mid": 4.0, "high": 6.0, "endgame": 8.0},
}
STAT_WEIGHTS = {
    "attack_damage": 5.0,
    "attack_speed": 8.0,
    "ability_power": 1.25,
    "max_health": .70,
    "armor": 2.0,
    "armor_toughness": 2.5,
    "movement_speed": 120.0,
}
PATTERN = {
    "CLOTH": {"Arcane":{"ability_power":2.0,"max_health":1.0},"Ritual":{"ability_power":1.3,"max_health":1.6},"Veil":{"movement_speed":.006,"max_health":1.5},"Sanctified":{"max_health":2.0,"ability_power":1.0}},
    "LEATHER": {"Predator":{"attack_damage":.65,"attack_speed":.045},"Shadow":{"movement_speed":.007,"attack_damage":.45},"Wildheart":{"max_health":1.7,"movement_speed":.0045},"Demonhide":{"armor":.65,"attack_damage":.45}},
    "MAIL": {"Hunter":{"attack_damage":.55,"movement_speed":.004},"Warden":{"armor":.70,"max_health":1.5},"Tempest":{"ability_power":1.5,"movement_speed":.0035},"Runic":{"ability_power":1.25,"armor":.55}},
    "PLATE": {"Bulwark":{"armor":.85,"max_health":1.7},"Crusader":{"max_health":1.5,"ability_power":.9},"Dread":{"attack_damage":.50,"armor":.60},"Runeforged":{"armor_toughness":.65,"ability_power":.9}},
}
BAND_LEVEL = {"early":14,"mid":24,"high":33,"endgame":42}
BAND_RARITY = {"early":"uncommon","mid":"rare","high":"epic","endgame":"legendary"}
SLOT_OFFSET = {"head":0,"chest":1,"legs":0,"feet":-1}
RANK_BY_LINE = {4:"NORMAL",5:"VETERAN",6:"ELITE",7:"BOSS",8:"BOSS",9:"CHAMPION"}
RANK_SOURCE = {"NORMAL":"combat:wilderness","VETERAN":"combat:veteran","ELITE":"combat:elite","CHAMPION":"combat:champion","BOSS":"combat:boss"}
SLOT_NAME = {
    "CLOTH":{"head":"Csuklya","chest":"Köpeny","legs":"Nadrág","feet":"Sarú"},
    "LEATHER":{"head":"Maszk","chest":"Vért","legs":"Nadrág","feet":"Csizma"},
    "MAIL":{"head":"Sisak","chest":"Láncvért","legs":"Lábvért","feet":"Bakancs"},
    "PLATE":{"head":"Sisak","chest":"Mellvért","legs":"Lábvért","feet":"Csizma"},
}
GATHERING = {
    "CLOTH":["herbalist:fiber","gathering:arcane"],
    "LEATHER":["hunting:hide","herbalist:tannin"],
    "MAIL":["mining:light_metal","hunting:cordage"],
    "PLATE":["mining:heavy_metal","mining:crystal"],
}
RECIPE_THEME = {
    ("CLOTH","fonixszovet"):[("runaszott_poszto",None),("holdlen_fonal",None),("fonixpihe",1)],
    ("CLOTH","holdlen"):[("szott_poszto",None),("holdlen_fonal",None),("fenyves_gyanta",1)],
    ("CLOTH","nemakristaly"):[("runaszott_poszto",None),("holdlen_fonal",None),("nema_kristaly",1),("szivfa_mag",1)],
    ("CLOTH","alkimista_fatyol"):[("szott_poszto",None),("kotogyanta",1),("gyogy_kivonat",1)],
    ("LEATHER","vadorzo"):[("erositett_bor",None),("in_kotelez",1),("vad_esszencia",1)],
    ("LEATHER","vadbor"):[("erositett_bor",None),("in_kotelez",1),("vad_esszencia",1),("cserle",1)],
    ("LEATHER","vizbor"):[("erositett_bor",None),("vizbor",1),("halolaj",1),("bokic_gyongy",1)],
    ("LEATHER","kitinbor"):[("erositett_bor",None),("kitin_lemez",1),("in_kotelez",1)],
    ("MAIL","csontenyv"):[("sodrott_lancszem",None),("cserzett_bor",1),("csontenyv",1)],
    ("MAIL","konnyu_otvozet"):[("sodrott_lancszem",None),("konnyu_otvozet",1),("in_kotelez",1)],
    ("MAIL","runalanc"):[("sodrott_lancszem",None),("holdlen_fonal",1),("runapor",1),("gyongyhaz_hej",1)],
    ("MAIL","viharkvarc_runas"):[("sodrott_lancszem",None),("konnyu_otvozet",1),("viharkvarc",1),("melyvizi_esszencia",1)],
    ("PLATE","glatziendorfi"):[("kovacsolt_lemez",None),("sarkfeny_cseppko",1),("tiszta_vasesszencia",1)],
    ("PLATE","borostyan_tarna"):[("kovacsolt_lemez",None),("melysegi_borostyan",1),("tiszta_vasesszencia",1)],
    ("PLATE","sarkfeny"):[("kovacsolt_lemez",None),("sarkfeny_cseppko",1),("tiszta_vasesszencia",1)],
    ("PLATE","runaforged"):[("kovacsolt_lemez",None),("runapor",1),("szorny_szerv",1)],
}
AMOUNT = {"head":2,"chest":4,"legs":3,"feet":2}
EXISTING_RECIPE = {
    "fonixpihe_kopeny":"p2_fonixpihe_kopeny",
    "vadorzo_csizma":"p2_vadorzo_csizma",
    "csontenyv_pancel":"p2_csontenyv_pancel",
    "glatziendorfi_jegvert":"glatziendorfi_jegvert",
    "melysegi_borostyan_mellvert":"item2_borostyan_tarnavert",
    "sarkfeny_sisak":"item2_sarkfeny_sisak",
}

ITEM_SETS = {
    "csillagfatyol":{"display-name":"Csillagfátyol Öltözet","tiers":{2:{"fixed-stats":{"ability_power":1.0}},4:{"fixed-stats":{"max_health":1.0}}}},
    "szenthamvak":{"display-name":"Szent Hamvak Öltözete","tiers":{2:{"fixed-stats":{"max_health":1.0}},4:{"fixed-stats":{"movement_speed":.002}}}},
    "predator_karma":{"display-name":"Predátor Karmája","tiers":{2:{"fixed-stats":{"attack_speed":.02}},4:{"fixed-stats":{"movement_speed":.002}}}},
    "demonbor":{"display-name":"Démonbőr Öltözet","tiers":{2:{"fixed-stats":{"armor":.5}},4:{"fixed-stats":{"max_health":1.0}}}},
    "runapajzs":{"display-name":"Rúnapajzs Lánc","tiers":{2:{"fixed-stats":{"armor":.5}},4:{"fixed-stats":{"ability_power":1.0}}}},
    "ostromtoro_ostromfal":{"display-name":"Ostromtörő Ostromfal","tiers":{2:{"fixed-stats":{"armor":.5}},4:{"fixed-stats":{"attack_damage":.25}}}},
}
ASCENSION_COSTS = {
    "csillagfatyol_mellvert":{"szivfa_mag":2,"holdlen_fonal":4,"runapor":4},
    "demonbor_mellvert":{"bokic_gyongy":2,"vizbor":3,"halolaj":2},
    "melyvizi_vadasz_mellvert":{"melyvizi_esszencia":2,"gyongyhaz_hej":3,"runapor":4},
    "runaforged_mellvert":{"szorny_szerv":2,"kovacsolt_lemez":3,"runapor":4},
}

# Existing combat catalog only. These are overlays: identity, material, visuals, acquisition,
# restrictions, signature, set and ascension authorities remain in item-templates.yml.
COMBAT_ITEM_RECALIBRATION: dict[str, dict[str, Any]] = {
    "kovacstanonc_penge": {"base-damage": 4.0, "fixed-stats": {"attack_speed": -2.4}},
    "borkeretes_ij": {"fixed-stats": {"ability_power": .5}},
    "mohas_fokusz": {"fixed-stats": {"ability_power": 1.5}},
    "tarnapajzs": {"base-armor": 1.0, "fixed-stats": {"max_health": 1.0}},
    "holdfeny_bot": {"fixed-stats": {"ability_power": 3.0}},
    "hamuszeli_vadaszij": {"fixed-stats": {"ability_power": 2.0}},
    "vadvilagi_eskukard": {"base-damage": 5.3, "fixed-stats": {"attack_speed": -2.4}},
    "viharkvarc_pallos": {"base-damage": 6.0, "fixed-stats": {"attack_speed": -2.7}},
    "szelvago_ij": {"fixed-stats": {"ability_power": 2.5}},
    "gyongyhaz_pajzs": {"base-armor": 2.0, "fixed-stats": {"max_health": 2.0}},
    "nema_kristaly_fokusz": {"fixed-stats": {"ability_power": 4.0}},
    "parazs_kard": {"base-damage": 5.8, "fixed-stats": {"attack_speed": -2.4}},
    "holdsarlo": {"base-damage": 4.8, "fixed-stats": {"ability_power": 3.0, "attack_speed": -2.2}},
    "viharszovo_bot": {"base-damage": 6.5, "fixed-stats": {"ability_power": 4.0, "attack_speed": -2.9}},
    "glatziendorfi_jegtoro": {"base-damage": 7.0, "fixed-stats": {"ability_power": 3.0, "attack_speed": -3.2}},
    "csillagvesz_ritualbot": {"fixed-stats": {"ability_power": 5.0}},
    "kapuparazs_pallos": {"base-damage": 7.2, "fixed-stats": {"attack_speed": -2.7}},
    "sarkanycsont_ij": {"fixed-stats": {"ability_power": 4.0}},
    "verszavanna_agyara": {"base-damage": 6.8, "fixed-stats": {"attack_speed": -2.4}},
    "kallan_szeletelo": {"base-damage": 7.5, "fixed-stats": {"attack_speed": -3.2}},
    "pyralingradi_tuzkopo": {"fixed-stats": {"ability_power": 5.0}},
    "zhoris_langnyelve": {"base-damage": 4.5, "fixed-stats": {"ability_power": 7.0, "attack_speed": -2.2}},
    "napfogyatkozas_fokusz": {"fixed-stats": {"ability_power": 8.0}},
    "miinus_haragja": {"base-damage": 8.5, "fixed-stats": {"attack_speed": -3.0}},
    "elsocsend_penge": {"base-damage": 8.0, "fixed-stats": {"attack_speed": -2.4}},
}


def base_armor(family: str, band: str, slot: str) -> float:
    return round(SET_ARMOR[family][band] * SLOT_SHARE[slot], 2)


def fixed_stats(family: str, archetype: str, band: str, slot: str,
                reserved_stats: dict[str, float] | None = None) -> dict[str, float]:
    reserved_stats = reserved_stats or {}
    reserved_budget = sum(value * STAT_WEIGHTS[stat] for stat, value in reserved_stats.items())
    target = SET_BUDGET[band] * SLOT_SHARE[slot]
    armor_budget = base_armor(family, band, slot) * STAT_WEIGHTS["armor"]
    toughness = round(max(0.0, SET_TOUGHNESS[family][band] * SLOT_SHARE[slot]
                          - reserved_stats.get("armor_toughness", 0.0)), 2)
    remaining = max(0.0, target - armor_budget
                    - toughness * STAT_WEIGHTS["armor_toughness"] - reserved_budget)
    pattern = PATTERN[family][archetype]
    authored_weight = sum(abs(value) * STAT_WEIGHTS[stat] for stat, value in pattern.items())
    result: dict[str, float] = {}
    for stat, value in pattern.items():
        share = abs(value) * STAT_WEIGHTS[stat] / authored_weight
        raw = remaining * share / STAT_WEIGHTS[stat]
        result[stat] = round(raw, 4 if stat in {"movement_speed", "attack_speed"} else 2)
    if toughness > 0.0:
        result["armor_toughness"] = round(result.get("armor_toughness", 0.0) + toughness, 2)
    return result


def midpoint_stats(stats: dict[str, Any] | None) -> dict[str, float]:
    result: dict[str, float] = {}
    for stat, bounds in (stats or {}).items():
        result[stat] = (float(bounds["min"]) + float(bounds["max"])) / 2.0
    return result


def template_id(line: dict[str, Any], slot: str) -> str:
    return line.get("anchors", {}).get(slot, f"{line['id']}_{SUFFIX[slot]}")


def rp2_presentations() -> dict[str, dict[str, str]]:
    manifest_path = PRODUCTION_MANIFEST if PRODUCTION_MANIFEST.is_file() else PILOT_MANIFEST
    if not manifest_path.is_file():
        return {}
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    pieces = manifest.get("pieces", [])
    presentations = {
        piece["template_id"]: {
            "item-model": piece["item_model"],
            "equipment-asset": piece["equipment_asset"],
        }
        for piece in pieces
    }
    expected = 160 if manifest_path == PRODUCTION_MANIFEST else 16
    if pieces and len(presentations) != expected:
        raise AssertionError(f"RP2 presentation manifest must expose exactly {expected} pieces")
    return presentations


def render() -> str:
    templates: dict[str, dict[str, Any]] = {}
    recipes: dict[str, dict[str, Any]] = {}
    acquisition = Counter()
    professions = Counter()
    armor_ids: list[str] = []
    new_count = 0
    rp2 = rp2_presentations()
    base_catalog = yaml.safe_load((ROOT / "src/main/resources/config/item-templates.yml")
                                  .read_text(encoding="utf-8")) or {}
    base_templates: dict[str, dict[str, Any]] = base_catalog.get("item-templates", {}) or {}

    for family, family_lines in LINES.items():
        if len(family_lines) != 10:
            raise AssertionError(f"{family}: exactly 10 gear lines required")
        for line_index, line in enumerate(family_lines):
            rank = RANK_BY_LINE.get(line_index, "CRAFTED")
            for slot in SLOTS:
                ident = template_id(line, slot)
                armor_ids.append(ident)
                acquisition[line["acq"]] += 1
                metadata = {
                    "catalog-line": line["id"],
                    "catalog-acquisition": line["acq"],
                    "catalog-archetype": line["archetype"],
                    "progression-band": line["band"],
                    "rank-eligibility": rank if line["acq"] != "crafted" else "CRAFTED",
                }
                if line.get("profession"):
                    metadata["catalog-profession"] = line["profession"]
                if ident in line.get("anchors", {}).values():
                    authored = base_templates.get(ident, {})
                    reserved = midpoint_stats(authored.get("rolled-stats"))
                    recalibrated = fixed_stats(family, line["archetype"], line["band"], slot, reserved)
                    # The packaged merge is leaf-based. Explicit zeroes retire obsolete fixed
                    # leaves without changing template identity/version or the signature rolls.
                    fixed = {key: 0.0 for key in (authored.get("fixed-stats") or {})}
                    fixed.update(recalibrated)
                    item_level = BAND_LEVEL[line["band"]] + SLOT_OFFSET[slot]
                    patch: dict[str, Any] = {
                        "rarity": BAND_RARITY[line["band"]],
                        "item-level": item_level,
                        "level-requirement": max(1, item_level - 4),
                        "base-armor": base_armor(family, line["band"], slot),
                        "fixed-stats": fixed,
                        "rune-sockets": 0 if line["band"] == "early" else (1 if line["band"] in {"mid", "high"} else 2),
                        "encounter-metadata": metadata,
                    }
                    if ident == "fonixpihe_kopeny":
                        patch["source-tags"] = ["combat:event", "profession:enchanter", "catalog:crafted"]
                    elif ident == "vadorzo_csizma":
                        patch["source-tags"] = ["profession:alchemist", "combat:wilderness", "catalog:crafted"]
                    if line.get("set"):
                        patch["set-id"] = line["set"]
                    patch.update(rp2.get(ident, {}))
                    templates[ident] = patch
                else:
                    new_count += 1
                    item_level = BAND_LEVEL[line["band"]] + SLOT_OFFSET[slot]
                    entry: dict[str, Any] = {
                        "schema": 2,
                        "version": 1,
                        "display-name": f"{line['name']} {SLOT_NAME[family][slot]}",
                        "lore": [f"{line['archetype']} {family} line: oldalirányú buildválasztás, nem nyers power-creep."],
                        "rarity": BAND_RARITY[line["band"]],
                        "item-level": item_level,
                        "family": "armor",
                        "armor-family": family.lower(),
                        "slot": slot,
                        "material": MATERIAL[family][slot],
                        "level-requirement": max(1, item_level - 4),
                        "base-armor": base_armor(family, line["band"], slot),
                        "fixed-stats": fixed_stats(family, line["archetype"], line["band"], slot),
                        "rune-sockets": 0 if line["band"] == "early" else (1 if line["band"] in {"mid", "high"} else 2),
                        "source-tags": ([f"profession:{line['profession']}", "catalog:crafted"] if line["acq"] == "crafted" else [RANK_SOURCE[rank], f"rank:{rank.lower()}"]),
                        "gathering-tags": GATHERING[family],
                        "encounter-metadata": metadata,
                    }
                    if line.get("set"):
                        entry["set-id"] = line["set"]
                    entry.update(rp2.get(ident, {}))
                    templates[ident] = entry

                if line["acq"] != "crafted":
                    continue
                profession = line["profession"]
                professions[profession] += 1
                if ident in EXISTING_RECIPE:
                    recipes[EXISTING_RECIPE[ident]] = {
                        "profession": profession,
                        "economy-category": "CANONICAL_GEAR",
                        "material-tier": "ENDGAME" if line["band"] == "endgame" else line["band"].upper(),
                    }
                    continue
                ingredients: list[str] = []
                dependencies: list[str] = []
                for material_id, authored_amount in RECIPE_THEME[(family, line["id"])]:
                    amount = authored_amount if authored_amount is not None else AMOUNT[slot]
                    ingredients.append(f"unique:{material_id}:{amount}")
                    dependencies.append(material_id)
                recipes[f"lte_{ident}"] = {
                    "profession": profession,
                    "kind": "crafting",
                    "economy-category": "CANONICAL_GEAR",
                    "material-tier": "ENDGAME" if line["band"] == "endgame" else line["band"].upper(),
                    "economy-managed": True,
                    "batchable": False,
                    "level": max(1, BAND_LEVEL[line["band"]] - 4),
                    "learn": "level",
                    "display-name": f"{line['name']} craft",
                    "category": f"{family} • {line['archetype']}",
                    "result": {"template": ident, "amount": 1},
                    "ingredients": ingredients,
                    "processing-dependencies": dependencies,
                }

    if len(armor_ids) != 160 or len(set(armor_ids)) != 160 or new_count != 142:
        raise AssertionError(f"catalog identity drift: total={len(armor_ids)} unique={len(set(armor_ids))} new={new_count}")
    if acquisition != Counter({"crafted":64,"world":48,"boss":32,"prestige":16}):
        raise AssertionError(f"acquisition split drift: {acquisition}")
    if professions != Counter({"armorer":24,"enchanter":24,"alchemist":16}):
        raise AssertionError(f"profession ownership drift: {professions}")
    if len(recipes) != 64:
        raise AssertionError(f"crafted recipe output count drift: {len(recipes)}")
    if rp2 and len(rp2) not in {16, 160}:
        raise AssertionError(f"RP2 presentation count drift: {len(rp2)}")

    if set(COMBAT_ITEM_RECALIBRATION) != {
            ident for ident, template in base_templates.items()
            if str(template.get("family", "")) == "weapon"
            or (str(template.get("slot", "")) == "off-hand"
                and str(template.get("material", "")) == "SHIELD")}:
        raise AssertionError("existing weapon/off-hand catalog identity drift")
    for ident, mechanical_patch in COMBAT_ITEM_RECALIBRATION.items():
        authored = base_templates[ident]
        item_level = int(authored["item-level"])
        patch = dict(mechanical_patch)
        patch["level-requirement"] = max(1, item_level - 4)
        templates[ident] = patch

    for ident, costs in ASCENSION_COSTS.items():
        template = templates[ident]
        template["ascension-path"] = ["awakened"]
        template["ascension-stages"] = {
            "awakened": {
                "item-level": int(template["item-level"]) + 4,
                "level-requirement": int(template["level-requirement"]) + 4,
                "fixed-stats": {key: round(value * 1.08, 4) for key, value in template["fixed-stats"].items()},
                "rune-sockets": 2,
                "signature-tier": 0,
                "lore": ["A meglévő build-identitást erősíti, nem új rarityt hoz létre."],
            }
        }

    document = {
        "item-sets": ITEM_SETS,
        "item-templates": templates,
        "profession-recipes": recipes,
        "itemization": {"ascension": {ident: {"awakened": {"materials": costs}} for ident, costs in ASCENSION_COSTS.items()}},
    }
    return (
        "# GENERATED by scripts/generate_long_term_equipment_catalog.py; edit the line vocabulary, not this output.\n"
        "# The generated leaves extend the canonical ItemTemplate / ProfessionRecipe authorities.\n"
        + yaml.safe_dump(document, allow_unicode=True, sort_keys=False, width=120)
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    rendered = render()
    if args.write:
        OUTPUT.write_text(rendered, encoding="utf-8")
    if args.check or not args.write:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != rendered:
            raise SystemExit("equipment-catalog-expansion.yml is stale; run generator with --write")
    print("Long-term equipment catalog generator: 160 armor / 25 combat items / 40 lines / 64 crafted recipes / 8 mechanical-set lines")


if __name__ == "__main__":
    main()
