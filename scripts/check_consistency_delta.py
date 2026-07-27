#!/usr/bin/env python3
"""Compare consistency output between a base Git ref and the checked-out head."""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import tempfile
from dataclasses import dataclass


ROOT = pathlib.Path(__file__).resolve().parents[1]
SUMMARY = re.compile(r"Összegzés:\s*(\d+) FAIL,\s*(\d+) WARN")


@dataclass(frozen=True)
class Result:
    sha: str
    fails: tuple[str, ...]
    warns: tuple[str, ...]
    output: str

    @property
    def fail_categories(self) -> set[str]:
        return {category(line) for line in self.fails}

    @property
    def warn_categories(self) -> set[str]:
        return {category(line) for line in self.warns}


def category(line: str) -> str:
    body = line.split(":", 1)[1].strip() if ":" in line else line
    return body.split(":", 1)[0].strip()


def git(*args: str, cwd: pathlib.Path = ROOT) -> str:
    completed = subprocess.run(
        ["git", *args], cwd=cwd, check=True, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    return completed.stdout.strip()


def run_consistency(checkout: pathlib.Path) -> Result:
    completed = subprocess.run(
        ["python3", "scripts/check_consistency.py"], cwd=checkout,
        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )
    output = completed.stdout
    match = SUMMARY.search(output)
    if match is None:
        raise SystemExit(
            f"Consistency output for {checkout} has no parseable summary:\n{output}"
        )
    expected_fails = int(match.group(1))
    expected_warns = int(match.group(2))
    fails = tuple(sorted(line for line in output.splitlines() if line.startswith("✗ FAIL:")))
    warns = tuple(sorted(line for line in output.splitlines() if line.startswith("⚠ WARN:")))
    if len(fails) != expected_fails or len(warns) != expected_warns:
        raise SystemExit(
            "Consistency summary and emitted diagnostics disagree: "
            f"summary={expected_fails}/{expected_warns}, lines={len(fails)}/{len(warns)}"
        )
    return Result(git("rev-parse", "HEAD", cwd=checkout), fails, warns, output)


def print_result(name: str, result: Result) -> None:
    print(f"{name} commit: {result.sha}")
    print(f"{name} consistency: {len(result.fails)} FAIL / {len(result.warns)} WARN")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-ref", required=True)
    parser.add_argument("--head-ref", default="HEAD")
    args = parser.parse_args()

    head_sha = git("rev-parse", args.head_ref)
    base_sha = git("rev-parse", args.base_ref)
    with tempfile.TemporaryDirectory(prefix="icesmp-consistency-base-") as directory:
        base_checkout = pathlib.Path(directory) / "base"
        git("worktree", "add", "--detach", str(base_checkout), base_sha)
        try:
            base = run_consistency(base_checkout)
            head = run_consistency(ROOT)
        finally:
            subprocess.run(
                ["git", "worktree", "remove", "--force", str(base_checkout)],
                cwd=ROOT, check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )

    if head.sha != head_sha:
        raise SystemExit(f"Head checkout moved during comparison: expected {head_sha}, got {head.sha}")

    print_result("Base", base)
    print_result("Head", head)

    new_fails = sorted(set(head.fails) - set(base.fails))
    new_warns = sorted(set(head.warns) - set(base.warns))
    new_fail_categories = sorted(head.fail_categories - base.fail_categories)
    new_warn_categories = sorted(head.warn_categories - base.warn_categories)

    problems: list[str] = []
    if len(head.fails) > len(base.fails):
        problems.append("FAIL count increased")
    if len(head.warns) > len(base.warns):
        problems.append("WARN count increased")
    if new_fail_categories:
        problems.append("new FAIL categories: " + ", ".join(new_fail_categories))
    if new_warn_categories:
        problems.append("new WARN categories: " + ", ".join(new_warn_categories))
    if new_fails:
        problems.append("new FAIL diagnostics:\n" + "\n".join(new_fails))
    if new_warns:
        problems.append("new WARN diagnostics:\n" + "\n".join(new_warns))

    if problems:
        raise SystemExit("Consistency drift detected:\n- " + "\n- ".join(problems))

    print("Consistency delta: PASS — no new FAIL/WARN diagnostic or category.")


if __name__ == "__main__":
    main()
