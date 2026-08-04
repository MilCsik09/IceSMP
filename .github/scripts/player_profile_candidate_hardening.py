#!/usr/bin/env python3
"""Refresh fail-closed PlayerProfile authority ownership on the resolved candidate."""
from __future__ import annotations

import os
import subprocess
from pathlib import Path

ROOT = Path.cwd()


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


def main() -> int:
    before = output("git", "rev-parse", "HEAD")
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
    run("git", "add", "scripts/player_profile_authority_allowlist.json")
    staged = output("git", "diff", "--cached", "--name-only")
    if staged:
        run("git", "diff", "--cached", "--check")
        run("git", "commit", "-m", "chore(profile): refresh authority ownership fingerprints")
    for ancestor in (
        os.environ["PROFILE_V2_HEAD"],
        os.environ["RECOVERY_HEAD"],
        os.environ["OLD_PLATFORM_HEAD"],
    ):
        result = run("git", "merge-base", "--is-ancestor", ancestor, "HEAD", check=False)
        if result.returncode != 0:
            raise RuntimeError(f"Required ancestor missing after hardening: {ancestor}")
    candidate = output("git", "rev-parse", "HEAD")
    print(f"Authority candidate: {before} -> {candidate}")
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with Path(github_output).open("a", encoding="utf-8") as handle:
            handle.write(f"candidate_sha={candidate}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
