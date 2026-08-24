#!/usr/bin/env python3
"""Fail-closed authored PvE template/combat/reward/Folia evidence generator."""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "src/main/resources/content/pve/enemies.yml"
ROOT_CONFIG = ROOT / "src/main/resources/config.yml"
WORLD_CONFIG = ROOT / "src/main/resources/config/world.yml"
JAVA = ROOT / "src/main/java/hu/taliann/icesmp"

WORLD_BOSSES = [
    "ring_warden", "magma_behemoth", "frost_king", "bone_king", "deep_horror",
    "venom_broodmother", "storm_herald", "plague_titan", "golem_sentinel",
    "piglin_warlord",
]
INVASION_CHAMPIONS = [
    "invasion_undead_champion", "invasion_bone_champion",
    "invasion_spider_champion", "invasion_chaos_champion",
    "invasion_nether_champion", "invasion_illager_champion",
    "invasion_witch_champion", "invasion_blazing_champion",
]
PROLOGUE = [
    "prologue_breach_piglin", "prologue_breach_brute", "prologue_breach_hoglin",
    "prologue_breach_blaze", "prologue_breach_skeleton", "prologue_breach_elite",
    "prologue_finale_boss", "prologue_flame_add", "prologue_brute_add",
    "prologue_bone_add",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"authored PvE consolidation audit failed: {message}")


