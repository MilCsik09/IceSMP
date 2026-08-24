#!/usr/bin/env python3
from __future__ import annotations

import argparse
import itertools
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
REPORT = ROOT / "build/reports/long-term-equipment/material-economy.json"

FILES = [
    "content/professions/materials.yml",
    "content/professions/recipes.yml",
    "content/equipment/equipment.yml",
]
ECONOMY_EXPANSION_IDS = {
    "holdlen_fonal", "cserle", "fenyves_gyanta", "kotogyanta", "halolaj",
    "gyongyhaz_hej", "vizbor", "in_kotelez", "kitin_lemez", "konnyu_otvozet",
    "szivfa_mag", "bokic_gyongy", "melyvizi_esszencia", "szorny_szerv",
}


def load_file(name: str) -> dict[str, Any]:
    path = RESOURCES / name
    if not path.exists():
        return {}
    raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    return raw if isinstance(raw, dict) else {}


def merge_tree(target: dict[str, Any], patch: dict[str, Any]) -> None:
    for key, value in patch.items():
        if value is None:
            target.pop(str(key), None)
        elif isinstance(value, dict) and isinstance(target.get(str(key)), dict):
            merge_tree(target[str(key)], value)
        else:
            target[str(key)] = value


def effective_tree() -> dict[str, Any]:
    result: dict[str, Any] = {}
    for name in FILES:
        merge_tree(result, load_file(name))
    return result


def merge_named(root: str) -> dict[str, dict[str, Any]]:
    section = effective_tree().get(root, {})
    if not isinstance(section, dict):
        return {}
    return {str(ident): value for ident, value in section.items() if isinstance(value, dict)}


def ingredient_specs(recipe: dict[str, Any]) -> list[tuple[str, str, int]]:
    parsed: list[tuple[str, str, int]] = []
    for raw in recipe.get("ingredients", []) or []:
        text = str(raw)
        parts = text.split(":")
        if text.startswith("unique:"):
            if len(parts) != 3:
                raise AssertionError(f"malformed unique ingredient: {text}")
            parsed.append(("unique", parts[1].lower(), int(parts[2])))
        else:
            if len(parts) != 2:
                raise AssertionError(f"malformed material ingredient: {text}")
            parsed.append(("material", parts[0].upper(), int(parts[1])))
    return parsed


def unique_inputs(recipe: dict[str, Any]) -> Counter[str]:
    out: Counter[str] = Counter()
    for kind, ident, amount in ingredient_specs(recipe):
        if kind == "unique":
            out[ident] += amount
    return out


def unique_result(recipe: dict[str, Any]) -> tuple[str, int] | None:
    result = recipe.get("result") or {}
    if not isinstance(result, dict) or not result.get("unique"):
        return None
    return str(result["unique"]).lower(), int(result.get("amount", 1))


def find_cycles(graph: dict[str, set[str]]) -> list[list[str]]:
    visiting: list[str] = []
    visited: set[str] = set()
    canonical: set[tuple[str, ...]] = set()

    def normalize_cycle(nodes: list[str]) -> tuple[str, ...]:
        body = nodes[:-1]
        rotations = [tuple(body[index:] + body[:index]) for index in range(len(body))]
        chosen = min(rotations)
        return chosen + (chosen[0],)

    def visit(node: str) -> None:
        if node in visiting:
            start = visiting.index(node)
            canonical.add(normalize_cycle(visiting[start:] + [node]))
            return
        if node in visited:
            return
        visiting.append(node)
        for nxt in sorted(graph.get(node, set())):
            visit(nxt)
        visiting.pop()
        visited.add(node)

    for node in sorted(graph):
        visit(node)
    return [list(cycle) for cycle in sorted(canonical)]


