#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from repository_inventory.documentation_scanner import check_coverage
from repository_inventory.inventory import generate_inventory
from repository_inventory.report import write_repository_reports
from repository_inventory.util import dump_json, load_json, load_manifest, write_markdown_table

INTEGRITY_CODES = {
    "MANIFEST_SYNTAX_ERROR", "DUPLICATE_STABLE_ID", "COMMAND_REGISTRATION_PARSE_ERROR",
    "COMMAND_IMPLEMENTATION_MISSING", "COMMAND_IMPLEMENTATION_UNRESOLVED", "SUBCOMMAND_IMPLEMENTATION_MISSING",
}


def main() -> int:
    parser = argparse.ArgumentParser(description="Check repository-to-Player-Docs coverage.")
    parser.add_argument("--root", default=".")
    parser.add_argument("--inventory", default="build/repository-inventory/repository-inventory.json")
    parser.add_argument("--output", default="build/repository-inventory")
    parser.add_argument("--mode", choices=("report", "strict"), default="report")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    inventory_path = Path(args.inventory)
    if not inventory_path.is_absolute(): inventory_path = root / inventory_path
    output = Path(args.output)
    if not output.is_absolute(): output = root / output
    try:
        if inventory_path.is_file():
            inventory = load_json(inventory_path)
        else:
            inventory = generate_inventory(root, args.mode)
            write_repository_reports(output, inventory)
        manifest = load_manifest(root / "docs/documentation-manifest.yml")
        coverage = check_coverage(root, inventory, manifest, args.mode)
    except Exception as exc:
        print(f"COVERAGE_ERROR: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2
    dump_json(output / "documentation-coverage.json", coverage)
    write_markdown_table(output / "documentation-coverage.md", "Documentation coverage",
                         ["Metric", "Value"], coverage["metrics"].items())
    blocking = [x for x in coverage["findings"] if x["severity"] == "FAIL"]
    integrity = [x for x in inventory.get("findings", []) if x.get("code") in INTEGRITY_CODES and x.get("severity") == "FAIL"]
    print("Documentation coverage:")
    for key, value in coverage["metrics"].items(): print(f"  {key}: {value}")
    if args.mode == "strict":
        return 1 if blocking or integrity or coverage["metrics"]["review_required_findings"] else 0
    return 1 if integrity else 0


if __name__ == "__main__":
    raise SystemExit(main())
