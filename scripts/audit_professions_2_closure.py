#!/usr/bin/env python3
"""Fail-closed idempotent Professions 2.0 closure authority verifier.

The historical closure rewrote production sources from embedded snapshots. That made the verifier
itself capable of restoring obsolete arithmetic. The final closure is intentionally read-only: it
requires the current single-authority contracts and fails if legacy parallel logic reappears.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, *tokens: str) -> str:
    text = source(path)
    for token in tokens:
        if token not in text:
            raise RuntimeError(f"{path}: missing closure token: {token}")
    return text


def main() -> None:
    plan = require(
        "src/main/java/hu/taliann/icesmp/professions/ProfessionEffectiveCraftPlan.java",
        "effectiveInput(",
        "effectiveOutputAmount(",
        "effectiveOutputs(",
        "Math.multiplyExact(oneCraft, batches)",
    )
    if "adjustInput((long)" in plan:
        raise RuntimeError("CraftPlan must round one craft before multiplying a batch")

    transaction = require(
        "src/main/java/hu/taliann/icesmp/professions/ProfessionCraftTransaction.java",
        "ProfessionEffectiveCraftPlan plan",
        "preflight(final Player player",
        "preflightStorage",
        "plan.materialInputs()",
        "plan.uniqueInputs()",
        "PERSISTENCE_FAILED",
        "inventory.setStorageContents(cloneContents(before));",
    )
    if "ProfessionSpecializationEconomyPolicy.effectFor(player, recipe)" in transaction:
        raise RuntimeError("transaction recomputes specialization outside CraftPlan authority")
    if "dropItemNaturally" in transaction:
        raise RuntimeError("profession transaction must not world-drop overflow")

    listener = require(
        "src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java",
        "ProfessionEffectiveCraftPlan.of(",
        "craftTransaction.preflight(player, plan, outputs)",
        "craftTransaction.apply(player, plan, outputs)",
        "plan.materialInputs()",
        "plan.uniqueInputs()",
    )
    for legacy in (
        "craftTransaction.apply(player, recipe, batches, outputs)",
        "hasIngredients(player, recipe)",
    ):
        if legacy in listener:
            raise RuntimeError(f"legacy click admission still present: {legacy}")

    gui = require(
        "src/main/java/hu/taliann/icesmp/gui/ProfessionRecipeGUI.java",
        "ProfessionEffectiveCraftPlan.of(recipe, specialization, 1)",
        "transaction.preflight(player, plan, previewOutputs)",
        "plan.materialInputs()",
        "plan.uniqueInputs()",
        "effectiveOutputAmount",
        "maxCraftableBatches",
    )
    if "hasIngredients(player, recipe" in gui:
        raise RuntimeError("GUI still uses raw recipe ingredients for craftability")

    require(
        "src/main/java/hu/taliann/icesmp/itemization/ItemSalvageService.java",
        "familyScrapId",
        "szovet_foszlany",
        "bor_hulladek",
        "lanc_toredek",
        "femhulladek",
    )
    require(
        "src/main/java/hu/taliann/icesmp/professions/BlueprintRecoveryPolicy.java",
        "ROLLBACK_UNTOUCHED",
        "RELEASE_AND_ROLLBACK",
        "CONSUME_AND_COMMIT",
        "COMMIT_CONSUMED",
    )
    require(
        "src/regression/java/hu/taliann/icesmp/professions/Professions2RegressionSuite.java",
        "effectiveCraftPlanHasNoBatchRoundingArbitrage",
        "blueprintRecoveryMatrixIsExact",
    )
    print("Professions 2.0 final closure authority: OK")


if __name__ == "__main__":
    main()