def cycle_assessment(cycle: list[str], recipe_rows: dict[str, list[tuple[str, dict[str, Any]]]]) -> dict[str, Any]:
    """Classify dependency cycles without treating every bounded recipe loop as an exploit.

    A cycle is unbounded only when every conversion can be performed using solely materials already
    inside that cycle. Vanilla inputs or a managed material from outside the cycle are a real faucet
    bound: repeating the cycle consumes that external stock. For simple one-cycle-input conversions,
    the product of output/input ratios proves whether a closed loop is neutral/positive. More complex
    closed loops fail conservatively as review-required instead of being silently accepted.
    """
    nodes = set(cycle[:-1])
    edge_options: list[list[dict[str, Any]]] = []
    for output_id, dependency_id in zip(cycle[:-1], cycle[1:]):
        options: list[dict[str, Any]] = []
        for recipe_id, recipe in recipe_rows.get(output_id, []):
            if str(recipe.get("kind", "")).lower() != "processing":
                continue
            inputs = ingredient_specs(recipe)
            dependency_amount = sum(amount for kind, ident, amount in inputs
                                    if kind == "unique" and ident == dependency_id)
            if dependency_amount <= 0:
                continue
            result = unique_result(recipe)
            if result is None:
                continue
            _, output_amount = result
            external = [
                f"{kind}:{ident}:{amount}" for kind, ident, amount in inputs
                if kind != "unique" or ident not in nodes
            ]
            in_cycle_unique = [(ident, amount) for kind, ident, amount in inputs
                               if kind == "unique" and ident in nodes]
            options.append({
                "recipe": recipe_id,
                "output": output_id,
                "dependency": dependency_id,
                "output_amount": output_amount,
                "dependency_amount": dependency_amount,
                "simple_cycle_input": len(in_cycle_unique) == 1,
                "external_inputs": external,
                "gain": output_amount / dependency_amount,
            })
        if not options:
            return {"cycle": cycle, "status": "TOPOLOGY_ONLY", "reason": "no processing recipe closes one dependency edge"}
        edge_options.append(options)

    closed_combinations = []
    for combination in itertools.product(*edge_options):
        if any(option["external_inputs"] for option in combination):
            continue
        if not all(option["simple_cycle_input"] for option in combination):
            closed_combinations.append({
                "recipes": [option["recipe"] for option in combination],
                "status": "COMPLEX_CLOSED_LOOP_REVIEW",
            })
            continue
        gain = 1.0
        for option in combination:
            gain *= float(option["gain"])
        closed_combinations.append({
            "recipes": [option["recipe"] for option in combination],
            "status": "UNBOUNDED_NON_LOSS" if gain >= 1.0 - 1e-12 else "CLOSED_LOSSY",
            "cycle_gain": round(gain, 8),
        })

    exploit = [row for row in closed_combinations
               if row["status"] in {"UNBOUNDED_NON_LOSS", "COMPLEX_CLOSED_LOOP_REVIEW"}]
    bounded_examples = []
    for options in edge_options:
        bounded_examples.extend({
            "recipe": option["recipe"],
            "external_inputs": option["external_inputs"],
        } for option in options if option["external_inputs"])
    return {
        "cycle": cycle,
        "status": "EXPLOIT_OR_REVIEW_REQUIRED" if exploit
        else ("CLOSED_LOSSY" if closed_combinations else "BOUNDED_BY_EXTERNAL_INPUT"),
        "closed_combinations": closed_combinations,
        "external_input_examples": bounded_examples[:12],
    }


def processing_depth(material: str, producers: dict[str, list[dict[str, Any]]], seen: set[str] | None = None) -> int:
    seen = set() if seen is None else set(seen)
    if material in seen:
        return 99
    seen.add(material)
    depths = [0]
    for recipe in producers.get(material, []):
        if str(recipe.get("kind", "")).lower() != "processing":
            continue
        deps = unique_inputs(recipe)
        if not deps:
            depths.append(1)
        else:
            depths.append(1 + max(processing_depth(dep, producers, seen) for dep in deps))
    return min(depths) if len(depths) > 1 else 0


