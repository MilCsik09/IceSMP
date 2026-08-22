#!/usr/bin/env python3
"""Fail-closed static evidence for the unified creature profile and technique authority."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "src/main/resources/config/mob-templates.yml"
PROFILE_SOURCE = ROOT / "src/main/java/hu/taliann/icesmp/pve/CreatureProfileService.java"
RUNTIME_SOURCE = ROOT / "src/main/java/hu/taliann/icesmp/pve/MobAbilityRuntime.java"
SCALING_SOURCE = ROOT / "src/main/java/hu/taliann/icesmp/managers/MobScalingManager.java"
TEMPERAMENT_ORDER = [
    "TIMID", "CALM", "DEFENSIVE", "TERRITORIAL", "HERD_DEFENSIVE", "PACK_DEFENSIVE"
]
MASK = (1 << 64) - 1


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"unified creature combat audit failed: {message}")


def rotate_left(value: int, bits: int) -> int:
    value &= MASK
    return ((value << bits) | (value >> (64 - bits))) & MASK


def stable_unit(msb: int, lsb: int, salt: int) -> float:
    value = (msb ^ rotate_left(lsb, 29) ^ salt) & MASK
    value ^= value >> 30
    value = (value * 0xBF58476D1CE4E5B9) & MASK
    value ^= value >> 27
    value = (value * 0x94D049BB133111EB) & MASK
    value ^= value >> 31
    return (value >> 11) * (2.0 ** -53)


def identity(species: str, index: int) -> tuple[int, int]:
    digest = hashlib.sha256(species.encode("ascii")).digest()
    return int.from_bytes(digest[:8], "big"), index & MASK


def select_temperament(species: str, index: int, policy: dict) -> str | None:
    temperament = policy.get("temperament", {}) or {}
    allowed = temperament.get("allowed", []) or []
    weights = temperament.get("weights", {}) or {}
    if not allowed:
        return None
    total = sum(int(weights.get(value, 0)) for value in allowed)
    if total <= 0:
        return allowed[0]
    msb, lsb = identity(species, index)
    bucket = int(stable_unit(msb, lsb, 0x4F1BBCDC) * total)
    cursor = 0
    for value in TEMPERAMENT_ORDER:
        if value not in allowed:
            continue
        cursor += int(weights.get(value, 0))
        if bucket < cursor:
            return value
    return allowed[0]


def reaction(species: str, index: int, policy: dict, temperament: str | None) -> str:
    if policy.get("disposition") != "PASSIVE" or temperament is None:
        return "NONE"
    fight = float((policy.get("temperament", {}) or {})
                  .get("fight-percent", {}).get(temperament, 0.0))
    msb, lsb = identity(species, index)
    if stable_unit(msb, lsb, 0x9E3779B9) * 100.0 >= fight:
        return "FLEE"
    warning = bool((policy.get("reaction", {}) or {}).get("warning-before-fight", False))
    return "WARN" if warning else "FIGHT"


def expected_fight_percent(policy: dict) -> float:
    temperament = policy.get("temperament", {}) or {}
    weights = temperament.get("weights", {}) or {}
    fight = temperament.get("fight-percent", {}) or {}
    total = sum(float(weights.get(value, 0.0)) for value in temperament.get("allowed", []) or [])
    if total <= 0.0:
        return 0.0
    return sum(float(weights.get(value, 0.0)) * float(fight.get(value, 0.0))
               for value in temperament.get("allowed", []) or []) / total


def authored_techniques(policy: dict) -> list[str]:
    result = list(policy.get("techniques", []) or [])
    flee = (policy.get("reaction", {}) or {}).get("flee-technique")
    if flee and flee not in result:
        result.append(flee)
    for techniques in (policy.get("rank-techniques", {}) or {}).values():
        for technique in techniques or []:
            if technique not in result:
                result.append(technique)
    return result


def write_reports(output: Path, reports: dict[str, object]) -> None:
    output.mkdir(parents=True, exist_ok=True)
    for name, report in reports.items():
        (output / f"{name}.json").write_text(
            json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    config = yaml.safe_load(CONFIG.read_text(encoding="utf-8"))
    abilities = config.get("mob-abilities", {}) or {}
    species = config.get("creature-species", {}) or {}
    require(80 <= len(species) <= 128, "species matrix is implausibly incomplete or unbounded")
    require(len(species) == len(set(species)), "duplicate species row")

    dispositions = Counter()
    categories = Counter()
    temperaments = Counter()
    rewards = Counter()
    provocations = Counter()
    primitive_usage: dict[str, list[str]] = defaultdict(list)
    technique_usage: dict[str, list[str]] = defaultdict(list)
    distribution = []
    rows = []

    for ability_id, ability in abilities.items():
        kind = ability.get("kind")
        if kind == "COMPOSITE":
            actions = ability.get("actions", []) or []
            require(actions, f"composite ability has no actions: {ability_id}")
            require(len(actions) <= 8, f"unbounded action sequence: {ability_id}")
            for action in actions:
                primitive = action.get("type")
                require(primitive in {"DAMAGE", "KNOCKBACK", "DASH", "RETREAT", "GUARD"},
                        f"unused/general-purpose primitive escaped bounded vocabulary: {primitive}")
                primitive_usage[primitive].append(ability_id)
            require(len(ability.get("conditions", []) or []) <= 8,
                    f"unbounded condition list: {ability_id}")
        triggers = ability.get("triggers", ["ON_TIMER"]) or ["ON_TIMER"]
        require(set(triggers) <= {"ON_TIMER", "ON_COMBAT_ENTER", "ON_PROVOKED", "ON_DAMAGED"},
                f"unsupported trigger escaped current scope: {ability_id}")
        dangerous = kind in {"LUNGE", "GROUND_SLAM", "PROJECTILE_BURST", "SUMMON",
                             "CLEAVE", "POISON_CLOUD", "DELAYED_RUNE"} or any(
            action.get("type") in {"DAMAGE", "DASH"} for action in ability.get("actions", []) or [])
        require(not dangerous or int(ability.get("telegraph-ticks", 0)) >= 10,
                f"dangerous technique lacks telegraph: {ability_id}")

    for entity_type, policy in sorted(species.items()):
        disposition = policy.get("disposition", "NON_COMBAT")
        category = policy.get("category", "CIVILIAN")
        reward = policy.get("reward-profile", "VANILLA_ONLY")
        provocation = policy.get("provocation", "NONE")
        dispositions[disposition] += 1
        categories[category] += 1
        rewards[reward] += 1
        provocations[provocation] += 1
        allowed = (policy.get("temperament", {}) or {}).get("allowed", []) or []
        temperaments.update(allowed)
        techniques = authored_techniques(policy)
        for technique in techniques:
            require(technique in abilities, f"unknown species technique: {entity_type}/{technique}")
            technique_usage[technique].append(entity_type)

        if disposition == "NON_COMBAT":
            require(not policy.get("level-enabled", False) and not policy.get("rank-enabled", False),
                    f"civilian received combat progression: {entity_type}")
            require(not techniques and reward == "VANILLA_ONLY" and provocation == "NONE",
                    f"civilian received technique/aggression/reward: {entity_type}")
        else:
            require(policy.get("level-enabled") is True and policy.get("rank-enabled") is True,
                    f"combat-capable species lacks canonical level/rank: {entity_type}")
        if disposition == "PASSIVE":
            require(reward == "VANILLA_ONLY", f"passive wildlife became a canonical gear faucet: {entity_type}")
        if disposition == "PASSIVE":
            require(provocation == "DIRECT_PLAYER", f"passive aggression is not provocation-only: {entity_type}")
            require(allowed, f"passive species lacks stable temperament: {entity_type}")
            counts = Counter(reaction(entity_type, index, policy,
                                      select_temperament(entity_type, index, policy))
                             for index in range(4096))
            measured = 100.0 * (counts["FIGHT"] + counts["WARN"]) / 4096.0
            expected = expected_fight_percent(policy)
            require(abs(measured - expected) <= 4.0,
                    f"deterministic reaction distribution drifted: {entity_type}")
            distribution.append({
                "entity_type": entity_type,
                "sample_size": 4096,
                "expected_fight_percent": round(expected, 3),
                "measured_fight_or_warn_percent": round(measured, 3),
                "outcomes": dict(sorted(counts.items())),
            })

        social = policy.get("social", {}) or {}
        relation = social.get("relation", "NONE")
        assistants = int(social.get("maximum-assistants", 0))
        candidates = int(social.get("maximum-candidates", 0))
        radius = float(social.get("radius", 0.0))
        require(0 <= assistants <= 6 and 0 <= candidates <= 32 and 0.0 <= radius <= 16.0,
                f"social policy exceeds runtime bounds: {entity_type}")
        require(relation != "NONE" or assistants == 0,
                f"disabled social policy admits assistants: {entity_type}")
        rows.append({
            "entity_type": entity_type,
            "category": category,
            "disposition": disposition,
            "level_enabled": bool(policy.get("level-enabled", False)),
            "rank_enabled": bool(policy.get("rank-enabled", False)),
            "temperaments": allowed,
            "provocation": provocation,
            "techniques": techniques,
            "social_relation": relation,
            "maximum_assistants": assistants,
            "maximum_candidates": candidates,
            "reward_profile": reward,
            "baby_policy": policy.get("baby-policy", "IDENTITY_ONLY"),
            "tame_policy": policy.get("tame-policy", "NOT_TAMEABLE"),
        })

    representatives = {"COW", "RABBIT", "GOAT", "BEE", "WOLF", "ZOMBIE", "SKELETON"}
    hostile_controls = {"ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "WITCH", "ENDERMAN"}
    require(representatives <= species.keys(), "representative vertical slice is incomplete")
    require(hostile_controls <= species.keys(), "hostile/neutral compatibility controls are incomplete")
    require(species["COW"]["disposition"] == "PASSIVE"
            and species["COW"]["reward-profile"] == "VANILLA_ONLY"
            and (species["COW"].get("social", {}) or {}).get("maximum-assistants") == 2,
            "Cow vertical slice lost passive/reward/social bounds")
    require(species["RABBIT"]["disposition"] == "PASSIVE"
            and expected_fight_percent(species["RABBIT"]) == 0.0,
            "Rabbit is not deterministic flee-first wildlife")
    require(species["WOLF"].get("tame-policy") == "OWNER_SAFE"
            and species["WOLF"].get("social", {}).get("relation") == "VANILLA",
            "Wolf tame/pack identity is not preserved")
    require(species["BEE"].get("social", {}).get("relation") == "VANILLA",
            "Bee vanilla swarm authority is not preserved")

    profile_source = PROFILE_SOURCE.read_text(encoding="utf-8")
    runtime_source = RUNTIME_SOURCE.read_text(encoding="utf-8")
    scaling_source = SCALING_SOURCE.read_text(encoding="utf-8")
    require("PersistentDataType" in profile_source and "PROFILE_VERSION_KEY" in profile_source
            and "TEMPERAMENT_KEY" in profile_source and "REACTION_KEY" in profile_source,
            "stable profile identity is not PDC-persisted")
    require("runtime.enterCombat" in profile_source and "runtime.trigger" in profile_source,
            "passive provocation bypasses common technique runtime")
    require("maximumCandidates" in profile_source and "maximumAssistants" in profile_source,
            "large-farm social work is not bounded")
    require("getScheduler().run" in profile_source and "Bukkit.getScheduler" not in profile_source,
            "profile/social mutation is not entity-owner scheduled")
    require("castEpoch" in runtime_source and "disengage" in runtime_source,
            "stale cast/disengage lifecycle proof missing")
    require("creatureSpecies.profile" in scaling_source,
            "wildlife level/rank bypasses canonical MobScalingManager")

    summary = {
        "schema_version": 1,
        "species_count": len(species),
        "ability_count": len(abilities),
        "composable_ability_count": sum(1 for value in abilities.values()
                                        if value.get("kind") == "COMPOSITE"),
        "disposition_counts": dict(sorted(dispositions.items())),
        "category_counts": dict(sorted(categories.items())),
        "temperament_usage": dict(sorted(temperaments.items())),
        "provocation_counts": dict(sorted(provocations.items())),
        "reward_profile_counts": dict(sorted(rewards.items())),
        "primitive_count": len(primitive_usage),
        "human_gate": "HUMAN_GAMEPLAY_STAGING_REQUIRED",
    }
    reports = {
        "unified-creature-summary": summary,
        "species-matrix": {"schema_version": 1, "rows": rows},
        "ability-primitive-coverage": {
            "schema_version": 1,
            "primitive_usage": {key: sorted(set(value)) for key, value in sorted(primitive_usage.items())},
            "technique_species_usage": {key: sorted(set(value)) for key, value in sorted(technique_usage.items())},
        },
        "provocation-distribution": {"schema_version": 1, "species": distribution},
        "social-reward-persistence": {
            "schema_version": 1,
            "social_limits": {"radius": 16.0, "maximum_candidates": 32, "maximum_assistants": 6,
                              "recursive_propagation": False},
            "reward_profiles": dict(sorted(rewards.items())),
            "persisted_fields": ["level", "rank", "profile_version", "spawn_source",
                                 "disposition", "temperament", "reaction", "reward_profile"],
            "spawn_sources_reward_blocked": ["SPAWNER", "SPAWNER_EGG", "BREEDING", "COMMAND", "CUSTOM"],
            "tame_owner_safe_species": sorted(row["entity_type"] for row in rows
                                               if row["tame_policy"] == "OWNER_SAFE"),
            "baby_default": "IDENTITY_ONLY_NO_AUTHORED_COMBAT",
        },
    }
    if args.output:
        write_reports(args.output, reports)
    print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
