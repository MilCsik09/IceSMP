#!/usr/bin/env python3
"""Harden the resolved PlayerProfile candidate without weakening any gate."""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
from pathlib import Path
from typing import Any

ROOT = Path.cwd()
MANIFEST_PATH = ROOT / "docs/documentation-manifest.yml"
PROFILE_DOC_PATH = ROOT / "docs/PLAYER_PROFILE_ARCHITECTURE.md"
PROFILE_FEATURE_ID = "feature.platform.player_profile"
PROFILE_MARKER = f"<!-- icesmp-doc-id: {PROFILE_FEATURE_ID} -->"

STALE_FEATURE_IDS = (
    "feature.resource-pack",
    "feature.moderation",
)
STALE_COMPONENT_IDS = (
    "component.hu.taliann.icesmp.classspec.persistence.RepositoryMutationStoreAdapter",
    "component.hu.taliann.icesmp.classspec.application.ClassProfileLifecycleService",
    "component.hu.taliann.icesmp.classspec.persistence.ClassProfileRepository",
    "component.hu.taliann.icesmp.classspec.application.ClassProfileMutationStore",
    "component.hu.taliann.icesmp.classspec.integration.BukkitClassProfileSessionBridge",
    "component.hu.taliann.icesmp.classspec.persistence.YamlClassProfileRepository",
    "component.hu.taliann.icesmp.classspec.persistence.ClassProfileCodec",
    "component.hu.taliann.icesmp.classspec.domain.ClassProfile",
)
NEW_COMPONENT_ID = (
    "component.hu.taliann.icesmp.classspec.persistence."
    "ClassSpecSectionMutationStoreAdapter"
)
NEW_CONFIG_ID = "config.player-profile"

STALE_CONFIG_RESOLUTIONS = (
    "config.dynamic.item-data-factory.config.factions.food-duty.hamukenyer-buff-seconds",
    "config.dynamic.item-data-factory.config.factions.food-duty.hamulakoma-buff-seconds",
    "config.dynamic.item-data-factory.config.factions.food-duty.lepeny-buff-seconds",
    "config.dynamic.item-data-factory.config.factions.food-duty.pisztrang-buff-seconds",
    "config.dynamic.item-data-factory.config.factions.food-duty.porkolt-buff-seconds",
    "config.dynamic.item-data-factory.config.factions.food-duty.rantotta-buff-seconds",
    "config.dynamic.item-data-factory.config.factions.food-duty.vadlakoma-buff-seconds",
)

DYNAMIC_CONFIG_RESOLUTIONS: dict[str, dict[str, str]] = {
    "config.dynamic.currency-manager.configuration.operations": {
        "resolved_path": "currency-balances.yml:operations",
        "reason": (
            "The receiver is the durable currency-balances.yml root; operations is the "
            "exact persisted transaction-witness section."
        ),
    },
    "config.dynamic.currency-manager.section.previous": {
        "resolved_path": "currency-balances.yml:operations.<operation-key>.previous",
        "reason": (
            "The receiver is one validated durable operation section; previous is its "
            "exact pre-mutation wallet snapshot."
        ),
    },
    "config.dynamic.currency-manager.section.expected": {
        "resolved_path": "currency-balances.yml:operations.<operation-key>.expected",
        "reason": (
            "The receiver is one validated durable operation section; expected is its "
            "exact post-mutation wallet snapshot."
        ),
    },
    "config.dynamic.player-profile-platform.config.player-profile.http.admin-tokens": {
        "resolved_path": "config/player-profile.yml:player-profile.http.admin-tokens",
        "reason": "The local config receiver is loaded exclusively from config/player-profile.yml.",
    },
    "config.dynamic.player-profile-platform.config.player-profile.http.bind": {
        "resolved_path": "config/player-profile.yml:player-profile.http.bind",
        "reason": "The local config receiver is loaded exclusively from config/player-profile.yml.",
    },
    "config.dynamic.player-profile-platform.config.player-profile.http.enabled": {
        "resolved_path": "config/player-profile.yml:player-profile.http.enabled",
        "reason": "The local config receiver is loaded exclusively from config/player-profile.yml.",
    },
    "config.dynamic.player-profile-platform.config.player-profile.http.max-request-bytes": {
        "resolved_path": "config/player-profile.yml:player-profile.http.max-request-bytes",
        "reason": "The local config receiver is loaded exclusively from config/player-profile.yml.",
    },
    "config.dynamic.player-profile-platform.config.player-profile.http.max-response-bytes": {
        "resolved_path": "config/player-profile.yml:player-profile.http.max-response-bytes",
        "reason": "The local config receiver is loaded exclusively from config/player-profile.yml.",
    },
    "config.dynamic.player-profile-platform.config.player-profile.http.port": {
        "resolved_path": "config/player-profile.yml:player-profile.http.port",
        "reason": "The local config receiver is loaded exclusively from config/player-profile.yml.",
    },
    "config.dynamic.player-profile-platform.config.player-profile.http.requests-per-minute": {
        "resolved_path": "config/player-profile.yml:player-profile.http.requests-per-minute",
        "reason": "The local config receiver is loaded exclusively from config/player-profile.yml.",
    },
    "config.dynamic.player-profile-platform.config.player-profile.http.self-tokens": {
        "resolved_path": "config/player-profile.yml:player-profile.http.self-tokens",
        "reason": "The local config receiver is loaded exclusively from config/player-profile.yml.",
    },
    "config.dynamic.player-profile-platform.config.player-profile.http.timeout-ms": {
        "resolved_path": "config/player-profile.yml:player-profile.http.timeout-ms",
        "reason": "The local config receiver is loaded exclusively from config/player-profile.yml.",
    },
}


