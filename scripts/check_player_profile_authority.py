#!/usr/bin/env python3
"""Fail-closed audit for IceSMP-owned persistent player state.

Every actual player-state access must be owned by PlayerProfile or explicitly documented
as runtime/item/entity metadata, a rebuildable mirror, or a separate shared aggregate.
Stable file+kind+symbol keys avoid line-number drift.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter
from pathlib import Path

RULES = {
    # Declarations of NamespacedKey/PersistentDataContainer are not authorities. The actual
    # container access is the relevant evidence and keeps receiver classification possible.
    "PLAYER_PDC": re.compile(r"getPersistentDataContainer\s*\("),
    "UUID_MAP": re.compile(r"(?:Map|ConcurrentMap|LoadingCache)\s*<\s*UUID\b"),
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
    "/storage/",
    "transactionjournal",
    "persistentstorecoordinator",
    "factiontaxjournal",
    "factionswitchjournal",
)

RUNTIME_PATH_OR_SYMBOL_TOKENS = (
    "/listeners/",
    "/spells/",
    "runtime",
    "session",
    "cache",
    "online",
    "live",
    "activecast",
    "pendingtask",
    "transient",
    "cooldownuntil",
    "regiontask",
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
        return (
            "RUNTIME",
            "Regression fixture or test-only state; production authority is audited separately.",
        )

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

    receiver_match = re.search(
        r"\b([a-z_$][a-z0-9_$]*)\s*\.\s*getpersistentdatacontainer\s*\(",
        lower_symbol,
    )
    receiver = receiver_match.group(1) if receiver_match else ""
    player_receivers = {"player", "target", "owner", "sender", "recipient", "victim", "killer", "viewer"}
    item_receivers = {"item", "itemstack", "stack", "meta", "itemmeta", "hand", "auctionhand", "result"}
    entity_receivers = {
        "entity", "mob", "projectile", "arrow", "horse", "animal", "creature", "living", "stand", "display"
    }

    if kind == "PLAYER_PDC" and receiver in player_receivers:
        return (
            "TRANSITION",
            "Direct player PDC access is durable player-state authority until replaced by PlayerProfile.",
        )

    if kind == "PLAYER_PDC" and (
        receiver in item_receivers
        or "/items/" in lower_path
        or "getitemmeta" in lower_symbol
        or "itemmeta" in lower_symbol
    ):
        return (
            "ITEM_METADATA",
            "Persistent item identity or item-owned metadata, not player progression.",
        )

    if kind == "PLAYER_PDC" and (
        receiver in entity_receivers
        or _has_any(combined, (
            "entity.getpersistentdatacontainer",
            "mob.getpersistentdatacontainer",
            "projectile.getpersistentdatacontainer",
            "arrow.getpersistentdatacontainer",
            "horse.getpersistentdatacontainer",
        ))
    ):
        return (
            "ENTITY_METADATA",
            "Entity-owned or short-lived runtime marker; durable player state belongs to PlayerProfile.",
        )

    player_owned_path = _has_any(lower_path, PLAYER_OWNED_PATH_TOKENS)
    shared_path = _has_any(lower_path, SHARED_AGGREGATE_PATH_TOKENS)

    if kind == "UUID_MAP":
        if _has_any(combined, RUNTIME_PATH_OR_SYMBOL_TOKENS):
            return (
                "RUNTIME",
                "In-memory runtime/session cache; it is rebuilt or discarded and is not durable authority.",
            )
        if player_owned_path:
            return (
                "TRANSITION",
                "Manager-owned UUID map represents player state and must move behind PlayerProfile authority.",
            )
        if shared_path:
            return (
                "GLOBAL_AGGREGATE_REFERENCE",
                "Separate shared aggregate keyed by player UUID; PlayerProfile stores only stable references.",
            )
        return (
            "TRANSITION",
            "Unproven UUID-keyed state requires migration or explicit runtime/shared-aggregate evidence.",
        )

    if kind in {"PLAYER_YAML", "DIRECT_FILE_IO"}:
        if player_owned_path:
            return (
                "TRANSITION",
                "Standalone player-owned durable storage must move behind PlayerProfile authority.",
            )
        if shared_path:
            return (
                "GLOBAL_AGGREGATE_REFERENCE",
                "Shared aggregate or persistence infrastructure remains separate from PlayerProfile.",
            )
        return (
            "TRANSITION",
            "Player-shaped direct persistence requires migration or explicit shared-aggregate evidence.",
        )

    if kind == "PLAYER_PDC":
        return (
            "TRANSITION",
            "PDC receiver could not be proven item/entity metadata; fail closed as player-state transition.",
        )

    return (
        "TRANSITION",
        "Unclassified persistent player-state finding requires migration or explicit non-authoritative evidence.",
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
    parser.add_argument(
        "--reclassify-all",
        action="store_true",
        help="Re-evaluate every current finding; use after classifier changes.",
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
        entries_by_key: dict[str, dict[str, str]] = {}
        for finding in findings:
            key = str(finding["key"])
            old = existing.get(key)
            reclassify = args.reclassify_all or (
                args.reclassify_transitions and old is not None
                and old.get("category") == "TRANSITION"
            )
            if old and not reclassify:
                entries_by_key[key] = old
                continue
            category, reason = classify_finding(finding)
            entries_by_key[key] = {"key": key, "category": category, "reason": reason}
        payload = {
            "version": 1,
            "allowed_categories": ALLOWED_CATEGORIES,
            "entries": [entries_by_key[key] for key in sorted(entries_by_key)],
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
    categories = Counter(str(entry.get("category", "")) for entry in allowed.values())
    report = {
        "finding_count": len(findings),
        "category_counts": dict(sorted(categories.items())),
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
    print("Authority categories: " + ", ".join(
        f"{category}={count}" for category, count in sorted(categories.items())
    ))
    for label, items in (("UNKNOWN", unknown), ("STALE", stale), ("INVALID", invalid)):
        for item in items[:50]:
            print(f"{label}: {item.get('key')}")
    return 1 if unknown or stale or invalid else 0


if __name__ == "__main__":
    raise SystemExit(main())
