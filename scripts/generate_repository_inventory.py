#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from repository_inventory.inventory import generate_inventory
from repository_inventory.report import write_repository_reports


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate deterministic IceSMP repository inventory.")
    parser.add_argument("--root", default=".", help="Repository root")
    parser.add_argument("--output", default="build/repository-inventory", help="Output directory")
    parser.add_argument("--mode", choices=("report", "strict"), default="report")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    output = Path(args.output)
    if not output.is_absolute(): output = root / output
    try:
        inventory = generate_inventory(root, args.mode)
        write_repository_reports(output, inventory)
    except Exception as exc:
        print(f"INVENTORY_ERROR: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2
    blocking = [item for item in inventory["findings"] if item.get("severity") == "FAIL"]
    print(f"Inventory: {len(inventory['commands'])} roots, {len(inventory['subcommands'])} subcommands, "
          f"{len(inventory['features'])} features, {len(blocking)} blocking findings")
    return 1 if blocking else 0


if __name__ == "__main__":
    raise SystemExit(main())
