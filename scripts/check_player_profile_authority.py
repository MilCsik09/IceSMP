#!/usr/bin/env python3
"""Fail-closed audit for IceSMP-owned persistent player state.

Every finding must be owned by PlayerProfile or explicitly documented as
runtime/item/entity metadata, a rebuildable mirror, a shared aggregate, or a
short-lived transition. Stable file+kind+symbol keys avoid line-number drift.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

RULES = {
    "PLAYER_PDC": re.compile(
        r"(?:getPersistentDataContainer\s*\(|PersistentDataContainer|NamespacedKey\s*\()"
    ),
    "UUID_MAP": re.compile(r"(?:Map|ConcurrentMap|LoadingCache)\s*<\s*UUID\b"),
    "PLAYER_YAML": re.compile(
        r"(?:YamlConfiguration|YamlStore)[^\n]*(?:player|uuid|profile)"
        r"|resolve\([^\n]*(?:player|uuid|profile)"
        r"|player[-_ ]?(?:data|profile)[^\n]*\.ya?ml",
        re.I,
    ),
    "DIRECT_FILE_IO": re.compile(
        r"(?:Files\.(?:read|write|move|delete|create)|FileChannel\.open|new\s+File\s*\()"
        r"[^\n]*(?:player|uuid|profile)",
        re.I,
    ),
    "LEGACY_NOOP": re.compile(
        r"\b(?:ClassProfile|ClassProfileCodec|YamlClassProfileRepository|"
        r"BukkitClassProfileSessionBridge|resetClassProfileV2|ICS2)\b"
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


def stable_symbol(line: str) -> str:
    line = re.sub(r"//.*$", "", line).strip()
    line = re.sub(r"\s+", " ", line)
    return line[:240]


def scan(root: Path) -> list[dict[str, object]]:
    findings: list[dict[str, object]] = []
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
                    findings.append(
                        {
                            "key": f"{kind}|{rel}|{symbol}",
                            "kind": kind,
                            "path": rel,
                            "line": number,
                            "symbol": symbol,
                        }
                    )
    return findings


def classify_finding(finding: dict[str, object]) -> tuple[str, str]:
    path = str(finding["path"])
    symbol = str(finding["symbol"])
    kind = str(finding["kind"])
    lower_path = path.lower()
    lower_symbol = symbol.lower()
    combined = lower_path + " " + lower_symbol

    if "/playerprofile/" in lower_path:
        return (
            "PLAYER_PROFILE_AUTHORITY",
            "Canonical PlayerProfile domain, repository, transaction or API implementation.",
        )

    if kind == "LEGACY_NOOP":
        return (
            "TRANSITION",
            "Legacy profile implementation or fallback must be removed by the full-authority stack.",
        )

    item_context = (
        "/items/" in lower_path
        or "itemmeta" in lower_symbol
        or "itemstack" in lower_symbol
        or "getitemmeta" in lower_symbol
    )
    player_container = re.search(
        r"\b(?:player|target|owner|sender|recipient)\s*\.\s*getpersistentdatacontainer",
        lower_symbol,
    )
    if item_context and not player_container:
        return (
            "ITEM_METADATA",
            "Persistent item identity or item-owned metadata, not player progression.",
        )

    entity_context = any(
        token in combined
        for token in (
            "entity.getpersistentdatacontainer",
            "mob.getpersistentdatacontainer",
            "projectile.getpersistentdatacontainer",
            "livingentity",
            "transiententities",
        )
    )
    if entity_context and not player_container:
        return (
            "ENTITY_METADATA",
            "Entity-owned or short-lived runtime marker; durable player state belongs to PlayerProfile.",
        )

    shared_tokens = (
        "guild",
        "party",
        "auction",
        "market",
        "claim",
        "season",
        "raid",
        "territory",
        "treasury",
        "council",
        "communitygoal",
        "community_goal",
        "exchangeboard",
        "blockregen",
        "transactionjournal",
        "persistentstorecoordinator",
        "yamlstore",
        "factiontaxjournal",
        "factionswitchjournal",
    )
    if any(token in combined for token in shared_tokens):
        return (
            "GLOBAL_AGGREGATE_REFERENCE",
            "Shared aggregate or persistence infrastructure remains separate; PlayerProfile stores only stable player references.",
        )

    if kind == "UUID_MAP":
        runtime_tokens = (
            "/listeners/",
            "/spells/",
            "runtime",
            "session",
            "cache",
            "online",
            "live",
            "active",
            "pendingtask",
            "cooldownuntil",
            "transient",
        )
        if any(token in combined for token in runtime_tokens):
            return (
                "RUNTIME",
                "In-memory runtime/session cache; it must be rebuilt or discarded and is not durable authority.",
            )
        return (
            "TRANSITION",
            "Manager-owned UUID state may still be durable player authority and requires explicit migration or proof of runtime-only use.",
        )

    if player_container or kind in {"PLAYER_YAML", "DIRECT_FILE_IO"}:
        return (
            "TRANSITION",
            "Existing player-owned PDC/YAML/file authority scheduled for removal by the full-authority stack.",
        )

    if "/regression/" in lower_path:
        return (
            "RUNTIME",
            "Regression fixture or temporary test state; production authority is audited separately.",
        )

    return (
        "TRANSITION",
        "Unclassified persistent player-state finding requires migration or an explicit non-authoritative classification.",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--allowlist", default="scripts/player_profile_authority_allowlist.json")
    parser.add_argument("--write-report")
    parser.add_argument("--update-allowlist", action="store_true")
    parser.add_argument(
        "--reclassify-transitions",
        action="store_true",
        help="Re-evaluate existing TRANSITION entries with the current classifier.",
    )
    args = parser.parse_args()

    root = Path(args.root).resolve()
    allow_path = root / args.allowlist
    findings = scan(root)
    if args.update_allowlist:
        existing: dict[str, dict[str, str]] = {}
        if allow_path.exists():
            existing = {
                item["key"]: item
                for item in json.loads(allow_path.read_text(encoding="utf-8")).get("entries", [])
            }
        entries: list[dict[str, str]] = []
        for finding in findings:
            old = existing.get(str(finding["key"]))
            if old and not (
                args.reclassify_transitions and old.get("category") == "TRANSITION"
            ):
                entries.append(old)
                continue
            category, reason = classify_finding(finding)
            entries.append(
                {"key": str(finding["key"]), "category": category, "reason": reason}
            )
        payload = {
            "version": 1,
            "allowed_categories": ALLOWED_CATEGORIES,
            "entries": sorted(entries, key=lambda item: item["key"]),
        }
        allow_path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    data = (
        json.loads(allow_path.read_text(encoding="utf-8"))
        if allow_path.exists()
        else {"entries": []}
    )
    allowed = {entry["key"]: entry for entry in data.get("entries", [])}
    current = {str(finding["key"]): finding for finding in findings}
    unknown = [finding for key, finding in current.items() if key not in allowed]
    stale = [entry for key, entry in allowed.items() if key not in current]
    invalid = [
        entry
        for entry in allowed.values()
        if not entry.get("reason")
        or entry.get("category") not in data.get("allowed_categories", [])
    ]
    transitions = [
        entry for entry in allowed.values() if entry.get("category") == "TRANSITION"
    ]
    report = {
        "finding_count": len(findings),
        "unknown": unknown,
        "stale": stale,
        "invalid": invalid,
        "transition_count": len(transitions),
        "fingerprint": hashlib.sha256(
            "\n".join(sorted(current)).encode("utf-8")
        ).hexdigest(),
    }
    if args.write_report:
        output = root / args.write_report
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    print(
        "PlayerProfile authority guard: "
        f"{len(findings)} findings, {len(unknown)} unknown, {len(stale)} stale, "
        f"{len(invalid)} invalid, {len(transitions)} transition"
    )
    for label, items in (("UNKNOWN", unknown), ("STALE", stale), ("INVALID", invalid)):
        for item in items[:50]:
            print(f"{label}: {item.get('key')}")
    return 1 if unknown or stale or invalid else 0


if __name__ == "__main__":
    raise SystemExit(main())
