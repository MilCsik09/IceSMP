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
    "cooldown",
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

    if kind == "PLAYER_PDC":
        return "TRANSITION", (
            "PDC receiver could not be proven item/entity metadata; fail closed."
        )

    return "TRANSITION", "Unclassified persistent player-state finding; fail closed."


def _load_override_payload(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"version": 2, "allowed_categories": ALLOWED_CATEGORIES, "entries": []}
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("authority override file must be an object")
    return payload


def audit(root: Path, allow_path: Path) -> dict[str, Any]:
    findings = scan(root)
    payload = _load_override_payload(allow_path)
    allowed_categories = payload.get("allowed_categories", ALLOWED_CATEGORIES)
    overrides: dict[str, dict[str, str]] = {}
    invalid: list[dict[str, object]] = []
    for raw in payload.get("entries", []):
        if not isinstance(raw, dict):
            invalid.append({"entry": raw, "reason": "override is not an object"})
            continue
        key = str(raw.get("key", ""))
        category = str(raw.get("category", ""))
        reason = str(raw.get("reason", ""))
        if not key or category not in allowed_categories or category == "TRANSITION" or not reason:
            invalid.append(raw)
            continue
        if key in overrides:
            invalid.append({"key": key, "reason": "duplicate override"})
            continue
        overrides[key] = {"key": key, "category": category, "reason": reason}

    current = {str(finding["key"]): finding for finding in findings}
    stale = [entry for key, entry in overrides.items() if key not in current]
    entries: list[dict[str, object]] = []
    unknown: list[dict[str, object]] = []
    transitions: list[dict[str, object]] = []
    for finding in findings:
        key = str(finding["key"])
        override = overrides.get(key)
        if override is not None:
            category = override["category"]
            reason = override["reason"]
            source = "override"
        else:
            category, reason = classify_finding(finding)
            source = "classifier"
        entry = {
            **finding,
            "category": category,
            "reason": reason,
            "classification_source": source,
        }
        if category not in allowed_categories or not reason:
            unknown.append(entry)
        if category == "TRANSITION":
            transitions.append(entry)
        entries.append(entry)

    categories = Counter(str(entry["category"]) for entry in entries)
    return {
        "finding_count": len(findings),
        "category_counts": dict(sorted(categories.items())),
        "unknown": unknown,
        "stale": stale,
        "invalid": invalid,
        "transitions": transitions,
        "transition_count": len(transitions),
        "override_count": len(overrides),
        "fingerprint": hashlib.sha256(
            "\n".join(sorted(current)).encode("utf-8")
        ).hexdigest(),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--allowlist", default="scripts/player_profile_authority_allowlist.json")
    parser.add_argument("--write-report")
    parser.add_argument("--update-allowlist", action="store_true")
    parser.add_argument("--reclassify-transitions", action="store_true")
    parser.add_argument("--reclassify-all", action="store_true")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    allow_path = root / args.allowlist
    if args.update_allowlist:
        payload = _load_override_payload(allow_path)
        payload["version"] = 2
        payload["allowed_categories"] = ALLOWED_CATEGORIES
        payload["entries"] = payload.get("entries", [])
        allow_path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    report = audit(root, allow_path)
    if args.write_report:
        output = root / args.write_report
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    print(
        "PlayerProfile authority guard: "
        f"{report['finding_count']} findings, {len(report['unknown'])} unknown, "
        f"{len(report['stale'])} stale overrides, {len(report['invalid'])} invalid overrides, "
        f"{report['transition_count']} transition"
    )
    print("Authority categories: " + ", ".join(
        f"{category}={count}"
        for category, count in report["category_counts"].items()
    ))
    for label, items in (
        ("UNKNOWN", report["unknown"]),
        ("STALE", report["stale"]),
        ("INVALID", report["invalid"]),
        ("TRANSITION", report["transitions"]),
    ):
        for item in items[:100]:
            print(f"{label}: {item.get('key', item)}")
    return 1 if (
        report["unknown"] or report["stale"] or report["invalid"]
        or report["transitions"]
    ) else 0


if __name__ == "__main__":
    raise SystemExit(main())
