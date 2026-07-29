#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from repository_inventory.delta import compare_inventories
from repository_inventory.util import dump_json, load_json


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare two generated repository inventories.")
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    delta = compare_inventories(load_json(Path(args.base)), load_json(Path(args.head)))
    dump_json(Path(args.output), delta)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
