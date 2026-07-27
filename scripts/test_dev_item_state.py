#!/usr/bin/env python3
"""Run the Gradle-owned DEV regression suite and reject obsolete complexity."""

from __future__ import annotations

import pathlib
import subprocess
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
DEV_RUNTIME_SOURCES = [
    ROOT / "src/main/java/hu/taliann/icesmp/managers/DevItemManager.java",
    ROOT / "src/main/java/hu/taliann/icesmp/managers/DevItemStateData.java",
]

FORBIDDEN_DEV_PATHS = {
    "DevItemRewardTransition": "general DEV reward transition framework",
    "OwnerFence": "owner generation/fence framework",
    "StateWriter": "general state-writer interface",
    "Preparation<": "preparation result record",
    "Completion<": "completion result record",
    "CountDownLatch": "large owner-transfer race fixture",
    "dev_reward_receipt": "player-PDC DEV receipt",
    "bingulus.pending.grant-id": "legacy grant id",
    "bingulus.pending.recipient": "legacy pending recipient",
    "Player.saveData()": "explicit playerdata commit",
    "registerCriticalWrite(stateFile)": "global critical persistence coupling",
    "hasCriticalWriteFailure": "cross-feature global gate",
    "AtomicReference": "parallel DEV state representation",
    "AtomicInteger": "parallel DEV state representation",
    "AtomicLong": "parallel DEV state representation",
}


def verify_removed_paths() -> None:
    source = "\n".join(path.read_text(encoding="utf-8") for path in DEV_RUNTIME_SOURCES)
    found = [description for token, description in FORBIDDEN_DEV_PATHS.items() if token in source]
    if found:
        raise SystemExit("Obsolete DEV reward paths remain: " + ", ".join(found))


def main() -> None:
    verify_removed_paths()
    gradlew = ROOT / ("gradlew.bat" if sys.platform.startswith("win") else "gradlew")
    if not gradlew.is_file():
        raise SystemExit(f"Gradle wrapper is missing: {gradlew}")
    subprocess.run(
        [str(gradlew), "devItemRewardRegressionTest", "--no-daemon", "--stacktrace"],
        cwd=ROOT,
        check=True,
    )


if __name__ == "__main__":
    main()
