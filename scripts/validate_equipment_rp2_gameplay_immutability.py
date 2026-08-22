#!/usr/bin/env python3
"""Prove RP2-C changes only generated presentation fields in the canonical armor catalog."""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
CATALOG = "src/main/resources/config/equipment-catalog-expansion.yml"
PRESENTATION_KEYS = {"item-model", "equipment-asset"}


def stripped(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: stripped(child) for key, child in value.items() if key not in PRESENTATION_KEYS}
    if isinstance(value, list):
        return [stripped(child) for child in value]
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--output")
    args = parser.parse_args()
    baseline_text = subprocess.check_output(
        ["git", "show", f"{args.baseline}:{CATALOG}"], cwd=ROOT, text=True)
    current_text = (ROOT / CATALOG).read_text(encoding="utf-8")
    baseline = yaml.safe_load(baseline_text)
    current = yaml.safe_load(current_text)
    errors: list[str] = []
    if stripped(baseline) != stripped(current):
        errors.append("non-presentation equipment catalog content changed")
    before_templates = baseline.get("item-templates", {})
    after_templates = current.get("item-templates", {})
    if set(before_templates) != set(after_templates):
        errors.append("ItemTemplate ID set changed")
    presentation_changes = 0
    for template_id in sorted(set(before_templates) & set(after_templates)):
        before = {key: before_templates[template_id].get(key) for key in PRESENTATION_KEYS}
        after = {key: after_templates[template_id].get(key) for key in PRESENTATION_KEYS}
        if before != after:
            presentation_changes += 1
    result = {
        "baseline": args.baseline,
        "catalog": CATALOG,
        "template_ids_before": len(before_templates),
        "template_ids_after": len(after_templates),
        "templates_with_presentation_change": presentation_changes,
        "allowed_fields": sorted(PRESENTATION_KEYS),
        "gameplay_semantic_change": bool(errors),
        "errors": errors,
        "status": "PASS" if not errors else "FAIL",
    }
    encoded = json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    print(encoded, end="")
    if args.output:
        target = ROOT / args.output
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(encoded, encoding="utf-8")
    if errors:
        raise SystemExit("; ".join(errors))


if __name__ == "__main__":
    main()