def ascension_consumers(config: dict[str, Any]) -> dict[str, list[str]]:
    consumers: dict[str, list[str]] = defaultdict(list)
    ascension = (((config.get("itemization") or {}).get("ascension") or {}))
    if not isinstance(ascension, dict):
        return consumers
    for template_id, stages in ascension.items():
        if not isinstance(stages, dict):
            continue
        for stage_id, stage in stages.items():
            if not isinstance(stage, dict):
                continue
            materials = stage.get("materials") or {}
            if not isinstance(materials, dict):
                continue
            for material_id, amount in materials.items():
                if int(amount) > 0:
                    consumers[str(material_id).lower()].append(f"ascension:{template_id}:{stage_id}")
    return consumers


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=str(REPORT.relative_to(ROOT)))
    args = parser.parse_args()

    config = effective_tree()
    materials = merge_named("profession-materials")
    recipes = merge_named("profession-recipes")
    managed = {mid: value for mid, value in materials.items() if bool(value.get("economy-managed", False))}
    introduced = {mid: managed[mid] for mid in ECONOMY_EXPANSION_IDS if mid in managed}
    assert set(introduced) == ECONOMY_EXPANSION_IDS, "handcrafted economy expansion ID drift"

    for mid, material in introduced.items():
        assert material.get("source-types"), f"{mid}: managed material requires a faucet/source declaration"
        assert material.get("sink-types"), f"{mid}: managed material requires a sink declaration"
        sources = {str(x).lower() for x in material.get("source-types", [])}
        assert not any(source.startswith("vendor") for source in sources), f"{mid}: high-value material cannot use vendor as baseline faucet"

    producers: dict[str, list[dict[str, Any]]] = defaultdict(list)
    recipe_rows: dict[str, list[tuple[str, dict[str, Any]]]] = defaultdict(list)
    consumers: dict[str, list[str]] = defaultdict(list)
    graph: dict[str, set[str]] = defaultdict(set)
    for rid, recipe in recipes.items():
        result = unique_result(recipe)
        inputs = unique_inputs(recipe)
        for mid in inputs:
            consumers[mid].append(f"recipe:{rid}")
        if result:
            out_id, _ = result
            producers[out_id].append(recipe)
            recipe_rows[out_id].append((rid, recipe))
            if str(recipe.get("kind", "")).lower() == "processing":
                for dep in inputs:
                    graph[out_id].add(dep)
    for material_id, rows in ascension_consumers(config).items():
        consumers[material_id].extend(rows)

    cycles = find_cycles(graph)
    cycle_assessments = [cycle_assessment(cycle, recipe_rows) for cycle in cycles]
    dangerous_cycles = [row for row in cycle_assessments if row["status"] == "EXPLOIT_OR_REVIEW_REQUIRED"]

    introduced_depth = {mid: processing_depth(mid, producers) for mid in introduced}
    too_deep = {mid: depth for mid, depth in introduced_depth.items() if depth > 2 and depth < 99}

    role_counts = Counter()
    source_professions = Counter()
    sink_coverage = Counter()
    for mid, material in managed.items():
        state = str(material.get("processing-state", "RAW")).upper()
        role_counts[state] += 1
        profession = str(material.get("primary-profession", "world") or "world").lower()
        source_professions[profession] += 1
        for sink in material.get("sink-types", []) or []:
            sink_coverage[str(sink).lower()] += 1

    unresolved = []
    reusable = []
    for mid, material in introduced.items():
        actual = sorted(set(consumers.get(mid, [])))
        declared = sorted({str(x) for x in material.get("sink-types", []) or []})
        rare = str(material.get("tier", "COMMON")).upper() in {"RARE", "BOSS"}
        minimum = 1 if rare else 2
        if len(actual) < minimum:
            unresolved.append({
                "material": mid,
                "tier": str(material.get("tier", "COMMON")).upper(),
                "actual_consumers": actual,
                "declared_sinks": declared,
                "required_actual_consumers": minimum,
            })
        if len(actual) >= 2:
            reusable.append(mid)

    report = {
        "schema": 2,
        "managed_material_count": len(managed),
        "introduced_material_count": len(introduced),
        "introduced_materials": sorted(introduced),
        "reusable_introduced_material_count": len(reusable),
        "reusable_introduced_materials": sorted(reusable),
        "processing_topology_cycle_count": len(cycles),
        "processing_topology_cycles": cycle_assessments,
        "positive_or_unbounded_processing_cycles": len(dangerous_cycles),
        "dangerous_processing_cycles": dangerous_cycles,
        "introduced_processing_depth": introduced_depth,
        "processing_depth_violations": too_deep,
        "processing_state_counts": dict(sorted(role_counts.items())),
        "primary_profession_supply_counts": dict(sorted(source_professions.items())),
        "declared_sink_coverage": dict(sorted(sink_coverage.items())),
        "consumer_contract_pending": unresolved,
        "consumer_contract_closed": not unresolved,
        "vendor_baseline_high_value_sources": [],
    }
    output = ROOT / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    assert not dangerous_cycles, f"positive/unbounded or complex closed processing cycle(s): {dangerous_cycles}"
    assert not too_deep, f"anti-fun introduced processing depth exceeds 2 stages: {too_deep}"
    assert not unresolved, f"managed material consumer contract pending: {unresolved}"
    print(
        "Long-term material economy audit: "
        f"{len(managed)} managed, {len(introduced)} introduced, "
        f"topology-cycles={len(cycles)}, dangerous-cycles={len(dangerous_cycles)}, "
        f"pending-consumer-contracts={len(unresolved)}"
    )


if __name__ == "__main__":
    main()