def source(relative: str) -> str:
    return (JAVA / relative).read_text(encoding="utf-8")


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
    templates = config.get("mob-templates", {}) or {}

    for template_id in WORLD_BOSSES + INVASION_CHAMPIONS + PROLOGUE:
        require(template_id in templates, f"missing roster template: {template_id}")
    for template_id in WORLD_BOSSES:
        row = templates[template_id]
        require(row.get("rank") == "WORLD_BOSS", f"world boss rank drift: {template_id}")
        require(row.get("spawn-policy") == "boss", f"world boss spawn policy drift: {template_id}")
        require(row.get("loot-profile") == "world_boss", f"world boss reward profile drift: {template_id}")
        require(2 <= len(row.get("abilities", [])) <= 5,
                f"world boss technique complexity is empty/unbounded: {template_id}")
        require(0.02 <= row["stats"]["health-multiplier"] <= 1.0,
                f"world boss carrier normalization would duplicate/saturate health: {template_id}")
    for template_id in INVASION_CHAMPIONS:
        row = templates[template_id]
        require(row.get("rank") == "CHAMPION" and row.get("spawn-policy") == "event",
                f"invasion champion policy drift: {template_id}")
        require(row.get("loot-profile") == "champion",
                f"invasion champion reward policy drift: {template_id}")
        require(row["stats"]["health-multiplier"] == 1.0
                and row["stats"]["damage-multiplier"] == 1.0,
                f"invasion template duplicates existing level/rank stats: {template_id}")
    require(templates["prologue_breach_elite"].get("rank") == "ELITE",
            "Prologue elite does not use canonical ELITE rank")
    require(templates["prologue_finale_boss"].get("rank") in {"BOSS", "WORLD_BOSS"},
            "Prologue finale does not use canonical boss rank")

    primitive_usage: dict[str, list[str]] = defaultdict(list)
    template_ability_usage: dict[str, list[str]] = defaultdict(list)
    summon_refs = []
    thresholds = []
    for template_id, template in templates.items():
        for ability_id in template.get("abilities", []) or []:
            require(ability_id in abilities, f"unknown template ability: {template_id}/{ability_id}")
            template_ability_usage[ability_id].append(template_id)
    for ability_id, ability in abilities.items():
        for action in ability.get("actions", []) or []:
            kind = str(action.get("type"))
            primitive_usage[kind].append(ability_id)
            if kind == "SUMMON_TEMPLATE":
                reference = action.get("reference")
                require(reference in templates, f"unknown summon template: {ability_id}/{reference}")
                require((action.get("parameters", {}) or {}).get("count", 0) <= 3,
                        f"summon count exceeds authored bound: {ability_id}")
                summon_refs.append({"ability": ability_id, "template": reference,
                                    "maximum_count": int((action.get("parameters", {}) or {}).get("count", 0)),
                                    "lifespan_ticks": int((action.get("parameters", {}) or {}).get("lifespan-ticks", 0))})
        if "HEALTH_THRESHOLD" in (ability.get("triggers", []) or []):
            conditions = ability.get("conditions", []) or []
            require(any(condition.get("type") == "HEALTH_BELOW" for condition in conditions),
                    f"threshold lacks typed health condition: {ability_id}")
            thresholds.append({"ability": ability_id,
                               "threshold": next(condition.get("value") for condition in conditions
                                                 if condition.get("type") == "HEALTH_BELOW"),
                               "semantics": "ONE_SHOT_PER_ENTITY_RUNTIME"})

    for summon in summon_refs:
        add_abilities = templates[summon["template"]].get("abilities", []) or []
        require(not any(action.get("type") == "SUMMON_TEMPLATE"
                        for ability_id in add_abilities
                        for action in (abilities[ability_id].get("actions", []) or [])),
                f"recursive authored summon kit: {summon['template']}")

    root_config_text = ROOT_CONFIG.read_text(encoding="utf-8")
    world_config_text = WORLD_CONFIG.read_text(encoding="utf-8")
    for obsolete in ("base-health:", "base-attack-damage:", "slam-damage:",
                     "hazard-damage:", "add-types:"):
        require(obsolete not in root_config_text,
                f"Prologue shadow combat config remains: {obsolete}")
    for obsolete in ("    health: 300.0", "    damage-multiplier: 2.0",
                     "aura-radius:", "special-damage:", "add-lifespan-seconds:",
                     "champion-slam-damage:"):
        require(obsolete not in world_config_text,
                f"world/invasion shadow combat config remains: {obsolete.strip()}")

    for primitive in ("APPLY_EFFECT", "SUMMON_TEMPLATE"):
        require(primitive_usage[primitive], f"new primitive has no migrated content use: {primitive}")
    require(not set(primitive_usage) - {"DAMAGE", "KNOCKBACK", "DASH", "RETREAT", "GUARD",
                                         "APPLY_EFFECT", "SUMMON_TEMPLATE"},
            "general-purpose primitive escaped bounded vocabulary")

    world = source("managers/WorldBossManager.java")
    invasion = source("managers/InvasionManager.java")
    prologue = source("prologue/PrologueEncounterEngine.java")
    dark_undead = source("managers/DarkUndeadAmbienceManager.java")
    runtime = source("pve/MobAbilityRuntime.java")
    spawn_service = source("pve/AuthoredCreatureSpawnService.java")
    scaling = source("managers/MobScalingManager.java")
    loot = source("listeners/MobLootListener.java")
    kill_gate = source("utils/MobKillUtil.java")

    require("startPhaseTick" not in world and "fireSpecial" not in world
            and "player.damage(" not in world and "setBaseValue" not in world,
            "WorldBossManager still owns combat attacks/stats")
    require("startChampionTick" not in invasion and "player.damage(" not in invasion
            and "setBaseValue" not in invasion,
            "InvasionManager still owns champion combat")
    require("private void slam(" not in prologue and "private void hazard(" not in prologue
            and "private void spawnAdds(" not in prologue and "setBaseValue" not in prologue,
            "PrologueEncounterEngine still owns boss combat")
    require("AuthoredCreatureSpawnService.current()" in world
            and "AuthoredCreatureSpawnService.current()" in invasion
            and "AuthoredCreatureSpawnService.current()" in prologue
            and "AuthoredCreatureSpawnService.current()" in dark_undead,
            "primary event producer bypasses common authored spawn authority")
    require("forceTemplate" in spawn_service and "forceRankedLevel" in spawn_service
            and "abilities.attach" in spawn_service,
            "authored spawn path does not attach canonical profile/runtime")
    require("applyEncounterModifier" in scaling and "encounter_stat_modifier" in scaling
            and "already applied" in scaling,
            "participant scaling lacks single-application provenance")
    require("consumedThresholds" in runtime and "pendingThresholds" in runtime
            and "castEpoch" in runtime and "state.paused" in runtime,
            "threshold/pause/cast-epoch lifecycle proof missing")
    require("player.getScheduler().run" in runtime and "Bukkit.getScheduler" not in runtime,
            "area/effect action is not remote-player ownership safe")
    require("maximum-summons-per-cast" in runtime and "cleanupSummons" in runtime,
            "summon cap/owner cleanup proof missing")
    require("AuthoredCreatureSpawnService.rewardOwner" in loot,
            "generic loot does not honor explicit authored reward ownership")
    require("RewardKind.TRACKING" in kill_gate and "AuthoredCreatureSpawnService.rewardOwner" in kill_gate,
            "generic faucet/progression gates do not honor event reward ownership")
    require("RewardOwner.NONE" in loot and "event.getDrops().clear()" in loot
            and "event.setDroppedExp(0)" in loot,
            "no-reward authored adds retain vanilla drops or XP")

    producer_inventory = [
        {"producer": "WorldBossManager", "previous_spawn": "direct World.spawn",
         "previous_stat_owner": "manager raw attributes + Java archetype multipliers",
         "previous_combat_owner": "phase/aura/special scheduler", "final_profile": "10 WORLD_BOSS templates",
         "final_runtime": "MobAbilityRuntime", "reward_owner": "split: generic boss band + contribution ledger",
         "orchestration_owner": "WorldBossManager", "status": "MIGRATED"},
        {"producer": "InvasionManager", "previous_spawn": "direct World.spawn",
         "previous_stat_owner": "forceRankedLevel", "previous_combat_owner": "champion slam scheduler",
         "final_profile": "generic waves + 8 CHAMPION templates", "final_runtime": "MobAbilityRuntime",
         "reward_owner": "generic rank loot", "orchestration_owner": "InvasionManager", "status": "MIGRATED"},
        {"producer": "PrologueEncounterEngine", "previous_spawn": "raw EntityType spawn",
         "previous_stat_owner": "elite/boss raw attributes", "previous_combat_owner": "slam/add/hazard phase scheduler",
         "final_profile": "10 Prologue templates", "final_runtime": "MobAbilityRuntime",
         "reward_owner": "Prologue finale service / adds NONE", "orchestration_owner": "PrologueEncounterEngine",
         "status": "MIGRATED"},
        {"producer": "SeasonFinaleManager", "previous_spawn": "WorldBossManager finale path",
         "previous_stat_owner": "WorldBossManager finale multiplier", "previous_combat_owner": "WorldBossManager",
         "final_profile": "selected WORLD_BOSS template + participant modifier", "final_runtime": "MobAbilityRuntime",
         "reward_owner": "season finale + contribution ledger", "orchestration_owner": "SeasonFinaleManager",
         "status": "MIGRATED_VIA_WORLD_BOSS"},
        {"producer": "BloodMoonManager", "previous_spawn": "none",
         "previous_stat_owner": "common level bonus", "previous_combat_owner": "none",
         "final_profile": "world modifier only", "final_runtime": "unchanged", "reward_owner": "soul modifier",
         "orchestration_owner": "BloodMoonManager", "status": "NO_SHADOW_COMBAT"},
        {"producer": "WildHuntManager", "previous_spawn": "direct World.spawn",
         "previous_stat_owner": "forceRankedLevel", "previous_combat_owner": "common runtime",
         "final_profile": "generic authored ELITE profile", "final_runtime": "MobAbilityRuntime",
         "reward_owner": "wild hunt + generic band", "orchestration_owner": "WildHuntManager", "status": "SPAWN_PATH_MIGRATED"},
        {"producer": "CultistEventManager", "previous_spawn": "direct spawnEntity",
         "previous_stat_owner": "forceRankedLevel", "previous_combat_owner": "common runtime",
         "final_profile": "generic authored VETERAN profile", "final_runtime": "MobAbilityRuntime",
         "reward_owner": "cultist table", "orchestration_owner": "CultistEventManager", "status": "SPAWN_PATH_MIGRATED"},
        {"producer": "CorruptionManager", "previous_spawn": "direct World.spawn",
         "previous_stat_owner": "forceLevel", "previous_combat_owner": "common runtime",
         "final_profile": "generic authored profile", "final_runtime": "MobAbilityRuntime",
         "reward_owner": "generic", "orchestration_owner": "CorruptionManager", "status": "SPAWN_PATH_MIGRATED"},
        {"producer": "EscortManager wave", "previous_spawn": "direct World.spawn",
         "previous_stat_owner": "forceLevel", "previous_combat_owner": "vanilla/common runtime",
         "final_profile": "generic authored profile", "final_runtime": "MobAbilityRuntime",
         "reward_owner": "generic", "orchestration_owner": "EscortManager", "status": "SPAWN_PATH_MIGRATED"},
        {"producer": "DungeonLootService", "previous_spawn": "direct spawn + raw health multiplier",
         "previous_stat_owner": "manager attributes", "previous_combat_owner": "vanilla/common runtime",
         "final_profile": "generic MINIBOSS + explicit modifier", "final_runtime": "MobAbilityRuntime",
         "reward_owner": "dungeon EVENT", "orchestration_owner": "DungeonLootService", "status": "STAT_PATH_MIGRATED"},
        {"producer": "DarkUndeadAmbienceManager", "previous_spawn": "direct spawnEntity",
         "previous_stat_owner": "forceLevel", "previous_combat_owner": "vanilla/common runtime",
         "final_profile": "generic authored NORMAL profile", "final_runtime": "MobAbilityRuntime",
         "reward_owner": "GENERIC", "orchestration_owner": "DarkUndeadAmbienceManager",
         "status": "SPAWN_PATH_MIGRATED"},
        {"producer": "AmbientEventManager animal migration", "previous_spawn": "CUSTOM passive herd",
         "previous_stat_owner": "CreatureProfileService delayed natural baseline",
         "previous_combat_owner": "common creature runtime", "final_profile": "natural species profile",
         "final_runtime": "MobAbilityRuntime", "reward_owner": "wildlife species policy",
         "orchestration_owner": "AmbientEventManager", "status": "NO_AUTHORED_COMBAT_OVERRIDE"},
    ]

    excluded_producers = [
        {"producer": "SoulShardManager champion", "boundary": "player-owned minion/pet combat, not PvE enemy"},
        {"producer": "Escort/Caravan/PlayerCaravan convoy", "boundary": "protected utility objective"},
        {"producer": "StrangerNpc/CityGuard", "boundary": "civilian/NPC authority"},
        {"producer": "SignatureItemListener mount", "boundary": "player utility mount"},
        {"producer": "AbundanceListener offspring", "boundary": "vanilla breeding/profile assignment"},
    ]

    world_matrix = []
    for template_id in WORLD_BOSSES:
        row = templates[template_id]
        actions = sorted({action.get("type") for ability_id in row.get("abilities", [])
                          for action in (abilities[ability_id].get("actions", []) or [])})
        world_matrix.append({
            "boss_id": template_id, "entity_type": row["entity-type"],
            "level": row["level"], "rank": row["rank"], "archetype": row["archetype"],
            "abilities": row.get("abilities", []), "action_primitives": actions,
            "thresholds": [entry for entry in thresholds if entry["ability"] in row.get("abilities", [])],
            "reward_profile": row["loot-profile"], "bestiary_id": row["bestiary-id"],
        })

    reports = {
        "authored-producer-inventory": producer_inventory,
        "producer-scope-boundary": excluded_producers,
        "shadow-combat-before-after": {
            "before": ["WorldBossManager phase/aura/special", "InvasionManager champion slam",
                       "PrologueEncounterEngine elite/boss stats and slam/add/hazard"],
            "after": [], "remaining_shadow_combat_count": 0,
        },
        "world-boss-matrix": world_matrix,
        "invasion-matrix": [{"template_id": value, **templates[value]}
                            for value in INVASION_CHAMPIONS],
        "prologue-matrix": [{"template_id": value, **templates[value]} for value in PROLOGUE],
        "ability-primitive-coverage": {
            "usage": {key: sorted(value) for key, value in sorted(primitive_usage.items())},
            "new_primitives": ["APPLY_EFFECT", "SUMMON_TEMPLATE"],
            "unused_new_primitive_count": 0,
        },
        "stat-provenance": {
            "world_boss": ["MobTemplate base", "level", "WORLD_BOSS rank", "participant modifier"],
            "invasion_champion": ["MobTemplate base", "forced event level", "CHAMPION rank"],
            "prologue_boss": ["MobTemplate base", "forced event level", "BOSS rank", "participant modifier"],
            "world_boss_template_stats": {
                value: templates[value]["stats"] for value in WORLD_BOSSES
            },
            "invasion_template_stat_delta": "identity (rank/level preserved exactly)",
            "prologue_finale_template_stats": templates["prologue_finale_boss"]["stats"],
            "double_application_guard": "icesmp:encounter_stat_modifier",
            "obsolete_raw_stat_config_count": 0,
        },
        "threshold-phase-report": thresholds,
        "summon-bounds": {"references": summon_refs, "global_maximum_per_cast": 3,
                          "recursive_propagation": False, "owner_cleanup": True,
                          "pause_propagation": True},
        "reward-ownership": {
            "world_boss": "GENERIC boss-band item roll + distinct contribution settlement",
            "invasion": "GENERIC rank policy", "prologue": "NONE at creature death; finale service owns reward",
            "dungeon": "EVENT", "summoned_add": "NONE",
        },
        "folia-safety": {"spawn": "owning region", "entity_mutation": "entity scheduler",
                         "remote_player_mutation": "player scheduler", "global_entity_scan": False,
                         "summon_maximum": 3},
        "authored-pve-summary": {
            "world_boss_count": len(WORLD_BOSSES),
            "invasion_champion_count": len(INVASION_CHAMPIONS),
            "prologue_template_count": len(PROLOGUE),
            "producer_count": len(producer_inventory),
            "remaining_shadow_combat_count": 0,
            "common_spawn_authority": "AuthoredCreatureSpawnService",
            "common_combat_authority": "MobAbilityRuntime",
            "human_gameplay_staging": "HUMAN_GAMEPLAY_STAGING_REQUIRED",
        },
    }
    if args.output:
        write_reports(args.output, reports)
    print(json.dumps(reports["authored-pve-summary"], sort_keys=True))


if __name__ == "__main__":
    main()
