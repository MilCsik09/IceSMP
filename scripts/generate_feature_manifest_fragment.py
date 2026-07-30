#!/usr/bin/env python3
"""Generate feature/component manifest entries and their documentation markers.

This tooling only writes the release-documentation build fragment and marker
registries.  It never edits ``docs/documentation-manifest.yml``.
"""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path

from repository_inventory.models import AUDIENCES


FEATURE_BEGIN = "<!-- BEGIN GENERATED FEATURE MANIFEST MARKERS -->"
FEATURE_END = "<!-- END GENERATED FEATURE MANIFEST MARKERS -->"
COMPONENT_BEGIN = "<!-- BEGIN GENERATED COMPONENT MANIFEST MARKERS -->"
COMPONENT_END = "<!-- END GENERATED COMPONENT MANIFEST MARKERS -->"
MARKER = re.compile(r"<!--\s*icesmp-doc-id:\s*([^\s>]+)\s*-->")

AUDIENCE_MAP = {
    "Játékos": "PLAYER",
    "Moderátor": "MODERATOR",
    "Admin": "ADMIN",
    "Builder": "INTERNAL",
    "Eventes": "INTERNAL",
    "Tesztelő": "TESTER",
    "Fejlesztő/üzemeltető": "DEVELOPER",
    "Konzol/integráció": "CONSOLE",
}
AUDIENCE_ORDER = {value: index for index, value in enumerate(AUDIENCES)}


