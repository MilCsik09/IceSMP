#!/usr/bin/env python3
"""Compile and run the dependency-free DEV-item durable-state regression suite."""

from __future__ import annotations

import pathlib
import shutil
import subprocess
import tempfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = [
    ROOT / "src/main/java/hu/taliann/icesmp/managers/DevItemStateData.java",
    ROOT / "src/regression/java/hu/taliann/icesmp/managers/DevItemStateDataRegressionTest.java",
]
MAIN_CLASS = "hu.taliann.icesmp.managers.DevItemStateDataRegressionTest"
MANAGER_SOURCE = ROOT / "src/main/java/hu/taliann/icesmp/managers/DevItemManager.java"


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f"Missing protocol method: {signature}")
    opening = source.find("{", start)
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


def verify_manager_protocol() -> None:
    if not MANAGER_SOURCE.is_file():
        raise SystemExit(f"Missing manager source: {MANAGER_SOURCE}")
    source = MANAGER_SOURCE.read_text(encoding="utf-8")

    tick = method_body(source, "private void tickOwner(final Player owner)")
    require_order(tick, "preparePendingRewardDurably", "getInventory().addItem")
    if "requestSave();" in tick:
        raise SystemExit("Pending reward delivery must not rely on the asynchronous save queue")

    delivery = tick[tick.index("final ItemStack[] inventoryBefore"):]
    require_order(
        delivery,
        "getPersistentDataContainer().set",
        "persistPlayer(owner)",
        "completePendingRewardDurably(pending)",
    )
    failed_save = delivery[delivery.index("if (!persistPlayer(owner))"):]
    require_order(failed_save, "setStorageContents(inventoryBefore)", "restoreDeliveryReceipt")

    prepare = method_body(source, "private PendingReward preparePendingRewardDurably")
    require_order(prepare, "pendingItem.set", "pendingGrantId.set", "writeSnapshot(snapshot)")
    complete = method_body(source, "private boolean completePendingRewardDurably")
    require_order(complete, "clearPendingRewardLocked()", "writeSnapshot(snapshot)")

    for durable_field in ("bingulus.pending.grant-id", "bingulus.pending.recipient"):
        if source.count(durable_field) < 2:
            raise SystemExit(f"Durable pending field is not loaded and saved: {durable_field}")
    if "legacy pending reward has no crash-safe grant-id and recipient" not in source:
        raise SystemExit("Ambiguous legacy pending rewards must remain fail-closed")


def require_tool(name: str) -> str:
    executable = shutil.which(name)
    if executable is None:
        raise SystemExit(f"Required Java 21 tool is unavailable: {name}")
    return executable


def main() -> None:
    verify_manager_protocol()
    javac = require_tool("javac")
    java = require_tool("java")
    missing = [str(path) for path in SOURCES if not path.is_file()]
    if missing:
        raise SystemExit("Missing regression source(s): " + ", ".join(missing))

    with tempfile.TemporaryDirectory(prefix="icesmp-dev-item-regression-") as directory:
        output = pathlib.Path(directory)
        subprocess.run(
            [javac, "--release", "21", "-d", str(output), *(str(path) for path in SOURCES)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run([java, "-cp", str(output), MAIN_CLASS], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
