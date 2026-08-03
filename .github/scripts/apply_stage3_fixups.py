#!/usr/bin/env python3
from __future__ import annotations
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content, encoding="utf-8")

def replace_once(path: str, old: str, new: str) -> None:
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one occurrence, got {count}: {old!r}")
    write(path, content.replace(old, new, 1))

# The icon mapping is deliberately stored in profession-materials.yml, not in the recipe catalog.
path = "src/regression/java/hu/taliann/icesmp/professions/ProfessionRecipeAuditRegressionSuite.java"
replace_once(path,
'''        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        final ConfigurationSection root = yaml.getConfigurationSection("profession-recipes");''',
'''        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        final YamlConfiguration materials = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/config/profession-materials.yml").toFile());
        final ConfigurationSection root = yaml.getConfigurationSection("profession-recipes");''')
replace_once(path,
'''                final String model = yaml.getString("profession-materials." + unique.toLowerCase(Locale.ROOT) + ".item-model");''',
'''                final String model = materials.getString("profession-materials."
                        + unique.toLowerCase(Locale.ROOT) + ".item-model");''')

# The canonical ConfigMenuGUI already exposes all three doom-gate scalar entries exactly once.
print("stage3 fixups applied")
