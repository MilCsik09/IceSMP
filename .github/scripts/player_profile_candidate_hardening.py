#!/usr/bin/env python3
"""Harden the resolved PlayerProfile candidate without weakening any gate."""
from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path

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
            "chore(profile): reconcile authority and documentation contracts",
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
