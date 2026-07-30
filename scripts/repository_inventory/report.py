from __future__ import annotations

from pathlib import Path
from typing import Any

from .models import finding_counts
from .util import dump_json, write_markdown_table


def _findings_md(path: Path, title: str, findings: list[dict[str, Any]]) -> None:
    write_markdown_table(path, title, ["Severity", "Code", "ID", "Message"],
                         ((f.get("severity"), f.get("code"), f.get("stable_id"), f.get("message")) for f in findings))


def write_repository_reports(output: Path, inventory: dict[str, Any]) -> None:
    output.mkdir(parents=True, exist_ok=True)
    dump_json(output / "repository-inventory.json", inventory)
    mapping = {
        "commands.json": inventory.get("commands", []),
        "routes.json": inventory.get("routes", inventory.get("subcommands", [])),
        "root-aliases.json": inventory.get("root_aliases", []),
        "routing-aliases.json": inventory.get("routing_aliases", []),
        "permissions.json": inventory.get("permissions", []),
        "config-keys.json": inventory.get("config_keys", []), "message-keys.json": inventory.get("message_keys", []),
        "features.json": inventory.get("features", []), "components.json": inventory.get("components", []),
        "documentation-coverage.json": inventory.get("documentation_coverage", {}),
    }
    for name, value in mapping.items(): dump_json(output / name, value)
    write_markdown_table(output / "commands.md", "Command inventory",
                         ["Path", "Audience", "Permission", "Aliases", "Implementation", "Confidence"],
                         (("/" + x["path"].replace(".", " "), ", ".join(x.get("audience", [])), ", ".join(x.get("permission", [])),
                           ", ".join(x.get("aliases", [])), x.get("implementation") or x.get("implementation_method", ""), x.get("confidence"))
                          for x in [*inventory.get("commands", []), *inventory.get("routes", inventory.get("subcommands", []))]))
    write_markdown_table(output / "permissions.md", "Permission inventory",
                         ["Node", "Audience", "Commands", "GUI", "Listeners", "Confidence"],
                         ((x["node"], x["audience"], ", ".join(x["commands"]), ", ".join(x["guis"]), ", ".join(x["listeners"]), x["confidence"])
                          for x in inventory.get("permissions", [])))
    write_markdown_table(output / "config-keys.md", "Config key inventory",
                         ["Path", "Type", "Default", "Readers", "Lifecycle", "Confidence"],
                         ((x["path"], ", ".join(x["types"]), x["default"], len(x["readers"]), x["lifecycle"], x["confidence"])
                          for x in inventory.get("config_keys", [])))
    write_markdown_table(output / "message-keys.md", "Message key inventory",
                         ["Key", "Definitions", "Uses", "Fallbacks", "Confidence"],
                         ((x["key"], len(x["definitions"]), len(x["uses"]), len(x["fallbacks"]), x["confidence"])
                          for x in inventory.get("message_keys", [])))
    write_markdown_table(output / "features.md", "Feature inventory",
                         ["ID", "Audience", "Sources", "Commands", "Documentation", "Confidence"],
                         ((x["id"], ", ".join(x["audience"]), len(x["sources"]), ", ".join(x["commands"]), ", ".join(x["documentation"]), x["confidence"])
                          for x in inventory.get("features", [])))
    coverage = inventory.get("documentation_coverage", {})
    write_markdown_table(output / "documentation-coverage.md", "Documentation coverage",
                         ["Metric", "Value"], coverage.get("metrics", {}).items())
    _findings_md(output / "review-required.md", "Review required",
                 [x for x in inventory.get("findings", []) if x.get("severity") == "REVIEW_REQUIRED"])
    _findings_md(output / "findings.md", "All findings", inventory.get("findings", []))
    counts = inventory.get("counts", {})
    severities = finding_counts(inventory.get("findings", []))
    result = "FAIL" if severities["FAIL"] else ("WARN" if severities["WARN"] or severities["REVIEW_REQUIRED"] else "PASS")
    lines = ["# Repository Documentation Inventory", "", f"**Result: {result}**", "", "```text",
             f"Root commands:          {counts.get('root_commands', 0)}",
             f"Functional routes:      {counts.get('functional_routes', counts.get('subcommands', 0))}",
             f"Root aliases:           {counts.get('root_aliases', counts.get('aliases', 0))}",
             f"Routing aliases:        {counts.get('routing_aliases', 0)}",
             f"Permissions:            {counts.get('permissions', 0)}",
             f"Player features:        {counts.get('player_features', 0)}",
             f"Admin features:         {counts.get('admin_features', 0)}",
             f"Unclassified:           {counts.get('unclassified', 0)}",
             f"Undocumented commands:  {counts.get('undocumented_commands', 0)}",
             f"Undocumented features:  {counts.get('undocumented_features', 0)}",
             f"Review required:        {severities.get('REVIEW_REQUIRED', 0)}",
             f"Blocking findings:      {severities.get('FAIL', 0)}", "```", "",
             "## Finding totals", "", f"- FAIL: {severities['FAIL']}", f"- WARN: {severities['WARN']}",
             f"- REVIEW_REQUIRED: {severities['REVIEW_REQUIRED']}", "",
             "Detailed JSON and Markdown reports are included in the workflow artifact."]
    (output / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
