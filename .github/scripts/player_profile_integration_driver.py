#!/usr/bin/env python3
"""Build the exact PlayerProfile integration candidate in a real Git checkout.

This driver is intentionally stored only on the temporary validation branch. It
never pushes. Publishing remains a separate, exact-head-gated workflow step after
all repository gates have passed.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path.cwd()
PROFILE_V2_HEAD = os.environ["PROFILE_V2_HEAD"]
RECOVERY_HEAD = os.environ["RECOVERY_HEAD"]
OLD_PLATFORM_HEAD = os.environ["OLD_PLATFORM_HEAD"]


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


def show(commit: str, path: str) -> str:
    return output("git", "show", f"{commit}:{path}") + "\n"


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def remove(path: str) -> None:
    target = ROOT / path
    if target.is_dir():
        shutil.rmtree(target)
    elif target.exists() or target.is_symlink():
        target.unlink()


def choose_ours(path: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    pattern = re.compile(
        r"^<<<<<<<[^\n]*\n(.*?)^=======\n(.*?)^>>>>>>>[^\n]*\n?",
        re.MULTILINE | re.DOTALL,
    )
    previous = None
    while text != previous and "<<<<<<<" in text:
        previous = text
        text = pattern.sub(lambda match: match.group(1), text)
    if any(marker in text for marker in ("<<<<<<<", "=======", ">>>>>>>")):
        raise RuntimeError(f"Unresolved conflict marker in {path}")
    write(path, text)


def task_block(text: str, name: str) -> str:
    match = re.search(
        rf"val {re.escape(name)} by tasks\.registering\(JavaExec::class\) \{{.*?^\}}\n",
        text,
        re.MULTILINE | re.DOTALL,
    )
    if not match:
        raise RuntimeError(f"Missing Gradle task block: {name}")
    return match.group(0).rstrip() + "\n\n"


def merge_maps(base: object, overlay: object) -> object:
    if isinstance(base, dict) and isinstance(overlay, dict):
        result = dict(base)
        for key, value in overlay.items():
            result[key] = merge_maps(result[key], value) if key in result else value
        return result
    return overlay


def resolve_ci() -> None:
    ci = show(PROFILE_V2_HEAD, ".github/workflows/ci.yml")
    branch_anchor = "      - rework/class-spec-1.21.11-compatibility\n"
    if "      - rework/class-spec-profile-v2\n" not in ci:
        if branch_anchor not in ci:
            raise RuntimeError("CI stacked-base branch anchor was not found")
        ci = ci.replace(branch_anchor, branch_anchor + "      - rework/class-spec-profile-v2\n", 1)

    old_markers = """          grep -F "ClassProfile v2 domain/codec regression tests passed." gradle-build.log
          grep -F "Class/spec application regression suite passed." gradle-build.log
          grep -F "YamlClassProfileRepository regression tests passed." gradle-build.log
          grep -F "Class profile lifecycle regression suite passed." gradle-build.log
"""
    new_markers = """          grep -F "ClassSpecSection v2 domain regression tests passed." gradle-build.log
          grep -F "Class/spec application regression suite passed." gradle-build.log
          grep -F "ClassSpecSection lifecycle regression suite passed." gradle-build.log
          grep -F "PlayerProfile domain regression suite passed." gradle-build.log
          grep -F "PlayerProfile YAML regression suite passed." gradle-build.log
          grep -F "PlayerProfile transaction regression suite passed." gradle-build.log
          grep -F "PlayerProfile API regression suite passed." gradle-build.log
