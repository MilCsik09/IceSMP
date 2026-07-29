#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import tempfile
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from repository_inventory.delta import compare_inventories
from repository_inventory.inventory import generate_inventory
from repository_inventory.util import dump_json, git, write_markdown_table


def resolve(root: Path, ref: str) -> str:
    value = git(root, "rev-parse", "--verify", f"{ref}^{{commit}}")
    if not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ValueError(f"Unresolvable git ref: {ref}")
    return value


def add_worktree(root: Path, target: Path, sha: str) -> None:
    git(root, "worktree", "add", "--detach", "--force", str(target), sha)


def remove_worktree(root: Path, target: Path) -> None:
    try: git(root, "worktree", "remove", "--force", str(target), check=False)
    finally: shutil.rmtree(target, ignore_errors=True)


def commit_metadata(root: Path, base_sha: str, head_sha: str) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    raw = git(root, "log", "--reverse", "--format=%H%x09%P%x09%s", f"{base_sha}..{head_sha}")
    commits: list[dict[str, Any]] = []
    pr_numbers: set[int] = set()
    for line in raw.splitlines():
        parts = line.split("\t", 2)
        if len(parts) != 3: continue
        sha, parents, subject = parts
        numbers = [int(x) for x in re.findall(r"#(\d+)", subject)]
        pr_numbers.update(numbers)
        commits.append({"sha": sha, "parents": parents.split(), "subject": subject,
                        "merge": len(parents.split()) > 1, "pr_numbers": numbers})
    pulls = [{"number": number, "title": "", "source": "git-log"} for number in sorted(pr_numbers)]
    return commits, pulls


def github_repo(root: Path) -> str | None:
    remote = git(root, "config", "--get", "remote.origin.url", check=False)
    match = re.search(r"github\.com[/:]([^/]+/[^/.]+)(?:\.git)?$", remote)
    return match.group(1) if match else os.getenv("GITHUB_REPOSITORY")


def enrich_pulls(pulls: list[dict[str, Any]], repository: str | None) -> tuple[list[dict[str, Any]], bool]:
    token = os.getenv("GITHUB_TOKEN")
    if not token or not repository:
        return pulls, False
    result = []
    complete = True
    for pull in pulls:
        url = f"https://api.github.com/repos/{repository}/pulls/{pull['number']}"
        request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "IceSMP-repository-inventory"})
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                data = json.load(response)
            result.append({"number": pull["number"], "title": data.get("title", ""),
                           "url": data.get("html_url", ""), "source": "github-api"})
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
            result.append(pull); complete = False
    return result, complete


def changed_files(root: Path, base_sha: str, head_sha: str) -> list[dict[str, str]]:
    raw = git(root, "diff", "--name-status", "--find-renames", base_sha, head_sha)
    result = []
    for line in raw.splitlines():
        parts = line.split("\t")
        if not parts: continue
        item = {"status": parts[0], "path": parts[-1]}
        if len(parts) > 2: item["previous_path"] = parts[1]
        result.append(item)
    return result


def write_delta_markdown(path: Path, title: str, delta: dict[str, Any]) -> None:
    rows = []
    for kind in ("added", "removed", "modified"):
        rows.extend((kind.upper(), item) for item in delta.get(kind, []))
    write_markdown_table(path, title, ["Change", "Stable ID"], rows)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate evidence-based inventory delta between arbitrary Git refs.")
    parser.add_argument("--root", default=".")
    parser.add_argument("--base-ref", required=True)
    parser.add_argument("--head-ref", default="HEAD")
    parser.add_argument("--output", default="build/release-inventory")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    output = Path(args.output)
    if not output.is_absolute(): output = root / output
    output.mkdir(parents=True, exist_ok=True)
    try:
        base_sha, head_sha = resolve(root, args.base_ref), resolve(root, args.head_ref)
    except Exception as exc:
        print(f"REF_ERROR: {exc}", file=sys.stderr); return 2
    temp_root = Path(tempfile.mkdtemp(prefix="icesmp-release-inventory-"))
    base_tree, head_tree = temp_root / "base", temp_root / "head"
    try:
        add_worktree(root, base_tree, base_sha)
        add_worktree(root, head_tree, head_sha)
        base_inventory = generate_inventory(base_tree, "report")
        head_inventory = generate_inventory(head_tree, "report")
        delta = compare_inventories(base_inventory, head_inventory)
        commits, pulls = commit_metadata(root, base_sha, head_sha)
        pulls, pr_metadata_complete = enrich_pulls(pulls, github_repo(root))
        files = changed_files(root, base_sha, head_sha)
        metadata = {
            "base_ref": args.base_ref, "base_sha": base_sha, "head_ref": args.head_ref, "head_sha": head_sha,
            "commit_count": len(commits), "merge_commit_count": sum(1 for x in commits if x["merge"]),
            "pull_request_count": len(pulls), "pr_metadata_complete": pr_metadata_complete,
            "generated_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
            "changed_files": files,
        }
        release = {"schema_version": 1, "metadata": metadata, "commits": commits,
                   "pull_requests": pulls, "delta": delta,
                   "base_inventory_counts": base_inventory.get("counts", {}),
                   "head_inventory_counts": head_inventory.get("counts", {}),
                   "review_required": [x for x in head_inventory.get("findings", []) if x.get("severity") == "REVIEW_REQUIRED"]}
        dump_json(output / "metadata.json", metadata)
        dump_json(output / "release-inventory.json", release)
        for name, value in delta.items(): dump_json(output / f"{name.replace('_delta', '')}-delta.json", value)
        write_delta_markdown(output / "command-delta.md", "Command delta", delta["command_delta"])
        write_delta_markdown(output / "feature-delta.md", "Feature delta", delta["feature_delta"])
        write_markdown_table(output / "commits.md", "Commits", ["SHA", "Merge", "PR", "Subject"],
                             ((x["sha"], x["merge"], ", ".join(map(str, x["pr_numbers"])), x["subject"]) for x in commits))
        write_markdown_table(output / "pull-requests.md", "Pull requests", ["Number", "Title", "Source"],
                             ((x["number"], x.get("title", ""), x["source"]) for x in pulls))
        write_markdown_table(output / "review-required.md", "Release review required", ["Code", "ID", "Message"],
                             ((x["code"], x.get("stable_id", ""), x["message"]) for x in release["review_required"]))
        summary = ["# Release Inventory", "", f"**Base:** `{base_sha}`", f"**Head:** `{head_sha}`", "",
                   f"Commits: {len(commits)}  ", f"Merge commits: {metadata['merge_commit_count']}  ",
                   f"Changed files: {len(files)}  ", f"PR metadata complete: {pr_metadata_complete}", "",
                   "## Delta totals", ""]
        for name, value in delta.items():
            summary.append(f"- {name}: +{len(value['added'])} / -{len(value['removed'])} / ~{len(value['modified'])}")
        (output / "summary.md").write_text("\n".join(summary) + "\n", encoding="utf-8")
        (output / "release-inventory.md").write_text("\n".join(summary) + "\n", encoding="utf-8")
    except Exception as exc:
        print(f"RELEASE_INVENTORY_ERROR: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2
    finally:
        if base_tree.exists(): remove_worktree(root, base_tree)
        if head_tree.exists(): remove_worktree(root, head_tree)
        shutil.rmtree(temp_root, ignore_errors=True)
    print(f"Release inventory: {base_sha}..{head_sha} ({len(commits)} commits)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
