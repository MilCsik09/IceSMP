#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
quality = (ROOT / 'src/main/java/hu/taliann/icesmp/professions/ProfessionCraftQualityPolicy.java').read_text(encoding='utf-8')
recipe_gui = (ROOT / 'src/main/java/hu/taliann/icesmp/gui/ProfessionRecipeGUI.java').read_text(encoding='utf-8')
forge_gui = (ROOT / 'src/main/java/hu/taliann/icesmp/gui/ItemForgeGUI.java').read_text(encoding='utf-8')

assert 'MAXIMUM_QUALITY_FLOOR = 0.95D' in quality
assert 'minimumQualityFloor' in quality and 'masterworkChance' in quality
assert 'Páncélcsalád:' in recipe_gui
assert 'Tárgyszint:' in recipe_gui
assert 'Roll-minőség:' in recipe_gui
assert 'ProfessionSpecializationEconomyPolicy.effectFor(player, recipe)' in recipe_gui
assert 'recordSalvage(preview.template().armorFamily())' in forge_gui
assert 'outcome.success() && operation == ItemMutationCoordinator.Operation.SALVAGE' in forge_gui
print('Professions 2.0 runtime/UX closure: OK')