"""
    if old_markers not in ci:
        raise RuntimeError("Expected Profile v2 CI marker block was not found")
    ci = ci.replace(old_markers, new_markers, 1)
    write(".github/workflows/ci.yml", ci)


def resolve_gradle() -> None:
    build = show(PROFILE_V2_HEAD, "build.gradle.kts")
    recovery_build = show(RECOVERY_HEAD, "build.gradle.kts")

    obsolete_tasks = (
        "classProfileV2RegressionTest",
        "classProfileRepositoryRegressionTest",
        "classProfileLifecycleRegressionTest",
    )
    for obsolete in obsolete_tasks:
        build = build.replace(task_block(build, obsolete), "", 1)

    new_task_names = (
        "classSpecSectionRegressionTest",
        "classSpecLifecycleRegressionTest",
        "playerProfileDomainRegressionTest",
        "playerProfileYamlRegressionTest",
        "playerProfileTransactionRegressionTest",
        "playerProfileApiRegressionTest",
    )
    insertion = "".join(task_block(recovery_build, name) for name in new_task_names)
    anchor = "val respecTransactionRegressionTest by tasks.registering(JavaExec::class) {"
    if anchor not in build:
        raise RuntimeError("Gradle insertion anchor was not found")
    build = build.replace(anchor, insertion + anchor, 1)

    check_match = re.search(r"tasks\.check \{\n.*?^\}\n?$", build, re.MULTILINE | re.DOTALL)
    if not check_match:
        raise RuntimeError("Gradle check dependency block was not found")
    check_block = """tasks.check {
    dependsOn(persistentStoreRegressionTest, devItemRewardRegressionTest, moderationRegressionTest,
        motdRegressionTest, sitRegressionTest, crateRegressionTest, configStartupRegressionTest,
        afkRegressionTest, worldGuardBridgeRegressionTest, territoryCapitalRegressionTest,
        hudRegressionTest, pauseMenuDialogRegressionTest, runtimeBugfixRegressionTest,
        factionPassiveRegressionTest, factionPassiveHardeningRegressionTest, factionTreasuryRegressionTest,
        relicItemRefreshRegressionTest, relicRefreshPipelineRegressionTest, lifecycleShutdownRegressionTest,
        questNpcValidationRegressionTest, resourcePackRegressionTest, classSpecCompatibilityRegressionTest,
        classSpecSectionRegressionTest, classSpecApplicationRegressionTest,
        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileYamlRegressionTest, playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
        respecTransactionRegressionTest, spellGrantLedgerRegressionTest)
}
"""
    build = build[: check_match.start()] + check_block
    for obsolete in obsolete_tasks:
        if obsolete in build:
            raise RuntimeError(f"Obsolete Gradle task survived: {obsolete}")
    write("build.gradle.kts", build)


def resolve_docs_and_manifest() -> None:
    choose_ours("CLAUDE.md")
    choose_ours("docs/ARCHITECTURE.md")

    base_manifest = json.loads(show(PROFILE_V2_HEAD, "docs/documentation-manifest.yml"))
    recovery_manifest = json.loads(show(RECOVERY_HEAD, "docs/documentation-manifest.yml"))
    merged_manifest = merge_maps(base_manifest, recovery_manifest)
    write(
        "docs/documentation-manifest.yml",
        json.dumps(merged_manifest, ensure_ascii=False, indent=2) + "\n",
    )


def resolve_listener() -> None:
    listener_path = "src/main/java/hu/taliann/icesmp/listeners/PlayerSessionCleanupListener.java"
    listener = show(PROFILE_V2_HEAD, listener_path).replace(
        "BukkitClassProfileSessionBridge",
        "BukkitClassSpecSectionSessionBridge",
    )
    required = (
        "FactionPassiveListener factionPassiveListener",
        "factionPassiveListener,",
        "private final FactionManager factionManager;",
        "factionManager.reconcileMembershipHistory(event.getPlayer());",
        "BukkitClassSpecSectionSessionBridge profileSessionBridge",
    )
    missing = [marker for marker in required if marker not in listener]
    if missing:
        raise RuntimeError(f"Session lifecycle hardening was not preserved: {missing}")
    write(listener_path, listener)


def remove_temporary_files() -> None:
    for path in (
        ".github/player-profile-platform-authority.txt",
        ".github/player-profile-platform-current-head.txt",
        ".github/player-profile-platform-no-force.txt",
        ".github/player-profile-platform-note.txt",
        ".github/player-profile-platform-please-open-pr.txt",
        ".github/player-profile-platform-pr-intent.txt",
        ".github/player-profile-platform-pr-ready.txt",
        ".github/player-profile-platform-pr-trigger.txt",
        ".github/player-profile-platform-root.txt",
        ".github/player-profile-platform-stack.txt",
        ".github/workflows/player-profile-authenticated-checkout.yml",
        "docs/development/PLAYER_PROFILE_PLATFORM_SCOPE.md",
        ".github/player-profile-root-payload",
        ".github/player-profile-bootstrap-trigger.txt",
        ".github/workflows/player-profile-bootstrap-checkout.yml",
    ):
        remove(path)


def update_repository_counts() -> tuple[int, int]:
    java_count = sum(1 for path in (ROOT / "src/main/java").rglob("*.java") if path.is_file())
    manager_count = sum(1 for path in (ROOT / "src/main/java").rglob("*Manager.java") if path.is_file())

    claude_path = ROOT / "CLAUDE.md"
    claude = claude_path.read_text(encoding="utf-8")
    claude, count = re.subn(
        r"A dokumentált release \d+ Java-fájl / \d+ manager\.",
        f"A dokumentált release {java_count} Java-fájl / {manager_count} manager.",
        claude,
        count=1,
    )
    if count != 1:
        raise RuntimeError("CLAUDE.md repository count marker was not found")
    claude_path.write_text(claude, encoding="utf-8")

    architecture_path = ROOT / "docs/ARCHITECTURE.md"
    architecture = architecture_path.read_text(encoding="utf-8")
    architecture, count = re.subn(
        r"- \*\*Méret:\*\* \d+ Java-fájl, ([^;]+); \d+ `\*Manager` osztály",
        rf"- **Méret:** {java_count} Java-fájl, \1; {manager_count} `*Manager` osztály",
        architecture,
        count=1,
    )
    if count != 1:
        raise RuntimeError("ARCHITECTURE.md repository count marker was not found")
    architecture_path.write_text(architecture, encoding="utf-8")
    return java_count, manager_count


def assert_no_conflict_markers() -> None:
    result = run(
        "git",
        "grep",
        "-nE",
        r"^(<<<<<<<|=======|>>>>>>>)",
        "--",
        ".",
        ":!docs/LORE.md",
        check=False,
        capture=True,
    )
    if result.returncode not in (0, 1):
        raise RuntimeError(result.stdout)
    if result.returncode == 0:
        raise RuntimeError(f"Conflict markers remain:\n{result.stdout}")


def main() -> int:
    run(
        "git",
        "fetch",
        "--no-tags",
        "origin",
        "+refs/heads/rework/class-spec-profile-v2:refs/remotes/origin/rework/class-spec-profile-v2",
        "+refs/heads/profile/player-profile-platform:refs/remotes/origin/profile/player-profile-platform",
        "+refs/heads/agent/player-profile-root-recovered-20260803-v4:refs/remotes/origin/agent/player-profile-root-recovered-20260803-v4",
    )
    exact_refs = {
        "refs/remotes/origin/rework/class-spec-profile-v2": PROFILE_V2_HEAD,
        "refs/remotes/origin/profile/player-profile-platform": OLD_PLATFORM_HEAD,
        "refs/remotes/origin/agent/player-profile-root-recovered-20260803-v4": RECOVERY_HEAD,
    }
    for ref, expected in exact_refs.items():
        actual = output("git", "rev-parse", ref)
        if actual != expected:
            raise RuntimeError(f"Ref drift: {ref}: expected {expected}, got {actual}")

    run("git", "config", "user.name", "github-actions[bot]")
    run("git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    run("git", "switch", "--detach", RECOVERY_HEAD)
    run("git", "switch", "-c", "local-player-profile-integration")

    merge = run("git", "merge", "--no-ff", "--no-commit", PROFILE_V2_HEAD, check=False)
    if merge.returncode != 1:
        raise RuntimeError(f"Expected a six-file semantic merge conflict, got status {merge.returncode}")
    conflicts = output("git", "diff", "--name-only", "--diff-filter=U").splitlines()
    expected_conflicts = sorted(
        (
            ".github/workflows/ci.yml",
            "CLAUDE.md",
            "build.gradle.kts",
            "docs/ARCHITECTURE.md",
            "docs/documentation-manifest.yml",
            "src/main/java/hu/taliann/icesmp/listeners/PlayerSessionCleanupListener.java",
        )
    )
    if sorted(conflicts) != expected_conflicts:
        raise RuntimeError(f"Unexpected conflict set: {conflicts}")

    resolve_ci()
    resolve_gradle()
    resolve_docs_and_manifest()
    resolve_listener()
    remove_temporary_files()
    java_count, manager_count = update_repository_counts()

    run("git", "add", "-A")
    unresolved = output("git", "diff", "--name-only", "--diff-filter=U")
    if unresolved:
        raise RuntimeError(f"Unresolved paths remain: {unresolved}")
    assert_no_conflict_markers()
    run("git", "diff", "--cached", "--check")
    run("git", "commit", "-m", "feat(profile): integrate PlayerProfile platform onto Profile v2")

    tree_before = output("git", "rev-parse", "HEAD^{tree}")
    run("git", "merge", "-s", "ours", "--no-ff", OLD_PLATFORM_HEAD, "-m", "chore(profile): retain stacked PR ancestry")
    tree_after = output("git", "rev-parse", "HEAD^{tree}")
    if tree_after != tree_before:
        raise RuntimeError("Stack ancestry merge changed the validated tree")

    for ancestor in (PROFILE_V2_HEAD, RECOVERY_HEAD, OLD_PLATFORM_HEAD):
        result = run("git", "merge-base", "--is-ancestor", ancestor, "HEAD", check=False)
        if result.returncode != 0:
            raise RuntimeError(f"Required ancestor missing: {ancestor}")
    run("git", "diff", "--check", f"{PROFILE_V2_HEAD}...HEAD")

    candidate = output("git", "rev-parse", "HEAD")
    print(f"Candidate: {candidate}")
    print(f"Java files: {java_count}")
    print(f"Manager classes: {manager_count}")
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with Path(github_output).open("a", encoding="utf-8") as handle:
            handle.write(f"candidate_sha={candidate}\n")
            handle.write(f"java_count={java_count}\n")
            handle.write(f"manager_count={manager_count}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