def load_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def catalogue_descriptions(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for section in re.split(r"(?m)^### ", text)[1:]:
        feature_match = re.search(r"(?m)^\| Funkcióazonosító \| `([^`]+)` \|$", section)
        description_match = re.search(r"(?m)^\| Közérthető leírás \| (.+) \|$", section)
        if not feature_match or not description_match:
            continue
        feature_id = feature_match.group(1)
        if feature_id in result:
            raise ValueError(f"Duplicate catalogue feature section: {feature_id}")
        result[feature_id] = description_match.group(1).replace("\\|", "|").strip()
    return result


def replace_generated_block(path: Path, begin: str, end: str, content: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(begin) != text.count(end):
        raise ValueError(f"Unbalanced generated marker block in {path}")
    block = f"{begin}\n{content.rstrip()}\n{end}"
    if begin in text:
        pattern = re.compile(re.escape(begin) + r".*?" + re.escape(end), re.DOTALL)
        text, count = pattern.subn(block, text)
        if count != 1:
            raise ValueError(f"Expected one generated marker block in {path}, found {count}")
    else:
        text = text.rstrip() + "\n\n" + block + "\n"
    path.write_text(text, encoding="utf-8")


def marker_counts(docs_root: Path) -> Counter[str]:
    counts: Counter[str] = Counter()
    for path in sorted(docs_root.rglob("*.md")):
        counts.update(MARKER.findall(path.read_text(encoding="utf-8")))
    return counts


def render(root: Path, map_path: Path, source_components_path: Path) -> dict:
    feature_doc = root / "docs/reference/FEATURE_CATALOGUE.md"
    evidence_doc = root / "docs/releases/RELEASE_EVIDENCE_MATRIX.md"
    output = root / "build/release-docs/feature_manifest_fragment.json"

    data = load_json(map_path)
    source_components = load_json(source_components_path)
    descriptions = catalogue_descriptions(feature_doc.read_text(encoding="utf-8"))

    taxonomy = data["feature_taxonomy"]
    components = data["components"]
    if len(taxonomy) != 49 or len(components) != 545:
        raise ValueError(f"Expected 49/545 feature-component entries, got {len(taxonomy)}/{len(components)}")

    feature_ids = [f"feature.{item['feature_id']}" for item in taxonomy]
    component_ids = [item["component_id"] for item in components]
    source_component_ids = [item["id"] for item in source_components]
    if len(set(feature_ids)) != 49:
        raise ValueError("Duplicate stable feature ID")
    if len(set(component_ids)) != 545:
        raise ValueError("Duplicate component mapping ID")
    if set(component_ids) != set(source_component_ids):
        missing = sorted(set(source_component_ids) - set(component_ids))
        stale = sorted(set(component_ids) - set(source_component_ids))
        raise ValueError(f"Component inventory drift: missing={missing}, stale={stale}")

    features: dict[str, dict] = {}
    for item, stable_id in zip(taxonomy, feature_ids, strict=True):
        feature_id = item["feature_id"]
        if feature_id not in descriptions:
            raise ValueError(f"No catalogue description for {feature_id}")
        mapped_audiences = {
            AUDIENCE_MAP[audience]
            for audience in next(
                (
                    component["audience"]
                    for component in components
                    if component["feature_id"] == feature_id
                ),
                ["Fejlesztő/üzemeltető"],
            )
        }
        if feature_id == "planning.lore_only":
            mapped_audiences.update({"DEVELOPER", "INTERNAL"})
        audiences = sorted(mapped_audiences, key=AUDIENCE_ORDER.__getitem__)
        if not audiences or not set(audiences).issubset(AUDIENCES):
            raise ValueError(f"Unsupported audience for {stable_id}: {audiences}")
        entry = {
            "audience": audiences,
            "description": descriptions[feature_id],
            "docs": ["docs/reference/FEATURE_CATALOGUE.md"],
        }
        if item["component_count"] == 0:
            entry["componentless"] = True
        features[stable_id] = entry

    component_entries = {
        item["component_id"]: {
            "feature": f"feature.{item['feature_id']}",
            "docs": ["docs/releases/RELEASE_EVIDENCE_MATRIX.md"],
        }
        for item in sorted(components, key=lambda value: value["component_id"])
    }
    unknown_features = sorted(
        {
            entry["feature"]
            for entry in component_entries.values()
            if entry["feature"] not in features
        }
    )
    if unknown_features:
        raise ValueError(f"Components refer to unknown features: {unknown_features}")

    fragment = {"features": features, "components": component_entries}
    write_json(output, fragment)

    feature_rows = [
        "## Dokumentációs markerregiszter — funkciók",
        "",
        "Az alábbi stabil azonosítók a dokumentációs coverage tooling számára készültek. "
        "A részletes tartalom az azonos nevű katalógustételben olvasható.",
        "",
        "| Stabil feature ID | Funkció | Release-státusz |",
        "|---|---|---|",
    ]
    for item, stable_id in zip(taxonomy, feature_ids, strict=True):
        feature_rows.append(
            f"| <!-- icesmp-doc-id: {stable_id} --> `{stable_id}` "
            f"| {item['name']} | {item['release_status']} |"
        )
    replace_generated_block(feature_doc, FEATURE_BEGIN, FEATURE_END, "\n".join(feature_rows))

    component_rows = [
        "## Dokumentációs markerregiszter — production komponensek",
        "",
        "Ez a rövid technikai tábla az aktuális production source inventory minden "
        "komponensét pontosan egy dokumentált funkcióhoz rendeli. A sorok coverage-"
        "markerek; a kategória, forrásút és deployed-class bizonyíték a buildben "
        "generált release inventoryban marad.",
        "",
        "| Stabil component ID | Dokumentált feature ID |",
        "|---|---|",
    ]
    for item in sorted(components, key=lambda value: value["component_id"]):
        stable_id = item["component_id"]
        feature_id = f"feature.{item['feature_id']}"
        component_rows.append(
            f"| <!-- icesmp-doc-id: {stable_id} --> `{stable_id}` | `{feature_id}` |"
        )
    replace_generated_block(evidence_doc, COMPONENT_BEGIN, COMPONENT_END, "\n".join(component_rows))

    counts = marker_counts(root / "docs")
    duplicate_targets = sorted(
        stable_id for stable_id in [*feature_ids, *component_ids] if counts[stable_id] != 1
    )
    if duplicate_targets:
        raise ValueError(
            "Missing/duplicate target marker counts: "
            + ", ".join(f"{stable_id}={counts[stable_id]}" for stable_id in duplicate_targets)
        )
    if any(stable_id not in features for stable_id in feature_ids):
        raise ValueError("Missing feature fragment entry")
    if any(stable_id not in component_entries for stable_id in source_component_ids):
        raise ValueError("Missing component fragment entry")

    return {
        "fragment": str(output.relative_to(root)),
        "features": len(features),
        "components": len(component_entries),
        "source_components": len(source_component_ids),
        "feature_markers_exactly_once": sum(counts[stable_id] == 1 for stable_id in feature_ids),
        "component_markers_exactly_once": sum(counts[stable_id] == 1 for stable_id in component_ids),
        "stale_components": 0,
        "missing_components": 0,
        "duplicate_components": 0,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--map", default="build/release-docs/feature_component_map.json")
    parser.add_argument(
        "--source-components",
        default="../repository-inventory/components.json",
    )
    args = parser.parse_args()
    root = Path(args.root).resolve()
    map_path = Path(args.map)
    if not map_path.is_absolute():
        map_path = root / map_path
    source_components_path = Path(args.source_components)
    if not source_components_path.is_absolute():
        source_components_path = root / source_components_path
    print(
        json.dumps(
            render(root, map_path, source_components_path),
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
