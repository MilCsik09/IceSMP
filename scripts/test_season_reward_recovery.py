#!/usr/bin/env python3
"""Dependency-free regression and source-order guard for season reward recovery."""
from __future__ import annotations

import pathlib
import shutil
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = [
    ROOT / "src/main/java/hu/taliann/icesmp/managers/SeasonRewardStateData.java",
    ROOT / "src/regression/java/hu/taliann/icesmp/managers/SeasonRewardStateDataRegressionTest.java",
]
MAIN = "hu.taliann.icesmp.managers.SeasonRewardStateDataRegressionTest"
SEASON = ROOT / "src/main/java/hu/taliann/icesmp/managers/SeasonManager.java"
MONUMENT = ROOT / "src/main/java/hu/taliann/icesmp/managers/SeasonMonumentManager.java"


def require_order(text: str, *needles: str) -> None:
    pos = -1
    for needle in needles:
        pos = text.find(needle, pos + 1)
        if pos < 0:
            raise SystemExit(f"Missing or reordered protocol step: {needle}")


def verify_source_protocol() -> None:
    season = SEASON.read_text(encoding="utf-8")
    monument = MONUMENT.read_text(encoding="utf-8")
    close = season[season.index("private boolean closeExpiredSeason"):]
    close = close[:close.index("private RewardPlan buildRewardPlan")]
    require_order(close, "buildRewardPlan", "pendingRewardBatch =", "writeStateLocked()", "return true")
    tick = season[season.index("public void tick()"):season.index("private boolean closeExpiredSeason")]
    require_order(tick, "coordinator.commit(closingSeason, openedSeason, seasonCommit)", "processPendingSeasonRewards()")
    if "treasuryManager.deposit(" in close:
        raise SystemExit("Season close must not perform non-idempotent treasury deposits")
    delivery = season[season.index("private CompletionStage<Void> deliverMemberClaim"):]
    delivery = delivery[:delivery.index("private boolean canFitAll")]
    # A PlayerProfile-migráció utáni protokoll: durable operation-receipt (prepare→commit)
    # keretezi a kézbesítést; a commit CSAK sikeres persist után, az acknowledge a commit után.
    require_order(delivery, "operationStore.prepare", "getInventory().addItem",
                  "applyMemberBuff", "if (!persistPlayer(player))",
                  "operationStore.commit", "acknowledgeMemberClaim")
    failed = delivery[delivery.index("if (!persistPlayer(player))"):]
    require_order(failed, "setStorageContents(inventoryBefore)", "restorePotionEffects",
                  "operationStore.commit")
    if "dropItemNaturally" in season:
        raise SystemExit("Durable season rewards must not use world-drop overflow")
    if "recordSeasonOnce" not in monument or "applied-grants" not in monument:
        raise SystemExit("Monument target lacks durable idempotency receipt")


def main() -> None:
    verify_source_protocol()
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        raise SystemExit("Java 21 tools are required")
    with tempfile.TemporaryDirectory(prefix="season-reward-regression-") as out:
        subprocess.run([javac, "--release", "21", "-d", out, *map(str, SOURCES)], cwd=ROOT, check=True)
        subprocess.run([java, "-cp", out, MAIN], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
