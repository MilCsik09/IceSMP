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


def require_tool(name: str) -> str:
    executable = shutil.which(name)
    if executable is None:
        raise SystemExit(f"Required Java 21 tool is unavailable: {name}")
    return executable


def main() -> None:
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
