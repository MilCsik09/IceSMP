#!/usr/bin/env python3
"""Fail closed while any legacy player-owned authority remains transitional."""
from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

PATH_PATTERN = re.compile(r"^[^|]+\|([^|]+)\|")


def classify(path: str, key: str) -> str:
    text = f"{path} {key}".lower()
    groups = (
        ("spellbook", ("spell", "mastery", "favorite")),
        ("talents", ("talent",)),
        ("faction", ("faction", "sinner", "sinmanager", "treasury")),
        ("economy", ("currency", "wallet", "bank", "economy", "refund", "debt")),
        ("professions", ("profession", "jobmanager", "recipe")),
        ("quests", ("quest", "objective")),
        ("companions", ("pet", "minion", "companion", "soulforge")),
        ("achievements", ("achievement", "bestiary", "milestone")),
        ("statistics", ("stat", "counter", "dailybudget")),
        ("preferences", ("hud", "scoreboard", "preference", "privacy", "notification", "intro")),
        ("moderation", ("moderation", "punishment", "reportmanager", "vanish")),
        ("class-spec", ("classprofile", "classspec", "respec", "specialization")),
    )
    for name, needles in groups:
        if any(needle in text for needle in needles):
            return name
    return "unclassified"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--json-output", default="build/player-profile-transition-report.json")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    allowlist = root / "scripts/player_profile_authority_allowlist.json"
    payload = json.loads(allowlist.read_text(encoding="utf-8"))
    transitions = [entry for entry in payload.get("entries", []) if entry.get("category") == "TRANSITION"]

    by_path: dict[str, list[dict[str, str]]] = defaultdict(list)
    by_domain: Counter[str] = Counter()
    by_kind: Counter[str] = Counter()
    for entry in transitions:
        key = str(entry.get("key", ""))
        match = PATH_PATTERN.match(key)
        path = match.group(1) if match else "<unresolved>"
        kind = key.split("|", 1)[0] if "|" in key else "UNKNOWN"
        domain = classify(path, key)
        by_kind[kind] += 1
        by_domain[domain] += 1
        by_path[path].append({"kind": kind, "domain": domain, "key": key})

    report = {
        "transition_count": len(transitions),
        "domains": dict(sorted(by_domain.items())),
        "kinds": dict(sorted(by_kind.items())),
        "files": [
            {
                "path": path,
                "count": len(entries),
                "domains": dict(sorted(Counter(item["domain"] for item in entries).items())),
                "kinds": dict(sorted(Counter(item["kind"] for item in entries).items())),
                "entries": entries,
            }
            for path, entries in sorted(by_path.items(), key=lambda item: (-len(item[1]), item[0]))
        ],
    }
    output = root / args.json_output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"PlayerProfile transition authorities: {len(transitions)}")
    for domain, count in by_domain.most_common():
        print(f"  domain {domain}: {count}")
    for path, entries in sorted(by_path.items(), key=lambda item: (-len(item[1]), item[0])):
        print(f"  {len(entries):4d}  {path}")

    if transitions:
        print(f"Detailed report: {output.relative_to(root)}")
        return 1
    print("No transitional player-owned authority remains.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
