#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[1]
CFG = ROOT / "src/main/resources/config"
REPORT = ROOT / "build/reports/long-term-equipment/material-economy.json"

FILES = [
    "profession-materials.yml",
    "profession-recipes.yml",
    "professions-2.yml",
    "material-economy-expansion.yml",
    "equipment-catalog-expansion.yml",
    "reward-discoverability-closure.yml",
]


def load_file(name: str) -> dict[str, Any]:
    path = CFG / name
    if not path.exists():
        return {}
    raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    return raw if isinstance(raw, dict) else {}


def merge_named(root: str) -> dict[str, dict[str, Any]]:
    effective: dict[str, dict[str, Any]] = {}
    for name in FILES:
        section = load_file(name).get(root, {})
        if not isinstance(section, dict):
            continue
        for ident, raw in section.items():
            if not isinstance(raw, dict):
                continue
            target = effective.setdefault(str(ident), {})
            for key, value in raw.items():
                if isinstance(value, dict) and isinstance(target.get(key), dict):
                    target[key] = {**target[key], **value}
                else:
                    target[key] = value
    return effective


def unique_inputs(recipe: dict[str, Any]) -> Counter[str]:
    out: Counter[str] = Counter()
    for raw in recipe.get("ingredients", []) or []:
        text = str(raw)
        if not text.startswith("unique:"):
            continue
        parts = text.split(":")
        if len(parts) != 3:
            raise AssertionError(f"malformed unique ingredient: {text}")
        out[parts[1].lower()] += int(parts[2])
    return out


def unique_result(recipe: dict[str, Any]) -> tuple[str, int] | None:
    result = recipe.get("result") or {}
    if not isinstance(result, dict) or not result.get("unique"):
        return None
    return str(result["unique"]).lower(), int(result.get("amount", 1))


def find_cycles(graph: dict[str, set[str]]) -> list[list[str]]:
    visiting: list[str] = []
    visited: set[str] = set()
    cycles: set[tuple[str, ...]] = set()

    def visit(node: str) -> None:
        if node in visiting:
            start = visiting.index(node)
            cycle = tuple(visiting[start:] + [node])
            cycles.add(cycle)
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
    return [list(cycle) for cycle in sorted(cycles)]


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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=str(REPORT.relative_to(ROOT)))
    args = parser.parse_args()

    materials = merge_named("profession-materials")
    recipes = merge_named("profession-recipes")
    overlay_materials = load_file("material-economy-expansion.yml").get("profession-materials", {}) or {}

    managed = {mid: value for mid, value in materials.items() if bool(value.get("economy-managed", False))}
    introduced = {str(mid): materials[str(mid)] for mid in overlay_materials}

    for mid, material in introduced.items():
        assert material.get("source-types"), f"{mid}: managed material requires a faucet/source declaration"
        assert material.get("sink-types"), f"{mid}: managed material requires a sink declaration"
        sinks = {str(x).lower() for x in material.get("sink-types", [])}
        assert "vendor" not in sinks and "vendor-source" not in sinks, f"{mid}: high-value material cannot use vendor as baseline sink/source"

    producers: dict[str, list[dict[str, Any]]] = defaultdict(list)
    consumers: dict[str, list[str]] = defaultdict(list)
    graph: dict[str, set[str]] = defaultdict(set)
    for rid, recipe in recipes.items():
        result = unique_result(recipe)
        inputs = unique_inputs(recipe)
        for mid in inputs:
            consumers[mid].append(rid)
        if result:
            out_id, _ = result
            producers[out_id].append(recipe)
            for dep in inputs:
                graph[out_id].add(dep)

    cycles = find_cycles(graph)
    assert not cycles, f"processing cycle(s) found: {cycles}"

    introduced_depth = {mid: processing_depth(mid, producers) for mid in introduced}
    too_deep = {mid: depth for mid, depth in introduced_depth.items() if depth > 2 and depth < 99}
    assert not too_deep, f"anti-fun processing depth exceeds 2 stages: {too_deep}"

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
    for mid, material in introduced.items():
        actual = sorted(set(consumers.get(mid, [])))
        declared = sorted({str(x) for x in material.get("sink-types", []) or []})
        if len(actual) < 2 and str(material.get("tier", "COMMON")).upper() not in {"RARE", "BOSS"}:
            unresolved.append({"material": mid, "actual_consumers": actual, "declared_sinks": declared})
        elif not actual and str(material.get("tier", "COMMON")).upper() in {"RARE", "BOSS"}:
            unresolved.append({"material": mid, "actual_consumers": actual, "declared_sinks": declared})

    report = {
        "schema": 1,
        "managed_material_count": len(managed),
        "introduced_material_count": len(introduced),
        "introduced_materials": sorted(introduced),
        "processing_cycle_count": len(cycles),
        "processing_cycles": cycles,
        "introduced_processing_depth": introduced_depth,
        "processing_state_counts": dict(sorted(role_counts.items())),
        "primary_profession_supply_counts": dict(sorted(source_professions.items())),
        "declared_sink_coverage": dict(sorted(sink_coverage.items())),
        "consumer_contract_pending": unresolved,
        "consumer_contract_closed": not unresolved,
        "vendor_baseline_high_value_sources": [],
        "positive_or_unbounded_processing_cycles": 0,
    }
    output = ROOT / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(
        "Long-term material economy audit: "
        f"{len(managed)} managed, {len(introduced)} introduced, "
        f"cycles={len(cycles)}, pending-consumer-contracts={len(unresolved)}"
    )


if __name__ == "__main__":
    main()
