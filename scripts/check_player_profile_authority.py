#!/usr/bin/env python3
"""Fail-closed audit for IceSMP-owned persistent player state.

The current source tree is always classified. The checked-in JSON file contains only
explicit, reviewed overrides for findings whose ownership cannot be proven from syntax.
A generated snapshot is not an authority: new source findings are classified immediately,
and ambiguous findings become TRANSITION blockers by default.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter
from pathlib import Path
from typing import Any

RULES = {
    "PLAYER_PDC": re.compile(r"getPersistentDataContainer\s*\("),
    # Only class fields are authority candidates. Local snapshots, method parameters and
    # immutable record components are values flowing through an authority, not authorities.
    "UUID_MAP": re.compile(
        r"\b(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?"
        r"(?:[A-Za-z0-9_$.]*Map|LoadingCache)\s*<\s*UUID\b"
        r"[^;()]*\b[A-Za-z_$][A-Za-z0-9_$]*\s*(?:=|;)"
    ),
    "PLAYER_YAML": re.compile(
        r"(?:YamlConfiguration|YamlStore)[^\n]*(?:player|uuid|profile)"
        r"|resolve\([^\n]*(?:player|uuid|profile)"
        r"|player[-_ ]?(?:data|profile)[^\n]*\.ya?ml",
        re.I,
    ),
    "DIRECT_FILE_IO": re.compile(
        r"(?:Files\.(?:read|write|move|delete|create|copy)|FileChannel\.open|new\s+File\s*\()"
        r"[^\n]*(?:player|uuid|profile)",
        re.I,
    ),
    "LEGACY_NOOP": re.compile(
        r"\b(?:resetClassProfileV2|legacyClassProfile|legacyProfile|profileMigration|MIGRATION_REVIEW)\b"
        r"|class-spec-rework\.enabled",
        re.I,
    ),
}
SOURCE_SUFFIXES = {".java", ".kt", ".kts"}
IGNORE_DIRS = {"build", ".gradle", "resource-pack", "node_modules", ".git"}
ALLOWED_CATEGORIES = [
    "PLAYER_PROFILE_AUTHORITY",
    "RUNTIME",
    "DERIVED_MIRROR",
    "ITEM_METADATA",
    "ENTITY_METADATA",
    "GLOBAL_AGGREGATE_REFERENCE",
    "TRANSITION",
]

PLAYER_OWNED_PATH_TOKENS = (
    "currencymanager",
    "bankmanager",
    "factionmanager",
    "sinmanager",
    "professionmanager",
    "jobmanager",
    "questmanager",
    "dailyquestmanager",
    "talentmanager",
    "spellmasterymanager",
    "spellfavoritesmanager",
    "achievementmanager",
    "bestiarymanager",
    "statsmanager",
    "intromanager",
    "hudmanager",
    "whispermanager",
    "specializationmanager",
    "soulforgemanager",
    "minionmanager",
    "petmanager",
)

SHARED_AGGREGATE_PATH_TOKENS = (
    "guildmanager",
    "partymanager",
    "marketmanager",
    "auctionmanager",
    "claimmanager",
    "factiontreasurymanager",
    "councilmanager",
    "raidmanager",
    "seasonmanager",
    "communitygoalmanager",
    "exchangeboardmanager",
    "territorymanager",
    "hiddenspotmanager",
    "donationchestmanager",
    "kingmanager",
    "moderationmanager",
    "seasonmonumentmanager",
    "/crates/",
    "/moderation/",
    "/storage/",
    "transactionjournal",
    "persistentstorecoordinator",
)

RUNTIME_SYMBOL_TOKENS = (
    "runtime",
    "session",
    "cache",
    "mirror",
    "projection",
    "leaderboard",
    "online",
    "live",
    "active",
    "pending",
    "transient",
    "debounce",
    "lastcast",
    "secondlast",
    "lastcombat",
    "lastregen",
    "lastride",
    "lastheader",
    "lasttab",
    "combo",
    "hint",
    "retaliation",
    "tracked",
    "temporary",
    "weak",
    "expiry",
    "endsat",
    "until",
    "announced",
    "population",
    "snapshot",
    "buffer",
    "hiddenbyviewer",
    "hiddenarmor",
    "frozenplayers",
    "seat",
    "regiontask",
    "scheduler",
    "tail",
    "lock",
    "queue",
    "inflight",
    "reservation",
    "task",
    "handle",
    "window",
    "witness",
)

CANONICAL_CLASS_SPEC_PATH_TOKENS = (
    "/classspec/domain/",
    "/classspec/profile/",
    "/classspec/persistence/playerprofile",
    "/classspec/application/defaultclassspecprofilegateway.java",
)


def stable_symbol(line: str) -> str:
    line = re.sub(r"//.*$", "", line).strip()
    line = re.sub(r"\s+", " ", line)
    return line[:240]


FILE_YAML_PATTERN = re.compile(r"\bYamlStore\b|\bYamlConfiguration\b")
FILE_PLAYER_ID_PATTERN = re.compile(r"UUID\.fromString\(|\.getUniqueId\(\)|Map<UUID")
FILE_YAML_SYMBOL = "<file combines YAML persistence with player-UUID-shaped data>"


def scan(root: Path) -> list[dict[str, object]]:
    findings: dict[str, dict[str, object]] = {}
    for path in sorted((root / "src").rglob("*")):
        if (
            not path.is_file()
            or path.suffix not in SOURCE_SUFFIXES
            or any(part in IGNORE_DIRS for part in path.parts)
        ):
            continue
        rel = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8", errors="replace")
        for number, line in enumerate(text.splitlines(), 1):
            for kind, pattern in RULES.items():
                if pattern.search(line):
                    symbol = stable_symbol(line)
                    key = f"{kind}|{rel}|{symbol}"
                    findings.setdefault(key, {
                        "key": key,
                        "kind": kind,
                        "path": rel,
                        "line": number,
                        "symbol": symbol,
                    })
        if FILE_YAML_PATTERN.search(text) and FILE_PLAYER_ID_PATTERN.search(text):
            key = f"PLAYER_YAML|{rel}|{FILE_YAML_SYMBOL}"
            findings.setdefault(key, {
                "key": key,
                "kind": "PLAYER_YAML",
                "path": rel,
                "line": 1,
                "symbol": FILE_YAML_SYMBOL,
            })
    return [findings[key] for key in sorted(findings)]


def _has_any(text: str, needles: tuple[str, ...]) -> bool:
    return any(needle in text for needle in needles)


def classify_finding(finding: dict[str, object]) -> tuple[str, str]:
    path = str(finding["path"])
    symbol = str(finding["symbol"])
    kind = str(finding["kind"])
    lower_path = path.lower()
    lower_symbol = symbol.lower()
    combined = lower_path + " " + lower_symbol

    if "/regression/" in lower_path or "/test/" in lower_path:
        return "RUNTIME", "Regression fixture or test-only state."

    if "/playerprofile/" in lower_path or _has_any(
            lower_path, CANONICAL_CLASS_SPEC_PATH_TOKENS):
        return "PLAYER_PROFILE_AUTHORITY", (
            "Canonical PlayerProfile or Profile v2 class/spec domain implementation."
        )

    if kind == "LEGACY_NOOP":
        return "TRANSITION", (
            "Legacy migration, fallback, review state or runtime kill switch is forbidden."
        )

    receiver_match = re.search(
        r"\b([a-z_$][a-z0-9_$]*)\s*\.\s*getpersistentdatacontainer\s*\(",
        lower_symbol,
    )
    receiver = receiver_match.group(1) if receiver_match else ""
    player_receivers = {
        "player", "target", "owner", "sender", "recipient", "victim", "killer", "viewer",
        "online", "challenger", "attacker", "bidder", "seller", "buyer", "member",
    }
    item_receivers = {
        "item", "itemstack", "stack", "meta", "itemmeta", "hand", "auctionhand", "result",
        "held", "tool", "weapon", "bow", "sigmeta", "craftedmeta",
    }
    entity_receivers = {
        "entity", "mob", "projectile", "arrow", "horse", "animal", "creature", "living",
        "dead", "stand", "display", "minion", "spawned", "stranger", "totem", "boss", "add",
        "tile", "firework", "mount", "npc",
    }

    if kind == "PLAYER_PDC" and receiver in player_receivers:
        return "TRANSITION", (
            "Direct player PDC access is durable player-state authority until replaced by PlayerProfile."
        )

    if kind == "PLAYER_PDC" and "getchunk().getpersistentdatacontainer" in lower_symbol:
        return "GLOBAL_AGGREGATE_REFERENCE", (
            "Chunk-owned durable world/block provenance is not player-owned profile state."
        )

    if kind == "PLAYER_PDC" and (
        receiver in item_receivers
        or receiver.endswith("meta")
        or "/items/" in lower_path
        or "getitemmeta" in lower_symbol
        or "itemmeta" in lower_symbol
    ):
        return "ITEM_METADATA", "Persistent item identity or item-owned metadata."

    if kind == "PLAYER_PDC" and (
        receiver in entity_receivers
        or _has_any(combined, (
            "entity.getpersistentdatacontainer",
            "mob.getpersistentdatacontainer",
            "projectile.getpersistentdatacontainer",
            "arrow.getpersistentdatacontainer",
            "horse.getpersistentdatacontainer",
            "boss.getpersistentdatacontainer",
            "minion.getpersistentdatacontainer",
            "tile.getpersistentdatacontainer",
            "dead.getpersistentdatacontainer",
        ))
    ):
        return "ENTITY_METADATA", (
            "Entity-, block-entity- or short-lived runtime marker."
        )

    player_owned_path = _has_any(lower_path, PLAYER_OWNED_PATH_TOKENS)
    shared_path = _has_any(lower_path, SHARED_AGGREGATE_PATH_TOKENS)

    if kind == "UUID_MAP":
        if _has_any(combined, RUNTIME_SYMBOL_TOKENS):
            return "RUNTIME", (
                "Field name or owning component proves rebuildable runtime/session/projection state."
            )
        if shared_path:
            return "GLOBAL_AGGREGATE_REFERENCE", (
                "Separate shared aggregate keyed by player or entity UUID."
            )
        if player_owned_path:
            return "TRANSITION", (
                "Manager-owned UUID field in a player domain lacks runtime/projection evidence."
            )
        return "TRANSITION", (
            "Unproven UUID-keyed field requires explicit runtime or shared-aggregate evidence."
        )

    if kind in {"PLAYER_YAML", "DIRECT_FILE_IO"}:
        if shared_path:
            return "GLOBAL_AGGREGATE_REFERENCE", (
                "Shared aggregate or persistence infrastructure remains separate from PlayerProfile."
            )
        if player_owned_path:
            return "TRANSITION", (
                "Standalone player-owned durable storage must move behind PlayerProfile."
            )
        return "TRANSITION", (
            "Player-shaped direct persistence requires explicit shared-aggregate evidence."
        )

    return "TRANSITION", "Unclassified persistence-shaped finding; fail closed."


def fingerprint(finding: dict[str, object]) -> str:
    payload = "\n".join((str(finding["kind"]), str(finding["path"]), str(finding["symbol"])))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:20]


def load_overrides(path: Path) -> dict[str, dict[str, Any]]:
    if not path.is_file():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    rows = data.get("overrides", [])
    if not isinstance(rows, list):
        raise SystemExit("override file requires an 'overrides' list")
    result: dict[str, dict[str, Any]] = {}
    for row in rows:
        if not isinstance(row, dict):
            raise SystemExit("each override must be an object")
        key = row.get("key")
        category = row.get("category")
        reason = str(row.get("reason", "")).strip()
        digest = row.get("fingerprint")
        if not isinstance(key, str) or not key:
            raise SystemExit("override key must be non-empty")
        if category not in ALLOWED_CATEGORIES or category == "TRANSITION":
            raise SystemExit(f"invalid override category for {key}: {category}")
        if not reason:
            raise SystemExit(f"override reason is required for {key}")
        if not isinstance(digest, str) or not digest:
            raise SystemExit(f"override fingerprint is required for {key}")
        if key in result:
            raise SystemExit(f"duplicate override key: {key}")
        result[key] = row
    return result


def audit(root: Path, override_path: Path) -> dict[str, object]:
    findings = scan(root)
    overrides = load_overrides(override_path)
    classified = []
    unknown_overrides = set(overrides)
    invalid_overrides: list[str] = []
    for finding in findings:
        category, reason = classify_finding(finding)
        override = overrides.get(str(finding["key"]))
        if override is not None:
            unknown_overrides.discard(str(finding["key"]))
            current = fingerprint(finding)
            if override.get("fingerprint") != current:
                invalid_overrides.append(str(finding["key"]))
            else:
                category = str(override["category"])
                reason = str(override["reason"])
        classified.append({**finding, "category": category, "reason": reason,
                           "fingerprint": fingerprint(finding)})
    categories = Counter(str(row["category"]) for row in classified)
    transitions = [row for row in classified if row["category"] == "TRANSITION"]
    result = {
        "schema": 2,
        "findings": classified,
        "summary": {
            "findings": len(classified),
            "categories": dict(sorted(categories.items())),
            "transitionCount": len(transitions),
            "unknownOverrideCount": len(unknown_overrides),
            "invalidOverrideCount": len(invalid_overrides),
        },
        "unknownOverrides": sorted(unknown_overrides),
        "invalidOverrides": sorted(invalid_overrides),
    }
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--override-file", default="config/player-profile-authority-overrides.json")
    parser.add_argument("--write-report")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    report = audit(root, root / args.override_file)
    if args.write_report:
        out = Path(args.write_report)
        if not out.is_absolute():
            out = root / out
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    summary = report["summary"]
    print("PlayerProfile authority guard: "
          f"{summary['findings']} findings, {summary['unknownOverrideCount']} unknown, "
          f"{summary['invalidOverrideCount']} stale overrides, "
          f"{summary['transitionCount']} transition")
    print("Authority categories: " + ", ".join(
        f"{key}={value}" for key, value in summary["categories"].items()))
    for row in report["findings"]:
        if row["category"] == "TRANSITION":
            print(f"TRANSITION: {row['key']}")
    return 1 if (summary["transitionCount"] or summary["unknownOverrideCount"]
                 or summary["invalidOverrideCount"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())
