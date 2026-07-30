#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

from repository_inventory.inventory import generate_inventory
from repository_inventory.report import write_repository_reports


def exit_code_for_inventory(mode: str, inventory: dict[str, Any]) -> int:
    """Return a blocking exit code only when strict enforcement was requested.

    Report mode is the adoption/discovery path: it must always emit the complete
    artifact set and expose repository findings without making an otherwise
    healthy tooling PR unmergeable. Strict mode is the release gate and keeps
    FAIL findings blocking.
    """
    blocking = [item for item in inventory["findings"] if item.get("severity") == "FAIL"]
    return 1 if mode == "strict" and blocking else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate deterministic IceSMP repository inventory.")
    parser.add_argument("--root", default=".", help="Repository root")
    parser.add_argument("--output", default="build/repository-inventory", help="Output directory")
    parser.add_argument("--mode", choices=("report", "strict"), default="report")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    output = Path(args.output)
    if not output.is_absolute():
        output = root / output
    try:
        inventory = generate_inventory(root, args.mode)
        write_repository_reports(output, inventory)
    except Exception as exc:
        print(f"INVENTORY_ERROR: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2
    blocking = [item for item in inventory["findings"] if item.get("severity") == "FAIL"]
    print(
        f"Inventory: {len(inventory['commands'])} roots, "
        f"{len(inventory.get('routes', inventory['subcommands']))} functional routes, "
        f"{len(inventory.get('root_aliases', []))} root aliases, "
        f"{len(inventory.get('routing_aliases', []))} routing aliases, "
        f"{len(inventory['features'])} features, {len(blocking)} blocking findings "
        f"({'enforced' if args.mode == 'strict' else 'reported'})"
    )
    return exit_code_for_inventory(args.mode, inventory)


if __name__ == "__main__":
    raise SystemExit(main())
