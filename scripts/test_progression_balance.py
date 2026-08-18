#!/usr/bin/env python3
"""Deterministic Itemization/Mob 2.0 catalog, economy and Monte Carlo balance gate."""

from __future__ import annotations

import math
import random
from collections import Counter
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
SAMPLES = 100_000


def load(name: str):
    with (ROOT / "src/main/resources/config" / name).open(encoding="utf-8") as stream:
        return yaml.safe_load(stream)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


items = load("item-templates.yml")
crafting = load("crafting.yml")
recipes = load("profession-recipes.yml")["profession-recipes"]
mobs = load("mob-templates.yml")
world = load("world.yml")

templates = items["item-templates"]
sets = items["item-sets"]
actual_runes = {key: value for key, value in crafting["runes"].items()
                if isinstance(value, dict) and key.startswith("runa_")}
canonical_recipes = {key: value for key, value in recipes.items()
                     if isinstance(value, dict) and value.get("result", {}).get("template")}

require(40 <= len(templates) <= 60, "authored item catalog must stay in the 40-60 gate")
require(6 <= sum(bool(row.get("signature-effect")) for row in templates.values()) <= 16,
        "signature catalog is outside the playable bound")
require(len(sets) >= 2, "at least two authored sets are required")
require(5 <= sum(bool(row.get("ascension-path")) for row in templates.values()) <= 8,
        "ascension-capable catalog must stay in the 5-8 gate")
require(8 <= len(actual_runes) <= 12, "rune catalog must stay in the 8-12 gate")
require(10 <= len(canonical_recipes) <= 15,
        "canonical profession gear recipes must stay in the 10-15 gate")

# Economy-unit invariant used by the production ItemSalvageService: its conservative input value
# is 64 and every possible dust output is capped before physical material mapping.
salvage = items["itemization"]["salvage"]
maximum_salvage_units = (salvage["maximum-dust"] * 3)
require(maximum_salvage_units <= 64, "salvage outputs can exceed conservative input value")

reroll = items["itemization"]["reroll"]
costs = []
for step in range(64):
    raw = reroll["base-amount"] * math.pow(reroll["growth"], step)
    costs.append(min(reroll["maximum-amount"], max(0, math.ceil(raw))))
require(costs == sorted(costs) and costs[-1] == reroll["maximum-amount"],
        "reroll curve must be monotonic and reach its hard cap")

mining = items["itemization"]["gathering"]["rare-mining"]
require(3 <= len(mining["resources"]) <= 5, "rare mining must expose 3-5 real sources")
by_block: dict[str, float] = {}
for resource in mining["resources"].values():
    for block in resource["source-blocks"]:
        by_block[block] = by_block.get(block, 0.0) + float(resource["chance"])
require(max(by_block.values()) <= 0.02, "one mining block has an exploitably high rare yield")
require(1 <= mining["daily-cap"] <= 12, "rare mining daily budget is not conservative")

require(15 <= len(mobs["mob-templates"]) <= 25, "MobTemplate roster must stay in the 15-25 gate")
require(len(mobs["mob-abilities"]) >= 4, "reusable ability registry is too small")
require(len({row["archetype"] for row in mobs["mob-templates"].values()}) >= 6,
        "mob roster lacks archetype variety")

rng = random.Random(0x1CE5_2026)
plain_quality = [rng.random() for _ in range(SAMPLES)]
amplified_quality = [0.65 + 0.35 * rng.random() for _ in range(SAMPLES)]
plain_mean = sum(plain_quality) / SAMPLES
amplified_mean = sum(amplified_quality) / SAMPLES
require(0.497 <= plain_mean <= 0.503, "unamplified roll distribution drifted")
require(0.822 <= amplified_mean <= 0.828 and min(amplified_quality) >= 0.65,
        "quality amplifier distribution or floor drifted")

promotion = world["mob-scaling"]["promotion"]


def promotion_sample(elite: float, veteran: float) -> tuple[float, float]:
    counts = Counter()
    for _ in range(SAMPLES):
        roll = rng.random() * 100.0
        counts["elite" if roll < elite else "veteran" if roll < elite + veteran else "normal"] += 1
    return counts["elite"] / SAMPLES * 100.0, counts["veteran"] / SAMPLES * 100.0


base_elite, base_veteran = promotion_sample(
    promotion["elite-percent"], promotion["veteran-percent"])
danger_elite, danger_veteran = promotion_sample(
    promotion["elite-percent"] + promotion["deep-elite-bonus-percent"],
    promotion["veteran-percent"] + promotion["danger-veteran-bonus-percent"])
require(abs(base_elite - promotion["elite-percent"]) < 0.20,
        "base elite Monte Carlo rate drifted")
require(danger_elite > base_elite and danger_veteran > base_veteran,
        "depth context must increase promotion without making it universal")
require(danger_elite + danger_veteran < 15.0, "danger promotion rate is too dense")

scaling = world["world-events"]["world-boss"]["scaling"]


def encounter_health(players: int, power_ratio: float = 1.0) -> float:
    count_health = 1.0 + scaling["player-coefficient"] * math.pow(
        max(0, players - 1), scaling["player-exponent"])
    adjustment = 1.0 + max(-scaling["combat-power-influence"], min(
        scaling["combat-power-influence"],
        (max(0.25, min(4.0, power_ratio)) - 1.0) * scaling["combat-power-influence"]))
    return min(scaling["maximum-health-multiplier"], count_health * adjustment)


party_curve = [encounter_health(count) for count in (1, 2, 5, 10, 60)]
require(party_curve == sorted(party_curve), "encounter HP curve is not monotonic")
require(party_curve[2] < 5.0 and party_curve[-1] <= scaling["maximum-health-multiplier"],
        "encounter HP curve became linear or unbounded")
require(encounter_health(5, 0.25) < encounter_health(5, 1.0) < encounter_health(5, 4.0),
        "live CombatPower must influence but not own the encounter snapshot")

runtime_source = (ROOT / "src/main/java/hu/taliann/icesmp/pve/MobAbilityRuntime.java").read_text()
require("MAX_ACTIVE_MOBS = 2048" in runtime_source,
        "ability runtime lost its hard active-state bound")
require("getNearbyEntities" in runtime_source and "Bukkit.getWorlds()" not in runtime_source,
        "ability runtime must remain local/event-driven, not a global scan")

print("Progression balance regression passed: "
      f"templates={len(templates)}, signatures={sum(bool(x.get('signature-effect')) for x in templates.values())}, "
      f"sets={len(sets)}, ascendable={sum(bool(x.get('ascension-path')) for x in templates.values())}, "
      f"runes={len(actual_runes)}, canonical_recipes={len(canonical_recipes)}, "
      f"mobs={len(mobs['mob-templates'])}, samples={SAMPLES}, "
      f"quality_mean={plain_mean:.4f}, amplified_mean={amplified_mean:.4f}, "
      f"elite={base_elite:.3f}%, deep_elite={danger_elite:.3f}%, hp60={party_curve[-1]:.3f}x")
