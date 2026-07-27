#!/usr/bin/env python3
"""Dependency-free regression and source-protocol guard for season/community transition."""

from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    ROOT / "src/main/java/hu/taliann/icesmp/managers/CommunitySeasonState.java",
    ROOT / "src/regression/java/hu/taliann/icesmp/managers/CommunitySeasonStateRegressionTest.java",
]
MAIN_CLASS = "hu.taliann.icesmp.managers.CommunitySeasonStateRegressionTest"
COMMUNITY_SOURCE = ROOT / "src/main/java/hu/taliann/icesmp/managers/CommunityGoalManager.java"
SEASON_SOURCE = ROOT / "src/main/java/hu/taliann/icesmp/managers/SeasonManager.java"
CORE_SOURCE = ROOT / "src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"


def require_tool(name: str) -> str:
    path = shutil.which(name)
    if path is None:
        raise SystemExit(f"Missing required tool: {name}")
    return path


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f"Missing protocol method: {signature}")
    opening = source.find("{", start)
    if opening < 0:
        raise SystemExit(f"Missing method body: {signature}")
    depth = 0
    for index in range(opening, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise SystemExit(f"Unbalanced method body: {signature}")


def require_order(text: str, *needles: str) -> None:
    position = -1
    for needle in needles:
        found = text.find(needle, position + 1)
        if found < 0:
            raise SystemExit(f"Missing or reordered protocol step: {needle}")
        position = found


def verify_source_protocol() -> None:
    community = COMMUNITY_SOURCE.read_text(encoding="utf-8")
    season = SEASON_SOURCE.read_text(encoding="utf-8")
    core = CORE_SOURCE.read_text(encoding="utf-8")

    if "resetForNewSeason" in community or "setSeasonResetHook" in season:
        raise SystemExit("The old mutating preflight hook must not remain")
    if "setSeasonTransitionCoordinator(communityGoalManager::commitSeasonTransition)" not in core:
        raise SystemExit("Core does not wire the coordinated transition")
    if community.count("YamlStore.registerCriticalWrite(storageFile)") != 1:
        raise SystemExit("Community transition file must be a critical write target")
    if season.count("YamlStore.registerCriticalWrite(storageFile)") != 1:
        raise SystemExit("Season transition file must be a critical write target")

    load = method_body(community, "public synchronized void load()")
    require_order(
        load,
        "CommunitySeasonState.reconcileOnLoad",
        "CommunitySeasonState.LoadAction.RESET_TO_CURRENT",
        "progress.clear()",
        "persistSeasonAlignmentOrThrow",
        "flushPendingCompletions()",
    )

    save = method_body(community, "private boolean saveStrict()")
    if 'yaml.set("season.number", progressSeasonNumber)' not in save:
        raise SystemExit("Community season generation is not persisted")

    transition = method_body(community, "public synchronized boolean commitSeasonTransition")
    require_order(
        transition,
        "flushPendingCompletions()",
        "CommunitySeasonState.validateTransition",
        "seasonCommit.getAsBoolean()",
        "progress.clear()",
        "progressSeasonNumber = openedSeason",
        "persistSeasonAlignmentOrThrow",
    )

    for signature in (
        "public synchronized void contribute(",
        "public synchronized boolean contributeOnce(",
    ):
        body = method_body(community, signature)
        if body.find("ensureContributionsEnabled()") > 80 or "ensureContributionsEnabled()" not in body:
            raise SystemExit(f"Contribution path is not fail-closed: {signature}")

    tick = method_body(season, "public void tick()")
    require_order(
        tick,
        "final BooleanSupplier seasonCommit",
        "coordinator.commit(closingSeason, openedSeason, seasonCommit)",
    )

    close = method_body(season, "private boolean closeExpiredSeason")
    require_order(
        close,
        "closingPoints.putAll(points)",
        "awardChampionMembers(champion)",
        "writeStateLocked()",
        '"season-chapter-opened"',
        "return true",
    )


def main() -> None:
    verify_source_protocol()
    missing = [str(path) for path in SOURCES if not path.is_file()]
    if missing:
        raise SystemExit("Missing regression sources: " + ", ".join(missing))

    javac = require_tool("javac")
    java = require_tool("java")
    with tempfile.TemporaryDirectory(prefix="season-community-regression-") as output:
        subprocess.run(
            [javac, "-encoding", "UTF-8", "-d", output, *map(str, SOURCES)],
            check=True,
            cwd=ROOT,
        )
        subprocess.run([java, "-cp", output, MAIN_CLASS], check=True, cwd=ROOT)

    print("Season/community transition regression: PASS")


if __name__ == "__main__":
    main()
