#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def replace_once(relative: str, old: str, new: str) -> None:
    path = ROOT / relative
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{relative}: expected one occurrence, got {count}: {old!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

replace_once(
    "src/main/java/hu/taliann/icesmp/managers/ConfigManager.java",
    "import java.util.LinkedHashMap;\nimport java.util.List;",
    "import java.util.HashSet;\nimport java.util.LinkedHashMap;\nimport java.util.List;",
)
replace_once(
    "src/main/java/hu/taliann/icesmp/managers/ConfigManager.java",
'''        final Set<String> overridePaths = plugin.getConfig().getKeys(true).stream()
                .filter(key -> !plugin.getConfig().isConfigurationSection(key))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());''',
'''        final Set<String> overridePaths = new HashSet<>();
        for (final String key : plugin.getConfig().getKeys(true)) {
            if (!plugin.getConfig().isConfigurationSection(key)) {
                overridePaths.add(key);
            }
        }''',
)
replace_once(
    "src/main/java/hu/taliann/icesmp/listeners/ConfigMenuGUIListener.java",
    "event.isShiftClick(), event.isRightClick()",
    "event.isShiftClick(), event.getClick().isRightClick()",
)
replace_once(
    "src/regression/java/hu/taliann/icesmp/professions/ProfessionRecipeAuditRegressionSuite.java",
'''        final String listener = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeListener.java"));
        check(listener.contains("uniqueIngredients") && listener.contains("profession"),
                "custom ingredients and profession gate remain enforced");''',
'''        final String listener = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeListener.java"));
        check(listener.contains("hasProfession(player, recipe.profession())")
                        && listener.contains("getLevel(player, recipe.profession())"),
                "legacy masterwork profession and level gates remain enforced");
        final String bookListener = Files.readString(
                Path.of("src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java"));
        check(bookListener.contains("recipe.uniqueIngredients().entrySet()")
                        && bookListener.contains("uniqueMaterials.idOf(item)"),
                "catalog custom ingredients require canonical unique-item identity");''',
)

print("stage3 compile fixups applied")