def run(*args: str, check: bool = True, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(args),
        cwd=ROOT,
        check=check,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
    )


def output(*args: str) -> str:
    return run(*args, capture=True).stdout.strip()


def sha256_file(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise RuntimeError(f"Contract source is missing: {relative}")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def entries_digest(section: dict[str, Any]) -> str:
    entries = {
        stable_id: entry
        for stable_id, entry in section.items()
        if not stable_id.startswith("_") and isinstance(entry, dict)
    }
    payload = json.dumps(
        entries,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def update_documentation_contract() -> None:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    features = manifest.setdefault("features", {})
    components = manifest.setdefault("components", {})
    config_sections = manifest.setdefault("config-sections", {})

    for stable_id in STALE_FEATURE_IDS:
        features.pop(stable_id, None)
    for stable_id in STALE_COMPONENT_IDS:
        components.pop(stable_id, None)

    features[PROFILE_FEATURE_ID] = {
        "docs": ["docs/PLAYER_PROFILE_ARCHITECTURE.md"]
    }
    components[NEW_COMPONENT_ID] = {
        "docs": ["docs/PLAYER_PROFILE_ARCHITECTURE.md"]
    }
    config_sections[NEW_CONFIG_ID] = {
        "docs": ["docs/PLAYER_PROFILE_ARCHITECTURE.md"]
    }

    resolutions = manifest.setdefault("config-resolutions", {})
    for stable_id in STALE_CONFIG_RESOLUTIONS:
        resolutions.pop(stable_id, None)
    resolutions.update(DYNAMIC_CONFIG_RESOLUTIONS)

    MANIFEST_PATH.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    profile_doc = PROFILE_DOC_PATH.read_text(encoding="utf-8")
    marker_count = profile_doc.count(PROFILE_MARKER)
    if marker_count == 0:
        heading = "# IceSMP PlayerProfile platform\n"
        if heading not in profile_doc:
            raise RuntimeError("PlayerProfile architecture title was not found")
        profile_doc = profile_doc.replace(
            heading,
            heading + "\n" + PROFILE_MARKER + "\n",
            1,
        )
    elif marker_count != 1:
        raise RuntimeError(f"Unexpected PlayerProfile marker count: {marker_count}")
    PROFILE_DOC_PATH.write_text(profile_doc, encoding="utf-8")


def generate_inventory(output_dir: str, mode: str) -> dict[str, Any]:
    run(
        "python3",
        "scripts/generate_repository_inventory.py",
        "--root",
        ".",
        "--output",
        output_dir,
        "--mode",
        mode,
    )
    path = ROOT / output_dir / "repository-inventory.json"
    return json.loads(path.read_text(encoding="utf-8"))


def sync_exact_command_contract(manifest: dict[str, Any], inventory: dict[str, Any]) -> None:
    section = manifest.get("commands")
    if not isinstance(section, dict):
        raise RuntimeError("Command manifest section is missing")

    for command in inventory.get("commands", []):
        stable_id = command.get("id")
        entry = section.get(stable_id)
        if not isinstance(entry, dict) or entry.get("kind") != "root":
            raise RuntimeError(f"Command root contract entry is missing: {stable_id}")
        entry["name"] = command["name"]
        entry["implementation"] = command["implementation"]
        entry["aliases"] = sorted(command.get("aliases", []))
        entry["registration_file"] = command["registration_file"]
        entry["registration_line"] = command["registration_line"]

    required_sources = {
        command["registration_file"]
        for command in inventory.get("commands", [])
    } | {
        route["source"]
        for route in inventory.get("routes", inventory.get("subcommands", []))
    }
    contract = section.setdefault("_contract", {})
    contract["entries_sha256"] = entries_digest(section)
    contract["expected_counts"] = {
        "root_commands": len(inventory.get("commands", [])),
        "functional_routes": len(inventory.get("routes", inventory.get("subcommands", []))),
        "root_aliases": len(inventory.get("root_aliases", [])),
        "routing_aliases": len(inventory.get("routing_aliases", [])),
    }
    contract["source_sha256"] = {
        relative: sha256_file(relative)
        for relative in sorted(required_sources)
    }


def permission_registration(item: dict[str, Any]) -> str:
    configured = any(
        source.get("kind") == "CONFIG"
        for source in item.get("sources", [])
    )
    if configured:
        return "CONFIG_DYNAMIC"
    return "STATIC" if item.get("registered") else "USE_ONLY"


def sync_exact_permission_contract(manifest: dict[str, Any], inventory: dict[str, Any]) -> None:
    section = manifest.get("permissions")
    if not isinstance(section, dict):
        raise RuntimeError("Permission manifest section is missing")

    permissions = inventory.get("permissions", [])
    for item in permissions:
        stable_id = item.get("id")
        entry = section.get(stable_id)
        if not isinstance(entry, dict):
            raise RuntimeError(f"Permission contract entry is missing: {stable_id}")
        entry["kind"] = "permission"
        entry["node"] = item["node"]
        entry["default"] = item["default"]
        entry["registration"] = permission_registration(item)
        entry["legacy_alias"] = bool(item.get("legacy_alias", False))
        entry["children"] = sorted(item.get("children", []))

    required_sources = {
        evidence["source"]
        for item in permissions
        for field in ("sources", "resolution_evidence")
        for evidence in item.get(field, [])
        if str(evidence.get("source", "")).startswith("src/")
    }
    configured_ids = {
        item["id"]
        for item in permissions
        if any(source.get("kind") == "CONFIG" for source in item.get("sources", []))
    }
    static_ids = {
        item["id"]
        for item in permissions
        if item.get("registered") and item["id"] not in configured_ids
    }

    contract = section.setdefault("_contract", {})
    contract["entries_sha256"] = entries_digest(section)
    contract["expected_counts"] = {
        "total": len(permissions),
        "static_registered": len(static_ids),
        "config_dynamic": len(configured_ids),
    }
    contract["source_sha256"] = {
        relative: sha256_file(relative)
        for relative in sorted(required_sources)
    }


def sync_inventory_contracts() -> None:
    first_inventory = generate_inventory(
        "build/repository-inventory-pre-sync",
        "report",
    )
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    sync_exact_command_contract(manifest, first_inventory)
    sync_exact_permission_contract(manifest, first_inventory)
    MANIFEST_PATH.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    strict_inventory = generate_inventory(
        "build/repository-inventory-post-sync",
        "strict",
    )
    unresolved = [
        finding
        for finding in strict_inventory.get("findings", [])
        if finding.get("severity") in {"FAIL", "REVIEW_REQUIRED"}
    ]
    if unresolved:
        raise RuntimeError(
            "Strict inventory still has unresolved findings:\n"
            + json.dumps(unresolved, ensure_ascii=False, indent=2)
        )


def main() -> int:
    before = output("git", "rev-parse", "HEAD")
    update_documentation_contract()

    # Refresh exact source fingerprints with the repository-owned scanner, then
    # immediately rerun it fail-closed. No finding category is ignored.
    run(
        "python3",
        "scripts/check_player_profile_authority.py",
        "--root",
        ".",
        "--update-allowlist",
    )
    run(
        "python3",
        "scripts/check_player_profile_authority.py",
        "--root",
        ".",
        "--write-report",
        "build/player-profile-authority-post-update.json",
    )

    # Exact command and permission contracts are derived from the scanner's
    # detected candidate surface. Dynamic config paths receive explicit source
    # resolutions; stale mappings are removed rather than ignored.
    sync_inventory_contracts()

    run(
        "git",
        "add",
        "scripts/player_profile_authority_allowlist.json",
        "docs/documentation-manifest.yml",
        "docs/PLAYER_PROFILE_ARCHITECTURE.md",
    )
    staged = output("git", "diff", "--cached", "--name-only")
    if staged:
        run("git", "diff", "--cached", "--check")
        run(
            "git",
            "commit",
            "-m",
            "chore(profile): reconcile authority and exact inventory contracts",
        )

    for ancestor in (
        os.environ["PROFILE_V2_HEAD"],
        os.environ["RECOVERY_HEAD"],
        os.environ["OLD_PLATFORM_HEAD"],
    ):
        result = run("git", "merge-base", "--is-ancestor", ancestor, "HEAD", check=False)
        if result.returncode != 0:
            raise RuntimeError(f"Required ancestor missing after hardening: {ancestor}")

    candidate = output("git", "rev-parse", "HEAD")
    print(f"Hardened candidate: {before} -> {candidate}")
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with Path(github_output).open("a", encoding="utf-8") as handle:
            handle.write(f"candidate_sha={candidate}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
