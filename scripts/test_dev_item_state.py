#!/usr/bin/env python3
"""Compile and run dependency-free DEV reward regressions plus obsolete-path guards."""

from __future__ import annotations

import pathlib
import shutil
import subprocess
import tempfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = [
    ROOT / "src/main/java/hu/taliann/icesmp/managers/DevItemStateData.java",
    ROOT / "src/main/java/hu/taliann/icesmp/managers/DevItemRewardTransition.java",
    ROOT / "src/regression/java/hu/taliann/icesmp/managers/DevItemStateDataRegressionTest.java",
    ROOT / "src/regression/java/hu/taliann/icesmp/managers/DevItemRewardTransitionRegressionTest.java",
    ROOT / "src/regression/java/hu/taliann/icesmp/managers/DevItemRewardRegressionSuite.java",
]
MAIN_CLASS = "hu.taliann.icesmp.managers.DevItemRewardRegressionSuite"
DEV_MANAGER = ROOT / "src/main/java/hu/taliann/icesmp/managers/DevItemManager.java"

FORBIDDEN_DEV_PATHS = {
    "dev_reward_receipt": "player-PDC DEV receipt",
    "bingulus.pending.grant-id": "legacy grant id",
    "bingulus.pending.recipient": "legacy pending recipient",
    "validateLegacyReceiptMigration": "legacy receipt migration",
    "Player.saveData()": "explicit playerdata commit",
    "requestSave()": "obsolete async save queue",
    "saveQueued": "obsolete async save queue flag",
    "saveAgain": "obsolete async save queue flag",
    "manual reconciliation": "legacy reconciliation documentation in runtime source",
}


def require_tool(name: str) -> str:
    executable = shutil.which(name)
    if executable is None:
        raise SystemExit(f"Required Java 21 tool is unavailable: {name}")
    return executable


def verify_removed_paths() -> None:
    source = DEV_MANAGER.read_text(encoding="utf-8")
    found = [description for token, description in FORBIDDEN_DEV_PATHS.items() if token in source]
    if found:
        raise SystemExit("Obsolete DEV reward paths remain: " + ", ".join(found))


def main() -> None:
    verify_removed_paths()
    javac = require_tool("javac")
    java = require_tool("java")
    missing = [str(path) for path in SOURCES if not path.is_file()]
    if missing:
        raise SystemExit("Missing regression source(s): " + ", ".join(missing))

    with tempfile.TemporaryDirectory(prefix="icesmp-dev-reward-regression-") as directory:
        output = pathlib.Path(directory)
        subprocess.run(
            [javac, "--release", "21", "-d", str(output), *(str(path) for path in SOURCES)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run([java, "-cp", str(output), MAIN_CLASS], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
