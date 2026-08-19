#!/usr/bin/env python3
"""Fail-closed specialization economy closure verifier.

Specialization policy remains PlayerProfile-backed, but all craft arithmetic must flow through the
immutable ProfessionEffectiveCraftPlan. This verifier deliberately performs no source rewriting.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    policy = (ROOT / "src/main/java/hu/taliann/icesmp/professions/ProfessionSpecializationEconomyPolicy.java").read_text(encoding="utf-8")
    for token in (
        "PlayerProfileSpecializationProgressStore",
        "roleOf",
        "PROCESSING_EFFICIENCY",
        "PROCESSING_YIELD",
        "CONSUMABLE_EFFICIENCY",
        "CONSUMABLE_YIELD",
        "EQUIPMENT_EXPERTISE",
        "SERVICE_EXPERTISE",
        "BLUEPRINT_EFFICIENCY",
    ):
        if token not in policy:
            raise RuntimeError(f"specialization policy incomplete: {token}")

    plan = (ROOT / "src/main/java/hu/taliann/icesmp/professions/ProfessionEffectiveCraftPlan.java").read_text(encoding="utf-8")
    for token in ("specialization", "effectiveInput", "effectiveOutputs", "effectiveOutputAmount"):
        if token not in plan:
            raise RuntimeError(f"CraftPlan specialization authority incomplete: {token}")

    transaction = (ROOT / "src/main/java/hu/taliann/icesmp/professions/ProfessionCraftTransaction.java").read_text(encoding="utf-8")
    if "ProfessionEffectiveCraftPlan plan" not in transaction:
        raise RuntimeError("transaction is not CraftPlan-backed")
    if "ProfessionSpecializationEconomyPolicy.effectFor(player, recipe)" in transaction:
        raise RuntimeError("specialization arithmetic escaped CraftPlan authority")

    config = (ROOT / "src/main/resources/config.yml").read_text(encoding="utf-8")
    for token in ("economy-efficiency-percent: 0.10", "economy-yield-percent: 0.10"):
        if token not in config:
            raise RuntimeError(f"specialization config missing: {token}")

    report = json.loads((ROOT / "docs/development/professions-2-specializations.json").read_text(encoding="utf-8"))
    if report.get("schema") != 1 or len(report.get("specializations", [])) != 16:
        raise RuntimeError("specialization handoff drift")
    if len({row["role"] for row in report["specializations"]}) < 6:
        raise RuntimeError("specialization role diversity drift")
    if report.get("policy", {}).get("random_conservation_proc") is not False:
        raise RuntimeError("random material conservation must remain disabled")

    print("Professions 2.0 specialization economy authority: OK")


if __name__ == "__main__":
    main()
